/**
 * ROUT backend - Cloudflare Workers edition.
 * Provider key lives in Worker secrets (GEMINI_API_KEY), never in the app or repo.
 * Per-device enrollment: no shared token is embedded in the public APK.
 * Storage: Workers KV binding ROUT_KV (device credentials, one-use enroll tickets, rate windows).
 */

const JSON_HEADERS = { 'Content-Type': 'application/json' };

function json(obj, code = 200) {
  return new Response(JSON.stringify(obj), { status: code, headers: JSON_HEADERS });
}

function randToken(bytes = 32) {
  const b = new Uint8Array(bytes);
  crypto.getRandomValues(b);
  return Array.from(b).map(x => x.toString(16).padStart(2, '0')).join('');
}

async function sha256(text) {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(text));
  return Array.from(new Uint8Array(digest)).map(x => x.toString(16).padStart(2, '0')).join('');
}

async function rateLimited(kv, key, limitPerMin) {
  const win = Math.floor(Date.now() / 60000);
  const k = `rate:${key}:${win}`;
  const n = parseInt((await kv.get(k)) || '0', 10) + 1;
  await kv.put(k, String(n), { expirationTtl: 120 });
  return n > limitPerMin;
}

async function deviceValid(env, token) {
  if (!token) return false;
  const hash = await sha256(token);
  return (await env.ROUT_KV.get(`device:${hash}`)) !== null;
}


function enrollStore(env) {
  // Durable Object binding in production; in-memory shim for local tests.
  if (env.ENROLLMENT) return env.ENROLLMENT.get(env.ENROLLMENT.idFromName('global'));
  return env.__enrollShim;
}

async function handleEnrollStart(request, env) {
  const body = await request.json().catch(() => ({}));
  const clientHash = String(body.client_hash || '');
  // The initiating app proves possession of a per-attempt secret at complete time.
  // Anyone who merely sees the approval URL (including the approver) cannot complete.
  if (!/^[0-9a-f]{64}$/.test(clientHash)) return json({ error: 'invalid_request' }, 400);
  const ticket = randToken(24);          // one-use, 10 minutes (DO enforces TTL)
  const store = enrollStore(env);
  await store.fetch(new Request('https://do/start', {
    method: 'POST', headers: JSON_HEADERS,
    body: JSON.stringify({ ticket, clientHash })
  }));
  const base = new URL(request.url).origin;
  return json({ enroll_id: ticket, approve_url: `${base}/approve?id=${ticket}`, expires_in: 600 });
}

function timingSafeEqual(a, b) {
  const ba = new TextEncoder().encode(a || '');
  const bb = new TextEncoder().encode(b || '');
  const n = Math.max(ba.length, bb.length, 1);
  let diff = ba.length ^ bb.length;
  for (let i = 0; i < n; i++) diff |= (ba[i % Math.max(ba.length, 1)] || 0) ^ (bb[i % Math.max(bb.length, 1)] || 0);
  return diff === 0;
}

async function handleApprove(request, url, env) {
  const id = url.searchParams.get('id') || '';
  const store = enrollStore(env);
  const rec = (await (await store.fetch(new Request('https://do/get?ticket=' + encodeURIComponent(id)))).json()).rec;
  const page = (title, body, button) => new Response(
    `<!doctype html><meta name=viewport content="width=device-width"><title>${title}</title>` +
    `<body style="background:#050608;color:#e8f4f8;font-family:sans-serif;display:grid;place-items:center;min-height:100vh;margin:0">` +
    `<div style="max-width:420px;padding:24px;text-align:center"><h2 style="color:#4DE8FF">ROUT</h2><p>${body}</p>${button}</div>`,
    { headers: { 'Content-Type': 'text/html' } });
  if (rec === null) return page('ROUT', 'This approval link is expired or already used.', '');
  if (rec.createdAt && Date.now() - rec.createdAt > 600000) {
    await store.fetch(new Request('https://do/consume', { method: 'POST', headers: JSON_HEADERS, body: JSON.stringify({ ticket: id }) }));
    return page('ROUT', 'This approval link is expired or already used.', '');
  }
  if (request.method === 'POST') {
    // Cross-site hardening: browsers send Sec-Fetch-Site / Origin on form posts.
    const sfs = request.headers.get('sec-fetch-site');
    if (sfs && sfs !== 'same-origin' && sfs !== 'same-site' && sfs !== 'none') {
      return page('ROUT', 'Invalid request origin.', '');
    }
    const origin = request.headers.get('origin');
    if (origin && origin !== url.origin) return page('ROUT', 'Invalid request origin.', '');
    if (await rateLimited(env.ROUT_KV, 'approve:' + (request.headers.get('cf-connecting-ip') || 'anon'), 10)) {
      return page('ROUT', 'Too many attempts. Wait a minute and try again.', '');
    }
    const form = await request.formData().catch(() => null);
    const ownerCode = form ? String(form.get('owner_code') || '') : '';
    const ticketId = form ? String(form.get('id') || '') : '';
    if (!ticketId || ticketId !== id) return page('ROUT', 'Invalid approval form.', '');
    // The owner code is the CSRF/forgery defense: a third party cannot know it.
    // It is never logged, echoed back, or written to storage - compared in memory only.
    if (!env.OWNER_CODE || !timingSafeEqual(ownerCode, env.OWNER_CODE)) {
      return page('ROUT', 'Wrong owner code.', '');
    }
    await store.fetch(new Request('https://do/approve', { method: 'POST', headers: JSON_HEADERS, body: JSON.stringify({ ticket: id }) }));
    return page('ROUT', 'Device approved. You can return to the ROUT app.', '');
  }
  return page('ROUT',
    'Approve this phone as a ROUT device? This link works once, for 10 minutes. Enter the owner code (stored in your vault) to approve.',
    `<form method=post><input type=hidden name=id value="${id}">` +
    `<input type=password name=owner_code autocomplete=off placeholder="Owner code" required style="padding:12px;width:70%;margin-bottom:12px;border-radius:8px;border:1px solid #4DE8FF;background:#0a1014;color:#e8f4f8"><br>` +
    `<button type=submit style="background:#4DE8FF;border:0;padding:14px 28px;border-radius:12px;font-size:16px">Approve this device</button></form>`);
}

async function handleEnrollComplete(request, env) {
  const body = await request.json().catch(() => ({}));
  const id = String(body.enroll_id || '');
  const clientSecret = String(body.client_secret || '');
  // State check first so pending gives the app its 202 poll response.
  const store = enrollStore(env);
  const peek = (await (await store.fetch(new Request('https://do/get?ticket=' + encodeURIComponent(id)))).json()).rec;
  if (peek === null) return json({ error: 'enroll_expired' }, 410);
  if (peek.createdAt && Date.now() - peek.createdAt > 600000) {
    await store.fetch(new Request('https://do/consume', { method: 'POST', headers: JSON_HEADERS, body: JSON.stringify({ ticket: id }) }));
    return json({ error: 'enroll_expired' }, 410);
  }
  if (peek.status !== 'approved') return json({ status: 'pending' }, 202);
  // Initiator binding: only the client that started enrollment knows this secret.
  if ((await sha256(clientSecret)) !== peek.clientHash) return json({ error: 'forbidden' }, 403);
  // Atomic consume: the Durable Object deletes the ticket inside one storage
  // transaction, so a same-instant double-complete can only succeed once.
  const consumed = (await (await store.fetch(new Request('https://do/consume', {
    method: 'POST', headers: JSON_HEADERS, body: JSON.stringify({ ticket: id })
  }))).json());
  if (!consumed.consumed) return json({ error: 'enroll_expired' }, 410);
  const token = randToken(32);
  await env.ROUT_KV.put(`device:${await sha256(token)}`, JSON.stringify({ created: Date.now() }));
  return json({ device_token: token }); // returned exactly once, at enrollment
}

function b64ToBytes(b64) {
  const bin = atob(b64);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

function pcmToWav(pcm, sampleRate = 24000) {
  const header = new ArrayBuffer(44);
  const v = new DataView(header);
  const wstr = (o, t) => { for (let i = 0; i < t.length; i++) v.setUint8(o + i, t.charCodeAt(i)); };
  wstr(0, 'RIFF'); v.setUint32(4, 36 + pcm.length, true); wstr(8, 'WAVE');
  wstr(12, 'fmt '); v.setUint32(16, 16, true); v.setUint16(20, 1, true); v.setUint16(22, 1, true);
  v.setUint32(24, sampleRate, true); v.setUint32(28, sampleRate * 2, true); v.setUint16(32, 2, true); v.setUint16(34, 16, true);
  wstr(36, 'data'); v.setUint32(40, pcm.length, true);
  const out = new Uint8Array(44 + pcm.length);
  out.set(new Uint8Array(header), 0); out.set(pcm, 44);
  return out;
}

async function handleSpeak(request, env) {
  const body = await request.json().catch(() => null);
  if (!body || typeof body.text !== 'string' || !body.text.trim()) {
    return json({ error: 'invalid_request' }, 400);
  }
  const text = body.text.slice(0, 2000);
  const voice = /^[A-Za-z]{1,24}$/.test(String(body.voice || '')) ? String(body.voice) : 'Kore';
  const model = env.TTS_MODEL || 'gemini-3.8-flash-tts';
  const upstream = await fetch(
    `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(model)}:generateContent`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'x-goog-api-key': env.GEMINI_API_KEY },
      body: JSON.stringify({
        contents: [{ parts: [{ text }] }],
        generationConfig: {
          responseModalities: ['AUDIO'],
          speechConfig: { voiceConfig: { prebuiltVoiceConfig: { voiceName: voice } } }
        }
      }),
      signal: AbortSignal.timeout(25000)
    });
  const data = await upstream.json().catch(() => ({}));
  if (!upstream.ok) return json({ error: 'upstream_error' }, 502);
  const parts = (((data.candidates || [])[0] || {}).content || {}).parts || [];
  const audio = parts.map(p => (p.inlineData && p.inlineData.data) || '').find(d => d);
  if (!audio) return json({ error: 'no_audio' }, 502);
  const wav = pcmToWav(b64ToBytes(audio));
  return new Response(wav, { headers: { 'Content-Type': 'audio/wav', 'Cache-Control': 'no-store' } });
}

async function handleRevoke(request, env, token) {
  // Device self-revoke (Bearer) OR owner revoke via X-Owner-Code header.
  const ownerHeader = request.headers.get('x-owner-code') || '';
  const isOwner = env.OWNER_CODE && timingSafeEqual(ownerHeader, env.OWNER_CODE);
  if (!isOwner && !(await deviceValid(env, token))) return json({ error: 'unauthorized' }, 401);
  if (isOwner) {
    const body = await request.json().catch(() => ({}));
    if (body.all === true) {
      let cursor; let n = 0;
      do {
        const page = await env.ROUT_KV.list({ prefix: 'device:', cursor });
        for (const k of page.keys) { await env.ROUT_KV.delete(k.name); n++; }
        cursor = page.list_complete ? undefined : page.cursor;
      } while (cursor);
      return json({ revoked: true, count: n });
    }
    const victim = String(body.token || '');
    if (!/^[0-9a-f]{64}$/.test(victim)) return json({ error: 'invalid_request' }, 400);
    await env.ROUT_KV.delete(`device:${await sha256(victim)}`);
    return json({ revoked: true });
  }
  await env.ROUT_KV.delete(`device:${await sha256(token)}`);
  return json({ revoked: true });
}

async function handleChat(request, env, token) {
  const body = await request.json().catch(() => null);
  if (!body || typeof body.input !== 'string' || !body.input.trim()) {
    return json({ error: 'invalid_request' }, 400);
  }
  const input = body.input.slice(0, 4000);
  const history = Array.isArray(body.messages) ? body.messages.slice(-20) : [];
  const contents = [];
  for (const m of history) {
    if (!m || typeof m.text !== 'string') continue;
    contents.push({ role: m.role === 'user' ? 'user' : 'model', parts: [{ text: String(m.text).slice(0, 2000) }] });
  }
  contents.push({ role: 'user', parts: [{ text: input }] });

  const model = env.GEMINI_MODEL || 'gemini-3.6-flash';
  const upstream = await fetch(
    `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(model)}:generateContent`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'x-goog-api-key': env.GEMINI_API_KEY },
      body: JSON.stringify({
        contents,
        systemInstruction: { parts: [{ text: 'You are ROUT, a concise personal assistant. The user speaks Hindi, Hinglish and English. Reply in the same language the user used. Keep answers short and spoken-friendly.' }] },
        generationConfig: { temperature: 0.6, maxOutputTokens: 512 }
      }),
      signal: AbortSignal.timeout(20000)
    });
  const data = await upstream.json().catch(() => ({}));
  if (!upstream.ok) return json({ error: 'upstream_error' }, 502);
  const parts = (((data.candidates || [])[0] || {}).content || {}).parts || [];
  return json({ reply: parts.map(p => p.text || '').join('').trim(), model });
}


/**
 * EnrollmentStore Durable Object: strongly consistent, single-threaded home for
 * one-use enrollment tickets. Ticket consume (complete) is atomic - the KV
 * get-then-delete race cannot happen here.
 */
export class EnrollmentStore {
  constructor(ctx, env) {
    this.ctx = ctx;
    this.env = env;
  }

  async fetch(request) {
    const url = new URL(request.url);
    const key = (t) => `ticket:${t}`;
    if (url.pathname === '/start' && request.method === 'POST') {
      const { ticket, clientHash } = await request.json();
      await this.ctx.storage.put(key(ticket), { status: 'pending', clientHash, createdAt: Date.now() });
      return json({ ok: true });
    }
    if (url.pathname === '/get' && request.method === 'GET') {
      const rec = await this.ctx.storage.get(key(url.searchParams.get('ticket') || ''));
      return json({ rec: rec || null });
    }
    if (url.pathname === '/approve' && request.method === 'POST') {
      const { ticket } = await request.json();
      const rec = await this.ctx.storage.get(key(ticket));
      if (!rec) return json({ ok: false, reason: 'missing' }, 410);
      await this.ctx.storage.put(key(ticket), { ...rec, status: 'approved' });
      return json({ ok: true });
    }
    if (url.pathname === '/consume' && request.method === 'POST') {
      // Atomic read-modify-delete inside one storage transaction.
      const { ticket } = await request.json();
      return await this.ctx.storage.transaction(async () => {
        const rec = await this.ctx.storage.get(key(ticket));
        if (!rec) return json({ consumed: false, reason: 'missing' }, 410);
        await this.ctx.storage.delete(key(ticket));
        return json({ consumed: true, rec });
      });
    }
    return json({ error: 'not_found' }, 404);
  }
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    try {
      if (request.method === 'GET' && url.pathname === '/health') {
        return json({
          status: 'ok', service: 'rout-backend-worker',
          model: env.GEMINI_MODEL || 'gemini-3.6-flash',
          keyConfigured: Boolean(env.GEMINI_API_KEY),
          time: new Date().toISOString()
        });
      }
      if (request.method === 'POST' && url.pathname === '/v1/enroll/start') {
        return await handleEnrollStart(request, env);
      }
      if ((request.method === 'GET' || request.method === 'POST') && url.pathname === '/approve') {
        return await handleApprove(request, url, env);
      }
      if (request.method === 'POST' && url.pathname === '/v1/enroll/complete') {
        return await handleEnrollComplete(request, env);
      }
      if (request.method === 'POST' && url.pathname === '/v1/revoke') {
        const token = (request.headers.get('authorization') || '').replace(/^Bearer\s+/i, '');
        return await handleRevoke(request, env, token);
      }
      if (request.method === 'POST' && url.pathname === '/v1/chat') {
        if (!env.GEMINI_API_KEY) return json({ error: 'ai_not_configured' }, 503);
        const token = (request.headers.get('authorization') || '').replace(/^Bearer\s+/i, '');
        if (!(await deviceValid(env, token))) return json({ error: 'unauthorized' }, 401);
        const limit = parseInt(env.RATE_LIMIT_PER_MINUTE || '30', 10);
        if (await rateLimited(env.ROUT_KV, await sha256(token), limit)) {
          return json({ error: 'rate_limited' }, 429);
        }
        return await handleChat(request, env);
      }
      if (request.method === 'POST' && url.pathname === '/v1/speak') {
        if (!env.GEMINI_API_KEY) return json({ error: 'ai_not_configured' }, 503);
        const token = (request.headers.get('authorization') || '').replace(/^Bearer\s+/i, '');
        if (!(await deviceValid(env, token))) return json({ error: 'unauthorized' }, 401);
        const limit = parseInt(env.RATE_LIMIT_PER_MINUTE || '30', 10);
        if (await rateLimited(env.ROUT_KV, 'speak:' + await sha256(token), limit)) {
          return json({ error: 'rate_limited' }, 429);
        }
        return await handleSpeak(request, env);
      }
      return json({ error: 'not_found' }, 404);
    } catch (e) {
      const timeout = e && (e.name === 'TimeoutError' || e.name === 'AbortError');
      return json({ error: timeout ? 'upstream_timeout' : 'server_error' }, timeout ? 504 : 500);
    }
  }
};

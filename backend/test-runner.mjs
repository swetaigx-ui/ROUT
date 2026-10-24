// ROUT worker test harness: in-memory KV stub + direct fetch calls.
import worker, { EnrollmentStore } from './worker.mjs';
import assert from 'node:assert';

function mkKV() {
  const m = new Map();
  return {
    async get(k) { return m.has(k) ? m.get(k) : null; },
    async put(k, v) { m.set(k, v); },
    async delete(k) { m.delete(k); },
    async list({ prefix = '', cursor } = {}) {
      const keys = [...m.keys()].filter(k => k.startsWith(prefix)).map(name => ({ name }));
      return { keys, list_complete: true, cursor: undefined };
    },
    _map: m,
  };
}
async function sha256hex(t) {
  const d = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(t));
  return [...new Uint8Array(d)].map(x => x.toString(16).padStart(2, '0')).join('');
}
// In-memory Durable Object shim: real EnrollmentStore class over Map storage.
function mkEnrollShim() {
  const m = new Map();
  const storage = {
    get: async (k) => (m.has(k) ? m.get(k) : undefined),
    put: async (k, v) => { m.set(k, v); },
    delete: async (k) => { m.delete(k); },
    transaction: async (fn) => fn(),
  };
  const store = new EnrollmentStore({ storage }, {});
  return { fetch: (req) => store.fetch(req), _map: m };
}
const mkEnv = (over = {}) => ({
  ROUT_KV: mkKV(),
  GEMINI_API_KEY: 'test-gemini-key',
  OWNER_CODE: 'test-owner-code',
  RATE_LIMIT_PER_MINUTE: '30',
  __enrollShim: mkEnrollShim(),
  ...over,
});
const J = { 'content-type': 'application/json' };
const post = (u, body, h = {}) => new Request(u, { method: 'POST', headers: { ...J, ...h }, body: JSON.stringify(body) });

// stub Gemini upstream
const realFetch = globalThis.fetch;
globalThis.fetch = async (url, init) => {
  if (String(url).includes('generativelanguage.googleapis.com')) {
    const wantsAudio = String((init && init.body) || '').includes('AUDIO');
    const part = wantsAudio ? { inlineData: { data: Buffer.from('fake-pcm-data-1234').toString('base64'), mimeType: 'audio/L16;rate=24000' } } : { text: 'Namaste! Main ROUT hoon.' };
    return new Response(JSON.stringify({ candidates: [{ content: { parts: [part] } }] }), { status: 200, headers: J });
  }
  if (String(url).includes('oauth2.googleapis.com/token')) {
    const email = globalThis.__oauthEmail || 'owner@example.com';
    const b64 = (o) => Buffer.from(JSON.stringify(o)).toString('base64url');
    const idt = b64({alg:'none'}) + '.' + b64({ iss: 'https://accounts.google.com', aud: 'cid', email, email_verified: globalThis.__oauthVerified !== false }) + '.x';
    return new Response(JSON.stringify({ id_token: idt }), { status: 200, headers: J });
  }
  return realFetch(url, init);
};

async function enrollDevice(env, { ownerCode = 'test-owner-code', device = 'TestPhone', clientSecret = 'app-secret-' + Math.random() } = {}) {
  const r1 = await worker.fetch(post('https://t/v1/enroll/start', { device_name: device, client_hash: await sha256hex(clientSecret) }), env);
  assert.equal(r1.status, 200, 'enroll/start 200');
  const { enroll_id, approve_url, expires_in } = await r1.json();
  assert.ok(enroll_id && approve_url.includes(enroll_id) && expires_in === 600);

  // approval page must require owner code
  const page = await worker.fetch(new Request(approve_url), env);
  const html = await page.text();
  assert.ok(html.includes('owner_code'), 'approve page asks for owner code');
  assert.ok(!html.includes('confirm=yes'), 'no GET confirm shortcut');

  if (ownerCode !== null) {
    const fd = new FormData();
    fd.append('id', enroll_id);
    fd.append('owner_code', ownerCode);
    const rA = await worker.fetch(new Request(approve_url, { method: 'POST', body: fd }), env);
    const approvedHtml = await rA.text();
    assert.ok(approvedHtml.includes('Device approved'), 'owner approval succeeds');
  }

  const rC = await worker.fetch(post('https://t/v1/enroll/complete', { enroll_id, client_secret: clientSecret }), env);
  const body = await rC.json();
  return { enroll_id, approve_url, completeStatus: rC.status, body, clientSecret };
}

// 1. health
{
  const r = await worker.fetch(new Request('https://t/health'), mkEnv());
  const b = await r.json();
  assert.equal(b.status, 'ok'); assert.equal(b.keyConfigured, true);
  console.log('ok health');
}

// 2. happy path enroll -> chat -> revoke -> 401
{
  const env = mkEnv();
  const { completeStatus, body } = await enrollDevice(env);
  assert.equal(completeStatus, 200); assert.ok(body.device_token);
  const tok = body.device_token;
  // replay: ticket consumed
  const again = await worker.fetch(post('https://t/v1/enroll/complete', { enroll_id: (await enrollDevice(env, { ownerCode: null })).enroll_id, client_secret: 'x' }), env);
  // (fresh unapproved ticket) -> pending
  assert.equal(again.status, 202);
  // chat without token
  const noTok = await worker.fetch(post('https://t/v1/chat', { input: 'hi' }), env);
  assert.equal(noTok.status, 401);
  // chat with token
  const chat = await worker.fetch(post('https://t/v1/chat', { input: 'hello', messages: [{ role: 'user', text: 'x' }] }, { authorization: 'Bearer ' + tok }), env);
  assert.equal(chat.status, 200);
  assert.ok((await chat.json()).reply.includes('ROUT'));
  // revoke
  const rv = await worker.fetch(post('https://t/v1/revoke', {}, { authorization: 'Bearer ' + tok }), env);
  assert.equal(rv.status, 200);
  const after = await worker.fetch(post('https://t/v1/chat', { input: 'hi' }, { authorization: 'Bearer ' + tok }), env);
  assert.equal(after.status, 401, 'revoked token rejected');
  console.log('ok enroll-chat-revoke');
}

// 3. wrong owner code cannot approve; ticket stays pending; no complete
{
  const env = mkEnv();
  const r1 = await worker.fetch(post('https://t/v1/enroll/start', { device_name: 'AttackerPhone', client_hash: await sha256hex('atk') }), env);
  const { enroll_id, approve_url } = await r1.json();
  const fd = new FormData(); fd.append('id', enroll_id); fd.append('owner_code', 'wrong');
  const rA = await worker.fetch(new Request(approve_url, { method: 'POST', body: fd }), env);
  assert.ok((await rA.text()).includes('Wrong owner code'));
  const rC = await worker.fetch(post('https://t/v1/enroll/complete', { enroll_id, client_secret: 'atk' }), env);
  assert.equal(rC.status, 202, 'unapproved ticket stays pending');
  console.log('ok owner-code gate');
}

// 4. missing OWNER_CODE config: approval always fails closed
{
  const env = mkEnv({ OWNER_CODE: undefined });
  const r1 = await worker.fetch(post('https://t/v1/enroll/start', { client_hash: await sha256hex('s') }), env);
  const { enroll_id, approve_url } = await r1.json();
  const fd = new FormData(); fd.append('id', enroll_id); fd.append('owner_code', 'anything');
  const rA = await worker.fetch(new Request(approve_url, { method: 'POST', body: fd }), env);
  assert.ok((await rA.text()).includes('Wrong owner code'), 'fails closed without configured code');
  console.log('ok fail-closed');
}

// 5. CSRF: form id must match URL id
{
  const env = mkEnv();
  const r1 = await worker.fetch(post('https://t/v1/enroll/start', { client_hash: await sha256hex('s') }), env);
  const { approve_url } = await r1.json();
  const fd = new FormData(); fd.append('id', 'forged-other-ticket'); fd.append('owner_code', 'test-owner-code');
  const rA = await worker.fetch(new Request(approve_url, { method: 'POST', body: fd }), env);
  assert.ok((await rA.text()).includes('Invalid approval form'));
  console.log('ok csrf');
}

// 6. rate limit on chat
{
  const env = mkEnv({ RATE_LIMIT_PER_MINUTE: '2' });
  const { body } = await enrollDevice(env);
  const h = { authorization: 'Bearer ' + body.device_token };
  assert.equal((await worker.fetch(post('https://t/v1/chat', { input: 'a' }, h), env)).status, 200);
  assert.equal((await worker.fetch(post('https://t/v1/chat', { input: 'a' }, h), env)).status, 200);
  assert.equal((await worker.fetch(post('https://t/v1/chat', { input: 'a' }, h), env)).status, 429);
  console.log('ok rate-limit');
}

// 7. approve rate limit (brute force)
{
  const env = mkEnv();
  const r1 = await worker.fetch(post('https://t/v1/enroll/start', { client_hash: await sha256hex('s') }), env);
  const { approve_url } = await r1.json();
  let last;
  for (let i = 0; i < 12; i++) {
    const fd = new FormData(); fd.append('id', new URL(approve_url).searchParams.get('id')); fd.append('owner_code', 'guess' + i);
    last = await worker.fetch(new Request(approve_url, { method: 'POST', body: fd }), env);
  }
  assert.ok((await last.text()).includes('Too many attempts'), 'approve brute force limited');
  console.log('ok approve-rate-limit');
}

// 8. no GEMINI key -> 503 honest
{
  const env = mkEnv({ GEMINI_API_KEY: undefined });
  const { body } = await enrollDevice(env);
  const r = await worker.fetch(post('https://t/v1/chat', { input: 'hi' }, { authorization: 'Bearer ' + body.device_token }), env);
  assert.equal(r.status, 503);
  assert.equal((await r.json()).error, 'ai_not_configured');
  console.log('ok ai-not-configured');
}

console.log('ALL WORKER TESTS PASSED');

// 9. initiator binding: approved ticket + wrong/missing client secret -> 403
{
  const env = mkEnv();
  const r1 = await worker.fetch(post('https://t/v1/enroll/start', { client_hash: await sha256hex('real-app-secret') }), env);
  const { enroll_id, approve_url } = await r1.json();
  const fd = new FormData(); fd.append('id', enroll_id); fd.append('owner_code', 'test-owner-code');
  await worker.fetch(new Request(approve_url, { method: 'POST', body: fd }), env);
  // attacker who saw the approve URL tries to complete
  const atk = await worker.fetch(post('https://t/v1/enroll/complete', { enroll_id, client_secret: 'attacker-guess' }), env);
  assert.equal(atk.status, 403, 'phished ticket cannot complete');
  const none = await worker.fetch(post('https://t/v1/enroll/complete', { enroll_id }), env);
  assert.equal(none.status, 403, 'missing secret cannot complete');
  // real initiator completes
  const ok = await worker.fetch(post('https://t/v1/enroll/complete', { enroll_id, client_secret: 'real-app-secret' }), env);
  assert.equal(ok.status, 200);
  console.log('ok initiator-binding');
}

// 10. owner revoke: any token by owner code, and revoke-all
{
  const env = mkEnv();
  const d1 = await enrollDevice(env);
  const d2 = await enrollDevice(env);
  // non-owner cannot revoke others
  const bad = await worker.fetch(post('https://t/v1/revoke', { token: d1.body.device_token }, { 'x-owner-code': 'nope' }), env);
  assert.equal(bad.status, 401);
  // owner revokes d1 by token
  const rv = await worker.fetch(post('https://t/v1/revoke', { token: d1.body.device_token }, { 'x-owner-code': 'test-owner-code' }), env);
  assert.equal(rv.status, 200);
  assert.equal((await worker.fetch(post('https://t/v1/chat', { input: 'hi' }, { authorization: 'Bearer ' + d1.body.device_token }), env)).status, 401);
  // d2 still valid
  assert.equal((await worker.fetch(post('https://t/v1/chat', { input: 'hi' }, { authorization: 'Bearer ' + d2.body.device_token }), env)).status, 200);
  // revoke all
  const all = await worker.fetch(post('https://t/v1/revoke', { all: true }, { 'x-owner-code': 'test-owner-code' }), env);
  assert.equal((await all.json()).count >= 1, true);
  assert.equal((await worker.fetch(post('https://t/v1/chat', { input: 'hi' }, { authorization: 'Bearer ' + d2.body.device_token }), env)).status, 401);
  console.log('ok owner-revoke');
}

// 11. cross-site approve POST rejected via Sec-Fetch-Site / Origin
{
  const env = mkEnv();
  const r1 = await worker.fetch(post('https://t/v1/enroll/start', { client_hash: await sha256hex('z') }), env);
  const { enroll_id, approve_url } = await r1.json();
  const fd = new FormData(); fd.append('id', enroll_id); fd.append('owner_code', 'test-owner-code');
  const xs = await worker.fetch(new Request(approve_url, { method: 'POST', body: fd, headers: { 'sec-fetch-site': 'cross-site' } }), env);
  assert.ok((await xs.text()).includes('Invalid request origin'));
  const fd2 = new FormData(); fd2.append('id', enroll_id); fd2.append('owner_code', 'test-owner-code');
  const badOrigin = await worker.fetch(new Request(approve_url, { method: 'POST', body: fd2, headers: { origin: 'https://evil.example' } }), env);
  assert.ok((await badOrigin.text()).includes('Invalid request origin'));
  // ticket still pending (not approved by rejected posts)
  const rC = await worker.fetch(post('https://t/v1/enroll/complete', { enroll_id, client_secret: 'z' }), env);
  assert.equal(rC.status, 202);
  console.log('ok origin-checks');
}

console.log('ROUND-2 TESTS PASSED');

// 12. atomic consume: concurrent completes - exactly one wins
{
  const env = mkEnv();
  const r1 = await worker.fetch(post('https://t/v1/enroll/start', { client_hash: await sha256hex('conc') }), env);
  const { enroll_id, approve_url } = await r1.json();
  const fd = new FormData(); fd.append('id', enroll_id); fd.append('owner_code', 'test-owner-code');
  await worker.fetch(new Request(approve_url, { method: 'POST', body: fd }), env);
  const results = await Promise.all([...Array(8)].map(() =>
    worker.fetch(post('https://t/v1/enroll/complete', { enroll_id, client_secret: 'conc' }), env)));
  const codes = results.map(r => r.status);
  assert.equal(codes.filter(c => c === 200).length, 1, 'exactly one complete issues a token');
  assert.equal(codes.filter(c => c === 410).length, 7, 'the rest get 410');
  const tokens = new Set();
  for (const r of results) if (r.status === 200) tokens.add((await r.json()).device_token);
  assert.equal(tokens.size, 1);
  console.log('ok atomic-consume');
}

// 13. stale approved ticket cannot complete; gets consumed
{
  const env = mkEnv();
  const r1 = await worker.fetch(post('https://t/v1/enroll/start', { client_hash: await sha256hex('old') }), env);
  const { enroll_id, approve_url } = await r1.json();
  const fd = new FormData(); fd.append('id', enroll_id); fd.append('owner_code', 'test-owner-code');
  await worker.fetch(new Request(approve_url, { method: 'POST', body: fd }), env);
  // backdate the approved ticket 11 minutes
  const k = 'ticket:' + enroll_id;
  const rec = env.__enrollShim._map.get(k);
  env.__enrollShim._map.set(k, { ...rec, createdAt: Date.now() - 11 * 60 * 1000 });
  const r = await worker.fetch(post('https://t/v1/enroll/complete', { enroll_id, client_secret: 'old' }), env);
  assert.equal(r.status, 410, 'stale ticket gets 410 at complete');
  assert.equal(env.__enrollShim._map.has(k), false, 'stale ticket consumed');
  console.log('ok stale-ticket-ttl');
}
console.log('ROUND-3 TESTS PASSED');

// /v1/speak: auth required, WAV out
{
  const env = mkEnv();
  const tok = (await enrollDevice(env)).body.device_token;
  const noTok = await worker.fetch(post('https://t/v1/speak', { text: 'hi' }), env);
  assert.equal(noTok.status, 401, 'speak requires device token');
  const r = await worker.fetch(post('https://t/v1/speak', { text: 'Namaste' }, { authorization: 'Bearer ' + tok }), env);
  assert.equal(r.status, 200, 'speak 200');
  assert.equal(r.headers.get('content-type'), 'audio/wav');
  const buf = new Uint8Array(await r.arrayBuffer());
  assert.equal(String.fromCharCode(...buf.slice(0, 4)), 'RIFF', 'wav header');
  assert.equal(buf.length, 44 + 'fake-pcm-data-1234'.length, 'wav wraps pcm');
  const bad = await worker.fetch(post('https://t/v1/speak', {}, { authorization: 'Bearer ' + tok }), env);
  assert.equal(bad.status, 400, 'speak invalid body');
  console.log('ok speak');
}

// Owner Google OAuth approval
{
  const env = mkEnv({ GOOGLE_CLIENT_ID: 'cid', GOOGLE_CLIENT_SECRET: 'sec', STATE_SECRET: 'state-secret', OWNER_EMAIL: 'owner@example.com' });
  const cs = 'oauth-secret-' + Math.random();
  const r1 = await worker.fetch(post('https://t/v1/enroll/start', { client_hash: await sha256hex(cs) }), env);
  const { enroll_id } = await r1.json();
  const pageHtml = await (await worker.fetch(new Request('https://t/approve?id=' + enroll_id), env)).text();
  assert.ok(pageHtml.includes('/oauth/start?id=' + enroll_id), 'google button shown');
  const st = await worker.fetch(new Request('https://t/oauth/start?id=' + enroll_id), env);
  assert.equal(st.status, 302);
  const loc = new URL(st.headers.get('location'));
  assert.equal(loc.host, 'accounts.google.com');
  assert.equal(loc.searchParams.get('redirect_uri'), 'https://t/oauth/callback');
  const state = loc.searchParams.get('state');
  const cookie = st.headers.get('set-cookie').split(';')[0];
  const cb = (extra = {}, stt = state, ck = cookie) => worker.fetch(new Request('https://t/oauth/callback?code=abc&state=' + encodeURIComponent(stt), { headers: { cookie: ck, ...extra } }), env);
  // no cookie -> rejected, still pending
  assert.ok((await (await cb({}, state, '')).text()).includes('could not be verified'));
  // forged state -> rejected
  assert.ok((await (await cb({}, state.slice(0, -2) + 'zz')).text()).includes('could not be verified'));
  // wrong google account -> rejected
  globalThis.__oauthEmail = 'attacker@example.com';
  assert.ok((await (await cb()).text()).includes('not the ROUT owner'));
  const pend = await worker.fetch(post('https://t/v1/enroll/complete', { enroll_id, client_secret: cs }), env);
  assert.equal(pend.status, 202, 'still pending after rejections');
  // unverified email -> rejected
  globalThis.__oauthEmail = 'owner@example.com'; globalThis.__oauthVerified = false;
  assert.ok((await (await cb()).text()).includes('not the ROUT owner'));
  // owner -> approved
  globalThis.__oauthVerified = true;
  assert.ok((await (await cb()).text()).includes('Device approved'));
  const done = await worker.fetch(post('https://t/v1/enroll/complete', { enroll_id, client_secret: cs }), env);
  assert.equal(done.status, 200);
  assert.ok((await done.json()).device_token);
  // not configured -> no button, oauth start refuses, owner code still works
  const env2 = mkEnv();
  const r2 = await worker.fetch(post('https://t/v1/enroll/start', { client_hash: await sha256hex('x') }), env2);
  const id2 = (await r2.json()).enroll_id;
  assert.ok(!(await (await worker.fetch(new Request('https://t/approve?id=' + id2), env2)).text()).includes('/oauth/start'));
  assert.ok((await (await worker.fetch(new Request('https://t/oauth/start?id=' + id2), env2)).text()).includes('not configured'));
  console.log('ok owner-oauth');
}

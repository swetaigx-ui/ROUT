# ROUT backend

Secure AI proxy for the ROUT Android app. The provider API key lives **only** on this
server (environment variable). The Android app never contains a key.

## Endpoints

- `GET /health` - service status, model name, whether the key is configured.
- `POST /v1/chat` - `{ "input": "...", "messages": [{role, text}, ...] }` -> `{ "reply": "..." }`
- `POST /v1/speak` - `{ "text": "...", "voice"?: "Kore" }` -> `audio/wav` (Gemini TTS, natural voice; same device auth + rate limit as chat)

Built-in: input validation, request size limit, per-IP rate limit, upstream timeout,
CORS allowlist, structured JSON errors.

## Run locally

```
cp .env.example .env   # fill GEMINI_API_KEY
node server.js         # Node 18+
```

## Free deployment options (verify current terms before choosing)

- A free Node host (e.g. Render/Railway/Fly free tiers) - set `GEMINI_API_KEY` in the
  host's secret/environment settings, never in code.
- After deploy, open `https://YOUR-HOST/health` and confirm `status: ok` and
  `keyConfigured: true`, then paste the base URL into ROUT Settings -> Backend URL.

## Provider

Google Gemini API (`gemini-2.5-flash` by default, override with `GEMINI_MODEL`).
Free-tier keys come from Google AI Studio. Free tier has rate limits and is not
unlimited - check current terms: https://ai.google.dev/gemini-api/docs/rate-limits

## Credential issuance binding

- `/v1/enroll/start` requires `client_hash` = SHA-256 of a per-attempt secret the app generates. `/v1/enroll/complete` must present that secret; a person who only sees the approval URL (including a phisher) gets 403.
- Ticket is consumed (deleted) before the device token is issued; replays get 410.
- Enrollment ticket state lives in the EnrollmentStore Durable Object (strongly consistent, single-threaded). Ticket consume happens inside one storage transaction - a same-instant double-complete is impossible: exactly one caller gets a token, the rest get 410 (tested with 8 concurrent completes).
- Free-plan verified (developers.cloudflare.com/durable-objects/platform/pricing): Durable Objects are available on Workers Free with the SQLite storage backend (5M rows read/day, 100k rows written/day free) - wrangler.toml declares `new_sqlite_classes` accordingly.
- Owner approval UX: Cloudflare Access (Zero Trust Free plan, $0 forever for under 50 users - cloudflare.com/plans/zero-trust-services) with Google sign-in restricted to the owner's email in front of /approve. OWNER_CODE remains as fail-closed fallback.
- `/approve` POST hardening: rejects cross-site posts (Sec-Fetch-Site / Origin mismatch). The owner code is never logged, stored, or echoed - compared in memory, constant-time.
- Revocation: a device self-revokes with its Bearer token. The owner revokes any token, or all devices, with `X-Owner-Code` (`POST /v1/revoke` with `{token}` or `{all:true}`).
- Legacy `server.js` (x-rout-token model) removed - worker.js is the only backend.

## Owner approval UX (deployment choice)

Preferred: put `/approve` behind Cloudflare Access (Zero Trust free tier) with Google sign-in restricted to the owner's email - no typed code at all. Fallback: OWNER_CODE field as implemented; the code lives in the owner's vault and can be autofilled by a password manager. The code is never sent over chat.

## Enrollment security model (owner-authenticated)

- `POST /v1/enroll/start` is open, but a ticket alone grants nothing. Ticket: 192-bit random, one-use, 10-minute TTL (KV expirationTtl).
- `GET/POST /approve?id=<ticket>` requires the OWNER_CODE (Worker secret, set via `wrangler secret put OWNER_CODE`, value stored in the owner's vault - never in repo, app, or chat). Wrong/missing code -> approval refused, ticket stays pending. Missing OWNER_CODE config fails closed.
- CSRF: the approval form POST must carry back the same ticket id as the URL; the owner code is the anti-forgery proof. Approval is POST-only (no GET confirm).
- Brute force: approval attempts rate-limited to 10/min per IP; chat to RATE_LIMIT_PER_MINUTE per device.
- `POST /v1/enroll/complete` returns the device token exactly once, then the ticket is deleted (replay -> 410/202).
- `POST /v1/revoke` (Bearer) deletes that device's hash; only SHA-256 hashes of device tokens are stored.
- Tests (backend/test-runner.mjs, 8 scenarios): health, enroll->chat->revoke->401, wrong owner code, fail-closed, CSRF, chat rate limit, approve brute-force limit, honest 503 without provider key. All passing.

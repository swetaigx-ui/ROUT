# ROUT 0.7.28 - voice fix and device control engine

Status: unit-tested only. Never run on a phone. Accessibility effects (taps, WhatsApp Send tap, scroll, like) are unverified on a real device.

## Voice (LIVE) fixes
- LIVE transcripts arrive in Devanagari; HindiNormal now maps command vocabulary (apps, reels, click, like, scroll, search, status, flashlight, wifi, bluetooth, hotspot, volume, brightness, DND, home/back/up/down).
- YouTube search sends only the search words (no "search karo" in the query).
- New routes: scroll, like, "X par click karo", WhatsApp status view, WhatsApp status post (asks what/who; never posts without content), WhatsApp "name + message".
- Like and WhatsApp send always show a confirmation (name, number, exact words). Saved-name gate unchanged; a dropped LIVE command now shows a chat line.

## Control engine (package control/)
- ControlModel: 35 action types, risk levels, strict PlanValidator (allowlist, max 8 actions, http/https only, settings panels exclude security/privacy/location/accessibility).
- ControlEngine: one run at a time, duplicate request-id protection, step timeout, STOP, per-action confirmation for HIGH risk, honest verified vs dispatched counts.
- AndroidExecutor: real handlers. PlanClient: POST /v1/control/plan, sends only command text + requestId; failures are shown, never turned into success.
- Home screen ControlStrip shows state, message, last log lines, STOP/OK.

## Backend
backend/worker.js has /v1/control/plan (device token, rate limit, plan validation). SOURCE ONLY: not deployed. Until deployed the app reports "endpoint abhi deploy nahi hua".

## Manifest
Added ANSWER_PHONE_CALLS (no runtime prompt yet; call.end fails honestly until the user approves a request flow). No Gradle dependencies added.

## Accessibility setup
Settings > Accessibility > ROUT > enable. Needed for tap/scroll/like/status actions.

## Not done
- file.open / file.rename / file.delete (feasible via SAF/MediaStore; not implemented)
- workflow.execute (not implemented)
- Dismissing a single named notification (only "all" with confirmation exists)
- call.end needs ANSWER_PHONE_CALLS grant flow
- Devanagari contact names are not transliterated to Latin contacts
- Cloud screen transfer, notification reply, background mic and wake word stay OFF (no grant)

## Tests
Full unit suite: 397 tests, 0 failures, 0 skipped (includes SpokenLive0729Test 9, ControlEngineTest 14).

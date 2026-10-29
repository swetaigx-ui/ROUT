# ROUT 0.7.31 - voice fix and device control engine

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

## Added in 0.7.29
- workflow.execute: runs an EXISTING saved routine by exact name through the normal router (each send/call/like step still asks its own confirmation; routine halts at the first one).
- notification.dismiss_app: dismisses dismissible notifications of ONE uniquely named app, after confirmation. Ambiguous names are refused.
- Devanagari contact names are romanised only to look up Latin-saved contacts; several hits stay ambiguous and sends still show name + number.
- Backend /v1/control/plan is DEPLOYED (Cloudflare Worker rout-backend); 0.7.29 worker spec adds the two actions above.

## Added in 0.7.31
- Routine names for workflow.execute match EXACTLY (saved name, optionally wrapped in filler words like "routine"/"chalao"); no fuzzy guessing, ambiguous or unknown names fail honestly.
- Router-level tests: a routine halted at a confirmation reports NOT complete, arms exactly one confirmation and sends nothing.
- AI-planned commands: the worker now returns the provider status. A 429 RESOURCE_EXHAUSTED (Gemini quota) is shown to the user as "AI plan service busy (quota)"; local deterministic commands keep working. IMPORTANT: "upstream_error" is a provider/service failure, NOT a validation rejection. Quota reset time is unknown.
- File rename/delete confirmation dialog rendered and tested (long names, Cancel, back).

## Added in 0.7.30
- file.open / file.rename / file.delete via an Activity-hosted system file picker (FilePickActivity): the USER picks the file; rename/delete then show the real file name in a dialog and act only on Yes (plus the engine confirmation). Works only where the storage provider supports rename/delete; closing the picker or dialog cancels with nothing changed. ROUT never searches storage on its own. Background launch can be blocked by Android; then the step fails honestly. Unit-tested with a fake launcher; the real picker/dialog is unverified on a phone.
- workflow.execute: a routine halted at a confirmation now reports NOT complete (waiting step and later steps did not run); the engine halts remaining plan steps.
- call.end message no longer sends the user to a non-existent permission screen.

## Not done (old note, superseded above)
- (file ops now implemented, see above) previously: file.open / file.rename / file.delete: not implemented yet. Deletion/rename of files owned by other apps needs an Activity-hosted system consent dialog (MediaStore.createDeleteRequest / SAF picker); non-media documents are not searchable by name without a user-picked grant (Android restriction).
- call.end needs ANSWER_PHONE_CALLS grant flow
- Cloud screen transfer, notification reply, background mic and wake word stay OFF (no grant)

## Tests
Full unit suite: 397 tests, 0 failures, 0 skipped (includes SpokenLive0729Test 9, ControlEngineTest 14).

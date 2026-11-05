# ROUT - Personal AI Assistant (Android)

ROUT is a native Android personal assistant (Kotlin + Jetpack Compose) for Hindi,
Hinglish and English. Package: `com.rout.aiassistant`.

**Powered by Gyan.**

## What works fully on-device (no internet, no key)

- Open installed apps by name (Hindi/Hinglish/English): "YouTube kholo"
- Google/YouTube search and opening websites (opens browser; needs internet to load pages)
- Flashlight on/off, volume up/down/mute/max
- Date & time, battery status, network status
- Alarms and timers via the system Clock app
- Calendar, Maps navigation intents
- Notes, tasks, reminders (inexact alarms), long-term memory ("yaad rakho ...")
- Multi-step commands: "YouTube kholo aur AI tutorials search karo"
- Call/SMS: finds the contact, then always asks for your confirmation, and opens the
  native dialer/messaging app - ROUT never calls or sends secretly
- Full text input fallback; every voice feature also works typed

## Needs internet, no key needed

- Weather via Open-Meteo (free, keyless): real data, never invented

## New in 0.9.0 (MAX feature parity)

On-device, privacy-gated (off by default, opt-in in Settings):
- **Phone Dhundo**: ring/locate your phone by voice
- **Anti-theft**: SMS triggers from a trusted number (ring/lock/locate)
- **Intruder camera**: front-camera photo on wrong unlock attempts
- **App Lock**: lock selected apps with a PIN
- **Hand Magic**: gesture control via front camera (MediaPipe)
- **Screen text/screenshot to AI**: share screen content to ROUT, only when you choose
- **Offline speech-to-text**: Vosk small en-in model, fully on-device
- **Notification read/reply assistant**: reads/replies only after you opt in

Everything works, no faked data:
- **Translator**: 15+ Indian/global languages (ML Kit, on-device after model download)
- **Dynamic Island**: mini bar for now-playing/timers/voice, with Settings toggle
- **Morning Briefing**: weather + calendar + tasks + quote in Hinglish, on demand or scheduled
- **Smart Alarm**: wakes you within a smart window using sleep pattern + weather
- **3D Avatar**: animated on-device WebGL companion on the home screen
- **Code Studio**: write/run 40+ languages via the free Piston API (needs internet)
- **Scanner**: on-device OCR + document scan (ML Kit)
- **Video Editor**: trim, filters, crop, mute, speed, watermark (Media3 Transformer)
- **Voice calls**: "call karke baat karo" style continuous voice conversations
- **Live conversation commands**: answer/end calls, speaker toggle by voice
- **MAX-style home screen**: glass cards, quick actions, dynamic island, ask bar

## Setup-required states (honest, not faked)

- **ROUT AI answers** (general questions, explanations, code help): requires the ROUT
  backend you deploy (see `backend/`). The API key lives only on the server. Until a
  backend URL is set in Settings, AI answers show a clear setup-required state.
- **Media control** (play/pause/skip): needs notification access, enabled by you in
  system settings. ROUT does not read notification contents.
- **Hey ROUT wake phrase**: runs as a foreground listening session you start, with a
  visible notification. this build has no always-on background wake word
  (current implementation limit, not faked).
- **Brightness**: changing it directly needs Android's "Modify system settings"
  grant; otherwise ROUT opens the right settings page.
- **Floating orb**: needs the overlay permission you grant explicitly.

## Not in v1 (documented, not silently dropped)

- True real-time speech-to-speech streaming (uses Android STT -> ROUT -> TTS pipeline)
- Accessibility-based screen reading and cross-app UI automation
- Desktop companion app
- On-device wake-word keyword spotting model

## Architecture

- `app/` - Kotlin, Jetpack Compose, Room (notes/tasks/memory/history), DataStore,
  encrypted on-device storage for the optional developer key, foreground listening
  service (microphone type), floating orb service, media-session control.
- `backend/` - zero-dependency Node 18+ AI proxy with health check, rate limiting,
  input validation, request size limits, CORS allowlist, structured errors.
  Provider key via environment only. Default model `gemini-2.5-flash` (verified
  against https://ai.google.dev/gemini-api/docs/models), overridable.

## Build

Requires JDK 17+, Android SDK (platform 36), Gradle 8.14+.

```
gradle assembleRelease
```

Release builds expect a signing config via `gradle.properties` (never commit keys):

```
ROUT_STORE_FILE=/path/to/rout-release.keystore
ROUT_STORE_PASSWORD=...
ROUT_KEY_ALIAS=rout
ROUT_KEY_PASSWORD=...
```

## Privacy

No raw audio is stored. No screen capture. No analytics. Calls/messages require
on-screen confirmation. Clear history/notes/tasks/memory from Settings.

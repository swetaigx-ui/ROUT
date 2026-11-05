# ROUT MAX-features build - working state (agent notes, not committed)

## Environment (workspace box, persists)
- JDK17: ~/deps/jdk-17.0.21+9 ; Gradle: ~/deps/gradle-8.14.3 ; SDK: ~/android-sdk (platform 36, build-tools 36.0.0, platform-tools)
- Build script: ~/build.sh  (exports env incl. signing from vault file /home/sandbox/.vault/rout-release-signing-key-9c66a7/password)
  Usage: `~/build.sh assembleRelease` (foreground; ~70s incremental; first run needed several 115s slices)
- Keystore: /downloads/ROUT-release-signing-backup.keystore (alias "rout", vault login "ROUT release signing key", sandbox allowed)
- GitHub push token: /home/sandbox/.vault/github-rout-push-token-swetaigx-ui-dca245/password (login "GitHub ROUT push token (swetaigx-ui)")
- Drive account: swetaigx@gmail.com. Prior uploads: ROUT-<ver>-release.apk + ROUT-<ver>-source.tar.gz in root.
- Repo: ~/ROUT (github.com/swetaigx-ui/ROUT, public). HEAD was 0.8.2 vc62.

## Baseline: BUILD SUCCESSFUL (0.8.2 vc62 signed release APK) on 2026-11-05 ~04:32 IST.

## Mission
Add every MAX (com.max.ai v5.3) feature ROUT lacks (spec: /downloads/max-feature-spec-ea0a94c1.txt).
MAX UI/look replaces ROUT UI AFTER parent forwards Gyan's screen recording (pending). Until then: build feature layer with working minimal UI (liquid glass, below cutout, Powered by Gyan footer), don't do final styling.
Hard rules: every button works; no faked data; privacy-gated features DEFAULT OFF with in-app opt-in; free-only; no keys in repo/APK.

## Feature checklist
- [ ] Dynamic Island overlay (SYSTEM_ALERT_WINDOW + notification-driven)
- [ ] Hand Magic camera gesture control (MediaPipe hand_landmarker.task - download from Google; DEFAULT OFF)
- [ ] Phone Dhundo (clap/whistle/SMS find-phone + remote stop)
- [ ] Anti-theft via SMS (alarm/locate via SMS from another phone)
- [ ] Intruder camera (photo on failed unlock; needs DeviceAdmin)
- [ ] Smart Alarm (exact alarms, full-screen intent, boot reschedule) - extend existing ReminderReceiver/BootReceiver
- [ ] Call handling (ANSWER_PHONE_CALLS, READ_CALL_LOG; answer/end via telecom API)
- [ ] Translator (free: ML Kit translate on-device - free, no key)
- [ ] ML Kit Scanner (barcode + document via GmsBarcodeScanning/GmsDocumentScanning)
- [ ] Video Editor (OpenGL shaders - heavy; consider basic trim/filter via Media3 Transformer)
- [ ] Code Studio (write/run code + QS tile; run = Kotlin? Free approach: JS via embedded engine or save/share; keep honest)
- [ ] Mirror (front-camera mirror)
- [ ] Web-agent tools (screenshot pick via MediaProjection, page/file pick, report view) - extend VisionShare
- [ ] Offline STT (Vosk model download, English/Hindi small models; DEFAULT OFF?)
- [ ] 3D avatar view (three.js WebView asset)
- [ ] Morning briefing (BriefingPlayReceiver -> existing NewsService + TTS)
- [ ] Geo/health actions (geofence exists; add step/health? Health Connect free)
- [ ] App lock (UnlockActivity-style gate on app open, PIN in EncryptedSharedPreferences)
- [ ] Boot start (BootReceiver exists - verify)
- [ ] Notification read/reply (exists per memory - verify/extend)
- [ ] MAX-style UI reskin (BLOCKED: waiting for screen recording from parent)

## Existing relevant code
- service/BootReceiver.kt, ReminderReceiver.kt, BedtimeReceiver.kt, GeoReminders.kt, GeofenceManager/Receiver/Store
- service/RoutNotify.kt (notification listener?), RoutMediaListenerService, ReplyReceiver (notification reply)
- commands/ScreenControl.kt, control/ControlEngine.kt, VisionShare.kt (screenshot AI, OFF by default)
- ui/screens/*, ui/theme/Theme.kt, FloatingOrbService, RoutTileService
- commands/WeatherService.kt (Open-Meteo keyless), NewsClient/NewsService

## Progress log
- 2026-11-05 04:32: env ready, baseline 0.8.2 builds+signs. Reported milestone 1 to parent.
- 2026-11-05 05:25: BUILD GREEN 0.9.0 vc63 (arm64-only, 64MB). Committed 1d44f5a: protect bundle + settings "Security & protection" card + voice cmds + MainActivity app-lock hook + boot reschedule. Detached-build trick that works: `setsid bash -c '~/build.sh assembleRelease > /tmp/runN.log 2>&1; echo EXIT=$? >> /tmp/runN.log' </dev/null >/dev/null 2>&1 & disown` then poll log (client timeouts cancel the task - don't use plain timeout for full builds).
- 2026-11-05 05:57: GREEN builds + commits: (1) tools: Code Studio (Rhino JS run on-device), Scanner (CameraX+MLKit barcode), Mirror, Translator screen; (2) Hand Magic (MediaPipe gestures -> scroll/back/home, model in assets); (3) Offline STT (Vosk, opt-in, model download in Settings, wired into VoiceEngine via handleFinalText); (4) 3D Avatar (three.js bundled avatar.html); (5) Morning briefing (daily exact alarm -> TTS weather+news). Screens wired in nav + Settings "Screens" chips; Settings cards: Security & protection, Offline voice (Vosk), Morning briefing. APK 71MB arm64.
- REMAINING: Video Editor (Media3), web-agent/screenshot tools (extend VisionShare), Call handling, Code Studio QS tile, Health/geo receivers, Dynamic Island wiring into VoiceEngine states (island service exists but not yet called), notification digest/quiet hours?, MAX-style UI RESKIN (big - home/drawer/settings per /downloads/max_sheet_*.jpg), run unit tests, then delivery: bump version, sign, upload APK+source to swetaigx Drive, push to GitHub (token in vault file), full report to parent.
- EARLIER NOTE (superseded by green builds): features/translate/{TranslatorManager,TranslateCommands}, features/island/{DynamicIslandService,IslandView}, features/briefing/{Briefing,BriefingService}; manifest entries added (BriefingReceiver, BriefingService, DynamicIslandService); CommandRouter hooks for protect+translate. Next run: build+fix, wire island into VoiceEngine states, briefing settings row, then continue checklist (Scanner, Code Studio, Mirror, Video Editor, Hand Magic, Vosk, 3D avatar, web-agent).
- 2026-11-05 04:45: PROTECT module written: features/protect/{ProtectPrefs,FindPhoneController,SmsCommandReceiver,IntruderAdminReceiver,IntruderCaptureService,AppLockActivity,SmartAlarm,SmartAlarmActivity}.kt + manifest entries (SMS recv/send, CAMERA, FGS_CAMERA, FULL_SCREEN_INTENT, device_admin.xml) + gradle deps (camerax 1.4.2, lifecycle-service 2.9.4, mlkit translate/barcode/language-id, mediapipe tasks-vision 0.10.14, vosk-android 0.3.47, media3 1.6.1 transformer/effect/common). NetworkStatus.lastKnownLocationText added. compileReleaseKotlin in daemon still running at run end - re-run `timeout 115 ~/build.sh assembleRelease` (repeat as needed; daemon survives, compile is slow on 1 CPU with new deps); fix any `^e:` lines. NOTE: repeated client timeouts may restart the task; prefer waiting then one client run.
- MAX UI reference received from parent: /downloads/max_sheet_1.jpg, max_sheet_2.jpg (contact sheets), max_ui_ref.mp4 (7.7min walkthrough). Design language: dark bg + glassy cards; home = glowing blue orb + "Hi Gyan" greeting + quick chips (Video/Image/Homework) + stats strip (weather/free/mood) + bottom Ask-input with pink mic FAB; left drawer (New chat/Task/Search/Scheduled/Screen Share/Customize/Images/Library/Teach Mode/Recordings); settings grouped cards (Voice & Listening, Modes & Memory), QS shortcut tiles (Hey/Ghost/Torch); feature screens: Smart Alarm, Notification digest, Quiet Hours, gesture cam w/ filter carousel (Cyan/FPV/Galaxy/Rainbow)+REC, Live Translator, AI Images, Phone Dhundo w/ permission checklist, Stocks, Safety list, Phone Doctor PASS badges, Gemini keys, diagnostics, About w/ POWERED BY credit. Hinglish labels. Apply to ROUT keeping ROUT name + "Powered by Gyan".
- TODO next: (1) get protect build green; (2) run unit tests; (3) next modules: Translator screen, Scanner, Code Studio, Dynamic Island, morning briefing receiver, app-lock hook in MainActivity, protect Settings section + voice commands ("phone dhundo", "smart alarm lagao", "app lock lagao"); (4) UI reskin per reference; (5) sign/upload/push/report.

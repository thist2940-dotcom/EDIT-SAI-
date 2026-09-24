# EDIT SAI Roadmap

1. Foundation / app shell — Phase 1 — COMPLETE
2. Video import and playback — Phase 2 — COMPLETE
2.5. Custom launcher icon — Phase 2.5 — COMPLETE
3. Basic timeline and trim — Phase 3 — IMPLEMENTED (APK test pending)
4. Split, multi-clip, speed, and audio — Phase 4 — NOT STARTED
5. Text, stickers, and overlays — Phase 5 — NOT STARTED
6. Effects engine — Phase 6 — NOT STARTED
7. Transition engine — Phase 7 — NOT STARTED
8. Asset library manager — Phase 8 — NOT STARTED
9. Animation Studio MVP — Phase 9 — NOT STARTED
10. Character and animation library — Phase 10 — NOT STARTED
11. Optional Pollinations image generation — Phase 11 — NOT STARTED
12. Optional Gemini editing assistant — Phase 12 — NOT STARTED
13. Downloadable asset packs — Phase 13 — NOT STARTED
14. Performance optimization — Phase 14 — NOT STARTED

Permanent rule: never begin the next phase until the current phase APK has been tested on the Android device and the user confirms the test.

Build fix note: Phase 2 CI exposed a missing Compose setContent import in MainActivity; this was identified from the Actions compiler log and corrected without changing Phase 1 behavior.

## Phase 2 crash-hardening update
The video-selection crash path is hardened: persistable URI permission failures are caught, content URIs remain Uri objects, Media3 uses DefaultDataSource.Factory, player creation/release is guarded by DisposableEffect, player errors are shown in the Editor, and CrashLogger saves EDIT_SAI_CRASH_LOG.txt to Downloads via MediaStore with an app-specific fallback on older Android.


## Phase 3 implementation note
Trim uses Media3 Transformer with MediaItem clipping and EditedMediaItem. Transformer output is staged in the app cache and then published to public Movies/EDIT_SAI with MediaStore on Android 10+. Export failures are surfaced without crashing and recorded by CrashLogger.

Phase 3 implementation follow-up: build validation is running in GitHub Actions; no Phase 4 work has started.

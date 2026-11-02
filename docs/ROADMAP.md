# EDIT SAI Roadmap

1. Foundation / app shell — Phase 1 — COMPLETE
2. Video import and playback — Phase 2 — COMPLETE
2.5. Custom launcher icon — Phase 2.5 — COMPLETE
3. Basic timeline and trim — Phase 3 — IN PROGRESS (code complete; awaiting device APK verification)
4. Core multi-clip timeline — Phase 4 — IN PROGRESS (multi-import, per-clip trim, split at playhead, undo/redo, persistent autosave/restore, rotate/flip/crop, and merged multi-clip export implemented; awaiting device APK verification)
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

## Phase 3 + Phase 4 implementation note
Trim and multi-clip export use Media3 Transformer 1.3.1 with `MediaItem` clipping, `EditedMediaItem`, `EditedMediaItemSequence`, and `Composition`. Per-clip transforms (rotation, horizontal/vertical flip, aspect-ratio crop) use Media3 `ScaleAndRotateTransformation` and `Presentation`. Timeline state is autosaved locally to `edit_sai_timeline_project.json` and supports 30-step Undo/Redo and Split at Playhead. Transformer output is staged in the app cache and published to public `Movies/EDIT_SAI` with `MediaStore` on Android 10+. Do not mark Phase 3 or Phase 4 complete until the user tests and confirms the APK on device.

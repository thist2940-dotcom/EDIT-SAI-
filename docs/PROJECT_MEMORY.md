# EDIT SAI Project Memory

## Purpose and vision
EDIT SAI is a personal-use Android video editor and animation studio. It is not intended for Google Play Store distribution. Development and testing are designed around an Android smartphone.

## Developer environment constraints
- Development is ONLY from an Android smartphone.
- No PC and no Android Studio.
- Code changes are made directly in GitHub.
- GitHub Actions builds the APK.
- The APK comes from the GitHub Actions artifact named edit-sai-debug-apk.

## Permanent working rules
1. Work phase by phase.
2. Never start the next phase until the current phase APK is tested on the Android device and the user confirms the test.
3. Do not remove existing working code unless absolutely necessary.
4. When modifying an existing file, preserve current functionality.
5. If a change may break an old feature, explain the risk before doing it.
6. Push directly to GitHub; GitHub Actions must build the APK artifact.
7. Update docs/ROADMAP.md, docs/PHASE_STATUS.md and docs/PROJECT_MEMORY.md in every phase commit.
8. Keep UI simple and touch-friendly for mid-range Android devices.
9. No paid SDKs, no copyrighted assets, and no Play Store requirements.
10. Never commit secrets, API keys, tokens, signing keys, local.properties, or generated build output.
11. Handle unsupported or missing media gracefully; never crash.
12. Stop after the requested phase and do not begin the next phase without explicit confirmation after device testing.

## Current status

### Phase 1 — COMPLETE
Phase 1 is complete and tested on device.

It includes:
- Jetpack Compose + Material 3 app shell
- Home screen
- New Project, Editor, Animation Studio, Effects Library, Asset Manager, AI Tools, and Settings screens
- CrashLogger
- GitHub Actions debug APK workflow

Device test confirmed all screens open, Back to Home works, and there are no crashes.

### Phase 2 — IN PROGRESS
Phase 2 is Video Import + Playback.

Implemented:
- Editor Select Video using ActivityResultContracts.OpenDocument
- MIME type video/*
- URI-based picker access with persistable permission attempt and graceful fallback
- Media3 ExoPlayer in Compose through AndroidView + PlayerView
- Play/pause
- Seek bar
- Current position and total duration
- Friendly unreadable/unsupported media and player error messages
- No deprecated onActivityResult
- No broad storage permission added

Phase 2 remains IN PROGRESS until the new APK is built, installed on the phone, tested, and confirmed.

## Full phase list
| Phase | Feature | Status |
|---|---|---|
| 1 | Foundation / app shell | COMPLETE |
| 2 | Video import and playback | IN PROGRESS |
| 3 | Basic timeline and trim | NOT STARTED |
| 4 | Split, multi-clip, speed, and audio | NOT STARTED |
| 5 | Text, stickers, and overlays | NOT STARTED |
| 6 | Effects engine | NOT STARTED |
| 7 | Transition engine | NOT STARTED |
| 8 | Asset library manager | NOT STARTED |
| 9 | Animation Studio MVP | NOT STARTED |
| 10 | Character and animation library | NOT STARTED |
| 11 | Optional Pollinations image generation | NOT STARTED |
| 12 | Optional Gemini editing assistant | NOT STARTED |
| 13 | Downloadable asset packs | NOT STARTED |
| 14 | Performance optimization | NOT STARTED |

## Build facts
- Repository: thist2940-dotcom/EDIT-SAI-
- Branch: main
- Package: com.saigro.editsai
- Compile/target SDK: 34
- Minimum SDK: 26
- JVM target: 17
- Compose compiler extension: 1.5.14
- GitHub Actions: JDK 17, Gradle 8.7
- APK artifact: edit-sai-debug-apk

## Phase 2 technical notes
Android's document picker supplies URI access, so READ_MEDIA_VIDEO and READ_EXTERNAL_STORAGE were not added. The selected URI is checked through ContentResolver before playback. ExoPlayer is released when the player leaves the Compose composition. Player errors become user-visible messages.

## Diagnostics
CrashLogger writes the latest uncaught stack trace to the app-private last_crash.txt. Future diagnostics work is planned in docs/ERROR_LOG_PLAN.md.

## Future AI handoff
Read README.md, docs/ROADMAP.md, docs/PHASE_STATUS.md, docs/ERROR_LOG_PLAN.md, and this file before coding. Continue only from the recorded phase/status. Do not assume a later phase has started until the developer confirms the current APK test and authorizes it.


## Latest CI fix
The Phase 2 Actions compiler identified a missing `androidx.activity.compose.setContent` import in MainActivity. The corrective change is limited to the import cleanup; Phase 2 behavior and Phase 1 functionality are preserved.


## Phase 2 crash fix
The device reported an immediate crash when selecting a DCIM/Camera MP4. The hardened path catches SecurityException and IllegalArgumentException for persistable URI permission, uses the Uri directly, configures Media3 with DefaultDataSource.Factory, creates/releases ExoPlayer in DisposableEffect, catches player setup exceptions, and surfaces Player.Listener errors as Cannot play this video or Cannot read this video. No MediaMetadataRetriever path is used.

CrashLogger now writes EDIT_SAI_CRASH_LOG.txt to the public Downloads collection through MediaStore on Android 10+ and falls back to the app-specific Downloads directory on older Android. On next launch the latest crash log is shown in a selectable banner so the full stack trace can be copied.

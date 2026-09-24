# EDIT SAI Project Memory

## Purpose and vision
EDIT SAI is a personal-use Android video editor and animation studio. It is not intended for Google Play Store distribution. Development and testing are designed around an Android smartphone.

## Developer environment constraints
- Development is ONLY from an Android smartphone.
- No PC and no Android Studio.
- Code changes are made directly in GitHub.
- GitHub Actions builds the APK.
- The APK can be installed directly from the GitHub Releases page; the Actions artifact remains available as a secondary build output.

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
Phase 1 is COMPLETE and tested on device.
Phase 2 is COMPLETE and tested on device.
Phase 2.5 is COMPLETE and tested on device.
Phase 3 is IMPLEMENTED and awaiting APK build/device testing.

## Phase 2.5 — Custom Launcher Icon
Source portrait filename: ChatGPT Image Sep 23, 2026, 02_47_57 PM.png in the repository root. The supplied image was cropped to a clean 1:1 square portrait for launcher use; the baked-in rounded card and dark outer frame are not used in the launcher asset.

Implementation:
- app/src/main/res/mipmap-nodpi/ic_launcher_photo.webp contains the clean 128x128 processed portrait.
- app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml uses adaptive icon layers.
- app/src/main/res/drawable/ic_launcher_foreground.xml applies a 17% inset.
- Background is #1A1A2E.
- android:icon and android:roundIcon point to the new launcher resources.
- The same portrait is used for the legacy round fallback.

Replacement steps:
1. Prepare a clean square portrait with no baked-in rounded card, border, or dark outer frame.
2. Replace ic_launcher_photo.webp with the new processed portrait.
3. Keep the adaptive icon XML, foreground inset, background color, and manifest references unless the icon design is intentionally changed.
4. Build/install a new APK and verify the icon on the launcher and app info screen.


## Phase 3 — Basic Trim + Export
- Editor retains the Phase 2 picker and Media3 playback.
- Two touch-friendly sliders control trim start/end and display MM:SS.
- Preview Selection seeks to the selected start and stops at the selected end.
- Media3 Transformer 1.3.1 exports the clipped MediaItem through EditedMediaItem.
- Export progress is shown while Transformer runs asynchronously.
- Output is staged in app cache and published to public Movies/EDIT_SAI using MediaStore on Android 10+.
- Output filename format is EDIT_SAI_trim_<timestamp>.mp4.
- Success provides a Snackbar action plus an Open Last Export button.
- Export failures are shown as friendly errors and logged with CrashLogger.

Phase 3 implementation follow-up: build validation is running in GitHub Actions; no Phase 4 work has started.

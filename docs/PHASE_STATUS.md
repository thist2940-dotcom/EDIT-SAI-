# Phase Status

## Phase 1 — Foundation
Status: COMPLETE and tested on device.

## Phase 2 — Video Import + Playback
Status: COMPLETE and tested on device.

Included:
- Editor Select Video using Activity Result Contracts OpenDocument
- MIME filter video/*
- Persistable URI permission attempt with graceful fallback
- URI readability check before playback
- AndroidX Media3 ExoPlayer playback
- Play/pause, seek slider, position and duration
- Friendly non-fatal media/player errors
- No broad storage permission

Phase 2 remains incomplete until the corrected APK is built, installed, tested, and confirmed.

## Phase 2.5 — Custom Launcher Icon
Status: COMPLETE and tested on device.

Included:
- Clean square crop of the supplied portrait
- Adaptive icon on Android 8.0+ with #1A1A2E background
- 17% foreground inset for mask safety
- Legacy/round fallback
- Manifest icon references
- No changes to the Phase 1 home screen or Phase 2 video player code

## Phase 3 — Basic Trim + Export
Status: IN PROGRESS; controls visibility and export require build/device verification.

Included:
- Start/end trim sliders with MM:SS labels
- Preview Selection bounded by trim end
- Media3 Transformer 1.3.1 export using MediaItem clipping + EditedMediaItem
- Visible export progress
- Public Movies/EDIT_SAI MediaStore save on Android 10+
- Timestamped EDIT_SAI_trim_<timestamp>.mp4 output
- Snackbar action and Open Last Export button
- Friendly export errors logged through CrashLogger
- Invalid trim range prevention

## Phase 4 — Core Multi-Clip Timeline
Status: IN PROGRESS; initial timeline controls implemented, APK validation pending.

Included so far:
- Import multiple videos into a session timeline
- Select a clip for playback and trim controls
- Duplicate, reorder, and delete timeline entries
- Scrollable editor layout so trim/export controls are reachable on smaller screens

Limitations still to implement or verify:
- Export currently exports the selected clip's trim range, not a merged multi-clip timeline
- Split-at-playhead, persistent project save/autosave, undo/redo, crop/rotate/flip, and full multi-clip export are not complete

Phases 3 and 4 are being developed together at the user's request. Build and on-device testing are still pending; neither phase is marked complete.

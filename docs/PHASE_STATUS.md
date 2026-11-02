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
Status: IN PROGRESS; code complete, awaiting on-device APK verification.

Included:
- Start/end trim sliders with MM:SS labels per clip
- Preview Selection bounded by trim end
- Media3 Transformer 1.3.1 export using MediaItem clipping + EditedMediaItem
- Visible export progress
- Public Movies/EDIT_SAI MediaStore save on Android 10+
- Timestamped EDIT_SAI_trim_<timestamp>.mp4 output
- Snackbar action and Open Last Export button
- Friendly export errors logged through CrashLogger
- Invalid trim range prevention

## Phase 4 — Core Multi-Clip Timeline
Status: IN PROGRESS; code complete, awaiting on-device APK verification.

Included:
- Import single or multiple videos into a timeline with independent per-clip state (unique ID, trim range, transforms)
- Select a clip for playback, trim, split, and transform controls
- Duplicate, reorder (up/down), and delete timeline entries
- Split at Playhead: splits the selected clip at the current playhead into two consecutive timeline clips
- Undo / Redo stack (up to 30 snapshots) across timeline, trim, split, and transform edits
- Persistent project autosave and restore (`edit_sai_timeline_project.json`) with unreadable-URI safety + New Project screen actions
- Crop / Rotate / Flip per clip: 90° rotation, horizontal flip, vertical flip, and aspect-ratio crop presets (`Full`, `16:9`, `9:16`, `1:1`) with live player preview and Media3 export effects (`ScaleAndRotateTransformation`, `Presentation`)
- Full multi-clip merged timeline export (`EDIT_SAI_timeline_<timestamp>.mp4`) via `EditedMediaItemSequence` and `Composition` alongside single-clip export (`EDIT_SAI_trim_<timestamp>.mp4`)
- Scrollable editor layout so all timeline, trim, transform, and export controls remain reachable on phone screens

Phases 3 and 4 are developed together at the user's request. Neither phase is marked COMPLETE until the user tests and confirms the APK on device.

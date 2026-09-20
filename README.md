# EDIT SAI

EDIT SAI is a personal-use Android video editing and animation workspace.

## Phase 1: Foundation

This phase provides a small, offline Jetpack Compose app shell with placeholder destinations. Video editing, media import, and export are intentionally not implemented yet.

### Build

GitHub Actions builds the debug APK on pushes to `main` or `master` using JDK 17 and Gradle 8.7. The artifact is named `edit-sai-debug-apk`.

### Package

`com.saigro.editsai`

Do not commit API keys, signing keys, `local.properties`, or generated build output.

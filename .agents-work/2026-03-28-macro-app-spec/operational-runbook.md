# Operational Runbook - myFitnessMeals MVP

Date: 2026-04-05
Session: 2026-03-28-macro-app-spec

## 1) Local startup
1. Ensure JDK 17 and Android SDK API 35 are installed.
2. Start emulator or connect a physical device.
3. Run app:
   - `./gradlew --no-daemon :app:assembleDebug`
   - `adb install -r app/build/outputs/apk/debug/app-debug.apk`

## 2) Garmin integration operations
1. Go to Settings -> Garmin section.
2. Paste authorization code in `Garmin auth code` field.
3. Click `Connect`.
4. Click `Sync now` and verify `Last sync` updates.
5. Use `Disconnect` to revoke local link.

### Expected state transitions
1. `DISCONNECTED` -> `CONNECTED` after successful connect.
2. `CONNECTED` -> `REAUTH_REQUIRED` if token is missing/expired and refresh fails.
3. `CONNECTED` remains after successful sync.

## 3) Privacy operations
1. Go to Settings -> Privacy.
2. `Export data` creates local JSON export file.
3. `Delete all data` requires explicit confirm action.

### Export location
1. App-private path under `files/exports/`.
2. Filename format: `myfitnessmeals-export-<timestamp>.json`.

## 4) Failure handling
1. Garmin connect failure:
   - Verify auth code format and try again.
2. Garmin sync failure:
   - Check network and retry.
   - If status is `REAUTH_REQUIRED`, reconnect Garmin.
3. Barcode scan issues:
   - Denied camera permission falls back to manual barcode input.

## 5) Pre-release validation
1. `./gradlew --no-daemon test`
2. `./gradlew --no-daemon :app:connectedDebugAndroidTest`
3. `./gradlew --no-daemon lint`
4. Manual Garmin flow on physical device.

## 6) Notes
1. `compileSdk=35` AGP warning is currently non-blocking.
2. Keep release branch focused; UI/UX CR can proceed after this baseline.

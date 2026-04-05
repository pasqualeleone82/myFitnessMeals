# Release Checklist (Beta)

Date: 2026-04-05
Session: 2026-03-28-macro-app-spec

## Build and quality gates
1. `./gradlew --no-daemon test` -> PASS
2. `./gradlew --no-daemon :app:connectedDebugAndroidTest` -> PASS
3. `./gradlew --no-daemon lint` -> PASS
4. `./gradlew --no-daemon :app:assembleDebug` -> PASS

## Artifact
1. APK path: `app/build/outputs/apk/debug/app-debug.apk`
2. Install command: `adb install -r app/build/outputs/apk/debug/app-debug.apk`

## Security checks
1. Garmin OAuth token storage is encrypted and fail-closed.
2. No plaintext fallback for token persistence.
3. Garmin auth code validation enabled.
4. Token refresh and reconnect hardening enabled.

## Privacy checks
1. Export data flow available from Settings.
2. Delete all data flow available from Settings with explicit confirmation.
3. Delete includes local DB + provider links + OAuth tokens + local settings.

## Manual go-live checks
1. Validate Garmin connect/sync/disconnect on physical device with real account.
2. Verify onboarding/settings persistence after app restart.
3. Verify barcode scan fallback when camera permission denied.

## Status
1. Ready for device validation and CR intake.

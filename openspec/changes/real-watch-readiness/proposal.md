## Why

The UII watch app (pairing, manual SOS, fall relay, heart-rate and motion signals, offline outbox, boot resume) was built and verified only on a Wear OS emulator. It cannot yet be installed on a relative's or wearer's real watch in a way that is safe to rely on: it still carries the scaffold's application id, is limited to the newest Android level, has no release signing, and nothing about its battery cost, fall-alert latency or behaviour when the watch loses its phone link has been measured. An alerting app that drains the battery by mid-day, or silently stops reporting, is worse than none, so these gaps have to be closed and measured before any real use.

## What Changes

- Give the app a permanent application id and a signed, shrink-safe **release** build that points at `https://uii.studev.net`, contains no debug-only permission, log hook or cleartext allowance, and can be rebuilt reproducibly with a documented command.
- Establish the real minimum supported Wear OS version (today `minSdk = 36`, i.e. Wear OS 6 only) by compiling and testing against lower API levels, and lower it as far as the APIs in use allow (Health Services fall events, foreground-service health type, notifications).
- Remove or isolate the original sensor-viewer scaffold (`MainActivity`, `SensorRepository`, …) from the shipped app, and resolve the stale `wearos-sensor-dashboard` change so the repo has one story.
- Add a repeatable on-device validation procedure and record its results: battery drain with monitoring on (including the 50 Hz accelerometer), fall-to-server latency, behaviour with the phone link lost and restored, reboot and app-update persistence, and survival of OEM battery management.
- Make field problems diagnosable without a debugger: the status screen shows app version, server host, pairing state, permission and capability state, outbox size and last upload result.
- Document how to install on a physical watch (ADB over Wi-Fi) and how to pair it with the web app.
- Based on the measurements, decide and record whether the accelerometer rate or reporting latency must change. Any such change is a follow-up task gated on the data, not assumed up front.

## Capabilities

### New Capabilities

- `watch-release-build`: A signed, production-configured release build with a stable application id, an explicitly chosen supported OS range, and no debug-only behaviour.
- `watch-field-validation`: The observable behaviour the watch must show on real hardware (battery budget, fall alert timing, offline resilience, persistence, diagnosability) and the procedure that proves it.

### Modified Capabilities

- None. `openspec/specs/` is empty; the in-flight `wearos-sensor-dashboard` change is superseded for the shipped app (see design) and is not an existing capability.

## Impact

- `watchface/build.gradle.kts` (application id, signing config, SDK levels, release settings), `watchface/src/main/AndroidManifest.xml`, `watchface/src/debug/AndroidManifest.xml`, and the permission-request path in `HomeActivity` (heart-rate permission differs below Wear OS 6). The Java package name stays; only the installed application id changes.
- Removing `MainActivity`, `AndroidSensorRepository`, `SensorInfo`, `SensorRepository`, `SensorStateStore` and their test from the shipped app.
- `HomeActivity` / `Ui` for the diagnostics lines.
- New documentation under the repo (install and validation guide) and a signing keystore that must stay out of version control.
- Dependency on the web side: migrations `0003`–`0005` and the device endpoints must be live on the production database before the watch can pair against `https://uii.studev.net`.
- Out of scope: seizure or pre-seizure detection on the watch, GPS, medication reminders, a phone companion app.

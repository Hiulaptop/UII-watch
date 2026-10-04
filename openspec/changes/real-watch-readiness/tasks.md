## 1. Pre-flight and baseline

- [ ] 1.1 Confirm the production web app is ready: migrations 0003-0005 applied, current build deployed, and a pairing code generated on the Devices page is accepted by `POST https://uii.studev.net/api/device/pair` from `curl` (expect HTTP 201, then remove the test device from the web). Record the result in the validation notes.
- [x] 1.2 Run `./gradlew testDebugUnitTest assembleDebug assembleRelease` on the current code and record the baseline: all tests pass, APK sizes, and the merged release manifest (`build/intermediates/merged_manifests`) saved for later comparison.

## 2. Release identity and configuration

- [x] 2.1 Change `applicationId` to `net.studev.uii` (namespace unchanged) and verify `assembleRelease` output reports that id with `aapt dump badging`, and that the debug build still launches and shows the home screen on the emulator.
- [x] 2.2 Make the fall cancel window debug-only: the release variant always uses 20000 ms whatever `-Puii.fallWindowMs` says. Verify with `aapt`/decompiled `BuildConfig` of a release built with `-Puii.fallWindowMs=1000` that the value is 20000.
- [x] 2.3 Make `API_BASE_URL` for release always production HTTPS: the release variant ignores `-Puii.apiBaseUrl` (a debug-only property) and only accepts `-Puii.releaseApiBaseUrl`, which must start with `https://` or configuration fails. Verified: a release built with `-Puii.apiBaseUrl=http://evil` contains `https://uii.studev.net`; `-Puii.releaseApiBaseUrl=http://x` fails the build with a clear message.
- [x] 2.4 Add the release `signingConfig` reading keystore path and passwords from Gradle properties or environment variables, failing with a message that names the missing item and never falling back to the debug key. Verify: release build without the settings fails; with a throwaway test keystore it succeeds and `apksigner verify --print-certs` shows the test certificate.
- [x] 2.5 Add keystore patterns (`*.jks`, `*.keystore`, `keystore.properties`) to `.gitignore` and verify `git status` ignores a dummy keystore placed in the repo.
- [x] 2.6 Verify the release package: merged manifest has no `ACCESS_LOCAL_NETWORK`, no `usesCleartextTraffic="true"`, no unused permission (compare with the saved baseline, justify each remaining permission in the notes), and `BODY_SENSORS` handling is decided in section 4.

## 3. Remove the sensor-viewer scaffold

- [x] 3.1 Delete `MainActivity`, `AndroidSensorRepository`, `SensorInfo`, `SensorRepository`, `SensorStateStore` and `SensorStateStoreTest`, the two "Cảm biến" buttons in `HomeActivity`, and the manifest entry and its comment. Verify the project compiles, remaining unit tests pass, and `aapt dump xmltree` shows exactly one launcher activity.
- [x] 3.2 Archive the abandoned `wearos-sensor-dashboard` change with `openspec archive wearos-sensor-dashboard --skip-specs` and verify `openspec list` no longer shows it as in progress.

## 4. Supported Wear OS range

- [x] 4.1 Add a Wear OS 5 (API 34) and a Wear OS 6 (API 36) emulator image, set `minSdk = 34`, and run `./gradlew lintRelease` and the unit tests; list every lint error and API-level problem found in the notes and fix them or raise the floor.
- [x] 4.2 Choose the heart-rate permission by `Build.VERSION.SDK_INT` (`BODY_SENSORS` below Wear OS 6, `READ_HEART_RATE` from it) in the request path and the missing-permission check, without reintroducing the request loop. Verify with a unit test for the selection logic and on both emulators that pairing grants what is needed and heart-rate samples arrive.
- [ ] 4.3 Exercise the full flow on the Wear OS 5 emulator (pair, signals, manual SOS, reboot resume, fall via synthetic health data if the image supports it) and record per-feature pass or fail; set the final `minSdk` to the lowest level that passes, and verify an install attempt on a lower-API emulator is refused by the package manager.
- [ ] 4.4 Verify the "no fall detection" path: on an emulator or build where the capability is absent, the app still pairs, reports signals and manual SOS, and the status screen says automatic fall detection is unavailable.

## 5. Diagnostics on the status screen

- [x] 5.1 Add app version and server host lines to the paired status text, derived from build info, and verify with a unit test of the formatter plus a screenshot on the emulator.
- [x] 5.2 Add one line per needed permission and capability (granted / missing, available / unavailable) and, when monitoring is stopped, name the cause; keep the existing "Cấp quyền" action. Verify by revoking each permission with `adb shell pm revoke` and checking the text names it and the button restores it.
- [x] 5.3 Show an unpaired or server-rejected state clearly: pair, then revoke the device on the web, and verify the next upload leaves the watch showing "bị từ chối / cần ghép lại" rather than a silent retry.

## 6. Measurement tooling and debug trigger

- [x] 6.1 Name the sampling constants in `MotionService` (rate, batching latency, window length) as documented configuration with their rationale, with no behaviour change; verify the existing signal tests still pass.
- [x] 6.2 Add a debug-source-set-only trigger that runs the same code path as a system fall event, usable with `adb shell am broadcast`; verify it works in a debug build and that the release merged manifest contains no reference to it.
- [x] 6.3 Write `tools/measure-battery.ps1` (records battery level and `dumpsys batterystats` snapshots over time to a CSV) and `tools/fall-latency.ps1` (triggers the debug event, then reads the alert's detection and creation times from the web database or API and prints the delay). Verify each runs end to end against the emulator and prints sensible numbers.

## 7. Install and validation on a real watch

- [ ] 7.1 Write the install and pairing guide (`docs/install.md`): enable developer options and ADB over Wi-Fi, `adb install -r`, grant permissions, pair with a code, vendor power-management settings to switch off, and how to uninstall. Verify by following it on a clean emulator or the real watch without any step missing.
- [ ] 7.2 Produce a signed release with the real keystore (kept outside the repo), install it over ADB on the real watch, pair against production and confirm samples appear on the web Devices and Dashboard pages.
- [ ] 7.3 Battery: run the baseline, current (50 Hz / 2 s) and a cheaper candidate configuration for at least six hours each under normal wear, plus one full 24-hour run of the chosen configuration; record drain per hour and projected runtime in the validation record, and decide whether any sampling change is needed (if so, add and verify it as a follow-up task here).
- [ ] 7.4 Fall timing and cancel: with the debug trigger, run at least 10 uncancelled and 5 cancelled cases online; verify each uncancelled alert reaches the server within 10 seconds after the cancel window and cancelled ones never arrive, and record the delays.
- [ ] 7.5 Connectivity: with the debug trigger and sample uploads, test a 10-minute and a 1-hour loss of connectivity (phone Bluetooth off or Wi-Fi off); verify exactly one alert per event arrives after reconnect with its original detection time, and sample rows are not duplicated.
- [ ] 7.6 Persistence and power management: reboot the watch untouched, update the app while an item is queued, and leave the watch idle overnight on its charger; verify monitoring resumes, queued items are delivered, and samples arrive within a minute of wearing it.
- [ ] 7.7 Multi-day idle check: leave the paired watch unused for three days and verify permissions are still granted and monitoring still works; if the system revoked anything, record it and add the needed mitigation.
- [ ] 7.8 Complete the validation record `docs/validation/<watch-model>-<version>.md` covering every requirement in `specs/watch-field-validation`, and verify the release gate: every requirement is pass for the model, otherwise the build is marked not for distribution.

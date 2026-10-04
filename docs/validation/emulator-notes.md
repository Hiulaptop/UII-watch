# Emulator results for `real-watch-readiness` (app 1.0, 2026-10-03)

This is **emulator evidence only**. It does not satisfy `specs/watch-field-validation` for any physical watch; that needs
the hardware runs in tasks 7.2 to 7.8. Emulators used: Wear OS 5 (API 34), Wear OS 6 (API 36), Wear OS 7 (API 37, earlier work).

## Pre-flight (task 1.1) - BLOCKED on the owner

`POST https://uii.studev.net/api/device/pair` with a wrong code returned **HTTP 404 (an HTML page)**. A current web build
answers `401 invalid_code`. So the web app deployed behind the tunnel does not yet contain the device API, and
migrations `0003` to `0005` cannot be assumed to be applied. Nothing in the watch can pair against production until the
owner runs the migrations and `docker compose up -d --build`. Re-run the curl in `docs/install.md` section 1 afterwards.

## Baseline before changes (task 1.2)

64 unit tests passed; debug APK 14.6 MB, unsigned release 11.4 MB. Merged release manifest saved as
`baseline-release-manifest.xml`.

## Release package (tasks 2.x)

| Check | Result |
|---|---|
| Application id | `net.studev.uii` (aapt2 badging) |
| Debug build launches, shows home | yes, Wear OS 5 and 6 |
| Release built with `-Puii.fallWindowMs=1000 -Puii.apiBaseUrl=http://evil` | `BuildConfig` has 20000 ms and `https://uii.studev.net` |
| `-Puii.releaseApiBaseUrl=http://x` | build fails: "must start with https://" |
| Release with no signing settings | build fails naming all four missing settings |
| Release with a throwaway key | signed; `apksigner verify` shows that certificate |
| Keystore files ignored by git | `*.jks`, `*.keystore`, `keystore.properties` |
| Merged release manifest | no `ACCESS_LOCAL_NETWORK`, `usesCleartextTraffic="false"`, no debug receiver (also absent from classes.dex and the installed package) |
| Signed release installs and launches (API 36) | yes; shows host `uii.studev.net` |
| Launcher entries | exactly one (`HomeActivity`) |

Permissions left in the release and why: `INTERNET` (server), `ACCESS_NETWORK_STATE` (upload only when online),
`POST_NOTIFICATIONS` (fall cancel notification), `RECEIVE_BOOT_COMPLETED` (resume after reboot), `FOREGROUND_SERVICE` and
`FOREGROUND_SERVICE_HEALTH` (motion service), `BODY_SENSORS` (heart rate, declared up to API 35 only),
`health.READ_HEART_RATE` (heart rate on API 36+), `ACTIVITY_RECOGNITION` (required by Health Services and the motion
service), `WAKE_LOCK` (WorkManager), `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (AndroidX).

## Supported Wear OS range (tasks 4.x)

`minSdk` lowered from 36 to **34** (Wear OS 5). `lintRelease` at 34: **0 errors**, 14 warnings (version-catalog hints, newer
library versions, `commit()` use that is deliberate, no launcher icon, `taskAffinity` for Wear recents). The first lint
run had 2 errors caused by unused leftover Watch Face Format resources (`raw/watchface.xml`,
`xml/watch_face_info.xml`, `drawable/preview.png`); they were deleted.

Below Wear OS 6 heart rate needs `BODY_SENSORS`; `Permissions.heartRate(sdk)` now picks the right one (unit tested), and
`BODY_SENSORS` is declared with `maxSdkVersion="35"`.

| Feature | Wear OS 5 (API 34) | Wear OS 6 (API 36) |
|---|---|---|
| Install, pair, permission dialogs grant what is needed | pass (physical activity, notifications, sensors) | pass (physical activity, heart rate, notifications) |
| Heart rate + motion rows reach the server with the app in the background | pass | pass |
| Manual SOS reaches the server and fans out | pass | not repeated |
| Reboot untouched: service restarts, rows reach the server | pass | pass |
| Debug fall trigger -> alert 8 s after trigger with an 8 s window (0.0-0.1 s after the window) | pass | pass |
| Synthetic fall from Health Services | not tried (debug trigger used) | not tried |

Open: (a) a refused install on a watch below API 34 is implied by `minSdkVersion:'34'` in the package but was not tried on
a Wear OS 4 image; (b) the app has not been run on a watch **without** fall detection (both emulators report it as
available), so that path is covered only by the status-text unit test.

## Status screen (tasks 5.x)

Shows version, server host, one line per permission, heart-rate and fall support, queue size, last upload. With a
permission revoked and refused, it says `GIÁM SÁT CHƯA ĐỦ. Thiếu quyền: ...` and names each one. After two refusals
Android stops showing its dialog, so **Cấp quyền** then opens the app's Settings page (verified it opens on API 34; the
toggles there were not driven end to end, the permissions were re-granted with `adb pm grant`). After a single refusal
(API 36), **Cấp quyền** shows the system dialog again and tapping Allow returned the screen to `Giám sát: đang chạy`. After the device was revoked on the server and the next upload got 401, the watch showed "Máy chủ đã
từ chối đồng hồ này. Hãy ghép nối lại."

Found and fixed during this: with the longer status text the SOS button had fallen below ten lines on a 454 px screen; SOS
is now the first control.

## Measurement tooling (tasks 6.x)

`tools/fall-latency.ps1` and `tools/measure-battery.ps1` ran end to end on the emulators. The emulator has no battery
drain, so the battery numbers are only a smoke test; real values need the watch.

# Watch UI v2 (Compose Material 3) - emulator results, 2026-10-04

Emulator evidence only (Wear OS 5 / API 34 and Wear OS 6 / API 36, against a local test server). Nothing here replaces the
physical-watch validation in `real-watch-readiness` tasks 7.x.

## Build

- Kotlin 2.2.10 (the version AGP 9.4.0 builds with) and the Compose compiler plugin at the same version; Wear Compose
  1.7.0 (`foundation`, `material3`, `navigation`), `activity-compose` 1.11.0, `lifecycle-runtime-compose` 2.9.4.
- APK size grew a lot: debug 14.6 -> 35.1 MB, **release 11.4 -> 27.5 MB** (R8 is off, as decided in `real-watch-readiness`).
  As an experiment, a release built with R8 enabled was **2.3 MB**; it was not adopted (it needs its own test pass on
  the alert path) but is the obvious next step if size matters for installs over ADB or Play.
- Unit tests: 80 (was 71 before the UI work; 7 old status-text tests were removed with the old screens, 16 added).
  `lintRelease` clean. Release manifest: one launcher activity, no debug receiver, no full-screen-intent permission.

## Behaviour checked against the specs

| Requirement | Result |
|---|---|
| Home states: green ring + heart rate (`--` when stale), amber + "Cấp quyền", grey + count, unpaired, refused | all seen on screen; match the Figma v2 frames |
| SOS on screen in every state, also at font scale 1.3 | yes; no clipped label at 1.3 |
| Details one tap away, one row per check, warning on network when offline | yes |
| Spoken labels | every ring, key and icon button has a Vietnamese description (uiautomator dump: no unlabeled clickables) |
| Text economy | unit test fails the build if a main-screen label exceeds three words (one caption was shortened) |
| SOS: 3 choices fit without scrolling; swipe back works; 5 s countdown with ring; Cancel sends nothing | yes |
| Honest delivery | online: "Đã gửi" only after the server stored it; offline: "Chờ mạng", and it switches to "Đã gửi" about 2 s after the network returns |
| Pairing | dots, short messages for: too short, wrong code, offline, too many devices; success starts monitoring and permission requests |
| Rate-limit message | not reached on the emulator (the burst was slower than the server's 10 per minute); covered by the unit test of the outcome-to-message mapping |
| Re-pair when the server removes the watch | "Cần ghép lại" appears, the button opens code entry, the queued alert is kept |
| Permission fix | tapping the amber ring asks again; Settings opens only when the system answers without a dialog |
| Reboot with the app never opened | monitoring resumes and samples reach the server (API 34 and API 36) |
| Background heart-rate and motion rows reach the server | yes, both versions |
| Cold start to usable home (`am start -W`) | API 34: 1.0-1.7 s; API 36: 0.3-0.6 s. No baseline: the old Java screens are gone, so these are absolute numbers on an emulator |

## Fall prompt: what actually works

- **Wear OS does not honour full-screen intents.** The system log on both emulators says
  `FullScreenIntentHelper: Fullscreen flag not enabled`, so the notification could not take over the screen. The
  `USE_FULL_SCREEN_INTENT` permission and the full-screen intent were removed again.
- The "Bạn ổn chứ?" screen therefore appears **only when the app is already in front**. In that case no notification is
  posted: the system's alert chip sat exactly on top of the "Tôi ổn" button and swallowed the taps (found by testing).
  If the wearer leaves the prompt before the window ends, it posts the notification itself.
- With the app not in front, the heads-up notification with the single "Tôi ổn" action is what appears; tapping its
  action cancels (outbox empty, no alert reaches the server); tapping elsewhere does not.
- Results (8 s test window): cancel from the prompt -> no alert (repeated); tap on the background -> alert sent; ignore ->
  alert sent and the prompt shows "Đã gửi"; cancel from the notification -> no alert.

## Known gaps and observations

- There is no "unpair" button any more (the design has none). A wearer removes the device on the website; the watch then
  shows "Cần ghép lại". `adb shell pm clear net.studev.uii` wipes it for development.
- After the server removes the watch, a new SOS shows "Chờ mạng" (it is not sent), which is true but names the network
  rather than the real cause; the home screen explains it once the countdown screen closes.
- The delivery screen treats "left the outbox" as "server accepted it". The uploader also drops an item the server
  permanently rejects (HTTP 4xx other than 401), which would be shown as sent. Not changed here (core behaviour).
- Full-day battery impact of Compose, behaviour on a physical watch, and the font scale beyond 1.3 are not measured.

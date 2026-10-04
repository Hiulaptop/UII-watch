# Hardening after the UI v2 work - 2026-10-04

## 1. Alerts the server refuses are no longer reported as sent

The delivery screen used to treat "left the outbox" as "the server accepted it". The uploader also removes an item the
server refuses for good (any 4xx except 401), so such an alert would have shown the green "Đã gửi".

- The uploader now tells a listener which item it dropped; the app keeps the last 20 such ids (`RecentIds`, unit tested).
- Both the manual-SOS confirmation and the fall prompt show an amber **"Không gửi được"** with the spoken text "Báo động
  không gửi được. Hãy gọi người thân ngay." and stay on screen until the wearer leaves.
- Verified on the Wear OS 5 emulator by moving the emulator clock three days ahead: the server answered `bad_timestamp`,
  the outbox emptied, and the watch showed "Không gửi được" and kept showing it. The clock was restored afterwards.
- Not changed: the server's choice of what to refuse, and what the relatives see (they get nothing, since nothing was stored).

## 2. Release build shrunk with R8

| Build | Before | After |
|---|---|---|
| Release | 27.5 MB | **2.3 MB** |

- `optimization { enable = true }` for release, with rules in `watchface/src/main/keepRules/rules.keep`: line numbers
  kept for traces, Health Services' listener service and the WorkManager worker kept by name.
- A `shrunk` build type was added for testing: the release's shrinking, debug-signed, debuggable (the app only allows
  plain http in debuggable builds) and pointed at the local test server. It is never distributed.
- On the shrunk build, on Wear OS 5: pairing, the three permission dialogs, SOS reaching the server, the fall prompt
  (cancel: no alert; ignore: alert sent), the refused-alert state, background heart-rate and motion rows, and resume after
  reboot with the app never opened. No crash in the log. (The first attempt crashed, but only because the test build was not
  allowed to use http; that was a test-build mistake, not R8.)
- Limits of that evidence: the shrunk test build is debuggable, which can change some R8 optimisations, so the real release
  (non-debuggable) was only built, linted and inspected (one launcher, no debug receiver, no debuggable flag), not run
  against a server. It also was not run on Wear OS 6 or a physical watch. Run `real-watch-readiness` task 7.2 with the
  actual release before distributing it.
- Keep `watchface/build/outputs/mapping/release/mapping.txt` (about 29 MB) with each release that leaves the machine.

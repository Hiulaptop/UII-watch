## Context

The app lives in the `watchface` Gradle module (Java, no Kotlin, no Compose). Observed today:

- `applicationId = "com.example.watchtest"`, `minSdk = 36`, `targetSdk = 37`, `versionCode = 1`, no `signingConfig`, release `optimization` disabled.
- `API_BASE_URL` defaults to `https://uii.studev.net`; `FALL_CANCEL_WINDOW_MS` defaults to 20000 and can be overridden by a Gradle property in any build type. `ACCESS_LOCAL_NETWORK` is declared in the debug manifest only; cleartext is allowed in debug only.
- Accelerometer: 50 Hz with 2 s batching latency (`MotionService`), a foreground service of type `health`, 10 s windows. Heart rate arrives through a Health Services passive listener. The permission set requested at runtime is `READ_HEART_RATE`, `ACTIVITY_RECOGNITION`, `POST_NOTIFICATIONS`; `BODY_SENSORS` is deliberately not requested (it can no longer be granted on the newest Android).
- The original sensor viewer (`MainActivity` and the `SensorRepository` family) is still in the app and linked from `HomeActivity` ("Cảm biến") and the manifest.
- The home screen already shows capability, motion state, queue size and last upload result; it does not show app version, server host or a per-permission breakdown.
- Alerts are sent before sample batches, each ordered by creation time; alert delivery is idempotent through a client id, so retries cannot duplicate.
- The wearer's physical watch model is not yet known to this project. All verification so far is on the Wear OS emulator.

See `proposal.md` for the motivation and `specs/` for the required behaviour.

## Goals / Non-Goals

**Goals:**
- A watch can be installed with a signed release, paired against production and left running for a day, with evidence that it meets the specs.
- The lowest supported Wear OS version is set by evidence, not by what the scaffold happened to pick.
- Whatever fails the battery or timing requirement is found early, with a decision recorded.

**Non-Goals:**
- No new detection logic (seizure or pre-seizure), no GPS, no medication reminders, no phone companion app, no Play Store listing.
- No UI redesign and no migration to Compose; the status screen only gains lines.
- No Java package rename; it is invisible to users and would add churn to every file.
- ~~No R8/shrinking~~ (superseded by `docs/validation/hardening-notes.md`): the Compose UI took the unshrunk release from 11 MB to 27 MB, so shrinking was enabled after a full emulator pass on the shrunk code (release 2.3 MB).

## Decisions

**1. Application id `net.studev.uii`; keep the `com.example.watchtest` namespace.**
The owner controls `studev.net`, so the reverse-domain id is stable and honest. Only `applicationId` changes, so `BuildConfig`, `R` and every class stay where they are. Alternative: rename the package too. Rejected: large mechanical diff, no user-visible gain, higher risk of a missed manifest reference. Consequence: the new id installs as a different app from the emulator builds, so the emulator pairing must be redone once; that is acceptable because no real watch is paired yet.

**2. Signing key supplied from outside the repository.**
The release `signingConfig` reads the keystore path and passwords from Gradle properties or environment variables (kept in the developer's user-level Gradle properties). If any is missing, the release task fails with a message naming the missing item, instead of falling back to the debug key. The `.gitignore` gains keystore patterns. The README states that losing the key means a new application id and re-pairing, so the key and its passwords must be backed up. Alternative: commit an encrypted keystore. Rejected: needless exposure of a long-lived secret.

**3. Distribution by sideloading over ADB (Wi-Fi), not the Play Store.**
One owner, a handful of watches, health-related app that would draw a heavy store review. A written install guide covers enabling developer options, ADB over Wi-Fi, `adb install -r`, granting permissions and pairing. Play distribution (Play App Signing, store listing, health declarations) is a separate future change.

**4. Treat the test-only fall window as debug-only.**
`FALL_CANCEL_WINDOW_MS` stays configurable, but the release variant ignores the property and uses 20 s, so a leftover test flag can never ship. Implemented in `build.gradle.kts` (the property is read only for the debug variant) and asserted by a build check on the release artifact.

**5. Lower `minSdk` only as far as an actual build and test shows is safe; target API 34 (Wear OS 5) first.**
`FOREGROUND_SERVICE_HEALTH` and the `health` service type exist from API 34, and `POST_NOTIFICATIONS` from 33, so 34 is the practical floor for the current design. Known work to get there: below Wear OS 6, passive heart rate is guarded by `BODY_SENSORS`, not `READ_HEART_RATE`, so the permission set must be chosen by `Build.VERSION.SDK_INT` (the old viewer did this; the UII flow does not). Fall events are already gated by a runtime capability check, so a watch without them degrades to signals plus manual SOS. Method: build with `minSdk = 34`, run lint, run the existing tests, then exercise pairing, signals, SOS and reboot on a Wear OS 5 emulator image; raise the floor back up for any API that fails. Alternative: stay at 36. Rejected unless the owner's watch and relatives' watches are all Wear OS 6, because a floor that excludes most installed watches defeats the purpose.

**6. Remove the sensor viewer rather than hide it.**
Delete `MainActivity`, `AndroidSensorRepository`, `SensorInfo`, `SensorRepository`, `SensorStateStore`, their test, the "Cảm biến" buttons and the manifest entry. The abandoned `wearos-sensor-dashboard` change is archived with `--skip-specs` so no capability is created from a feature that is no longer shipped. Alternative: keep it as a debug-only screen. Rejected: it can be recovered from git history and its permission logic is the part that was already superseded.

**7. Measure before changing the sampling rate.**
50 Hz with 2 s batching was chosen for useful raw bursts around an alert. Whether it fits a day of battery is unknown. The validation measures three configurations on real hardware over comparable wear periods: monitoring off (baseline), current (50 Hz / 2 s), and a cheaper candidate (for example 25 Hz and a longer batching latency). Battery data comes from the system's battery statistics and level readings sampled over at least six hours per configuration. The sampling constants become named, documented values so a change is a one-line decision. Whether a change is needed is decided from the data and recorded in the validation record; no rate change is made on assumption. If the budget fails, the first lever is a lower rate or longer batching; the second is a duty cycle around quiet periods, which is a larger change and would get its own proposal.

**8. Fall latency is measured from the system event, using a debug-only trigger.**
A person is never made to fall for a test. On the emulator, the synthetic health data tooling raises the event. On real hardware, a debug-only entry point invokes the same code path the system event does, and the delay is read as the difference between the alert's detection time and the server's creation time, minus the configured cancel window. The OS-level delay before the event is vendor behaviour and is only noted from observation. The debug entry point is excluded from release by living in the debug source set.

**9. Diagnostics are text lines on the existing status screen.**
Add app version, server host, and one line per needed permission and capability; keep the existing queue and last-upload lines. When monitoring is off, name the cause and keep the single existing "Cấp quyền" action. Alternative: a separate diagnostics screen. Rejected: the wearer needs this at a glance on a small display, and one screen is easier to read out over the phone.

**10. Validation is a written record, not a script alone.**
`docs/validation/<watch-model>-<version>.md` lists each spec requirement with the date, method, measured values and pass or fail. Helper scripts under `tools/` collect the numbers (battery level over time, upload timestamps from the server) but the pass/fail judgement and the release gate are the record.

## Risks / Trade-offs

- [Battery budget not met at 50 Hz] → decision 7 measures first and has a cheaper configuration ready to compare; release is held until it passes.
- [Vendor power management (for example Samsung's sleeping-apps and auto-optimisation) stops monitoring] → status screen reports monitoring off with the cause; the install guide lists the per-vendor settings to switch off; the overnight scenario is part of validation.
- [Wear OS system auto-reset of permissions for unused apps] → the status screen detects a lost permission (already partly done); validation includes a multi-day idle check on the real watch.
- [Watch lacks system fall detection] → documented as a hardware limit; the app states it on screen; the wearer's relatives must not assume fall alerts for that watch.
- [Phone link or Wi-Fi drops often on a real watch] → outbox already persists and retries; validation exercises 10-minute and 1-hour outages; if delivery lags more than expected the retry schedule is tuned from the recorded data.
- [Lowering `minSdk` reveals behavioural differences between Wear OS 5 and 6] → test on both emulator images and on the real model; raise the floor for any API that fails and record why.
- [Signing key lost or leaked] → loss means a new application id and re-pairing; leak means anyone could ship an update. Mitigation: key outside the repo, backed up in the owner's password manager, passwords never written into the repository or this change.
- [Production server is not ready] → pairing against `https://uii.studev.net` needs web migrations `0003`–`0005` applied and the current web build deployed; tasks include a pre-flight check before any hardware test.

## Migration Plan

1. Apply the web migrations and redeploy the web app (owner action); confirm with a pairing code from the Devices page.
2. Land the build changes (application id, signing, debug-only flags, scaffold removal) and verify on the emulator.
3. Install the release build over ADB on the real watch, pair, and run the validation record.
4. Rollback: an old build installs over a new one only with `-d` and the same key; since the application id changes once, the emulator builds are removed and reinstalled, and nothing real depends on them.

## Open Questions

- Which watch model(s) will be used by the wearer and by the other people who may install the app? This only decides which models get a validation record, not what is built.
- Is 24 hours the right battery target for this wearer (for example a nightly charge routine versus every two days)? The spec uses 24 hours as the conservative default; a different number only changes a threshold.

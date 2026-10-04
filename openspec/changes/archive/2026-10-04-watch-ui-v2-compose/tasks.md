## 1. Build foundation

- [x] 1.1 Find the Kotlin version bundled with AGP 9.4.0 and record it; remove `enableKotlin = false`, add the `org.jetbrains.kotlin.plugin.compose` plugin at that version and the Wear Compose 1.7.0 dependencies (`foundation`, `material3`, `navigation`, `ui-tooling` for debug) plus `activity-compose` through `gradle/libs.versions.toml`; verify `./gradlew assembleDebug assembleRelease testDebugUnitTest` succeeds and note both APK sizes against the previous 14.6 MB debug and release sizes.
- [x] 1.2 Add `UiiWatchTheme` (colour scheme from the `watch/*` tokens, `StatusColors` for ok and warn, typography) with a single preview composable; verify it renders in the Compose preview and in a debug-only launch on the emulator with the right background and accent.
- [x] 1.3 Add the vector icon drawables (heart, check, alert, cloud, link, bell, bolt, arrow-down, close, backspace) from the Figma shapes and all Vietnamese strings in `strings.xml`; verify a screen listing every icon renders at 24 and 64 dp without distortion.

## 2. State model

- [x] 2.1 Write the pure `HomeState` model and its mapping from plain inputs (paired, refused flag, permissions, monitoring status, queue size, fall support, last heart rate and its age), and verify unit tests cover all five states, each missing-permission reason, a stale versus fresh heart rate, and refused versus never-paired.
- [x] 2.2 Add a lifecycle-aware two-second poll that builds the state from `Uii`/`UiiState`, and a `FileOutbox` lookup by item id; verify with an instrumented or unit test that a queued item reads as waiting and a removed one does not.

## 3. Home and details

- [x] 3.1 Build the home screen (status ring, heart rate or placeholder, always-visible SOS edge button) for the OK, attention, waiting, unpaired and refused states; verify screenshots on the Wear OS 5 emulator match Figma frames `48:190`, `48:203`, `48:215`, `48:228` and `50:198`.
- [x] 3.2 Make the ring open the Details screen, or start the permission request / app settings from the attention state, and build Details (five one-word rows with marks, small footer with version, host, queue, last upload); verify against frame `50:208`, including the warning row when the fall capability is absent (unit-level) and revoked permissions via `adb shell pm revoke`.
- [x] 3.3 Verify the home screen at the largest system font scale (`adb shell settings put system font_scale 1.3` or the maximum offered): SOS fully visible and no clipped label, in every state.

## 4. Pairing

- [x] 4.1 Build code entry (eight progress dots, round keypad with delete and confirm), the short error states mapped from `Pairing.Result`, and the success path that starts monitoring and the permission requests; verify against frame `49:187` and with a pairing code against the local test server (success, wrong code, rate limit, too many devices, offline).
- [x] 4.2 Build the re-pair screen shown when the server refuses the token; verify by revoking the device on the test server, sending an alert, and checking the screen appears and its button opens code entry while the queued alert is kept.

## 5. SOS and fall flow

- [x] 5.1 Build the SOS choices and the five-second countdown ring with Cancel; verify against frames `49:226` and `49:246` that all three choices fit without scrolling and Cancel sends nothing (server shows no alert).
- [x] 5.2 Build the delivery confirmation driven by the outbox (sent only after acceptance, waiting otherwise, switching to sent if it succeeds while open); verify online, and with the emulator network disabled (`adb shell svc wifi disable` or airplane mode) that it never shows sent until the server stored the alert.
- [x] 5.3 Build `FallPromptActivity` and attach it to the fall notification as a full-screen intent with the existing notification as fallback; verify with the debug fall broadcast that the prompt appears with the countdown, that "Tôi ổn" cancels (no server alert, later fall still reported), that doing nothing sends it within the existing timing, and that tapping the prompt background does not cancel.
- [x] 5.4 Verify the fall prompt when the system does not allow full-screen intents (permission revoked or screen off): the notification with the single action appears and behaves the same.

## 6. Navigation, cleanup and checks

- [x] 6.1 Wire all routes in the swipe-dismissable navigation graph, make the Compose activity the launcher, and verify swipe-back from Details, SOS, countdown and pairing and that no back buttons remain.
- [x] 6.2 Add spoken labels to every ring, icon-only button and progress element; verify with `uiautomator dump` that each has a non-empty content description in Vietnamese, and audit all main-screen text against the three-words rule.
- [x] 6.3 Delete `HomeActivity.java`, `PairActivity.java`, `SosActivity.java`, `Ui.java` and `StatusText.java`, rewrite the remaining tests, and verify the full unit suite and a release build pass and the manifest has one launcher activity.
- [x] 6.4 Re-run the earlier emulator checks on Wear OS 5 and 6 with the new UI (pair, background signals, SOS, fall via debug trigger, revoke, permission loss, reboot resume) and record results, plus cold-start-to-SOS timing and APK size, in `docs/validation/ui-v2-notes.md`; update `docs/install.md` screenshots or wording that mentions removed buttons.

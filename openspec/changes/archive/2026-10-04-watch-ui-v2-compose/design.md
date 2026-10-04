## Context

Today (see `real-watch-readiness`): three Java activities build views by hand through `Ui.java` (`HomeActivity` 158 lines, `PairActivity` 117, `SosActivity` 66); `StatusText` formats ten lines of status; `Notifications` posts the fall notification with a single "Tôi ổn" action; the Java core (`Uii`, `UiiState`, `FileOutbox`, `Uploader`, services, receivers) is independent of the screens and tested. Build: AGP 9.4.0, `compileSdk 37`, `minSdk 34`, `enableKotlin = false`, no Kotlin sources, no `libs.versions.toml` entries beyond the AGP plugin.

The target design is in Figma file `5l2Tk6EEHJLlZ0WdB5zqKb`, page `Pages`, row "Watch app v2" (frames `48:190`, `48:203`, `48:215`, `48:228`, `49:187`, `49:226`, `49:246`, `49:259`, `50:187`, `50:198`, `50:208`, principles card `50:233`). Frames are 454 px round (227 dp at 320 dpi); colours are the `watch/*` variables including new `watch/ok` and `watch/warn`.

Documentation checked while planning: Wear Compose 1.7.0 is the current stable release of `androidx.wear.compose` (foundation, material3, navigation, ui-tooling), and Material 3 is the recommended library, replacing the Material 2 `compose-material`. With AGP 9 Kotlin is built in; the Compose compiler is added with `org.jetbrains.kotlin.plugin.compose` at the Kotlin version AGP uses. See `proposal.md` for motivation and `specs/` for behaviour.

## Goals / Non-Goals

**Goals:**
- Screens that match the v2 Figma frames, readable in a second, with SOS always on screen.
- No regression in the proven core: outbox, uploader, boot resume, fall hold-back and cancel, permissions per Android level.
- Round-screen layout handled by the library, not by hand measurement.

**Non-Goals:**
- No change to the server API, the signal payloads, or detection logic.
- No Tiles, complications, ambient (always-on) rendering, or custom haptics.
- No port of the Java core to Kotlin; only new code is Kotlin.

## Decisions

**1. Compose for Wear OS Material 3 1.7.0 with built-in Kotlin.**
Remove `enableKotlin = false`, add the `org.jetbrains.kotlin.plugin.compose` plugin at the Kotlin version bundled with AGP 9.4.0 (confirmed in the first task), and depend on `compose-foundation`, `compose-material3`, `compose-navigation` and `androidx.activity:activity-compose`, with versions in `libs.versions.toml`. Alternatives: keep views and polish them (rejected: rings, edge buttons and round-aware lists are the point and are hand work in views); use Material 2 Wear (rejected: superseded).

**2. One hosting activity with a swipe-dismissable navigation graph.**
`HomeActivity` (Kotlin, `ComponentActivity`) hosts a `SwipeDismissableNavHost` with routes `home`, `details`, `pair`, `sos`, `countdown/{type}`, `sent/{id}`; this gives swipe-to-go-back and removes `PairActivity` and `SosActivity`. Alternative: keep an activity per screen (rejected: duplicates theme setup and defeats shared back-stack handling). The fall prompt is its own small activity (decision 6) because it must start from a notification.

**3. A pure state model drives the home screen.**
A Kotlin `HomeState` (OK, ATTENTION(reason), WAITING(count), UNPAIRED, REFUSED) is computed from plain values (paired, revoked flag, per-permission granted, monitoring status, queue size, fall support) by a pure function with unit tests; it replaces the formatting role of `StatusText`. The composable only renders the state. The values are polled every two seconds while the screen is resumed (as today) using a lifecycle-aware flow. Alternative: observe `UiiState` with listeners (rejected: needs new plumbing in a core that works).

**4. Theme and tokens.**
`UiiWatchTheme` maps `watch/bg`→background, `watch/primary`→onBackground, `watch/surface`→surface container, `watch/secondary`→onSurfaceVariant, `watch/accent`→primary (SOS and primary buttons). Status colours (`ok`, `warn`) live in a `StatusColors` composition local because Material 3 has no slots for them. Typography is the Material 3 Wear scale on the system Roboto; sizes from the Figma frames are used as a check, not copied in pixels. Colour values are defined once in code with a comment pointing at the Figma variable names.

**5. Icons are vector drawables made from the Figma shapes.**
Eight to ten simple icons (heart, check, alert, cloud, link, bell, bolt, down arrow, close, backspace) added as `res/drawable` vectors. Alternative: `material-icons-extended` (rejected: large dependency for a handful of glyphs).

**6. Fall prompt in front of the app; notification everywhere else (revised during implementation).**
The plan was a full-screen intent from the notification. Testing on the Wear OS 5 and 6 emulators showed the system ignores it ("Fullscreen flag not enabled"), so a background service cannot open an activity over the lock screen or another app. The prompt (`FallPromptActivity`: "Bạn ổn chứ?", a ring counting down the cancel window, one large "Tôi ổn" button) is therefore started by `FallAlerts` only while the app's screens are in front, and then **no notification is posted**, because the system alert chip lands exactly on the button and swallows taps. If the wearer leaves the prompt before the window ends, it posts the notification itself. In every other case the existing heads-up notification with its single "Tôi ổn" action is the interface, unchanged. Cancelling still goes through `FallAlerts.cancel`, so the held-back outbox logic is untouched, and tapping anywhere but the button or the action never cancels.

**7. Truthful "sent" state from the outbox.**
After the SOS countdown the alert is queued (as today) and the confirmation screen observes the outbox: the item present means waiting, item gone with a successful upload means sent. The uploader already removes items only on acceptance, so presence is a reliable signal; a small query on `FileOutbox` by id is the only core addition. Alternative: have the uploader emit an event (rejected: more change to proven code).

**8. Accessibility is part of the build, not a polish step.**
Every icon-only or ring element carries a `contentDescription`/semantics label from `strings.xml` (Vietnamese). With text this short, font scaling is the layout risk, so each screen is checked at the largest font scale. All user-visible text moves to `strings.xml`.

**9. Remove the old screens after parity, not before.**
The Java activities stay until the new graph passes the same emulator checks as before (pair, SOS, fall, revoke, permission loss, reboot); then `HomeActivity.java`, `PairActivity.java`, `SosActivity.java`, `Ui.java` and `StatusText.java` are deleted with their tests rewritten against the new state model.

## Risks / Trade-offs

- [APK size and cold start grow with Compose] → record APK size and the time from tapping the launcher to a usable SOS before and after; if the SOS path is slower than the old one by a noticeable margin, add a baseline profile.
- [Full-screen fall prompt not allowed by the system] → confirmed not allowed on Wear OS; see decision 6: the notification is the main path and the prompt is a bonus while the app is in front.
- [Compose on the 512 MB emulator and on small watches uses more memory] → check for jank and out-of-memory on the Wear OS 5 emulator and on the real watch in `real-watch-readiness` task 7.x.
- [Queued alerts survive a refused pairing and would be sent under a new device token after re-pairing] → acceptable for the same wearer re-pairing (the alert's own detection time is kept); the server attributes by device owner, so the Details screen shows the queue and a re-pair as a different person is out of scope.
- [A rewrite can regress proven behaviour] → decision 9 keeps old screens until parity; behaviour tests of the Java core are untouched.
- [Material 3 Wear components may not reproduce the Figma exactly] → differences are recorded; Figma is the reference for layout and colour, the library for spacing, hit targets and round-screen handling.

## Migration Plan

1. Add the Compose/Kotlin build, theme and icons; keep the Java screens working.
2. Build the state model and the new screens behind a new navigation graph; switch the launcher to it.
3. Verify on the Wear OS 5 and 6 emulators against the Figma frames and the specs; then delete the old screens.
4. Rollback: revert to the commit before the launcher switch; the Java core and data formats are untouched, so a downgrade needs no data migration.

## Open Questions

- Which Kotlin version AGP 9.4.0 bundles, and therefore which Compose compiler plugin version to pin; answered by the first task.
- Whether the user wants the fall prompt to vibrate in a distinct pattern (not designed in Figma; left out until asked).

## Why

The watch screens work but are built for engineers: the home screen is ten lines of status text, the SOS button competes with them, buttons carry sentences, and the layout is hand-measured with plain Android views, so content gets cut by the round bezel and there is no consistent component set. The people who wear it are ordinary users, often during or right after a frightening event; they need to read the state in one second and press one obvious thing. A clean, low-text design (Figma page `Pages`, row "Watch app v2", 11 screens) has been drawn; this change turns it into the app.

## What Changes

- Replace the view-based screens (`HomeActivity`, `PairActivity`, `SosActivity`, `Ui`, `StatusText` rendering) with **Jetpack Compose for Wear OS, Material 3** (`androidx.wear.compose` 1.7.0), themed with the existing `watch/*` colour tokens plus new status colours (ok green, warning amber).
- **Home** becomes a status ring (green OK, amber needs attention, grey waiting for network, plus the unpaired and server-refused states) showing the latest heart rate, with the SOS button always on screen. Every technical detail moves to a **Details** screen one tap away.
- **SOS flow**: three icon options, a 5-second countdown ring with Cancel, and a confirmation that says "sent" only after the server accepted the alert, otherwise "waiting for network".
- **Fall prompt**: a full-screen "Bạn ổn chứ?" with a countdown and one large "Tôi ổn" button, with the existing notification as the fallback where the system will not show a full-screen alert.
- **Pairing**: eight progress dots instead of masked text, round keys, and short icon-led error messages instead of sentences.
- Navigation uses swipe-to-go-back; the "Quay lại" buttons disappear.
- Every icon-only element gets a spoken label, and layouts must survive a larger system font.
- Remove the old view screens once the new ones pass the same emulator checks.

## Capabilities

### New Capabilities

- `watch-home-status`: The home screen's glanceable states, the always-available SOS button, the heart-rate readout, the one-tap Details screen, text economy and accessibility rules.
- `watch-sos-and-fall-flow`: Manual SOS selection, countdown and cancel, truthful delivery confirmation, and the fall prompt with its single cancel action.
- `watch-pairing-ui`: Code entry with progress dots, short error states, and the re-pair screen when the server drops the watch.

### Modified Capabilities

- None in `openspec/specs/` (empty). Note: the in-flight change `real-watch-readiness` requires a status screen that shows version, server, permissions and queue. Its wording is relaxed to "the status screen or a Details screen one tap away" so both changes agree.

## Impact

- `watchface/build.gradle.kts` (enable Kotlin, add the Compose compiler plugin and Wear Compose dependencies, remove `enableKotlin = false`), `gradle/libs.versions.toml`.
- New Kotlin UI code and Android resources (vector icons, strings); the Java `Uii`, `UiiState`, outbox, uploader and services stay as they are.
- `AndroidManifest.xml` (single hosting activity, possible full-screen-intent permission), `Notifications`, `FallAlerts` and `SosActivity`'s send path, `Pairing` result handling for the new error mapping.
- Larger APK and a Kotlin/Compose build step; emulator verification of earlier work must be repeated for the new screens.
- Depends on `real-watch-readiness` for the signed-release and test tooling; does not change the server API.
- Out of scope: seizure or pre-seizure detection, GPS, medication reminders, Tiles, complications, always-on (ambient) mode, haptic patterns.

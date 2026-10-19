# Installing UII on a Wear OS watch

The app is installed by hand (sideloading) with ADB; it is not on the Play Store. Sections 2, 3, 5 and 6 were run on the
Wear OS 5 and 6 emulators. Section 4 (ADB over Wi-Fi) and the vendor settings in section 7 were **not yet verified on a
watch**.

## 1. What you need

- A watch running **Wear OS 5 (Android 14) or newer**. Older watches refuse the install.
- A Windows/macOS/Linux PC with Android Studio (its bundled JDK is enough) and `adb` from the Android SDK platform-tools.
- The web app deployed at `https://uii.studev.net` with the device API live (migrations `0003` to `0005` applied). Quick check:
  `curl -s -o /dev/null -w "%{http_code}" -X POST https://uii.studev.net/api/device/pair -H "Content-Type: application/json" -d "{\"code\":\"00000000\"}"`
  must print `401`. A `404` means the web app on the server is out of date.

## 2. Create the signing key (once)

The release is signed with a key that must stay **outside this repository**. Losing it means a new application id and
pairing every watch again, so back it up (for example in a password manager).

```bash
keytool -genkeypair -v -keystore uii-release.jks -alias uii -keyalg RSA -keysize 4096 -validity 9125
```

Keep `uii-release.jks` somewhere private, then put its location and passwords in your **user-level**
`~/.gradle/gradle.properties` (not in the project):

```properties
uii.keystoreFile=C:/Users/you/secure/uii-release.jks
uii.keystorePassword=...
uii.keyAlias=uii
uii.keyPassword=...
```

The same four values can be given as environment variables `UII_KEYSTORE_FILE`, `UII_KEYSTORE_PASSWORD`,
`UII_KEY_ALIAS`, `UII_KEY_PASSWORD`. If any is missing the release build stops and names what is missing; it never
falls back to a debug signature.

## 3. Build

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"     # Windows/Git Bash; adjust for your OS
./gradlew assembleRelease
```

The signed package is `watchface/build/outputs/apk/release/watchface-release.apk` (about 2 MB: the release is shrunk with R8).
Keep `watchface/build/outputs/mapping/release/mapping.txt` of every build you give to someone: it turns an obfuscated crash trace back into class and method names. The release always talks to
`https://uii.studev.net` with a 20 second "Tôi ổn" window. The Gradle properties `uii.apiBaseUrl` and `uii.fallWindowMs`
only affect **debug** builds.

## 4. Connect the watch with ADB over Wi-Fi

1. On the watch: Settings > System > About > Versions, tap **Build number** seven times.
2. Settings > Developer options: turn on **ADB debugging** and **Wireless debugging**.
3. In Wireless debugging choose **Pair new device**; it shows an address, a port and a pairing code.
4. On the PC: `adb pair <address>:<pair-port>` and enter the code, then `adb connect <address>:<debug-port>` using the
   port shown on the Wireless debugging screen. `adb devices` must list the watch.

## 5. Install and grant

```bash
adb install -r watchface/build/outputs/apk/release/watchface-release.apk
```

Open **UII** on the watch. Pair it first (section 6); the app then asks for three permissions, in this order on Wear OS 5:
physical activity, notifications, and sensors (heart rate). On Wear OS 6 the heart-rate request is the health permission
instead. Allow all of them. If you refuse, the home ring turns amber ("Cấp quyền"); tap it to be asked again, and if Android no longer shows the dialog the app opens its page
in Settings where you can switch them on.

## 6. Pair with an account

1. On the web, sign in as the wearer and open **Thiết bị** (Devices); press the button to create a pairing code
   (8 digits, valid for 10 minutes, usable once).
2. On the watch: tap **Bắt đầu**, type the digits (eight dots fill as you type), then the orange tick.
3. The home screen should now show a **green ring** with the heart rate (or `--` until the first reading), and **SOS** at the bottom. Tap the ring for the Details screen: one row per check, with the app version and server in small text.
   The **SOS** button is the first thing on the screen.

Updating later is `adb install -r` with the same key; the pairing and the queued items survive.

To distribute through Google Play instead, see `docs/play-release.md`. A Play install and an `adb` install are signed
with different keys and cannot update each other: uninstall one before installing the other, then pair again.

## 7. Keep monitoring alive

Some watches stop background apps to save battery. Open the watch's battery or app settings and make sure UII is **not**
restricted (look for names such as "background usage", "sleeping apps" or "auto-optimisation"; the wording differs by
maker). **Not yet verified on a physical watch** - the overnight check in `docs/validation` decides what to add here.
If monitoring stops, the ring turns **amber** with "Cấp quyền"; a **grey** ring with a number means alerts are waiting for the network; a **"Cần ghép lại"** screen means the server removed this watch.

## 8. Uninstall / unpair

- Remove the device on the web (Thiết bị); the watch then shows "Cần ghép lại" the next time it contacts the server. To wipe a watch completely: `adb shell pm clear net.studev.uii`.
- `adb uninstall net.studev.uii`

## Developer tools (debug builds only)

- Fall alert without anyone falling:
  `adb shell am broadcast -n net.studev.uii/com.example.watchtest.uii.DebugFallReceiver -a net.studev.uii.DEBUG_FALL --ez force true`
- `tools/fall-latency.ps1` measures how long that alert takes to reach the server; `tools/measure-battery.ps1` records
  the battery level over time. See each script's header for usage.
- Point a debug build at a local server: `./gradlew assembleDebug -Puii.apiBaseUrl=http://10.0.2.2:3000`
  (`10.0.2.2` is the emulator's name for the PC).

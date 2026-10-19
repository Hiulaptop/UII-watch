# Releasing UII to Google Play (Wear OS)

Status of this guide: written 2026-10-15 for version **0.0.1**. The build steps were run; the Play Console steps were
**not** (they need your account), so menu names may differ slightly from what your console shows.

## 0. What is already prepared

| Item | Where |
|---|---|
| Version `0.0.1` (single source) and derived `versionCode` | `watchface/build.gradle.kts` (`appVersionName`) |
| Application id `net.studev.uii`, standalone Wear OS app, targets API 37, minSdk 34 | `watchface/build.gradle.kts`, `AndroidManifest.xml` |
| Launcher icon (adaptive, heart on dark) and 512x512 store icon | `res/mipmap-anydpi`, `docs/play-store/icon-512.png` |
| In-app consent + medical disclaimer, shown before any sensor permission or monitoring | `ConsentScreen.kt`, `Monitoring.start` |
| Public privacy policy page | UII-web `/privacy` (set `PRIVACY_CONTACT_EMAIL`) |
| Android App Bundle build, R8 shrunk, no debug code | `./gradlew :watchface:bundleRelease` |

Checked on the release build: package `net.studev.uii`, versionCode 100, versionName 0.0.1, no `RandomDetector` /
`DEBUG_DETECT` in the dex, no `ACCESS_LOCAL_NETWORK`, launcher icon present, AAB about 3.4 MB.

## 1. Version numbers

`versionCode = major*1_000_000 + minor*10_000 + patch*100 + build`.

| versionName | build | versionCode |
|---|---|---|
| 0.0.1 | 0 | 100 |
| 0.0.1 (second upload) | `-Puii.build=1` | 101 |
| 0.1.0 | 0 | 10_000 |
| 1.0.0 | 0 | 1_000_000 |

Play rejects an upload whose versionCode is not higher than every earlier one on the app, so every upload needs a new
number: bump the version, or pass `-Puii.build=N` for a re-upload of the same version.

## 2. One-time: the upload key

Follow section 2 of `docs/install.md` (`keytool ...`, then the four `uii.*` settings in `~/.gradle/gradle.properties`).
With Play App Signing this key is only the **upload key**; Google holds the key users' devices verify. If you lose the
upload key you can ask Google to reset it, which is much better than the sideload-only situation described in
`install.md`.

**Consequence you must know:** a build installed from Play is signed with Google's key, a build installed with
`adb install` is signed with yours. Android will not update one with the other. Switching a watch from sideloaded to
Play means uninstall, install from Play, and pair again. After that, updates are seamless.

## 3. Build the bundle

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew :watchface:testDebugUnitTest :watchface:bundleRelease
# second upload of the same version: add -Puii.build=1
```

Output: `watchface/build/outputs/bundle/release/watchface-release.aab`. R8 mapping is inside the bundle; Play shows
deobfuscated crashes.

## 4. Play Console

1. **Create app** (name UII, default language Vietnamese, App, Free).
2. **Test and release -> Advanced settings -> Form factors -> Add form factor -> Wear OS.** Wear OS has its own tracks.
3. **Test and release -> Testing -> Internal testing** (the Wear OS one) -> create release -> accept Play App Signing ->
   upload the `.aab` -> release notes -> save, review, roll out.
4. **Testers:** create an email list with the Google account that is signed in on the test watch, add it to the track,
   copy the opt-in link, open it on a phone or browser logged into that account, accept, then install from the Play
   Store on the watch.
5. Complete **Policy -> App content** (section 6) and **Store presence -> Main store listing** (section 5). Internal
   testing can start before every item is finished; production cannot.

Personal developer accounts created after 13 Nov 2023 must run a closed test with 12 testers for 14 continuous days
before they can request production access (Google Play Console Help, "testing requirements for new personal developer
accounts"). Internal testing is not subject to this. If your account is an organisation account the rule may not apply;
check the Dashboard for the "production access" task.

## 5. Store listing (draft)

- **Name:** UII
- **Short description (80):** Cảnh báo té ngã và dấu hiệu bất thường trên đồng hồ, báo ngay cho người thân.
- **Full description:**
  > UII chạy trên đồng hồ Wear OS để theo dõi nhịp tim và chuyển động, phát hiện té ngã và dấu hiệu bất thường, rồi báo cho
  > người thân trong vòng kết nối của bạn.
  >
  > • Báo động chủ động: chạm SOS, chọn tình huống, đếm ngược 5 giây để hủy nếu bấm nhầm.
  > • Báo động tự động: khi phát hiện nguy cơ, đồng hồ hỏi "Bạn ổn chứ?"; nếu không trả lời, cảnh báo được gửi.
  > • Thẻ y tế trên đồng hồ sau khi cảnh báo đã gửi, giúp người ở gần hỗ trợ.
  > • Người thân theo dõi trên trang web UII và nhận thông báo.
  >
  > Lưu ý: UII chỉ hỗ trợ cảnh báo, không phải thiết bị y tế, không chẩn đoán hay ngăn ngừa bệnh và không thay thế bác sĩ hoặc
  > cấp cứu. Cần có tài khoản UII và ghép nối đồng hồ bằng mã từ trang web.
- **App icon:** `docs/play-store/icon-512.png`
- **Screenshots:** not produced yet. Take 4 from an emulator or the watch (consent, home, SOS menu, countdown or
  "Bạn ổn chứ?"): `adb exec-out screencap -p > shot1.png`, then check the size limits shown in the console.
- **Privacy policy URL:** `https://<your web domain>/privacy`
- **Category:** the "Health & Fitness" vs "Medical" choice is yours; see "Open decisions".

## 6. Declarations to complete

### Data safety (draft answers, review against your real setup)

| Data | Collected | Shared | Purpose | Optional |
|---|---|---|---|---|
| Heart rate, physical activity (motion, steps) | Yes | With the wearer's own circle | App functionality | No (core) |
| Health info the user types (blood type, allergies, medications, conditions) | Yes | With the wearer's circle; shown on the watch after an alert | App functionality | Yes |
| Name, email | Yes | No | Account management | No |
| Approximate/precise location | Not yet collected | - | - | - |
| Data encrypted in transit | Yes (HTTPS only in release) | | | |
| Users can request deletion | Yes (Settings -> delete account) | | | |

### Health apps declaration and sensitive permissions

Google's Health apps policy lists `BODY_SENSORS`, `BODY_SENSORS_BACKGROUND`, `ACCESS_BACKGROUND_LOCATION` and
`ACTIVITY_RECOGNITION` as health-related permissions that need a prominent in-app disclosure and affirmative consent. The
consent screen added in this release is that disclosure. Permissions in the manifest and why:

| Permission | Why |
|---|---|
| `health.READ_HEART_RATE` / `BODY_SENSORS` (<= API 35) | Heart rate for alerts and the dashboard |
| `ACTIVITY_RECOGNITION` | Health Services passive monitoring and fall events |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_HEALTH` | Continuous motion monitoring while worn |
| `POST_NOTIFICATIONS` | The "Bạn ổn chứ?" prompt and its single "Tôi ổn" action |
| `RECEIVE_BOOT_COMPLETED` | Resume monitoring after restart or update |
| `VIBRATE` | Warning vibration |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Send alerts and samples |

### Foreground service declaration (type `health`)

Describe: continuous accelerometer reading while the watch is worn so a fall or abnormal movement can be detected and
reported within seconds; if the system stopped it the wearer would not be warned and relatives would not be notified.
Google asks for a video link that shows how a user triggers the feature: record the watch screen showing pairing,
consent, the home ring turning green (monitoring on), and the persistent monitoring notification.

### Other

Content rating questionnaire, target audience, ads (none), government app (no), financial features (none). **Target
audience is your decision** (see below).

## 7. Test plan for the first testers

1. Install from Play, open UII, read and accept the consent screen, grant permissions.
2. Create a pairing code on the web Devices page, pair; home ring turns green.
3. SOS -> pick a situation -> cancel before the count ends: nothing arrives on the web. Repeat and let it finish: alert
   arrives, "Đã gửi" shows, the medical card appears.
4. Turn off Wi-Fi/Bluetooth, trigger SOS: "Chờ mạng"; reconnect: it arrives once.
5. Reboot the watch, open nothing: monitoring resumes (heart rate appears on the web within minutes).
6. Install the next build from Play: pairing and queued items survive.

## 8. Updating later

1. Change `appVersionName` (or pass `-Puii.build=N`), run tests, `bundleRelease`.
2. Upload the new bundle to Internal testing -> release notes -> roll out. Testers get it through Play auto-update.
3. When ready for others: promote the same release to Closed testing, then Production with a **staged rollout**
   (for example 10% -> 50% -> 100%) and watch crashes/ANRs in Play Console before widening.
4. Always deploy the web app first when a watch release adds or changes an endpoint; older watches stay in the field
   for days. Keep the device API backward compatible.
5. Consent text changes: raise `UiiState.CONSENT_VERSION`; everyone is asked again on next open.

## 9. Open decisions and known gaps

- **Account type and signing choice.** Whether you must run the 12-tester closed test depends on the account type. The
  Play-managed signing recommended above forces a one-time uninstall/re-pair of sideloaded watches.
- **Target audience.** Not decided. The privacy page deliberately makes no statement about children; decide and update
  it, because audience choices change which Play policies apply.
- **Category and medical claims.** The listing text avoids "diagnose/prevent" language. Google's Health apps policy
  distinguishes health/fitness apps from medical apps; Google's published page does not say how it treats seizure or fall
  detection, so you may be asked questions during review.
- **Contact email** for the privacy page (`PRIVACY_CONTACT_EMAIL`) and a store listing support email.
- **Background body-sensor permission.** The manifest requests heart rate but **not** `BODY_SENSORS_BACKGROUND`
  (API 33-35) or `READ_HEALTH_DATA_IN_BACKGROUND` (API 36+), which Google's Health Services documentation lists for
  reading body sensors in the background through `PassiveMonitoringClient`. The motion service is a foreground service,
  but passive heart rate while the app is not in front may be limited without them. Verify on a real watch with the
  app closed before relying on it; adding the permission also requires the Play declaration and a consent update.
- **Retention.** No automatic deletion exists for samples/alerts; the privacy page says so. Decide a period.
- **Screenshots** and a **feature video** for the foreground-service declaration still have to be recorded.
- **Existing sideloaded watches** will stop monitoring after updating to this build until the wearer opens the app and
  accepts the consent screen. This is intentional.

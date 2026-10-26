## 1. Spikes and contract

- [ ] 1.1 On a Wear OS 5 and a Wear OS 6 emulator, and on a real watch if available: can `MotionService` (health FGS started from boot) read a one-shot fix with only `ACCESS_FINE_LOCATION`? Record the answer and pick the permission set (design, Decision 6).
- [x] 1.2 Tile source decided with the owner: self-hosted PMTiles. Background location decided: ask for it.

## 2. Server (UII-web)

- [x] 2.1 Read the relevant guide in `node_modules/next/dist/docs/` first (`UII-web/AGENTS.md`): this Next.js has breaking changes.
- [x] 2.2 Migration `0007_alert_locations.sql`: `alerts.accuracy_m`, `alerts.located_at`, table `alert_locations`, RLS on. (Run it in the Supabase SQL editor before deploying the new web build; the apply-0004-0006 bundle is not extended.)
- [x] 2.3 `schemas.ts`: add `accuracy_m` and `fix_time` to `alertBodySchema`; refine so `lat` and `lng` come together; add `locationBodySchema` and its byte limit. Unit tests in `tests/device.test.mjs`.
- [x] 2.4 `ingest.ts`: store `accuracy_m` and `located_at` with the alert and write the first fix to `alert_locations`; add `ingestLocation` (newer-or-more-accurate rule, 6-row cap, 15-minute window, publish `alert_updated`).
- [x] 2.5 `app/api/device/alerts/location/route.ts`: authenticate device, rate limit, validate, call `ingestLocation`.
- [x] 2.6 `lib/alerts.ts`: return the `alert_locations` rows in `getAlertDetail`.
- [x] 2.7 Account export and account deletion include `alert_locations`.

## 3. Watch (UII-watch)

- [x] 3.1 Manifest: `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`. `MotionService` keeps its `health` type.
- [x] 3.2 `LocationPolicy` (pure, unit-tested): milestone times, "is this fix good enough" (accuracy 50 m), "is the last known fix fresh" (10 min), "is it worth sending" (newer or more accurate).
- [x] 3.3 `LocationTracker`: one-shot reads on its own executor, keeps the best fix in memory, schedules L0, L2 and L3 for an incident and cancels them when the alert is cancelled or the watch is unpaired.
- [x] 3.4 `Payloads.alert` takes an optional fix and writes `lat`, `lng`, `accuracy_m`, `fix_time` only when all are in range. `Payloads.location(clientId, fix)` builds the update body. Tests next to the existing `PayloadsTest`.
- [x] 3.5 `Outbox.Kind.LOCATION`, `Api.postAlertLocation`, `ApiClient`, `Uploader` ordering (ALERT, LOCATION, SAMPLES); update the `FakeApi` and `PairingTest` fakes. Test ordering and that a location never precedes its alert.
- [x] 3.6 Wire the milestones: `FallAlerts.sendManual` and `PassiveAlerts.report` start the incident (L0), attach `LocationTracker.best()` at build time (L1), and `FallAlerts.cancel` and the countdown cancel stop it. Test that a missing or denied location leaves the alert payload and timing unchanged.
- [x] 3.7 `LocationScreen` asked once after consent (optional, not in `missingPermissions`; `CONSENT_VERSION` unchanged on purpose), informational Details row `row_location`.

## 4. Website map (UII-web)

- [x] 4.1 `npm i maplibre-gl`; client component `components/alerts/alert-map.tsx` (person icon, accuracy circle, trail, re-centre control) and a client wrapper with `dynamic(..., { ssr: false })`.
- [x] 4.2 Replace the "Vị trí" block of `app/alerts/[id]/page.tsx` with the map card; keep the no-position text, the coordinates line with "cập nhật HH:MM", and the Google Maps link.
- [x] 4.3 Self-hosted tiles: `GET /api/map/tiles` (signed-in, Range only), `map-style.ts`, `docker-compose.yml` volume, `MAP_DATA_DIR` in `.env.example`, `UII-web/docs/map-tiles.md`, in-page note when the file is missing.
- [ ] 4.4 Confirm the `LiveAlerts` refresh moves the icon without resetting the user's pan or zoom.

## 5. Verification

- [ ] 5.1 Emulator: `adb emu geo fix <lng> <lat>`, then trigger a manual SOS and a debug fall; the alert arrives with a position, the +2 and +5 min fixes arrive as updates, cancelling stops them.
- [ ] 5.2 Airplane mode during the incident: alert and locations queue and arrive in order after reconnect.
- [ ] 5.3 Location permission denied: SOS timing and payload identical to today; Home ring unaffected.
- [ ] 5.4 Another circle member can see the map; a non-member cannot; deleting the alert removes its locations.
- [ ] 5.5 Battery: scripted SOS with `tools/measure-battery.ps1`, compare with a baseline of the same duration with no incident; record in `docs/validation`.
- [ ] 5.6 `./gradlew testDebugUnitTest assembleDebug assembleRelease`, `npm test`, `npm run build` all pass.

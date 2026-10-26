## Context

- The server contract already has `lat` (-90..90) and `lng` (-180..180) on `alertBodySchema`, and `ingestAlert` writes them to `alerts.lat/lng`. `getAlertDetail` returns them and `app/alerts/[id]/page.tsx` prints them with a Google Maps link. Nothing on the watch sets them.
- Every alert on the watch is built in one of two places: `FallAlerts.sendManual` (button; sent at once) and `PassiveAlerts.report` (detection; held in the outbox for the 20 s cancel window, then flushed at `due`). Both read `Uii.freshHeartRate` and `RawBuffer.snapshot` to fill the payload.
- `MotionService` is a foreground service of type `health`, running whenever the watch is paired and monitoring. The outbox (`FileOutbox`) holds `ALERT` and `SAMPLES` items; `Uploader` sends alerts first and drops payloads the server rejects for good.
- Live updates to the website already exist: `publishToCircle` pushes `alert` / `alert_updated` events and `LiveAlerts` calls `router.refresh()`.
- `minSdk = 34`, so `LocationManager.getCurrentLocation` (API 30+) is available everywhere the app runs. Some target watches have no Google Play services, so the design must not depend on them.
- `NEXT_PUBLIC_*` values are inlined at build time by the `Dockerfile` (`ARG` + `ENV`), so a map style URL needs the same plumbing.

## Goals / Non-Goals

**Goals**
- Every SOS that can be located carries a position, without ever making the alert later.
- Zero location work when there is no incident; at most four fixes per incident.
- The map on the alert page shows where the wearer is and where they have been since the alert, and refreshes by itself.
- Refusing the location permission leaves everything else working exactly as today.

**Non-Goals**
- No continuous or periodic tracking, no "where is my relative now" outside an incident, no geofencing.
- No location in email / push bodies (see Decision 7).
- No Google Play services dependency on the watch; no routing or address lookup on the website.

## Decisions

### 1. Milestones, not tracking

A position is requested **one-shot** (never `requestLocationUpdates`) at four milestones, counted from the moment the incident starts. The incident starts when the wearer picks a type in the SOS menu, or when a passive detection fires.

| Milestone | When | Why |
|---|---|---|
| **L0 Trigger** | SOS type picked / detection fired | The 5 s countdown and the 20 s cancel window are idle time that a cold GPS fix needs anyway. |
| **L1 Send** | The moment the alert is built for the outbox | Attach the best position known *right now*; never wait. |
| **L2 +2 min** | 2 min after L0, only if the incident was not cancelled | The wearer may have been moved or may have wandered; improves a poor first fix. |
| **L3 +5 min** | 5 min after L0, then stop | Last update. After this the watch is back to zero location work. |

The four times are constants in `LocationPolicy` so they can be tuned from battery measurements without touching the flow. A cancelled alert ("Tôi ổn" or "Hủy") cancels the remaining milestones.

### 2. Never delay the alert

Location is **best effort around** the alert. `LocationTracker` keeps the best fix in memory. At L1 the alert is built with:

1. the L0 fix if it already arrived, else
2. the platform's last known position if it is younger than 10 minutes (free: no radio), else
3. no position.

An alert is never held back for a fix. When no good fix existed at send time, or its accuracy was worse than 100 m, the later milestones improve it (Decision 4).

### 3. How a fix is read

`LocationManager.getCurrentLocation` with a 20 s timeout and a cancellation signal, asking `GPS_PROVIDER` first and `NETWORK_PROVIDER` as a second request if the GPS one returns nothing. The first fix with accuracy of 50 m or better ends the request; otherwise the most accurate fix seen before the timeout is used. Platform `LocationManager` rather than the fused provider keeps the app free of Play services; the fused provider can be added later behind the same `LocationTracker` interface if measurements show a clear gain.

The reading runs inside `MotionService` (see Decision 6). It uses its own single-thread executor so a slow GPS never blocks sensor callbacks.

### 4. Updates ride the outbox

Later fixes are sent as `LOCATION` outbox items, not by rewriting the alert:

- New `Outbox.Kind.LOCATION`. Its `notBeforeMs` is the alert's `due`, so a location can never reach the server before the alert it belongs to. `Uploader` orders `ALERT`, then `LOCATION`, then `SAMPLES`.
- Body: `{ client_id, lat, lng, accuracy_m, fix_time }` where `client_id` is the alert's client id.
- Network down or process killed: the item stays in the outbox like any other and is sent later. A `REJECTED` answer (for example the alert was removed) drops it, as for other payloads.
- Unpairing already empties the outbox, so no location outlives the pairing.

### 5. Server contract and storage

- `alertBodySchema` gains optional `accuracy_m` (0..10000) and `fix_time` (ms; same bounds as `detected_at`). `lat` and `lng` must both be present or both absent.
- `POST /api/device/alerts/location` (device token, rate limited, same Zod style): finds the alert by `(device_id, client_id)`, inserts a row in `alert_locations`, and updates `alerts.lat/lng/accuracy_m/located_at` when the new fix is newer, or equally recent and more accurate, than the stored one. At most 6 rows per alert and nothing accepted more than 15 minutes after the alert; extra fixes are ignored with HTTP 200 so the watch drops them.
- Migration `0007`: `alerts.accuracy_m real`, `alerts.located_at timestamptz`, and `alert_locations(id, alert_id references alerts on delete cascade, t, lat, lng, accuracy_m, source text)`. RLS is enabled with no policy, like the other device tables; access goes through the server.
- After a stored update the server calls the existing publish path with `alert_updated`, so open pages refresh.

### 6. Permissions and the background question (decided)

A fall detected while the app is closed must still report where it happened, so the app asks for `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` and `ACCESS_BACKGROUND_LOCATION`. The owner chose to ask for the background permission.

- Android wants the background permission asked on its own after the foreground one, so `LocationScreen` runs the two requests one after the other.
- Both are **optional**: they are not in `Permissions.needed()` and not in `missingPermissions`, so refusing never changes the Home state. The Details screen shows an informational "Vị trí" row, and tapping it when not granted opens the question again.
- `MotionService` keeps its `health` foreground type unchanged. With the background permission the one-shot reads work without a `location` service type, which avoids a new way for the service to fail at start.
- **Still to verify on a device (task 1.1):** that a read started from a detection while the app is closed really returns a fix on Wear OS 5 and 6. If a watch refuses, the alert still goes out with the cached position or none.

### 7. Privacy

- Location is read only inside the four-milestone window of a real incident, and only after consent.
- Location has **its own question** (`LocationScreen`, answer stored as `locationChoice`), asked once after the consent and the normal permissions. `CONSENT_VERSION` is deliberately **not** bumped: raising it would stop monitoring on every paired watch until someone re-consented, which is worse for a safety app than asking one extra question.
- It is shown only to the same people who can already open that alert (`getSubjectAccess`), and `alert_locations` is deleted with the alert. Account export and account deletion (`lib/account/service.ts`) include it.
- Email and push notifications carry **no coordinates**, only the link to the alert page; a message can sit in a mailbox for years and a link needs a sign-in.
- Rows are never read for anything but the alert page. No retention job is needed beyond the alert's own lifetime.

### 8. The website map

- `maplibre-gl` (client only). A client component `AlertMap` loaded with `dynamic(..., { ssr: false })` from a small client wrapper, because MapLibre touches `window` on import.
- **Map content:** a person icon at the latest fix (a symbol layer using an SVG added with `map.addImage`, drawn in the site's graphite and ember-orange palette), a translucent circle of `accuracy_m` radius around it, and a thin line through earlier fixes with small dots, the oldest fading. The map fits the points on first load, then keeps the user's own panning; a "Về vị trí hiện tại" control re-centres on the latest fix.
- **Data:** `getAlertDetail` also returns the `alert_locations` rows. The page passes plain JSON to the component. When `LiveAlerts` triggers `router.refresh()`, the component receives new props and moves the icon with `easeTo`.
- **No position:** keep the current "Đồng hồ không gửi kèm vị trí." text.
- **Fallback link:** keep "Mở bản đồ" (Google Maps) under the map, since it also works in a navigation app.
- **Tiles (decided: self-hosted):** one PMTiles file on the server, mounted read-only at `/data/map` (`MAP_DATA_DIR` in `docker-compose.yml`). `GET /api/map/tiles` answers only signed-in users and only HTTP Range requests (at most 8 MB each), so the browser's `pmtiles` protocol reads just the tiles on screen. No third party sees who looks at which alert. The style is drawn in code (`map-style.ts`) in the site's warm greys, with no text labels so that no font files need hosting. Without the file the page still shows the marker and says the map data is missing. How to produce the file: `UII-web/docs/map-tiles.md`.
- The page already uses a text-heavy editorial style; the map is one rounded card in the existing grid, no new visual language.

### 9. Watch UI

No new screen. The Details screen gets one informational row "Vị trí" (check when a location permission is granted, warning otherwise) that is **not** part of the Home state rules. Strings: `row_location`, `consent_location`.

## Risks / Trade-offs

- **Cold GPS indoors gives nothing in 20 s.** Then the alert goes without a position and the +2 and +5 min fixes may still catch one; the network provider gives a coarse position (large `accuracy_m`) that the circle on the map makes obvious. Accepted: a coarse position is better than none, and the circle shows it honestly.
- **Battery:** at most four one-shot fixes per incident, measured in `tools/measure-battery.ps1` runs with a scripted SOS; incident frequency is rare by definition. If four fixes cost too much, drop L3 first.
- **Permission friction on a small screen:** background location is a heavy ask. It stays optional and the consent text says why.
- **Tile provider is an operating cost and a privacy surface** (the viewer's browser contacts it). Self-hosting a PMTiles file removes both at the price of some storage.
- **A fix can be old or wrong:** every stored fix carries `fix_time` and `accuracy_m`, and the UI shows "cập nhật HH:MM" so nobody trusts a stale point as live.

## Open Questions

1. **Which milestones to keep** after the first battery measurement (drop L3 first if needed).
2. **Size of the PMTiles file** for the area the circle lives in, once the owner has cut it.

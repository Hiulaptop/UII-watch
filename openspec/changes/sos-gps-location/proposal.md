## Why

When the wearer sends an SOS (by hand, or because the watch detected a fall or seizure), the circle is told *that* something happened but not *where*. The server already accepts `lat`/`lng` on an alert and stores them, and the alert page prints raw coordinates, but the watch never sends a position and the website has no map. A relative who gets the alert has to phone the wearer to ask where they are, which is exactly when the wearer may be unable to answer.

GPS is the most expensive thing on a watch. Reading it continuously would break the all-day battery budget the app is built around, and a position is only needed around an incident. So the position is taken at a few **fixed milestones around an SOS** and never otherwise.

## What Changes

- **Watch:** after the wearer's SOS or a passive detection, take at most four position fixes at fixed milestones (trigger, send, +2 min, +5 min). Nothing is read when there is no incident. Fixes ride the existing outbox, so they survive no network and a killed process.
- **Server:** extend the alert contract with `accuracy_m` and `fix_time`, add `POST /api/device/alerts/location` for later fixes of the same alert, and keep every fix in a new `alert_locations` table so the map can show the short trail. `alerts.lat/lng` keeps meaning "best current position".
- **Website:** replace the text "Vị trí" block on the alert page with a **MapLibre GL JS** map showing a person icon for the wearer, an accuracy circle, and the trail. The map moves on its own when a newer fix arrives, using the existing live-update channel.
- **Consent and permissions:** location is **optional**. The watch asks about location once after consent (including background location, so a fall detected with the app closed can still report where), and refusing it never turns the home ring amber.

## Capabilities

### New Capabilities

- `sos-location`: What position the watch sends with an SOS, when it is read, how it behaves offline and without permission, how the server stores it and who can see it, and how the website shows it.

### Modified Capabilities

- `watch-home-status`: the Details screen gains one informational "Vị trí" row. The Home ring logic is unchanged.
- `watch-sos-and-fall-flow`: an SOS may now carry a position; it must never be delayed waiting for one.

## Impact

- Watch: `AndroidManifest.xml` (location permissions), a new `LocationTracker`, `PassiveAlerts`, `FallAlerts.sendManual`, `Outbox` / `FileOutbox` / `Uploader` (new `LOCATION` kind), `Payloads`, `Api` / `ApiClient`, `Permissions`, `UiiState.CONSENT_VERSION`, `ConsentScreen` strings, Details row.
- Web: migration `0007`, `lib/device/schemas.ts`, `lib/device/ingest.ts`, new route `app/api/device/alerts/location/route.ts`, `lib/alerts.ts`, `app/alerts/[id]/page.tsx`, a new client component `components/alerts/alert-map.tsx`, `package.json` (`maplibre-gl`), `Dockerfile` and `docker-compose.yml` (a build-time map style URL), account export/delete (`lib/account/service.ts`).
- Privacy: precise location is sensitive. It is collected only after consent, only around an incident, shown only to people who can already see that alert, and deleted with it.

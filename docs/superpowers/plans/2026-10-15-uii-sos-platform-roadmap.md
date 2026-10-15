# UII SOS Platform Roadmap (watch + web, mobile-ready)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan phase by phase. Each phase is independently shippable and testable; write a detailed per-task plan (superpowers:writing-plans) at the start of each phase, after the phase before it has landed.

**Goal:** A watch that detects (or is told about) danger, warns the wearer, asks them to cancel false alarms, and otherwise sends an SOS with health info and live location to their circle, with the detection model replaceable behind a black-box interface.

**Architecture:** Keep the existing split: Wear OS app (`UII-watch`, Java/Kotlin) talks to the Next.js app (`UII-web`, Supabase) over the device HTTP API. Detection is a swappable `MotionDetector`; everything downstream of a `Detection` (cooldown prompt, alert, health card, location, escalation) is real and testable today with a random detector. The device API stays plain JSON + Zod contracts so a future mobile app reuses the same circle/alert endpoints.

**Tech Stack:** Java 17 + Kotlin/Compose for Wear OS (minSdk 34), Health Services, Android location APIs; Next.js (this repo's non-standard version: read `node_modules/next/dist/docs/` before writing web code, per `UII-web/AGENTS.md`), Supabase/Postgres, Zod, SSE hub already in `lib/notify`.

**Spec:** the feature list in the 2026-10-15 request; per-phase OpenSpec changes are created at the start of each phase.

## Global Constraints

- The random/test detector must be physically absent from release builds (debug source set only). A release build must never raise an alert without a real signal or a wearer tap.
- Heart rate + accelerometer stay the only sensor inputs (see 2026-10-13 plan).
- Cancel window for passive alerts is `Uii.FALL_CANCEL_WINDOW_MS` (20 s release); do not shorten it without field data.
- Health, medication and location data are sensitive personal data: collected only after explicit consent, minimum retention, shown only to circle members and, for the SOS card, only after an alert has fired.
- Existing guarantees stay: offline outbox, idempotent `client_id`, honest "Đã gửi" only after server acceptance.
- No new third-party paid service (SMS, voice, maps) without the owner's decision (see "Decisions needed").

---

## 1. What already exists vs. what is missing

| Requirement | Already built | Missing |
|---|---|---|
| Detect pre-seizure / fall / abnormal motion | OS fall event -> `FallAlerts`; 50 Hz accel; `MotionFeatures`/`FeatureWindower` (2026-10-14) | Detector abstraction; a way to simulate detections end to end |
| Pre-seizure **warning to the wearer** | Only a manual "sắp co giật" button | Local warning (haptic + "ngồi xuống") before anything is sent |
| **Passive SOS** with cooldown, cancel, then send | Fall prompt "Bạn ổn chứ?" + `Outbox` due time + cancel (`FallAlerts`) | Generalise from "fall only" to any detection kind; show **health info** after sending |
| **Active SOS** | 3 choices, 5 s countdown, send, honest confirmation | Fast path to **reach relatives**: acknowledgement, escalation, call fallback |
| **Live location** | Server schema accepts `lat`/`lng` on alerts; no watch code; location out of scope in earlier specs | Watch location (snapshot + live session), server table + stream, web map |
| Circle, notifications, SOS public card, medication, reports | All on the web (`lib/notify`, `lib/sos`, SSE, email) | Ack/escalation ladder; location in notifications |
| Mobile app | Nothing | API stays reusable; push channel (`notif_channel` already has `push`) |
| P2P call, social SOS map, points | Nothing | Optional, last (see Phase E) |

---

## Phase A - Black-box detector + passive SOS end to end (do first)

**Why first:** it lets you test the watch <-> web connection with realistic alerts today, and the model drops in later by implementing one interface.

**Deliverables**
1. `MotionDetector` interface + `Detection` value; `NoopDetector` in main.
2. `RandomDetector` and a debug receiver in `src/debug` only (adb-driven: choose kind, probability, or fire one now).
3. `PassiveAlerts` (generalises `FallAlerts.onFallDetected`) handling `FALL`, `SEIZURE`, `PRE_SEIZURE` with per-kind dedupe.
4. Pre-seizure local warning screen (haptic + one big "Tôi ổn" + short advice), then the same cooldown -> send path.
5. Health-info screen shown after a passive alert is sent (name, blood type, allergies, conditions, medications, emergency contacts) from a cached SOS card.
6. Server: `trigger: "algorithm"` already accepted; add `GET /api/device/sos-card` returning the profile the public `/sos/[token]` page already builds.

**Interfaces (fixed now so the model can be swapped later)**

```java
package com.example.watchtest.uii;

/** The only thing the rest of the app knows about detection. Called about once per second with a feature row. */
public interface MotionDetector {
    /** @return a detection, or null. Must be cheap and must never throw. */
    Detection onFeatures(long tMs, float[] features, Double heartRateBpm);

    final class Detection {
        public final String type;        // Payloads.FALL | Payloads.SEIZURE | Payloads.PRE_SEIZURE
        public final double confidence;  // 0..1
        public Detection(String type, double confidence) { this.type = type; this.confidence = confidence; }
    }
}
```

```java
// src/debug/java/.../RandomDetector.java  (debug source set only; absent from release by construction)
public final class RandomDetector implements MotionDetector {
    private final java.util.Random rng;
    private final double perRowProbability;   // e.g. 1/600 ~ one detection per 10 minutes
    public RandomDetector(java.util.Random rng, double perRowProbability) { ... }
    @Override public Detection onFeatures(long tMs, float[] f, Double hr) {
        if (rng.nextDouble() >= perRowProbability) return null;
        double r = rng.nextDouble();
        String type = r < 0.5 ? Payloads.PRE_SEIZURE : r < 0.8 ? Payloads.FALL : Payloads.SEIZURE;
        return new Detection(type, 0.5 + 0.5 * rng.nextDouble());
    }
}
```

**Tasks**
- A1 `MotionDetector`, `NoopDetector`, `DetectorHolder` (volatile current detector); `MotionService` calls it with `uii.lastFeatures` rows and `Uii.freshHeartRate`. Test: holder defaults to Noop; a stub detector's detection reaches `PassiveAlerts` once.
- A2 `RandomDetector` + `DebugDetectorReceiver` (`adb shell am broadcast -a net.studev.uii.DEBUG_DETECT --es type seizure`, `--ef p 0.01`). Test (JVM): p=0 never fires, p=1 always fires, seeded mix roughly 50/30/20.
- A3 `PassiveAlerts.onDetected(kind, confidence, t)`: reuse `Outbox` due-time hold and `FallPromptActivity`; add `Payloads.TRIGGER_ALGORITHM = "algorithm"` and send `confidence`. Per-kind dedupe window (fall 2 min as today; pre-seizure 5 min). Test with `PayloadsTest`-style JSON assertions.
- A4 Pre-seizure warning: strong vibration pattern + screen "Ngồi xuống" / "Tôi ổn" (<= 3 words per spec rule), prompt cooldown uses same cancel flow. Update `watch-sos-and-fall-flow` spec.
- A5 Health-info screen: `SosCard` model cached from `GET /api/device/sos-card` at pairing and refreshed on app start; shown after a passive or active alert is **accepted by the server**. Test: offline shows the cached card; no card shows a short "chưa có thông tin" state.
- A6 Web: show `trigger=algorithm` and confidence on the alert page; extend `tests/device.test.mjs`; extend `scripts/watch-sim.mjs` with `alert ... --algorithm --confidence 0.8`.

**Acceptance:** with a debug build on the emulator, `adb ... DEBUG_DETECT --es type fall` produces: prompt -> (no tap) -> alert on the web dashboard within seconds -> health card on watch; tapping "Tôi ổn" produces nothing on the server. Release build contains no `RandomDetector` class (check the APK with `unzip -l`/R8 mapping).

---

## Phase B - Live location sharing

**Design**
- Three modes, escalating privacy cost:
  1. **Alert snapshot** (default): one fix attached to every alert (`lat`/`lng`/accuracy already in schema).
  2. **Incident live session**: from alert send until resolved/cancelled/timeout (30 min cap), a fix every 5-10 s.
  3. **Continuous sharing**: explicit opt-in toggle per wearer, off by default, visible indicator, much heavier on battery.
- Watch: foreground service type `location` for the live session; fused location where Google Play services exist, platform `LocationManager` otherwise (Xiaomi/non-GMS).
- Server: `device_locations(device_id, alert_id nullable, t, lat, lng, accuracy_m)`, `POST /api/device/locations` (batched, rate limited, schema-validated), retention job (delete non-incident points after N hours), publish through the existing SSE hub.
- Web: live map on the alert page and circle page (OpenStreetMap tiles via Leaflet needs no paid key; decision below), "last seen" age and accuracy ring, link on the public `/sos/[token]` page during an open incident only.

**Risks / spikes (do first, before building)**
- **Background start restrictions:** a passive alert fires from `HealthPassiveService` with no UI. Android limits starting location foreground services from the background. Spike: does `ACCESS_BACKGROUND_LOCATION` + the health FGS already running (`MotionService`) allow a `location`-typed upgrade? If not, the live session starts only after the wearer sees the prompt, and the snapshot comes from the last known fix.
- **Permission UX:** fine location + background location are separate grants on Wear OS; extend the existing permission flow and Details screen rows.
- **Battery:** measure the live session and continuous mode against the existing budget in `docs/validation` before offering mode 3.

**Acceptance:** emulator route playback (Android Studio extended controls) shows movement on the web map within ~10 s; locations stop within one interval after "resolved"; revoking the permission turns the Details row amber and alerts still send without coordinates.

---

## Phase C - Acknowledgement and escalation (the "reach relatives early" part)

**Design**
- Server-side ladder per alert, driven by a worker/cron already needed for retries:
  - t0: notify circle (SSE + email today; add push when the mobile app exists).
  - t+60 s without ack: re-notify guardians, raise severity channel, include location link.
  - t+180 s without ack: next tier (SMS or voice call to emergency contacts) **only if** the owner configured a provider and the contact consented.
  - Every step writes an `alert_events` row (enum `alert_action` already has `acknowledged`, `escalated`, `called_ems`, `resolved`).
- Watch learns progress: `GET /api/device/alerts/{id}` polled while the confirmation screen is open (and on next sync): shows "Đã gửi" -> "Người thân đã xem" -> "Đang đến". No false reassurance: states come only from the server.
- Active SOS fast path: after "đã gửi", a **"Gọi"** button dials the primary emergency contact through the watch dialer when telephony/Bluetooth call is available, hidden otherwise. No calls are placed automatically.
- Web: guardians get Ack / "Tôi đang đến" / Resolve buttons on the alert page; first ack stops the ladder.

**Acceptance:** with no ack the ladder fires on schedule (unit tests with a fake clock); one ack stops it; offline watch queues the alert and the ladder starts when it arrives (`detected_at` older than now is respected).

---

## Phase D - Hardening for real devices and the mobile app

- Real-device matrix: Galaxy Watch (Wear OS), Pixel Watch, and each Xiaomi model **only if it runs Wear OS**; record sampling rate, HR cadence, location behaviour, battery, OEM battery management (continues `real-watch-readiness`).
- Mobile-ready API: freeze and document the circle/alert/location endpoints (Zod schemas as the source of truth, add `/api/v1` prefix before a second client depends on them); device tokens vs. user sessions stay separate.
- Push notifications (FCM) as the `push` channel; SSE remains for the web.
- Observability: alert-to-ack latency, outbox age, ladder step counts.

---

## Phase E - Optional features (separate specs, after A-D are field-proven)

| Feature | Recommendation | Main risks |
|---|---|---|
| **Peer-to-peer calling** | Not on the watch first. Do it between the web and a future mobile app with WebRTC + a TURN server; the watch only exposes the dial button from Phase C. | TURN hosting cost; WebRTC stacks are not a good fit for Wear OS; call reliability is a safety claim |
| **Social SOS map (nearby rescuers)** | Last. Opt-in responder role, approximate location until the victim's alert is accepted, verified accounts, rate limits, report/abuse flow, legal review first. | Exposes a vulnerable person's location to strangers; harassment, false calls, liability if nobody comes; sensitive-data law (confirm obligations under Vietnam's personal data decree 13/2023 and any health-data rules with a lawyer) |
| **Points / rewards / coupons** | Event ledger table (`points_events`) with rules engine; reward **confirmed** outcomes only (victim confirms a rescue, health task verified), never alert creation. Coupons need a partner and a legal/tax review. | Incentive to fake emergencies or farm points; privacy of health-task data; fraud |

---

## Decisions (answered 2026-10-15)

1. **Escalation channels:** SMS/voice are **not needed now**. Phase C uses in-app channels only (SSE, email, later push); the ladder's SMS/voice tier is left as an unimplemented extension point.
2. **Map provider:** OpenStreetMap + Leaflet (no key).
3. **Health card on the watch:** visible to **anyone** holding the watch, after an alert has fired (no PIN). Consequence: the card must show only fields the wearer chose to expose (profile form gets a per-field "show on watch" switch, default conservative), and it must never be shown unless an alert is in flight or was just accepted.
4. **Continuous location sharing:** **yes**, mode 3 is in scope. Consequences: explicit opt-in screen with plain-language consent, a persistent on-watch indicator while sharing, a one-tap off, and a retention limit for non-incident points.
5. **Target launch region/legal:** still open; needed before Phase E and before storing other people's health data.

## Self-Review

- Every requested feature maps to a phase: pre-seizure warning (A4), passive SOS with cooldown + health info (A3, A5), active SOS contact (C), live location (B), black-box detector (A1-A2), mobile-ready (D), optional features (E).
- Phase A items are concrete enough to start; B-E intentionally list design and acceptance, with detailed task plans written at phase start because they depend on the spikes (background location, provider choices).
- Names are consistent: `MotionDetector.Detection`, `PassiveAlerts.onDetected`, `Payloads.TRIGGER_ALGORITHM`.
- Not verified here: Android background-location FGS rules and Wear OS availability of specific Xiaomi models; both are flagged as spikes/checks instead of assumed.

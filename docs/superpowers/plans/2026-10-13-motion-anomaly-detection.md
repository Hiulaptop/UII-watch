# Motion Anomaly Detection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Feed continuous motion (accelerometer) and heart-rate signals from the watch into a model that detects abnormal movement (seizure-like convulsions, falls), and raise the existing alert flow when it fires.

**Architecture:** `MotionService` already reads the accelerometer at 50 Hz and `HealthPassiveService` already receives batched heart rate. Today both are reduced to 10 s mean/max/variance rows, which is too coarse for a model. Add a short-window feature extractor (spectral + jerk features) on the raw stream, a research-mode raw/feature capture to build a labelled dataset, an offline training loop, then an on-device detector that feeds the existing `Payloads.alert(...)` / `FallAlerts` path. Ship the detector in shadow mode (log only) before it is allowed to alert.

**Tech Stack:** Java (existing `uii` package, JUnit 4 on the JVM), Android `SensorManager`, Health Services `PassiveListenerService`, Python (scikit-learn / TensorFlow Lite) for offline training, existing Next.js `UII-web` ingestion endpoints.

**Spec:** to be written as OpenSpec change `motion-anomaly-detection` (Task 0). Context: `openspec/changes/real-watch-readiness/proposal.md` lists seizure detection as out of scope, so this is a new change.

## Global Constraints

- Wellness/alerting only; never present output as a medical diagnosis.
- `minSdk = 34`, `targetSdk = 37`, Java 17 (`watchface/build.gradle.kts`).
- Accelerometer stays at `MotionService.SAMPLING_US = 20_000` (50 Hz) and `MAX_LATENCY_US = 2_000_000`; do not change them without a battery measurement (`docs/validation`).
- Feature code is pure Java with no Android imports, so it runs in JVM unit tests.
- Server contract is `lib/device/schemas.ts` in UII-web; out-of-range values are dropped in `Payloads`, never sent.
- No alert may be raised by the model until shadow-mode results are recorded in `docs/validation`.
- Detection must work with no network: no model call, feature lookup or threshold fetch may depend on the server.
- Inputs limited to accelerometer + Health Services heart rate (portable across Galaxy Watch and other Wear OS devices); sensor sampling rate varies per device, so features derive the rate from timestamps.

---

## What the existing code already gives us (and what is missing)

| Need | Existing | Gap |
|---|---|---|
| Raw motion at 50 Hz, batched in hardware | `MotionService` (foreground, type `health`) | Only magnitude mean/max/var per 10 s is kept |
| Last seconds of raw axes | `RawBuffer` (ring, attached to alerts) | Not fed to any model |
| Heart rate in background | `HealthPassiveService` -> `HeartRateBucketer`, `Uii.noteHeartRate / freshHeartRate` | Cadence is device-chosen (1 s to ~10 min); no freshness feature |
| OS fall detection | `HealthEvent.FALL_DETECTED` -> `FallAlerts` | Not a model; do not replace it |
| Alert + cancel window + upload + retry | `Payloads.alert`, `FallPromptActivity`, `Outbox`, `Uploader` | Reuse as is (`TRIGGER_DEVICE`, `confidence`, `snapshot`) |
| Labels | Manual SOS taps (`pre_seizure`, `seizure`, `fall`) | No negative/"false alarm" labels, no raw upload for training |

---

## File Structure

| File | Responsibility |
|---|---|
| `watchface/src/main/java/com/example/watchtest/uii/MotionFeatures.java` (create) | Pure function: raw x/y/z window -> 7 features |
| `watchface/src/main/java/com/example/watchtest/uii/FeatureWindower.java` (create) | Sliding 2 s window, 1 s hop over the raw stream |
| `watchface/src/test/java/com/example/watchtest/uii/MotionFeaturesTest.java` (create) | Feature correctness on synthetic signals |
| `watchface/src/test/java/com/example/watchtest/uii/FeatureWindowerTest.java` (create) | Windowing cadence |
| `watchface/src/main/java/com/example/watchtest/uii/MotionService.java` (modify) | Push raw readings into the windower |
| Later phases | `AnomalyDetector`, `CaptureSink`, `tools/ml/*` (defined after Phase 2 data exists) |

---

## Phase 0 - Decisions that change the model (blocking)

### Task 0: Write the OpenSpec change

Decisions (answered 2026-10-14):

1. **Target anomalies:** seizure (convulsive, rhythmic shaking) and falls. Two detectors sharing the feature pipeline, not one catch-all.
2. **Inference runs offline, on the watch.** No server in the detection path; the server is only used later to collect data and retrain. This rules out server-side confirmation, so the model must be small (trees / logistic / tiny CNN) and false alarms are handled by the on-watch cancel window.
3. **No labelled data yet.** Phase 2 (dataset) comes before any model work; Phase 1 can proceed in parallel because it needs no data.
4. **Hardware: Galaxy Watch, Xiaomi, mainstream devices.** Only the common denominator is assumed: accelerometer via `SensorManager` + heart rate via Health Services. No SpO2/HRV/ECG, no Samsung Health Sensor SDK (not portable). Xiaomi watches differ in OS (some run Wear OS, many run a proprietary OS the app cannot install on); confirm each target model runs Wear OS before counting it.

- [ ] Run `/opsx:propose motion-anomaly-detection` in `UII-watch` and record the four decisions above in `proposal.md`.

---

## Phase 1 - Short-window features on-device (pure Java, fully testable)

### Task 1: `MotionFeatures`

**Files:**
- Create: `watchface/src/main/java/com/example/watchtest/uii/MotionFeatures.java`
- Test: `watchface/src/test/java/com/example/watchtest/uii/MotionFeaturesTest.java`

**Interfaces:**
- Produces: `static float[] MotionFeatures.compute(float[] x, float[] y, float[] z, double sampleRateHz)` returning `COUNT = 7` values ordered as `MotionFeatures.NAMES`.

- [ ] **Step 1: Write the failing test**

```java
package com.example.watchtest.uii;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MotionFeaturesTest {
    private static final double FS = 50.0;
    private static final int MEAN = 0, STD = 1, MAX = 2, JERK = 3, ZCR = 4, DOM = 5, BAND = 6;

    /** 2 s of gravity on z plus a sine of the given frequency and amplitude on z. */
    private static float[][] sine(double hz, double amp) {
        int n = 100;
        float[] x = new float[n], y = new float[n], z = new float[n];
        for (int i = 0; i < n; i++) z[i] = (float) (9.81 + amp * Math.sin(2 * Math.PI * hz * i / FS));
        return new float[][]{x, y, z};
    }

    @Test public void aStillWristHasNoVariationAndNoDominantFrequency() {
        float[][] s = sine(1.0, 0.0);
        float[] f = MotionFeatures.compute(s[0], s[1], s[2], FS);
        assertEquals(9.81, f[MEAN], 1e-3);
        assertEquals(0.0, f[STD], 1e-3);
        assertEquals(0.0, f[DOM], 1e-6);
        assertEquals(0.0, f[BAND], 1e-6);
    }

    @Test public void aFiveHertzShakeIsFoundInTheConvulsionBand() {
        float[][] s = sine(5.0, 3.0);
        float[] f = MotionFeatures.compute(s[0], s[1], s[2], FS);
        assertEquals(5.0, f[DOM], 0.26);
        assertTrue("most power in 2-8 Hz, was " + f[BAND], f[BAND] > 0.8);
        assertTrue(f[STD] > 1.5);
        assertTrue(f[JERK] > 20);
        assertTrue(f[ZCR] > 8);
    }

    @Test public void aSlowOneHertzSwingIsOutsideTheConvulsionBand() {
        float[][] s = sine(1.0, 3.0);
        float[] f = MotionFeatures.compute(s[0], s[1], s[2], FS);
        assertEquals(1.0, f[DOM], 0.26);
        assertTrue("was " + f[BAND], f[BAND] < 0.3);
    }

    @Test(expected = IllegalArgumentException.class)
    public void tooFewSamplesAreRejected() {
        MotionFeatures.compute(new float[3], new float[3], new float[3], FS);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run (from `UII-watch`): `./gradlew :watchface:testDebugUnitTest --tests "*MotionFeaturesTest"`
Expected: FAIL, `MotionFeatures` does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
package com.example.watchtest.uii;

/**
 * Features of one window of raw accelerometer readings (m/s^2). Pure Java so it runs in JVM unit tests.
 * The 2-8 Hz band is where rhythmic convulsive shaking sits; walking is mostly 1-2.5 Hz.
 */
public final class MotionFeatures {
    public static final String[] NAMES = {
            "mag_mean", "mag_std", "mag_max", "jerk_rms", "zero_cross_per_s", "dominant_hz", "band_2_8_ratio"};
    public static final int COUNT = NAMES.length;

    private static final double F_MIN = 0.5, F_MAX = 12.0, F_STEP = 0.25, BAND_LO = 2.0, BAND_HI = 8.0;

    private MotionFeatures() {}

    public static float[] compute(float[] x, float[] y, float[] z, double fs) {
        int n = x.length;
        if (n < 8 || y.length != n || z.length != n || !(fs > 0)) {
            throw new IllegalArgumentException("need >= 8 samples on three equal axes and a positive rate");
        }
        double[] m = new double[n];
        double sum = 0, max = 0;
        for (int i = 0; i < n; i++) {
            m[i] = Math.sqrt((double) x[i] * x[i] + (double) y[i] * y[i] + (double) z[i] * z[i]);
            sum += m[i];
            max = i == 0 ? m[0] : Math.max(max, m[i]);
        }
        double mean = sum / n;

        double var = 0, jerkSq = 0;
        int crossings = 0;
        for (int i = 0; i < n; i++) {
            double d = m[i] - mean;
            var += d * d;
            if (i > 0) {
                double j = (m[i] - m[i - 1]) * fs;
                jerkSq += j * j;
                if ((m[i] - mean) * (m[i - 1] - mean) < 0) crossings++;
            }
        }
        double std = Math.sqrt(var / n);

        double total = 0, band = 0, bestPower = 0, bestHz = 0;
        for (double f = F_MIN; f <= F_MAX; f += F_STEP) {
            double w = 2 * Math.PI * f / fs, re = 0, im = 0;
            for (int i = 0; i < n; i++) {
                double d = m[i] - mean;
                re += d * Math.cos(w * i);
                im -= d * Math.sin(w * i);
            }
            double p = (re * re + im * im) / ((double) n * n);
            total += p;
            if (f >= BAND_LO && f <= BAND_HI) band += p;
            if (p > bestPower) { bestPower = p; bestHz = f; }
        }
        boolean still = total < 1e-9;

        float[] out = new float[COUNT];
        out[0] = (float) mean;
        out[1] = (float) std;
        out[2] = (float) max;
        out[3] = (float) Math.sqrt(jerkSq / (n - 1));
        out[4] = (float) (crossings / (n / fs));
        out[5] = still ? 0f : (float) bestHz;
        out[6] = still ? 0f : (float) (band / total);
        return out;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :watchface:testDebugUnitTest --tests "*MotionFeaturesTest"`
Expected: PASS (4 tests)

- [ ] **Step 5: Commit**

```bash
git add watchface/src/main/java/com/example/watchtest/uii/MotionFeatures.java watchface/src/test/java/com/example/watchtest/uii/MotionFeaturesTest.java
git commit -m "feat: short-window motion features (spectral band, jerk, zero crossings)"
```

### Task 2: `FeatureWindower`

**Files:**
- Create: `watchface/src/main/java/com/example/watchtest/uii/FeatureWindower.java`
- Test: `watchface/src/test/java/com/example/watchtest/uii/FeatureWindowerTest.java`

**Interfaces:**
- Consumes: `MotionFeatures.compute(...)` from Task 1.
- Produces: `FeatureWindower(long windowMs, long hopMs)`, `FeatureWindower.Row add(long tMs, float x, float y, float z)` (null until a window is ready), `Row { long tMs; float[] f; }`.

- [ ] **Step 1: Write the failing test**

```java
package com.example.watchtest.uii;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class FeatureWindowerTest {
    @Test public void aFiveHertzShakeProducesOneRowPerSecondOnceTheFirstWindowIsFull() {
        FeatureWindower w = new FeatureWindower(2_000, 1_000);
        List<FeatureWindower.Row> rows = new ArrayList<>();
        for (int i = 0; i < 300; i++) {            // 6 s at 50 Hz
            long t = 1_000_000L + i * 20L;
            float z = (float) (9.81 + 3 * Math.sin(2 * Math.PI * 5 * i / 50.0));
            FeatureWindower.Row r = w.add(t, 0f, 0f, z);
            if (r != null) rows.add(r);
        }
        assertTrue("rows: " + rows.size(), rows.size() >= 4 && rows.size() <= 6);
        for (FeatureWindower.Row r : rows) assertEquals(5.0, r.f[5], 0.26);
    }

    @Test public void nothingIsEmittedBeforeTheWindowIsFull() {
        FeatureWindower w = new FeatureWindower(2_000, 1_000);
        for (int i = 0; i < 40; i++) assertNull(w.add(i * 20L, 0f, 0f, 9.81f));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :watchface:testDebugUnitTest --tests "*FeatureWindowerTest"`
Expected: FAIL, `FeatureWindower` does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
package com.example.watchtest.uii;

import java.util.ArrayDeque;

/** Slides a window over the raw stream and emits one feature row per hop. Not thread-safe: use one thread. */
public final class FeatureWindower {
    public static final class Row {
        public final long tMs;
        public final float[] f;
        Row(long tMs, float[] f) { this.tMs = tMs; this.f = f; }
    }

    private static final class S {
        final long t; final float x, y, z;
        S(long t, float x, float y, float z) { this.t = t; this.x = x; this.y = y; this.z = z; }
    }

    private final long windowMs, hopMs;
    private final ArrayDeque<S> buf = new ArrayDeque<>();
    private long nextEmitMs = Long.MIN_VALUE;

    public FeatureWindower(long windowMs, long hopMs) {
        if (windowMs <= 0 || hopMs <= 0) throw new IllegalArgumentException("window and hop must be positive");
        this.windowMs = windowMs;
        this.hopMs = hopMs;
    }

    /** Returns a row when a hop has elapsed and the window is at least 90% full, else null. */
    public Row add(long tMs, float x, float y, float z) {
        buf.addLast(new S(tMs, x, y, z));
        while (!buf.isEmpty() && tMs - buf.peekFirst().t > windowMs) buf.removeFirst();
        long span = tMs - buf.peekFirst().t;
        if (span < windowMs * 0.9) return null;
        if (nextEmitMs != Long.MIN_VALUE && tMs < nextEmitMs) return null;
        nextEmitMs = tMs + hopMs;

        int n = buf.size();
        if (n < 8) return null;
        float[] xs = new float[n], ys = new float[n], zs = new float[n];
        int i = 0;
        for (S s : buf) { xs[i] = s.x; ys[i] = s.y; zs[i] = s.z; i++; }
        double fs = (n - 1) * 1000.0 / span;
        return new Row(tMs, MotionFeatures.compute(xs, ys, zs, fs));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :watchface:testDebugUnitTest --tests "*FeatureWindowerTest"`
Expected: PASS (2 tests)

- [ ] **Step 5: Commit**

```bash
git add watchface/src/main/java/com/example/watchtest/uii/FeatureWindower.java watchface/src/test/java/com/example/watchtest/uii/FeatureWindowerTest.java
git commit -m "feat: sliding feature windower over raw accelerometer stream"
```

### Task 3: Wire the windower into `MotionService`

**Files:**
- Modify: `watchface/src/main/java/com/example/watchtest/uii/MotionService.java` (`onSensorChanged`, field list)

**Interfaces:**
- Consumes: `FeatureWindower.add(...)` from Task 2.
- Produces: `Uii.lastFeatures` (volatile `float[]`) readable by later phases and the debug status line.

- [ ] **Step 1: Add the field and call**

```java
// field, next to `aggregator`
private final FeatureWindower windower = new FeatureWindower(2_000, 1_000);

// in onSensorChanged, after uii.raw.add(...)
FeatureWindower.Row row = windower.add(t, x, y, z);
if (row != null) uii.lastFeatures = row.f;
```

- [ ] **Step 2: Add `public volatile float[] lastFeatures;` to `Uii.java`**, next to `noteHeartRate`.

- [ ] **Step 3: Build and run all unit tests**

Run: `./gradlew :watchface:testDebugUnitTest :watchface:assembleDebug`
Expected: BUILD SUCCESSFUL, all existing tests still pass.

- [ ] **Step 4: Commit**

```bash
git add watchface/src/main/java/com/example/watchtest/uii/MotionService.java watchface/src/main/java/com/example/watchtest/uii/Uii.java
git commit -m "feat: compute short-window motion features in MotionService"
```

---

## Phase 2 - Build the dataset (gate: no model work without it)

Detailed code is deferred until Task 0 answers the target anomaly and labelling questions; the acceptance criteria are fixed now.

- **Task 4: Research-mode capture.** Debug/opt-in flag that stores raw `t,x,y,z` (50 Hz) plus every HR reading plus `Uii.freshHeartRate` age, in a rolling local file; uploads only on charger + Wi-Fi. Budget: about 150 floats/s, tens of MB/day. Accept: 24 h capture on a real watch with no crash and measured battery delta recorded in `docs/validation`.
- **Task 5: Labels.** Reuse manual SOS taps as positive labels with +/-60 s context; add a "false alarm / all good" tap and a daily-activity diary (walking, eating, brushing teeth, driving, sleeping) so negatives include the hard cases. Accept: a CSV with `subject_id, t_start, t_end, label`.
- **Task 6: Server ingestion.** New authenticated endpoint next to `src/app/api/device/samples/route.ts` in UII-web accepting binary raw chunks; Zod schema in `lib/device/schemas.ts`. Accept: round-trip test with `scripts/watch-sim.mjs`.
- **Public data to bootstrap the fall side (verify licences/suitability first):** SisFall, MobiAct. Seizure data is scarce; expect to rely on wearer data and clinical partners.

## Phase 3 - Offline model

- **Task 7: Baseline first.** Rule on `band_2_8_ratio`, `mag_std`, `dominant_hz` persisting over N of M windows. Acceptance numbers are fixed now: report sensitivity per event and false alarms per 24 h, split by subject (leave-one-subject-out), never random windows.
- **Task 8: Learned model.** Gradient-boosted trees or logistic regression on the 7 features plus HR features (`hr`, `hr_delta_30s`, `hr_age_s`, missing flag). Only try a 1D-CNN on raw windows if the baseline plateaus. Export as hand-coded Java tree/weights or TFLite. Accept: beats baseline on false alarms at equal sensitivity.

## Phase 4 - On-device detector

- **Task 9: `AnomalyDetector`.** Consumes `FeatureWindower.Row` + `Uii.freshHeartRate`; temporal smoothing/hysteresis (N-of-M, refractory period); returns `Detection(type, confidence)`. TDD with recorded windows from Phase 2 as fixtures.
- **Task 10: Alert path.** On detection call `Payloads.alert(newClientId(), SEIZURE|FALL, TRIGGER_DEVICE, t, hr, accelPeak, confidence, uii.raw.snapshot(...))` through the existing cancel-window flow. Do not touch the OS fall relay.
- **Task 11: Shadow mode.** Build flag that logs detections and uploads them as non-alerting events. Accept: 2 weeks on real wearers, false-alarm rate recorded, before enabling alerts.

## Phase 5 - Field validation

Extend `docs/validation` (battery with feature extraction + inference, detection latency, behaviour offline, reboot persistence). Gate alerts on the measured false-alarm budget agreed in Task 0.

---

## Self-Review

- Coverage: motion features (Tasks 1-3), HR integration (Task 8-9), dataset (4-6), model (7-8), alert path (10), validation (11, Phase 5). Phases 2-5 are intentionally outlined, not coded, because the model and label format depend on Task 0 answers and on collected data.
- Placeholders: none inside Phase 1. Later phases state acceptance criteria instead of code.
- Types: `MotionFeatures.compute` / `FeatureWindower.Row.f` index `5` = `dominant_hz` is used consistently in tests; `COUNT = 7` matches `NAMES`.
- Known risk: `FeatureWindower` derives the rate from timestamps, assuming near-uniform delivery; verify on a real watch because batched delivery can bunch timestamps.

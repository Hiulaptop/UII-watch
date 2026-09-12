## Why

The current project is only a minimal Watch Face Format package and provides no way to inspect the physical sensors available on a Wear OS watch. A standalone sensor dashboard will make the watch's hardware observable during development and testing, while providing a compact reference implementation based on the Android Wear OS sample patterns.

## What Changes

- Replace the current no-code watch-face-only app scope with a Wear OS standalone application entry point.
- Discover the sensors exposed by the current watch at runtime and present them in a scrollable list.
- Display current readings, supported axes/values, availability, and update state for each selected or visible sensor.
- Start and stop sensor listeners with the screen/app lifecycle to avoid unnecessary battery usage and stale registrations.
- Handle absent sensors, unavailable readings, registration failures, and permission requirements with visible user-facing states.
- Add a readable detail view for a sensor's values and metadata while retaining access to the full sensor list.
- Add automated tests for sensor mapping/state handling and UI/device validation coverage for runtime behavior.

## Capabilities

### New Capabilities

- `wearos-sensor-dashboard`: Discover available watch sensors and present their live metadata and readings in a lifecycle-aware, scrollable Wear OS interface.

### Modified Capabilities

- None. No existing project capabilities or requirements are defined under `openspec/specs/`.

## Impact

- The `watchface` module's manifest, Gradle configuration, resources, and new Kotlin source/UI layers will be affected. The existing Watch Face Format manifest configuration is incompatible with a code-bearing sensor application and must be replaced or separated as part of implementation.
- Android sensor APIs and the Wear OS UI toolkit/dependencies will be introduced; exact library versions should follow the repository's compatible compile/target SDK and current Android documentation.
- Behavior depends on the physical sensor set and permissions of the paired/watch device, so the UI must be capability-driven rather than hard-coded to one model.
- No network service or phone companion is required for the initial scope; readings are collected locally and are not persisted or uploaded.

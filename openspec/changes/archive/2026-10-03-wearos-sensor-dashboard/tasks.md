## 1. Convert The Application Scaffold

- [x] 1.1 Convert `watchface` from the current Watch Face Format no-code manifest to a standalone code-bearing Wear OS launcher application, and verify the manifest contains the watch feature and a launchable activity.
- [x] 1.2 Add Kotlin/JVM and Wear OS UI dependencies compatible with the existing AGP 9.4.0, compile SDK 37, and target SDK 37 configuration, then verify dependency resolution and a debug build succeed.
- [ ] 1.3 Establish application packages, theme, strings, and navigation resources for the sensor dashboard, and verify the app installs and launches on a Wear OS emulator or watch.

## 2. Build The Sensor Data Layer

- [x] 2.1 Define the sensor identity, metadata, availability, pending, current-value, and error states needed by the dashboard, and verify unit tests cover empty, multi-axis, pending, and unavailable models.
- [ ] 2.2 Implement a platform sensor adapter that discovers sensors at runtime, converts events into application state, and isolates registration failures per sensor, then verify fake-adapter tests preserve other sensors when one registration fails.
- [ ] 2.3 Add lifecycle-aware registration and unregistration with a conservative update rate and latest-value coalescing, then verify tests or instrumentation confirm no active listeners remain after the dashboard leaves the foreground.
- [ ] 2.4 Implement permission and capability handling for sensors that require it, including user-visible recovery/error state, and verify permission-denied and unsupported-sensor paths do not crash the dashboard.

## 3. Implement The Wear OS Interface

- [ ] 3.1 Implement the vertically scrollable sensor inventory with human-readable names, types, availability, pending/current values, and an explicit no-sensors state, then verify long inventories are fully scrollable on a watch-sized display.
- [ ] 3.2 Implement sensor selection and detail navigation with complete metadata, multi-axis values, units/dimensions where available, and back navigation, then verify the selected sensor remains correct and the inventory is retained on return.
- [ ] 3.3 Add layout and formatting behavior for long names, vendor-specific values, and multi-axis readings without horizontal scrolling or clipping essential data, then verify representative content on round and rectangular emulator configurations.
- [ ] 3.4 Add accessibility labels/content descriptions and clear status/error wording for sensor rows and detail content, then verify the primary inventory and navigation actions are discoverable through accessibility inspection.

## 4. Validate The End-To-End Experience

- [ ] 4.1 Add unit/UI tests for discovery, event updates, pending readings, individual failures, sensor unavailability, navigation, and empty state, and verify the relevant Gradle test tasks pass.
- [ ] 4.2 Run the debug build and install it on a Wear OS emulator or physical watch, then verify offline launch, runtime sensor discovery, live updates for representative sensors, detail navigation, and visible failure states.
- [ ] 4.3 Verify lifecycle cleanup and power-sensitive behavior by leaving and re-entering the dashboard, then confirm listeners stop when hidden and resume with current state when visible.
- [x] 4.4 Review the final manifest and packaged APK for accidental retained Watch Face Format assumptions, unnecessary permissions, network requirements, or background components, and verify the standalone app remains installable.

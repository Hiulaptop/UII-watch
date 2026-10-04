## Context

The repository currently contains one `watchface` Android application module configured for Watch Face Format, with `android:hasCode="false"`, no Kotlin source, and no runtime dependencies. The new behavior therefore requires converting the module into a code-bearing standalone Wear OS app rather than extending an existing screen. See `proposal.md` for motivation and `specs/wearos-sensor-dashboard/spec.md` for the externally visible contract.

## Goals / Non-Goals

**Goals:**

- Establish a device-driven sensor data flow from platform discovery through lifecycle-aware collection to list/detail UI state.
- Keep sensor failures isolated and represent pending, unavailable, and current states explicitly.
- Use a Wear OS-native, vertically scrollable presentation that works offline and on small round or rectangular displays.
- Make the sensor layer independently testable without requiring a physical watch for every state transition test.

**Non-Goals:**

- Historical charts, persistent sampling, export, analytics, or cloud synchronization.
- A phone companion, remote sensor proxy, or network protocol.
- Background collection after the dashboard is no longer visible.
- Preserving the current Watch Face Format package as a second user-facing experience in this initial change.

## Decisions

### Convert the existing module to a standalone code-bearing Wear OS app

Remove the Watch Face Format-only manifest assumptions and add the application entry point, launcher metadata, and compatible Wear OS UI dependencies to the existing `watchface` module. This is preferred over adding a second module because the repository is currently a single-purpose scaffold and there is no shared code to justify module boundaries. A separate module can be introduced later if a watch face becomes an explicit product requirement.

### Use the platform sensor manager behind a small repository/adapter boundary

Wrap runtime sensor enumeration, listener registration, event conversion, and unregistration behind an application-owned boundary. The UI consumes immutable sensor presentation state rather than platform event objects. This keeps Android callback details out of composables/screens and allows tests to provide fake sensor inventories and events. Direct platform calls from UI were rejected because they make lifecycle cleanup, error isolation, and deterministic testing harder.

### Maintain per-sensor state keyed by stable sensor identity

Create a discovered sensor model containing identity, display metadata, availability, and the latest formatted/raw values. Maintain independent status for each sensor so one registration or availability failure does not replace the whole dashboard state. Sensor values should be formatted at the presentation boundary, preserving enough raw information for detail rendering and test assertions.

### Collect only while the dashboard is visible

Tie registration and unregistration to the screen lifecycle or a lifecycle-aware collection scope. Registration should use a conservative update rate appropriate for a watch dashboard, with the exact rate selected during implementation based on the supported API and battery behavior. Avoid a permanently running service because continuous background sampling is outside the contract and creates unnecessary power/privacy cost.

### Use a scrollable list plus a sensor detail route

Render the inventory with the Wear OS list/scaffold primitives supported by the selected dependency versions, and route selection to a detail presentation with back navigation. Keep rows compact but allow multi-axis values and long metadata to wrap or be shown in detail rather than clipping or requiring horizontal scrolling. A single screen with expandable rows was rejected because it makes deep metadata and navigation less predictable on a small display.

### Model permission and capability failures as UI states

At startup and registration, translate platform permission, missing sensor, unsupported type, and runtime failure conditions into explicit states. Do not request permissions for sensors that do not require them; where a platform permission is required, explain the need and provide the platform request path. No permission or sensor capability is to be inferred from the watch model.

## Risks / Trade-offs

- [Sensor inventories and event semantics vary by watch model] -> Derive all rows from runtime discovery, display platform-provided type/name metadata, and test with empty, partial, and multi-axis inventories.
- [High-frequency updates can drain battery or overwhelm a small UI] -> Use a conservative sampling rate, register only while visible, and coalesce/render the latest value rather than retaining an unbounded event queue.
- [The current Watch Face Format artifact may be removed or become incompatible] -> Treat the conversion as an intentional migration, verify the generated APK launches as a standalone watch app, and retain no-code watch-face metadata only if implementation validation proves it can coexist safely.
- [A physical watch/emulator may not expose every sensor or permission path] -> Cover state transitions with fake sensor adapters and reserve device validation for discovery, rendering, lifecycle cleanup, and representative real sensors.
- [Wear OS and Android dependency/API compatibility may constrain available UI primitives] -> Align dependency versions with the repository's compile/target SDK during implementation and isolate version-specific UI choices behind the application layer.

## Migration Plan

1. Convert the module manifest/build configuration and add the standalone launcher entry point.
2. Implement the sensor adapter, state model, list/detail UI, lifecycle handling, and tests.
3. Build and install a debug APK on a Wear OS emulator or watch; verify discovery, live updates, navigation, failure states, offline operation, and cleanup.
4. Roll back by restoring the prior Watch Face Format manifest/resources and removing the new source/dependencies if the standalone migration is not accepted. No persisted data migration is required.

## Open Questions

- The exact set of sensor types to emphasize in the first screen can be chosen during implementation because the contract requires runtime discovery rather than a fixed curated list.
- The final presentation of units for vendor-specific sensors can be selected after checking the platform metadata available on the target SDK; unsupported units must still be labeled clearly rather than omitted.

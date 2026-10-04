## Purpose

Provide a standalone Wear OS dashboard that lets a user inspect the sensors exposed by their watch and view current readings without relying on a phone companion or a fixed hardware model.

## ADDED Requirements

### Requirement: Discover and list available sensors
The application MUST enumerate the sensors available on the current watch at runtime and show each sensor as a distinct list item with a human-readable name, sensor category/type, and availability state.

#### Scenario: Watch exposes multiple sensors
- **WHEN** the dashboard opens on a watch with registered sensors
- **THEN** it shows a vertically scrollable list containing each available sensor and does not assume a fixed sensor inventory

#### Scenario: Watch exposes no usable sensors
- **WHEN** the dashboard cannot find any usable sensors
- **THEN** it shows an explicit empty-state message explaining that no sensors are available on the device

### Requirement: Show live sensor readings
The application MUST present the latest reading for each active sensor in a readable form, including all values needed to interpret multi-axis readings and an indication of the reading's unit or dimension when available.

#### Scenario: Reading is received
- **WHEN** an active sensor publishes a new event
- **THEN** the corresponding sensor display updates to the latest values and remains associated with the correct sensor

#### Scenario: Sensor has not produced a reading
- **WHEN** a sensor is listed but has not produced an event yet
- **THEN** its display identifies that a reading is pending rather than showing misleading zero values

### Requirement: Inspect sensor details
The application MUST let the user open a sensor from the list and view its identifying metadata and current values in a detail view, with a clear way to return to the full list.

#### Scenario: User opens a sensor
- **WHEN** the user selects a sensor list item
- **THEN** the application opens a detail view for that sensor showing its name, type, availability, and latest values

#### Scenario: User returns from details
- **WHEN** the user invokes the detail view's back navigation
- **THEN** the application returns to the sensor list without losing the discovered sensor inventory

### Requirement: Reflect sensor and registration failures
The application MUST communicate when a sensor is unavailable, cannot be registered, or stops producing usable data, and MUST keep other sensors usable when one sensor fails.

#### Scenario: Individual sensor cannot be registered
- **WHEN** registration fails for one sensor while other sensors can be registered
- **THEN** that sensor is marked with an actionable unavailable/error state and the remaining sensors continue to display or update

#### Scenario: Sensor becomes unavailable
- **WHEN** the platform reports that an active sensor is unavailable
- **THEN** the UI updates that sensor's state and does not present its previous reading as current

### Requirement: Respect lifecycle and local-data boundaries
The application MUST collect readings locally, MUST not require network connectivity or a phone companion for dashboard functionality, and MUST stop active sensor collection when the dashboard is no longer visible or is being destroyed.

#### Scenario: Dashboard leaves the foreground
- **WHEN** the user navigates away from or closes the dashboard
- **THEN** active sensor collection is stopped and the application does not continue consuming sensor updates in the background

#### Scenario: Dashboard is used offline
- **WHEN** the watch has no network connection or paired phone connection
- **THEN** the user can still discover sensors and view readings provided by the watch hardware

### Requirement: Remain usable on small watch displays
The dashboard MUST support vertical scrolling and present sensor names, states, and values without requiring horizontal scrolling or access to a non-watch device.

#### Scenario: Long sensor inventory
- **WHEN** the available sensor list exceeds the watch viewport
- **THEN** the user can scroll through the complete inventory and select any listed sensor

#### Scenario: Long or multi-axis value content
- **WHEN** a sensor has multiple values or metadata that does not fit in one compact row
- **THEN** the list or detail presentation remains readable within the watch display constraints without clipping essential values

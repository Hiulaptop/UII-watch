## Purpose

Defines how the UII watch app must behave on real hardware in everyday conditions, and the evidence required before it is relied on to alert a wearer's relatives.

## ADDED Requirements

### Requirement: Full-day battery budget
With monitoring active and no other use, a fully charged supported watch SHALL last at least 24 hours before reaching the low-battery state, and the measured drain attributable to the app SHALL be recorded for each tested watch model.

#### Scenario: Day-long run
- **WHEN** a fully charged watch with the app paired and monitoring runs for 24 hours with normal wrist wear and no other app use
- **THEN** it still has charge remaining at the end, and the recorded drain per hour is stored with the watch model and app version

#### Scenario: Budget missed
- **WHEN** the measured drain would not last 24 hours
- **THEN** the validation is recorded as failed and a reduction in sampling cost is required before release

### Requirement: Fall alert timing
After the watch reports a fall and the wearer does not cancel, the alert SHALL reach the server within 10 seconds after the cancel window ends while the watch has connectivity, and the delay between the system's fall event and the server storing the alert SHALL be recorded. The delay between a person actually falling and the system's event belongs to the watch vendor and is not staged with a person during validation.

#### Scenario: Online fall, not cancelled
- **WHEN** the watch reports a fall, has connectivity, and the wearer does not cancel
- **THEN** the server stores the alert no later than 10 seconds after the cancel window ends

#### Scenario: Wearer cancels
- **WHEN** the wearer chooses "Tôi ổn" during the cancel window
- **THEN** no alert reaches the server and a following fall is still reported

### Requirement: Loss and return of connectivity
Alerts, samples and manual SOS raised while the watch has no route to the server SHALL be kept and delivered, oldest alerts first, when the route returns, without duplicates and without the wearer opening the app.

#### Scenario: Phone link lost during a fall
- **WHEN** a fall alert becomes due while the watch has no connectivity and connectivity returns ten minutes later
- **THEN** the alert is delivered once, shortly after connectivity returns, with its original detection time

#### Scenario: Samples while offline
- **WHEN** the watch is offline for an hour and then reconnects
- **THEN** the recorded samples are uploaded within the server's accepted history window and none are uploaded twice

### Requirement: Persistence across restart and update
Pairing, queued items and monitoring SHALL survive a watch reboot and an app update with no action by the wearer.

#### Scenario: Reboot
- **WHEN** a paired watch is restarted and not touched
- **THEN** monitoring resumes on its own and the next samples reach the server

#### Scenario: Update
- **WHEN** the app is updated while items are queued
- **THEN** the queued items are delivered after the update and monitoring resumes

### Requirement: Survives battery management
Monitoring SHALL keep running or be restored without the wearer's action under the watch's normal power management, and when it has been stopped by the system the status screen SHALL tell the wearer monitoring is off and how to turn it back on.

#### Scenario: Idle overnight
- **WHEN** the watch is left unused overnight on its charger and then worn
- **THEN** samples reach the server from within a minute of being worn

#### Scenario: Monitoring stopped
- **WHEN** the system has stopped monitoring or revoked a needed permission
- **THEN** the status screen shows monitoring is off with the missing item named, and one action restores it

### Requirement: Field diagnosability
The status screen, or a details screen one tap away from it, SHALL show the app version, the server host, whether the watch is paired, whether each needed permission and capability is available, the number of queued items and the result and time of the last upload.

#### Scenario: Diagnosing a silent watch
- **WHEN** a relative reports that no data is arriving
- **THEN** the wearer can read from the status screen or its details screen whether the watch is unpaired, missing a permission, offline with a queue, or being rejected by the server

### Requirement: Recorded validation evidence
Each release candidate SHALL have a written validation record covering every requirement in this capability for each supported watch model it was tested on, and a release SHALL NOT be distributed to a wearer while any requirement is unverified or failed on the model they use.

#### Scenario: Release gate
- **WHEN** a release candidate is proposed for a wearer's watch model
- **THEN** a record exists showing pass for that model on every requirement, or the release is held

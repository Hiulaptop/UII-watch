## Purpose

Defines what a distributable build of the UII watch app must be: identifiable, signed, pointed at production, free of development-only behaviour, and installable on the watches it claims to support.

## ADDED Requirements

### Requirement: Permanent application identity
The release app SHALL use a permanent application id that is not the scaffold placeholder, and that id SHALL NOT change between releases so that updates install over earlier versions without losing the pairing.

#### Scenario: Update keeps the pairing
- **WHEN** a newer release signed with the same key is installed over an older one on a paired watch
- **THEN** the watch remains paired and the queued outbox items are still sent

#### Scenario: Placeholder id is rejected
- **WHEN** a release artifact is produced
- **THEN** its application id is not `com.example.*`

### Requirement: Signed reproducible release
The project SHALL produce a signed release package with one documented command, and the signing key and its passwords SHALL NOT be stored in version control.

#### Scenario: Release build from a clean checkout
- **WHEN** the documented release command is run with the signing configuration supplied from outside the repository
- **THEN** a signed package is produced and its signature verifies against the release certificate

#### Scenario: Missing signing configuration
- **WHEN** the release command is run without signing configuration
- **THEN** the build fails with a message naming what is missing and does not silently produce a debug-signed release

### Requirement: Production configuration only
The release build SHALL communicate with the production server over HTTPS only and SHALL NOT contain development-only permissions, cleartext-traffic allowances, diagnostic logging of user data, or shortened fall-cancel windows.

#### Scenario: Release manifest is clean
- **WHEN** the release package's merged manifest is inspected
- **THEN** it declares no local-network permission, no cleartext allowance, and only permissions that a shipped feature uses

#### Scenario: Fall cancel window in release
- **WHEN** a release build detects a fall
- **THEN** the wearer has the production cancel window (20 seconds) before the alert is sent, regardless of any property used for testing

#### Scenario: Server address
- **WHEN** a release build contacts the server
- **THEN** every request goes to the production host over HTTPS

### Requirement: Declared supported Wear OS range
The app SHALL declare the lowest Wear OS version it is verified on, and a watch below that version SHALL be unable to install it rather than install and fail at runtime.

#### Scenario: Supported watch
- **WHEN** the release package is installed on a watch at or above the declared minimum
- **THEN** pairing, SOS, heart-rate and motion reporting work, and fall relay works wherever the watch reports fall detection as available

#### Scenario: Watch without fall detection
- **WHEN** the watch reports that fall detection is not available
- **THEN** the app still pairs and reports signals and manual SOS, and its status screen says that automatic fall detection is unavailable on this watch

### Requirement: Single shipped purpose
The shipped app SHALL contain only UII functionality and SHALL NOT include the original sensor-inspection screens.

#### Scenario: Launcher entry
- **WHEN** the app is installed
- **THEN** exactly one launcher entry exists, and it opens the UII home screen

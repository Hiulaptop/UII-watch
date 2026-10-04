## Purpose

Defines how a wearer pairs the watch with an account by typing the eight digit code shown on the website, and what they see when pairing fails or is withdrawn.

## ADDED Requirements

### Requirement: Code entry with progress
The pairing screen SHALL show eight dots that fill as digits are entered, a round numeric keypad with delete and confirm keys, and SHALL NOT display the typed digits as text or show instructions on the keypad screen.

#### Scenario: Typing a code
- **WHEN** the wearer enters three digits
- **THEN** three dots are filled and five are empty

#### Scenario: Incomplete code
- **WHEN** confirm is pressed with fewer than eight digits
- **THEN** a short message says the code needs eight digits and nothing is sent

### Requirement: Short error states
A failed pairing attempt SHALL show a short icon-led message of at most three words for each cause: wrong or expired code, too many attempts, too many devices on the account, no network, and any other error.

#### Scenario: Wrong code
- **WHEN** the server rejects the code
- **THEN** the digits are cleared and the wrong-code message is shown

#### Scenario: Too many devices
- **WHEN** the account already has the maximum number of devices
- **THEN** the too-many-devices message is shown

### Requirement: Successful pairing
After a successful pairing the watch SHALL start monitoring, ask for any missing permissions, and show the home screen.

#### Scenario: First pairing
- **WHEN** the server accepts the code
- **THEN** the permission requests follow one after another and the home screen then shows the green or amber state

### Requirement: Re-pair when withdrawn
When the server stops accepting the watch's credentials, the watch SHALL show the re-pair screen with one button to start pairing again, and SHALL keep any queued alerts until pairing succeeds.

#### Scenario: Device removed on the website
- **WHEN** the account owner removes the device on the website and the watch next contacts the server
- **THEN** the re-pair screen appears and its button opens code entry

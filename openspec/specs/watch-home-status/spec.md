# watch-home-status Specification

## Purpose
Defines what the wearer sees and can do from the watch's home screen: a state readable in one second, an SOS button that is always there, and technical details kept out of the way but one tap from home.

## Requirements

### Requirement: Glanceable health state
The home screen SHALL show exactly one of five states, each with its own colour, icon and at most two words: normal (green), needs attention (amber, naming what to fix), waiting for network (grey, with the number of items waiting), not paired, and refused by the server (needs re-pairing).

#### Scenario: Everything works
- **WHEN** the watch is paired, all permissions are granted and monitoring is running
- **THEN** the home screen shows the green state and no sentence

#### Scenario: A permission is missing
- **WHEN** a needed permission is not granted
- **THEN** the home screen shows the amber state with a short label for the fix, and tapping the ring starts the permission request or opens the app's settings page if the system no longer shows the dialog

#### Scenario: No network with items waiting
- **WHEN** alerts or samples are queued because the server cannot be reached
- **THEN** the home screen shows the grey state with the number waiting, and the queue empties by itself when the network returns

#### Scenario: Server refused the watch
- **WHEN** the server answers that the watch's credentials are no longer valid
- **THEN** the home screen shows the re-pair state instead of silently appearing unpaired

### Requirement: SOS is always on screen
The SOS button SHALL be visible and tappable on the home screen in every state, including when text is enlarged, without scrolling.

#### Scenario: Each state
- **WHEN** the home screen is shown in the green, amber, grey, unpaired-with-credentials-lost or normal state
- **THEN** SOS is on screen and one tap opens the SOS choices

#### Scenario: Enlarged system font
- **WHEN** the system font scale is set to its largest setting
- **THEN** SOS is still fully visible and no label on the home screen is cut off

### Requirement: Heart rate readout
The green home state SHALL show the most recent heart rate if it is less than two minutes old, and a dash placeholder otherwise.

#### Scenario: Fresh reading
- **WHEN** a heart rate arrived within the last two minutes
- **THEN** that number is shown with its unit

#### Scenario: Stale or missing reading
- **WHEN** no heart rate arrived in the last two minutes
- **THEN** the number is replaced by a placeholder rather than an old value

### Requirement: Details screen one tap away
Tapping the status ring SHALL open a Details screen that shows, one row per item with a pass or warning mark: heart-rate permission, activity permission, notification permission, fall detection support and network; and in small text the app version, the server host, the number of queued items and the result and time of the last upload.

#### Scenario: Reading details
- **WHEN** the wearer taps the status ring
- **THEN** each checked item appears as a one-word row with a mark, and the technical footer is readable

#### Scenario: Watch without fall detection
- **WHEN** the system reports that fall detection is not available
- **THEN** the fall row shows a warning mark so the wearer and their relatives are not misled

### Requirement: Text economy
Controls on the home, SOS and pairing screens SHALL be labelled with at most three words, and these screens SHALL NOT show explanatory sentences; explanations belong on the Details screen.

#### Scenario: Audit of the main screens
- **WHEN** the text of the home, SOS menu, countdown, confirmation and pairing screens is listed
- **THEN** no label exceeds three words and none is a sentence

### Requirement: Spoken labels
Every element that conveys meaning without text (status ring, icons, progress dots, icon-only buttons) SHALL have a spoken description in Vietnamese that states the state or action.

#### Scenario: Screen reader on the home ring
- **WHEN** a screen reader focuses the status ring
- **THEN** it announces the state in words (for example that monitoring is normal, or which permission is missing), not just a symbol name

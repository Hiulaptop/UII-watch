# watch-sos-and-fall-flow Specification

## Purpose
Defines how a wearer raises an alert by hand, how a detected fall asks them whether they are fine, and how the watch reports honestly whether the alert left the watch.

## Requirements

### Requirement: Choose what is happening
The SOS screen SHALL offer exactly three choices, each with an icon and at most two words: about to have a seizure, having a seizure, and fell. Going back SHALL be by swipe, with no dedicated back button.

#### Scenario: Opening SOS
- **WHEN** the wearer taps SOS on the home screen
- **THEN** the three choices are visible together on the round display without scrolling

### Requirement: Countdown with cancel
Choosing a situation SHALL start a five second countdown shown as a progress ring with the remaining seconds, and a Cancel control that stops it without sending anything. When it reaches zero the alert SHALL be queued for immediate sending.

#### Scenario: Cancelled in time
- **WHEN** the wearer taps Cancel before the countdown ends
- **THEN** nothing is sent and the watch returns to the home screen

#### Scenario: Countdown completes
- **WHEN** the countdown reaches zero
- **THEN** the alert is queued with its type and the current heart rate, and sending starts at once

### Requirement: Honest delivery confirmation
After a manual alert is queued, the confirmation SHALL say "Đã gửi" only once the server has accepted it; while it is still waiting for the network it SHALL say that it is waiting and show that it will be sent when the connection returns.

#### Scenario: Online
- **WHEN** the server accepts the alert shortly after the countdown ends
- **THEN** the confirmation shows the green sent state

#### Scenario: Offline
- **WHEN** the server cannot be reached
- **THEN** the confirmation shows the waiting state, never the sent state, and changes to sent by itself if the screen is still open when delivery succeeds

### Requirement: Fall prompt
When the watch reports a fall it SHALL ask "Bạn ổn chứ?" with a countdown of the cancel window and one large "Tôi ổn" button, shown full-screen where the system allows it; otherwise the same single action SHALL be on a high-priority notification. Tapping anywhere else on the prompt or notification SHALL NOT cancel the alert.

#### Scenario: Wearer is fine
- **WHEN** the wearer taps "Tôi ổn" during the countdown
- **THEN** the pending fall alert is removed, the prompt closes, and a later fall is still reported

#### Scenario: No answer
- **WHEN** the countdown ends without a tap
- **THEN** the fall alert is sent exactly as before, and the prompt shows the same confirmation states as a manual alert

#### Scenario: System will not show a full-screen alert
- **WHEN** the system declines the full-screen prompt
- **THEN** the high-priority notification with the single "Tôi ổn" action is shown instead and behaves the same

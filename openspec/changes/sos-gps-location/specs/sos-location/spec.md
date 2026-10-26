## Purpose

Defines what position an SOS carries, when the watch reads it, and how the website shows it.

## ADDED Requirements

### Requirement: Position is read only around an incident
The watch SHALL read its position only during the window that starts when an SOS or passive detection begins, at the milestones trigger, send, 2 minutes and 5 minutes, and SHALL NOT read it at any other time.

#### Scenario: No incident, no reading
- **WHEN** the watch is paired and monitoring and no SOS or detection has occurred
- **THEN** it makes no location request

#### Scenario: Milestones stop after five minutes
- **WHEN** an alert was sent and five minutes have passed since it began
- **THEN** no further location request is made for that alert

#### Scenario: Cancelling stops the rest
- **WHEN** the wearer cancels the alert ("Tôi ổn" or "Hủy") before a milestone
- **THEN** the remaining milestones for that alert are not run

### Requirement: Location never delays an alert
The watch SHALL send an alert at the same moment it would have without location, using the best position available at that moment or none.

#### Scenario: No fix yet
- **WHEN** the countdown ends and no fix has arrived
- **THEN** the alert is sent immediately, with the last known position if it is younger than 10 minutes, otherwise without a position

#### Scenario: Permission refused
- **WHEN** the wearer has not granted a location permission
- **THEN** alerts are sent exactly as before and the Home screen status is unchanged

### Requirement: Later fixes update the same alert
The watch SHALL send fixes taken after the alert as updates to that alert, through the outbox, and the server SHALL accept an update only for an alert of the same device.

#### Scenario: Offline update
- **WHEN** a milestone fix is taken while the watch has no connection
- **THEN** it is kept in the outbox and sent after the alert once the connection returns

#### Scenario: Better position replaces worse
- **WHEN** the server receives a fix that is newer, or as recent and more accurate, than the stored one
- **THEN** the alert's current position becomes that fix and the earlier fix stays in the trail

#### Scenario: Another device
- **WHEN** a device sends a location update for an alert that belongs to a different device
- **THEN** the server stores nothing

### Requirement: Location is optional and consented
The watch SHALL ask for location permission only after the wearer accepted the consent text that mentions location, and SHALL treat the permission as optional.

#### Scenario: Consent shown again
- **WHEN** a watch that accepted the earlier consent text starts after the update
- **THEN** it shows the updated consent text before reading any position

### Requirement: Map on the alert page
The website SHALL show, for an alert that has a position, a MapLibre GL JS map with an icon for the wearer at the latest fix, a circle for its accuracy, and the earlier fixes as a trail, and SHALL update it without a page reload when a newer fix arrives.

#### Scenario: Newer fix arrives
- **WHEN** the page is open and the server stores a newer fix
- **THEN** the icon moves to it and the viewer's own pan and zoom are kept

#### Scenario: No position
- **WHEN** the alert has no position
- **THEN** the page says the watch sent no position and shows no map

#### Scenario: Not a circle member
- **WHEN** a signed-in user who cannot see the alert asks for it
- **THEN** neither the alert nor its positions are returned

### Requirement: No coordinates in messages
Email and push notifications SHALL NOT contain coordinates; they SHALL link to the alert page.

#### Scenario: Notification text
- **WHEN** a notification is sent for an alert that has a position
- **THEN** its body contains no latitude or longitude

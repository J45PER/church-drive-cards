# Church Drive Satellites: changelog

## 0.82.0

- Signs in automatically through the Church Drive app (0.82.0 or later) now that its Play signing certificate is trusted.

## 0.81.0

- Account side panel like the other apps: tap the initial or swipe left for Open Church Drive, Settings, About and Sign out.
- About shows the app's signing certificate (SHA-256), tap to copy. The Church Drive app has to list the Play-signed copy's certificate before it will share its sign-in, which is why auto sign-in did not work yet.
- Settings: open on the satellite last looked at.

## 0.80.0

- Starts at the shared version number (see docs/VERSIONING.md).
- First version. Lists the house's voice satellites (found from their Design setting in Home Assistant), shows a live preview of each design and its flow, and sets each satellite's design, glances, backlight, night hours and switches.
- Not yet: adding a new satellite by scanning its QR code (see docs/SATELLITE-PAIRING.md).

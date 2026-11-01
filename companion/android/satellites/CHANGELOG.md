# Church Drive Satellites: changelog

## 0.89.0

- New app icon, themed icon and splash: Clive's mark with its top-right square turned into a circle and three ripples going out of it, every gap as thick as the ripples. On the splash only the ripples move: they appear one after another. Designed in `satellite/design/app-icons.html`. The mark is also on the sign-in screen and the empty list.

## 0.88.0

- Version moves with the shared number. No other change.

## 0.87.0

No app changes. The Home Assistant integration now removes a phone's old duplicate Church Drive app registrations, locates people from this app only and sends notices only to this app.

## 0.86.0

- Version moves with the shared number. No other change.

## 0.85.0

- Version moves with the shared number. No other change.

## 0.84.0

- No change to this app; version kept equal to the others.

## 0.83.0

- Version moves with the shared number. No other change.

## 0.82.0

- Pairing (Phase 3): the Add tab scans the QR code on a satellite's screen or sticker (or takes its name and six digits by hand), sends it the home Wi-Fi over Bluetooth (Improv), adds it to Home Assistant's ESPHome integration, waits for it to appear and opens it to set up. Remove from Home Assistant and Reset pairing are under Actions. Not yet tried on a real satellite.
- Whole-house management (Phase 2): presets (save a satellite's design, glances, backlight, night hours, volume and switches under a name; apply to any satellites), copying one satellite's settings to others, a Voice section with Home Assistant's own assistant, wake-word and finished-speaking choices, and warnings on the list ("Weak Wi-Fi", "Offline for 30 min"). A satellite with no screen is listed too, without the design, glance and backlight sections. `satellite/ha/clive-satellite-health.yaml` sends a Home Assistant notification when one is offline or weak for a while.
- Day-to-day control (Phase 1): a Status card (Wi-Fi in words and dBm, network, address, firmware, running time, when it went offline), a Sound section (speaker volume and "Say hello"), Name and room (rename, move to a room), Actions (flash the screen, clean screen, restart with a confirmation) and a Firmware card with Install when Home Assistant offers an update. Each shows only what the satellite has, so older firmware still works. Renaming and rooms need a Home Assistant administrator.
- Signs in automatically through the Church Drive app (0.82.0 or later) now that its Play signing certificate is trusted.

## 0.81.0

- Account side panel like the other apps: tap the initial or swipe left for Open Church Drive, Settings, About and Sign out.
- About shows the app's signing certificate (SHA-256), tap to copy. The Church Drive app has to list the Play-signed copy's certificate before it will share its sign-in, which is why auto sign-in did not work yet.
- Settings: open on the satellite last looked at.

## 0.80.0

- Starts at the shared version number (see docs/VERSIONING.md).
- First version. Lists the house's voice satellites (found from their Design setting in Home Assistant), shows a live preview of each design and its flow, and sets each satellite's design, glances, backlight, night hours and switches.
- Not yet: adding a new satellite by scanning its QR code (see docs/SATELLITE-PAIRING.md).

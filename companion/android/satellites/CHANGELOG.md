# Church Drive Satellites: changelog

## 0.94.2

- No change to this app; the To-Do splash was fixed.

## 0.94.1

- Fixed the crash when opening the settings sidebar: the new cog icons' drawing data was too long for Android to read, so it is now simplified (same look).

## 0.94.0

- The app is now called **Clive Satellite Manager**. Its splash reads "Clive" large, and "Satellite Manager" slides in beside it as the logo changes.
- The splash screen starts on Clive's idle logo; the squares slide to the bottom left, the top-right one turns into the circle and the ripples go out. Plays once, then stays.
- New icons throughout, from the approved icon sheet: the satellite speaker icon for the Satellites tab and list, with a small badge for what each section does (plus, sound, Clive's mic, update, pin, tap, copy, cog, history); Material Design icons for design, temperatures, glances, backlight, back, close and the signing certificate.
- The Status icon follows the Wi-Fi signal (four bars down to one, crossed out when offline).
- Account panel: Open Church Drive shows the Church Drive logo, About shows the Satellites logo, and Settings uses the new rounded cog.

## 0.93.0

- The launcher icon (and the themed icon) is smaller inside its circle, so the outer ripple is no longer cut off at the edge on round icon masks.

## 0.92.0

Shared code change only: the connection to the house (in `core`) asks the house whether Google push can reach the phone. No visible change in this app.

## 0.91.0

No app changes. The People card's old-location warning now points to the Church Drive app (Share my location, then Allow all the time and Unrestricted battery) instead of the Home Assistant app.

## 0.90.0

- The sign-in page (with the new mark above it), the cards, section headings, pills, tiles and the round initial button now come from the shared code in `core` (the same as To-Do uses) instead of private copies. They look the same.

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

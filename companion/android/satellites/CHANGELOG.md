# Church Drive Satellites: changelog

## 0.111.2

- Unselected buttons are a faint wash, not an outline (see the phone app).

## 0.111.1

- No change to this app (a dashboard light card change); version kept equal.

## 0.111.0

- Light text and icons on the dark cards (see the phone app).

## 0.110.0

- No change to this app; version kept equal.

## 0.109.0

- The Syncing wording, the states retry and the house icons (see the phone app).

## 0.108.0

- No change to this app; version kept equal.

## 0.107.0

- The card glow (see the phone app).

## 0.106.0

- The new colours (see the phone app).

## 0.105.1

- Built-in colours updated (see the phone app); the update banner is neutral.

## 0.105.0

- Live colours (see the phone app).

## 0.104.3

- Readable colours in the dark (see the phone app).

## 0.104.2

- No change to this app; version kept equal to the others.

## 0.104.1

- No change to this app; version kept equal to the others.

## 0.104.0

- **Update notice.** A banner and a once-a-day question when a newer version is out (a Play install uses Google Play's update screen; a test build asks GitHub). The house's own colours replace Material You (see the phone app).

## 0.103.0

- No change to this app; version kept equal to the others.

## 0.102.0

- No change to this app; version kept equal to the others.

## 0.101.0

- No change in this app. Icons and cards are now also available as files from Home Assistant (docs/CLIVE-ASSETS.md); the app does not read them yet.
## 0.100.1

- No change to the Satellites app; version kept in step.

## 0.100.0

- Satellite device icons redrawn in the Clive set.

## 0.99.0

- No change to the Satellites app; the version number moves with the other apps.

## 0.98.0

- No change to the Satellites app; the version number moves with the other apps.

## 0.97.0

- Every icon signed off in the icon audit up to #37 is now built in. New in this version: the climate set (thermometer, thermostat and its auto form with Clive's mark, the leaf, a house with a thermometer), Gregg's status icons rebuilt from the common house, !, bolt and pause bars (and the standard Gregg now replaces the plain robot on the Cleaning tab, the home panel and the watch), our own broom, the Devices icons by type (server or hub, phone or tablet, network, screen), people (a person, a group, an administrator), the shopping cart (empty and all bought), the calendar (today, done, a time, cancelled, moved on), the Send arrow, volume (low, medium, high, max, off and mute, with the speaker, subwoofer and the two smart speakers), the Wi-Fi cone (rounded, with empty and unknown) and the whole weather set.
- The satellite volume badge and the Satellites app's Wi-Fi icons use the new shapes. Where a screen used a Material icon for any of these (To-Do's Mine and Shared, shopping, date, Send, the Cleaning tab and the Devices page), it now draws the house's own, and so does any Home Assistant icon name that means the same thing (for example `mdi:weather-partly-cloudy`, `mdi:cart`, `mdi:account-group`, `mdi:wifi-strength-2`).
- In Home Assistant the same shapes are an icon set of their own, `cd:` (for example `cd:weather-rainy`, `cd:cart`, `cd:thermostat`), so any dashboard card can use them.

## 0.96.0

- The signed-off icon audit (#1 to #26) is built in. Everywhere the apps drew a Material icon for one of these, they now draw the house's own: add, minus, close, back, the chevrons, info, sign out and in, check for updates, the eye (and its crossed-out form), the location pin, history, the whole battery (every 10%, charging, low, unknown), the shield family for the alarm (home, away, night, disarmed, arming, triggered, signing key), the padlock and key, the lightbulb (on, off, lit, group), the lightning bolt, the light scenes (sun, half sun, flower, moon, palette), the settings cog, the notifications chat, the House, the To-Do logo and the play, pause and stop controls at one size. Any Home Assistant icon name that means one of these (for example `mdi:lightbulb`, `mdi:battery-60`, `mdi:shield-home`, `mdi:map-marker`) is drawn the same way, so the dashboards and cards match. Lights with their own icon from the Hue pack keep it.
- The satellite badges (add, settings, voice, location, updates, history), the widgets and the Satellites app's own buttons use the new shapes too.

## 0.95.6

- No change to this app.

## 0.95.5

- No change to this app.

## 0.95.4

- Fix the app crashing the moment it opened (since 0.95.0): the new "is this person an administrator" setting was created after the line that first set it, so starting the app failed every time. It is created first now. The crash screen added in 0.95.3 also no longer starts the app's main part behind it, which is why it never showed.

## 0.95.3

- If the app crashes, the next time it opens it shows what went wrong on a plain screen (with Copy and Try again), so a crash on launch can be read and sent instead of just seen as the app closing.

## 0.95.2

- Fix the app crashing: the satellite icons with a badge (and the To-Do logo) had path data of 10 to 19 thousand characters, too long for Android to read back safely, so opening a satellite could crash the app, and it then reopened on that satellite. They are redrawn from the same shapes in under 4 thousand characters.

## 0.95.1

- No change to this app; the icons in the phone and To-Do apps were updated.

## 0.95.0

- A scanner of the app's own replaces Google's: the camera is dimmed and shows clearly only through the overlap of two squares in Clive's glow colours (the cut in Clive's logo). When a code is read the frame turns teal and a card confirms it (the six digits, or the ID on a sticker) to check against the satellite. The app now asks for the camera.
- Satellites have a permanent ID, `RND1-000123-4`: a model (RND round screen, SPK speaker, DSP small display), the hardware generation, a running number and a check character, so a typing mistake is told from a code that doesn't exist. You can type the ID instead of the name when you can't scan.
- A sticker QR code carries the satellite's own API key, so nothing shared is typed to add it; the shared key is only asked for with a screen QR code that has none.
- Only an administrator can add a satellite; other people are told so on the Add tab.

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

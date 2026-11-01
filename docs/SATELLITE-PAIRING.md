# Satellite pairing and the companion app (proposal)

Status: **written, not tried on a real satellite.** The firmware (`satellite/clive-round.yaml`) has `esp32_ble` + `improv_ble`, opens the pair screen by itself when there is no Wi-Fi, accepts Wi-Fi over Bluetooth only while that screen shows, and has a Reset pairing button. The app's Add tab scans the QR, sends the Wi-Fi over Bluetooth (Improv GATT client in `ImprovBle.kt`), adds the device through Home Assistant's ESPHome configuration flow (`AddToHouse.kt`) and opens it.

How it differs from the proposal below: the QR code's six digits are only for the person to check against the screen (there is no per-device secret); the API encryption key is the one every satellite is built with, typed into the app once and kept on the phone, rather than a fresh key per device; and satellites without a screen (a sticker QR, no button yet) are listed and configurable but their pairing hardware is not designed.

## IDs, stickers and keys (0.95.0)

Every satellite has one permanent ID printed on its sticker, for example `RND1-000123-4`: a model code (RND round screen, SPK speaker with no screen, DSP small display), the hardware generation (1 to 9), a six digit running number for that model and generation, and a check character (Luhn mod 32 over Crockford base 32, no I, L, O or U) so the app can tell a typing mistake from a code that doesn't exist. The same ID gives the device's name on Bluetooth, the network and in Home Assistant: `clive-rnd1-000123`.

`python3 satellite/tools/make_device.py --model RND --gen 1 --room "Living room"` makes a new one: it takes the next free number from `satellite/devices/registry.csv` (git-ignored, because it holds every device's key: back it up), creates the satellite's own API key, writes `satellite/devices/<id>/<id>.yaml` to compile and flash, and draws the sticker for a 14 x 60 mm label (a Phomemo D30). The sticker's QR code is `clive://pair?id=...&k=<its own key>` (`&d=<name>` is optional), so the app adds it with its own key and nothing shared is typed. A satellite built straight from `clive-round.yaml` still uses the shared `clive_round_api_key`, for development.

Only a Home Assistant administrator can add a satellite.

**Satellites with no screen** include the ESPHome package `satellite/pairing_button.yaml` (see `satellite/example-screenless.yaml`, which passes `esphome config`). Holding the button for 3 seconds opens a two minute window in which Bluetooth accepts the home Wi-Fi; a satellite that has no Wi-Fi yet opens the window by itself 20 seconds after it starts. The device's own file adds its chime and light to the `pairing_started` script. The window and the button are not tried on a board yet. The app tells the person to hold the button, scans the sticker (the QR carries the device's own key) and sends the Wi-Fi as it does for a screen.

**Home Assistant out of reach:** the round satellite replaces the date on its resting screen with "can't reach the house" after a minute without a connection, and puts the date back when it returns. The clock stays.

## The plan

A third app, the **satellite companion app**, adds, names and configures every satellite: the round one, other displays, and ones with no display. Mockups are in `satellite/design/flows.html`.

Things it sets per satellite: name and room (a Home Assistant area), the **design** (one of four complete flows: Glance, Clock, Weather, Ring, each previewed live in the app), the three glance slots for the Glance design (who is home, electricity, car, alarm, heating, air quality, or the forecast icon), the room whose temperature is shown inside (Glance only), the backlight schedule, and a "show on the satellite" test. The outside temperature is always the weather feed's and has no setting.

## Pairing

A satellite does not need a display to be paired. Every device needs a **button** (proof you are standing next to it) and a **speaker or light** (so it can answer). The QR code can be on the screen, on a sticker, or both.

| Kind of device | Shows | How |
|---|---|---|
| Touch display (the round screen) | QR on screen under Settings > Pair or reset, and by itself on first boot | Scan it in the app |
| Small display, no touch (128 x 64 black and white) | A small QR and a 6 digit code | One button starts pairing. Scan, or type the six digits |
| No display | A QR sticker on the underside and the box | Hold the button 3 seconds, it chimes and the light pulses. Scan the sticker, press the button when it chimes again |

Common mechanics:

1. A device that has never been set up advertises over Bluetooth (`improv_ble`) and falls back to its own hotspot (`captive_portal`).
2. The app scans the QR. The QR holds the device id and a **one-time token** that expires after 10 minutes. It never holds the permanent encryption key.
3. The app connects over Bluetooth and sends the home Wi-Fi.
4. The device joins Wi-Fi. The app gives it a name, a room and a **fresh API encryption key**, and tells the Church Drive integration to adopt it in Home Assistant.
5. The device chimes or shows a tick and comes up on its design's resting screen.

A sticker is the baseline because it works for every device. An on-screen QR is a convenience for ones that have a display, and has the advantage that it can be a fresh code each time.

## Open questions

- Who may add a satellite: any signed-in person, or administrators only (as with the activity log)?
- Is the sticker secret printed at flash time, or is the sticker only an id and the button press is the secret?
- What does a satellite do when it loses Home Assistant for a long time: keep the clock and a "can't reach the house" line on the idle layout?
- Where does the date line's inside temperature come from: a chosen room sensor per satellite, set in the app?

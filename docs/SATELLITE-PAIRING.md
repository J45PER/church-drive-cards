# Satellite pairing and the companion app (proposal)

Status: **proposal, not built**. The ESPHome parts named here exist (checked against ESPHome 2026.10.1): `improv_ble`, `improv_serial`, `captive_portal`, `qr_code`. The app, the one-time token and the adoption step are not written or tested.

## The plan

A third app, the **satellite companion app**, adds, names and configures every satellite: the round one, other displays, and ones with no display. Mockups are in `satellite/design/flows.html`.

Things it sets per satellite: name and room (a Home Assistant area), the idle layout (A to H in `satellite/design/idle-layouts.html`, E chosen first), whether the date line shows the outside and inside temperature, the three glance slots (who is home, electricity, car, alarm, heating, air quality, or the forecast icon), the backlight schedule (day and night, night using layout H), and a "show on the satellite" test.

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
5. The device chimes or shows a tick and comes up on its idle layout.

A sticker is the baseline because it works for every device. An on-screen QR is a convenience for ones that have a display, and has the advantage that it can be a fresh code each time.

## Open questions

- Who may add a satellite: any signed-in person, or administrators only (as with the activity log)?
- Is the sticker secret printed at flash time, or is the sticker only an id and the button press is the secret?
- What does a satellite do when it loses Home Assistant for a long time: keep the clock and a "can't reach the house" line on the idle layout?
- Where does the date line's inside temperature come from: a chosen room sensor per satellite, set in the app?

# Clive satellite: the round screen

A voice satellite for **Clive** built on the Waveshare ESP32-P4-WIFI6-Touch-LCD-3.4C (The Pi Hut: "ESP32-P4 3.4" Round Touch Display Dev Board (800x800)", Waveshare SKU 31523, about the size of a coaster). It listens for the wake word, answers through its speaker, and draws Clive's cards on the round screen.

**Status: draft.** `clive-round.yaml` passes `esphome config` on ESPHome 2026.10.1. It has not been flashed to a real board, so the items under "Check on first boot" are unproven.

## What it does

| Part | How |
|---|---|
| Wake word | `micro_wake_word` on the device. `hey_jarvis` for now, because the apps already ignore "Jarvis" at the start of a sentence. A custom "Clive" model can replace it. |
| Speech | ESPHome `voice_assistant` sends it to Home Assistant's Assist pipeline, which is the Claude conversation agent with Clive's prompt (`ha/clive-prompt.md`). Nothing about Clive changes. |
| Answer | Spoken through the onboard speaker (ES8311 codec and amplifier). |
| Screen | LVGL on the 800x800 round panel: clock when idle, "Listening", a spinner while thinking, the reply text, and Clive's cards. A tap while idle starts listening. A tap on a card puts the clock back. A card goes by itself after 25 seconds. |
| Cards | `ha/clive-satellite-show.yaml` forwards the `church_drive_assistant_show` event to the device while it is the one being spoken to. |

## Hardware (from the Pi Hut page and Waveshare's wiki, repo and schematic)

ESP32-P4 (dual core 360 MHz, 32 MB PSRAM, 32 MB flash), ESP32-C6 for Wi-Fi 6 and Bluetooth 5 over SDIO, 3.4" round IPS 800x800 (JD9365 over MIPI-DSI, 2 lanes), GT9271 touch (GT911 compatible), ES7210 microphone ADC with onboard mics and echo cancellation, ES8311 codec with an amplifier and an 8 ohm 2 W speaker (included), microSD, USB-A and USB-C, MIPI-CSI camera connector, 40-pin header.

Pins used (Waveshare's Arduino examples and schematic, `waveshareteam/ESP32-P4-WIFI6-Touch-LCD-XC`):

| Function | GPIO |
|---|---|
| I2C SDA / SCL (codec 0x18, mic ADC 0x40, touch) | 7 / 8 |
| I2S MCLK / BCLK / LRCK | 13 / 12 / 10 |
| I2S data out (speaker) / in (mics) | 9 / 11 |
| Speaker amplifier enable (high = on) | 53 |
| LCD reset / backlight PWM / backlight enable | 27 / 26 / 33 |
| Touch reset | 23 (interrupt pin not published) |
| ESP32-C6 SDIO clk, cmd, d0 to d3, reset | 18, 19, 14 to 17, 54 |

ESPHome has a built-in display model for this exact board, `WAVESHARE-ESP32-P4-WIFI6-TOUCH-LCD-3.4C`, with Waveshare's panel init sequence, so no display tables are kept here.

## Setup

1. Install ESPHome (the Home Assistant add-on or `pip install esphome`).
2. Copy `secrets.example.yaml` to `secrets.yaml` next to `clive-round.yaml` and fill it in. `secrets.yaml` is git-ignored. Make the API key with `python3 -c "import secrets,base64;print(base64.b64encode(secrets.token_bytes(32)).decode())"`.
3. First flash over the board's **USB-C UART** port: `esphome run clive-round.yaml`. Later updates go over the air.
4. Home Assistant finds it as a new ESPHome device. Adopt it, give it an **area** (that is what lets Clive know which room he is in), and in Settings > Voice assistants set the satellite's pipeline to the Claude one.
5. Install `ha/clive-satellite-show.yaml` as an automation after changing `assist_satellite.clive_round_screen` to the entity your device created. The action name `esphome.clive_round_show_card` follows the node name.

## Check on first boot

These are the guesses I could not test without the board:

- **Chip revision.** If it will not boot or the log names the chip revision, set `engineering_sample: true` under `esp32:`.
- **Touch address.** The interrupt pin is not published, so the GT911 may answer on `0x14` instead of `0x5D`. If the log says the touch chip is not found, change `address`.
- **Microphone level.** `mic_gain` (ES7210) and `auto_gain` / `volume_multiplier` (voice assistant) are starting values. If Clive mishears you, raise the gain. If he hears himself, lower the speaker volume or the gain.
- **Flash size** is declared as 16 MB because ESPHome only allows over-the-air updates above that with an experimental flag. The chip has 32 MB.
- **Screen layout** was laid out for the circle (text kept inside about 520 px of the centre) but never seen. Expect to nudge `y` offsets and fonts.
- **Weather and list cards** are shown as a title, one big line, a sub line and up to five rows. The phone and watch apps draw richer layouts (icons, club badges, hourly strip). Those are not drawn here yet.

## Design

The look follows the watch app's Talk face scaled to 800 px, written up in [`docs/CLIVE-STYLE.md`](../docs/CLIVE-STYLE.md). `design/clive-round-ui.html` is an interactive mockup of every state, `design/idle-layouts.html` has eight idle layouts (E chosen), and `design/flows.html` covers the idle screen, quick settings, actions and pairing. Open them in a browser. Pairing and the companion app are proposed in [`docs/SATELLITE-PAIRING.md`](../docs/SATELLITE-PAIRING.md). The firmware in `clive-round.yaml` is still the simpler first draft (flat colour pages); the glow, the animated mark and the card layouts in the mockup are what it should grow into.

## Not done yet

- **Which room.** Clive's TV and PC control is held back until a room-aware tool exists (`ha/clive.md`). The satellite's area is the input for that, but the tool is not built. It is the next step and does not depend on this device working first.
- **Custom "Clive" wake word.** Needs a trained `micro_wake_word` model.
- **A person.** A wall device has no signed-in user, so Clive treats the speaker as unknown and asks which of Die Ann, Hayley, Ian or Jamie for anything personal, as `ha/clive-prompt.md` already says.
- **Bigger card art.** Weather symbols and club badges.

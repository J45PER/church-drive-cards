# Clive satellite: the round screen

A voice satellite for **Clive** on the Waveshare ESP32-P4-WIFI6-Touch-LCD-3.4C (The Pi Hut: "ESP32-P4 3.4" Round Touch Display Dev Board (800x800)", Waveshare SKU 31523). It listens for the wake word, answers through its speaker, and shows Clive's design on the round screen. The look is in [`docs/CLIVE-STYLE.md`](../docs/CLIVE-STYLE.md).

## Status

| Part | State |
|---|---|
| Firmware compiles for the ESP32-P4 (ESPHome 2026.10.1) | Yes: 4.6 MB of the 8.1 MB app partition, 18% RAM |
| The screen, run in ESPHome's desktop simulator (real LVGL rendering) | Yes: all four designs, the glow, the dock and mark, cards, quick settings, menu, pairing QR (see `tools/sim_shots.sh`) |
| Flashed to a real board | **No.** Touch address, chip revision, audio levels, the glow's frame rate and the mark's animation speed are untested |
| Home Assistant automations (`ha/`) | Written, templates syntax-checked, **not installed or run** |
| Pairing (QR, Bluetooth, adopting the device) | Proposal only: [`docs/SATELLITE-PAIRING.md`](../docs/SATELLITE-PAIRING.md) |
| Companion app | `companion/android/satellites/`: lists and configures satellites; pairing not built |

## What it does

| Part | How |
|---|---|
| Wake word | `micro_wake_word` on the device. `hey_jarvis` for now, because the apps already ignore "Jarvis" at the start of a sentence. A custom "Clive" model can replace it. |
| Speech | ESPHome `voice_assistant` sends it to Home Assistant's Assist pipeline, which is the Claude conversation agent with Clive's prompt (`ha/clive-prompt.md`). Nothing about Clive changes. |
| Resting | Black ground and no glow, in one of four designs (Glance, Clock, Weather, Ring), with Clive's mark small in a dock at the bottom. |
| Listening, thinking, speaking | The Clive glow is the background, the resting layout stays on top, and only the mark in the dock changes pose. Nothing he says is written on the screen. |
| Cards | When an answer needs a graphic the resting layout fades into a card. Plain answers and plain confirmations are only spoken. |
| Quick settings | Swipe down from the top edge: volume, brightness, mic, spoken answers, follow-up listening, night, clean screen, and a Settings button. |
| Settings menu, pairing | Settings > Pair or reset shows a QR code and a six digit code for the companion app. |

Settings that Home Assistant and the app can change are ESPHome entities: **Design**, **Glance 1 to 3**, **Backlight day / night**, **Night starts / ends (hour)**, the switches **Microphone**, **Spoken answers**, **Follow-up listening**, **Night mode**, a **Volume** number, the buttons **Restart**, **Identify** and **Clean screen**, and diagnostics (**Wi-Fi signal**, **Uptime**, **IP address**, **Wi-Fi network**, **ESPHome version**). The companion app shows and drives all of them.

## Files

| File | What |
|---|---|
| `clive-round.yaml` | The device: ESP32-P4, Wi-Fi through the C6, audio, wake word, voice assistant, touch, backlight, entities. Includes `ui.yaml`. |
| `ui.yaml` | **Generated** by `tools/gen_ui.py`: fonts, the mark's frames, the whole LVGL widget tree, scripts, the design selects. Do not edit; change the generator. |
| `clive_ui.h` | The UI logic in C++: glow animation, state, cards, shade, menu, pairing token. |
| `clive_icons.h` | **Generated**: Material Design Icons as UTF-8 strings. |
| `assets/mark/` | The mark's animation frames, rendered by `tools/make_mark_frames.py` (LVGL has no even-odd paths). |
| `sim.yaml`, `sim_hooks.yaml`, `sim_previews.yaml` | The same UI in ESPHome's host simulator (SDL), with the hardware stubbed. |
| `tools/sim_shots.sh` | Runs the simulator's scripted tour under a virtual display and saves a screenshot of every state. |
| `tools/sim_previews.sh` | Renders the companion app's design previews from the simulator. |
| `ha/clive-satellite-info.yaml` | Pushes the weather, who is home, the rate, the car, the alarm and the heating to the screen. |
| `ha/clive-satellite-show.yaml` | Sends Clive's cards to the screen while it is the device being spoken to. |
| `ha/clive-satellite-answer.yaml` | Passes a tapped Yes or No to Clive. |
| `ha/clive-satellite-health.yaml` | Notifies when a satellite has been offline for 10 minutes or its Wi-Fi has been weak for 15. |
| `design/` | The HTML mockups the firmware was built from (`flows.html`, `idle-layouts.html`, `clive-round-ui.html`). |

## Setup

1. Install ESPHome (the Home Assistant add-on or `pip install esphome`).
2. Copy `secrets.example.yaml` to `secrets.yaml` and fill it in (`secrets.yaml` is git-ignored). Make the API key with `python3 -c "import secrets,base64;print(base64.b64encode(secrets.token_bytes(32)).decode())"`.
3. First flash over the board's **USB-C UART** port: `esphome run clive-round.yaml`. Later updates go over the air.
4. Home Assistant finds it as a new ESPHome device. Adopt it, give it an **area**, and in Settings > Voice assistants set its pipeline to the Claude one.
5. Install the automations in `ha/` (show, answer, info, and optionally health). They use this house's entity ids (`weather.forecast_home`, the four `person.*`, the Octopus rate, the Astra's battery, the alarm, `climate.downstairs`). Change `assist_satellite.clive_round_screen` in `clive-satellite-show.yaml` to the entity your device created.

## Changing the screen

```bash
python3 tools/gen_ui.py                       # regenerate ui.yaml and clive_icons.h
esphome compile sim.yaml                      # the desktop simulator (needs libsdl2-dev)
tools/sim_shots.sh sim-shots                  # a screenshot of every state, headless (needs xvfb and imagemagick)
```

## Check on first boot

- **Chip revision.** If it will not boot or the log names the chip revision, set `engineering_sample: true` under `esp32:`.
- **Touch address.** The interrupt pin is not published, so the GT911 may answer on `0x14` instead of `0x5D`. If the log says the touch chip is not found, change `address`.
- **Glow frame rate.** The glow is three radial-gradient circles redrawn about 16 times a second in software. If it is choppy or the touch lags, lower the update rate (the 60 ms interval in `tools/gen_ui.py`) or the glow's size.
- **Mark animation.** The mark plays pre-rendered frames at fixed speeds (4 s, 1.4 s, 1.2 s, 0.7 s loops). It does not yet swell with your voice.
- **Microphone level.** `mic_gain` (ES7210) and `auto_gain` / `volume_multiplier` (voice assistant) are starting values.
- **Wake-word model.** `micro_wake_word` downloads `hey_jarvis` at build time from GitHub.

## Not done yet

- **Pairing** from the app (QR scan, Bluetooth hand-over of the Wi-Fi, adopting the device).
- **Which room.** Clive's TV and PC control is held back until a room-aware tool exists (`ha/clive.md`). The satellite's area is the input for that.
- **The `ask` card.** The Yes / No pills exist on the screen, but `clive_show_card` has no `ask` kind yet, so nothing sends one.
- **Club badges and weather icons for every condition** on cards. Cards show a white Material icon and text.
- **A custom "Clive" wake word.**

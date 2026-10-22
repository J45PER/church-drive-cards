# Clive on the Home Assistant side

Clive is the preferred Assist pipeline ("Home Assistant Cloud"): wake word `hey_jarvis` (openWakeWord, local), Home Assistant Cloud speech-to-text and text-to-speech, and the Claude conversation agent (`conversation.claude_conversation`, Haiku 4.5, the Anthropic integration with the household's own API key). `prefer_local_intents` is on, so simple commands never reach Claude.

What Clive can reach is decided by **voice assistant exposure** (Settings > Voice assistants > Expose), which is global, not per person. Everything below changes only `conversation`; Alexa and Google exposure were left alone.

Naming: scripts and code are named `clive_*` (the live scripts were renamed from `jarvis_*` on 2026-10-21; same configs, checked by config hash). The old name survives only in the wake word model `hey_jarvis` (a model name, not ours to rename) and in old log ids that must never change. Nothing in code should hard-code the assistant's name: use `assistantName` / the house's name in the app, and generic names (`Assistant*`) for code identifiers.

The `clive-*.yaml` files here are copies of the live scripts. Edit them in Home Assistant and re-export, or paste a file back to restore it.

## Scripts (all exposed to Assist)

| Script | What it does |
|---|---|
| `clive_apply_scene` | Applies a scene from the Church Drive scene library (`church_drive.apply_scene`) to a room, zone or light. The scene list is typed in by hand: add new Scene Builder scenes to it. |
| `clive_arm_alarm` | Arms the alarm (Home or Away). **There is no disarm.** The alarm panel itself is not exposed; "is it set?" reads `sensor.alarm_status`. |
| `clive_zappi_info` | The whole car-charge picture in one answer (plug, charger, mode, rate, cheap or not, cheapest rate and when, smart charge settings). |
| `clive_zappi_charge` | Sets the charge mode (Fast, Eco, Eco+, Stopped). Fast at a non-cheap rate asks first, see below. |
| `clive_zappi_settings` | Ready-by time, max price, smart charge on/off. Never starts or stops a charge. |
| `clive_home_safety_check` | Read-only: smoke/heat/CO alarms, tamper, low batteries of home devices (not phones). Finds devices by device class. |
| `clive_show` | Puts a picture and/or an https link on the screen of the person who asked (event `church_drive_assistant_show`, carrying their Home Assistant user; the Church Drive app shows it under his answer). Does nothing on a device without a screen, so his spoken answer must stand alone. |
| `clive_show_card` | Puts a card on the screen of the person who asked: the weather, a score or fixture with club badges, what is playing (same event as `clive_show`, with a `card`). Drawn by the Church Drive watch app; other screens ignore it, so his spoken answer must stand alone. |
| `clive_vacuum` | Gregg: start, pause, stop, status. Nothing else. |

Also exposed for the car: `zappi_charge_later`, `zappi_car_yes`, `zappi_car_no` (the existing scripts, unchanged), the smart charge helpers, and four Zappi sensors (status, plug status, charging power, charge added).

## Deliberately not exposed

- **Sirens** (5 switches, 5 siren entities) and the **alarm panel** (no voice disarm).
- **Camera motion-detection switches** (Driveway, Entrance, Garden, Living Room, Front Doorbell): they are motion on/off switches.
- **Energy and cost data** (Octopus electricity and gas, Zappi energy and grid power): admin-only, and exposure is global.
- **TVs and PCs** (`media_player.*`): until a room-aware tool exists. The app has no room, so "only the device in the same room" cannot be enforced yet. Needs a custom LLM API in this integration that reads the satellite's area. Build it with the first satellite.
- **`select.zappi_charge_mode`**: only reachable through `clive_zappi_charge`.
- **The car** (Vauxhall Astra): read-only sensors only (battery, range, fuel, fuel range, plugged, charging, charging end, mileage, service due, doors, boot). No buttons, switches or numbers, so no lock, horn or preconditioning.
- **Hue scenes** (133) and the integration's "Church Drive" working scenes: Clive uses the library through `clive_apply_scene` instead.
- Gregg's own vacuum entity, Find Robot switch, and the Ring chime switches.

## The Fast-charge confirmation

`clive_zappi_charge` with mode Fast at a rate that is not cheap does not start. It stamps `input_datetime.zappi_fast_charge_confirm_asked`, and returns `needs_confirmation` with the current and cheapest rates. Clive must ask and wait. A repeat call with `confirm_expensive: true` only counts if it arrives **5 to 300 seconds** after that stamp. The first version trusted the flag, and Clive set it himself on the first call, so the window is enforced in the script.

The helper is an `input_datetime` (date and time), initial `2000-01-01 00:00:00`.

## Gotchas learned the hard way

- **Check state after a write, not the spoken reply.** Clive says "Done" whenever a script runs without error. A template condition that returned a non-boolean string (a time) was silently false.
- **Script `variables` re-sort alphabetically when saved** through the API, so a variable that reads another declared "later" breaks. Use separate `variables` steps, or one template.
- **Datetimes and lists of datetimes become strings between variables.** Do date maths in a single template (as the Zappi smart charge automation does).
- **A script can return data to Clive** with `stop` and `response_variable`; the result is shown to him as a tool result.
- **"Waiting for EV" means the car IS plugged in** but not asking to charge (usually full). Only "EV Disconnected" is unplugged.
- **Octopus Go publishes tomorrow's cheap window only after midnight**, so tomorrow's rates hold just one slot. `clive_zappi_info` projects the 00:30 to 05:30 window from today's pattern and says so.
- **Battery-low sensors come from Battery Notes**, not the phone app, so phones are excluded by device (`device_id` of `mobile_app` entities).
- **Don't let him overclaim.** The safety check says what it did not check (doors, windows, locks, the burglar alarm).

## Who is asking, web search, pictures (prompt additions)

Each person signs in to the app with their own Home Assistant user, so the conversation agent can know who is asking: the prompt is a template and `user_name` is the signed-in user's name. Add this to the prompt (Settings > Devices & services > Claude > Claude conversation), after "Where you are":

```
{% if user_name %}The person talking to you is {{ user_name }}. When they say "my", "me" or "I", they mean {{ user_name }}: for their tasks or to-dos read the list called "Priorities" followed by their first name, and also the shared priorities list, and never ask which person they are. {% else %}You do not know who is talking, because this device has nobody signed in, so for anything personal such as to-dos ask which of Die Ann, Hayley, Ian or Jamie it is for. {% endif %}
```

Web search: the Claude agent already searches the web (sport scores and fixtures, news, facts), as the prompt asks. Check that "Allow web search" is on in the same settings page.

Pictures and links: add to the prompt, after "What you can see and do":

```
When a picture or web page would genuinely help (what something looks like, a recipe, a match report) and a web search gave you a real https address, call the show on screen script as well as answering aloud. Your spoken answer must make sense without it, because many devices have no screen. Never read a web address aloud.
```

Cards: the full, current prompt (with a mandatory card paragraph, and the order of speaking and calling scripts that Home Assistant needs) is in [clive-prompt.md](clive-prompt.md). Use that, not a paragraph added to an old prompt.

Screens are decided case by case: the Church Drive Android app listens for `church_drive_assistant_show` and shows it to the person it is for. A satellite with a display needs its own listener that filters on the event's `context.user_id` or its own device; satellites without one need nothing.

## The assistant's name (one setting)

Set it in Home Assistant: Settings > Devices & services > Church Drive > Configure > Assistant name (default Clive). It reaches:
- the apps, the watch and Church Drive To-Do, through `church_drive/assist/info` (remembered on each device);
- Clive's own prompt, through `sensor.church_drive_assistant`. Start the Claude conversation prompt with `You are {{ states('sensor.church_drive_assistant') }}, the voice assistant of the Church Drive household` instead of typing the name;
- spoken phrases: the apps still strip "Jarvis" and "Clive" as well as the current name from sentences.

Not covered, because they are not in this repo: the wake word (an openWakeWord model, `hey_jarvis` until a new one is trained; the apps show whatever the Assist pipeline's wake word is), the watch launcher label (a fixed Android label) and the Assist pipeline's name in Home Assistant.

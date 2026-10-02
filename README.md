# Church Drive

A Home Assistant integration for the Church Drive house. Today it delivers the house's custom Lovelace cards: it serves the cards bundle and loads it on every dashboard, so there's no Lovelace resource to manage. It also keeps a library of **universal scenes** that any Light Control card can use in any room or zone:

- **White scenes** (Bright, Cool bright, Dimmed, Read, Concentrate, Energise, Relax, Rest, Nightlight, using the Hue app's own values). They're set with one `light.turn_on`, so nothing is stored on the Hue bridge.
- **Colour scenes**: Hue's animated ones (Soho, Magneto, Ruby glow, Emerald isle, Lake Placid and others). They're played through one hidden working scene per Hue room or zone called "Church Drive", so they can animate like Hue's dynamic scenes, and gradient strips show several colours. Tap a playing scene to pause it, and tap again to play.
- **Your own scenes**, from the **Scene Builder card**: white (colour temperature + brightness) or up to nine colours, optionally animated at a chosen speed, with an optional icon. "Try in" plays an unsaved scene on a real room.

Pick them in a light card's Scenes list, once for the room and once for each of its zones (e.g. "Bright · Kitchen Spotlights" sets only the spotlights). Each Hue room and zone also gets a **scene select** entity (e.g. `select.kitchen_scene`). It shows the universal scene the room is on, choosing an option applies it, and it's handy for automations. The `church_drive.apply_scene` action does the same for any lights. Optionally the white scenes can also be created as real Hue scenes in chosen rooms (Settings → Devices & services → Church Drive → Configure). That's useful for Hue switches and the Hue app. It runs at startup, on options change and on the `church_drive.sync_scenes` action.

The cards share one bundle and one look: no border, drop shadow, rounded top/bottom corners only, hover tints on interactive elements.

## Cards

Every card is set up like a built-in one: **Add Card** → search for it, pick it from the preview, and configure it in the visual editor. No YAML is needed. The YAML examples below are for reference or copy-paste only.

### `battery-zone-card` / `gauge-zone-card`
A zone/room card listing measurable values (battery %, storage used, signal strength, anything with a value and a max) as gradient-filled rows, sorted worst-first, colour-coded red/orange/green. `battery-zone-card` is `gauge-zone-card` with battery-friendly defaults (auto battery-icon stepping, low-value-is-bad colouring, Battery Notes `battery_last_replaced` integration).

```yaml
type: custom:battery-zone-card
title: Entrance
entities:
  - entity: sensor.example_battery_plus
    name: Ring Alarm Keypad
    word: replaced
```

```yaml
type: custom:gauge-zone-card
title: Storage Used
direction: high
entities:
  - entity: sensor.backup_drive_used_percent
    name: Backup Drive
    icon: mdi:harddisk
```

### `alarm-panel-card`
The status as the card title in its colour; a ring round the shield that empties during an entry or exit delay, with what to do and the timer on the same line; then Disarm / Home / Away / Night buttons (only the modes the entity supports). The card is the same size in every state, and fades towards the state colour as a delay runs out.

```yaml
type: custom:alarm-panel-card
entity: alarm_control_panel.church_drive_alarm
```

### `light-control-card`
Mode-aware light control. Has a real visual editor (Add Card → search "Light Control"). Rows are a combined toggle + brightness drag control, tinted (never literally painted) with the light's live colour so white/bright lights stay readable. Tapping a light opens Home Assistant's native more-info dialog for full colour/effects control.

- `mode: light`: one row for a single light.
- `mode: group`: the group's row, with its member lights indented underneath.
- `mode: room`: the area name as a title, then that area's Hue room/zone groups as top rows, with the individual lights indented underneath. Lights are found through the entity **or** device area, as Hue assigns areas to devices. Hidden entities and settings/diagnostic entities (e.g. an air purifier's display backlight) are skipped.

**Choosing what's shown:** in `room` and `group` (zone) modes, the editor's **Show** list picks which rows appear and in what order, each with an optional name override. A room offers its lights, its Hue room group, and any Hue zones made up only of its lights, even zones with no area. A zone offers its member lights. Leave the list empty to show everything. Each entry can also set a **Level**: `0` top, `1` child or `2` grandchild, indented 16px per level. Without one, a Hue room sits at the top, zones become its children, and lights sit one level under the deepest group.

```yaml
type: custom:light-control-card
mode: room
area: living_room
entities:
  - light.living_room
  - entity: light.living_room_ambience
    level: 1
  - entity: light.tv_table_lamp
    name: Reading lamp
    level: 2
```

Each row says its state on the right (**Off**, a brightness like **45%**, or **On** for on/off-only lamps). Off rows are dimmed, and lit rows are tinted with the light's colour (white lights get a clearly warm or cool tint). Tap a row to toggle, drag across it to set brightness, and tap the tune icon for the native more-info dialog. Rows can have an **icon override**: per Show-list entry, or the card's *Icon override* for single-light and zone cards.

**Demo mode** (`demo: true`, in the editor's *Demo mode* section) swaps Home Assistant for a built-in pretend home, so the card can be tried without touching real lights. `demo_room` picks `living_room` (a room plus a zone of animated scenes, including a white-only lamp and an on/off-only lamp) or `bedroom` (two groups plus a hidden settings light the card should skip). Every tap, drag, scene, pause and hold works on the pretend lights, and nothing is sent to Home Assistant. The Design Presets dashboard uses only demo cards.

**Scenes** show as tiles under the lights, as tall as a light row, up to four per row and always filling the width. Names sit on one line (cut short with … if needed) and by default hide on tiles under about 100px wide, leaving just the icon; the editor's *Scene names* option can set them to always or never show, up to `max_scenes` (default 8; `0` hides them). Every card starts with the same four: Bright, Dimmed, Relax and Nightlight for its room. They're in the editor's Scenes list (shown by name and place, e.g. "Bright · Kitchen"), where you can remove, reorder or add others in the editor with an optional name, icon and uploaded picture per scene. Without a picture, a tile gets a gradient in that scene's colours. The most recently activated scene glows in its own colour while its lights are on, and the other tiles are dimmed. Animated (dynamic) Hue scenes start animating when tapped and show a pulsing ▶ while they're playing; tap a playing scene again to stop the animation and hold its colours (it then shows ⏸, and another tap plays it again). **Press and hold** any scene tile to turn off all of the card's lights. Icons follow whatever is set in Home Assistant, including custom icon packs such as `phu:` Hue icons.

```yaml
type: custom:light-control-card
mode: room
area: living_room
```

```yaml
type: custom:light-control-card
mode: group
entity: light.living_room
name: Living Room (group)
```

### `scene-builder-card`
Make your own universal scenes: a name, white or colours, brightness, animated or still with a speed, and an icon. Try them on a room before saving. Saved scenes appear in every light card's scene list. It lives on the Design Presets dashboard's **Scene builder** tab. Saving needs an admin user.

### `scene-styles-card`
The central place to style scene tiles. It sits on the Design Presets dashboard's **Scene styles** tab and previews every scene name in the house with its current look. In its visual editor, each entry picks a scene **name** and sets an icon, up to three background colours, or a picture. That style then applies to that scene in every room, on every Light Control card, on every dashboard. A light card's own per-scene overrides still win, and unstyled scenes use built-in colours matched to the Hue scene names.

Light cards read the styles from the `design-presets` dashboard once per page load, so reload other dashboards to pick up changes.

### `climate-card`

One thermostat, radiator valve or air conditioner, laid out like the alarm card. The title is in the status colour (heating orange, cooling blue, Eco green, off grey) with a one-word status beside it. A gauge from the device's min to max shows the target (a tick, with the number in the middle) and the room temperature (a white dot); it's for reading, not dragging. Humidity and the outside temperature sit beside it, and the room temperature is big on the right. The card tints towards its colour while heating or cooling.

Every row below can be switched on or off in the editor (*Rows to show*):
- **Temperature history** and **Humidity history** (24 hours; off by default). With both on they share one graph: temperature in the status colour with the target dashed, humidity in purple, each on its own scale.
- **− and +**: two wide buttons; taps are gathered and sent once you stop.
- **Mode**, **Preset**, **Fan speed** and **Swing** dropdowns, full width. Fan speed and swing only appear on devices that have them.
- **Quick settings**: one row of up to 5 tiles, each setting a mode, a preset and/or a temperature. The one that matches glows. The defaults are favourite temperatures (cooling ones on air con) plus Eco; **Reset** in the editor puts them back.

*Other sensors* (optional): an outdoor temperature (a weather entity or sensor), a window or door sensor (shows a "window open" warning), a humidity sensor for devices without one, and extra readings shown as small chips. `demo: true` uses a pretend thermostat.

```yaml
type: custom:climate-card
entity: climate.downstairs
outdoor_entity: weather.forecast_home
window_entity: binary_sensor.lounge_window
show_temperature_history: true
show_humidity_history: true
```

### `climate-zone-card`

One floor or zone, with a block per room or sensor. Each room has a **type** that sets its comfortable range (living room 19–22°, bedroom 16–20°, office 19–22°, hall/landing 16–21°, bathroom 20–24°, kitchen 17–21°; guessed from the name if not set, and overridable per room). The temperature is coloured on a smooth scale around that range: teal to green to lime inside it, cyan to deep blue below, amber to deep red above, and ice-white with a "Freezing: pipes at risk" warning at 0° and below. Humidity stays purple, paler when dry and deeper above 60%.

Each room's 24-hour graph shades its comfortable range, colours the temperature line by the same scale and draws humidity with dotted 40–60% limits (changeable per card). The card title takes the colour of the room furthest outside its range.

```yaml
type: custom:climate-zone-card
title: Ground Floor
rooms:
  - name: Living Room
    type: living
    temperature: sensor.downstairs_temperature
    humidity: sensor.downstairs_humidity
    icon: mdi:sofa
  - name: Entrance
    temperature: sensor.entrance_sensor_temperature
```

### `fan-card`

A fan with a speed gauge, then two rows of tiles: Off and the speeds (1, 2, 3…), then the other presets (Natural, Sleep…) and Oscillate. Tiles are grey until selected, like the alarm card. Speeds come from presets named `speed_1`… (Philips) or from the fan's percentage steps. Each row can be switched off.

### `air-purifier-card`

An air purifier: a PM2.5 gauge in air-quality colours (good ≤35, fair ≤75, poor ≤115, very poor above: the Chinese standard Philips purifiers use, changeable per card), the allergen index, a 24-hour PM2.5 graph, mode tiles and filter life bars ("Clean soon" / "Replace soon" under 25%). Every extra can be switched off. Picking the purifier finds its PM2.5, allergen and filter sensors from the same entity name prefix. The 24-hour PM2.5 graph is coloured by level, blending from green through amber and orange to red as the air gets worse, using the card's air-quality bands.

### `co-alarm-card`

A carbon monoxide alarm (e.g. X-Sense): the CO reading on a gauge, status, battery and last report, with **Hold to test** (a 1.5-second press, as it sounds the real alarm) and Mute. The card turns red with a warning when CO is found. Picking the CO reading sensor fills in the alarm's other entities.

### `cover-card` (Blind Card)

A blind, curtain or other cover: Open / Stop / Close tiles and a position bar when the cover reports one. A cover that only assumes its state (e.g. an RF blind) shows the last command instead.

All five have `demo: true` for the Design Presets page.

**Pop-ups** open only where Home Assistant's pop-up adds controls the card lacks: the thermostat gauge, the fan gauge, and a blind that can go to a position. Readings (floor rooms, PM2.5, filters, carbon monoxide) don't open one.

The **climate card** graph also shows the comfortable range and 40–60% humidity lines: pick a room type (`room_type`) or set `comfort_low`/`comfort_high`, `humidity_low`/`humidity_high`; `show_limits: false` hides them.

**Graphs** (climate, climate zone and air purifier cards): press and hold, or hover with a mouse, to read a point in time (a line, dots and a label with the time and the real readings; drag to move). Jumpy sensors are smoothed (15-minute averages, then a gentle moving average, drawn as a curve); each editor has a *Smooth the graphs* switch.

### `device-health-card`

Lists every device Church Drive watches, with whether it's responding, when it was last heard from and how often it usually reports. Stale devices show why, their last real reading and the fixes tried, with a **Fix now** button (fixes also run automatically).

**Device health** (in the integration): choose devices in Settings → Devices & services → Church Drive → Configure → Device health. Church Drive remembers each device's last real reading and spots when its state has gone stale:
- **old readings**: a counter such as `runtime` went backwards (e.g. the Philips fan after a restart);
- **stopped updating**: a device with a counter says it's on but hasn't updated for 3× its usual gap;
- **unavailable** for more than 5 minutes.

It then fixes it automatically: a refresh, then re-sending the last real state (fans and thermostats). If a fan still doesn't respond, it nudges it (a few seconds in its quietest other mode, then back) after 1.5, 10 and 30 minutes. It never reloads an integration on its own, because reloading the Philips integration gets it stuck. `sensor.church_drive_device_health` counts devices needing attention; the fan, air purifier, carbon monoxide, blind and climate cards show a "Not responding" banner on their own; `church_drive.health_fix` runs a fix on demand.

### `octopus-card`

Octopus Energy at a glance, from the Octopus Energy integration. It finds your meters by itself. `show` picks one of four views:
- `electricity`: the rate this half-hour, marked Cheap or Peak, and live use from a Home Mini. It also shows today's running cost and a 24-hour strip of today's rates with the cheap window and how long until it starts.
- `last_day`: the latest complete day Octopus has sent. That's the cost of electricity and gas, half-hour use coloured by rate, and how much ran at the cheap rate.
- `gas`: the rate, standing charge and today so far.
- `octoplus`: points, weekend happy hours and saving sessions.

### `ev-charger-card`

A myenergi Zappi car charger: whether it's charging, the power, this charge's energy (and roughly what it's costing at the Octopus rate), and Stop / Eco / Eco+ / Fast buttons. It finds the Zappi by itself once the myenergi integration is set up, and until then says it isn't connected yet.

### `house-tasks-card`

The jobs the house has spotted, such as a low battery, a filter due, a device not responding or a vacuum message. It reads them from the automatic to-do list (`entity`, default `todo.priorities_automatic`). There's nothing to tick: each job goes by itself once the device reports it's done.

Items carry the description "Automatic · <kind> · <detail> · for <names>", where the names are "Everyone" or one or more first names ("Jamie, Hayley"); an automation keeps the list up to date. `show: mine` (the default) lists the jobs that name the signed-in person (tagged "You, Hayley" when shared) and everyone's. `show: all` lists every job with a name tag. `title` is optional, `color` defaults to purple (#ab47bc), and `demo: true` shows pretend jobs for Design Presets.

### `task-list-card`

A to-do list (`entity`, any to-do list) you can tick off, add to and change, where tasks can repeat and remind people. Tap a task, or "Add a task", to set:
- its name and notes;
- **Repeats**: Never (with an optional due date and time), Daily (every N days), Weekly (every week, fortnightly, or every 3 or 4 weeks, on chosen days with a time for each), Monthly (every N months on a day of the month or the last day), Yearly (on a date), or After it's done (N days, weeks or months after it's ticked off). Repeats can start on a chosen date;
- **Reminds**: anyone with a person entity, Everyone, or No one (`remind_default` sets it for new tasks).
- **The signed-in person's own list** (`entity: mine`): each person sees their own "Priorities <first name>" list, so one card serves everyone.
  For a personal list, set **Who tasks are for** (`assign: me`) to *Just the signed-in person*: the people
  chips become a simple **Remind me** Yes / No (`remind_me` sets it for new tasks).

The repeat and who it reminds are kept as plain words in the task's description, e.g. `Every 2 weeks: Mon 09:00 · for Hayley · use the blue mop` (see `src/repeat.js`). The "Church Drive: repeating tasks" automation reads the same words. It reminds people when a task comes due, and when a repeating task is ticked off it's due again at its next time. Ticked-off one-off tasks sit under "Show done", with "Clear done tasks". `icons: true` gives each task an icon from its name (hoover, bathroom and so on). `demo: true` shows pretend tasks.

### `notifications-card`

Who gets each kind of notification and house job (a table: kinds down the side, people
across, plus **All** for everyone including people added later), and which of each
person's phones they go to. People come from Home Assistant (Settings > People) and their
phones from the companion app, so a new person appears by themselves. Kinds whose devices
aren't set up yet show as waiting. Only administrators can change it. Options: `title`,
`show` (`all`, `notifications`, `jobs`, `phones`), `color`, `demo`.

When **All** is ticked, each person's box shows a grey tick (they get it through All);
tapping one unticks that person and All, leaving the others ticked. Turning All off unticks
everyone, and ticking the last unticked person turns All on.

Automations send with **`church_drive.notify`** (`kind`, `title`, `message`, optional
`admin_message`, `people`, `tag`, `link`, `image`, `critical`). `sensor.church_drive_people`
lists the people and who's assigned to each kind (`assign`), for templates.

### `media-card`

TVs and speakers, a row each (playing first) with play/pause or power; tap one for our
pop-up remote: play/pause and skip, volume, source, a direction pad and power. The pad
finds its own way to each TV: a `remote.*` on the same device (Google TV) or an LG's
`webostv.button`. Options: `title`, `entities` (empty for all), `color`, `demo`.

### `system-card` (Devices & services)

Internet (everyone), and for admins remote access, backups, updates and how long Home
Assistant has been running. Finds the eero, Home Assistant Cloud, backup and uptime
sensors by itself; each can be chosen instead (`internet`, `remote`, `backup_last`,
`backup_next`, `uptime`).

### `safety-card`

Every smoke, heat and CO alarm (found by device class, or `entities`): all clear or ALARM,
battery, last check-in and the CO reading, with any alarm going off at the top in red.

### `people-card` (Who's home)

Everyone in Home Assistant's people, home / away / unknown (or their zone), and their
companion-app phones' batteries. New people appear by themselves.

### `camera-card`

One camera, one card each (several sit side by side in a panel). The tile shows the
newer of ring-mqtt's snapshot and the Ring integration's recording frame, with why and
how long ago it was taken ("Doorbell · 4 min"). An orange badge means the picture is
older than `refresh_after` (default 60 min). While someone's looking, the card then
presses the camera's Take Snapshot button, at most once per `refresh_after` for everyone,
so battery cameras last. A battery icon shows below 25%. Tap for a pop-up with live
video, Events (the events viewer), Snapshot and the light in the camera's area (or
`light`). With `talk_stream` (a go2rtc stream with two-way audio) it also has
Talk. The snapshot, button, battery and events are found from the camera's name
(`camera.front_door_live_view` → `camera.front_door_snapshot`, and so on).

### Camera events viewer

Tap **Events** in a camera's pop-up, or tap a zone card, to see that camera's
doorbell presses and motion from the last 5 days, as pictures by day. Tap one to play
its recording, or **Live** for the camera now. The Church Drive integration saves a
picture of every event and downloads Ring's recording when it's ready (Ring Protect).
These go in `/media/church_drive/events/<camera>/`, are kept 5 days and capped at
3 GB (the oldest go first). They're not in backups. The time kept, the space and the
folder (e.g. a NAS share) are set over `church_drive/camera/settings`.

### `camera-links-card` (Manager's Camera links)

Which cameras record when something happens, like Ring's Linked Devices but for any
motion sensor, door or doorbell, and set separately for **Disarmed**, **Home** (and
night) and **Away** (and while the alarm's going off). The current mode is marked "now".
Rows are triggers (each camera's motion and doorbell, plus any you add); tick the
cameras that should record, and for how long. The integration turns on ring-mqtt's live
stream for those cameras (Ring saves it as a recording) and they show in the events
viewer as "Linked · from …". Each camera records a linked clip at most once every 2
minutes (changeable). It starts with what Ring did: Front Door motion and the doorbell
also record the Driveway for 30 s, in every mode.

### `zone-map-card` (Manager's Locations) and satellite maps everywhere

Home Assistant's zones on a satellite map (Esri imagery with road and place names), or
a street map. Search an address, postcode or place (OpenStreetMap), **+ Add zone** then
tap the map, or tap a zone to rename it, drag its centre to move it, drag the white dot
on its edge to resize it, and **Save** or **Delete**. It changes Home Assistant's own
zones (Home included). The **Satellite / Street** buttons also set every other map in
Home Assistant on that device: the Zones page, map cards and person maps all use the
same satellite or street map instead of Home Assistant's own (`cd-map-style` in the
browser; `ha` would put Home Assistant's back).

**Google maps (optional):** in Church Drive's settings (**Configure › Google maps**), a
Google **Map Tiles API** key switches these maps to Google's satellite (with shop, road
and place names) and road maps; lock that key to Home Assistant's web addresses. Home
Assistant fetches the tiles (giving its own address), so the key never reaches a browser,
and Home Assistant's own maps (MapLibre or Leaflet) get them too. A Google **Places API (New)** key makes the search find shops and
businesses; Home Assistant does the searching, so that key never reaches a browser.
Without keys (or if Google refuses one) it uses Esri and OpenStreetMap. A search drops a
pin; tap the pin to start a zone there, named after the place.

### `places-card` (Manager's Locations)

A card for each person: Home (automatic), then the places they go. Each place is a
Home Assistant zone with their own name for it (Work, Gym, anything). **Edit** shows the
zone and name boxes, ✕ to delete one and **+ Add a place**; nothing changes until
**Save** (or **Cancel**). Zones are made and named in Settings › Areas, labels & zones;
adding or moving one asks everyone's phones for their location, so it applies straight
away. Who's home
then says "At work · Ashfield School", and `sensor.church_drive_people` gives each
person's `place` and `zone` for templates.

### `security-zone-card`

One outside or entry zone (Front Garden, Entrance, Driveway…), one card each. It has:
- **Title:** the zone name with its state: "Motion just now", "Closed", "Quiet", "Open 4 min".
- **Activity strip:** the last 6, 12 (default) or 24 hours, as bars (busy periods, the default) or ticks. Motion is indigo, door open amber, doorbell pink and tamper red, with a faint yellow band while the zone's light was on. Press and hold (or hover) and drag to read a moment in the title line.
- **Last events:** e.g. "Closed since 20:25 · motion 20:25 · rang yesterday 17:34".
- **Batteries:** each on its own row with its name (e.g. Doorbell, Hue sensor) and a level bar. Below 25% turns amber.
- **Light tile:** optional, for the zone's light.

Colours: indigo normally, amber while the door is open, red with a warning line when a tamper sensor trips or a door opens while the alarm (optional) is set. Motion can come from motion sensors (on/off) and camera motion events. `demo: true` with `demo_state` (quiet / motion / open / tamper) for Design Presets.

### `nav-bar-card`

A floating capsule pinned to the bottom of the screen, with every page of a dashboard one tap away. Each page has a name, an icon, a page path and a colour. The colour and the icon can come live from templates (`color_template`, `icon_template`), for example following the alarm: a shield that's off when disarmed and locked when armed away. The current page shows as a filled capsule with its name, or just a filled circle with `icons_only: true`. A page marked "Only admins see this page" (`admin_only`) is hidden from everyone else. When there are too many pages for the width (six on most phones), the bar tightens up and the current page shows as its coloured capsule without the name. A small dot marks a page whose "needs attention" template gives something other than 0, off or empty. Put the same card on every page; it takes no room in the layout. Tapping the current page scrolls back to the top, and so does the round button to the right of the bar, whose three strokes fold from a dash (at the top of the page) into an ↑ once you scroll down (`back_to_top: false` hides it). While the bar is on screen it hides the dashboard's own tabs at the top (`hide_tabs: false` keeps them); the menu, search and edit buttons stay, and the tabs come back in edit mode. `demo: true` shows it in place for Design Presets.

### `section-panel-card`

A group of cards on a faint panel in the section's colour, headed by a large title with a coloured icon and an optional live summary on the right (any Home Assistant template, e.g. `1 room on`). Several panels can share one dashboard section, so they stack without gaps. `color_template` (optional) takes a template that gives a colour, so a panel can follow a state, e.g. the alarm. The editor has the title, icon, colour and summary, then Home Assistant's own card list for the cards inside.

```yaml
type: custom:section-panel-card
title: Lights
icon: mdi:lightbulb-group
color: amber
summary: "{{ states('light.kitchen') | title }}"
cards:
  - type: custom:light-control-card
    mode: room
    area: kitchen
```

**Open or compact** (section panels): the ⌄ on a panel's title switches it between open and **compact**. In compact, every Church Drive card inside shrinks to one row with its key reading and main buttons: a thermostat's temperature with its quick settings, the fan's Off/1/2/3, the purifier's PM2.5 with Off/Auto/Sleep, a blind's Up/Stop/Down, the alarm's state with its arm buttons, a light room's brightness with on/off, a floor's rooms as coloured chips, and a zone's state with its light. Other cards wait until the panel opens. Tapping a compact card's name opens the panel. Phones (under 600px wide) start as `phone_start` (default `compact`), and bigger screens as `tablet_start` (default `open`). Each device then remembers what each signed-in person last chose. `open_when` is a template: while it's true the panel opens by itself (e.g. a door open or CO found), then it goes back to how you left it. `collapsible: false` removes the ⌄.

**Layout on wider screens** (section panels): a panel's cards sit side by side whenever each can be at least `card_width` wide (default 300px; `0` keeps one per row), so cameras fill a tablet and stack on a phone. When sections sit side by side, their panels line up row by row: the first panels share a height, then the second, and so on, and the last panel in each section fills to a common bottom (`match_height: false` turns this off; never on phones).

### `auto-layout-card`

Holds a page's panels in one list and arranges them itself, so adding a panel needs no rearranging. It uses as many columns as fit (`column_width`, default 340px; `max_columns`, default 3), splits the panels into columns of about equal height while keeping their order (down the first column, then the next), and stretches each column's last open panel so the columns end level. Full-width panels sit across the page below the columns: a panel goes full width by itself when it holds 3 or more small cards (zones, cameras, tiles) that won't fit side by side in one column, or set `full_width: yes` / `no` on the panel. A panel with no `card_width` picks one from its cards (zones 200px, cameras 220px, others 300px). Panels with buttons and sliders (lights, alarm, thermostats, fan, purifier, blinds, tiles with controls) go above ones that only show information, like cameras and room temperatures (`controls_first: false` turns this off; a panel's `priority: controls` or `priority: info` overrides). Phones (under 600px) get one column in that order. On phones, pages get **jump-to chips** in a capsule that floats under the header, one per panel in its colour: tapping one brings that panel up to just under the chips (opening it if it's compact, for that visit only), and the chip for the panel you're looking at is filled in while that panel is open (`jump_chips: auto | always | never`). The chips stretch to fill the capsule, which runs the full width of the page. For panels near the end of the page, a chip adds blank space at the bottom so they can still reach the top; it goes again once you scroll back up. Jumping to another chip closes the panel the last jump opened (unless you've changed it). There's always enough room at the end of the page for the last panel to scroll up under the chips too, just as its chip would put it. The chips capsule, like the nav bar and its back-to-top circle, has a frosted acrylic finish with no outline. Once the page has settled, panels keep their columns: opening or closing one re-levels the bottoms (smoothly) but never moves panels between columns. It rebalances when the page loads and when a tablet turns. `title` shows the page's name large at the top, above the chips (`{user}` is replaced with the signed-in person's first name, e.g. `Hello {user}`). The header is the same height on every page: a title line and a three-row widget (blank rows when there's less to show). With `priorities: true` the widget also lists the signed-in person's to-dos from the to-do list named "Priorities <first name>" (e.g. `todo.priorities_jamie`), then the shared "Priorities Everyone" list (`todo.priorities_everyone`) if there is one, overdue and due-soonest first, each with a ✓ (ticking a shared one clears it for everyone). It also shows the person's jobs and everyone's from "Priorities Automatic" (see `house-tasks-card`) with no ✓ and an icon for their kind, since they clear when the device reports it's sorted. What the widget shows is `header_content`, picked in the visual editor:
- `priorities` (the same as `priorities: true`): the to-dos above.
- `todo_summary`: three lines about the signed-in person's lists: what's due today, what's overdue, and the house's jobs for them.
- `lines`: up to three live lines (`header_lines`). Each has a `text` template (`<b>…</b>` makes part bold; empty text hides the line), an `icon`, a `color`, an optional `alert_when` template with `alert_color` (default amber) and `alert_icon`, and `panel`: a panel title on the page, or a page path, to open when tapped. They're snapshots only and never make tasks or send notifications.
- `forecast`: a weather forecast for `forecast_entity`, either `forecast_type: hourly` (every other hour, with rain) or `daily` (the week, with highs and lows). `forecast_panel` is what tapping it opens.
- `list`: one to-do list (`header_list`, e.g. the cleaning schedule), soonest due first, each with when it's due and a ✓. Overdue items are red. `list_color` and `list_icon` style it, and tapping a row (or the To-do button) opens `priorities_page`.

A page address ending in `#<panel title>`, e.g. `/dashboard-mobile/todo#cleaning`, jumps to that panel when the page opens.

On the Home to-dos, a repeating task only shows when it's due in the next two days.

The widget is at most `widget_width` wide (default 520px) and is centred; `priorities_page` adds a To-do › button down its right side (with "+N" when there are more). Any panel that has opened by itself (its `open_when` is true, e.g. a door open) shows under it as an alert pill with its live summary, and tapping one goes to that panel. Put it alone in a section that spans the whole page.

```yaml
type: custom:auto-layout-card
cards:
  - type: custom:section-panel-card
    title: Heating
    cards: [...]
  - type: custom:section-panel-card
    title: Doors & Motion
    full_width: true
    cards: [...]
```

### `section-title-card`

Both the panel and the title take an optional `link` (a page). On a panel, it becomes a full-width **Go to …** button at the bottom while the panel is open (`link_label` changes the name), so tapping the title only opens and closes the panel. On a title on its own (or a panel with `collapsible: false`), tapping the title row, which shows a ›, opens the page. Quick Actions uses this to jump to each full page.

The open/compact toggle is a ring in the panel's colour with a minus that turns into a plus as the panel closes; the whole title row toggles too, and the panel slides between its heights.


Just the panel's heading (title, coloured icon, live summary), for use on its own.

## Development

`npm test` runs the unit tests for the repeat rules (`src/repeat.js`) in UK time.
`node tools/editors-smoke.mjs` (after `npm run build`, with Playwright) checks every card's visual editor opens. `ha/repeating-tasks.jinja` is the Home Assistant side of the repeat rules, and `tools/repeat-cases.mjs` gives cases to cross-check it.

```bash
npm install
npm run build      # writes custom_components/church_drive/frontend/church-drive-cards.js and church-drive-cards-beta.js
```

Always commit both rebuilt bundles with any `src/` change. The build also copies the `package.json` version into `custom_components/church_drive/manifest.json`. The **Build check** GitHub Action fails a PR if any of that doesn't match a fresh build.

New or changed cards are tested on the **Design Presets** dashboard in Home Assistant before being used anywhere else.

### Beta testing

`church-drive-cards-beta.js` is the same code with every card renamed to `…-beta` (e.g. `custom:light-control-card-beta`, shown as "(beta)" in the card picker). It can load alongside the released cards without clashing.

- Home Assistant loads it as a separate dashboard resource from jsDelivr, pinned to one commit:
  `https://cdn.jsdelivr.net/gh/J45PER/church-drive-cards@<commit>/church-drive-cards-beta.js`
- The **Design Presets** dashboard has a **Beta** tab with `-beta` copies of the example cards. Nothing else uses beta cards.

To test a change before release: push the branch, point the beta resource's URL at the new commit, hard-refresh, and try it on the Beta tab. When it works, bump the version and merge as below.

### Releasing

1. Bump `version` in `package.json`, run `npm install --package-lock-only` and `npm run build` in the PR.
2. Merge to `main`. The **Release** GitHub Action sees an unreleased version, checks that the bundle is up to date, and publishes release `vX.Y.Z` (with the cards bundle attached for reference). Merges that don't change the version don't produce a release.
3. HACS lists the release as an update in Home Assistant (Settings → Updates).

## Installing in Home Assistant

Via HACS: add this repository as a custom repository (category: **Integration**), download "Church Drive", restart Home Assistant, then add it under Settings → Devices & services → Add integration → Church Drive. There's nothing to fill in.

The integration serves the cards at `/church_drive/church-drive-cards.js`, adds them to every page itself and keeps a matching dashboard resource so they load even while HA is starting. Don't add another resource for them.

Or manually: copy `custom_components/church_drive` into `/config/custom_components/`, restart, and add the integration the same way.

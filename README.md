# Church Drive

A Home Assistant integration for the Church Drive house. Today it delivers the house's custom Lovelace cards: it serves the cards bundle and loads it on every dashboard, so there's no Lovelace resource to manage. It also keeps a library of **universal scenes** (Bright, Relax, Rest, Nightlight so far) and syncs them to the Hue rooms and zones chosen in its options (Settings → Devices & services → Church Drive → Configure). It uses Home Assistant's existing Hue connection, so there's no bridge pairing. For each chosen room, a scene the room doesn't have is created on the bridge (tagged as a Church Drive scene), and a Hue scene with the same name that's already there is left untouched and used as-is. The sync runs at startup, when the options change, and on the `church_drive.sync_scenes` action.

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
Status header, live entry/exit-delay countdown, icon-only arm/disarm buttons (only the modes the entity actually supports), whole-card colour wash on hover.

```yaml
type: custom:alarm-panel-card
entity: alarm_control_panel.church_drive_alarm
```

### `light-control-card`
Mode-aware light control. Has a real visual editor (Add Card → search "Light Control"). Rows are a combined toggle + brightness drag control, tinted (never literally painted) with the light's live colour so white/bright lights stay readable. Scene chips auto-detect by area. Tapping a light opens Home Assistant's native more-info dialog for full colour/effects control.

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

**Scenes** show as square tiles under the lights, always four per row. Names sit on one line (cut short with … if needed) and by default hide on tiles under about 100px wide, leaving just the icon; the editor's *Scene names* option can set them to always or never show, up to `max_scenes` (default 8, two full rows; `0` hides them), in the same order as the Hue app. They're picked automatically from the card's Hue room and any Hue zone made only of its lights, or you can list your own in the editor with an optional name, icon and uploaded picture per scene. Without a picture, a tile gets a gradient in that scene's colours. The most recently activated scene glows in its own colour while its lights are on, and the other tiles are dimmed. Animated (dynamic) Hue scenes start animating when tapped and show a pulsing ▶ while they're playing; tap a playing scene again to stop the animation and hold its colours (it then shows ⏸, and another tap plays it again). **Press and hold** any scene tile to turn off all of the card's lights. Icons follow whatever is set in Home Assistant, including custom icon packs such as `phu:` Hue icons.

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

### `scene-styles-card`
The central place to style scene tiles. It sits on the Design Presets dashboard's **Scene styles** tab and previews every scene name in the house with its current look. In its visual editor, each entry picks a scene **name** and sets an icon, up to three background colours, or a picture. That style then applies to that scene in every room, on every Light Control card, on every dashboard. A light card's own per-scene overrides still win, and unstyled scenes use built-in colours matched to the Hue scene names.

Light cards read the styles from the `design-presets` dashboard once per page load, so reload other dashboards to pick up changes.

## Development

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

The integration serves the cards at `/church_drive/church-drive-cards.js` and adds them to every page itself. Don't also add a Lovelace resource for them, or they would load twice.

Or manually: copy `custom_components/church_drive` into `/config/custom_components/`, restart, and add the integration the same way.

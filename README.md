# Church Drive Cards

Custom Lovelace cards for the Church Drive Home Assistant dashboards. One bundle, three cards, all styled to match: no border, drop shadow, rounded top/bottom corners only, hover tints on interactive elements.

## Cards

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

Tap a row to toggle, drag across it to set brightness, and tap the tune icon for the native more-info dialog. Icons follow whatever is set in Home Assistant, including custom icon packs such as `phu:` Hue icons.

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

## Development

```bash
npm install
npm run build      # outputs church-drive-cards.js at the repo root
```

Always commit the rebuilt `church-drive-cards.js` with any `src/` change. The **Build check** GitHub Action fails a PR if the committed bundle doesn't match a fresh build.

New or changed cards are tested on the **Design Presets** dashboard in Home Assistant before being used anywhere else.

### Releasing

1. Bump `version` in `package.json` (and run `npm install --package-lock-only`), then merge it to `main`.
2. Tag the merge commit and push the tag: `git tag v0.3.0 && git push origin v0.3.0`.
3. The **Release** GitHub Action checks that the tag matches `package.json` and that the bundle is up to date, then publishes a GitHub release with `church-drive-cards.js` attached.
4. HACS lists the release as an update in Home Assistant (Settings → Updates).

## Installing in Home Assistant

Via HACS: add this repository as a custom repository (category: Dashboard), install "Church Drive Cards", then add the resource if HACS doesn't do it automatically:

```yaml
url: /hacsfiles/church-drive-cards/church-drive-cards.js
type: module
```

Or manually: build, copy `church-drive-cards.js` to `/config/www/`, then register `/local/church-drive-cards.js` (type: module) as a Lovelace resource (Settings → Dashboards → Resources).

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
Mode-aware light control — single light, light group, or a whole room (auto-discovers that area's lights via the entity/device registry). Has a real visual editor (Add Card → search "Light Control"). Rows are a combined toggle + brightness drag control, tinted (never literally painted) with the light's live colour so white/bright lights stay readable. Scene chips auto-detect by area. Tapping a light opens Home Assistant's native more-info dialog for full colour/effects control.

```yaml
type: custom:light-control-card
mode: room
area: living_room
```

## Development

```bash
npm install
npm run build      # outputs dist/church-drive-cards.js
```

## Installing in Home Assistant

Via HACS: add this repository as a custom repository (category: Dashboard), install "Church Drive Cards", then add the resource if HACS doesn't do it automatically:

```yaml
url: /hacsfiles/church-drive-cards/church-drive-cards.js
type: module
```

Or manually: build, then register `dist/church-drive-cards.js` as a Lovelace resource (Settings → Dashboards → Resources).

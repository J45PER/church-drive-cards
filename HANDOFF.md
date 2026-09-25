# Church Drive: Handoff

*Last updated 2026-09-24. Current release: **v0.9.0**.*

## Where this stands

The **Church Drive** Home Assistant integration (`custom_components/church_drive`),
installed through **HACS** from this public repo
(`github.com/J45PER/church-drive-cards`). Since v0.7.0 its only job is to deliver the
custom Lovelace cards: it serves the bundle
(`custom_components/church_drive/frontend/church-drive-cards.js`) and adds it to
every frontend page with `add_extra_js_url`. There is no Lovelace resource for the
released cards. Since v0.8.0 it also syncs **universal scenes** to the Hue bridge (step 2 of the
plan below).

### Universal scenes

- `library.py` defines each scene once (name, mirek or xy, brightness).
- `hue.py` reuses HA's Hue connection (`hue` config entry `runtime_data.api`, an
  aiohue v2 bridge), so there's no link button. For each room/zone chosen in the
  options (`scene_groups`, bridge group ids) and each library scene:
  - a scene tagged `metadata.appdata = "cd:<key>"` is ours: brought in line;
  - an untagged same-name scene (made by the Hue app) is left alone and used;
  - otherwise a new tagged scene is created.
- It runs at startup, on options change (entry reload) and via the
  `church_drive.sync_scenes` action (returns a per-room summary). Diagnostics
  dump the chosen rooms' scenes with their light settings.
- Since v0.9.0 the cards use the library directly: the integration serves it
  over the websocket (`church_drive/library`: key, name, brightness,
  `color_temp_kelvin` or `xy_color`) and `src/universal-scenes.js` loads it once
  per page. A card scene `entity: universal:<key>` (or YAML `scene: Bright`) is
  applied with one `light.turn_on` on the card's Hue room group (else its
  zones/groups, else its lights) and shows as selected while every lit light
  matches it (brightness within 4/255, kelvin within 3%, xy within 0.02; lights
  that can't show the colour only need to be on). The editor's Scenes list puts
  universal scenes first, once per place (the room and each of its zones,
  e.g. "Bright · Kitchen Spotlights" = `universal:bright@light.kitchen_spotlights`,
  which sets only that zone and is matched against only its lights; the tile
  reads "Bright · Spotlights"), then the Hue scenes, each labelled with its
  room/zone; Hue scenes named like a universal one are hidden. There's also a
  `church_drive.apply_scene` action (lights + scene name).
- Why not sync every room to the bridge: it already has ~123 scenes and the full
  library everywhere would add ~100, past the bridge's ~200 limit. Bridge
  scenes stay for animated/colour scenes (step 4).
- Plan: step 2 = Kitchen test (done in v0.8.0, Rest created, the Hue app's
  Bright/Relax/Nightlight linked). Step 3 (v0.9.0) = library applied by the
  cards in every room (active-scene entity still to do). Step 4 = gradients,
  dynamic palettes and a scene builder. Everything below is merged to
`main`, released and running live. The user has checked each change on a real
device.

| Card | Source | What it is |
|---|---|---|
| `battery-zone-card` / `gauge-zone-card` | `src/gauge-zone-card.js` | Zone card of gradient-filled value rows, worst-first, red/orange/green thresholds |
| `alarm-panel-card` | `src/alarm-panel-card.js` | Alarm status, live entry/exit countdown, icon-only arm/disarm buttons |
| `light-control-card` | `src/light-control-card.js` | Single light / group / room control with scene tiles |

Shared modules:
- `src/form-editor.js`: the `ha-form` visual-editor base used by every card.
- `src/scene-style.js`: scene tile gradients and icons.
- `src/suffix.js`: holds the `-beta` name suffix for the beta build.

`README.md` has the user-facing docs and YAML examples.

## How the user works (conventions)

- **Test on Design Presets first.** The `design-presets` dashboard is the test bench.
  New or changed cards go on its **Beta** tab (see below) before anything is released.
- **Everything is set up from the UI.** Every card and option must be configurable
  in the visual editor, with nothing YAML-only. New options need an editor field.
- **Not wanted:**
  - Don't put light room cards on the real dashboards (Mobile, Hayley, Living Room
    Panel).
  - Don't strip room names from scene labels in general. The light card shows the
    Hue scene `name` attribute, which is fine.
- **Non-Hue lights** are deferred until the user actually owns one. Don't build for
  them speculatively.
- The user prefers to be shown visual options side by side (rendered mock-ups) when a
  style choice is open, rather than having one picked for them.

## Release and deployment pipeline

- **HACS:**
  - Installed as a custom repository, category **Integration** (HACS id
    `1385560733`). Before v0.7.0 it was category Dashboard with the resource
    `/hacsfiles/church-drive-cards/church-drive-cards.js`; that resource was removed
    in the switchover.
  - The integration has one config entry (no options). `__init__.py` registers the
    static path `/church_drive` → `frontend/` (no cache headers) once per HA run and
    adds `/church_drive/church-drive-cards.js?v=<version>` as an extra module URL.
    The version query makes browsers fetch the new bundle after an update.
  - Switchover notes (2026-09-25): HACS keeps a custom repo's category even after
    `ha_manage_hacs action=remove` (that only deletes the download). To change
    category, also drop the registration with
    `ha_call_service(ws_command="hacs/repositories/remove", data={"repository": "1385560733"})`,
    then `add_repository` again. The config entry was added with
    `ha_set_integration(domain="church_drive")` (entry `01M3C9Z12M0W755GDTM455NFVB`).
  - Rollback: remove the config entry and the HACS download, then point a module
    resource at the bundle on jsDelivr
    (`…/church-drive-cards@<commit>/custom_components/church_drive/frontend/church-drive-cards.js`).
  - An integration update needs an HA restart to take effect (HACS raises a repair
    for it). Do a `ha_restart` after downloading.
- **Build:**
  - `npm run build` (`build.mjs`, esbuild) writes
    `custom_components/church_drive/frontend/church-drive-cards.js` and
    `church-drive-cards-beta.js`, and copies the `package.json` version into
    `manifest.json`. Commit all of it.
  - **Build check** (`.github/workflows/build-check.yml`) fails a PR if either
    committed bundle is stale.
- **Release** (`.github/workflows/release.yml`):
  - Runs on every push to `main`.
  - If `package.json`'s version has no GitHub release yet, it checks the bundles and
    runs `gh release create vX.Y.Z`, which creates the tag (the bundle is attached
    for reference; HACS installs the integration folder from the tag).
  - To release: bump `version` (plus `npm install --package-lock-only` and
    `npm run build`) in the PR, then merge.
  - It's version-driven rather than tag-driven because the Claude cloud session can
    push branches but not tags.
- **After a release:**
  - HACS only re-checks custom repos about every 48h, so run
    `ha_manage_hacs action=update_information`, then `action=download` with
    `version=vX.Y.Z`, then `ha_restart`.
  - Once, GitHub returned HTTP 500 to HA for a fresh release asset. It downloaded fine
    a couple of minutes later; wait and retry.
- **Rollback:** reinstall an older release from HACS (Redownload → pick a version).
- **Beta channel:**
  - `church-drive-cards-beta.js` is the same code with every card and editor
    registered as `<name>-beta`, shown as "(beta)" in the card picker. It loads
    alongside the release without clashing.
  - HA loads it as a separate module resource (id `436186c683fe4c7d81c865b67bb0e109`)
    from `https://cdn.jsdelivr.net/gh/J45PER/church-drive-cards@<commit>/church-drive-cards-beta.js`.
    It's currently pinned to `f3d476d` (v0.6.1 content; the cards code is unchanged in v0.7.0).
  - Design Presets has a **Beta** tab (`/design-presets/beta`) with `-beta` copies of
    all the example cards (light cards in demo mode). Nothing else uses beta cards.
  - To test a branch: push it, repoint that resource's URL at the branch commit, and
    have the user hard-refresh the Beta tab. Then bump, merge and release.
  - jsDelivr is blocked from the Claude cloud container, but the user's browsers load
    it fine.

## Card features (current)

### Battery / gauge zone card
- **Visual editor:**
  - Title.
  - A collapsible *Colours, units and icons* section: which end is bad, red/orange
    thresholds, unit, full-bar value, icon mode.
  - An editable **Rows** list (HA `object` selector with `fields`): entity, name,
    battery wording, secondary text, icon override, fixed value, date (date picker),
    and unit/max overrides.
- **Icon modes:** `battery` (steps with value), `gauge`, `entity` (registry icon →
  state icon → device-class default via `<ha-state-icon>`), and `custom` (one icon
  picker for every row). A row's own icon always wins.
- **Starting config:** a new battery card starts with the three lowest real `%`
  battery sensors; a new gauge card starts with one example row.
- **Older YAML:** `demo_pct`/`demo_date` are still read. The Design Presets example
  was migrated to `value`/`date`.

### Alarm panel card
- **Visual editor:** alarm entity picker, plus a collapsible demo section (state,
  target state, countdown, by, time via a datetime picker normalised for Safari,
  supported features).
- **Starting config:** a new card starts with the first real alarm entity.

### Light control card
- **Modes:**
  - `light`: one row.
  - `group`: the group row with its member lights indented underneath.
  - `room`: the area title, the area's Hue room/zone groups as top rows, and bulbs
    indented underneath. Area comes from the entity **or** device (Hue sets it on the
    device). Hidden entities and `entity_category` entities are skipped (e.g.
    `light.hayleys_bedroom_air_purifier_display_backlight`).
- **Rows:**
  - A tap toggles; a horizontal drag sets brightness.
  - The tune icon opens HA's more-info dialog without toggling. The row only acts on
    presses that started on it.
  - The row background is the light colour blended at 30% (`color-mix`), so
    white/bright lights stay readable.
  - Icons come from the entity registry first (custom `phu:*` Hue icons, from the
    installed custom-brand-icons).
- **Re-rendering:** rows only rebuild when one of the card's own lights, scenes or
  scene groups changes, never mid-drag.
- **Scene tiles:**
  - Square tiles, up to `max_scenes` (default 8, two full rows; `0` hides them), in Hue app order
    (the group's `hue_scenes`).
  - Auto-detected from the scene's Hue group **device**. This includes Hue zones
    made up only of the card's lights (e.g. *Living Room Ambience*, whose device has
    no area). Duplicate names keep the first.
  - Each tile shows a centred icon at about 40% of the tile width, with no circle, and
    the scene name. The background is an uploaded picture (`image` selector) or a
    gradient in the scene's palette; Hue doesn't give HA scene artwork, so
    `scene-style.js` approximates the standard Hue scenes by name.
  - **Selected:** the most recently activated scene (latest state timestamp) while
    its group is on. It glows in its own colour with a slight lift, and the other
    tiles dim.
  - **Playing / paused:**
    - Playing: the scene has `is_dynamic` and any **lit** bulb in its group reports
      `dynamics: dynamic_palette`. Hue leaves `dynamics` set on bulbs after they're
      off, so off bulbs are ignored. Shows a pulsing plain white ▶.
    - Paused: an animated scene that's selected but not animating. Shows a plain
      white ⏸.
  - **Tap:**
    - Static scene: `scene.turn_on`.
    - Animated scene: `hue.activate_scene` with `dynamic: true`.
    - Playing scene: **pause** by sending each lit bulb of the group `light.turn_on`
      with its current `xy_color`/`color_temp_kelvin` and brightness. Re-activating
      with `dynamic: false` does **not** work, because Hue restarts auto-dynamic
      scenes.
  - **Press and hold (~0.5s):** turns off all of the card's lights (vibrates where
    supported; iPhones can't). A drag is neither a tap nor a hold, and the browser's
    long-press menu is suppressed.
  - **Editor:** *Max scenes*, plus a scene list of objects (scene, name, icon,
    picture). Older plain lists of IDs are upgraded on load.

- **Show list** (`entities`, room and zone/group modes): picks and orders the
  rows, each as an ID or `{entity, name}`.
  - Room choices: the area's groups, Hue zones made only of its bulbs (even with
    no area), and its bulbs.
  - Zone choices: its member lights.
  - The editor offers only valid choices, labelled room/zone/light from `hue_type`.
  - An empty list, or one where nothing exists, shows everything.
  - Each entry can set `level` (0 top, 1 child, 2 grandchild; 16px indent each).
    Defaults: Hue room 0, zones 1 when a room is shown, lights one level under the
    deepest group.
- **Scene tile layout:** always four per row. Names are one line (ellipsis), scale
  9–13px via `cqw`, and hide on tiles under 100px. `scene_names` (`auto` | `always` |
  `never`) overrides that. The badge scales too.
- **Demo mode** (`demo: true`, `demo_room: living_room | bedroom`): `src/demo-home.js`
  simulates a Hue home in the browser (registries, `light.*`, `scene.turn_on`,
  `hue.activate_scene` with cycling palettes). Nothing reaches HA. The tune icon is
  hidden. **Every light card on Design Presets (main and Beta tabs) must use demo
  mode**, because the user doesn't want those pages touching real devices.

- **Row state:** each row shows Off / NN% / On / Unavailable on the right (live
  while dragging). Off rows are dimmed; lit rows are tinted 40% with warm/cool
  mapped whites. Icon overrides work per Show-list entry and via the card `icon`
  for the head row.
- **Icon packs:** `src/icons.js` draws pack icons (`phu:` etc.) itself, as inline SVG
  from `window.customIcons[prefix].getIcon()`, cached, and waits up to 20s for a pack
  that registers late. `<ha-icon>` was unreliable here: it resolves once, and stayed
  blank if the pack wasn't ready. `mdi:` icons still use `<ha-icon>`. An icon missing
  from its pack falls back to `mdi:help-circle-outline`.
- **White lights:** colour-temperature mode (or a very pale colour) uses the kelvin
  warm/cool colour, not the near-white RGB.

### Scene styles card (central scene looks)
- `scene-styles-card` sits on the Design Presets **Scene styles** tab
  (`/design-presets/scene-styles`) and is the single store for scene tile looks.
- Its `styles` list is keyed by scene **name**, so one entry styles that scene in
  every room. Each entry takes an icon, `colour_1..3` (`[r,g,b]`) or an `image`.
- Light cards on any dashboard fetch the `design-presets` config (`lovelace/config`)
  once per page and use it. On the same page, edits apply live via a window event.
  The beta build prefers a `scene-styles-card-beta` if one exists.
- Precedence: a card's own per-scene override > the central style > built-in
  palettes (`src/scene-style.js`, covering every Hue scene name in the home).

### All cards
- **Sizing:**
  - `getCardSize()` reflects the real height, for masonry.
  - `getGridOptions()` is full width (min half) for sections views, where HA's own
    Layout tab resizes.
  - The live dashboards are masonry.
- **Card picker:** a live preview (`preview: true`), a README link, and starting
  configs with real entities.

## Live Home Assistant notes

- **Dashboards using the cards:** Battery Status (`battery-status`), Alarm
  (`alarm-panel`), and Design Presets (`design-presets`, main + Beta tabs). All three
  use the masonry layout.
- **Design Presets:** has notes for each card describing what to check. The light
  examples use real Living Room lights, plus room cards for Hayleys Bedroom and
  Hayley's Landing (two groups each).
- **Living Room Hue setup:**
  - `light.living_room` is the room group (scenes in order Nightlight, Rest, Bright).
  - `light.living_room_ambience` is a zone (TV lightstrip + ceiling) holding the
    animated scenes (Soho, Lake Placid, Toil and trouble…).
  - `light.living_room_table_lights` is a zone with no scenes.

## Open items

- The user may still find some editor options too complex once they've used them
  properly. Ask which ones. Options: hide until needed, move into an "Advanced"
  section, plainer labels.
- The per-scene **picture upload** (`image` selector inside the scene list) hasn't
  been tried on the real dashboard yet. If it misbehaves, fall back to a URL text
  field.
- Non-Hue lighting: deferred (see conventions).

## Dev loop

```bash
npm install
npm run build     # both bundles at repo root
npm run watch     # rebuild both on change
```

Headless checks: Chromium with Playwright is available in the cloud container. Card
behaviour has been tested by loading the built bundle into a page with stub `ha-icon`,
`ha-card` and `ha-form` elements and a mock `hass` built from the real registry.

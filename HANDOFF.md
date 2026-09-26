# Church Drive: Handoff

*Last updated 2026-09-26. Current release: **v0.10.7**.*

## Where this stands

This repo (`github.com/J45PER/church-drive-cards`, public) is the **Church Drive**
Home Assistant integration (`custom_components/church_drive`). It's installed through
HACS as an integration. It does two jobs:

1. **Delivers the custom Lovelace cards.** It serves the bundle and loads it on
   every dashboard. There's no separate card install.
2. **Universal scenes.** It keeps one library of scenes (Bright, Relax, Soho… plus
   the user's own). Any light card can use them in any room or zone without Hue
   scene setup. There's also a scene select per room/zone and a scene builder.

Everything is merged to `main`, released and running live. The user tests on real
devices before each release.

**Tested on real lights with the user (v0.10.5, Kitchen and Hayley's Bedroom):**
- Colour scenes animate; tapping pauses and resumes them (⏸/▶).
- The selected tile glows (others dim) and stays selected after a page refresh;
  the room's `select.<room>_scene` follows.
- Speeds now feel like Hue's (after switching to the bridge's real palettes).
- Gradient strips show several colours on animated scenes.
- Zone-only scenes (e.g. "… · Bedroom Ambience") change only the zone's lights;
  the room's other lights stay off.
- No drop-out between colour scenes: the logbook showed the ~3s off-blips on
  v0.10.3 and none after v0.10.4's two working scenes.
- White scenes (Bright, Relax, Nightlight) select and match.
- Scene builder: the user made a custom colour scene, tried it and saved it.

**New in v0.10.6, still to check on real lights:**
1. **No two lights share a colour.** The user saw a custom two-colour scene put
   the same colour on several lights at once (colours were dealt round, so light
   1 and 3 matched). Now `spread()` in `apply.py` (and `spreadColours()` in
   `src/universal-scenes.js`) gives each light its own colour, blending between
   neighbouring palette colours when there are more lights than colours. The
   bridge palette is padded to at least five colours the same way, like Hue's
   own palettes, so the animation has room to keep lights apart.
2. **Default scenes.** A card with no scenes chosen now shows Bright, Dimmed,
   Relax and Nightlight (universal, applied to the card's room), the same on
   every card. The old auto-pick of the room's Hue scenes is gone.

**On Beta / in the next release:**
- A scene tile is only shown selected while some of its lights are on. After
  the v0.10.7 restart, `select.hayleys_bedroom_scene` came back as
  "Concentrate" with every bedroom light off (the Hue room still reports on
  because the unavailable "My Boy Hugo" lamp is on as far as the bridge knows),
  so the tile stayed lit with the others dimmed.
- Scene selects now check again once HA has fully started
  (`EVENT_HOMEASSISTANT_STARTED`), as the restored scene is checked before the
  Hue lights have loaded.

**New in v0.10.7 (checked by the user on Beta):**
- The editor's Scenes list starts filled with Bright, Dimmed, Relax and
  Nightlight for the card's room (`lccFillDefaultScenes`), so they can be
  reordered or removed. Until they're changed they follow the card if its room
  changes. An emptied list shows no scenes; a card with no `scenes` key at all
  still shows the four.
- List items are labelled "Bright · Kitchen" instead of
  `universal:bright@light.kitchen`, with any name override underneath. HA's
  object list shows a field's raw value (select option labels aren't looked
  up), so `label` is a form-only field: an invisible `constant` selector in the
  item's fields, added by the shared editor's `display` hook and removed by
  `store` before saving.
- A red **Reset** button on the Scenes list's Add row (Reset left, Add right)
  puts the four defaults back for the card's room. ha-form has no buttons, so
  the shared editor takes `buttons` (`{ label, apply, field, variant }`) and
  reaches into the list's shadow DOM (`ha-selector[name=scenes]` →
  `ha-selector-object` → `.items-container`), adding the button and a flex
  style. If HA's markup changes and that isn't found within 2s, the button goes
  under the form instead. The reset sets `scenes: 'reset'`, which
  `lccFillDefaultScenes` swaps for the defaults before saving.

If a colour scene sets fixed colours but doesn't animate, the bridge probably
rejected the palette body in `apply.py`. The integration falls back to per-light
colours and logs a warning ("Couldn't play … on the Hue bridge"). Read it with
`ha_get_logs(source="system", search="church")`.

| Card | Source | What it is |
|---|---|---|
| `battery-zone-card` / `gauge-zone-card` | `src/gauge-zone-card.js` | Zone card of gradient-filled value rows, worst-first, red/orange/green |
| `alarm-panel-card` | `src/alarm-panel-card.js` | Alarm status, live entry/exit countdown, icon-only arm/disarm buttons |
| `light-control-card` | `src/light-control-card.js` | Light / zone / room control with scene tiles |
| `scene-styles-card` | `src/scene-styles-card.js` | Central scene tile looks (icon, colours, picture) |
| `scene-builder-card` | `src/scene-builder-card.js` | Make custom universal scenes |

Shared frontend modules:
- `src/form-editor.js`: the `ha-form` visual-editor base.
- `src/scene-style.js`: tile gradients and icons, plus central styles.
- `src/universal-scenes.js`: loads the scene library and handles matching and
  playing checks.
- `src/icons.js`: icon-pack icons drawn as inline SVG.
- `src/demo-home.js`: the pretend Hue home.
- `src/suffix.js`: the `-beta` suffix.

Integration modules (`custom_components/church_drive/`):
- `__init__.py`: setup, card delivery, services and websocket commands.
- `library.py`: built-in and custom scenes, plus colour conversion.
- `apply.py`: applies a scene to lights, rooms or zones.
- `select.py`: scene select entities.
- `hue.py`: bridge access and the optional white-scene sync.
- `config_flow.py`: the options screen (rooms for the sync).
- `diagnostics.py`.

`README.md` has the user-facing docs.

## How the user works (conventions)

- **Beta first.** New or changed card behaviour goes on the Design Presets **Beta**
  tab. The user checks it, says "release it", and then it ships.
- **Everything from the UI.** Every option needs a visual-editor field. Nothing is
  YAML-only.
- **Design Presets uses pretend lights only.** On the main and Beta tabs, light cards
  are in demo mode, battery/gauge cards use fixed values, and the alarm uses
  `demo: true`. The Scene builder tab is the exception: its "Try it in" uses real
  lights by design.
- **Mock-ups for style choices.** When a look is open, send rendered examples side by
  side (an Artifact page works well) and let the user pick before building.
- **No manual steps for the user.** Claude does HACS, restarts, dashboard edits and
  config entries through the HA MCP tools.
- **Non-Hue lights** are deferred until the user owns one.
- Don't strip room names from scene labels in general.
- Light room cards now replace the old tiles on the real dashboards; the user asked
  for this. An earlier "keep them off the real dashboards" rule no longer applies.

## Release and deployment

- **Build:** `npm run build` (`build.mjs`, esbuild) writes
  `custom_components/church_drive/frontend/church-drive-cards.js` and
  `church-drive-cards-beta.js`, and copies the `package.json` version into
  `manifest.json`. Commit all of it. **Build check** CI fails a PR if any of that is
  stale.
- **Release:**
  1. Bump `version` in `package.json` and `package-lock.json`, run the build, and
     set "Current release" here.
  2. Open a PR and merge once CI is green. The Release workflow creates `vX.Y.Z`,
     because the session can push branches but not tags.
- **Install:**
  1. `ha_manage_hacs(update_information, 1385560733)`, then
     `download version=vX.Y.Z`.
  2. Point the beta resource at the merge commit.
  3. `ha_restart(confirm=True)`. The call returns a Cloudflare 502 because HA goes
     down mid-request, which is expected. Wait about 3 minutes.
  4. Verify: the `ad4dc52d…` resource shows `?v=X.Y.Z`, and system logs for
     "church" are empty.
- **How the cards load:**
  - The integration registers the static path `/church_drive` → `frontend/` (no
    cache headers).
  - It adds `/church_drive/church-drive-cards.js?v=<version>` as an extra module
    URL.
  - Since v0.10.1 it also keeps a Lovelace **resource** with that same URL (id
    `ad4dc52d0b0241f5b082a1c084c3072a`). It's updated at setup and removed with the
    integration.
  - Without the resource, a page opened while HA was starting showed "Configuration
    error" on every card. The URLs are identical, so the browser loads the bundle
    once.
  - Don't add any other resource for the released cards.
- **HACS:** custom repo, category Integration (id `1385560733`).
  - Changing category needs `ha_call_service(ws_command="hacs/repositories/remove",
    data={"repository": "1385560733"})` before `add_repository`, because a plain
    remove keeps the category.
  - The config entry is `01M3C9Z12M0W755GDTM455NFVB` (options: `scene_groups`).
- **Beta channel:**
  - `church-drive-cards-beta.js` registers every card as `<name>-beta`.
  - HA loads it from resource `436186c683fe4c7d81c865b67bb0e109`:
    `https://cdn.jsdelivr.net/gh/J45PER/church-drive-cards@<commit>/church-drive-cards-beta.js`.
    It's pinned to the latest main merge (`fb5679e`).
  - To test a branch: push it, repoint the resource, and ask for a hard refresh.
  - jsDelivr is blocked from the cloud container, but works for the user.
- **Rollback:** download an older release in HACS and restart.
- **Dashboard edits:** use `ha_config_set_dashboard` with `python_transform` plus
  `config_hash`.
  - It needs `BestPracticeKey`. The key rotates hourly; re-read
    `ha_get_skill_guide(skill='home-assistant-best-practices', file='SKILL.md')`
    to get it.
  - The sandbox forbids comprehensions that reference locals, so use loops.
  - HA auto-backs up each dashboard edit.

## Universal scenes

### Library (`library.py`)
- **White:** Bright, Cool bright, Dimmed, Read, Concentrate, Energise, Relax, Rest,
  Nightlight.
  - Values were read from the Kitchen's Hue scenes. Examples: Bright 370 mirek (2703K)
    100%, Dimmed 370/30%, Read 346, Concentrate 233, Energise 156, Cool bright 250,
    Relax 447/56.25%, Nightlight xy (0.561, 0.4042) at minimum.
  - Rest (447/34%) is an estimate, because the Kitchen had no Hue Rest.
- **Colour:** 19 animated palettes read from this home's bridge (Hue diagnostics
  `full_state` → scene `palette.color`, each colour `(x, y, brightness %)`, plus
  Hue's own `speed` 0.60–0.73). The scenes are Soho, Magneto, Ruby glow, Emerald
  isle, Dreamy dusk, Lake Placid, Toil and trouble, Spellbound, Storybook, Arise,
  Shine, Unwind, Pumpkin patch, Phantom, City Blue, Motown, Witching hour and
  Meriete. Aqua is still approximated because it wasn't on the bridge.
  - Before v0.10.5 most palettes were rough hex guesses (3 very different
    colours). They jumped between colours and felt too fast even at Hue's
    speeds. Hue's palettes are 4–5 close colours, which drift gently.
  - Per-colour brightness is used for each light's action and the palette.
    Gradient points use `interpolated_palette`.
  - Tiles for universal colour scenes use these real colours, taking priority
    over the old `scene-style.js` approximations.
- **Custom:** from the Scene Builder, stored in HA storage `church_drive.scenes`.
  - Fields: white takes kelvin + brightness; colour takes up to 9 hex colours,
    brightness, animated (`dynamic`) and `speed`. Both can take an `icon`.
- **Websocket:**
  - `church_drive/library` returns `{scenes: [...frontend form], custom: [...stored]}`.
  - `church_drive/scene/save` and `church_drive/scene/delete` are admin-only.
  - `church_drive/scene/preview` plays an unsaved scene on a target.

### Applying (`apply.py`, `church_drive.apply_scene`)
- **White:** one `light.turn_on` on the target (kelvin/xy + brightness), so nothing
  is stored on the bridge.
- **Colour on a Hue room/zone:**
  1. The target's HA entity maps to a grouped_light, whose owner is the room/zone.
  2. The group has two working scenes ("Church Drive" `cd:live` and "Church Drive
     2" `cd:live2`, created on first use, their HA entities hidden). The one that
     isn't playing (`status.active`) is rewritten, since rewriting the playing
     one made the lights drop out for ~3s (seen in Hayley's Bedroom, v0.10.3):
     - actions: a colour per light, spread along the palette and blended when
       there are more lights than colours, so no two match; up to 5 gradient
       points on gradient lights;
     - a palette (colours + dimming), padded to at least 5 blended colours;
     - the speed.
  3. The scene is recalled with `dynamic_palette` (animated) or `active` (still).
  - This adds at most two bridge scenes per room/zone. They show in the Hue app
    too.
  - Colour scenes use Hue's own speed and brightness per scene (`HUE_TIMING`, read
    from the bridge's scene entities: 0.60–0.73). The first version used 0.5,
    which the user noticed was slower than Hue. Custom scenes default to 0.63.
- **Anything else** (a single light, a non-Hue group, or a bridge error): colours
  are spread round the member lights (same blending) with `light.turn_on`.
- **Why not one bridge scene per room per scene:** the bridge already had ~123
  scenes, and the full library everywhere would pass its ~200 limit.

### Scene selects (`select.py`)
- There's one `select.<room/zone>_scene` per Hue grouped light: 23 entities, e.g.
  `select.kitchen_scene`.
- **State:**
  - **Colour scene:** sticky from when it's applied until the lights are all
    off, a colour-capable light goes into `color_temp` mode, or a Hue scene of
    that group (not "Church Drive…") is recalled after it (`colour_still_on`).
    Colours can't be checked, because lights mid-animation or paused sit between
    palette colours. In v0.10.4 a stray report knocked the select to unknown
    ~45s after tapping, so a page refresh showed the tile unselected.
  - **White scene:** current while the lights match it.
  - **Otherwise:** a matching white scene, else unknown.
  - For 15s after an apply, the new scene is shown regardless, then rechecked.
    Before v0.10.4 the select dropped to unknown when the lights briefly reported
    off during a colour recall, so tiles didn't glow or show play/pause.
  - It's a RestoreEntity: the last scene survives restarts.
  - Colour matching allows xy ±0.06, because Hue clamps colours to each bulb's
    gamut.
- **Attributes:** `target` (group entity) and `scene_key`.
- **Behaviour:** choosing an option applies it. Options are every library name, and
  they update when custom scenes change.

### Optional bridge sync (`hue.py`, options screen)
- Settings → Devices & services → Church Drive → Configure → pick rooms/zones.
  Currently set to Kitchen only.
- **What it does:** creates white library scenes on the bridge (tagged `cd:<key>`).
  Same-name Hue app scenes are left untouched. Kitchen "Rest" was created this way
  (`scene.kitchen_kitchen_rest`).
- **When it runs:** at startup, on options change and via
  `church_drive.sync_scenes`. Its response is a per-room summary.
- **Diagnostics** dump the chosen rooms' scenes and light settings. That's how the
  library values were read.

### Card side
- **Config:** a scene item `entity: universal:<key>` applies to the card's room.
  `universal:<key>@<light entity>` targets one zone/group/light. YAML `scene: Bright`
  also works.
  - Target when there's no `@`: the card's Hue room group, else its zones/groups,
    else its lights.
- **Scene list in the editor:** universal scenes come first, once per place, e.g.
  "Bright · Kitchen", "Bright · Kitchen Spotlights".
  - Then the Hue scenes of the card's room/zones, always labelled with their group.
  - Hue scenes named like a universal one are hidden. Scenes from elsewhere already
    on the card stay, marked "(other room)".
- **Tile names:** a zone-targeted tile reads "Bright · Spotlights" (room name
  stripped).
- **Tap:** a real home calls `church_drive.apply_scene`. The pretend home sets
  lights locally; colour scenes animate via `DemoHome.playPalette`.
- **Selected:** a universal tile is selected when its target's scene select says so,
  or failing that when the lights match:
  - brightness within 4/255;
  - kelvin within 3%;
  - xy within 0.06 of a palette colour;
  - lights that can't show the colour only need to be on.
- **Animated scenes:** they show playing ▶ or paused ⏸. Tapping a playing one
  freezes each lit bulb at its current colour.

## Card features (current)

### Light control card
- **Modes:**
  - `light`: one row.
  - `group` ("Zone or light group"): the group plus its lights.
  - `room`: the area's Hue room/zone groups plus the bulbs.
  - Area comes from the entity or its device. Hidden and `entity_category` entities
    are skipped.
- **Show list** (`entities`): picks and orders the rows. Each row can set a name,
  an icon and a `level` (0/1/2, 16px indent each). Defaults: room at 0, zones at 1
  when a room is shown, lights one level under the deepest group.
- **Rows:**
  - A tap toggles, and a horizontal drag sets brightness.
  - The tune icon opens more-info without toggling.
  - The right-hand state reads Off / NN% / On / Unavailable.
  - Off rows are dimmed. Lit rows are tinted 40% in the light's colour; white
    lights use a warm/cool kelvin colour.
  - Rows are 48px (top level) or 42px (children).
- **Scene tiles:**
  - They're the same height as a top row (48px), with 12px corners. The icon and
    name sit side by side.
  - **Rows:** at most 4 tiles per row, in as few rows as possible, shared out evenly
    with any fuller row last. Each row fills the width: 5 = 2 + 3, 6 = 3 + 3,
    7 = 3 + 4, 9 = 3 + 3 + 3.
  - **Names:** hidden on tiles under 100px wide (`scene_names: auto`). `always` and
    `never` override that.
  - `max_scenes` defaults to 8, and 0 hides tiles.
  - **Default scenes** (no `scenes` key): universal Bright, Dimmed, Relax and
    Nightlight on the card's room, the same on every card
    (`LCC_DEFAULT_SCENES`). The editor fills them into the list; an empty list
    shows no scenes.
  - **Backgrounds:** an uploaded picture, else the central style, else a built-in
    palette, else the scene's own colours (custom scenes), else a colour from a
    name hash.
  - **Selected:** the tile glows in its own colour and the other tiles dim. For Hue
    scenes, the selected one is the most recently activated scene while its group
    is on.
  - **Playing** (pulsing ▶): any lit bulb reports `dynamics: dynamic_palette`.
  - **Paused** (⏸): the selected animated scene isn't animating. Tapping a playing
    tile pauses it by sending each lit bulb its current colour. Hue's
    `dynamic:false` doesn't work for auto-dynamic scenes.
  - **Tapping a Hue scene:** a static one uses `scene.turn_on`; an animated one uses
    `hue.activate_scene` with `dynamic: true`.
  - **Press and hold (~0.5s)** turns off all of the card's lights. A drag cancels
    it.
- **Demo mode** (`demo: true`, `demo_room: living_room | bedroom`): a pretend Hue
  home. Configured scenes apply only when they're universal or exist in the pretend
  home.
- **Icons:**
  - `src/icons.js` draws `phu:` and other pack icons as inline SVG, cached, and
    waits up to 20s for a late pack. A missing icon becomes
    `mdi:help-circle-outline`.
  - `mdi:` icons use `<ha-icon>`.

### Scene styles card
- It's on Design Presets → **Scene styles**, and is keyed by scene name.
- Each style sets an icon, `colour_1..3` or an `image`.
- Light cards everywhere load it once per page from the `design-presets` config.
- Precedence: a card's own override > the central style > built-in.

### Scene builder card
- It's on Design Presets → **Scene builder**. It lists custom scenes (edit, delete)
  and has a New scene form:
  - name;
  - White (kelvin) or Colours (up to 9 pickers; right-click removes one);
  - brightness;
  - animated + speed;
  - icon.
- **Try it in** plays the scene on a chosen real room/zone.
- **Save** reloads the library for every card on the page.

### Battery / gauge zone card and alarm panel card
- The battery/gauge card has a full visual editor: rows list, colours/units/icons
  section, and icon modes `battery | gauge | entity | custom`.
- The alarm card has an entity picker and a demo section.
- Starting configs pick real entities.

### All cards
- `getCardSize()` gives the real height. `getGridOptions()` is full width, with a
  minimum of half.
- The card picker shows a preview and a README link.

## Live Home Assistant

- **HA** is 2026.9.x. The Hue bridge is `ecb5fa993162` (config entry
  `01K9M3B829ZTWAA7DHVYPA79K4`), with 12 rooms and 11 zones.
- **Dashboards using the cards:**
  - **Mobile** (`dashboard-mobile`):
    - Quick Actions has the alarm card and Kitchen / Living Room / Middle Floor
      light cards.
    - The **Lighting** tab has room cards for Kitchen, Living Room and Entrance
      (Ground Floor); Garden (Patio Lightstrip + Garden Spotlight); Middle Floor
      Hallway, Second Bedroom and Spare Bedroom; and Landing (Main + Ambient
      Spotlights), Office, Hayley's Bedroom and En-Suite.
    - The tiles' names and `phu:` icons were kept as row overrides.
  - **Hayley** (`dashboard-hayley`): full-width light cards for Hayley's Bedroom,
    Kitchen Spotlights, Living Room Ambience and Middle Floor. They replaced two
    side-by-side tile pairs.
  - **Living Room Panel** (`living-room-panel`): a Living Room card (Ceiling Light,
    Shelf Table Lamp, TV lightstrip, TV Table Lamp) and a Kitchen card on its
    Kitchen tab (Ambience + Spotlights).
  - **Battery Status**, **Alarm**, and **Design Presets** (tabs: main, Beta, Scene
    styles, Scene builder).
- `light.outside` still sits in the Garden sensor list on Mobile's Security tab. It
  was left there on purpose.
- **Kitchen:**
  - Room `light.kitchen` (8 lights).
  - Zones `light.kitchen_spotlights` and `light.kitchen_ambience` (2 lights).
  - The Mobile Kitchen card uses Hue scenes Energise, Bright, Cool bright and Ruby
    glow.
- **Living Room:** room `light.living_room`, zones `light.living_room_ambience` and
  `light.living_room_table_lights`.

## Open items

- The real-light tests at the top of this file.
- The active-scene select doesn't list Hue-only scenes (e.g. Hue's Ruby glow in a
  room). It only lists library scenes.
- `scene.kitchen_kitchen_rest` has a doubled name. It's harmless and could be
  renamed.
- The user may find some editor options too complex. Ask which, then consider an
  "Advanced" section.
- Per-scene picture upload (`image` selector) is still untested on a real
  dashboard.
- Non-Hue lighting is deferred.
- A later repo rename (e.g. to "church-drive") may happen. HACS would then need
  re-adding.

## Dev loop

```bash
npm install
npm run build     # release bundle into custom_components/…/frontend + beta bundle at root
npm run watch
ruff check custom_components --select E,F,W,B --line-length 140   # Python lint
```

**Headless checks:** Chromium and Playwright are available. Load
`church-drive-cards-beta.js` into a page with a mock `hass` (states, entities,
`callWS` returning a library, `callService` logging) and a light card in demo mode.
Tap tiles and read back titles and classes. HA itself can't run in the container
(Python 3.11), so check the Python by review, `py_compile`, ruff and small unit
tests of `library.py`.

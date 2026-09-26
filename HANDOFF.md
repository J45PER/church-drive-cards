# Church Drive: Handoff

*Last updated 2026-09-26. Current release: **v0.12.0**.*

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

**Confirmed on real lights with the user:**
- Colour scenes animate; tapping pauses and resumes them (⏸/▶).
- The selected tile glows (others dim) and stays selected after a page refresh;
  the room's `select.<room>_scene` follows.
- Speeds feel like Hue's (after switching to the bridge's real palettes, v0.10.5).
- Gradient strips show several colours on animated scenes.
- Zone-only scenes (e.g. "… · Bedroom Ambience") change only the zone's lights.
- No drop-out between colour scenes (two alternating working scenes, v0.10.4).
- White scenes (Bright, Relax, Nightlight) select and match.
- Scene builder: making, trying and saving a custom colour scene.
- Editor (v0.10.7): default scenes in the list, friendly labels, Reset button.
- Tiles clear when the lights go off (v0.10.9, Hayley's Bedroom).
- Fuller scene row first (v0.10.10) and the scene-count warning (v0.10.11, on
  Beta).
- Alarm card redesign (v0.11.0) on Beta in demo mode.
- Section panels (v0.12.0) on the real Mobile dashboard: Quick Actions checked
  closely (gaps), the other tabs looked at before release.

**Still to check on real lights:** a colour scene (e.g. a two-colour custom one)
in a room with several lights should give each light its own shade, blended
between the colours (v0.10.6). Nobody has looked yet. And the new alarm card
(v0.11.0) during a real exit or entry delay: the ring should empty, the timer
count down on the title's row, and the card fade towards amber/orange.

**New in v0.12.0 (2026-09-26): dashboard section style "D".** The user wanted
bigger section titles and a background behind each section so its cards read
as one group. Mock-ups: claude.ai/artifact/3CCBXYAz5YgKPRxYG7kFrQ (A panel,
B one block, C heading band, D tinted panels + summary). Picked D: each group
on a faint panel of its own colour, a 1.6rem title with the icon in that
colour, and a short live summary on the right.
- **`section-title-card`** (`src/section-title-card.js`): `title`, `icon`,
  `color` (HA colour name or CSS colour), `summary` (a template, rendered live
  over the `render_template` subscription like HA's markdown card; plain text
  is shown as is). HA colour names are drawn as `var(--<name>-color, <hex>)`
  with a hex fallback table (`STC_FALLBACK`).
- **`section-panel-card`** (`src/section-panel-card.js`): a Section Title plus
  its `cards` on a rounded panel (24px corners, 12px padding, 12px between
  cards, tinted 10% in the colour). Children are made with
  `loadCardHelpers().createCardElement`. A panel right under another panel in
  the same section gets the view's column gap (32px) above it; it finds the
  previous card by walking up to its `hui-card` and checking the previous
  sibling's `config.type`. The editor is the title fields plus HA's own
  vertical-stack editor for the cards.
- **Why a card and not HA's section background:** the first try used native
  section `background: {color, opacity}` with the title card. Sections view
  lines sections up in grid rows, so Climate (under Security) started below
  the tall Lights section; `row_span: 2` on Lights just split its height
  across both rows and the gap stayed. A panel is a card, so several stack in
  one section with no gap problem. (Section `row_span` and
  `dense_section_placement` exist, for reference.)
- **Spacing the user asked for:** the vertical gap between stacked panels
  matches the column gap; the gap between cards inside a panel matches the
  panel's inner edge. Cards that were wrapped in a `vertical-stack` inside a
  panel were unwrapped so they get the 12px gap too.
- **The whole Mobile dashboard is converted** (see "Live Home Assistant"). The
  user checked Quick Actions on Beta, then the other tabs, then asked for the
  release; the dashboard was switched from `-beta` to the released type.

**New in v0.11.0 (2026-09-26): alarm card redesign.** The user asked for a
bigger card that doesn't change size when the timer shows, a layout matching
the other cards, and the timer in line with the status. Mock-ups went through
three rounds (claude.ai/artifact/UkEti2a7AQnNuqihWt8Mek): four layouts, then
"C" (countdown ring) with the status moved up as the title, then refinements.
The user picked "C2" as it was, plus R4's background tint (only during a delay
or when triggered).
- The card no longer grows when a delay starts; it's the same height in every
  state (`getCardSize` 4).
- The status is the card title (1.5rem, 500, like the light and battery card
  titles) in its state colour; "Arming Away" names the mode being armed to.
- Under it: a 72px ring round the shield that empties during a delay, what's
  happening ("Leave now / until armed", "Disarm now / until the alarm sounds",
  or who armed/disarmed it and when), and the timer on the same line.
- Buttons are 56px, icon over a label (Disarm / Home / Away / Night), 12px
  corners; the mode being armed to lights up during a delay.
- The card background fades from normal towards the state colour as a delay
  runs out (6% → 32%), and is 32% when triggered. The old hover-only wash is gone.
- The alarm only reports seconds left, so the delay's full length is the most
  seen since it started. The local countdown only resyncs when the reported
  figure changes (hass is re-set on every change in the house). Demo mode loops
  the countdown so the ring and tint can be seen on Beta.
- The Beta tab has two demo alarm cards: Entry delay (22s) and Disarmed.

**What changed on 2026-09-25/26 (v0.10.6 → v0.10.11):**
- **v0.10.6: every light gets its own colour.** A custom two-colour scene put the
  same colour on several lights, because colours were dealt round in turn.
  `spread()` in `apply.py` (and `spreadColours()` in `src/universal-scenes.js`
  for the pretend home and the fallback) now spreads the palette along the
  lights, blending neighbouring colours when there are more lights than colours
  (red, blue on 6 lights = red, 4 purples, blue). The bridge palette is padded
  to at least five blended colours, like Hue's own, so the animation keeps
  neighbouring lights apart.
- **v0.10.6: default scenes.** Every light card starts with Bright, Dimmed, Relax
  and Nightlight for its room (`LCC_DEFAULT_SCENES`). The old auto-pick of the
  room's Hue scenes is gone.
- **v0.10.7: editor.**
  - The Scenes list starts filled with the four defaults for the card's room
    (`lccFillDefaultScenes`). Until they're changed, they follow the card to a
    new room. An emptied list shows no scenes; a card with no `scenes` key
    still shows the four.
  - List items read "Bright · Kitchen" instead of `universal:bright@light.kitchen`,
    with any name override underneath. HA's object list shows a field's raw
    value (it doesn't look up select option labels), so `label` is a form-only
    field: an invisible `constant` selector in the item's fields, added by the
    shared editor's `display` hook and removed by `store` before saving.
  - A red **Reset** button sits on the Scenes list's Add row (Reset left, Add
    right, same filled style, `variant="danger"`). ha-form has no buttons, so
    `src/form-editor.js` takes `buttons` (`{ label, apply, field, variant }`) and
    reaches into the list's shadow DOM (`ha-selector[name=scenes]` →
    `ha-selector-object` → `.items-container`), adding the button and a flex
    style. If HA's markup changes and that isn't found within 2s, the button
    goes under the form instead. Reset sets `scenes: 'reset'`, which
    `lccFillDefaultScenes` swaps for the defaults before saving.
- **v0.10.8 / v0.10.9: tiles stuck selected with the lights off.** Hayley's
  Bedroom showed Concentrate selected with every light off.
  - Real cause (v0.10.9): Hue groups give their members (`entity_id`) as a
    **set**, and `members()` in `apply.py` only accepted a list, so every Hue
    room/zone was checked as one light, the group itself. The bedroom group
    still reports "on" at 4291K because the unavailable "My Boy Hugo" lamp is on
    as far as the bridge knows, which matched Concentrate. `members()` now takes
    any collection. This also fixed which lights the selects watch and the
    per-light colour fallback.
  - Also (v0.10.8): a tile only shows selected while some of its lights are on,
    and selects re-check once HA has fully started (`EVENT_HOMEASSISTANT_STARTED`).
- **v0.10.10: scene rows.** When tiles can't be shared equally, the fuller row
  comes first so the last row's tiles stretch: 5 = 3 + 2, 7 = 4 + 3.
- **v0.10.11: scene-count warning.** The editor shows a yellow warning above the
  Scenes list when it has more scenes than *Max scenes* shows ("Only the first 8
  of these 10 scenes show on the card. Raise Max scenes to show them all."; a
  separate message for 0). It uses a new `alerts` option in `src/form-editor.js`
  (`{ field, text(config) }`): an `ha-alert` placed across the top of the list's
  items container, the same way as the Reset button, hidden with
  `style.display` (ha-alert's own `display` overrides the `hidden` attribute).
- **Real dashboards** were switched to the default scenes, and the old
  `max_scenes: 4` caps were removed (they hid added scenes). See "Live Home
  Assistant" below.

If a colour scene sets fixed colours but doesn't animate, the bridge probably
rejected the palette body in `apply.py`. The integration falls back to per-light
colours and logs a warning ("Couldn't play … on the Hue bridge"). Read it with
`ha_get_logs(source="system", search="church")`.

| Card | Source | What it is |
|---|---|---|
| `battery-zone-card` / `gauge-zone-card` | `src/gauge-zone-card.js` | Zone card of gradient-filled value rows, worst-first, red/orange/green |
| `alarm-panel-card` | `src/alarm-panel-card.js` | Status as the title, countdown ring and timer, labelled arm/disarm buttons; fixed size |
| `light-control-card` | `src/light-control-card.js` | Light / zone / room control with scene tiles |
| `scene-styles-card` | `src/scene-styles-card.js` | Central scene tile looks (icon, colours, picture) |
| `scene-builder-card` | `src/scene-builder-card.js` | Make custom universal scenes |
| `section-title-card` | `src/section-title-card.js` | Large section title, coloured icon, live template summary |
| `section-panel-card` | `src/section-panel-card.js` | Section title plus cards on a colour-tinted panel |

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
- New dashboard groups go in a **Section Panel** (coloured panel, big title,
  live summary), several per HA section, matching the Mobile dashboard.
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
    It's pinned to `454e060` (the v0.12.0 section panels; the same card code as the release).
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
  - It's a RestoreEntity: the last scene survives restarts. It's checked again
    once HA has fully started, because at restore time the Hue lights may not
    have loaded.
  - It judges a room/zone by its **member bulbs** (`members()`), never the Hue
    group's own on/off, which can be wrong (Hayley's Bedroom reads "on" with every
    bulb off because of the unavailable Hugo lamp). Hue gives members as a set.
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
- **Scene list in the editor:** starts with the four defaults for the card's room;
  items read "Bright · Kitchen"; a red Reset on the Add row puts the defaults
  back; a yellow warning shows when there are more scenes than Max scenes. The
  Add dropdown lists universal scenes first, once per place, e.g.
  "Bright · Kitchen", "Bright · Kitchen Spotlights".
  - Then the Hue scenes of the card's room/zones, always labelled with their group.
  - Hue scenes named like a universal one are hidden. Scenes from elsewhere already
    on the card stay, marked "(other room)".
- **Tile names:** a zone-targeted tile reads "Bright · Spotlights" (room name
  stripped).
- **Tap:** a real home calls `church_drive.apply_scene`. The pretend home sets
  lights locally; colour scenes animate via `DemoHome.playPalette`.
- **Selected:** only while some of its lights are on. A universal tile is
  selected when its target's scene select says so, or failing that when the
  lights match:
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
    with any fuller row first, so the last row's tiles stretch wider. Each row
    fills the width: 5 = 3 + 2, 6 = 3 + 3, 7 = 4 + 3, 9 = 3 + 3 + 3.
  - **Names:** hidden on tiles under 100px wide (`scene_names: auto`). `always` and
    `never` override that.
  - `max_scenes` defaults to 8, and 0 hides tiles. Scenes past the limit are
    left off the card (that's why added scenes didn't show on the Quick Actions
    cards, which were capped at 4); since v0.10.11 the editor warns about it.
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
- The alarm card has an entity picker and a demo section (state, mode being
  armed to, countdown, by, time, supported features).
- **Alarm card layout (v0.11.0):** 16px padding; the status title (1.5rem,
  state colour); a row with the 72px ring (shield icon inside; empties as a
  delay runs), two lines of what's happening, and the timer (2rem, hidden but
  still taking space when there's no delay); then the mode buttons (56px, icon
  over label, only the modes in `supported_features`). Colours: Disarmed green,
  Home blue, Away red, Night purple, exit delay amber, entry delay deep orange,
  triggered red. Background tint 6% → 32% of the state colour as a delay runs
  out, 32% when triggered. The real alarm is `alarm_control_panel.church_drive_alarm`
  (Home + Away; attributes `entrySecondsLeft`, `exitSecondsLeft`, `targetState`,
  `lastArmedBy/Time`, `lastDisarmedBy/Time`).
- Starting configs pick real entities.

### Section panel and section title cards
- Use a **Section Panel** for each group on a dashboard page (see v0.12.0 at
  the top): title, icon, colour, optional template summary, and its cards.
  Stack several in one HA section per column instead of using HA section
  backgrounds or heading cards.
- Colours in use: security green, lights amber (garden green), climate
  deep-orange/orange/blue, cooling light-blue, doors indigo, cameras blue-grey,
  fire red, blinds brown, cleaning blue.

### All cards
- `getCardSize()` gives the real height. `getGridOptions()` is full width, with a
  minimum of half.
- The card picker shows a preview and a README link.

## Live Home Assistant

- **HA** is 2026.9.x. The Hue bridge is `ecb5fa993162` (config entry
  `01K9M3B829ZTWAA7DHVYPA79K4`), with 12 rooms and 11 zones.
- **Dashboards using the cards:**
  - **Mobile** (`dashboard-mobile`), all tabs on section panels (v0.12.0), one
    HA section per column (theme Mushroom Shadow):
    - **Quick Actions:** [Security (green, alarm state) + Climate (deep-orange,
      downstairs °C · action)] [Lights (amber, "N rooms on" over Kitchen /
      Living Room / Middle Floor)] [Cleaning (blue, state · battery)]. Header
      "Hello {{ user }}".
    - **Lighting** (max 4 columns; the user split it into four sections): Ground
      Floor (Kitchen, Living Room, Entrance), Middle Floor (Hallway, Second
      Bedroom, Spare Bedroom), Top Floor (Landing, Office, Hayley's Bedroom,
      En-Suite), all amber with "N rooms on"; Garden (green, On/Off; Patio
      Lightstrip + Garden Spotlight). Row names and `phu:` icons are overrides.
    - **Security:** [Alarm (green) + Doors & Motion (indigo, "Doors closed" /
      "N doors open"; the door, motion, battery and tamper lists)] [Outdoor
      Cameras + Indoor Cameras (blue-grey)] [Fire Alarm (red, safe mode)].
    - **Climate:** [Heating + Temperature (orange, downstairs °C) + Humidity
      (blue, downstairs %)] [Cooling (light-blue, fan) + Air Purifier (green,
      on/off · PM2.5, filters and graphs)] [Blinds (brown; blank while the blind
      reports unknown)].
    - **Cleaning:** one Cleaning panel (blue, state · battery).
  - **Hayley** (`dashboard-hayley`): full-width light cards for Hayley's Bedroom,
    Kitchen Spotlights, Living Room Ambience and Middle Floor.
  - **Living Room Panel** (`living-room-panel`): a Living Room card (Ceiling Light,
    Shelf Table Lamp, TV lightstrip, TV Table Lamp) and a Kitchen card on its
    Kitchen tab (Ambience + Spotlights).
  - **Battery Status**, **Alarm**, and **Design Presets** (tabs: main, Beta, Scene
    styles, Scene builder).
  - **Alarm cards:** Mobile → Quick Actions and Security tabs, the Alarm
    dashboard (`alarm-panel`), Design Presets main (demo), and two demo cards on
    the Beta tab (Entry delay 22s, Disarmed).
- **Scenes on the real cards (2026-09-26):** every light card has the four
  defaults aimed at its Hue room (`universal:<key>@light.<room>`; Bedroom is
  `light.second_bedroom`, Garden `light.garden`), except:
  - Mobile Quick Actions **Kitchen**: the defaults plus Cool bright, Energise,
    Soho · Ambience and Emerald isle (the user added these).
  - Mobile Lighting **Hayley's Landing**: the custom scene Cyber Fidelity on the
    landing ambience zone.
  - Mobile Lighting **Hayley's Bedroom**: the user's 8 test scenes (Bright, Cool
    bright, Dimmed · Main, Nightlight, City Blue, and Dreamy dusk / Soho /
    Spellbound on the ambiance zone).
  - No real card sets `max_scenes` below 8 any more.
- `light.outside` still sits in the Garden sensor list on Mobile's Security tab. It
  was left there on purpose.
- **Kitchen:** room `light.kitchen` (8 lights); zones `light.kitchen_spotlights`
  and `light.kitchen_ambience` (2 lights).
- **Hayley's Bedroom:** room `light.hayleys_bedroom`, zone
  `light.hayley_s_bedroom_ambiance`, also a `light.hayley_s_bedroom_main` group.
  "My Boy Hugo" (`light.my_boy_hugo`) is unavailable, and the bridge still counts
  it as on, so the Hue room reports "on" with every bulb off.
- **Living Room:** room `light.living_room`, zones `light.living_room_ambience` and
  `light.living_room_table_lights`.

## Open items

- The colour-spread and real alarm-delay checks at the top of this file.
- "My Boy Hugo" is unavailable; the user may want to power-cycle or re-pair it.
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
ruff check custom_components --select E,F,W,B --line-length 140   # Python lint (only the long palette lines in library.py fail, by design)
```

**Headless checks:** Chromium and Playwright are available. Load
`church-drive-cards-beta.js` into a page with a mock `hass` (states, entities,
`callWS` returning a library, `callService` logging) and a light card in demo mode.
Tap tiles and read back titles and classes. HA itself can't run in the container
(Python 3.11), so check the Python by review, `py_compile`, ruff and small unit
tests of `library.py`.

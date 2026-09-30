# Church Drive: Handoff

*Last updated 2026-09-30. Current release: **v0.23.0**.*

## Where this stands

This repo (`github.com/J45PER/church-drive-cards`, public) is the **Church Drive**
Home Assistant integration (`custom_components/church_drive`). It's installed through
HACS as an integration. It does two jobs:

1. **Delivers the custom Lovelace cards.** It serves the bundle and loads it on
   every dashboard. There's no separate card install.
2. **Universal scenes.** It keeps one library of scenes (Bright, Relax, Soho… plus
   the user's own). Any light card can use them in any room or zone without Hue
   scene setup. There's also a scene select per room/zone and a scene builder.

**New in v0.22.0 (2026-09-29): smoother panels, floating chips, page headers** (the user found the
chips didn't pin, and panels jumped to a slightly larger height before expanding):
- Chips pin like `position:sticky` (HA's card wrappers stop real sticky working):
  - At the top of the page the capsule sits in its own row, inside the spacer
    (`position:absolute`), so it never covers the view header's "Hello Jamie".
  - Once that row reaches `headerBottom()` + 8px (measured from hui-root's `.header`),
    it moves to `document.body` as `position:fixed`, aligned to the card.
  - Chip jumps aim for the pinned position (`_pinnedChipsBottom`).
  - The chips are inline in edit mode or preview.
- `_slide` holds the panel at its old height (`height`, `overflow:hidden`) for two
  frames while the cards redraw, then measures `height:auto` and transitions to it
  (340ms), fading the cards in. The old version measured too early, so it animated to
  a slightly-too-small height and then jumped.
- While a panel slides it fires `cd-anim` +1/−1; Auto Layout pauses `_queue` and lays
  out once at the end. Panels have `transition: min-height`, so re-levelling eases.
- Auto Layout keeps each panel's column once the page has settled (4s after connect;
  2.5s after the column count changes); open/close only re-levels.
- Chip taps (`_jumpTo`):
  - Open a compact panel for that visit only (`_fallback = 'open'`, not saved).
  - Add a tail spacer so the last panels can reach just under the chips.
  - `_trimTail` clears the spacer once the jump has landed and the user scrolls back up
    40px or more, or after 3s if the jump never lands.
  - The user asked for both (2026-09-29).
  - The next jump (or ↑) closes the panel the last jump opened, if it's still only
    open for that jump (`_closeJumped`).
  - Scrolling uses `_glide`, which tracks the target each frame (16% of the remaining
    distance) so panels resizing on the way don't throw it off. A touch or wheel stops
    it. `scrollParent` finds HA's scroller.
  - **Back to top** is a 58px circle to the right of the nav bar (`back_to_top`,
    default on).
    - Three `<i>` strokes animate: a dash at the top, an ↑ once scrolled past 40px
      (`.nb-up`). The strokes are opaque, with the secondary text colour for the
      dash; a translucent dash showed its overlap.
    - Tapping it (or the current page's icon) fires `cd-to-top`, which closes the
      panel a chip jump opened, and glides to the top.
    - `kitScrollParent`, `kitScrollTop` and `kitGlide` moved to card-kit. The user
      asked for it by the nav bar rather than in the chips.
  - Fixed: a panel opened by a jump needed two presses to close (the toggle read its
    state after clearing `_fallback`).
- **Page header** (Auto Layout `title`, 2026-09-29):
  - The title is centred, 2rem bold, like the Home view's "Hello {{ user }}" markdown
    header.
  - Under it are alert pills for panels whose `open_when` is true (`el._alert`), with
    icon, live colour and "Title · summary". Tapping a pill `_jumpTo`s the panel.
  - It redraws on each layout pass (`cd-panels-changed`, plus the 3s tick for summary
    text).
  - **Consistency pass:** the header has a fixed height on every page (40px title line,
    34px alerts line that's blank when empty and scrolls sideways rather than
    wrapping; 98px in all). `{user}` in the title is the signed-in person's first
    name. Home dropped HA's view header (markdown "Hello {{ user }}") for Auto Layout
    `title: Hello {user}`, so every Mobile page uses the same header. Chips `auto` now
    shows on every phone page (1+ titled panels), not only 4+, so the row is always
    there.
- **v0.23.0 (released 2026-09-30, reload-only): header widget, priorities, House Tasks card, nav bar tight mode.**
  - The header's alerts line became a fixed three-row widget (26px rows, 96px box;
    header 160px on every page): page alerts first, then the person's to-dos.
  - With `priorities: true`, Auto Layout subscribes (`todo/item/subscribe`) to
    `todo.priorities_<first name>` and lists open items, overdue and soonest-due first
    (overdue in red, others purple), each with a ✓ (`todo.update_item`, completed).
  - `priorities_page` adds a side button "To-do ›" (or "+N ›").
  - The user chose A-style, fixed-size ("like a widget"), with "view more" going to a
    per-person to-do page.
  - **HA side (2026-09-30):**
    - Local To-do lists `todo.priorities_jamie`, `todo.priorities_hayley` and
      `todo.priorities_diane`.
    - Mobile gets a `todo` view with one section per person (`visibility: user`),
      holding an Auto Layout "To-do" page with a "My to-do" panel and HA's
      `todo-list` card, plus the nav bar.
    - Home's Auto Layout has `priorities: true` and
      `priorities_page: /dashboard-mobile/todo`.
    - `automation.church_drive_automatic_to_do_tasks` syncs automatic tasks every
      15 minutes, on start, when the selects or vacuum message change, and when
      device health holds for 15 minutes. Tasks:
      - devices not responding;
      - Battery Status batteries under 20%;
      - purifier filters under 10%, and Gregg's filter under 24h;
      - unread Gregg messages.
    - **Automatic tasks live on their own list, `todo.priorities_automatic`**
      ("Priorities Automatic", local_todo entry 01M3RR7QJ3FWB8TKKGKQVPWT5J), never on
      the lists people tick. The user asked for no tick box at all on tasks the
      device confirms. The description is "Automatic · <kind> · <detail> · for
      <names>", where <names> is "Everyone" (all three) or one or more first names
      (e.g. "Automatic · Filters due · 8% left · for Jamie, Hayley"). One copy per
      task however many people it's for.
      - The automation removes a task once the device reports it's sorted (e.g. the
        doorbell going from 17% to 95% after a battery change).
      - It keeps the detail and assignee up to date in place (`refresh`,
        `todo.update_item` description).
      - It reopens a task ticked off elsewhere (e.g. HA's own To-do panel) while it's
        still needed.
      - It clears any automatic items left on the personal or shared lists.
    - **Showing them:**
      - The widget reads `todo.priorities_automatic` and shows the person's tasks and
        everyone's, with no ✓, in purple (#ab47bc, `HOUSE_TASKS_COLOR`), with an icon by kind (battery, filter,
        heart-pulse for devices, vacuum).
      - The new **`house-tasks-card`** is a read-only list of the same tasks. Its
        `show` option is `mine` (default: tasks naming this person, shown as "You, Hayley"
        when shared, plus everyone's) or `all` (every task, with names). Each person's To-do page has it in a purple (#ab47bc,
        the user's choice) "From the house" panel; Manager has it with `show: all`.
    - **Shared tasks** (added 2026-09-30, user's request): `todo.priorities_everyone`
      ("Priorities Everyone") is for tasks people add for everyone. The widget merges
      it after the person's own list (`mdi:account-group`), and ticking one clears it
      for everyone. Each To-do page has a teal "Shared" panel for it.
    - **Who gets each kind** (multiple people allowed, user's request): 12 switches,
      `input_boolean.to_do_<kind>_<person>`. The kinds are `devices_not_responding`,
      `low_batteries`, `filters_due` and `vacuum_messages`; the people are `jamie`,
      `hayley` and `diane`. Each kind started with Jamie only.
      - The automation's `assign` variable turns them into "Jamie, Hayley",
        "Everyone" (all on) or "" (none, no tasks).
      - They replaced the single-choice `input_select.to_do_*` dropdowns, which were
        deleted.
      - The automation is queued with `max: 10` and `max_exceeded: silent`. With
        max 2, flipping several switches at once dropped the final run and left a
        stale assignee.
      - Tested: Jamie+Hayley, all three (Everyone) and none (removed) on the
        pre-filter task.
    - **More sources (2026-09-30, after v0.23.0; user: "the filter is just an
      example"):** `wanted` now covers:
      - Batteries: every Battery Notes sensor (`*_battery_plus[_N]`) below
        `input_number.to_do_battery_low_below` (30%), or whose `*_battery_plus_low`
        flag is on.
        - Phones and tablets are skipped (any device with a `mobile_app` entity).
        - Rechargeables ("Li-ion"/"Rechargeable" in `battery_type_and_quantity`)
          become "Charge <name>". Others become "Replace battery: <name>" with the
          type in the detail.
        - Names come from the `batteries` map first, then the device name.
        - Duplicates (Ring registers some devices twice) are merged by device name
          plus rechargeable.
      - Devices not responding: the health sensor, plus any light / switch / fan /
        climate / cover / camera / vacuum / lock / alarm unavailable longer than
        `input_number.to_do_offline_for` (60 min). Media players are left out, since
        TVs go unavailable when off.
        - The whole device must be quiet: nothing else on it reporting, ignoring
          buttons/selects/times/numbers/text/events/updates, which sit at
          "unknown". Before this, Gregg got a false "not responding" because only
          `switch.gregg_off_peak_charging` was unavailable while the vacuum was
          docked and fine (fixed 2026-09-30).
        - "My Boy Hugo" (a Hue Go, a portable lamp) is genuinely unreachable. The
          user can label it "Ignore in to-dos" if it's usually unplugged.
      - Filters and parts: the purifier filters (<10%), plus all six Gregg
        consumables due within `input_number.to_do_parts_due_within` (24h): filter,
        main and side brush, sensors, dock tray, mop cloth.
      - Safety alarms: smoke/CO `*life_end` / `*end_of_life` sensors on. New kind,
        `input_boolean.to_do_safety_alarms_*`.
      - Updates: `update.*` on. New kind, `input_boolean.to_do_updates_*`.
      - Anything labelled `ignore_in_to_dos` ("Ignore in to-dos"), on the device or
        the entity, is skipped.
      - First run: "Charge Ring Alarm Keypad" (25%), "Replace battery: Hue Living
        Room Light Dial" (29%, CR2032), and the pre-filter. Gregg's sensors (34h)
        and dock tray (30h) come next.
      - Manager's grid has the two new kinds plus a "When to make a task" block with
        the three thresholds.
      - HA restarted on its own at 11:03 on 2026-09-30, not by us. The switches
        restored with values someone had set in Manager in the meantime; they were
        left as they are.
    - **Notifications (2026-09-30, user's request):**
      `automation.church_drive_to_do_reminders` (queued, max 20) sends two kinds, each
      person on their own devices. Devices are set in the `people` variable: Jamie's
      iPhone and Pixel (not the iPad or the watch), Hayley's Pixel 9, Diane's phone.
      - **Summary** (trigger id `summary`): at `input_datetime.to_do_reminder_time`
        ("To-do: summary time", 10:00) on `input_select.to_do_summary_day`
        (Saturday; "Every day" is also an option).
        - One notification per person, "To-do summary (N)": their own list, the
          shared list (marked "(shared)"), and the automatic tasks that name them or
          Everyone, with details. At most 8 lines, then "…and N more".
        - Nothing is sent to someone with nothing to do.
        - Per person: `input_boolean.to_do_reminders_<person>` ("To-do summary ·
          <name>").
      - **New-task alerts**, per person: `input_boolean.to_do_new_task_alerts_<person>`.
        - Automatic tasks (trigger id `auto`): the sync automation fires
          `church_drive_todo_added` {list, item, kind, who} for each task it adds.
          The alert "New to-do: <kind>" goes to the people named in `who`, so it's
          exact.
        - People's lists (trigger id `added`): when the open count of
          `todo.priorities_<person>` or `_everyone` goes up by N, the last N open
          items are taken as new. Local To-do appends new items at the end; an
          unticked old item could be misread, which is rare.
        - Own list: the alert goes to that person. Shared list: to everyone.
        - Never sent to the person who added it (matched by the change's
          `context.user_id` to a `person`). Changes made by an automation or the
          API have no user, so they notify.
      - Tapping any of them opens `/dashboard-mobile/todo`. The summary uses tag
        `church-drive-todo-summary`; each alert has its own tag.
      - Manager: a "Notifications" block with the day and time, plus two rows of
        person tiles (summary / as soon as something new is added).
      - Tested on Jamie only: the summary (while it was still daily at 18:00), an
        automatic alert via a test event, and an added item on Jamie's list (added,
        then removed).
      - Why the old battery alert never fired:
        `automation.battery_notes_low_battery_alert` only triggers when a Battery
        Notes `_low` flag turns on, at Battery Notes' 10% threshold. Nothing has
        been that low, and it only notifies the Pixel. It's left in place. The
        weekly Friday 18:00 battery report does run.
    - Manager's "Automatic to-dos" panel has a "Who gets each kind of task" grid: a
      heading per kind and three purple person tiles (tap to toggle). Below it are
      the lists and `house-tasks-card` with `show: all`.
    - **Navigation:** every Mobile nav bar has a sixth page, To-do
      (`/dashboard-mobile/todo`, #7e57c2). The nav bar gained a tight mode
      (`.nb-tight`, set by `_fit()` when the items overflow, and on resize): 2px
      gaps, 40px items, and the current page shown as its coloured pill without a
      label. It kicks in below about 430px wide.
    - **Tasks dashboard** (`dashboard-tasks`, in the sidebar, mdi:clipboard-check-outline;
      user's request):
      - a **To-do** view that mirrors Mobile's To-do view, per person (the same three
        panels, 3 columns on wide screens);
      - a **Shopping** view with `todo.shopping_list`;
      - its own nav bar: To-do / Shopping (no Home button; the user didn't want a link back to Mobile).
      - It's a copy of Mobile's config, not a live link, so change both together. More
        lists (e.g. other shopping lists) go on the Shopping view as extra panels.
    - The header widget is capped at `widget_width` (default 520px) and centred, so it
      isn't page-wide on tablets and PCs.
    - On release, `-beta` was stripped from the Mobile, Tasks and Manager dashboards,
      so they all use the released card types.
- **In beta: acrylic and scroll room (2026-09-30).**
  - The user chose option C, "Deep frost", from the acrylic mock-up.
    - `kitAcrylicCss(sel)` in card-kit gives: a 76% card-colour tint with a 4% top
      highlight, `blur(42px) saturate(115%)` behind it, 8% SVG grain (`KIT_GRAIN`) in
      `::after`, and no outline.
    - Used by the chips capsule (`.al-cap`), the nav bar (`.nb`) and back-to-top
      (`.nb-top`). Their old `inset 0 0 0 1px` outline is gone.
  - Frosted cards: section panels set `--cd-card-bg` (74% tint) and
    `--cd-card-filter`, plus HA's `--ha-card-background` /
    `--ha-card-backdrop-filter`, so both Church Drive cards (`kitShell`, compact
    rows, `kitHead` tint) and HA's own cards go see-through and blurred.
    - Panel option `frosted_cards` (default on).
    - The page background stays flat dark (the user's choice for now). A soft colour
      background was offered in the mock-up.
  - The last panel can always scroll up under the chips.
    - `_restTail()` keeps room at the end of the page so the last panel (in page
      order) can scroll to just under the pinned chips, where a chip jump puts it.
    - It's measured against the real end of the scroller, so the nav bar's spacer
      counts, and applies on phones with chips.
    - `_trimTail()` falls back to it after a jump; `_queue()` re-checks it after each
      layout.
    - Tested: at the very bottom the last panel sits at the same place its chip
      takes it (112px vs 112px).
  - Follow-up from the user's feedback:
    - Cards in panels use the lighter "B" recipe (62% tint, `blur(30px)
      saturate(125%)`). The panel's coloured background is unchanged. Chips, nav
      and back-to-top stay "C".
    - **Empty panels last:** Auto Layout has `empty_last` and panels have
      `empty_when` (a template).
      - A card can report emptiness with a bubbling `cd-card-empty` event
        {empty}; `house-tasks-card` does this. The panel also picks up a report
        made before it was on the page (`_reportedEmpty`).
      - The layout plan now includes item order, so a reorder rebuilds it even on
        one column.
      - To-do views (Mobile and Tasks): order From the house → My to-do → Shared,
        `empty_last: true`, `controls_first: false`; the lists' panels have
        `empty_when: states(todo…) == 0`.
      - Tested: with no house tasks the order became My to-do > Shared > From the
        house, and it went back when a task appeared.
    - Consistency pass: the alarm, climate, light control, gauge/battery zone,
      scene builder and scene styles cards drew their own solid
      `var(--card-background-color)` surface, so they (and the alarm/climate
      status tints) weren't frosted when a panel was open. They now use
      `KIT_CARD_BG` plus `backdrop-filter: var(--cd-card-filter, none)`, like
      `kitShell`. A card outside a panel still falls back to the normal solid
      colour.
    - Header widget To-do button: no "›"; the text matches the icon's lilac
      (#b39ddb), reading "+N more" or "To-do"; 22px icon; aria-label.
  - Mobile is on `-beta` cards again for testing; strip them on release. Mock-up:
    https://claude.ai/artifact/Y9kej1q5Hxpggozpc8CkmV
- The back-to-top strokes are solid white in both states.
- **Chips capsule, 2026-09-29:**
  - Chips sit in a scrolling `.al-strip` and grow to fill it (`flex:1 0 auto`), then
    scroll when there are many.
  - The current chip is filled only while its panel is open; when compact it isn't
    marked at all (the user disliked an outline).
  - The open/close-all button (v0.22.0) was removed in v0.22.1 on the user's request (back-to-top
    already closes the panel a chip opened). The capsule (`.al-cap` holding
    `.al-strip`, `CHIPS_CSS`) now runs the full width.
- **Page-change flicker:** while a page first lays out (`_settleUntil`), stretch
  changes are applied with no min-height transition, so panels no longer visibly grow
  into place when you arrive. `PANEL_TRANSITION` is exported from the panel.
- Managed panels (inside Auto Layout) no longer install their own ResizeObserver,
  3s timer or `cd-panels-changed` listener.
- v0.22.0 and v0.22.1 were released reload-only; Mobile is back on released card types.

**New in v0.21.0 (2026-09-29): panel controls.** Chosen from the `panel-ux` mock-up
(the user picked C's +/− with B's footer):
- The open/compact toggle is a ring in the panel's colour (`--stc-c`, follows
  `color_template`) with a − that turns into a + (`.stc-shut`, CSS in
  `STC_TOGGLE_CSS`).
- The whole title row opens and closes the panel, even when the panel has a `link`.
- Panels slide between heights (`_slide` in the panel: Web Animations on height, 320ms,
  skipped for `prefers-reduced-motion`; runs on user choices only, not alerts).
- A panel's `link` is a full-width **"Go to …"** footer button (`link_label`,
  `margin-top:auto` so it sits at the bottom of stretched panels, hidden while
  compact). `_naturalHeight` measures to the footer when it shows. A standalone
  Section Title, or a panel with `collapsible: false`, still navigates from the row.
- **Jump-to chips** in Auto Layout (`jump_chips: auto | always | never`; auto =
  one column and 4+ titled panels). They're sticky under the header
  (`top: var(--header-height)`), in page order, coloured from each panel's live
  background. Tapping one scrolls to its panel (`scrollMarginTop` = header + chips),
  and the chip for the panel at the top is filled in (window scroll listener,
  capture).
- The alarm card's countdown ring is 84px (r 38, stroke 6), like the other dials.
- The light card's scene tiles take colours from the scene (`styleName`), not their
  label. A scene aimed at one light is labelled "Bright · Front Light", which used
  to miss the palette and get hashed colours.
- Released with a full restart (the user asked for one). The Philips fan and
  purifier came back by themselves, and health read 0 stale devices.

**Also 2026-09-29 (Home Assistant config, not the repo):**
- **Other dashboards converted** to one Auto Layout card per page with Section
  Panels, like Mobile: Hayley, Living Room Panel, Alarm and Battery Status (see
  Live Home Assistant).
- **Stale-device alert:** `automation.church_drive_tell_jamie_when_a_device_stays_stale`.
  - When `sensor.church_drive_device_health` > 0 for 15 minutes (after the auto-fix
    nudges at 90s and 10 min), it notifies Jamie's iPhone
    (`notify.mobile_app_xitol_j45per_iphone`; tag `church-drive-health`; opens Mobile
    Climate). The message names the stale devices.
  - When the count drops below 1, it sends `clear_notification`. This also fires after
    a restart, which is harmless.
  - The user may want the Pixel (`mobile_app_xitol_j45per_p10pxl`) as well.

**New in v0.20.0: Auto Layout Card** (`src/auto-layout-card.js`). The user
wanted tablet layout to adapt by itself as panels are added, not hand-arranged per
page. Each Mobile page is one full-width section holding a
`custom:auto-layout-card` (all its panels, in the old phone order) plus the nav bar. It shares panels between columns to make the
tallest column as short as possible (tries every sharing up to 11 panels, each column
keeps list order; keeps the current sharing unless a new one is 24px+ better),
stretches each column's last open panel so bottoms are level but only by up to
max(160px, half its height) (the user disliked big empty panels), and only moves cards when the split changes (so
cameras don't reload). Controls first (`controls_first`, default on): within each band, panels whose
cards have buttons/sliders (`hasControls`: alarm, light-control, climate, fan,
purifier, cover, scene cards, tiles with features, nested stacks checked) go above
info-only panels (cameras, climate-zone, security-zone, entities), on phones too;
a panel's `priority: controls|info` overrides. Automatic widths: a panel with no `card_width` picks one from its cards (`AUTO_WIDTH`:
security-zone 200, picture/camera 220, tile 200, else 300; max over cards).
`full_width` is auto/yes/no (true/false still work): auto = 3+ cards of width ≤240 that
don't fit side by side in one column. Full-width panels sit below the balanced columns
(controls first). Mobile panels have no `card_width`/`full_width` now (all automatic).
Panels inside get `_managed = true`, which turns off their own
section-to-section matching and stacked-panel spacing. Mobile uses the
released types (`custom:auto-layout-card`, `custom:section-panel-card`).

**Also in v0.20.0: nav bar `icon_template`** (a page's icon from a template). On
Mobile the Security page's icon follows the alarm (disarmed shield-off-outline, home
shield-home, away shield-lock, night shield-moon, arming shield-sync, pending
shield-alert, triggered alarm-light).

**Also in v0.20.0: no flicker on rebuild.** `stcRender` remembers each template's last
result (memory + localStorage `cd-tpl-cache`, 300 max) and calls back with it straight
away, so nav bar icons/colours and panel colours/summaries don't flash their fixed
values; the first colour is set without its fade (`stcSetInstantly`). Panels remember
the last signed-in user (`cd-user`) for their open/compact key before `hass` arrives.
Auto Layout remembers each page's last arrangement (`cd-layout-plans`, keyed by path,
columns and panel titles) and uses it before panels can be measured.

Mobile's Lighting page also gained a **Front Garden** panel (green, `light.front_light`,
area `front`, Bright/Dimmed/Relax/Nightlight as plain `universal:<key>` refs; an
`@light.front_light` target renamed the tiles "Bright · Front Light", which missed the
palette and got hashed colours; fixed in v0.21.0). v0.20.0 was released reload-only.

Everything is merged to `main`, released and running live, and every dashboard uses
released card types; `-beta` types are only on Design Presets' Beta tab. The user tests on real
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
- Alarm card redesign (v0.11.0): on Beta, then in a real delay ("okay").
- Each light gets its own shade in a colour scene (v0.10.6, "looked like it
  does").
- Security panels follow the alarm colour (v0.12.1), seen live.
- Section panels (v0.12.0) on the real Mobile dashboard: Quick Actions checked
  closely (gaps), the other tabs looked at before release.

- Climate Card first look on Mobile Quick Actions (v0.13.0, phone screenshot):
  gauge, humidity, outside temperature, real humidity history and quick
  settings all render with the Nest off. The user then asked for the
  alarm-style quick settings and mode-coloured panels (both done, v0.13.1).

**Confirmed on real devices (Climate tab, 2026-09-27):** the new cards render
on the phone; the purifier's air quality now matches the Philips app (v0.14.1,
"Good" at 18 µg/m³); the fan no longer flickers "On" before its speed (v0.14.2,
reported by the user, fix untested on the real fan).

**Confirmed 2026-09-28:**
- v0.19.0 was the first **reload-only release**: HACS download, then
  `reload_config_entry` for `01M3C9Z12M0W755GDTM455NFVB`. The resource went to
  `?v=0.19.0` with no HA restart, the Philips devices were untouched, and health
  stayed at 0.
- Device Health, v0.18.0 restart (09:33): the fan (off, re-synced to its 06:55
  reading) and the purifier (Auto) were both flagged and re-synced within a
  minute. No nudge was needed and health went to 0. The purifier that stayed
  frozen after the v0.17.2 restart had recovered by morning.
- The user saw the nav bar on Beta/Mobile ("Looks good") before release.

**Still to check on real devices:**
- **Nav bar on the phone (v0.18.0):** HA's own tabs are hidden (the selector was
  only tested on a mock; if they show, check `hui-root`'s real tab element); taps
  go to the page; the dots and Security's alarm colour are live; the Home panel
  titles (›) open their pages; the bar doesn't cover the last card.
- **Security Zone Cards (v0.17.0):** hold-to-scrub on the strips with a finger
  (no page scroll), the Front light / Floodlight tiles, whether the Front Garden
  bars read well.
- **Alarm countdown (v0.17.1):** in a real exit or entry delay, the ring should
  empty smoothly to the end, without refilling for the last ~15 s.
- v0.15.x on the phone: the colour scale and room types, press-and-hold on
  the graphs with a finger (hold ~0.3s, drag; page shouldn't scroll), the
  smoothed lines, the flatter floor cards, and the Nest graphs' comfortable
  band and humidity lines.
- The Climate Card with the Nests actually heating (too warm to test so far):
  −/+ (one `set_temperature` after tapping stops), Off/Heat/Eco quick settings,
  and the Heating panel changing colour.
- Hold to test on the carbon monoxide card (sounds the real alarm; only when
  the user is happy to).

**New in v0.19.0: open or compact section panels.** Mock-ups:
claude.ai/artifact/6R69sMFfG7u7jrM15YmYbL. The user chose Compact on phones, Open on
tablets and auto-open on alerts, and wanted collapsed panels to keep key
details and actions ("especially on Climate"). Peek was dropped.
- `kitCompact(root, spec)` and `kitCompactable(Cls, rebuild)` in card-kit. A card
  with `supportsCompact` gets a `compact` property from its panel and renders one
  row: name (tap → `cd-expand`, which opens the panel), value, status, mini
  buttons or chips. The rows:
  - climate: temperature, word · target, quick settings as icons;
  - climate-zone: room chips on czColour;
  - fan: Off/1/2/3;
  - purifier: PM2.5, Off/Auto/Sleep (plus the current mode);
  - CO: ppm, status;
  - cover: Up/Stop/Down;
  - security-zone: word, low batteries, light;
  - alarm: state, delay seconds, arm buttons;
  - light-control: the first top-level row's light, brightness, "N on", power;
  - device-health: names of stale devices.
  Other cards are hidden in compact.
- Section panel: `phone_start` (default compact) and `tablet_start` (default
  open). Phone means `innerWidth < 600`. The choice is stored in `localStorage`
  under `cd-panel:<user id>:<path>:<title>:<phone|tablet>` (per device and per HA user). `open_when` is a template that
  forces open while truthy. `collapsible: false` removes the ⌄. Always open in
  edit mode/preview. Section title: `collapsible` shows the ⌄ (`stc-toggle`
  event); with a `link`, the row navigates and only the ⌄ toggles.
- Mobile: every Church Drive card ran as its `-beta` type while testing and was
  switched back to the released types on release. `open_when` is
  set on Doors & Motion (door/tamper), Alarm and Security (pending/triggered), Air
  Quality (CO or PM2.5 > 75) and Fire Alarm (`binary_sensor.entrance_smoke_alarm_alarm_status`;
  the base station's safe mode reads "Disarmed" normally).

**Also in v0.19.0: panel layout on wider screens.**
- The panel's cards are in `.spc-cards`, a grid of `repeat(auto-fill, minmax(min(100%,
  card_width), 1fr))` (default 300, `0` = one per row).
- `match_height` (default on, off under 600px): the sections in a row are
  those in the same shadow root (`hui-section` elements) with the same top; each
  sits in its own wrapper in real HA, so siblings don't work. Items are
  top-level `hui-card`s, lined up by index: the kth panels share the tallest
  natural height, and the last panel in each section absorbs the rest so the
  sections end level. Natural height is the grid bottom + 12px padding − the
  panel top, which avoids feedback loops. Every panel computes the whole row and
  applies its own share. It re-runs on a body ResizeObserver and every 3s.
  First version only stretched the last panel and didn't find the neighbours
  (user screenshot 2026-09-28).
  Compact panels never stretch and aren't used as a height to match. A section
  ending in a compact panel isn't levelled (a collapsed Indoor Cameras had been
  stretched into an empty box). `_apply` fires `cd-panels-changed` on window so
  the whole row re-lines up at once.
  The nav bar's `hui-card` is skipped (it's only a spacer). The kth panels are
  lined up only among columns where k isn't the last item, and each column's
  last panel fills to the common bottom. Before this, Climate's single-panel
  column (Windows & Doors) was paired with Heating's first panel and never
  levelled, because the nav bar was the last item.
- Inside a panel, cards on the same grid row share a height (`align-items:
  stretch`, `.spc-cards > * { display:flex }` and `> ha-card { flex:1 }`). The zone
  card's light tile is pushed to the bottom (`margin-top:auto`).
- kitShell header: the title row wraps. On cards under 260px (container query)
  the title is 1.25rem and the status word drops to its own line, so the title
  isn't cut ("Front G…" in the user's screenshot).
- Mobile → Security, rearranged for the user (`max_columns: 3`):
  - [Alarm + Fire Alarm (card_width 0)] span 1;
  - [Outdoor + Indoor Cameras (card_width 220: 3 then 2 across)] span 2, bottoms
    level with Fire Alarm;
  - [Doors & Motion (card_width 200: 5 zones across) + nav bar] span 3, full width.
  - Phone order: Alarm, Fire Alarm, cameras, Doors & Motion.

**New in v0.18.0: Nav Bar Card** (`nav-bar-card`), live on every Mobile page. The user chose style B, the
floating capsule, from mock-ups (claude.ai/artifact/9bJeSLs2W7VjJaTV2ShZXF), over
splitting Mobile into separate dashboards.
- It's pinned to the bottom. It renders into `document.body` (class
  `church-drive-nav-bar`) so the sections layout can't clip `position:fixed`. It
  shows inline in demo/editor/preview. It leaves a 70px spacer where the card sits.
- Pages: `name`, `icon`, `path`, `color`, `color_template`, `alert_template` (a
  dot when not 0/off/false/empty). The active page is the longest path matching
  `location.pathname`. Navigation uses `kitNavigate` (pushState plus
  `location-changed`).
- On Mobile, `nav-bar-card` is the last card of every view. The Quick Actions
  view got the path `home`. Security takes its colour from the alarm template and
  gets a dot for doors open or tamper. Climate gets a dot for Device Health > 0 or
  the CO alarm. Cleaning gets a dot for `vacuum.gregg` error.
- Section panel/title `link` option: the Quick Actions panels (Security, Climate,
  Lights, Cleaning) link to their pages.
- `hide_tabs` (default on): while a pinned bar is on screen, it puts
  `style.cd-nav-hide-tabs` into `hui-root`'s shadow root. It's found by a shadow-DOM
  search from `home-assistant`, and hides `.toolbar ha-tab-group / sl-tab-group /
  paper-tabs / ha-tabs`. A module-level holder count, with a 400ms delayed removal,
  avoids a flash between pages. It's inline (so not hidden) in edit mode. It was
  only tested against a mock of HA's header: if the tabs still show on the phone,
  check the real element name in `hui-root`.
- Not done yet: jump-to chips on long pages.

**Also in v0.18.0 (the last release that needed a full restart for card changes):**
- Nudges only use quiet presets (`QUIET_PRESETS`: sleep, speed_1, low, silent,
  medium, speed_2), never auto/turbo/natural. On the v0.17.2 restart the purifier
  was nudged to `auto` (its first preset).
- The card version (`?v=`) is re-read on every Church Drive setup. From then on,
  **card-only releases should reload the Church Drive config entry
  (`01M3C9Z12M0W755GDTM455NFVB`) instead of restarting HA.** Full restarts are
  when the Philips devices fail to connect ("Failed to connect to host
  192.168.4.48") and come back stale. Python changes still need a restart.
- After the v0.17.2 restart (00:35, 2026-09-28) the fan was fine. The purifier's
  live feed died at startup, with runtime frozen at 0:11:42, and two nudges didn't
  revive it. It recovered by itself by morning. Don't reload the Philips
  integration: that gets stuck.

**v0.17.2: Device Health never reloads integrations; it nudges instead.**
On the v0.17.1 restart (00:24, 2026-09-28) the fan came back stale, showing Speed 1
with the runtime frozen at "4 days, 12:11:19". The logs say "Failed to connect to
host 192.168.4.48" at startup: the Philips integration misses the fan while HA
boots. Re-sending Speed 1 did nothing because HA already showed Speed 1. At +4 min
the automatic reconnect reloaded the Philips entry, which got stuck in
`failed_unload` (aiocoap `InvalidStateError` in `client.shutdown()`). The fan went
unavailable. The user doesn't want to press buttons. The fix:
- The automatic reconnect is gone. A **nudge** replaces it at 90 s, 10 min and
  30 min: the fan goes to its quietest other preset (`sleep`, or the first other
  one) for 5 s, then gets its last real reading re-sent. A real change makes the
  integration talk to the fan again.
- `health_fix` gains `action: nudge`. The card and banner **Fix now** nudges fans.
  The Reconnect button is removed (the manual `reconnect` action still exists
  but is never automatic).
- Recovery: the entry `01M0ZVDY6MWDBAJDE75VS2DFQF` was disabled, HA restarted,
  and the entry re-enabled.
- The fan was fine for months before. The stale state only shows on HA restarts,
  and 2026-09-27 had many restarts for releases.

**v0.17.1: visual tidy-ups.**
- **Fan card:** the dial is 84px, like the purifier, CO and thermostat dials. It no
  longer tints its background when on. Tints are kept for warnings only: purifier
  poor air, CO alarm, zone door open/tamper.
- **Alarm card:** the countdown ring's full length is the seconds left plus the time
  since `last_changed` (ignored if the clocks are 10+ minutes apart). A card rebuilt
  mid-delay used to refill the ring for the last ~15 s. The alarm's own attribute
  counts 60 → 1 smoothly. The alarm ring is still 72px; the user was asked
  whether to match it to 84px.

**New in v0.17.0: Security Zone Card** (`security-zone-card`), live on Mobile →
Security (released type) and demoed on Beta. The user chose it through several rounds of
mock-ups: A/B/C → C variants → compact C → "a card per zone" with "each battery
on its own row". Defaults: hourly bars, 12 hours.
- One card per zone: the title is the zone name plus a state word (Motion just
  now / Closed / Quiet / Open N min / Tamper). There's a 24-slot activity strip:
  motion indigo `#7986cb`, door amber, doorbell pink, tamper red, and a light-on
  band in faint yellow. Bars or ticks; 6/12/24 h. Press and hold (0.3 s) or hover
  to scrub, with the readout in the title line. Then a last-events line, each
  battery on its own row (name, level bar, %, amber below 25%) and an optional
  light tile.
- Colours: indigo `#5c6bc0`; amber (tint 10) while the door is open; red (tint 18)
  plus a warning line on a tamper, or on a door open while `alarm_entity` is armed.
- History comes from `kitStateHistory` (new in card-kit; `KitHistory` takes a
  loader). It reloads every 10 min, and state changes seen in between are
  appended live. Event entities count each distinct timestamp state as an event.
- Mobile → Security → Doors & Motion holds five `security-zone-card`:
  Front Garden (Hue `binary_sensor.front_door_sensor_motion` + Ring
  `event.front_door_motion`, doorbell `event.front_door_ding`, `light.front_light`,
  Doorbell 58% / Hue sensor), Entrance (`binary_sensor.front_door`, Ring
  `binary_sensor.motion_detector_39299`, tampers, Door contact / Ring sensor),
  Driveway (`event.driveway_motion`, Camera), Back Door, and Back Garden (Hue
  garden motion, `light.outside` Floodlight, Camera / Hue sensor). The panel's
  colour and summary templates follow the worst zone. The old five entities lists
  are gone (dashboard auto-backup has them).
- Design Presets → Beta has a demo Doors & Motion panel (motion / quiet / open /
  tamper-with-ticks).
- Still to check on the phone: hold-to-scrub on the strips (no page scroll), the
  light tiles, and whether the Front Garden bars read well.
- The front Hue sensor is very chatty (~350 motions a day, passing traffic).
  Bars handle it.

**v0.16.1: Device Health checks instantly.** After the first restart with
v0.16.0 the fan still showed off: the saved real reading was there (runtime
5d 7:46) but nothing flagged the stale one (4d 12:11). Now the counter check runs
the moment health starts (for states already in place), again on
`homeassistant_started`, on every state event and every minute's tick. It
no longer waits for the +2 min startup check. A stale device is re-synced
straight away (RESYNC_AFTER 0; reconnect still +4 min). Also fixed:
the fired `listen_once` unsub being called on unload ("Unable to remove
unknown job listener"), and select.py's settle re-check lambda running
`async_write_ha_state` off the event loop (now a `@callback` method).

**New in v0.16.0: Device Health** (plan: claude.ai/artifact/39ndvAG5WwTBwtSfKt2s3N).
Why: after restarts the Philips fan (philips_airpurifier_coap v0.37, entry
`01M0ZVDY6MWDBAJDE75VS2DFQF`) reconnects with an old "off" (runtime frozen at
"4 days, 12:11:19") and goes silent while really running. `update_entity`
doesn't help; any real command does. A manual `reload_config_entry` got stuck
(failed_unload → unavailable) and needed disable/enable + a restart.
- `custom_components/church_drive/health.py` (DeviceHealth): options
  `health_entities` (Configure → menu → Device health; the options flow is
  now a menu: scenes / health). Stores each entity's last real reading
  (Store `church_drive.health`), learns its usual gap. Checks: counter
  (`runtime`/`uptime`) went backwards (a reset is accepted after it counts
  up twice), stopped updating (counter devices only, 3x usual gap, ≥10 min),
  unavailable >5 min (`unknown` ignored: RF blind), startup check +2 min.
  Fixes, all automatic (the user's choice): refresh; +1 min re-sync last real
  state (fan/climate, reading <24 h old); +4 min reconnect the entry (only if
  loaded, max every 6 h). Health start is wrapped so a failure can't stop
  the cards loading.
- `sensor.church_drive_device_health` (state = count needing attention;
  `devices` attribute per entity: status, reason, since, last_heard,
  usual_gap, last_real, fixes). `church_drive.health_fix` (entity_id, action
  refresh/resync/reconnect).
- Cards: `kitHealthBanner` (card-kit) shows "Not responding since…" with the
  reason, last real reading and Fix now on the fan, purifier, CO, blind and
  climate cards (controls dim). New `device-health-card` (Fix now /
  Reconnect per stale device).
- Watched: the Climate-tab devices (fan, purifier, both Nests, CO reading,
  blind). Device Health card on the new admin-only **Manager** dashboard.
- Future: phone notification to Jamie only when something stays wrong.

**New in v0.15.1: flatter floor cards, fewer pop-ups, Nest limits** (mock-up
claude.ai/artifact/8QJHfHymWDSdL1BsH7cu2Z).
- Climate Zone card: rooms sit straight on the card (no shaded box each),
  separated by a thin divider. Rooms no longer open a pop-up, so press-and-hold
  on the graph isn't interrupted.
- Pop-up rule (the user's): only where HA's pop-up adds controls the card
  doesn't have. Kept: thermostat gauge, fan gauge, blind icon only if the cover
  supports set_position (feature 4; Hayley's RF blind doesn't). Removed: floor
  rooms, purifier PM2.5 gauge and filter bars, carbon monoxide gauge.
- Climate card graph: shaded comfortable band (dashed edges) and dotted
  humidity limits, like the zone card. New options `show_limits` (default on),
  `room_type` (CZ_TYPES, default living), `comfort_low`/`comfort_high`,
  `humidity_low`/`humidity_high` (40/60). Legend shows "▭ Living room 19–22°"
  and "┄ 40–60%". Mobile Heating: Downstairs `living`, Upstairs `office`.

**New in v0.15.0: temperature scale, limits, graph readout and smoothing**
(mock-up claude.ai/artifact/2G7Wj2We66Jq6r3jVK6CfN; the user chose a smooth
scale, a freezing colour, S3 = limits on the graph, and humidity limits).
- `climate-zone-card`: rooms take `type` (living/bedroom/office/hall/bathroom/
  kitchen, `CZ_TYPES`; guessed from name/icon) and optional `low`/`high`.
  `czColour()` interpolates stops around low/high; ≤0° is ice-white #e3f2fd with
  a "Freezing: pipes at risk" banner. Title = colour of the room furthest
  outside its range. Humidity: `humidity_low` 40, `humidity_high` 60,
  `humidity_dry` 30 per card; stays purple (paler dry, deeper humid; the user
  asked for purple shades, not amber). Graph: shaded comfortable band, line
  coloured by the scale (vertical SVG gradient), dotted humidity limits.
  The old `cold_below`/`cool_below`/`warm_from`/`hot_from` are gone.
- `card-kit`: `kitScrub` (press and hold 300ms on touch, hover with a mouse;
  line + dots + label with the time and raw readings; blocks scrolling while
  held; a hold doesn't count as a tap). `kitSmooth` (15-min time-weighted
  slots, then a 7-tap moving average) and `kitPath` (Catmull-Rom curve).
  `smooth_graphs` switch (default on) on the zone, purifier and climate cards.
- Mobile Climate zone cards have explicit room types (Living Room living,
  Entrance/Landing/Hayley's Landing hall, Hayley's Bedroom bedroom, Hayley's
  Office office).

**New in v0.14.2: no flicker on the fan and purifier cards.** The Philips fan
reports "on" before its speed, so tapping speed 1 briefly showed "On".
`KitPending` (card-kit) shows the tapped state straight away and holds it until
the device matches (numbers within 2) or 8 seconds pass. The Cooling panel's
summary template can still show "Fan on" for a moment.

**New in v0.14.0: five new cards for the Climate tab** (planned on the mock-up
page claude.ai/artifact/TV12cWZZUqRqLnymQ9rFGB). The user chose a "by job" page
with five groups: Heating, Cooling, Climate (temperature and humidity zone by
zone, one card per floor), Air Quality (leaf icon: purifier, X-Sense CO alarm)
and Windows & Doors (blind now; curtains and door/window sensors later).
- Shared `src/card-kit.js`: title + status word, 84px arc gauge, alarm-style
  tiles (`kitTiles`, grey until selected), hold-to-press tiles (1.5s),
  `KitHistory` (24h history via `history/history_during_period`, refreshed
  every 10 min) and `kitGraph` (one or two series, each on its own scale).
- `climate-zone-card`: `title`, `rooms` [{name, temperature, humidity, icon,
  note}], `show_graphs`, `show_humidity_graph`, `hours`, comfort thresholds
  (`cold_below` 18, `cool_below` 20, `warm_from` 22.5, `hot_from` 24.5). One
  graph per room (the user didn't want rooms stacked on one graph).
- `fan-card`: speeds from `speed_N` presets or percentage steps; two rows as in
  the mock-up (the user's choice); `temperature_entity` optional.
- `air-purifier-card`: PM2.5 bands 35/75/115 since v0.14.1 (were 12/35/55,
  which showed Fair at 18 while the Philips app said Good), editable per card; Philips allergen index 1-3 low,
  4-6 moderate, 7-9 high; `show_gauge`/`show_allergen`/`show_graph`/
  `show_modes`/`show_filters` (the user wanted every extra toggleable); sensors
  found from the fan's object-id prefix.
- `co-alarm-card`: entities found from the reading sensor's prefix
  (`alarm_status`, `device_status`, `battery`, `report_time`, `device_test`,
  `mute`); red with a warning when the alarm is on or ≥50 ppm.
- `cover-card` ("Blind Card"): Open/Stop/Close by supported features; RF blind
  (`assumed_state`, state unknown) shows the last command (this session only).

**New in v0.13.1: climate quick settings like the alarm buttons.** Grey
(white icon and text) until selected; the selected one is solid in its mode or
preset colour (Heat #ff7a2f, Cool #3aa0ff, Eco #4caf50, Off #8b919c). No more
gradient tiles or dimming. Also (dashboard only, no release needed): the Mobile
climate panels follow the mode via `color_template`: Quick Actions "Climate"
from `climate.downstairs` (grey off / green Eco / blue cooling / orange
otherwise); Climate "Heating" from both Nests (grey all off / orange any
heating / green all on-ones Eco / blue cooling / orange otherwise).

**New in v0.13.0: Climate Card** (`src/climate-card.js`, `custom:climate-card`).
Design F6 from the mock-ups (claude.ai/artifact/451vWxApsbondg1ThVLXx2 and the
feature mock-ups claude.ai/artifact/5mcJrR2c5hNz9UqicKjZDX). Built for the Nests
(`climate.downstairs`, `climate.upstairs`: heat/off, presets none/eco) and ready
for air con (fan/swing dropdowns appear when the entity has `fan_modes` /
`swing_modes`) and per-room valves.
- Layout: coloured title + status word; 84px display-only gauge (min→max,
  target tick and number, white room dot), humidity/outside beside it, room
  temperature big on the right; window-open chip; history; −/+ as two 48px
  tiles; full-width dropdowns (mode, preset, fan, swing); one row of up to 5
  quick settings; extra reading chips.
- Row toggles (`show_*`): on by default controls, mode, preset, fan, swing,
  quick; off by default `show_temperature_history`, `show_humidity_history`.
- History: `history/history_during_period` over 24h, reading the climate
  entity's `current_temperature` / `current_humidity` / `temperature`
  attributes (or `humidity_entity`), refreshed every 10 minutes. Both on →
  one graph, humidity `#b388ff`.
- Quick settings: `quick_settings` list (name, hvac_mode, preset_mode,
  temperature, icon, color); saved on first edit, Reset button, warning above 5.
  A plain mode/temperature tile clears the preset to `none`.
- Dropdown menus open in the card's flow (not floating) so panels can't clip
  them.
- **Deferred:** boost timer (needs a timer end from HA) and "next change"
  (needs a schedule).
- On Design Presets: the main tab has a Climate Card entry (description,
  demo card with both histories + real outside temperature, YAML) before the
  Light Control Card; the Beta tab has three demo climate cards (defaults,
  everything on, off).
- On real dashboards (2026-09-26), replacing the old HA tiles:
  - Mobile → Quick Actions "Climate" panel: one card for `climate.downstairs`
    (replaced the thermostat tile and the humidity trend tile) with humidity
    history, outside temperature, −/+ and quick settings; mode and preset
    dropdowns off, so its quick settings default to Off / Heat / Eco.
  - Mobile → Climate "Heating" panel: `climate.downstairs` and
    `climate.upstairs` (replaced both thermostat tiles), all default rows plus
    temperature + humidity history (one graph), outside temperature, and the
    `sensor.<room>_humidity` sensors as `humidity_entity`.
  - The Temperature and Humidity panels (statistics graphs for all the room
    sensors) were left as they were.

**New in v0.12.1: panel colour from a template.** Section Title
and Section Panel cards take `color_template` (a template giving a colour name
or code; overrides `color`, live via `render_template`). The title recolours its
icon and fires `stc-color`; the panel catches it and recolours its background
(0.6s fade). Used on Mobile → Quick Actions "Security" and Security → "Alarm"
with the alarm card's colours: disarmed green, home blue, away red, night
#7e57c2, arming orange, entry delay deep-orange, triggered red.

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
  3. **Card-only release** (nothing under `custom_components/` changed except the
     built bundle and the manifest version): reload Church Drive instead of
     restarting, with `ha_call_service('homeassistant', 'reload_config_entry',
     data={'entry_id': '01M3C9Z12M0W755GDTM455NFVB'})`. Setup re-reads the
     version, so `?v=` updates (since v0.18.0). **Python changes** need
     `ha_restart(confirm=True)`. That call returns a Cloudflare 502 because HA goes
     down mid-request, which is expected; wait about 3 minutes. Every full restart
     risks the Philips fan and purifier coming back stale (Device Health re-syncs
     them), so batch Python changes and avoid restarts at night.
     - Waiting: foreground `sleep` is blocked and `timeout N true` returns at once.
       Use `timeout 180 tail -f /dev/null` (Bash timeout above 180000 ms).
  4. Verify: the `ad4dc52d…` resource shows `?v=X.Y.Z`, and system logs for
     "church" are empty (apart from Device Health's "looks stale" warnings after
     a restart). Check `sensor.church_drive_device_health` goes back to 0.
  5. Switch any `-beta` card types on real dashboards (tested there before the
     release) back to the released types.
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
    It's pinned to `3533a28` (the v0.23.0 merge).
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
- A panel can follow a state with `color_template` (v0.12.1), e.g. the two
  Security panels follow the alarm.
- Open/compact: the +/− ring or the title row toggles; panels slide (v0.21.0). Phones
  start compact, tablets open; each device remembers per signed-in user; `open_when`
  opens a panel while a template is true.
- `link` gives a "Go to …" footer button (v0.21.0).
- Inside an **Auto Layout Card** (v0.20.0) a panel's `full_width` (auto/yes/no) and
  `priority` (auto/controls/info) apply, and its own height matching is off.
- Colours in use: security green (live: alarm colours), lights amber (garden green), climate
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
  - **Mobile** (`dashboard-mobile`): each tab is one full-width section holding an
    Auto Layout Card (v0.20.0) with its section panels in phone order, then the nav
    bar (theme Mushroom Shadow). Bracketed groups below are the old per-column
    sections; Auto Layout now arranges them. There are no hand-set `card_width` or
    `full_width` values; Doors & Motion (and Outdoor Cameras on narrower screens) go
    full width automatically.
    - **Every page** ends with a `nav-bar-card` (v0.18.0): Home · Lights ·
      Security · Climate · Cleaning, floating at the bottom, HA's tabs hidden.
      The Quick Actions view's path is `home` (`/dashboard-mobile/home`).
    - **Quick Actions:** its Security, Climate, Lights and Cleaning panels have
      "Go to …" footer buttons to their pages (v0.21.0). [Security (alarm colour, alarm state) + Climate (colour from
      `climate.downstairs`: grey off / green Eco / blue cooling / orange;
      downstairs °C · action; a Climate Card since v0.13.0)] [Lights (amber, "N rooms on" over Kitchen /
      Living Room / Middle Floor)] [Cleaning (blue, state · battery)]. Header
      "Hello {{ user }}".
    - **Lighting** (max 4 columns; the user split it into four sections): Ground
      Floor (Kitchen, Living Room, Entrance), Middle Floor (Hallway, Second
      Bedroom, Spare Bedroom), Top Floor (Landing, Office, Hayley's Bedroom,
      En-Suite), all amber with "N rooms on"; Garden (green, On/Off; Patio
      Lightstrip + Garden Spotlight); Front Garden (green, On/Off; `light.front_light`,
      area `front`, v0.20.0). Row names and `phu:` icons are overrides.
    - **Security:** [Alarm (alarm colour) + Doors & Motion (indigo / amber door
      open / red tamper; "Doors closed", "Back Door open", "Tamper"; five Security
      Zone Cards (v0.17.0): Front Garden, Entrance, Driveway, Back Door, Back Garden)] [Outdoor
      Cameras + Indoor Cameras (blue-grey)] [Fire Alarm (red, safe mode)].
    - **Climate** (rebuilt with the v0.14.0 cards, 2026-09-27): [Heating (the
      user's own climate cards: quick settings Off/Heat/Eco, dropdowns off;
      colour from both Nests) + Climate (colour = the room furthest outside its
      room-type range (v0.15.0; ice-white if any room is at 0° or below); "Avg ° · Outside °"; Ground Floor, Middle Floor
      and Hayley's Floor zone cards)] [Cooling (Bedroom Fan card; cyan when on,
      grey off) + Air Quality (leaf; Air Purifier card + Carbon Monoxide card;
      colour and summary from PM2.5 with the Philips app's bands 35/75/115, red
      if the CO alarm is on)] [Windows & Doors
      (Blind card for `cover.roller_blind`)]. The old Temperature/Humidity
      lists and graphs and the fan/purifier/blind tiles are gone.
    - **Cleaning:** one Cleaning panel (blue, state · battery).
  - **Hayley** (`dashboard-hayley`, view path `home`), Auto Layout (2026-09-29):
    - Security: alarm-panel card, alarm colours.
    - Lights: Hayley's Bedroom, Kitchen Spotlights, Living Room Ambience and Middle
      Floor light cards; "N rooms on".
    - Bedroom: fan-card and cover-card for the fan and `cover.roller_blind`; cyan
      when the fan is on.
    - Cleaning: vacuum tiles and the Gregg message card.
  - **Living Room Panel** (`living-room-panel`, wall tablet, max 2 columns, keeps its
    tabs, no nav bar), Auto Layout (2026-09-29):
    - Living Room tab (path `living-room`): Lights (Ceiling Light, Shelf Table Lamp,
      TV lightstrip, TV Table Lamp); Cleaning; Back Door (security-zone card; opens
      and turns amber or red when the door is open or tampered); Weather (hourly
      forecast).
    - Kitchen tab: Lights (Ambience + Spotlights); Heat Alarm (tiles; opens on
      alarm).
  - **Manager** (`dashboard-manager`, admin-only, v0.16.0): System view with
    a Device Health section panel (green / amber from
    `sensor.church_drive_device_health`) holding the Device Health card.
    Meant for the user only (require_admin can't limit it to one account).
  - **Battery Status** (`battery-status`), Auto Layout (2026-09-29):
    - Ground Floor, Middle Floor and Hayley's Floor panels holding the per-room
      battery-zone cards.
    - Summary "N low · Lowest X%". Colour green, amber under 40%, red under 20%.
    - `phone_start: open`.
  - **Alarm** (`alarm-panel`, hidden from the sidebar), Auto Layout (2026-09-29): Alarm
    (alarm-panel card, not collapsible) and Activity (logbook; "N minutes ago").
  - **Design Presets** (tabs: main, Beta, Scene styles, Scene builder).
  - **Climate cards:** Mobile → Quick Actions (Downstairs) and Climate →
    Heating (Downstairs, Upstairs), Design Presets main (demo), three demo
    cards on Beta.
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
- `light.outside` is the Back Garden zone card's Floodlight tile on Mobile's Security tab.
- **Kitchen:** room `light.kitchen` (8 lights); zones `light.kitchen_spotlights`
  and `light.kitchen_ambience` (2 lights).
- **Hayley's Bedroom:** room `light.hayleys_bedroom`, zone
  `light.hayley_s_bedroom_ambiance`, also a `light.hayley_s_bedroom_main` group.
  "My Boy Hugo" (`light.my_boy_hugo`) is unavailable, and the bridge still counts
  it as on, so the Hue room reports "on" with every bulb off.
- **Living Room:** room `light.living_room`, zones `light.living_room_ambience` and
  `light.living_room_table_lights`.

## Open items

- **Future (not now): our own Android app.** The user wants home-screen widgets
  showing the panels, and later the same app for Android-based satellite
  devices (wall tablets, voice satellites). Lovelace cards can't become Android
  widgets (the HA app's widgets are native: template, entity, button, camera,
  media, to-do), so this is a separate native app, e.g. Jetpack Glance widgets
  talking to HA over its websocket API. The panel summaries and colour
  templates, and the `church_drive/*` websocket commands and
  `church_drive.apply_scene`, can be reused as its data source.
- **Climate card, deferred:** a boost timer (needs HA to expose when boost
  ends) and a "next change" line (needs a heating schedule in HA). Also later:
  Bosch-style air con and per-room smart valves; the card already handles
  fan/swing modes and a separate humidity sensor.
- **Offered, not yet asked for:** a small ↗ in a closed panel's row to reach its page
  (the Go to footer only shows while the panel is open); sending the stale-device
  alert to the Pixel too.
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

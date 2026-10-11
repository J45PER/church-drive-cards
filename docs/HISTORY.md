# Church Drive: release history (archive)

Moved out of HANDOFF.md to keep it short. Newest release notes first; older detail is below. See CHANGELOG.md for the one-line index.

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
      person on their own devices. It sends through `script.church_drive_notify_person`
      ("Church Drive: notify a person"; fields person / title / message / tag / link),
      which has one plain notify step per phone: Jamie's iPhone and Pixel (not the iPad
      or the watch), Hayley's Pixel 9, Diane's phone. The first step, "People", holds
      each person's list and switches.
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
- **v0.34.0 (released 2026-10-03, restarted): mode icons in one list.** The icon each mode gets (a
  fan's Sleep, a thermostat's Eco, the Zappi's Stop, a blind's Open, the CO alarm's Mute) is kept once
  in the integration (`icons.py`: built-in icons, the user's changes in storage `church_drive.icons`)
  and read over the websocket `church_drive/icons` (changed with `church_drive/icons/set`, admin only;
  it fires the event `church_drive_icons_changed`). The cards ask `iconFor(group, key, fallback)` in
  `src/icon-library.js` (fallback = the icon the card always had, used until the list arrives and
  without the integration), the new **Icon Styles card** (`src/icon-styles-card.js`, on Manager) changes
  them, and the Android app reads the same list (`IconMap` in `ModeIcons.kt`), so one change shows
  everywhere. Groups and keys: `fan` (off, speed_low/medium/high, natural, sleep, auto, turbo, eco,
  oscillate, other...), `purifier`, `climate_mode`, `climate_preset`, `charger` (stopped, eco, eco+,
  fast), `cover` (open, stop, close), `co` (test, mute). A quick setting's own `icon` on a thermostat
  card still wins. Tests: `test/icon-library.test.mjs`.
- **v0.36.0 (released 2026-10-03, restarted): People manager.** Manager's first panel is
  People (`people-manager-card`): picture, places, phones, cars, alerts, to-dos, arrivals per
  person; car owners kept by the integration (see the cars notes above).
- **v0.36.1 (reload-only): Eco switches the thermostat on.** A thermostat's Eco is a preset, which only
  takes hold while it is on, so a quick setting with just a preset (Eco) on a thermostat that is *off*
  now sets Heat first (Heat, else Auto, else the first mode that isn't Off), waits for it, then sets the
  preset: one tap, not Heat then Eco. The steps live in `src/climate-quick.js` (`quickPlan`, tested in
  `test/climate-quick.test.mjs`); the Android app does the same (`ecoSteps` in `ClimateData.kt`).
- **v0.37.0 (released 2026-10-04, restarted): Device access.** Manager › People › Devices limits a
  device to some people; cards and panels hide it from everyone else (see the Device access notes above).
- **v0.38.0 (released 2026-10-04, restarted): Phone locations.** People manager › Phones has a
  Location switch per phone and flags a location that's stopped updating (see the Phone locations notes above).
- **v0.38.1 (reload-only): car and charger overrides.** Car card says when a plugged-in car's figures
  are from, with Refresh (wakes the car); charger card has Unlock and Charge now / Pause while a car's plugged in.
- **v0.38.2 (reload-only): the same in compact.** The Home page's Car charging panel is compact on phones, which
  showed only the modes: now, while a car's plugged in, the charger's compact row has Unlock (when locked) and Charge now /
  Pause in the modes' place (tap its name for the modes), and the car's row adds "Updated HH:MM" and Refresh.
- **v0.35.0 (released 2026-10-03, reload-only): Car card.** Cars moved off the charger card
  onto `custom:car-card`, which can give cars to people (see the cars notes above).
- **v0.33.1 (released 2026-10-03, reload-only): charger card shows the cars.** Each car from
  Stellantis Vehicles / VW Group Connect (battery bar and %, range, Plugged in / Charging);
  `show_cars` in the editor, `cars:` to override.
- **v0.32.2 (released 2026-10-02, reload-only): media card.** A TV asleep in standby
  (unavailable, e.g. the LG) shows as Off; its power button runs `script.<player>_wake` when
  there is one (Wake-on-LAN); "Turning on…/off…" shows straight away until the TV reports
  (at most 45 s).
- **After v0.32.1 (live config, no release):** garden and front door sensors are motion only
  (their temperatures hidden; the Climate panel's Outside zone removed and "Outside" and the
  frost alert use `weather.forecast_home`). The alarm-mode notification names the HA user who
  changed it (`trigger.to_state.context.user_id` → person); keypad / Ring app changes wait up
  to 20 s for Ring's `lastDisarmedBy` / `lastArmedBy` (it lags the state, and HA's own changes
  show as Hayley, the Ring account). Zappi connected and car alerts written (see above).
- **v0.32.1 (released 2026-10-02, restarted): map switch and nearest zone.**
  - Home Assistant's own maps (person more-info, zones page) get a Satellite / Street
    switch top right (`addSwitch` in `src/map-style.js`, a Leaflet control). It shares
    the choice with the zone map card (`cd-map-style`).
  - A person's place picks the zone whose centre is nearest when their phone is in
    several (`_nearest_zone` in `people.py`). HA itself picks the smaller zone when GPS
    accuracy is wide (Diane at the Banking Hub showed as the Post Office). HA's own
    person state still uses HA's choice.
  - The beta map debug logging is gone.
- **v0.32.0 (released 2026-10-02, restarted): Google maps through Home Assistant, on Home Assistant's own maps.**
  - **HA 2026.9's maps are MapLibre** (Leaflet only as a fallback without WebGL2), and its
    bundled Leaflet never sets `window.L`, so the old `window.L` patch never fired on real
    pages: HA's maps were never swapped. Now `map-style.js` hooks `<ha-map>`
    (`customElements.whenDefined`, patching connected/disconnected, plus a shadow-DOM walk
    for maps already shown) and watches `el._engine._map`:
    - MapLibre: hides the style's background and vector layers, adds raster sources/layers
      `cd-raster-N` under everything else (zones and people stay on top), and re-applies on
      `styledata` when HA swaps the style (dark mode).
    - Leaflet: `setUrl` on the base tile layer, extra label layers for Esri.
    - Esri satellite/street without a Google key; `ha` style restores HA's map.
  - **Tile proxy (`maps.py`):** HA's page has `<meta name="referrer" content="same-origin">`, so
    a referrer-locked Map Tiles key can't work from the browser. `MapTileView` at
    `/api/church_drive/maptile/<style>/<z>/<x>/<y>?t=<token>` (no auth; the token comes from the
    signed-in `church_drive/maps` command and changes each start) fetches from Google with HA's
    external URL as Referer, caching sessions per style. `church_drive/maps` now returns
    `tile_url` (the key never reaches the browser).
  - Keys (2026-10-02): Map Tiles = "Maps Platform API Key" …7cx0 (websites: Nabu Casa,
    homeassistant.local:8123, 192.168.4.136:8123); Places = "Church Drive search" …bzyU (no app
    restriction, Places API (New) only). The …bReuE key isn't in the project; nothing uses it.
- **v0.31.3 (released 2026-10-01, restarted): clip player controls.**
  - Under a playing clip (`.ce-bar` in `camera-events.js`): play/pause, time, a range
    timeline to scrub, length, Download and Full screen.
  - Download fetches the clip once it starts playing, so the tap can open the share sheet
    straight away (`navigator.share` with a File; iPhone: Save Video), else an `<a download>`.
  - Full screen is the media box on Android/PC (pan and zoom still work), and
    `webkitEnterFullscreen` (Apple's player) on iPhone. The user tested it on both phones.
- **v0.31.2 (released 2026-10-01, restarted): old camera events removed.**
  - Once, on start (`cleared_old` in the store): events without a `whole` clip (all from
    before v0.31.1, which could never play) are removed with their pictures. The user asked
    for the old ones to go. First real clip after v0.31.1 (front door live view 19:48)
    came in as `hevc` and was converted.
- **v0.31.1 (released 2026-10-01, restarted): camera clips play on phones; live view starts muted.**
  - **Clips were only the first chunk:** `_async_clip` used `resp.content.read(MAX_CLIP)`,
    which returns whatever has arrived, so each saved .mp4 was a few KB (phones: "format
    not supported" / DEMUXER_ERROR_COULD_NOT_OPEN). Now `iter_chunked` to the end. On start,
    clips saved before (no `whole` flag) are deleted; their pictures stay.
  - **Ring records HEVC:** phones' HA apps (iOS WKWebView, Android WebView) played the
    sound over a black picture. `_async_remux` converts HEVC to H.264 (libx264 veryfast
    crf 26, max 1280 px, aac 64k, faststart) with HA's ffmpeg, one at a time
    (`_convert_lock`), `nice 10`. If that fails it keeps HEVC tagged hvc1. Other codecs are
    just remuxed with the index first. `codec` is saved per event. Found with beta-only
    logging of the `<video>` state from both phones (since removed).
  - **Card:** clips start inside the tap (iOS needs a gesture), fall back to muted, a big
    play button when paused, the event picture as poster, and a red "Couldn't play this
    clip (…)" on error. Live view (`ha-camera-stream`) starts muted (the speaker unmutes)
    and the tag says "Connecting…" until a video is actually playing ("Still picture" if
    it never does).
  - Local HA address for the Map Tiles key's website list: http://192.168.4.136:8123/*.
- **v0.31.0 (released 2026-10-01, restarted): zone map fix, panels fill their width, notification boxes, Google maps, iPhone pop-ups.**
  - **Zone map was blank:** Leaflet sets `position:relative` on a map made before it's on
    the page, so `.zm-map` was 0 px high. `.zm-map` is `position:absolute !important`
    with full size, and the map fits the zones once it has a size (`_wantFit`).
  - **Panels fill their width:** `section-panel-card` uses `auto-fit` (was `auto-fill`),
    so one or two cards share the whole row instead of leaving empty tracks (Camera
    links was cut off at "L…"). `places-card` too.
  - **Notifications card:** each group, and Phones, is its own box (`.nc-boxes`, CSS
    columns of 290px), so it's about half as tall on laptops. Manager's to-do settings
    moved from the Notifications panel into their own "To-do settings" panel.
  - **Google maps (`map-style.js`, `zone-map-card`, `__init__.py`, `config_flow.py`):**
    - Options step "Google maps": `google_tiles_key` (Map Tiles API; browsers use it)
      and `google_places_key` (Places API (New); server only).
    - `church_drive/maps` gives the tiles key and whether Places is set;
      `church_drive/maps/search` runs Places Text Search near Home (key stays in HA).
    - Browsers make Map Tiles sessions (satellite + `layerRoadmap`, and roadmap), kept
      in localStorage `cd-gmap` for about two weeks. `googleTiles(style)` gives the URL;
      HA's own maps start on Esri and switch (`cd-map-google` event, `setUrl`) once a
      session is ready. A refused key logs a console warning and stays on Esri.
    - Search drops a pin (`zm-pin`); tapping it starts a new zone named after the place.
    - Keys set on 2026-10-01. The Map Tiles key was refused with API_KEY_SERVICE_BLOCKED
      (its API restrictions need Map Tiles API ticked) and needs website restrictions for
      the Nabu Casa address, homeassistant.local:8123 and the local IP. The Places key had
      no restrictions yet; it should be limited to Places API (New), application
      restrictions None.
  - **iPhone pop-ups:** the body was `flex:1` (basis 0), which iOS WebKit sizes as
    empty, so pop-ups that fill in later (cameras, events) were only header-high and
    sat at the bottom. Now `flex:1 1 auto`, and the phone sheet is pinned with
    `inset:auto 0 0 0` instead of an auto margin. Found by logging the real sizes from
    the user's iPhone (iOS 26.6.2, HA app 2026.9.1) into the system log.
  - **Phone sheet grab bar:** drag the bar or title down to close (over 120px, or a
    flick over 50px), up to open to 90dvh.
  - Event clip thumbnails use `mdi:play` instead of ▶ (iOS drew it as an emoji).
- **v0.30.3 (released 2026-10-01, restarted): start-up no longer waits for device health fixes.**
  - `health.py` runs its automatic refresh, re-sync and nudge as background tasks
    (`_background`), which HA's start-up doesn't wait for. Each device call now has a
    60 s limit (`CALL_TIMEOUT`).
  - Before this, a Philips fan re-sync after a restart held start-up for about 5
    minutes, and Church Drive's entities only appeared after that.
  - Tested against HA core with a fan service that never answers.
- **v0.30.2 (released 2026-10-01, restarted): Locations Edit/Save, zone map, satellite maps everywhere, zone location requests, "Live view".**
  - **Satellite maps** (`src/map-style.js`, `installMapStyle()` in index.js):
    - A `window.L` setter catches Home Assistant's Leaflet. Its UMD build always
      sets `window.L`.
    - `TileLayer.initialize` swaps cartocdn, OSM or stadia URLs for Esri
      World_Imagery (plus World_Transportation and World_Boundaries_and_Places
      label layers, added in `onAdd`) or World_Street_Map.
    - Per browser: localStorage `cd-map-style` = satellite (default), street or
      ha. Guarded by `window.__cdMapStyle`, because the release and beta bundles
      both load.
  - **`zone-map-card`** (Manager › Locations, above the person cards):
    - Uses bundled Leaflet ESM (`leaflet` dependency; the ESM build doesn't
      touch `window.L`). Leaflet CSS is imported as text (esbuild loader).
    - The bundle is now whitespace-minified (`minifyWhitespace`) to stay about
      700 KB.
    - Zones are matched to `zone/list` ids by name. Saving uses
      zone/create/update/delete, or `config/core/update` for Home.
    - Search uses Nominatim (gb). This environment can't reach Esri or
      Nominatim, so it was tested in a browser with tiles blocked: the hook
      swap, drawing, select, resize, add and the save message.
  - `places-card` shows text with an Edit button. Edit gives the zone and name rows,
    ✕ and + Add a place, saved together with Save (`_drafts`); Cancel drops them.
  - `people.py`: a zone added, moved or resized sends `request_location_update` to
    every phone 10 s later (`_ask_locations`). Phones only check zones on a new
    location. Hayley showed Away inside Frasers Group because the zone (15:16) was
    newer than her last location (15:09).
  - `events.py`: an on_demand recording with no link waiting is kind `live` (someone
    watched live). Stored "linked" entries without a trigger are relabelled on load.
    The viewer has a "Live view" chip.
- **v0.30.1 (released 2026-10-01, restarted): clip download fix.** Ring's
  `sensor.<x>_last_activity` attribute `created_at` is a datetime, not text, which broke
  `_async_clip` (`_when()` now takes either). Start-up note: Church Drive's entities
  only appear once HA says it's started, which can take about 5 minutes here. The
  Philips fan resync holds up start-up (an existing issue).
- **v0.30.0 (released 2026-10-01, restarted): camera events, camera links and places.** Manager gained Locations and Camera links panels; Security's camera and zone cards open the events viewer.
  - **Camera events** (`events.py`, `CameraEvents`):
    - Saves a picture 8 s after each Ring `event.<x>_ding/_motion`, from
      `camera.<x>_snapshot` (else the live view).
    - When the Ring camera's `last_video_id` changes, it downloads `video_url` and
      pairs it with the event of the same kind within 4 minutes (kind from
      `sensor.<x>_last_activity`).
    - Files go in `/media/church_drive/events/<x>/YYYYmmdd-HHMMSS_<kind>.jpg|mp4`.
      They're kept 5 days and capped at 3 GB (oldest first), tidied hourly.
    - Store `church_drive.camera_events`. Websocket `church_drive/camera/events`
      returns `{camera}` with signed links via `EventFileView`
      (`/api/church_drive/events/<x>/<file>`). `church_drive/camera/settings`
      (admin) sets keep_days, max_gb and folder (for a NAS later).
    - Backups don't include `/media` (automatic backups have no
      `include_folders`), so clips aren't backed up.
    - Tested with a fake camera and download against HA core 2026.2 (scratch venv).
  - **Events viewer** (`src/camera-events.js`, `openCameraEvents`):
    - Opened from the camera pop-up's Events button, and by tapping a
      security-zone card (its camera is the `camera` option, else found from its
      ding/motion event entities or its name).
    - The camera pop-up no longer lists events. `stageMedia()` (drag and zoom) and
      `liveElement()` are shared from that file.
    - `popup.close()` now returns a promise, so a second pop-up can open straight
      after.
  - **Camera links** (`events.py`, plus `src/camera-links-card.js` on Manager):
    - Stored as `links {disarmed|home|away: {trigger_entity: {cams: [base], secs}}}`.
      The mode comes from the first alarm panel (`ALARM_MODE`: night counts as
      home; pending and triggered count as away).
    - A trigger is an event entity changing state, or a binary_sensor turning
      on. The linked camera's `switch.<x>_live_stream` (ring-mqtt) turns on for
      `secs`, at most once per `cooldown` (120 s) per camera. The camera's own
      trigger is skipped.
    - A "linked" event is logged with its `source`, and paired with the
      recording whose last_activity category is on_demand.
    - Clip thumbnails now come from 1 s into the clip via HA's ffmpeg (Ring
      depends on ffmpeg). The snapshot is the fallback.
    - Seeded with what the user had in Ring: front_door motion and ding →
      driveway 30 s in every mode. They need to turn Ring's own Linked Devices
      off in the Ring app.
    - Websocket `church_drive/camera/links` and `.../links/set` (admin);
      `cooldown` is set in camera/settings.
    - Tested against HA core (switch calls, cooldown, per-mode). Not yet tested
      against real Ring: whether a ring-mqtt live view actually produces a Ring
      recording and a new `last_video_id`.
  - **Places:**
    - `people.py` stores `places {person: [{zone, name}]}`. `people()` gives
      `place` (Home, their name for the zone, the zone's name, Away, Unknown),
      `zone` and `places`; these also go into `sensor.church_drive_people`.
    - Websocket `church_drive/people/places` (admin).
    - `places-card` (Manager's Locations): one card per person, with Home
      automatic, zone and name rows, and + Add a place.
    - The people card shows `placeText()`, e.g. "At work · Ashfield School".
    - Zones are made by the user in /config/zone; Ashfield School exists.
      Diane's three post offices are to come.
- **v0.29.2 (released 2026-10-01, restarted): nav bar icons shrink to fit.** In
  icons-only mode each item was a fixed 42px, so eight Mobile pages ran under the
  back-to-top button on a phone. `_fit()` now sets `--nb-sz` to the bar's width shared
  between the pages, from 28 to 42px (33px on a 390px iPhone, 29px at 360px).
- **v0.29.1 (released 2026-10-01, restarted): the camera pop-up video fills its 16:9 box at the video's own shape.**
  - The Front Door is a Battery Doorbell Plus with square head-to-toe video, so it was
    letterboxed before.
  - Drag to look around; pinch, scroll or double-tap to zoom (up to 4×). Sound has its
    own button. This is `_stage()` in `camera-card.js`.
  - The shape comes from the tile picture first, then the playing `<video>`, found
    inside the player's shadow roots.
  - Measured with clientWidth/Height, because the pop-up opens with a little zoom.
  - Not de-fisheyed: that would need WebGL. The user hasn't asked for it yet.
- **v0.29.0 (released 2026-10-01, reload-only): Camera Card** (`src/camera-card.js`).
  - The user chose tile style A (picture with name and age badge). Tapping a tile opens
    the shared pop-up (`popup.js`).
  - Pictures come from ring-mqtt's `camera.<x>_snapshot` (attributes `timestamp` and
    `type`: motion / ding / interval) or the Ring integration's `camera.<x>_live_view`
    (a frame from the last recording, often 12–24 h old; its time comes from
    `sensor.<x>_last_activity`). The newer one wins.
  - Refresh: while the tile is visible and the picture is older than `refresh_after`
    (60 min), the card presses `button.<x>_take_snapshot`. The button's state (its
    last press) keeps this to once per hour for everyone. There's no fixed timer on
    battery cameras.
  - Battery cameras are Front Door (Battery Doorbell Plus), Driveway and Garden. Their
    ring-mqtt snapshot mode is Motion + Ding / Motion / Motion (Garden changed from
    All with an hourly timer). Entrance and Living Room are plugged in and keep All
    with a 30 s timer.
  - Pop-up live video: `webrtc-camera` when `talk_stream` is set, else
    `ha-camera-stream`, else a live picture-entity. Talk sends the microphone by
    re-creating webrtc-camera with `media: video,audio,microphone`.
  - **Talk is not set up yet.** The user is creating a "Church Drive" Ring account
    (Hayley is the Ring admin; HA currently signs in as Hayley, so alarm changes
    from HA show as her). Then:
    1. Hayley shares the location with it.
    2. Re-sign in the Ring integration and ring-mqtt (app `03cabcc9_ring_mqtt`) with it.
    3. Sign in go2rtc (app `03cabcc9_go2rtc_hevc_fix`, 1.9.14, web UI → Add → Ring)
       and add a `front_door` stream.
    4. Set `talk_stream: front_door` on the doorbell's camera card.
    Two-way audio on Ring through go2rtc is untested here.
  - Security's Outdoor and Indoor Cameras panels now use `custom:camera-card`.
- **v0.28.0 (released 2026-10-01, restarted): Auto Layout fills blank space; Manager on Auto Layout.**
  - The layout arithmetic is in `src/arrange.js` (tests: `test/arrange.test.mjs`).
    `arrange()` tries each automatically wide panel three ways: across the page below
    the columns, across the columns beside the tallest one (a "span", under the
    shorter columns), or in a column. It picks the shortest page. Cost counts blank
    space inside panels (a zone alone on its last row) and gaps under short columns
    that stretching can't fill. A wide panel in a column costs +40.
    `balance(heights, k, gap, keep, spanH)` handles the span case.
  - Panels estimate their size at other widths: `_fitAt` / `_heightAt` / `_emptyAt`
    in `section-panel-card.js`.
  - Order: controls first, then cameras (`isCamera`, a panel of only `camera.*`
    pictures, or `priority: camera`), then info. Security's Safety panel is
    `priority: info`, so the order is Alarm, cameras, then Safety (the user's
    choice).
  - Camera panels use a grid with worked-out spans (`_flow`), so a camera alone on
    the last row fills it. Two cameras that would stack count as wide.
  - New `full_width: wide` (editor: "Wide (two columns or more)"): never put in one
    column. Manager's Notifications panel uses it.
  - The layout keeps adapting while pictures load (margin 24px, 60px after 4s). It
    only freezes once a panel is opened or closed after the page has loaded
    (`_touched`). Plans are stored under a `v4|` key.
  - Manager is now one section holding an Auto Layout card: Who's home, Device
    Health, Notifications (wide), Automatic to-dos.
  - The user's laptop scales the display, so its columns are about 350 CSS px even
    though they look like 525: two cameras don't fit side by side there.
- **v0.27.1 (released 2026-10-01, reload-only): Notifications table's All column ticks and unticks everyone; ticking the last person turns All on; solid grey for ticks through All.**
- **v0.27.0 (released 2026-10-01, reload-only): Devices page, media/system/safety/people cards, cheap-rate view, Outside zone, grey ticks under All.**
- **v0.26.0 (released 2026-10-01, needs a restart): people and notifications engine.**
  `people.py`/`kinds.py`, `church_drive.notify`, `sensor.church_drive_people`, the
  `church_drive/people*` websocket commands, the `church_drive_todo_changed` event,
  `notifications-card`, `my_list`/`my_name` template variables and Task List
  `entity: mine`. On first start it carried the old `input_boolean.to_do_*` switches over.
  See "In progress" at the top for what's wired up so far.
- **v0.25.0 (released 2026-10-01, reload-only): page header lines, forecast, to-do summary; Task List card with repeating tasks; #panel jumps.**
  - See "Page header lines and the cleaning schedule" and "Repeating tasks" below.
  - On release the Mobile dashboard went back to release types. The Tasks dashboard's
    To-do view moved to `task-list-card` too (My to-do, Shared, Cleaning), matching
    the Mobile To-do page.
- **v0.24.2 (released 2026-09-30, reload-only): Auto Layout visual editor fixed.**
  - The editor threw on its first `hass` (see the conventions section), so every
    Auto Layout card was YAML-only. Released with the HA-side fixes for visual editing
    and for repeat notifications after a restart.
- **v0.24.1 (released 2026-09-30, reload-only): Octopus violet for gas.**
  - The Gas card uses `OCTO_VIOLET` (#7b61ff). On the Mobile Energy view, the
    Electricity and Gas panels use the flat `phu:octopusenergy` icon.
- **v0.24.0 (released 2026-09-30, reload-only): acrylic, frosted cards, scroll room, To-do ordering, purifier graph, Energy.**
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
    - Air purifier PM2.5 graph coloured by level (user picked "A" from
      https://claude.ai/artifact/XNN42H7nhgtjN3svHSnnJX, with smooth blends):
      - `kitGraph` series take `colorAt(v)`: a 21-stop vertical gradient over the
        graph's own scale, as in the climate room graphs.
      - `kitBlend(v, anchors)` interpolates the colours. `apScale` anchors each
        band's colour mid-band: green at 60% of good_max, amber mid-Fair, orange
        mid-Poor, red above; the bands follow the card's own settings.
      - The scale still fits the readings (the user didn't ask to change it).
      - Hover/hold: the dot follows the line colour (`colourOf: colorAt` passed to
        `kitScrub`, as in climate zone) and the label adds the band, e.g.
        "PM2.5 93 µg/m³ · Poor".
    - Header widget To-do button: no "›"; the text matches the icon's lilac
      (#b39ddb), reading "+N more" or "To-do"; 22px icon; aria-label.
  - On release `-beta` was stripped from Mobile again. Acrylic mock-up:
    https://claude.ai/artifact/Y9kej1q5Hxpggozpc8CkmV
- **Energy (v0.24.0, user's request):**
  - Octopus Energy (BottlecapDave integration, entry `01M3SXJ58FATC6QSNR4EH3X8PG`,
    account A-26769B84) is on **Octopus Go**: 4.99p from 00:30 to 05:30, 26.57p
    otherwise. Gas is fixed at 5.24p.
  - **Home Mini:** it was online but `home_mini_settings.supports_live_consumption` was
    false. It was switched on with the integration's reconfigure flow, which needs
    `home_pro_settings: {}` and `price_cap_settings: {}` sent back. That added
    `current_demand` (W), `current_accumulative_consumption`/`cost` (and peak /
    off_peak), plus gas current_* sensors. The first reading was 225 W.
  - **New cards (`src/energy-cards.js`):**
    - `octopus-card` with `show`: `electricity` / `last_day` / `gas` / `octoplus`. It
      finds meters from the entity registry (platform `octopus_energy`).
      - Cheap means the day's minimum rate from the `event.*_day_rates` `rates`
        attribute.
      - `last_day` uses the `charges` attribute of `previous_accumulative_cost`
        (rate + consumption per half-hour).
    - `ev-charger-card` for a myenergi Zappi: it finds `select.*zappi*` with an Eco+
      option, and the power / session / status / plug sensors. It can be overridden
      per entity.
      - Until myenergi is set up it says "Not connected yet".
      - Mode buttons use `select.select_option`.
    - Both support compact rows.
  - **Icons can be pictures:** an https URL, `/local/...`, or `brand:<domain>` for
    brands.home-assistant.io. A failed picture hides itself.
  - **Octopus icon and colours (user's choice, 2026-09-30):** flat `phu:octopusenergy`
    from Custom Brand Icons (not the logo picture, for flat-icon consistency).
    Electricity is pink `#f050f8` (Octoplus keeps pink with a gift icon), Gas is Octopus violet `#7b61ff`
    (`OCTO_VIOLET`, option A of https://claude.ai/artifact/35p65QgdMS29MW5tQh2PWJ).
    The pack also has `phu:myenergi`, which the user hasn't asked for.
  - **Nav bar:**
    - `icons_only` (Mobile uses it): 42px icons, the current page as a filled circle.
    - Per-page `admin_only` hides a page from non-admins (`hass.user.is_admin`).
  - **Mobile `energy` view** (only Jamie and Hayley, the admins; the view has `visible`
    and the section a user condition). Panels:
    - Octopus Electricity (#f050f8, `phu:octopusenergy`);
    - Car charger (#00b8d4);
    - Last full day;
    - Gas (#7b61ff, `phu:octopusenergy`);
    - Octoplus (#f050f8, `mdi:gift-outline`).
    Every Mobile nav bar has an Energy page (`admin_only`) and `icons_only: true`.
  - Home has a **Car charger** panel for everyone (after Climate).
  - **Zappi (connected 2026-10-02):** myenergi entry `01M3YEKEVKPTN4PZH6EGDMMH3V`, hub
    serial 20220859; device "Zappi", entities tidied to `*.zappi_*` (`select.zappi_charge_mode`,
    `sensor.zappi_plug_status`, `_status`, `_charge_added_session`, `_charging_power` (CT1),
    `_grid_power` (CT2), `_energy_used_today`, …). The hub is device "myenergi Hub" with
    `sensor.myenergi_*` (`house_power` = live whole-house use, `grid_import_today`,
    `import_power`, `grid_voltage`, …), not used by any card yet (idea: live house power on
    Energy).
    Plug values: EV Disconnected / EV Connected / Waiting for EV / EV ready to charge /
    Charging / Fault; status: Paused / Charging / Boosting / Completed.
    Earlier notes: the HACS integration CJNE/ha-myenergi (id 401145616) is downloaded. HA
    restarted at about 21:28 on 2026-09-30, which should have loaded it. It still needs a
    config flow with the hub serial and API key, which the user will send. "Set up the
    Zappi charger" is on Jamie's to-do list (`todo.priorities_jamie`) as a reminder;
    tick it off once it's connected.
  - Went live 2026-09-30: the Mobile dashboard (including Energy and the Home charger
    panel) is on the release types.
  - Energy mock-up: https://claude.ai/artifact/XRtwhvTDAuvS1YYtQgM8hz
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

**New in v0.20.0: Auto Layout Card** (`src/auto-layout-card.js`; v0.28.0 changed
where wide panels go and the order; see its release notes). The user
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


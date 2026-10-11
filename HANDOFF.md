# Church Drive: Handoff

*Last updated 2026-10-11. Current release: **v0.41.0**.*

## Where this stands

**Mirror card (released in v0.33.0, 2026-10-02).** `custom:mirror-card` shows
another page's card on this one (Home's Kitchen lights = the Lighting page's Kitchen card),
picked in the visual editor: dashboard, page, card. Logic is in `src/mirror.js` (tested in
`test/mirror.test.mjs`): a card is keyed by its area, else entity, else name (`area:kitchen`,
`#2` for repeats). It reads `lovelace/config`, reloads on the `lovelace_updated` event,
and forwards the Section Panel's compact mode. The companion Android app follows mirrors too
(`DashboardLights.kt` has to keep the same keys). The Home Lights panel on the Mobile dashboard uses three mirrors of the Lighting page's
Kitchen, Living room and Middle floor cards (`source: area:kitchen` etc.). Also in v0.33.0:
light cards retry loading the scene library (`universal-scenes.js`) if HA wasn't ready, so a
page opened during a restart no longer stays without scenes.

This repo (`github.com/J45PER/church-drive-cards`, public) is the **Church Drive**
Home Assistant integration (`custom_components/church_drive`). It's installed through
HACS as an integration. It does two jobs:

1. **Delivers the custom Lovelace cards.** It serves the bundle and loads it on
   every dashboard. There's no separate card install.
2. **Universal scenes.** It keeps one library of scenes (Bright, Relax, Soho… plus
   the user's own). Any light card can use them in any room or zone without Hue
   scene setup. There's also a scene select per room/zone and a scene builder.

**Now (2026-10-04, v0.38.2 live, nothing on beta):**
- **Mobile pages** each have a three-row header under the title (Auto Layout
  `header_content`):
  - Home: the signed-in person's to-dos.
  - Lights, Security and Energy: live template lines.
  - Climate: an hourly forecast.
  - Cleaning: the cleaning list, which opens the To-do page at its Cleaning panel.
  - To-do: a summary.
  See "Page header lines and the cleaning schedule" under the conventions.
- **To-dos:** `task-list-card` runs My to-do, Shared and Cleaning on the Mobile To-do
  page and the Tasks dashboard. Any task can repeat (daily, weekly or fortnightly,
  monthly, yearly, after done) and remind people. HA's side is "Church Drive:
  repeating tasks". The rules are in `src/repeat.js` (`npm test`) and
  `ha/repeating-tasks.jinja`.
- **House jobs:** the automatic to-dos (`house-tasks-card`, "Priorities Automatic")
  and the to-do notifications are set in Manager.
- **People and notifications (v0.26.0, live 2026-10-01).** Plan:
  https://claude.ai/artifact/XmAHes61uTUSrJNL4aGgmC. Nothing names anyone any more:
  - The integration (`people.py`, `kinds.py`) picks people up from HA's people and their
    phones from the companion app, makes "Priorities <first name>" lists, and keeps who
    gets each kind. `church_drive.notify` sends a kind (or to named `people`) on each
    assigned person's phones; safety kinds sound on silent; `admin_message` lines and
    `admin_only` kinds (energy cost) go to admins only (Jamie, Hayley).
    `sensor.church_drive_people` (`assign`, `everyone`, `people`) feeds templates;
    `church_drive_todo_changed` fires for every to-do list's count change.
  - Manager: a **Notifications** panel (`notifications-card`: kinds × people table with
    All, phones as chips) plus the summary day/time and house-job thresholds. The old
    `input_boolean.to_do_*` switches and `script.church_drive_notify_person` were carried
    over and deleted. Jamie's iPad and watch start switched off.
  - Mobile and Tasks To-do views: one section for everyone (`entity: mine`,
    `states(my_list)`).
  - Automations on `church_drive.notify`: automatic to-do tasks, to-do reminders,
    repeating tasks, "a device stays unresponsive" (entity id still
    `..._tell_jamie_when_a_device_stays_stale`), Battery Notes alert and weekly report
    (kind `battery_report`, Jamie and Hayley).
  - New (N1–N27): `church_drive_safety_alarms`, `_security_alerts`, `_house_alerts`,
    `_energy_alerts` (cheap rate is a quiet notification), `_system_alerts`,
    `_people_alerts`. "Nobody home" only counts when every person's location is known
    (Diane and Ian don't share theirs yet). **Car (N17–N20)** is "Church Drive: car
    alerts" (2026-10-02): plugged in, charging started/paused (5 minutes steady), charged
    (`Completed`; rough cost to admins at the rate when it finished), and at 9pm "not
    plugged in" when the next 12 hours have a rate under half of now's.
  - **Released in v0.27.0 (2026-10-01, reload-only), mock-ups https://claude.ai/artifact/A4d6pusQxNRdBe34qYZsnP:**
    `media-card` (style B, pop-up remote), `system-card`, `safety-card`, `people-card`,
    octopus-card `show: cheap`, climate-zone `type: outside`, grey ticks under All.
    Mobile: new **Devices** page (view 6, `/dashboard-mobile/devices`, nav item before
    Energy on every page's nav bar, #ec407a, red when the internet's down) with TVs &
    speakers and Devices & services panels and three header lines; Security's Fire Alarm
    panel became **Safety** (safety-card + the base station tiles); Climate panel has an
    Outside (garden sensor) zone and the summary uses it; Energy starts with a **Cheap
    rate** panel. Manager: **Who's home** panel, Notifications card on beta type, and the
    per-person to-do lists come from `sensor.church_drive_people` via auto-entities.
    Design Presets Beta tab has demos of all five. On release the real dashboards went
    back to released types.
  - Earlier choice notes (2026-10-01): mock-ups https://claude.ai/artifact/A4d6pusQxNRdBe34qYZsnP
    — media card **B** (a row per player, pop-up remote), a new **Devices** page (media,
    then Devices & services: internet, remote access, backups, updates, uptime), C2 Safety
    panel on Security, C3 garden temperature on Climate, C6 cheap rate and power-downs on
    Energy, C8 Who's home on Manager. C4, C7 and C9 not wanted.
- **People (2026-10-01): Jamie, Hayley, Diane and Ian** (Ian added by the user;
  user id `f9a53de72d834d778c6bd476284fb8a2`, phone `notify.mobile_app_ian`).
  Adding a person is a per-person checklist, done for Ian:
  - lists and switches: `todo.priorities_<name>` (Local To-do), the six
    `input_boolean.to_do_<kind>_<name>` switches, plus the summary and new-task alert
    switches. Ian's summary switch is `input_boolean.to_do_summary_ian` (the others
    are `to_do_reminders_<name>`).
  - `script.church_drive_notify_person`: a person option and a step per phone.
  - Automations: automatic to-do tasks (trigger lists, `lists`, `people`), to-do
    reminders ("People" step, triggers, `get_items` targets), and repeating tasks
    (trigger, condition and `lists`).
  - Dashboards: Manager's grids (two columns of tiles) and lists, and a per-user To-do
    section on Mobile and Tasks (copy of another person's, user visibility).
  - "Everyone" on an automatic task means every person is ticked, so with four people
    the existing tasks now read "for Jamie, Hayley, Diane" until Ian is ticked too.
- **Every setting is visual:** see "Everything from the UI" and its checklist.
  Run `tools/editors-smoke.mjs` before pushing.
- **Waiting on the user:**
  - Presence sensors, for the Lights "maybe left on" line.
  - The cars: **Vauxhall Astra (PHEV, AMZ 2927) connected 2026-10-03** through Stellantis Vehicles
    (HACS 839422993, manual OAuth code from Chrome's console `mymvxsdk://oauth2redirect…?code=`;
    the code is single-use and lasts a minute or two). Device "Vauxhall Astra", entities
    `*.amz_2927_vauxhall_astra_*` (battery %, range/fuel range/mileage set to miles,
    `binary_sensor…_battery_plugged/_charging`, buttons for preconditioning, lock, charge).
    The **VW (full EV)** still needs signing in: VW Group Connect (HACS 1207816350, domain
    `vag_connect`) is loaded; on Jamie's to-do list.
  - **People manager** (`src/people-manager-card.js`, `custom:people-manager-card`, v0.36.0, on Manager as the first panel "People"; mock-up
    https://claude.ai/artifact/X9iAro1YMDVjzTv2M6DiR8): a list of people (chips on a phone)
    and the chosen person's page: picture (Home Assistant image upload, then `person/update`;
    a YAML person can't be changed), Places (replaces `places-card`, removed from Locations),
    Phones, Cars (new: `church_drive/people/cars`, kept in the people store, `cars` on
    `sensor.church_drive_people`), Alerts by group, To-dos (To-dos and House jobs kinds),
    Arrivals (who's told about this person). Admins see everyone; others only themselves,
    read-only. The Car card reads owners from the sensor (its own `people` setting is gone);
    car alerts (plugged in, charged) go only to the car's owners when it has any.
  - **Device access** (v0.37.0): People manager › Devices limits a device to some
    people (`church_drive/people/access` {key: device id or entity id, people}; none = everyone's
    again). Kept in the people store (`access`); `sensor.church_drive_people` has `access` per
    entity (a device's entities all take its people). Cards: `kitCanUse` / `kitCardDenied` in
    `card-kit.js`; a section panel hides a child card whose every entity (from its config,
    templates aside) is someone else's, and hides itself when that leaves nothing (Auto Layout
    then skips it); light cards drop such lights from their rows. Hiding only, not a lock.
  - **Phone locations** (v0.38.0): each phone in People manager › Phones has Alerts and
    **Location** switches. Location is the person's own device trackers (Settings › People), set
    with `church_drive/people/tracking` {person, tracker, on}; the phones list is now the
    person's trackers plus every companion-app tracker signed in as them (`mobile_app` entry
    `user_id`), so a tracker taken off still gets alerts. Why: HA's person follows whichever
    tracker updated last, so Jamie's iPad at home kept saying "home" (2026-10-04 Jamie was set to
    the Pixel and iPhone only by hand). **Location age:** the integration keeps when each
    tracker last sent a location (`seen` in the people store; state changes and same-place
    reports, not restores at start-up). Every 10 minutes, a person whose location is older than
    `stale_hours` (default 6, set in Phones; `church_drive/people/settings`) gets a silent
    `request_location_update` to their tracked phones; no answer in 15 minutes flags them
    `stale` (`located`, `stale` on the people sensor and in `church_drive/people`). People
    manager shows a warning with the phone fixes; "where" and the Who's home card add "· 2 days
    old". Diane's phone stopped sending locations on 2026-10-01 (battery still reports): her
    app's location is off or blocked on the phone; Ian has never shared one.
  - **App to match** (the Android app on `companion-apps` should follow these): Car card and
    owners (`church_drive/people/cars`, `cars` on the people sensor); People manager (picture via
    image upload + `person/update`, Cars, Arrivals, To-dos apart from Alerts, Devices);
    device access (`access` on the people sensor: hide what the person can't use); media: a TV
    in standby is Off and power runs `script.<player>_wake`; phone Location switches and
    "location old" (`tracker`/`tracks`/`seen` per phone, `located`/`stale` per person); car card
    "Updated HH:MM" + Refresh (car's `button.*_wakeup`); charger Unlock / Charge now / Pause (both done
    in app 0.2.1, `chargerOverrides` in `Cards.kt`, `carAsOf`/`carStale` in `CarCard.kt`).
  - **Car card** (`src/car-card.js`, `custom:car-card`, v0.35.0): each car from those
    integrations (`carDevices`/`carEntities`): battery bar and %, electric range, fuel % and
    range, Plugged in / Charging / where it is, "Full by" when charging; tap for more-info.
    `cars:` [{device, name, people}] gives cars to people; `only_mine` (default on) shows the
    signed-in person only their cars (a car with no people is everyone's) and the card hides
    itself when they have none. It sits at the top of the "Car charging" panel on Home and
    Energy; `ev-charger-card` is the Zappi alone again (the v0.33.1 car rows are gone).
    "Church Drive: car alerts" names the plugged-in car and its % (plugged in waits up to 3
    minutes for the car to report; charged).
  - **Car figures while charging** (v0.38.1): Stellantis cars only send figures when
    something happens, so a charging Astra showed 12% for hours (the integration polls every 60 s
    but gets the car's last report). "Church Drive: car figures while charging" (automation,
    live) presses each charging Stellantis car's `button.*_wakeup` every 15 minutes, and the
    Astra's as soon as it starts charging (2026-10-11: it was every 30 minutes, so a charge
    starting at 00:32 showed 7% until 01:00 while the car had reached 30%; the PHEV gains about
    20% in 20 minutes). The car answers in ~20 s; the command status can read "Failed" while the
    figures still refresh. Car card: past 20 minutes old while plugged in or charging, a line
    "Updated HH:MM" (from the battery sensor's `Last updated` attribute, else its state) and a
    **Refresh** button that presses the car's wake-up (`carAsOf`, `carTime`, `wake` in
    `carEntities`).
  - **Zappi starts when a mode is picked** (automation, live 2026-10-10): the Zappi has "lock when
    plugged in" on (set in the myenergi app), so Fast picked from HA or the apps waited for an
    unlock and "change now" at the charger. On a mode change to Fast/Eco/Eco+ (waiting up to 10
    minutes for the car to be plugged in, then 20 s for the lock), if `binary_sensor.zappi_locked`
    is on and it isn't charging: `myenergi.myenergi_unlock`, then the same mode again. A car
    plugged in with no mode picked stays locked. Checked 2026-10-11 00:30: it ran (unlock, 5 s, Fast)
    and charging started; "Charge when locked" stayed on, so the unlock does not turn it off and
    PR #121 (drop the unlock) isn't needed.
  - **Charger button** (v0.38.3, replaces the v0.38.1 Unlock / Charge now / Pause overrides): while a
    car's plugged in, `ev-charger-card` (expanded and compact) shows one button above the modes:
    **Start charge** (unlock if locked, then Fast) until it's charging, then a greyed **Charging**
    marker (Stop is the mode tile); with no car a greyed **No car connected** (expanded card only; the
    compact view shows the modes then). Why: `binary_sensor.zappi_locked` stayed on for 72 h, including
    a 7.6 kW overnight charge, so the lock button was misleading. "Charging" also counts plug status
    "Charging", because the Zappi sits at 0 W / "Waiting for EV" between bursts. Colours: grey
    No car connected, teal Charging. Android app 0.2.3 has the same (card and widget).
  - **Zappi smart charge** (automation + helpers, live 2026-10-10, copy in `ha/zappi-smart-charge.yaml`): a car plugged
    in with no mode picked waits for the cheapest Octopus half-hours (within 10% of the cheapest before "ready by",
    07:00, and no dearer than the max price, 15p), then picks Fast; at the end of the window it picks Stopped. Anyone
    picking a mode takes over until unplugged. State in `input_select.zappi_smart_charge_state` (Idle, Waiting,
    Charging, Done, Manual); switch `input_boolean.zappi_smart_charge`. Not run through a real cheap window yet
    (first chance: the 00:30 start after the car is plugged in with Stopped).
  - **Smart charge in the card (v0.41.0) and app (0.5.0):** `ev-charger-card` has a Smart charge switch
    (`input_boolean.zappi_smart_charge`), "Stopping…" while Stop waits for the readings, and a prompt when Start charge or
    Fast is pressed at the normal rate with a cheap window within 18 h: Charge at HH:MM (`script.zappi_charge_later`) or
    Charge now. "Church Drive: Zappi catches up after a mode change" refreshes the readings after any mode change.
- Nothing else is pending. Ideas the user hasn't asked for are under Open items.


Older release notes (v0.10 to v0.38 detail) are archived in [docs/HISTORY.md](docs/HISTORY.md); [CHANGELOG.md](CHANGELOG.md) lists every release in one line.

## How the user works (conventions)

- **Beta first.** New or changed card behaviour goes on the Design Presets **Beta**
  tab. The user checks it, says "release it", and then it ships.
- **Everything from the UI.** Every option needs a visual-editor field. Nothing is
  YAML-only. That covers HA config too (audited 2026-09-30):
  - No templated action names (`action: notify.{{ … }}`). HA's editor can't show
    them and drops that step to YAML. Templates are fine in `data` and `target`. To
    reach different phones, call `script.church_drive_notify_person`.
  - No top-level automation `variables:`. The visual editor keeps them but never
    shows them. Put them in a first "variables" step with an alias instead (both
    to-do automations do this).
  - Card editors: every card has one, and every option it reads is in its schema.
  - **Checklist for every change (the user asked again, 2026-10-01):**
    - Every new card option goes in its editor schema. Run the editor smoke test
      (`editors.mjs`, below) before pushing.
    - Every new automation or script: no templated action names, no top-level
      `variables`, and no step that forces YAML. Settings people change live in
      helpers, cards or plain to-do text, never only in YAML.
    - 2026-10-01 check: the page-header fields, `task-list-card` and
      `automation.church_drive_cleaning_schedule` ("repeating tasks") all follow this.
  - **Auto Layout editor bug (fixed on beta 2026-09-30):** since f87fb14 its
    `set hass` called the card's `_renderHead()` / `_watchTodo()`, which don't exist on
    the editor. It threw, so HA fell back to YAML for every Auto Layout card. Those
    lines now live in the card's own `set hass`.
  - Smoke test: `tools/editors-smoke.mjs` (with `tools/editors-smoke.html`, after
    `npm run build`) builds every registered editor with a stub config and fake hass,
    and reports any that throw. Set `PLAYWRIGHT_MODULE` to Playwright's `index.mjs`
    if it isn't installed in the project.
- **Our own pop-ups (`src/popup.js`, user's request 2026-10-01, on beta):** the template
  for every card that needs a pop-up, including when we go back to cards that use HA's
  pop-ups or need a custom one. Size aware, the user's choice from mock-ups: **a bottom
  sheet with a grab bar on phones (up to 600px wide), a centred window on tablets and
  PCs.** `openPopup(card, { title, icon, color, content, onClose })` returns
  `{ body, setTitle, close, open }`. It's a native `<dialog>` with `showModal()`, kept
  inside the card so the card's CSS and theme variables still apply. It closes with
  its ✕, a tap outside, Escape, or Back (it pushes one history step while open).
  First user: the Task List card's add and change form ("New task" / "Change task").
- **Page header lines and the cleaning schedule (user's request, 2026-10-01, released in v0.25.0):**
  - Mock-ups: https://claude.ai/artifact/D9cz58EsbDS4VLx2e6tCsE (round 2 is the agreed
    one). These are snapshots only: nothing in a header makes tasks or sends
    notifications (the user was explicit).
  - Auto Layout `header_content`: `none` / `priorities` / `todo_summary` / `lines` /
    `forecast` / `list`. `normalize` maps the old `priorities: true`, and `store`
    keeps `priorities` in step (the editor's field handler takes the form's values
    whole, so removed keys stay removed).
    - `lines`: each line's `text` and `alert_when` are templates rendered by
      `stcRender`. `<b>` is the only markup kept. `panel` taps jump to a panel with
      that title, or navigate.
    - `forecast`: `weather/subscribe_forecast`. The user chose hourly and wants daily
      as an editor option (`forecast_type`). Page alerts replace the forecast while
      there are any.
    - `list` and `todo_summary` are worked out in the card, because templates can't
      see to-do items or the signed-in user.
  - The Mobile pages use them:
    - Lights: lights on (Hue groups excluded), not reachable, and "maybe left on".
      "Maybe left on" checks, in order: everyone's phone out; an area with a
      motion/occupancy/presence sensor quiet for 30 min; outdoor lights in daylight;
      anything on over 4 h. The sensor check picks up new presence sensors by itself
      (the user will add some; the BSB002 bridge has no MotionAware). Diane's phone
      doesn't share location, so "everyone's out" ignores her (unknown state).
    - Security (no alarm line, per the user): doors, last ring/movement from Ring's
      `event.*_ding/_motion`, who's home.
    - Climate: hourly forecast from `weather.forecast_home`.
    - Cleaning: `list` of `todo.cleaning`. Its rows and To-do button open
      `/dashboard-mobile/todo#cleaning`.
    - To-do (all three per-person sections): `todo_summary`.
    - Energy: rate now / next cheap window (from the rates events), live W, today's
      cost.
    - The line templates were tested live with `ha_eval_template` before going in.
  - **Repeating tasks (the user's follow-up, 2026-10-01, released in v0.25.0):**
    - The cleaning schedule belongs on the **To-do page**. The Cleaning page header
      (`list` of `todo.cleaning`) opens `/dashboard-mobile/todo#cleaning` from its
      rows and its to-do button (`priorities_page`, `list_icon:
      mdi:format-list-checks`, label "To-do").
    - **`#panel` addresses:** an Auto Layout page opened with `#<panel title>` (slug or
      exact title) jumps to that panel once laid out, like its chip. It then drops the
      `#…` with `replaceState`. It's checked on layout, connect and
      `location-changed` (HA keeps views alive), and a hidden per-person copy of the
      page skips it (zero height).
    - Repeats apply to every list: Cleaning, each person's own and Shared.
    - `task-list-card` (it replaced the beta-only `cleaning-schedule-card`) is a full
      to-do card: tick, add, edit, show and clear done. Repeat choices: Never (optional
      due date and time), Daily (every N days), Weekly (1 to 4 weeks, days with a time
      each), Monthly (day N or the last day, every N months), Yearly, and After it's
      done (N days, weeks or months). It also has a start date, who it reminds, and
      notes.
    - The To-do page (all three per-person sections) uses it for My to-do (with
      `assign: me`, on beta: no people chips, just "Remind me" Yes / No for the
      signed-in person; `remind_default` kept until the release), Shared, and a
      Cleaning panel. The Tasks
      dashboard's To-do view (`dashboard-tasks`) matches it, with a `todo_summary`
      header. Its Shopping view keeps HA's `todo-list` card: the shopping list
      integration only stores names, so it can't hold repeats.
    - **Rules live in `src/repeat.js`** (unit tests in `test/`, `npm test`, UK time).
      Words: `<repeat> · <for …|no reminders> · <notes>`. A description that doesn't
      start with a repeat is plain notes. "Once · for X" means no repeat, just a
      reminder at the due time. The phase comes from the current due time
      (fortnightly keeps its fortnight), so the start date isn't stored.
    - The automation's Jinja (`occ` macro) is a port of `nextOccurrence`. 18 cases
      matched exactly (`tools/repeat-cases.mjs` against `ha/repeating-tasks.jinja`,
      run through `ha_eval_template`). **Change both together**, and paste the
      template back into the automation's "Work out what's due" step, with `t0 =
      now()`.
    - A repeating task is always open, so the Home widget only shows one due within
      2 days.
    - `automation.church_drive_to_do_reminders` "added" branch now needs
      `trigger.to_state.context.user_id`. That way a task the automation brings back
      isn't announced as a "New to-do".
  - **The schedule's HA side:**
    - Local To-do "Cleaning" (`todo.cleaning`, entry `01M3T9B7S3G28WA6JG3PECQ2TH`).
    - `automation.church_drive_cleaning_schedule` (alias now "Church Drive: repeating
      tasks"; the entity ID kept its first name) covers `todo.cleaning` and the
      `todo.priorities_*` lists except automatic. The "Which lists" step lists them.
      `input_datetime.cleaning_next_reminder` is now named "Tasks: next reminder".
      - It reschedules ticked-off or undated jobs to their next time, reminds the
        named people at due time via `script.church_drive_notify_person`, and sets
        `input_datetime.cleaning_next_reminder` (its own wake-up).
      - Triggers: that helper, the list's state, `call_service` for
        **`todo.update_item` only**, start, and hourly. Reading the list
        (`get_items`) is itself a `call_service` event: an early version triggered
        on any todo call and looped until it was turned off.
      - It waits 2 s after an edit, because the event arrives before the list is
        saved.
      - Tested end to end: the 00:20 reminder reached Jamie's phones, and ticking
        off brought the job back at next week's time.
- **No repeat notifications after a restart (user's request, 2026-09-30).** A
  restart makes sensors briefly `unavailable`. Anything that reacts to "became low" or
  "task added" must not treat coming back from unavailable as news. Fixes so far:
  - `automation.church_drive_automatic_to_do_tasks`:
    - It never removes tasks in the 10 minutes after HA starts or the automation
      reloads (`now() - as_datetime(this.last_changed)`).
    - `wanted` also carries `hold: true` entries for tasks whose source can't be read
      (a battery or part sensor `unavailable`/`unknown`, or a device offline for less
      than "offline for", whose `last_changed` restarts at boot). Holds are never
      added, reopened or refreshed; they only stop a drop.
    - Before this, the Ring keypad task was dropped at boot and re-added two minutes
      later, which re-sent "New to-do" to everyone.
  - `automation.battery_notes_low_battery_alert`: the trigger has
    `not_from: [unavailable, unknown]`.
  - `automation.church_drive_tell_jamie_when_a_device_stays_stale`: the "stale" branch
    needs `trigger.from_state` to be a number (a numeric_state trigger re-arms when
    it comes back from unavailable).
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
    It's pinned to the v0.25.0 merge (see below).
  - To test a branch: push it, repoint the resource, and ask for a hard refresh.
  - jsDelivr is blocked from the cloud container, but works for the user.
- **Rollback:** download an older release in HACS and restart.
- **Dashboard edits:** use `ha_config_set_dashboard` with `python_transform` plus
  `config_hash`.
  - It needs `BestPracticeKey`. The key rotates hourly; re-read
    `ha_get_skill_guide(file='SKILL.md')` (the `skill` argument is no longer accepted)
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
      Security · Climate · Cleaning · To-do · Energy (admins only), icons only,
      floating at the bottom, HA's tabs hidden. The Quick Actions view's path is
      `home` (`/dashboard-mobile/home`).
    - **Page headers (v0.25.0):** Home `priorities`; Lights, Security and Energy
      `lines`; Climate `forecast` (hourly, `weather.forecast_home`); Cleaning `list`
      (`todo.cleaning`); To-do `todo_summary`. The line templates are in each page's
      Auto Layout `header_lines`, editable in the visual editor.
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
    - **Cleaning:** one Cleaning panel (blue, state · battery). Its header lists the
      cleaning jobs.
    - **To-do** (one section per person, shown by user condition): From the house
      (`house-tasks-card`), My to-do and Shared (`task-list-card`), and Cleaning
      (`task-list-card` for `todo.cleaning`). `empty_last` puts empty panels last.
    - **Energy** (admins only): Octopus Electricity, Car charger (`ev-charger-card`,
      waiting for myenergi), Last full day, Gas, Octoplus.
    - **Home** also has a Car charger panel for everyone.
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
    `sensor.church_drive_device_health`) holding the Device Health card, and an
    "Automatic to-dos" panel:
    - who gets each kind of house job;
    - notifications: the summary's day and time, and who gets the summary and
      new-task alerts;
    - the thresholds for making a task;
    - the lists, and the house's jobs.
    Meant for the user only (require_admin can't limit it to one account).
  - **Tasks** (`dashboard-tasks`): To-do (per person, the same panels as the Mobile
    To-do page, `todo_summary` header) and Shopping (HA's list card for
    `todo.shopping_list`), with a To-do · Shopping nav bar.
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
- **Automations, scripts and helpers made for Church Drive** (all UI-editable):
  - `automation.church_drive_automatic_to_do_tasks`: house jobs on
    `todo.priorities_automatic`. It never removes tasks for 10 min after a restart,
    and holds a task while its source is unavailable.
  - `automation.church_drive_to_do_reminders`: the weekly summary and new-task
    alerts. It ignores tasks the automations bring back.
  - `automation.church_drive_cleaning_schedule` ("Church Drive: repeating tasks"):
    repeats and due-time reminders on Cleaning and the personal and shared lists.
    It wakes on `input_datetime.cleaning_next_reminder` ("Tasks: next reminder").
  - `automation.church_drive_tell_jamie_when_a_device_stays_stale`, and
    `automation.battery_notes_low_battery_alert` (`not_from` unavailable).
  - `script.church_drive_notify_person`: one plain notify step per phone. Every
    notification goes through it.
  - Helpers:
    - `input_boolean.to_do_<kind>_<person>` (who gets each kind of house job);
    - `input_boolean.to_do_reminders_<person>` and
      `input_boolean.to_do_new_task_alerts_<person>`;
    - `input_select.to_do_summary_day` and `input_datetime.to_do_reminder_time`;
    - `input_number.to_do_battery_low_below`, `input_number.to_do_parts_due_within`
      and `input_number.to_do_offline_for`.
  - The label `ignore_in_to_dos` keeps a device or entity out of the house jobs.
  - To-do lists (Local To-do):
    - `todo.priorities_jamie`, `todo.priorities_hayley` and `todo.priorities_diane`;
    - `todo.priorities_everyone` (shared);
    - `todo.priorities_automatic` (house jobs);
    - `todo.cleaning`.
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

- **Ring as the Church Drive account (job for later).** On 2026-10-02 the Ring
  integration was briefly moved to church-drive-63@outlook.com (Advanced user under
  Ring's Feb 2026 "User Permissions"), but Ring returned no devices to it
  (diagnostics `device_data: []`, also after a fresh sign-in), so Hayley's account was
  re-added. That entry and Ring-MQTT are still Hayley's; Ring shows HA's mode changes
  as Hayley. An empty church-drive entry is kept. To try later, in order:
  1. Hayley's app: Church Drive's per-device toggles all on; delete any old/offline Ring devices.
  2. As Church Drive: Control Center › Authorized Client Devices, remove HA/Python sessions;
     delete and re-add the Church Drive Ring entry; check for devices.
  3. If still empty: sign Ring-MQTT in as Church Drive (different library) with Hayley on
     hand to revert. Mode changes go through Ring-MQTT, so that's what shows the account.
  - The user renamed the Ring devices (Front Doorbell, Driveway/Garden/Entrance/Living Room
    Camera, Ring Chime). Their entity_ids were put back to the originals
    (`camera.<x>_live_view`, `event.<x>_ding/_motion`, `sensor.<x>_last_activity`, …),
    which events.py, the cards, links and automations use.
  - Battery Notes for the Ring cameras: the kept notes are on the Ring-MQTT devices
    (`sensor.front_door_battery_plus_3`, `sensor.driveway_battery_plus_2`,
    `sensor.garden_battery_plus_2`, used by Battery Status, the automatic to-dos and "Any
    Battery Low"). The copies on the Ring integration's devices were removed 2026-10-02;
    if Battery Notes offers them again as discovered, ignore them.

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
- **LG TV turn-on (done 2026-10-02, live config):** two Wake on LAN entries (wired
  64:E4:A5:F4:ED:8E, Wi-Fi 54:B7:BD:FA:3B:FC; "Turn on via Wi-Fi" is on), `script.living_room_tv_wake`
  sends both, "Living Room TV: turn on" (`webostv.turn_on` trigger) runs it. The media card
  (beta) shows an asleep TV as Off and its power button runs `script.<player>_wake` when one
  exists. Removed: the stale DLNA entries (the Windows PC renderer, an unknown "Bedroom") and
  the empty LG ThinQ entry (ThinQ doesn't cover webOS TVs).
- **Presence:** the user plans presence sensors. The Lights "maybe left on" line
  picks up any motion, occupancy or presence binary sensor that has an area. Hue
  MotionAware would need a Bridge Pro (they have a BSB002).
- **Diane's phone** doesn't share its location (`person.diane` is `unknown`), so
  "everyone's out" checks only count Jamie and Hayley. That could be turned on in
  her app.
- The weekly to-do summary lists all open tasks, repeating ones included even when
  they're due well later. Filter them if the user finds it noisy.
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
npm test          # repeat rules (src/repeat.js), UK time
PLAYWRIGHT_MODULE=/opt/node22/lib/node_modules/playwright/index.mjs node tools/editors-smoke.mjs   # every card editor opens
TZ=Europe/London node tools/repeat-cases.mjs   # cases to cross-check ha/repeating-tasks.jinja in HA
ruff check custom_components --select E,F,W,B --line-length 140   # Python lint (only the long palette lines in library.py fail, by design)
```

**Headless checks:** Chromium and Playwright are available. Load
`church-drive-cards-beta.js` into a page with a mock `hass` (states, entities,
`callWS` returning a library, `callService` logging) and a light card in demo mode.
Tap tiles and read back titles and classes. HA itself can't run in the container
(Python 3.11), so check the Python by review, `py_compile`, ruff and small unit
tests of `library.py`.

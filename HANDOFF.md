# Church Drive: Handoff

*Last updated 2026-10-23. Current release: **v0.67.0** (Android app 0.48.1).*

## Where this stands

**Ring cameras and doorbell: the current way (2026-10-13).** Video and Talk for every Ring
camera go through the private add-on **go2rtc (Church Drive, Ring fix)** (`go2rtc-ring/` in this
repo, app slug `57e66166_go2rtc_church_ring`, go2rtc 1.9.14 + HEVC fix + our Ring patch). Streams
are named `front_door`, `driveway`, `garden`, `entrance`, `living_room` in `/config/go2rtc.yaml`
(sources `ring:?camera_id=...&device_id=...&refresh_token=...`, one login per stream; the file holds
tokens, so never print it). The old go2rtc-hevc-fix add-on is uninstalled; so is the temporary
Camera Test dashboard. Why it works: Ring answers HEVC-capable battery cameras with H265, so the
patch offers H264 only and Ring transcodes on its side; it also replies to Ring's pings, asks for
keyframes and logs the signalling (`log: ring: debug`, no secrets; turn off when not debugging).
Router forwards 8555 (WebRTC) to Home Assistant for Talk over 5G. A refresh token with a stray space
(from hand-editing) gives "illegal base64 data" in the go2rtc log. Next jobs: refine the camera UI;
motion and doorbell notifications: built in v0.55.0 (events.py `notice()` builds both, the buttons and pop-up style come from the notification buttons and styles of v0.53/v0.54; movement alerts per alarm mode are `alerts` in the events store, set in Camera links). After merging, delete the doorbell and night_motion branches of the "Church Drive: security alerts" automation.

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
- **Alarm lights (v0.52.0, 2026-10-12, PR #179; v0.52.1 fixes strobing and restoring after the user's first real test).** v0.52.1: a Hue room group's `entity_id` attribute is a **set** (the code only accepted lists, so groups were saved and restored as groups, not as their lights); Hue lights now flash on the bridge through the CLIP v2 `signaling` property (`alternating` two colours, `on_off_color` one colour; found through the hue config entry's `runtime_data.api.request`, the light's registry `unique_id` is its Hue id), other lights are pulsed by Home Assistant with blocking calls; stop = `no_signal` (the bridge's rules want a `duration` even for `no_signal`: v0.52.1 left it out, so stops were refused; fixed in v0.52.2) then restore twice. The fake Home Assistant in `test/alarm_lights_test.py` now uses sets and a pretend bridge. `alarm_lights.py` (runtime: listens for smoke/heat/CO binary sensors and alarm panels, plays strobes, saves and restores the lights in `scene.church_drive_before_alarm`, light-only test) and `alarm_lights_logic.py` (rules, tested). `src/alarm-lights-card.js` is the Manager page. See README "Alarm lights" for the table. Decided with the user: strobe vs constant is a per-room/zone setting; day/night from light sensors, whole house, read once at alarm start; security home = red/blue + constant, away = red only (no constant), day/night does not change security yet; fire = red/amber, green for a minute after; blue when disarmed. Not decided or not built: presence-based constant lights in a fire (rooms with people and pets), green lights for safest exits, a different pattern for security by night. Still to do: put the card on the Manager page, try the light test on the real lights, check the Hue bridge copes with 0.5 s pulses on many groups, and choose the light sensors (indoor landing sensors see the house's own lights).
- **Shopping places (v0.45.0).** Places on a list are Google Maps search results `{name,address,lat,lon}` (`shopping_logic.clean_places`, `near_place`, `maps_url`), not zones; `church_drive/maps/search` takes `anywhere: true`. Old zone ids are converted on start. `time` is optional (needs a date): `_on_minute` reminds, `_check_near` fires on person lat/lon within 150 m, keyword shop-zone prompts only cover lists with no places. Not tested on a real phone yet.
- **Shopping lists (v0.42.0; v0.43.0 restyled it to match the task cards ; v0.43.1 made it an indigo Shopping panel on the To-do page, not a page of its own).** `shopping.py` /
  `shopping_logic.py` (see README "Shopping lists"). Data in `.storage/church_drive.shopping`. The to-do
  task for a list carries `church-drive-shopping:<id>` in its description: the cards and app must open the
  list page when they see it. The inbox's pseudo list id `shopping` replaces `todo.shopping_list` for AI
  output; `ws_inbox` batches carry `shopping: {lists, default, missed}` and `inbox/confirm` takes
  `shopping: {target: <id>|new, name, people, date, places}`. Not yet live-tested against HA: after
  installing, try `church_drive.shopping_add`, then tick items in `church_drive/shopping/item`.
  Web: `src/shopping-card.js` (`custom:shopping-card`; the list page is `openShoppingPage`, also opened by the
  Task List card for a task with the marker; `src/shopping-logic.js` is tested). Needs a `shopping` view on the
  Mobile dashboard holding the card (replaces the HA list card there; not done yet). Next: the Android full-page
  list with the share question and place picker.
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
    "Car last reported HH:MM · 23h ago" (v0.52.3; was "Updated HH:MM"; the time is the battery sensor's `Last updated` attribute, i.e. when the car last sent figures, not when HA last polled; else its state) and a
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
    2026-10-13: it did NOT start overnight. The mode was already Fast (left on from earlier), so there
    was no mode change to trigger the unlock, and "Zappi smart charge" saw a non-Stopped mode and went
    Manual. The Zappi said Boosting 00:30-05:30 but stayed "EV Connected" at 0 W (locked, waiting at
    the unit). Fixed live 2026-10-13: a second trigger, `sensor.zappi_status` Boosting for 3 minutes,
    runs the same unlock + resend-the-mode. (Charge when locked goes on at an unlock and off at
    unplug; it is not turned off by the unlock.) **Rule from the user: at 00:30, if a car is plugged
    in it must start. The Vauxhall always charges Fast when plugged in; when the VW is set up it
    should be Eco+.** Not built: today the mode is one global select, so a per-car mode (which car is
    plugged in comes from that car's integration plug sensor) is a TODO for when the VW is added.
    **Safety net (live 2026-10-13):** "Church Drive: Zappi safety net at 00:40" (automation). At 00:40,
    if a car is plugged in, the mode isn't Stopped, the status isn't Completed and it isn't charging:
    unlock (if locked), send the mode again, wait up to 5 min for plug status Charging, and if it still
    isn't, `church_drive.notify` kind `car_charging` ("Car didn't start charging", to whoever is ticked
    for that in Manager). Note a `for:` trigger (the Boosting one) restarts its clock on an HA restart
    or an `unavailable` blip, which is why this second chance exists.
    **2026-10-14 smart-charge fix (live):** at "Completed" the mode goes to Stopped; unplugging always sets
    Stopped (it had been left on Fast, which let the car top up at the day rate at 06:48); a "Done" car is
    set back to Waiting at the end of the cheap window so a car left plugged in starts the next night.
    **Car confirmation (live 2026-10-14):** every plug-in must be confirmed before anything unlocks or
    starts it. `Church Drive: car alerts` (plugged branch) turns `input_boolean.zappi_car_confirmed` off,
    stamps `input_datetime.zappi_car_last_asked` and sends "Confirm car to charge tonight" with Yes / No
    buttons (`CD_car_yes` / `CD_car_no`, the Church Drive app's house-button format) to everyone ticked for
    kind `car_plugged` (admins by default), with no car name (the Vauxhall's feed is stale).
    `Church Drive: Zappi car confirmation` takes the replies from both apps (`church_drive_notice_action`
    from the Church Drive app, `mobile_app_notification_action` from the Home Assistant app) and runs
    `script.zappi_car_yes` (confirmed + smart charge Waiting + "Car confirmed") or `script.zappi_car_no`
    (smart charge Manual + Stopped + "Not charging"); the Android Home card runs the same two scripts. It
    clears the confirmation on unplug (so the same car plugged in again is asked again), and asks again an
    hour after it was last asked while the car is plugged in, unconfirmed and smart charge is Waiting, never
    while it is charging. A pause in a charge does not clear it (only unplugging does), and nothing here
    stops a charge that is already going except an explicit No. Smart charge's "Cheap now" branch, "Zappi
    starts when a mode is picked" (incl. the Boosting trigger) and the 00:40 safety net all need it on; a
    person picking a mode in the app or card (context user_id set) or tapping "Charge now" counts as Yes.
    The card's Start charge unlocks by itself.
    **Android app 0.31.0:** `car_yes` / `car_no` added to the app's house-button verbs (`NoticeButton.kt`), so
    the buttons show on the notification (phone unlocked first), and `CarConfirm.kt` is the Home card
    (between the to-do highlights and Security; only while `carNeedsConfirm`).
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

## House icon set (started 2026-10-23)

- The house's own icons are `cd:` names, drawn from `companion/android/app/src/main/java/com/churchdrive/app/ui/CdIcons.kt` through `HaIcon`; Gregg's animated status is `GreggIcon.kt`. They come from the icon board (Gregg: https://claude.ai/artifact/8cnnwHR9FF6dACApaYF5YT, houses: https://claude.ai/artifact/6HfDaXTvvkXm8wwGueK23v, brand marks: https://claude.ai/artifact/LXyo1cjjgfPLdhiLgYCQ5r).
- Rules the user set: one disc size on every icon, a ring round it 11 thick with a gap of 22 to the disc that never changes size and only spins or fills like a progress ring; buttons (play, pause, stop, home) are bare glyphs because they are used everywhere, and only a status shows the whole robot; the house is the app icon's house without the block, with a door notched through the floor (medium door).
- Done in the app (0.48.3): Gregg's status icon (card and tile), the vacuum buttons, the two brush rows. Not shown anywhere yet: dock jobs, modes, the other parts. **Levels (suction, intensity) need a rethink**: not in the vacuum disc layout.
- 0.49.0 (user's testing of 0.48.3): the Home Security icon follows the alarm colour, Cleaning (Home panel and bar) uses Gregg's icon, which now has a front bumper; Clive shows an empty card with his thinking mark the moment a question is asked and the answer's card fades in (a first version of the morph: a fade and scale, not a shape morph; the card icons are not all centred yet, only weather and the new cards); Gregg and air cards; the watch clears the result after 3.5 s. Still to do: a real morph from the thinking mark into each card's icon, every card's icon centred, the Gregg icon in the Home Assistant dashboards.
- 0.50.0 (from the card board, https://claude.ai/artifact/KmNZrfPxWv88c1v12J5Cif): **card standard.** Every card (phone and watch) has an icon zone at the top, `CARD_ICON_ZONE` 88 dp with a 72 dp icon, the same place and size for the loading mark and the icon it becomes (a crossfade with a turn, `CardIconZone` in `AssistCards.kt`; a point-by-point outline morph as on the board is still to do), card icons are white (`onSurface`), and the figures open out underneath. `cardIcon`, `sportIcon`, `cardBrand` and `cardRows` (core `AssistData.kt`) decide the icon (the sport's: tennis ball, golf club, football by default, set by a `sport` field on score, scores and fixture; the house's `clive_scores` and `clive_fixture` take `sport`), a logo that replaces it when the app has it (music `app`, a car's `brand` or the first word of its name, and a competition's logo from `comp` via Simple Icons for Premier League and the UEFA competitions, falling back to the sport icon), and the rows. Twelve new kinds (energy, secure, doors, tasks, devices, lights, heat, shop, event, reminder, media, answer) are drawn by the shared `ListCardBody` (phone) and a generic watch block from `big`, `title`, `sub`, `pill`, `pill_tone`, `rows` (name, detail, value, pill, tone, marker ok/warn/todo/done/dot), `percent`, `foot`, `tone`; `clive_show_card` accepts them now, but eight now have scripts (`clive_energy_now`, `clive_house_secure`, `clive_doors`, `clive_lights_on`, `clive_devices_attention`, `clive_my_tasks`, `clive_shopping_list`, `clive_media_status`, in `ha/`, live and exposed); still without one: heat (`clive_timed_control` should show it), event and reminder (no household calendar), and the web answer (`clive_answer`). Still to do: the real outline morph in the app, a competition-logo and car-make library of our own (one colour, in the `cd:` set) where Simple Icons has none, auditing the logos against the app's style, Spotify and other third-party service cards, and the Levels icons.
- Not done: the same icons in the Home Assistant dashboards (a `cd:` icon pack for Lovelace, to publish through HACS), and the other devices' sets.

## Release and deployment

- **Google Play (since 2026-10-23, `.github/workflows/companion-android.yml`):** the first time a build has a new version, CI uploads the signed AABs to Play's **internal testing** track with the `PLAY_SERVICE_ACCOUNT_JSON` secret (a dedicated service account with release permission on the two listings): phone and watch together (`com.churchdrive.manager`, two form factors, version codes differ) and To-Do (`com.churchdrive.todo`). Release notes are the changelog section cut to 500 characters. Production stays a button in Play Console. First tries (0.48.2, 0.48.3): the service account first lacked an invite, then the Wear OS bundle was refused on the phone track (a watch bundle needs the `wear:internal` track, so the two now upload to separate tracks). If an upload fails, the Actions log says why; the manual workflow **Upload to Google Play** (Actions tab, `play-upload.yml`) uploads a version that already has its GitHub releases, without a new version number.

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

## Clive: Home Assistant side

Full notes in `ha/clive.md` (read it first), live prompt copy in `ha/clive-prompt.md`, script copies in `ha/clive-*.yaml`. Short version (as of 2026-10-23):
- Clive = the preferred Assist pipeline: openWakeWord `hey_jarvis` (local), HA Cloud speech, Claude Sonnet 5.5 via the Anthropic integration since 2026-10-23 (was Haiku 4.5; `thinking_budget` 0, `max_tokens` 1000), `prefer_local_intents` on. The assistant's name is one setting (Church Drive > Configure > Assistant name, `sensor.church_drive_assistant`).
- Exposure is global, so what must not be done by voice is simply not exposed: sirens, the alarm panel (arm-only script instead), camera motion switches, energy data, TVs and PCs (until a room-aware tool exists), the car's buttons.
- Scenes go through `script.clive_apply_scene` (library scenes, applied by `church_drive.apply_scene`); the 133 Hue scenes are unexposed. The scene list in that script is typed in by hand.
- Fast charging at a non-cheap rate needs a spoken confirmation, enforced in `clive_zappi_charge` with a 5 to 300 second window.
- **Answers come from scripts that return `say`.** Prompt rules alone could not make the format reliable, so `clive_who_is_home`, `clive_car_status`, `clive_scores`, `clive_fixture` and `clive_answer` show their own card and return the exact words; his reply is that text. `clive_show_card` draws the weather, score, fixture and music cards, `clive_show` a picture or link. Cards are drawn by the phone and watch apps. The model switch to Sonnet fixed the two that kept drifting (a single past score, how-to questions, which also leaked `<cite>` tags); its speed still has to be measured from the app's Settings timings.
- **Clive runs the heating (2026-10-23, Home Assistant only except the app reminder).** `clive_timed_control` (and `clive_timed_revert`) run a zone or the bedroom fan for a set time and put it back; `Clive: keep the climate comfortable` (every 10 minutes, `clive_climate_zone`) turns heating on, down or off against each room's comfort range; a hand change to a thermostat pauses him for `input_number.clive_climate_pause_minutes` (120); `clive_climate_control` resumes, pauses or reports. Notifications use kind `clive_actions` (added in `kinds.py`; nobody is ticked until the integration release is installed and HA restarted, then tick it in Manager). App 0.48.0 shows "Heating set by hand" with Resume on Home and a Resume Clive notification button. There is no air conditioning in HA.
- **Tuning from a session:** the live instructions and model settings can be read and changed through the Anthropic integration's `conversation` subentry (see "Tuning Clive from a session" in `ha/clive.md`); edit `clive-prompt.md` first, send it as `prompt` with `recommended: false`, and log the change in the tuning log. Live prompt and repo copy were identical on 2026-10-23.
- **House climate card (2026-10-23, app 0.48.1):** `script.clive_house_climate` (copy in `ha/clive-house-climate.yaml`) shows a `climate` card and returns the words; the weather card also takes another place's figures from a web search (`clive_show_card` `data`). Cards are drawn by hand-written routines per kind in the apps, so a new kind needs an app release. Not drawn on the watch.
- Known: the Vauxhall Astra's own sensors were unavailable on 2026-10-23, so the car card has no battery. The Python pieces (`kinds.py`, `notice_actions.py`) need an integration release and a restart to go live.
- Still to build: a custom LLM API in this integration (live scene list, room-aware device power from the satellite's area). Not before the first satellite.
- App side (0.34.2 and 0.34.3): follow-up listening after "anything else?", a shorter wait for the end of speech, and Settings shows how long each answer took. Later: Clive cards in the phone and watch apps (0.46 to 0.47), stop button while listening, no listening after a typed question (0.47.2).

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

- **Live-config changes made 2026-10-13 (no release; HA is now 2026.10.x):**
  - **Automatic to-do tasks no longer runs continuously.** `automation.church_drive_automatic_to_do_tasks`
    triggered on every change to `sensor.church_drive_device_health`, including its attributes
    (`last_heard` updates every few seconds). With `for: 15 minutes`, each change started its own
    timer, so the whole automation ran about 450 times an hour (it reads every device and eight to-do
    lists) and HA felt slow. The trigger is now a `numeric_state` trigger (`above: 0`, `for: 15 min`).
    The 15-minute sweep still catches the rest. If it's ever slow again, check how often this
    automation runs in the logbook first.
  - **Who set the alarm.** Ring confirms an arm/disarm with no HA user on the state change, so the
    alarm notification fell back to Ring's account (Hayley). `automation.church_drive_remember_who_sets_the_alarm`
    watches `call_service` for `alarm_control_panel.alarm_arm_*`/`alarm_disarm` on
    `alarm_control_panel.church_drive_alarm` by a real user (the Church Drive app, HA's app) and writes
    `First name|epoch seconds` to `input_text.church_drive_alarm_set_by`.
    "Church Drive: security alerts" uses it when under 3 minutes old and says "from the app";
    otherwise Ring's own name and "on the keypad or Ring app". A keypad change within 3 minutes of
    an app change would be named after the app user.
  - **Battery Notes needs "Battery replaced" pressed.** After swapping a battery, press
    `button.<device>_battery_replaced[_n]` (the doorbell's is `button.front_door_battery_replaced_3`,
    the rechargeable Li-ion one; `_2` is the door contact sensor). Ring reports a new battery level
    slowly, so the percentage lags after a swap.
  - **Notifications (v0.62.0 + app 0.30.x).** Taps go to the right place (`tap_link_for` in
    `notice_actions_logic.py`; `church-drive-device:<entity>` and `church-drive-health` links are for
    the Church Drive app only, never sent to the Home Assistant app). Each pop-up kind has its own
    default sound (`tools/make_notice_sounds.py` makes the tones; channels `cd_popup_<family>`).
    Device Health page for everyone (account panel, or press and hold a device's card): batteries
    amber at 40% or less and red at 20% or less, filters 30% and 10%. Google push's status row is for
    administrators only. Not built: an administrator's own upload of a custom sound per family.

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
      waiting for myenergi), Last 7 days (`octopus-card show: week`, replaced Last full day in v0.44.0: reads HA's long-term statistics `octopus_energy:<meter>_previous_accumulative_*`, per-day `change`, days keyed in HA's time zone), Gas, Octoplus.
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

- **Logo audit (2026-10-23, https://claude.ai/artifact/TKBwaNGuwdxdDDfVz4ERt3).** 64 logos the cards could need: 38 are in the app's Simple Icons set, 26 are not (Disney+, Prime Video, BBC iPlayer and Sounds, Sky, NOW, Amazon Music, AirPlay, Mercedes-Benz, Cupra, Land Rover, the UEFA, league and cup competitions, Octopus, myenergi, Eufy). Proposed own mark for the gaps: a ring (Gregg's thickness) with the short code in the middle, white, in our own library. Not built yet; waiting on the user's say-so. There is no icon library on Home Assistant: the house's `cd:` icons live only in the app (`CdIcons.kt`, generated from the design boards); Home Assistant holds only the per-mode icon choices (`church_drive.icons`) and serves the Hue `phu:` pack. A shared source (`icons/` in this repo, generating the app's set and a HACS pack) is the plan.
- **Logos in the app (0.50.1, 2026-10-23).** Card logos come from two sets, tried in this order by `CardIcon`: Simple Icons (`si:`, `assets/si-icons.json`, now 3,745 logos: 279 added from the newer npm `simple-icons` release, among them Prime Video, BBC iPlayer, Amazon Music, Mercedes-Benz, Land Rover) and our own (`ol:`, `assets/own-logos.json`, drawn by `OwnLogos` with the even-odd rule), then the card's own icon. Our own are named like Simple Icons names (`SimpleIcons.slugFor`): championsleague, europaleague, conferenceleague, facup, eflleague (one shared mark, the EFL ball and letters, for the Championship, League One and League Two), carabaocup, scottishpremiership, nationsleague, disneyplus, cupra, octopusenergy. `cardBrand` (core) maps a card's `comp` (competition), a car's make, a music `app` and an energy card's `brand` to those names; `COMPETITION_LOGOS` is the table (more specific names first). Sources, all private use: the SVGs in github.com/habereet/team-league-competition-logos (competitions), the theSVG colour set on npm (Disney+, earlier), and images the owner supplied (Conference League, Cupra, Disney+, Octopus Energy, EFL League One and Two). Each was flattened to one colour by drawing it large, keeping the darker (or, for light-on-dark images, brighter) areas and tracing them with potrace (npm `potrace`) into one outline on a 24 grid; the scripts were in the session scratchpad, not the repo (render with Chromium, threshold, `potrace`, fit to 22 x 22 centred in 24). Wikimedia Commons, Wikipedia and the EFL site were not reachable from the cloud sandbox (only npm, GitHub raw and git are), and GitHub search is blocked by the session. Still ring marks (no logo found; the board design is a ring at Gregg's thickness with the short code): European Championship, Six Nations, myenergi, Eufy. Not done: the watch does not draw these logos (it has its own small set, `ServiceIcons.kt`), and Serie A and the other European leagues and the NFL were dropped on purpose (nobody will ask). The audit board is https://claude.ai/artifact/TKBwaNGuwdxdDDfVz4ERt3.
- **Gregg levels (0.51.0, 2026-10-23).** The suction and water levels are `cd:suction-quiet|standard|turbo|max|boost` and `cd:water-low|medium|high` in `CdIcons`, shown as icon tiles by `greggLevelIcon` (`GreggIcon.kt`) when a `select.*_suction_level` or `*_water_level` entity card is drawn (`SecurityPage.kt`); the real options are Quiet, Standard, Turbo, Max, Boost_IQ and Low, Medium, High. They were drawn on the board https://claude.ai/artifact/UaVzvTNyiXBoVq4E8CpHDU to follow the Eufy app's icons (blades with round ends, M and bolt badges cut into the turbine) and baked to filled outlines: each icon was drawn from its board SVG at 1200 px in Chromium and traced with potrace (the strokes and the badge mask cannot be exported as paths any other way here). Not shown anywhere yet: the dock, cleaning-mode and parts icons (in the set only) and the cleaning mode select (Vacuum, Mop, Vacuum and mop). Next on the icon work: the cleaning mode icons, then the dock and parts ones, then other button designs.
- **Gregg mode and dock icons (0.51.1, 2026-10-23).** `cd:vac`, `cd:both`, `cd:empty` and `cd:dry` are redrawn as a disc (r57, scale 1) with the glyph cut out plus the full ring: the status pinwheel (still), the pinwheel drawn small with a thinner line beside a solid drop, a dock with a solid rounded block, and mdi water-off (a drop with a line through it) for dry mop. `cd:mop`, `cd:wash` and `cd:find` stay the older designs. `greggLevelIcon` also covers the cleaning mode select (tiles), and `greggEntityIcon` the dock buttons and find switch. Parts keep the house's own icons. Baked from the icon board by render and potrace.
- **House icons everywhere, watch logos and fixes (0.51.2, 2026-10-23).** `CdIcons.alias` maps Home Assistant's `mdi:home*` names to `cd:home` (solid) and `cd:home-away` (outline), so every `HaIcon` draws the house's house. The watch carries `assets/competition-logos.json` (the competition logos from the phone's sets) and `WatchLogos.kt`; a card whose `cardBrand` is one of them shows it in place of the symbol. On the watch what was said now sits under the mark (`TalkFace`). The phone's Clive page scrolls to the newest message again 400 ms after it changes (cards land late). The Clive prompt change is logged in `ha/clive.md`.
- **League fixtures card (0.51.3, 2026-10-23).** Card kind `fixtures` (title, comp, sport, rows of {home, away, when, time}); the phone draws it in `AssistCards.kt` (date and time at the left, badges and short names), the watch in `CliveCards.kt` (badges, v, day and time). Script `clive_fixtures` (live, exposed to Assist, copy in `ha/clive-fixtures.yaml`) builds it; `clive_show_card` accepts the kind.
- **Watch smoothness and fit (0.51.4, 2026-10-23).** `Crown.kt`: `PagerState.stepBy` goes from `targetPage` while a glide is running (notches no longer restart from a stale page), 48 px per notch, an unfinished turn is forgotten after 300 ms. `Glass.kt` reads its animated values while drawing (no recomposition per frame) and draws the air-quality gradient as two sweep-gradient arcs. `WatchData` skips an unchanged rendered answer and an unchanged entity map. `FitBox` scales content down to its box instead of scrolling (Clive's answer and card on the watch). The loading ring is one sweep-gradient arc. The phone's list cards stop at `CARD_MAX_ROWS` (7). Not changed: a touch on the ring of a face with a ring drag still turns the ring, so a sideways swipe that starts on the ring is not a page swipe.
- **Thermostat sheet (0.52.8, 2026-10-25).** Chosen from mock-ups (canvas https://claude.ai/artifact/DXaTFANvDxv7tFiC1ZBYge): option C (a status line at the bottom of each thermostat card) opening option 2A (a bottom sheet). `ThermostatSheet.kt`: `ZoneControl`/`zoneControl(entities, climateId)` read `input_boolean.clive_climate_<zone>_auto`, `input_number.clive_climate_<zone>_low`/`_high`, `input_datetime.clive_climate_<zone>_resume` and `input_text.clive_climate_<zone>_note`; the status line and sheet show only where the auto switch exists. Resume calls `script.clive_climate_control` with `zone`. The two Settings rows were removed. HA: the comfort ranges are now the four input_numbers (no `initial`, so restarts keep them: set to 18/21 and 15/19), read as `z.low`/`z.high` in `clive_climate_zone` and in `clive_timed_control`'s `t`.
- **Big-club crests (0.52.7, 2026-10-25).** `TeamBadges` looked clubs up by name on TheSportsDB, which missed Barcelona, PSG, Lille and Bodo/Glimt and matched other sports for Bayern (basketball) and Inter ("Intercity"). `KNOWN` (football-data.org crest numbers) now also lists Europe's big clubs, and `clean()` strips accents and hyphens. The numbers could not be checked from the sandbox (the proxy blocks crests.football-data.org): look at a Champions League card, and a wrong number would show as the wrong crest (a missing one falls back to the club's letters). Still by name search: Bodo/Glimt, Lens, Como, Slavia Praha, Viking, Sabah, LASK and the like.
- **Fixtures shape and keyboard gap (0.52.6, 2026-10-25).** Clive sent `games` as `{games: [...]}`, so `clive_fixtures` errored (`'dict object' has no attribute 'home'`) and he fell back to `clive_show_card` with rows the card could not read (dates only, no logo). The script now unwraps `games`, accepts `home_team`/`away_team` and names as strings or `{name, short}`; the card's `side()` accepts plain-text names and `cardBrand` falls back to `title` for `comp`. The Clive overlay and FAB now use `Modifier.assistantBottomInset` (AssistScreen.kt), which measures the parent's real bottom on screen (`onGloballyPositioned`) against `window height - max(ime, bar)`, instead of subtracting the keyboard blindly (the old padding left the page showing between Clive and the keyboard). Also `max_tokens` 1000 to 4096 (see ha/clive.md).
- **Per-zone control and slower heating (0.52.5, first built as 0.52.4 but another session's 0.52.4 was released first, 2026-10-24).** Comfort ranges are 1°C lower (downstairs 18 to 21, upstairs 15 to 19; `clive_climate_zone` `zones` and `clive_timed_control` `cfg`, heating zones only; the fan and the app's `ROOM_TYPES` colour ranges were left). Heating target is raised only below `low - 1.5` and lowered only at `high + 1` or more. The global `input_boolean.clive_auto_climate` was deleted and replaced by `input_boolean.clive_climate_downstairs_auto` and `..._upstairs_auto` (on = Clive controlled), read as `z.auto` in the zone script; the app's Settings has `AutoClimateRow` per zone. The live prompt still says "switched off for the whole house".
- **Clive adjusts targets only (0.52.3, 2026-10-24).** `clive_climate_zone` no longer turns a zone on, off or to eco: `needs_heat` is mode heat, preset none, room below the range and target under room+1, and `lower` needs mode heat, preset none. Global switch `input_boolean.clive_auto_climate` (decision `skip_disabled`, no manual-change detection, no pauses while off); the app's Settings has `AutoClimateRow` (AccountPanel.kt) reading it by a `render_template` subscription and calling `input_boolean.turn_on/off`. The helper was created off and turned on. The prompt line about the heating was changed in the live conversation entry too.
- **Cards lost after a reconnect (0.52.2, 2026-10-24).** HA drops every websocket subscription when the line drops (`HaClient` clears `subscriptions`), and `AssistModel.watchShow` only subscribed once, so after a reconnect no `church_drive_assistant_show` event ever arrived and answers were words only (the `clive_fixtures` trace showed the card fired with the right user). `watchShow(fresh = true)` now runs at the start of every `send` and wake run. The keyboard hide in `AssistScreen` is keyed on messages, state and the card, repeats after 350 ms and also uses the window insets controller.
- **Cleaning page and Show more (0.52.0, 2026-10-23).** `CleaningPage.kt` draws the Cleaning page from Gregg's entities (hero card, how he cleans, dock, parts with a two-tap reset, switches) in place of the dashboard's `cleaning` panels whenever `vacuum.gregg` is known; the dashboard's version is still used until it loads. List cards show `CARD_MAX_ROWS` rows then a `ShowMore` line (a plain tappable line, not a Material button, because `UiStandardsTest` bans raw buttons). The keyboard is folded on send and on an answer. `BrandOrIcon` (CarCard.kt) draws a make's logo by name. **0.52.1:** the page is all `Tone.Blue`; the settings are `PickRow` drop-downs (`DropdownMenu`, not a Material button); the dock is four tiles two to a row (the dry-time and empty-mode selects were dropped); each part has a `HealthBar` coloured by `partColour` from `partLifeLeft` with nominal lifetimes (filter and rolling brush 360 h, side brush and cloth 180 h, sensors and tray 30 h, worked out from Gregg's figures, because Home Assistant only gives hours left).

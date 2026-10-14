# Changelog

One line per release from the git history (newest first). Detail: HANDOFF.md and docs/HISTORY.md. Android app releases are in companion/android/CHANGELOG.md.

- **v0.63.0 + app 0.32.0** (2026-10-14): the alarm cards (web, app, widget) say who really armed or disarmed it (the person Home Assistant recorded, in `sensor.church_drive_alarm_actor`), not Ring's own account (Hayley) when someone did it from the app or dashboard
- **v0.62.0 + app 0.30.0** (2026-10-13): Notifications go to the right place and sound right: each kind has its own default sound (doorbell ding-dong, movement ping, energy, to-do and device tones; safety alarms keep the alarm), tapping a notification opens its page, camera, shopping list or device (a battery alert opens that device; device and battery kinds open Device Health; a kind with no link opens its page); new Device Health page for everyone (account panel, or press and hold a device's card), with battery, filter and not-responding status and a battery history; Google push status is now for administrators only (`tap_link_for` in notice_actions_logic.py; sounds made by tools/make_notice_sounds.py)
- **v0.61.0** (2026-10-13): Google push: the house now accepts the phone's token (the app names its phone `notify.mobile_app_x`; the house expected `mobile_app_x` and threw every token away)
- **v0.60.0 + app 0.28.0** (2026-10-13): Manager's Phones list shows the Church Drive app and the Home Assistant app on the same phone as one row ("with the Church Drive app"), and its Alerts switch covers both; the app's Google push line shows each step and which one is stuck
- **v0.59.0 + app 0.27.0** (2026-10-13): Google push can be seen working: Settings in the app says where it has got to (asking Google, token sent to the house, or what went wrong) and can retry; the house logs when a token arrives or is unusable
- **v0.58.0 + app 0.26.0** (2026-10-13): Google push for the Church Drive app (Firebase Cloud Messaging): the app gives the house its push token, and once `church_drive_fcm.json` (a service account key for the Firebase project) is in Home Assistant's config folder, notifications for the app go through Google, falling back to the app's own connection if a push can't be sent. Nothing changes until the key is there
- **v0.57.0 + app 0.25.0** (2026-10-13): one copy of each notification per phone: if a person's phone runs the Church Drive app and it's connected, the Home Assistant app on that phone (same model) isn't sent a second copy; if the app isn't connected, the Home Assistant app gets it as before. Missed notices are only kept for the app's catch-up when nothing else carried them. App: Settings > "Hide the connected notice" opens Android's switch for the small "connected" notice
- **v0.56.0 + app 0.24.0** (2026-10-13): the notification preview and "Send me a test" simulate each kind properly: its own words, picture and buttons, sent through the real path in the style you chose (new `church_drive_notice_test` event; each kind in `church_drive/people` carries a `sample`)
- **app 0.23.0** (2026-10-13): the new camera live view is the main one (Security page, camera cards, notification and widget taps); the old one is no longer used
- **app 0.22.0** (2026-10-13): doorbell and movement notifications show the camera's picture above their buttons
- **add-on go2rtc-ring 1.9.14-ring.4** (2026-10-13): more Ring logging (peer state, why a stream stopped) to find why the doorbell feed sometimes goes black
- **v0.55.0** (2026-10-13): movement alerts follow the alarm mode (Manager, Camera links: which cameras tell the house, for Disarmed, Home and Away; starts as none, battery cameras, all); the integration now sends the doorbell and movement notifications itself, with the picture and buttons from the notification buttons (so the doorbell and night-motion parts of the "security alerts" automation are no longer needed)
- **v0.54.0 + app 0.21.0** (2026-10-13): Notification buttons (stage 2 of notification settings): doorbell, cameras, alarms, smoke, vacuum, energy and more now carry buttons (view camera, open the page, lights on, send vacuum home, charge car, mute, snooze). No calling and nothing that disarms the alarm; buttons that change the house need the phone unlocked. Catch-up: the house keeps the last few notices for each person and the app asks for any it missed while offline (`church_drive/notices/missed`)
- **v0.53.0 + app 0.20.0** (2026-10-13): Notification styles: anyone can choose which notifications they get and how each appears on their phone (new `church_drive/people/mine` command for your own tick; `church_drive/people` now says which person you are)
- **v0.52.2** (2026-10-12): alarm lights fix: the Hue bridge refused the "stop flashing" command (it insists on a duration), so lights kept flashing until the bridge's own timer ran out; now accepted, and retried if missed
- **v0.52.1** (2026-10-12): alarm lights: Hue lights are flashed by the Hue bridge itself (quick, not throttled), pulses from Home Assistant are quicker and can't queue up, a method and pulse speed setting; fix: lights are saved and put back one by one (Hue groups weren't being expanded, so rooms came back wrong or not at all)
- **v0.52.0** (2026-10-12): alarm lights: the lights answer an alarm (fire/smoke/heat/CO: red-amber pulses, constant lights at night; house alarm home: red-blue + constant, away: red only; green or blue all-clear; lights restored), day or night from light sensors for the whole house, a Manager card with a lights-only test
- **add-on go2rtc-ring 1.9.14-ring.3** (2026-10-12): the Ring offer is H264 only (Ring answered the battery cameras with H265, so the player got an empty H264 track: go2rtc issue #1961)
- **add-on go2rtc-ring 1.9.14-ring.2** (2026-10-12): asks Ring for keyframes (battery cameras connected but gave no picture) and logs whether media arrives
- **add-on go2rtc-ring 1.9.14-ring.1** (2026-10-12): a private go2rtc add-on (HEVC fix + Ring signalling fix and logging) to find and fix the Ring battery cameras that go2rtc's Ring source never starts; add this repository in the Add-on Store to install it
- **v0.51.0 + app 0.19.0** (2026-10-12): steps lists show only as tasks on the to-do page, not in the Shopping section; "New steps list" is beside "Add a task"
- **v0.50.0 + app 0.18.0** (2026-10-12): steps lists work like shopping lists (one task on the to-do page, no tick of its own, a page of steps, finished by finishing them, optional reminder at Home); the inline steps of 0.49.0 are removed; `church_drive.steps_create` service
- **v0.49.0 + app 0.17.0** (2026-10-12): to-do tasks can have steps: add them when changing a task, tick each on its own in the list (dashboard card and app); kept as tick lines in the task's notes
- **app 0.16.3** (2026-10-12): live view: LIVE means a picture has arrived; falls back to Home Assistant if go2rtc sends none in 15 s
- **app 0.16.2** (2026-10-12): experimental camera view: expanded/full screen cropped in the video (fixes black areas); go2rtc Talk uses HA's relay; says why go2rtc failed
- **app 0.16.1** (2026-10-12): experimental camera view: every camera tries its own go2rtc stream for Talk
- **v0.48.1** (2026-10-12): experimental camera card: with `talk_stream` it falls back to Home Assistant's live view (listen-only) if the go2rtc stream isn't there
- **app 0.16.0** (2026-10-12): experimental camera view: Talk through go2rtc, falling back to listen-only; notification link that opens a camera answering
- **app 0.15.1** (2026-10-12): experimental camera view: fixes the black bar when expanded; says so when the camera won't take Talk audio
- **app 0.15.0** (2026-10-11): Experimental page in the app's panel: the new camera live view (layout C), Talk and Answer
- **v0.48.0** (2026-10-11): experimental `camera-experimental-card`: the new live view (16:9 video, tap-to-show buttons, bigger and landscape full screen, battery/signal, Answer and Talk); the stable camera card is unchanged
- **v0.47.1** (2026-10-11): camera card and app back to how they were before v0.47.0 / app 0.14.0 (overlapping buttons, full screen, Talk crash); a redesign follows
- **v0.45.2** (2026-10-11): the app's shopping list page scrolls (it ran under the navigation bar); app 0.8.2
- **v0.46.0** (2026-10-11): the app can ask the house for its Assist wake word (church_drive/assist/info), so the assistant is named after it (Jarvis); app 0.10.1
- **v0.45.1** (2026-10-11): searching for a shop shows the results as pins on a map to tap and pick (web and app); app 0.8.1
- **v0.45.0** (2026-10-11): shopping-list places come from a Google Maps search (any shop anywhere, e.g. a Lidl on holiday) instead of zones; an optional time; reminder at that time; the list comes up when you get near a place; Navigate button in the app (app 0.8.0)
- **v0.44.1** (2026-10-11): fix a crash in the page header lines when the lines change while a page is open (logged as an uncaught error on iPhones)
- **v0.44.0** (2026-10-11): Octopus "Last 7 days" replaces "Last full day": per-day stacked bars, electricity/gas balance bar, read from Home Assistant's long-term statistics (app 0.7.0)
- **v0.43.3** (2026-10-11): the Home page highlights show a shopping list with a cart and no tick, and tapping it opens the list
- **v0.43.2** (2026-10-11): a banner offers to reload when a page is still running older Church Drive cards than the installed update
- **v0.43.1** (2026-10-11): shopping is a section of the To-do page (no separate page), indigo blue
- **v0.43.0** (2026-10-11): shopping lists look like the task cards, can't be ticked off before their items, own Shopping page in the app (app 0.6.0)
- **v0.42.1** (2026-10-11): shopping reminders link to the Tasks dashboard's Shopping view
- **v0.42.0** (2026-10-11): shopping lists in the integration: one task per list, bought / unavailable items, carry-over of unavailable items, date and shop-arrival reminders, add-to-existing-or-new question for shared items
- **v0.41.0** (2026-10-10): Zappi smart charge switch, cheap-rate prompt and Stopping state (+ docs)
- **v0.40.0** (2026-10-10): place reminders, and Done / Snooze on task reminders
- **v0.39.0** (2026-10-10): task inbox (messages and voice to checked to-do tasks)
- **v0.38.3** (2026-10-10): charger card has one Start charge / Charging button; Android app 0.2.3
- **v0.38.2** (2026-10-10): charger and car overrides in the compact view too
- **v0.38.1** (2026-10-10): Car card Updated/Refresh; charger Unlock and Charge now/Pause
- **v0.38.0** (2026-10-04): Phone locations (Location switch per phone, location-old warning)
- **v0.37.0** (2026-10-04): Device access (limit devices to people)
- **v0.35.1** (2026-10-03): Eco on a thermostat that is off switches it on first (one tap, not Heat then Eco)
- **v0.36.0** (2026-10-03): People manager (picture, places, phones, cars, alerts, to-dos, arrivals)
- **v0.35.0** (2026-10-03): Car card (cars given to people); charger card back to the Zappi
- **v0.34.0** (2026-10-03): mode icons in one list (Icon Styles card)
- **v0.33.1** (2026-10-03): charger card shows the cars; handoff notes
- **v0.33.0** (2026-10-02): Mirror card; light cards retry the scene library
- **v0.32.2** (2026-10-02): media card wakes and shows sleeping TVs; handoff notes
- **v0.32.1** (2026-10-02): Satellite/Street switch on HA maps, nearest zone for a person's place
- **v0.32.0** (2026-10-02): Google maps through Home Assistant, on Home Assistant's own maps
- **v0.31.3** (2026-10-01): Clip player controls
- **v0.31.2** (2026-10-01): Remove camera events from before the clip fix
- **v0.31.1** (2026-10-01): Camera clips play on phones, live view starts muted
- **v0.31.0** (2026-10-01): Zone map fix, panels fill their width, notification boxes, Google maps, iPhone pop-ups
- **v0.30.3** (2026-10-01): Device health fixes don't hold up start-up
- **v0.30.2** (2026-10-01): Locations Edit/Save, zone map, satellite maps everywhere
- **v0.30.1** (2026-10-01): Camera clips download
- **v0.30.0** (2026-10-01): Camera events, camera links and per-person places
- **v0.29.2** (2026-10-01): Nav bar icons shrink to fit
- **v0.29.1** (2026-10-01): Camera pop-up video fills the box, with drag and zoom
- **v0.29.0** (2026-10-01): Camera Card
- **v0.28.0** (2026-10-01): Auto Layout fills blank space; Manager on Auto Layout
2026-10-01 Release v0.27.1: Notifications table's All column ticks and unticks everyone
2026-10-01 Release v0.27.0: Devices page, Media/System/Safety/People cards, cheap-rate view
2026-10-01 Release v0.26.0: people and notifications, one To-do section for everyone, task pop-up
2026-10-01 Release v0.25.0: page header lines, forecast and to-do summary; Task List card with repeating tasks
2026-09-30 Release v0.25.0
2026-09-30 Release v0.24.2: Auto Layout visual editor fix
2026-09-30 Release v0.24.2
2026-09-30 Release v0.24.1: Octopus violet for gas
2026-09-30 Release v0.24.1
2026-09-30 HANDOFF: v0.24.0 live on Mobile, Zappi status
2026-09-30 Release v0.24.0
2026-09-30 Release v0.23.0
2026-09-29 Release v0.22.1: chips without the open/close-all button
2026-09-29 Handoff: v0.22.0 released, beta pin
2026-09-29 Release v0.22.0: smoother panels, floating chips with open/close-all, page headers with alerts, back-to-top by the nav bar
2026-09-29 Handoff: v0.21.0 released with a restart, beta pin
2026-09-29 Release v0.21.0: panel +/- toggle, sliding panels, Go to footer, sticky jump-to chips, 84px alarm ring, scene tile colours
2026-09-29 Release v0.20.0: Auto Layout Card, nav bar icon templates, no flicker on rebuild
2026-09-28 Handoff: v0.19.0 released with a reload only; beta pin

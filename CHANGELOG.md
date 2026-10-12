# Changelog

One line per release from the git history (newest first). Detail: HANDOFF.md and docs/HISTORY.md. Android app releases are in companion/android/CHANGELOG.md.

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

# Church Drive Android: changes by version

The version is set in `version.properties` and shown in the app (top bar for administrators, and Account > About). Each CI build
also has a build number, shown beside it. The first build of each version is kept as its own release, `android-v<version>`,
with the APK, so there is a record of what shipped. Newest first.

## 0.8.1

- Searching for a shopping-list place shows the results as red pins on a satellite map: tap the right one, then "Use this place".

## 0.8.0

- **Shopping lists: places from a Google Maps search.** Search for a shop anywhere (for example "Lidl Protaras Cyprus") instead of picking a zone, and
  give the list a time as well as a date. A list with a place has a **Navigate** button that opens Google Maps with directions to it. Needs
  Church Drive 0.45 in Home Assistant and a Google Places key in its options for the search.

## 0.7.0

- **Octopus: Last 7 days** replaces Last full day on the Energy page (needs nothing new in Home Assistant). A stacked bar per day in the
  electricity and gas colours, the week's electricity/gas balance as a colour bar, and tap a day for its cost, kWh and cheap-rate share.
  Days Octopus hasn't sent show as gaps with a note saying how far behind each fuel is. It reads the long-term statistics Home
  Assistant keeps, so late days fill in by themselves.

## 0.6.1

- **Shopping is a section of the To-do page**, not a button of its own in the bottom bar (it showed as "Shoppin" and crowded the
  bar). The section comes from the dashboard's To-do page, like the others.
- Shopping uses an indigo blue instead of green.

## 0.6.0

- **Shopping lists** (needs Church Drive 0.43.0 or later in Home Assistant): a **Shopping** page of its own in the bottom bar. Each
  list opens as a full page you can scroll and leave with Back: tick items Bought or Not available, add several at once with commas,
  and change the list's name, people, date and shop places from Edit. A new list can start with what wasn't available last time.
- **A shopping list is one task.** On a to-do list it shows a cart instead of a tick and opens the list when tapped. It is finished
  by finishing its items, never by ticking it. On Home it shows with a cart and no tick too.
- **Sharing shopping**: when shared or spoken text has shopping in it, the check box asks whether it goes on a list you already have
  or a new one.
- A shopping reminder (the list's date, or arriving at a shop) opens that list when tapped.

## 0.5.1

- **Connection fix**: the app no longer sends anything to the house before the sign-in has finished. Sent early, Home Assistant
  took it for a failed sign-in and dropped the connection, which showed as "Can't sign in just now. Trying again…" and made
  voice and share fail. It waits for the connection first.
- **The right message when the house says no**: "Church Drive in Home Assistant needs updating" now shows only when Home Assistant
  really doesn't have the command. Any other refusal says "The house couldn't do that" and what Home Assistant said.

## 0.5.0

- **Smart charge** on the car charger: a **Smart charge** switch under the Start charge button, saying what it's doing
  ("Waiting for the cheap rate, 00:30"). It charges a plugged-in car at the cheapest Octopus rate by itself, and anyone
  picking a mode takes over. Needs the smart charge set up in Home Assistant.
- **A question before charging at the normal rate**: pressing **Start charge** (or **Fast**) when the rate is high and the cheap
  rate is coming up asks **Charge at 00:30** or **Charge now at 25.3p**. The charger widget's Start charge opens the same
  question in the app.
- **Stop shows "Stopping…"** until the charger's readings catch up (they refresh about once a minute; the house now asks for
  fresh ones every few seconds after a mode change), instead of saying "Charging" for up to a minute.

## 0.4.1

- **Update prompts**: the app now checks for a newer version each time it comes to the front, not only when it starts afresh,
  so the update banner shows up without closing the app first. It checks at most every ten minutes.

## 0.4.0

- **Reminders at a place**: in a task's edit box, **Remind me at a place** (Home, or any place the house knows). You're reminded
  each time you arrive there, until the task is done.
- **Done and Snooze on task reminders**, for both place and time reminders. **Done** ticks the task off; **Snooze** asks for
  5, 10, 20, 30 minutes or 1 hour. A time reminder comes back after the snooze; a place reminder comes back after it only if
  you're still there (if you've left, it waits for your next arrival). Needs Church Drive v0.40.0.
- Setting a place is on a task you edit; a brand-new task from the app gets its place when you edit it (the dashboard can
  set it when adding).

## 0.3.1

- **Tasks from text and voice, quicker and clearer**: no waiting box for a quick answer (a small "Adding…" or "Reading…"
  only shows if it takes more than a moment). If the house doesn't have the task inbox yet it now says so straight away
  ("Church Drive in Home Assistant needs updating to 0.39.0 or later"), instead of waiting several seconds and giving a vague
  error. Waiting for the connection still lasts up to about eight seconds.

## 0.3.0

- **Tasks from messages**: highlight text in any app and choose **Church Drive to-do** in the menu, or use **Share** >
  **Church Drive to-do**. The house reads it with AI and splits it into tasks, each on the list it belongs on (shopping,
  cleaning, a person's own, everyone's) with a day if the text gives one. Check them first: untick any, change the words,
  pick a different list, then **Add**. Tasks waiting to be checked (from a voice request, or if you closed the box) show
  when you next open the app. Needs Church Drive v0.39.0 and an AI Task in Home Assistant.
- **Tasks by voice** without Gemini or Assistant: a microphone in the top bar, an **Add task** Quick Settings tile (add it
  from the tile editor), and **Add task** in the app icon's long-press menu. It uses the phone's own speech box, listens only
  when you tap (never in the background), and the tasks go straight onto the right lists; the app shows where each went. There is also an **Add task** widget
  (one cell, just the microphone), and the **To-do** widget has the same microphone beside its heading.

## 0.2.3

- **Car charger**: one button instead of Unlock / Charge now / Pause, always shown on the card. Grey **No car connected**,
  tappable **Start charge** (unlocks if needed, then Fast) while a car's plugged in, and a teal **Charging** while it
  charges (including the 0 W gaps between bursts). The lock isn't shown: the charger reports it as locked even mid-charge.
  The widget has Start charge (and Stop) while a car's plugged in and not charging.

## 0.2.2

- **Car charger widget**: while a car's plugged in, **Unlock** (when the charger's locked) and **Charge now** (Fast; the
  house unlocks the charger) come first, and Charge now becomes **Pause** while charging. A one-row widget keeps them and
  Stop.

## 0.2.1

- **Car charger**: while a car's plugged in, two buttons above the modes, as on the dashboard: the lock ("Locked · Unlock"
  unlocks the charger; "Unlocked" is greyed out, as it locks itself again next time a car's plugged in) and **Charge now**
  (unlocks if needed, then Fast), which becomes **Pause** while charging.
- **Car**: when a plugged-in car's figures are over 20 minutes old, "Updated 20:41" and **Refresh** (wakes the car so it
  sends them now).

## 0.2.0

The first numbered version: everything in the app up to the widgets.

- **Widgets** (Material You, light and dark, see-through, follow the phone's colours; state colours stay):
  Alarm, Lights (one room with all its scenes in their colours, or several rooms), Thermostat, Scenes, Shortcuts, Fan and air
  (any fan, air purifier or air conditioner), Blinds, Vacuum, Car charger, Weather, People, Security summary (up to four rows),
  To-do (the signed-in person's tasks from every To-do category, in their colours), Jobs, Camera, Gauge and Gauge cluster.
  Every size from one row to tall; one-row cards show the buttons that fit and you choose which; tapping opens the right page
  (a camera opens its live view). Long-press a widget, then its settings, to change what it shows.
- **Account panel** sliding from the right: Settings (notifications, location), About (version, update check), and for
  administrators Energy and Devices.
- **Notifications and location from the app**, with no Home Assistant app needed; no permanent notice for location.
- **Sign in** with a Home Assistant username and password (refresh tokens, so it stays signed in).
- **Climate**: graphs matching the dashboard, weather in the Climate section, thermostat Eco switches Heat on first.
- **Security**: alarm above the notices with its countdown, camera events and clips.
- **To-do**: edit, delete and tick off tasks.
- **Icon**: the Church Drive logo as an adaptive, themed icon.

## 0.1.x

Test builds numbered by CI run (0.1.1, 0.1.2 ...) while the app was being built.

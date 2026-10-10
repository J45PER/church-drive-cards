# Church Drive Android: changes by version

The version is set in `version.properties` and shown in the app (top bar for administrators, and Account > About). Each CI build
also has a build number, shown beside it. The first build of each version is kept as its own release, `android-v<version>`,
with the APK, so there is a record of what shipped. Newest first.

## 0.3.0

- **Tasks from messages**: highlight text in any app and choose **Church Drive to-do** in the menu, or use **Share** >
  **Church Drive to-do**. The house reads it with AI and splits it into tasks, each on the list it belongs on (shopping,
  cleaning, a person's own, everyone's) with a day if the text gives one. Check them first: untick any, change the words,
  pick a different list, then **Add**. Tasks waiting to be checked (from a voice request, or if you closed the box) show
  when you next open the app. Needs Church Drive v0.39.0 and an AI Task in Home Assistant.
- **Tasks by voice** without Gemini or Assistant: a microphone in the top bar, an **Add task** widget, an **Add task** Quick Settings tile (add it
  from the tile editor), and **Add task** in the app icon's long-press menu. It uses the phone's own speech box, listens only
  when you tap (never in the background), and the tasks go straight onto the right lists; the app shows where each went. There is also an **Add task** widget
  (wide, with its name; shrink it for just the microphone).

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

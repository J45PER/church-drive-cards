# Church Drive Android: changes by version

The version is set in `version.properties` and shown in the app (top bar for administrators, and Account > About). Each CI build
also has a build number, shown beside it. The first build of each version is kept as its own release, `android-v<version>`,
with the APK, so there is a record of what shipped. Newest first.

## 0.20.0

- New: Account > Settings > Notification styles. It lists every kind of notification the house sends, grouped like Manager's table. For each one you can turn it on or off for yourself, and choose how it appears on this phone: Alert (a pop-up with sound that rings through silent mode and Do Not Disturb, for safety alarms only), Pop up (a banner with sound), Pop up quietly (a banner, no sound or buzz), Quiet (only in the notification shade) or Off on this phone. "Send me a test" shows how it will look, and "Android settings" opens Android's own settings for it.
- Notifications now use proper Android channels, so banners really pop up. Before, every one used a normal channel, which Android shows only in the shade, which is why few seemed to arrive. Safety alarms keep ringing loudly unless you change them.
- Needs Church Drive 0.53.0 in Home Assistant.

## 0.19.9

- A test: the Android 13 "themed icon" layer is switched off, to see whether the Files app's Downloads list then shows the logo instead of a white circle for the downloaded app files. The home-screen icon is the full-colour logo as before. (The themed-icon pictures are still in the project, so it is easy to put back.)

## 0.19.8

- Notifications no longer carry the extra logo picture on the right of the card: the app icon on the left (now the right logo) is all they show.

## 0.19.7

- Fixed the app icon showing as a plain white circle (in the notification, the downloads list and on phones with a medium-density icon set): the new logo had only been added for the sharpest screens, so most phones were still using the old artwork, which disappears on the white background. The logo (and the themed icon) is now provided for every screen density.

## 0.19.6

- Notifications show the Church Drive logo in the card itself (the round picture beside the title), and the accent colour matches the logo. The single-colour house stays in the status bar until you have a separate top-bar icon.

## 0.19.5

- The little icon in notifications (such as "Church Drive is connected") is now the house instead of a plain white circle. It is a simple outline, because Android draws that icon in a single colour and the number is too small to read there.

## 0.19.4

- New app logo: the gradient house with "63", on a white background, in the app icon, the Android 13 themed icon (a plain outline with the number cut out) and the splash screen.

## 0.19.3

- **Car card**: the old-figures line says whose time it is and how old it is: "Car last reported 00:46 · 23h ago" (it was "Updated 00:46"). The time is when the car last sent figures, not when Home Assistant last checked.

## 0.19.2

- The icon keeps the artwork's own vertical position (the whole picture is scaled down as it was drawn, not re-centred on the house), in the app icon, the themed icon and the splash screen.

## 0.19.1

- The app icon is on a white background again, and the house is smaller inside the icon so it isn't cut or crowded by round and rounded-square icon shapes. The splash screen matches (white, with the same house).

## 0.19.0

- Steps lists show only as tasks on the to-do page, never in the Shopping section. "New steps list" is now beside "Add a task" on the to-do list, not on the Shopping card.

## 0.18.1

- The app has a new icon (the Church Drive house with its glow) and a matching splash screen. The icon sits on a dark background and, on Android 13 and newer, has a themed version that takes your wallpaper's colours.

## 0.18.0

- Steps lists replace steps inside a to-do task. A steps list works like a shopping list: it is one task on the to-do page (no tick of its own, and no steps shown under it), and you work through its steps on its own page, one tick each. The task finishes itself when the last step is done. Make one from the Shopping card's "New steps list"; it can remind you at Home.
- The inline steps of 0.17.0 are gone.

## 0.17.0

- To-do tasks can have steps. Edit a task and use "Add a step"; each step is ticked on its own in the list, and the line under the task says how many are done ("2 of 5"). The steps are kept in the task's notes, so the dashboard's task list shows and ticks the same ones.

## 0.16.3

- Live view: "LIVE" now means a picture has actually arrived, not just that the connection is up. A camera slow to start sending (a battery camera) shows "Connecting…" until its first picture. If go2rtc sends no picture within 15 seconds the view falls back to Home Assistant's connection (listen-only), and says so.

## 0.16.2

- Experimental camera view: expanded and full screen are drawn a different way. The picture is no longer a view bigger than the screen that is moved and scaled (that left black areas and showed the wrong part); it is exactly the box, and the video is cropped to the part in view. Dragging and pinching still move and zoom it, and the doorbell's tall picture is still cropped to 16:9 and can be dragged up and down.
- Talk through go2rtc now also uses the relay server Home Assistant gives for its own live view, so it can work on mobile data and not only on the home Wi-Fi.
- When go2rtc can't connect, the view now says why (for any camera) before carrying on listen-only.

## 0.16.1

- Experimental camera view: every camera now tries the go2rtc stream named after it (`driveway`, `entrance`, `garden`, `living_room`, `front_door`) for Talk, not just the doorbell. A camera with no such stream just listens, as before.

## 0.16.0

- Experimental camera view, Talk through go2rtc: for a doorbell (or a camera whose card sets `talk_stream`) the live view now connects to the go2rtc stream of that name (a doorbell uses its own name, e.g. `front_door`) through Home Assistant's WebRTC Camera integration, and sends the phone's microphone to it. Home Assistant's own WebRTC could only listen to a Ring camera. If there is no such go2rtc stream the view says so and carries on, listen-only, through Home Assistant.
- A doorbell notification can open the live view answering: give the notification the link `church-drive-camera:camera.front_door_live_view?answer`.

## 0.15.1

- Experimental camera view: the picture no longer stops at half the screen with a black bar on the right when expanded or full screen. It now fills the box and starts centred, and dragging and zooming work (the live picture is drawn on a surface that scales properly).
- Talk: if the camera's answer shows it won't take audio from the phone (as Ring's connection through Home Assistant may not), the view now says so and drops back to listening, instead of looking as though it is working.

## 0.15.0

- A new Experimental page in the panel that opens from your name, with every camera as a tile that opens the new live view (the cameras elsewhere in the app are unchanged):
  - The video is a 16:9 box for every camera; the doorbell's tall picture is cropped to it and can be dragged up and down.
  - Normal: two small icons on the picture (bigger, full screen); under it a timer and Hang up / Talk / Sound, then Light, Snapshot and Events.
  - Bigger (remembered for each camera) and full screen (the phone turns to landscape): tap the picture to show or hide the buttons, which hide after 3 seconds. Battery and signal stay on the picture. In full screen the call buttons are bottom left, + (Light, Snapshot, Events) bottom right, exit top right.
  - Talk sends the phone's microphone to the camera over the live connection; it needs the camera or Home Assistant to accept audio from the app. A crash when switching Talk on or off is fixed.
  - When the doorbell rings its tile has Answer, and the live view opens with a big Answer button that turns on the sound and the microphone.

## 0.14.1

- Back to the 0.13.0 camera view. The 0.14.0 camera changes (Answer, Talk, bigger and full-screen views, status icons, timeline) are taken out for now: the buttons overlapped, disappeared in full screen, and Talk crashed (a disposed WebRTC audio track). They will come back with a new design.

## 0.13.0

- Jarvis is full screen only. The small sheet is gone (it kept sliding and was a second layout to keep matching), along with the "Voice opens full screen" setting. He opens and closes the same way as every other pop-up.

## 0.12.9

- Opening Jarvis from the microphone button, the widget or the Quick Settings tile no longer lets Android slide his screen up and down: the system's own open, close and task animations are switched off for it (it runs as its own screen), so only the card slides and the backdrop fades.

## 0.12.8

- A new setting, Classic Jarvis colours (Account > Settings), keeps his original indigo, purple and green instead of following the phone's theme.

## 0.12.7

- Jarvis now uses your phone's own theme colours (the Material You accent colours from your wallpaper) instead of fixed indigo, purple and green: the cloud, the glow behind the microphone, the thinking dots, your message bubbles and the status text all follow the phone.

## 0.12.6

- Every pop-up now opens and closes the same way: the page behind fades dark in place, and the pop-up rises from below and fades in (and leaves the same way backwards). This covers the task editors, the shopping list, light settings, the camera viewers, the media remote and Jarvis. (Small yes/no boxes keep Android's own look.)
- When Jarvis closes himself after "that's it", his screen now leaves with the same exit instead of vanishing.

## 0.12.5

- The dimmed, frosted backdrop behind the small assistant now fades in and out in place. Before, it was part of the window, so it slid up and down with the card. Now only the card slides, and the backdrop (and its blur) fades by itself.

## 0.12.4

- Jarvis says Diane's name properly ("Die Ann"): the house is told to spell it that way for the voice, and the app shows it as "Diane" on screen and speaks it that way in his own welcome.

## 0.12.3

- Jarvis's spoken welcome says "Welcome back Jamie" with no comma, which the voice turned into a long pause before the name.

## 0.12.2

- When you dismiss Jarvis ("that's it", "no thanks", "nothing else", "bye", "thanks"), he answers and then closes instead of listening again.

## 0.12.1

- Jarvis waits longer before deciding you have finished speaking (about 1.7 seconds of quiet after "Hey Jarvis", and a longer pause on the phone's own listening when you tap the microphone), so he cuts you off less.

## 0.12.0

- Press and hold a light, zone or room to open its settings full screen. What it shows follows the light: brightness (with 10 to 100% shortcuts) for a dimmable light, a warm-to-cool slider with Candle, Warm, Neutral, Daylight and Cool shortcuts for a white one, a colour wheel with swatches (and a White tab if it can do white too) for a colour one, and On/Off for a light that only switches.
- A room, zone or group has All lights (one set of controls for the whole group) and Each light (a switch per light; tap one to open its own settings), so rooms that only expose a group can still set each bulb.
- The first time a light with settings is on screen, a one-line "Hold for colours" hint shows under it, until you have held one.

## 0.11.8

- The bottom of a shopping list (and the other full-screen editors) is no longer cut off: there is now room to scroll the last buttons clear of the phone's gesture bar.

## 0.11.7

- Jarvis's cloud now loops smoothly: the colours used to reach the end of their drift and jump back to the start, and also jumped when the state changed.

## 0.11.6

- The small Jarvis cloud in the minimised sheet is no longer cut off in a square: its glow has room to fade out.

## 0.11.5

- Jarvis's cloud is now drawn exactly like the approved design: the same colour positions, soft edges and sizes (the earlier version had the three colours piled on top of each other, with an extra halo and white centre).
- The listening rings follow the design too: small, soft and fading.

## 0.11.4

- Jarvis's cloud is livelier (between the old calm and lively): the colours drift further and a little faster.
- Each action has its own movement: listening blooms outwards in soft rings that grow with your voice, thinking beats like a heart, speaking sends a wave of swelling through the colours, and idle breathes slowly.

## 0.11.3

- The dimmed background behind the small assistant fades in and out instead of sliding down with the sheet, and is frosted (blurred) on Android 12 and newer.
- Jarvis's replies are speech bubbles, in the small sheet and in the conversation.
- In the full-screen conversation the larger Jarvis cloud and his status stay at the top and the chat starts below it.

## 0.11.2

- The glow behind the thinking dots is much smaller and softer: it hugs the three dots.

## 0.11.1

- **Thinking dots:** darker grey, and the colour glow is now exactly behind all three dots (it had been drawn off to the side).
- The small cloud beside his name keeps its indigo, purple and green instead of washing out to white.

## 0.11.0

- **A calmer, cleaner Jarvis.** The small sheet has no coloured bar and uses a small living cloud beside his name; what you said and his answer are
  plain words, with one scrolling row of suggestions and the controls underneath (keyboard, a grey microphone with a soft glow behind it, close).
- **Full screen:** a big, slow cloud while you talk; a Gemini-style colour glow along the bottom edge that strengthens while he listens, thinks or
  speaks; in a conversation your words are a soft gradient pill and his are plain text, with a typing box and the glowing microphone.
- **Thinking:** three small grey dots, each moving in its own time, with one soft glow of all three colours behind them.
- **The microphone's glow is at the very back**, behind the typing box, buttons and suggestions, never over them.
- The cloud moves much more slowly than before.

## 0.10.4

- **Fix: the full-screen assistant is solid again** (it faded to see-through at the bottom, showing the app behind it), and tapping the microphone
  again no longer stacks a second copy on top of the first.
- The microphone button is a plain grey button with the glow clearly behind it, and the orb keeps its indigo, purple and green instead of washing out to white.

## 0.10.3

- **Fix: asking a question no longer starts streaming the microphone to the house.** Home Assistant says "no audio handler" for a text question with a
  null, and the app read that as handler 0, so every question sent audio nobody was listening for (the "non-existing handler 0" errors in Home
  Assistant). Only the wake word listening streams audio now.
- **The voice stays live.** After Jarvis answers (and finishes speaking) the app listens again for your follow-up, and stops by itself after a moment
  of silence; if the house asks you something it listens at once.
- **Jarvis looks more like part of the app:** the glow stays with the conversation, three pulsing dots while he thinks, bubbles sized to their words,
  a tinted background, and the microphone's glow no longer runs off the screen edge.
- **"Hey Jarvis" is easier to find and diagnose:** the first-time and what's-new screens offer to turn it on; Settings shows, when it is on but not
  listening, the last things the voice pipeline did.

## 0.10.2

- **The assistant speaks its introduction.** The first time ever it introduces itself (name, what it can do, what is new), out loud in the house's
  voice as well as on screen; after each update it just says what is new. It plays when you open the assistant (the widget, the microphone, or the
  wake word) after installing a new version.
- **Climate:** the floors' graphs now look like the thermostat cards': the comfort band for the room, humidity limits, smoothed. The floor cards are grey
  again (they had taken the panel's colour, which turned amber whenever one room was warm), and the Home page's Climate icon follows the home's
  climate score (green for a good score).

## 0.10.1

- The assistant looks more like Siri or Gemini: a soft glow of indigo, purple and green clouds that blend and drift, and swell with your voice.
  The text in it is readable again, and it is named from the house's wake word (Jarvis) wherever it appears. A green microphone shows in the
  top bar while it is listening for the wake word, and Settings says why if it isn't. (Needs Church Drive 0.46 in Home Assistant for the name.)

## 0.10.0

- **A wake word for the assistant.** With "Listen for the wake word" switched on (Account > Settings, asks for the microphone), say the wake word
  of the house's Assist pipeline, such as "Hey Jarvis", while the app is open and the assistant opens by itself. The microphone goes to Home
  Assistant, which listens for the wake word and then does the speech-to-text, the answer and the voice, exactly as a Voice Assistant satellite does. It listens
  only while the app is on screen. The assistant is named after its wake word (Jarvis) in its introduction and its title; with no wake word it is
  "your house assistant". Needs a wake word engine (the openWakeWord add-on) and a wake word on the pipeline.

## 0.9.0

- **A voice assistant that looks like part of the app.** The widget, the microphone in the app, the Quick Settings tile and the long-press
  shortcut open it as a sheet over whatever you are doing (over the home screen from the widget, without opening the app); swipe it up for the
  full screen (an orb, then the conversation) and down to close. A turning indigo, purple and green gradient shows when it is listening. What you say
  goes to Home Assistant's own Assist, so lights, heating, the alarm, lists and your own automations all work, and the answer is spoken in the
  house's voice. If the house doesn't understand a sentence it offers to add it as a task. The first time after each update it introduces itself
  and what is new. Account > Settings > "Voice opens full screen" makes the full screen the default. The microphone is asked for the first time.
- **Easier to reach the account and settings:** a round button with your initial, and a left swipe anywhere that doesn't scroll sideways opens it.
- **Manager is one button** in the account panel, opening a page of its sections (people, cameras, reminders...), each its own page; Back goes one step at a time.
- **Security:** the green messages under the alarm fade away after a few seconds, or swipe off sooner; amber and red ones stay (and a green one comes back if it starts to warn). Panels such as Safety fold away when you tap their heading.
- **Climate:** tap a floor (ground, middle, top) to see each room's temperature and humidity graph for the last day.
- **To-do editors are full-screen pages** (edit a task; the tasks to check from shared text), like the shopping list, with Cancel, Save and Delete as the app's own buttons.
- **Consistent colours:** cards inside a dashboard panel (Safety, the climate floors, cleaning, energy, the manager's cards) take their panel's colour, as on the dashboard, instead of grey.

## 0.8.2

- The shopping list page scrolls up and down again, and its bottom buttons are no longer hidden by the navigation bar.

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

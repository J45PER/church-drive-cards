# Church Drive Android: changes by version

The version is set in `version.properties` and shown in the app (top bar for administrators, and Account > About). Each CI build
also has a build number, shown beside it. The first build of each version is kept as its own release, `android-v<version>`,
with the APK, so there is a record of what shipped. Newest first.

## 0.87.0

No app changes. The Home Assistant integration now removes a phone's old duplicate Church Drive app registrations, locates people from this app only and sends notices only to this app.

## 0.86.0

- **"Which car is plugged in?"** replaces "Confirm car to charge tonight": the Home page card and the notification now have **Primary** (the car on the Octopus app, today the ID.5: charges in the 23:30 to 05:30 cheap window and whenever Octopus schedules it), **Additional** (any other car: cheap window only) and **No**. The old Yes button still works and counts as Primary. Needs the Home Assistant changes described in `ha/zappi-octopus-intelligent.md`.
- **The car charger card has the dashboard card's choice of car.** While a car is plugged in, Primary / Additional shows under Smart charge (picking one on a car nobody has answered for also confirms it), and the card says the Octopus rate now.
- **Octopus's charging plan on the charger card.** Under Smart charge the card lists the next planned charges for the car Octopus controls ("02:30 to 04:30 · 15.6 kWh · Smart", or Boost while Boost is on), "Now:" for one in progress. The Energy page shows the same card.
- **Boost (Octopus) on the charger card.** For the Primary car while it is plugged in, a Boost tile turns Octopus's bump charge on or off. Boost charges now, but Octopus bills it at the standard rate, not the cheap one, and the card says so.
- **The Car card lists the ID.5.** Its battery % and Octopus's charging plan ("Octopus charging until 05:30", "Next Octopus charge 02:30 to 04:30") come from the Octopus integration; it shows as plugged in and charging when it is the Primary car on the Zappi. Octopus gives no range or place for it.

## 0.85.0

- **Camera pictures are refreshed by Home Assistant, not by opening the app.** The camera cards no longer press each camera's Take Snapshot button when you open the page (that woke battery cameras and, with several cameras, put five "pressed Take Snapshot" lines in the Activity log under your name). Church Drive in Home Assistant now asks each camera itself: every 60 minutes for a camera on mains power and 180 for one with a battery, counted from the camera's last picture, event (motion or ring) or button press, so a busy camera is never asked. The orange "old" badge stays, and the pop-up's **New snapshot** button still works. Needs Church Drive 0.85.0 or later in Home Assistant.
- **Activity log:** a camera's Take Snapshot button is never listed, and Clive's heating presets read naturally ("set Downstairs to no preset", "set Downstairs to Eco").

## 0.84.0

- **Pause after a change.** A thermostat's settings sheet has a new row, Pause after a change, to set how long the assistant leaves a thermostat alone after somebody changes it (15 minutes to 12 hours, a quarter of an hour at a time; both thermostats use it). It is now 30 minutes instead of 2 hours. Needs Church Drive 0.84.0 or later for the web sheet; the number already exists in Home Assistant.

## 0.83.0

- **Activity log: "overruled" instead of every device change.** The general lines for devices turning on or off outside Home Assistant (0.54.3) were too many, so they are gone. The log now lists only when something **Clive set** (the heating target or mode, a light, a switch or a fan) is **changed again from outside the app**: the thermostat's own dial or app, a wall switch, a remote or a schedule in the device. Each line names the device, what changed and when he had set it. Pick **Overruled Clive** in the Actions list to see just those. Needs Church Drive 0.83.0 or later in Home Assistant.

## 0.82.0

- Shares its sign-in with the Play-signed Church Drive Satellites app (its signing certificate is added to the trusted list), so Satellites signs in automatically.

## 0.81.0

- Version moves with the shared number. No other change to the phone and watch apps.

## 0.80.0

- One version number for every Church Drive app and the Home Assistant integration from here (`node tools/versions.mjs`, docs/VERSIONING.md). The phone app and the watch app move from 0.54.4 to 0.80.0 with no other change.
- `core` now lets the new Church Drive Satellites app take this app's sign-in, like To-Do does.

## 0.54.5

- The assistant takes the heating off Eco by himself when somebody comes home (the Clive log said he would ask).

## 0.54.4

- The assistant's away rule now puts the heating on Eco instead of turning it off (Home Assistant side, noted here for the Clive log).
- The Home page reminder when Clive has paused on a thermostat says "Heating changed" instead of "Heating set by hand": the change may have come from the Nest's own schedule or away handling rather than a person.

## 0.54.3

- **Activity log shows devices turned on or off outside Home Assistant.** When a light, switch, fan, humidifier or thermostat turns on or off with nothing in Home Assistant asking (its own app, a wall switch, a remote, a schedule in the device), it is listed under the device's name, marked "outside Home Assistant". Pick **Devices (own app or switch)** in the Actions list to see just those. Home Assistant can't tell an app from a switch, so they share one label. Needs Church Drive 0.72.0 or later in Home Assistant.

## 0.54.2

- Asking Clive about tomorrow's weather (or the day after, up to five days) now shows that day's card, with its high, low and hours, instead of only speaking. Today's weather card also shows today's high and low (after midnight it was showing yesterday's). Listening: the phone now asks its own noise suppression to quieten a television or a fan behind your voice, and Clive decides you have finished speaking after 0.9 seconds of quiet instead of 1.2, so he moves from listening to thinking sooner.
- Time zones everywhere: when the phone is not in the home's time zone, every time in the app (cards, graphs, camera events, car, alarm, widgets, notifications and the hours on Clive's cards) shows the phone's time with the home's after it, "14:05 (12:05 home)". In the same zone it shows one time, as before.
- Time zones: the phone now tells the house which time zone it is in. When it is not the home's, Clive gives the time where you are as well as the home time ("It's 14:05 here, which is 12:05 at home"), says which times are home times, and treats "here" as where you are. Between midnight and 03:30 (your time) when you say tomorrow, he first asks whether you mean today or tomorrow, naming both days, in case you have not been to bed yet.

## 0.54.1

- **Activity log filters are drop-down lists.** Dates (today, yesterday, last 7 days, last 30 days, all dates, or **pick a range** with an exact start and end date and time), Actions (**all actions**, people, the assistant's asked or automatic changes, or both, coming and going), Time of day and Who. A range has its own times, so the time-of-day list is hidden while it is used.
- **Says why it is empty.** If Home Assistant is running a Church Drive older than 0.71.0 (which has no activity log, so nothing is recorded), the page now says to update it in HACS and restart, instead of looking blank. An empty log explains that actions are recorded only from when 0.71.0 started running.

## 0.54.0

- **Activity log (administrators).** A new row in the account panel (the side panel) opens a page of what people and the assistant did in the house and who came and went: a person's own switching from the app or a dashboard, what the assistant did, and arrivals and departures. The assistant's lines say whether someone **asked** him (and who) or an **automation** ran his script (and which). Filter by day (today, yesterday, 7 or 30 days), time of day (morning, afternoon, evening, night), kind and person; new lines appear by themselves. Needs Church Drive 0.71.0 or later in Home Assistant.

## 0.53.1

- **Leaving a landscape camera no longer locks the app sideways.** Closing full screen on a camera (or the camera itself) turns the screen back to portrait even with auto-rotate off, then lets it rotate normally again; before, the app stayed in landscape until you closed and reopened it.

## 0.53.0

- **Climate ranges follow the numbers.** A thermostat's graph now shows the comfortable range the assistant uses (set in its Settings sheet), not the room type's, so a lowered living room shows 18 to 21 instead of 19 to 22. The Climate page's rooms can have their own range too: tap a room that has its range numbers (the entrance, the landings and the master bedroom) to change it.
- **Blinds work again.** A blind that doesn't report where it is (an RF blind, state unknown) had its Open, Stop and Close greyed out; they now work, and the card says "Last: Open" after you use them.
- The assistant's heating rules (off when the alarm is away, never on by himself, asks to take over when you disarm, turn on or off and set a temperature on request) are in the Clive log.

## 0.52.9

- Watch: swiping sideways between pages is smoother. The next page is already built when you start the swipe, and turning a page no longer rebuilds every face on the watch (only the glass behind them follows).

## 0.52.8

- Each thermostat's card now has a line at the bottom saying who looks after it ("Clive controlled", "Clive controlled · paused until 12:24" or "Manual"). Tapping it opens that thermostat's settings: Clive controlled or Manual, the comfortable range (a half degree at a time, with what he waits for before acting), the pause with a button to end it now, and the last thing he did. The two thermostat switches are gone from Settings.

## 0.52.7

- Clive's cards now show the right crest for Europe's big clubs (Barcelona, Paris Saint-Germain, Bayern, Inter, Real Madrid and the like): they come from a fixed list instead of a name search that missed some and found another sport's club for others (Bayern's basketball team, "Intercity" for Inter).

## 0.52.6

- Clive's league fixtures card could come up with dates and times but no teams or competition logo when he sent the games in a different shape: the house now accepts either shape, and the card also takes team names given as plain text and finds the competition's logo from its title. Clive no longer leaves a gap between himself and the keyboard (the page showed through above the keys): he now measures where the screen really ends instead of guessing.

## 0.52.5

- Clive's automatic heating is slower to act and aims 1°C cooler: downstairs 18 to 21°C and upstairs 15 to 19°C, with the heating target only raised once a room is more than 1.5°C below the bottom of its range, and lowered once it is 1°C above the top. Each thermostat now has its own switch in Settings (Downstairs heating, Upstairs heating): Controlled by Clive, or Manual so he leaves it alone. This replaces the single house-wide switch.

## 0.52.4

- The Security page's Alarm heading and icon now take the alarm's own colour (blue when armed home, red away), as on Home, instead of sometimes showing green.

## 0.52.3

- Clive's automatic heating now only adjusts the target temperature of a zone that is already heating, and never turns the heating on, off or to eco by himself. A new switch in Settings ("Clive adjusts the heating") turns his automatic adjusting off for the whole house.

## 0.52.2

- Clive's cards (fixtures, scores and the rest) were lost after the phone's line to the house had dropped and come back, because the house forgets what the app is listening for; the app now starts listening again at every question. The keyboard now folds away as soon as a question is sent and again when the answer arrives.

## 0.52.1

- Cleaning: "How he cleans" is now "Cleaning settings", each setting a line with a drop-down (the mode, suction and water show the house's icons) instead of rows of cramped buttons. Every group on the page is in the cleaning blue. The dock keeps its four actions (empty the dust bin, wash the mop, dry the mop, find Gregg), two to a row, and drops the dry time and empty-the-bin choices. Each part shows a bar for the life it has left, fading smoothly from blue through green and yellow to red as it nears the end, like the battery bars on the Security page.

## 0.52.0

- Cleaning: a new page, drawn by the app from Gregg's own entities so it all looks the same. At the top his status icon, the battery and the last clean with the Start, Stop and Dock buttons; then how he cleans (mode, suction and water as the house's icons, with cleaning and mop intensity), the dock (empty the bin, wash and dry the mop and find him, with the house's icons, and how long to dry and when to empty), his parts with the time left on each and a Reset (tap twice), and his switches. The Home page's Cleaning title carries the same resting icon as the tab.
- Clive: a long list on a card (scores, fixtures, tasks) shows its first seven rows and a Show more button for the rest; the keyboard folds away when you send a question and when the answer arrives; the orb is smaller while there is a conversation, so more of the answer shows.
- Cars: a car's logo (Vauxhall, Tesla and the rest, from the first word of its name) replaces the plain car symbol on the charging card.

## 0.51.4

- Watch: the loading ring is one arc that fades smoothly from bright at its head to nothing at its tail (it was three steps of brightness).
- Watch: the crown moves one page for each turn of a notch and carries on smoothly through a quick turn (before, each notch started from the page you had left, so quick turns stuck or jumped), and a half turn that was never finished is forgotten.
- Watch: smoother and lighter. The ring and background are drawn without redrawing the whole screen on every frame, the air quality gradient is one arc instead of ninety-six, and a state change that changes nothing (the house sends the same list again) no longer redraws every page.
- Watch: Clive's answer and card never scroll; what does not fit under the mark is made smaller until it does, and a long plain answer stops at six lines. What you said stays above it in one small line. Long car and air quality names are cut with an ellipsis.
- Phone: a list card (scores, fixtures, tasks and the rest) shows at most seven rows and says how many more, so the card fits the screen and needs no scrolling.

## 0.51.3

- Clive: ask for the games of a league ("What are the Championship games this weekend?") and he shows them as a list: each club's badge, the date and the kick off time, under the league's logo, on the phone and the watch. It needs the house's new league fixtures script (already live).

## 0.51.2

- Home Assistant's house icons (home, home outline, out and away, in) everywhere in the app now draw the house's own house, including the page bar's Home tab and the people in the alarm panel.
- Watch: what you said stays under Clive's mark, where it was while you spoke, instead of jumping above it (once the answer is in it is the first small line of the answer). A card for a football or other competition shows the competition's logo in place of the symbol (Premier League, EFL, the cups and the European competitions).
- Phone: the Clive page scrolls up to the newest answer on its own, and again when its card arrives a moment later.
- Clive (house prompt): questions about a league's or competition's next games call the next game script, and any answer to a question, including a simple fact, goes through the answer script so it shows a card instead of only words.

## 0.51.1

- Gregg: the vacuum, vacuum and mop, empty dust bin and dry mop icons are redrawn in the disc-and-ring style with the outline of the glyph cut out (the vacuum's pinwheel is the one from the status icon, and the vacuum and mop pinwheel is drawn small with a thinner line to match). The cleaning mode choice shows them as tiles, and the dock buttons and Find Gregg carry their own icons on his card. Dry mop is a water drop with a line through it (Home Assistant's water-off icon, adapted).
- Security: the house inside the Armed Home shield is the house's own house shape (the app house without its chimney, with a notched door), on the phone and the watch. The outlined one (the Manager's alarm modes) and the home and away marks beside each person in the alarm panel use the same house.
- Cleaning: the tab in the page bar and the title of the Cleaning page show Gregg's resting icon, which never changes, instead of his status (or the older vacuum symbol).

## 0.51.0

- Gregg: suction level (Quiet, Standard, Turbo, Max, Boost IQ) and water level (Low, Medium, High) show as icon tiles on his card, drawn like the Eufy app's icons: a pinwheel of curved blades (three, four, five; Max and Boost IQ carry a badge) and a drop that fills. They are `cd:suction-*` and `cd:water-*` in the house's own icon set.

## 0.50.1

- Clive: cards carry the logo of the competition (Premier League, EFL Championship, League One and Two with one shared mark, Carabao Cup, FA Cup, Scottish Premiership, Champions, Europa and Conference Leagues, Nations League), of Disney+, Octopus Energy and Cupra, and the new Simple Icons logos (Prime Video, BBC iPlayer, Amazon Music, Mercedes-Benz, Land Rover), instead of the plain sport or car icon. Our own logos are in `assets/own-logos.json` and show through `ol:` icons.
- 279 more brand logos in the Simple Icons set. AirPlay, Mercedes-Benz and Prime Video now find their logos by name.

## 0.50.0

- Clive: every card has its icon in the same place at the same size. The thinking mark turns into it (a crossfade, not yet a shape morph) and the figures open out underneath, on the phone, and the watch's mark no longer shifts when the card arrives.
- Clive: sports cards (score, scores, fixture) show their sport's icon (a tennis ball for tennis, a golf club for golf, a football by default). A competition's one-colour logo, a music service's logo (Spotify) and a car's make (Vauxhall) replace the icon when the app has the logo.
- Clive: twelve new card layouts the house can send (energy, secure, doors, tasks, devices, lights, heat, shop, event, reminder, media, answer), all drawn by one shared layout, on the phone and the watch. The house sends them with `clive_show_card`.

## 0.49.0

- Clive: a card appears the moment you ask, empty with his thinking mark in the middle, and the answer's card takes its place. The weather card's icon is now in the middle.
- Clive: cards for Gregg's status and for the air quality (they need the house's scripts, which are already live).
- Clive's title shows the name set in Home Assistant, not the old wake word's name.
- The Home page's Security icon follows the alarm's colour (green off, blue home, red away), and Cleaning (on Home and in the bar) uses Gregg's own status icon.
- Gregg's icon has his front bumper.
- Watch: a few seconds after an answer the screen clears, ready for a new question; if Clive is listening for a follow-up he goes to listening instead.

## 0.48.3

- Gregg's status icon: a disc with his lidar turret and a ring round it, replacing the plain vacuum icon on the Cleaning card and the vacuum tile. The ring turns in three parts while he cleans, backwards in two parts while he heads home, fills to the battery level while he charges, and blinks closed on an error. Once he is docked and charged it shows the house.
- Start, Pause, Stop and Home buttons on the vacuum are plain icons with no disc or ring. Gregg's rolling brush and side brush rows use the house's own icons.
- The beginnings of the house's own icon set (`cd:` names): the buttons, Gregg's status marks, dock jobs, cleaning modes and parts. Only the status, the buttons and the two brushes show anywhere yet.

## 0.48.2

- A rebuild of 0.48.1 with a new version number, to put the app on Google Play's internal testing track automatically for the first time. No change to the app.

## 0.48.1

- Clive's climate card: asked about the temperature in the house, he shows each heating zone with its temperature, target, humidity and state (heating, idle, Eco, off), and the outside temperature.
- The keyboard no longer comes back over the page when you leave the app and return with Clive's typing box open.

## 0.48.0

- Home page: a reminder below the to-do highlights when somebody has changed the heating by hand and Clive has stepped back from that zone for a while. It says when he takes over again and has a button to hand it back at once.
- Clive's notifications can be answered from the notification: Resume Clive hands the heating back without opening the app.
- Clive's own notifications (what he did in the house) show his mark, small and in colour; everyone else's still show the Manager's.

## 0.47.2

- Listening now shows a stop button: a rounded square that breathes and swells with your voice, with a ripple spreading from it. Tap it to stop.
- Typing a question (or tapping a suggestion) no longer makes Clive start listening after his answer: only a spoken question gets the follow-up listening.
- Names on Clive's cards are always the real spelling (Diane), never the way the voice is told to say it.

## 0.47.1

- Brand logos: the app now carries the Simple Icons set (3,466 logos, CC0): any icon written `si:spotify`, `si:netflix` and so on (the same names as the HACS Simple Icons pack) draws as a single-colour vector, and a music card shows the service's logo. The watch carries about 35 of the common ones for its music card.

## 0.47.0

- Clive's cards no longer have a background of their own: they sit straight in his speech bubble.
- Answers with club badges wait (a short thinking pause) until the badges have been found and downloaded, so the card appears finished and a crest never pops in after a flash of letters. The letters only show for a club that has no badge.

## 0.46.9

- Watch: the crown scrolls (down and up the faces of a page, and between Clive's Talk, Prompts and Settings) and no longer changes temperatures, brightness or other values. Use the ring or the plus and minus buttons for values.

## 0.46.8

- A new Scores card: several results at once (Champions League night, the weekend's games) as a table with the club badges, on the phone and the watch.
- When Clive answers with a card, the phone shows just the card (no repeat of his words in a bubble); he can still say more aloud.
- New cards for the house: who's home (each person in their place colour: home green, a named place teal, away grey, unknown amber), how the car is charging (a ring coloured by the dashboard's battery bands: red under 20%, orange under 40%) and a receipt for what Clive has just done (a light shows its own colour).

## 0.46.7

- Google Play installs no longer look at GitHub for updates: Account > About says Google Play keeps the app up to date, and its row opens the app's Play page. Test builds from GitHub update as before.

## 0.46.6

- Clive's cards: on the phone the card is see-through (a faint wash and a hairline edge) instead of a solid colour; the weather card shows where, the next hours and the day's high and low; club crests come from a built-in list of Premier League clubs first, then a search by name, and show without a white disc (the watch too, which also shows the next four hours).

## 0.46.5

- Watch: the loading arc round the edge is white (brightest at its head, fading along its tail).

## 0.46.4

- The big "Reading…" / "Adding…" box is gone: it no longer appears when the app opens (it was the check for tasks waiting from earlier, which waits for the connection), and when you share text to be read the same small pill as "Updating" shows at the top (tap it to cancel).

## 0.46.3

- Loading: the phone app shows "Updating" (or "Connecting") as a pill under the title, with Clive's mark drifting beside it, over what it remembered from last time, instead of a line that pushed the page down. On the watch, both apps remember the last answer from the house and show it at once, with an arc of Clive's colours circling the edge and a small "Updating" pill (his mark trading size) until the live answer arrives.
- The build number and version are only in About (not on the main pages), and the signing certificate there is for administrators only.
- The To-do row in the account panel uses a single-colour icon like the other rows.

## 0.46.2

- Manager > Watch is built (it said Coming soon): pages on or off and in order, what is shown on each (alarm modes, climate devices, rooms and zones, cleaning and charger faces, cars), how Lights starts, and Clive's toggles. The same settings as the Watch panel on the web Manager.
- Clive's cards: club badges are found by the screen itself from the team's name (a model can't find a real picture address); the phone's cards have the weather icon and the weather's or sport's colours, like the watch.
- The typing box starts a sentence with a capital, and Clive's round button has the same outline as the typing box.
- The gap above the keyboard after leaving the app and coming back: the window is set to resize again every time the app comes back.
- Watch: the crown only turns a value (temperature, brightness), a firmer turn is needed for each step, and it never scrolls pages or faces.

## 0.46.1

- Clive's cards (a score or fixture with club badges, the weather, what is playing) now show in the app's chat too, not only on the watch. Typing no longer ends with "Allow the microphone to talk to the house": a follow-up listen that can't start because the microphone isn't allowed is dropped quietly.
- Watch: Clive's mark is no longer clipped at its edges; his glow runs smoother (it redraws each frame without rebuilding the screen); the glass stays lit while the app is in use and while Clive thinks (the companion lets it sleep two minutes after the last touch); the crown turns temperature and brightness, otherwise moves between faces and pages (in Clive, between Talk, Prompts and Settings); Clive and To-Do open in their own tasks, so the Church Drive icon always opens the companion; all three watch icons are adaptive and the same size.
- Signing in is now shared between the Google Play Church Drive and Church Drive To Do apps: both Play signing certificates are in the allowed list (`TrustedApps`).

## 0.46.0

- Watch: a new companion app (Church Drive) with pages you swipe across: Alarm, Climate, Lights, Cleaning and Car charger, on a ring and background that stay put while the content slides. Which pages, their order and the items on each are chosen in the Manager's Watch panel. Clive is a separate watch app with Talk, Prompts and Settings, a glow background, and visual answers (weather, scores with club badges, fixtures, music) that change the glow. Both watch icons are now the same size.
- Shared code: moved the scene looks into `core`, and Clive's answers can carry a card (`watch-cards` in the Clive log).

## 0.45.0

- Signing in no longer asks for the Home Assistant address: it is the house's cloud address, built in. Press and hold the "Church Drive" title on the sign-in screen to type a different one (administrators).
- Sharing the sign-in with Church Drive To Do now works between the Google Play apps. The sign-in is offered only to Church Drive apps whose signing certificate is in the allowed list (`TrustedApps`); until the Play certificates are added there, To Do asks for the username and password itself. About shows this app's signing certificate (SHA-256) to copy into the list.

## 0.44.2

- Code comment only (the To-Do app no longer declares the `SHARE_SESSION` permission; see its changelog). No change to the app.

## 0.44.1

- First Google Play fixes. Clive keeps his original indigo, purple and green by default; Settings now has "Use phone theme colours for Clive" (off) to give him the phone's colours instead.
- A room's pill on Home and Lights takes its colour from a light in the room that is on and showing a colour, not the group's last colour; with only plain white lights or switches on it follows the theme.
- To-do in the account panel always shows the Church Drive To-Do icon, and its text is one line so the icon lines up with the others.
- Experimental is hidden for now (nothing is being tried out).
- Clive's round button is the colour of the bottom bar.
- Settings > "Get Church Drive To Do" opens the To-Do app's Google Play listing (`com.churchdrive.todo`) when it isn't installed; once it is, To-do opens the app as before.

## 0.44.0

- Ready for Google Play: CI now also builds the signed app bundles (`church-drive-manager.aab` for the phone, `church-drive-manager-wear.aab` for the watch), attached to each version's release. Built for Android 16 (target API 36; the watch stays on 35). The Play build does not check GitHub for test builds.

## 0.43.0

- The app is now **Church Drive Manager**, with the Google Play package name `com.churchdrive.manager` (the phone app and the watch app share it). It installs as a new app beside the old Church Drive: sign in again, then remove the old one. The To-Do app keeps `com.churchdrive.todo` and is named Church Drive To Do.

## 0.42.0

- New colours for the app icons (Church Drive, Church Drive To-Do, the watch tiles): a soft three-point glow of indigo, purple and green on white, like the assistant's orb. The status-bar icon stays plain white.
- New start-up screen: the app's mark, its name ("Home Manager") and "Powered by Home Assistant" with the Home Assistant logo, for a moment before the app opens. In dark mode it is all white on the app's own dark colour; in light mode the mark keeps its colours and the text is dark. Tap it to skip. The phone's own start-up screen is now just that background.

## 0.41.0

- The assistant's name comes from the house (Church Drive > Configure > Assistant name in Home Assistant), so the phone app, watch and To-Do app all follow a rename without an update. The last name heard is kept on the device. Earlier names (Jarvis, Clive) are still understood when spoken to him.
- Code for the assistant is named `Assistant*` throughout (Clive* and Jarvis* are gone).

## 0.40.0

- Internal: code is no longer named after the assistant (Jarvis* became Assistant*), and the house's scripts are now clive_*; nothing changes on screen.
- Clive can show a picture or a link under his answer in the assistant screen when it helps (the house's new `jarvis_show` script). Only https addresses are shown, and only to the person who asked. Nothing changes when he has nothing to show, and his spoken answer never depends on it.

## 0.39.2

- The coloured glow is back behind the white mark on the assistant's round button (0.39.0 had taken it away). It moves with what he is doing, and the white mark moves on top of it.

## 0.39.1

- Settings no longer names the old wake word as an example. The screens still show the real wake word, whatever the house's Assist uses (today "Hey Jarvis", until a "Hey Clive" one is added).

## 0.39.0

- New icons use the Church Drive colours: the house (and the watch's Clive and To-do tiles) is now drawn in the indigo, purple and green gradient on white, not white on teal. The status-bar icon stays plain white, as Android needs.
- In Account, To-do and its setting show the Church Drive To-Do app's own clipboard icon when To-do opens that app.
- The assistant's round button is now a still circle with Clive's white mark moving on it: it drifts when he is shut, becomes a microphone (the capsule slowly breathing) when he waits, the same microphone wobbling like a level meter while he listens, two squircles trading size while he thinks, and two equaliser bars while he speaks.
- Shared code change for Clive To Do (see Church Drive To-Do's notes).

## 0.38.2

- The assistant is now called Clive everywhere in the app and on the watch (labels, buttons, settings, the watch tile). The wake word is still "Hey Jarvis" until a "Hey Clive" one is trained in Home Assistant. Everyone is told about the new name once.

## 0.38.1

- New look for the app icon: the Church Drive house with a rounded square tucked into its roof, cut out where they overlap, in white on the teal of the logo. The launcher icon, the splash screen, the notification icon in the status bar and the watch app's icon all use it. (The themed-icon version is kept in the project, still switched off.)
- The assistant's round button shows the new Clive mark (two rounded squares, cut out where they overlap) when it is shut, and the watch app's Jarvis and To-do tiles have new icons to match.
- The mark is drawn from exact shapes now, not pictures, so it stays sharp at every size.

## 0.38.0

- Tasks on the Home page open that task in Church Drive To-Do (its edit page) when the To-Do app is installed. Settings has a new "Open To-do in Church Drive To-Do" switch (shown when the app is installed): turn it off and To-do, and the tasks on Home, open this app's own To-do page instead. Shopping and steps lists always open in this app.
- Tapping a job the house spotted ("From the house") on Home goes to what it is about: the device's page (found by its name in the job, such as a battery or filter), the Cleaning page for the vacuum's messages, the Security page for a safety alarm, or Device Health when no device fits.
- A job only Home Assistant can do, such as an update waiting, opens Home Assistant at Settings > Updates (in its app if it is on the phone, else the browser).
- The To-Do app can ask this app to open a house job's device the same way.

## 0.37.0

- To-do moved from the bar along the bottom into the account panel (the panel that slides in from the right, from your initial). Where Church Drive To-Do is installed, tapping To-do (there, on the Home highlights, from a widget or from a notification) opens that app instead; where it isn't, the To-do page opens inside this app as before.
- The bottom bar is now Home, Lights, Security, Climate and Cleaning.
- Under the bonnet: the update check moved into the shared `core` module so Church Drive To-Do can use it.

## 0.36.1

- The house's own tasks ("From the house") are coral instead of purple: the To-do page's house card, the Home highlights, the To-do widget and Church Drive To-Do's House page. Purple stays for your own tasks, indigo for shopping, teal for steps and red for overdue.

## 0.36.0

- Change the date of a repeating task's next time by hand: in a task's edit page, "Pick a date" opens a calendar (the heading is "Next due" for a repeating task). The repeat carries on from the day you pick, so a fortnightly or every-30-days task moves its whole cycle. It works for any task's day too, not just the next few days.
- Under the bonnet: the task-editing rules and the calendar moved into the shared `core` module, so Church Drive To-Do uses them too.

## 0.35.2

- Church Drive To-Do now looks like the main app: the same Material You colours, the large collapsing title with your initial (tap it for your name and Sign out), and your tasks on a coloured card with the same tick boxes, "Add a task" box and Done section.
- The pages are along the bottom, as in the main app: My to-do, Shared, Cleaning and House (the jobs the house has spotted for you or everyone), each with a count of what is open. A shopping or steps list shows with its cart or tick-list icon and is worked through in the main app.
- My to-do is only ever your own list. The app used to list every to-do list in the house, other people's included; it now asks only for yours, the shared list, cleaning and the house's jobs for you.
- Under the bonnet: the app's colours and theme moved into the shared `core` module.

## 0.35.1

- Church Drive To-Do signs in by itself when the Church Drive app is on the phone and signed in: open it and your lists are there, with no address or password to type. It follows the main app (signing out or in as someone else there does the same in To-Do, which has no Sign out button of its own while it is using that sign-in). Both apps must come from the same build source (same signing key); otherwise To-Do shows its normal sign-in.
- Church Drive To-Do has its own icon (the main app's purple-blue-green gradient house with a tick).
- The "update available" notice no longer appears when you are already on the latest version. The app used to offer an update whenever the newest test build came from a different commit than its own, which a build installed from a branch preview always was. It now offers one only when the newest build number is higher than the one you are running.

## 0.35.0

- Pixel Watch (Wear OS) app, in the same Play listing as the phone app: it is signed in by the phone (open Church Drive on the phone once and the watch has the same sign-in; signing out on the phone signs the watch out). On the watch, Church Drive has the key actions (alarm Home, Away, Night and Disarm, scenes, all lights off, the vacuum), Jarvis (speak or tap a sentence; he answers on screen and aloud, with a Voice on/off button) and a tile with Jarvis, To-Do and Actions.
- To-Do on the watch is a mini app of its own with its own icon: your to-do list, the shared list and the cleaning jobs, tap a task to tick it off, "Add a task" by voice.
- Church Drive To-Do: a phone app with just the to-do lists (its own install and sign-in), made from the same code.
- Under the bonnet: the Home Assistant connection, sign-in storage and to-do and Jarvis data moved into a shared `core` module, so the phone, watch and to-do apps use one copy.

## 0.34.3

- Account > Settings shows how long Jarvis took over his last answer, step by step: hearing you, thinking, making the voice and starting to speak. It is for finding out which part is slow.

## 0.34.2

- Jarvis no longer closes after asking "anything else?": he listens as soon as he finishes speaking (also when the house says the conversation continues), and if the phone's listener gives up before you have started to answer, he listens once more before closing. Only "no thanks", "that's it" and the like end the conversation.
- Jarvis answers sooner: he waits 1.2 seconds of quiet (was 1.7) before deciding you have finished, the phone's listener waits less too, and the follow-up listen starts 0.2 seconds after he stops speaking.

## 0.34.1

- Jarvis's round button: the ground behind his avatar is the same dark grey as the ring round it, and the avatar is a little smaller so the ring no longer clips its edge.

## 0.34.0

- Thermostat target no longer jumps back (to 12.1, say). The Nest ignores a new target while Eco is on, so a tap on − or + now takes the thermostat out of Eco first, then sets the target. While Eco is on the card says "Eco target … (tap to heat)".
- Targets land on the thermostat's steps (12, 12.5, 13) instead of drifting from an odd starting value (12.1, 12.6, 13.1), and a run of quick taps sends one change when you stop tapping.

## 0.33.9

- The glow at the bottom of Jarvis fades out completely at its top, so it has no hard edge there (clipping it to stop it covering the bar had left one).
- The Type button works every time: after the keyboard was folded away with the keyboard's own button, the typing box still held focus, so asking again did nothing. The focus is now let go first and the keyboard asked for again. Jarvis no longer opens with the keyboard up because of the last time (the request was remembered between openings), and the Type button is only lit while the keyboard is up.

## 0.33.8

- Jarvis's round button has his avatar back as its background (the moving orb, following what he is doing) with a dark grey ring and a white logo or microphone over it.
- The glow at the bottom of Jarvis no longer spills over the bottom bar, and flickers much less: it stays on a moment longer between the voice steps and follows the voice more smoothly (and a little softer).
- The keyboard no longer counts twice in Jarvis: the phone was panning the whole window up to show the typing box as well as Jarvis moving it, so the page showed between his box and the keyboard (and the top of him was cut off). While he is open the window no longer pans.

## 0.33.7

- Fixes 0.33.6, where the bottom bar was drawn at the top of the screen and the page was empty: the bar's row now has a fixed height instead of filling all the room it is offered.

## 0.33.6

- Jarvis and the bottom bar: the app's own bar now changes into his five buttons while he is open (and back), instead of a second bar appearing. The keyboard goes over the bar, as everywhere else, and the typing box (with his round button) sits just above the keyboard instead of leaving the page showing in between.

## 0.33.5

- Jarvis's bottom bar has five buttons, Type in the middle: Close, New chat (starts a fresh conversation), Type, Voice (silences his spoken answers, so he only writes them; remembered) and Settings (takes you to the app's settings). Jarvis log: "New chat, Voice and Settings".
- Jarvis's round button now has his three colours behind it, a grey ring round it and a white symbol: his logo when he is shut, a microphone (a stop square while he listens) when he is open. While he is open he always listens for the wake word, whatever the setting says: the Settings switch (now "Open Jarvis with the wake word") is for opening him by voice when he is shut.
- Jarvis opens from his button instead of sliding up over the page: he fades and swells in from the bottom right, and the round button is the one button throughout (his logo when he is shut, his microphone when he is open), always on top of him. With "Open Jarvis ready to type" on he opens quietly, not listening and without the keyboard; tap Type to bring it up or the microphone to talk.

## 0.33.4

- Jarvis: the round button at the bottom right of every page now shows his logo, and opens him ready to talk or ready to type: Settings has "Open Jarvis ready to type" next to the wake word switch. Inside Jarvis the bottom bar stays, with his own buttons (Close and Type), and the microphone is the same round button in the same place. Jarvis log: "Type or talk".

## 0.33.3

- Jarvis's microphone is now a round button at the bottom right of every page, just above the page bar, instead of a small icon in the top corner. The green "listening for the wake word" dot stays at the top. Inside Jarvis, the microphone is now at the bottom right too (with the keyboard button on the left), so opening him and talking are the same thumb spot.
- Jarvis's introduction ("Hi Jamie, I'm Jarvis" and "What's new") now comes once: it counts as seen the moment it is shown, however it is closed and a new app version no longer brings it back. Jarvis now has his own log (`ASSIST_FEATURES`): the full hello is for the first time ever, and after that a person is told, once, only about log entries they have not heard (a new feature, change or fix), and just those.
- Security page notices (doors open, last movement, who is home, smoke and CO, batteries): an "all is well" notice shows once after each app update, then stays away on later visits; it only comes back when it turns amber or red. When they go, they slide off to the side one after another instead of fading. The notices now sit above the alarm card, as on the dashboard. Who's home is no longer a notice: the Security page's alarm card lists each person on a line of their own (administrators only), and the old line only appears, for administrators, when everyone is out and the alarm is off.

## 0.33.2

- Home to-do highlights: each task's text now takes its colour too (shopping indigo, overdue red), and a steps task is teal so it no longer looks like a shopping list. The steps icon on the To-do page is teal as well.

## 0.33.1

- Home page to-do highlights: the button on the right ("+N more" or "To-do") now fills the full height of the task rows, so there is no blank space under it when three tasks show. Shopping and steps tasks use the shopping indigo instead of purple (overdue ones stay red).

## 0.33.0

- Security zone cards can name the camera whose events they show (the card's `camera` setting): Back Garden now shows the garden camera's events and Back Door the living room's.
- The camera viewer's bigger/smaller and full screen buttons (and the call and + buttons) now sit clear of the status and navigation bars when the picture is made bigger or full screen, so they can always be tapped to go back.

## 0.32.0

- The alarm card (Home, Security, and the Security widget) names who really armed or disarmed it. It used to say "by Hayley" (the Ring account Home Assistant uses) even when Jamie did it from the app; now it names the person Home Assistant recorded, and still names Ring's person for the keypad or the Ring app. Needs Church Drive 0.63.0 in Home Assistant.

## 0.31.0

- **Confirm the car**: each time a car is plugged in, or unplugged and plugged in again, the house asks "Confirm car to charge tonight" and nothing unlocks or starts it until someone says Yes. The question comes as a notification with **Yes** and **No** buttons (they need the phone unlocked), and as a card on the Home page, between the to-do highlights and Security, with the same two buttons. It is asked again an hour later if nobody answers, never while the car is already charging, and a pause in a charge never clears the answer.

## 0.30.1

- Fixed a crash on opening the app ("Reading a state that was created after the snapshot was taken"): the icon pack's state is now created at start-up, not in the middle of drawing the bottom bar.
- Device health is simpler: a device's page (tap it, or press and hold its card) shows just its battery and filter as a coloured bar, whether it is answering, and when it was last heard. The long list of every entity, and the battery graph, are gone.
- Device health now uses bars and colours on the list too. A battery at 40% or less is amber ("needs attention") and at 20% or less is red; a filter at 30% or less is amber and at 10% or less is red. So 28% is no longer "all fine".

## 0.30.0

- Notification sounds: each kind of pop-up has its own default sound instead of the phone's one: a doorbell ding-dong, a soft ping for movement, rising notes for energy and the car, a blip for to-dos, low beats for device and system problems. Safety alarms still ring through silent. Each sound family is its own Android channel (Settings > Notifications > Church Drive), so you can change or silence one in Android's settings. Anything else keeps the old pop-up sound.
- Tapping a notification now goes to the right place: a page, a camera (answering for the doorbell), a shopping list, or a device. A battery alert opens that device's page; other device and battery alerts open Device Health.
- Device Health: a new full-page view for everyone, from the account panel (Device health) or by pressing and holding a device's card (fans, purifiers, covers, lights, CO alarms, sensors, the vacuum). It lists devices with a battery, a filter or that stopped answering, those needing attention first; a device opens its own page with its status, last heard, a week of battery history and all its entities.
- Google push is shown in Settings to administrators only. Phones still register by themselves.
- Needs Church Drive 0.62.0 or later in Home Assistant for the device and Device Health links.

## 0.29.0

- No more "Church Drive is connected" notice. Once the house has this phone's Google push token, notifications come through Google and the app's own connection (the thing that needed that notice) is stopped. If Google push isn't working on a phone, the connection still runs as before, but its notice is created blocked so it doesn't show or leave a dot. The "Hide the connected notice" row in Settings is gone because it is no longer needed.
- Needs Church Drive 0.61.0 or later in Home Assistant and a `church_drive_fcm.json` key there.

## 0.28.0

- The phone is registered with Home Assistant under its own name with "-CDA" after it (for example XITOL-J45PER-P10PXL-CDA) instead of "Jamie Pixel 10 Pro XL", so it is plainly the same phone as the Home Assistant app on it. A phone registered under the old name is renamed in Home Assistant the next time the app connects. Its notify service name changes with it (Home Assistant picks that up on its next restart).
- Settings > Google push now shows each step Google push needs (Google Play services, this app's install ID, the token) and which one is stuck or failed, with Google's own message, and updates while the page is open.

## 0.27.0

- Settings > Google push: says where Google push has got to on this phone (asking Google, token sent to the house, or what went wrong), and tapping it tries again.

## 0.26.0

- Google push: the app registers with Google's push service and tells the house its token. When the house has its key (Church Drive 0.58.0 and a `church_drive_fcm.json` in Home Assistant's config folder), notifications arrive through Google, even when Android has put the app to sleep. Until then nothing changes.

## 0.25.0

- Settings > Hide the connected notice: opens Android's settings for the small "connected" notice, where its switch can be turned off. The connection and notifications carry on without it.
- Needs Church Drive 0.57.0 in Home Assistant, which sends one copy per phone (the Church Drive app's when it's connected, else the Home Assistant app's).

## 0.24.0

- Notification styles: the preview and "Send me a test" now simulate each kind properly. The preview shows that kind's own words, a camera picture box for the ones that have a picture (doorbell, movement, door, smoke), and its buttons (Answer, View, Mute and so on). "Send me a test" asks the house to send a pretend one of that kind to this phone, so it comes the real way: the real buttons and picture, in the style you chose for that kind (an Alert rings through silent). Its title starts with "Test:". Needs Church Drive 0.56.0 in Home Assistant; without it you get the old plain test.

## 0.23.0

- The new camera live view is now the main one: on the Security page, in the Cameras cards and when opened from a notification or widget. It has the bigger and full-screen pictures, Talk, Events, and Answer on the doorbell (a green outline and an Answer button on its tile while it rings). The old live view is no longer used, and the Experimental page no longer lists the cameras.

## 0.22.0

- Doorbell and movement notifications show the camera's picture above their buttons, like Ring's own app. The words and buttons appear straight away and the picture follows a second or two later (fetched again a few seconds after in case the first was the previous snapshot). Pull the notification down, or expand it, to see the picture.

## 0.21.0

- Notification buttons: notifications from the house can carry buttons that open a camera (answering a doorbell) or a page, turn lights on, send the vacuum home, charge or pause the car, run a backup, mute that kind for a while on this phone, or snooze it. Buttons that change the house need the phone unlocked. There are no calling buttons, and nothing can disarm the alarm.
- Catching up: if the phone was offline or asleep when the house sent something, the app asks for what it missed (the last few hours, up to 8) when it reconnects and shows it, without repeating ones it already showed.
- Notification styles page: if the house isn't sending notifications to this phone (its switch is off in Manager), a card at the top says so, and administrators can turn it on there.

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

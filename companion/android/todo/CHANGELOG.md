# Church Drive To-Do: changes by version

Its own versions and releases (`todo-v<version>`, and `todo-latest` for the newest test build), separate from the phone app's. The version is set in
`version.properties` here. A change to `core` (shared code) is a change to both apps, so bump both. Newest first.

## 0.81.0

- Version moves with the shared number. No other change.

## 0.80.0

- One version number for every Church Drive app and the Home Assistant integration from here (`node tools/versions.mjs`, docs/VERSIONING.md). To-Do moves from 0.54.4 to 0.80.0 with no other change.

## 0.54.5

- No change to this app; version kept equal to the phone app.

## 0.54.4

- No change to this app; version kept equal to the phone app.

## 0.54.3

- No change to this app; version kept equal to the phone app (Activity log: devices changed outside Home Assistant).

## 0.54.2

- Shared code now shows times in both the device's and the home's time zone when they differ (no change in the To-Do app itself).
- Asking Clive about tomorrow's weather (or the day after, up to five days) now shows that day's card, with its high, low and hours, instead of only speaking. Today's weather card also shows today's high and low (after midnight it was showing yesterday's). Listening: the phone now asks its own noise suppression to quieten a television or a fan behind your voice, and Clive decides you have finished speaking after 0.9 seconds of quiet instead of 1.2, so he moves from listening to thinking sooner.
- Time zones: the phone now tells the house which time zone it is in. When it is not the home's, Clive gives the time where you are as well as the home time ("It's 14:05 here, which is 12:05 at home"), says which times are home times, and treats "here" as where you are. Between midnight and 03:30 (your time) when you say tomorrow, he first asks whether you mean today or tomorrow, naming both days, in case you have not been to bed yet.

## 0.54.1

- No change to this app; version kept equal to the phone app (Activity log filters).

## 0.54.0

- No change to this app; version kept equal to the phone app (the new Activity log for administrators).

## 0.53.1

- No change to this app; version kept equal to the phone app (a camera left in landscape no longer locks the screen).

## 0.53.0

- Shared code changed (the assistant's feature log has a new entry about the heating and the alarm). Nothing else changes in this app.

## 0.52.9

- No change to this app; version kept equal to the phone app (the watch swipe is smoother).

## 0.52.8

- Each thermostat's card now has a line at the bottom saying who looks after it ("Clive controlled", "Clive controlled · paused until 12:24" or "Manual"). Tapping it opens that thermostat's settings: Clive controlled or Manual, the comfortable range (a half degree at a time, with what he waits for before acting), the pause with a button to end it now, and the last thing he did. The two thermostat switches are gone from Settings.

## 0.52.7

- Clive's cards now show the right crest for Europe's big clubs (Barcelona, Paris Saint-Germain, Bayern, Inter, Real Madrid and the like): they come from a fixed list instead of a name search that missed some and found another sport's club for others (Bayern's basketball team, "Intercity" for Inter).

## 0.52.6

- Clive's league fixtures card could come up with dates and times but no teams or competition logo when he sent the games in a different shape: the house now accepts either shape, and the card also takes team names given as plain text and finds the competition's logo from its title. Clive no longer leaves a gap between himself and the keyboard (the page showed through above the keys): he now measures where the screen really ends instead of guessing.

## 0.52.5

- Clive's automatic heating is slower to act and aims 1°C cooler: downstairs 18 to 21°C and upstairs 15 to 19°C, with the heating target only raised once a room is more than 1.5°C below the bottom of its range, and lowered once it is 1°C above the top. Each thermostat now has its own switch in Settings (Downstairs heating, Upstairs heating): Controlled by Clive, or Manual so he leaves it alone. This replaces the single house-wide switch.

## 0.52.4

- Version kept in step with the phone app (the Security page's alarm colour). No change to this app.

## 0.52.3

- Clive's automatic heating now only adjusts the target temperature of a zone that is already heating, and never turns the heating on, off or to eco by himself. A new switch in Settings ("Clive adjusts the heating") turns his automatic adjusting off for the whole house.

## 0.52.2

- Clive's cards (fixtures, scores and the rest) were lost after the phone's line to the house had dropped and come back, because the house forgets what the app is listening for; the app now starts listening again at every question. The keyboard now folds away as soon as a question is sent and again when the answer arrives.

## 0.52.1

- Version kept in step with the phone app (the Cleaning page's settings and part bars). No change to this app.

## 0.52.0

- Version kept in step with the phone app (a new Cleaning page, Clive's show more). Shared code changed. No change to this app.

## 0.51.4

- Version kept in step with the phone app (watch smoothness and fit). Shared code changed. No change to this app.

## 0.51.3

- Version kept in step with the phone app (league fixtures card). Shared code changed. No change to this app.

## 0.51.2

- Version kept in step with the phone app (house icons, Clive fixes). Shared code changed. No change to this app.

## 0.51.1

- Version kept in step with the phone app (Gregg's mode and dock icons). Shared code changed. No change to this app.

## 0.51.0

- Version kept in step with the phone app (Gregg's level icons). Shared code changed. No change to this app.

## 0.50.1

- Version kept in step with the phone app (card logos). Shared code changed. No change to this app.

## 0.50.0

- Version kept in step with the phone app (Clive's card icons and new card layouts). Shared code changed. No change to this app.

## 0.49.0

- Version kept in step with the phone app (Clive's cards, Gregg's icon). Shared code changed. No change to this app.

## 0.48.3

- Version kept in step with the phone app (Gregg's new icons). No change to this app.

## 0.48.2

- Version kept in step with the phone app (a rebuild to test the automatic Google Play upload). No change to this app.

## 0.48.1

- Version kept in step with the phone app. Shared code changed (a new Clive card kind and two entries in his log). No change to this app.

## 0.48.0

- Version kept in step with the phone app. Shared code changed (a notification button for Clive). No change to this app.

## 0.47.2

- Version kept in step with the phone app. Shared code changed (Clive). No change to this app.

## 0.47.1

- Version kept in step with the phone app. No change to this app.

## 0.47.0

- Version kept in step with the phone app. Shared code changed (the Clive cards). No change to this app.

## 0.46.9

- Version kept in step with the phone app. No change to this app.

## 0.46.8

- Shared code changed (the Scores card). No change to this app.

## 0.46.7

- The To-Do app from Google Play no longer shows the "newer version ready" banner that pointed at GitHub. About says Google Play keeps it up to date and opens its Play page. Test builds from GitHub update as before.

## 0.46.6

- Shared code changed (club crests). No change to this app.

## 0.46.5

- Version kept in step with the phone app. No change to this app.

## 0.46.4

- Version kept in step with the phone app. No change to this app.

## 0.46.3

- Swiping left anywhere that doesn't scroll sideways opens the account panel, as in the main app. The signing certificate is no longer shown in About (it is in the main app's About, for administrators).

## 0.46.2

- Shared code changed for the watch and the phone's Clive cards (club badges found from the team's name). No change to this app.

## 0.46.1

- Signing in is now shared with the Google Play phone app: both Play signing certificates are in the allowed list (`TrustedApps`).

## 0.46.0

- Shared code changed for the watch (scene looks and Clive's answer cards moved into `core`). No change to this app.

## 0.45.0

- Signing in no longer asks for the Home Assistant address (the house's cloud address is built in; press and hold the title to change it).
- The phone app's sign-in is taken only from a Church Drive app whose signing certificate is allowed (see the phone app's changelog); About shows this app's certificate.

## 0.44.2

- Fixes "Can't install" from Google Play: To-Do no longer declares the phone app's `SHARE_SESSION` permission (Play signs each app with its own key, and two apps declaring the same permission under different keys can't both be installed). Signing in from the phone app now works only when both apps share a key (GitHub builds); from Play, sign in here directly.

## 0.44.1

- Version bumped to match the phone app (0.44.1) for the Google Play upload. No change to the app itself.

## 0.44.0

- Ready for Google Play: CI now also builds the signed app bundle (`church-drive-todo.aab`), attached to each version's release. Built for Android 16 (target API 36).

## 0.43.0

- Named Church Drive To Do (package `com.churchdrive.todo`, unchanged); it now finds the phone app by its new package name, `com.churchdrive.manager`.

## 0.42.0

- New icon colours: a soft three-point glow of indigo, purple and green on white.
- New start-up screen: the clipboard mark, "To Do" and "Powered by Home Assistant" with the Home Assistant logo (white on the app's dark colour in dark mode, colour and dark text in light mode). Tap to skip.

## 0.41.0

- Clive To Do takes the assistant's name from the house (Assistant name setting), so it follows a rename.

## 0.40.0

- Shared code change only; nothing new in this app.

## 0.39.2

- Shared code change only; nothing new in this app.

## 0.39.1

- Shared code change only; nothing new in this app.

## 0.39.0

- **Clive To Do**: a round Clive button on every page opens a small sheet where you type or say a sentence. Clive can only add a task ("add milk", "add bread, milk and eggs", "put bin bags on the shared list") or tick one off ("tick off the bins", "mark hoovering as done"). He works on your own, the shared and the cleaning lists, asks "which one?" if more than one task fits, and turns down anything else. Speaking uses the phone's own speech input.
- New icon colours: the indigo, purple and green gradient on white.

## 0.38.2

- Shared code change: the voice assistant's name is now Clive. Nothing visible in this app.

## 0.38.1

- New app icon: a clipboard with ticked rows, a second clipboard behind it cut out where they overlap, in white on indigo. It is also the themed icon on Android 13 and later.

## 0.38.0

- A task tapped on the Church Drive app's Home page opens here, on that task (its list, then the task's edit page).
- A job in the House page goes to the part of the house it is about when tapped: the device's page in the Church Drive app (or its Cleaning or Security page), or Home Assistant's device list if that app isn't on the phone. A job only Home Assistant can do, such as an update waiting, opens Home Assistant at Settings > Updates (its app if on the phone, else the browser). "Open Church Drive" in the side panel now works on every Android version (the app declares that it looks for it).

## 0.37.0

- A side panel, like the main app's: tap your initial and it slides in from the right with your name, Open Church Drive, Settings, About and Sign out (Sign out is there only when the app has its own sign-in; with the Church Drive app's sign-in, you sign out there).
- Settings: "Show finished tasks" keeps each list's Done section open, and "Open on the page I left" brings you back to the page you were on. Notifications points at the Church Drive app, which delivers the house's alerts and task reminders (and Google push) to the phone once, so this app never sends a second copy.
- About: version, build and commit, and Check for updates with a download button. A banner on the lists says when a newer version is ready.

## 0.36.1

- The House page is coral instead of purple, so the house's jobs stand apart from your own tasks.

## 0.36.0

- Tap a task to edit it: its name, day, repeat, time, who is reminded and notes, or delete it. The box on its left still ticks it.
- "Pick a date" opens a calendar for any day. For a repeating task the heading is "Next due": use it to move the next date by hand; the repeat carries on from the day you pick.

## 0.35.2

- Looks like the main Church Drive app: the same Material You colours, the large collapsing title with your initial (tap it for your name and Sign out), and your tasks on a coloured card with the same tick boxes, "Add a task" box and Done section.
- Pages along the bottom, as in the main app: My to-do, Shared, Cleaning and House (the jobs the house has spotted for you or everyone), each with a count of what is open. A shopping or steps list shows with its cart or tick-list icon and is worked through in the main app.
- My to-do is only ever your own list. The app no longer lists every to-do list in the house, other people's included.

## 0.35.1

- Signs in by itself when the Church Drive app is on the phone and signed in. It follows the main app (signing out or in as someone else there does the same here, and there is no Sign out button while it uses that sign-in). Both apps must be signed with the same key; otherwise To-Do shows its normal sign-in.
- Its own icon (the main app's purple-blue-green gradient house with a tick).

## 0.35.0

- First version: a phone app with just the to-do lists, with its own install and sign-in.

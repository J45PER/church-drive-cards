# Church Drive To-Do: changes by version

Its own versions and releases (`todo-v<version>`, and `todo-latest` for the newest test build), separate from the phone app's. The version is set in
`version.properties` here. A change to `core` (shared code) is a change to both apps, so bump both. Newest first.

## 0.38.0

- A task tapped on the Church Drive app's Home page opens here, on that task (its list, then the task's edit page).
- A job in the House page goes to the part of the house it is about when tapped: the device's page in the Church Drive app (or its Cleaning or Security page), or Home Assistant's device list if that app isn't on the phone. "Open Church Drive" in the side panel now works on every Android version (the app declares that it looks for it).

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

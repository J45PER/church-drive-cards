# Church Drive To-Do: changes by version

Its own versions and releases (`todo-v<version>`, and `todo-latest` for the newest test build), separate from the phone app's. The version is set in
`version.properties` here. A change to `core` (shared code) is a change to both apps, so bump both. Newest first.

## 0.35.2

- Looks like the main Church Drive app: the same Material You colours, the large collapsing title with your initial (tap it for your name and Sign out), and your tasks on a coloured card with the same tick boxes, "Add a task" box and Done section.
- Pages along the bottom, as in the main app: My to-do, Shared, Cleaning and House (the jobs the house has spotted for you or everyone), each with a count of what is open. A shopping or steps list shows with its cart or tick-list icon and is worked through in the main app.
- My to-do is only ever your own list. The app no longer lists every to-do list in the house, other people's included.

## 0.35.1

- Signs in by itself when the Church Drive app is on the phone and signed in. It follows the main app (signing out or in as someone else there does the same here, and there is no Sign out button while it uses that sign-in). Both apps must be signed with the same key; otherwise To-Do shows its normal sign-in.
- Its own icon (the main app's purple-blue-green gradient house with a tick).

## 0.35.0

- First version: a phone app with just the to-do lists, with its own install and sign-in.

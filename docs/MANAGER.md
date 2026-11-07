# Clive Manager: setup and brief

Start here in a new chat: "Read `docs/MANAGER.md` in J45PER/church-drive-cards and carry on from step 1."

The Manager is an admin-only website, with an app version, where the house's look and behaviour are set once and pushed to Home Assistant and the Android apps without a new app release.

---

## 1. Firebase: the web app and its config block (start here)

Project (already exists, it also sends the integration's push notifications):

| | |
|---|---|
| Name | Church Drive Smart Home |
| Project ID | `church-drive-smart-home` |
| Project number | `861622039700` |

### 1a. Register the web app and get the config block

1. Open <https://console.firebase.google.com> and choose **Church Drive Smart Home**.
2. Click the gear next to **Project Overview**, then **Project settings**, then scroll to **Your apps**.
3. Click the web icon `</>`.
4. App nickname: `Clive Manager`. Leave "Also set up Firebase Hosting" unticked for now. Press **Register app**.
5. The next screen shows a `firebaseConfig` block:

   ```js
   const firebaseConfig = {
     apiKey: "…",
     authDomain: "church-drive-smart-home.firebaseapp.com",
     projectId: "church-drive-smart-home",
     storageBucket: "…",
     messagingSenderId: "861622039700",
     appId: "…"
   };
   ```

   **Paste that block in the chat.** These values are not secret (they identify the project; the security rules protect the data).

### 1b. Turn on the services (left menu, "Build")

1. **Authentication**, **Get started**, **Sign-in method**, enable **Google**. Under Settings, Authorised domains, add the site address once it is known (`church-drive-smart-home.web.app` is added automatically).
2. **Firestore Database**, **Create database**, **Production mode**, location **`europe-west2` (London)**.
3. **Hosting**, **Get started** (click through the intro; deploying is done later from GitHub).
4. Stay on the free **Spark** plan. Cloud Functions would need Blaze, so avoid needing them.

### 1c. Who may use it

Send the Google account email addresses of the admins. They go in an allowlist in Firestore (`admins/<email>`), and the security rules let only those accounts write.

### 1d. Letting Home Assistant read the settings (later, never paste the key in chat)

1. Google Cloud console, **IAM and Admin**, **Service accounts**, create `clive-ha-reader`.
2. Give it only read access to Firestore (role **Cloud Datastore Viewer**).
3. Create a JSON key, download it, and put the file in the HA config folder (for example `/config/church_drive/firebase-reader.json`). Do not paste it anywhere.

### 1e. Deploying from GitHub (later)

A deploy service account (role **Firebase Hosting Admin**) as a GitHub secret named `FIREBASE_SERVICE_ACCOUNT`, so merges publish the site.

---

## 2. What the Manager sets

- Colour palettes (five colours each: Colour, Dark, Accent, Light, Deep) and new palettes.
- The category each card uses (which palette it takes), and the card design style (A tint, B gradient, C moving glow; C is chosen).
- Icon sets, so the app and Home Assistant use the same icons (today the dashboard names the icon and the app follows).
- Layouts for dashboards and app pages.

Changes are applied to Home Assistant and the apps remotely. Claude reads the data and applies it, as it has from the review boards.

## 3. Proposed architecture (confirm in the new chat)

- **Web**: static site on Firebase Hosting, Google sign-in, admin allowlist, Firestore security rules (admins write, others read nothing).
- **Data in Firestore**: `palettes`, `cards` (category and design pick per card), `icons`, `layouts`, `meta/revision`.
- **Home Assistant**: the Church Drive integration reads the Firestore config with the read-only service account and applies it through the paths below.
- **Apps**: already pull palettes live from HA, so no app update is needed for a colour change.
- **App version**: the same site as an installable web app, or a screen inside Clive Home Manager. Decide in the new chat.

## 4. What already exists

- **Live palettes on HA**: stored by the integration (`custom_components/church_drive/colours.py`, `colours_logic.py`). WebSocket `church_drive/colours` (read) and `church_drive/colours/set` (admin); service `church_drive.set_palette` (key, name, colours; admin); event `church_drive_colours_changed`; `sensor.church_drive_colours` (state is a revision number, attribute `kind_palettes` for the glow colours).
- **Followers**: the apps (`HaClient.noteColours` fetches when the sensor revision changes) and the dashboards (`src/clive-theme.js` sets CSS variables `--<name>-color`).
- **Palette source**: `design/clive/cards.json`; `tools/gen-clive-colours.mjs` writes `CliveColours.kt`, `src/clive-colours.js` and `docs/CLIVE-COLOURS.md`; `tools/export-clive-assets.mjs` writes the served copies (rerun after palette or version changes).
- **Icons**: WebSocket `church_drive/icons`; the app loads the brand pack (`phu:` icons) named in the dashboard via `IconPack.kt`; house icons are `cd:` names (`src/clive-common.js`, `CdCommon.kt`).
- **Review boards** (Claude artifacts, private): Clive Card Review `https://claude.ai/artifact/F6Ycp5g3rY2f2GDXLTYvw3` and Clive Card Designs `https://claude.ai/artifact/PRGC4nVwuMzWvWiwZQpf2G`. Their saved data shows the shape: 70 cards each with a pick and a comment; palettes with five colours.
- **Design decisions made**: moving-glow style C everywhere; the glow runs on the mobile dashboard by default (a card's `glow: true/false` overrides); section cards carry the glow and the cards inside sit on dark acrylic; text is white on dark cards with colour kept for status pills, selected buttons, temperatures, alarm status and section titles; Off and Unavailable use the neutral set.

## 5. How releases work in this repo

- Work on the designated branch only, open a draft PR from it. Before pushing use `git merge -s ours origin/<branch>` (a force push is blocked).
- Squash-merge; the `Release` workflow publishes `v<version>`; install on HA through HACS (`update_information`, then `download` with the version).
- Set versions with `node tools/versions.mjs set X`; add a section to `CHANGELOG.md` and the three Android changelogs (`companion/android/CHANGELOG.md`, `todo/`, `satellites/`).
- Run `npm test` and `node tools/export-clive-assets.mjs` before pushing.
- The Android code cannot be compiled in a session; CI (`Companion Android`) is the compiler, so check it after every merge.
- Jamie has authorised merging, releasing and installing on HA without asking each time. Leave the old drafts #341, #331 and #266 alone.

## 6. Still open from the design work

- Indoor camera icon on the app's Security page: the dashboard sets `phu:ring-stick-up-cam` (Custom Brand Icons pack); the app does not show it and the cause is not found. It belongs with icon sync.
- Per-card redesigns not done: EV and charging backgrounds, the cards not built on the shared kit, purifier button label sizing.
- To-Do and Satellites screens: they use the shared kit and have the glow and white text from 0.112.0; Jamie says they show none of the new style on any element. Ask which app version they show.
- 0.113.0 (room colour from the ceiling light, Security mode in the app, shared update banner) is on PR #361.

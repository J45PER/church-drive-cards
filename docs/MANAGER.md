# Clive Manager: design and brief

Start here in a new chat: "Read `docs/MANAGER.md` in J45PER/church-drive-cards and carry on from the first unticked step in section 9."

Clive Manager is two separate things that share one login and one set of data:

1. **The website** (Firebase Hosting, free Spark plan): the full admin and design tool.
2. **Clive Admin** (a 4th native Android app, `com.churchdrive.admin`): only the key admin features, taken out of the main app.

Colours, icons, layouts and card picks are set once and reach Home Assistant and the Android apps without a new app release.

Decisions in this document were made with Jamie in the chat that wrote it. The earlier draft (Google sign-in, Firestore as the main store) is replaced.

---

## 1. Decisions

| Topic | Decision |
|---|---|
| Sign-in | Home Assistant's own login, the same as the other apps. **No Google sign-in.** Admins only: HA says whether the user is an administrator. |
| Main data | Lives in Home Assistant (people, notifications, device health, the integration's settings). |
| Firestore | Holds only the live-changing design data (palettes and colour codes, card picks, icon picks, layouts, a revision number). Read-only to everyone; written only by Home Assistant. |
| Hosting | Firebase Hosting, free plan. No Cloud Functions (they need Blaze). |
| Website | Its own thing, bigger than the admin panel: all colour management, the designer pages, and the admin features. |
| Android app | A 4th app, **Clive Admin**, id `com.churchdrive.admin`, module `admin`. Not every feature: the key admin features only. |
| Moves out of the main app | **Manager**, **Energy** and **Device Health** (all admin-only) leave the main app's sidebar and go into Clive Admin. |
| Colour palettes in the app | **View only.** Editing is on the website. |
| Designer pages | A lot of them move onto the website as **editor pages** (section 6). An editor page does not change anything live: it sends a proposal to Claude, who checks it and applies it. Every applied change keeps a past version for rollback. |
| Branding | Clive Admin needs its own logo and animated splash (section 7). |

---

## 2. Sign-in (Home Assistant, no Google)

Firebase cannot check Home Assistant credentials on the free plan, so the Manager never asks Firebase to. It signs in to HA and talks to HA.

**Remote address:** `https://z4wyrbgulhsd63dfyimjem41urgk1lek.ui.nabu.casa` (`TrustedApps.CLOUD_URL` in `core`). The apps keep the address the person enters; the website asks for it once and remembers it in the browser.

**Website (browser):** the standard Home Assistant sign-in (IndieAuth).

1. The site sends the browser to `<ha>/auth/authorize?client_id=<site address>/&redirect_uri=<site address>/` and Home Assistant shows its own login page (username, password and the two-step code if set).
2. HA returns to the site with a `code`.
3. The site swaps it at `<ha>/auth/token` (`grant_type=authorization_code`) for an access token and a refresh token, and keeps the refresh token in the browser's storage.
4. It opens HA's WebSocket (`wss://<ha>/api/websocket`), authenticates with the access token, and asks `auth/current_user`. If `is_admin` is not true it signs out and says why. Admin is checked again by HA on every call, so the site is never what protects anything.

**Android (Clive Admin):** reuses `HaAuth.kt` and the sign-in storage in `core` (login flow, token refresh), exactly like the other apps. Non-admins are refused after sign-in.

**One HA setting is needed, by Jamie:** the site's address must be allowed in HA's `configuration.yaml`, then HA restarted:

```yaml
http:
  cors_allowed_origins:
    - https://church-drive-smart-home.web.app
```

The final line is whatever address the site ends up on (a custom domain would be added too). HA's login page also needs to be reachable from outside; the Nabu Casa address already is.

---

## 3. Data: Home Assistant writes, Firestore carries the live design data

```
Website / Clive Admin  --(admin WebSocket calls, HA login)-->  Home Assistant
Home Assistant         --(service account, Admin SDK)-------->  Firestore (design data)
Apps and dashboards    <--(read-only, live listeners)---------  Firestore
Apps and dashboards    <--(as today)-------------------------   Home Assistant
```

- The site and the app **never write to Firestore**. Firestore's rules can only check Firebase sign-ins, and these users sign in to HA. They send a change to HA; HA checks the user is an administrator, stores it, then copies it to Firestore with a service account (the Admin SDK ignores the rules).
- **Firestore rules:** `allow read` on the design collections, `allow write: if false` everywhere. The data is colours and picks, nothing secret.
- **Collections:** `palettes`, `cards` (category and design pick per card), `icons`, `layouts`, `meta/revision`.
- **HA stays the source of truth.** If Firestore is empty or unreachable, HA still serves the same data (today's `church_drive/colours`), so the apps keep working. Firestore is the fast, live copy that changes without an app release.
- **Apps:** today they fetch colours when `sensor.church_drive_colours` changes. They keep doing that until a Firestore listener is added to the apps and proven; nothing breaks if it is never added.

### New and existing Home Assistant calls (integration `custom_components/church_drive`)

Existing: WebSocket `church_drive/colours`, `church_drive/colours/set` (admin), service `church_drive.set_palette`, event `church_drive_colours_changed`, `sensor.church_drive_colours` (state is a revision number), `church_drive/icons`.

To add (all admin-only, each applied change bumps the revision and mirrors to Firestore):
`church_drive/cards` and `/cards/set`, `church_drive/icons/set`, `church_drive/layouts` and `/layouts/set`, `church_drive/manager/whoami` (the HA user's admin flag and name), and for the editor pages `church_drive/manager/propose`, `/proposals` (list, with status), `/resolve` (applied or declined, with a reason) and `/versions` (past versions, restore).

### Setting up the Firebase side (Jamie, no secrets in chat)

Project (exists): Church Drive Smart Home, ID `church-drive-smart-home`, number `861622039700`. The web app `Clive Manager` is registered; its config block (not secret) is in `web/manager/firebase-config.js`.

1. Firebase console, **Build**, **Firestore Database**, **Create database**, **Production mode**, location **`europe-west2` (London)**.
2. **Build**, **Hosting**, **Get started** (click through).
3. Stay on **Spark**. Authentication is **not** used, so leave Google sign-in off.
4. Google Cloud console, **IAM and Admin**, **Service accounts**: create `clive-ha-writer` with role **Cloud Datastore User**. Create a JSON key, download it, put it on HA at `/config/church_drive/firebase-writer.json`. **Never paste it anywhere.**
5. Deploy: a second service account with role **Firebase Hosting Admin**, stored as the GitHub secret `FIREBASE_SERVICE_ACCOUNT`, so a merge publishes the site.

Analytics is left out (the config block's `measurementId` is not used).

---

## 4. The website

Static site, no build step (ES modules, Firebase from the CDN only for Firestore reads). Lives in `web/manager/`.

- **Sign in** with Home Assistant (section 2), then the sections below.
- **Colours:** palettes (five colours each: Colour, Dark, Accent, Light, Deep), new palettes, which palette each card category takes, the card design style (A tint, B gradient, C moving glow; C is chosen), live preview of the real cards.
- **Cards:** the 70-card list from the review boards, each with its pick and a comment.
- **Icons:** icon sets, so the app and HA use the same icons (today the dashboard names the icon and the app follows).
- **Layouts:** dashboards and app pages.
- **Designer pages:** section 6.
- **Admin features** that exist in the app too (section 5), at full size.

## 5. Clive Admin (the app)

Native Android, module `admin`, application id `com.churchdrive.admin`, display name **Clive Admin**. In-app text takes the house's assistant name from the house like the other apps; **no code identifiers are named after the assistant** (`companion/android/CLAUDE.md`), so the module, package and classes are `admin` / `Admin*`.

**Contents (first version):**
- **Device Health** (moved out of the main app, the admin-only `devices` page).
- **Manager** (moved out of the main app): the sections now behind Account, Manager in the main app (people, cameras, reminders, notifications, Who's home, device health and so on). Today: `ManagerHub` in `AccountPanel.kt`, `ManagerPages.kt`, `ManagerCards.kt`, and the cached `manager` config in `AppViewModel`.
- **Energy** (moved out of the main app): `Page.Energy` in `Pages.kt` and `EnergyCards.kt`, `EnergyData.kt`, `EnergyWeek.kt`.
- **Colour palettes: view only.** Shows the palettes and which card uses which. No editing.
- Sign-in as section 2.

**Removed from the main app:** the Energy page, the Device Health page and the Manager entry. The main app keeps a link that opens Clive Admin (or the Play listing if not installed). Notice kinds such as Energy and car stay in the main app, because they are notifications, not pages.

**Shared code:** anything both apps use stays in `core` (sign-in, HA connection, the card kit). Move shared pieces to `core` rather than copying them. Follow `UI-STANDARDS.md` for every screen.

**Housekeeping that comes with a 4th app** (copy how `satellites` was added):
- `settings.gradle.kts` include; `admin/CHANGELOG.md` with a section for every version; `node tools/versions.mjs` knows the module; `docs/VERSIONING.md` updated.
- One version number across all apps and the integration, one `v<version>` GitHub release; a rolling `admin-latest` build for the update check.
- CI (`Companion Android`) builds and tests it; the Android code cannot be compiled in a session, so CI is the compiler.
- Play: a new listing for `com.churchdrive.admin`, internal testing track, with the same `PLAY_SERVICE_ACCOUNT_JSON` service account needing an invite on the new listing.

## 6. Designer pages that move to the website

Candidates, from `satellite/design/` and `design/clive/`: `icons-board.html`, `icon-audit.html`, `clive-icons.html`, `icon-ideas*.html`, `icon-cog.html`, `icon-device.html`, `app-icons.html`, `app-names.html`, `brand-logos.html`, `type-combos.html`, `splash-*.html`, `idle-layouts.html`, `device-codes.html`, `flows.html`, `scanner-*.html`, `design/clive/icon-morphs.html`, and the two private review boards (Clive Card Review `https://claude.ai/artifact/F6Ycp5g3rY2f2GDXLTYvw3`, Clive Card Designs `https://claude.ai/artifact/PRGC4nVwuMzWvWiwZQpf2G`).

**Decided: they are editor pages, with a check step and version history.**

1. **Edit:** an admin changes a palette, card pick, icon or layout on the page and sees a preview.
2. **Propose:** Save sends a *proposal* to Home Assistant (`church_drive/manager/propose`, admin-only), stored with who, when, what changed and an optional note. Nothing goes live.
3. **Check and apply:** Claude reads the open proposals (through Home Assistant), checks them (a colour that makes text unreadable, an icon that does not exist, a layout that breaks the shared kit), applies them to the repo and Home Assistant as needed, and marks each *applied* or *declined* with a reason, shown back on the page.
4. **Versions:** every applied change stores the full previous version (`church_drive/manager/versions`, mirrored read-only to Firestore as `history/<kind>/<revision>`). The website lists past versions with a diff and a **Restore** button; a restore is itself a proposal, so it is checked and applied the same way, except that an admin may mark one **urgent** to apply straight away.
5. Pages with nothing to apply (pure reference boards, such as the icon audit) stay static.

Start with the colour and card pages because they drive the live data.

## 7. Branding for Clive Admin

- Its own logo (app icon, adaptive and monochrome) and an animated splash, in the same family as the other apps (see `satellite/design/splash-*.html`, `BrandSplash.kt`, the wear and satellites icons). Make options on a design board first and let Jamie choose, as was done for the others.
- Colours come from the house's palettes, not hard-coded.

## 8. What already exists

- **Live palettes on HA:** `custom_components/church_drive/colours.py`, `colours_logic.py`. WebSocket `church_drive/colours` (read) and `church_drive/colours/set` (admin); service `church_drive.set_palette`; event `church_drive_colours_changed`; `sensor.church_drive_colours` (state is a revision number, attribute `kind_palettes` for the glow colours).
- **Followers:** the apps (`HaClient.noteColours` fetches when the revision changes) and the dashboards (`src/clive-theme.js` sets CSS variables `--<name>-color`).
- **Palette source:** `design/clive/cards.json`; `tools/gen-clive-colours.mjs` writes `CliveColours.kt`, `src/clive-colours.js` and `docs/CLIVE-COLOURS.md`; `tools/export-clive-assets.mjs` writes the served copies (rerun after palette or version changes).
- **Icons:** WebSocket `church_drive/icons`; the app loads the brand pack (`phu:` icons) via `IconPack.kt`; house icons are `cd:` names (`src/clive-common.js`, `CdCommon.kt`).
- **Sign-in:** `HaAuth.kt` (login flow, tokens, refresh) in `core`.
- **Design decisions made:** moving-glow style C everywhere; the glow runs on the mobile dashboard by default (a card's `glow: true/false` overrides); section cards carry the glow and the cards inside sit on dark acrylic; text is white on dark cards with colour kept for status pills, selected buttons, temperatures, alarm status and section titles; Off and Unavailable use the neutral set.

## 8a. The status banner (website)

`web/manager/status.js` is the website's large version of the pill the apps show at the top of their pages (reference: `design/clive/link-pill.html`; app: `core/.../LinkRefresh.kt`). It starts as Clive's round button, widens into a banner 120 px tall and 464 px wide, and keeps the colour cloud drifting behind it. It sits fixed under the header, so it also shows on the "Connecting" and retry screens.

- **Faces:** Connecting, Updating, No connection (after 6 s down; Try now), Home Assistant restarting, and on a touch screen Pull to refresh, Release to refresh, Up to date. A pull reloads the current page; the browser's own pull-to-reload is switched off (`overscroll-behavior-y: contain`).
- **What it says** is only what the site knows: the house's address, the page, the attempt and countdown, when the line was last up, Home Assistant's version and state (`get_config`) and how fast it answered (`auth/current_user`). The design page's item counts are sample data; the site does not read every state, so it never shows them.
- **Restart** is told by the close code Home Assistant uses (1001, 1012, 1013; `HaSocket.onClose(code)`) or by `get_config` saying it is not `RUNNING` yet; it lasts until the page has loaded again, and five minutes at most.
- **Opening and closing.** It appears as a circle and widens; closing runs that backwards (the banner narrows into the circle, then the circle shrinks, rises and fades). Pulling down grows the circle with the finger (from 30% size and clear, full by half way to Release) instead of popping it up; a pull that starts while the banner is already showing leaves its size alone. A touch that scrolls the page is never a pull.
- **Icons.** Only the house's own: Clive's mark, and the Clive set's `cd:wifi-off` (No connection), `cd:update-arrow` (restarting) and `cd:check` (Up to date), read through `icons.js` like every other icon on the site. The tick is wiped in from the left over 380 ms instead of drawn stroke by stroke.
- **Staying fresh.** `freshness.js`: back to the tab after a minute or more away, and every five minutes while a page is open, the page is read again with the Updating pill (then the Up to date tick), keeping the scroll place. Only pages marked `live` (Home, Health, Energy, Activity) are refreshed; the editors never are, and nothing is redrawn while a field has focus, a dialog is open, or the page was touched in the last ten seconds. The apps do the same (`Freshness.kt`): back after a minute away reads the whole house again with the pill; every five minutes it reads quietly and shows the pill only if it found something stale.
- **One width.** The pill and its cloud are drawn from the same width every frame, so the cloud never resizes ahead of or behind the pill. (The apps had this wrong in 0.135.0: the cloud measured the final width at once.)
- Rules and words are tested in `test/manager-status.test.mjs`.

## 8b. Decisions on what lives where (Jamie, 2026-11-11)

- **People** stays web-only. **Locations** and **Icons** (mode icons) are not needed in Clive Admin for now (the phone app and the website keep them).
- **Devices** (TVs and speakers, devices and services) goes in Clive Admin and on the website (0.137.0).
- **Alarm lights** is web-only (the phone app never had it). **Watch settings** is in all three.
- **When things leave the phone app:** Energy and Device health once Clive Admin's version is the same design and layout as the original; the Manager once its features have moved to whichever of Clive Admin or the website applies.
- **House style:** everything on the Manager website matches the apps' style (section panels with an icon, a coloured title and a summary pill, tone cards in the palette colours). The website's Energy page therefore follows the dashboard's Energy panels like the apps.
- **Firestore is wanted:** Home Assistant mirrors the design data (palettes, card picks, icons, layouts) and its history, and the same store holds backups of that history.

## 9. Build order

Tick these off as they are done.

- [x] Firebase web app registered; config block received.
- [ ] Jamie: the steps in `docs/MANAGER-SETUP.md` (Firestore (Production, `europe-west2`) and Hosting turned on; `clive-ha-writer` key on HA; `cors_allowed_origins` set and HA restarted).
- [x] Integration (0.133.0): proposals and version history for palettes, with tests (`test/proposals_logic_test.py`): `church_drive/manager/whoami`, `/propose` (set, replace, reset, or `restore` a past version; `urgent` applies at once), `/proposals`, `/resolve`, `/versions`; `sensor.church_drive_proposals` (state = number waiting, attribute `pending` = each change with its automatic checks); services `church_drive.apply_proposal` and `church_drive.decline_proposal` (with a reason). **How Claude checks:** read `sensor.church_drive_proposals` through the Home Assistant connection, look at each change and its `checks`, then call `apply_proposal` (the previous palettes are kept as a version first) or `decline_proposal` with the reason the website shows.
- [ ] Integration: the Firestore mirror (HA copies palettes, cards, icons, layouts and history to Firestore with the `clive-ha-writer` key), and proposals for cards, icons and layouts (only palettes so far).
- [x] `firestore.rules`, `firebase.json`, `.firebaserc` and the publish workflow (`.github/workflows/manager-site.yml`, which publishes only once the `FIREBASE_SERVICE_ACCOUNT` secret exists: `docs/MANAGER-SETUP.md`, part E).
- [x] Website v1 (`web/manager`): Home Assistant sign-in, administrators only, the app's look, Home and Colours (view only). Tested against a mocked Home Assistant (`test/manager-web.test.mjs` for the logic).
- [x] Website: the Manager (web only, no version): the sections of the house's Manager dashboard. Built: People (phones, places, alerts, to-dos, arrivals), Who's home, Notifications, Locations (view), Camera links, Icons, To-do settings and other settings cards, Device health link. Also built (no version): Alarm lights and Watch settings. Not yet: automatic to-dos and house jobs, cars and pictures. These change settings straight away through the integration's existing admin commands, as the apps do, not through proposals.
- [x] Website: change a palette (sent for Clive to check, Changes page with history and restore: 0.133.0).
- [x] Website (0.134.0): the Design page: Cards (the palette each of the 70 cards uses, with a comment: recorded by the integration and versioned), Icons (a dashboard panel's icon, from the `cd:` set) and Layouts (order and hiding of a view's panels). Icons and layouts are changed on the dashboard by Claude when checking (`ha_config_set_dashboard`), then `church_drive.apply_proposal` records it with what it replaced (the proposal carries `before`). Card picks are applied by the integration and read back through `church_drive/manager/design`; the apps do not read them yet, so Claude applies a pick where the card is drawn. Still open: the designer pages (icon audit, splash and logo boards), and the Firestore mirror.
- [ ] Website: designer pages, then the designer pages, Device health, Energy and Activity.
- [x] Clive Admin v1 (0.128.0, the same number as every other app and the integration): `admin` module, `com.churchdrive.admin`, Home Assistant sign-in (or the phone app's), administrators-only check, palettes view-only, update check, CI, placeholder logo. Needs a first CI run, then an install on Jamie's phone to prove sign-in.
- [ ] Clive Admin: Device Health and Energy are done (0.130.0); the Manager is in progress (0.132.0: the hub, Notifications, Locations, Camera links, Icons, To-do settings and house jobs; still to move: People, Who's home, Device health list card, Alarm lights, Watch, automatic to-dos). Original note: the Manager sections, one at a time, each native in the admin module (the phone app's card kit is too tangled to move: about 90 files reach it). Nothing leaves the phone app until Clive Admin is proven; then the phone app opens the right Clive Admin page, as it does for To-Do.
- [ ] Play: create the `com.churchdrive.admin` listing, invite the service account, add its Play signing fingerprint to `TrustedApps.CERTS` (the Play upload step is `continue-on-error` until then).
- [ ] Remove Manager and Energy from the main app (only after Clive Admin is installable).
- [x] Clive Admin logo and animated splash: the house's shield (`cd:shield`) with Clive's mark cut out of its middle (idea D on `satellite/design/admin-logo.html`), generated by `tools/gen-admin-logo.mjs` into `LogoRings.kt` and the launcher icon.
- [x] Firestore mirror (0.140.0): the integration copies the live palettes (`palettes/current`), card picks (`cards/picks`), the version history of every kind, which is the backups (`history/<kind>-<revision>`), and `meta/revision` to Firestore with the service account at `/config/church_drive/firebase-writer.json`; a few seconds after any change, only the documents that changed, and `church_drive.sync_firestore` forces a full copy. Without the key nothing changes. Icons and layouts live on the dashboards themselves, so what is mirrored for them is their history.
- [ ] Apps and dashboards read Firestore live (optional; HA keeps working without it).

## 10. How releases work in this repo

- Work on the designated branch only, open a draft PR from it. Before pushing use `git merge -s ours origin/<branch>` if the remote branch has moved (a force push is blocked).
- Squash-merge; the `Release` workflow publishes `v<version>`; install on HA through HACS (`update_information`, then `download` with the version).
- Set versions with `node tools/versions.mjs set X`; add a section to `CHANGELOG.md` and every Android changelog (`companion/android/CHANGELOG.md`, `todo/`, `satellites/`, and `admin/` once it exists).
- Run `npm test` and `node tools/export-clive-assets.mjs` before pushing.
- Jamie has authorised merging, releasing and installing on HA without asking each time. Leave the old drafts #341, #331 and #266 alone.

## 11. Still open

- Indoor camera icon on the app's Security page: the dashboard sets `phu:ring-stick-up-cam`; the app does not show it. It belongs with icon sync.
- Per-card redesigns not done: EV and charging backgrounds, the cards not built on the shared kit, purifier button label sizing.
- To-Do and Satellites screens show none of the new style on any element (Jamie): ask which app version they show.
- 0.113.0 (room colour from the ceiling light, Security mode, shared update banner) is on PR #361; this document came from that branch.


### 0.140.0: the website as a website, and the boards moved across

- **Navigation:** Clive's header (mark, wordmark, top navigation in three groups: The house, Design, Manage; account menu), a hamburger drawer under 900px, a footer. The old bottom bar and side rail are gone.
- **Home:** grouped house-style panels with a tone card per page.
- **Icon library:** every icon in the served set (324 drawn, the 69 still to draw), searchable, by family, with the code to copy. **Idea boards:** the 22 boards in `satellite/design/` and `design/clive/` are copied into the site when it is published and listed by theme (Brand and names, Icons, Screens, Satellites); each opens inside the Manager. They stay as the record of what was tried and chosen; the editor pages that apply a chosen board to the house (card design style A/B/C, splash, app icons) are still to come.
- **Energy:** now complete against the apps (last 7 days, cars, Zappi charger, Octopus charging plan).

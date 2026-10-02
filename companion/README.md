# Church Drive companion apps

Planning and source for the apps that sit alongside the Church Drive integration.
This branch is a scratch space for design; nothing here ships with the HACS integration.

## Planned apps

1. **Kiosk app (Android)**: runs on wall-mounted Android tablets. Full-screen,
   locked to a chosen Home Assistant dashboard, auto-reconnects, screen on/off
   and brightness controlled from HA, and reports device state back to HA
   (battery, screen, app version, current URL).
2. **User app (Android/iOS)**: a Church Drive branded app for residents, so
   Ian and Diane don't need the stock Home Assistant app. Signs in to Church
   Drive's HA, shows the dashboard, and handles notifications and presence.
3. **Manager app (Windows)**: one place to see and manage the fleet.
   - Android kiosk devices: configure, push updates, restart, set dashboard URL, view status.
   - Embedded browser-based panels (e.g. a Shelly wall panel running the normal
     HA frontend): can't run our app, so manage them only to the extent the
     device exposes (see open questions). Fallback: manage Android kiosks only.

## Decisions

- **Android apps are native** (Kotlin + Jetpack Compose), not a wrapper around the HA
  frontend. They talk to HA over its WebSocket/REST API and draw their own UI.
- Each kiosk has its own screen (layout) and its own HA user/token, so one tablet can
  be changed or revoked without affecting the others.

## Consequences of going native

- The existing Lovelace cards (`src/`, JavaScript) can't run in a native UI. Each
  card a kiosk needs (light control, alarm panel, etc.) has to be rebuilt in Compose.
- So the native app needs its own screen definitions: which cards, in what layout,
  per kiosk. Preferred: stored in HA via the Church Drive integration, so the manager
  can edit them and the kiosk fetches them, instead of baking them into the APK.
- Possible hybrid: a native shell with a "web card" tile for anything not yet ported.

## Open questions

- Manager <-> devices: via the HA API (entities/services exposed by a Church Drive
  companion integration) or direct to each device? Going via HA is simpler and
  works for any device HA already knows about, including the Shelly panel.
- What can we control on the Shelly panel? Likely only what its HA integration
  exposes (relays, firmware update), not kiosk settings. Needs checking.
- Notifications and presence for the User app: reuse HA's mobile_app integration
  protocol, or something simpler?
- Distribution: sideloaded APK for kiosks; Play Store / TestFlight or private for users?

## Suggested order

1. Kiosk app MVP (immediate payoff, simplest).
2. Church Drive integration additions: a `church_drive` device registry for companions.
3. Windows manager for Android kiosks.
4. User app.
5. Embedded panels in the manager, if feasible.

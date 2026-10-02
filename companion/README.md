# Church Drive companion apps

Planning and source for the apps that sit alongside the Church Drive integration.
This branch is a scratch space for design; nothing here ships with the HACS integration.

## Planned apps

1. **Kiosk app (Android)**: runs on wall-mounted Android tablets. Full-screen,
   kiosk-locked, with a native UI designed for its room. Auto-reconnects, screen
   on/off and brightness controlled from HA, and reports device state back to HA
   (battery, screen, app version, current layout).
2. **User app (Android)**: a Church Drive branded app for residents, so
   Ian and Diane don't need the stock Home Assistant app. Signs in to Church
   Drive's HA, shows a curated set of native controls, and handles notifications
   and presence.
3. **Manager app (Windows)**: one place to manage both Android apps.
   - Kiosk devices: configure, set each tablet's room layout, restart,
     push updates, view status.
   - User app: manage which screens/controls each person gets, who has the app
     and their access, notification settings, and revoke a lost phone.
   - Embedded browser-based panels (e.g. a Shelly wall panel running the normal
     HA frontend): can't run our app, so manage them only as far as the device
     exposes (see open questions). Fallback: kiosks and user app only.

## Decisions

- **Both Android apps are fully native** (Kotlin + Jetpack Compose), talking to HA over
  its WebSocket/REST API. No WebView wrapper around the HA frontend.
- **Kiosk UIs are room-appropriate**: each kiosk gets a layout designed for its room,
  not a generic dashboard. Each kiosk has its own HA user/token, so one tablet can be
  changed or revoked without affecting the others.
- **Android only**: Ian and Diane both use Android, so no iOS app is needed.
- **User app is curated**: a limited set of screens and controls, not full HA.
- **Priority: user app first**, then kiosks.
- Shared code: one Kotlin module for the HA connection, auth, entity state and the
  native controls (light, alarm, scenes, cameras) that both apps use.

## Consequences

- The Lovelace cards in `src/` can't be reused; the controls both apps need are
  rebuilt in Compose, and only those (not every card).
- Screen layouts (which controls, which room template) should live in HA via the
  Church Drive integration, so the Windows manager can edit them and apps fetch
  them without a new APK.

## User app pages (draft)

Taken from the current `dashboard-mobile` views, without Media, Devices or Energy.
The car charger stays, for all users. Each page is a stack of titled sections, as on the dashboard.

| Page | Sections |
|---|---|
| Home (Quick Actions) | Security (alarm), Climate (downstairs), Lights, Cleaning (vacuum), Car charger |
| Lighting | Ground Floor, Middle Floor, Top Floor, Garden, Front Garden |
| Security | Alarm, Safety, Outdoor cameras, Indoor cameras, Doors & Motion |
| Climate | Heating, Climate zones, Cooling (fan), Air quality, Windows & Doors (blind) |
| Cleaning | Vacuum controls and settings |
| To-do | From the house, My to-do, Shared, Cleaning |

Native pieces needed: alarm panel, light control, climate (and zone), camera, safety,
security zone, fan, air purifier, CO alarm, cover, vacuum, EV charger (the Home page
shows it), house tasks and task list.

## Native widgets

A main purpose of the app is to provide Android home-screen widgets that look like the
cards in their sections (alarm, lights, climate and so on), so common actions don't
need the app opened.

## Open questions

- Manager <-> devices: via the HA API (entities/services exposed by a Church Drive
  companion integration) or direct to each device? Going via HA is simpler and
  works for any device HA already knows about, including the Shelly panel.
- What can we control on the Shelly panel? Likely only what its HA integration
  exposes (relays, firmware update), not kiosk settings. Needs checking.
- Notifications and presence for the User app: reuse HA's mobile_app integration
  protocol, or something simpler?
- Distribution: sideloaded APK for kiosks; Play Store (private/internal track) or sideloaded APK for users?

## Suggested order

1. Shared Kotlin core (HA connection, auth, state) plus the Church Drive integration
   additions for per-device/per-person settings.
2. User app MVP (alarm, lights/scenes, notifications, presence).
3. Kiosk app with the first room template.
4. Windows manager (user app and kiosk management).
5. Embedded panels in the manager, if feasible.

# Church Drive Cards — Handoff to local Claude Code

## Where this stands

The repo (`church-drive-cards`) is fully scaffolded, built, and **committed locally**
(git history included in this zip) but **not yet pushed**. The cloud session that
built it can't push: its git/GitHub credentials are proxy-restricted to a pre-bound
repository set and this repo isn't in it, and there's no way to add it to that set
from inside the chat. Pushing from your own machine (outside that proxy) is the fix.

**GitHub repo already exists**: `https://github.com/J45PER/church-drive-cards.git`
(private, created empty — no README/gitignore/license, so it's a clean target).

You already generated a fine-grained PAT scoped to this repo (Contents: read/write)
while setting this up — reuse it, or generate a fresh one via
GitHub → Settings → Developer settings → Personal access tokens → Fine-grained tokens.

## To push (run in a terminal with this unzipped folder as cwd)

```bash
git remote -v          # should already show origin -> the URL above; add if missing:
# git remote add origin https://github.com/J45PER/church-drive-cards.git
git push -u origin main
# when prompted for a password, paste the PAT (not your GitHub password)
```

If you'd rather embed the token in the URL for a one-off push instead of a credential
prompt:
```bash
git push https://<TOKEN>@github.com/J45PER/church-drive-cards.git main:main
```

## What's in the repo

Three custom Lovelace cards, bundled into one HACS-installable plugin
(`church-drive-cards.js`, built via esbuild — see `README.md` for full usage/YAML
examples and HACS install instructions):

- **`battery-zone-card` / `gauge-zone-card`** (`src/gauge-zone-card.js`) — zone/room
  card, gradient-filled rows, worst-first sorting, red/orange/green thresholds,
  feathered row seams, battery-icon auto-stepping. Confirmed working live.
- **`alarm-panel-card`** (`src/alarm-panel-card.js`) — status header, live
  entry/exit countdown, icon-only arm/disarm buttons, hover-only whole-card tint.
  Confirmed working live.
- **`light-control-card`** (`src/light-control-card.js`) — mode-aware
  (light/group/room) control with a real visual editor (`ha-form`-based). **Has
  known unresolved bugs** — see below.

## `light-control-card` status (updated 2026-09-24)

Checked against the real Church Drive HA registry. Fixed and **pushed live** to
resource `e4b8b41e6c4e48aab5c0894734db8c87`; still needs a look on a real device:

1. **White/bright lights unreadable.** Rows now blend the light colour into the card
   background at 30% (`color-mix`), and text uses theme colours. Checked in headless
   Chromium with 6500K white, 2700K warm and saturated red/blue; all readable.
2. **Room title at top.** `.lcc-title` at `1.5rem/500`, shown in room mode only.
3. **"Room mode shows zero lights".** Not a room-mode bug. The registry lookup was
   fine: Living Room resolves 4 bulbs through their *device* area, plus the
   `light.living_room` Hue room group. The lone "Living Room (group)" card with nothing
   under it was the `mode: group` demo card, which never listed members. Now:
   - `group` mode lists the group's `entity_id` members as indented rows under it.
   - `room` mode shows the area's Hue room/zone groups as the top row(s) and the
     individual bulbs indented underneath. It skips hidden entities and entities with an
     `entity_category` (e.g. `light.hayleys_bedroom_air_purifier_display_backlight`).
     Hue zones that have no area (`light.living_room_ambience`, etc.) aren't shown.
4. **Drag-to-dim getting interrupted.** The card rebuilt every row on each `hass` update,
   i.e. any state change anywhere in the house, which could replace a row
   mid-drag. It now only rebuilds when one of its own lights changes, and never mid-drag.

Rollback: the previous live build is effectively this repo's first commit of
`src/light-control-card.js`, plus a filter that dropped group entities from room mode.

## Other outstanding asks from the user (not yet started)

- ~~Card size / auto-size~~ **Done 2026-09-24.** All three cards report a real
  `getCardSize()` for masonry views. `getGridOptions()` defaults them to full width
  (min half) in sections views, where the card editor's own **Layout** tab resizes
  them. The live dashboards (Battery Status, Alarm, Design Presets) are masonry.
- ~~Visual editors~~ **Done 2026-09-24.** The battery/gauge and alarm cards have
  `ha-form` editors built on the shared `src/form-editor.js` (the light card uses it
  too). The gauge "Rows" list uses HA's `object` selector with `fields`, and the alarm
  demo options sit in an expandable section.
- **Non-Hue lighting support** — user is planning to add non-Hue bulbs in future.
  The card's scene/toggle logic is already brand-agnostic; only the
  dynamic-effects pop-up currently leans on Hue's native `effect_list`/`effect`
  attributes via `hass-more-info`. Explicitly deferred by the user until real
  non-Hue hardware exists — don't build speculatively.

## Live Home Assistant state

Since 2026-09-24, the cards are **installed through HACS** as a custom repository
(`J45PER/church-drive-cards`, category Dashboard, HACS id `1385560733`). HACS
registered the resource `/hacsfiles/church-drive-cards/church-drive-cards.js`. The
three hand-pushed inline resources (`828809e8…`, `46b33cd0…`, `e4b8b41e…`) have been
deleted.

Release flow: bump `version` in `package.json` and merge to `main`. The **Release**
workflow publishes `vX.Y.Z` and HACS shows it under Settings → Updates. HACS only
re-checks custom repos about every 48h, so run "Update information" on the repo in
HACS (or `ha_manage_hacs action=update_information`) to see a release straight away.

Testing convention: try changes on the **Design Presets** dashboard first. Card
resources are global, so a release reaches every dashboard at once; test before
bumping the version.

Rollback: install an older release from HACS (Redownload → pick version).

Beta channel: HA also loads `church-drive-cards-beta.js` (cards renamed `…-beta`)
from jsDelivr, pinned to a commit, as its own dashboard resource. Repoint that
resource's URL at a branch commit to try a change on the Design Presets **Beta**
tab before merging. See the README's "Beta testing" section.

## Dev loop

```bash
npm install
npm run build     # outputs church-drive-cards.js at repo root (esbuild, IIFE)
```

`.github/workflows/build-check.yml` was lost in the original web upload and was
restored on 2026-09-24. It fails a PR if the committed bundle is stale.

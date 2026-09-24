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

## Known bugs / unfinished work on `light-control-card`

These fixes are written into `src/light-control-card.js` in this zip, but were
**never verified live** against the real Home Assistant instance before this
handoff — the room-mode "no lights" bug in particular has survived 2-3 previous
fix attempts, so treat the current code as a hypothesis, not a confirmed fix:

1. **White/bright lights made rows unreadable** — was literally painting the row
   background with the light's live colour (solid white behind white text for a
   bright/white light). Fix applied: blend the colour into the card background at
   30% via `color-mix(in srgb, ${color} 30%, var(--card-background-color))`
   instead of a literal fill. **Needs visual confirmation** across a range of
   colour temperatures/brightnesses, not just white.

2. **Room title not at the top of the card** — should match the Battery Status
   card's title convention (top of card, `font-size:1.5rem; font-weight:500`).
   Styling was added (`.lcc-title` block, shown only in room mode) but **not
   re-verified live**.

3. **Room mode shows zero individual lights** (the persistent, still-unresolved
   bug). Root cause theory: Hue (and possibly other integrations) often set
   `area_id` on the *device*, not the entity, so a naive
   `hass.entities[x].area_id === areaId` filter comes back empty. Current code
   (`lccAreaOf()` in `src/light-control-card.js`) checks entity area first, then
   falls back to `hass.devices[entry.device_id].area_id`. Two earlier attempts
   also excluded "group-like" entities (where `attributes.entity_id` is an
   array) from the member list, on the theory they'd duplicate the room itself —
   but the user reported that *still* produced zero individual lights (only a
   lone "Living Room (group)" card, no rows underneath). The **current version
   removed that exclusion entirely** — reasoning that if the *only* entity with a
   resolvable area in a room is the group/zone entity itself (plausible for Hue),
   excluding it legitimately empties the list. **This needs to be tested against
   the real HA instance first** — add the card in `mode: room` for a real area
   (e.g. `area: living_room`) and confirm individual light rows actually render
   below any group entity.

## Other outstanding asks from the user (not yet started)

- **Card size / auto-size configuration** — no option currently exists on any of
  the three cards. Likely needs a `grid_options` field surfaced in each editor
  plus a proper `getCardSize()` implementation.
- **Visual editors for `battery-zone-card` / `gauge-zone-card` and
  `alarm-panel-card`** — currently YAML-only. Only `light-control-card` has a
  real `ha-form`-based editor (`LightControlCardEditor` in
  `src/light-control-card.js`) to use as the pattern to replicate.
- **Non-Hue lighting support** — user is planning to add non-Hue bulbs in future.
  The card's scene/toggle logic is already brand-agnostic; only the
  dynamic-effects pop-up currently leans on Hue's native `effect_list`/`effect`
  attributes via `hass-more-info`. Explicitly deferred by the user until real
  non-Hue hardware exists — don't build speculatively.

## Live Home Assistant state (separate from this repo, not yet synced)

The **live** dashboard resources in Home Assistant (Church Drive Home Assistant
instance) still run older, hand-pushed JS blobs registered via
`ha_config_set_dashboard_resource` — they predate this repo and have **not** been
replaced with builds from here yet:

- `battery-zone-card`/`gauge-zone-card` — dashboard resource id
  `828809e8e65544588bdf477cdcb325b8` (matches this repo's version, believed current)
- `alarm-panel-card` — dashboard resource id `46b33cd080e845558979afa89837a13d`
  (matches this repo's version, believed current)
- `light-control-card` — dashboard resource id `e4b8b41e6c4e48aab5c0894734db8c87`
  (**does NOT yet have the bug fixes in this repo** — still has the white-fill
  contrast bug, title issue, and room-lookup bug live)

Once the fixes above are verified, either keep pushing built bundles to that
resource id directly, or switch to installing via HACS custom repository pointing
at `github.com/J45PER/church-drive-cards` (cleaner going forward — this is what
the whole repo/plugin effort was for).

## Dev loop

```bash
npm install
npm run build     # outputs church-drive-cards.js at repo root (esbuild, IIFE)
```

The `.github/workflows/build-check.yml` CI job just verifies the committed bundle
matches a fresh build — it does not auto-commit.

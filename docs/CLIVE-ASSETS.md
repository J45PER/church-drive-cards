# Clive's icons and cards, hosted by Home Assistant

So Clive's icons and card designs can change without a new app release, they are two files Home Assistant serves, and the apps, the dashboard cards and the satellite tools read them.

| File | URL | Made from |
|---|---|---|
| `icons.json` | `/church_drive/clive/icons.json` | `design/clive/house-icons.json`, a snapshot of the **Church Drive icons** page (the only icon source besides `phu:`) |
| `cards.json` | `/church_drive/clive/cards.json` | `design/clive/cards.json`: palettes, tones, what each card kind shows, the icon animations, Gregg's states and the morph |

Both are served from the integration's `frontend/` folder (already a static path), so there is nothing new to install.

```bash
node tools/export-clive-assets.mjs           # regenerates both from design/clive/
node tools/export-clive-assets.mjs --check   # fails if the served files are stale (use in CI)
```

## What is in the icon file

Every icon is `{ n: name, s: section, p: path markup }` on a 200 x 200 grid, filled with the text colour (even-odd). Names are the ones on the icons page:

| Prefix | What | Where it comes from |
|---|---|---|
| `cd:` | The house's own icons | `icons.json` |
| `GreggIcon …` | Gregg's seven status icons (the ring is drawn by code in the apps; these are the still frames) | `icons.json` |
| `sat_…` | Satellite icons | `icons.json` |
| `AssistantGlyph …` | Clive's mark in each pose | `icons.json` |
| `hue:` / `ring:` | Our own Hue and Ring brand icons, drawn one at a time | `icons.json` once drawn; listed under `pending` until then |
| `phu:` | The Hue-style pack from HACS (Custom Brand Icons) | **Not copied.** It stays in the HACS pack Home Assistant serves (the apps already read it, `IconPack`), and is used only until the same icon exists as `hue:` or `cd:` |
| `si:` / `ol:` | Brand logos from Simple Icons, and our own where it has none | Read by name from their own files, as now |
| `mdi:` | Material | Not allowed for anything new |

Icons that are still a dotted placeholder on the icons page are listed under `pending` and are not served.

## What is in the cards file

* `palettes` (the glow colours a card sends by name) and `tones`.
* `kinds`: the fields each card kind takes and its icon. `heat` (now, a double chevron, target) and `heat2` (a row per thermostat) are new.
* `icons`: the animation of an icon while its card is up (the fan spins, the volume cycles, the lock opens then closes, the blind extends).
* `gregg`: which status icon, motion and glow each state of Gregg uses, and which action leads to which state.
* `morph`: every icon on a card arrives as a morph from Clive's mark, and a card that changes (Gregg's state, a level) morphs from the icon before it.

A new **kind** of card still needs code in the apps. Everything else here is data.

## What the apps read from the house today

* **Colours**: `church_drive/colours` (the palettes, with the owner's changes).
* **Done-card motions and misheard names**: `church_drive/assist/info` answers `motions` (the rules in `done_motions.rules`: which motion a done card plays, from its icon and detail), `heard` (`heard.corrections`: a misheard word and the name that was said) and `names` (the household's first names). The phone app keeps the last answer, and has a built-in copy (`companion/android/core/src/main/assets/clive-assistant.json`, written by the export tool) for before the house has answered.

To change the motions or the corrections without any release, call the service on Home Assistant; the apps take it the next time they connect:

```yaml
action: church_drive.set_assistant_data
data:
  heard: { hailey: Hayley, dianne: Diane }   # replaces the whole list
  # motions: [ ...rules... ]                 # replaces the whole list
  # reset: true                              # back to the shipped ones
```

## Next

The icons are not read from the house yet: the apps draw their built-in set. The served `icons.json` differs from it (10 icons redrawn, 13 the apps do not have), so a loader needs a preview of what would change first. A new **kind** of motion or card still needs code in the apps.

# App look and feel: the rules

The user app should look like one app. These rules are what keeps a fan, an air purifier and a thermostat
from looking like three different people built them. **Read this before changing or adding any card, page or button.**
The mechanical ones are checked by `UiStandardsTest` (it fails the build); the rest are a checklist.

## Before every change or addition

1. **Look at the dashboard first.** Open the matching card's source in `src/*.js` (or the Lovelace config) and copy
   what it shows: the layout, the labels, the icons, which rows there are. The app mirrors the dashboard; any
   difference is deliberate and said so in the commit.
2. **Use the kit, don't draw your own.** Everything shared is in `ui/UiKit.kt` and `ui/ModeIcons.kt`:
   - Choices (fan speeds, purifier modes, thermostat shortcuts, charger modes, blind, camera buttons): `TileRow` with
     `TileItem`s. Icon-only controls (vacuum): `IconRow`. A list of plain text options (suction level): `OptionRow`.
   - A card's frame: `EntityCard`. A titled group: `SectionPanel`. Icons: `HaIcon`. Colours: `Tone` and `toneColors`.
   - Never a raw `Button`, `OutlinedButton`, `TextButton`, `FilledTonalButton` or `FilledIconButton` in a card.
3. **Sizes come from `Ui`.** Choice tiles are `Ui.TileHeight` (48 dp); an icon above a name (alarm modes, scenes, a
   light's pill) is `Ui.TallTileHeight` (64 dp). Icon `Ui.TileIcon`, text `Ui.TileText` or `Ui.TileTextStacked`,
   gap `Ui.TileGap`. Never type a tile height or icon size in a card.
4. **Labels use `CentredText`, never a bare `Text`, on tiles and buttons.** A bare `Text` leaves space under the
   letters, so the icon and label look pushed to the top of the tile.
5. **Rows fill their width.** Tiles go in rows of up to `Ui.TilesPerRow` (4) and share the width evenly
   (`tileRowSizes`: five tiles are 3 + 2, seven are 4 + 3). A short last row's tiles are wider; there is never blank
   space at the end of a row, and the row never scrolls sideways. Tiles that belong together (like the fan's Off and
   speeds, then its modes) go in separate `TileRow`s, as on the dashboard.
6. **Icons mirror Home Assistant.** An icon for a mode goes through `IconMap.of(group, key, default)` so the Icon
   Styles card (Manager) changes it everywhere. New modes are added to `icons.py` in the integration as well as
   here, with the same group and key. Every `"mdi:..."` in the code must be a real Material Design icon (checked).
7. **Colours mean something**, as on the dashboard (green fine, amber and orange warning, red wrong). Use `Tone`;
   take a card's colour from the dashboard's own template where there is one. No stray hex colours in cards.
8. **Check both themes and a narrow phone** (light and dark, 360 dp wide): nothing clipped, nothing leaving a gap.
9. **Put logic in pure functions with a test** (`PagesLogicTest`, `CameraEventsDataTest`...), and keep the UI thin.
10. **Run the checks**: CI runs `testDebugUnitTest`, including `UiStandardsTest`. If a check fails, change the
    code, not the check. Change a check only on purpose, and say why in the commit message.

## What `UiStandardsTest` checks

- `tileRowSizes` never leaves a gap and never makes the last row the longest.
- No file but `UiKit.kt` types a 48, 56 or 64 dp height.
- Tile labels use `CentredText` (tile rows, alarm modes, scene tiles).
- The only raw Material buttons are the few allowed (login, the add-task button, the step buttons); a new one fails.
- Every `"mdi:..."` named in the code is a real icon, and every built-in icon is in the full set.

## Where things are

| Thing | File |
|---|---|
| Sizes, `CentredText`, row sharing | `ui/UiKit.kt` |
| Tiles, icon rows, `IconMap`, mode icons | `ui/ModeIcons.kt` |
| Full Material Design icon set (7,447 icons) | `assets/mdi-icons.json`, loaded by `MdiAll` |
| Card frames, alarm, thermostat, charger | `ui/Cards.kt` |
| Climate, fan, purifier, CO, blind | `ui/ClimateCards.kt` |
| To-do cards | `ui/TodoCards.kt` |
| Page layout from the dashboard | `ui/PageBody.kt`, `ui/DashboardPanels.kt` |

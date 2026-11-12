# Church Drive: style guide for every screen

One look across the phone app, the Admin app, the dashboards and the Manager website. **The apps are the reference.** When the website
draws something the apps already draw, it copies the app: the same shape, colours, icons and wording. The website may change the
*layout* around a component (columns, a wider screen, an administrator's point of view), never the component itself.

This page is written from the code. Where it and the code disagree, the code wins and this page gets fixed. Related guides:
`docs/CLIVE-STYLE.md` (Clive, the watch and the satellite), `docs/CLIVE-COLOURS.md` (palettes) and `companion/android/UI-STANDARDS.md`
(rules for the phone app's cards).

## 1. Before drawing anything on the website

1. Find the component in the app: Kotlin first (`companion/android/core/.../ui/EnergyKit.kt`, `UiKit.kt`, `StateColors.kt`), then the
   dashboard card in `src/*.js`.
2. Copy its shape, colour rules, icons, labels and which rows it has. Do not redraw it from memory or from a screenshot.
3. Change only the layout. If something else has to differ, say so in the commit and add it to section 7.
4. Check dark and light, a phone and a wide screen.

## 2. Colour

**Tones come from the palettes** (`design/clive/cards.json`, `entity_colours.types`). A palette has five colours: Colour, Dark, Accent,
Light, Deep. The app turns a palette into four roles (`StateColors.kt`, `colorsOf`):

| Role | Dark theme | Light theme |
|---|---|---|
| Card background (container) | the palette's **Dark** | a soft tint: white moved 38% towards **Light** |
| Text and icons on the card | white (the neutral text colour), never the palette colour | **Deep** |
| Strong colour (accent): selected buttons, pills, gauges, header icons | **Colour**, lifted towards **Light** only if it would not read on Dark (4.5:1, `readableOn`) | **Colour** |
| Text on the strong colour | **Dark** | **Dark** |

So a card is mostly white text on its Dark ground. The colour appears only in the strong parts: the header icon circle, the status pill,
a selected button, a gauge or bar. Never put the palette colour on body text.

**Meaning is fixed.** Green is fine, amber and orange are a warning, red is wrong or overdue. A card takes its colour from the
dashboard's own template (for example Device health takes `ok`, `warn` or `bad` from its worst device). No stray hex colours in a card.

**Level colours** (`EnergyKit.kt`, `levelFade`): a gauge's whole fill is one colour that follows the level: 0% red `#E5534B`, 25% amber
`#E8A33B`, 50% yellow `#E6C93A`, 75% green `#4CC38A`, 100% blue `#5B8CFF`, blended between. A battery (`batteryFade`) stays
green `#4CC38A` from 75% up. The figure beside a gauge takes the same colour. The track is the text colour at 25%.

**Status tags** (`StatusPill`): the strong colour as text on a faint wash of itself (18%), fully rounded. A car's tag is Charging
(the Cooling palette), Plugged in (Indigo `#9FA8DA`), Home (Green `#81C995`) or the place (Grey `#9AA0A6`).

## 3. Components

| Component | Rule | App source |
|---|---|---|
| Card | Rounded, palette Dark ground with the glow, white text. A header is an icon circle, the title and at most one status pill. | `GridCard`, `ToneCard` |
| Header icon | A circle of the strong colour at 18%, the icon in the strong colour at about 55% of the circle. | `ToneIcon` |
| Status pill | Strong colour text on 18% of itself, one per card header. | `StatusPill` |
| Button (`GridButton`) | **18 px corners.** Selected: the strong colour with the palette's Dark on it. Not selected: the text colour at 10%. Disabled: the label and icon at 40%. Label and icon centred. Never a switch for Smart charge: it is a button that fills when on. | `GridButton` |
| Choice tiles | One row of up to four, sharing the width. A row of choices (charger modes) is **icon above name**, 24 px icon, 12 px label, about 72 px tall. A button in a row is 48 px tall, 20 px icon, 13 px label, icon left of the name. All selected tiles use the **card's** colour, not a colour per choice. | `TileRow`, `Ui` |
| Level bar | 6 px (8 px on a single gauge), round ends, track at 25%, fill from `levelFade`/`batteryFade`. | `LevelBar` |
| Car row | Icon circle, name with its tag under it, the percentage large in the level colour, a bar, then one grey line (`16 mi electric · Fuel range 304 mi`). | `CarsCard` |
| Charger | Status title, session kWh, the rate now, the Smart charge text, then: car chooser buttons (only while plugged in), Start charge and Smart charge side by side, the four modes. | `ChargerCard` |

## 4. Type and size (website)

Every page uses one scale. These are the defaults; the example site has a slider for each. Fonts are Instrument Sans (and JetBrains Mono for
code and figures that need it).

| Role | Size | Weight |
|---|---|---|
| Page title | 24 px | 500 |
| Panel or section heading | 18 px | 500 |
| Card title | 16 px | 500 |
| Subtitle | 13 px | 400, text at 72% |
| Body | 14 px | 400 |
| Big number | 26 px | 500 |
| Card padding / corners | 14 px / 20 px | |

A card never invents its own sizes. Spacing between cards is the same gap everywhere (10 px).

## 5. Website page rules

- **No figure appears twice on a page.** A headline number lives in one place (a pill, a tile); the cards carry what the pills do not.
- **Icons live inside the card**, in its header, not above it in a separate heading row.
- **A card has the same rows whatever the data.** A missing figure reads *Data not available* in muted italics. A card must never change
  height because a value is missing.
- **Columns are the same height**; the last card in a column stretches.
- **Header lines** (the dashboard's `header_lines`) are one row of pills, each in its own accent colour.
- **Read-only preview first.** Controls shown from the house (charger modes, Smart charge) are read-only in the example site and say so.
- **Android phones** are sent straight to the Clive Admin app (or its Google Play listing). Other phones and computers stay on the site.
- **Panels** sit in columns of 3 on a wide screen, 2 on a tablet, 1 on a phone (as `AdaptiveLayout.kt`).

## 6. Sources

| Subject | File |
|---|---|
| Tone roles, `readableOn`, `toneColors` | `companion/android/core/.../ui/StateColors.kt` |
| Buttons, level bars, car and charger cards | `companion/android/core/.../ui/EnergyKit.kt` |
| Sizes, tile rows | `companion/android/core/.../ui/UiKit.kt` |
| Palettes | `design/clive/cards.json`, `docs/CLIVE-COLOURS.md` |
| Icons | `design/clive/house-icons.json`, `custom_components/church_drive/frontend/clive/icons.json` |
| Dashboard cards (what the apps mirror) | `src/*.js`, for example `src/energy-cards.js` |
| The Manager website | `web/manager/` |

## 7. Open decisions

- The Manager site's Home tiles use a white-tinted icon circle; the apps use the strong colour at 18%. Decide whether to match.
- The charger's Smart charge and mode buttons are not clickable in the example site; the live site will call the same services as the app.
- Light theme on the website: cards keep the dark ground in both themes today; the apps switch to a soft tint in light.

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
| Header icon | **Website:** a plain white icon beside the title, no circle (the apps use a tinted circle, `ToneIcon`; the site chose white on purpose, see section 7). | `ToneIcon` |
| Status pill | One per card header. **Website:** white text on a white wash; the apps use the strong colour on 18% of itself (`StatusPill`). | `StatusPill` |
| Button (`GridButton`) | Selected: filled, with the **palette's Dark on the icon and label** (not white). Not selected: the text colour at 10%. Disabled: icon and label at 40%. **Website:** 12 px corners and the charger's own layout (Smart charge as a switch row, car chooser as a picker); the apps use 18 px corners and plain buttons. | `GridButton` |
| Choice tiles | One row of up to four, sharing the width. A row of choices (charger modes) is **icon above name**, 24 px icon, 12 px label, about 72 px tall. A button in a row is 48 px tall, 20 px icon, 13 px label, icon left of the name. All selected tiles use the **card's** colour, not a colour per choice. | `TileRow`, `Ui` |
| Level bar | 6 px (8 px on a single gauge), round ends, track at 25%, **fill from `levelFade`/`batteryFade`** (the bar's colour changes with the level). | `LevelBar` |
| Car row | **Website:** name, an optional *Plugged in* tag, the percentage and range on the right, a thin bar in the level colour. The apps add an icon circle and a large percentage (`CarsCard`). | `CarsCard` |
| Charger | **Website:** session kWh, the action button, a Smart charge switch row, a *Car plugged in* picker (only while plugged in), then the four modes with an icon beside the name. The apps lay these out as buttons on a grid (`ChargerCard`). | `ChargerCard` |
| Device health card | A card per device in its level's tone (Green fine, Amber check soon, Red needs you), the icon, name, then area and any problem; a bar per battery or filter in the level's colour on a 22% track; needing attention first, then fine. The level word shows only when it is not fine. | `DeviceHealthScreen.kt` |
| Activity row | A 64 dp row (rounded left 20, right 32): the ground is what was done (its palette, Dark into Colour; lights show their own look), a plain icon, what happened on top and the time with its context below, and on the right a pill cut into the row (a sixth of the width, at least 150 dp) with who did it: their own colour and initial, or Clive's three colours and mark. The glow turns behind both. | `ActivityPage.kt` (app), the Activity page (website) |
| System rows | Icon, name, one grey line, an optional status pill (amber or red). A figure already in a header pill (internet, update count) is not repeated in a row. | `SystemCard` |

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
- **Navigation is a bottom dock**, as on the Home Assistant dashboard: a floating round-ended bar of icons, each in its page's colour, the current page filled with the palette's Dark on it, a small dot when something needs a look (Device health, Edits), and a round button to tuck it away. Pages with sections (Settings) get a second, smaller bar above it for their sections. The top bar keeps only the brand, the status capsule and the light/dark button.
- **Acrylic surfaces**: the header, the dock, tone cards, neutral cards and pills are translucent and blur what is behind them (about 18 px blur, 70% opaque), with a 1 px inner highlight. The page has a soft colour wash behind it (the Clive indigo, purple and green) so the blur has something to show. Acrylic is a setting (blur and opacity), and off means solid.
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

- **Chosen differences from the apps:** white icons without circles in card headers, white-wash pills, and the car and charger layout above. Everything else on those cards (the level colours, a selected button's dark icon) follows the app.
- The Manager site's Home tiles use a white-tinted icon circle; decide whether to match the card headers (plain white icon).
- The charger's Smart charge and mode buttons are not clickable in the example site; the live site will call the same services as the app.
- Light theme on the website: cards keep the dark ground in both themes today; the apps switch to a soft tint in light.

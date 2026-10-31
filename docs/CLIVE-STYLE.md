# Clive: UI and style guide

How the assistant looks and moves on every screen: the phone app, the watch app, and the round satellite. This is **extracted from the code**, which is the source of truth. Where this page and the code disagree, the code wins and this page should be fixed. Every value below names the file it came from.

"Clive" is the assistant's name only by default. The house sets it (Church Drive > Configure > Assistant name) and the apps show `AssistantBrand.shown`. Never hard-code the name on a screen; use the house's.

Paths are relative to `companion/android/`. `core` is shared by the phone, watch and to-do apps.

## 1. The idea

One calm presence, not a chat window. A dark glass ground, a soft glow that moves with what Clive is doing, a small white mark, and **one thing to read** at a time. The answer is spoken, so the screen carries only what a glance needs: a figure, a line, at most a few rows. Anything longer belongs on the phone.

Order of authority for look and feel: the user's own words, then this guide and the code, then the app-wide rules in `UI-STANDARDS.md` (which cover the phone's dashboard cards, not Clive).

## 2. Ground and ink

| Token | Value | Where |
|---|---|---|
| Ground | `#090A14` | `wear/.../CliveGlow.kt` (glow canvas), `AssistantActivity.kt` (root) |
| Vignette | radial, transparent at 45% of the radius to `#B305060E` at the edge | `CliveGlow.kt` |
| Primary text | white `#FFFFFF` | `CliveCards.kt` |
| Soft text | `#E8EAFF` (card lines), `#D5D8F6` (what was heard, notes), `#B9BDD9` (signed-out title) | `CliveCards.kt`, `AssistantActivity.kt` |
| Error text | `#FFB4AB` | `AssistantActivity.kt` |
| Prompt pill | fill `#59080914`, 1 dp border `#80DCE0FF`, text `#EEF0FF` | `AssistantActivity.kt` |
| Toggle on / off | `#4FD08A` / `#47FFFFFF` | `AssistantActivity.kt` |
| Text shadow | `#A6000000`, offset (0, 2), blur 12, on every line over the glow | `AssistantActivity.kt` (`SHADOW`) |

Type on the watch is the system font, bold for figures and titles, semibold for secondary lines. There is no custom typeface in the apps.

## 3. The glow

Three colour blobs turning round the middle of the glass, over the ground and under a vignette. Defined in `wear/.../CliveGlow.kt`; the phone's orb (`app/.../AssistScreen.kt`, `AssistOrb`) is the same idea as a floating cloud.

**Base palette** (also the phone's `ClassicPalette`): indigo `#5C6BC0`, purple `#AB47BC`, green `#4FD08A`.

**How it is drawn.** Each blob is a radial gradient from its colour (at the glow's alpha) to transparent, radius `0.62 × w` (w = the glass's shorter side), centre at distance `0.28 w` (blobs 1 and 2) or `0.30 w` (blob 3) from the middle:

| Blob | Colour | Angle (degrees) | Distance |
|---|---|---|---|
| 1 | a | `phase + 210` | 0.28 w |
| 2 | b | `-phase × 1.3 + 330` | 0.28 w |
| 3 | c | `phase × 0.8 + 90` | 0.30 w |

`phase` advances `360 / 16 × speed` degrees per second (a full turn in 16 s at speed 1).

**Intensity by state** (brightness, speed):

| State | Brightness | Speed | Extra |
|---|---|---|---|
| Idle, waiting | 0.72 | 0.6 | none |
| Listening | 1.0 | 1.6 | brightness `+ (level − 0.25) × 0.25` with the voice level |
| Thinking | 0.92 | 4.2 | none |
| Speaking | 0.95 | 2.4 | brightness `+ sin(t × 9) × 0.07` |
| Another face showing (Prompts, Settings) | 0.5 | unchanged | dimmed |

Colour changes take 1.4 s, brightness 0.5 s, speed 0.6 s.

**Palettes that follow the answer** (`GlowPalette`, `cardPalette`): a card's own `palette` (up to three `#rrggbb`) wins, otherwise by kind.

| Palette | a | b | c | Used for |
|---|---|---|---|---|
| Base | `#5C6BC0` | `#AB47BC` | `#4FD08A` | Everything else |
| Clear | `#1565C0` | `#42A5F5` | `#81D4FA` | Weather: sunny, partly cloudy |
| Rain | `#37474F` | `#5C7C99` | `#78909C` | Weather: anything else (cloudy, rainy, fog) |
| Night | `#0D1B4A` | `#283593` | `#5C6BC0` | Weather: night |
| Snow | `#90A4AE` | `#B3E5FC` | `#E1F5FE` | Weather: snowy |
| Storm | `#37474F` | `#6A1B9A` | `#5C6BC0` | Weather: lightning |
| Sport | `#6CABDD` | `#1C2C5B` | `#A5CDEB` | Scores and fixtures |

## 4. The mark

Clive's logo is **two overlapping rounded squares running bottom-left to top-right, with the overlap cut out** (even-odd fill), always white on the glow. `core/.../AssistantMark.kt`. Each square is 76 wide with corner radius 30, offset 11.3 along the diagonal, in a 200-unit space centred on (100, 100). The brand files are in `branding/` (`clive.svg`, `clive-logo.png`, wordmarks).

Only the mark moves; the button or glass behind it is still. Poses (`AssistantGlyphPose`) and their motion:

| Pose | Shape | Motion |
|---|---|---|
| Idle | The two squircles | Slide together and apart along the diagonal, 4 s cycle, up to 55% closer |
| Ready (phone, waiting for the mic) | A microphone: capsule `(80,40) 40×64 r20` over a bar `(66,130) 68×16 r8` | Capsule breathes 5%, 3 s |
| Listening | A stop button: one rounded square `(70,70) 60×60 r18` (the second shape shrinks away) | Pulses 7% on a 1.4 s cycle and swells up to 16% with the voice level |
| Thinking | The two squircles | Trade size: one grows 17.5% as the other shrinks, 1.2 s cycle |
| Speaking | Two equaliser bars `(64,52) 30×96 r15` and `(106,52) 30×96 r15` | Heights trade ±25% on a 0.7 s cycle |

Pose changes morph over 600 ms (ease in-out).

**On a card the mark turns into the card's symbol**: the mark fades and shrinks to 35% while rotating 25 degrees, and the symbol grows in from 35% rotating from -25 degrees to 0, over 450 ms. Symbols are Material Design icons (`mdi:`) by kind (`cardIcon` in `core/.../AssistData.kt`): weather by condition, `lightning-bolt` energy, `account-multiple` people, `check-circle` / `close-circle` done, `lightbulb`, `thermometer`, `car-electric`, `shield-check`, `door`, `radiator`, `cart`, `music-note`, `bell-ring` and so on. A competition logo or a service logo (Spotify, a car's make) replaces the icon when the app has it.

## 5. The watch layout (the reference for any round screen)

The watch is laid out in a **280-unit square** (`Ui(minOf(w, h) / 280)`), then scaled to the glass. Everything is placed by `x, y, width` in those units (`Modifier.at`). This is the round-screen layout to copy. `wear/.../AssistantActivity.kt`.

Three **faces** stacked vertically, moved with the crown or a swipe, with a **rail** of dots at the right (x = 280 − 20, vertical, active dot 4×12, others 4×4, white at 95% / 35%):

1. **Talk** (the main face)
2. **Prompts**: title "Ask {name}" (16 bold) at y 30, then up to five outlined pills at x 44, y `56 + 36 i`, 192×32, text 12. Pills are the phone's suggestions: "Who is home?", "Turn off all the lights", "Any doors open?", "Good night", and "What's the weather?".
3. **Settings**: title "{name} settings" at y 30, four rows at x 38, y `58 + 46 i`, 204×40: Spoken answers, Follow-up listening, Visual answers, Buzz on answer. Each is a pill with a switch (34×20) at the right.

**Talk face**, by state:

| State | Mark | Text |
|---|---|---|
| Resting | Size 104, centre (140, 124) | Name, 17 bold, y 34. "Tap to talk", 12 at 90%, y 206 |
| Listening | Size 58, centre (140, 84) | What is being heard live (14, up to 3 lines), or "Go ahead…" |
| Thinking | Size 58, centre (140, 84) | What was said (12, 2 lines), "Thinking…" at y 184 |
| Answer | Size 58, centre (140, 84) | The answer (14, up to the fit box at x 38, y 126, 204×100) |
| Answer with a card | Mark becomes the symbol (58) | Fit box at y 118, 204×116: what was heard (10), the card, the answer (12) |
| Error | Size 58 | `#FFB4AB`, 14, at y 134 |

Text that does not fit is **made smaller, never scrolled** (`FitBox`). The mark is never clipped (it drifts beyond its own square).

## 6. Cards

A card is a `kind` plus fields, sent by Home Assistant (`script.clive_show_card`, event `church_drive_assistant_show`; see `ha/clive.md`). Kinds with layouts on the watch: weather, score, scores, fixture, fixtures, music, and the shared layout for energy, secure, doors, tasks, devices, lights, heat, shop, event, reminder, media and answer. `people`, `done`, `car`, `climate`, `gregg` and `air` are drawn on the phone only.

**Watch card blocks** (`wear/.../CliveCards.kt`), sizes in watch units:

| Kind | Layout |
|---|---|
| weather | Place (11, 85%). Temperature 40 bold with a 20 semibold degree sign raised at the top. Description 12. "H 14° · L 8°" (11). Then up to 4 hours: the hour (10) over the temperature (12 semibold), 10 apart |
| score | Home badge 40, "5 – 1" at 34 bold, away badge 40. Status (13 semibold). Scorers (11, 2 lines) |
| scores | Up to 6 rows of badge 20, score 18 bold, badge 20 |
| fixtures | Up to 6 rows of badge 20, "v", badge 20, day and time (12) |
| fixture | Badges 26 with "v", title 14 semibold, day 26 bold, time and "in" 13, where and competition 11 |
| music | Service logo 22, title 16 semibold (2 lines), artist 12, "Playing on {device}" 11 |
| shared | `big` 30 bold, `title` 15 semibold (2 lines), `sub` 12 soft (2 lines), then up to 3 rows as "name · value" at 11 |

Club badges are found from the team name (`TeamBadges.lookup`) when the card gives no address; until a picture loads, three white letters stand in.

**Phone card body** (`app/.../AssistCards.kt`, `ListCardBody`): `big` (display small, bold), `title`, `sub`, an optional **pill**, then **rows** (marker, name and detail, value, pill), a percent bar, and a footer. Pills are the tone's accent text on the accent at 18% alpha, fully rounded, 10 dp by 3 dp. Row markers: `ok` green tick, `warn` amber alert, `todo` empty box, `done` ticked box, `dot` coloured dot. At most `CARD_MAX_ROWS` rows with a "show more".

**Tones** (`core/.../StateColors.kt`): Green, Blue, Red, Purple, Amber, Orange, Teal, Indigo, Grey, Coral. Dark variants (the glass is always dark):

| Tone | Container | Accent |
|---|---|---|
| Green | `#1B3A28` | `#81C995` |
| Blue | `#1C3358` | `#8AB4F8` |
| Red | `#4D1F1C` | `#F28B82` |
| Purple | `#3A2552` | `#C58AF9` |
| Amber | `#4A3A12` | `#FDD663` |
| Orange | `#4A2A14` | `#FFAB70` |
| Teal | `#0F3A40` | `#4DD0E1` |
| Indigo | `#262B4A` | `#9FA8DA` |
| Grey | `#303134` | `#9AA0A6` |
| Coral | `#4D2620` | `#FF9C8A` |

Meaning is fixed: green fine, amber and orange warning, red wrong or overdue. Never invent a colour for a card; pick a tone, or send a `palette` for the glow.

## 7. Voice and words

The screen never repeats the spoken answer word for word. Clive answers in two short sentences (an answer, then an offer), confirms commands in one short sentence, and spells **Diane** as "Die Ann" in speech only; on screen it is always **Diane**. Numbers are digits with units (`17°C`, `12:30pm`). The full rules are in `ha/clive-prompt.md`. A card must make sense with the sound off, and the spoken answer must make sense with no screen.

## 8. The round satellite (proposal, not built)

The 3.4" 800×800 satellite (`satellite/`) should be **the watch's Talk face at 800 px**, not a new design. Scale factor `800 / 280 = 2.857` from every watch unit above (the 58-unit mark becomes about 166 px, the 104-unit mark about 297 px, 14-unit text about 40 px).

What carries over unchanged: ground, glow and its palettes, the mark and its poses, text colours and shadow, the three faces and rail, the card blocks, "made smaller, never scrolled".

What is different because it is a fixed device, not a wrist:
- **Idle** can show the time and date under the mark as well as "Tap to talk", since it is never switched off. (The watch dims instead.)
- **Cards time out** after 25 seconds (`card_seconds` in `clive-round.yaml`), then return to the resting face.
- **Faces move by swipe or tap** on the rail dots (there is no crown).
- It has **no phone beside it**, so the Prompts and Settings faces must work on their own. Settings would need a brightness control and volume, which the watch does not have.

**Decisions so far** (mockups in `satellite/design/`):
- **Clive's dock.** The bottom of the glass (watch units y 208 to 280, a circle segment) is Clive's dock: a black background at about 42% opacity with a hairline on top, and the mark centred in it (about y 245, size 32). It is on **every screen except settings** (quick settings, the settings menu and pairing). The mark does not move to the middle any more. It stays in the dock and takes its poses there: stop button while listening, trading squircles while thinking, equaliser bars while speaking. Tapping the dock talks to him. Content sits above it (y 44 to 204).
- **Icons are plain white, always.** No coloured icons and no coloured circles on the glass or in the companion app (the watch draws every Clive icon with `Color.White`). Colour comes only from the **glow palette** (a card's own `palette`, or the weather and sport palettes), from text tones (the error text `#FFB4AB`), and from the green switch. A card's icon is shown white above its figures. State on controls is shown by fill and outline, not by colour: an outlined circle, brighter fill and full-strength icon when on, dim icon when off. Outlined pills as on the watch; the one that matters most is the brighter one.
- **Idle** is a black ground with the dock, and no glow. Layout **E**: time, then a date line carrying the date and the outside and inside temperatures ("Sat 31 Oct · 12° out · 19° in"), then three configurable glances (who is home, electricity, car by default). The forecast icon is one of the glance options, not fixed. The backlight dims on a schedule and night uses the minimal layout H. See `idle-layouts.html`.
- **Talking** states come first in `flows.html`: listening, thinking, answering, then the cards (weather, score, energy, who is home). **Actions** answer with the `done` content (name, detail, optional bar) but drawn in the Clive style: a white icon, a white bar, and the colour carried by the glow (the light's own colour for a light, red for the alarm, amber when he asks first or refuses, red for a failure). Anything costly asks first with Yes and No pills. Things he may never do (disarm the alarm, sirens, TVs) are refused plainly with where to do it instead.
- **Quick settings**: drag down from the top edge. Volume and brightness sliders (white fill), six switches and a Settings button. The settings menu uses the watch's prompt pills.
- **The companion app** is drawn in the same look: a glow header with the white mark, white icons, outlined panels and chips, green switches.
- **Pairing and setup** are in `docs/SATELLITE-PAIRING.md`.

Open questions for building it in LVGL (ESPHome):
- The glow is three radial gradients and a vignette. LVGL's gradient support varies by version, so the options are pre-rendered glow images rotated slowly, or a small canvas drawn per frame. Needs testing on the board for frame rate.
- The mark is a two-shape even-odd path morphing between poses. In LVGL this is likely a set of pre-rendered frames or an animated canvas.
- Card icons are Material Design Icons. ESPHome can include the MDI font, or the glyphs can be converted from `assets/mdi-icons.json`.
- Club badges come from a lookup by team name in the apps; the satellite has no equivalent yet.

## Sources

| Subject | File |
|---|---|
| Glow, palettes, badge loader | `wear/src/main/java/com/churchdrive/app/wear/CliveGlow.kt` |
| Watch layout, faces, rail | `wear/src/main/java/com/churchdrive/app/wear/AssistantActivity.kt` |
| Watch card blocks, card palette and symbol | `wear/src/main/java/com/churchdrive/app/wear/CliveCards.kt` |
| The mark and its poses | `core/src/main/java/com/churchdrive/app/ui/AssistantMark.kt` |
| Card icons, kinds, suggestions, states | `core/src/main/java/com/churchdrive/app/ui/AssistData.kt` |
| Tones | `core/src/main/java/com/churchdrive/app/ui/StateColors.kt` |
| Assistant name from the house | `core/src/main/java/com/churchdrive/app/ui/AssistantBrand.kt` |
| Phone orb and screen | `app/src/main/java/com/churchdrive/app/ui/AssistScreen.kt` |
| Phone card bodies | `app/src/main/java/com/churchdrive/app/ui/AssistCards.kt` |
| Brand files | `branding/` |
| Voice and wording | `ha/clive-prompt.md` |

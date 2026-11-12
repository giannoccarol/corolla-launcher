# Design

The look is settled in a design mockup (private):
<https://claude.ai/artifact/XordcZoRD1Qzed4ogEWiKA>. This file records the decisions so the
code can be changed without reopening it.

## Principles

1. **Uniform theme.** Dark means everything is black; light means everything is white. The
   dock, cards, dialogs and system bars all follow the same mode.
2. **Dark is the default.** It is the theme the owner chose for the mockup.
3. **No purple**, anywhere.
4. **Glanceable.** One big clock, one big play button, a few large targets. Nothing on the home
   screen needs reading while moving.
5. **One icon set.** App icons are themed by default (one glyph per app on a tile in the app's
   own hue), so apps from different designers do not clash. Monochrome glyphs, original icons
   and icon packs are a setting.

## Canvas

Designed on **1024 x 600 dp** (a 7" head unit at 160 dpi, where 1 dp = 1 px). Side columns
have fixed proportions and the centre takes the rest, so other landscape sizes stretch rather
than break. The mockup lives in the design canvas linked above ("Dashboard" artboards).

```
┌──────────────────────────────────────────────────────────────────────────┐
│ (L) Lorenzo │ [Home] Navigation  Apps        gps bt net │ date  TIME     │  50 dp
├──────────────────────┬───────────────────────┬───────────────────────────┤
│ [ Where to?        ] │ [chips]               │ Corolla          Driving  │
│ (⌂) Home           › │ cover  title / artist │   [ car photo ]           │
│ (▣) Work           › │ ───────── progress    │ 12.4 km  18 min  41 km/h  │  270 dp
│ ──────────────────── │     ⏮    ▶    ⏭      ├───────────────────────────┤
│ RECENT               ├───────────────────────┤ [theme] [wi-fi] [bt]      │
│ ◷ place  ◷ place     │ Most used        All ›│ [display][audio][more]    │
│ [ 43 km/h   N      ] │ app  app  app  app    │                           │  148 dp
├──────────────────────┴───────────────────────┴───────────────────────────┤
│ ◁ ○ □            app app [all apps] app app              ‹ volume ›      │  74 dp
└──────────────────────────────────────────────────────────────────────────┘
   ~372 dp                 ~364 dp                    ~252 dp     (12 dp gaps)
```

The launcher is **full screen**: the system status and navigation bars are hidden while it is
in front, and replaced by the top and bottom bars above.

- **Top bar**: the driver's initial and name (tap to edit; "Add your name" when unset), tabs,
  then the status icons. An icon dims to 28 % when its radio is off instead of disappearing,
  so the bar never shifts.
- **Bottom bar**: system keys drawn as outlines so they read as controls and not as apps; four
  docked apps that only change when the driver pins or unpins (stable for muscle memory),
  around the All apps button; volume.
- **Most used** is the moving part: by launch count, never repeating a docked app.
- Values not available yet show an em dash, never a fake zero.

## Colors

Defined in `res/values/colors.xml` and `res/values-night/colors.xml` under the same names.

| Name | Light | Dark | Used for |
| --- | --- | --- | --- |
| `bg` | `#FFFFFF` | `#000000` | screen background, dock, system bars |
| `surface` | `#F1F3F5` | `#131518` | cards, fields, dialogs, settings rows |
| `surface_2` | `#E3E6EA` | `#22262C` | pills, round buttons, unselected chips |
| `text_primary` | `#15171A` | `#F4F5F7` | main text and icons |
| `text_secondary` | `#59606B` | `#A7ADB7` | labels, hints, secondary icons |
| `line` | 10 % black | 9 % white | dock divider, field outline |
| `track` | `#D5D9DF` | `#30353D` | progress bar track |
| `tile_on_bg` / `tile_on_text` | `#D4DFF5` / `#1A4BBF` | `#1D2E52` / `#A9C5FF` | quick settings tile that is on |
| `chip_on_bg` / `chip_on_text` | `#15171A` / `#FFFFFF` | `#F4F5F7` / `#000000` | selected source chip |
| `accent` | `#3B76F6` | same | play button, progress, dialog buttons |
| `on_accent` | `#FFFFFF` | same | icon on the accent color |

Secondary text keeps at least 4.5:1 contrast on every surface it sits on. Check that again when
changing any of these values.

## Type

System Roboto. Clock in `sans-serif-light`, titles and buttons in `sans-serif-medium`.

| Element | Size |
| --- | --- |
| Speed | 44sp light |
| Time in the top bar | 19sp |
| Track title | 21sp |
| Screen title | 28sp |
| Body, artist, fields | 15-18sp |
| Section labels | 13sp, uppercase, tracked |

## Shapes and sizes

- Cards 24dp radius; tiles 16dp; speed panel 18dp; pills fully rounded; settings rows 18dp.
- Play button 66dp, previous/next 54dp, system keys 54x56dp, docked apps 52dp, tabs 40dp tall
  inside a 50dp bar (the tab row is information first; the whole bar is reachable but small).
- Bottom bar app icons 52dp with 12dp gaps; Most used 52dp; app grid icons 72dp.
- Themed icon tiles are rounded squares (24 % radius) with a hairline (`card_border`); the
  glyph fills 58 % of the tile. With original icons, adaptive icons are redrawn in the same
  shape by `IconShaper`, whatever the device's icon mask.

## Surfaces

- **Cards, tiles, rows, pills**: the fill colour, then a top-down sheen (`card_highlight`, 5 %
  white in dark, 60 % white in light) and a 1dp hairline edge (`card_border`). On a pure black
  screen that separates surfaces without shadows, which would be invisible there anyway.
- **Panels**: `sheet_surface` is the card colour at 92-95 % opacity, so the dimmed home shows
  faintly through, with a brighter hairline (`sheet_border`).
- **Media card**: the card plus a diagonal wash of the album cover's colour (34 % in dark, 20 %
  in light), picked by `ui/AlbumColor` (most saturated, mid-bright hue, then calmed).
- **Numbers that change in place** (time, speed, volume, trip, track times, slider values)
  use tabular figures (`fontFeatureSettings="tnum"`).
- **Slider knob**: white with an accent ring in both themes.

## Motion

Present, refined, never in the way. Curves are Material's: *emphasized decelerate*
(0.05, 0.7, 0.1, 1) for things arriving, *standard* (0.2, 0, 0, 1) for state changes,
`linear_out_slow_in` / `fast_out_linear_in` for windows.

| Where | What | Duration |
| --- | --- | --- |
| Anything pressed | sinks to 95 % and 82 % opacity, settles back | 90 / 260 ms |
| Home screen appearing | the three columns and the bottom bar rise 16dp and fade in, staggered 60 ms | 380 ms |
| Dock and Most used icons | grow from 82 % and fade in, staggered 35 ms | 300 ms |
| Opening All apps or Settings | rises 4 % and fades in; closing reverses it | 220 / 180 ms |
| Panels | grow from 94 % and fade in; leave to 97 % faster | 240 / 160 ms |
| Track change | title and cover rise 6dp and fade in; the card's album wash crossfades | 260 / 320 ms |
| Play / pause | the icon morphs (two halves of a path) | 260 ms |
| Progress | glides between the once-a-second updates | system |
| Quick settings tile on/off | fill, icon and label crossfade | 320 ms |
| Status icons | fade between on (100 %) and off (28 %) | 260 ms |
| Theme switch | a snapshot of the old screen fades out over the new | 420 ms |
| Assistant | halo follows the voice while listening, breathes slowly while thinking, reply fades in | live / 900 ms |

Everything lives in `ui/Motion.kt` (and `ui/ThemeTransition.kt`) and is skipped when
*Settings → Animations* or the system animator scale is off.

## Sound

None. `android:soundEffectsEnabled` is false in the app theme and the dialog theme, which
makes it the default for every view. The head unit's own touch beep, if it has one, is a
device setting outside the app.

## Icons

Vector drawables in `res/drawable/ic_*.xml`, 24-unit viewport, solid white shapes, tinted in
layouts with `app:tint`. Filled style, rounded joins. No emoji, no bitmap icons.

### App icons

The default style mirrors Android 13's themed icons, coloured with each app's own hue.
`IconTint` takes the hue from a 12x12 thumbnail of the app's icon (the same picker as the
album tint) and makes a tonal pair from it:

| Theme | Tile (HSV) | Glyph (HSV) |
| --- | --- | --- |
| Light | s 0.17, v 0.97 | s 0.90, v 0.66 (0.48 for oranges to cyans, 0.40 for yellows) |
| Dark | s 0.55, v 0.26 (0.22 for oranges to cyans) | s 0.40 (0.32), v 1.0 |

Every hue keeps a glyph-to-tile contrast of about 4.5:1 or more (a unit test checks all of
them). Violet hues become blue and magenta ones pink, here and in the album tint: no purple.

Apps whose icon has no colour, and the monochrome style, use named colours:

| Name | Light | Dark | Use |
| --- | --- | --- | --- |
| `icon_tile_neutral` | `#E3E6EA` | `#22262C` | tile of a grey app |
| `icon_glyph_neutral` | `#3A4049` | `#E3E6EA` | glyph of a grey app |
| `icon_tile` | `#DCE5F7` | `#1A2436` | tile, monochrome style |
| `icon_glyph` | `#1A4BBF` | `#A9C5FF` | glyph, monochrome style |

Glyphs come, in order, from the app's own monochrome layer (Android 13+), the bundled set
(`glyph_*.xml`: brand logos from Simple Icons, symbols from Material Icons, white, generated by
`scripts/import_glyphs.py`), the silhouette of the app's adaptive foreground when it looks like
a logo, and finally the app's initial. Glyphs are stored as alpha masks and coloured when they
are shown, so they follow a theme change at once.

## Panels

Rounded card (24dp) over the home, 62 % of the screen width up to 640dp, at most 74 % of the
height before it scrolls. Rows are at least 64dp, switch rows 76dp, buttons 60dp. Switches are
68x38dp with a 30dp knob; sliders have a 16dp track, a 34dp knob and 56dp minus and plus
buttons, because a finger in a moving car cannot hit a thin line.

## Speed gauge

`ui/SpeedGauge` draws the speed panel on a canvas, in the spirit of a late-90s Japanese
roadster's digital dash (an inspiration, not a copy of any maker's artwork):

- **Bar graph** across the panel: 36 segments, 5 km/h each up to 180, rising in height from
  left to right. Lit segments are the accent colour, amber (`gauge_warn`) from 130 km/h, the
  Italian motorway limit; unlit ones stay faintly visible (`gauge_ghost`). The bar glides to a
  new speed (450 ms) and the segment being reached fades in.
- **Digits**: three italic seven-segment digits, 52dp tall, unlit segments ghosted like an LCD.
  They change at once, as a real LCD does. No speed shows three dashes.
- Scale 0 · 60 · 120 · 180 under the bar; heading at the bottom right.

## Car photo

`art/corolla-e120.png` is the source (transparent background).
`scripts/make_car_image.py` builds the app's picture from it: it finds the three visible tyre
contact points, infers the hidden fourth, and lays a ground shadow under the car in three
layers (wide soft ambient shadow over the footprint, darker core under the body, tight contact
patches under the tyres), so the shadow follows the 3/4 perspective. It then writes one WebP
per density in `res/drawable-{mdpi,hdpi,xhdpi,xxhdpi}/car_corolla.webp`, sized for the 216 dp
it is drawn at, with a light unsharp mask (letting Android shrink one large bitmap made it look
soft). Run it again whenever the source photo changes:

```bash
python3 scripts/make_car_image.py --preview /tmp/car.png
```

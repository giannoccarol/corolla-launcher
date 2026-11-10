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
5. **Real app icons.** The dock and the app grid show each app's own icon, read from the device.

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
- Adaptive app icons are redrawn as rounded squares (24 % radius) by `IconShaper`, so every
  icon has the same silhouette regardless of the device's icon mask.

## Motion

Present but quiet: it should make the screen feel responsive, never ask for attention.

| Where | What | Duration |
| --- | --- | --- |
| Any button or icon, while pressed | shrinks to 93 %, springs back on release | 80 / 180 ms |
| Home screen appearing | left pane, right pane and dock rise 14dp and fade in, staggered | 260 ms |
| Dock icons, when the set changes | pop in one after the other | 200 ms |
| Opening All apps or Settings | the screen rises 4 % and fades in; closing reverses it | 220 / 180 ms |
| App grid, first show | tiles fade and scale in, staggered | 180 ms |
| Track change | title, artist and cover fade in | 200 ms |
| Play/pause change | the play button bounces once | 180 ms |

Rules: only alpha, translation and scale; nothing loops; nothing that stays on screen moves
(the progress bar steps once a second on purpose). *Settings → Animations* and the system
"animator duration scale" both switch everything off. All of it lives in `ui/Motion.kt`.

## Sound

None. `android:soundEffectsEnabled` is false in the app theme and the dialog theme, which
makes it the default for every view. The head unit's own touch beep, if it has one, is a
device setting outside the app.

## Icons

Vector drawables in `res/drawable/ic_*.xml`, 24-unit viewport, solid white shapes, tinted in
layouts with `app:tint`. Filled style, rounded joins. No emoji, no bitmap icons.

## Car photo

`art/corolla-e120.png` is the source (transparent background). The app ships it pre-scaled
per density in `res/drawable-{mdpi,hdpi,xhdpi,xxhdpi}/car_corolla.webp`, sized for the 216 dp
it is drawn at, with a light unsharp mask. Letting Android shrink one large bitmap made it look
soft, because it scales without mipmaps. Regenerate all four from the source if it changes.

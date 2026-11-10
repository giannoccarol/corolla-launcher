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

Designed on **1024 x 600 dp** (a 7" head unit at 160 dpi, where 1 dp = 1 px). The layout uses
weights, so other landscape sizes stretch rather than break. Screens shorter than 500 dp of
usable height get a smaller clock and cover (`values/dimens.xml` vs `values-h500dp/`).

```
┌──────────────────────────────────────────────────────────────────────────┐
│ time  date                                            gps  bt  network    │  36 dp
├───────────────────────────────┬──────────────────────────────────────────┤
│ CLOCK                         │                        [settings] [theme] │
│                               │ ┌──────────────────────────────────────┐ │
│                               │ │ [source chips]                       │ │
│                               │ │ cover   title / artist / progress    │ │
│ NAVIGATION                    │ │                                      │ │
│ [ Where to?                 ] │ │        ⏮      ▶      ⏭              │ │
│ [ Home ]        [ Work ]      │ └──────────────────────────────────────┘ │
├───────────────────────────────┴──────────────────────────────────────────┤
│ ◁  ○  □        [all apps] app app app app app app          ‹ volume ›     │  88 dp
└──────────────────────────────────────────────────────────────────────────┘
        40 %                                   60 %
```

The launcher is **full screen**: the system status and navigation bars are hidden while it is
in front, and replaced by the two strips above.

- **Status bar** (top, every screen): small time and date on the left; GPS, Bluetooth and
  network icons on the right. An icon dims to 28 % when its radio is off instead of
  disappearing, so the bar never shifts. It is information only, with nothing to tap.
- **System keys** (dock, bottom left, home screen only): Back, Home, Recent apps, drawn as
  outlines so they read as system controls and not as apps.

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
| `chip_on_bg` / `chip_on_text` | `#15171A` / `#FFFFFF` | `#F4F5F7` / `#000000` | selected source chip |
| `accent` | `#3B76F6` | same | play button, progress, dialog buttons |
| `on_accent` | `#FFFFFF` | same | icon on the accent color |

Secondary text keeps at least 4.5:1 contrast on every surface it sits on. Check that again when
changing any of these values.

## Type

System Roboto. Clock in `sans-serif-light`, titles and buttons in `sans-serif-medium`.

| Element | Size |
| --- | --- |
| Clock | 108sp (84sp on short screens) |
| Track title | 26sp |
| Screen title | 28sp |
| Body, artist, fields | 15-18sp |
| Section labels | 13sp, uppercase, tracked |

## Shapes and sizes

- Cards 28dp radius; pills fully rounded; settings rows 18dp.
- Play button 76dp, previous/next 64dp, top-right buttons 48dp.
- Dock icons 56dp with 18dp gaps; app grid icons 72dp.
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

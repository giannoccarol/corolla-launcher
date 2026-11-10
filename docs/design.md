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
┌───────────────────────────────┬──────────────────────────────────────────┐
│ date                          │                        [settings] [theme] │
│ CLOCK                         │ ┌──────────────────────────────────────┐ │
│                               │ │ [source chips]                       │ │
│                               │ │ cover   title / artist / progress    │ │
│ NAVIGATION                    │ │                                      │ │
│ [ Where to?                 ] │ │        ⏮      ▶      ⏭              │ │
│ [ Home ]        [ Work ]      │ └──────────────────────────────────────┘ │
├───────────────────────────────┴──────────────────────────────────────────┤
│ [all apps]            app app app app app app              ‹ volume ›     │
└──────────────────────────────────────────────────────────────────────────┘
        40 %                                   60 %              dock: 88 dp
```

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

## Icons

Vector drawables in `res/drawable/ic_*.xml`, 24-unit viewport, solid white shapes, tinted in
layouts with `app:tint`. Filled style, rounded joins. No emoji, no bitmap icons.

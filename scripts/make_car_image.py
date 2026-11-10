#!/usr/bin/env python3
"""Builds the car card picture from art/corolla-e120.png: adds a ground shadow and writes one
pre-scaled, lightly sharpened WebP per screen density.

The shadow is computed from the photo's own silhouette, so it follows the 3/4 perspective:

  1. find where each visible wheel touches the ground (the lowest opaque pixels);
  2. infer the hidden fourth wheel, which makes the car's footprint on the ground;
  3. lay down three layers: a wide soft ambient shadow over the footprint, a darker core under
     the body, and tight contact shadows under the tyres;
  4. put the car on top.

Run from the repository root:  python3 scripts/make_car_image.py   (needs Pillow)
Pass --preview PATH to also write the result over the dark and light card colours.
"""

from __future__ import annotations

import argparse
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "art" / "corolla-e120.png"
RES = ROOT / "app" / "src" / "main" / "res"
# Drawn about 216dp wide in the car card.
DENSITIES = {"mdpi": 216, "hdpi": 324, "xhdpi": 432, "xxhdpi": 648}
DARK_CARD = (19, 21, 24, 255)
LIGHT_CARD = (241, 243, 245, 255)


def bottoms(alpha: Image.Image) -> list[int | None]:
    """Lowest opaque row in each column, or None for an empty column."""
    width, height = alpha.size
    pixels = alpha.load()
    result: list[int | None] = []
    for x in range(width):
        lowest = None
        for y in range(height - 1, -1, -1):
            if pixels[x, y] > 128:
                lowest = y
                break
        result.append(lowest)
    return result


def wheel_contacts(alpha: Image.Image) -> list[tuple[int, int]]:
    """The visible tyres' contact points: the deepest point in each third of the width."""
    width, _ = alpha.size
    lows = bottoms(alpha)
    contacts = []
    # The left, middle and right thirds hold the front-left, front-right and rear-right tyres
    # of this 3/4 front view.
    for start, end in ((0, width // 3), (width // 3, 2 * width // 3), (2 * width // 3, width)):
        candidates = [(lows[x], x) for x in range(start, end) if lows[x] is not None]
        y, x = max(candidates)
        # Centre of the tyre: columns within a few pixels of the lowest point.
        near = [cx for cx in range(start, end) if lows[cx] is not None and lows[cx] >= y - 6]
        contacts.append(((min(near) + max(near)) // 2, y))
    return contacts


def scaled(points: list[tuple[float, float]], factor: float, lift: float = 0) -> list[tuple[float, float]]:
    cx = sum(p[0] for p in points) / len(points)
    cy = sum(p[1] for p in points) / len(points)
    return [(cx + (x - cx) * factor, cy + (y - cy) * factor - lift) for x, y in points]


def shadow_for(car: Image.Image) -> Image.Image:
    width, height = car.size
    front_left, front_right, rear_right = wheel_contacts(car.getchannel("A"))
    # Parallelogram: the hidden rear-left tyre sits where front-left + (rear-right - front-right).
    rear_left = (front_left[0] + rear_right[0] - front_right[0], front_left[1] + rear_right[1] - front_right[1])
    footprint = [front_left, front_right, rear_right, rear_left]
    unit = width / 1000  # Sizes below are tuned on a 1000px-wide car.

    def layer(draw_shape, blur: float) -> Image.Image:
        mask = Image.new("L", car.size, 0)
        draw_shape(ImageDraw.Draw(mask))
        return mask.filter(ImageFilter.GaussianBlur(blur * unit))

    ambient = layer(lambda d: d.polygon(scaled(footprint, 1.14), fill=135), 28)
    core = layer(lambda d: d.polygon(scaled(footprint, 0.9, lift=5 * unit), fill=200), 13)

    def tyres(d: ImageDraw.ImageDraw) -> None:
        for x, y in (front_left, front_right, rear_right):
            # The nearer the tyre (lower in the picture), the wider its patch looks.
            depth = (y - rear_right[1]) / max(1, front_right[1] - rear_right[1])
            half_w = (38 + 24 * depth) * unit
            half_h = (8 + 5 * depth) * unit
            d.ellipse((x - half_w, y - half_h, x + half_w, y + half_h * 0.9), fill=250)

    contact = layer(tyres, 5)
    alpha = ImageChops.lighter(ImageChops.lighter(ambient, core), contact)
    shadow = Image.new("RGBA", car.size, (0, 0, 0, 0))
    shadow.putalpha(alpha)
    return shadow


def build() -> Image.Image:
    car = Image.open(SOURCE).convert("RGBA")
    car = car.crop(car.getchannel("A").getbbox())
    # Room below and on the sides for the shadow to fade out.
    pad_x, pad_bottom = int(car.width * 0.03), int(car.height * 0.06)
    canvas = Image.new("RGBA", (car.width + 2 * pad_x, car.height + pad_bottom), (0, 0, 0, 0))
    canvas.alpha_composite(car, (pad_x, 0))
    shadow = shadow_for(canvas)
    result = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    result.alpha_composite(shadow)
    result.alpha_composite(canvas)
    return result.crop(result.getchannel("A").getbbox())


def export(picture: Image.Image) -> None:
    for folder, width in DENSITIES.items():
        height = round(picture.height * width / picture.width)
        resized = picture.resize((width, height), Image.LANCZOS)
        rgb = resized.convert("RGB").filter(ImageFilter.UnsharpMask(radius=0.8, percent=60, threshold=2))
        rgb.putalpha(resized.getchannel("A"))
        target = RES / f"drawable-{folder}" / "car_corolla.webp"
        target.parent.mkdir(parents=True, exist_ok=True)
        rgb.save(target, "WEBP", quality=95, method=6)
        print(f"{target.relative_to(ROOT)}: {width}x{height}, {target.stat().st_size // 1024} KB")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--preview", type=Path, help="also write a dark/light preview PNG here")
    args = parser.parse_args()
    picture = build()
    export(picture)
    if args.preview:
        small = picture.resize((640, round(picture.height * 640 / picture.width)), Image.LANCZOS)
        sheet = Image.new("RGBA", (small.width * 2 + 40, small.height + 40), (0, 0, 0, 255))
        for i, colour in enumerate((DARK_CARD, LIGHT_CARD)):
            card = Image.new("RGBA", (small.width + 20, small.height + 40), colour)
            card.alpha_composite(small, (10, 20))
            sheet.paste(card, (i * (small.width + 20), 0))
        sheet.convert("RGB").save(args.preview)
        print(f"preview: {args.preview}")


if __name__ == "__main__":
    main()

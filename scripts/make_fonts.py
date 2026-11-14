#!/usr/bin/env python3
"""Writes the bundled typefaces (res/font/*.ttf) from Google Fonts' variable font files.

Manrope and Nunito ship in google/fonts as single variable fonts, which Android only reads as
variable from API 26. The launcher runs on API 24, so this script cuts static instances at the
three weights the app uses (Light 300, Regular 400, SemiBold 600) and keeps only the Latin
characters the five interface languages need, which makes each file a fraction of the size.

Both fonts are under the SIL Open Font License 1.1 (licenses/OFL-*.txt). Get them and run from
the repository root (needs fontTools: pip install fonttools):

  curl -L -o Manrope.ttf "https://github.com/google/fonts/raw/main/ofl/manrope/Manrope%5Bwght%5D.ttf"
  curl -L -o Nunito.ttf "https://github.com/google/fonts/raw/main/ofl/nunito/Nunito%5Bwght%5D.ttf"
  python3 scripts/make_fonts.py --manrope Manrope.ttf --nunito Nunito.ttf
"""

from __future__ import annotations

import argparse
from pathlib import Path

from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

ROOT = Path(__file__).resolve().parent.parent
FONT_DIR = ROOT / "app" / "src" / "main" / "res" / "font"
WEIGHTS = {"light": 300, "regular": 400, "semibold": 600}

# Basic Latin, Latin-1 (à é ö ñ ç ß...), Latin Extended-A (œ, Œ...), general punctuation
# (– — ‘ ’ “ ” • …), the euro sign, arrows and the degree/multiplication signs.
UNICODES = (
    list(range(0x20, 0x7F))
    + list(range(0xA0, 0x180))
    + list(range(0x2010, 0x2028))
    + [0x2030, 0x2039, 0x203A, 0x20AC, 0x2122, 0x2190, 0x2191, 0x2192, 0x2193, 0x2212]
)


def static_instance(source: Path, weight: int, target: Path) -> None:
    font = TTFont(source)
    instance = instancer.instantiateVariableFont(font, {"wght": weight}, updateFontNames=True)
    options = subset.Options()
    options.layout_features = ["kern", "liga", "tnum", "lnum", "case"]
    options.name_IDs = ["*"]
    options.notdef_outline = True
    subsetter = subset.Subsetter(options)
    subsetter.populate(unicodes=UNICODES)
    subsetter.subset(instance)
    instance.save(target)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--manrope", type=Path, required=True, help="Manrope[wght].ttf")
    parser.add_argument("--nunito", type=Path, required=True, help="Nunito[wght].ttf")
    args = parser.parse_args()
    FONT_DIR.mkdir(parents=True, exist_ok=True)
    for family, source in (("manrope", args.manrope), ("nunito", args.nunito)):
        for name, weight in WEIGHTS.items():
            target = FONT_DIR / f"{family}_{name}.ttf"
            static_instance(source, weight, target)
            print(f"{target.relative_to(ROOT)}: {target.stat().st_size // 1024} KB")


if __name__ == "__main__":
    main()

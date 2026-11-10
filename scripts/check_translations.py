#!/usr/bin/env python3
"""Verify that every translation has exactly the same strings as the English base.

Checks, for each app/src/main/res/values-<lang>/strings.xml:
  * no translatable string from values/strings.xml is missing;
  * no string exists that the base does not define (stale leftovers);
  * format placeholders such as %1$s match the base string;
  * the language is declared in locales_config.xml and Languages.kt.

Exit code 0 when everything matches, 1 otherwise. Standard library only.
"""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app" / "src" / "main" / "res"
LANGUAGES_KT = (
    ROOT / "app/src/main/java/io/github/giannoccarol/corollalauncher/settings/Languages.kt"
)
PLACEHOLDER = re.compile(r"%(?:\d+\$)?[sdf]")
# values-<lang> or values-<lang>-r<REGION>; skips qualifiers like values-night or values-h500dp.
LOCALE_DIR = re.compile(r"^values-([a-z]{2,3}(?:-r[A-Z]{2})?)$")


def load(path: Path) -> tuple[dict[str, str], set[str]]:
    """Returns (translatable strings, names marked translatable=false)."""
    strings: dict[str, str] = {}
    untranslatable: set[str] = set()
    for node in ET.parse(path).getroot().findall("string"):
        name = node.attrib["name"]
        if node.attrib.get("translatable") == "false":
            untranslatable.add(name)
        else:
            strings[name] = "".join(node.itertext())
    return strings, untranslatable


def main() -> int:
    base, untranslatable = load(RES / "values" / "strings.xml")
    errors: list[str] = []
    languages: list[str] = []

    for directory in sorted(RES.iterdir()):
        match = LOCALE_DIR.match(directory.name)
        strings_file = directory / "strings.xml"
        if not match or not strings_file.exists():
            continue
        language = match.group(1)
        languages.append(language)
        translated, _ = load(strings_file)

        for name in sorted(base.keys() - translated.keys()):
            errors.append(f"{language}: missing '{name}'")
        for name in sorted(translated.keys() - base.keys()):
            reason = "is not translatable" if name in untranslatable else "is not in the base"
            errors.append(f"{language}: '{name}' {reason}")
        for name in sorted(base.keys() & translated.keys()):
            expected = sorted(PLACEHOLDER.findall(base[name]))
            actual = sorted(PLACEHOLDER.findall(translated[name]))
            if expected != actual:
                errors.append(
                    f"{language}: '{name}' has placeholders {actual}, expected {expected}"
                )

    locales_config = (RES / "xml" / "locales_config.xml").read_text(encoding="utf-8")
    languages_kt = LANGUAGES_KT.read_text(encoding="utf-8")
    for language in ["en", *languages]:
        tag = language.replace("-r", "-")
        if f'android:name="{tag}"' not in locales_config:
            errors.append(f"{language}: not declared in res/xml/locales_config.xml")
        if f'"{tag}"' not in languages_kt:
            errors.append(f"{language}: not listed in Languages.SUPPORTED")

    if errors:
        print("Translation check failed:")
        for error in errors:
            print(f"  - {error}")
        return 1

    print(f"OK: {len(base)} strings, languages: en (base), {', '.join(languages)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())

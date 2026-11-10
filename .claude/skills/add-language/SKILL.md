---
name: add-language
description: Add a new interface language to the launcher, or add and change user-facing strings across all existing languages. Use when asked to translate the app, support another language, or add, rename or reword any text shown in the UI.
---

# Add a language or a string

English (`app/src/main/res/values/strings.xml`) is the base. Every other language must define
exactly the same translatable strings. `scripts/check_translations.py` enforces this.

## Adding a language

For a language with tag `xx` (for example `pt`, or `pt-BR` for a regional variant):

1. Create `app/src/main/res/values-xx/strings.xml` (regional: `values-pt-rBR`). Copy the base
   file, remove the `app_name` line (it is `translatable="false"`), keep the section comments,
   and translate every value.
2. Add `<locale android:name="xx" />` to `app/src/main/res/xml/locales_config.xml`.
3. Add `"xx"` to `Languages.SUPPORTED` in
   `app/src/main/java/io/github/giannoccarol/corollalauncher/settings/Languages.kt`.
4. Add the language to the lists in `README.md` ("Five languages...") and update the count.
5. Run `python3 scripts/check_translations.py`, then build.
6. On the emulator, choose the language in *Settings → Language* and look at the home screen,
   the app drawer and the settings screen. Long words must not be cut off or wrap badly:
   the Home/Work buttons and the dock are the tightest spots.

## Adding or changing a string

1. Add it to `values/strings.xml` with a descriptive name prefixed by its area
   (`home_`, `media_`, `drawer_`, `app_`, `nav_`, `settings_`, `theme_`).
2. Add the translation to **every** `values-xx/strings.xml`, in the same position.
3. Run the translation check.

## Translation rules

- Translate meaning, not words. Use the wording the platform itself uses in that language
  (what Android calls Settings, Back, Cancel).
- Keep it short: this is read at a glance in a car. Prefer the common everyday word.
- Keep placeholders such as `%1$s` exactly as in the base, and keep them in a natural position.
- Escape apostrophes as `\'`. Do not use HTML entities for accented letters; the files are UTF-8.
- Do not translate `app_name`, app names, or `Live`-style terms the language keeps in English.
- Follow the language's own punctuation (for example a space before `?` in French, `¿...?` in
  Spanish).

If you are not confident in a translation, say so and list those strings for a native speaker
to review instead of guessing silently.

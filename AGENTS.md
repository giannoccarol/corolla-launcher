# Agent guide

Rules and context for AI coding assistants working in this repository. Human contributors
should read it too: it is the shortest description of how the project is meant to be changed.

## What this is

An Android launcher (home screen app) for an aftermarket car head unit: a Podofo Android unit
in a Toyota Corolla E120. Single Gradle module (`:app`), Kotlin, classic Views with
ViewBinding, AppCompat DayNight. No Compose, no dependency injection, no database.

Read these before non-trivial work:

- [README.md](README.md): features, build, install, known limits.
- [docs/architecture.md](docs/architecture.md): how the code is organised and why.
- [docs/design.md](docs/design.md): colors, sizes and layout rules.
- [docs/head-unit.md](docs/head-unit.md): what is known and unknown about the target device.

## Commands

```bash
./gradlew :app:assembleDebug            # build
./gradlew :app:testDebugUnitTest        # unit tests
python3 scripts/check_translations.py   # translations complete and consistent
scripts/emulator.sh start               # 1024x600 emulator (run "create" once first)
```

Gradle needs JDK 17 or 21. If the default `java` is newer, set `JAVA_HOME` to one of those
before calling `./gradlew`.

A change is done when the build, the unit tests and the translation check all pass, and any
visible change has been looked at on the emulator in **both themes**.

## Hard rules

1. **No hardcoded user-facing text.** Every string goes in `res/values/strings.xml` (English)
   and in **every** `values-xx/strings.xml`. Run the translation check.
2. **The theme is uniform.** Never hardcode a color in a layout or in Kotlin. Use the named
   colors, and define any new color in both `values/colors.xml` and `values-night/colors.xml`.
   A screen must never mix light and dark surfaces.
3. **No purple.** The owner does not want it anywhere in the UI.
4. **It is used while driving.** Touch targets are at least 48dp, primary ones 56dp or more.
   Text on the home screen is at least 13sp, and anything read at a glance is much larger.
   No interaction may require precise or repeated taps.
5. **Assume weak hardware.** The unit may be a quad-core Cortex-A7 with 1-2 GB of RAM running
   Android 8-10, whatever the listing claims. Keep `minSdk` at 24, guard newer APIs with
   `Build.VERSION.SDK_INT`, avoid heavy libraries and never block the main thread.
6. **Animations are welcome, but only cheap ones, and only through `ui/Motion.kt`.** Animate
   alpha, translation and scale only; never size, padding, color, text or anything else that
   forces a layout or a redraw. Keep each under about 250 ms, never loop, and never animate
   something that is permanently on screen (no pulsing, no moving progress). Every animation
   must be skipped when `Motion.enabled()` is false.
7. **No sounds.** The theme disables view click sounds; do not re-enable them, and do not add
   audio feedback of any kind.
8. **Fail soft.** Head unit firmware strips system apps and settings screens. Every
   `startActivity` to something outside the app must handle `ActivityNotFoundException`.
9. **Be honest in the docs.** If something was only tested on the emulator, the README says so.
   Update "Known limits" when a limit is removed or discovered.

## Conventions

- Kotlin official style, 4 spaces, 100 columns. Comments explain *why*, not what.
- Logic that can be pure (no Android types) lives in its own object and gets a unit test;
  see `DockSelector`.
- One activity per screen, each in its feature package. Shared helpers go in `ui/`.
- New dependencies need a reason written in the pull request; prefer the platform and AndroidX.
- All repository content is in English: code, comments, docs, commit messages.

## Commits and pull requests

- Written in English. Imperative subject, 72 characters at most; body explains why.
- No AI attribution: no `Co-Authored-By` lines for assistants, no "Generated with" lines.
- Credits for other people's work go in the README "Credits" section, never in commit messages.

## Where things go

| Need | Place |
| --- | --- |
| New screen | new package under `corollalauncher/`, activity declared in the manifest |
| New setting | `settings/Prefs.kt` for storage, a row in `SettingsActivity.render()` |
| New language | follow `.claude/skills/add-language/SKILL.md` |
| New icon | vector drawable in `res/drawable/`, white fill, tinted in the layout |
| New color | both `values/colors.xml` and `values-night/colors.xml` |
| New animation | a function in `ui/Motion.kt`, or an XML in `res/anim/` used from it |
| Device finding | `docs/head-unit.md` |

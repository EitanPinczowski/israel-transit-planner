---
name: i18n-rtl
description: Hebrew right-to-left and English UI rules, plus the design system (palette, dark mode, insets, phone sizes). Load before touching any UI text, layout, colour, icon, or number/time formatting.
---

# Hebrew RTL + English

- Resources: `values/` = English (default), `values-iw/` = Hebrew. (`iw`, not `he`: it is
  the qualifier every Android version resolves.) A string in only one file is a bug.
- Manifest has `android:supportsRtl="true"`. In Compose use `start`/`end`, never
  `left`/`right`; `Alignment.BottomStart`, `Arrangement.Start`, `PaddingValues(start=…)`.
- Directional icons (back arrow, chevrons) must mirror: `Icons.AutoMirrored.*`.
- **Except real-world directions.** Walking-direction arrows (← left, → right in
  `WalkDirections`) mean the street, not the layout: never mirror them in Hebrew.
- Numbers, times, line numbers inside Hebrew text: wrap in `⁨…⁩` (FSI/PDI) or use
  `BidiFormatter` so "קו 5 בעוד 7 דק׳" doesn't reorder. Line numbers like `5א` are text,
  never parse them as ints.
- Never put `→` between two times or places: in RTL the arrow points backwards. Use an
  en dash (`12:00–13:37`), which reads correctly in both directions.
  Between two places ("Home → Post office", "5 → Central station") use a string resource
  with `→` in `values/` and `←` in `values-iw/` (`chain_leg_title`, `line_to`), never a
  literal arrow in Kotlin.
- `tools/check_strings.py` (run by `check_docs.py`, so by CI) fails on: a key or plural in
  only one language, different placeholders for one key, an unescaped `'`, a bare `%`
  outside `formatted="false"`. A cloud session has no aapt, so this is the only check before CI.
- Delay badges (`+3`) inside Hebrew strings: put an LRM (U+200E) before `+` and wrap the
  number in FSI/PDI, or the plus sign jumps to the wrong side (`late`, `late_paren`).
- Times: 24-hour, `HH:mm`, zone `Asia/Jerusalem` (`ISRAEL` in `core/features/Common.kt`),
  whatever the phone's zone is.
- Stop and route names come from MOTIS with `language=he` by default; request `en` when
  the UI is English.
- Plurals: `<plurals>` in both files; Hebrew needs `one` and `other` (and `two` reads
  better for minutes: "2 דקות").

# Design: theme, dark mode, every phone shape

- Colours come from `ui/Theme.kt` (`LightColors`/`DarkColors`, seeded from the icon's
  `#1E88E5`; amber = tertiary). No Material You on purpose: route colours are designed
  against these surfaces. Never hard-code a UI colour in Compose; use `MaterialTheme.colorScheme`.
- Corners: `MaterialTheme.shapes` (`extraSmall` 6 chips · `small` 8 · `medium` 12 option
  cards · `large` 16 cards · `extraLarge` 28 sheets). No ad-hoc `RoundedCornerShape`.
- Text on an operator's route colour: `onColor(hex)` (core, tested) picks black or white.
- Leg colours inside one itinerary come from `LegPalette.colors(legs)` (core
  `present/LegPalette.kt`), never `legColor(leg)` alone: chip, map line and stop dots all
  use it. A transit leg keeps its operator colour only when no other leg of the trip has it
  and it reads on both maps (≥ 1.5:1); otherwise it takes the next of 6 Okabe–Ito based
  colours, each ≥ 3:1 on OpenFreeMap's light `#F8F4F0` and dark `#0C0C0C` (the `background`
  layer; parks and water can be closer). Walks stay grey. Colours are per itinerary, not per
  line: line 370 can be blue in one option and orange in the next. The trip sheet and the
  vehicle dot take the same colour (`LegPalette.colorOf`).
- **Settings → Appearance** (C6): `UserSettings.language` (null = phone, "he", "en") and
  `.theme` ("SYSTEM"/"LIGHT"/"DARK", `Appearance.dark`). `ui/AppLocale` applies them: API
  33+ `LocaleManager` (+ `res/xml/locales_config.xml`, the phone's per-app page; a choice
  made there wins once at the next start), API 26–32 a wrapped base context in
  `MainActivity.attachBaseContext`. Both are mirrored in SharedPreferences for the first
  frame. The Transitous `language` is `TransitApp.language`, read per request: the app's
  language, else the phone's. The map style and bar icons follow the theme, not only the
  phone. A screen never reads `isSystemInDarkTheme()` itself: `AppTheme(dark)` is set once.
- Map layers (`MapController`) take `dark` and use their own `Palette`; a new layer needs
  both values. Rides get a casing (halo) so any line colour reads on any map.
- Insets: edge-to-edge everywhere. Overlays pad with `WindowInsets.safeDrawing` (bars +
  camera cutout + keyboard); sheets pad their content with bars + cutout but paint under
  the nav bar. The notch is on a SIDE in landscape: never pad only the top.
- Sizes: nothing fixed in height. Sheets cap at 50% of the screen (less if needed so 30% stays map) and lists inside take
  `weight(1f, fill = false)`; on phones (and any window under 700dp tall) the search card
  folds to one line while results show, and the search column ends where the bottom part
  begins; 600dp wide and up, everything moves into one start-side column. The mode tabs
  stay one line (icons, the selected one named; `ModeTabs.kt`), never wrap or scroll sideways; typing happens in the tapped row.
- MainScreen reports the map area it covers (`MapPadding`); the Activity uses it for
  camera fits and to keep the compass and the OSM ⓘ (licence: must stay visible) clear.
- Icon: `python tools/gen_icons.py` writes the launcher foreground, background, themed
  layer and notification icon from one geometry. Edit the script, not the XML. The themed
  layer is a silhouette with holes, never the colour art (it would tint to a white block).
- Check a UI change in the screenshots CI records (Paparazzi; artifact `screenshots`, or
  the `screenshots` branch, which a cloud session can read; see `android-build`):
  small/big/landscape × light/dark × he/en × large text.

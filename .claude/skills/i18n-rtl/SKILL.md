---
name: i18n-rtl
description: Hebrew right-to-left and English UI rules. Load before touching any UI text, layout, icon, or number/time formatting.
---

# Hebrew RTL + English

- Resources: `values/` = English (default), `values-iw/` = Hebrew. (`iw`, not `he`: it is
  the qualifier every Android version resolves.) A string in only one file is a bug.
- Manifest has `android:supportsRtl="true"`. In Compose use `start`/`end`, never
  `left`/`right`; `Alignment.BottomStart`, `Arrangement.Start`, `PaddingValues(start=…)`.
- Directional icons (back arrow, chevrons) must mirror: `Icons.AutoMirrored.*`.
- Numbers, times, line numbers inside Hebrew text: wrap in `⁨…⁩` (FSI/PDI) or use
  `BidiFormatter` so "קו 5 בעוד 7 דק׳" doesn't reorder. Line numbers like `5א` are text,
  never parse them as ints.
- Never put `→` between two times or places: in RTL the arrow points backwards. Use an
  en dash (`12:00–13:37`), which reads correctly in both directions.
- Times: 24-hour, `HH:mm`, zone `Asia/Jerusalem` (`ISRAEL` in `core/features/Common.kt`),
  whatever the phone's zone is.
- Stop and route names come from MOTIS with `language=he` by default; request `en` when
  the UI is English.
- Plurals: `<plurals>` in both files; Hebrew needs `one` and `other` (and `two` reads
  better for minutes: "2 דקות").

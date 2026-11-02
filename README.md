# UI tests — 115/merge @ a3fc554 (run 41389624235)

29 runs · 478 screenshots · 564 tests passed · **0 failed** · **0 crash logs** · 393 distinct findings

Monkey (6000 events, seed 4242): **no crash**

All screens side by side: [screens.md](screens.md). Rules: `.claude/skills/ui-testing`.

## Runs

| run | screen as the device reported it |
|---|---|
| P1-en-dark | 360×640 dp, 320 dpi, font ×1.0 |
| P1-en-light | 360×640 dp, 320 dpi, font ×1.0 |
| P1-he-dark | 360×640 dp, 320 dpi, font ×1.0 |
| P1-he-light | 360×640 dp, 320 dpi, font ×1.0 |
| P2-en-light | 360×640 dp, 320 dpi, font ×1.3 |
| P2-he-light | 360×640 dp, 320 dpi, font ×1.3 |
| P3-en-light | 320×693 dp, 540 dpi, font ×1.0 |
| P3-he-light | 320×693 dp, 540 dpi, font ×1.0 |
| P4-en-dark | 384×832 dp, 450 dpi, font ×1.0 |
| P4-en-light | 384×832 dp, 450 dpi, font ×1.0 |
| P4-he-dark | 384×832 dp, 450 dpi, font ×1.0 |
| P4-he-light | 384×832 dp, 450 dpi, font ×1.0 |
| P5-en-light | 384×832 dp, 450 dpi, font ×2.0 |
| P5-he-light | 384×832 dp, 450 dpi, font ×2.0 |
| P6-en-light | 411×914 dp, 420 dpi, font ×1.0 |
| P6-he-light | 411×914 dp, 420 dpi, font ×1.0 |
| P7-en-light | 344×882 dp, 420 dpi, font ×1.0 |
| P7-he-light | 344×882 dp, 420 dpi, font ×1.0 |
| P8-en-dark | 914×411 dp, 420 dpi, font ×1.0 |
| P8-en-light | 914×411 dp, 420 dpi, font ×1.0 |
| P8-he-dark | 914×411 dp, 420 dpi, font ×1.0 |
| P8-he-light | 914×411 dp, 420 dpi, font ×1.0 |
| P9-en-light | 841×701 dp, 420 dpi, font ×1.0 |
| P9-he-light | 841×701 dp, 420 dpi, font ×1.0 |

## Findings per rule and run

| rule | P1-en-dark | P1-en-light | P1-he-dark | P1-he-light | P2-en-light | P2-he-light | P3-en-light | P3-he-light | P4-en-dark | P4-en-light | P4-he-dark | P4-he-light | P5-en-light | P5-he-light | P6-en-light | P6-he-light | P7-en-light | P7-he-light | P8-en-dark | P8-en-light | P8-he-dark | P8-he-light | P9-en-light | P9-he-light | api26-device-light | api29-device-light | api33-device-light | api34-device-light | api35-device-light |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| A11Y-MAP |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  | 2 |
| J1 |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  | 1 |  | 1 |  |  |
| R2 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 |  |  |  |  |  |  |  |  |  |  |  |
| R3 | 13 | 13 | 13 | 13 | 19 | 21 | 14 | 13 | 1 | 1 | 1 | 1 | 35 | 40 | 2 | 2 | 4 | 3 | 24 | 24 | 20 | 20 | 18 | 17 |  |  |  |  |  |
| R4 | 4 | 4 | 4 | 4 | 7 | 6 | 7 | 4 | 13 | 13 | 11 | 11 | 10 | 10 | 7 | 7 | 7 | 5 | 5 | 5 | 5 | 5 | 13 | 13 |  |  |  |  |  |
| R5 | 5 | 9 | 4 | 9 | 16 | 9 | 11 | 11 | 6 | 10 | 7 | 11 | 9 | 10 | 11 | 12 | 10 | 10 | 1 | 1 | 1 | 2 | 12 | 13 |  |  |  |  |  |

## A11Y-MAP —  (2)

| state | where | detail | runs (n) |
|---|---|---|---|
| journeys | journey | he: a long-press at (160, 196), between the panels, dropped no pin on this emulator | api35-device-light (1) |
| journeys | journey | en: a long-press at (160, 196), between the panels, dropped no pin on this emulator | api35-device-light (1) |

## J1 — first launch without location (or: not verifiable on that emulator) (2)

| state | where | detail | runs (n) |
|---|---|---|---|
| journeys | journey | not verifiable on this emulator: SystemUI crashed (API 26) | api26-device-light (1) |
| journeys | journey | no location permission prompt on first launch | api33-device-light (1) |

## R2 — tappable thing outside the usable screen (2)

| state | where | detail | runs (n) |
|---|---|---|---|
| [03-search-keyboard](shots/P1-en-dark/03-search-keyboard.jpg) | keyboard | # tappable items hidden behind the keyboard, e.g. "Routing: Transitous · Map © OpenStreetMap, OpenFreeMap" | P1-en-dark, P1-en-light, P2-en-light, P3-en-light, P4-en-dark, P4-en-light … (9) |
| [03-search-keyboard](shots/P1-he-dark/03-search-keyboard.jpg) | keyboard | # tappable items hidden behind the keyboard, e.g. "ניתוב: Transitous · מפה © OpenStreetMap, OpenFreeMap" | P1-he-dark, P1-he-light, P2-he-light, P3-he-light, P4-he-dark, P4-he-light … (9) |

## R3 — text clipped, squeezed or a time/price cut off (140)

| state | where | detail | runs (n) |
|---|---|---|---|
| [04-trip-results](shots/P1-en-dark/04-trip-results.jpg) | "Line 370 · 2 stops · 69 min · get off at ⁨מחלף לה גווארדייה⁩" | squeezed: 2 lines in 0 dp | P1-en-dark, P1-en-light, P2-en-light, P3-en-light, P8-en-dark, P8-en-light … (7) |
| [04-trip-results](shots/P1-he-dark/04-trip-results.jpg) | "קו ⁨370⁩ · ⁨2⁩ תחנות · ⁨69⁩ דק׳ · ירידה ב־⁨מחלף לה גווארדייה" | squeezed: 2 lines in 0 dp | P1-he-dark, P1-he-light, P2-he-light, P3-he-light, P8-he-dark, P8-he-light … (7) |
| [04-trip-results](shots/P1-he-dark/04-trip-results.jpg) | "קו ⁨171⁩ · ⁨6⁩ תחנות · ⁨7⁩ דק׳ · ירידה ב־⁨ת. רכבת תל אביב - " | squeezed: 2 lines in 0 dp | P1-he-dark, P1-he-light, P2-he-light, P3-he-light, P8-he-dark, P8-he-light … (7) |
| [07-trip-offline-banner](shots/P1-en-dark/07-trip-offline-banner.jpg) | "Line 370 · 2 stops · 69 min · get off at ⁨מחלף לה גווארדייה⁩" | squeezed: 2 lines in 0 dp | P1-en-dark, P1-en-light, P2-en-light, P3-en-light, P8-en-dark, P8-en-light … (7) |
| [07-trip-offline-banner](shots/P1-he-dark/07-trip-offline-banner.jpg) | "קו ⁨370⁩ · ⁨2⁩ תחנות · ⁨69⁩ דק׳ · ירידה ב־⁨מחלף לה גווארדייה" | squeezed: 2 lines in 0 dp | P1-he-dark, P1-he-light, P2-he-light, P3-he-light, P8-he-dark, P8-he-light … (7) |
| [07-trip-offline-banner](shots/P1-he-dark/07-trip-offline-banner.jpg) | "קו ⁨171⁩ · ⁨6⁩ תחנות · ⁨7⁩ דק׳ · ירידה ב־⁨ת. רכבת תל אביב - " | squeezed: 2 lines in 0 dp | P1-he-dark, P1-he-light, P2-he-light, P3-he-light, P8-he-dark, P8-he-light … (7) |
| [17-update-banner-results](shots/P1-en-dark/17-update-banner-results.jpg) | "Line 370 · 2 stops · 69 min · get off at ⁨מחלף לה גווארדייה⁩" | squeezed: 2 lines in 0 dp | P1-en-dark, P1-en-light, P2-en-light, P3-en-light, P8-en-dark, P8-en-light … (7) |
| [17-update-banner-results](shots/P1-he-dark/17-update-banner-results.jpg) | "קו ⁨370⁩ · ⁨2⁩ תחנות · ⁨69⁩ דק׳ · ירידה ב־⁨מחלף לה גווארדייה" | squeezed: 2 lines in 0 dp | P1-he-dark, P1-he-light, P2-he-light, P3-he-light, P8-he-dark, P8-he-light … (7) |
| [17-update-banner-results](shots/P1-he-dark/17-update-banner-results.jpg) | "קו ⁨171⁩ · ⁨6⁩ תחנות · ⁨7⁩ דק׳ · ירידה ב־⁨ת. רכבת תל אביב - " | squeezed: 2 lines in 0 dp | P1-he-dark, P1-he-light, P2-he-light, P3-he-light, P8-he-dark, P8-he-light … (7) |
| [18-save-trip-dialog](shots/P1-en-dark/18-save-trip-dialog.jpg) | "Line 370 · 2 stops · 69 min · get off at ⁨מחלף לה גווארדייה⁩" | squeezed: 2 lines in 0 dp | P1-en-dark, P1-en-light, P2-en-light, P3-en-light, P8-en-dark, P8-en-light … (7) |
| [18-save-trip-dialog](shots/P1-he-dark/18-save-trip-dialog.jpg) | "קו ⁨370⁩ · ⁨2⁩ תחנות · ⁨69⁩ דק׳ · ירידה ב־⁨מחלף לה גווארדייה" | squeezed: 2 lines in 0 dp | P1-he-dark, P1-he-light, P2-he-light, P3-he-light, P8-he-dark, P8-he-light … (7) |
| [18-save-trip-dialog](shots/P1-he-dark/18-save-trip-dialog.jpg) | "קו ⁨171⁩ · ⁨6⁩ תחנות · ⁨7⁩ דק׳ · ירידה ב־⁨ת. רכבת תל אביב - " | squeezed: 2 lines in 0 dp | P1-he-dark, P1-he-light, P2-he-light, P3-he-light, P8-he-dark, P8-he-light … (7) |
| [04-trip-results](shots/P1-en-dark/04-trip-results.jpg) | "Line 5 · 1 stop · 1 min · get off at ⁨מרכז רפואי סורוקה/אוני" | squeezed: 2 lines in 0 dp | P1-en-dark, P1-en-light, P3-en-light, P8-en-dark, P8-en-light, P9-en-light (6) |
| [04-trip-results](shots/P1-en-dark/04-trip-results.jpg) | "Line 171 · 6 stops · 7 min · get off at ⁨ת. רכבת תל אביב - ס" | squeezed: 2 lines in 0 dp | P1-en-dark, P1-en-light, P3-en-light, P8-en-dark, P8-en-light, P9-en-light (6) |
| [04-trip-results](shots/P1-he-dark/04-trip-results.jpg) | "קו ⁨5⁩ · תחנה אחת · ⁨1⁩ דק׳ · ירידה ב־⁨מרכז רפואי סורוקה/אונ" | squeezed: 2 lines in 0 dp | P1-he-dark, P1-he-light, P3-he-light, P8-he-dark, P8-he-light, P9-he-light (6) |
| [05-trip-second-option](shots/P1-en-dark/05-trip-second-option.jpg) | "Train 406 to ⁨כרמיאל⁩ · 5 stops · 71 min · get off at ⁨תל אב" | squeezed: 2 lines in 0 dp | P1-en-dark, P1-en-light, P2-en-light, P8-en-dark, P8-en-light, P9-en-light (6) |
| [07-trip-offline-banner](shots/P1-en-dark/07-trip-offline-banner.jpg) | "Line 5 · 1 stop · 1 min · get off at ⁨מרכז רפואי סורוקה/אוני" | squeezed: 2 lines in 0 dp | P1-en-dark, P1-en-light, P3-en-light, P8-en-dark, P8-en-light, P9-en-light (6) |
| [07-trip-offline-banner](shots/P1-en-dark/07-trip-offline-banner.jpg) | "Line 171 · 6 stops · 7 min · get off at ⁨ת. רכבת תל אביב - ס" | squeezed: 2 lines in 0 dp | P1-en-dark, P1-en-light, P3-en-light, P8-en-dark, P8-en-light, P9-en-light (6) |
| [07-trip-offline-banner](shots/P1-he-dark/07-trip-offline-banner.jpg) | "קו ⁨5⁩ · תחנה אחת · ⁨1⁩ דק׳ · ירידה ב־⁨מרכז רפואי סורוקה/אונ" | squeezed: 2 lines in 0 dp | P1-he-dark, P1-he-light, P3-he-light, P8-he-dark, P8-he-light, P9-he-light (6) |
| [17-update-banner-results](shots/P1-en-dark/17-update-banner-results.jpg) | "Line 5 · 1 stop · 1 min · get off at ⁨מרכז רפואי סורוקה/אוני" | squeezed: 2 lines in 0 dp | P1-en-dark, P1-en-light, P3-en-light, P8-en-dark, P8-en-light, P9-en-light (6) |
| [17-update-banner-results](shots/P1-en-dark/17-update-banner-results.jpg) | "Line 171 · 6 stops · 7 min · get off at ⁨ת. רכבת תל אביב - ס" | squeezed: 2 lines in 0 dp | P1-en-dark, P1-en-light, P3-en-light, P8-en-dark, P8-en-light, P9-en-light (6) |
| [17-update-banner-results](shots/P1-he-dark/17-update-banner-results.jpg) | "קו ⁨5⁩ · תחנה אחת · ⁨1⁩ דק׳ · ירידה ב־⁨מרכז רפואי סורוקה/אונ" | squeezed: 2 lines in 0 dp | P1-he-dark, P1-he-light, P3-he-light, P8-he-dark, P8-he-light, P9-he-light (6) |
| [18-save-trip-dialog](shots/P1-en-dark/18-save-trip-dialog.jpg) | "Line 5 · 1 stop · 1 min · get off at ⁨מרכז רפואי סורוקה/אוני" | squeezed: 2 lines in 0 dp | P1-en-dark, P1-en-light, P3-en-light, P8-en-dark, P8-en-light, P9-en-light (6) |
| [18-save-trip-dialog](shots/P1-en-dark/18-save-trip-dialog.jpg) | "Line 171 · 6 stops · 7 min · get off at ⁨ת. רכבת תל אביב - ס" | squeezed: 2 lines in 0 dp | P1-en-dark, P1-en-light, P3-en-light, P8-en-dark, P8-en-light, P9-en-light (6) |
| [18-save-trip-dialog](shots/P1-he-dark/18-save-trip-dialog.jpg) | "קו ⁨5⁩ · תחנה אחת · ⁨1⁩ דק׳ · ירידה ב־⁨מרכז רפואי סורוקה/אונ" | squeezed: 2 lines in 0 dp | P1-he-dark, P1-he-light, P3-he-light, P8-he-dark, P8-he-light, P9-he-light (6) |
| [05-trip-second-option](shots/P2-he-light/05-trip-second-option.jpg) | "⁨8⁩ תחנות · ⁨76⁩ דק׳ בנסיעה · ירידה ב־⁨ת. רכבת תל אביב - סבי" | squeezed: 2 lines in 0 dp | P2-he-light, P4-he-dark, P4-he-light, P6-he-light, P7-he-light (5) |
| [05-trip-second-option](shots/P4-en-dark/05-trip-second-option.jpg) | "8 stops · 76 min on board · off at ⁨ת. רכבת תל אביב - סבידור" | squeezed: 2 lines in 0 dp | P4-en-dark, P4-en-light, P5-en-light, P6-en-light, P7-en-light (5) |
| [03-search-keyboard](shots/P8-en-dark/03-search-keyboard.jpg) | "תל" | text is clipped: box 191×0 dp, text 20×24 dp, 1 lines (height) | P8-en-dark, P8-en-light, P8-he-dark, P8-he-light (4) |
| [05-trip-second-option](shots/P1-he-dark/05-trip-second-option.jpg) | "רכבת ⁨406⁩ ל⁨כרמיאל⁩ · ⁨5⁩ תחנות · ⁨71⁩ דק׳ · ירידה ב־⁨תל אב" | squeezed: 2 lines in 0 dp | P1-he-dark, P1-he-light, P2-he-light, P5-he-light (4) |
| [18-save-trip-dialog](shots/P8-en-dark/18-save-trip-dialog.jpg) | "Routes" | text is clipped: box 52×0 dp, text 51×24 dp, 1 lines (height) | P8-en-dark, P8-en-light, P9-en-light (3) |
| [18-save-trip-dialog](shots/P8-en-dark/18-save-trip-dialog.jpg) | "Updated 07:55" | text is clipped: box 76×0 dp, text 76×16 dp, 1 lines (height) | P8-en-dark, P8-en-light, P9-en-light (3) |
| [18-save-trip-dialog](shots/P8-en-dark/18-save-trip-dialog.jpg) | "Remind me" | text is clipped: box 70×0 dp, text 70×20 dp, 1 lines (height) | P8-en-dark, P8-en-light, P9-en-light (3) |
| [18-save-trip-dialog](shots/P8-en-dark/18-save-trip-dialog.jpg) | "Start trip" | text is clipped: box 56×0 dp, text 56×20 dp, 1 lines (height) | P8-en-dark, P8-en-light, P9-en-light (3) |
| [18-save-trip-dialog](shots/P8-he-dark/18-save-trip-dialog.jpg) | "מסלולים" | text is clipped: box 59×0 dp, text 58×24 dp, 1 lines (height) | P8-he-dark, P8-he-light, P9-he-light (3) |
| [18-save-trip-dialog](shots/P8-he-dark/18-save-trip-dialog.jpg) | "עודכן ב־⁨07:55⁩" | text is clipped: box 70×0 dp, text 70×16 dp, 1 lines (height) | P8-he-dark, P8-he-light, P9-he-light (3) |
| [18-save-trip-dialog](shots/P8-he-dark/18-save-trip-dialog.jpg) | "תזכורת" | text is clipped: box 42×0 dp, text 42×20 dp, 1 lines (height) | P8-he-dark, P8-he-light, P9-he-light (3) |
| [18-save-trip-dialog](shots/P8-he-dark/18-save-trip-dialog.jpg) | "התחלת נסיעה" | text is clipped: box 83×0 dp, text 83×20 dp, 1 lines (height) | P8-he-dark, P8-he-light, P9-he-light (3) |
| [03-search-keyboard](shots/P8-en-dark/03-search-keyboard.jpg) | "Routing: Transitous · Map © OpenStreetMap, OpenFreeMap" | text is clipped: box 309×0 dp, text 308×16 dp, 1 lines (height) | P8-en-dark, P8-en-light (2) |
| [03-search-keyboard](shots/P8-he-dark/03-search-keyboard.jpg) | "ניתוב: Transitous · מפה © OpenStreetMap, OpenFreeMap" | text is clipped: box 293×0 dp, text 293×16 dp, 1 lines (height) | P8-he-dark, P8-he-light (2) |
| [04-trip-results](shots/P2-en-light/04-trip-results.jpg) | "5 at 08:04 from ⁨מרפאות חוץ סורוקה/אוניברסיטת בן גוריון⁩" | squeezed: 2 lines in 0 dp | P2-en-light, P5-en-light (2) |
| [04-trip-results](shots/P2-en-light/04-trip-results.jpg) | "Line 171 · 6 stops · 7 min · get off at ⁨ת. רכבת תל אביב - ס" | squeezed: 3 lines in 0 dp | P2-en-light, P5-en-light (2) |
| [04-trip-results](shots/P2-he-light/04-trip-results.jpg) | "⁨5⁩ ב־⁨08:04⁩ מ־⁨מרפאות חוץ סורוקה/אוניברסיטת בן גוריון⁩" | squeezed: 2 lines in 0 dp | P2-he-light, P5-he-light (2) |
| [05-trip-second-option](shots/P2-he-light/05-trip-second-option.jpg) | "⁨370⁩ ב־⁨08:37⁩ מ־⁨מרכז רפואי סורוקה/אוניברסיטת בן גוריון⁩" | squeezed: 2 lines in 0 dp | P2-he-light, P7-he-light (2) |
| [05-trip-second-option](shots/P5-en-light/05-trip-second-option.jpg) | "370 at 08:37 from ⁨מרכז רפואי סורוקה/אוניברסיטת בן גוריון⁩" | squeezed: 2 lines in 0 dp | P5-en-light, P7-en-light (2) |
| [07-trip-offline-banner](shots/P2-en-light/07-trip-offline-banner.jpg) | "5 at 08:04 from ⁨מרפאות חוץ סורוקה/אוניברסיטת בן גוריון⁩" | squeezed: 2 lines in 0 dp | P2-en-light, P5-en-light (2) |
| [07-trip-offline-banner](shots/P2-en-light/07-trip-offline-banner.jpg) | "Line 171 · 6 stops · 7 min · get off at ⁨ת. רכבת תל אביב - ס" | squeezed: 3 lines in 0 dp | P2-en-light, P5-en-light (2) |
| [07-trip-offline-banner](shots/P2-he-light/07-trip-offline-banner.jpg) | "⁨5⁩ ב־⁨08:04⁩ מ־⁨מרפאות חוץ סורוקה/אוניברסיטת בן גוריון⁩" | squeezed: 2 lines in 0 dp | P2-he-light, P5-he-light (2) |
| [15-settings-bottom](shots/P6-en-light/15-settings-bottom.jpg) | "Counted on this phone only: numbers and times, never places " | squeezed: 2 lines in 0 dp | P6-en-light, P7-en-light (2) |
| [15-settings-bottom](shots/P6-he-light/15-settings-bottom.jpg) | "נספר בטלפון בלבד: מספרים ושעות, אף פעם לא מקומות או חיפושים." | squeezed: 2 lines in 0 dp | P6-he-light, P7-he-light (2) |
| [15-settings-bottom](shots/P8-en-dark/15-settings-bottom.jpg) | "Say when rain is likely while walking" | squeezed: 2 lines in 0 dp | P8-en-dark, P8-en-light (2) |
| [15-settings-bottom](shots/P8-en-dark/15-settings-bottom.jpg) | "Asks Open-Meteo for the forecast near your start (rounded to" | squeezed: 4 lines in 0 dp | P8-en-dark, P8-en-light (2) |
| [15-settings-bottom](shots/P8-en-dark/15-settings-bottom.jpg) | "Each night, on Wi-Fi and while charging, the app plans tomor" | squeezed: 6 lines in 0 dp | P8-en-dark, P8-en-light (2) |
| [15-settings-bottom](shots/P8-en-dark/15-settings-bottom.jpg) | "Set one of your saved places as Home (below) to include your" | squeezed: 2 lines in 0 dp | P8-en-dark, P8-en-light (2) |
| [15-settings-bottom](shots/P8-he-dark/15-settings-bottom.jpg) | "כדי לכלול את השגרות, הגדירו אחד מהמקומות השמורים כבית (למטה)" | squeezed: 2 lines in 0 dp | P8-he-dark, P8-he-light (2) |
| [17-update-banner-results](shots/P2-en-light/17-update-banner-results.jpg) | "5 at 08:04 from ⁨מרפאות חוץ סורוקה/אוניברסיטת בן גוריון⁩" | squeezed: 2 lines in 0 dp | P2-en-light, P5-en-light (2) |
| [17-update-banner-results](shots/P2-en-light/17-update-banner-results.jpg) | "Line 171 · 6 stops · 7 min · get off at ⁨ת. רכבת תל אביב - ס" | squeezed: 3 lines in 0 dp | P2-en-light, P5-en-light (2) |
| [17-update-banner-results](shots/P2-he-light/17-update-banner-results.jpg) | "⁨5⁩ ב־⁨08:04⁩ מ־⁨מרפאות חוץ סורוקה/אוניברסיטת בן גוריון⁩" | squeezed: 2 lines in 0 dp | P2-he-light, P5-he-light (2) |
| [18-save-trip-dialog](shots/P2-en-light/18-save-trip-dialog.jpg) | "5 at 08:04 from ⁨מרפאות חוץ סורוקה/אוניברסיטת בן גוריון⁩" | squeezed: 2 lines in 0 dp | P2-en-light, P5-en-light (2) |
| [18-save-trip-dialog](shots/P2-en-light/18-save-trip-dialog.jpg) | "Line 171 · 6 stops · 7 min · get off at ⁨ת. רכבת תל אביב - ס" | squeezed: 3 lines in 0 dp | P2-en-light, P5-en-light (2) |
| [18-save-trip-dialog](shots/P2-he-light/18-save-trip-dialog.jpg) | "⁨5⁩ ב־⁨08:04⁩ מ־⁨מרפאות חוץ סורוקה/אוניברסיטת בן גוריון⁩" | squeezed: 2 lines in 0 dp | P2-he-light, P5-he-light (2) |
| … | | 80 more | |

## R4 — touch target under 48×48 dp (136)

| state | where | detail | runs (n) |
|---|---|---|---|
| [13-stop-departures](shots/P3-en-light/13-stop-departures.jpg) | "Add Train to My lines" | 58×24 dp; its 48 dp tap area reaches "Add Train to My lines" | P3-en-light, P4-en-dark, P4-en-light (3) |
| [13-stop-departures](shots/P3-he-light/13-stop-departures.jpg) | "הוספת ⁨רכבת⁩ לקווים שלי" | 58×24 dp; its 48 dp tap area reaches "הוספת ⁨רכבת⁩ לקווים שלי" | P3-he-light, P4-he-dark, P4-he-light (3) |
| [03-search-keyboard](shots/P8-en-dark/03-search-keyboard.jpg) | "node#334" | 259×17 dp; its 48 dp tap area reaches "From My location" | P8-en-dark, P8-en-light (2) |
| [03-search-keyboard](shots/P8-en-dark/03-search-keyboard.jpg) | "Cancel" | 40×17 dp; its 48 dp tap area reaches "From My location" | P8-en-dark, P8-en-light (2) |
| [03-search-keyboard](shots/P8-he-dark/03-search-keyboard.jpg) | "node#334" | 259×17 dp; its 48 dp tap area reaches "מ־ המיקום שלי" | P8-he-dark, P8-he-light (2) |
| [03-search-keyboard](shots/P8-he-dark/03-search-keyboard.jpg) | "ביטול" | 40×17 dp; its 48 dp tap area reaches "מ־ המיקום שלי" | P8-he-dark, P8-he-light (2) |
| [05-trip-second-option](shots/P4-en-dark/05-trip-second-option.jpg) | "⁨≈ ₪27⁩ 370 at 08:37 from ⁨מרכז רפואי סורוקה/אוניברסיטת בן ג" | 352×17 dp; its 48 dp tap area reaches "Fewer stops ⁨≈ ₪30.5⁩ Tap a line to see all its stops Train " | P4-en-dark, P4-en-light (2) |
| [05-trip-second-option](shots/P4-en-dark/05-trip-second-option.jpg) | "⁨≈ ₪27⁩ 370 at 08:37 from ⁨מרכז רפואי סורוקה/אוניברסיטת בן ג" | 352×17 dp; its 48 dp tap area reaches "Remind me" | P4-en-dark, P4-en-light (2) |
| [05-trip-second-option](shots/P4-en-dark/05-trip-second-option.jpg) | "⁨≈ ₪27⁩ 370 at 08:37 from ⁨מרכז רפואי סורוקה/אוניברסיטת בן ג" | 352×17 dp; its 48 dp tap area reaches "Start trip" | P4-en-dark, P4-en-light (2) |
| [05-trip-second-option](shots/P4-he-dark/05-trip-second-option.jpg) | "⁨≈ ₪27⁩ ⁨370⁩ ב־⁨08:37⁩ מ־⁨מרכז רפואי סורוקה/אוניברסיטת בן ג" | 352×33 dp; its 48 dp tap area reaches "פחות עצירות ⁨≈ ₪30.5⁩ הקישו על קו כדי לראות את כל התחנות שלו" | P4-he-dark, P4-he-light (2) |
| [11-drop-off-results](shots/P4-en-dark/11-drop-off-results.jpg) | "🚌 Transit from the start, no ride You arrive 10:50 2 transf" | 352×23 dp; its 48 dp tap area reaches "🚗 Get out at ⁨מזכרת בתיה⁩ · driver +4 min You arrive 10:41 " | P4-en-dark, P4-en-light (2) |
| [11-drop-off-results](shots/P4-en-dark/11-drop-off-results.jpg) | "🚌 Transit from the start, no ride You arrive 10:50 2 transf" | 352×23 dp; its 48 dp tap area reaches "Send to driver" | P4-en-dark, P4-en-light (2) |
| [11-drop-off-results](shots/P4-en-dark/11-drop-off-results.jpg) | "🚌 Transit from the start, no ride You arrive 10:50 2 transf" | 352×23 dp; its 48 dp tap area reaches "Start trip" | P4-en-dark, P4-en-light (2) |
| [11-drop-off-results](shots/P4-he-dark/11-drop-off-results.jpg) | "🚌 תחבורה ציבורית מההתחלה, בלי הסעה הגעה ב־⁨10:50⁩ ⁨2⁩ החלפו" | 352×23 dp; its 48 dp tap area reaches "🚗 לרדת ב⁨מזכרת בתיה⁩ · לנהג/ת ‎+⁨4⁩ דק׳ הגעה ב־⁨10:41⁩ ⁨2⁩ " | P4-he-dark, P4-he-light (2) |
| [11-drop-off-results](shots/P4-he-dark/11-drop-off-results.jpg) | "🚌 תחבורה ציבורית מההתחלה, בלי הסעה הגעה ב־⁨10:50⁩ ⁨2⁩ החלפו" | 352×23 dp; its 48 dp tap area reaches "שליחה לנהג/ת" | P4-he-dark, P4-he-light (2) |
| [11-drop-off-results](shots/P4-he-dark/11-drop-off-results.jpg) | "🚌 תחבורה ציבורית מההתחלה, בלי הסעה הגעה ב־⁨10:50⁩ ⁨2⁩ החלפו" | 352×23 dp; its 48 dp tap area reaches "התחלת נסיעה" | P4-he-dark, P4-he-light (2) |
| [12-pick-up](shots/P4-en-dark/12-pick-up.jpg) | "🚗 Get picked up at ⁨צומת דבירה⁩ at 09:41 Home at 10:04 save" | 352×12 dp; its 48 dp tap area reaches "🚗 Get picked up at ⁨מרכז אורן/יצחק רגר⁩ at 09:34 Home at 09" | P4-en-dark, P4-en-light (2) |
| [12-pick-up](shots/P4-en-dark/12-pick-up.jpg) | "🚗 Get picked up at ⁨צומת דבירה⁩ at 09:41 Home at 10:04 save" | 352×12 dp; its 48 dp tap area reaches "Send to driver" | P4-en-dark, P4-en-light (2) |
| [12-pick-up](shots/P4-en-dark/12-pick-up.jpg) | "🚗 Get picked up at ⁨צומת דבירה⁩ at 09:41 Home at 10:04 save" | 352×12 dp; its 48 dp tap area reaches "Start trip" | P4-en-dark, P4-en-light (2) |
| [12-pick-up](shots/P4-he-dark/12-pick-up.jpg) | "🚗 איסוף ב⁨צומת דבירה⁩ ב־⁨09:41⁩ בבית ב־⁨10:04⁩ חוסך ⁨46⁩ דק" | 352×12 dp; its 48 dp tap area reaches "🚗 איסוף ב⁨מרכז אורן/יצחק רגר⁩ ב־⁨09:34⁩ בבית ב־⁨09:54⁩ חוסך" | P4-he-dark, P4-he-light (2) |
| [12-pick-up](shots/P4-he-dark/12-pick-up.jpg) | "🚗 איסוף ב⁨צומת דבירה⁩ ב־⁨09:41⁩ בבית ב־⁨10:04⁩ חוסך ⁨46⁩ דק" | 352×12 dp; its 48 dp tap area reaches "שליחה לנהג/ת" | P4-he-dark, P4-he-light (2) |
| [12-pick-up](shots/P4-he-dark/12-pick-up.jpg) | "🚗 איסוף ב⁨צומת דבירה⁩ ב־⁨09:41⁩ בבית ב־⁨10:04⁩ חוסך ⁨46⁩ דק" | 352×12 dp; its 48 dp tap area reaches "התחלת נסיעה" | P4-he-dark, P4-he-light (2) |
| [13-stop-departures](shots/P1-en-dark/13-stop-departures.jpg) | "Train באר שבע מרכז 08:28" | 328×16 dp; its 48 dp tap area reaches "Train כרמיאל 08:21" | P1-en-dark, P1-en-light (2) |
| [13-stop-departures](shots/P1-en-dark/13-stop-departures.jpg) | "Train באר שבע מרכז 08:28" | 328×16 dp; its 48 dp tap area reaches "Add Train to My lines" | P1-en-dark, P1-en-light (2) |
| [13-stop-departures](shots/P1-en-dark/13-stop-departures.jpg) | "Add Train to My lines" | 58×12 dp; its 48 dp tap area reaches "Train כרמיאל 08:21" | P1-en-dark, P1-en-light (2) |
| [13-stop-departures](shots/P1-en-dark/13-stop-departures.jpg) | "Add Train to My lines" | 58×12 dp; its 48 dp tap area reaches "Add Train to My lines" | P1-en-dark, P1-en-light (2) |
| [13-stop-departures](shots/P1-he-dark/13-stop-departures.jpg) | "רכבת באר שבע מרכז 08:28" | 328×16 dp; its 48 dp tap area reaches "רכבת כרמיאל 08:21" | P1-he-dark, P1-he-light (2) |
| [13-stop-departures](shots/P1-he-dark/13-stop-departures.jpg) | "רכבת באר שבע מרכז 08:28" | 328×16 dp; its 48 dp tap area reaches "הוספת ⁨רכבת⁩ לקווים שלי" | P1-he-dark, P1-he-light (2) |
| [13-stop-departures](shots/P1-he-dark/13-stop-departures.jpg) | "הוספת ⁨רכבת⁩ לקווים שלי" | 58×12 dp; its 48 dp tap area reaches "רכבת כרמיאל 08:21" | P1-he-dark, P1-he-light (2) |
| [13-stop-departures](shots/P1-he-dark/13-stop-departures.jpg) | "הוספת ⁨רכבת⁩ לקווים שלי" | 58×12 dp; its 48 dp tap area reaches "הוספת ⁨רכבת⁩ לקווים שלי" | P1-he-dark, P1-he-light (2) |
| [13-stop-departures](shots/P4-en-dark/13-stop-departures.jpg) | "Train באר שבע מרכז 08:35" | 352×28 dp; its 48 dp tap area reaches "Train תל אביב מרכז 08:35" | P4-en-dark, P4-en-light (2) |
| [13-stop-departures](shots/P4-en-dark/13-stop-departures.jpg) | "Train באר שבע מרכז 08:35" | 352×28 dp; its 48 dp tap area reaches "Add Train to My lines" | P4-en-dark, P4-en-light (2) |
| [13-stop-departures](shots/P4-en-dark/13-stop-departures.jpg) | "Add Train to My lines" | 58×24 dp; its 48 dp tap area reaches "Train תל אביב מרכז 08:35" | P4-en-dark, P4-en-light (2) |
| [13-stop-departures](shots/P4-he-dark/13-stop-departures.jpg) | "רכבת באר שבע מרכז 08:35" | 352×28 dp; its 48 dp tap area reaches "רכבת תל אביב מרכז 08:35" | P4-he-dark, P4-he-light (2) |
| [13-stop-departures](shots/P4-he-dark/13-stop-departures.jpg) | "רכבת באר שבע מרכז 08:35" | 352×28 dp; its 48 dp tap area reaches "הוספת ⁨רכבת⁩ לקווים שלי" | P4-he-dark, P4-he-light (2) |
| [13-stop-departures](shots/P4-he-dark/13-stop-departures.jpg) | "הוספת ⁨רכבת⁩ לקווים שלי" | 58×24 dp; its 48 dp tap area reaches "רכבת תל אביב מרכז 08:35" | P4-he-dark, P4-he-light (2) |
| [13-stop-departures](shots/P8-en-dark/13-stop-departures.jpg) | "Train כרמיאל 08:21" | 364×38 dp; its 48 dp tap area reaches "Train הרצליה 08:06" | P8-en-dark, P8-en-light (2) |
| [13-stop-departures](shots/P8-en-dark/13-stop-departures.jpg) | "Train כרמיאל 08:21" | 364×38 dp; its 48 dp tap area reaches "Add Train to My lines" | P8-en-dark, P8-en-light (2) |
| [13-stop-departures](shots/P8-en-dark/13-stop-departures.jpg) | "Add Train to My lines" | 58×34 dp; its 48 dp tap area reaches "Train הרצליה 08:06" | P8-en-dark, P8-en-light (2) |
| [13-stop-departures](shots/P8-he-dark/13-stop-departures.jpg) | "רכבת כרמיאל 08:21" | 364×38 dp; its 48 dp tap area reaches "רכבת הרצליה 08:06" | P8-he-dark, P8-he-light (2) |
| [13-stop-departures](shots/P8-he-dark/13-stop-departures.jpg) | "רכבת כרמיאל 08:21" | 364×38 dp; its 48 dp tap area reaches "הוספת ⁨רכבת⁩ לקווים שלי" | P8-he-dark, P8-he-light (2) |
| [13-stop-departures](shots/P8-he-dark/13-stop-departures.jpg) | "הוספת ⁨רכבת⁩ לקווים שלי" | 58×34 dp; its 48 dp tap area reaches "רכבת הרצליה 08:06" | P8-he-dark, P8-he-light (2) |
| [03-search-keyboard](shots/P6-en-light/03-search-keyboard.jpg) | "הגר באר שבע" | 387×21 dp; its 48 dp tap area reaches "הגר באר שבע" | P6-en-light (1) |
| [03-search-keyboard](shots/P6-he-light/03-search-keyboard.jpg) | "הגר באר שבע" | 387×5 dp; its 48 dp tap area reaches "הגר באר שבע" | P6-he-light (1) |
| [04-trip-results](shots/P2-en-light/04-trip-results.jpg) | "Bus 171, 9:21 to 9:28, 6 stops, get off at ת. רכבת תל אביב -" | 41×24 dp; its 48 dp tap area reaches "Remind me" | P2-en-light (1) |
| [04-trip-results](shots/P2-he-light/04-trip-results.jpg) | "אוטובוס קו ⁨171⁩, מ־⁨9:21⁩ עד ⁨9:28⁩, ⁨6⁩ תחנות, ירידה ב⁨ת. " | 41×24 dp; its 48 dp tap area reaches "תזכורת" | P2-he-light (1) |
| [04-trip-results](shots/P5-en-light/04-trip-results.jpg) | "Bus 5, 8:04 to 8:05, 1 stop, get off at מרכז רפואי סורוקה/או" | 26×36 dp; its 48 dp tap area reaches "Bus 171, 9:21 to 9:28, 6 stops, get off at ת. רכבת תל אביב -" | P5-en-light (1) |
| [04-trip-results](shots/P5-en-light/04-trip-results.jpg) | "Bus 171, 9:21 to 9:28, 6 stops, get off at ת. רכבת תל אביב -" | 55×36 dp; its 48 dp tap area reaches "Bus 5, 8:04 to 8:05, 1 stop, get off at מרכז רפואי סורוקה/או" | P5-en-light (1) |
| [04-trip-results](shots/P5-he-light/04-trip-results.jpg) | "אוטובוס קו ⁨5⁩, מ־⁨8:04⁩ עד ⁨8:05⁩, תחנה אחת, ירידה ב⁨מרכז ר" | 26×36 dp; its 48 dp tap area reaches "אוטובוס קו ⁨171⁩, מ־⁨9:21⁩ עד ⁨9:28⁩, ⁨6⁩ תחנות, ירידה ב⁨ת. " | P5-he-light (1) |
| [04-trip-results](shots/P5-he-light/04-trip-results.jpg) | "אוטובוס קו ⁨171⁩, מ־⁨9:21⁩ עד ⁨9:28⁩, ⁨6⁩ תחנות, ירידה ב⁨ת. " | 55×36 dp; its 48 dp tap area reaches "אוטובוס קו ⁨5⁩, מ־⁨8:04⁩ עד ⁨8:05⁩, תחנה אחת, ירידה ב⁨מרכז ר" | P5-he-light (1) |
| [04-trip-results](shots/P6-en-light/04-trip-results.jpg) | "Walking directions" | 139×9 dp; its 48 dp tap area reaches "Remind me" | P6-en-light (1) |
| [04-trip-results](shots/P6-he-light/04-trip-results.jpg) | "הוראות הליכה" | 106×13 dp; its 48 dp tap area reaches "תזכורת" | P6-he-light (1) |
| [07-trip-offline-banner](shots/P5-en-light/07-trip-offline-banner.jpg) | "Bus 5, 8:04 to 8:05, 1 stop, get off at מרכז רפואי סורוקה/או" | 26×36 dp; its 48 dp tap area reaches "Bus 171, 9:21 to 9:28, 6 stops, get off at ת. רכבת תל אביב -" | P5-en-light (1) |
| [07-trip-offline-banner](shots/P5-en-light/07-trip-offline-banner.jpg) | "Bus 171, 9:21 to 9:28, 6 stops, get off at ת. רכבת תל אביב -" | 55×33 dp; its 48 dp tap area reaches "Bus 5, 8:04 to 8:05, 1 stop, get off at מרכז רפואי סורוקה/או" | P5-en-light (1) |
| [07-trip-offline-banner](shots/P5-he-light/07-trip-offline-banner.jpg) | "אוטובוס קו ⁨5⁩, מ־⁨8:04⁩ עד ⁨8:05⁩, תחנה אחת, ירידה ב⁨מרכז ר" | 26×36 dp; its 48 dp tap area reaches "אוטובוס קו ⁨171⁩, מ־⁨9:21⁩ עד ⁨9:28⁩, ⁨6⁩ תחנות, ירידה ב⁨ת. " | P5-he-light (1) |
| [07-trip-offline-banner](shots/P5-he-light/07-trip-offline-banner.jpg) | "אוטובוס קו ⁨171⁩, מ־⁨9:21⁩ עד ⁨9:28⁩, ⁨6⁩ תחנות, ירידה ב⁨ת. " | 55×32 dp; its 48 dp tap area reaches "אוטובוס קו ⁨5⁩, מ־⁨8:04⁩ עד ⁨8:05⁩, תחנה אחת, ירידה ב⁨מרכז ר" | P5-he-light (1) |
| [12-pick-up](shots/P3-en-light/12-pick-up.jpg) | "🚗 Get picked up at ⁨מרכז אורן/יצחק רגר⁩ at 09:34 Home at 09" | 288×25 dp; its 48 dp tap area reaches "🚗 Get picked up at ⁨להבים רהט⁩ at 09:22 Home at 09:46 saves" | P3-en-light (1) |
| [12-pick-up](shots/P3-en-light/12-pick-up.jpg) | "🚗 Get picked up at ⁨מרכז אורן/יצחק רגר⁩ at 09:34 Home at 09" | 288×25 dp; its 48 dp tap area reaches "Send to driver" | P3-en-light (1) |
| [12-pick-up](shots/P3-en-light/12-pick-up.jpg) | "🚗 Get picked up at ⁨מרכז אורן/יצחק רגר⁩ at 09:34 Home at 09" | 288×25 dp; its 48 dp tap area reaches "Start trip" | P3-en-light (1) |
| [12-pick-up](shots/P7-en-light/12-pick-up.jpg) | "🚗 Get picked up at ⁨צומת דבירה⁩ at 09:41 Home at 10:04 save" | 312×15 dp; its 48 dp tap area reaches "🚗 Get picked up at ⁨מרכז אורן/יצחק רגר⁩ at 09:34 Home at 09" | P7-en-light (1) |
| … | | 76 more | |

## R5 — Accessibility Test Framework (111)

| state | where | detail | runs (n) |
|---|---|---|---|
| [15-settings-bottom](shots/P1-en-light/15-settings-bottom.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 2.77. This ratio is based on an estimated foreground color of #FF4638 and an estimated background color of #E6E8EE. Consider using colors that result in a contrast ratio greater than 4.50 for small text, or 3.00 for large text. [android.widget.TextView Rect | P1-en-light, P1-he-light, P2-en-light, P2-he-light, P3-en-light, P3-he-light … (16) |
| [12-pick-up](shots/P6-he-light/12-pick-up.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 4.14. This ratio is based on an estimated foreground color of #D70617 and an estimated background color of #D1E4FF. Consider using colors that result in a contrast ratio greater than 4.50 for small text, or 3.00 for large text. This item may be obscured by  | P6-he-light, P8-he-light, P9-he-light (3) |
| [02-home-saved](shots/P2-en-light/02-home-saved.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 3.99. This ratio is based on an estimated foreground color of #92742E and an estimated background color of #F2F3FA. Consider using colors that result in a contrast ratio greater than 4.50 for small text, or 3.00 for large text. This item may be obscured by  | P2-en-light, P2-he-light (2) |
| [02-home-saved](shots/P5-en-light/02-home-saved.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 4.06. This ratio is based on an estimated foreground color of #73777F and an estimated background color of #F2F3FA. Consider using colors that result in a contrast ratio greater than 4.50 for small text, or 3.00 for large text. This item may be obscured by  | P5-en-light, P5-he-light (2) |
| [08-better-start](shots/P1-he-light/08-better-start.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 4.14. This ratio is based on an estimated foreground color of #D70617 and an estimated background color of #D1E4FF. Consider using colors that result in a contrast ratio greater than 4.50 for small text, or 3.00 for large text. This item may be obscured by  | P1-he-light, P4-he-light (2) |
| [13-stop-departures](shots/P1-en-dark/13-stop-departures.jpg) | DuplicateSpeakableTextCheck | WARNING: This clickable item's speakable text: "Add Train to My lines" is identical to that of 3 other item(s). [android.view.View Rect(572, 840 - 688, 936) "" clickable=true focusable=true srFocusable=true important=true] | P1-en-dark, P1-en-light (2) |
| [13-stop-departures](shots/P1-he-dark/13-stop-departures.jpg) | DuplicateSpeakableTextCheck | WARNING: This clickable item's speakable text: "הוספת ⁨רכבת⁩ לקווים שלי" is identical to that of 3 other item(s). [android.view.View Rect(32, 840 - 148, 936) "" clickable=true focusable=true srFocusable=true important=true] | P1-he-dark, P1-he-light (2) |
| [13-stop-departures](shots/P4-en-dark/13-stop-departures.jpg) | DuplicateSpeakableTextCheck | WARNING: This clickable item's speakable text: "Add Train to My lines" is identical to that of 6 other item(s). [android.view.View Rect(871, 1350 - 1034, 1485) "" clickable=true focusable=true srFocusable=true important=true] | P4-en-dark, P4-en-light (2) |
| [13-stop-departures](shots/P4-he-dark/13-stop-departures.jpg) | DuplicateSpeakableTextCheck | WARNING: This clickable item's speakable text: "הוספת ⁨רכבת⁩ לקווים שלי" is identical to that of 6 other item(s). [android.view.View Rect(46, 1350 - 209, 1485) "" clickable=true focusable=true srFocusable=true important=true] | P4-he-dark, P4-he-light (2) |
| [13-stop-departures](shots/P8-en-dark/13-stop-departures.jpg) | DuplicateSpeakableTextCheck | WARNING: This clickable item's speakable text: "Add Train to My lines" is identical to that of 2 other item(s). [android.view.View Rect(877, 455 - 1029, 580) "" clickable=true focusable=true srFocusable=true important=true] | P8-en-dark, P8-en-light (2) |
| [13-stop-departures](shots/P8-he-dark/13-stop-departures.jpg) | DuplicateSpeakableTextCheck | WARNING: This clickable item's speakable text: "הוספת ⁨רכבת⁩ לקווים שלי" is identical to that of 2 other item(s). [android.view.View Rect(1371, 455 - 1523, 580) "" clickable=true focusable=true srFocusable=true important=true] | P8-he-dark, P8-he-light (2) |
| [14-settings-top](shots/P4-en-dark/14-settings-top.jpg) | DuplicateSpeakableTextCheck | WARNING: This clickable item's speakable text: "Not selected, 10 min" is identical to that of 1 other item(s). [android.view.View Rect(410, 1288 - 625, 1423) "" clickable=true focusable=true srFocusable=true important=true] | P4-en-dark, P4-en-light (2) |
| [14-settings-top](shots/P4-en-dark/14-settings-top.jpg) | DuplicateSpeakableTextCheck | WARNING: This clickable item's speakable text: "Not selected, 5 min" is identical to that of 1 other item(s). [android.view.View Rect(620, 1153 - 813, 1288) "" clickable=true focusable=true srFocusable=true important=true] | P4-en-dark, P4-en-light (2) |
| [14-settings-top](shots/P4-he-dark/14-settings-top.jpg) | DuplicateSpeakableTextCheck | WARNING: This clickable item's speakable text: "לא נבחר, ⁨10⁩ דק׳" is identical to that of 1 other item(s). [android.view.View Rect(495, 1290 - 704, 1425) "" clickable=true focusable=true srFocusable=true important=true] | P4-he-dark, P4-he-light (2) |
| [14-settings-top](shots/P4-he-dark/14-settings-top.jpg) | DuplicateSpeakableTextCheck | WARNING: This clickable item's speakable text: "לא נבחר, ⁨5⁩ דק׳" is identical to that of 1 other item(s). [android.view.View Rect(313, 1155 - 500, 1290) "" clickable=true focusable=true srFocusable=true important=true] | P4-he-dark, P4-he-light (2) |
| [15-settings-bottom](shots/P1-en-dark/15-settings-bottom.jpg) | DuplicateSpeakableTextCheck | WARNING: This clickable item's speakable text: "Delete" is identical to that of 3 other item(s). [android.view.View Rect(536, 548 - 632, 644) "" clickable=true focusable=true srFocusable=true important=true] | P1-en-dark, P1-en-light (2) |
| [15-settings-bottom](shots/P1-en-dark/15-settings-bottom.jpg) | DuplicateSpeakableTextCheck | WARNING: This clickable item's speakable text: "Set as Home" is identical to that of 3 other item(s). [android.view.View Rect(324, 548 - 420, 644) "" clickable=true focusable=true srFocusable=true important=true] | P1-en-dark, P1-en-light (2) |
| [15-settings-bottom](shots/P1-en-dark/15-settings-bottom.jpg) | DuplicateSpeakableTextCheck | WARNING: This clickable item's speakable text: "⏰" is identical to that of 3 other item(s). [android.view.View Rect(420, 548 - 536, 644) "" clickable=true focusable=true srFocusable=true important=true] | P1-en-dark, P1-en-light (2) |
| [15-settings-bottom](shots/P1-he-dark/15-settings-bottom.jpg) | DuplicateSpeakableTextCheck | WARNING: This clickable item's speakable text: "מחיקה" is identical to that of 3 other item(s). [android.view.View Rect(88, 548 - 184, 644) "" clickable=true focusable=true srFocusable=true important=true] | P1-he-dark, P1-he-light (2) |
| [15-settings-bottom](shots/P1-he-dark/15-settings-bottom.jpg) | DuplicateSpeakableTextCheck | WARNING: This clickable item's speakable text: "⏰" is identical to that of 3 other item(s). [android.view.View Rect(184, 548 - 300, 644) "" clickable=true focusable=true srFocusable=true important=true] | P1-he-dark, P1-he-light (2) |
| [15-settings-bottom](shots/P1-he-dark/15-settings-bottom.jpg) | DuplicateSpeakableTextCheck | WARNING: This clickable item's speakable text: "הגדרה כבית" is identical to that of 3 other item(s). [android.view.View Rect(300, 548 - 396, 644) "" clickable=true focusable=true srFocusable=true important=true] | P1-he-dark, P1-he-light (2) |
| [15-settings-bottom](shots/P4-en-dark/15-settings-bottom.jpg) | DuplicateSpeakableTextCheck | WARNING: This clickable item's speakable text: "Delete" is identical to that of 3 other item(s). [android.view.View Rect(787, 1377 - 922, 1512) "" clickable=true focusable=true srFocusable=true important=true] | P4-en-dark, P4-en-light (2) |
| [15-settings-bottom](shots/P4-en-dark/15-settings-bottom.jpg) | DuplicateSpeakableTextCheck | WARNING: This clickable item's speakable text: "Set as Home" is identical to that of 3 other item(s). [android.view.View Rect(489, 1377 - 624, 1512) "" clickable=true focusable=true srFocusable=true important=true] | P4-en-dark, P4-en-light (2) |
| [15-settings-bottom](shots/P4-en-dark/15-settings-bottom.jpg) | DuplicateSpeakableTextCheck | WARNING: This clickable item's speakable text: "⏰" is identical to that of 3 other item(s). [android.view.View Rect(624, 1377 - 787, 1512) "" clickable=true focusable=true srFocusable=true important=true] | P4-en-dark, P4-en-light (2) |
| [15-settings-bottom](shots/P4-he-dark/15-settings-bottom.jpg) | DuplicateSpeakableTextCheck | WARNING: This clickable item's speakable text: "מחיקה" is identical to that of 3 other item(s). [android.view.View Rect(158, 1377 - 293, 1512) "" clickable=true focusable=true srFocusable=true important=true] | P4-he-dark, P4-he-light (2) |
| [15-settings-bottom](shots/P4-he-dark/15-settings-bottom.jpg) | DuplicateSpeakableTextCheck | WARNING: This clickable item's speakable text: "⏰" is identical to that of 3 other item(s). [android.view.View Rect(293, 1377 - 456, 1512) "" clickable=true focusable=true srFocusable=true important=true] | P4-he-dark, P4-he-light (2) |
| [15-settings-bottom](shots/P4-he-dark/15-settings-bottom.jpg) | DuplicateSpeakableTextCheck | WARNING: This clickable item's speakable text: "הגדרה כבית" is identical to that of 3 other item(s). [android.view.View Rect(456, 1377 - 591, 1512) "" clickable=true focusable=true srFocusable=true important=true] | P4-he-dark, P4-he-light (2) |
| [04-trip-results](shots/P2-en-light/04-trip-results.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 2.12. This ratio is based on an estimated foreground color of #A7A9B0 and an estimated background color of #F2F3FA. Consider using colors that result in a contrast ratio greater than 4.50 for small text, or 3.00 for large text. This item may be obscured by  | P2-en-light (1) |
| [05-trip-second-option](shots/P1-en-dark/05-trip-second-option.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 2.00. This ratio is based on an estimated foreground color of #47799E and an estimated background color of #00497D. Consider using colors that result in a contrast ratio greater than 4.50 for small text, or 3.00 for large text. This item may be obscured by  | P1-en-dark (1) |
| [05-trip-second-option](shots/P1-en-light/05-trip-second-option.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 1.36. This ratio is based on an estimated foreground color of #B4C5DD and an estimated background color of #D1E4FF. Consider using colors that result in a contrast ratio greater than 4.50 for small text, or 3.00 for large text. This item may be obscured by  | P1-en-light (1) |
| [05-trip-second-option](shots/P2-en-light/05-trip-second-option.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 2.12. This ratio is based on an estimated foreground color of #A7A9B0 and an estimated background color of #F2F3FA. Consider using colors that result in a contrast ratio greater than 4.50 for small text, or 3.00 for large text. This item may be obscured by  | P2-en-light (1) |
| [06-trip-network-error](shots/P2-en-light/06-trip-network-error.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 2.12. This ratio is based on an estimated foreground color of #A7A9B0 and an estimated background color of #F2F3FA. Consider using colors that result in a contrast ratio greater than 4.50 for small text, or 3.00 for large text. This item may be obscured by  | P2-en-light (1) |
| [07-trip-offline-banner](shots/P2-en-light/07-trip-offline-banner.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 2.12. This ratio is based on an estimated foreground color of #A7A9B0 and an estimated background color of #F2F3FA. Consider using colors that result in a contrast ratio greater than 4.50 for small text, or 3.00 for large text. This item may be obscured by  | P2-en-light (1) |
| [08-better-start](shots/P2-en-light/08-better-start.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 2.12. This ratio is based on an estimated foreground color of #A7A9B0 and an estimated background color of #F2F3FA. Consider using colors that result in a contrast ratio greater than 4.50 for small text, or 3.00 for large text. This item may be obscured by  | P2-en-light (1) |
| [08-better-start](shots/P4-he-dark/08-better-start.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 2.36. This ratio is based on an estimated foreground color of #F92612 and an estimated background color of #00497D. Consider using colors that result in a contrast ratio greater than 4.50 for small text, or 3.00 for large text. This item may be obscured by  | P4-he-dark (1) |
| [09-better-start-none](shots/P2-en-light/09-better-start-none.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 2.12. This ratio is based on an estimated foreground color of #A7A9B0 and an estimated background color of #F2F3FA. Consider using colors that result in a contrast ratio greater than 4.50 for small text, or 3.00 for large text. This item may be obscured by  | P2-en-light (1) |
| [13-stop-departures](shots/P2-en-light/13-stop-departures.jpg) | DuplicateSpeakableTextCheck | WARNING: This clickable item's speakable text: "Add Train to My lines" is identical to that of 3 other item(s). [android.view.View Rect(572, 858 - 688, 954) "" clickable=true focusable=true srFocusable=true important=true] | P2-en-light (1) |
| [13-stop-departures](shots/P2-he-light/13-stop-departures.jpg) | DuplicateSpeakableTextCheck | WARNING: This clickable item's speakable text: "הוספת ⁨רכבת⁩ לקווים שלי" is identical to that of 3 other item(s). [android.view.View Rect(32, 858 - 148, 954) "" clickable=true focusable=true srFocusable=true important=true] | P2-he-light (1) |
| [13-stop-departures](shots/P3-en-light/13-stop-departures.jpg) | DuplicateSpeakableTextCheck | WARNING: This clickable item's speakable text: "Add Train to My lines" is identical to that of 4 other item(s). [android.view.View Rect(830, 1475 - 1026, 1636) "" clickable=true focusable=true srFocusable=true important=true] | P3-en-light (1) |
| [13-stop-departures](shots/P3-he-light/13-stop-departures.jpg) | DuplicateSpeakableTextCheck | WARNING: This clickable item's speakable text: "הוספת ⁨רכבת⁩ לקווים שלי" is identical to that of 4 other item(s). [android.view.View Rect(54, 1475 - 250, 1636) "" clickable=true focusable=true srFocusable=true important=true] | P3-he-light (1) |
| [13-stop-departures](shots/P5-en-light/13-stop-departures.jpg) | DuplicateSpeakableTextCheck | WARNING: This clickable item's speakable text: "Add Train to My lines" is identical to that of 4 other item(s). [android.view.View Rect(871, 1562 - 1034, 1713) "" clickable=true focusable=true srFocusable=true important=true compose: #2292 at Offset(871.0, 1562.0) SemanticsConfiguration@ca29ce6{ mer | P5-en-light (1) |
| [13-stop-departures](shots/P5-he-light/13-stop-departures.jpg) | DuplicateSpeakableTextCheck | WARNING: This clickable item's speakable text: "הוספת ⁨רכבת⁩ לקווים שלי" is identical to that of 4 other item(s). [android.view.View Rect(46, 1566 - 209, 1717) "" clickable=true focusable=true srFocusable=true important=true compose: #2360 at Offset(46.0, 1566.0) SemanticsConfiguration@7bfe9cc{ merg | P5-he-light (1) |
| [13-stop-departures](shots/P6-en-light/13-stop-departures.jpg) | DuplicateSpeakableTextCheck | WARNING: This clickable item's speakable text: "Add Train to My lines" is identical to that of 7 other item(s). [android.view.View Rect(886, 1370 - 1038, 1495) "" clickable=true focusable=true srFocusable=true important=true] | P6-en-light (1) |
| [13-stop-departures](shots/P6-he-light/13-stop-departures.jpg) | DuplicateSpeakableTextCheck | WARNING: This clickable item's speakable text: "הוספת ⁨רכבת⁩ לקווים שלי" is identical to that of 7 other item(s). [android.view.View Rect(42, 1370 - 194, 1495) "" clickable=true focusable=true srFocusable=true important=true] | P6-he-light (1) |
| [13-stop-departures](shots/P7-en-light/13-stop-departures.jpg) | DuplicateSpeakableTextCheck | WARNING: This clickable item's speakable text: "Add Train to My lines" is identical to that of 7 other item(s). [android.view.View Rect(710, 1328 - 862, 1453) "" clickable=true focusable=true srFocusable=true important=true] | P7-en-light (1) |
| [13-stop-departures](shots/P7-he-light/13-stop-departures.jpg) | DuplicateSpeakableTextCheck | WARNING: This clickable item's speakable text: "הוספת ⁨רכבת⁩ לקווים שלי" is identical to that of 7 other item(s). [android.view.View Rect(42, 1328 - 194, 1453) "" clickable=true focusable=true srFocusable=true important=true] | P7-he-light (1) |
| [13-stop-departures](shots/P9-en-light/13-stop-departures.jpg) | DuplicateSpeakableTextCheck | WARNING: This clickable item's speakable text: "Add Train to My lines" is identical to that of 3 other item(s). [android.view.View Rect(877, 1083 - 1029, 1208) "" clickable=true focusable=true srFocusable=true important=true] | P9-en-light (1) |
| [13-stop-departures](shots/P9-he-light/13-stop-departures.jpg) | DuplicateSpeakableTextCheck | WARNING: This clickable item's speakable text: "הוספת ⁨רכבת⁩ לקווים שלי" is identical to that of 3 other item(s). [android.view.View Rect(1179, 1083 - 1331, 1208) "" clickable=true focusable=true srFocusable=true important=true] | P9-he-light (1) |
| [14-settings-top](shots/P2-en-light/14-settings-top.jpg) | DuplicateSpeakableTextCheck | WARNING: This clickable item's speakable text: "Not selected" is identical to that of 1 other item(s). [android.view.View Rect(278, 989 - 437, 1040) "" clickable=true focusable=true srFocusable=true important=true] | P2-en-light (1) |
| [14-settings-top](shots/P3-en-light/14-settings-top.jpg) | DuplicateSpeakableTextCheck | WARNING: This clickable item's speakable text: "Not selected" is identical to that of 1 other item(s). [android.view.View Rect(135, 1968 - 391, 2016) "" clickable=true focusable=true srFocusable=true important=true] | P3-en-light (1) |
| [14-settings-top](shots/P3-en-light/14-settings-top.jpg) | DuplicateSpeakableTextCheck | WARNING: This clickable item's speakable text: "Not selected, 10 min" is identical to that of 1 other item(s). [android.view.View Rect(384, 1542 - 640, 1704) "" clickable=true focusable=true srFocusable=true important=true] | P3-en-light (1) |
| [14-settings-top](shots/P3-en-light/14-settings-top.jpg) | DuplicateSpeakableTextCheck | WARNING: This clickable item's speakable text: "Not selected, 5 min" is identical to that of 1 other item(s). [android.view.View Rect(633, 1380 - 862, 1542) "" clickable=true focusable=true srFocusable=true important=true] | P3-en-light (1) |
| [14-settings-top](shots/P3-he-light/14-settings-top.jpg) | DuplicateSpeakableTextCheck | WARNING: This clickable item's speakable text: "לא נבחר, ⁨10⁩ דק׳" is identical to that of 1 other item(s). [android.view.View Rect(491, 1546 - 740, 1708) "" clickable=true focusable=true srFocusable=true important=true] | P3-he-light (1) |
| [14-settings-top](shots/P3-he-light/14-settings-top.jpg) | DuplicateSpeakableTextCheck | WARNING: This clickable item's speakable text: "לא נבחר, ⁨5⁩ דק׳" is identical to that of 1 other item(s). [android.view.View Rect(276, 1384 - 498, 1546) "" clickable=true focusable=true srFocusable=true important=true] | P3-he-light (1) |
| [14-settings-top](shots/P3-he-light/14-settings-top.jpg) | DuplicateSpeakableTextCheck | WARNING: This clickable item's speakable text: "לא נבחר" is identical to that of 1 other item(s). [android.view.View Rect(733, 1972 - 982, 2016) "" clickable=true focusable=true srFocusable=true important=true] | P3-he-light (1) |
| [14-settings-top](shots/P5-he-light/14-settings-top.jpg) | DuplicateSpeakableTextCheck | WARNING: This clickable item's speakable text: "לא נבחר" is identical to that of 1 other item(s). [android.view.View Rect(647, 1997 - 913, 2053) "" clickable=true focusable=true srFocusable=true important=true] | P5-he-light (1) |
| [14-settings-top](shots/P6-en-light/14-settings-top.jpg) | DuplicateSpeakableTextCheck | WARNING: This clickable item's speakable text: "Not selected" is identical to that of 1 other item(s). [android.view.View Rect(227, 2066 - 391, 2148) "" clickable=true focusable=true srFocusable=true important=true compose: #2826 at Offset(227.0, 2087.0) SemanticsConfiguration@c94b143{ mergeDescenda | P6-en-light (1) |
| [14-settings-top](shots/P6-en-light/14-settings-top.jpg) | DuplicateSpeakableTextCheck | WARNING: This clickable item's speakable text: "Not selected, 10 min" is identical to that of 1 other item(s). [android.view.View Rect(419, 1199 - 615, 1325) "" clickable=true focusable=true srFocusable=true important=true] | P6-en-light (1) |
| [14-settings-top](shots/P6-en-light/14-settings-top.jpg) | DuplicateSpeakableTextCheck | WARNING: This clickable item's speakable text: "Not selected, 5 min" is identical to that of 1 other item(s). [android.view.View Rect(611, 1073 - 787, 1199) "" clickable=true focusable=true srFocusable=true important=true] | P6-en-light (1) |
| [14-settings-top](shots/P6-he-light/14-settings-top.jpg) | DuplicateSpeakableTextCheck | WARNING: This clickable item's speakable text: "לא נבחר, ⁨10⁩ דק׳" is identical to that of 1 other item(s). [android.view.View Rect(504, 1200 - 695, 1326) "" clickable=true focusable=true srFocusable=true important=true] | P6-he-light (1) |
| … | | 51 more | |


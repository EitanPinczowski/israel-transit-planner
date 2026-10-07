# UI tests — claude/search-fix @ 4a40812 (run 37580962446)

29 runs · 438 screenshots · 491 tests passed · **0 failed** · **0 crash logs** · 184 distinct findings

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
| J1 |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  | 1 |  | 1 |  |  |
| R2 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 |  |  |  |  |  |  |  |  |  |  |  |
| R3 |  |  | 5 | 5 | 6 | 16 | 3 | 6 |  |  | 5 | 5 | 12 | 25 |  | 10 |  | 11 | 5 | 5 | 15 | 15 | 3 | 13 |  |  |  |  |  |
| R4 | 1 | 1 | 1 | 1 | 1 | 1 | 2 | 4 | 3 | 3 | 3 | 3 | 10 | 7 | 5 | 3 | 5 | 2 | 2 | 2 | 2 | 2 | 2 | 2 |  |  |  |  |  |
| R5 | 13 | 13 | 13 | 14 | 22 | 14 | 16 | 15 | 17 | 17 | 17 | 17 | 14 | 14 | 16 | 17 | 18 | 17 | 13 | 13 | 13 | 14 | 13 | 14 |  |  |  |  |  |

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

## R3 — text clipped, squeezed or a time/price cut off (95)

| state | where | detail | runs (n) |
|---|---|---|---|
| [04-trip-results](shots/P6-he-light/04-trip-results.jpg) | "המהירה" | text is clipped: box 47×20 dp, text 49×20 dp, 1 lines (width) | P6-he-light, P7-he-light, P8-he-dark, P8-he-light, P9-he-light (5) |
| [04-trip-results](shots/P6-he-light/04-trip-results.jpg) | "מעט החלפות" | text is clipped: box 78×20 dp, text 98×20 dp, 1 lines (width) | P6-he-light, P7-he-light, P8-he-dark, P8-he-light, P9-he-light (5) |
| [05-trip-second-option](shots/P6-he-light/05-trip-second-option.jpg) | "המהירה" | text is clipped: box 47×20 dp, text 49×20 dp, 1 lines (width) | P6-he-light, P7-he-light, P8-he-dark, P8-he-light, P9-he-light (5) |
| [05-trip-second-option](shots/P6-he-light/05-trip-second-option.jpg) | "מעט החלפות" | text is clipped: box 78×20 dp, text 98×20 dp, 1 lines (width) | P6-he-light, P7-he-light, P8-he-dark, P8-he-light, P9-he-light (5) |
| [07-trip-offline-banner](shots/P6-he-light/07-trip-offline-banner.jpg) | "המהירה" | text is clipped: box 47×20 dp, text 49×20 dp, 1 lines (width) | P6-he-light, P7-he-light, P8-he-dark, P8-he-light, P9-he-light (5) |
| [07-trip-offline-banner](shots/P6-he-light/07-trip-offline-banner.jpg) | "מעט החלפות" | text is clipped: box 78×20 dp, text 98×20 dp, 1 lines (width) | P6-he-light, P7-he-light, P8-he-dark, P8-he-light, P9-he-light (5) |
| [17-update-banner-results](shots/P6-he-light/17-update-banner-results.jpg) | "המהירה" | text is clipped: box 47×20 dp, text 49×20 dp, 1 lines (width) | P6-he-light, P7-he-light, P8-he-dark, P8-he-light, P9-he-light (5) |
| [17-update-banner-results](shots/P6-he-light/17-update-banner-results.jpg) | "מעט החלפות" | text is clipped: box 78×20 dp, text 98×20 dp, 1 lines (width) | P6-he-light, P7-he-light, P8-he-dark, P8-he-light, P9-he-light (5) |
| [18-save-trip-dialog](shots/P6-he-light/18-save-trip-dialog.jpg) | "המהירה" | text is clipped: box 47×20 dp, text 49×20 dp, 1 lines (width) | P6-he-light, P7-he-light, P8-he-dark, P8-he-light, P9-he-light (5) |
| [18-save-trip-dialog](shots/P6-he-light/18-save-trip-dialog.jpg) | "מעט החלפות" | text is clipped: box 78×20 dp, text 98×20 dp, 1 lines (width) | P6-he-light, P7-he-light, P8-he-dark, P8-he-light, P9-he-light (5) |
| [03-search-keyboard](shots/P8-en-dark/03-search-keyboard.jpg) | "תל" | text is clipped: box 191×0 dp, text 20×24 dp, 1 lines (height) | P8-en-dark, P8-en-light, P8-he-dark, P8-he-light (4) |
| [18-save-trip-dialog](shots/P8-en-dark/18-save-trip-dialog.jpg) | "Routes" | text is clipped: box 52×0 dp, text 51×24 dp, 1 lines (height) | P8-en-dark, P8-en-light, P9-en-light (3) |
| [18-save-trip-dialog](shots/P8-en-dark/18-save-trip-dialog.jpg) | "Updated 07:55" | text is clipped: box 76×0 dp, text 76×16 dp, 1 lines (height) | P8-en-dark, P8-en-light, P9-en-light (3) |
| [18-save-trip-dialog](shots/P8-he-dark/18-save-trip-dialog.jpg) | "מסלולים" | text is clipped: box 59×0 dp, text 58×24 dp, 1 lines (height) | P8-he-dark, P8-he-light, P9-he-light (3) |
| [18-save-trip-dialog](shots/P8-he-dark/18-save-trip-dialog.jpg) | "עודכן ב־⁨07:55⁩" | text is clipped: box 70×0 dp, text 70×16 dp, 1 lines (height) | P8-he-dark, P8-he-light, P9-he-light (3) |
| [03-search-keyboard](shots/P8-en-dark/03-search-keyboard.jpg) | "Routing: Transitous · Map © OpenStreetMap, OpenFreeMap" | text is clipped: box 309×0 dp, text 308×16 dp, 1 lines (height) | P8-en-dark, P8-en-light (2) |
| [03-search-keyboard](shots/P8-he-dark/03-search-keyboard.jpg) | "ניתוב: Transitous · מפה © OpenStreetMap, OpenFreeMap" | text is clipped: box 293×0 dp, text 293×16 dp, 1 lines (height) | P8-he-dark, P8-he-light (2) |
| [04-trip-results](shots/P1-he-dark/04-trip-results.jpg) | "המהירה" | text is clipped: box 49×20 dp, text 64×20 dp, 1 lines (width) | P1-he-dark, P1-he-light (2) |
| [04-trip-results](shots/P2-he-light/04-trip-results.jpg) | "⁨5⁩ ב־⁨08:04⁩ מ־⁨מרפאות חוץ סורוקה/אוניברסיטת בן גוריון⁩" | squeezed: 2 lines in 0 dp | P2-he-light, P5-he-light (2) |
| [04-trip-results](shots/P4-he-dark/04-trip-results.jpg) | "מעט החלפות" | text is clipped: box 79×20 dp, text 91×20 dp, 1 lines (width) | P4-he-dark, P4-he-light (2) |
| [05-trip-second-option](shots/P1-he-dark/05-trip-second-option.jpg) | "המהירה" | text is clipped: box 49×20 dp, text 64×20 dp, 1 lines (width) | P1-he-dark, P1-he-light (2) |
| [05-trip-second-option](shots/P4-he-dark/05-trip-second-option.jpg) | "מעט החלפות" | text is clipped: box 79×20 dp, text 91×20 dp, 1 lines (width) | P4-he-dark, P4-he-light (2) |
| [07-trip-offline-banner](shots/P1-he-dark/07-trip-offline-banner.jpg) | "המהירה" | text is clipped: box 49×20 dp, text 64×20 dp, 1 lines (width) | P1-he-dark, P1-he-light (2) |
| [07-trip-offline-banner](shots/P2-en-light/07-trip-offline-banner.jpg) | "5 at 08:04 from ⁨מרפאות חוץ סורוקה/אוניברסיטת בן גוריון⁩" | squeezed: 2 lines in 0 dp | P2-en-light, P5-en-light (2) |
| [07-trip-offline-banner](shots/P2-he-light/07-trip-offline-banner.jpg) | "⁨5⁩ ב־⁨08:04⁩ מ־⁨מרפאות חוץ סורוקה/אוניברסיטת בן גוריון⁩" | squeezed: 2 lines in 0 dp | P2-he-light, P5-he-light (2) |
| [07-trip-offline-banner](shots/P4-he-dark/07-trip-offline-banner.jpg) | "מעט החלפות" | text is clipped: box 79×20 dp, text 91×20 dp, 1 lines (width) | P4-he-dark, P4-he-light (2) |
| [17-update-banner-results](shots/P1-he-dark/17-update-banner-results.jpg) | "המהירה" | text is clipped: box 49×20 dp, text 64×20 dp, 1 lines (width) | P1-he-dark, P1-he-light (2) |
| [17-update-banner-results](shots/P2-he-light/17-update-banner-results.jpg) | "⁨5⁩ ב־⁨08:04⁩ מ־⁨מרפאות חוץ סורוקה/אוניברסיטת בן גוריון⁩" | squeezed: 2 lines in 0 dp | P2-he-light, P5-he-light (2) |
| [17-update-banner-results](shots/P4-he-dark/17-update-banner-results.jpg) | "מעט החלפות" | text is clipped: box 79×20 dp, text 91×20 dp, 1 lines (width) | P4-he-dark, P4-he-light (2) |
| [18-save-trip-dialog](shots/P1-he-dark/18-save-trip-dialog.jpg) | "המהירה" | text is clipped: box 49×20 dp, text 64×20 dp, 1 lines (width) | P1-he-dark, P1-he-light (2) |
| [18-save-trip-dialog](shots/P2-he-light/18-save-trip-dialog.jpg) | "⁨5⁩ ב־⁨08:04⁩ מ־⁨מרפאות חוץ סורוקה/אוניברסיטת בן גוריון⁩" | squeezed: 2 lines in 0 dp | P2-he-light, P5-he-light (2) |
| [18-save-trip-dialog](shots/P4-he-dark/18-save-trip-dialog.jpg) | "מעט החלפות" | text is clipped: box 79×20 dp, text 91×20 dp, 1 lines (width) | P4-he-dark, P4-he-light (2) |
| [18-save-trip-dialog](shots/P8-en-dark/18-save-trip-dialog.jpg) | "Routing: Transitous · Map © OpenStreetMap, OpenFreeMap" | text is clipped: box 309×2 dp, text 308×16 dp, 1 lines (height) | P8-en-dark, P8-en-light (2) |
| [18-save-trip-dialog](shots/P8-he-dark/18-save-trip-dialog.jpg) | "ניתוב: Transitous · מפה © OpenStreetMap, OpenFreeMap" | text is clipped: box 293×2 dp, text 293×16 dp, 1 lines (height) | P8-he-dark, P8-he-light (2) |
| [04-trip-results](shots/P2-en-light/04-trip-results.jpg) | "5 at 08:04 from ⁨מרפאות חוץ סורוקה/אוניברסיטת בן גוריון⁩" | squeezed: 2 lines in 0 dp | P2-en-light (1) |
| [04-trip-results](shots/P2-he-light/04-trip-results.jpg) | "המהירה" | text is clipped: box 63×27 dp, text 64×27 dp, 1 lines (width) | P2-he-light (1) |
| [04-trip-results](shots/P2-he-light/04-trip-results.jpg) | "מעט החלפות" | text is clipped: box 105×27 dp, text 128×27 dp, 1 lines (width) | P2-he-light (1) |
| [04-trip-results](shots/P3-he-light/04-trip-results.jpg) | "מעט הליכה" | text is clipped: box 67×20 dp, text 76×20 dp, 1 lines (width) | P3-he-light (1) |
| [04-trip-results](shots/P5-en-light/04-trip-results.jpg) | "If I miss this: next at 08:34 (+11 min)" | squeezed: 2 lines in 0 dp | P5-en-light (1) |
| [04-trip-results](shots/P5-he-light/04-trip-results.jpg) | "המהירה" | text is clipped: box 89×37 dp, text 91×37 dp, 1 lines (width) | P5-he-light (1) |
| [04-trip-results](shots/P5-he-light/04-trip-results.jpg) | "מעט הליכה" | text is clipped: box 125×37 dp, text 137×37 dp, 1 lines (width) | P5-he-light (1) |
| [04-trip-results](shots/P5-he-light/04-trip-results.jpg) | "אם אפספס: הבא ב־⁨08:34⁩ (‎+⁨11⁩ דק׳)" | squeezed: 2 lines in 0 dp | P5-he-light (1) |
| [05-trip-second-option](shots/P2-he-light/05-trip-second-option.jpg) | "המהירה" | text is clipped: box 63×27 dp, text 64×27 dp, 1 lines (width) | P2-he-light (1) |
| [05-trip-second-option](shots/P2-he-light/05-trip-second-option.jpg) | "מעט החלפות" | text is clipped: box 105×27 dp, text 128×27 dp, 1 lines (width) | P2-he-light (1) |
| [05-trip-second-option](shots/P3-he-light/05-trip-second-option.jpg) | "מעט הליכה" | text is clipped: box 67×20 dp, text 76×20 dp, 1 lines (width) | P3-he-light (1) |
| [05-trip-second-option](shots/P5-he-light/05-trip-second-option.jpg) | "המהירה" | text is clipped: box 89×37 dp, text 91×37 dp, 1 lines (width) | P5-he-light (1) |
| [05-trip-second-option](shots/P5-he-light/05-trip-second-option.jpg) | "מעט הליכה" | text is clipped: box 125×37 dp, text 137×37 dp, 1 lines (width) | P5-he-light (1) |
| [07-trip-offline-banner](shots/P2-he-light/07-trip-offline-banner.jpg) | "המהירה" | text is clipped: box 63×27 dp, text 64×27 dp, 1 lines (width) | P2-he-light (1) |
| [07-trip-offline-banner](shots/P2-he-light/07-trip-offline-banner.jpg) | "מעט החלפות" | text is clipped: box 105×27 dp, text 128×27 dp, 1 lines (width) | P2-he-light (1) |
| [07-trip-offline-banner](shots/P3-he-light/07-trip-offline-banner.jpg) | "מעט הליכה" | text is clipped: box 67×20 dp, text 76×20 dp, 1 lines (width) | P3-he-light (1) |
| [07-trip-offline-banner](shots/P5-en-light/07-trip-offline-banner.jpg) | "If I miss this: next at 08:34 (+11 min)" | squeezed: 2 lines in 0 dp | P5-en-light (1) |
| [07-trip-offline-banner](shots/P5-he-light/07-trip-offline-banner.jpg) | "המהירה" | text is clipped: box 89×37 dp, text 91×37 dp, 1 lines (width) | P5-he-light (1) |
| [07-trip-offline-banner](shots/P5-he-light/07-trip-offline-banner.jpg) | "מעט הליכה" | text is clipped: box 125×37 dp, text 137×37 dp, 1 lines (width) | P5-he-light (1) |
| [07-trip-offline-banner](shots/P5-he-light/07-trip-offline-banner.jpg) | "הקישו על קו כדי לראות את כל התחנות שלו" | squeezed: 2 lines in 0 dp | P5-he-light (1) |
| [07-trip-offline-banner](shots/P5-he-light/07-trip-offline-banner.jpg) | "אם אפספס: הבא ב־⁨08:34⁩ (‎+⁨11⁩ דק׳)" | squeezed: 2 lines in 0 dp | P5-he-light (1) |
| [08-better-start](shots/P2-en-light/08-better-start.jpg) | "07:55–09:27" | squeezed: 3 lines in 56 dp | P2-en-light (1) |
| [08-better-start](shots/P2-he-light/08-better-start.jpg) | "07:55–09:27" | squeezed: 2 lines in 85 dp | P2-he-light (1) |
| [08-better-start](shots/P3-en-light/08-better-start.jpg) | "07:55–09:27" | squeezed: 2 lines in 76 dp | P3-en-light (1) |
| [08-better-start](shots/P5-en-light/08-better-start.jpg) | "07:55–09:27" | text is clipped: box 0×465 dp, text 18×465 dp, 11 lines (width) | P5-en-light (1) |
| [08-better-start](shots/P5-en-light/08-better-start.jpg) | "07:55–09:27" | squeezed: 11 lines in 0 dp | P5-en-light (1) |
| … | | 35 more | |

## R4 — touch target under 48×48 dp (45)

| state | where | detail | runs (n) |
|---|---|---|---|
| [13-stop-departures](shots/P1-en-dark/13-stop-departures.jpg) | "☆" | 58×28 dp; its 48 dp tap area reaches "☆" | P1-en-dark, P1-en-light, P1-he-dark, P1-he-light (4) |
| [13-stop-departures](shots/P4-en-dark/13-stop-departures.jpg) | "☆" | 58×24 dp; its 48 dp tap area reaches "☆" | P4-en-dark, P4-en-light, P4-he-dark, P4-he-light (4) |
| [03-search-keyboard](shots/P8-en-dark/03-search-keyboard.jpg) | "node#334" | 259×17 dp; its 48 dp tap area reaches "From My location" | P8-en-dark, P8-en-light (2) |
| [03-search-keyboard](shots/P8-en-dark/03-search-keyboard.jpg) | "Cancel" | 40×17 dp; its 48 dp tap area reaches "From My location" | P8-en-dark, P8-en-light (2) |
| [03-search-keyboard](shots/P8-he-dark/03-search-keyboard.jpg) | "node#334" | 259×17 dp; its 48 dp tap area reaches "מ־ המיקום שלי" | P8-he-dark, P8-he-light (2) |
| [03-search-keyboard](shots/P8-he-dark/03-search-keyboard.jpg) | "ביטול" | 40×17 dp; its 48 dp tap area reaches "מ־ המיקום שלי" | P8-he-dark, P8-he-light (2) |
| [04-trip-results](shots/P5-en-light/04-trip-results.jpg) | "5" | 26×36 dp; its 48 dp tap area reaches "171" | P5-en-light, P5-he-light (2) |
| [04-trip-results](shots/P5-en-light/04-trip-results.jpg) | "171" | 55×36 dp; its 48 dp tap area reaches "5" | P5-en-light, P5-he-light (2) |
| [07-trip-offline-banner](shots/P5-en-light/07-trip-offline-banner.jpg) | "5" | 26×36 dp; its 48 dp tap area reaches "171" | P5-en-light, P5-he-light (2) |
| [11-drop-off-results](shots/P4-en-dark/11-drop-off-results.jpg) | "🚌 Transit from the start, no ride You arrive 10:50 2 transf" | 352×14 dp; its 48 dp tap area reaches "🚗 Get out at ⁨מזכרת בתיה⁩ · driver +4 min You arrive 10:41 " | P4-en-dark, P4-en-light (2) |
| [11-drop-off-results](shots/P4-he-dark/11-drop-off-results.jpg) | "🚌 תחבורה ציבורית מההתחלה, בלי הסעה הגעה ב־⁨10:50⁩ ⁨2⁩ החלפו" | 352×14 dp; its 48 dp tap area reaches "🚗 לרדת ב⁨מזכרת בתיה⁩ · לנהג/ת ‎+⁨4⁩ דק׳ הגעה ב־⁨10:41⁩ ⁨2⁩ " | P4-he-dark, P4-he-light (2) |
| [12-pick-up](shots/P4-en-dark/12-pick-up.jpg) | "🚗 Get picked up at ⁨צומת דבירה⁩ at 09:41 Home at 10:04 save" | 352×18 dp; its 48 dp tap area reaches "🚗 Get picked up at ⁨מרכז אורן/יצחק רגר⁩ at 09:34 Home at 09" | P4-en-dark, P4-en-light (2) |
| [12-pick-up](shots/P4-he-dark/12-pick-up.jpg) | "🚗 איסוף ב⁨צומת דבירה⁩ ב־⁨09:41⁩ בבית ב־⁨10:04⁩ חוסך ⁨46⁩ דק" | 352×18 dp; its 48 dp tap area reaches "🚗 איסוף ב⁨מרכז אורן/יצחק רגר⁩ ב־⁨09:34⁩ בבית ב־⁨09:54⁩ חוסך" | P4-he-dark, P4-he-light (2) |
| [13-stop-departures](shots/P2-en-light/13-stop-departures.jpg) | "☆" | 58×23 dp; its 48 dp tap area reaches "☆" | P2-en-light, P2-he-light (2) |
| [13-stop-departures](shots/P6-en-light/13-stop-departures.jpg) | "☆" | 58×16 dp; its 48 dp tap area reaches "☆" | P6-en-light, P6-he-light (2) |
| [13-stop-departures](shots/P7-en-light/13-stop-departures.jpg) | "☆" | 58×0 dp; its 48 dp tap area reaches "☆" | P7-en-light, P7-he-light (2) |
| [13-stop-departures](shots/P9-en-light/13-stop-departures.jpg) | "☆" | 58×4 dp; its 48 dp tap area reaches "☆" | P9-en-light, P9-he-light (2) |
| [17-update-banner-results](shots/P5-en-light/17-update-banner-results.jpg) | "5" | 26×36 dp; its 48 dp tap area reaches "171" | P5-en-light, P5-he-light (2) |
| [17-update-banner-results](shots/P5-en-light/17-update-banner-results.jpg) | "171" | 55×36 dp; its 48 dp tap area reaches "5" | P5-en-light, P5-he-light (2) |
| [03-search-keyboard](shots/P6-en-light/03-search-keyboard.jpg) | "הגר באר שבע" | 387×18 dp; its 48 dp tap area reaches "הגר באר שבע" | P6-en-light (1) |
| [04-trip-results](shots/P6-en-light/04-trip-results.jpg) | "Mon 08:21–09:46 85 min Walk 13′ Train Walk 1′ ⁨≈ ₪30.5⁩ Trai" | 379×33 dp; its 48 dp tap area reaches "Mon 08:00–09:35 95 min Walk 4′ Walk 2′ Walk 5′ Walk 7′ ⁨≈ ₪2" | P6-en-light (1) |
| [04-trip-results](shots/P6-he-light/04-trip-results.jpg) | "יום ב׳ ⁨08:21⁩–09:46 ⁨85⁩ דק׳ הליכה 13′ רכבת הליכה 1′ ⁨≈ ₪30" | 379×33 dp; its 48 dp tap area reaches "יום ב׳ ⁨08:00⁩–09:35 ⁨95⁩ דק׳ הליכה 4′ הליכה 2′ הליכה 5′ הלי" | P6-he-light (1) |
| [04-trip-results](shots/P7-en-light/04-trip-results.jpg) | "Buzz before my stop" | 178×7 dp; its 48 dp tap area reaches "Remind me to leave" | P7-en-light (1) |
| [05-trip-second-option](shots/P3-en-light/05-trip-second-option.jpg) | "Mon 08:21–09:46 85 min Walk 13′ Walk 1′ ⁨≈ ₪30.5⁩ Tap a line" | 288×25 dp; its 48 dp tap area reaches "Mon 08:00–09:35 95 min Walk 4′ 5 Walk 2′ 370 Walk 5′ 171 Wal" | P3-en-light (1) |
| [05-trip-second-option](shots/P3-he-light/05-trip-second-option.jpg) | "יום ב׳ ⁨08:21⁩–09:46 ⁨85⁩ דק׳ הליכה 13′ הליכה 1′ ⁨≈ ₪30.5⁩ ה" | 288×24 dp; its 48 dp tap area reaches "יום ב׳ ⁨08:00⁩–09:35 ⁨95⁩ דק׳ הליכה 4′ 5 הליכה 2′ 370 הליכה " | P3-he-light (1) |
| [07-trip-offline-banner](shots/P5-en-light/07-trip-offline-banner.jpg) | "171" | 55×25 dp; its 48 dp tap area reaches "5" | P5-en-light (1) |
| [07-trip-offline-banner](shots/P5-he-light/07-trip-offline-banner.jpg) | "171" | 55×24 dp; its 48 dp tap area reaches "5" | P5-he-light (1) |
| [07-trip-offline-banner](shots/P6-en-light/07-trip-offline-banner.jpg) | "Mon 08:21–09:46 85 min Walk 13′ Train Walk 1′ ⁨≈ ₪30.5⁩ Trai" | 379×33 dp; its 48 dp tap area reaches "Mon 08:00–09:35 95 min Walk 4′ Walk 2′ Walk 5′ Walk 7′ ⁨≈ ₪2" | P6-en-light (1) |
| [07-trip-offline-banner](shots/P7-en-light/07-trip-offline-banner.jpg) | "Mon 08:21–09:46 85 min Walk 13′ Train Walk 1′ ⁨≈ ₪30.5⁩ Trai" | 312×1 dp; its 48 dp tap area reaches "Mon 08:00–09:35 95 min Walk 4′ Walk 2′ Walk 5′ Walk 7′ ⁨≈ ₪2" | P7-en-light (1) |
| [07-trip-offline-banner](shots/P7-en-light/07-trip-offline-banner.jpg) | "Mon 08:21–09:46 85 min Walk 13′ Train Walk 1′ ⁨≈ ₪30.5⁩ Trai" | 312×1 dp; its 48 dp tap area reaches "Walking directions" | P7-en-light (1) |
| [07-trip-offline-banner](shots/P7-he-light/07-trip-offline-banner.jpg) | "יום ב׳ ⁨08:21⁩–09:46 ⁨85⁩ דק׳ הליכה 13′ רכבת הליכה 1′ ⁨≈ ₪30" | 312×17 dp; its 48 dp tap area reaches "יום ב׳ ⁨08:00⁩–09:35 ⁨95⁩ דק׳ הליכה 4′ הליכה 2′ הליכה 5′ הלי" | P7-he-light (1) |
| [11-drop-off-results](shots/P3-en-light/11-drop-off-results.jpg) | "🚗 Get out at ⁨מזכרת בתיה⁩ · driver +4 min You arrive 10:41 " | 288×3 dp; its 48 dp tap area reaches "🚗 Get out at ⁨קרית מלאכי⁩ · driver +4 min You arrive 10:30 " | P3-en-light (1) |
| [11-drop-off-results](shots/P3-he-light/11-drop-off-results.jpg) | "🚗 לרדת ב⁨מזכרת בתיה⁩ · לנהג/ת ‎+⁨4⁩ דק׳ הגעה ב־⁨10:41⁩ ⁨2⁩ " | 288×2 dp; its 48 dp tap area reaches "🚗 לרדת ב⁨קרית מלאכי⁩ · לנהג/ת ‎+⁨4⁩ דק׳ הגעה ב־⁨10:30⁩ ⁨2⁩ " | P3-he-light (1) |
| [11-drop-off-results](shots/P3-he-light/11-drop-off-results.jpg) | "🚗 לרדת ב⁨מזכרת בתיה⁩ · לנהג/ת ‎+⁨4⁩ דק׳ הגעה ב־⁨10:41⁩ ⁨2⁩ " | 288×2 dp; its 48 dp tap area reaches "רטט לפני התחנה שלי" | P3-he-light (1) |
| [12-pick-up](shots/P3-he-light/12-pick-up.jpg) | "🚗 איסוף ב⁨מרכז אורן/יצחק רגר⁩ ב־⁨09:34⁩ בבית ב־⁨09:54⁩ חוסך" | 288×23 dp; its 48 dp tap area reaches "🚗 איסוף ב⁨להבים רהט⁩ ב־⁨09:22⁩ בבית ב־⁨09:46⁩ חוסך ⁨63⁩ דק׳" | P3-he-light (1) |
| [12-pick-up](shots/P9-en-light/12-pick-up.jpg) | "🚗 Get picked up at ⁨מרכז אורן/יצחק רגר⁩ at 09:34 Home at 09" | 364×16 dp; its 48 dp tap area reaches "🚗 Get picked up at ⁨להבים רהט⁩ at 09:22 Home at 09:46 saves" | P9-en-light (1) |
| [12-pick-up](shots/P9-he-light/12-pick-up.jpg) | "🚗 איסוף ב⁨מרכז אורן/יצחק רגר⁩ ב־⁨09:34⁩ בבית ב־⁨09:54⁩ חוסך" | 364×16 dp; its 48 dp tap area reaches "🚗 איסוף ב⁨להבים רהט⁩ ב־⁨09:22⁩ בבית ב־⁨09:46⁩ חוסך ⁨63⁩ דק׳" | P9-he-light (1) |
| [13-stop-departures](shots/P5-en-light/13-stop-departures.jpg) | "☆" | 58×9 dp; its 48 dp tap area reaches "☆" | P5-en-light (1) |
| [13-stop-departures](shots/P5-he-light/13-stop-departures.jpg) | "☆" | 58×7 dp; its 48 dp tap area reaches "☆" | P5-he-light (1) |
| [14-settings-top](shots/P5-en-light/14-settings-top.jpg) | "15 min" | 114×2 dp; its 48 dp tap area reaches "5 min" | P5-en-light (1) |
| [14-settings-top](shots/P5-en-light/14-settings-top.jpg) | "15 min" | 114×2 dp; its 48 dp tap area reaches "10 min" | P5-en-light (1) |
| [14-settings-top](shots/P5-en-light/14-settings-top.jpg) | "20 min" | 114×2 dp; its 48 dp tap area reaches "10 min" | P5-en-light (1) |
| [17-update-banner-results](shots/P6-en-light/17-update-banner-results.jpg) | "Mon 08:21–09:46 85 min Walk 13′ Train Walk 1′ ⁨≈ ₪30.5⁩ Trai" | 379×33 dp; its 48 dp tap area reaches "Mon 08:00–09:35 95 min Walk 4′ Walk 2′ Walk 5′ Walk 7′ ⁨≈ ₪2" | P6-en-light (1) |
| [17-update-banner-results](shots/P6-he-light/17-update-banner-results.jpg) | "יום ב׳ ⁨08:21⁩–09:46 ⁨85⁩ דק׳ הליכה 13′ רכבת הליכה 1′ ⁨≈ ₪30" | 379×33 dp; its 48 dp tap area reaches "יום ב׳ ⁨08:00⁩–09:35 ⁨95⁩ דק׳ הליכה 4′ הליכה 2′ הליכה 5′ הלי" | P6-he-light (1) |
| [17-update-banner-results](shots/P7-en-light/17-update-banner-results.jpg) | "Buzz before my stop" | 178×7 dp; its 48 dp tap area reaches "Remind me to leave" | P7-en-light (1) |

## R5 — Accessibility Test Framework (40)

| state | where | detail | runs (n) |
|---|---|---|---|
| [04-trip-results](shots/P1-en-dark/04-trip-results.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 3.87. This ratio is based on an estimated foreground color of #FFFFFF and an estimated background color of #D55E00. Consider using colors that result in a contrast ratio greater than 4.50 for small | P1-en-dark, P1-en-light, P1-he-dark, P1-he-light, P2-en-light, P2-he-light … (24) |
| [04-trip-results](shots/P1-en-dark/04-trip-results.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 3.42. This ratio is based on an estimated foreground color of #FFFFFF and an estimated background color of #009E73. Consider using colors that result in a contrast ratio greater than 4.50 for small | P1-en-dark, P1-en-light, P1-he-dark, P1-he-light, P2-en-light, P2-he-light … (24) |
| [05-trip-second-option](shots/P1-en-dark/05-trip-second-option.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 3.87. This ratio is based on an estimated foreground color of #FFFFFF and an estimated background color of #D55E00. Consider using colors that result in a contrast ratio greater than 4.50 for small | P1-en-dark, P1-en-light, P1-he-dark, P1-he-light, P2-en-light, P2-he-light … (24) |
| [05-trip-second-option](shots/P1-en-dark/05-trip-second-option.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 3.42. This ratio is based on an estimated foreground color of #FFFFFF and an estimated background color of #009E73. Consider using colors that result in a contrast ratio greater than 4.50 for small | P1-en-dark, P1-en-light, P1-he-dark, P1-he-light, P2-en-light, P2-he-light … (24) |
| [11-drop-off-results](shots/P1-en-dark/11-drop-off-results.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 3.87. This ratio is based on an estimated foreground color of #FFFFFF and an estimated background color of #D55E00. Consider using colors that result in a contrast ratio greater than 4.50 for small | P1-en-dark, P1-en-light, P1-he-dark, P1-he-light, P2-en-light, P2-he-light … (24) |
| [11-drop-off-results](shots/P1-en-dark/11-drop-off-results.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 3.42. This ratio is based on an estimated foreground color of #FFFFFF and an estimated background color of #009E73. Consider using colors that result in a contrast ratio greater than 4.50 for small | P1-en-dark, P1-en-light, P1-he-dark, P1-he-light, P2-en-light, P2-he-light … (24) |
| [17-update-banner-results](shots/P1-en-dark/17-update-banner-results.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 3.87. This ratio is based on an estimated foreground color of #FFFFFF and an estimated background color of #D55E00. Consider using colors that result in a contrast ratio greater than 4.50 for small | P1-en-dark, P1-en-light, P1-he-dark, P1-he-light, P2-en-light, P2-he-light … (24) |
| [17-update-banner-results](shots/P1-en-dark/17-update-banner-results.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 3.42. This ratio is based on an estimated foreground color of #FFFFFF and an estimated background color of #009E73. Consider using colors that result in a contrast ratio greater than 4.50 for small | P1-en-dark, P1-en-light, P1-he-dark, P1-he-light, P2-en-light, P2-he-light … (24) |
| [07-trip-offline-banner](shots/P1-en-dark/07-trip-offline-banner.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 3.87. This ratio is based on an estimated foreground color of #FFFFFF and an estimated background color of #D55E00. Consider using colors that result in a contrast ratio greater than 4.50 for small | P1-en-dark, P1-en-light, P1-he-dark, P1-he-light, P2-en-light, P2-he-light … (20) |
| [07-trip-offline-banner](shots/P1-en-dark/07-trip-offline-banner.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 3.42. This ratio is based on an estimated foreground color of #FFFFFF and an estimated background color of #009E73. Consider using colors that result in a contrast ratio greater than 4.50 for small | P1-en-dark, P1-en-light, P1-he-dark, P1-he-light, P3-en-light, P3-he-light … (18) |
| [18-save-trip-dialog](shots/P1-en-dark/18-save-trip-dialog.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 3.87. This ratio is based on an estimated foreground color of #FFFFFF and an estimated background color of #D55E00. Consider using colors that result in a contrast ratio greater than 4.50 for small | P1-en-dark, P1-en-light, P1-he-dark, P1-he-light, P2-en-light, P2-he-light … (18) |
| [18-save-trip-dialog](shots/P1-en-dark/18-save-trip-dialog.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 3.42. This ratio is based on an estimated foreground color of #FFFFFF and an estimated background color of #009E73. Consider using colors that result in a contrast ratio greater than 4.50 for small | P1-en-dark, P1-en-light, P1-he-dark, P1-he-light, P2-en-light, P2-he-light … (18) |
| [03-search-keyboard](shots/P4-en-dark/03-search-keyboard.jpg) | SpeakableTextPresentCheck | ERROR: This item may not have a label readable by screen readers. | P4-en-dark, P4-en-light, P7-en-light, P7-he-light, P8-en-dark, P8-en-light … (8) |
| [12-pick-up](shots/P4-en-dark/12-pick-up.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 3.87. This ratio is based on an estimated foreground color of #FFFFFF and an estimated background color of #D55E00. Consider using colors that result in a contrast ratio greater than 4.50 for small | P4-en-dark, P4-en-light, P4-he-dark, P4-he-light, P6-en-light, P6-he-light … (8) |
| [07-trip-offline-banner](shots/P2-en-light/07-trip-offline-banner.jpg) | SpeakableTextPresentCheck | ERROR: This item may not have a label readable by screen readers. | P2-en-light, P2-he-light, P7-en-light, P8-en-dark, P8-en-light, P8-he-dark … (7) |
| [13-stop-departures](shots/P1-en-dark/13-stop-departures.jpg) | DuplicateSpeakableTextCheck | WARNING: This clickable item's speakable text: "☆" is identical to that of 3 other item(s). | P1-en-dark, P1-en-light, P1-he-dark, P1-he-light, P2-en-light, P2-he-light (6) |
| [13-stop-departures](shots/P4-en-dark/13-stop-departures.jpg) | DuplicateSpeakableTextCheck | WARNING: This clickable item's speakable text: "☆" is identical to that of 6 other item(s). | P4-en-dark, P4-en-light, P4-he-dark, P4-he-light, P7-en-light, P7-he-light (6) |
| [13-stop-departures](shots/P8-en-dark/13-stop-departures.jpg) | DuplicateSpeakableTextCheck | WARNING: This clickable item's speakable text: "☆" is identical to that of 2 other item(s). | P8-en-dark, P8-en-light, P8-he-dark, P8-he-light, P9-en-light, P9-he-light (6) |
| [12-pick-up](shots/P6-he-light/12-pick-up.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 4.14. This ratio is based on an estimated foreground color of #D70617 and an estimated background color of #D1E4FF. Consider using colors that result in a contrast ratio greater than 4.50 for small | P6-he-light, P8-he-light, P9-he-light (3) |
| [13-stop-departures](shots/P3-en-light/13-stop-departures.jpg) | DuplicateSpeakableTextCheck | WARNING: This clickable item's speakable text: "☆" is identical to that of 4 other item(s). | P3-en-light, P3-he-light, P5-he-light (3) |
| [02-home-saved](shots/P2-en-light/02-home-saved.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 3.99. This ratio is based on an estimated foreground color of #92742E and an estimated background color of #F2F3FA. Consider using colors that result in a contrast ratio greater than 4.50 for small | P2-en-light, P2-he-light (2) |
| [02-home-saved](shots/P5-en-light/02-home-saved.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 4.06. This ratio is based on an estimated foreground color of #73777F and an estimated background color of #F2F3FA. Consider using colors that result in a contrast ratio greater than 4.50 for small | P5-en-light, P5-he-light (2) |
| [05-trip-second-option](shots/P3-en-light/05-trip-second-option.jpg) | SpeakableTextPresentCheck | ERROR: This item may not have a label readable by screen readers. | P3-en-light, P3-he-light (2) |
| [08-better-start](shots/P1-he-light/08-better-start.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 4.14. This ratio is based on an estimated foreground color of #D70617 and an estimated background color of #D1E4FF. Consider using colors that result in a contrast ratio greater than 4.50 for small | P1-he-light, P4-he-light (2) |
| [11-drop-off-results](shots/P3-en-light/11-drop-off-results.jpg) | SpeakableTextPresentCheck | ERROR: This item may not have a label readable by screen readers. | P3-en-light, P3-he-light (2) |
| [13-stop-departures](shots/P6-en-light/13-stop-departures.jpg) | DuplicateSpeakableTextCheck | WARNING: This clickable item's speakable text: "☆" is identical to that of 7 other item(s). | P6-en-light, P6-he-light (2) |
| [18-save-trip-dialog](shots/P9-en-light/18-save-trip-dialog.jpg) | SpeakableTextPresentCheck | ERROR: This item may not have a label readable by screen readers. | P9-en-light, P9-he-light (2) |
| [04-trip-results](shots/P2-en-light/04-trip-results.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 2.12. This ratio is based on an estimated foreground color of #A7A9B0 and an estimated background color of #F2F3FA. Consider using colors that result in a contrast ratio greater than 4.50 for small | P2-en-light (1) |
| [05-trip-second-option](shots/P2-en-light/05-trip-second-option.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 2.12. This ratio is based on an estimated foreground color of #A7A9B0 and an estimated background color of #F2F3FA. Consider using colors that result in a contrast ratio greater than 4.50 for small | P2-en-light (1) |
| [06-trip-network-error](shots/P2-en-light/06-trip-network-error.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 2.12. This ratio is based on an estimated foreground color of #A7A9B0 and an estimated background color of #F2F3FA. Consider using colors that result in a contrast ratio greater than 4.50 for small | P2-en-light (1) |
| [07-trip-offline-banner](shots/P2-en-light/07-trip-offline-banner.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 2.12. This ratio is based on an estimated foreground color of #A7A9B0 and an estimated background color of #F2F3FA. Consider using colors that result in a contrast ratio greater than 4.50 for small | P2-en-light (1) |
| [08-better-start](shots/P2-en-light/08-better-start.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 2.12. This ratio is based on an estimated foreground color of #A7A9B0 and an estimated background color of #F2F3FA. Consider using colors that result in a contrast ratio greater than 4.50 for small | P2-en-light (1) |
| [08-better-start](shots/P4-he-dark/08-better-start.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 2.36. This ratio is based on an estimated foreground color of #F92612 and an estimated background color of #00497D. Consider using colors that result in a contrast ratio greater than 4.50 for small | P4-he-dark (1) |
| [09-better-start-none](shots/P2-en-light/09-better-start-none.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 2.12. This ratio is based on an estimated foreground color of #A7A9B0 and an estimated background color of #F2F3FA. Consider using colors that result in a contrast ratio greater than 4.50 for small | P2-en-light (1) |
| [12-pick-up](shots/P3-en-light/12-pick-up.jpg) | SpeakableTextPresentCheck | ERROR: This item may not have a label readable by screen readers. | P3-en-light (1) |
| [13-stop-departures](shots/P5-en-light/13-stop-departures.jpg) | DuplicateSpeakableTextCheck | WARNING: This clickable item's speakable text: "☆" is identical to that of 5 other item(s). | P5-en-light (1) |
| [17-update-banner-results](shots/P2-en-light/17-update-banner-results.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 2.12. This ratio is based on an estimated foreground color of #A7A9B0 and an estimated background color of #F2F3FA. Consider using colors that result in a contrast ratio greater than 4.50 for small | P2-en-light (1) |
| [17-update-banner-results](shots/P9-en-light/17-update-banner-results.jpg) | DuplicateSpeakableTextCheck | WARNING: This clickable item's speakable text: "Close" is identical to that of 1 other item(s). | P9-en-light (1) |
| [17-update-banner-results](shots/P9-he-light/17-update-banner-results.jpg) | DuplicateSpeakableTextCheck | WARNING: This clickable item's speakable text: "סגירה" is identical to that of 1 other item(s). | P9-he-light (1) |
| [18-save-trip-dialog](shots/P2-en-light/18-save-trip-dialog.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 2.12. This ratio is based on an estimated foreground color of #A7A9B0 and an estimated background color of #F2F3FA. Consider using colors that result in a contrast ratio greater than 4.50 for small | P2-en-light (1) |


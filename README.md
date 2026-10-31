# UI tests — 104/merge @ e351769 (run 41238804994)

29 runs · 458 screenshots · 544 tests passed · **3 failed** · **0 crash logs** · 318 distinct findings

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
| J1 |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  | 1 |  |  |  |  |
| R2 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 |  |  |  |  |  |  |  |  |  |  |  |
| R3 | 13 | 13 | 14 | 13 | 20 | 21 | 13 | 12 | 1 | 1 | 1 | 1 | 36 | 45 | 1 | 1 | 3 | 2 | 20 | 20 | 19 | 19 | 19 | 17 |  |  |  |  |  |
| R4 | 4 | 4 | 7 | 4 | 6 | 6 | 7 | 4 | 13 | 13 | 11 | 11 | 10 | 10 | 7 | 7 | 7 | 5 | 5 | 5 | 5 | 5 | 13 | 13 |  |  |  |  |  |
| R5 | 12 | 12 | 11 | 12 | 18 | 10 | 11 | 11 | 15 | 15 | 24 | 24 | 10 | 11 | 17 | 19 | 15 | 15 | 10 | 10 | 10 | 11 | 12 | 13 |  |  |  |  |  |

## J1 — first launch without location (or: not verifiable on that emulator) (1)

| state | where | detail | runs (n) |
|---|---|---|---|
| journeys | journey | not verifiable on this emulator: SystemUI crashed (API 26) | api26-device-light (1) |

## R2 — tappable thing outside the usable screen (2)

| state | where | detail | runs (n) |
|---|---|---|---|
| [03-search-keyboard](shots/P1-en-dark/03-search-keyboard.jpg) | keyboard | # tappable items hidden behind the keyboard, e.g. "Routing: Transitous · Map © OpenStreetMap, OpenFreeMap" | P1-en-dark, P1-en-light, P2-en-light, P3-en-light, P4-en-dark, P4-en-light … (9) |
| [03-search-keyboard](shots/P1-he-dark/03-search-keyboard.jpg) | keyboard | # tappable items hidden behind the keyboard, e.g. "ניתוב: Transitous · מפה © OpenStreetMap, OpenFreeMap" | P1-he-dark, P1-he-light, P2-he-light, P3-he-light, P4-he-dark, P4-he-light … (9) |

## R3 — text clipped, squeezed or a time/price cut off (142)

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
| [03-search-keyboard](shots/P8-en-dark/03-search-keyboard.jpg) | "תל" | text is clipped: box 191×0 dp, text 20×24 dp, 1 lines (height) | P8-en-dark, P8-en-light, P8-he-dark, P8-he-light (4) |
| [05-trip-second-option](shots/P1-he-dark/05-trip-second-option.jpg) | "רכבת ⁨406⁩ ל⁨כרמיאל⁩ · ⁨5⁩ תחנות · ⁨71⁩ דק׳ · ירידה ב־⁨תל אב" | squeezed: 2 lines in 0 dp | P1-he-dark, P1-he-light, P2-he-light, P5-he-light (4) |
| [05-trip-second-option](shots/P4-en-dark/05-trip-second-option.jpg) | "8 stops · 76 min on board · off at ⁨ת. רכבת תל אביב - סבידור" | squeezed: 2 lines in 0 dp | P4-en-dark, P4-en-light, P6-en-light, P7-en-light (4) |
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
| [07-trip-offline-banner](shots/P2-en-light/07-trip-offline-banner.jpg) | "5 at 08:04 from ⁨מרפאות חוץ סורוקה/אוניברסיטת בן גוריון⁩" | squeezed: 2 lines in 0 dp | P2-en-light, P5-en-light (2) |
| [07-trip-offline-banner](shots/P2-en-light/07-trip-offline-banner.jpg) | "Line 171 · 6 stops · 7 min · get off at ⁨ת. רכבת תל אביב - ס" | squeezed: 3 lines in 0 dp | P2-en-light, P5-en-light (2) |
| [07-trip-offline-banner](shots/P2-he-light/07-trip-offline-banner.jpg) | "⁨5⁩ ב־⁨08:04⁩ מ־⁨מרפאות חוץ סורוקה/אוניברסיטת בן גוריון⁩" | squeezed: 2 lines in 0 dp | P2-he-light, P5-he-light (2) |
| [17-update-banner-results](shots/P2-en-light/17-update-banner-results.jpg) | "5 at 08:04 from ⁨מרפאות חוץ סורוקה/אוניברסיטת בן גוריון⁩" | squeezed: 2 lines in 0 dp | P2-en-light, P5-en-light (2) |
| [17-update-banner-results](shots/P2-en-light/17-update-banner-results.jpg) | "Line 171 · 6 stops · 7 min · get off at ⁨ת. רכבת תל אביב - ס" | squeezed: 3 lines in 0 dp | P2-en-light, P5-en-light (2) |
| [17-update-banner-results](shots/P2-he-light/17-update-banner-results.jpg) | "⁨5⁩ ב־⁨08:04⁩ מ־⁨מרפאות חוץ סורוקה/אוניברסיטת בן גוריון⁩" | squeezed: 2 lines in 0 dp | P2-he-light, P5-he-light (2) |
| [18-save-trip-dialog](shots/P2-en-light/18-save-trip-dialog.jpg) | "5 at 08:04 from ⁨מרפאות חוץ סורוקה/אוניברסיטת בן גוריון⁩" | squeezed: 2 lines in 0 dp | P2-en-light, P5-en-light (2) |
| [18-save-trip-dialog](shots/P2-en-light/18-save-trip-dialog.jpg) | "Line 171 · 6 stops · 7 min · get off at ⁨ת. רכבת תל אביב - ס" | squeezed: 3 lines in 0 dp | P2-en-light, P5-en-light (2) |
| [18-save-trip-dialog](shots/P2-he-light/18-save-trip-dialog.jpg) | "⁨5⁩ ב־⁨08:04⁩ מ־⁨מרפאות חוץ סורוקה/אוניברסיטת בן גוריון⁩" | squeezed: 2 lines in 0 dp | P2-he-light, P5-he-light (2) |
| [18-save-trip-dialog](shots/P8-en-dark/18-save-trip-dialog.jpg) | "Routing: Transitous · Map © OpenStreetMap, OpenFreeMap" | text is clipped: box 309×2 dp, text 308×16 dp, 1 lines (height) | P8-en-dark, P8-en-light (2) |
| [18-save-trip-dialog](shots/P8-he-dark/18-save-trip-dialog.jpg) | "ניתוב: Transitous · מפה © OpenStreetMap, OpenFreeMap" | text is clipped: box 293×2 dp, text 293×16 dp, 1 lines (height) | P8-he-dark, P8-he-light (2) |
| [04-trip-results](shots/P2-en-light/04-trip-results.jpg) | "Line 5 · 1 stop · 1 min · get off at ⁨מרכז רפואי סורוקה/אוני" | squeezed: 3 lines in 0 dp | P2-en-light (1) |
| [04-trip-results](shots/P2-he-light/04-trip-results.jpg) | "קו ⁨5⁩ · תחנה אחת · ⁨1⁩ דק׳ · ירידה ב־⁨מרכז רפואי סורוקה/אונ" | squeezed: 3 lines in 0 dp | P2-he-light (1) |
| [04-trip-results](shots/P5-en-light/04-trip-results.jpg) | "⚠ Walk 5 min, 0 min to spare" | squeezed: 2 lines in 0 dp | P5-en-light (1) |
| [04-trip-results](shots/P5-en-light/04-trip-results.jpg) | "Line 5 · 1 stop · 1 min · get off at ⁨מרכז רפואי סורוקה/אוני" | squeezed: 4 lines in 0 dp | P5-en-light (1) |
| [04-trip-results](shots/P5-en-light/04-trip-results.jpg) | "Line 370 · 2 stops · 69 min · get off at ⁨מחלף לה גווארדייה⁩" | squeezed: 3 lines in 0 dp | P5-en-light (1) |
| [04-trip-results](shots/P5-en-light/04-trip-results.jpg) | "If I miss this: next at 08:34 (+11 min)" | squeezed: 2 lines in 0 dp | P5-en-light (1) |
| … | | 82 more | |

## R4 — touch target under 48×48 dp (127)

| state | where | detail | runs (n) |
|---|---|---|---|
| [13-stop-departures](shots/P3-en-light/13-stop-departures.jpg) | "☆" | 58×24 dp; its 48 dp tap area reaches "☆" | P3-en-light, P3-he-light, P4-en-dark, P4-en-light, P4-he-dark, P4-he-light (6) |
| [13-stop-departures](shots/P1-en-dark/13-stop-departures.jpg) | "☆" | 58×12 dp; its 48 dp tap area reaches "☆" | P1-en-dark, P1-en-light, P1-he-dark, P1-he-light (4) |
| [03-search-keyboard](shots/P8-en-dark/03-search-keyboard.jpg) | "node#334" | 259×17 dp; its 48 dp tap area reaches "From My location" | P8-en-dark, P8-en-light (2) |
| [03-search-keyboard](shots/P8-en-dark/03-search-keyboard.jpg) | "Cancel" | 40×17 dp; its 48 dp tap area reaches "From My location" | P8-en-dark, P8-en-light (2) |
| [03-search-keyboard](shots/P8-he-dark/03-search-keyboard.jpg) | "node#334" | 259×17 dp; its 48 dp tap area reaches "מ־ המיקום שלי" | P8-he-dark, P8-he-light (2) |
| [03-search-keyboard](shots/P8-he-dark/03-search-keyboard.jpg) | "ביטול" | 40×17 dp; its 48 dp tap area reaches "מ־ המיקום שלי" | P8-he-dark, P8-he-light (2) |
| [04-trip-results](shots/P5-en-light/04-trip-results.jpg) | "5" | 26×36 dp; its 48 dp tap area reaches "171" | P5-en-light, P5-he-light (2) |
| [04-trip-results](shots/P5-en-light/04-trip-results.jpg) | "171" | 55×36 dp; its 48 dp tap area reaches "5" | P5-en-light, P5-he-light (2) |
| [05-trip-second-option](shots/P4-en-dark/05-trip-second-option.jpg) | "Mon 08:27–10:05 98 min Walk 10′ 370 Walk 5′ 171 Walk 7′ ⚠ Wa" | 352×17 dp; its 48 dp tap area reaches "Mon 08:21–09:46 85 min Walk 13′ Walk 1′ Fewer stops ⁨≈ ₪30.5" | P4-en-dark, P4-en-light (2) |
| [05-trip-second-option](shots/P4-en-dark/05-trip-second-option.jpg) | "Mon 08:27–10:05 98 min Walk 10′ 370 Walk 5′ 171 Walk 7′ ⚠ Wa" | 352×17 dp; its 48 dp tap area reaches "Remind me" | P4-en-dark, P4-en-light (2) |
| [05-trip-second-option](shots/P4-en-dark/05-trip-second-option.jpg) | "Mon 08:27–10:05 98 min Walk 10′ 370 Walk 5′ 171 Walk 7′ ⚠ Wa" | 352×17 dp; its 48 dp tap area reaches "Start trip" | P4-en-dark, P4-en-light (2) |
| [05-trip-second-option](shots/P4-he-dark/05-trip-second-option.jpg) | "יום ב׳ ⁨08:27⁩–10:05 ⁨98⁩ דק׳ הליכה 10′ 370 הליכה 5′ 171 הלי" | 352×33 dp; its 48 dp tap area reaches "יום ב׳ ⁨08:21⁩–09:46 ⁨85⁩ דק׳ הליכה 13′ הליכה 1′ פחות עצירות" | P4-he-dark, P4-he-light (2) |
| [07-trip-offline-banner](shots/P5-en-light/07-trip-offline-banner.jpg) | "5" | 26×36 dp; its 48 dp tap area reaches "171" | P5-en-light, P5-he-light (2) |
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
| [13-stop-departures](shots/P1-en-dark/13-stop-departures.jpg) | "Train באר שבע מרכז 08:28" | 328×16 dp; its 48 dp tap area reaches "☆" | P1-en-dark, P1-en-light (2) |
| [13-stop-departures](shots/P1-en-dark/13-stop-departures.jpg) | "☆" | 58×12 dp; its 48 dp tap area reaches "Train כרמיאל 08:21" | P1-en-dark, P1-en-light (2) |
| [13-stop-departures](shots/P1-he-dark/13-stop-departures.jpg) | "רכבת באר שבע מרכז 08:28" | 328×16 dp; its 48 dp tap area reaches "רכבת כרמיאל 08:21" | P1-he-dark, P1-he-light (2) |
| [13-stop-departures](shots/P1-he-dark/13-stop-departures.jpg) | "רכבת באר שבע מרכז 08:28" | 328×16 dp; its 48 dp tap area reaches "☆" | P1-he-dark, P1-he-light (2) |
| [13-stop-departures](shots/P1-he-dark/13-stop-departures.jpg) | "☆" | 58×12 dp; its 48 dp tap area reaches "רכבת כרמיאל 08:21" | P1-he-dark, P1-he-light (2) |
| [13-stop-departures](shots/P2-en-light/13-stop-departures.jpg) | "☆" | 58×5 dp; its 48 dp tap area reaches "☆" | P2-en-light, P2-he-light (2) |
| [13-stop-departures](shots/P4-en-dark/13-stop-departures.jpg) | "Train באר שבע מרכז 08:35" | 352×28 dp; its 48 dp tap area reaches "Train תל אביב מרכז 08:35" | P4-en-dark, P4-en-light (2) |
| [13-stop-departures](shots/P4-en-dark/13-stop-departures.jpg) | "Train באר שבע מרכז 08:35" | 352×28 dp; its 48 dp tap area reaches "☆" | P4-en-dark, P4-en-light (2) |
| [13-stop-departures](shots/P4-en-dark/13-stop-departures.jpg) | "☆" | 58×24 dp; its 48 dp tap area reaches "Train תל אביב מרכז 08:35" | P4-en-dark, P4-en-light (2) |
| [13-stop-departures](shots/P4-he-dark/13-stop-departures.jpg) | "רכבת באר שבע מרכז 08:35" | 352×28 dp; its 48 dp tap area reaches "רכבת תל אביב מרכז 08:35" | P4-he-dark, P4-he-light (2) |
| [13-stop-departures](shots/P4-he-dark/13-stop-departures.jpg) | "רכבת באר שבע מרכז 08:35" | 352×28 dp; its 48 dp tap area reaches "☆" | P4-he-dark, P4-he-light (2) |
| [13-stop-departures](shots/P4-he-dark/13-stop-departures.jpg) | "☆" | 58×24 dp; its 48 dp tap area reaches "רכבת תל אביב מרכז 08:35" | P4-he-dark, P4-he-light (2) |
| [13-stop-departures](shots/P6-en-light/13-stop-departures.jpg) | "☆" | 58×16 dp; its 48 dp tap area reaches "☆" | P6-en-light, P6-he-light (2) |
| [13-stop-departures](shots/P7-en-light/13-stop-departures.jpg) | "☆" | 58×0 dp; its 48 dp tap area reaches "☆" | P7-en-light, P7-he-light (2) |
| [13-stop-departures](shots/P8-en-dark/13-stop-departures.jpg) | "Train כרמיאל 08:21" | 364×38 dp; its 48 dp tap area reaches "Train הרצליה 08:06" | P8-en-dark, P8-en-light (2) |
| [13-stop-departures](shots/P8-en-dark/13-stop-departures.jpg) | "Train כרמיאל 08:21" | 364×38 dp; its 48 dp tap area reaches "☆" | P8-en-dark, P8-en-light (2) |
| [13-stop-departures](shots/P8-en-dark/13-stop-departures.jpg) | "☆" | 58×34 dp; its 48 dp tap area reaches "Train הרצליה 08:06" | P8-en-dark, P8-en-light (2) |
| [13-stop-departures](shots/P8-he-dark/13-stop-departures.jpg) | "רכבת כרמיאל 08:21" | 364×38 dp; its 48 dp tap area reaches "רכבת הרצליה 08:06" | P8-he-dark, P8-he-light (2) |
| [13-stop-departures](shots/P8-he-dark/13-stop-departures.jpg) | "רכבת כרמיאל 08:21" | 364×38 dp; its 48 dp tap area reaches "☆" | P8-he-dark, P8-he-light (2) |
| [13-stop-departures](shots/P8-he-dark/13-stop-departures.jpg) | "☆" | 58×34 dp; its 48 dp tap area reaches "רכבת הרצליה 08:06" | P8-he-dark, P8-he-light (2) |
| [13-stop-departures](shots/P9-en-light/13-stop-departures.jpg) | "☆" | 58×4 dp; its 48 dp tap area reaches "☆" | P9-en-light, P9-he-light (2) |
| [17-update-banner-results](shots/P5-en-light/17-update-banner-results.jpg) | "5" | 26×36 dp; its 48 dp tap area reaches "171" | P5-en-light, P5-he-light (2) |
| [17-update-banner-results](shots/P5-en-light/17-update-banner-results.jpg) | "171" | 55×36 dp; its 48 dp tap area reaches "5" | P5-en-light, P5-he-light (2) |
| [03-search-keyboard](shots/P6-en-light/03-search-keyboard.jpg) | "הגר באר שבע" | 387×21 dp; its 48 dp tap area reaches "הגר באר שבע" | P6-en-light (1) |
| [03-search-keyboard](shots/P6-he-light/03-search-keyboard.jpg) | "הגר באר שבע" | 387×5 dp; its 48 dp tap area reaches "הגר באר שבע" | P6-he-light (1) |
| [04-trip-results](shots/P2-en-light/04-trip-results.jpg) | "171" | 41×24 dp; its 48 dp tap area reaches "Remind me" | P2-en-light (1) |
| [04-trip-results](shots/P2-he-light/04-trip-results.jpg) | "171" | 41×24 dp; its 48 dp tap area reaches "תזכורת" | P2-he-light (1) |
| [04-trip-results](shots/P6-en-light/04-trip-results.jpg) | "Walking directions" | 139×9 dp; its 48 dp tap area reaches "Remind me" | P6-en-light (1) |
| [04-trip-results](shots/P6-he-light/04-trip-results.jpg) | "הוראות הליכה" | 106×13 dp; its 48 dp tap area reaches "תזכורת" | P6-he-light (1) |
| [07-trip-offline-banner](shots/P5-en-light/07-trip-offline-banner.jpg) | "171" | 55×33 dp; its 48 dp tap area reaches "5" | P5-en-light (1) |
| [07-trip-offline-banner](shots/P5-he-light/07-trip-offline-banner.jpg) | "171" | 55×32 dp; its 48 dp tap area reaches "5" | P5-he-light (1) |
| [12-pick-up](shots/P3-en-light/12-pick-up.jpg) | "🚗 Get picked up at ⁨מרכז אורן/יצחק רגר⁩ at 09:34 Home at 09" | 288×25 dp; its 48 dp tap area reaches "🚗 Get picked up at ⁨להבים רהט⁩ at 09:22 Home at 09:46 saves" | P3-en-light (1) |
| [12-pick-up](shots/P3-en-light/12-pick-up.jpg) | "🚗 Get picked up at ⁨מרכז אורן/יצחק רגר⁩ at 09:34 Home at 09" | 288×25 dp; its 48 dp tap area reaches "Send to driver" | P3-en-light (1) |
| [12-pick-up](shots/P3-en-light/12-pick-up.jpg) | "🚗 Get picked up at ⁨מרכז אורן/יצחק רגר⁩ at 09:34 Home at 09" | 288×25 dp; its 48 dp tap area reaches "Start trip" | P3-en-light (1) |
| … | | 67 more | |

## R5 — Accessibility Test Framework (46)

| state | where | detail | runs (n) |
|---|---|---|---|
| [04-trip-results](shots/P1-en-dark/04-trip-results.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 3.87. This ratio is based on an estimated foreground color of #FFFFFF and an estimated background color of #D55E00. Consider using colors that result in a contrast ratio greater than 4.50 for small | P1-en-dark, P1-en-light, P1-he-dark, P1-he-light, P2-en-light, P2-he-light … (24) |
| [04-trip-results](shots/P1-en-dark/04-trip-results.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 3.42. This ratio is based on an estimated foreground color of #FFFFFF and an estimated background color of #009E73. Consider using colors that result in a contrast ratio greater than 4.50 for small | P1-en-dark, P1-en-light, P1-he-dark, P1-he-light, P2-en-light, P2-he-light … (24) |
| [07-trip-offline-banner](shots/P1-en-dark/07-trip-offline-banner.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 3.87. This ratio is based on an estimated foreground color of #FFFFFF and an estimated background color of #D55E00. Consider using colors that result in a contrast ratio greater than 4.50 for small | P1-en-dark, P1-en-light, P1-he-dark, P1-he-light, P2-en-light, P2-he-light … (24) |
| [07-trip-offline-banner](shots/P1-en-dark/07-trip-offline-banner.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 3.42. This ratio is based on an estimated foreground color of #FFFFFF and an estimated background color of #009E73. Consider using colors that result in a contrast ratio greater than 4.50 for small | P1-en-dark, P1-en-light, P1-he-dark, P1-he-light, P2-en-light, P2-he-light … (24) |
| [17-update-banner-results](shots/P1-en-dark/17-update-banner-results.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 3.87. This ratio is based on an estimated foreground color of #FFFFFF and an estimated background color of #D55E00. Consider using colors that result in a contrast ratio greater than 4.50 for small | P1-en-dark, P1-en-light, P1-he-dark, P1-he-light, P2-en-light, P2-he-light … (22) |
| [17-update-banner-results](shots/P1-en-dark/17-update-banner-results.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 3.42. This ratio is based on an estimated foreground color of #FFFFFF and an estimated background color of #009E73. Consider using colors that result in a contrast ratio greater than 4.50 for small | P1-en-dark, P1-en-light, P1-he-dark, P1-he-light, P2-en-light, P2-he-light … (22) |
| [11-drop-off-results](shots/P1-en-dark/11-drop-off-results.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 3.87. This ratio is based on an estimated foreground color of #FFFFFF and an estimated background color of #D55E00. Consider using colors that result in a contrast ratio greater than 4.50 for small | P1-en-dark, P1-en-light, P1-he-dark, P1-he-light, P3-en-light, P3-he-light … (21) |
| [11-drop-off-results](shots/P1-en-dark/11-drop-off-results.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 3.42. This ratio is based on an estimated foreground color of #FFFFFF and an estimated background color of #009E73. Consider using colors that result in a contrast ratio greater than 4.50 for small | P1-en-dark, P1-en-light, P1-he-dark, P1-he-light, P3-en-light, P3-he-light … (20) |
| [18-save-trip-dialog](shots/P1-en-dark/18-save-trip-dialog.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 3.87. This ratio is based on an estimated foreground color of #FFFFFF and an estimated background color of #D55E00. Consider using colors that result in a contrast ratio greater than 4.50 for small | P1-en-dark, P1-en-light, P1-he-dark, P1-he-light, P2-en-light, P2-he-light … (18) |
| [18-save-trip-dialog](shots/P1-en-dark/18-save-trip-dialog.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 3.42. This ratio is based on an estimated foreground color of #FFFFFF and an estimated background color of #009E73. Consider using colors that result in a contrast ratio greater than 4.50 for small | P1-en-dark, P1-en-light, P1-he-dark, P1-he-light, P2-en-light, P2-he-light … (18) |
| [03-search-keyboard](shots/P4-en-dark/03-search-keyboard.jpg) | SpeakableTextPresentCheck | ERROR: This item may not have a label readable by screen readers. | P4-en-dark, P4-en-light, P4-he-dark, P4-he-light, P6-he-light, P8-en-dark … (9) |
| [12-pick-up](shots/P4-en-dark/12-pick-up.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 3.87. This ratio is based on an estimated foreground color of #FFFFFF and an estimated background color of #D55E00. Consider using colors that result in a contrast ratio greater than 4.50 for small | P4-en-dark, P4-en-light, P4-he-dark, P4-he-light, P6-en-light, P6-he-light … (8) |
| [13-stop-departures](shots/P2-en-light/13-stop-departures.jpg) | DuplicateSpeakableTextCheck | WARNING: This clickable item's speakable text: "☆" is identical to that of 2 other item(s). | P2-en-light, P2-he-light, P8-en-dark, P8-en-light, P8-he-dark, P8-he-light … (8) |
| [13-stop-departures](shots/P4-en-dark/13-stop-departures.jpg) | DuplicateSpeakableTextCheck | WARNING: This clickable item's speakable text: "☆" is identical to that of 6 other item(s). | P4-en-dark, P4-en-light, P4-he-dark, P4-he-light, P7-en-light, P7-he-light (6) |
| [13-stop-departures](shots/P1-en-dark/13-stop-departures.jpg) | DuplicateSpeakableTextCheck | WARNING: This clickable item's speakable text: "☆" is identical to that of 3 other item(s). | P1-en-dark, P1-en-light, P1-he-dark, P1-he-light (4) |
| [13-stop-departures](shots/P3-en-light/13-stop-departures.jpg) | DuplicateSpeakableTextCheck | WARNING: This clickable item's speakable text: "☆" is identical to that of 4 other item(s). | P3-en-light, P3-he-light, P5-en-light, P5-he-light (4) |
| [12-pick-up](shots/P6-he-light/12-pick-up.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 4.14. This ratio is based on an estimated foreground color of #D70617 and an estimated background color of #D1E4FF. Consider using colors that result in a contrast ratio greater than 4.50 for small | P6-he-light, P8-he-light, P9-he-light (3) |
| [02-home-saved](shots/P2-en-light/02-home-saved.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 3.99. This ratio is based on an estimated foreground color of #92742E and an estimated background color of #F2F3FA. Consider using colors that result in a contrast ratio greater than 4.50 for small | P2-en-light, P2-he-light (2) |
| [02-home-saved](shots/P5-en-light/02-home-saved.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 4.06. This ratio is based on an estimated foreground color of #73777F and an estimated background color of #F2F3FA. Consider using colors that result in a contrast ratio greater than 4.50 for small | P5-en-light, P5-he-light (2) |
| [05-trip-second-option](shots/P6-en-light/05-trip-second-option.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 3.87. This ratio is based on an estimated foreground color of #FFFFFF and an estimated background color of #D55E00. Consider using colors that result in a contrast ratio greater than 4.50 for small | P6-en-light, P6-he-light (2) |
| [05-trip-second-option](shots/P7-en-light/05-trip-second-option.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 1.15. This ratio is based on an estimated foreground color of #157DB8 and an estimated background color of #0072B2. Consider using colors that result in a contrast ratio greater than 4.50 for small | P7-en-light, P7-he-light (2) |
| [08-better-start](shots/P1-he-light/08-better-start.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 4.14. This ratio is based on an estimated foreground color of #D70617 and an estimated background color of #D1E4FF. Consider using colors that result in a contrast ratio greater than 4.50 for small | P1-he-light, P4-he-light (2) |
| [13-stop-departures](shots/P6-en-light/13-stop-departures.jpg) | DuplicateSpeakableTextCheck | WARNING: This clickable item's speakable text: "☆" is identical to that of 7 other item(s). | P6-en-light, P6-he-light (2) |
| [17-update-banner-results](shots/P9-en-light/17-update-banner-results.jpg) | SpeakableTextPresentCheck | ERROR: This item may not have a label readable by screen readers. | P9-en-light, P9-he-light (2) |
| [18-save-trip-dialog](shots/P9-en-light/18-save-trip-dialog.jpg) | SpeakableTextPresentCheck | ERROR: This item may not have a label readable by screen readers. | P9-en-light, P9-he-light (2) |
| [04-trip-results](shots/P2-en-light/04-trip-results.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 2.12. This ratio is based on an estimated foreground color of #A7A9B0 and an estimated background color of #F2F3FA. Consider using colors that result in a contrast ratio greater than 4.50 for small | P2-en-light (1) |
| [04-trip-results](shots/P4-he-dark/04-trip-results.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 3.34. This ratio is based on an estimated foreground color of #B5975A and an estimated background color of #5E4200. Consider using colors that result in a contrast ratio greater than 4.50 for small | P4-he-dark (1) |
| [04-trip-results](shots/P4-he-light/04-trip-results.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 2.22. This ratio is based on an estimated foreground color of #AF9569 and an estimated background color of #FFDEA6. Consider using colors that result in a contrast ratio greater than 4.50 for small | P4-he-light (1) |
| [05-trip-second-option](shots/P1-en-dark/05-trip-second-option.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 2.00. This ratio is based on an estimated foreground color of #47799E and an estimated background color of #00497D. Consider using colors that result in a contrast ratio greater than 4.50 for small | P1-en-dark (1) |
| [05-trip-second-option](shots/P1-en-light/05-trip-second-option.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 1.36. This ratio is based on an estimated foreground color of #B4C5DD and an estimated background color of #D1E4FF. Consider using colors that result in a contrast ratio greater than 4.50 for small | P1-en-light (1) |
| [05-trip-second-option](shots/P2-en-light/05-trip-second-option.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 2.12. This ratio is based on an estimated foreground color of #A7A9B0 and an estimated background color of #F2F3FA. Consider using colors that result in a contrast ratio greater than 4.50 for small | P2-en-light (1) |
| [06-trip-network-error](shots/P2-en-light/06-trip-network-error.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 2.12. This ratio is based on an estimated foreground color of #A7A9B0 and an estimated background color of #F2F3FA. Consider using colors that result in a contrast ratio greater than 4.50 for small | P2-en-light (1) |
| [07-trip-offline-banner](shots/P2-en-light/07-trip-offline-banner.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 2.12. This ratio is based on an estimated foreground color of #A7A9B0 and an estimated background color of #F2F3FA. Consider using colors that result in a contrast ratio greater than 4.50 for small | P2-en-light (1) |
| [07-trip-offline-banner](shots/P4-he-dark/07-trip-offline-banner.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 3.34. This ratio is based on an estimated foreground color of #B5975A and an estimated background color of #5E4200. Consider using colors that result in a contrast ratio greater than 4.50 for small | P4-he-dark (1) |
| [07-trip-offline-banner](shots/P4-he-light/07-trip-offline-banner.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 2.22. This ratio is based on an estimated foreground color of #AF9569 and an estimated background color of #FFDEA6. Consider using colors that result in a contrast ratio greater than 4.50 for small | P4-he-light (1) |
| [08-better-start](shots/P2-en-light/08-better-start.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 2.12. This ratio is based on an estimated foreground color of #A7A9B0 and an estimated background color of #F2F3FA. Consider using colors that result in a contrast ratio greater than 4.50 for small | P2-en-light (1) |
| [08-better-start](shots/P4-he-dark/08-better-start.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 2.36. This ratio is based on an estimated foreground color of #F92612 and an estimated background color of #00497D. Consider using colors that result in a contrast ratio greater than 4.50 for small | P4-he-dark (1) |
| [09-better-start-none](shots/P2-en-light/09-better-start-none.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 2.12. This ratio is based on an estimated foreground color of #A7A9B0 and an estimated background color of #F2F3FA. Consider using colors that result in a contrast ratio greater than 4.50 for small | P2-en-light (1) |
| [17-update-banner-results](shots/P2-en-light/17-update-banner-results.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 2.12. This ratio is based on an estimated foreground color of #A7A9B0 and an estimated background color of #F2F3FA. Consider using colors that result in a contrast ratio greater than 4.50 for small | P2-en-light (1) |
| [17-update-banner-results](shots/P4-he-dark/17-update-banner-results.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 3.34. This ratio is based on an estimated foreground color of #B5975A and an estimated background color of #5E4200. Consider using colors that result in a contrast ratio greater than 4.50 for small | P4-he-dark (1) |
| [17-update-banner-results](shots/P4-he-light/17-update-banner-results.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 2.22. This ratio is based on an estimated foreground color of #AF9569 and an estimated background color of #FFDEA6. Consider using colors that result in a contrast ratio greater than 4.50 for small | P4-he-light (1) |
| [17-update-banner-results](shots/P9-en-light/17-update-banner-results.jpg) | DuplicateSpeakableTextCheck | WARNING: This clickable item's speakable text: "Close" is identical to that of 1 other item(s). | P9-en-light (1) |
| [17-update-banner-results](shots/P9-he-light/17-update-banner-results.jpg) | DuplicateSpeakableTextCheck | WARNING: This clickable item's speakable text: "סגירה" is identical to that of 1 other item(s). | P9-he-light (1) |
| [18-save-trip-dialog](shots/P2-en-light/18-save-trip-dialog.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 2.12. This ratio is based on an estimated foreground color of #A7A9B0 and an estimated background color of #F2F3FA. Consider using colors that result in a contrast ratio greater than 4.50 for small | P2-en-light (1) |
| [18-save-trip-dialog](shots/P4-he-dark/18-save-trip-dialog.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 3.34. This ratio is based on an estimated foreground color of #B5975A and an estimated background color of #5E4200. Consider using colors that result in a contrast ratio greater than 4.50 for small | P4-he-dark (1) |
| [18-save-trip-dialog](shots/P4-he-light/18-save-trip-dialog.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 2.22. This ratio is based on an estimated foreground color of #AF9569 and an estimated background color of #FFDEA6. Consider using colors that result in a contrast ratio greater than 4.50 for small | P4-he-light (1) |

## Test failures

**layout-1/P1-he-light** `ScreensTest.s15_settings_bottom`

```
java.lang.IllegalArgumentException: Detected multithreaded access to SnapshotStateObserver: previousThreadId=2), currentThread={id=42, name=Instr: androidx.test.runner.AndroidJUnitRunner}. Note that observation on multiple threads in layout/draw is not supported. Make sure your measure/layout/draw for each Owner (AndroidComposeView) is executed on the same thread.
	at androidx.compose.runtime.PreconditionsKt.throwIllegalArgumentException(Preconditions.kt:26)
	at androidx.compose.runtime.snapshots.SnapshotStateObserver.observeReads(SnapshotStateObserver.kt:708)
	at androidx.compose.ui.node.OwnerSnapshotObserver.observeReads$ui_release(OwnerSnapshotObserver.kt:133)
	at androidx.compose.ui.node.OwnerSnapshotObserver.observeSemanticsReads$ui_release(OwnerSnapshotObserver.kt:121)
	at androidx.compose.ui.node.LayoutNode.getCollapsedSemantics$ui_release(LayoutNode.kt:433)
```

**layout-1/P3-he-light** `ScreensTest.s15_settings_bottom`

```
java.lang.IllegalArgumentException: Detected multithreaded access to SnapshotStateObserver: previousThreadId=2), currentThread={id=47, name=Instr: androidx.test.runner.AndroidJUnitRunner}. Note that observation on multiple threads in layout/draw is not supported. Make sure your measure/layout/draw for each Owner (AndroidComposeView) is executed on the same thread.
	at androidx.compose.runtime.PreconditionsKt.throwIllegalArgumentException(Preconditions.kt:26)
	at androidx.compose.runtime.snapshots.SnapshotStateObserver.observeReads(SnapshotStateObserver.kt:708)
	at androidx.compose.ui.node.OwnerSnapshotObserver.observeReads$ui_release(OwnerSnapshotObserver.kt:133)
	at androidx.compose.ui.node.OwnerSnapshotObserver.observeSemanticsReads$ui_release(OwnerSnapshotObserver.kt:121)
	at androidx.compose.ui.node.LayoutNode.getCollapsedSemantics$ui_release(LayoutNode.kt:433)
```

**layout-3/P9-he-light** `ScreensTest.s15_settings_bottom`

```
java.lang.IllegalArgumentException: Detected multithreaded access to SnapshotStateObserver: previousThreadId=2), currentThread={id=47, name=Instr: androidx.test.runner.AndroidJUnitRunner}. Note that observation on multiple threads in layout/draw is not supported. Make sure your measure/layout/draw for each Owner (AndroidComposeView) is executed on the same thread.
	at androidx.compose.runtime.PreconditionsKt.throwIllegalArgumentException(Preconditions.kt:26)
	at androidx.compose.runtime.snapshots.SnapshotStateObserver.observeReads(SnapshotStateObserver.kt:708)
	at androidx.compose.ui.node.OwnerSnapshotObserver.observeReads$ui_release(OwnerSnapshotObserver.kt:133)
	at androidx.compose.ui.node.OwnerSnapshotObserver.observeSemanticsReads$ui_release(OwnerSnapshotObserver.kt:121)
	at androidx.compose.ui.node.LayoutNode.getCollapsedSemantics$ui_release(LayoutNode.kt:433)
```


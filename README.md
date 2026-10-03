# UI tests — claude/gallant-mendel-4yps2i @ bc993db (run 37154148160)

29 runs · 437 screenshots · 491 tests passed · **0 failed** · **0 crash logs** · 503 distinct findings

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
| J10 |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  | 1 | 1 | 1 | 1 | 1 |
| J4 |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  | 3 | 3 | 3 | 3 | 3 |
| R1 | 12 | 12 | 12 | 12 | 13 | 13 | 11 | 11 | 7 | 7 | 7 | 7 | 13 | 13 | 3 | 3 | 6 | 6 | 17 | 17 | 17 | 17 | 10 | 10 |  |  |  |  |  |
| R2 | 3 | 3 | 3 | 2 | 6 | 6 | 2 | 2 | 1 | 1 | 1 |  | 1 | 2 | 1 | 1 | 1 | 1 | 14 | 14 | 11 | 11 | 3 | 3 |  |  |  |  |  |
| R3 |  |  |  |  | 6 | 6 | 7 | 5 |  |  |  |  | 11 | 21 |  |  | 6 |  | 1 | 1 | 1 | 1 | 6 | 5 |  |  |  |  |  |
| R4 | 14 | 14 | 18 | 18 | 9 | 8 | 16 | 15 | 9 | 9 | 8 | 8 | 4 | 6 | 1 | 1 | 5 | 9 | 11 | 11 | 11 | 11 | 8 | 8 |  |  |  |  |  |
| R5 | 58 | 88 | 57 | 88 | 66 | 79 | 76 | 75 | 47 | 72 | 47 | 73 | 53 | 52 | 71 | 72 | 71 | 71 | 30 | 47 | 29 | 46 | 101 | 100 |  |  |  |  |  |
| R7 | 16 | 16 | 16 | 16 | 16 | 16 | 16 | 16 | 16 | 16 | 16 | 16 | 16 | 16 | 16 | 16 | 16 | 16 | 16 | 16 | 16 | 16 | 16 | 16 |  |  |  |  |  |

## J10 — Settings reachable with results open (1)

| state | where | detail | runs (n) |
|---|---|---|---|
| journeys | journey | Settings button can't be tapped while results are open (the panel covers it) | api26-device-light, api29-device-light, api33-device-light, api34-device-light, api35-device-light (5) |

## J4 — Back closes what is open before leaving (4)

| state | where | detail | runs (n) |
|---|---|---|---|
| journeys | journey | Back with results open left the app | api26-device-light, api29-device-light, api33-device-light, api34-device-light, api35-device-light (5) |
| journeys | journey | Back with a stop's departures open left the app | api26-device-light, api29-device-light, api33-device-light, api34-device-light, api35-device-light (5) |
| journeys | journey | Back with the search open left the app | api29-device-light, api33-device-light, api34-device-light, api35-device-light (4) |
| journeys | journey | Back with the search open did not close it | api26-device-light (1) |

## R1 — search card and bottom panel overlap (113)

| state | where | detail | runs (n) |
|---|---|---|---|
| [10-drop-off-fields](shots/P5-en-light/10-drop-off-fields.jpg) | top/bottom | search card and bottom panel overlap by 21 dp | P5-en-light, P8-en-dark, P8-en-light, P8-he-dark, P8-he-light (5) |
| [02-home-saved](shots/P8-en-dark/02-home-saved.jpg) | top/bottom | search card and bottom panel overlap by 1 dp | P8-en-dark, P8-en-light, P8-he-dark, P8-he-light (4) |
| [03-search-keyboard](shots/P1-en-dark/03-search-keyboard.jpg) | top/bottom | search card and bottom panel overlap by 76 dp | P1-en-dark, P1-en-light, P1-he-dark, P1-he-light (4) |
| [03-search-keyboard](shots/P8-en-dark/03-search-keyboard.jpg) | top/bottom | search card and bottom panel overlap by 52 dp | P8-en-dark, P8-en-light, P8-he-dark, P8-he-light (4) |
| [04-trip-results](shots/P1-en-dark/04-trip-results.jpg) | top/bottom | search card and bottom panel overlap by 276 dp | P1-en-dark, P1-en-light, P1-he-dark, P1-he-light (4) |
| [04-trip-results](shots/P4-en-dark/04-trip-results.jpg) | top/bottom | search card and bottom panel overlap by 61 dp | P4-en-dark, P4-en-light, P4-he-dark, P4-he-light (4) |
| [04-trip-results](shots/P8-en-dark/04-trip-results.jpg) | top/bottom | search card and bottom panel overlap by 256 dp | P8-en-dark, P8-en-light, P8-he-dark, P8-he-light (4) |
| [05-trip-second-option](shots/P1-en-dark/05-trip-second-option.jpg) | top/bottom | search card and bottom panel overlap by 276 dp | P1-en-dark, P1-en-light, P1-he-dark, P1-he-light (4) |
| [05-trip-second-option](shots/P4-en-dark/05-trip-second-option.jpg) | top/bottom | search card and bottom panel overlap by 61 dp | P4-en-dark, P4-en-light, P4-he-dark, P4-he-light (4) |
| [05-trip-second-option](shots/P8-en-dark/05-trip-second-option.jpg) | top/bottom | search card and bottom panel overlap by 256 dp | P8-en-dark, P8-en-light, P8-he-dark, P8-he-light (4) |
| [06-trip-network-error](shots/P1-en-dark/06-trip-network-error.jpg) | top/bottom | search card and bottom panel overlap by 36 dp | P1-en-dark, P1-en-light, P1-he-dark, P1-he-light (4) |
| [06-trip-network-error](shots/P8-en-dark/06-trip-network-error.jpg) | top/bottom | search card and bottom panel overlap by 146 dp | P8-en-dark, P8-en-light, P8-he-dark, P8-he-light (4) |
| [07-trip-offline-banner](shots/P4-en-dark/07-trip-offline-banner.jpg) | top/bottom | search card and bottom panel overlap by 14 dp | P4-en-dark, P4-en-light, P4-he-dark, P4-he-light (4) |
| [07-trip-offline-banner](shots/P8-en-dark/07-trip-offline-banner.jpg) | top/bottom | search card and bottom panel overlap by 256 dp | P8-en-dark, P8-en-light, P8-he-dark, P8-he-light (4) |
| [08-better-start](shots/P8-en-dark/08-better-start.jpg) | top/bottom | search card and bottom panel overlap by 306 dp | P8-en-dark, P8-en-light, P8-he-dark, P8-he-light (4) |
| [09-better-start-none](shots/P8-en-dark/09-better-start-none.jpg) | top/bottom | search card and bottom panel overlap by 158 dp | P8-en-dark, P8-en-light, P8-he-dark, P8-he-light (4) |
| [11-drop-off-results](shots/P1-en-dark/11-drop-off-results.jpg) | top/bottom | search card and bottom panel overlap by 336 dp | P1-en-dark, P1-en-light, P1-he-dark, P1-he-light (4) |
| [11-drop-off-results](shots/P4-en-dark/11-drop-off-results.jpg) | top/bottom | search card and bottom panel overlap by 122 dp | P4-en-dark, P4-en-light, P4-he-dark, P4-he-light (4) |
| [11-drop-off-results](shots/P8-en-dark/11-drop-off-results.jpg) | top/bottom | search card and bottom panel overlap by 324 dp | P8-en-dark, P8-en-light, P8-he-dark, P8-he-light (4) |
| [12-pick-up](shots/P1-en-dark/12-pick-up.jpg) | top/bottom | search card and bottom panel overlap by 336 dp | P1-en-dark, P1-en-light, P1-he-dark, P1-he-light (4) |
| [12-pick-up](shots/P4-en-dark/12-pick-up.jpg) | top/bottom | search card and bottom panel overlap by 122 dp | P4-en-dark, P4-en-light, P4-he-dark, P4-he-light (4) |
| [12-pick-up](shots/P8-en-dark/12-pick-up.jpg) | top/bottom | search card and bottom panel overlap by 324 dp | P8-en-dark, P8-en-light, P8-he-dark, P8-he-light (4) |
| [13-stop-departures](shots/P1-en-dark/13-stop-departures.jpg) | top/bottom | search card and bottom panel overlap by 180 dp | P1-en-dark, P1-en-light, P1-he-dark, P1-he-light (4) |
| [13-stop-departures](shots/P8-en-dark/13-stop-departures.jpg) | top/bottom | search card and bottom panel overlap by 256 dp | P8-en-dark, P8-en-light, P8-he-dark, P8-he-light (4) |
| [14-settings-top](shots/P8-en-dark/14-settings-top.jpg) | top/bottom | search card and bottom panel overlap by 1 dp | P8-en-dark, P8-en-light, P8-he-dark, P8-he-light (4) |
| [15-settings-bottom](shots/P8-en-dark/15-settings-bottom.jpg) | top/bottom | search card and bottom panel overlap by 1 dp | P8-en-dark, P8-en-light, P8-he-dark, P8-he-light (4) |
| [16-history](shots/P8-en-dark/16-history.jpg) | top/bottom | search card and bottom panel overlap by 1 dp | P8-en-dark, P8-en-light, P8-he-dark, P8-he-light (4) |
| [17-update-banner-results](shots/P1-en-dark/17-update-banner-results.jpg) | top/bottom | search card and bottom panel overlap by 332 dp | P1-en-dark, P1-en-light, P1-he-dark, P1-he-light (4) |
| [17-update-banner-results](shots/P4-en-dark/17-update-banner-results.jpg) | top/bottom | search card and bottom panel overlap by 117 dp | P4-en-dark, P4-en-light, P4-he-dark, P4-he-light (4) |
| [17-update-banner-results](shots/P8-en-dark/17-update-banner-results.jpg) | top/bottom | search card and bottom panel overlap by 312 dp | P8-en-dark, P8-en-light, P8-he-dark, P8-he-light (4) |
| [18-save-trip-dialog](shots/P1-en-dark/18-save-trip-dialog.jpg) | top/bottom | search card and bottom panel overlap by 276 dp | P1-en-dark, P1-en-light, P1-he-dark, P1-he-light (4) |
| [18-save-trip-dialog](shots/P4-en-dark/18-save-trip-dialog.jpg) | top/bottom | search card and bottom panel overlap by 61 dp | P4-en-dark, P4-en-light, P4-he-dark, P4-he-light (4) |
| [18-save-trip-dialog](shots/P8-en-dark/18-save-trip-dialog.jpg) | top/bottom | search card and bottom panel overlap by 256 dp | P8-en-dark, P8-en-light, P8-he-dark, P8-he-light (4) |
| [03-search-keyboard](shots/P2-en-light/03-search-keyboard.jpg) | top/bottom | search card and bottom panel overlap by 102 dp | P2-en-light, P2-he-light (2) |
| [03-search-keyboard](shots/P3-en-light/03-search-keyboard.jpg) | top/bottom | search card and bottom panel overlap by 68 dp | P3-en-light, P3-he-light (2) |
| [03-search-keyboard](shots/P9-en-light/03-search-keyboard.jpg) | top/bottom | search card and bottom panel overlap by 64 dp | P9-en-light, P9-he-light (2) |
| [04-trip-results](shots/P2-en-light/04-trip-results.jpg) | top/bottom | search card and bottom panel overlap by 304 dp | P2-en-light, P2-he-light (2) |
| [04-trip-results](shots/P3-en-light/04-trip-results.jpg) | top/bottom | search card and bottom panel overlap by 215 dp | P3-en-light, P3-he-light (2) |
| [04-trip-results](shots/P7-en-light/04-trip-results.jpg) | top/bottom | search card and bottom panel overlap by 11 dp | P7-en-light, P7-he-light (2) |
| [04-trip-results](shots/P9-en-light/04-trip-results.jpg) | top/bottom | search card and bottom panel overlap by 176 dp | P9-en-light, P9-he-light (2) |
| [05-trip-second-option](shots/P2-en-light/05-trip-second-option.jpg) | top/bottom | search card and bottom panel overlap by 304 dp | P2-en-light, P2-he-light (2) |
| [05-trip-second-option](shots/P3-en-light/05-trip-second-option.jpg) | top/bottom | search card and bottom panel overlap by 215 dp | P3-en-light, P3-he-light (2) |
| [05-trip-second-option](shots/P7-en-light/05-trip-second-option.jpg) | top/bottom | search card and bottom panel overlap by 11 dp | P7-en-light, P7-he-light (2) |
| [05-trip-second-option](shots/P9-en-light/05-trip-second-option.jpg) | top/bottom | search card and bottom panel overlap by 176 dp | P9-en-light, P9-he-light (2) |
| [06-trip-network-error](shots/P3-en-light/06-trip-network-error.jpg) | top/bottom | search card and bottom panel overlap by 24 dp | P3-en-light, P3-he-light (2) |
| [07-trip-offline-banner](shots/P1-en-dark/07-trip-offline-banner.jpg) | top/bottom | search card and bottom panel overlap by 228 dp | P1-en-dark, P1-en-light (2) |
| [07-trip-offline-banner](shots/P1-he-dark/07-trip-offline-banner.jpg) | top/bottom | search card and bottom panel overlap by 229 dp | P1-he-dark, P1-he-light (2) |
| [07-trip-offline-banner](shots/P2-en-light/07-trip-offline-banner.jpg) | top/bottom | search card and bottom panel overlap by 268 dp | P2-en-light, P2-he-light (2) |
| [07-trip-offline-banner](shots/P9-en-light/07-trip-offline-banner.jpg) | top/bottom | search card and bottom panel overlap by 112 dp | P9-en-light, P9-he-light (2) |
| [08-better-start](shots/P1-en-dark/08-better-start.jpg) | top/bottom | search card and bottom panel overlap by 148 dp | P1-en-dark, P1-en-light (2) |
| [08-better-start](shots/P1-he-dark/08-better-start.jpg) | top/bottom | search card and bottom panel overlap by 149 dp | P1-he-dark, P1-he-light (2) |
| [08-better-start](shots/P9-en-light/08-better-start.jpg) | top/bottom | search card and bottom panel overlap by 48 dp | P9-en-light, P9-he-light (2) |
| [09-better-start-none](shots/P1-en-dark/09-better-start-none.jpg) | top/bottom | search card and bottom panel overlap by 24 dp | P1-en-dark, P1-en-light (2) |
| [09-better-start-none](shots/P1-he-dark/09-better-start-none.jpg) | top/bottom | search card and bottom panel overlap by 25 dp | P1-he-dark, P1-he-light (2) |
| [09-better-start-none](shots/P2-en-light/09-better-start-none.jpg) | top/bottom | search card and bottom panel overlap by 75 dp | P2-en-light, P2-he-light (2) |
| [11-drop-off-results](shots/P3-en-light/11-drop-off-results.jpg) | top/bottom | search card and bottom panel overlap by 276 dp | P3-en-light, P3-he-light (2) |
| [11-drop-off-results](shots/P6-en-light/11-drop-off-results.jpg) | top/bottom | search card and bottom panel overlap by 39 dp | P6-en-light, P6-he-light (2) |
| [11-drop-off-results](shots/P7-en-light/11-drop-off-results.jpg) | top/bottom | search card and bottom panel overlap by 71 dp | P7-en-light, P7-he-light (2) |
| [11-drop-off-results](shots/P9-en-light/11-drop-off-results.jpg) | top/bottom | search card and bottom panel overlap by 217 dp | P9-en-light, P9-he-light (2) |
| [12-pick-up](shots/P2-en-light/12-pick-up.jpg) | top/bottom | search card and bottom panel overlap by 379 dp | P2-en-light, P2-he-light (2) |
| … | | 53 more | |

## R2 — tappable thing outside the usable screen (33)

| state | where | detail | runs (n) |
|---|---|---|---|
| [03-search-keyboard](shots/P1-en-dark/03-search-keyboard.jpg) | "רגואן" | tappable at [#,#][#,#] leaves the usable area [#,#][#,#] | P1-en-dark, P1-en-light, P1-he-dark, P1-he-light, P2-en-light, P2-he-light … (7) |
| [03-search-keyboard](shots/P1-en-dark/03-search-keyboard.jpg) | "גבעת רגע" | tappable at [#,#][#,#] leaves the usable area [#,#][#,#] | P1-en-dark, P1-en-light, P1-he-dark, P1-he-light (4) |
| [03-search-keyboard](shots/P3-en-light/03-search-keyboard.jpg) | "רגל הפיל" | tappable at [#,#][#,#] leaves the usable area [#,#][#,#] | P3-en-light, P3-he-light, P9-en-light, P9-he-light (4) |
| [03-search-keyboard](shots/P6-en-light/03-search-keyboard.jpg) | keyboard | # tappable items hidden behind the keyboard, e.g. "רגבה", "רגואן", "גבעת רגע" | P6-en-light, P6-he-light, P7-en-light, P7-he-light (4) |
| [03-search-keyboard](shots/P8-en-dark/03-search-keyboard.jpg) | "תל אביב סבידור" | tappable at [#,#][#,#] leaves the usable area [#,#][#,#] | P8-en-dark, P8-en-light, P8-he-dark, P8-he-light (4) |
| [03-search-keyboard](shots/P8-en-dark/03-search-keyboard.jpg) | "הגר" | tappable at [#,#][#,#] leaves the usable area [#,#][#,#] | P8-en-dark, P8-en-light, P8-he-dark, P8-he-light (4) |
| [10-drop-off-fields](shots/P8-en-dark/10-drop-off-fields.jpg) | "רחובות" | tappable at [#,#][#,#] leaves the usable area [#,#][#,#] | P8-en-dark, P8-en-light, P8-he-dark, P8-he-light (4) |
| [03-search-keyboard](shots/P1-en-dark/03-search-keyboard.jpg) | keyboard | # tappable items hidden behind the keyboard, e.g. "node##", "My location", "תל אביב סבידור" | P1-en-dark, P1-en-light, P2-en-light (3) |
| [03-search-keyboard](shots/P3-en-light/03-search-keyboard.jpg) | keyboard | # tappable items hidden behind the keyboard, e.g. "My location", "תל אביב סבידור", "הגר" | P3-en-light, P5-en-light, P9-en-light (3) |
| [03-search-keyboard](shots/P3-he-light/03-search-keyboard.jpg) | keyboard | # tappable items hidden behind the keyboard, e.g. "המיקום שלי", "תל אביב סבידור", "הגר" | P3-he-light, P5-he-light, P9-he-light (3) |
| [03-search-keyboard](shots/P4-en-dark/03-search-keyboard.jpg) | keyboard | # tappable items hidden behind the keyboard, e.g. "הגר", "הגר", "רגבה" | P4-en-dark, P4-en-light, P4-he-dark (3) |
| [04-trip-results](shots/P2-en-light/04-trip-results.jpg) | "Routing: Transitous · Map © OpenStreetMap, OpenFreeMap" | tappable at [#,#][#,#] leaves the usable area [#,#][#,#] | P2-en-light, P8-en-dark, P8-en-light (3) |
| [04-trip-results](shots/P2-he-light/04-trip-results.jpg) | "ניתוב: Transitous · מפה © OpenStreetMap, OpenFreeMap" | tappable at [#,#][#,#] leaves the usable area [#,#][#,#] | P2-he-light, P8-he-dark, P8-he-light (3) |
| [05-trip-second-option](shots/P2-en-light/05-trip-second-option.jpg) | "Routing: Transitous · Map © OpenStreetMap, OpenFreeMap" | tappable at [#,#][#,#] leaves the usable area [#,#][#,#] | P2-en-light, P8-en-dark, P8-en-light (3) |
| [05-trip-second-option](shots/P2-he-light/05-trip-second-option.jpg) | "ניתוב: Transitous · מפה © OpenStreetMap, OpenFreeMap" | tappable at [#,#][#,#] leaves the usable area [#,#][#,#] | P2-he-light, P8-he-dark, P8-he-light (3) |
| [17-update-banner-results](shots/P2-en-light/17-update-banner-results.jpg) | "Routing: Transitous · Map © OpenStreetMap, OpenFreeMap" | tappable at [#,#][#,#] leaves the usable area [#,#][#,#] | P2-en-light, P8-en-dark, P8-en-light (3) |
| [17-update-banner-results](shots/P2-he-light/17-update-banner-results.jpg) | "ניתוב: Transitous · מפה © OpenStreetMap, OpenFreeMap" | tappable at [#,#][#,#] leaves the usable area [#,#][#,#] | P2-he-light, P8-he-dark, P8-he-light (3) |
| [03-search-keyboard](shots/P1-he-dark/03-search-keyboard.jpg) | keyboard | # tappable items hidden behind the keyboard, e.g. "node##", "המיקום שלי", "תל אביב סבידור" | P1-he-dark, P2-he-light (2) |
| [03-search-keyboard](shots/P2-en-light/03-search-keyboard.jpg) | "רגבה" | tappable at [#,#][#,#] leaves the usable area [#,#][#,#] | P2-en-light, P2-he-light (2) |
| [03-search-keyboard](shots/P8-en-dark/03-search-keyboard.jpg) | keyboard | # tappable items hidden behind the keyboard, e.g. "From My location", "To Where to? (or long-press the map)", "⇅" | P8-en-dark, P8-en-light (2) |
| [03-search-keyboard](shots/P8-he-dark/03-search-keyboard.jpg) | keyboard | # tappable items hidden behind the keyboard, e.g. "מ־ המיקום שלי", "אל לאן? (או לחיצה ארוכה על המפה)", "⇅" | P8-he-dark, P8-he-light (2) |
| [03-search-keyboard](shots/P9-en-light/03-search-keyboard.jpg) | "עולי רגל" | tappable at [#,#][#,#] leaves the usable area [#,#][#,#] | P9-en-light, P9-he-light (2) |
| [07-trip-offline-banner](shots/P8-en-dark/07-trip-offline-banner.jpg) | "Routing: Transitous · Map © OpenStreetMap, OpenFreeMap" | tappable at [#,#][#,#] leaves the usable area [#,#][#,#] | P8-en-dark, P8-en-light (2) |
| [07-trip-offline-banner](shots/P8-he-dark/07-trip-offline-banner.jpg) | "ניתוב: Transitous · מפה © OpenStreetMap, OpenFreeMap" | tappable at [#,#][#,#] leaves the usable area [#,#][#,#] | P8-he-dark, P8-he-light (2) |
| [10-drop-off-fields](shots/P8-en-dark/10-drop-off-fields.jpg) | "אוניברסיטת בן גוריון" | tappable at [#,#][#,#] leaves the usable area [#,#][#,#] | P8-en-dark, P8-en-light (2) |
| [10-drop-off-fields](shots/P8-en-dark/10-drop-off-fields.jpg) | "תל אביב סבידור" | tappable at [#,#][#,#] leaves the usable area [#,#][#,#] | P8-en-dark, P8-en-light (2) |
| [10-drop-off-fields](shots/P8-en-dark/10-drop-off-fields.jpg) | "מיתר" | tappable at [#,#][#,#] leaves the usable area [#,#][#,#] | P8-en-dark, P8-en-light (2) |
| [11-drop-off-results](shots/P8-en-dark/11-drop-off-results.jpg) | "Routing: Transitous · Map © OpenStreetMap, OpenFreeMap" | tappable at [#,#][#,#] leaves the usable area [#,#][#,#] | P8-en-dark, P8-en-light (2) |
| [11-drop-off-results](shots/P8-he-dark/11-drop-off-results.jpg) | "ניתוב: Transitous · מפה © OpenStreetMap, OpenFreeMap" | tappable at [#,#][#,#] leaves the usable area [#,#][#,#] | P8-he-dark, P8-he-light (2) |
| [12-pick-up](shots/P8-en-dark/12-pick-up.jpg) | "Routing: Transitous · Map © OpenStreetMap, OpenFreeMap" | tappable at [#,#][#,#] leaves the usable area [#,#][#,#] | P8-en-dark, P8-en-light (2) |
| [12-pick-up](shots/P8-he-dark/12-pick-up.jpg) | "ניתוב: Transitous · מפה © OpenStreetMap, OpenFreeMap" | tappable at [#,#][#,#] leaves the usable area [#,#][#,#] | P8-he-dark, P8-he-light (2) |
| [13-stop-departures](shots/P8-en-dark/13-stop-departures.jpg) | "Routing: Transitous · Map © OpenStreetMap, OpenFreeMap" | tappable at [#,#][#,#] leaves the usable area [#,#][#,#] | P8-en-dark, P8-en-light (2) |
| [13-stop-departures](shots/P8-he-dark/13-stop-departures.jpg) | "ניתוב: Transitous · מפה © OpenStreetMap, OpenFreeMap" | tappable at [#,#][#,#] leaves the usable area [#,#][#,#] | P8-he-dark, P8-he-light (2) |

## R3 — text clipped, squeezed or a time/price cut off (60)

| state | where | detail | runs (n) |
|---|---|---|---|
| [04-trip-results](shots/P2-en-light/04-trip-results.jpg) | "370 at 08:37 from מרכז רפואי סורוקה/אוניברסיטת בן גוריון 1 t" | squeezed: 3 lines in 0 dp | P2-en-light, P3-en-light, P7-en-light (3) |
| [05-trip-second-option](shots/P2-en-light/05-trip-second-option.jpg) | "370 at 08:37 from מרכז רפואי סורוקה/אוניברסיטת בן גוריון 1 t" | squeezed: 3 lines in 0 dp | P2-en-light, P3-en-light, P7-en-light (3) |
| [07-trip-offline-banner](shots/P2-en-light/07-trip-offline-banner.jpg) | "370 at 08:37 from מרכז רפואי סורוקה/אוניברסיטת בן גוריון 1 t" | squeezed: 3 lines in 0 dp | P2-en-light, P3-en-light, P7-en-light (3) |
| [17-update-banner-results](shots/P2-en-light/17-update-banner-results.jpg) | "370 at 08:37 from מרכז רפואי סורוקה/אוניברסיטת בן גוריון 1 t" | squeezed: 3 lines in 0 dp | P2-en-light, P3-en-light, P7-en-light (3) |
| [18-save-trip-dialog](shots/P2-en-light/18-save-trip-dialog.jpg) | "370 at 08:37 from מרכז רפואי סורוקה/אוניברסיטת בן גוריון 1 t" | squeezed: 3 lines in 0 dp | P2-en-light, P3-en-light, P7-en-light (3) |
| [04-trip-results](shots/P2-he-light/04-trip-results.jpg) | "⁨370⁩ ב־⁨08:37⁩ מ־מרכז רפואי סורוקה/אוניברסיטת בן גוריון החל" | squeezed: 3 lines in 0 dp | P2-he-light, P3-he-light (2) |
| [05-trip-second-option](shots/P2-he-light/05-trip-second-option.jpg) | "⁨370⁩ ב־⁨08:37⁩ מ־מרכז רפואי סורוקה/אוניברסיטת בן גוריון החל" | squeezed: 3 lines in 0 dp | P2-he-light, P3-he-light (2) |
| [07-trip-offline-banner](shots/P2-he-light/07-trip-offline-banner.jpg) | "⁨370⁩ ב־⁨08:37⁩ מ־מרכז רפואי סורוקה/אוניברסיטת בן גוריון החל" | squeezed: 3 lines in 0 dp | P2-he-light, P3-he-light (2) |
| [07-trip-offline-banner](shots/P8-en-dark/07-trip-offline-banner.jpg) | "370 at 08:37 from מרכז רפואי סורוקה/אוניברסיטת בן גוריון 1 t" | squeezed: 2 lines in 0 dp | P8-en-dark, P8-en-light (2) |
| [07-trip-offline-banner](shots/P8-he-dark/07-trip-offline-banner.jpg) | "⁨370⁩ ב־⁨08:37⁩ מ־מרכז רפואי סורוקה/אוניברסיטת בן גוריון החל" | squeezed: 2 lines in 0 dp | P8-he-dark, P8-he-light (2) |
| [17-update-banner-results](shots/P2-he-light/17-update-banner-results.jpg) | "⁨370⁩ ב־⁨08:37⁩ מ־מרכז רפואי סורוקה/אוניברסיטת בן גוריון החל" | squeezed: 3 lines in 0 dp | P2-he-light, P3-he-light (2) |
| [18-save-trip-dialog](shots/P2-he-light/18-save-trip-dialog.jpg) | "⁨370⁩ ב־⁨08:37⁩ מ־מרכז רפואי סורוקה/אוניברסיטת בן גוריון החל" | squeezed: 3 lines in 0 dp | P2-he-light, P3-he-light (2) |
| [04-trip-results](shots/P5-en-light/04-trip-results.jpg) | "Train at 08:34 from באר שבע צפון Direct · walk 14 min" | squeezed: 3 lines in 0 dp | P5-en-light (1) |
| [04-trip-results](shots/P5-he-light/04-trip-results.jpg) | "מסלולים" | squeezed: 2 lines in 88 dp | P5-he-light (1) |
| [04-trip-results](shots/P5-he-light/04-trip-results.jpg) | "⁨≈ ₪30.5⁩" | text is clipped: box 93×32 dp, text 93×32 dp, 1 lines (width) | P5-he-light (1) |
| [04-trip-results](shots/P5-he-light/04-trip-results.jpg) | "⁨רכבת⁩ ב־⁨08:34⁩ מ־באר שבע צפון ישיר · הליכה ⁨14⁩ דק׳" | squeezed: 2 lines in 0 dp | P5-he-light (1) |
| [04-trip-results](shots/P9-en-light/04-trip-results.jpg) | "Train at 08:48 from באר שבע צפון Direct · walk 14 min" | squeezed: 2 lines in 0 dp | P9-en-light (1) |
| [04-trip-results](shots/P9-he-light/04-trip-results.jpg) | "⁨רכבת⁩ ב־⁨08:48⁩ מ־באר שבע צפון ישיר · הליכה ⁨14⁩ דק׳" | squeezed: 2 lines in 0 dp | P9-he-light (1) |
| [05-trip-second-option](shots/P5-en-light/05-trip-second-option.jpg) | "Train at 08:34 from באר שבע צפון Direct · walk 14 min" | squeezed: 3 lines in 0 dp | P5-en-light (1) |
| [05-trip-second-option](shots/P5-he-light/05-trip-second-option.jpg) | "מסלולים" | squeezed: 2 lines in 88 dp | P5-he-light (1) |
| [05-trip-second-option](shots/P5-he-light/05-trip-second-option.jpg) | "⁨≈ ₪30.5⁩" | text is clipped: box 93×32 dp, text 93×32 dp, 1 lines (width) | P5-he-light (1) |
| [05-trip-second-option](shots/P5-he-light/05-trip-second-option.jpg) | "⁨רכבת⁩ ב־⁨08:34⁩ מ־באר שבע צפון ישיר · הליכה ⁨14⁩ דק׳" | squeezed: 2 lines in 0 dp | P5-he-light (1) |
| [05-trip-second-option](shots/P9-en-light/05-trip-second-option.jpg) | "Train at 08:48 from באר שבע צפון Direct · walk 14 min" | squeezed: 2 lines in 0 dp | P9-en-light (1) |
| [05-trip-second-option](shots/P9-he-light/05-trip-second-option.jpg) | "⁨רכבת⁩ ב־⁨08:48⁩ מ־באר שבע צפון ישיר · הליכה ⁨14⁩ דק׳" | squeezed: 2 lines in 0 dp | P9-he-light (1) |
| [07-trip-offline-banner](shots/P5-en-light/07-trip-offline-banner.jpg) | "Train at 08:34 from באר שבע צפון Direct · walk 14 min" | squeezed: 3 lines in 0 dp | P5-en-light (1) |
| [07-trip-offline-banner](shots/P5-he-light/07-trip-offline-banner.jpg) | "מסלולים" | squeezed: 2 lines in 88 dp | P5-he-light (1) |
| [07-trip-offline-banner](shots/P5-he-light/07-trip-offline-banner.jpg) | "⁨≈ ₪30.5⁩" | text is clipped: box 93×32 dp, text 93×32 dp, 1 lines (width) | P5-he-light (1) |
| [07-trip-offline-banner](shots/P5-he-light/07-trip-offline-banner.jpg) | "⁨רכבת⁩ ב־⁨08:34⁩ מ־באר שבע צפון ישיר · הליכה ⁨14⁩ דק׳" | squeezed: 2 lines in 0 dp | P5-he-light (1) |
| [07-trip-offline-banner](shots/P9-en-light/07-trip-offline-banner.jpg) | "Train at 08:48 from באר שבע צפון Direct · walk 14 min" | squeezed: 2 lines in 0 dp | P9-en-light (1) |
| [07-trip-offline-banner](shots/P9-he-light/07-trip-offline-banner.jpg) | "⁨רכבת⁩ ב־⁨08:48⁩ מ־באר שבע צפון ישיר · הליכה ⁨14⁩ דק׳" | squeezed: 2 lines in 0 dp | P9-he-light (1) |
| [08-better-start](shots/P2-en-light/08-better-start.jpg) | "07:55–09:27" | squeezed: 3 lines in 64 dp | P2-en-light (1) |
| [08-better-start](shots/P2-he-light/08-better-start.jpg) | "07:55–09:27" | squeezed: 2 lines in 93 dp | P2-he-light (1) |
| [08-better-start](shots/P3-en-light/08-better-start.jpg) | "07:55–09:27" | squeezed: 2 lines in 84 dp | P3-en-light (1) |
| [08-better-start](shots/P5-en-light/08-better-start.jpg) | "07:55–09:27" | text is clipped: box 0×465 dp, text 18×465 dp, 11 lines (width) | P5-en-light (1) |
| [08-better-start](shots/P5-en-light/08-better-start.jpg) | "07:55–09:27" | squeezed: 11 lines in 0 dp | P5-en-light (1) |
| [08-better-start](shots/P5-he-light/08-better-start.jpg) | "07:55–09:27" | squeezed: 9 lines in 28 dp | P5-he-light (1) |
| [08-better-start](shots/P5-he-light/08-better-start.jpg) | "⁨≈ ₪30.5⁩" | text is clipped: box 93×32 dp, text 93×32 dp, 1 lines (width) | P5-he-light (1) |
| [12-pick-up](shots/P3-en-light/12-pick-up.jpg) | "Home at 10:04" | squeezed: 2 lines in 0 dp | P3-en-light (1) |
| [12-pick-up](shots/P5-en-light/12-pick-up.jpg) | "Home at 09:46" | text is clipped: box 0×550 dp, text 24×550 dp, 13 lines (width) | P5-en-light (1) |
| [12-pick-up](shots/P5-en-light/12-pick-up.jpg) | "Home at 09:46" | squeezed: 13 lines in 0 dp | P5-en-light (1) |
| [12-pick-up](shots/P5-en-light/12-pick-up.jpg) | "Driver leaves at 08:57 · round trip 49 min" | squeezed: 2 lines in 0 dp | P5-en-light (1) |
| [12-pick-up](shots/P5-he-light/12-pick-up.jpg) | "בבית ב־⁨09:46⁩" | squeezed: 8 lines in 28 dp | P5-he-light (1) |
| [12-pick-up](shots/P5-he-light/12-pick-up.jpg) | "הנהג/ת יוצא/ת ב־⁨08:57⁩ · הלוך ושוב ⁨49⁩ דק׳" | squeezed: 2 lines in 0 dp | P5-he-light (1) |
| [12-pick-up](shots/P5-he-light/12-pick-up.jpg) | "⁨≈ ₪30.5⁩" | text is clipped: box 93×32 dp, text 93×32 dp, 1 lines (width) | P5-he-light (1) |
| [14-settings-top](shots/P7-en-light/14-settings-top.jpg) | "No area downloaded. Move the map to your area, then download" | squeezed: 2 lines in 0 dp | P7-en-light (1) |
| [14-settings-top](shots/P9-en-light/14-settings-top.jpg) | "Free map routing assumes empty roads. Drive times at Sun–Thu" | squeezed: 2 lines in 0 dp | P9-en-light (1) |
| [15-settings-bottom](shots/P5-en-light/15-settings-bottom.jpg) | "Rush-hour drive factor: ×1.3" | squeezed: 2 lines in 0 dp | P5-en-light (1) |
| [15-settings-bottom](shots/P5-he-light/15-settings-bottom.jpg) | "מקדם עומס בשעות שיא: ×⁨1.3⁩" | squeezed: 2 lines in 0 dp | P5-he-light (1) |
| [17-update-banner-results](shots/P5-en-light/17-update-banner-results.jpg) | "Train at 08:34 from באר שבע צפון Direct · walk 14 min" | squeezed: 3 lines in 0 dp | P5-en-light (1) |
| [17-update-banner-results](shots/P5-he-light/17-update-banner-results.jpg) | "מסלולים" | squeezed: 2 lines in 88 dp | P5-he-light (1) |
| [17-update-banner-results](shots/P5-he-light/17-update-banner-results.jpg) | "⁨≈ ₪30.5⁩" | text is clipped: box 93×32 dp, text 93×32 dp, 1 lines (width) | P5-he-light (1) |
| [17-update-banner-results](shots/P5-he-light/17-update-banner-results.jpg) | "⁨רכבת⁩ ב־⁨08:34⁩ מ־באר שבע צפון ישיר · הליכה ⁨14⁩ דק׳" | squeezed: 2 lines in 0 dp | P5-he-light (1) |
| [17-update-banner-results](shots/P9-en-light/17-update-banner-results.jpg) | "Train at 08:48 from באר שבע צפון Direct · walk 14 min" | squeezed: 2 lines in 0 dp | P9-en-light (1) |
| [17-update-banner-results](shots/P9-he-light/17-update-banner-results.jpg) | "⁨רכבת⁩ ב־⁨08:48⁩ מ־באר שבע צפון ישיר · הליכה ⁨14⁩ דק׳" | squeezed: 2 lines in 0 dp | P9-he-light (1) |
| [18-save-trip-dialog](shots/P5-en-light/18-save-trip-dialog.jpg) | "Train at 08:34 from באר שבע צפון Direct · walk 14 min" | squeezed: 3 lines in 0 dp | P5-en-light (1) |
| [18-save-trip-dialog](shots/P5-he-light/18-save-trip-dialog.jpg) | "מסלולים" | squeezed: 2 lines in 88 dp | P5-he-light (1) |
| [18-save-trip-dialog](shots/P5-he-light/18-save-trip-dialog.jpg) | "⁨≈ ₪30.5⁩" | text is clipped: box 93×32 dp, text 93×32 dp, 1 lines (width) | P5-he-light (1) |
| [18-save-trip-dialog](shots/P5-he-light/18-save-trip-dialog.jpg) | "⁨רכבת⁩ ב־⁨08:34⁩ מ־באר שבע צפון ישיר · הליכה ⁨14⁩ דק׳" | squeezed: 2 lines in 0 dp | P5-he-light (1) |
| [18-save-trip-dialog](shots/P9-en-light/18-save-trip-dialog.jpg) | "Train at 08:48 from באר שבע צפון Direct · walk 14 min" | squeezed: 2 lines in 0 dp | P9-en-light (1) |
| [18-save-trip-dialog](shots/P9-he-light/18-save-trip-dialog.jpg) | "⁨רכבת⁩ ב־⁨08:48⁩ מ־באר שבע צפון ישיר · הליכה ⁨14⁩ דק׳" | squeezed: 2 lines in 0 dp | P9-he-light (1) |

## R4 — touch target under 48×48 dp (153)

| state | where | detail | runs (n) |
|---|---|---|---|
| [03-search-keyboard](shots/P1-en-dark/03-search-keyboard.jpg) | "גבעת רגע" | 336×8 dp; its 48 dp tap area reaches "רגואן" | P1-en-dark, P1-en-light, P1-he-dark, P1-he-light (4) |
| [03-search-keyboard](shots/P4-en-dark/03-search-keyboard.jpg) | "עולי רגל" | 360×7 dp; its 48 dp tap area reaches "רגל הפיל" | P4-en-dark, P4-en-light, P4-he-dark, P4-he-light (4) |
| [03-search-keyboard](shots/P8-en-dark/03-search-keyboard.jpg) | "הגר" | 890×3 dp; its 48 dp tap area reaches "תל אביב סבידור" | P8-en-dark, P8-en-light, P8-he-dark, P8-he-light (4) |
| [03-search-keyboard](shots/P2-en-light/03-search-keyboard.jpg) | "רגואן" | 336×13 dp; its 48 dp tap area reaches "רגבה" | P2-en-light, P2-he-light (2) |
| [03-search-keyboard](shots/P3-en-light/03-search-keyboard.jpg) | "רגל הפיל" | 296×16 dp; its 48 dp tap area reaches "גבעת רגע" | P3-en-light, P3-he-light (2) |
| [03-search-keyboard](shots/P6-en-light/03-search-keyboard.jpg) | "עולי רגל" | 387×10 dp; its 48 dp tap area reaches "רגל הפיל" | P6-en-light, P6-he-light (2) |
| [03-search-keyboard](shots/P7-en-light/03-search-keyboard.jpg) | "עולי רגל" | 320×10 dp; its 48 dp tap area reaches "רגל הפיל" | P7-en-light, P7-he-light (2) |
| [03-search-keyboard](shots/P8-en-dark/03-search-keyboard.jpg) | "הגר" | 890×3 dp; its 48 dp tap area reaches "Routing: Transitous · Map © OpenStreetMap, OpenFreeMap" | P8-en-dark, P8-en-light (2) |
| [03-search-keyboard](shots/P8-en-dark/03-search-keyboard.jpg) | "Routing: Transitous · Map © OpenStreetMap, OpenFreeMap" | 325×24 dp; its 48 dp tap area reaches "My location" | P8-en-dark, P8-en-light (2) |
| [03-search-keyboard](shots/P8-he-dark/03-search-keyboard.jpg) | "הגר" | 890×3 dp; its 48 dp tap area reaches "ניתוב: Transitous · מפה © OpenStreetMap, OpenFreeMap" | P8-he-dark, P8-he-light (2) |
| [03-search-keyboard](shots/P8-he-dark/03-search-keyboard.jpg) | "ניתוב: Transitous · מפה © OpenStreetMap, OpenFreeMap" | 309×24 dp; its 48 dp tap area reaches "המיקום שלי" | P8-he-dark, P8-he-light (2) |
| [03-search-keyboard](shots/P9-en-light/03-search-keyboard.jpg) | "עולי רגל" | 817×10 dp; its 48 dp tap area reaches "רגל הפיל" | P9-en-light, P9-he-light (2) |
| [04-trip-results](shots/P1-en-dark/04-trip-results.jpg) | "Pick me up" | 104×32 dp; its 48 dp tap area reaches "Routing: Transitous · Map © OpenStreetMap, OpenFreeMap" | P1-en-dark, P1-en-light (2) |
| [04-trip-results](shots/P1-en-dark/04-trip-results.jpg) | "Routing: Transitous · Map © OpenStreetMap, OpenFreeMap" | 331×24 dp; its 48 dp tap area reaches "Pick me up" | P1-en-dark, P1-en-light (2) |
| [04-trip-results](shots/P1-he-dark/04-trip-results.jpg) | "הורידו אותי בדרך" | 133×32 dp; its 48 dp tap area reaches "ניתוב: Transitous · מפה © OpenStreetMap, OpenFreeMap" | P1-he-dark, P1-he-light (2) |
| [04-trip-results](shots/P1-he-dark/04-trip-results.jpg) | "אספו אותי" | 93×32 dp; its 48 dp tap area reaches "ניתוב: Transitous · מפה © OpenStreetMap, OpenFreeMap" | P1-he-dark, P1-he-light (2) |
| [04-trip-results](shots/P1-he-dark/04-trip-results.jpg) | "ניתוב: Transitous · מפה © OpenStreetMap, OpenFreeMap" | 316×24 dp; its 48 dp tap area reaches "הורידו אותי בדרך" | P1-he-dark, P1-he-light (2) |
| [04-trip-results](shots/P1-he-dark/04-trip-results.jpg) | "ניתוב: Transitous · מפה © OpenStreetMap, OpenFreeMap" | 316×24 dp; its 48 dp tap area reaches "אספו אותי" | P1-he-dark, P1-he-light (2) |
| [04-trip-results](shots/P4-en-dark/04-trip-results.jpg) | "Routing: Transitous · Map © OpenStreetMap, OpenFreeMap" | 326×24 dp; its 48 dp tap area reaches "History" | P4-en-dark, P4-en-light (2) |
| [04-trip-results](shots/P4-he-dark/04-trip-results.jpg) | "ניתוב: Transitous · מפה © OpenStreetMap, OpenFreeMap" | 311×24 dp; its 48 dp tap area reaches "היסטוריה" | P4-he-dark, P4-he-light (2) |
| [05-trip-second-option](shots/P1-en-dark/05-trip-second-option.jpg) | "Pick me up" | 104×32 dp; its 48 dp tap area reaches "Routing: Transitous · Map © OpenStreetMap, OpenFreeMap" | P1-en-dark, P1-en-light (2) |
| [05-trip-second-option](shots/P1-en-dark/05-trip-second-option.jpg) | "Routing: Transitous · Map © OpenStreetMap, OpenFreeMap" | 331×24 dp; its 48 dp tap area reaches "Pick me up" | P1-en-dark, P1-en-light (2) |
| [05-trip-second-option](shots/P1-he-dark/05-trip-second-option.jpg) | "הורידו אותי בדרך" | 133×32 dp; its 48 dp tap area reaches "ניתוב: Transitous · מפה © OpenStreetMap, OpenFreeMap" | P1-he-dark, P1-he-light (2) |
| [05-trip-second-option](shots/P1-he-dark/05-trip-second-option.jpg) | "אספו אותי" | 93×32 dp; its 48 dp tap area reaches "ניתוב: Transitous · מפה © OpenStreetMap, OpenFreeMap" | P1-he-dark, P1-he-light (2) |
| [05-trip-second-option](shots/P1-he-dark/05-trip-second-option.jpg) | "ניתוב: Transitous · מפה © OpenStreetMap, OpenFreeMap" | 316×24 dp; its 48 dp tap area reaches "הורידו אותי בדרך" | P1-he-dark, P1-he-light (2) |
| [05-trip-second-option](shots/P1-he-dark/05-trip-second-option.jpg) | "ניתוב: Transitous · מפה © OpenStreetMap, OpenFreeMap" | 316×24 dp; its 48 dp tap area reaches "אספו אותי" | P1-he-dark, P1-he-light (2) |
| [05-trip-second-option](shots/P4-en-dark/05-trip-second-option.jpg) | "Routing: Transitous · Map © OpenStreetMap, OpenFreeMap" | 326×24 dp; its 48 dp tap area reaches "History" | P4-en-dark, P4-en-light (2) |
| [05-trip-second-option](shots/P4-he-dark/05-trip-second-option.jpg) | "ניתוב: Transitous · מפה © OpenStreetMap, OpenFreeMap" | 311×24 dp; its 48 dp tap area reaches "היסטוריה" | P4-he-dark, P4-he-light (2) |
| [06-trip-network-error](shots/P8-en-dark/06-trip-network-error.jpg) | "Routing: Transitous · Map © OpenStreetMap, OpenFreeMap" | 325×24 dp; its 48 dp tap area reaches "To תל אביב סבידור" | P8-en-dark, P8-en-light (2) |
| [06-trip-network-error](shots/P8-en-dark/06-trip-network-error.jpg) | "Routing: Transitous · Map © OpenStreetMap, OpenFreeMap" | 325×24 dp; its 48 dp tap area reaches "Now" | P8-en-dark, P8-en-light (2) |
| [06-trip-network-error](shots/P8-en-dark/06-trip-network-error.jpg) | "Routing: Transitous · Map © OpenStreetMap, OpenFreeMap" | 325×24 dp; its 48 dp tap area reaches "Depart" | P8-en-dark, P8-en-light (2) |
| [06-trip-network-error](shots/P8-en-dark/06-trip-network-error.jpg) | "Routing: Transitous · Map © OpenStreetMap, OpenFreeMap" | 325×24 dp; its 48 dp tap area reaches "Arrive by" | P8-en-dark, P8-en-light (2) |
| [06-trip-network-error](shots/P8-he-dark/06-trip-network-error.jpg) | "ניתוב: Transitous · מפה © OpenStreetMap, OpenFreeMap" | 309×24 dp; its 48 dp tap area reaches "אל תל אביב סבידור" | P8-he-dark, P8-he-light (2) |
| [06-trip-network-error](shots/P8-he-dark/06-trip-network-error.jpg) | "ניתוב: Transitous · מפה © OpenStreetMap, OpenFreeMap" | 309×24 dp; its 48 dp tap area reaches "עכשיו" | P8-he-dark, P8-he-light (2) |
| [06-trip-network-error](shots/P8-he-dark/06-trip-network-error.jpg) | "ניתוב: Transitous · מפה © OpenStreetMap, OpenFreeMap" | 309×24 dp; its 48 dp tap area reaches "יציאה" | P8-he-dark, P8-he-light (2) |
| [06-trip-network-error](shots/P8-he-dark/06-trip-network-error.jpg) | "ניתוב: Transitous · מפה © OpenStreetMap, OpenFreeMap" | 309×24 dp; its 48 dp tap area reaches "הגעה עד" | P8-he-dark, P8-he-light (2) |
| [07-trip-offline-banner](shots/P1-en-dark/07-trip-offline-banner.jpg) | "Routing: Transitous · Map © OpenStreetMap, OpenFreeMap" | 331×24 dp; its 48 dp tap area reaches "⇅" | P1-en-dark, P1-en-light (2) |
| [07-trip-offline-banner](shots/P1-he-dark/07-trip-offline-banner.jpg) | "ניתוב: Transitous · מפה © OpenStreetMap, OpenFreeMap" | 316×24 dp; its 48 dp tap area reaches "⇅" | P1-he-dark, P1-he-light (2) |
| [07-trip-offline-banner](shots/P4-en-dark/07-trip-offline-banner.jpg) | "Routing: Transitous · Map © OpenStreetMap, OpenFreeMap" | 326×24 dp; its 48 dp tap area reaches "Now" | P4-en-dark, P4-en-light (2) |
| [07-trip-offline-banner](shots/P4-en-dark/07-trip-offline-banner.jpg) | "Routing: Transitous · Map © OpenStreetMap, OpenFreeMap" | 326×24 dp; its 48 dp tap area reaches "Depart" | P4-en-dark, P4-en-light (2) |
| [07-trip-offline-banner](shots/P4-en-dark/07-trip-offline-banner.jpg) | "Routing: Transitous · Map © OpenStreetMap, OpenFreeMap" | 326×24 dp; its 48 dp tap area reaches "Arrive by" | P4-en-dark, P4-en-light (2) |
| [07-trip-offline-banner](shots/P4-he-dark/07-trip-offline-banner.jpg) | "ניתוב: Transitous · מפה © OpenStreetMap, OpenFreeMap" | 311×24 dp; its 48 dp tap area reaches "עכשיו" | P4-he-dark, P4-he-light (2) |
| [07-trip-offline-banner](shots/P4-he-dark/07-trip-offline-banner.jpg) | "ניתוב: Transitous · מפה © OpenStreetMap, OpenFreeMap" | 311×24 dp; its 48 dp tap area reaches "יציאה" | P4-he-dark, P4-he-light (2) |
| [07-trip-offline-banner](shots/P4-he-dark/07-trip-offline-banner.jpg) | "ניתוב: Transitous · מפה © OpenStreetMap, OpenFreeMap" | 311×24 dp; its 48 dp tap area reaches "הגעה עד" | P4-he-dark, P4-he-light (2) |
| [07-trip-offline-banner](shots/P8-en-dark/07-trip-offline-banner.jpg) | "08:27–10:05 98 min Walk 10′ 370 Walk 5′ 171 Walk 7′ ⁨≈ ₪27⁩ " | 890×35 dp; its 48 dp tap area reaches "08:21–09:46 85 min Walk 13′ Train Walk 1′ ⁨≈ ₪30.5⁩ Train at" | P8-en-dark, P8-en-light (2) |
| [07-trip-offline-banner](shots/P8-he-dark/07-trip-offline-banner.jpg) | "08:27–10:05 ⁨98⁩ דק׳ הליכה 10′ 370 הליכה 5′ 171 הליכה 7′ ⁨≈ " | 890×35 dp; its 48 dp tap area reaches "08:21–09:46 ⁨85⁩ דק׳ הליכה 13′ רכבת הליכה 1′ ⁨≈ ₪30.5⁩ ⁨רכבת" | P8-he-dark, P8-he-light (2) |
| [08-better-start](shots/P1-en-dark/08-better-start.jpg) | "Now" | 62×32 dp; its 48 dp tap area reaches "Routing: Transitous · Map © OpenStreetMap, OpenFreeMap" | P1-en-dark, P1-en-light (2) |
| [08-better-start](shots/P1-en-dark/08-better-start.jpg) | "Depart" | 75×32 dp; its 48 dp tap area reaches "Routing: Transitous · Map © OpenStreetMap, OpenFreeMap" | P1-en-dark, P1-en-light (2) |
| [08-better-start](shots/P1-en-dark/08-better-start.jpg) | "Routing: Transitous · Map © OpenStreetMap, OpenFreeMap" | 331×24 dp; its 48 dp tap area reaches "Now" | P1-en-dark, P1-en-light (2) |
| [08-better-start](shots/P1-en-dark/08-better-start.jpg) | "Routing: Transitous · Map © OpenStreetMap, OpenFreeMap" | 331×24 dp; its 48 dp tap area reaches "Depart" | P1-en-dark, P1-en-light (2) |
| [08-better-start](shots/P1-he-dark/08-better-start.jpg) | "עכשיו" | 66×32 dp; its 48 dp tap area reaches "ניתוב: Transitous · מפה © OpenStreetMap, OpenFreeMap" | P1-he-dark, P1-he-light (2) |
| [08-better-start](shots/P1-he-dark/08-better-start.jpg) | "יציאה" | 66×32 dp; its 48 dp tap area reaches "ניתוב: Transitous · מפה © OpenStreetMap, OpenFreeMap" | P1-he-dark, P1-he-light (2) |
| [08-better-start](shots/P1-he-dark/08-better-start.jpg) | "ניתוב: Transitous · מפה © OpenStreetMap, OpenFreeMap" | 316×24 dp; its 48 dp tap area reaches "עכשיו" | P1-he-dark, P1-he-light (2) |
| [08-better-start](shots/P1-he-dark/08-better-start.jpg) | "ניתוב: Transitous · מפה © OpenStreetMap, OpenFreeMap" | 316×24 dp; its 48 dp tap area reaches "יציאה" | P1-he-dark, P1-he-light (2) |
| [09-better-start-none](shots/P8-en-dark/09-better-start-none.jpg) | "Routing: Transitous · Map © OpenStreetMap, OpenFreeMap" | 325×24 dp; its 48 dp tap area reaches "To תל אביב סבידור" | P8-en-dark, P8-en-light (2) |
| [09-better-start-none](shots/P8-en-dark/09-better-start-none.jpg) | "Routing: Transitous · Map © OpenStreetMap, OpenFreeMap" | 325×24 dp; its 48 dp tap area reaches "Now" | P8-en-dark, P8-en-light (2) |
| [09-better-start-none](shots/P8-en-dark/09-better-start-none.jpg) | "Routing: Transitous · Map © OpenStreetMap, OpenFreeMap" | 325×24 dp; its 48 dp tap area reaches "Depart" | P8-en-dark, P8-en-light (2) |
| [09-better-start-none](shots/P8-he-dark/09-better-start-none.jpg) | "ניתוב: Transitous · מפה © OpenStreetMap, OpenFreeMap" | 309×24 dp; its 48 dp tap area reaches "אל תל אביב סבידור" | P8-he-dark, P8-he-light (2) |
| [09-better-start-none](shots/P8-he-dark/09-better-start-none.jpg) | "ניתוב: Transitous · מפה © OpenStreetMap, OpenFreeMap" | 309×24 dp; its 48 dp tap area reaches "עכשיו" | P8-he-dark, P8-he-light (2) |
| [09-better-start-none](shots/P8-he-dark/09-better-start-none.jpg) | "ניתוב: Transitous · מפה © OpenStreetMap, OpenFreeMap" | 309×24 dp; its 48 dp tap area reaches "יציאה" | P8-he-dark, P8-he-light (2) |
| … | | 93 more | |

## R5 — Accessibility Test Framework (115)

| state | where | detail | runs (n) |
|---|---|---|---|
| [04-trip-results](shots/P1-en-dark/04-trip-results.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 3.68. This ratio is based on an estimated foreground color of #FFFFFF and an estimated background color of #1E88E5. Consider using colors that result in a contrast ratio greater than 4.50 for small | P1-en-dark, P1-en-light, P1-he-dark, P1-he-light, P2-en-light, P2-he-light … (24) |
| [05-trip-second-option](shots/P1-en-dark/05-trip-second-option.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 3.68. This ratio is based on an estimated foreground color of #FFFFFF and an estimated background color of #1E88E5. Consider using colors that result in a contrast ratio greater than 4.50 for small | P1-en-dark, P1-en-light, P1-he-dark, P1-he-light, P2-en-light, P2-he-light … (24) |
| [07-trip-offline-banner](shots/P1-en-dark/07-trip-offline-banner.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 3.68. This ratio is based on an estimated foreground color of #FFFFFF and an estimated background color of #1E88E5. Consider using colors that result in a contrast ratio greater than 4.50 for small | P1-en-dark, P1-en-light, P1-he-dark, P1-he-light, P2-en-light, P2-he-light … (24) |
| [11-drop-off-results](shots/P1-en-dark/11-drop-off-results.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 3.68. This ratio is based on an estimated foreground color of #FFFFFF and an estimated background color of #1E88E5. Consider using colors that result in a contrast ratio greater than 4.50 for small | P1-en-dark, P1-en-light, P1-he-dark, P1-he-light, P2-en-light, P2-he-light … (24) |
| [17-update-banner-results](shots/P1-en-dark/17-update-banner-results.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 3.68. This ratio is based on an estimated foreground color of #FFFFFF and an estimated background color of #1E88E5. Consider using colors that result in a contrast ratio greater than 4.50 for small | P1-en-dark, P1-en-light, P1-he-dark, P1-he-light, P2-en-light, P2-he-light … (24) |
| [18-save-trip-dialog](shots/P1-en-dark/18-save-trip-dialog.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 3.68. This ratio is based on an estimated foreground color of #FFFFFF and an estimated background color of #1E88E5. Consider using colors that result in a contrast ratio greater than 4.50 for small | P1-en-dark, P1-en-light, P1-he-dark, P1-he-light, P2-en-light, P2-he-light … (24) |
| [03-search-keyboard](shots/P1-en-dark/03-search-keyboard.jpg) | SpeakableTextPresentCheck | ERROR: This item may not have a label readable by screen readers. | P1-en-dark, P1-en-light, P1-he-dark, P1-he-light, P4-en-dark, P4-en-light … (20) |
| [03-search-keyboard](shots/P1-en-dark/03-search-keyboard.jpg) | DuplicateSpeakableTextCheck | WARNING: This clickable item's speakable text: "הגר" is identical to that of 1 other item(s). | P1-en-dark, P1-en-light, P1-he-dark, P1-he-light, P2-en-light, P2-he-light … (20) |
| [02-home-saved](shots/P1-en-light/02-home-saved.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 1.23. This ratio is based on an estimated foreground color of #1D1B20 and an estimated background color of #00000000. Consider using colors that result in a contrast ratio greater than 4.50 for sma | P1-en-light, P1-he-light, P2-en-light, P2-he-light, P3-en-light, P3-he-light … (18) |
| [14-settings-top](shots/P1-en-light/14-settings-top.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 1.23. This ratio is based on an estimated foreground color of #1D1B20 and an estimated background color of #00000000. Consider using colors that result in a contrast ratio greater than 4.50 for sma | P1-en-light, P1-he-light, P2-en-light, P2-he-light, P3-en-light, P3-he-light … (18) |
| [15-settings-bottom](shots/P1-en-light/15-settings-bottom.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 1.23. This ratio is based on an estimated foreground color of #1D1B20 and an estimated background color of #00000000. Consider using colors that result in a contrast ratio greater than 4.50 for sma | P1-en-light, P1-he-light, P2-en-light, P2-he-light, P3-en-light, P3-he-light … (18) |
| [16-history](shots/P1-en-light/16-history.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 1.23. This ratio is based on an estimated foreground color of #1D1B20 and an estimated background color of #00000000. Consider using colors that result in a contrast ratio greater than 4.50 for sma | P1-en-light, P1-he-light, P2-en-light, P2-he-light, P3-en-light, P3-he-light … (18) |
| [12-pick-up](shots/P1-en-dark/12-pick-up.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 3.68. This ratio is based on an estimated foreground color of #FFFFFF and an estimated background color of #1E88E5. Consider using colors that result in a contrast ratio greater than 4.50 for small | P1-en-dark, P1-en-light, P1-he-dark, P1-he-light, P2-he-light, P3-en-light … (17) |
| [01-home-empty](shots/P2-en-light/01-home-empty.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 2.27. This ratio is based on an estimated foreground color of #99959C and an estimated background color of #E6E0E9. Consider using colors that result in a contrast ratio greater than 4.50 for small | P2-en-light, P2-he-light, P3-en-light, P3-he-light, P4-en-light, P4-he-light … (16) |
| [02-home-saved](shots/P2-en-light/02-home-saved.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 2.27. This ratio is based on an estimated foreground color of #99959C and an estimated background color of #E6E0E9. Consider using colors that result in a contrast ratio greater than 4.50 for small | P2-en-light, P2-he-light, P3-en-light, P3-he-light, P4-en-light, P4-he-light … (16) |
| [03-search-keyboard](shots/P2-en-light/03-search-keyboard.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 2.27. This ratio is based on an estimated foreground color of #99959C and an estimated background color of #E6E0E9. Consider using colors that result in a contrast ratio greater than 4.50 for small | P2-en-light, P2-he-light, P3-en-light, P3-he-light, P4-en-light, P4-he-light … (16) |
| [10-drop-off-fields](shots/P1-en-light/10-drop-off-fields.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 1.23. This ratio is based on an estimated foreground color of #1D1B20 and an estimated background color of #00000000. Consider using colors that result in a contrast ratio greater than 4.50 for sma | P1-en-light, P1-he-light, P2-en-light, P2-he-light, P3-en-light, P3-he-light … (16) |
| [10-drop-off-fields](shots/P2-en-light/10-drop-off-fields.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 2.27. This ratio is based on an estimated foreground color of #99959C and an estimated background color of #E6E0E9. Consider using colors that result in a contrast ratio greater than 4.50 for small | P2-en-light, P2-he-light, P3-en-light, P3-he-light, P4-en-light, P4-he-light … (16) |
| [14-settings-top](shots/P2-en-light/14-settings-top.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 2.27. This ratio is based on an estimated foreground color of #99959C and an estimated background color of #E6E0E9. Consider using colors that result in a contrast ratio greater than 4.50 for small | P2-en-light, P2-he-light, P3-en-light, P3-he-light, P4-en-light, P4-he-light … (16) |
| [15-settings-bottom](shots/P2-en-light/15-settings-bottom.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 2.27. This ratio is based on an estimated foreground color of #99959C and an estimated background color of #E6E0E9. Consider using colors that result in a contrast ratio greater than 4.50 for small | P2-en-light, P2-he-light, P3-en-light, P3-he-light, P4-en-light, P4-he-light … (16) |
| [06-trip-network-error](shots/P1-en-light/06-trip-network-error.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 1.23. This ratio is based on an estimated foreground color of #1D1B20 and an estimated background color of #00000000. Consider using colors that result in a contrast ratio greater than 4.50 for sma | P1-en-light, P1-he-light, P3-en-light, P3-he-light, P4-en-light, P4-he-light … (14) |
| [13-stop-departures](shots/P3-en-light/13-stop-departures.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 2.27. This ratio is based on an estimated foreground color of #99959C and an estimated background color of #E6E0E9. Consider using colors that result in a contrast ratio greater than 4.50 for small | P3-en-light, P3-he-light, P4-en-light, P4-he-light, P5-en-light, P5-he-light … (12) |
| [17-update-banner-results](shots/P1-en-dark/17-update-banner-results.jpg) | DuplicateSpeakableTextCheck | WARNING: This clickable item's speakable text: "Close" is identical to that of 1 other item(s). | P1-en-dark, P1-en-light, P2-en-light, P3-en-light, P4-en-dark, P4-en-light … (10) |
| [17-update-banner-results](shots/P1-he-dark/17-update-banner-results.jpg) | DuplicateSpeakableTextCheck | WARNING: This clickable item's speakable text: "סגירה" is identical to that of 1 other item(s). | P1-he-dark, P1-he-light, P2-he-light, P3-he-light, P4-he-dark, P4-he-light … (10) |
| [01-home-empty](shots/P1-en-light/01-home-empty.jpg) | ImageContrastCheck | WARNING: The image's contrast ratio is 1.10. This ratio is based on an estimated foreground color of #F0F4F4F4 and an estimated background color of #E6FFFFFF. Consider increasing this ratio to 3.00 or greater. This item may be obscured by o | P1-en-light, P1-he-light, P2-en-light, P2-he-light, P9-en-light, P9-he-light (6) |
| [01-home-empty](shots/P1-en-light/01-home-empty.jpg) | ImageContrastCheck | WARNING: The image's contrast ratio is 1.12. This ratio is based on an estimated foreground color of #E9F4F7 and an estimated background color of #E6FFFFFF. Consider increasing this ratio to 3.00 or greater. This item may be obscured by oth | P1-en-light, P1-he-light, P2-en-light, P2-he-light, P9-en-light, P9-he-light (6) |
| [02-home-saved](shots/P1-en-light/02-home-saved.jpg) | ImageContrastCheck | WARNING: The image's contrast ratio is 1.10. This ratio is based on an estimated foreground color of #F0F4F4F4 and an estimated background color of #E6FFFFFF. Consider increasing this ratio to 3.00 or greater. This item may be obscured by o | P1-en-light, P1-he-light, P2-en-light, P2-he-light, P9-en-light, P9-he-light (6) |
| [02-home-saved](shots/P1-en-light/02-home-saved.jpg) | ImageContrastCheck | WARNING: The image's contrast ratio is 1.12. This ratio is based on an estimated foreground color of #E9F4F7 and an estimated background color of #E6FFFFFF. Consider increasing this ratio to 3.00 or greater. This item may be obscured by oth | P1-en-light, P1-he-light, P2-en-light, P2-he-light, P9-en-light, P9-he-light (6) |
| [10-drop-off-fields](shots/P1-en-light/10-drop-off-fields.jpg) | ImageContrastCheck | WARNING: The image's contrast ratio is 1.10. This ratio is based on an estimated foreground color of #F0F4F4F4 and an estimated background color of #E6FFFFFF. Consider increasing this ratio to 3.00 or greater. This item may be obscured by o | P1-en-light, P1-he-light, P2-en-light, P2-he-light, P9-en-light, P9-he-light (6) |
| [10-drop-off-fields](shots/P1-en-light/10-drop-off-fields.jpg) | ImageContrastCheck | WARNING: The image's contrast ratio is 1.12. This ratio is based on an estimated foreground color of #E9F4F7 and an estimated background color of #E6FFFFFF. Consider increasing this ratio to 3.00 or greater. This item may be obscured by oth | P1-en-light, P1-he-light, P2-en-light, P2-he-light, P9-en-light, P9-he-light (6) |
| [14-settings-top](shots/P1-en-light/14-settings-top.jpg) | ImageContrastCheck | WARNING: The image's contrast ratio is 1.10. This ratio is based on an estimated foreground color of #F0F4F4F4 and an estimated background color of #E6FFFFFF. Consider increasing this ratio to 3.00 or greater. This item may be obscured by o | P1-en-light, P1-he-light, P2-en-light, P2-he-light, P9-en-light, P9-he-light (6) |
| [14-settings-top](shots/P1-en-light/14-settings-top.jpg) | ImageContrastCheck | WARNING: The image's contrast ratio is 1.12. This ratio is based on an estimated foreground color of #E9F4F7 and an estimated background color of #E6FFFFFF. Consider increasing this ratio to 3.00 or greater. This item may be obscured by oth | P1-en-light, P1-he-light, P2-en-light, P2-he-light, P9-en-light, P9-he-light (6) |
| [15-settings-bottom](shots/P1-en-light/15-settings-bottom.jpg) | ImageContrastCheck | WARNING: The image's contrast ratio is 1.10. This ratio is based on an estimated foreground color of #F0F4F4F4 and an estimated background color of #E6FFFFFF. Consider increasing this ratio to 3.00 or greater. This item may be obscured by o | P1-en-light, P1-he-light, P2-en-light, P2-he-light, P9-en-light, P9-he-light (6) |
| [15-settings-bottom](shots/P1-en-light/15-settings-bottom.jpg) | ImageContrastCheck | WARNING: The image's contrast ratio is 1.12. This ratio is based on an estimated foreground color of #E9F4F7 and an estimated background color of #E6FFFFFF. Consider increasing this ratio to 3.00 or greater. This item may be obscured by oth | P1-en-light, P1-he-light, P2-en-light, P2-he-light, P9-en-light, P9-he-light (6) |
| [16-history](shots/P1-en-light/16-history.jpg) | ImageContrastCheck | WARNING: The image's contrast ratio is 1.10. This ratio is based on an estimated foreground color of #F0F4F4F4 and an estimated background color of #E6FFFFFF. Consider increasing this ratio to 3.00 or greater. This item may be obscured by o | P1-en-light, P1-he-light, P2-en-light, P2-he-light, P9-en-light, P9-he-light (6) |
| [16-history](shots/P1-en-light/16-history.jpg) | ImageContrastCheck | WARNING: The image's contrast ratio is 1.12. This ratio is based on an estimated foreground color of #E9F4F7 and an estimated background color of #E6FFFFFF. Consider increasing this ratio to 3.00 or greater. This item may be obscured by oth | P1-en-light, P1-he-light, P2-en-light, P2-he-light, P9-en-light, P9-he-light (6) |
| [17-update-banner-results](shots/P4-en-dark/17-update-banner-results.jpg) | SpeakableTextPresentCheck | ERROR: This item may not have a label readable by screen readers. | P4-en-dark, P4-en-light, P4-he-dark, P4-he-light, P9-en-light, P9-he-light (6) |
| [01-home-empty](shots/P4-en-dark/01-home-empty.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 2.71. This ratio is based on an estimated foreground color of #78757D and an estimated background color of #36343B. Consider using colors that result in a contrast ratio greater than 4.50 for small | P4-en-dark, P4-he-dark, P8-en-dark, P8-he-dark (4) |
| [02-home-saved](shots/P4-en-dark/02-home-saved.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 2.71. This ratio is based on an estimated foreground color of #78757D and an estimated background color of #36343B. Consider using colors that result in a contrast ratio greater than 4.50 for small | P4-en-dark, P4-he-dark, P8-en-dark, P8-he-dark (4) |
| [03-search-keyboard](shots/P1-en-light/03-search-keyboard.jpg) | ImageContrastCheck | WARNING: The image's contrast ratio is 1.07. This ratio is based on an estimated foreground color of #F0F4F4F4 and an estimated background color of #FCFCFD. Consider increasing this ratio to 3.00 or greater. This item may be obscured by oth | P1-en-light, P1-he-light, P2-en-light, P2-he-light (4) |
| [03-search-keyboard](shots/P1-en-light/03-search-keyboard.jpg) | ImageContrastCheck | WARNING: The image's contrast ratio is 1.09. This ratio is based on an estimated foreground color of #E9F4F7 and an estimated background color of #FCFCFD. Consider increasing this ratio to 3.00 or greater. This item may be obscured by other | P1-en-light, P1-he-light, P2-en-light, P2-he-light (4) |
| [03-search-keyboard](shots/P4-en-dark/03-search-keyboard.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 2.71. This ratio is based on an estimated foreground color of #78757D and an estimated background color of #36343B. Consider using colors that result in a contrast ratio greater than 4.50 for small | P4-en-dark, P4-he-dark, P8-en-dark, P8-he-dark (4) |
| [07-trip-offline-banner](shots/P3-en-light/07-trip-offline-banner.jpg) | SpeakableTextPresentCheck | ERROR: This item may not have a label readable by screen readers. | P3-en-light, P3-he-light, P9-en-light, P9-he-light (4) |
| [10-drop-off-fields](shots/P4-en-dark/10-drop-off-fields.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 2.71. This ratio is based on an estimated foreground color of #78757D and an estimated background color of #36343B. Consider using colors that result in a contrast ratio greater than 4.50 for small | P4-en-dark, P4-he-dark, P8-en-dark, P8-he-dark (4) |
| [11-drop-off-results](shots/P3-en-light/11-drop-off-results.jpg) | SpeakableTextPresentCheck | ERROR: This item may not have a label readable by screen readers. | P3-en-light, P3-he-light, P9-en-light, P9-he-light (4) |
| [14-settings-top](shots/P4-en-dark/14-settings-top.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 2.71. This ratio is based on an estimated foreground color of #78757D and an estimated background color of #36343B. Consider using colors that result in a contrast ratio greater than 4.50 for small | P4-en-dark, P4-he-dark, P8-en-dark, P8-he-dark (4) |
| [15-settings-bottom](shots/P4-en-dark/15-settings-bottom.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 2.71. This ratio is based on an estimated foreground color of #78757D and an estimated background color of #36343B. Consider using colors that result in a contrast ratio greater than 4.50 for small | P4-en-dark, P4-he-dark, P8-en-dark, P8-he-dark (4) |
| [01-home-empty](shots/P1-en-dark/01-home-empty.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 1.81. This ratio is based on an estimated foreground color of #5C5A62 and an estimated background color of #36343B. Consider using colors that result in a contrast ratio greater than 4.50 for small | P1-en-dark, P1-he-dark (2) |
| [01-home-empty](shots/P1-en-dark/01-home-empty.jpg) | ImageContrastCheck | WARNING: The image's contrast ratio is 2.02. This ratio is based on an estimated foreground color of #1B5266 and an estimated background color of #80181A22. Consider increasing this ratio to 3.00 or greater. This item may be obscured by oth | P1-en-dark, P1-he-dark (2) |
| [01-home-empty](shots/P1-en-light/01-home-empty.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 1.59. This ratio is based on an estimated foreground color of #B7B3BB and an estimated background color of #E6E0E9. Consider using colors that result in a contrast ratio greater than 4.50 for small | P1-en-light, P1-he-light (2) |
| [02-home-saved](shots/P1-en-dark/02-home-saved.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 1.81. This ratio is based on an estimated foreground color of #5C5A62 and an estimated background color of #36343B. Consider using colors that result in a contrast ratio greater than 4.50 for small | P1-en-dark, P1-he-dark (2) |
| [02-home-saved](shots/P1-en-dark/02-home-saved.jpg) | ImageContrastCheck | WARNING: The image's contrast ratio is 2.02. This ratio is based on an estimated foreground color of #1B5266 and an estimated background color of #80181A22. Consider increasing this ratio to 3.00 or greater. This item may be obscured by oth | P1-en-dark, P1-he-dark (2) |
| [02-home-saved](shots/P1-en-light/02-home-saved.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 1.59. This ratio is based on an estimated foreground color of #B7B3BB and an estimated background color of #E6E0E9. Consider using colors that result in a contrast ratio greater than 4.50 for small | P1-en-light, P1-he-light (2) |
| [03-search-keyboard](shots/P1-en-dark/03-search-keyboard.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 1.81. This ratio is based on an estimated foreground color of #5C5A62 and an estimated background color of #36343B. Consider using colors that result in a contrast ratio greater than 4.50 for small | P1-en-dark, P1-he-dark (2) |
| [03-search-keyboard](shots/P1-en-dark/03-search-keyboard.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 3.61. This ratio is based on an estimated foreground color of #7E7C85 and an estimated background color of #27272E. Consider using colors that result in a contrast ratio greater than 4.50 for small | P1-en-dark, P1-he-dark (2) |
| [03-search-keyboard](shots/P1-en-dark/03-search-keyboard.jpg) | ImageContrastCheck | WARNING: The image's contrast ratio is 1.73. This ratio is based on an estimated foreground color of #1B5266 and an estimated background color of #27272E. Consider increasing this ratio to 3.00 or greater. This item may be obscured by other | P1-en-dark, P1-he-dark (2) |
| [03-search-keyboard](shots/P1-en-light/03-search-keyboard.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 1.59. This ratio is based on an estimated foreground color of #B7B3BB and an estimated background color of #E6E0E9. Consider using colors that result in a contrast ratio greater than 4.50 for small | P1-en-light, P1-he-light (2) |
| [03-search-keyboard](shots/P1-en-light/03-search-keyboard.jpg) | TextContrastCheck | WARNING: The item's text contrast ratio is 1.18. This ratio is based on an estimated foreground color of #E9E9E9 and an estimated background color of #FCFCFD. Consider using colors that result in a contrast ratio greater than 4.50 for small | P1-en-light, P1-he-light (2) |
| [03-search-keyboard](shots/P9-en-light/03-search-keyboard.jpg) | ImageContrastCheck | WARNING: The image's contrast ratio is 1.10. This ratio is based on an estimated foreground color of #F0F4F4F4 and an estimated background color of #E6FFFFFF. Consider increasing this ratio to 3.00 or greater. This item may be obscured by o | P9-en-light, P9-he-light (2) |
| [03-search-keyboard](shots/P9-en-light/03-search-keyboard.jpg) | ImageContrastCheck | WARNING: The image's contrast ratio is 1.12. This ratio is based on an estimated foreground color of #E9F4F7 and an estimated background color of #E6FFFFFF. Consider increasing this ratio to 3.00 or greater. This item may be obscured by oth | P9-en-light, P9-he-light (2) |
| … | | 55 more | |

## R7 — route not in the visible map / map squeezed (24)

| state | where | detail | runs (n) |
|---|---|---|---|
| [04-trip-results](shots/P1-en-dark/04-trip-results.jpg) | map | only # dp of map left between the panels | P1-en-dark, P1-en-light, P1-he-dark, P1-he-light, P2-en-light, P2-he-light … (24) |
| [05-trip-second-option](shots/P1-en-dark/05-trip-second-option.jpg) | map | only # dp of map left between the panels | P1-en-dark, P1-en-light, P1-he-dark, P1-he-light, P2-en-light, P2-he-light … (24) |
| [07-trip-offline-banner](shots/P1-en-dark/07-trip-offline-banner.jpg) | map | only # dp of map left between the panels | P1-en-dark, P1-en-light, P1-he-dark, P1-he-light, P2-en-light, P2-he-light … (24) |
| [08-better-start](shots/P1-en-dark/08-better-start.jpg) | map | only # dp of map left between the panels | P1-en-dark, P1-en-light, P1-he-dark, P1-he-light, P2-en-light, P2-he-light … (24) |
| [11-drop-off-results](shots/P1-en-dark/11-drop-off-results.jpg) | map | only # dp of map left between the panels | P1-en-dark, P1-en-light, P1-he-dark, P1-he-light, P2-en-light, P2-he-light … (24) |
| [12-pick-up](shots/P1-en-dark/12-pick-up.jpg) | map | only # dp of map left between the panels | P1-en-dark, P1-en-light, P1-he-dark, P1-he-light, P2-en-light, P2-he-light … (24) |
| [17-update-banner-results](shots/P1-en-dark/17-update-banner-results.jpg) | map | only # dp of map left between the panels | P1-en-dark, P1-en-light, P1-he-dark, P1-he-light, P2-en-light, P2-he-light … (24) |
| [18-save-trip-dialog](shots/P1-en-dark/18-save-trip-dialog.jpg) | map | only # dp of map left between the panels | P1-en-dark, P1-en-light, P1-he-dark, P1-he-light, P2-en-light, P2-he-light … (24) |
| [04-trip-results](shots/P1-en-dark/04-trip-results.jpg) | route | route [#,#][#,#] is not inside the visible map #..# | P1-en-dark, P1-en-light, P1-he-dark, P1-he-light, P2-en-light, P2-he-light … (20) |
| [05-trip-second-option](shots/P1-en-dark/05-trip-second-option.jpg) | route | route [#,#][#,#] is not inside the visible map #..# | P1-en-dark, P1-en-light, P1-he-dark, P1-he-light, P2-en-light, P2-he-light … (20) |
| [07-trip-offline-banner](shots/P1-en-dark/07-trip-offline-banner.jpg) | route | route [#,#][#,#] is not inside the visible map #..# | P1-en-dark, P1-en-light, P1-he-dark, P1-he-light, P2-en-light, P2-he-light … (20) |
| [08-better-start](shots/P1-en-dark/08-better-start.jpg) | route | route [#,#][#,#] is not inside the visible map #..# | P1-en-dark, P1-en-light, P1-he-dark, P1-he-light, P2-en-light, P2-he-light … (20) |
| [11-drop-off-results](shots/P1-en-dark/11-drop-off-results.jpg) | route | route [#,#][#,#] is not inside the visible map #..# | P1-en-dark, P1-en-light, P1-he-dark, P1-he-light, P2-en-light, P2-he-light … (20) |
| [12-pick-up](shots/P1-en-dark/12-pick-up.jpg) | route | route [#,#][#,#] is not inside the visible map #..# | P1-en-dark, P1-en-light, P1-he-dark, P1-he-light, P2-en-light, P2-he-light … (20) |
| [17-update-banner-results](shots/P1-en-dark/17-update-banner-results.jpg) | route | route [#,#][#,#] is not inside the visible map #..# | P1-en-dark, P1-en-light, P1-he-dark, P1-he-light, P2-en-light, P2-he-light … (20) |
| [18-save-trip-dialog](shots/P1-en-dark/18-save-trip-dialog.jpg) | route | route [#,#][#,#] is not inside the visible map #..# | P1-en-dark, P1-en-light, P1-he-dark, P1-he-light, P2-en-light, P2-he-light … (20) |
| [04-trip-results](shots/P8-en-dark/04-trip-results.jpg) | route | route [-#,-#][#,#] is not inside the visible map #..# | P8-en-dark, P8-en-light, P8-he-dark, P8-he-light (4) |
| [05-trip-second-option](shots/P8-en-dark/05-trip-second-option.jpg) | route | route [#,-#][#,#] is not inside the visible map #..# | P8-en-dark, P8-en-light, P8-he-dark, P8-he-light (4) |
| [07-trip-offline-banner](shots/P8-en-dark/07-trip-offline-banner.jpg) | route | route [#,-#][#,#] is not inside the visible map #..# | P8-en-dark, P8-en-light, P8-he-dark, P8-he-light (4) |
| [08-better-start](shots/P8-en-dark/08-better-start.jpg) | route | route [-#,-#][#,#] is not inside the visible map #..# | P8-en-dark, P8-en-light, P8-he-dark, P8-he-light (4) |
| [11-drop-off-results](shots/P8-en-dark/11-drop-off-results.jpg) | route | route [-#,-#][#,#] is not inside the visible map #..# | P8-en-dark, P8-en-light, P8-he-dark, P8-he-light (4) |
| [12-pick-up](shots/P8-en-dark/12-pick-up.jpg) | route | route [-#,-#][#,#] is not inside the visible map #..# | P8-en-dark, P8-en-light, P8-he-dark, P8-he-light (4) |
| [17-update-banner-results](shots/P8-en-dark/17-update-banner-results.jpg) | route | route [-#,-#][#,#] is not inside the visible map #..# | P8-en-dark, P8-en-light, P8-he-dark, P8-he-light (4) |
| [18-save-trip-dialog](shots/P8-en-dark/18-save-trip-dialog.jpg) | route | route [-#,-#][#,#] is not inside the visible map #..# | P8-en-dark, P8-en-light, P8-he-dark, P8-he-light (4) |


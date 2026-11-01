# Bug bash: the release checklist

For the owner and the testers, on **real phones**, before a release is announced. The emulators in CI cover layout, journeys, upgrades and
crashes (`docs/testing/inventory.md` says what is and is not covered); they cannot ring a real alarm, ride a real bus or feel slow.
The boss links this file in each release's close-out. Tick a line, or write the problem on the issue form
(`.github/ISSUE_TEMPLATE/bug.yml`): app version, phone, what you did, what you expected, what happened, a screenshot.

Use the **signed APK from the GitHub release**, never a debug or CI build (a different key cannot be installed over it).

## 1. Install over the old version
- [ ] On a phone that has the previous release with saved places, a saved trip, a routine and some history: install the new APK **over it**. It must say "updated", not "App not installed".
- [ ] Open the app: places, saved trips, Home / Work, routines, My lines, history and settings are all still there. The language and theme are as before.
- [ ] A leave reminder set before the update still rings (the update re-arms it).
- [ ] The in-app banner "Version x is ready · Update" shows on the **old** build within a day (or after Settings → Check for updates) and installs the new one.

## 2. Every feature once (Trip, then the four car features)
- [ ] Trip: search by typing, by long-pressing the map, and from "my location"; open an option, tap a leg, see stops and the vehicle; Earlier / Later; the day chip (Tomorrow).
- [ ] Better start, Drop-off, Pick-up, Park & ride: one real search each, pick an option, "Send to driver" (the share sheet shows Waze and Google Maps links).
- [ ] Stop sheet and the day timetable; star a line (My lines) and open it.
- [ ] Share an address into the app from WhatsApp or Maps (a street address, and a `geo:` link).
- [ ] Settings: change a walking limit, the traffic factor, the fare profile; the "Pay with…" button opens a payment app or its store page.
- [ ] Monthly report (needs a finished month in History): the share sheet shows a CSV and the summary.
- [ ] Crash log: Settings → Crash log is empty after all of the above, or its entries are explained.

## 3. Hebrew and English, dark mode
- [ ] Settings → language: עברית, then English, then System. Nothing is cut off, mirrored wrongly or left in the other language; times and prices read left to right inside Hebrew text.
- [ ] Theme: Light, Dark, System. The map, the cards, the chips and the notification icons stay readable in both.
- [ ] Largest font size (phone Settings → Display): the results and the action row are still usable.

## 4. TalkBack basics
- [ ] Turn TalkBack on. Swipe through the search card, the tabs (they have names, not "button"), the results and an option's action row.
- [ ] A leg chip is read as a sentence (line, direction, times), not as a colour.
- [ ] The map is skipped or announced once, not a hundred stops. Back closes what is open.
- [ ] Every button can be double-tapped and is large enough to hit.

## 5. A reminder that really rings
- [ ] Settings → Check reminders: every row is green, or its hint opens the right settings page (notifications, exact alarms, battery, app standby, location).
- [ ] "Test reminder in 1 minute": lock the phone, put it in your pocket, wait. It rings once, with sound, on the lock screen.
- [ ] Set a real leave reminder for a trip 15–20 minutes away. Lock the phone. It rings at the leave time; the countdown notification counts down; "Missed it? Plan from here" appears if you do not leave.
- [ ] Restart the phone with a reminder pending. After boot the reminder is still armed (no need to open the app).

## 6. One real ride with the get-off alert
- [ ] Plan a real bus or train trip, tap **Start trip**, and ride it. The ongoing notification shows the stops left.
- [ ] The "get off at the next stop" alert fires once, a stop before yours, with sound and speech if enabled; not twice, not early at boarding.
- [ ] With the screen off and the app in the background the alert still comes. GPS stops when the ride ends (no lingering location icon).
- [ ] The trip appears in History afterwards, with the fare estimate.

## 7. Offline
- [ ] Airplane mode on, then open the app: the last results show with an "offline" banner; saved places and trips open; a new search says clearly it has no signal and Retry works when the signal is back.
- [ ] Your starred stops show their saved departures (the night refresh saved them, if you left the phone on Wi-Fi and charging).
- [ ] The offline map area (if you saved one) shows the map without signal.

## 8. Report anything with the issue form
- [ ] For each problem: open an issue with the **bug report** form (version, phone, steps, expected / happened, screenshot, the crash log if there is one). Tick that it holds no home address or personal places.
- [ ] Friends without a GitHub account: Settings → Crash log → Share crash log over WhatsApp.
- [ ] Before the release is announced the boss reads the open `bug` issues and names the fixed ones in the notes.

---
name: dead-ends
description: Approaches that were considered and rejected, with the reason. Load BEFORE proposing a map SDK, a routing API, a server, real-time source, or anything that costs money.
---

# Rejected — don't re-propose without new evidence

| idea | why not |
|---|---|
| Google Maps SDK / Directions / Places | needs a billing account (card). Free-only rule. |
| Moovit API | no free public API. |
| OSM's own tile servers (tile.openstreetmap.org) | tile usage policy forbids app traffic. OpenFreeMap allows it. |
| Oracle Cloud "Always Free" VM + OpenTripPlanner | sign-up needs a card; owner said no card. |
| Any own server in v1 | nothing to host = nothing to pay for or keep awake. Fallback only: MOTIS on the owner's PC + Cloudflare Tunnel. |
| MOT SIRI real-time directly | needs registration + a static IP (i.e. a server). (Transitous carries no MOT GTFS-RT either — see below.) |
| Play Store distribution | paid developer account; the app is for friends and family. |
| One `plan` request per candidate stop for "better start" | MOTIS does the drop-off search itself (`preTransitModes=CAR`); per-candidate plans would cost ~25 requests and break the Transitous traffic rule. |
| `CAR_DROPOFF` as the better-start / pick-up mode | Recorded on Transitous (2026-10-02): accepted, but drives ~4 min from a 0-s stub and post-transit answers end in a walk. `CAR` gives the real station drop-off. |
| Real-time for MOT lines via Transitous | not loaded (checked twice, 2026-10-03/04: 0 `realTime` in 105 departures); its `feeds/il.json` has GTFS-RT only for busofash. Revisit if Transitous adds MOT GTFS-RT. |
| `CAR_PARKING` for park & ride | Recorded on Transitous (2026-10-03, Meitar → Tel Aviv, 20-min cap): parks at unnamed OSM lots near bus stops, then walk + bus; never at a station; 20′ later than `CAR` to Be'er Sheva North. Pick stations from `RailStations` instead. |
| Raising the drop-off default detour above 10 min | Owner decided to keep 10 (2026-10-03). At peak, leaving the highway for a mid-route station costs 14–21 min (golden trip 8); the slider already lets the user allow more. |
| Firebase Blaze plan, BrowserStack, AWS Device Farm, Sauce Labs for device testing | need a card (or a paid plan). Test Lab on Spark + GitHub's free emulators cover it (skill `ui-testing`). |
| Pixel-diff golden screenshots from emulators as a gate | emulator rendering and fonts drift between images; the emulator layout audit checks rules instead. Paparazzi (JVM, deterministic) is the golden gate (B4). |
| Automatic crash reporting (Crashlytics, Sentry, ACRA to a backend) | needs a third-party account or a server of our own, and sends data without the user's tap. B4 keeps the last 5 crashes on the phone; the user shares them by hand. |
| Roborazzi (or a second screenshot library) | Paparazzi (#18) already renders every screen without an emulator; two libraries would mean two sets of goldens. B4 extended Paparazzi instead. |
| Keeping Android and core in one Gradle build | cloud sessions can't download the Android SDK, so the engine could no longer be tested there. |
| Rail platform numbers from `track` | Empty on every Israel Railways leg (fixtures + live plan, 2026-10-06, research PR #27). Bus-station platforms come from the stop `description` instead (Phase 9 C1). |
| Bike / scooter rentals (Tel-O-Fun etc.) via Transitous | `GET /api/v1/rentals` around Tel Aviv, 3 km (2026-10-06): 0 providers, 0 stations. No Israeli feed. |
| `refresh-itinerary` | Refreshes with real-time data, and MOT lines have none on Transitous. |
| AppCompat (`AppCompatDelegate.setApplicationLocales`) for the app language | C6, 2026-10-06: below Android 13 it only re-applies to `AppCompatActivity`, so it would mean moving `MainActivity` off `ComponentActivity` and the theme onto `Theme.AppCompat`, plus a new dependency. On 13+ it is the framework `LocaleManager` call `ui/AppLocale` makes directly; below, `AppLocale` wraps the base context as the back-port does. |

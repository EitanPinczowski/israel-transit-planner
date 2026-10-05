---
name: special-features
description: The car + transit features - better starting point, let me off on the way (drop-off), best pick-up point, park & ride - and the shared Pareto engine and traffic factor. Load before editing core/features or changing any of their constants.
---

# The four car + transit features

All live in `core/src/main/kotlin/il/transit/core/features/` and return a **Pareto front**
over (driver cost, passenger arrival, transfers) — `paretoFront()` in `Common.kt`. The UI
shows the front; the user picks. Never collapse it to one "best" answer: the owner asked
for the trade-off to be visible (e.g. "+4 min for the driver, arrive 18:40" vs "+0, 19:10").

## Better start (`BetterStart.kt`)
Someone drops you (and leaves) at a stop within the slider limit. One walk-only baseline
plus one `plan` per rung of `capLadder()` with `preTransitModes=CAR` (Transitous's
`CAR_DROPOFF` answers barely use the car — see the transitous-api skill). The ladder
(⅓, ⅔, full limit; rungs < 3 min dropped) is what turns MOTIS's (time, transfers) answer
into a driver-time trade-off. The widest rung runs first and alone: if a query asks for
`CAR_DROPOFF`, it is also the probe — on HTTP 400 the search switches to `CAR` for every
rung and reports `usedMode`. Drive cost = all non-zero car legs before the first transit
leg (`leadingCarLegs()`). Hence `BUDGET = 5` (baseline + refused probe + 3 rungs), pinned by a
test. Any other error propagates; a 500 must never be read as "mode unsupported".
Options must beat the baseline by `minGainMin` (5) or with
fewer transfers. The cap is divided by the traffic factor because MOTIS measures free
flow. MOTIS times the free-flow drive to reach the stop just as the vehicle leaves, so
`leaveAt` = itinerary start − (traffic delay − wait at the stop), never later than the
start. (A "tight" warning used to fire on every peak option; it is gone.)

## Let me off on the way (`DropOff.kt`)
You ride A→B and need C. Steps and their request cost (total pinned by `BUDGET = 10`):
1. car route A→B via `directModes=CAR`, `maxDirectTime` = 4 h (1) — geometry = corridor,
   duration = baseline T. Without `maxDirectTime` MOTIS drops drives over 30 min.
2. stops: drives > 15 km use the bundled `RailStations` (0 requests); shorter drives ask
   `map/stops` for the corridor bbox (1). Transitous ignores `modes`, so a long-drive
   fallback (empty list) filters to rail client-side.
3. `pickCandidates`: within `corridorM` (1.5 km) of the line, ≥ `minSpacingM` (2 km) from
   each other and from A and B; the drive is cut into `maxCandidates` (8) equal stretches
   and each takes its best stop (trains, then light rail, then importance). Ranking the
   whole corridor instead bunched every pick at the busy Tel Aviv end.
4. two `one-to-many` calls (A→s, s→B) with all 8 (2): detour = A→s + s→B − T, ×traffic,
   + 60 s stop
5. one `plan` s→C for at most `maxPlans` (4) stops within the detour limit, evenly
   `spread` through route order (≤ 4)
6. baselines: ride to B then transit, and transit from A (2)
If the car route fails, only the transit-from-A baseline is returned — never an error.
`RailStations` is generated: `python tools/gen_rail_stations.py` (MOT GTFS, not Transitous;
needs network access to gtfs.mot.gov.il). Re-run when a station or line opens; the test
pinning the Meitar → Tel Aviv picks may then need its list updated.
In the app, the drop-off tab's fields map as A = "From", B = "Driver to" (`UiState.driverTo`),
C = "I go to" (`UiState.to`); `UiState.readyToPlan` waits for all three.

## Sending the point to the driver (`NavLinks.kt`)
Waze (`waze.com/ul?ll=…&navigate=yes`) and Google Maps directions URLs, fixed six decimals
in `Locale.US`. Plain links via Android's share sheet — no SDK, no key. Drop-off and
pick-up tabs reuse them.

## Best pick-up (`PickUp.kt`)
Mirror of better start on the arrival side: `postTransitModes=CAR` with a cap ladder,
`BUDGET = 4`. Driver cost = round trip (2 × the car leg); `driverLeavesAt` is worked back
from the pick-up time. Home arrival is `pickUpTime + traffic-adjusted drive`, NOT MOTIS's
`endTime` (free flow). Same "worth it" rule as better start: ≥ `minGainMin` (5) earlier than
transit all the way, or fewer transfers. In the app, "Driver at" is `UiState.to`.

## Park & ride (`ParkRide.kt`)
Your own car to a train station, park, go on by train. `BUDGET = 5`, pinned by a test:
1. transit-only baseline (1);
2. bundled `RailStations`, trains only (light rail has no parking), with a straight-line
   pre-filter: ≥ 1.5 km away (closer = walk) and within cap × `MAX_SPEED_MPS` (30 m/s), the 12
   nearest (0). No "towards the destination" cut: Meitar's best station, Be'er Sheva North,
   is farther from Tel Aviv than Meitar is;
3. one `one-to-many` CAR from the origin, `max` = the slider limit ÷ traffic factor (1);
4. `choose`: the best `maxPlans` (3) reached stations by drive + crow-flies remainder at
   `RAIL_MPS` (20 m/s), ≥ 2 km apart (HaShalom and Tel Aviv Center would be one answer twice);
   a `plan` from each one's **stop id**, at drive (×traffic) + `parkSec` (5′) (≤ 3).
Every itinerary of those plans whose first vehicle is a train is an option (a bus from the
forecourt is not park & ride); `leaveAt` = its start − parkSec − drive, so you leave home
just in time for the train, not at the search time (recorded: parked 07:54, train 08:34 →
leave 08:10). Same "worth it" rule as better start. Driver cost = the drive.
The car part on the map is origin → station as a straight line: no route request fits the
budget. "Way back to my car" is `MainViewModel.wayBack` with the destination swapped for the
parked station, switching to the Trip tab (1 request); "drive home from there" is
`ParkRideOption.driveHomeSec`: the outbound free-flow drive × the traffic factor at the
way-back arrival — an estimate, no request (the reverse drive may differ).
In the app: `AppMode.PARK_RIDE`, slider 5–40 (default 20), `ui/ParkRideState.kt` (the one
`UiState.parkRide` field), `ui/screens/ParkRidePanel.kt`, `strings_park_ride.xml`.

### `CAR_PARKING` verdict (Phase 8 A1)
Recorded 2026-10-03 (`plan_car_parking_meitar`, pinned in `ApiPhase8Test`): Meitar → Tel Aviv
HaHagana, Monday 08:00, `preTransitModes=CAR_PARKING`, 20-min cap. **Unusable.** All 5
answers park at an unnamed OSM parking lot (`vertexType` NORMAL, no stop id) 2–13′ away,
walk up to 11′ to a bus stop, and ride a bus first — none parks at a station. Best arrives
10:06; `CAR` from the same place drives 15′ to Be'er Sheva North and is in Tel Aviv Center (a
stop past HaHagana) at 09:46. So B2 picks stations itself: bundled `RailStations`, drive times
from `one-to-many` CAR (traffic-adjusted), then `plan` from the chosen stations' stop ids.

## Traffic (`TrafficProfile`)
Free routers assume empty roads. ×1.3 Sun–Thu 07–09 and 16–18 Israel time, else ×1.0.
Editable in app settings (phase 1). If a golden trip shows drives systematically off,
change the profile, not the features.

## Changing a constant
Each default above has a reason written next to it. Changing one: update the test that
pins it, and re-run `golden-trips` — a unit test with a fake API cannot tell you whether
the answer got better on real roads.

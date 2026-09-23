---
name: special-features
description: The car + transit features - better starting point, let me off on the way (drop-off), best pick-up point - and the shared Pareto engine and traffic factor. Load before editing core/features or changing any of their constants.
---

# The three car + transit features

All live in `core/src/main/kotlin/il/transit/core/features/` and return a **Pareto front**
over (driver cost, passenger arrival, transfers) — `paretoFront()` in `Common.kt`. The UI
shows the front; the user picks. Never collapse it to one "best" answer: the owner asked
for the trade-off to be visible (e.g. "+4 min for the driver, arrive 18:40" vs "+0, 19:10").

## Better start (`BetterStart.kt`)
Someone drops you (and leaves) at a stop within the slider limit. One walk-only baseline
plus one `plan` per rung of `capLadder()` with `preTransitModes=CAR_DROPOFF`. The ladder
(⅓, ⅔, full limit; rungs < 3 min dropped) is what turns MOTIS's (time, transfers) answer
into a driver-time trade-off. The widest rung runs first and alone: it is also the probe
for `CAR_DROPOFF` support — on HTTP 400 the search switches to `CAR` for every rung and
reports `usedMode`. Hence `BUDGET = 5` (baseline + refused probe + 3 rungs), pinned by a
test. Any other error propagates; a 500 must never be read as "mode unsupported".
Options must beat the baseline by `minGainMin` (5) or with
fewer transfers. The cap is divided by the traffic factor because MOTIS measures free
flow. `tight` = the traffic-adjusted drive eats the slack before the first departure.

## Let me off on the way (`DropOff.kt`)
You ride A→B and need C. Steps and their request cost (total pinned by `BUDGET = 10`):
1. car route A→B via `directModes=CAR` (1) — geometry = corridor, duration = baseline T
2. `map/stops` in the corridor bbox (1); drives > 15 km ask for rail-like stops only
3. `pickCandidates`: within `corridorM` (1.5 km) of the line, rail first, then MOTIS
   importance, ≥ `minSpacingM` (2 km) apart, at most `maxCandidates` (4)
4. two `one-to-many` calls (A→s, s→B) (2): detour = A→s + s→B − T, ×traffic, + 60 s stop
5. one `plan` s→C per candidate within the detour limit (≤ 4)
6. baselines: ride to B then transit, and transit from A (2)
If the car route fails, only the transit-from-A baseline is returned — never an error.
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

## Traffic (`TrafficProfile`)
Free routers assume empty roads. ×1.3 Sun–Thu 07–09 and 16–18 Israel time, else ×1.0.
Editable in app settings (phase 1). If a golden trip shows drives systematically off,
change the profile, not the features.

## Changing a constant
Each default above has a reason written next to it. Changing one: update the test that
pins it, and re-run `golden-trips` — a unit test with a fake API cannot tell you whether
the answer got better on real roads.

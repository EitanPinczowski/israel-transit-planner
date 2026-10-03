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
| MOT SIRI real-time directly | needs registration + a static IP (i.e. a server). Transitous already ingests a GTFS-RT feed for Israel. |
| Play Store distribution | paid developer account; the app is for friends and family. |
| One `plan` request per candidate stop for "better start" | MOTIS does the drop-off search itself (`preTransitModes=CAR`); per-candidate plans would cost ~25 requests and break the Transitous traffic rule. |
| `CAR_DROPOFF` as the better-start / pick-up mode | Recorded on Transitous (2026-10-02): accepted, but drives ~4 min from a 0-s stub and post-transit answers end in a walk. `CAR` gives the real station drop-off. |
| Raising the drop-off default detour above 10 min | Owner decided to keep 10 (2026-10-03). At peak, leaving the highway for a mid-route station costs 14–21 min (golden trip 8); the slider already lets the user allow more. |
| Keeping Android and core in one Gradle build | cloud sessions can't download the Android SDK, so the engine could no longer be tested there. |

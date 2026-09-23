# Israel Transit Planner

A free Android trip planner for Israeli public transport — buses, Israel Railways and
light rail — with three car + transit features no mainstream app has:

1. **Better starting point** — someone drops you off within a drive-time you choose; the
   app finds the stop from which the whole trip is faster or has fewer transfers.
2. **Let me off on the way** — you're a passenger on a drive A→B and need to reach C; the
   app ranks where to get out by the driver's detour **and** your arrival at C.
3. **Best pick-up point** — for the way home: which stop to ride to so the person
   collecting you drives the least.

Personal project for the author, friends and family. No ads, no accounts, no tracking,
no server — the app talks directly to free, open services.

## Data and services (all free, no API key)

- Routing, stops, departures and real-time: **[Transitous](https://transitous.org)**
  (open-source [MOTIS](https://github.com/motis-project/motis)), using the Israel Ministry
  of Transport GTFS feed. [Data sources and licences](https://transitous.org/sources/).
- Map: **[OpenFreeMap](https://openfreemap.org)**, map data © [OpenStreetMap](https://www.openstreetmap.org/copyright) contributors.
- Map rendering: [MapLibre Native](https://maplibre.org).

Transitous is run by volunteers; this project follows its
[usage policy](https://transitous.org/api/) (open source, non-commercial, light traffic,
identifying User-Agent).

## Install

Get the latest APK from **[Releases](https://github.com/EitanPinczowski/israel-transit-planner/releases/latest)**
and open it on your phone — step by step, in Hebrew and English: [docs/install.md](docs/install.md).
The app tells you when a newer version is out.

Development builds: `app-debug` under **Actions → CI → Artifacts** (a debug key, so it
cannot update a release install — uninstall first).

## Build

```sh
./gradlew -p core test                      # engine + tests, any JDK 17+, no Android SDK
./gradlew -p android :app:assembleDebug     # the app, needs the Android SDK
```

Project layout, rules and the roadmap: [CLAUDE.md](CLAUDE.md), [ROADMAP.md](ROADMAP.md).

## Licence

MIT — see [LICENSE](LICENSE).

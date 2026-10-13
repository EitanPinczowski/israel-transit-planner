# Phase 11 ideas — research, round 2

Asked by the boss on 2026-10-13. The owner picked 1 (reminder check), 3 ("I missed it"), 6
(accessibility) and 9 (data and battery) from `phase11-ideas.md`, and added three topics:
**A** live data, **B** Rav-Kav, **C** "which rail to take". Plus 4–6 new ideas.

**Method and limits (read this first).**
- Read `CLAUDE.md`, `team.md`, `dead-ends`, `transitous-api`, round 1, `ROADMAP.md`.
- Web search and page fetches for A and B. Web results are third-party text, not primary
  sources, and I say where that matters.
- **Test calls: 3 of ~5.** All with `MotisClient.USER_AGENT`-style UA, ≥ 1 s apart:
  1. + 2. `open-bus-stride-api.hasadna.org.il` (`/openapi.json`, then
     `/siri_vehicle_locations/list`): **blocked by this sandbox's network policy (proxy 403,
     `CONNECT tunnel failed`)**. So **nothing about Stride's freshness or limits was measured**.
     The owner would need to allow that host for a follow-up, or run two `curl`s from a PC.
  3. Transitous `plan` Tel Aviv → Haifa, Wed 14 Oct 2026 08:00, 4 itineraries (used for C and
     for the wheelchair finding below). n=1, one time of day.
- I did **not** call Israel Railways' servers (see A2: it needs an embedded key).
- I could not run an Android phone, so nothing about NFC was tested.

---

## A. Live data about buses and trains

**Verdict: no source passes all the hard rules today.** One (Stride) is keyless and free but
is a research archive on someone else's volunteer server, and it cannot be verified from here.
Israel Railways' live feed needs a key. The only clean route is upstream (Transitous).

### A1. Hasadna Open Bus Stride API — `open-bus-stride-api.hasadna.org.il`
| | |
|---|---|
| What it serves | REST/JSON (FastAPI + PostgreSQL, MIT-licensed code). Per the repo: SIRI vehicle locations (`/siri_vehicle_locations/list`), SIRI rides and ride stops, GTFS rides, ride stops, route timetables. Lists page with `limit`/`offset`. |
| Key | None found: the project's own usage guide example has no credentials. The repo README says nothing on auth, limits or terms. **Not confirmed live** (blocked). |
| Freshness | **Unknown, not measured.** Stride's SIRI data is collected from the MOT's SIRI feed by Hasadna's pipeline and stored in a database. Whether a position is seconds or minutes old when queryable is not documented in anything I could read. Ask in Hasadna's Slack `#open-bus`. |
| Coverage | **Buses only** (the MOT SIRI feed). **No trains.** Light rail: not stated. |
| Terms / limits | Not stated anywhere I could read (README, usage guide). An older Hasadna page notes some list endpoints are too heavy without time filters, i.e. they expect narrow queries. It is a volunteer-run research service: no SLA. |
| Requests per use | "Where is my bus": `siri_vehicle_locations/list` filtered by line/stop + a time window, 1 request, repeated every ~30 s while a ride sheet is open. That is a **polling pattern**, which is exactly what a volunteer server should not get from every friend's phone. A cached/day-bounded call (once on opening a stop, no polling) is defensible; a live map is not. |
| Privacy | Request carries the line/stop being looked at plus the phone's IP. No account. |
| Plug-in | `StrideClient` behind `GuardedStride` modelled on `GuardedWeather`: 1 call per user tap, cache 30 s per (line, stop), ≤ 2 at once, one retry, 3 s timeout, **silent failure** (the board stays "scheduled"), credited. It must be a **new approved outside service** (`CLAUDE.md` lists approved ones). Needs a tested join between a Stride `line_ref`/`journey_ref` and the GTFS `tripId`/route we hold; this join is the hard, unverified part (Stride keys on MOT's own line refs, Transitous on the GTFS ids). |
| Verdict | Fits the hard rules **only if Hasadna agrees** and a measured freshness is good. Not buildable on what I could verify. **Owner action:** approve it as a service in principle, allow the host for a 3-call measurement, and ask Hasadna in `#open-bus` (a) is app traffic welcome, (b) the delay of SIRI data, (c) a rate they accept. |

### A2. Israel Railways' own endpoints — **breaks the hard rules**
- The rail.co.il website talks to `https://rail-api.rail.co.il/rjpa/api/v1/…` (e.g.
  `timetable/searchTrain`, POST/JSON). It returns trains with delays, per the community
  wrapper `sh0oki/israel-rail-api` and the older Hasadna wiki ("Unofficial API documentation":
  delays, train positions, load "Omasim", all from 2018 and for an older API).
- **It needs a key.** The wrapper sends an `ocp-apim-subscription-key` header whose own
  comment says it is the "API key bundled in main.js of rail.co.il". So it is a key
  extracted from Israel Railways' website, issued for their site, not to us.
- **Plainly: shipping that key in our APK breaks "no API key in the app — ever", and it is also
  using someone else's credential against their evident intent.** I did not call it and I do
  not reproduce the key here. Do not use it, with or without a proxy.
- Older mobile endpoints (a raw IP over HTTP) are documented as closed or fragile by the
  community wrappers. Terms of use: none published that I found.
- **A legitimate route would be to ask Israel Railways (or the MOT) for a public/keyed
  developer programme.** A key obtained that way is still a key in an APK (rule says no); only
  a keyless endpoint would qualify. So: dead unless they publish one.

### A3. MOT feeds into Transitous (upstream contribution)
- The MOT publishes the static GTFS (`gtfs.mot.gov.il`, listed on MobilityData as
  mdb-2519, no real-time attached) and a real-time **SIRI SM** web service (stop monitoring,
  ICD 2.7, Hebrew docs on gov.il). Per Hasadna's documentation it is query-only: **no history,
  no GTFS-RT**, and access needs registering with the MOT (CLAUDE.md/`dead-ends` also say
  a static IP).
- Transitous *can* ingest SIRI (Estimated Timetable, Situation Exchange) and GTFS-RT
  (trip updates, alerts); a feed must be named like the static feed it updates. Vehicle
  positions are not supported there yet.
- The Transitous Israel config today has one real-time feed: busofash (night buses);
  re-checked by round 1 today: 0 of 37 MOT departures were `realTime`.
- **Path that costs us nothing:** someone with a registered MOT account runs a small
  SIRI-SM → GTFS-RT converter (that is a server, and not ours to run, per the rules) and
  Transitous ingests it. In practice this is a project for Hasadna or the MOT: Hasadna already
  holds the registration. Our part: open an issue in `public-transport/transitous` asking
  whether an Israel SIRI/GTFS-RT feed is planned, and say we would consume it. **Cost to
  the app if it lands: 0** (`stoptimes` and `plan` start returning `realTime: true`; the app
  already reads delays for busofash).
- **Owner action:** post that question to Transitous' Matrix channel / GitHub (it can ride
  the existing `docs/transitous-contact.md` thread). No code from us.

**Ranked A:** (1) ask Transitous/Hasadna, 0 code; (2) Stride single-shot "bus now" only if
Hasadna agrees and freshness is measured; (3) Israel Railways: no.

---

## B. Rav-Kav features

### B1. Reading the card over NFC
- **The card is Calypso** (ISO 14443 type B / ISO 7816-4 files; per a 2017 Israeli
  developers' mailing-list post and the Rav-Kav Online pages). Contracts, history and
  holder data live **on the card**, not on a server, which is why even the official app and
  web flow just need an NFC reader. That is the strongest evidence that a phone alone can read
  *something* without any server.
- **What is readable without secret keys:** I could not verify this from here. Calypso
  cards normally let public files (environment, contract list, event/trip log, counters)
  be read with a plain `READ RECORD` after `SELECT`, and use a SAM only for *writing* and for
  protected "sessions". The Rav-Kav Online app shows balance and ride history after a scan, so
  that data is readable by a phone. **Unproven for us:** the exact file layout, and whether
  some files demand the secure session. Verdict = *plausible, must be proven on a real card*.
- **Prior art:** Metrodroid (open-source, GPL) lists Rav-Kav among supported cards in its
  contributor list, which is the best lead for a file-layout reference. Eclipse Keyple
  (Calypso SDK for Android NFC; the old Java repo is no longer maintained) and
  CalypsoInspector (a Calypso parser, tested on French cards) are general. A closed-source
  Play app (`com.mosko.ravkav`) reads it too. I found **no open-source Rav-Kav reader I
  could read**, so the layout would be reverse-engineered from the card in the owner's
  wallet or from Metrodroid's source (**GPL: copying code into an MIT repo is not allowed;
  re-implementing from the documented layout is**).
- **What the app could show:** (1) contract name and **expiry date** (monthly pass) →
  reminder before it ends; (2) stored-value balance; (3) last trips (stop, time) → fills
  History without GPS. Everything stays on the phone.
- **Android side:** `NfcAdapter` reader mode + `IsoDep`; `<uses-permission NFC>`; no Play
  Services. Keyple is **not** needed (and adds a dependency); about 150 lines of APDU code.
- **Risks, plainly:**
  - *Terms of the card/Rav-Kav Online:* no public terms for third-party reading found. Reading
    the owner's *own* card on the owner's *own* phone is the same thing the official app does,
    but I am not a lawyer; for friends and family it's their own cards. **Writing** (top-up) is
    out: it needs a SAM and operator systems.
  - *Privacy law:* card data is personal (ID-linked for personalised cards). Keep it on the
    phone, never in a log or crash share. Same rule as saved places.
  - *Fragility:* operator may change file layout or add protection; the feature must fail to
    "can't read this card" without a crash.
  - *Test cost:* needs a real card on a real phone. The cloud can't. **One prototype by the
    owner**, taking a screenshot of what the official app shows, then compare.
- **Effort:** M–L (the unknown layout is the risk), S for the expiry reminder once there.
  **Requests: 0. New outside service: no.** Owner decision: do you accept "read my own card,
  on my phone, nothing leaves it"? and will you lend a card for a prototype?

### B2. Deep link into Rav-Kav Online (top-up)
- Rav-Kav Online is the official app (HopOn-run, per Globes) for loading rides/passes. I found
  **no documented deep-link scheme or public intent API**, and I could not look at the app.
- Free fallback with no new knowledge: `packageManager.getLaunchIntentForPackage(<package>)`
  (needs `<queries>` in the manifest) opens the app; if not installed, link to its web page.
  So a "Top up" button that just *opens the app*. Ten lines, 0 requests.
- Don't build anything that imitates the payment flow or the purchase. **Effort: S.** The
  package name must be read from the owner's phone (I did not confirm it). **Service: no.**

### B3. "Pass expires soon" reminder (fits the app)
- Without NFC: the user types the pass end date once in Settings → a notification 3 days
  before, through the existing `ReminderScheduler` path (round 1 idea 1 makes it more
  reliable). **S, 0 requests, no service.** With B1 the date can be read, not typed.
- Also possible, cheap: a **fare cap hint**: History already knows the rides; if the month's
  rides (via `FareEstimator`) exceed a pass price, say "a monthly pass would be cheaper". The
  pass prices must come from the official table by the owner (`FareTable` is bands, not
  passes); mark it "estimate". **S–M.**

**Ranked B:** (1) B3 typed-date reminder, S; (2) B2 open-the-app button, S; (3) B1 NFC read,
M–L, after the owner's prototype.

---

## C. "Which rail to take"

### C(a). Which platform / track
- Round 1 and `dead-ends`: `track` is empty on Israel Railways legs. Re-seen today:
  `from/to` of the 4 rail legs have no `track` (n=1).
- **Other free sources:** the Israel Railways API (needs a key, see A2); the station
  departure boards on rail.co.il (website only; scraping a page is against the Transitous
  "don't scrape" spirit and not stable); **no open dataset of platform per train** found.
  In practice Israel Railways assigns platforms late and changes them; even an official feed
  would be "live", which is the A problem.
- **Static alternative that is useful and free:** a bundled per-station fact "trains to the
  north usually leave from platform N" would be a rules table that is wrong when a platform
  changes. Not recommended without an owner-checked source (same reasoning as round 1's bike
  rules table).
- **Verdict: not possible with free keyless data. Stays a dead end.** Say on the board
  "check the station screen for the platform".

### C(b). Which train when several go the same way
This **is** possible from data the app already receives; round 1 did not look at it. In
today's Tel Aviv → Haifa answer the rail legs are:

| leg | `headsign` (= train no.) | `displayName` (route) | stops between | `routeType` |
|---|---|---|---|---|
| Tel Aviv Merkaz → Bat Galim | 24 | Be'er Sheva ⇄ Nahariya | 3 | 2 |
| Tel Aviv University → Bat Galim | 156 | Modi'in ⇄ Nahariya | 3 | 2 |
| Tel Aviv University → Hof HaCarmel | 156 | Modi'in ⇄ Nahariya | 2 | 2 |
| Tel Aviv Merkaz → Bat Galim | 404 | Be'er Sheva ⇄ Karmiel | 4 | 2 |

(n=1; `routeShortName` and `tripShortName` are empty; the **train number is in `headsign`**.)
- So the app can show: "Train 24, to Nahariya, 3 stops on the way, 64 min" versus "Train 404,
  4 stops, 63 min", and tell **direct vs stopping** by comparing the stop counts of equal
  departures. It can also mark "different station: University instead of Merkaz" (same
  line, different boarding station).
- **Light rail versus train:** `mode` TRAM versus REGIONAL_RAIL; the app already has
  `LegKind.LIGHT_RAIL`. Nothing new needed.
- Gap: MOTIS gives stop counts, not the official "express/stopping" label. "Fewer stops" is
  an honest label; the words "express/direct" are the app's inference.
- **Effort: S.** A tested `present/` helper: train number + terminus + stops count in the
  leg chip and the details; optional "Prefer fewer stops" sort chip. **0 requests. No service.**

### C(c). Which carriage to board
- No data exists in any source I found: GTFS has no carriage or exit-side field; Israel
  Railways publishes no carriage-level data (only a "load" figure appears in the community
  notes for the old API). The earlier Phase 10 review already rejected "platform/carriage
  exits". **Verdict: impossible with free data.** Don't promise it.

**Ranked C:** (b) is the real win, S. (a) and (c): no.

---

## New finding that corrects the docs: vehicle accessibility *is* in the data
`ROADMAP.md` (the Accessible routes row) and the skills say "vehicle accessibility is not in
the data". Today's live plan shows every transit leg carries **`wheelchairAccessible`**:

| mode | value | count (4 itineraries) |
|---|---|---|
| BUS | ACCESSIBLE | 14 |
| BUS | NOT_ACCESSIBLE | 1 |
| REGIONAL_RAIL | NOT_ACCESSIBLE | 4 |

- The app's `Models.kt` does not read the field (grep found no use).
- **Caution:** `NOT_ACCESSIBLE` on **all 4 Israel Railways legs is almost certainly a data
  default, not a fact** (the trains are generally boardable with help). Do not show it for rail.
  Bus values look informative but come from one query. The bus data needs a wider sample
  (10–20 plans, several cities) before the app says "accessible" in a way a wheelchair
  user will rely on. Never print "accessible" for `UNKNOWN`/missing.

---

## New ideas (ranked)

### 1. "Accessible bus" badge on legs, using `wheelchairAccessible` — S–M
**User gets:** with the Accessible-routes setting on, buses marked `ACCESSIBLE` get a badge
and `NOT_ACCESSIBLE` ones a warning; the sort/filter can hide them. Stops get the same from
the stop's `wheelchair_boarding` if the field is present (not checked; ask in the first
sample). **Data:** the `plan` answer already has it. **Requests:** 0 extra.
**Effort:** S–M (`Models`, `present/` text, TalkBack text, tests; fixture re-record).
**Risks:** a wrong "accessible" is worse than none. Rail is excluded (above); wider sample
first. **Outside service: no.** Pairs with accessibility pass (round 1 #6).

### 2. Direct versus stopping train labels — S  (see C(b))
**Requests 0. Outside service: no.** Highest value per line of code in this round.

### 3. Pre-filled "Report a problem" GitHub issue — S
**User gets:** "Report a problem" in Settings opens the phone's browser at
`https://github.com/<repo>/issues/new?title=…&body=…` with app version, Android version, the
query (only if the user ticks it) and the last crash's top lines already in the body. The user
taps Submit in GitHub with their own account. Extends the existing "Share crash log"
(B4) and round 1 #7's "report a wrong answer".
**Data:** none. **Requests:** 0 (the browser does it). **Effort:** S (a tested
`IssueUrl` builder; URL length limit ~8 k chars, so the body is truncated and the full log
stays on the share sheet). **Risks:** the user needs a GitHub account; friends often won't
(then keep the share sheet). The body must not contain places the user did not tick.
**Outside service: no new** (github.com is opened by the user's browser, not called by
the app). Consistent with `dead-ends`: nothing is sent automatically.

### 4. Plan from a share: "Share → Israel Transit Planner" for an address/link — S–M
Partly done already (D6 accepts `geo:` and Waze/Maps links). New: an **`ACTION_SEND`
text** target so a WhatsApp "meet at Dizengoff 50" opens the app with that as the
destination. **0 requests until the user searches** (then 1 geocode, the existing path).
**Risks:** parsing free text; show the match before planning (the calendar picker already
does). **Outside service: no.**

### 5. F-Droid distribution — M
- The repo is public, open source and has **no Play Services/Firebase dependencies** (checked
  `android/app/build.gradle.kts`: AndroidX, Compose, MapLibre, DataStore, WorkManager only;
  MapLibre is open source). So the licence side is likely fine.
- What it would need: a reproducible/reproducible-enough build from source (F-Droid builds
  the app itself, so the signing key becomes F-Droid's, **and users cannot update from the
  owner's signed APK to the F-Droid build**: two update tracks); metadata file;
  fastlane-style listing text; no proprietary libs or trackers (the Test Lab/CI-only tools are
  not in the app); the "anti-features" check for network services (it uses Transitous, Photon
  and Open-Meteo: F-Droid labels this `NonFreeNet` only when a non-free service is needed;
  these are free services but say so).
- Benefit: friends get auto-updates without the APK link flow. For family + friends this is
  probably not worth it before the sideload flow has hurt. **Effort: M, mostly paperwork;
  needs the owner to accept F-Droid's signing.** **Outside service: yes in a loose sense**
  (F-Droid's build server hosts it) → owner approval. The GitHub-release flow
  (`release-apk`) already does the job.

### 6. In-app update check against the GitHub release — S–M
**User gets:** "Update available (v0.10)" with a button that opens the release page in the
browser (no install from inside the app; no `REQUEST_INSTALL_PACKAGES`). **Requests:** 1 per
week to `api.github.com/repos/<repo>/releases/latest`, cached; no key (unauthenticated is 60
per hour per IP). Fits friends-and-family distribution better than F-Droid. **Risks:** GitHub
is a new outside service for the app (owner approval); the call sends the user's IP and a
version string, nothing else; make it a visible switch, default on, silent on failure.
**Effort:** S–M.

### Candidates the boss named that already exist — do not build
- **App shortcuts / Quick Settings tile:** done (`ROADMAP` Phase 9 C4 tile, "App-icon
  shortcuts: My lines + 3 saved places").
- **Planning from a calendar event:** done (B3, "From my calendar").
- **Wheelchair routing (`pedestrianProfile=WHEELCHAIR`):** done as the Accessible-routes
  setting; only the vehicle field above is new.

---

## Summary table
| # | item | effort | requests per use | new outside service (owner approval)? |
|---|---|---|---|---|
| A1 | Stride "bus now" | M | 1 per tap | **yes** (Hasadna), unverified |
| A2 | Rail API | — | — | **no: needs an embedded key; rejected** |
| A3 | Ask Transitous/Hasadna for an Israel feed | 0 code | 0 | no |
| B1 | NFC read of own Rav-Kav | M–L | 0 | no |
| B2 | Open Rav-Kav Online app | S | 0 | no |
| B3 | Pass-expiry reminder | S | 0 | no |
| C(b) | Train number / direct vs stopping | S | 0 | no |
| C(a),(c) | platform, carriage | — | — | not possible with free data |
| 1 | Accessible-bus badge | S–M | 0 | no |
| 3 | Pre-filled GitHub issue | S | 0 | no |
| 4 | Share-to-app text | S–M | 0–1 | no |
| 5 | F-Droid | M | 0 | loosely yes |
| 6 | Update check | S–M | ≤ 1 / week | **yes** (GitHub API) |

## Owner decisions this list needs
1. Post the "is an Israel SIRI/GTFS-RT feed planned?" question to Transitous/Hasadna? (A3)
2. Allow the Stride host for a 3-call measurement, or run two `curl`s from a PC? (A1)
3. NFC: OK to read your own card on your phone, and can you lend one for a prototype? (B1)
4. Accessible-bus badge: allow a wider live sample (10–20 plans) before building. (idea 1)
5. Update check: approve `api.github.com` as an app-called service? (idea 6)
6. Should `ROADMAP.md`/`transitous-api` be corrected about vehicle accessibility? (done by the
   boss or planner; this PR changes no skill.)

# Draft message to Transitous

Their usage policy asks projects to get in touch before routine use of the routing
endpoints, and their API page (https://transitous.org/api/) says to do it in their
**Matrix channel** — the link is on that page. Paste this from your own Matrix account
(any client, e.g. app.element.io; creating an account is free); edit freely.

---

**Subject:** Small open-source Android planner for Israel — OK to use the routing API?

Hi Transitous team,

I'm building a small open-source (MIT) Android trip planner for Israeli public transport:
https://github.com/EitanPinczowski/israel-transit-planner. It's non-commercial and used by
me and a handful of friends and family (roughly 5–15 people), sideloaded, not on any store.

It calls `api.transitous.org` directly from the phone:
- `plan`, `geocode`, `map/stops`, `stoptimes` for normal trip planning;
- `plan` with `preTransitModes=CAR_DROPOFF` (and `postTransitModes`) for "someone drops
  me at a better station" features, and `one-to-many` with `CAR` for drive times.

Traffic controls built in: every request carries a User-Agent with the repo URL; responses
are cached (plans 60 s, stops/geocoding 1 day); at most 2 concurrent requests per device;
one retry on 429/503 then give up; and each special feature has a hard, unit-tested
request cap (≤ 10 per search, most searches 2–5). The app links to
https://transitous.org/sources/ on the map screen.

Questions:
1. Is this usage OK with you, and is there a rate you'd like us to stay under?
2. Is `CAR_DROPOFF` enabled on the public instance, including as a post-transit mode?
3. What are the server caps for `maxPreTransitTime` and `one-to-many` (`max`, number of
   `many` points)?

If it ever becomes too much for the public instance, we'll self-host MOTIS instead.

Thanks for running Transitous!
Eitan

package il.transit.core.golden

import il.transit.core.api.BudgetedTransitApi
import il.transit.core.api.Endpoint
import il.transit.core.api.GeocodeMatch
import il.transit.core.api.GuardedTransitApi
import il.transit.core.api.Itinerary
import il.transit.core.api.Preferences
import il.transit.core.api.MotisClient
import il.transit.core.api.Place
import il.transit.core.api.PlanRequest
import il.transit.core.api.PlanResponse
import il.transit.core.api.StopTimesResponse
import il.transit.core.api.TransitApi
import il.transit.core.features.BetterStartPlanner
import il.transit.core.features.BetterStartQuery
import il.transit.core.features.DropOffKind
import il.transit.core.features.DropOffPlanner
import il.transit.core.features.DropOffQuery
import il.transit.core.calendar.DayKind
import il.transit.core.calendar.Holidays
import il.transit.core.features.ISRAEL
import il.transit.core.features.ParkRidePlanner
import il.transit.core.features.ParkRideQuery
import il.transit.core.features.Option
import il.transit.core.features.PickUpPlanner
import il.transit.core.features.PickUpQuery
import il.transit.core.features.paretoFront
import il.transit.core.geo.BBox
import il.transit.core.geo.Geo
import il.transit.core.geo.LatLon
import il.transit.core.invariants.Invariants
import il.transit.core.plan.ChainPlanner
import il.transit.core.plan.ChainStop
import il.transit.core.plan.LastRideFinder
import il.transit.core.present.DayNotice
import il.transit.core.user.UserSettings
import kotlinx.coroutines.delay
import il.transit.core.plan.TimeMode
import il.transit.core.plan.TripPlanner
import il.transit.core.plan.TripQuery
import il.transit.core.present.LegKind
import il.transit.core.present.hhmm
import il.transit.core.present.legKind
import il.transit.core.present.summarize
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.exitProcess

/**
 * The golden trips of .claude/skills/golden-trips (~25), run against live Transitous through
 * the same planners and guard the app uses. Every answer meets the shared invariants
 * (`core/src/invariants`: times in order, legs chain in time and space, Pareto fronts, fares,
 * ride details, no unreasonable chains) plus the trip's own expectation; the report goes to
 * Markdown. Comparing with Moovit stays a human job.
 *
 * Live traffic: at most [CAP] requests a run, at least 1 s apart, then the runner stops and
 * reports the rest as not run. Never wired into `test`; CI runs it weekly (weekly-live.yml).
 *   ./gradlew -p core goldenTrips            (next Tuesday / Friday)
 *   ./gradlew -p core goldenTrips --args=2026-10-06
 */

private object P {
    val bgu = LatLon(31.2622, 34.8013)
    val savidor = LatLon(32.0839, 34.7983)
    val kelaAlon = LatLon(33.1308, 35.6825)
    val tabenkinTlv = LatLon(32.1205, 34.803)
    val bsCenter = LatLon(31.2433, 34.7976)
    val jerusalemCbs = LatLon(31.7890, 35.2033)
    val bsRamot = LatLon(31.2747, 34.8123)
    val haShalom = LatLon(32.0734, 34.7930)
    val hofHaCarmel = LatLon(32.7936, 34.9573)
    val neveZeev = LatLon(31.2366, 34.7685)
    val rehovot = LatLon(31.8947, 34.8093)
    val bsHome = LatLon(31.2520, 34.7915)
    val carlebach = LatLon(32.0663, 34.7797)
    val petahTikva = LatLon(32.0917, 34.8860)
    val meitar = LatLon(31.3236, 34.9381)
    val nahariya = LatLon(33.0125, 35.0970)
    val katzrin = LatLon(32.9936, 35.6906)
    val tiberias = LatLon(32.7880, 35.5320)
    val eilat = LatLon(29.5577, 34.9519)
    val mitzpeRamon = LatLon(30.6100, 34.8010)
    val mountHerzl = LatLon(31.7745, 35.1790)
    val jerusalemLightRailCenter = LatLon(31.7876, 35.2060)
    val gilo = LatLon(31.7330, 35.1890)
    val nazareth = LatLon(32.7020, 35.2980)
    val haifaMerkaz = LatLon(32.8000, 35.0030)
    val allenby = LatLon(32.0640, 34.7710)
    val bneiBrak = LatLon(32.0840, 34.8340)
    val haifaHof = LatLon(32.7936, 34.9573)
    val netanya = LatLon(32.3215, 34.8532)
    val netivot = LatLon(31.4230, 34.5900)
}

/** The weekly run's request cap (phase11.md "Request budgets"): the runner stops when it is reached. */
private const val CAP = 60
private const val GAP_MS = 1000L

private class CapReached : RuntimeException("request cap $CAP reached")

/** Counts what actually leaves for the server (below the cache), at most [CAP], at least [GAP_MS] apart. */
private class Counting(private val inner: TransitApi) : TransitApi {
    val n = AtomicInteger()
    private var last = 0L

    private suspend fun <T> send(call: suspend () -> T): T {
        if (n.get() >= CAP) throw CapReached()
        val wait = last + GAP_MS - System.currentTimeMillis()
        if (wait > 0) delay(wait)
        n.incrementAndGet()
        last = System.currentTimeMillis()
        return call()
    }

    override suspend fun plan(req: PlanRequest): PlanResponse = send { inner.plan(req) }
    override suspend fun oneToMany(one: LatLon, many: List<LatLon>, mode: String, maxSeconds: Int, arriveBy: Boolean): List<Int?> =
        send { inner.oneToMany(one, many, mode, maxSeconds, arriveBy) }
    override suspend fun stops(box: BBox, modes: Set<String>?, language: String): List<Place> = send { inner.stops(box, modes, language) }
    override suspend fun geocode(text: String, language: String, near: LatLon?, max: Int): List<GeocodeMatch> =
        send { inner.geocode(text, language, near, max) }
    override suspend fun reverseGeocode(at: LatLon, language: String, max: Int): List<GeocodeMatch> =
        send { inner.reverseGeocode(at, language, max) }
    override suspend fun stopTimes(stopId: String, time: Instant?, n: Int, language: String, pageCursor: String?): StopTimesResponse =
        send { inner.stopTimes(stopId, time, n, language, pageCursor) }
    override suspend fun trip(tripId: String, language: String): Itinerary = send { inner.trip(tripId, language) }
    override suspend fun mapTrips(box: BBox, start: Instant, end: Instant, zoom: Double, language: String): List<il.transit.core.api.TripSegment> =
        send { inner.mapTrips(box, start, end, zoom, language) }
}

private class Outcome(val title: String, val expect: String) {
    val problems = mutableListOf<String>()
    val lines = mutableListOf<String>()
    var requests = 0
    var skipped: String? = null
    fun check(ok: Boolean, problem: () -> String) { if (!ok) problems += problem() }
}

private fun line(it: Itinerary): String {
    val s = summarize(it)
    val chips = s.chips.joinToString(" ") { c -> if (c.kind == LegKind.WALK || c.kind == LegKind.CAR) "${c.kind.name.lowercase()} ${c.minutes}′" else (c.label ?: c.kind.name.lowercase()) }
    val fare = s.fare?.let { f -> " · ₪%.2f".format(f.agorot / 100.0) }.orEmpty()
    return "${s.depart}–${s.arrive} (${s.durationMin}′, ${s.transfers} transfers) $chips$fare"
}

private fun kinds(it: Itinerary): Set<LegKind> = it.legs.filter { l -> l.isTransit }.map { l -> legKind(l.mode) }.toSet()

/** Invariants every itinerary must meet, whatever the trip: the shared ones, plus time and space chaining. */
private fun Outcome.sane(tag: String, it: Itinerary, from: LatLon?, to: LatLon?) {
    check(it.legs.isNotEmpty()) { "$tag: no legs" }
    if (it.legs.isEmpty()) return
    check(it.start < it.end) { "$tag: starts ${it.start} after it ends ${it.end}" }
    check(kotlin.math.abs(it.end.epochSecond - it.start.epochSecond - it.duration) <= 60) { "$tag: duration ${it.duration}s ≠ end − start" }
    // Live answers round to the minute: the leg order is checked with a 60 s slack, the rest as on recordings.
    val shared = Invariants.itinerary(tag, it).filterNot { m -> "starts before leg" in m }
    problems += shared + Invariants.timeChain(tag, it) + Invariants.space(tag, it, from, to)
    if (it.firstTransitLeg != null) check(summarize(it).fare != null) { "$tag: transit option without a fare estimate" }
}

private fun <T> Outcome.front(tag: String, options: List<Option<T>>) {
    check(paretoFront(options).size == options.size) { "$tag: options include a dominated one" }
    problems += Invariants.front(tag, options)
}

private fun at(date: LocalDate, h: Int, m: Int = 0): Instant = ZonedDateTime.of(date, LocalTime.of(h, m), ISRAEL).toInstant()

fun main(args: Array<String>) {
    val failed = runBlocking { runAll(args) }
    exitProcess(if (failed == 0) 0 else 1)
}

/** Returns the number of failing trips. */
private suspend fun runAll(args: Array<String>): Int {
    val today = LocalDate.now(ISRAEL)
    val weekday = args.getOrNull(0)?.let(LocalDate::parse) ?: today.with(TemporalAdjusters.next(DayOfWeek.TUESDAY))
    val friday = weekday.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY))
    val out = File(args.getOrNull(1) ?: "build/golden/report.md")

    val wire = Counting(MotisClient())
    val api = GuardedTransitApi(wire)
    val trips = TripPlanner(api)
    val results = mutableListOf<Outcome>()

    suspend fun run(o: Outcome, body: suspend Outcome.() -> Unit) {
        val before = wire.n.get()
        if (before >= CAP) {
            o.skipped = "not run: the $CAP-request cap was reached"
            results += o
            return
        }
        try {
            o.body()
        } catch (e: CapReached) {
            o.skipped = "stopped: the $CAP-request cap was reached"
        } catch (e: Exception) {
            o.problems += "threw ${e::class.simpleName}: ${e.message}"
        }
        o.requests = wire.n.get() - before
        results += o
    }

    suspend fun trip(
        n: Int, name: String, expect: String, from: LatLon, to: LatLon, time: Instant,
        mode: TimeMode = TimeMode.DEPART_AT, allowEmpty: Boolean = false, extra: Outcome.(List<Itinerary>) -> Unit,
    ) = run(Outcome("$n. $name", expect)) {
        val r = trips.plan(TripQuery(Endpoint.Coord(from), Endpoint.Coord(to), mode, time))
        check(allowEmpty || r.itineraries.isNotEmpty()) { "no transit options" }
        r.itineraries.forEachIndexed { i, it -> sane("option ${i + 1}", it, from, to) }
        problems += Invariants.chains("options", r.itineraries)
        r.itineraries.take(4).forEach { lines += line(it) }
        if (mode == TimeMode.ARRIVE_BY) {
            r.itineraries.forEachIndexed { i, it -> check(!it.end.isAfter(time)) { "option ${i + 1} arrives ${hhmm(it.end)}, after ${hhmm(time)}" } }
        } else {
            r.itineraries.forEachIndexed { i, it -> check(!it.start.isBefore(time.minusSeconds(120))) { "option ${i + 1} leaves ${hhmm(it.start)}, before ${hhmm(time)}" } }
        }
        extra(r.itineraries)
    }

    val tue8 = at(weekday, 8)
    trip(1, "BGU → Tel Aviv Savidor", "train from Be'er Sheva North/University", P.bgu, P.savidor, tue8) { its ->
        check(its.take(3).any { LegKind.TRAIN in kinds(it) }) { "none of the first 3 options uses a train" }
    }
    trip(2, "Be'er Sheva Center → Jerusalem CBS", "direct intercity bus, or train", P.bsCenter, P.jerusalemCbs, tue8) { its ->
        check(its.any { it.transfers == 0 || LegKind.TRAIN in kinds(it) }) { "no direct bus and no train" }
        check(its.first().duration <= 150 * 60) { "fastest takes ${its.first().duration / 60}′ (> 150′)" }
    }
    trip(3, "Be'er Sheva Ramot → BGU", "local bus, < 30 min", P.bsRamot, P.bgu, tue8) { its ->
        check(its.first().duration <= 30 * 60) { "fastest takes ${its.first().duration / 60}′ (> 30′)" }
        check(LegKind.BUS in kinds(its.first())) { "fastest is not a bus" }
    }
    trip(4, "Tel Aviv HaShalom → Haifa Hof HaCarmel", "direct train", P.haShalom, P.hofHaCarmel, tue8) { its ->
        check(its.take(3).any { LegKind.TRAIN in kinds(it) && it.transfers == 0 }) { "no direct train in the first 3 options" }
    }
    trip(5, "Jerusalem CBS → Tel Aviv, arrive by 09:00", "train or direct bus, leaving in time", P.jerusalemCbs, P.savidor, at(weekday, 9), TimeMode.ARRIVE_BY) { its ->
        check(its.first().duration <= 120 * 60) { "latest-leaving option takes ${its.first().duration / 60}′ (> 120′)" }
    }
    trip(6, "Friday 15:00 Be'er Sheva → Tel Aviv", "still service; flag last trips", P.bsCenter, P.savidor, at(friday, 15)) { its ->
        check(its.any { it.end.atZone(ISRAEL).toLocalDate() == friday }) { "nothing arrives on Friday" }
    }

    run(Outcome("7. Better start: Neve Ze'ev → Tel Aviv, 10 min", "suggests a Be'er Sheva station")) {
        val budgeted = BudgetedTransitApi(api, BetterStartPlanner.BUDGET)
        val r = BetterStartPlanner(budgeted).plan(BetterStartQuery(P.neveZeev, P.savidor, tue8, maxDriveMin = 10))
        lines += "baseline: " + (r.baseline?.let(::line) ?: "none") + " · mode ${r.usedMode} · ${budgeted.used}/${BetterStartPlanner.BUDGET} requests"
        r.options.forEach { o -> lines += "drive ${o.payload.driveSec / 60}′ to ${o.payload.dropOffStopName}: " + line(o.payload.itinerary) }
        r.baseline?.let { sane("baseline", it, P.neveZeev, P.savidor) }
        r.options.forEachIndexed { i, o -> sane("option ${i + 1}", o.payload.itinerary, null, P.savidor) }
        front("options", r.options)
        check(r.options.any { "באר שבע" in it.payload.dropOffStopName }) { "no option drops off at a Be'er Sheva station" }
        check(r.options.all { it.payload.driveSec <= 10 * 60 * 1.3 + 60 }) { "a drive exceeds the 10′ limit (with traffic)" }
    }

    run(Outcome("8. Drop-off: Be'er Sheva → Tel Aviv by car, C = Rehovot, 10 min", "both baselines; at 10′ maybe no station")) {
        val budgeted = BudgetedTransitApi(api, DropOffPlanner.BUDGET)
        val r = DropOffPlanner(budgeted).plan(DropOffQuery(P.bsCenter, P.savidor, P.rehovot, tue8, maxDetourMin = 10))
        lines += "direct drive ${r.directDriveSec?.div(60)}′ · ${r.candidatesConsidered} candidates · ${budgeted.used}/${DropOffPlanner.BUDGET} requests"
        r.options.forEach { o -> lines += "${o.payload.kind} ${o.payload.stop?.name.orEmpty()} detour ${o.payload.detourSec / 60}′: " + line(o.payload.transit) }
        r.options.forEachIndexed { i, o -> sane("option ${i + 1}", o.payload.transit, null, P.rehovot) }
        front("options", r.options)
        check(r.directDriveSec != null) { "no car route A→B" }
        check(r.candidatesConsidered > 0) { "no candidate stations along the drive" }
        check(r.options.any { it.payload.kind == DropOffKind.TRANSIT_FROM_START }) { "no transit-from-start baseline" }
        r.options.filter { it.payload.kind == DropOffKind.STOP_ON_THE_WAY }.forEach { o ->
            check(o.payload.detourSec <= 10 * 60 + 60) { "${o.payload.stop?.name} costs ${o.payload.detourSec / 60}′ (> 10′)" }
        }
    }

    run(Outcome("9. Pick-up: Tel Aviv → Be'er Sheva home, 15 min", "Be'er Sheva Center or North station")) {
        val budgeted = BudgetedTransitApi(api, PickUpPlanner.BUDGET)
        val r = PickUpPlanner(budgeted).plan(PickUpQuery(P.savidor, P.bsHome, at(weekday, 17)))
        lines += "baseline: " + (r.baseline?.let(::line) ?: "none") + " · ${budgeted.used}/${PickUpPlanner.BUDGET} requests"
        r.options.forEach { o -> lines += "picked up at ${o.payload.pickUpStopName} ${hhmm(o.payload.pickUpTime)}, drive ${o.payload.driveSec / 60}′: " + line(o.payload.itinerary) }
        r.baseline?.let { sane("baseline", it, P.savidor, P.bsHome) }
        r.options.forEachIndexed { i, o -> sane("option ${i + 1}", o.payload.itinerary, P.savidor, null) }
        front("options", r.options)
        check(r.baseline != null) { "no transit-all-the-way baseline" }
        if (r.options.isNotEmpty()) {
            check(r.options.any { "באר שבע" in it.payload.pickUpStopName }) { "no pick-up at a Be'er Sheva station" }
        }
    }

    trip(10, "Tel Aviv Carlebach → Petah Tikva", "light rail", P.carlebach, P.petahTikva, tue8) { its ->
        check(its.take(3).any { LegKind.LIGHT_RAIL in kinds(it) }) { "none of the first 3 options uses the light rail" }
    }
    // D12: the chain rule is judged by Invariants.chains (every trip above has it too) with the 10-minute
    // window on both ends; a 5-change option hours away from the ≤ 3-change ones is allowed to stay.
    trip(12, "Near Kela Alon (Golan) → Tabenkin 15, Tel Aviv", "no unreasonable chain (D12, 10′ window)", P.kelaAlon, P.tabenkinTlv, at(weekday, 7)) { its ->
        check(its.none { it.transfers >= TripPlanner.LONG_CHAIN_CHANGES } || its.any { it.transfers <= TripPlanner.SHORT_CHAIN_CHANGES }) {
            "only 5+ change chains"
        }
    }

    // ---- Phase 11 T5: the widened set ---------------------------------------------------------------

    run(Outcome("13. Better start #2: Meitar → Tel Aviv, 20 min", "a Be'er Sheva station, drive within the limit")) {
        val budgeted = BudgetedTransitApi(api, BetterStartPlanner.BUDGET)
        val r = BetterStartPlanner(budgeted).plan(BetterStartQuery(P.meitar, P.savidor, tue8, maxDriveMin = 20))
        lines += "baseline: " + (r.baseline?.let(::line) ?: "none") + " · ${budgeted.used}/${BetterStartPlanner.BUDGET} requests"
        r.options.forEach { o -> lines += "drive ${o.payload.driveSec / 60}′ to ${o.payload.dropOffStopName}: " + line(o.payload.itinerary) }
        r.baseline?.let { sane("baseline", it, P.meitar, P.savidor) }
        r.options.forEachIndexed { i, o -> sane("option ${i + 1}", o.payload.itinerary, null, P.savidor) }
        front("options", r.options)
        check(budgeted.used <= BetterStartPlanner.BUDGET) { "budget exceeded: ${budgeted.used}" }
        check(r.options.all { it.payload.driveSec <= 20 * 60 * 1.3 + 60 }) { "a drive exceeds the 20′ limit (with traffic)" }
    }

    run(Outcome("14. Drop-off #2: Haifa → Tel Aviv by car, C = Netanya, 15 min", "baselines present; stations within the detour")) {
        val budgeted = BudgetedTransitApi(api, DropOffPlanner.BUDGET)
        val r = DropOffPlanner(budgeted).plan(DropOffQuery(P.haifaHof, P.savidor, P.netanya, tue8, maxDetourMin = 15))
        lines += "direct drive ${r.directDriveSec?.div(60)}′ · ${r.candidatesConsidered} candidates · ${budgeted.used}/${DropOffPlanner.BUDGET} requests"
        r.options.forEach { o -> lines += "${o.payload.kind} ${o.payload.stop?.name.orEmpty()} detour ${o.payload.detourSec / 60}′: " + line(o.payload.transit) }
        r.options.forEachIndexed { i, o -> sane("option ${i + 1}", o.payload.transit, null, P.netanya) }
        front("options", r.options)
        check(r.directDriveSec != null) { "no car route A→B" }
        check(r.options.any { it.payload.kind == DropOffKind.TRANSIT_FROM_START }) { "no transit-from-start baseline" }
        r.options.filter { it.payload.kind == DropOffKind.STOP_ON_THE_WAY }.forEach { o ->
            check(o.payload.detourSec <= 15 * 60 + 60) { "${o.payload.stop?.name} costs ${o.payload.detourSec / 60}′ (> 15′)" }
        }
    }

    run(Outcome("15. Pick-up #2: Tel Aviv → Meitar, 20 min", "a baseline; pick-ups are real stops")) {
        val budgeted = BudgetedTransitApi(api, PickUpPlanner.BUDGET)
        val r = PickUpPlanner(budgeted).plan(PickUpQuery(P.savidor, P.meitar, at(weekday, 17), maxDriveMin = 20))
        lines += "baseline: " + (r.baseline?.let(::line) ?: "none") + " · ${budgeted.used}/${PickUpPlanner.BUDGET} requests"
        r.options.forEach { o -> lines += "picked up at ${o.payload.pickUpStopName} ${hhmm(o.payload.pickUpTime)}, drive ${o.payload.driveSec / 60}′: " + line(o.payload.itinerary) }
        r.baseline?.let { sane("baseline", it, P.savidor, P.meitar) }
        r.options.forEachIndexed { i, o -> sane("option ${i + 1}", o.payload.itinerary, P.savidor, null) }
        front("options", r.options)
        check(r.options.all { it.payload.pickUpStopName.isNotBlank() }) { "a pick-up without a stop name" }
        check(r.options.all { 2 * it.payload.driveSec == it.driverCostSec }) { "driver cost is not the round trip" }
    }

    run(Outcome("16. Park & ride #2: Netivot → Tel Aviv, Sunday 07:30, 20 min", "stations from the bundled list; drive ≥ free flow")) {
        val sunday = weekday.with(TemporalAdjusters.next(DayOfWeek.SUNDAY))
        val budgeted = BudgetedTransitApi(api, ParkRidePlanner.BUDGET)
        val r = ParkRidePlanner(budgeted).plan(ParkRideQuery(P.netivot, P.savidor, at(sunday, 7, 30), maxDriveMin = 20))
        lines += "baseline: " + (r.baseline?.let(::line) ?: "none") + " · ${budgeted.used}/${ParkRidePlanner.BUDGET} requests"
        r.options.forEach { o -> lines += "park at ${o.payload.station.name}, drive ${o.payload.driveSec / 60}′: " + line(o.payload.itinerary) }
        r.baseline?.let { sane("baseline", it, P.netivot, P.savidor) }
        r.options.forEachIndexed { i, o -> sane("option ${i + 1}", o.payload.itinerary, null, P.savidor) }
        front("options", r.options)
        check(r.options.all { it.payload.driveSec >= it.payload.freeFlowSec }) { "traffic made a drive shorter" }
        check(r.options.all { !it.payload.leaveAt.isAfter(it.payload.parkedBy) }) { "leaves after being parked" }
    }

    run(Outcome("17. Errands: Be'er Sheva home → Center (15′) → BGU", "both legs found, each leaves after the last arrives + stay")) {
        val r = ChainPlanner(api).plan(P.bsHome, listOf(ChainStop(P.bsCenter, "Center", stayMin = 15)), P.bgu, tue8, UserSettings(), "en")
        r.legs.forEachIndexed { i, it -> lines += "leg ${i + 1}: " + line(it); sane("leg ${i + 1}", it, null, null) }
        check(r.failedAt == null) { "no way to make leg ${r.failedAt?.plus(1)}" }
        r.legs.zipWithNext().forEachIndexed { i, (a, b) ->
            check(!b.start.isBefore(a.end.plusSeconds(15 * 60 - 60))) { "leg ${i + 2} leaves ${hhmm(b.start)} before the 15′ stay at the stop ends" }
        }
    }

    run(Outcome("18. Last trip home: Tel Aviv → Be'er Sheva Center, Tuesday", "a last trip in the service day; the next after it")) {
        val last = LastRideFinder(api).find(Endpoint.Coord(P.savidor), Endpoint.Coord(P.bsCenter), weekday, Preferences(), "en")
        last.last?.let { lines += "last: " + line(it); sane("last", it, P.savidor, P.bsCenter) }
        last.next?.let { lines += "next: " + line(it); sane("next", it, P.savidor, P.bsCenter) }
        check(last.last != null) { "no last trip found" }
        check(last.last == null || last.next == null || last.next!!.start > last.last!!.start) { "the next trip is not after the last" }
    }

    trip(19, "Nahariya → Haifa Hof HaCarmel", "direct train", P.nahariya, P.haifaHof, tue8) { its ->
        check(its.take(3).any { LegKind.TRAIN in kinds(it) }) { "none of the first 3 options uses a train" }
    }
    trip(20, "Katzrin (Golan) → Tiberias", "a bus; takes < 2 h", P.katzrin, P.tiberias, tue8) { its ->
        check(its.first().duration <= 120 * 60) { "fastest takes ${its.first().duration / 60}′ (> 120′)" }
    }
    trip(21, "Eilat → Be'er Sheva Center", "intercity bus", P.eilat, P.bsCenter, tue8) { its ->
        check(its.any { LegKind.BUS in kinds(it) }) { "no bus option" }
        check(its.first().duration <= 9 * 3600) { "fastest takes ${its.first().duration / 60}′ (> 9 h)" }
    }
    trip(22, "Mitzpe Ramon → Be'er Sheva Center", "a bus", P.mitzpeRamon, P.bsCenter, tue8) { its ->
        check(its.any { LegKind.BUS in kinds(it) }) { "no bus option" }
    }
    trip(23, "Jerusalem light rail: Mount Herzl → Central", "light rail", P.mountHerzl, P.jerusalemLightRailCenter, tue8) { its ->
        check(its.take(3).any { LegKind.LIGHT_RAIL in kinds(it) }) { "none of the first 3 options uses the light rail" }
    }
    trip(24, "Jerusalem CBS → Gilo", "a bus, < 60 min", P.jerusalemCbs, P.gilo, tue8) { its ->
        check(its.first().duration <= 60 * 60) { "fastest takes ${its.first().duration / 60}′ (> 60′)" }
    }
    trip(25, "Nazareth → Haifa Merkaz HaShmona", "a bus, < 2 h", P.nazareth, P.haifaMerkaz, tue8) { its ->
        check(its.first().duration <= 120 * 60) { "fastest takes ${its.first().duration / 60}′ (> 120′)" }
    }
    trip(26, "Night: Tel Aviv Allenby → Bnei Brak, 01:30 after Thursday", "a night bus (busofash, the one real-time feed)", P.allenby, P.bneiBrak, at(weekday.with(TemporalAdjusters.nextOrSame(DayOfWeek.THURSDAY)).plusDays(1), 1, 30)) { its ->
        val rt = its.count { o -> o.legs.any { l -> l.realTime } }
        lines += "$rt of ${its.size} options carry real-time data"
        check(its.first().start.atZone(ISRAEL).hour in 1..5) { "first option leaves at ${hhmm(its.first().start)}, not at night" }
    }
    val saturday = friday.plusDays(1)
    trip(27, "Saturday night after Shabbat: Be'er Sheva → Tel Aviv, 20:00", "nothing during Shabbat; service by the evening", P.bsCenter, P.savidor, at(saturday, 20)) { its ->
        check(its.first().start.atZone(ISRAEL).let { it.toLocalDate() == saturday && it.hour < 18 }.not()) { "an option leaves in the Shabbat afternoon: ${hhmm(its.first().start)}" }
    }
    val holiday = generateSequence(today) { it.plusDays(1) }.take(DayNotice.HORIZON_DAYS.toInt() + 1)
        .mapNotNull { d -> Holidays.day(d) }.firstOrNull { it.kind != DayKind.REDUCED }
    if (holiday == null) {
        results += Outcome("28. Holiday or holiday eve", "service as the day's kind says").also { it.skipped = "no holiday in range (${DayNotice.HORIZON_DAYS} days): skipped" }
    } else {
        trip(28, "${holiday.holiday} (${holiday.kind}, ${holiday.date}): Be'er Sheva → Tel Aviv, 10:00", "no-service day: none before the evening; an eve: runs", P.bsCenter, P.savidor, at(holiday.date, 10), allowEmpty = true) { its ->
            if (holiday.kind == DayKind.NO_SERVICE) {
                check(its.none { it.start.atZone(ISRAEL).let { z -> z.toLocalDate() == holiday.date && z.hour < 17 } }) { "an option leaves on a no-service day before 17:00" }
            } else {
                check(its.isNotEmpty()) { "no option on the eve" }
            }
        }
    }

    val failed = results.count { it.problems.isNotEmpty() }
    val report = buildString {
        appendLine("# Golden trips — ${weekday} (weekday) / ${friday} (Friday)")
        appendLine()
        appendLine("Run ${ZonedDateTime.now(ISRAEL).toLocalDateTime().withNano(0)} Israel time · ${wire.n.get()} requests to Transitous · ${results.count { it.problems.isEmpty() && it.skipped == null }}/${results.size} pass, ${results.count { it.skipped != null }} not run, $failed fail · cap $CAP")
        appendLine()
        for (o in results) {
            appendLine("## ${if (o.skipped != null) "SKIP" else if (o.problems.isEmpty()) "PASS" else "FAIL"} — ${o.title}")
            o.skipped?.let { appendLine("_${it}_") }
            appendLine("Expect: ${o.expect} · ${o.requests} requests")
            appendLine()
            o.problems.forEach { appendLine("- **${it}**") }
            o.lines.forEach { appendLine("- $it") }
            appendLine()
        }
    }
    out.parentFile?.mkdirs()
    out.writeText(report)
    println(report)
    println("report: ${out.absolutePath}")
    return failed
}

package il.transit.core.golden

import il.transit.core.api.BudgetedTransitApi
import il.transit.core.api.Endpoint
import il.transit.core.api.GeocodeMatch
import il.transit.core.api.GuardedTransitApi
import il.transit.core.api.Itinerary
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
import il.transit.core.features.ISRAEL
import il.transit.core.features.Option
import il.transit.core.features.PickUpPlanner
import il.transit.core.features.PickUpQuery
import il.transit.core.features.paretoFront
import il.transit.core.geo.BBox
import il.transit.core.geo.Geo
import il.transit.core.geo.LatLon
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
 * The ten golden trips of .claude/skills/golden-trips, run against live Transitous through
 * the same planners and guard the app uses. Checks what a machine can check (an answer
 * exists, the expected mode, legs that chain in time and space, budgets) and writes a
 * Markdown report. Comparing with Moovit stays a human job.
 *
 * Live traffic: about 25 requests a run. On demand only — never wired into `test` or CI.
 *   ./gradlew -p core goldenTrips            (next Tuesday / Friday)
 *   ./gradlew -p core goldenTrips --args=2026-10-06
 */

private object P {
    val bgu = LatLon(31.2622, 34.8013)
    val savidor = LatLon(32.0839, 34.7983)
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
}

/** Counts what actually leaves for the server (below the cache). */
private class Counting(private val inner: TransitApi) : TransitApi {
    val n = AtomicInteger()
    override suspend fun plan(req: PlanRequest): PlanResponse { n.incrementAndGet(); return inner.plan(req) }
    override suspend fun oneToMany(one: LatLon, many: List<LatLon>, mode: String, maxSeconds: Int, arriveBy: Boolean): List<Int?> {
        n.incrementAndGet(); return inner.oneToMany(one, many, mode, maxSeconds, arriveBy)
    }
    override suspend fun stops(box: BBox, modes: Set<String>?, language: String): List<Place> { n.incrementAndGet(); return inner.stops(box, modes, language) }
    override suspend fun geocode(text: String, language: String, near: LatLon?, max: Int): List<GeocodeMatch> {
        n.incrementAndGet(); return inner.geocode(text, language, near, max)
    }
    override suspend fun reverseGeocode(at: LatLon, language: String, max: Int): List<GeocodeMatch> {
        n.incrementAndGet(); return inner.reverseGeocode(at, language, max)
    }
    override suspend fun stopTimes(stopId: String, time: Instant?, n: Int, language: String): StopTimesResponse {
        this.n.incrementAndGet(); return inner.stopTimes(stopId, time, n, language)
    }
}

private class Outcome(val title: String, val expect: String) {
    val problems = mutableListOf<String>()
    val lines = mutableListOf<String>()
    var requests = 0
    fun check(ok: Boolean, problem: () -> String) { if (!ok) problems += problem() }
}

private fun line(it: Itinerary): String {
    val s = summarize(it)
    val chips = s.chips.joinToString(" ") { c -> if (c.kind == LegKind.WALK || c.kind == LegKind.CAR) "${c.kind.name.lowercase()} ${c.minutes}′" else (c.label ?: c.kind.name.lowercase()) }
    val fare = s.fare?.let { f -> " · ₪%.2f".format(f.agorot / 100.0) }.orEmpty()
    return "${s.depart}–${s.arrive} (${s.durationMin}′, ${s.transfers} transfers) $chips$fare"
}

private fun kinds(it: Itinerary): Set<LegKind> = it.legs.filter { l -> l.isTransit }.map { l -> legKind(l.mode) }.toSet()

/** Invariants every itinerary must meet, whatever the trip. */
private fun Outcome.sane(tag: String, it: Itinerary, from: LatLon?, to: LatLon?) {
    check(it.legs.isNotEmpty()) { "$tag: no legs" }
    if (it.legs.isEmpty()) return
    check(it.start < it.end) { "$tag: starts ${it.start} after it ends ${it.end}" }
    check(kotlin.math.abs(it.end.epochSecond - it.start.epochSecond - it.duration) <= 60) { "$tag: duration ${it.duration}s ≠ end − start" }
    it.legs.zipWithNext().forEachIndexed { i, (a, b) ->
        check(!b.start.isBefore(a.end.minusSeconds(60))) { "$tag: leg ${i + 2} starts ${hhmm(b.start)} before leg ${i + 1} ends ${hhmm(a.end)}" }
        val gap = Geo.distanceM(LatLon(a.to.lat, a.to.lon), LatLon(b.from.lat, b.from.lon))
        check(gap <= 400) { "$tag: legs ${i + 1}→${i + 2} jump %.0f m".format(gap) }
    }
    if (from != null) {
        val d = Geo.distanceM(from, LatLon(it.legs.first().from.lat, it.legs.first().from.lon))
        check(d <= 1500) { "$tag: starts %.0f m from the origin".format(d) }
    }
    if (to != null) {
        val d = Geo.distanceM(to, LatLon(it.legs.last().to.lat, it.legs.last().to.lon))
        check(d <= 1500) { "$tag: ends %.0f m from the destination".format(d) }
    }
    if (it.firstTransitLeg != null) check(summarize(it).fare != null) { "$tag: transit option without a fare estimate" }
}

private fun <T> Outcome.front(tag: String, options: List<Option<T>>) {
    check(paretoFront(options).size == options.size) { "$tag: options include a dominated one" }
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
        try {
            o.body()
        } catch (e: Exception) {
            o.problems += "threw ${e::class.simpleName}: ${e.message}"
        }
        o.requests = wire.n.get() - before
        results += o
    }

    suspend fun trip(
        n: Int, name: String, expect: String, from: LatLon, to: LatLon, time: Instant,
        mode: TimeMode = TimeMode.DEPART_AT, extra: Outcome.(List<Itinerary>) -> Unit,
    ) = run(Outcome("$n. $name", expect)) {
        val r = trips.plan(TripQuery(Endpoint.Coord(from), Endpoint.Coord(to), mode, time))
        check(r.itineraries.isNotEmpty()) { "no transit options" }
        r.itineraries.forEachIndexed { i, it -> sane("option ${i + 1}", it, from, to) }
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

    val failed = results.count { it.problems.isNotEmpty() }
    val report = buildString {
        appendLine("# Golden trips — ${weekday} (weekday) / ${friday} (Friday)")
        appendLine()
        appendLine("Run ${ZonedDateTime.now(ISRAEL).toLocalDateTime().withNano(0)} Israel time · ${wire.n.get()} requests to Transitous · ${results.size - failed}/${results.size} pass")
        appendLine()
        for (o in results) {
            appendLine("## ${if (o.problems.isEmpty()) "PASS" else "FAIL"} — ${o.title}")
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

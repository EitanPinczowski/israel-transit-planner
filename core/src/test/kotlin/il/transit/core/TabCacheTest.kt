package il.transit.core

import il.transit.core.api.Itinerary
import il.transit.core.api.MotisJson
import il.transit.core.api.PlanResponse
import il.transit.core.api.ReplayTransitApi
import il.transit.core.api.parseTime
import il.transit.core.features.BetterStartOption
import il.transit.core.features.BetterStartPlanner
import il.transit.core.features.BetterStartQuery
import il.transit.core.features.BetterStartResult
import il.transit.core.features.DropOffOption
import il.transit.core.features.DropOffPlanner
import il.transit.core.features.DropOffQuery
import il.transit.core.features.DropOffResult
import il.transit.core.features.ParkRideOption
import il.transit.core.features.ParkRidePlanner
import il.transit.core.features.ParkRideQuery
import il.transit.core.features.ParkRideResult
import il.transit.core.features.PickUpOption
import il.transit.core.features.PickUpPlanner
import il.transit.core.features.PickUpQuery
import il.transit.core.features.PickUpResult
import il.transit.core.geo.LatLon
import il.transit.core.plan.ChainPlanner
import il.transit.core.plan.ChainResult
import il.transit.core.plan.ChainStop
import il.transit.core.plan.PlanCache
import il.transit.core.plan.TabCacheJson
import il.transit.core.plan.TabResult
import il.transit.core.plan.TripCacheJson
import il.transit.core.plan.TripPlanner
import il.transit.core.plan.TripResult
import il.transit.core.plan.stillAhead
import il.transit.core.user.UserSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Phase 11 R1: the car tabs' and errands' answers survive the file, and what already left is dropped. */
class TabCacheTest {
    private val now = Instant.parse("2026-10-05T04:55:00Z")
    private val replay = ReplayTransitApi(Clock.fixed(now, ZoneOffset.UTC)) { javaClass.getResource("/fixtures/$it.json")!!.readText() }
    private val meitar = LatLon(31.3236, 34.9381)
    private val telAviv = LatLon(32.0839, 34.7983)
    private val bgu = LatLon(31.262, 34.801)
    private val soroka = LatLon(31.2589, 34.7992)

    /** Every kind, planned from the recordings as the app plans it (at limits where the replay offers options). */
    private val all: List<TabResult> = runBlocking {
        listOf(
            TabResult.BetterStart(BetterStartPlanner(replay).plan(BetterStartQuery(meitar, telAviv, now, maxDriveMin = 20))),
            TabResult.DropOff(DropOffPlanner(replay).plan(DropOffQuery(meitar, telAviv, LatLon(32.08, 34.81), now))),
            TabResult.PickUp(PickUpPlanner(replay).plan(PickUpQuery(telAviv, meitar, now, maxDriveMin = 30))),
            TabResult.ParkRide(ParkRidePlanner(replay).plan(ParkRideQuery(meitar, telAviv, now)), meitar),
            TabResult.Errands(ChainPlanner(replay).plan(bgu, listOf(ChainStop(soroka, "Soroka", 15)), telAviv, now, UserSettings(), "en")),
        )
    }

    /** When [i] has to be started: its first boarding, or the walk's start when it has no ride. */
    private fun start(i: Itinerary): Instant = parseTime(i.firstTransitLeg?.startTime ?: i.startTime)

    /** The earliest time each option and the baseline needs: its start, or the driver's leaving if earlier. */
    private fun boardings(r: TabResult): List<Instant> = when (r) {
        is TabResult.BetterStart -> r.result.options.map { minOf(start(it.payload.itinerary), it.payload.leaveAt) } + listOfNotNull(r.result.baseline).map(::start)
        is TabResult.DropOff -> r.result.options.map { start(it.payload.transit) }
        is TabResult.PickUp -> r.result.options.map { minOf(start(it.payload.itinerary), it.payload.driverLeavesAt) } + listOfNotNull(r.result.baseline).map(::start)
        is TabResult.ParkRide -> r.result.options.map { minOf(start(it.payload.itinerary), it.payload.leaveAt) } + listOfNotNull(r.result.baseline).map(::start)
        is TabResult.Errands -> r.chain.legs.map(::start)
    }

    @Test fun `every kind comes back from the file as it was`() {
        all.forEach { r -> assertTrue("$r has something to show", boardings(r).isNotEmpty()) }
        val entries = all.mapIndexed { i, r -> PlanCache.Entry("k$i", now, r) }
        assertEquals(entries, TabCacheJson.decode(TabCacheJson.encode(entries)))
    }

    /** Each saved type on its own, through its own serializer: what a later field change must keep readable. */
    @Test fun `every part has a serializer of its own that round-trips`() {
        val json = kotlinx.serialization.json.Json
        fun <T> back(ser: kotlinx.serialization.KSerializer<T>, v: T) = assertEquals(v, json.decodeFromString(ser, json.encodeToString(ser, v)))
        val bs = all.filterIsInstance<TabResult.BetterStart>().single()
        val dr = all.filterIsInstance<TabResult.DropOff>().single()
        val pu = all.filterIsInstance<TabResult.PickUp>().single()
        val pr = all.filterIsInstance<TabResult.ParkRide>().single()
        val er = all.filterIsInstance<TabResult.Errands>().single()
        back(TabResult.BetterStart.serializer(), bs)
        back(TabResult.DropOff.serializer(), dr)
        back(TabResult.PickUp.serializer(), pu)
        back(TabResult.ParkRide.serializer(), pr)
        back(TabResult.Errands.serializer(), er)
        back(BetterStartResult.serializer(), bs.result)
        back(DropOffResult.serializer(), dr.result)
        back(PickUpResult.serializer(), pu.result)
        back(ParkRideResult.serializer(), pr.result)
        back(ChainResult.serializer(), er.chain)
        back(ChainStop.serializer(), ChainStop(soroka, "Soroka"))
        back(LatLon.serializer(), meitar)
        dr.result.options.forEach { back(DropOffOption.serializer(), it.payload) }
        pu.result.options.forEach { back(PickUpOption.serializer(), it.payload) }
        pr.result.options.forEach { back(ParkRideOption.serializer(), it.payload) }
        val start = BetterStartOption(bs.result.baseline!!, 300, "Stop", meitar, now)
        back(BetterStartOption.serializer(), start)
        assertEquals("\"2026-10-05T04:55:00Z\"", json.encodeToString(il.transit.core.plan.InstantIso, now))
    }

    /**
     * The same through a decoder that reads fields in order (kotlinx's sequential path, as a binary
     * format would): the generated code's other branch, so a future format change cannot break it unseen.
     */
    @Test fun `every result also round-trips field by field`() {
        fun <T> seq(ser: kotlinx.serialization.KSerializer<T>, v: T) {
            val out = ArrayList<Any?>()
            ser.serialize(ListEncoder(out), v)
            assertEquals(v, ser.deserialize(ListDecoder(ArrayDeque(out))))
        }
        all.forEach { r ->
            when (r) {
                is TabResult.BetterStart -> seq(BetterStartResult.serializer(), r.result)
                is TabResult.DropOff -> seq(DropOffResult.serializer(), r.result)
                is TabResult.PickUp -> seq(PickUpResult.serializer(), r.result)
                is TabResult.ParkRide -> seq(ParkRideResult.serializer(), r.result)
                is TabResult.Errands -> seq(ChainResult.serializer(), r.chain)
            }
        }
    }

    @Test fun `unreadable data is no entry`() {
        assertTrue(TabCacheJson.decode("{not json").isEmpty())
        assertTrue(TabCacheJson.decode(null).isEmpty())
    }

    private val file: String get() = TabCacheJson.encode(all.mapIndexed { i, r -> PlanCache.Entry("k$i", now, r) })

    @Test fun `a file cut off mid-write restores as empty, never throws`() {
        val whole = file
        listOf(1, whole.length / 3, whole.length / 2, whole.length - 1).forEach { n ->
            assertTrue("cut at $n", TabCacheJson.decode(whole.take(n)).isEmpty())
        }
    }

    @Test fun `a kind this version does not know restores as empty`() {
        val unknown = file.replaceFirst("\"type\":\"pickUp\"", "\"type\":\"teleport\"")
        assertTrue("the test changed a type", unknown != file)
        assertTrue(TabCacheJson.decode(unknown).isEmpty())
    }

    @Test fun `the Trip cache's file is not a tab cache`() {
        val plan = MotisJson.decodeFromString(PlanResponse.serializer(), javaClass.getResource("/fixtures/plan_bgu_telaviv.json")!!.readText())
        val trip = TripCacheJson.encode(listOf(PlanCache.Entry("TRIP-NOW:x", now, TripResult(plan.itineraries, null))))
        assertTrue(TabCacheJson.decode(trip).isEmpty())
    }

    /**
     * The file is decoded as one list: one bad row loses every row, the good ones too. Accepted: the
     * cache only saves a restore its requests, and a fresh search writes a whole file again.
     */
    @Test fun `a good row then a bad one - the whole file restores as empty`() {
        val good = TabCacheJson.encode(listOf(PlanCache.Entry("k0", now, all.first())))
        assertEquals(1, TabCacheJson.decode(good).size)
        val withBad = good.removeSuffix("]") + ",{\"key\":\"k1\",\"savedAtEpoch\":\"soon\",\"result\":{}}]"
        assertTrue(TabCacheJson.decode(withBad).isEmpty())
    }

    @Test fun `a time that is not ISO-8601 restores as empty`() {
        val leave = Regex("\"leaveAt\":\"[^\"]+\"")
        assertNotNull("the file has a leaveAt", leave.find(file))
        assertTrue(TabCacheJson.decode(file.replaceFirst(leave, "\"leaveAt\":\"5 past 8\"")).isEmpty())
    }

    @Test fun `at the search time nothing is dropped`() {
        all.forEach { assertEquals(it, it.stillAhead(now)) }
    }

    @Test fun `an option whose vehicle or driver left over a minute ago is dropped, the rest stay`() {
        all.filter { it !is TabResult.Errands }.forEach { r ->
            val times = boardings(r).sorted()
            val first = times.first()
            val after = r.stillAhead(first.plusSeconds(TripPlanner.DEPARTED_GRACE_SEC + 1))
            val left = after?.let(::boardings).orEmpty()
            assertTrue("$r", left.none { it < first.plusSeconds(1) })
            assertEquals("$r", times.count { it > first }, left.size)
            // Within the grace minute it is still shown.
            assertEquals(r, r.stillAhead(first.plusSeconds(TripPlanner.DEPARTED_GRACE_SEC)))
        }
    }

    @Test fun `an errand chain whose first leg left is gone as a whole`() {
        val chain = all.single { it is TabResult.Errands }
        val first = boardings(chain).min()
        assertNotNull(chain.stillAhead(first))
        assertNull(chain.stillAhead(first.plusSeconds(TripPlanner.DEPARTED_GRACE_SEC + 1)))
    }

    @Test fun `exactly at the grace minute an option stays, a second later it goes`() {
        val pickUp = all.single { it is TabResult.PickUp } as TabResult.PickUp
        val o = pickUp.result.options.first()
        val board = start(o.payload.itinerary)
        val only = TabResult.PickUp(pickUp.result.copy(baseline = null, options = listOf(o.copy(payload = o.payload.copy(driverLeavesAt = board)))))
        assertEquals(only, only.stillAhead(board.plusSeconds(TripPlanner.DEPARTED_GRACE_SEC)))
        assertNull(only.stillAhead(board.plusSeconds(TripPlanner.DEPARTED_GRACE_SEC + 1)))
    }

    /** [i] with no ride, starting at [at]: walking all the way. */
    private fun walk(i: Itinerary, at: Instant) = i.copy(startTime = at.toString(), legs = i.legs.filter { !it.isTransit })

    @Test fun `a walk-only option goes once its walk started over a minute ago`() {
        val pickUp = all.single { it is TabResult.PickUp } as TabResult.PickUp
        val start = now.plusSeconds(600)
        val walking = TabResult.PickUp(pickUp.result.copy(baseline = walk(pickUp.result.baseline!!, start), options = emptyList()))
        assertEquals(walking, walking.stillAhead(start.plusSeconds(TripPlanner.DEPARTED_GRACE_SEC)))
        assertNull("not kept forever", walking.stillAhead(start.plusSeconds(TripPlanner.DEPARTED_GRACE_SEC + 1)))
        val chain = TabResult.Errands(ChainResult(listOf(walk(pickUp.result.baseline!!, start)), null))
        assertNull(chain.stillAhead(start.plusSeconds(TripPlanner.DEPARTED_GRACE_SEC + 1)))
    }

    @Test fun `a car option whose driver had to leave over a minute ago goes, though its ride is ahead`() {
        val gone = now.minusSeconds(TripPlanner.DEPARTED_GRACE_SEC + 1)
        val bs = all.single { it is TabResult.BetterStart } as TabResult.BetterStart
        val bsLate = bs.result.options.map { it.copy(payload = it.payload.copy(leaveAt = gone)) }
        assertEquals(bs.result.baseline, (TabResult.BetterStart(bs.result.copy(options = bsLate)).stillAhead(now) as TabResult.BetterStart).result.baseline)
        assertTrue((TabResult.BetterStart(bs.result.copy(options = bsLate)).stillAhead(now) as TabResult.BetterStart).result.options.isEmpty())
        val pu = all.single { it is TabResult.PickUp } as TabResult.PickUp
        val puLate = pu.result.options.mapIndexed { i, o -> if (i == 0) o.copy(payload = o.payload.copy(driverLeavesAt = gone)) else o }
        assertEquals(pu.result.options.drop(1), (TabResult.PickUp(pu.result.copy(options = puLate)).stillAhead(now) as TabResult.PickUp).result.options)
        val pr = all.single { it is TabResult.ParkRide } as TabResult.ParkRide
        val prLate = pr.result.options.map { it.copy(payload = it.payload.copy(leaveAt = gone)) }
        val prBack = TabResult.ParkRide(pr.result.copy(options = prLate, baseline = null), pr.origin).stillAhead(now)
        assertNull("no option and no baseline left", prBack)
        // At the boundary the driver may still be on the way out: kept.
        val edge = now.minusSeconds(TripPlanner.DEPARTED_GRACE_SEC)
        val prEdge = TabResult.ParkRide(pr.result.copy(options = pr.result.options.map { it.copy(payload = it.payload.copy(leaveAt = edge)) }), pr.origin)
        assertEquals(prEdge, prEdge.stillAhead(now))
    }

    @Test fun `a failed errand chain stays while its planned legs are ahead, and goes with its first one`() {
        val er = all.single { it is TabResult.Errands } as TabResult.Errands
        val failed = TabResult.Errands(ChainResult(er.chain.legs.take(1), failedAt = 1))
        assertEquals(failed, failed.stillAhead(now))
        val first = start(failed.chain.legs.single())
        assertNull(failed.stillAhead(first.plusSeconds(TripPlanner.DEPARTED_GRACE_SEC + 1)))
    }

    @Test fun `an errand chain with only a middle leg gone is gone as a whole`() {
        val er = all.single { it is TabResult.Errands } as TabResult.Errands
        val legs = er.chain.legs
        val middleGone = TabResult.Errands(ChainResult(listOf(legs.first(), walk(legs.first(), now.minusSeconds(3_600)), legs.last()), null))
        assertTrue("first and last are ahead", listOf(legs.first(), legs.last()).all { start(it) >= now.minusSeconds(TripPlanner.DEPARTED_GRACE_SEC) })
        assertNull(middleGone.stillAhead(now))
    }

    @Test fun `long after every boarding nothing is left to show`() {
        all.forEach { r -> assertNull("$r", r.stillAhead(now.plusSeconds(3 * 86_400))) }
    }
}

/** Writes every value in order into a list (kotlinx docs' "list encoder"); null marks as false/true. */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
private class ListEncoder(private val out: MutableList<Any?>) : kotlinx.serialization.encoding.AbstractEncoder() {
    override val serializersModule = kotlinx.serialization.modules.EmptySerializersModule()

    override fun encodeValue(value: Any) {
        out += value
    }

    override fun encodeNull() {
        out += false
    }

    override fun encodeNotNullMark() {
        out += true
    }

    override fun beginCollection(descriptor: kotlinx.serialization.descriptors.SerialDescriptor, collectionSize: Int): kotlinx.serialization.encoding.CompositeEncoder {
        encodeInt(collectionSize)
        return this
    }
}

/** Reads [ListEncoder]'s list back, declaring every structure sequential. */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
private class ListDecoder(private val input: ArrayDeque<Any?>, private var count: Int = 0) : kotlinx.serialization.encoding.AbstractDecoder() {
    private var index = 0
    override val serializersModule = kotlinx.serialization.modules.EmptySerializersModule()

    override fun decodeValue(): Any = input.removeFirst()!!

    override fun decodeElementIndex(descriptor: kotlinx.serialization.descriptors.SerialDescriptor): Int =
        if (index == count) kotlinx.serialization.encoding.CompositeDecoder.DECODE_DONE else index++

    override fun beginStructure(descriptor: kotlinx.serialization.descriptors.SerialDescriptor): kotlinx.serialization.encoding.CompositeDecoder =
        ListDecoder(input, descriptor.elementsCount)

    override fun decodeSequentially(): Boolean = true

    override fun decodeCollectionSize(descriptor: kotlinx.serialization.descriptors.SerialDescriptor): Int = decodeInt().also { count = it }

    override fun decodeNotNullMark(): Boolean = decodeValue() as Boolean
}

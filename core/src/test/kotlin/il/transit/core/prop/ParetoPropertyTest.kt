package il.transit.core.prop

import il.transit.core.features.Option
import il.transit.core.features.paretoFront
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.Random

class ParetoPropertyTest {
    private val base = Instant.parse("2026-10-05T05:00:00Z")

    private data class Raw(val cost: Int, val arrMin: Int, val transfers: Int)

    // Small ranges on purpose: they make ties and dominated options common.
    private val raw: Arb<Raw> = Arb.bind(Arb.int(0..12), Arb.int(0..12), Arb.int(0..4)) { c, a, t -> Raw(c * 60, a, t) }
    private val inputs: Arb<List<Option<Int>>> = Arb.list(raw, 0..25).map { l ->
        l.mapIndexed { i, r -> Option(i, r.cost, base.plusSeconds(r.arrMin * 60L), r.transfers) }
    }

    private fun dominates(a: Option<Int>, b: Option<Int>) =
        a.driverCostSec <= b.driverCostSec && !a.arrival.isAfter(b.arrival) && a.transfers <= b.transfers &&
            (a.driverCostSec < b.driverCostSec || a.arrival.isBefore(b.arrival) || a.transfers < b.transfers)

    private fun key(o: Option<Int>) = Triple(o.driverCostSec, o.arrival, o.transfers)

    @Test fun `the front has no dominated option, is a subset of the input and covers every input`() = runBlocking<Unit> {
        checkAll(cfg(1000), inputs) { opts ->
            val front = paretoFront(opts)
            assertTrue(front.all { f -> opts.any { it === f } })
            for (a in front) for (b in front) if (a !== b) assertTrue("dominated: $b by $a", !dominates(a, b))
            // Every input option is in the front (same numbers) or is dominated by a front member.
            for (o in opts) assertTrue("$o lost", front.any { key(it) == key(o) || dominates(it, o) })
            // No exact duplicates, and sorted by arrival then cost.
            assertEquals(front.map(::key).distinct().size, front.size)
            assertEquals(front.sortedWith(compareBy<Option<Int>> { it.arrival }.thenBy { it.driverCostSec }).map(::key), front.map(::key))
            if (opts.isNotEmpty()) assertTrue(front.isNotEmpty())
        }
    }

    @Test fun `the front does not depend on input order`() = runBlocking<Unit> {
        checkAll(cfg(1000), inputs, Arb.int()) { opts, seed ->
            val shuffled = opts.shuffled(Random(seed.toLong()))
            assertEquals(paretoFront(opts).map(::key), paretoFront(shuffled).map(::key))
        }
    }

    @Test fun `running the front twice changes nothing`() = runBlocking<Unit> {
        checkAll(cfg(1000), inputs) { opts ->
            val once = paretoFront(opts)
            assertEquals(once, paretoFront(once))
        }
    }
}

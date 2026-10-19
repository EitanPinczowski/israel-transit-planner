package il.transit.planner.ui

import il.transit.core.features.ISRAEL
import il.transit.core.plan.TimeMode
import il.transit.core.present.DayPick
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.util.Random

/**
 * Phase 11 E7, in T1's style ([SavedUiPropertyTest]): any picked day and time survives process
 * death. Every day from today to [DayPick.MAX_DAYS] ahead, any minute (DST nights included), any
 * "now" in 2026–2030: the restored search is the same instant, on the picked calendar day, and
 * the chip says the same thing. Seeded, so CI is deterministic.
 */
class DayPickSavedUiPropertyTest {
    companion object {
        const val SEED = 20261018L
        const val CASES = 2000
    }

    private val rnd = Random(SEED)

    @Test fun `any picked day and time round-trips through SavedUi`() {
        val start = LocalDate.of(2026, 1, 1).atStartOfDay(ISRAEL).toInstant()
        repeat(CASES) { i ->
            val now = start.plusSeconds((rnd.nextDouble() * 5 * 365 * 86_400).toLong())
            val day = DayPick.today(now).plusDays(rnd.nextInt(DayPick.MAX_DAYS.toInt() + 1).toLong())
            val time = LocalTime.of(rnd.nextInt(24), rnd.nextInt(60))
            val mode = if (rnd.nextBoolean()) TimeMode.DEPART_AT else TimeMode.ARRIVE_BY
            val at = DayPick.at(day, time)
            val saved = SavedUi(mode = AppMode.TRIP, from = PlaceRef.MyLocation, to = null, driverTo = null, timeMode = mode, time = at, selected = 0, optionId = null, cacheKey = null, sheet = null)
            val bundle = HashMap<String, Any?>()
            saved.write { k, v -> bundle[k] = v }
            val back = SavedUi.read { bundle[it] }!!
            val why = "case $i seed $SEED: $day $time now $now"
            assertEquals(why, mode, back.timeMode)
            assertEquals(why, at, back.time)
            assertEquals(why, day, back.time!!.atZone(ISRAEL).toLocalDate())
            assertEquals(why, DayPick.label(at, now), DayPick.label(back.time!!, now))
        }
    }
}

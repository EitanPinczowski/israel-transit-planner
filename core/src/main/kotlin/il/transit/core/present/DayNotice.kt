package il.transit.core.present

import il.transit.core.calendar.DayKind
import il.transit.core.calendar.Holiday
import il.transit.core.calendar.HolidayDay
import il.transit.core.calendar.Holidays
import il.transit.core.features.ISRAEL
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * What the Trip tab says about the day searched (Phase 10 D3), from the bundled calendar
 * only: **0 requests** to build any of it. "Plan for <day>" re-plans once (1 request, on tap).
 *  - [banner]: a holiday, its eve, or a reduced-service day ("Thu 22 Apr is Pesach: no buses or
 *    trains until the evening after"). It never claims a time: the last trip comes from the
 *    timetable ([il.transit.core.plan.LastRideFinder]).
 *  - [forEmpty]: a search with 0 options on Shabbat or a holiday ([Notice.NoService]), or beyond
 *    how far the timetable reaches ([Notice.BeyondHorizon]); null keeps the usual "no options".
 * Days are civil days in Israel: a holiday starts in the evening before, which is its eve.
 */
object DayNotice {
    /**
     * How far ahead Transitous's MOT timetable reaches, in days. Measured 2026-10-12 (3 live
     * `plan` calls, fixtures `plan_horizon_*`): +30, +60 and +90 days answer nothing; plans 8 days
     * ahead answered (fixtures `plan_pages_*`, recorded 2026-10-05 for 2026-10-13). Measured again
     * 2026-10-18 (tester): +14 and +23 days gave full results. Owner, 2026-10-18: 21 (was 8), so
     * "may not reach" shows only past 21 days.
     */
    const val HORIZON_DAYS = 21L

    /** When service is offered again after Shabbat or a holiday (owner: "offer Saturday 20:00"). */
    val RESUME: LocalTime = LocalTime.of(20, 0)

    /**
     * From this time on a Friday or a holiday eve, Shabbat or the holiday may have begun (the
     * earliest candle lighting of the year, in December, is about 16:00).
     */
    val EVENING: LocalTime = LocalTime.of(16, 0)

    sealed interface Notice {
        /** "No service that day" + "Plan for <[resume]>". [holiday] null = Shabbat; [evening]: after a Friday or an eve's afternoon. */
        data class NoService(val holiday: Holiday?, val resume: Instant, val evening: Boolean) : Notice

        /** "The timetable may not reach <[date]> yet" + "Plan for <[instead]>": the same weekday and time within the horizon. */
        data class BeyondHorizon(val date: LocalDate, val instead: Instant) : Notice
    }

    /**
     * The banner above the options for a search at [searched], or null. An eve that is a
     * Saturday has no banner: there is no service that day anyway. A no-service day has none
     * from [RESUME] on when the next day has service: the holiday is over and buses run.
     */
    fun banner(searched: Instant): HolidayDay? {
        val local = searched.atZone(ISRAEL)
        val d = Holidays.day(local.toLocalDate()) ?: return null
        val eve = d.kind == DayKind.EVE || d.kind == DayKind.YOM_KIPPUR_EVE
        if (eve && d.date.dayOfWeek == DayOfWeek.SATURDAY) return null
        val over = d.kind == DayKind.NO_SERVICE && !local.toLocalTime().isBefore(RESUME) && !Holidays.restDay(d.date.plusDays(1))
        return d.takeUnless { over }
    }

    /** What to say when a search at [searched], run at [now], found 0 options; null = the usual text. */
    fun forEmpty(searched: Instant, now: Instant): Notice? {
        noService(searched)?.let { return it }
        val date = searched.atZone(ISRAEL).toLocalDate()
        val limit = now.atZone(ISRAEL).toLocalDate().plusDays(HORIZON_DAYS)
        if (!date.isAfter(limit)) return null
        return Notice.BeyondHorizon(date, sameWeekdayWithin(searched, limit, now))
    }

    /**
     * Inside a rest window — from [EVENING] of a Friday or an eve, through Shabbat or the
     * holiday, until [RESUME] of its last day — the notice offering the end of that window.
     */
    internal fun noService(t: Instant): Notice.NoService? {
        val local = t.atZone(ISRAEL)
        val date = local.toLocalDate()
        val time = local.toLocalTime()
        val resting = Holidays.restDay(date) && (time.isBefore(RESUME) || Holidays.restDay(date.plusDays(1)))
        val evening = Holidays.shortDay(date) && !time.isBefore(EVENING)
        if (!resting && !evening) return null
        var first = if (evening) date.plusDays(1) else date
        if (resting && !time.isBefore(RESUME)) first = first.plusDays(1)
        var last = first
        while (Holidays.restDay(last.plusDays(1))) last = last.plusDays(1)
        val holiday = generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }
            .firstNotNullOfOrNull { d -> Holidays.holiday(d)?.takeIf { it.kind == DayKind.NO_SERVICE } }
        return Notice.NoService(holiday, last.atTime(RESUME).atZone(ISRAEL).toInstant(), evening)
    }

    /**
     * [t] moved back a week at a time until its day is within [limit]; a day without service
     * (Shabbat cannot happen, a holiday can) goes back one more week while that stays after [now].
     */
    private fun sameWeekdayWithin(t: Instant, limit: LocalDate, now: Instant): Instant {
        var z = t.atZone(ISRAEL)
        while (z.toLocalDate().isAfter(limit)) z = z.minusWeeks(1)
        if (Holidays.restDay(z.toLocalDate()) && z.minusWeeks(1).toInstant().isAfter(now)) z = z.minusWeeks(1)
        return z.toInstant()
    }
}

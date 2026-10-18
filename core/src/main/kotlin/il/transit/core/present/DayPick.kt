package il.transit.core.present

import il.transit.core.calendar.DayKind
import il.transit.core.calendar.HolidayDay
import il.transit.core.calendar.Holidays
import il.transit.core.features.ISRAEL
import il.transit.core.plan.LastRideFinder
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Picking the day of a search (Phase 11 E7, owner 2026-10-18): the day step after Depart at /
 * Arrive by, and the day on the time chip. Pure, **0 requests**: a picked day only changes the
 * time the usual search is sent with.
 *
 * Days are calendar days in Israel. The service day turns at 04:00 ([EDGE]), so around midnight
 * "tomorrow" is ambiguous: the picked calendar date is used as picked, and the chip then names
 * the date instead of saying "tomorrow" ([label]).
 */
object DayPick {
    /** The date picker reaches this many days ahead (past [DayNotice.HORIZON_DAYS] the D3 notice speaks). */
    const val MAX_DAYS = 60L

    /** Weekday chips after Today and Tomorrow ("Sun 20"). */
    const val NAMED_DAYS = 5

    /** Where one service day ends and the next begins. */
    val EDGE: LocalTime = LastRideFinder.DAY_STARTS

    /** What marks a day in the list: Shabbat, a Friday, or a holiday / its eve / a reduced day (D3 names). */
    sealed interface Mark {
        object Shabbat : Mark
        object Friday : Mark
        data class Holiday(val day: HolidayDay) : Mark
    }

    /** One chip of the day step; [offset] 0 is today, 1 tomorrow. */
    data class Day(val date: LocalDate, val offset: Int, val mark: Mark?)

    /** How the chip names the day. */
    enum class Word { TODAY, TOMORROW, DATE }

    data class Label(val word: Word, val date: LocalDate, val time: String)

    fun today(now: Instant): LocalDate = now.atZone(ISRAEL).toLocalDate()

    /** The last day the date picker offers. */
    fun lastDay(now: Instant): LocalDate = today(now).plusDays(MAX_DAYS)

    /** [date] can be picked: from today to [MAX_DAYS] ahead. */
    fun selectable(date: LocalDate, now: Instant): Boolean = !date.isBefore(today(now)) && !date.isAfter(lastDay(now))

    /** The chips: Today, Tomorrow and the next [NAMED_DAYS] days, each with its [Mark]. */
    fun days(now: Instant): List<Day> {
        val today = today(now)
        return (0..NAMED_DAYS + 1).map { i -> today.plusDays(i.toLong()).let { Day(it, i, mark(it)) } }
    }

    fun mark(date: LocalDate): Mark? {
        val h = Holidays.day(date)
        val eve = h?.kind == DayKind.EVE || h?.kind == DayKind.YOM_KIPPUR_EVE
        return when {
            h != null && !(eve && date.dayOfWeek == DayOfWeek.SATURDAY) -> Mark.Holiday(h)
            date.dayOfWeek == DayOfWeek.SATURDAY -> Mark.Shabbat
            date.dayOfWeek == DayOfWeek.FRIDAY -> Mark.Friday
            else -> null
        }
    }

    /**
     * [time] on [date] in Israel. On the spring change (02:00 → 03:00) a time in the gap moves
     * forward by the gap (02:30 → 03:30); on the autumn change (02:00 → 01:00) a repeated time is
     * the first one, still summer time.
     */
    fun at(date: LocalDate, time: LocalTime): Instant = ZonedDateTime.of(date, time, ISRAEL).toInstant()

    /** A picked time already gone: the app asks "Tomorrow at …?", never moves it by itself. */
    fun past(at: Instant, now: Instant): Boolean = at.isBefore(now.truncatedTo(ChronoUnit.MINUTES))

    /** The same clock time on the next day, for that question's Yes. */
    fun nextDay(at: Instant): Instant {
        val z = at.atZone(ISRAEL)
        return at(z.toLocalDate().plusDays(1), z.toLocalTime())
    }

    /**
     * How the chip names [at], seen at [now]: no day for today, "tomorrow", else the date. When
     * either [now] or [at] is between midnight and [EDGE] (the night belongs to the day before),
     * a day other than tonight is always named by its date, so "tomorrow" never means two things.
     */
    fun label(at: Instant, now: Instant): Label {
        val z = at.atZone(ISRAEL)
        val date = z.toLocalDate()
        val today = today(now)
        val nowNight = now.atZone(ISRAEL).toLocalTime().isBefore(EDGE)
        val atNight = z.toLocalTime().isBefore(EDGE)
        val word = when {
            date == today && (!nowNight || atNight) -> Word.TODAY
            date == today.plusDays(1) && !nowNight && !atNight -> Word.TOMORROW
            else -> Word.DATE
        }
        return Label(word, date, hhmm(at))
    }

    /** Between midnight and [EDGE]: the Today and Tomorrow chips show their dates too. */
    fun night(now: Instant): Boolean = now.atZone(ISRAEL).toLocalTime().isBefore(EDGE)

    /** "Thu 23 Oct" / "יום ה׳ 23 באוק׳", for the chip. */
    fun dateText(date: LocalDate, locale: Locale): String = date.format(DateTimeFormatter.ofPattern("EEE d MMM", locale))

    /** "Sun 20" / "יום א׳ 20", for a weekday chip. */
    fun chipText(date: LocalDate, locale: Locale): String = date.format(DateTimeFormatter.ofPattern("EEE d", locale))

    /** Material's date picker speaks UTC midnight milliseconds. */
    fun toPicker(date: LocalDate): Long = date.toEpochDay() * DAY_MS

    fun fromPicker(ms: Long): LocalDate = LocalDate.ofEpochDay(Math.floorDiv(ms, DAY_MS))

    private const val DAY_MS = 86_400_000L
}

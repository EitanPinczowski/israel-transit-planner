package il.transit.core.remind

import il.transit.core.features.ISRAEL
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Rav-Kav pass expiry (Phase 11 E1, 0 requests). The user types the pass's last day; the app
 * says so 3 days before and on the last day, at 09:00 Israel time, Shabbat included (owner
 * decision 4: a quiet, default-importance notification). Nothing about prices.
 * The Android side arms one inexact alarm for [next] and posts what [due] returns.
 */
object PassExpiry {
    /** 09:00 Israel time, whatever zone the phone is in. */
    val AT: LocalTime = LocalTime.of(9, 0)

    const val DAYS_BEFORE = 3L

    /** A date further ahead than this is refused: no pass is sold for longer (and a typo is likelier). */
    const val MAX_DAYS_AHEAD = 400L

    enum class Kind { SOON, LAST_DAY }

    data class Notice(val kind: Kind, val at: Instant, val endsOn: LocalDate)

    enum class Validity { OK, PAST, TOO_FAR }

    /** A date the user typed: the past and more than [MAX_DAYS_AHEAD] days ahead are refused. */
    fun check(endsOn: LocalDate, today: LocalDate): Validity = when {
        endsOn.isBefore(today) -> Validity.PAST
        endsOn.isAfter(today.plusDays(MAX_DAYS_AHEAD)) -> Validity.TOO_FAR
        else -> Validity.OK
    }

    /** Israel's date at [now]. */
    fun today(now: Instant): LocalDate = now.atZone(ISRAEL).toLocalDate()

    /** The saved date once it has passed: the setting goes back to Off. */
    fun isPast(endsOn: LocalDate, now: Instant): Boolean = endsOn.isBefore(today(now))

    /** Both notices, in order. The time is 09:00 on that day in Israel, so a DST change in between shifts the UTC hour. */
    fun notices(endsOn: LocalDate): List<Notice> = listOf(
        Notice(Kind.SOON, at(endsOn.minusDays(DAYS_BEFORE)), endsOn),
        Notice(Kind.LAST_DAY, at(endsOn), endsOn),
    )

    /** The next notice strictly after [now], for the alarm; null when both have gone. */
    fun next(endsOn: LocalDate, now: Instant): Notice? = notices(endsOn).firstOrNull { it.at.isAfter(now) }

    /**
     * What an alarm firing at [now] should post: the latest notice already due, if it is still
     * its own day in Israel (a Doze-delayed alarm still posts; one from yesterday does not).
     */
    fun due(endsOn: LocalDate, now: Instant): Notice? =
        notices(endsOn).lastOrNull { !it.at.isAfter(now) }?.takeIf { today(it.at) == today(now) }

    /** "Sun 1 Nov" (English), the same pattern in the app's language. */
    fun dateLabel(date: LocalDate, locale: Locale): String = DateTimeFormatter.ofPattern("EEE d MMM", locale).format(date)

    /** The stored form ([il.transit.core.user.UserSettings.passEndsOn]): ISO, or null for Off / a broken value. */
    fun parse(iso: String?): LocalDate? = iso?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private fun at(day: LocalDate): Instant = day.atTime(AT).atZone(ISRAEL).toInstant()
}

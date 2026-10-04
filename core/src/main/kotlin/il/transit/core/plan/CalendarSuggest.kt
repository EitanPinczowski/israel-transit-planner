package il.transit.core.plan

import il.transit.core.api.GeocodeMatch
import il.transit.core.api.TransitApi
import il.transit.core.features.ISRAEL
import il.transit.core.geo.LatLon
import il.transit.core.present.hhmm
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

/**
 * One instance of a calendar event, as the phone's calendar provider gives it. Read on the
 * phone only: the [title] never leaves it. Only [location] may be sent to the geocoder, and
 * only when the user picks the event and it holds no coordinates.
 */
data class CalendarEvent(
    val title: String,
    val location: String,
    val begin: Instant,
    val end: Instant,
    val allDay: Boolean = false,
    /** The user said no to the invitation. */
    val declined: Boolean = false,
)

/** "09:50 · Dentist · Herzl 12, Be'er Sheva"; [tomorrow] when it is not today (Israel time). */
data class CalendarRow(val time: String, val title: String, val location: String, val tomorrow: Boolean)

/**
 * Where a picked event takes place, ready to be the destination. [source] is the event's own
 * location text, shown next to [name] before planning. [approximate]: the geocoder's answer may
 * not be that place (only the town, or another house number): the user should check it.
 */
data class CalendarDestination(val name: String, val at: LatLon, val source: String, val approximate: Boolean = false)

/** "From my calendar": which events to offer, where they are, and when to arrive. */
object CalendarSuggest {
    /** How far ahead the list looks. */
    val WINDOW: Duration = Duration.ofHours(24)

    /** At most this many rows in the list. */
    const val MAX = 6

    /** Arrive this long before the event starts, by default. */
    const val DEFAULT_BUFFER_MIN = 10
    val BUFFER_CHOICES = listOf(0, 5, 10, 15, 20, 30)

    /**
     * The events worth offering, soonest first: in the next [WINDOW], with a location, not
     * all-day, not started yet, not declined. The same event seen twice (it sits in two
     * calendars) is shown once.
     */
    fun pick(events: List<CalendarEvent>, now: Instant, max: Int = MAX): List<CalendarEvent> =
        events.asSequence()
            .filter { !it.allDay && !it.declined && it.location.isNotBlank() }
            .filter { it.begin.isAfter(now) && !it.begin.isAfter(now.plus(WINDOW)) }
            .sortedBy { it.begin }
            .distinctBy { Triple(it.begin, it.title.trim(), it.location.trim()) }
            .take(max)
            .toList()

    fun row(e: CalendarEvent, now: Instant): CalendarRow = CalendarRow(
        time = hhmm(e.begin),
        title = e.title.trim(),
        location = e.location.trim(),
        tomorrow = e.begin.atZone(ISRAEL).toLocalDate() != now.atZone(ISRAEL).toLocalDate(),
    )

    /**
     * Arrive-by time: the event start minus [bufferMin]. null when that is already past
     * (the event starts in 5 min with a 10-min buffer): then the trip is simply "leave now".
     */
    fun arriveBy(e: CalendarEvent, bufferMin: Int, now: Instant): Instant? =
        e.begin.minusSeconds(bufferMin * 60L).takeIf { it.isAfter(now) }

    private val COORDS = Regex("""(-?\d{1,2}\.\d+)\s*[,;]\s*(-?\d{1,3}\.\d+)""")

    /**
     * Coordinates written in the location: "31.2622, 34.8013", "geo:31.26,34.80", or a maps
     * link holding them. Decimal points are required, so "Herzl 12, 3" is not read as a point.
     */
    fun parseLatLon(location: String): LatLon? {
        val m = COORDS.find(location) ?: return null
        val lat = m.groupValues[1].toDoubleOrNull() ?: return null
        val lon = m.groupValues[2].toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return LatLon(lat, lon)
    }

    /**
     * The geocoder's place bias, rounded to 0.1° (~10 km): enough to keep answers near the
     * user, and the same location text then hits the day-long geocode cache wherever the
     * user stands in town.
     */
    fun biasPoint(at: LatLon?): LatLon? = at?.let { LatLon(round1(it.lat), round1(it.lon)) }

    private fun isLink(text: String) = "://" in text || text.startsWith("geo:", ignoreCase = true)

    private fun round1(v: Double) = (v * 10).roundToInt() / 10.0

    /**
     * Where [e] is. Coordinates in the location cost nothing; otherwise ONE geocode request,
     * with only the location text (never the title). null when nothing is found: the user
     * then types the place.
     */
    suspend fun resolve(api: TransitApi, e: CalendarEvent, language: String, near: LatLon?): CalendarDestination? {
        val text = e.location.trim()
        parseLatLon(text)?.let { at ->
            val words = !isLink(text) && COORDS.replace(text, "").any { it.isLetter() }
            return CalendarDestination((if (words) text else e.title.trim()).ifBlank { text }, at, text)
        }
        // A short maps link (maps.app.goo.gl/…) holds no address to look up.
        if (isLink(text)) return null
        val m = api.geocode(text, language, biasPoint(near), 1).firstOrNull() ?: return null
        return CalendarDestination(m.name, LatLon(m.lat, m.lon), text, isApproximate(text, m))
    }

    private val NUMBER = Regex("""(?<![\d.])\d{1,4}(?![\d.])""")

    /**
     * Is [m] possibly not the place [location] names? Recorded 2026-10-04: "הרצל 12, באר שבע"
     * came back as "הרצל 126", and English addresses as the town alone. So: approximate when
     * the location has a house number the answer does not carry (another number, a street, a
     * stop or a town), or when the answer is a town or area itself (`place_*`).
     */
    fun isApproximate(location: String, m: GeocodeMatch): Boolean {
        if (m.type == "PLACE" && m.category?.startsWith("place") == true) return true
        val numbers = NUMBER.findAll(location).map { it.value.trimStart('0') }.toSet()
        if (numbers.isEmpty()) return false
        val house = m.houseNumber?.takeWhile { it.isDigit() }?.trimStart('0')
        return house == null || house !in numbers
    }
}

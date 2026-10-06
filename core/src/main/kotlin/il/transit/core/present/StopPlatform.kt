package il.transit.core.present

import il.transit.core.api.Place
import il.transit.core.api.TransitModes

/**
 * Where exactly to stand: "Platform 12 · floor 6 · stop 47899". Each part is null when the
 * data does not have it; the UI shows nothing when all are null ([isEmpty]).
 */
data class StopPlatform(val platform: String? = null, val floor: String? = null, val stopCode: String? = null) {
    val isEmpty: Boolean get() = platform == null && floor == null && stopCode == null

    fun ifEmpty(other: () -> StopPlatform): StopPlatform = if (isEmpty) other() else this
}

/**
 * The parts of a stop's description text, from the MOT feed:
 * `רחוב: תחנה מרכזית קומה 6 עיר: תל אביב יפו רציף: 626 קומה: 6`. Keys are Hebrew words
 * ending in ':'; a value runs to the next key. Unknown keys, extra spaces, English text or a
 * blank description give nulls, never an exception.
 */
fun parseStopDescription(text: String?): StopPlatform {
    if (text.isNullOrBlank()) return StopPlatform()
    val keys = KEY.findAll(text).toList()
    fun value(name: String): String? {
        val i = keys.indexOfFirst { it.groupValues[1] == name }.takeIf { it >= 0 } ?: return null
        val end = keys.getOrNull(i + 1)?.range?.first ?: text.length
        return text.substring(keys[i].range.last + 1, end).trim().takeIf { v -> v.length <= MAX_VALUE && v.any { it.isLetterOrDigit() } }
    }
    return StopPlatform(platform = value(PLATFORM), floor = value(FLOOR))
}

/**
 * Platform, floor and stop code of [place], or empty. [rail]: the place is boarded or left by
 * train. Israel Railways sends no platform (`track` is empty on every leg, see `dead-ends`)
 * and its stop code is not on any sign, so a station shows nothing.
 */
fun stopPlatform(place: Place, rail: Boolean = isRailStation(place)): StopPlatform {
    if (rail) return StopPlatform()
    return parseStopDescription(place.description).copy(stopCode = place.stopCode?.trim()?.takeIf { it.isNotEmpty() })
}

private fun isRailStation(place: Place): Boolean =
    !place.modes.isNullOrEmpty() && place.modes.all { it in TransitModes.HEAVY_RAIL }

private const val PLATFORM = "רציף"
private const val FLOOR = "קומה"

/** Longer than this is not a platform or floor number but stray text. */
private const val MAX_VALUE = 10

/** A Hebrew word right before ':' (at the start or after a space). */
private val KEY = Regex("""(?:^|\s)([א-ת]+)\s*:""")

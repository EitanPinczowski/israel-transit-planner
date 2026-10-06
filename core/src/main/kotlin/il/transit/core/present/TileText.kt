package il.transit.core.present

import il.transit.core.plan.CacheLookup

/**
 * What the Quick Settings tile (Phase 9 C4) says, before the app turns it into strings:
 * "Set Home", "Home · tap to plan", or "Home · 22:40–23:35" (an en dash, which reads right
 * in Hebrew too). Pure, so the cases are tested without a phone.
 */
sealed interface TileText {
    /** No Home set (or its place was deleted): the tap opens Settings. */
    data object NoHome : TileText

    /** Nothing cached home, or every cached option already left (Saturday afternoon). */
    data object TapToPlan : TileText

    /** The next cached option home, leave and arrive, Israel time. */
    data class Next(val leave: String, val arrive: String) : TileText {
        val times: String get() = "$leave–$arrive"
    }

    companion object {
        fun of(homeSet: Boolean, hit: CacheLookup.TripHome?): TileText = when {
            !homeSet -> NoHome
            hit?.next == null -> TapToPlan
            else -> Next(hhmm(hit.next.start), hhmm(hit.next.end))
        }
    }
}

package il.transit.core.present

/**
 * How the main screen shares its space between the search, the results and the map, in dp.
 * Plain arithmetic so the rules are unit-tested; the UI tests' layout audit (R1, R7) checks
 * the result on every phone profile.
 */
object ScreenLayout {
    /** From this width on (tablets, phones in landscape) search and results sit in a side column. */
    const val WIDE_FROM_DP = 600f

    /** The side column's width in wide mode; the map takes the rest. */
    const val SIDE_COLUMN_DP = 380f

    /** On a phone, the map keeps at least this share of the screen height between the panels. */
    const val MIN_MAP_SHARE = 0.3f

    /** A panel never shrinks below its header and one row. */
    const val MIN_PANEL_DP = 120f

    fun isWide(widthDp: Float): Boolean = widthDp >= WIDE_FROM_DP

    /**
     * The tallest the bottom panel may be: the height under the search area ([topBottomDp] is
     * where it ends, from the top of the screen), minus the map's share on a phone. In a side
     * column the map is beside it, so the panel may take all of it.
     */
    fun panelCap(heightDp: Float, topBottomDp: Float, wide: Boolean): Float {
        val map = if (wide) 0f else MIN_MAP_SHARE * heightDp
        return (heightDp - topBottomDp - map).coerceAtLeast(MIN_PANEL_DP)
    }
}

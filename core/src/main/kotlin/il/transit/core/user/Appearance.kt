package il.transit.core.user

/**
 * Settings → Appearance (Phase 9 C6): the app's own language and light/dark choice, apart
 * from the phone's. Pure, so the app's Activity, receivers and workers all agree.
 */
object Appearance {
    const val SYSTEM = "SYSTEM"
    const val LIGHT = "LIGHT"
    const val DARK = "DARK"

    val THEMES = listOf(SYSTEM, LIGHT, DARK)

    /** The language choices in Settings; null = the phone's. */
    val LANGUAGES: List<String?> = listOf(null, "he", "en")

    /** Dark UI and map? [SYSTEM] (and anything unknown from a newer version) follows the phone. */
    fun dark(theme: String, systemDark: Boolean): Boolean = when (theme) {
        LIGHT -> false
        DARK -> true
        else -> systemDark
    }

    /**
     * The language sent to Transitous for stop names and geocoding: the app's language when
     * set, else the phone's. Hebrew unless that is something other than Hebrew ("iw" is the
     * old code Android still reports on some versions).
     */
    fun transitLanguage(appLanguage: String?, phoneLanguage: String): String =
        when ((appLanguage ?: phoneLanguage).substringBefore('-').lowercase()) {
            "iw", "he" -> "he"
            else -> "en"
        }

    /** A stored tag the Settings chips know, else null (System): "iw" from an old phone reads as "he". */
    fun normalizeLanguage(tag: String?): String? = when (tag?.substringBefore('-')?.lowercase()) {
        null, "" -> null
        "iw", "he" -> "he"
        "en" -> "en"
        else -> null
    }
}

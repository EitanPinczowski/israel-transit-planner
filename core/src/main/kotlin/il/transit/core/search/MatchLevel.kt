package il.transit.core.search

/**
 * How much of what was typed a search answer is ([PlaceSearch.level], Phase 10 D2). Only
 * [HOUSE] and [PLACE] are "the answer"; the rest stay under "Closest matches", and the row says
 * what is missing ("Street only: no number 15", "Town only").
 */
enum class MatchLevel {
    /** The address as typed: its house number (and letter, range) when one was typed. */
    HOUSE,

    /** The typed street, but not the typed house number (Transitous has "הרצל" or "הרצל 126" for "הרצל 12"). */
    STREET,

    /** Only the town the text names. */
    TOWN,

    /** A named place, a stop, or the town itself when only a town was typed. */
    PLACE,

    /** Near the text, not it. */
    NONE,
    ;

    /** May be shown as the answer. */
    val isAnswer: Boolean get() = this == HOUSE || this == PLACE

    /** The row should say what is missing. */
    val partial: Boolean get() = this == STREET || this == TOWN
}

/** What a suggestion row says about its level: [house] is the typed house ("15", "12א", "12–14") a [MatchLevel.STREET] row lacks. */
data class MatchNote(val level: MatchLevel, val house: String? = null)

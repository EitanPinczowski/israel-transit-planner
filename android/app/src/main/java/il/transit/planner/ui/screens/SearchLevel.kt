package il.transit.planner.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import il.transit.core.search.MatchLevel
import il.transit.core.search.MatchNote
import il.transit.planner.R

/**
 * What a "Closest matches" row is missing (Phase 10 D2): "Street only: no number 15", "Town
 * only". Null when the row is the answer, or near it in no simpler way.
 */
@Composable
internal fun matchNoteText(note: MatchNote?): String? = when (note?.level) {
    MatchLevel.STREET -> note.house?.let { stringResource(R.string.search_level_street_no_number, it) } ?: stringResource(R.string.search_level_street)
    MatchLevel.TOWN -> stringResource(R.string.search_level_town)
    else -> null
}

package il.transit.planner.ui.screens

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import il.transit.core.present.Phrase
import il.transit.core.present.SpeechKey
import il.transit.core.present.Spoken
import il.transit.planner.R

/**
 * Phase 11 E5: turns core's [Spoken] (present/Speech.kt) into the words TalkBack reads, in the app's
 * language (`strings_a11y.xml`). Used as a `contentDescription`; never drawn on screen.
 */
@Composable
internal fun spoken(s: Spoken): String {
    val res = LocalContext.current.resources
    return s.render { say(res, it) }
}

/** One phrase in [res]'s language. Plurals pass the count last, after the text arguments. */
internal fun say(res: Resources, p: Phrase): String {
    val args: Array<Any> = (p.args + listOfNotNull(p.count.takeIf { p.key.plural })).toTypedArray()
    return if (p.key.plural) res.getQuantityString(plural(p.key), p.count ?: 0, *args) else res.getString(string(p.key), *args)
}

private fun string(k: SpeechKey): Int = when (k) {
    SpeechKey.BUS_LINE -> R.string.a11y_bus_line
    SpeechKey.LIGHT_RAIL_LINE -> R.string.a11y_light_rail_line
    SpeechKey.OTHER_LINE -> R.string.a11y_other_line
    SpeechKey.TRAIN_NUMBER_TO -> R.string.a11y_train_number_to
    SpeechKey.TRAIN_NUMBER -> R.string.a11y_train_number
    SpeechKey.KIND_BUS -> R.string.a11y_kind_bus
    SpeechKey.KIND_TRAIN -> R.string.a11y_kind_train
    SpeechKey.KIND_LIGHT_RAIL -> R.string.a11y_kind_light_rail
    SpeechKey.KIND_OTHER -> R.string.a11y_kind_other
    SpeechKey.TIMES -> R.string.a11y_times
    SpeechKey.GET_OFF -> R.string.a11y_get_off
    SpeechKey.TO_PLACE -> R.string.a11y_to_place
    SpeechKey.BADGE_ACCESSIBLE -> R.string.a11y_badge_accessible
    SpeechKey.BADGE_MAY_NOT_BE -> R.string.a11y_badge_may_not_be
    SpeechKey.LEAVE_AT -> R.string.a11y_leave_at
    SpeechKey.ARRIVE_AT -> R.string.a11y_arrive_at
    SpeechKey.DIRECT -> R.string.a11y_direct
    SpeechKey.HEADSIGN -> R.string.a11y_headsign
    SpeechKey.LEAVES_AT -> R.string.a11y_leaves_at
    SpeechKey.CANCELLED -> R.string.a11y_cancelled
    else -> error("$k is a plural")
}

private fun plural(k: SpeechKey): Int = when (k) {
    SpeechKey.WALK_MIN -> R.plurals.a11y_walk_min
    SpeechKey.DRIVE_MIN -> R.plurals.a11y_drive_min
    SpeechKey.STOPS -> R.plurals.a11y_stops
    SpeechKey.MINUTES -> R.plurals.a11y_minutes
    SpeechKey.TRANSFERS -> R.plurals.a11y_transfers
    SpeechKey.LATE_MIN -> R.plurals.a11y_late_min
    SpeechKey.TIGHT_CHANGE -> R.plurals.a11y_tight_change
    else -> error("$k is not a plural")
}

/** Test tag of a leg chip: the a11y journey compares its description with [il.transit.core.present.Speech]. */
internal fun legChipTag(legIndex: Int) = "leg-chip-$legIndex"

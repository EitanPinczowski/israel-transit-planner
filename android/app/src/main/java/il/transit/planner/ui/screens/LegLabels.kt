package il.transit.planner.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import il.transit.core.api.Itinerary
import il.transit.core.api.Leg
import il.transit.core.present.AccessibleBadge
import il.transit.core.present.LegChip
import il.transit.core.present.LegKind
import il.transit.core.present.TrainLabel
import il.transit.core.present.accessibleBadge
import il.transit.core.present.fewerStops
import il.transit.core.present.trainLabel
import il.transit.planner.R

/**
 * Leg labels (Phase 11 E3): the train number ("Train 24 to Nahariya · 3 stops on the way"), the
 * "Fewer stops" chip and the accessible-bus badge. Every decision is in core
 * (`present/TrainLabel.kt`, `present/AccessibleBadge.kt`); this file only joins strings.
 */
@Composable
internal fun trainText(t: TrainLabel, withStops: Boolean): String {
    val head = t.to?.let { stringResource(R.string.train_to, t.number, it) } ?: stringResource(R.string.train_number, t.number)
    if (!withStops) return head
    val stops = if (t.stopsBetween == 0) stringResource(R.string.train_no_stops)
    else pluralStringResource(R.plurals.train_stops_between, t.stopsBetween, t.stopsBetween)
    return stringResource(R.string.train_label, head, stops)
}

/** The card's leg chip, with the number on a train ("Train 24") instead of the kind alone. */
@Composable
internal fun withTrainNumber(c: LegChip, itin: Itinerary): LegChip {
    if (c.kind != LegKind.TRAIN || c.legIndex !in itin.legs.indices) return c
    val t = remember(itin, c.legIndex) { trainLabel(itin.legs[c.legIndex]) } ?: return c
    return c.copy(label = stringResource(R.string.train_number, t.number))
}

/** Indices of the options with a train that makes at least 2 fewer stops than a comparable one. */
@Composable
internal fun rememberFewerStops(options: List<Itinerary>): Set<Int> =
    remember(options) { fewerStops(options).map { it.option }.toSet() }

@Composable
internal fun FewerStopsChip() {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer, shape = MaterialTheme.shapes.extraSmall) {
        Text(stringResource(R.string.fewer_stops), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

/** The trip sheet's title: a train's label with its stops, else the vehicle's destination. */
@Composable
internal fun legTitle(leg: Leg, headsign: String): String =
    remember(leg) { trainLabel(leg) }?.let { trainText(it, withStops = true) } ?: headsign

/**
 * Under a bus leg's header, with Accessible routes on: the badge with its words (never the icon
 * alone), or an amber line. Nothing on rail, nothing when the data says nothing.
 */
@Composable
internal fun AccessibleBadgeRow(leg: Leg, accessibleRoutes: Boolean) {
    when (remember(leg, accessibleRoutes) { accessibleBadge(leg, accessibleRoutes) }) {
        AccessibleBadge.ACCESSIBLE -> Surface(color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer, shape = MaterialTheme.shapes.extraSmall) {
            Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // The words say it; the symbol is decoration for TalkBack.
                Icon(Wheelchair, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(stringResource(R.string.accessible_bus), style = MaterialTheme.typography.labelMedium)
            }
        }
        AccessibleBadge.MAY_NOT_BE -> Surface(color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer, shape = MaterialTheme.shapes.extraSmall) {
            Text("⚠ " + stringResource(R.string.bus_may_not_be_accessible), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
        }
        null -> Unit
    }
}

/** Material's "Accessible" symbol (the core icon set has none). A real-world sign: never mirrored. */
private val Wheelchair: ImageVector by lazy {
    ImageVector.Builder("Wheelchair", 24.dp, 24.dp, 24f, 24f).addPath(
        addPathNodes(
            "M12,4m-2,0a2,2 0,1 1,4 0a2,2 0,1 1,-4 0" +
                "M19,13v-2c-1.54,0.02 -3.09,-0.75 -4.07,-1.83l-1.29,-1.43c-0.17,-0.19 -0.38,-0.34 -0.61,-0.45 " +
                "-0.01,0 -0.01,-0.01 -0.02,-0.01H13c-0.35,-0.2 -0.75,-0.3 -1.19,-0.26C10.76,7.11 10,8.04 10,9.09V15" +
                "c0,1.1 0.9,2 2,2h5v5h2v-5.5c0,-1.1 -0.9,-2 -2,-2h-3v-3.45c1.29,1.07 3.25,1.94 5,1.95z" +
                "M13.83,18c-0.41,1.16 -1.52,2 -2.83,2 -1.66,0 -3,-1.34 -3,-3 0,-1.31 0.84,-2.41 2,-2.83V12.1" +
                "c-2.28,0.46 -4,2.48 -4,4.9 0,2.76 2.24,5 5,5 2.42,0 4.44,-1.72 4.9,-4h-2.07z",
        ),
        fill = SolidColor(Color.Black),
    ).build()
}

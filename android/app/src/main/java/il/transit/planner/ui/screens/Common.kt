@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package il.transit.planner.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import il.transit.core.fare.FareEstimate
import il.transit.core.present.LegChip
import il.transit.core.present.LegKind
import il.transit.core.present.fareLabel
import il.transit.planner.R

private const val TRANSITOUS_SOURCES = "https://transitous.org/sources/"

/** 5–30 minutes in steps of 5. Local while dragging; [onDone] (a search) runs once, on release. */
@Composable
internal fun MinutesSlider(labelRes: Int, current: Int, onDone: (Int) -> Unit) {
    var value by remember(current) { mutableStateOf(current.toFloat()) }
    Column {
        Text(stringResource(labelRes, value.toInt()), style = MaterialTheme.typography.labelLarge)
        Slider(
            value = value,
            onValueChange = { value = (Math.round(it / 5f) * 5).toFloat() },
            onValueChangeFinished = { onDone(value.toInt()) },
            valueRange = 5f..30f,
            steps = 4,
        )
    }
}

@Composable
internal fun PlaceRow(
    labelRes: Int,
    value: String?,
    active: Boolean,
    placeholderRes: Int = R.string.choose_destination,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(labelRes), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(64.dp))
        Text(
            value ?: stringResource(placeholderRes),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** "≈ ₪8", isolated so Hebrew text around it cannot reorder the amount. */
@Composable
internal fun FareText(f: FareEstimate?) {
    if (f == null) return
    val text = if (f.agorot == 0) stringResource(R.string.fare_free_label) else "\u2068${fareLabel(f)}\u2069"
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
}

@Composable
internal fun LegChipView(c: LegChip, onClick: (() -> Unit)? = null) {
    val color = parseColor(c.color)
    val text = when (c.kind) {
        LegKind.WALK, LegKind.CAR -> "${kindName(c.kind)} ${c.minutes}′"
        else -> c.label ?: kindName(c.kind)
    }.let { if (c.alert) "⚠ $it" else it } // only when an answer carries an alert
    Box(
        Modifier.background(if (c.kind == LegKind.WALK) Color.Transparent else color, RoundedCornerShape(6.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        val live = when {
            c.delayMin != null && c.delayMin!! > 0 -> " +${c.delayMin}"
            c.realTime -> " ●"
            else -> ""
        }
        Text(
            text + live,
            style = MaterialTheme.typography.labelMedium,
            color = if (c.kind == LegKind.WALK) MaterialTheme.colorScheme.onSurfaceVariant else Color.White,
        )
    }
}

@Composable
internal fun kindName(k: LegKind): String = stringResource(
    when (k) {
        LegKind.WALK -> R.string.kind_walk
        LegKind.BUS -> R.string.kind_bus
        LegKind.TRAIN -> R.string.kind_train
        LegKind.LIGHT_RAIL -> R.string.kind_light_rail
        LegKind.CAR -> R.string.kind_car
        LegKind.OTHER -> R.string.kind_other
    },
)

internal fun parseColor(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color.Gray)

@Composable
internal fun NameDialog(titleRes: Int, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, placeholder = { Text(stringResource(R.string.name_hint)) })
        },
        confirmButton = { TextButton(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
internal fun AttributionChip(modifier: Modifier) {
    val context = LocalContext.current
    Surface(
        modifier = modifier.clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TRANSITOUS_SOURCES))) },
        shape = CircleShape,
        tonalElevation = 2.dp,
    ) {
        Text(stringResource(R.string.attribution), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

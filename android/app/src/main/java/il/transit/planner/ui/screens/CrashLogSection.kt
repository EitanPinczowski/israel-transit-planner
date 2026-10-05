package il.transit.planner.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import il.transit.planner.R

/** What Settings needs for the local crash log: how many are kept, and the two buttons. */
class CrashLogUi(
    val count: Int = 0,
    /** Opens the share sheet with the log as plain text. The user picks where it goes. */
    val share: () -> Unit = {},
    val clear: () -> Unit = {},
)

/** "Crash log": how many are kept on the phone, then Share / Clear. Nothing is sent by itself. */
@Composable
internal fun CrashLogSection(log: CrashLogUi) {
    Column {
        Text(stringResource(R.string.crash_log), style = MaterialTheme.typography.labelLarge)
        Text(
            if (log.count == 0) stringResource(R.string.crash_log_none)
            else pluralStringResource(R.plurals.crash_log_count, log.count, log.count),
            style = MaterialTheme.typography.bodySmall,
        )
        if (log.count > 0) {
            Row {
                TextButton(onClick = log.share) { Text(stringResource(R.string.crash_log_share)) }
                TextButton(onClick = log.clear) { Text(stringResource(R.string.crash_log_clear)) }
            }
        }
    }
}

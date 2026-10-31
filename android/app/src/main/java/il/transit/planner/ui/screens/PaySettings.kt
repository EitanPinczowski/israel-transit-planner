@file:OptIn(ExperimentalLayoutApi::class)

package il.transit.planner.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import il.transit.core.present.PayApp
import il.transit.core.present.PayApps
import il.transit.core.user.UserSettings
import il.transit.planner.R
import il.transit.planner.ui.PayWith

/** The app's language for the payment apps' names ("iw"/"he" → Hebrew). */
@Composable
private fun payLanguage(): String = LocalConfiguration.current.locales[0].language

/**
 * Settings → "Paying for rides" → "My payment app" (Phase 11 E4): None (default) or one of the
 * owner's list. 0 requests; we never take a payment.
 */
@Composable
internal fun PaySettingsSection(s: UserSettings, set: (UserSettings) -> Unit) {
    val lang = payLanguage()
    val chosen = PayApps.chosen(s.paymentApp)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.pay_settings_title), style = MaterialTheme.typography.labelLarge)
        Text(stringResource(R.string.pay_my_app), style = MaterialTheme.typography.bodyMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(chosen == null, { set(s.copy(paymentApp = null)) }, label = { Text(stringResource(R.string.pay_none)) })
            PayApps.ALL.forEach { a ->
                FilterChip(chosen == a, { set(s.copy(paymentApp = a.packageName)) }, label = { Text(a.name(lang)) })
            }
        }
        Text(stringResource(R.string.pay_settings_help), style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * The trip sheet's button: "Pay with Moovit" when a payment app is chosen, else "Pay with…",
 * which opens the chooser (installed apps first). One line under it says we take no payment.
 */
@Composable
internal fun PayWithButton(paymentApp: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lang = payLanguage()
    val chosen = PayApps.chosen(paymentApp)
    var choosing by rememberSaveable { mutableStateOf(false) }
    fun open(app: PayApp) {
        if (!PayWith.open(context, app)) Toast.makeText(context, R.string.pay_open_failed, Toast.LENGTH_SHORT).show()
    }
    Column(modifier) {
        OutlinedButton(onClick = { if (chosen != null) open(chosen) else choosing = true }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(chosen?.let { stringResource(R.string.pay_with_app, it.name(lang)) } ?: stringResource(R.string.pay_with))
        }
        PayDisclaimer()
    }
    if (choosing) {
        val installed = remember { runCatching { PayWith.installed(context) }.getOrDefault(emptySet()) }
        PayChooserDialog(installed, onDismiss = { choosing = false }) { app ->
            choosing = false
            open(app)
        }
    }
}

@Composable
internal fun PayDisclaimer() {
    Text(stringResource(R.string.pay_disclaimer), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun PayChooserDialog(installed: Set<String>, onDismiss: () -> Unit, onPick: (PayApp) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pay_with)) },
        text = { PayChooserContent(installed, onPick) },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** The chooser's body on its own, so screenshot tests can render it (Paparazzi draws no dialog windows). */
@Composable
internal fun PayChooserContent(installed: Set<String>, onPick: (PayApp) -> Unit) {
    val lang = payLanguage()
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        PayApps.chooserOrder(installed).forEach { a ->
            val has = a.packageName in installed
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button) { onPick(a) }
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(a.name(lang), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(if (has) R.string.pay_installed else R.string.pay_get),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (has) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        PayDisclaimer()
    }
}

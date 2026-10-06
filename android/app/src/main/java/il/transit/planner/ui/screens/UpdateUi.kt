package il.transit.planner.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import il.transit.core.update.LatestRelease
import il.transit.core.update.UpdateCheck
import il.transit.core.update.UpdateFailure
import il.transit.core.update.UpdateState
import il.transit.planner.BuildConfig
import il.transit.planner.R
import il.transit.planner.data.ApkInstaller
import il.transit.planner.ui.MainActions

/**
 * "Version 0.8.0 is ready · Update" over the map. One tap downloads inside the app (progress
 * here), then opens Android's own "Update this app?" prompt. Without the install permission it
 * opens that settings screen and continues when the user comes back.
 */
@Composable
internal fun UpdateBanner(latest: LatestRelease, state: UpdateState, vm: MainActions) {
    val phase = state as? UpdateState.WithRelease ?: UpdateState.Available(latest)
    val context = LocalContext.current
    val launch = updateLaunchers(phase, vm)
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(updateLine(phase), modifier = Modifier.weight(1f).padding(vertical = 8.dp), style = MaterialTheme.typography.bodyMedium)
                when (phase) {
                    is UpdateState.Available -> TextButton(onClick = vm::startUpdate) { Text(stringResource(R.string.update_action)) }
                    is UpdateState.NeedsPermission -> TextButton(onClick = { launch?.permission?.invoke() }) { Text(stringResource(R.string.update_allow)) }
                    is UpdateState.Ready -> TextButton(onClick = { launch?.install?.invoke(phase.file) }) { Text(stringResource(R.string.update_install)) }
                    else -> Unit
                }
                IconButton(onClick = vm::dismissUpdate) { Icon(Icons.Default.Close, stringResource(R.string.close)) }
            }
            when (phase) {
                is UpdateState.Downloading -> {
                    val mod = Modifier.fillMaxWidth().padding(end = 12.dp, bottom = 12.dp)
                    val pct = phase.percent
                    if (pct == null) LinearProgressIndicator(mod) else LinearProgressIndicator(progress = { pct / 100f }, modifier = mod)
                }
                is UpdateState.Failed -> Row(Modifier.fillMaxWidth().padding(end = 4.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { openInBrowser(context, phase.release) }) { Text(stringResource(R.string.update_browser)) }
                    TextButton(onClick = vm::startUpdate) { Text(stringResource(R.string.update_retry)) }
                }
                else -> Unit
            }
        }
    }
}

/** Settings: "Version 0.7.0" with "Check for updates", and what the last check found. */
@Composable
internal fun UpdateSettingsRow(state: UpdateState, vm: MainActions, version: String = BuildConfig.VERSION_NAME) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.update_version, version), style = MaterialTheme.typography.labelLarge)
            val line = when (state) {
                UpdateState.Idle -> null
                UpdateState.Checking -> stringResource(R.string.update_checking)
                is UpdateState.UpToDate -> stringResource(R.string.update_latest, state.current)
                UpdateState.CheckFailed -> stringResource(R.string.update_check_failed)
                is UpdateState.WithRelease -> updateLine(state)
            }
            line?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        when (state) {
            is UpdateState.Available -> TextButton(onClick = vm::startUpdate) { Text(stringResource(R.string.update_action)) }
            is UpdateState.Failed -> TextButton(onClick = vm::startUpdate) { Text(stringResource(R.string.update_retry)) }
            UpdateState.Idle, is UpdateState.UpToDate, UpdateState.CheckFailed ->
                TextButton(onClick = vm::checkForUpdates) { Text(stringResource(R.string.update_check)) }
            else -> Unit // checking, downloading, or the banner is asking Android
        }
    }
}

@Composable
private fun updateLine(s: UpdateState.WithRelease): String {
    val v = s.release.version
    return when (s) {
        is UpdateState.Available -> stringResource(R.string.update_ready, v)
        is UpdateState.Downloading -> s.percent?.let { stringResource(R.string.update_downloading, v, it) }
            ?: stringResource(R.string.update_downloading_unknown, v)
        is UpdateState.NeedsPermission -> stringResource(R.string.update_needs_permission)
        is UpdateState.Ready -> stringResource(R.string.update_install_ready, v)
        is UpdateState.Failed -> stringResource(
            when (s.reason) {
                UpdateFailure.NETWORK -> R.string.update_failed_network
                UpdateFailure.NO_SPACE -> R.string.update_failed_space
                UpdateFailure.CHECKSUM -> R.string.update_failed_checksum
                UpdateFailure.BAD_URL -> R.string.update_failed_url
                UpdateFailure.DIFFERENT_BUILD -> R.string.update_failed_different_build
                UpdateFailure.INSTALL -> R.string.update_failed_install
            },
        )
    }
}

/** The old way, as the fallback: the APK link if it is GitHub's, else the release page. */
private fun openInBrowser(context: Context, r: LatestRelease) {
    val url = r.apkUrl?.takeIf(UpdateCheck::isAllowedApkUrl) ?: r.pageUrl
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

private class UpdateLaunchers(val install: (String) -> Unit, val permission: () -> Unit)

/**
 * Android's install prompt and "Install unknown apps" screen, each opened once when the update
 * gets there (and again from the banner's button). Null without an Activity (screenshot tests).
 */
@Composable
private fun updateLaunchers(phase: UpdateState.WithRelease, vm: MainActions): UpdateLaunchers? {
    if (LocalActivityResultRegistryOwner.current == null) return null
    val context = LocalContext.current
    val installer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val code = r.data?.takeIf { it.hasExtra(ApkInstaller.EXTRA_INSTALL_RESULT) }?.getIntExtra(ApkInstaller.EXTRA_INSTALL_RESULT, 0)
        vm.updateInstallFinished(r.resultCode, code)
    }
    // Back from the settings screen, allowed or not: try again (it asks again only on a tap).
    val settings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { vm.startUpdate() }
    val launchers = remember(installer, settings) {
        UpdateLaunchers(
            install = { file ->
                runCatching { installer.launch(ApkInstaller.installIntent(context, file)) }
                    .onFailure { vm.updateInstallFinished(NO_INSTALLER, null) }
            },
            permission = { runCatching { settings.launch(ApkInstaller.permissionIntent(context)) } },
        )
    }
    when (phase) {
        is UpdateState.Ready -> LaunchedEffect(phase.file) { launchers.install(phase.file) }
        is UpdateState.NeedsPermission -> LaunchedEffect(Unit) { launchers.permission() }
        else -> Unit
    }
    return launchers
}

/** Activity.RESULT_FIRST_USER: what we report when no installer could be opened at all. */
private const val NO_INSTALLER = 1

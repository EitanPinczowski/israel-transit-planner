package il.transit.planner.ui

import il.transit.core.update.UpdateCheck
import il.transit.core.update.UpdateFlow
import il.transit.core.update.UpdateState
import il.transit.planner.data.ApkInstaller
import il.transit.planner.data.UpdateChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant

/**
 * The in-app update, for [MainViewModel]: the daily check, Settings → "Check for updates"
 * (once a minute at most), and Update → download → verify → Android's prompt. Transitions are
 * core's [UpdateFlow]; this only does the I/O and writes [UiState.update] / [UiState.updateState].
 */
internal class UpdateController(
    private val checker: UpdateChecker,
    private val scope: CoroutineScope,
    private val clock: Clock,
    private val edit: ((UiState) -> UiState) -> Unit,
    private val current: () -> UiState,
) {
    private var lastManual: Instant? = null

    fun daily() = scope.launch {
        checker.check(clock.instant())?.let { r -> edit { it.copy(update = r, updateState = UpdateState.Available(r)) } }
    }

    fun checkNow() {
        val s = current().updateState
        if (s is UpdateState.Checking || s is UpdateState.Downloading) return
        val now = clock.instant()
        if (!UpdateCheck.canCheckNow(lastManual, now)) return
        lastManual = now
        edit { it.copy(updateState = UpdateState.Checking) }
        scope.launch {
            val next = UpdateFlow.afterCheck(checker.currentVersion, checker.checkNow(now))
            edit { it.copy(updateState = next, update = (next as? UpdateState.Available)?.release ?: it.update) }
        }
    }

    fun start() {
        val installer = checker.installer ?: return
        val st = current()
        val from = st.updateState as? UpdateState.WithRelease ?: st.update?.let(UpdateState::Available) ?: return
        val next = UpdateFlow.onUpdate(from, installer.canInstall())
        edit { it.copy(updateState = next, update = (next as? UpdateState.WithRelease)?.release ?: it.update) }
        if (next is UpdateState.Downloading && from !is UpdateState.Downloading) download(installer, next.release.apkUrl!!)
    }

    fun installFinished(resultCode: Int, installCode: Int?) =
        edit { it.copy(updateState = UpdateFlow.installResult(it.updateState, resultCode, installCode)) }

    private fun download(installer: ApkInstaller, url: String) = scope.launch {
        val result = installer.download(url) { read, total ->
            if (UpdateFlow.progress(current().updateState, read, total) != current().updateState) {
                edit { it.copy(updateState = UpdateFlow.progress(it.updateState, read, total)) }
            }
        }
        edit {
            it.copy(
                updateState = when (result) {
                    is ApkInstaller.Download.Done -> UpdateFlow.downloaded(it.updateState, result.file.path, result.sha256)
                    is ApkInstaller.Download.Failed -> UpdateFlow.failed(it.updateState, result.reason)
                },
            )
        }
    }
}

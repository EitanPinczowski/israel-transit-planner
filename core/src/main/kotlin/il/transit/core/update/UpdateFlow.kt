package il.transit.core.update

/** Why an in-app update stopped. Each has its own one-line message. */
enum class UpdateFailure {
    /** The download was cut (no connection, server error). */
    NETWORK,
    /** The phone has no room for the APK. */
    NO_SPACE,
    /** The file is not the one GitHub published (digest mismatch, or no digest to compare). */
    CHECKSUM,
    /** The link (or a redirect) left GitHub, or there is no APK in the release. */
    BAD_URL,
    /** Android refused: the installed copy is signed with another key (a debug or test build). */
    DIFFERENT_BUILD,
    /** Android refused for another reason. */
    INSTALL,
}

/**
 * The update banner and the Settings row, as one state. Idle → Checking → UpToDate / CheckFailed
 * / Available → (tap Update) → NeedsPermission ↔ Downloading(pct) → Ready → Android's prompt.
 * Every step after Available can end in Failed, whose "Try again" is a tap on Update.
 */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val current: String) : UpdateState
    data object CheckFailed : UpdateState

    /** The states that carry a newer release (the banner shows for these). */
    sealed interface WithRelease : UpdateState { val release: LatestRelease }

    data class Available(override val release: LatestRelease) : WithRelease
    /** [percent] is null while the size is unknown. */
    data class Downloading(override val release: LatestRelease, val percent: Int?) : WithRelease
    /** Android hasn't let this app install apps. [file] is the verified APK, if already downloaded. */
    data class NeedsPermission(override val release: LatestRelease, val file: String? = null) : WithRelease
    /** Verified APK at [file]; Android's install prompt is (or can be) open. */
    data class Ready(override val release: LatestRelease, val file: String) : WithRelease
    data class Failed(override val release: LatestRelease, val reason: UpdateFailure) : WithRelease
}

/** Pure transitions; the app does the I/O and feeds the outcomes here. */
object UpdateFlow {
    /** Activity result codes of Android's installer (Activity.RESULT_*), and the one failure we name. */
    const val RESULT_OK = -1
    const val RESULT_CANCELED = 0
    const val INSTALL_FAILED_UPDATE_INCOMPATIBLE = -7

    fun afterCheck(current: String, result: CheckResult): UpdateState = when (result) {
        CheckResult.UpToDate -> UpdateState.UpToDate(current)
        CheckResult.Failed -> UpdateState.CheckFailed
        is CheckResult.Newer -> UpdateState.Available(result.release)
    }

    /**
     * A tap on Update (or Try again, or coming back from the permission screen). Downloading
     * ignores it. Without the install permission → NeedsPermission, keeping a file already
     * verified; with it → Ready again for that file, or a fresh download from an allowed URL.
     */
    fun onUpdate(state: UpdateState, canInstall: Boolean): UpdateState {
        if (state !is UpdateState.WithRelease || state is UpdateState.Downloading) return state
        val r = state.release
        val file = when (state) {
            is UpdateState.Ready -> state.file
            is UpdateState.NeedsPermission -> state.file
            else -> null
        }
        return when {
            !canInstall -> UpdateState.NeedsPermission(r, file)
            file != null -> UpdateState.Ready(r, file)
            r.apkUrl == null || !UpdateCheck.isAllowedApkUrl(r.apkUrl) -> UpdateState.Failed(r, UpdateFailure.BAD_URL)
            else -> UpdateState.Downloading(r, 0)
        }
    }

    /** Bytes so far of [total] (≤ 0 when unknown). Only whole-percent changes produce a new state. */
    fun progress(state: UpdateState, read: Long, total: Long): UpdateState {
        if (state !is UpdateState.Downloading) return state
        val pct = if (total > 0) (read * 100 / total).toInt().coerceIn(0, 100) else null
        return if (pct == state.percent) state else state.copy(percent = pct)
    }

    /**
     * The download finished at [file] with [actualSha256]. Installs only a file whose digest
     * matches the one GitHub published; a release without a digest is refused too (the browser
     * link stays as the fallback).
     */
    fun downloaded(state: UpdateState, file: String, actualSha256: String): UpdateState {
        if (state !is UpdateState.Downloading) return state
        val expected = state.release.apkSha256
        return if (expected != null && expected.equals(actualSha256, ignoreCase = true)) {
            UpdateState.Ready(state.release, file)
        } else {
            UpdateState.Failed(state.release, UpdateFailure.CHECKSUM)
        }
    }

    fun failed(state: UpdateState, reason: UpdateFailure): UpdateState =
        if (state is UpdateState.WithRelease) UpdateState.Failed(state.release, reason) else state

    /**
     * Android's installer answered (`EXTRA_RETURN_RESULT`). A success replaces the app, so this
     * process rarely sees it. Cancel keeps Ready, so the banner offers Install again.
     */
    fun installResult(state: UpdateState, resultCode: Int, installCode: Int?): UpdateState {
        if (state !is UpdateState.Ready) return state
        return when (resultCode) {
            RESULT_OK, RESULT_CANCELED -> state
            else -> UpdateState.Failed(
                state.release,
                if (installCode == INSTALL_FAILED_UPDATE_INCOMPATIBLE) UpdateFailure.DIFFERENT_BUILD else UpdateFailure.INSTALL,
            )
        }
    }
}

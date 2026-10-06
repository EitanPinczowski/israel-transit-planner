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
    /**
     * Android hasn't let this app install apps. [file] is the verified APK, if already downloaded.
     * [shown]: the settings screen was opened for this tap, so it is not opened again by itself.
     */
    data class NeedsPermission(override val release: LatestRelease, val file: String? = null, val shown: Boolean = false) : WithRelease
    /** Verified APK at [file]. [shown]: Android's prompt was opened for this tap (once per tap). */
    data class Ready(override val release: LatestRelease, val file: String, val shown: Boolean = false) : WithRelease
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
     * A tap on Update, Allow, Install or Try again. Downloading ignores it. Without the install
     * permission → NeedsPermission, keeping a file already verified; with it → Ready again for
     * that file, or a fresh download from an allowed URL. Either opens its screen once more.
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

    /**
     * Back from Android's "Install unknown apps" screen. Allowed → continue as a tap would;
     * not allowed → stay, without opening the screen again by itself.
     */
    fun onPermissionReturn(state: UpdateState, canInstall: Boolean): UpdateState =
        if (state is UpdateState.NeedsPermission && !canInstall) state.copy(shown = true) else onUpdate(state, canInstall)

    /** The app just opened the screen this state asks for (the prompt or the permission screen). */
    fun shown(state: UpdateState): UpdateState = when (state) {
        is UpdateState.Ready -> state.copy(shown = true)
        is UpdateState.NeedsPermission -> state.copy(shown = true)
        else -> state
    }

    /** Should the app open a screen by itself now? Once per tap, never again on its own. */
    fun needsScreen(state: UpdateState): Boolean =
        (state is UpdateState.Ready && !state.shown) || (state is UpdateState.NeedsPermission && !state.shown)

    /**
     * The banner's X. A running download stops (back to Available, the file is dropped); any
     * other state is kept, so Settings can still pick it up.
     */
    fun dismissed(state: UpdateState): UpdateState =
        if (state is UpdateState.Downloading) UpdateState.Available(state.release) else state

    /** "Check now" would throw away a download, a verified file or a pending permission: skip it. */
    fun canCheck(state: UpdateState): Boolean =
        state !is UpdateState.Checking && state !is UpdateState.Downloading &&
            state !is UpdateState.Ready && state !is UpdateState.NeedsPermission

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

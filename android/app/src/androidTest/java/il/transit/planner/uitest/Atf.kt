package il.transit.planner.uitest

import android.os.Build
import android.view.View
import android.view.WindowManager
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResult.AccessibilityCheckResultType
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityViewCheckResult
import com.google.android.apps.common.testing.accessibility.framework.integrations.espresso.AccessibilityValidator

/**
 * Google's Accessibility Test Framework over the windows a user can reach now: the main window, or
 * only the dialogs and popups on top of it when one is open (Phase 11 E5; before, only the main
 * window was checked, so an open dialog was never looked at). Touch size is left to R4: ATF can't see
 * Compose growing a small tap area to 48 dp.
 *
 * Used by the layout audit (R5, with a screenshot, so text contrast is checked too: warnings), by
 * [A11yHook] after every journey step (errors only, no screenshot), and by `A11yJourneysTest`.
 */
object Atf {
    /** One ATF result, with where it is. */
    data class Finding(val error: Boolean, val check: String, val message: String, val where: String) {
        override fun toString() = "${if (error) "ERROR" else "WARNING"} $check at $where: $message"
    }

    /** Layout audit rule R5: errors and warnings as violations; if ATF itself fails, it says so. */
    fun check(d: AppDriver): List<Violation> {
        val out = mutableListOf<Violation>()
        d.inst.runOnMainSync {
            try {
                for (f in run(d, screenshots = true)) {
                    out += Violation("R5", f.check, "${if (f.error) "ERROR" else "WARNING"}: ${f.message} [${f.where}]".replace("\n", " ").take(300))
                }
            } catch (e: Throwable) {
                out += Violation("R5", "atf", "not run: ${e::class.simpleName}: ${e.message?.take(160)}")
            }
        }
        return out
    }

    /** ATF over the reachable windows. Main thread only. */
    fun run(d: AppDriver, screenshots: Boolean): List<Finding> {
        val out = mutableListOf<Finding>()
        for (root in reachableRoots(d)) {
            val validator = AccessibilityValidator()
                .setRunChecksFromRootView(true)
                .setCaptureScreenshots(screenshots)
                .setThrowExceptionFor(null)
            for (r in validator.checkAndReturnResults(root)) {
                if (r.type != AccessibilityCheckResultType.ERROR && r.type != AccessibilityCheckResultType.WARNING) continue
                if (r.sourceCheckClass.simpleName == "TouchTargetSizeCheck") continue
                out += Finding(r.type == AccessibilityCheckResultType.ERROR, r.sourceCheckClass.simpleName, r.message.toString().replace("\n", " "), where(r))
            }
        }
        return out
    }

    /** What the finding is about: its class, bounds and words, so a CI log alone can find it. */
    private fun where(r: AccessibilityViewCheckResult): String {
        val e = r.element
        if (e != null) {
            val words = listOfNotNull(e.text?.toString(), e.contentDescription?.toString()).filter { it.isNotBlank() }.joinToString(" / ").take(60)
            return "${e.className} ${e.boundsInScreen} \"$words\""
        }
        val v = r.view ?: return "?"
        return "${v.javaClass.simpleName} ${v.width}x${v.height} \"${v.contentDescription ?: ""}\""
    }

    /**
     * The roots of the app's windows a user can reach: the dialogs and popups on top when there are
     * any, else the Activity's own window. A toast is never one. Main thread only.
     */
    fun reachableRoots(d: AppDriver): List<View> {
        val main = d.activity.window.decorView
        val others = windowRoots(main).filter { v ->
            v !== main && v.isAttachedToWindow && v.isShown && v.width > 0 && v.height > 0 && isAppWindow(v)
        }
        return others.ifEmpty { listOf(main) }
    }

    private fun isAppWindow(v: View): Boolean {
        val type = (v.layoutParams as? WindowManager.LayoutParams)?.type ?: return false
        return type in WindowManager.LayoutParams.FIRST_APPLICATION_WINDOW..WindowManager.LayoutParams.LAST_APPLICATION_WINDOW ||
            type in WindowManager.LayoutParams.FIRST_SUB_WINDOW..WindowManager.LayoutParams.LAST_SUB_WINDOW
    }

    /** Every window root of this process: public on API 29+, the window manager's own list before. */
    @Suppress("UNCHECKED_CAST")
    private fun windowRoots(main: View): List<View> {
        if (Build.VERSION.SDK_INT >= 29) return android.view.inspector.WindowInspector.getGlobalWindowViews()
        return runCatching {
            val global = Class.forName("android.view.WindowManagerGlobal")
            val instance = global.getMethod("getInstance").invoke(null)
            val field = global.getDeclaredField("mViews").apply { isAccessible = true }
            (field.get(instance) as List<View>).toList()
        }.getOrElse { listOf(main) }
    }
}

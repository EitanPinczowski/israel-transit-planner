package il.transit.planner.uitest

import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResult.AccessibilityCheckResultType
import com.google.android.apps.common.testing.accessibility.framework.integrations.espresso.AccessibilityValidator

/**
 * Rule R5: Google's Accessibility Test Framework over the main window (labels, touch targets,
 * text contrast from a screenshot, …). Errors and warnings become violations; if the
 * framework itself fails on this device the run says so instead of passing silently.
 */
object Atf {
    fun check(d: AppDriver): List<Violation> {
        val out = mutableListOf<Violation>()
        d.inst.runOnMainSync {
            try {
                val validator = AccessibilityValidator()
                    .setRunChecksFromRootView(true)
                    .setCaptureScreenshots(true)
                    .setThrowExceptionFor(null)
                val results = validator.checkAndReturnResults(d.activity.window.decorView)
                for (r in results) {
                    if (r.type != AccessibilityCheckResultType.ERROR && r.type != AccessibilityCheckResultType.WARNING) continue
                    out += Violation("R5", r.sourceCheckClass.simpleName, "${r.type}: ${r.message}".replace("\n", " ").take(240))
                }
            } catch (e: Throwable) {
                out += Violation("R5", "atf", "not run: ${e::class.simpleName}: ${e.message?.take(160)}")
            }
        }
        return out
    }
}

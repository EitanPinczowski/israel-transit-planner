package il.transit.planner.uitest

import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inspector.WindowInspector
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResult.AccessibilityCheckResultType
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityViewCheckResult
import com.google.android.apps.common.testing.accessibility.framework.integrations.espresso.AccessibilityValidator
import il.transit.planner.ui.screens.DayTags

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

    /**
     * Errors that are not ours to fix, reported as warnings with the reason (Phase 11 E5: "listed in the
     * PR with the reason"): the ATF check, the test tag of the node they sit in, why.
     */
    private val KNOWN = listOf(
        Triple(
            "EditableContentDescCheck",
            DayTags.TIME_INPUT,
            "Material 3's TimeInput gives its own hour and minute fields a contentDescription (\"for hour\"); the app can't reach them",
        ),
    )

    /** ATF over the reachable windows. Main thread only. */
    fun run(d: AppDriver, screenshots: Boolean): List<Finding> {
        val out = mutableListOf<Finding>()
        val roots = reachableRoots(d)
        val known = KNOWN.flatMap { (check, tag, why) -> taggedOnScreen(roots, tag).map { Triple(check, it, why) } }
        for (root in roots) {
            val validator = AccessibilityValidator()
                .setRunChecksFromRootView(true)
                .setCaptureScreenshots(screenshots)
                .setThrowExceptionFor(null)
            for (r in validator.checkAndReturnResults(root)) {
                if (r.type != AccessibilityCheckResultType.ERROR && r.type != AccessibilityCheckResultType.WARNING) continue
                if (r.sourceCheckClass.simpleName == "TouchTargetSizeCheck") continue
                val check = r.sourceCheckClass.simpleName
                val message = r.message.toString().replace("\n", " ")
                val excused = known.firstOrNull { (c, rect, _) -> c == check && r.element?.boundsInScreen?.let { b -> inside(b.left, b.top, b.right, b.bottom, rect) } == true }
                out += if (excused != null) {
                    Finding(false, check, "known, not blocking: ${excused.third}. $message", where(r, roots))
                } else {
                    Finding(r.type == AccessibilityCheckResultType.ERROR, check, message, where(r, roots))
                }
            }
        }
        return out
    }

    private fun inside(l: Int, t: Int, r: Int, b: Int, box: android.graphics.Rect) = l >= box.left - 2 && t >= box.top - 2 && r <= box.right + 2 && b <= box.bottom + 2

    /**
     * The Compose node(s) the size of [b], for a finding ATF can only place by its bounds (matched by size:
     * a window's offset on screen differs between phones). Main thread only.
     */
    private fun composeAt(roots: List<View>, b: android.graphics.Rect): String {
        val found = mutableListOf<String>()
        fun walk(n: SemanticsNode) {
            val near = kotlin.math.abs(n.size.width - b.width()) <= 2 && kotlin.math.abs(n.size.height - b.height()) <= 2
            if (near) found += "#${n.id} at ${n.positionOnScreen} ${n.config}".replace("\n", " ").take(260)
            n.children.forEach(::walk)
        }
        roots.forEach { root -> composeRoots(root).forEach { walk(it.semanticsOwner.unmergedRootSemanticsNode) } }
        return if (found.isEmpty()) "" else " compose: " + found.take(3).joinToString(" | ")
    }

    /** Screen bounds of the Compose nodes tagged [tag] in [roots]. Main thread only. */
    private fun taggedOnScreen(roots: List<View>, tag: String): List<android.graphics.Rect> {
        val out = mutableListOf<android.graphics.Rect>()
        fun walk(n: SemanticsNode) {
            if (n.config.getOrNull(SemanticsProperties.TestTag) == tag) {
                val p = n.positionOnScreen
                out += android.graphics.Rect(p.x.toInt(), p.y.toInt(), p.x.toInt() + n.size.width, p.y.toInt() + n.size.height)
            }
            n.children.forEach(::walk)
        }
        roots.forEach { root -> composeRoots(root).forEach { walk(it.semanticsOwner.unmergedRootSemanticsNode) } }
        return out
    }

    /** The Compose roots under [v] (an AndroidComposeView is one). */
    fun composeRoots(v: View): List<RootForTest> {
        if (v is RootForTest) return listOf(v)
        if (v !is ViewGroup) return emptyList()
        return (0 until v.childCount).flatMap { composeRoots(v.getChildAt(it)) }
    }

    /** What the finding is about: its class, bounds and words, so a CI log alone can find it. */
    private fun where(r: AccessibilityViewCheckResult, roots: List<View>): String {
        val e = r.element
        if (e != null) {
            val texts = listOfNotNull(e.text?.toString(), e.contentDescription?.toString())
            val words = texts.filter { it.isNotBlank() }.joinToString(" / ").take(60)
            val b = e.boundsInScreen
            val flags = "clickable=${e.isClickable} focusable=${e.isFocusable} srFocusable=${e.isScreenReaderFocusable} important=${e.isImportantForAccessibility}"
            return "${e.className} $b \"$words\" $flags" + composeAt(roots, android.graphics.Rect(b.left, b.top, b.right, b.bottom))
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
        if (Build.VERSION.SDK_INT >= 29) return WindowInspector.getGlobalWindowViews()
        return runCatching {
            val global = Class.forName("android.view.WindowManagerGlobal")
            val instance = global.getMethod("getInstance").invoke(null)
            val field = global.getDeclaredField("mViews").apply { isAccessible = true }
            (field.get(instance) as List<View>).toList()
        }.getOrElse { listOf(main) }
    }
}

package il.transit.planner.uitest

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.IdlingResource

/**
 * Phase 11 E5 (owner, 2026-10-13): Google's Accessibility Test Framework after every step of every
 * journey, on every API level, errors blocking. A journey taps through Compose directly, so there is
 * no single "step" call to hook; instead [AppDriver] registers this as a Compose idling resource,
 * which the test framework polls on the main thread whenever it syncs with the app: before every
 * tap, text input and lookup. Whenever the screen's words and layout differ from the last screen
 * checked, ATF runs over the windows a user can reach ([Atf.reachableRoots]; no screenshot, so no
 * contrast: that is the layout audit's R5).
 *
 * An error counts only if the same screen still shows it at the next poll (or at the end): a frame
 * in the middle of an animation is not a screen a user rests on. [AppDriver.close] fails the journey
 * with every error found, each with the screen it was on.
 */
class A11yHook(private val d: AppDriver) : IdlingResource {
    /** Screens checked so far; `A11yJourneysTest` asserts it is not 0, so the hook can't go quiet. */
    var checks = 0
        private set
    val errors = linkedMapOf<String, String>()
    private var lastChecked: String? = null
    private var pending: Pair<String, List<Atf.Finding>>? = null

    override val isIdleNow: Boolean
        get() {
            if (Looper.myLooper() == Looper.getMainLooper()) runCatching { poll() }.onFailure { errors["hook"] = "the a11y check failed: $it" }
            return true // never makes a test wait
        }

    /** Checks the screen if it changed since the last check. Main thread only. */
    fun poll(final: Boolean = false) {
        val sig = signature() ?: return
        pending?.let { (s, found) ->
            pending = null
            if (s == sig || final) confirm(s, if (s == sig) Atf.run(d, screenshots = false).filter { it.error } else found)
        }
        if (sig == lastChecked) return
        lastChecked = sig
        checks++
        val found = Atf.run(d, screenshots = false).filter { it.error }
        if (found.isEmpty()) return
        if (final) confirm(sig, found) else pending = sig to found
    }

    private fun confirm(sig: String, found: List<Atf.Finding>) {
        for (f in found) {
            val key = "${f.check} ${f.where}"
            if (key !in errors) errors[key] = "$f (screen: ${sig.take(160)})"
        }
    }

    /**
     * The reachable windows' words and node bounds (Compose semantics, read on the main thread), or
     * null with no Activity. Bounds are rounded to 8 px so a sub-pixel settle is not a new screen.
     */
    private fun signature(): String? {
        val roots = runCatching { Atf.reachableRoots(d) }.getOrNull() ?: return null
        val sb = StringBuilder()
        for (r in roots) composeRoots(r).forEach { walk(it.semanticsOwner.rootSemanticsNode, sb) }
        return sb.toString()
    }

    private fun walk(n: SemanticsNode, sb: StringBuilder) {
        val text = n.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
        val desc = n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")
        val b = n.boundsInWindow
        if (text != null || desc != null) sb.append("$text|$desc@${b.left.toInt() / 8},${b.top.toInt() / 8};")
        n.children.forEach { walk(it, sb) }
    }

    private fun composeRoots(v: View): List<RootForTest> {
        if (v is RootForTest) return listOf(v)
        if (v !is ViewGroup) return emptyList()
        return (0 until v.childCount).flatMap { composeRoots(v.getChildAt(it)) }
    }
}

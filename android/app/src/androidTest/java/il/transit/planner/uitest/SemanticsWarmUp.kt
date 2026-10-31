@file:OptIn(androidx.compose.ui.InternalComposeUiApi::class)

package il.transit.planner.uitest

import android.os.Build
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.IdlingResource
import androidx.compose.ui.test.junit4.ComposeTestRule
import java.util.Collections
import java.util.WeakHashMap

/**
 * Computes every Compose root's semantics on the MAIN thread each time a test waits for idle, so the
 * test thread only ever reads cached semantics (Phase 11 F2).
 *
 * Root cause of "Detected multithreaded access to SnapshotStateObserver" (Settings dialog, ~1 run in 12):
 * Compose 1.7 computes a node's semantics lazily (`LayoutNode.getCollapsedSemantics`), inside the owner's
 * SnapshotStateObserver, on whichever thread reads them first. After every fetch the test rule prints the
 * node's subtree (`fetchOneOrThrow` → `printToString`) on the `Instr:` thread, which computes the semantics
 * of rows a scroll has just composed. On the main thread, the accessibility / content-capture checkers
 * (every 100 ms after a change, outside Compose's idle tracking) compute them too: two threads in one
 * observer. Current Compose (androidx-main) computes semantics on the main thread when they change; moving
 * to Compose 1.9 costs a Paparazzi upgrade, every golden and +2.8 % APK (tried in this package), so the
 * tests do the same here:
 * Espresso checks idling resources on the main thread, so [isIdleNow] fills the cache there, after the UI
 * settled and before the test thread reads.
 */
internal class SemanticsWarmUp private constructor() : IdlingResource {
    private var told = false

    override val isIdleNow: Boolean
        get() {
            val onMain = Looper.myLooper() == Looper.getMainLooper()
            if (!told) {
                told = true
                Log.i(TAG, "idling check on the main thread: $onMain") // the warm-up only helps there
            }
            if (!onMain) return true
            runCatching { composeRoots().forEach { warm(it.semanticsOwner.unmergedRootSemanticsNode) } }
                .onFailure { Log.w(TAG, "semantics warm-up: $it") }
            return true
        }

    /** Reading a node's children builds (and caches) the semantics of every layout node below it. */
    private fun warm(node: SemanticsNode) {
        for (child in node.children) warm(child)
    }

    /**
     * Every Compose root in this process's windows (Activity, dialogs, popups). Found from the windows, not
     * through `ViewRootForTest.onViewCreatedCallback`: the test rule checks that callback is its own.
     */
    private fun composeRoots(): List<ViewRootForTest> {
        val out = ArrayList<ViewRootForTest>()
        fun walk(v: View) {
            if (v is ViewRootForTest && v.isAttachedToWindow) out += v
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        windowViews().forEach(::walk)
        return out
    }

    @Suppress("UNCHECKED_CAST")
    private fun windowViews(): List<View> =
        if (Build.VERSION.SDK_INT >= 29) {
            WindowInspector.getGlobalWindowViews()
        } else {
            val global = Class.forName("android.view.WindowManagerGlobal")
            val instance = global.getMethod("getInstance").invoke(null)
            val field = global.getDeclaredField("mViews").apply { isAccessible = true }
            synchronized(instance!!) { ArrayList(field.get(instance) as List<View>) }
        }

    companion object {
        private const val TAG = "SemanticsWarmUp"
        private val installed: MutableSet<ComposeTestRule> = Collections.newSetFromMap(WeakHashMap())

        /** Once per test rule. */
        fun install(compose: ComposeTestRule) {
            if (installed.add(compose)) compose.registerIdlingResource(SemanticsWarmUp())
        }
    }
}

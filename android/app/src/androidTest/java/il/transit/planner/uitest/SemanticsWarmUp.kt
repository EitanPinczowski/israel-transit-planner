@file:OptIn(androidx.compose.ui.InternalComposeUiApi::class)

package il.transit.planner.uitest

import android.os.Looper
import android.util.Log
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
    private val roots: MutableSet<ViewRootForTest> = Collections.newSetFromMap(WeakHashMap())
    private var told = false

    override val isIdleNow: Boolean
        get() {
            val onMain = Looper.myLooper() == Looper.getMainLooper()
            if (!told) {
                told = true
                Log.i(TAG, "idling check on the main thread: $onMain") // the warm-up only helps there
            }
            if (!onMain) return true
            val live = synchronized(roots) { roots.toList() }
            for (root in live) {
                if (!root.view.isAttachedToWindow) continue
                runCatching { warm(root.semanticsOwner.unmergedRootSemanticsNode) }
                    .onFailure { Log.w(TAG, "semantics warm-up: $it") }
            }
            return true
        }

    /** Reading a node's children builds (and caches) the semantics of every layout node below it. */
    private fun warm(node: SemanticsNode) {
        for (child in node.children) warm(child)
    }

    companion object {
        private const val TAG = "SemanticsWarmUp"
        private val installed: MutableSet<ComposeTestRule> = Collections.newSetFromMap(WeakHashMap())

        /**
         * Once per test rule, after the rule set itself up: the roots the app creates from now on (Activity,
         * dialogs, popups) are kept, the rule's own root registry still hears of them.
         */
        fun install(compose: ComposeTestRule) {
            if (!installed.add(compose)) return
            val warmUp = SemanticsWarmUp()
            val before = ViewRootForTest.onViewCreatedCallback
            ViewRootForTest.onViewCreatedCallback = { root ->
                before?.invoke(root)
                synchronized(warmUp.roots) { warmUp.roots += root }
            }
            compose.registerIdlingResource(warmUp)
        }
    }
}

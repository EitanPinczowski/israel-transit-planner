package il.transit.planner

import leakcanary.LeakCanary
import shark.AndroidReferenceMatchers
import shark.LibraryLeakReferenceMatcher
import shark.ReferencePattern

/**
 * LeakCanary's reference matchers for the uitest build (Phase 11 T8): Android's own defaults plus two ANDROID FRAMEWORK paths
 * that hold a closed Activity or service for a while and that no app code can release. They are reported as library leaks (still
 * in the trace, never a failure); every other path to a retained Activity, ViewModel, MapView or service fails the journey.
 *  - `InteractionJankMonitor$Configuration.mContext`: Android 15's jank tracker keeps the window's context in a pending message
 *    (seen on API 35 with every closed MainActivity, a `com.android.internal.jank` runnable on the main looper);
 *  - `TextToSpeech$Connection$1.this$1`: the binder callback of a TextToSpeech that `RideService` has already `shutdown()`; the
 *    system TTS service drops it when it likes (a JNI global), holding the service until then.
 */
object LeakConfig {
    fun install() {
        LeakCanary.config = LeakCanary.config.copy(
            referenceMatchers = AndroidReferenceMatchers.appDefaults + listOf(
                LibraryLeakReferenceMatcher(
                    pattern = ReferencePattern.InstanceFieldPattern("com.android.internal.jank.InteractionJankMonitor\$Configuration", "mContext"),
                    description = "Android 15 InteractionJankMonitor keeps the window context of a closed Activity in a pending message.",
                ),
                LibraryLeakReferenceMatcher(
                    pattern = ReferencePattern.InstanceFieldPattern("android.speech.tts.TextToSpeech\$Connection\$1", "this\$1"),
                    description = "The binder callback of a shut-down TextToSpeech stays registered in the system TTS service for a while.",
                ),
            ),
        )
    }
}

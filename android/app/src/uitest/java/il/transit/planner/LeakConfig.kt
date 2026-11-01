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
 *  - `TextToSpeech$Connection$1.this$1` / `TextToSpeech$SystemConnection$1.this$1`: the binder callback of a TextToSpeech that
 *    `RideService` has already `shutdown()`; the system TTS service drops it when it likes (a JNI global), holding the service until then;
 *  - `LocationManager$ListenerTransport.mListener` / `.this$0` / `.mListenerHandler` (Android 8 to 10): the transport of a listener `RideService.onDestroy`
 *    has already removed (`removeUpdates`) stays in the framework (a JNI global) until its binder is collected.
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
                    pattern = ReferencePattern.InstanceFieldPattern("android.speech.tts.TextToSpeech\$SystemConnection\$1", "this\$1"),
                    description = "The binder callback of a shut-down TextToSpeech (system connection) stays registered in the TTS service.",
                ),
                LibraryLeakReferenceMatcher(
                    pattern = ReferencePattern.InstanceFieldPattern("android.location.LocationManager\$ListenerTransport", "this\$0"),
                    description = "The transport of a removed location listener keeps its LocationManager (and that its service) until its binder is collected.",
                ),
                LibraryLeakReferenceMatcher(
                    pattern = ReferencePattern.InstanceFieldPattern("android.location.LocationManager\$ListenerTransport", "mListenerHandler"),
                    description = "The handler of a removed location listener's transport keeps its LocationManager until its binder is collected.",
                ),
                LibraryLeakReferenceMatcher(
                    pattern = ReferencePattern.InstanceFieldPattern("android.location.LocationManager\$ListenerTransport", "mListener"),
                    description = "Android 8 keeps the transport of a removed location listener until its binder is collected.",
                ),
                LibraryLeakReferenceMatcher(
                    pattern = ReferencePattern.InstanceFieldPattern("android.speech.tts.TextToSpeech\$Connection\$1", "this\$1"),
                    description = "The binder callback of a shut-down TextToSpeech stays registered in the system TTS service for a while.",
                ),
            ),
        )
    }
}

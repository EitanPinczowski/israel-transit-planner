package il.transit.planner.data

import il.transit.core.api.UsageLog
import il.transit.core.api.UsageMeter
import il.transit.core.api.UsageOutcome
import il.transit.core.api.UsageService
import il.transit.core.api.UsageSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The data card's counts on disk (Phase 11 E4): a small JSON file of [UsageLog] days, only counts
 * and times. Read once, written at most every [SAVE_DELAY_MS] after a change. Never uploaded.
 */
class UsageStore(private val file: File, private val clock: Clock = Clock.systemUTC()) : UsageMeter {
    private val log = UsageLog(clock, UsageLog.decode(runCatching { file.takeIf { it.exists() }?.readText() }.getOrNull()))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pending = AtomicBoolean(false)
    private val _changes = MutableStateFlow(0L)

    /** Bumps on every change, so an open card refreshes. */
    val changes: StateFlow<Long> = _changes.asStateFlow()

    init {
        log.onChange = {
            _changes.value = _changes.value + 1
            if (pending.compareAndSet(false, true)) {
                scope.launch {
                    delay(SAVE_DELAY_MS)
                    pending.set(false)
                    runCatching { file.writeText(UsageLog.encode(log.days())) } // a full disk only loses counts
                }
            }
        }
    }

    override fun count(service: UsageService, kind: String, outcome: UsageOutcome) = log.count(service, kind, outcome)

    fun nightRefresh(at: Instant, requests: Int) = log.nightRefresh(at, requests)

    fun rideGps(duration: Duration) = log.rideGps(duration)

    fun summary(): UsageSummary = log.summary()

    companion object {
        const val FILE = "usage.json"
        private const val SAVE_DELAY_MS = 2_000L
    }
}

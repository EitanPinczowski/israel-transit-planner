package il.transit.planner.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import il.transit.core.present.hhmm
import il.transit.core.remind.Maker
import il.transit.core.remind.ProblemKind
import il.transit.core.remind.ReminderHealth
import il.transit.core.remind.ReminderProblem
import il.transit.core.remind.Severity
import il.transit.core.remind.TestResult
import il.transit.core.user.UserSettings
import il.transit.planner.R
import il.transit.planner.remind.ReminderCheck
import il.transit.planner.ui.MainActions
import il.transit.planner.ui.UiState
import kotlinx.coroutines.delay
import java.time.Instant
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading

/** Phase 11 E1: the reminder check is closed, offered once as a hint, or open. */
enum class ReminderCheckView { NONE, HINT, OPEN }

/** MainScreen's one hook: the one-time hint after the first reminder, or the check itself. */
@Composable
internal fun ReminderCheckHost(state: UiState, vm: MainActions) {
    when (state.reminderCheck) {
        ReminderCheckView.NONE -> Unit
        ReminderCheckView.HINT -> ReminderCheckHint(
            onCheck = { vm.reminderCheck(ReminderCheckView.OPEN) },
            onLater = { vm.reminderCheck(ReminderCheckView.NONE) },
        )
        ReminderCheckView.OPEN -> ReminderCheckDialog(state.settings) { vm.reminderCheck(ReminderCheckView.NONE) }
    }
}

/** "Check that reminders will ring on this phone": Check now / Not now. */
@Composable
internal fun ReminderCheckHint(onCheck: () -> Unit, onLater: () -> Unit) {
    AlertDialog(
        onDismissRequest = onLater,
        text = { Text(stringResource(R.string.check_hint)) },
        confirmButton = { TextButton(onClick = onCheck, modifier = Modifier.testTag("check_hint_ok")) { Text(stringResource(R.string.check_hint_ok)) } },
        dismissButton = { TextButton(onClick = onLater) { Text(stringResource(R.string.check_hint_later)) } },
    )
}

/** Settings → "Check reminders". */
@Composable
internal fun ReminderCheckRow(onOpen: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.check_reminders_row), style = MaterialTheme.typography.labelLarge, modifier = Modifier.semantics { heading() })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.check_reminders_help), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onOpen, modifier = Modifier.testTag("check_reminders")) { Text(stringResource(R.string.check_reminders_open)) }
        }
    }
}

/**
 * The check, read from the phone again every few seconds while it is open (the user may have
 * just come back from fixing something), with the test reminder's state.
 */
@Composable
internal fun ReminderCheckDialog(settings: UserSettings, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(3_000)
            tick++
        }
    }
    val phone = remember(tick, settings) { ReminderCheck.state(context, settings) }
    val problems = remember(phone) { ReminderHealth.check(phone) }
    val test = remember(tick) { ReminderCheck.result(context, Instant.now()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
        title = { Text(stringResource(R.string.check_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ReminderCheckContent(
                    problems = problems,
                    maker = ReminderHealth.maker(phone),
                    test = test,
                    onFix = { ReminderCheck.open(context, it) },
                    onTest = {
                        ReminderCheck.startTest(context)
                        tick++
                    },
                )
            }
        },
    )
}

/** The dialog's body on its own, for screenshot tests: no phone reads, no clock. */
@Composable
internal fun ReminderCheckContent(
    problems: List<ReminderProblem>,
    maker: Maker?,
    test: TestResult,
    onFix: (ReminderProblem) -> Unit,
    onTest: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (ReminderHealth.allSet(problems)) {
            Text(stringResource(R.string.check_all_set), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }
        problems.forEach { p -> ProblemCard(p) { onFix(p) } }
        maker?.let { Text(stringResource(R.string.check_maker, it.label), style = MaterialTheme.typography.bodySmall) }
        val canTest = ReminderHealth.canTest(problems)
        Button(onClick = onTest, enabled = canTest && test !is TestResult.Waiting, modifier = Modifier.fillMaxWidth().testTag("check_test")) {
            Text(stringResource(R.string.check_test))
        }
        if (!canTest) {
            Text(stringResource(R.string.check_test_cant), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        TestLine(test, problems)
    }
}

@Composable
private fun TestLine(test: TestResult, problems: List<ReminderProblem>) {
    when (test) {
        TestResult.None -> Unit
        is TestResult.Waiting -> Text(stringResource(R.string.check_test_waiting, hhmm(test.dueAt)), style = MaterialTheme.typography.bodySmall)
        is TestResult.Rang -> Text(
            stringResource(R.string.check_test_rang, hhmm(test.at)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.testTag("check_test_rang"),
        )
        is TestResult.Missed -> Column {
            Text(stringResource(R.string.check_test_missed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            // The list is worst first, so its first entry is the likeliest reason.
            val why = if (problems.isEmpty()) R.string.check_test_missed_none else R.string.check_test_missed_first
            Text(stringResource(why), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ProblemCard(p: ReminderProblem, onFix: () -> Unit) {
    val (container, onContainer) = when (p.severity) {
        Severity.WONT_RING -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        Severity.MAY_FAIL -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        Severity.OTHER_ALERT, Severity.TIP -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = container, contentColor = onContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // The severity is written out, so colour is never the only signal.
            Text(stringResource(severityText(p.severity)), style = MaterialTheme.typography.labelMedium)
            Text(stringResource(problemText(p.kind)), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onFix) { Text(stringResource(R.string.check_open)) }
        }
    }
}

private fun severityText(s: Severity) = when (s) {
    Severity.WONT_RING -> R.string.check_sev_wont_ring
    Severity.MAY_FAIL -> R.string.check_sev_may_fail
    Severity.OTHER_ALERT -> R.string.check_sev_other_alert
    Severity.TIP -> R.string.check_sev_tip
}

/** Each kind's own string, `check_<kind>`. */
private fun problemText(k: ProblemKind) = when (k) {
    ProblemKind.NOTIFICATIONS_PERMISSION -> R.string.check_notifications_permission
    ProblemKind.NOTIFICATIONS_OFF -> R.string.check_notifications_off
    ProblemKind.REMINDER_CHANNEL_OFF -> R.string.check_reminder_channel_off
    ProblemKind.BACKGROUND_RESTRICTED -> R.string.check_background_restricted
    ProblemKind.REMINDER_CHANNEL_QUIET -> R.string.check_reminder_channel_quiet
    ProblemKind.EXACT_ALARMS_OFF -> R.string.check_exact_alarms_off
    ProblemKind.STANDBY_RESTRICTED -> R.string.check_standby_restricted
    ProblemKind.BATTERY_OPTIMIZED -> R.string.check_battery_optimized
    ProblemKind.PASS_CHANNEL_OFF -> R.string.check_pass_channel_off
    ProblemKind.LAST_TRIP_CHANNEL_OFF -> R.string.check_last_trip_channel_off
    ProblemKind.LOCATION_OFF -> R.string.check_location_off
}

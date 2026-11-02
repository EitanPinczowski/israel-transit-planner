package il.transit.planner.labs

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import il.transit.core.card.CardFile
import il.transit.core.card.CardReport
import il.transit.core.card.FileOutcome
import il.transit.core.card.FileRead
import il.transit.core.card.RavKavReader
import il.transit.core.card.hexGroups
import il.transit.planner.R

/*
 * Phase 11 E6: the Rav-Kav read spike. This source set is compiled into DEBUG builds only;
 * release and uitest get an empty LabsRow (src/release, src/uitest).
 *
 * Privacy: the card's answers live only in this dialog's Compose state (plain `remember`, never
 * `rememberSaveable`, so not even the saved-instance bundle holds them). Nothing is logged,
 * written to a file or the crash log, or shared, and the text is not selectable (no copy).
 * The holder's file is never read (`CardFile.HOLDER_SFI`), and the tag's UID is never shown.
 */

/** Settings → "Labs (debug build)" → "Read my Rav-Kav". */
@Composable
internal fun LabsRow() {
    var open by remember { mutableStateOf(false) }
    Column {
        Text(stringResource(R.string.labs_title), style = MaterialTheme.typography.labelLarge, modifier = Modifier.semantics { heading() })
        TextButton(onClick = { open = true }) { Text(stringResource(R.string.labs_ravkav_open)) }
    }
    if (open) RavKavLabDialog(onDismiss = { open = false })
}

/** What the lab shows. */
internal sealed interface LabUi {
    data object NoNfc : LabUi
    data object NfcOff : LabUi
    data object Waiting : LabUi
    data object Reading : LabUi
    class Done(val report: CardReport) : LabUi
}

@Composable
private fun RavKavLabDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var ui by remember { mutableStateOf<LabUi>(LabUi.Waiting) }
    var attempt by remember { mutableIntStateOf(0) }
    DisposableEffect(attempt) {
        val activity = context.findActivity()
        val adapter = NfcAdapter.getDefaultAdapter(context)
        when {
            activity == null || adapter == null -> {
                ui = LabUi.NoNfc
                onDispose {}
            }
            !adapter.isEnabled -> {
                ui = LabUi.NfcOff
                onDispose {}
            }
            else -> {
                ui = LabUi.Waiting
                val main = Handler(Looper.getMainLooper())
                // Runs on an NFC binder thread, once per card tap.
                val callback = NfcAdapter.ReaderCallback { tag ->
                    main.post { ui = LabUi.Reading }
                    val report = read(tag)
                    main.post { ui = LabUi.Done(report) }
                }
                val flags = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
                adapter.enableReaderMode(activity, callback, flags, null)
                onDispose {
                    adapter.disableReaderMode(activity)
                    main.removeCallbacksAndMessages(null)
                }
            }
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) { RavKavLabContent(ui, onClose = onDismiss, onRetry = { attempt++ }) }
    }
}

/** One tap: plain ISO commands over IsoDep. Any failure is "Can't read this card", never a crash. */
private fun read(tag: Tag): CardReport {
    val iso = IsoDep.get(tag) ?: return CardReport.CantRead
    return try {
        iso.connect()
        iso.timeout = 2_000
        RavKavReader(iso::transceive).read()
    } catch (_: Exception) {
        CardReport.CantRead
    } finally {
        runCatching { iso.close() }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** The lab's body on its own, so a screenshot test can render each state. */
@Composable
internal fun RavKavLabContent(ui: LabUi, onClose: () -> Unit, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.labs_ravkav_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            TextButton(onClick = onClose) { Text(stringResource(R.string.labs_ravkav_close)) }
        }
        Text(stringResource(R.string.labs_ravkav_privacy), style = MaterialTheme.typography.bodySmall)
        when (ui) {
            LabUi.NoNfc -> Text(stringResource(R.string.labs_ravkav_no_nfc))
            LabUi.NfcOff -> {
                Text(stringResource(R.string.labs_ravkav_nfc_off))
                TextButton(onClick = onRetry) { Text(stringResource(R.string.labs_ravkav_retry)) }
            }
            LabUi.Waiting -> Text(stringResource(R.string.labs_ravkav_hold))
            LabUi.Reading -> Text(stringResource(R.string.labs_ravkav_reading))
            is LabUi.Done -> when (val r = ui.report) {
                CardReport.CantRead -> Text(stringResource(R.string.labs_ravkav_cant_read), color = MaterialTheme.colorScheme.error)
                is CardReport.Answered -> Answered(r)
            }
        }
    }
}

@Composable
private fun Answered(r: CardReport.Answered) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text(
                stringResource(R.string.labs_ravkav_summary, r.millis, r.selectStatus.toString(), "%02X".format(r.cla)),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(stringResource(R.string.labs_ravkav_again), style = MaterialTheme.typography.bodySmall)
        }
        item { Text(stringResource(R.string.labs_ravkav_skipped), style = MaterialTheme.typography.bodySmall) }
        items(r.files) { FileCard(it) }
    }
}

@Composable
private fun FileCard(f: FileRead) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.labs_file_header, stringResource(fileName(f.file)), "%02X".format(f.file.sfi)),
                style = MaterialTheme.typography.titleSmall,
            )
            val sw = f.status.toString()
            Text(
                when (f.outcome) {
                    FileOutcome.READ -> {
                        val n = f.records.size
                        pluralStringResource(R.plurals.labs_outcome_read, n, n, f.records.joinToString(", ") { it.size.toString() })
                    }
                    FileOutcome.EMPTY -> stringResource(R.string.labs_outcome_empty, sw)
                    FileOutcome.NOT_FOUND -> stringResource(R.string.labs_outcome_not_found, sw)
                    FileOutcome.NEEDS_KEYS -> stringResource(R.string.labs_outcome_needs_keys, sw)
                    FileOutcome.REFUSED -> stringResource(R.string.labs_outcome_refused, sw)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            // Raw bytes: on screen only, left to right in both languages, not selectable.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                f.records.forEachIndexed { i, bytes ->
                    Text(
                        "${i + 1}: ${bytes.hexGroups()}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

private fun fileName(f: CardFile): Int = when (f) {
    CardFile.CONTRACT_LIST -> R.string.labs_file_contract_list
    CardFile.CONTRACTS -> R.string.labs_file_contracts
    CardFile.COUNTERS -> R.string.labs_file_counters
    CardFile.EVENT_LOG -> R.string.labs_file_event_log
    CardFile.SPECIAL_EVENTS -> R.string.labs_file_special_events
}

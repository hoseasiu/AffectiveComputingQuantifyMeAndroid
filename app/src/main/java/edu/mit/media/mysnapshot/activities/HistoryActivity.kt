package edu.mit.media.mysnapshot.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import edu.mit.media.mysnapshot.data.ExperimentExporter
import edu.mit.media.mysnapshot.database.ExperimentEntity
import edu.mit.media.mysnapshot.engine.ExperimentType
import edu.mit.media.mysnapshot.ui.theme.DayCountGrey
import edu.mit.media.mysnapshot.ui.theme.FadeBlue
import edu.mit.media.mysnapshot.ui.theme.FadeRed
import edu.mit.media.mysnapshot.ui.theme.PageIndicatorDisabled
import edu.mit.media.mysnapshot.ui.theme.QuantifyMeTheme
import edu.mit.media.mysnapshot.viewmodel.HistoryEvent
import edu.mit.media.mysnapshot.viewmodel.HistoryViewModel
import java.io.File
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

@AndroidEntryPoint
class HistoryActivity : ComponentActivity() {

    private val viewModel: HistoryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            QuantifyMeTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()

                LaunchedEffect(Unit) {
                    viewModel.events.collect { event ->
                        when (event) {
                            is HistoryEvent.ExportReady -> shareExportJson(event.experiment, event.json)
                        }
                    }
                }

                HistoryScreen(
                    experiments = state.experiments,
                    isBusy = state.isCancelling,
                    onCancelConfirmed = viewModel::cancelExperiment,
                    onExport = viewModel::exportExperiment
                )
            }
        }
    }

    /**
     * User-initiated, one-shot export (AGENT_PLANS/MODERNIZE.md, "Optional: local data
     * export") -- writes the JSON to a cache-only file and hands it to the system share
     * sheet via a content:// URI. No network call, no shared/external storage; nothing
     * fires unless the user taps the export button and then picks a share target.
     */
    private fun shareExportJson(experiment: ExperimentEntity, json: String) {
        val exportsDir = File(cacheDir, "exports").apply { mkdirs() }
        val file = File(exportsDir, ExperimentExporter.suggestedFileName(experiment))
        file.writeText(json)
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(sendIntent, "Export Experiment Data"))
    }

    companion object {
        const val LOGTAG = "HistoryActivity"
    }
}

@Composable
private fun HistoryScreen(
    experiments: List<ExperimentEntity>?,
    isBusy: Boolean,
    onCancelConfirmed: (ExperimentEntity) -> Unit,
    onExport: (ExperimentEntity) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FadeBlue)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "My Experiments",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )
        }

        when {
            experiments == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            experiments.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No Experiments Found!",
                    modifier = Modifier.padding(30.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(experiments, key = { it.id }) { experiment ->
                    ExperimentCard(experiment, isBusy, onCancelConfirmed, onExport)
                }
            }
        }
    }
}

@Composable
private fun ExperimentCard(
    experiment: ExperimentEntity,
    isBusy: Boolean,
    onCancelConfirmed: (ExperimentEntity) -> Unit,
    onExport: (ExperimentEntity) -> Unit
) {
    val experimentType = remember(experiment.type) { ExperimentType.fromTypeKey(experiment.type) }
    var showCancelDialog by rememberSaveable { mutableStateOf(false) }

    val backgroundColor = when {
        experiment.isCancelled -> PageIndicatorDisabled
        experiment.isActive -> FadeRed
        else -> Color.White
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(10.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(modifier = Modifier.weight(1f)) {
                    Image(
                        painter = painterResource(experimentType.iconId),
                        contentDescription = null,
                        modifier = Modifier
                            .size(70.dp)
                            .padding(end = 20.dp)
                    )
                    Column {
                        Text(text = experimentType.name, fontSize = 18.sp)

                        val days = ChronoUnit.DAYS.between(
                            experiment.startTime.toLocalDate(),
                            (experiment.endTime ?: OffsetDateTime.now()).toLocalDate()
                        )
                        val dayCountText = "$days Days" +
                            (if (experiment.isCancelled) " (Canceled)" else "") +
                            (if (experiment.isActive) " (Active)" else "")
                        Text(
                            text = dayCountText,
                            fontSize = 16.sp,
                            color = DayCountGrey,
                            modifier = Modifier.padding(top = 7.dp)
                        )
                    }
                }
                IconButton(onClick = { onExport(experiment) }) {
                    Image(
                        painter = painterResource(android.R.drawable.ic_menu_share),
                        contentDescription = "Export this experiment's data"
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            when {
                experiment.isCancelled -> Text(
                    text = "Canceled",
                    fontSize = 22.sp,
                    fontStyle = FontStyle.Italic
                )
                experiment.isActive -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(onClick = { showCancelDialog = true }, enabled = !isBusy) {
                        Text("Quit")
                    }
                }
                else -> Column {
                    Row {
                        Text(text = "Your Result: ", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = experiment.resultValue?.let { experimentType.formatInstruction(it) } ?: "",
                            fontSize = 18.sp
                        )
                    }
                    Row(modifier = Modifier.padding(top = 10.dp)) {
                        Text(text = "Confidence: ", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = Math.round((experiment.resultConfidence ?: 0f) * 100f).toString() + "%",
                            fontSize = 18.sp
                        )
                    }
                }
            }
        }
    }

    if (showCancelDialog) {
        CancelExperimentDialog(
            onDismiss = { showCancelDialog = false },
            onConfirm = { showCancelDialog = false; onCancelConfirmed(experiment) }
        )
    }
}

@Composable
private fun CancelExperimentDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var reason by rememberSaveable { mutableStateOf("") }
    var showReasonError by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Really Stop Experiment?") },
        text = {
            Column {
                Text("All your progress will be lost forever! If you want to quit, please let us know why.")
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it; showReasonError = false },
                    label = { Text("Reason to Quit") },
                    singleLine = true,
                    isError = showReasonError
                )
                if (showReasonError) {
                    Text(
                        text = "Please enter a reason",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (reason.isNotEmpty()) onConfirm() else showReasonError = true
            }) {
                Text("Continue")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel!")
            }
        }
    )
}

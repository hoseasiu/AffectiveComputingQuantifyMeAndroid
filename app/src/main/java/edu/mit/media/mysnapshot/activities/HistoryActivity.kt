package edu.mit.media.mysnapshot.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import edu.mit.media.mysnapshot.data.ExperimentRepository
import edu.mit.media.mysnapshot.database.ExperimentEntity
import edu.mit.media.mysnapshot.engine.ExperimentType
import edu.mit.media.mysnapshot.ui.theme.DayCountGrey
import edu.mit.media.mysnapshot.ui.theme.FadeBlue
import edu.mit.media.mysnapshot.ui.theme.FadeRed
import edu.mit.media.mysnapshot.ui.theme.PageIndicatorDisabled
import edu.mit.media.mysnapshot.ui.theme.QuantifyMeTheme
import kotlinx.coroutines.launch
import org.joda.time.DateTime
import org.joda.time.Days
import org.joda.time.LocalDate
import javax.inject.Inject

@AndroidEntryPoint
class HistoryActivity : ComponentActivity() {

    @Inject
    lateinit var repository: ExperimentRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            QuantifyMeTheme {
                val experiments by repository.getAllExperiments().collectAsStateWithLifecycle(initialValue = null)
                var isCancelling by remember { mutableStateOf(false) }

                HistoryScreen(
                    experiments = experiments,
                    isBusy = isCancelling,
                    onCancelConfirmed = { experiment ->
                        isCancelling = true
                        lifecycleScope.launch {
                            repository.cancelExperiment(experiment.id)
                            isCancelling = false
                        }
                    }
                )
            }
        }
    }

    companion object {
        const val LOGTAG = "HistoryActivity"
    }
}

@Composable
private fun HistoryScreen(
    experiments: List<ExperimentEntity>?,
    isBusy: Boolean,
    onCancelConfirmed: (ExperimentEntity) -> Unit
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
                    ExperimentCard(experiment, isBusy, onCancelConfirmed)
                }
            }
        }
    }
}

@Composable
private fun ExperimentCard(
    experiment: ExperimentEntity,
    isBusy: Boolean,
    onCancelConfirmed: (ExperimentEntity) -> Unit
) {
    val experimentType = remember(experiment.type) { ExperimentType.fromTypeKey(experiment.type) }
    var showCancelDialog by remember { mutableStateOf(false) }

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
            Row {
                Image(
                    painter = painterResource(experimentType.iconId),
                    contentDescription = null,
                    modifier = Modifier
                        .size(70.dp)
                        .padding(end = 20.dp)
                )
                Column {
                    Text(text = experimentType.name, fontSize = 18.sp)

                    val days = Days.daysBetween(
                        LocalDate(experiment.startTime),
                        LocalDate(experiment.endTime ?: DateTime.now())
                    ).days
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
    var reason by remember { mutableStateOf("") }
    var showReasonError by remember { mutableStateOf(false) }

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

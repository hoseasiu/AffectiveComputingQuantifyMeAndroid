package edu.mit.media.mysnapshot.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dagger.hilt.android.AndroidEntryPoint
import edu.mit.media.mysnapshot.data.ExperimentRepository
import edu.mit.media.mysnapshot.engine.ExperimentType
import edu.mit.media.mysnapshot.ui.theme.FadeBlue
import edu.mit.media.mysnapshot.ui.theme.QuantifyMeTheme
import edu.mit.media.mysnapshot.ui.theme.White
import kotlinx.coroutines.flow.first
import org.joda.time.LocalDate
import org.joda.time.format.DateTimeFormat
import javax.inject.Inject

/**
 * Opt-in mid-experiment progress view (AGENT_PLANS/MODERNIZE.md Phase 5.1, paper §6.2):
 * shows check-ins across every stage reached so far, not just the current stage's 7-day
 * grid already on `ExperimentInstructionsActivity`. Only reachable after the user
 * explicitly opts in via that screen's one-time explanation dialog.
 */
@AndroidEntryPoint
class ExperimentProgressActivity : ComponentActivity() {

    @Inject
    lateinit var repository: ExperimentRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val experimentId = intent.getIntExtra(EXPERIMENT_ID_EXTRA, -1)

        setContent {
            QuantifyMeTheme {
                var experimentType by remember { mutableStateOf<ExperimentType?>(null) }
                var stages by remember { mutableStateOf<List<ExperimentRepository.StageProgress>?>(null) }

                LaunchedEffect(experimentId) {
                    val experiment = repository.getExperimentById(experimentId).first()
                    experimentType = experiment?.let { ExperimentType.fromTypeKey(it.type) }
                    stages = repository.getProgressSummary(experimentId)
                }

                ProgressScreen(experimentType, stages)
            }
        }
    }

    companion object {
        const val LOGTAG = "ExperimentProgressActivity"
        private const val EXPERIMENT_ID_EXTRA = "experiment_id"

        @JvmStatic
        fun startActivity(context: Context, experimentId: Int) {
            val intent = Intent(context, ExperimentProgressActivity::class.java)
            intent.putExtra(EXPERIMENT_ID_EXTRA, experimentId)
            context.startActivity(intent)
        }
    }
}

private val dayFormat = DateTimeFormat.forPattern("MMM d")

@Composable
private fun ProgressScreen(
    experimentType: ExperimentType?,
    stages: List<ExperimentRepository.StageProgress>?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FadeBlue)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp, bottom = 20.dp, start = 20.dp, end = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Your Progress So Far", fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            experimentType?.let {
                Text(text = it.name, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }

        when {
            stages == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            stages.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No stages completed yet -- check back after your first check-in.",
                    modifier = Modifier.padding(30.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(stages, key = { it.stage }) { stage ->
                    StageProgressCard(stage)
                }
            }
        }
    }
}

@Composable
private fun StageProgressCard(
    stage: ExperimentRepository.StageProgress
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(10.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            val dateRange = if (stage.start != null && stage.end != null) {
                "${dayFormat.print(stage.start)} - ${dayFormat.print(stage.end)}"
            } else {
                ""
            }
            Text(text = "Stage ${stage.stage}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(text = dateRange, fontSize = 14.sp)

            if (stage.restartCount > 0) {
                Text(
                    text = "Restarted ${stage.restartCount} time${if (stage.restartCount == 1) "" else "s"}",
                    fontSize = 14.sp
                )
            }

            if (stage.checkins.isEmpty()) {
                Text(text = "No check-ins yet this stage.", fontSize = 14.sp)
            } else {
                stage.checkins.sortedBy { it.checkinDate }.forEach { checkin ->
                    Text(
                        text = "${dayFormat.print(LocalDate(checkin.checkinDate))}: " +
                            "happiness ${checkin.happiness}, stress ${checkin.stress}, " +
                            "productivity ${checkin.productivity}",
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

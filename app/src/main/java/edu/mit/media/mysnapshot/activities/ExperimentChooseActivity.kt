package edu.mit.media.mysnapshot.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import edu.mit.media.mysnapshot.R
import edu.mit.media.mysnapshot.data.ExperimentRepository
import edu.mit.media.mysnapshot.engine.ExperimentType
import edu.mit.media.mysnapshot.ui.theme.DarkBlue
import edu.mit.media.mysnapshot.ui.theme.FadeBlue
import edu.mit.media.mysnapshot.ui.theme.QuantifyMeTheme
import edu.mit.media.mysnapshot.ui.theme.White
import edu.mit.media.mysnapshot.ui.theme.rememberQuantifyMeFonts
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ExperimentChooseActivity : ComponentActivity() {

    @Inject
    lateinit var repository: ExperimentRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            QuantifyMeTheme {
                ExperimentChooseScreen(
                    onExperimentTypeSelected = { type ->
                        ExperimentIntroActivity.startActivity(this, type)
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()

        lifecycleScope.launch {
            val experiment = repository.getLatestExperiment().first()
            if (experiment != null && !experiment.isCancelled) {
                startActivity(Intent(this@ExperimentChooseActivity, MainActivity::class.java))
                finish()
                overridePendingTransition(0, 0)
            }
        }
    }

    companion object {
        const val LOGTAG = "ExperimentChooseActivity"
    }
}

// Display order on the choose screen -- deliberately independent of
// ExperimentType.getAllTypes()'s config order (this is a UI-presentation concern).
private val chooseOrder = listOf(
    "leisurehappiness",
    "stepssleepefficiency",
    "sleepdurationproductivity",
    "sleepvariabilitystress"
)

@Composable
private fun ExperimentChooseScreen(onExperimentTypeSelected: (ExperimentType) -> Unit) {
    val fonts = rememberQuantifyMeFonts()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(FadeBlue)
                .padding(top = 60.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 15.dp, vertical = 20.dp)
        ) {
            chooseOrder.map { ExperimentType.fromTypeKey(it) }.forEach { type ->
                Card(
                    onClick = { onExperimentTypeSelected(type) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 7.dp),
                    shape = RoundedCornerShape(10.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
                ) {
                    Image(
                        painter = painterResource(type.chooseBannerIconId),
                        contentDescription = type.name,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart),
            shape = RoundedCornerShape(0.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 7.dp)
        ) {
            Box {
                Text(
                    text = "Select Your Experiment",
                    color = White,
                    fontFamily = fonts.raleway,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkBlue)
                        .padding(top = 25.dp, bottom = 20.dp)
                )
                Image(
                    painter = painterResource(R.drawable.icon_settings_experiment_effectiveness),
                    contentDescription = null,
                    modifier = Modifier
                        .padding(start = 20.dp, top = 15.dp)
                        .size(40.dp)
                )
            }
        }
    }
}

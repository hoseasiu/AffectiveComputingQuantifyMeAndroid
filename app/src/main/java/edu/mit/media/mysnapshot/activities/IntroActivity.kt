package edu.mit.media.mysnapshot.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.mit.media.mysnapshot.R
import edu.mit.media.mysnapshot.ui.theme.FadeGreen
import edu.mit.media.mysnapshot.ui.theme.QuantifyMeTheme

/** The very first onboarding screen (AGENT_PLANS/IMPROVEMENTS.md 3.2): a static welcome splash. */
class IntroActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            QuantifyMeTheme {
                IntroScreen(onGetStarted = {
                    startActivity(Intent(this@IntroActivity, SettingsActivity::class.java))
                    finish()
                })
            }
        }
    }

    companion object {
        const val LOGTAG = "IntroActivity"
    }
}

@Composable
private fun IntroScreen(onGetStarted: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FadeGreen)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.art_icon),
                contentDescription = null,
                modifier = Modifier
                    .size(50.dp)
                    .padding(top = 20.dp, bottom = 10.dp)
            )
            Text(
                text = "Quantify Me",
                fontSize = 24.sp,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Welcome!",
                fontSize = 22.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 25.dp)
            )
            Text(
                text = "Everyone needs different amounts of sleep or different activity levels. What works for others may not work for you.",
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp)
            )
            Text(
                text = buildAnnotatedString {
                    append("We will guide you through experiments so you can find out what works for ")
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append("you") }
                    append(".")
                },
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp)
            )
            Text(
                text = "Before we can start, we will ask a few questions to get to know you better.",
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp)
            )
            Text(
                text = "For science!",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp, bottom = 20.dp)
            )
        }

        Button(
            onClick = onGetStarted,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
        ) {
            Text("Get Started!")
        }
    }
}

package edu.mit.media.mysnapshot.activities

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import edu.mit.media.mysnapshot.data.ExperimentStageState
import edu.mit.media.mysnapshot.database.ExperimentEntity
import edu.mit.media.mysnapshot.database.QuantifyMeDatabase
import edu.mit.media.mysnapshot.engine.ExperimentTypeRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.OffsetDateTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * First Compose UI / instrumentation test for the app (AGENT_PLANS/IMPROVEMENTS.md item 5,
 * [issue #21](https://github.com/hoseasiu/AffectiveComputingQuantifyMeAndroid/issues/21)) --
 * drives the real 6-step check-in wizard (`ExperimentCheckinActivity`, see its class doc)
 * end-to-end against an on-device Room database, the same manual pass that previously had to
 * substitute for automated coverage (AGENT_PLANS/IMPROVEMENTS.md's 3.1 device-verification note).
 *
 * Uses [createEmptyComposeRule] rather than `createAndroidComposeRule<ExperimentCheckinActivity>()`
 * because the check-in needs a specific, pre-seeded experiment id passed via Intent extra --
 * the empty rule lets [ActivityScenario.launch] drive the launch Intent directly while Compose
 * test APIs still find the activity's composables globally.
 *
 * `leisurehappiness` is used (not one of the three Health-Connect-backed types) so this test
 * doesn't also depend on a real/faked Health Connect provider being present on the test device.
 *
 * `@Before`/`@After` call `database.clearAllTables()`: this runs against the same on-device
 * Room database the debug app itself uses (no test-only DB override is wired up), so every run
 * wipes local check-in/experiment data on whatever device/emulator runs it -- expected and fine
 * for a CI emulator, but don't run this against a device with real data you want to keep.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ExperimentCheckinScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createEmptyComposeRule()

    @Inject
    lateinit var database: QuantifyMeDatabase

    private var experimentId: Int = -1

    @Before
    fun setUp() {
        hiltRule.inject()

        // HiltTestApplication skips MyApplication.onCreate(), so the JSON-config-backed
        // ExperimentType registry (Phase 5.4) needs loading here instead -- the real,
        // asset-reading load() (not the JVM-only loadForTest() seam ExperimentRepositoryTest
        // uses), since this runs on a real device/emulator with real assets.
        ExperimentTypeRegistry.load(ApplicationProvider.getApplicationContext())

        runBlocking {
            database.clearAllTables()
            experimentId = seedActiveLeisureHappinessExperiment()
        }
    }

    @After
    fun tearDown() {
        runBlocking { database.clearAllTables() }
    }

    private suspend fun seedActiveLeisureHappinessExperiment(): Int {
        val today = LocalDate.now()
        val state = ExperimentStageState.initial(today)
        val (datesJson, targetsJson, restartJson) = state.toJson()
        val experiment = ExperimentEntity(
            type = "leisurehappiness",
            startTime = OffsetDateTime.now(),
            selfEfficacy = 3,
            appEfficacy = 3,
            experimentEfficacy = 3,
            stageDatesJson = datesJson,
            stageTargetValuesJson = targetsJson,
            stageRestartCountJson = restartJson
        )
        return database.experimentDao().insert(experiment).toInt()
    }

    private fun launchCheckin(): ActivityScenario<ExperimentCheckinActivity> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(context, ExperimentCheckinActivity::class.java).apply {
            putExtra(ExperimentCheckinActivity.EXPERIMENT_ID_EXTRA, experimentId)
        }
        return ActivityScenario.launch(intent)
    }

    @Test
    fun checkinWizard_intro_showsHeadingAndBothActionButtons() {
        launchCheckin().use {
            composeTestRule.onNodeWithText("Daily Check In").assertIsDisplayed()
            composeTestRule.onNodeWithContentDescription("Open the Health Connect app").assertIsDisplayed()
            composeTestRule.onNodeWithText("Continue").assertIsDisplayed()
        }
    }

    @Test
    fun checkinWizard_fullHappyPath_submitsCheckinToTheRepository() {
        launchCheckin().use {
            composeTestRule.onNodeWithText("Continue").performClick()

            val followedQuestion = "How did you do with following the experiment's instructions?"
            composeTestRule.onNodeWithText(followedQuestion).assertIsDisplayed()
            composeTestRule.onNodeWithContentDescription("$followedQuestion Option 4 of 7").performClick()

            composeTestRule.onNodeWithText("How much leisure time did you have in the past 24 hours?")
                .assertIsDisplayed()
            composeTestRule.onNodeWithText("Please Select an Option").performClick()
            composeTestRule.onNodeWithText("1 Hour").performClick()

            val happyQuestion = "How happy were you in the past 24 hours?"
            composeTestRule.onNodeWithText(happyQuestion).assertIsDisplayed()
            composeTestRule.onNodeWithContentDescription("$happyQuestion Option 4 of 7").performClick()

            val stressQuestion = "How stressed were you in the past 24 hours?"
            composeTestRule.onNodeWithText(stressQuestion).assertIsDisplayed()
            composeTestRule.onNodeWithContentDescription("$stressQuestion Option 4 of 7").performClick()

            val productivityQuestion = "How productive were you in the past 24 hours?"
            composeTestRule.onNodeWithText(productivityQuestion).assertIsDisplayed()
            composeTestRule.onNodeWithContentDescription("$productivityQuestion Option 4 of 7").performClick()

            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                runBlocking { database.checkinDao().getCheckinsForExperiment(experimentId).first() }.isNotEmpty()
            }

            val checkins = runBlocking { database.checkinDao().getCheckinsForExperiment(experimentId).first() }
            assertEquals(1, checkins.size)
            val checkin = checkins.first()
            assertEquals(3, checkin.didFollowInstructions)
            assertEquals(3, checkin.happiness)
            assertEquals(3, checkin.stress)
            assertEquals(3, checkin.productivity)
            assertEquals(60, checkin.leisureTime)
        }
    }
}

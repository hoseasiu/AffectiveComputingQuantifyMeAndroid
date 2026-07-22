package edu.mit.media.mysnapshot.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dagger.hilt.android.AndroidEntryPoint
import edu.mit.media.mysnapshot.R
import edu.mit.media.mysnapshot.engine.ExperimentType
import edu.mit.media.mysnapshot.ui.theme.DarkBlue
import edu.mit.media.mysnapshot.ui.theme.FadeBlue
import edu.mit.media.mysnapshot.ui.theme.QuantifyMeTheme
import edu.mit.media.mysnapshot.ui.theme.White
import edu.mit.media.mysnapshot.ui.theme.rememberQuantifyMeFonts
import edu.mit.media.mysnapshot.viewmodel.ExperimentChooseEvent
import edu.mit.media.mysnapshot.viewmodel.ExperimentChooseViewModel

@AndroidEntryPoint
class ExperimentChooseActivity : ComponentActivity() {

    private val viewModel: ExperimentChooseViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            QuantifyMeTheme {
                val uiState by viewModel.uiState.collectAsState()

                LaunchedEffect(Unit) {
                    viewModel.events.collect { event ->
                        when (event) {
                            ExperimentChooseEvent.NavigateToMain -> {
                                startActivity(Intent(this@ExperimentChooseActivity, MainActivity::class.java))
                                finish()
                                overridePendingTransition(0, 0)
                            }

                            is ExperimentChooseEvent.DeleteCustomTypeFailed -> {
                                Toast.makeText(
                                    this@ExperimentChooseActivity,
                                    "Can't delete \"${event.typeName}\" -- it's still used by an experiment.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                }

                ExperimentChooseScreen(
                    builtinTypes = uiState.builtinTypes,
                    customTypes = uiState.customTypes,
                    onExperimentTypeSelected = { type ->
                        ExperimentIntroActivity.startActivity(this, type)
                    },
                    onCreateYourOwnSelected = {
                        CreateExperimentActivity.startActivity(this)
                    },
                    onDeleteCustomType = viewModel::deleteCustomType
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkForExistingExperiment()
        viewModel.refreshTypes()
    }

    companion object {
        const val LOGTAG = "ExperimentChooseActivity"
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExperimentChooseScreen(
    builtinTypes: List<ExperimentType>,
    customTypes: List<ExperimentType>,
    onExperimentTypeSelected: (ExperimentType) -> Unit,
    onCreateYourOwnSelected: () -> Unit,
    onDeleteCustomType: (ExperimentType) -> Unit
) {
    val fonts = rememberQuantifyMeFonts()
    var showManageDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(FadeBlue)
                .padding(top = 60.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 15.dp, vertical = 20.dp)
        ) {
            builtinTypes.forEach { type ->
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

            // Issue #34 -- custom types (#31/#33) authored on-device render the same as a
            // bundled type (generic fallback art via chooseBannerIconId), long-press opens the
            // "My Experiments" management list for deleting one that's no longer wanted.
            customTypes.forEach { type ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 7.dp)
                        .combinedClickable(
                            onClick = { onExperimentTypeSelected(type) },
                            onLongClick = { showManageDialog = true }
                        ),
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

            // Issue #33 -- lets a user author a brand new experiment type on-device instead of
            // picking one of the bundled ones above. Deliberately not part of the
            // ExperimentType.fromTypeKey mapping above, since it navigates to a different
            // Activity (CreateExperimentActivity) rather than an existing ExperimentType.
            Card(
                onClick = onCreateYourOwnSelected,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 7.dp),
                shape = RoundedCornerShape(10.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
            ) {
                Text(
                    text = "Create Your Own Experiment",
                    fontFamily = fonts.raleway,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp)
                )
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

    if (showManageDialog) {
        ManageCustomTypesDialog(
            customTypes = customTypes,
            onDelete = onDeleteCustomType,
            onDismiss = { showManageDialog = false }
        )
    }
}

/**
 * Issue #34's "My Experiments" management list, reached via long-press on any custom-type
 * card. Deletion (not editing -- scope guard for v1) is guarded by
 * [ExperimentChooseViewModel.deleteCustomType]/[edu.mit.media.mysnapshot.data.ExperimentRepository.deleteCustomType]:
 * a type still referenced by any experiment simply doesn't disappear from this list, and the
 * failure reason surfaces as a Toast from the Activity's event collector.
 */
@Composable
private fun ManageCustomTypesDialog(
    customTypes: List<ExperimentType>,
    onDelete: (ExperimentType) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("My Experiments") },
        text = {
            if (customTypes.isEmpty()) {
                Text("No custom experiments yet.")
            } else {
                LazyColumn {
                    items(customTypes, key = { it.typeKey }) { type ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = type.name, modifier = Modifier.padding(vertical = 8.dp))
                            TextButton(onClick = { onDelete(type) }) {
                                Text("Delete")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

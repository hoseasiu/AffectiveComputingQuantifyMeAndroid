package edu.mit.media.mysnapshot.ui.wizard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.mit.media.mysnapshot.ui.theme.PageIndicatorDisabled
import edu.mit.media.mysnapshot.ui.theme.White

/**
 * Shared progressive-reveal wizard pieces used by both [edu.mit.media.mysnapshot.activities.SettingsActivity]
 * and [edu.mit.media.mysnapshot.activities.ExperimentConfigActivity] (issue #22). Ported from
 * the same legacy widgets [edu.mit.media.mysnapshot.activities.ExperimentCheckinActivity]'s
 * private composables of the same shape already replaced (`ScrollPageIndicator`,
 * `ColoredRadioGroup`, `NoDefaultSpinner`) -- kept as a second copy here rather than factored
 * out of the check-in screen, to avoid touching that already-shipped, already-tested code for
 * an unrelated migration.
 */
@Composable
fun StepDotsIndicator(
    currentStep: Int,
    revealedSteps: Int,
    totalSteps: Int,
    onDotClick: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until totalSteps) {
            val revealed = i < revealedSteps
            val isCurrent = i == currentStep
            val description = "Step ${i + 1} of $totalSteps" + when {
                isCurrent -> ", current step"
                !revealed -> ", not yet available"
                else -> ""
            }

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .then(
                        if (revealed) {
                            Modifier.clickable(onClickLabel = description) { onDotClick(i) }
                        } else {
                            Modifier
                        }
                    )
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(if (isCurrent) 14.dp else 10.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isCurrent -> MaterialTheme.colorScheme.primary
                                revealed -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                else -> PageIndicatorDisabled
                            }
                        )
                )
            }
        }
    }
}

@Composable
fun RadioScaleStep(
    icon: Int,
    question: String,
    leftLabel: String,
    rightLabel: String,
    leftColor: Color,
    rightColor: Color,
    selected: Int?,
    onSelect: (Int) -> Unit,
    scaleSize: Int = 7
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier
                .size(64.dp)
                .padding(bottom = 16.dp)
        )
        Text(
            text = question,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .semantics { heading() }
                .padding(bottom = 24.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 0 until scaleSize) {
                ScaleButton(
                    color = scaleButtonColor(i, scaleSize, leftColor, rightColor),
                    selected = selected == i,
                    description = "$question Option ${i + 1} of $scaleSize" +
                        when (i) {
                            0 -> ", $leftLabel"
                            scaleSize - 1 -> ", $rightLabel"
                            else -> ""
                        },
                    onClick = { onSelect(i) }
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = leftLabel, fontSize = 14.sp)
            Text(text = rightLabel, fontSize = 14.sp)
        }
    }
}

@Composable
private fun ScaleButton(
    color: Color,
    selected: Boolean,
    description: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(color)
            .then(
                if (selected) {
                    Modifier.background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f), CircleShape)
                } else {
                    Modifier
                }
            )
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Text(text = "✓", color = White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
    }
}

/**
 * Direct port of `ColoredRadioGroup.getColor()`'s linear interpolation (`i / total`, not
 * `i / (total - 1)`, so the rightmost button never quite reaches the pure [rightColor]) --
 * see [edu.mit.media.mysnapshot.activities.ExperimentCheckinActivity]'s identical port for
 * the same reasoning.
 */
private fun scaleButtonColor(index: Int, total: Int, leftColor: Color, rightColor: Color): Color {
    val fraction = index / total.toFloat()
    return Color(
        red = leftColor.red + (rightColor.red - leftColor.red) * fraction,
        green = leftColor.green + (rightColor.green - leftColor.green) * fraction,
        blue = leftColor.blue + (rightColor.blue - leftColor.blue) * fraction,
        alpha = 1f
    )
}

/** Replaces `NoDefaultSpinner`: an empty-string sentinel stands in for "nothing selected yet". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownStep(
    icon: Int,
    question: String,
    prompt: String,
    labels: Array<String>,
    values: Array<String>,
    selected: String?,
    onSelect: (String) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val selectedLabel = selected
        ?.let { value -> values.indexOf(value).takeIf { it >= 0 } }
        ?.let { labels[it] }
        ?: ""

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier
                .size(64.dp)
                .padding(bottom = 16.dp)
        )
        Text(
            text = question,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .semantics { heading() }
                .padding(bottom = 24.dp)
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = selectedLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text(prompt) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
                    .semantics { contentDescription = "$question. $prompt. Currently $selectedLabel" }
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                labels.forEachIndexed { i, label ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            expanded = false
                            onSelect(values[i])
                        }
                    )
                }
            }
        }
    }
}

package edu.mit.media.mysnapshot.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val QuantifyMeColorScheme = lightColorScheme(
    primary = AccentRed,
)

@Composable
fun QuantifyMeTheme(content: @Composable () -> Unit) {
    // Legacy XML screens are all light-themed with hardcoded colors; Compose screens follow
    // suit for now rather than introducing a dark variant mid-migration (Phase 4, see
    // AGENT_PLANS/MODERNIZE.md).
    MaterialTheme(
        colorScheme = QuantifyMeColorScheme,
        content = content
    )
}

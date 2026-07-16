package edu.mit.media.mysnapshot.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val QuantifyMeLightColorScheme = lightColorScheme(
    primary = AccentRed,
)

// Dark counterpart of QuantifyMeLightColorScheme. AccentRed is kept as the primary (it's
// already light/pastel enough to read well on dark surfaces); the rest of the tokens are
// dedicated dark-mode tones -- not inverted light-scheme colors -- built from DarkBlue/
// DarkPurple so the app's existing hue identity carries over. See Color.kt for the palette.
private val QuantifyMeDarkColorScheme = darkColorScheme(
    primary = AccentRed,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = TertiaryDark,
    onTertiary = OnTertiaryDark,
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
)

/**
 * App-wide Material 3 theme for the Compose screens.
 *
 * Follows the system light/dark setting via [isSystemInDarkTheme] and picks between
 * [QuantifyMeLightColorScheme] and [QuantifyMeDarkColorScheme] accordingly. The legacy XML
 * screens are untouched by this and remain hardcoded light, but Compose screens (Phase 4
 * migration) now adapt to the device theme.
 *
 * [dynamicColor] opts into Material You wallpaper-derived color (Android 12+, via
 * [dynamicLightColorScheme]/[dynamicDarkColorScheme]), falling back to the brand schemes above
 * on older devices or when disabled. Defaults to `false`: QuantifyMe has a deliberate brand
 * accent (the coral AccentRed) used throughout onboarding/experiment flows, and letting
 * per-device wallpaper colors override that would make the brand inconsistent across users and
 * screenshots. Existing call sites (`QuantifyMeTheme { ... }`) are unaffected since this is a
 * new leading parameter with a default, and Kotlin resolves the trailing lambda to `content`
 * regardless.
 */
@Composable
fun QuantifyMeTheme(dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> QuantifyMeDarkColorScheme
        else -> QuantifyMeLightColorScheme
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

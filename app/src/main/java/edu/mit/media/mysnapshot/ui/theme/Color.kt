package edu.mit.media.mysnapshot.ui.theme

import androidx.compose.ui.graphics.Color

// Mirrors app/src/main/res/values/colors.xml -- kept as a small manual mapping rather than
// pulling every legacy XML color into Compose, since Phase 4 only needs the ones screens
// migrated so far actually reference.
val FadeBlue = Color(0xFFC5D4DB)
val FadeRed = Color(0xFFF5DFDC)
val White = Color(0xFFFFFFFF)
val PageIndicatorDisabled = Color(0xFFCCCCCC)
val DayCountGrey = Color(0xFF555555)
val AccentRed = Color(0xFFF58D80)
val DarkBlue = Color(0xFF618EA5)
val FadeYellow = Color(0xFFFFF7E6)
val Yellow = Color(0xFFFFDE9B)
val DarkPurple = Color(0xFF8061A1)
val FadeGreen = Color(0xFFC5D1C6)

// Mirrors colors.xml's `radio_red`/`radio_green` (== AccentRed / #9fd2a7), the default
// left/right endpoints ColoredRadioGroup lerps its 7 scale-button colors between.
val RadioRed = AccentRed
val RadioGreen = Color(0xFF9FD2A7)

// Dark-mode color scheme tokens. AccentRed (the light-scheme primary) is already a light,
// pastel coral with plenty of luminance, so it's reused as-is for the dark primary per M3
// guidance (dark themes want higher-tone/lighter primaries against dark surfaces); the
// remaining tokens below are new dark-specific tones -- not simple inversions of the light
// palette -- built around DarkBlue/DarkPurple to keep the app's existing hue identity while
// giving comfortable contrast on dark surfaces.
val OnPrimaryDark = Color(0xFF4A1508)
val PrimaryContainerDark = Color(0xFF6E2A1E)
val OnPrimaryContainerDark = Color(0xFFFFDBD1)
val SecondaryDark = Color(0xFF9CC4D8)
val OnSecondaryDark = Color(0xFF0D2A35)
val SecondaryContainerDark = Color(0xFF32505E)
val OnSecondaryContainerDark = Color(0xFFC5E4F0)
val TertiaryDark = Color(0xFFCBB0E2)
val OnTertiaryDark = Color(0xFF33144A)
val TertiaryContainerDark = Color(0xFF4D3468)
val OnTertiaryContainerDark = Color(0xFFE8D6F5)
val BackgroundDark = Color(0xFF1C1815)
val OnBackgroundDark = Color(0xFFEAE1DE)
val SurfaceDark = Color(0xFF1C1815)
val OnSurfaceDark = Color(0xFFEAE1DE)
val SurfaceVariantDark = Color(0xFF4D4038)
val OnSurfaceVariantDark = Color(0xFFD3C4BB)
val OutlineDark = Color(0xFF9C8D85)

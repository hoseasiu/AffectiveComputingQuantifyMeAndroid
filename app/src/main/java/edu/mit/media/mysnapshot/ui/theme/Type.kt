package edu.mit.media.mysnapshot.ui.theme

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily

// Compose equivalent of view/FontTextView.java's TYPEFACE_NAMES table -- the same five .ttf
// assets under assets/fonts/, loaded via the AssetManager-backed Font() overload rather than
// duplicating them into res/font/. Deferred from HistoryActivity's Compose migration (Phase 4)
// to whichever screen first needed more than Material 3's default typography; that's this one.
private fun assetFontFamily(context: Context, fileName: String) =
    FontFamily(Font("fonts/$fileName", context.assets))

data class QuantifyMeFonts(
    val montserratRegular: FontFamily,
    val montserratBold: FontFamily,
    val raleway: FontFamily,
    val ralewaySemibold: FontFamily,
    val ralewayLight: FontFamily
)

@Composable
fun rememberQuantifyMeFonts(): QuantifyMeFonts {
    val context = LocalContext.current
    return remember {
        QuantifyMeFonts(
            montserratRegular = assetFontFamily(context, "Montserrat-Regular.ttf"),
            montserratBold = assetFontFamily(context, "Montserrat-Bold.ttf"),
            raleway = assetFontFamily(context, "Raleway-Medium.ttf"),
            ralewaySemibold = assetFontFamily(context, "Raleway-SemiBold.ttf"),
            ralewayLight = assetFontFamily(context, "Raleway-Light.ttf")
        )
    }
}

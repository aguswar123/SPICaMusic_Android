package me.spica27.spicamusic.ui.widget

import androidx.annotation.StringRes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import me.spica27.spicamusic.R

/**
 * Fonts the user can pick for the player and for the lyrics.
 * [id] is what gets stored in the settings; [family] is null for "Default" (the app's own font).
 */
enum class LyricsFontOption(
    val id: String,
    @StringRes val labelRes: Int,
    val family: FontFamily?,
) {
    Default("default", R.string.lyr_set_font_default, null),
    System("system", R.string.lyr_set_font_system, FontFamily.SansSerif),
    Serif("serif", R.string.lyr_set_font_serif, FontFamily.Serif),
    Monospace("mono", R.string.lyr_set_font_mono, FontFamily.Monospace),
    Cursive("cursive", R.string.lyr_set_font_cursive, FontFamily.Cursive),
    ;

    companion object {
        fun fromId(id: String?): LyricsFontOption = entries.firstOrNull { it.id == id } ?: Default

        /**
         * Font family for the lyrics only. Returns null when the lyrics should simply follow the player font
         * ("same"), otherwise the chosen family (the plain default font when "Default" is chosen).
         */
        fun lyricsFamily(lyricsFont: String): FontFamily? =
            if (lyricsFont == SAME_AS_PLAYER) null else fromId(lyricsFont).family ?: FontFamily.Default

        const val SAME_AS_PLAYER = "same"
    }
}

/** Applies [family] to every text style of the typography (no-op when null). */
fun Typography.withFontFamily(family: FontFamily?): Typography =
    if (family == null) {
        this
    } else {
        copy(
            displayLarge = displayLarge.copy(fontFamily = family),
            displayMedium = displayMedium.copy(fontFamily = family),
            displaySmall = displaySmall.copy(fontFamily = family),
            headlineLarge = headlineLarge.copy(fontFamily = family),
            headlineMedium = headlineMedium.copy(fontFamily = family),
            headlineSmall = headlineSmall.copy(fontFamily = family),
            titleLarge = titleLarge.copy(fontFamily = family),
            titleMedium = titleMedium.copy(fontFamily = family),
            titleSmall = titleSmall.copy(fontFamily = family),
            bodyLarge = bodyLarge.copy(fontFamily = family),
            bodyMedium = bodyMedium.copy(fontFamily = family),
            bodySmall = bodySmall.copy(fontFamily = family),
            labelLarge = labelLarge.copy(fontFamily = family),
            labelMedium = labelMedium.copy(fontFamily = family),
            labelSmall = labelSmall.copy(fontFamily = family),
        )
    }

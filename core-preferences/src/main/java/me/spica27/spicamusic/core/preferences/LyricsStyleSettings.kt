package me.spica27.spicamusic.core.preferences

/** Where the transliteration (romanized text) sits relative to the lyric words. */
enum class TransliterationPosition(
    val value: String,
) {
    Top("top"),
    Bottom("bottom"),
    ;

    companion object {
        fun fromString(value: String?): TransliterationPosition = entries.firstOrNull { it.value == value } ?: Bottom
    }
}

/** How the sung words are animated. [Default] is the original sweep, [Floaty] floats whole words letter by letter. */
enum class LyricsAnimation(
    val value: String,
) {
    Default("default"),
    Floaty("floaty"),
    ;

    companion object {
        fun fromString(value: String?): LyricsAnimation = entries.firstOrNull { it.value == value } ?: Default
    }
}

/**
 * Every user-adjustable lyrics option, with the defaults and ranges of the reference lyrics window.
 * All percentages are plain Ints (100 = 100%).
 */
data class LyricsStyleSettings(
    // ---- Font ----
    /** Font for the whole player: "default" or "system". */
    val fontStyle: String = "default",
    /** Font for the lyrics only: "same" follows [fontStyle]. */
    val lyricsFont: String = "same",
    /** 400 Regular .. 800 Extra bold. */
    val lyricsWeight: Int = DEFAULT_WEIGHT,
    // ---- Text size (percent) ----
    val lyricsSize: Int = 100,
    val transliterationSize: Int = 100,
    val translationSize: Int = 100,
    val backgroundTranslationSize: Int = 100,
    // ---- Transliteration ----
    val attachToWords: Boolean = true,
    val transliterationPosition: TransliterationPosition = TransliterationPosition.Bottom,
    // ---- Blur and opacity (percent) ----
    val unsungOpacity: Int = 50,
    val blur: Int = 100,
    // ---- Motion ----
    /** Time the lyrics take to scroll to the current line, in ms. */
    val moveDurationMs: Int = 600,
    /** Where the playing line sits on screen, percent of the height (5..70). */
    val currentLinePosition: Int = 40,
    // ---- Jiggle ----
    val jiggleEnabled: Boolean = true,
    val jiggleStrength: Int = 50,
    val jiggleDurationMs: Int = 1200,
    val jiggleDelayMs: Int = 45,
    // ---- Word animation ----
    val lyricsAnimation: LyricsAnimation = LyricsAnimation.Default,
) {
    /** Returns a copy with every value forced into its allowed range. */
    fun sanitized(): LyricsStyleSettings =
        copy(
            lyricsWeight = (lyricsWeight / 100 * 100).coerceIn(WEIGHT_RANGE),
            lyricsSize = lyricsSize.coerceIn(LYRICS_SIZE_RANGE),
            transliterationSize = transliterationSize.coerceIn(SECONDARY_SIZE_RANGE),
            translationSize = translationSize.coerceIn(SECONDARY_SIZE_RANGE),
            backgroundTranslationSize = backgroundTranslationSize.coerceIn(SECONDARY_SIZE_RANGE),
            unsungOpacity = unsungOpacity.coerceIn(UNSUNG_RANGE),
            blur = blur.coerceIn(BLUR_RANGE),
            moveDurationMs = moveDurationMs.coerceIn(MOVE_RANGE),
            currentLinePosition = currentLinePosition.coerceIn(POSITION_RANGE),
            jiggleStrength = jiggleStrength.coerceIn(JIGGLE_STRENGTH_RANGE),
            jiggleDurationMs = jiggleDurationMs.coerceIn(JIGGLE_DURATION_RANGE),
            jiggleDelayMs = jiggleDelayMs.coerceIn(JIGGLE_DELAY_RANGE),
        )

    companion object {
        const val DEFAULT_WEIGHT = 700
        val WEIGHT_RANGE = 400..800
        val LYRICS_SIZE_RANGE = 70..140
        val SECONDARY_SIZE_RANGE = 60..200
        val UNSUNG_RANGE = 10..100
        val BLUR_RANGE = 0..200
        val MOVE_RANGE = 200..1500
        val POSITION_RANGE = 5..70
        val JIGGLE_STRENGTH_RANGE = 10..100
        val JIGGLE_DURATION_RANGE = 500..2500
        val JIGGLE_DELAY_RANGE = 0..150
    }
}

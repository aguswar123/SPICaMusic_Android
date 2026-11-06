package me.spica27.spicamusic.ui.widget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.spica27.spicamusic.R
import me.spica27.spicamusic.core.preferences.LyricsAnimation
import me.spica27.spicamusic.core.preferences.LyricsStyleSettings
import me.spica27.spicamusic.core.preferences.TransliterationPosition

/**
 * Settings sheet of the lyrics window (opened from the lyrics tool button).
 *
 * Sections: Font, Text size, Transliteration, Lyrics blur and opacity, Jiggle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsSettingsSheet(
    settings: LyricsStyleSettings,
    onUpdate: ((LyricsStyleSettings) -> LyricsStyleSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp),
        ) {
            AnimationSection(settings = settings, onUpdate = onUpdate)
            FontSection(settings = settings, onUpdate = onUpdate)
            TextSizeSection(settings = settings, onUpdate = onUpdate)
            TransliterationSection(settings = settings, onUpdate = onUpdate)
            BlurAndOpacitySection(settings = settings, onUpdate = onUpdate)
            JiggleSection(settings = settings, onUpdate = onUpdate)
        }
    }
}

@Composable
private fun AnimationSection(
    settings: LyricsStyleSettings,
    onUpdate: ((LyricsStyleSettings) -> LyricsStyleSettings) -> Unit,
) {
    LyricsSettingsSection(stringResource(R.string.lyr_set_section_animation))

    LyricsSettingsChips(
        label = stringResource(R.string.lyr_set_animation),
        description = stringResource(R.string.lyr_set_animation_desc),
        options = LyricsAnimation.entries,
        selected = settings.lyricsAnimation,
        optionLabel = {
            stringResource(
                if (it == LyricsAnimation.Floaty) R.string.lyr_set_animation_floaty else R.string.lyr_set_animation_default,
            )
        },
        onSelect = { option -> onUpdate { it.copy(lyricsAnimation = option) } },
    )
}

@Composable
private fun FontSection(
    settings: LyricsStyleSettings,
    onUpdate: ((LyricsStyleSettings) -> LyricsStyleSettings) -> Unit,
) {
    LyricsSettingsSection(stringResource(R.string.lyr_set_section_font))

    LyricsSettingsChips(
        label = stringResource(R.string.lyr_set_font_style),
        description = stringResource(R.string.lyr_set_font_style_desc),
        options = LyricsFontOption.entries,
        selected = LyricsFontOption.fromId(settings.fontStyle),
        optionLabel = { stringResource(it.labelRes) },
        optionFont = { it.family },
        onSelect = { option -> onUpdate { it.copy(fontStyle = option.id) } },
    )

    // "Same as player" + every font
    val lyricsFontOptions = listOf<String>(LyricsFontOption.SAME_AS_PLAYER) + LyricsFontOption.entries.map { it.id }
    LyricsSettingsChips(
        label = stringResource(R.string.lyr_set_lyrics_font),
        description = stringResource(R.string.lyr_set_lyrics_font_desc),
        options = lyricsFontOptions,
        selected = settings.lyricsFont,
        optionLabel = { id ->
            if (id == LyricsFontOption.SAME_AS_PLAYER) {
                stringResource(R.string.lyr_set_font_same)
            } else {
                stringResource(LyricsFontOption.fromId(id).labelRes)
            }
        },
        optionFont = { id -> if (id == LyricsFontOption.SAME_AS_PLAYER) null else LyricsFontOption.fromId(id).family },
        onSelect = { id -> onUpdate { it.copy(lyricsFont = id) } },
    )

    val weightLabels =
        mapOf(
            400 to stringResource(R.string.lyr_set_weight_regular),
            500 to stringResource(R.string.lyr_set_weight_medium),
            600 to stringResource(R.string.lyr_set_weight_semibold),
            700 to stringResource(R.string.lyr_set_weight_bold),
            800 to stringResource(R.string.lyr_set_weight_extra_bold),
        )
    LyricsSettingsSlider(
        label = stringResource(R.string.lyr_set_weight),
        description = stringResource(R.string.lyr_set_weight_desc),
        value = settings.lyricsWeight,
        range = LyricsStyleSettings.WEIGHT_RANGE,
        step = 100,
        valueText = { weightLabels[it] ?: it.toString() },
        onValueChange = { v -> onUpdate { it.copy(lyricsWeight = v) } },
    )

    LyricsSettingsResetButton(
        text = stringResource(R.string.lyr_set_reset_fonts),
        onClick = {
            onUpdate {
                it.copy(
                    fontStyle = LyricsStyleSettings().fontStyle,
                    lyricsFont = LyricsStyleSettings().lyricsFont,
                    lyricsWeight = LyricsStyleSettings.DEFAULT_WEIGHT,
                )
            }
        },
    )
}

@Composable
private fun TextSizeSection(
    settings: LyricsStyleSettings,
    onUpdate: ((LyricsStyleSettings) -> LyricsStyleSettings) -> Unit,
) {
    LyricsSettingsSection(stringResource(R.string.lyr_set_section_text_size))
    val percent: (Int) -> String = { "$it%" }

    LyricsSettingsSlider(
        label = stringResource(R.string.lyr_set_lyrics_size),
        description = stringResource(R.string.lyr_set_lyrics_size_desc),
        value = settings.lyricsSize,
        range = LyricsStyleSettings.LYRICS_SIZE_RANGE,
        step = 5,
        valueText = percent,
        onValueChange = { v -> onUpdate { it.copy(lyricsSize = v) } },
    )
    LyricsSettingsSlider(
        label = stringResource(R.string.lyr_set_transliteration_size),
        description = stringResource(R.string.lyr_set_transliteration_size_desc),
        value = settings.transliterationSize,
        range = LyricsStyleSettings.SECONDARY_SIZE_RANGE,
        step = 5,
        valueText = percent,
        onValueChange = { v -> onUpdate { it.copy(transliterationSize = v) } },
    )
    LyricsSettingsSlider(
        label = stringResource(R.string.lyr_set_translation_size),
        description = stringResource(R.string.lyr_set_translation_size_desc),
        value = settings.translationSize,
        range = LyricsStyleSettings.SECONDARY_SIZE_RANGE,
        step = 5,
        valueText = percent,
        onValueChange = { v -> onUpdate { it.copy(translationSize = v) } },
    )
    LyricsSettingsSlider(
        label = stringResource(R.string.lyr_set_bg_translation_size),
        description = stringResource(R.string.lyr_set_bg_translation_size_desc),
        value = settings.backgroundTranslationSize,
        range = LyricsStyleSettings.SECONDARY_SIZE_RANGE,
        step = 5,
        valueText = percent,
        onValueChange = { v -> onUpdate { it.copy(backgroundTranslationSize = v) } },
    )

    LyricsSettingsResetButton(
        text = stringResource(R.string.lyr_set_reset_text_size),
        onClick = {
            onUpdate {
                it.copy(
                    lyricsSize = 100,
                    transliterationSize = 100,
                    translationSize = 100,
                    backgroundTranslationSize = 100,
                )
            }
        },
    )
}

@Composable
private fun TransliterationSection(
    settings: LyricsStyleSettings,
    onUpdate: ((LyricsStyleSettings) -> LyricsStyleSettings) -> Unit,
) {
    LyricsSettingsSection(stringResource(R.string.lyr_set_section_transliteration))

    LyricsSettingsToggle(
        label = stringResource(R.string.lyr_set_attach_to_words),
        description = stringResource(R.string.lyr_set_attach_to_words_desc),
        checked = settings.attachToWords,
        onCheckedChange = { v -> onUpdate { it.copy(attachToWords = v) } },
    )

    LyricsSettingsChips(
        label = stringResource(R.string.lyr_set_translit_position),
        description = stringResource(R.string.lyr_set_translit_position_desc),
        options = TransliterationPosition.entries,
        selected = settings.transliterationPosition,
        optionLabel = {
            stringResource(
                if (it == TransliterationPosition.Top) R.string.lyr_set_position_top else R.string.lyr_set_position_bottom,
            )
        },
        onSelect = { v -> onUpdate { it.copy(transliterationPosition = v) } },
    )
}

@Composable
private fun BlurAndOpacitySection(
    settings: LyricsStyleSettings,
    onUpdate: ((LyricsStyleSettings) -> LyricsStyleSettings) -> Unit,
) {
    LyricsSettingsSection(stringResource(R.string.lyr_set_section_blur_opacity))
    val off = stringResource(R.string.lyr_set_off)

    LyricsSettingsSlider(
        label = stringResource(R.string.lyr_set_unsung_opacity),
        description = stringResource(R.string.lyr_set_unsung_opacity_desc),
        value = settings.unsungOpacity,
        range = LyricsStyleSettings.UNSUNG_RANGE,
        step = 5,
        valueText = { "$it%" },
        onValueChange = { v -> onUpdate { it.copy(unsungOpacity = v) } },
    )
    LyricsSettingsSlider(
        label = stringResource(R.string.lyr_set_blur),
        description = stringResource(R.string.lyr_set_blur_desc),
        value = settings.blur,
        range = LyricsStyleSettings.BLUR_RANGE,
        step = 10,
        valueText = { if (it == 0) off else "$it%" },
        onValueChange = { v -> onUpdate { it.copy(blur = v) } },
    )
    LyricsSettingsSlider(
        label = stringResource(R.string.lyr_set_move_duration),
        description = stringResource(R.string.lyr_set_move_duration_desc),
        value = settings.moveDurationMs,
        range = LyricsStyleSettings.MOVE_RANGE,
        step = 50,
        valueText = { String.format(java.util.Locale.US, "%.2f s", it / 1000f) },
        onValueChange = { v -> onUpdate { it.copy(moveDurationMs = v) } },
    )
    LyricsSettingsSlider(
        label = stringResource(R.string.lyr_set_current_position),
        description = stringResource(R.string.lyr_set_current_position_desc),
        value = settings.currentLinePosition,
        range = LyricsStyleSettings.POSITION_RANGE,
        step = 5,
        valueText = { "$it%" },
        onValueChange = { v -> onUpdate { it.copy(currentLinePosition = v) } },
    )
}

@Composable
private fun JiggleSection(
    settings: LyricsStyleSettings,
    onUpdate: ((LyricsStyleSettings) -> LyricsStyleSettings) -> Unit,
) {
    LyricsSettingsSection(stringResource(R.string.lyr_set_section_jiggle))

    LyricsSettingsToggle(
        label = stringResource(R.string.lyr_set_jiggle),
        description = stringResource(R.string.lyr_set_jiggle_desc),
        checked = settings.jiggleEnabled,
        onCheckedChange = { v -> onUpdate { it.copy(jiggleEnabled = v) } },
    )

    // The three jiggle sliders only matter while the effect is on.
    if (settings.jiggleEnabled) {
        LyricsSettingsSlider(
            label = stringResource(R.string.lyr_set_jiggle_strength),
            description = stringResource(R.string.lyr_set_jiggle_strength_desc),
            value = settings.jiggleStrength,
            range = LyricsStyleSettings.JIGGLE_STRENGTH_RANGE,
            step = 5,
            valueText = { "$it%" },
            onValueChange = { v -> onUpdate { it.copy(jiggleStrength = v) } },
        )
        LyricsSettingsSlider(
            label = stringResource(R.string.lyr_set_jiggle_duration),
            description = stringResource(R.string.lyr_set_jiggle_duration_desc),
            value = settings.jiggleDurationMs,
            range = LyricsStyleSettings.JIGGLE_DURATION_RANGE,
            step = 100,
            valueText = { String.format(java.util.Locale.US, "%.1f s", it / 1000f) },
            onValueChange = { v -> onUpdate { it.copy(jiggleDurationMs = v) } },
        )
        LyricsSettingsSlider(
            label = stringResource(R.string.lyr_set_jiggle_delay),
            description = stringResource(R.string.lyr_set_jiggle_delay_desc),
            value = settings.jiggleDelayMs,
            range = LyricsStyleSettings.JIGGLE_DELAY_RANGE,
            step = 5,
            valueText = { "$it ms" },
            onValueChange = { v -> onUpdate { it.copy(jiggleDelayMs = v) } },
        )
    }
}

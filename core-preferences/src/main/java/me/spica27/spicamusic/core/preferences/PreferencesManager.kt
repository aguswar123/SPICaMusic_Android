package me.spica27.spicamusic.core.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class PreferencesManager(
    private val context: Context,
) {
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

    object Keys {
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val LIQUID_GLASS_ENABLED = booleanPreferencesKey("liquid_glass_enabled")
        val BOTTOM_BAR_LAYOUT = stringPreferencesKey("bottom_bar_layout")
        val THEME_COLOR_STYLE = stringPreferencesKey("theme_color_style")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val DYNAMIC_SPECTRUM_BACKGROUND = stringPreferencesKey("dynamic_spectrum_background")
        val DYNAMIC_COVER_TYPE = stringPreferencesKey("dynamic_cover_type")
        val PROGRESS_BAR_STYLE = stringPreferencesKey("progress_bar_style")
        val EQ_ENABLED = booleanPreferencesKey("eq_enabled")
        val EQ_BANDS = stringPreferencesKey("eq_bands")
        val LOUDNESS_NORMALIZATION_ENABLED = booleanPreferencesKey("loudness_normalization_enabled")

        // 目标响度（LUFS）。本项目 float 一律以字符串存储，见 getFloat/setFloat
        val LOUDNESS_TARGET_LUFS = stringPreferencesKey("loudness_target_lufs")
        val SCAN_MIN_DURATION_SEC = stringPreferencesKey("scan_min_duration_sec")
        val SCAN_MIN_FILE_SIZE_KB = stringPreferencesKey("scan_min_file_size_kb")
        val SCAN_ENABLED_FORMATS = stringPreferencesKey("scan_enabled_formats")
        val SCAN_LAST_COMPLETED_AT = stringPreferencesKey("scan_last_completed_at")

        // 是否把 "A/B"、"A、B"、"A & B" 等单个艺人字符串拆成多位艺人（默认关闭）
        val ARTIST_SPLIT_ENABLED = booleanPreferencesKey("artist_split_enabled")

        // ---- 歌词样式（Lyrics style）----
        val LYRIC_FONT_STYLE = stringPreferencesKey("lyric_font_style")
        val LYRIC_FONT = stringPreferencesKey("lyric_font")
        val LYRIC_WEIGHT = intPreferencesKey("lyric_weight")
        val LYRIC_SIZE = intPreferencesKey("lyric_size")
        val LYRIC_TRANSLITERATION_SIZE = intPreferencesKey("lyric_transliteration_size")
        val LYRIC_TRANSLATION_SIZE = intPreferencesKey("lyric_translation_size")
        val LYRIC_BG_TRANSLATION_SIZE = intPreferencesKey("lyric_bg_translation_size")
        val LYRIC_ATTACH_TO_WORDS = booleanPreferencesKey("lyric_attach_to_words")
        val LYRIC_TRANSLITERATION_POSITION = stringPreferencesKey("lyric_transliteration_position")
        val LYRIC_UNSUNG_OPACITY = intPreferencesKey("lyric_unsung_opacity")
        val LYRIC_BLUR = intPreferencesKey("lyric_blur")
        val LYRIC_MOVE_DURATION_MS = intPreferencesKey("lyric_move_duration_ms")
        val LYRIC_CURRENT_POSITION = intPreferencesKey("lyric_current_position")
        val LYRIC_JIGGLE_ENABLED = booleanPreferencesKey("lyric_jiggle_enabled")
        val LYRIC_JIGGLE_STRENGTH = intPreferencesKey("lyric_jiggle_strength")
        val LYRIC_JIGGLE_DURATION_MS = intPreferencesKey("lyric_jiggle_duration_ms")
        val LYRIC_JIGGLE_DELAY_MS = intPreferencesKey("lyric_jiggle_delay_ms")
    }


    /** 歌词样式：任何一项变化都会发出完整、已校正的设置对象。 */
    val lyricsStyle: Flow<LyricsStyleSettings> =
        context.dataStore.data
            .map { p ->
                val d = LyricsStyleSettings()
                LyricsStyleSettings(
                    fontStyle = p[Keys.LYRIC_FONT_STYLE] ?: d.fontStyle,
                    lyricsFont = p[Keys.LYRIC_FONT] ?: d.lyricsFont,
                    lyricsWeight = p[Keys.LYRIC_WEIGHT] ?: d.lyricsWeight,
                    lyricsSize = p[Keys.LYRIC_SIZE] ?: d.lyricsSize,
                    transliterationSize = p[Keys.LYRIC_TRANSLITERATION_SIZE] ?: d.transliterationSize,
                    translationSize = p[Keys.LYRIC_TRANSLATION_SIZE] ?: d.translationSize,
                    backgroundTranslationSize = p[Keys.LYRIC_BG_TRANSLATION_SIZE] ?: d.backgroundTranslationSize,
                    attachToWords = p[Keys.LYRIC_ATTACH_TO_WORDS] ?: d.attachToWords,
                    transliterationPosition =
                        p[Keys.LYRIC_TRANSLITERATION_POSITION]
                            ?.let(TransliterationPosition::fromString) ?: d.transliterationPosition,
                    unsungOpacity = p[Keys.LYRIC_UNSUNG_OPACITY] ?: d.unsungOpacity,
                    blur = p[Keys.LYRIC_BLUR] ?: d.blur,
                    moveDurationMs = p[Keys.LYRIC_MOVE_DURATION_MS] ?: d.moveDurationMs,
                    currentLinePosition = p[Keys.LYRIC_CURRENT_POSITION] ?: d.currentLinePosition,
                    jiggleEnabled = p[Keys.LYRIC_JIGGLE_ENABLED] ?: d.jiggleEnabled,
                    jiggleStrength = p[Keys.LYRIC_JIGGLE_STRENGTH] ?: d.jiggleStrength,
                    jiggleDurationMs = p[Keys.LYRIC_JIGGLE_DURATION_MS] ?: d.jiggleDurationMs,
                    jiggleDelayMs = p[Keys.LYRIC_JIGGLE_DELAY_MS] ?: d.jiggleDelayMs,
                ).sanitized()
            }.distinctUntilChanged()

    /** 读取当前歌词样式，套用 [transform] 后一次性写回所有键。 */
    suspend fun updateLyricsStyle(transform: (LyricsStyleSettings) -> LyricsStyleSettings) {
        context.dataStore.edit { p ->
            val d = LyricsStyleSettings()
            val current =
                LyricsStyleSettings(
                    fontStyle = p[Keys.LYRIC_FONT_STYLE] ?: d.fontStyle,
                    lyricsFont = p[Keys.LYRIC_FONT] ?: d.lyricsFont,
                    lyricsWeight = p[Keys.LYRIC_WEIGHT] ?: d.lyricsWeight,
                    lyricsSize = p[Keys.LYRIC_SIZE] ?: d.lyricsSize,
                    transliterationSize = p[Keys.LYRIC_TRANSLITERATION_SIZE] ?: d.transliterationSize,
                    translationSize = p[Keys.LYRIC_TRANSLATION_SIZE] ?: d.translationSize,
                    backgroundTranslationSize = p[Keys.LYRIC_BG_TRANSLATION_SIZE] ?: d.backgroundTranslationSize,
                    attachToWords = p[Keys.LYRIC_ATTACH_TO_WORDS] ?: d.attachToWords,
                    transliterationPosition =
                        p[Keys.LYRIC_TRANSLITERATION_POSITION]
                            ?.let(TransliterationPosition::fromString) ?: d.transliterationPosition,
                    unsungOpacity = p[Keys.LYRIC_UNSUNG_OPACITY] ?: d.unsungOpacity,
                    blur = p[Keys.LYRIC_BLUR] ?: d.blur,
                    moveDurationMs = p[Keys.LYRIC_MOVE_DURATION_MS] ?: d.moveDurationMs,
                    currentLinePosition = p[Keys.LYRIC_CURRENT_POSITION] ?: d.currentLinePosition,
                    jiggleEnabled = p[Keys.LYRIC_JIGGLE_ENABLED] ?: d.jiggleEnabled,
                    jiggleStrength = p[Keys.LYRIC_JIGGLE_STRENGTH] ?: d.jiggleStrength,
                    jiggleDurationMs = p[Keys.LYRIC_JIGGLE_DURATION_MS] ?: d.jiggleDurationMs,
                    jiggleDelayMs = p[Keys.LYRIC_JIGGLE_DELAY_MS] ?: d.jiggleDelayMs,
                )
            val n = transform(current).sanitized()
            p[Keys.LYRIC_FONT_STYLE] = n.fontStyle
            p[Keys.LYRIC_FONT] = n.lyricsFont
            p[Keys.LYRIC_WEIGHT] = n.lyricsWeight
            p[Keys.LYRIC_SIZE] = n.lyricsSize
            p[Keys.LYRIC_TRANSLITERATION_SIZE] = n.transliterationSize
            p[Keys.LYRIC_TRANSLATION_SIZE] = n.translationSize
            p[Keys.LYRIC_BG_TRANSLATION_SIZE] = n.backgroundTranslationSize
            p[Keys.LYRIC_ATTACH_TO_WORDS] = n.attachToWords
            p[Keys.LYRIC_TRANSLITERATION_POSITION] = n.transliterationPosition.value
            p[Keys.LYRIC_UNSUNG_OPACITY] = n.unsungOpacity
            p[Keys.LYRIC_BLUR] = n.blur
            p[Keys.LYRIC_MOVE_DURATION_MS] = n.moveDurationMs
            p[Keys.LYRIC_CURRENT_POSITION] = n.currentLinePosition
            p[Keys.LYRIC_JIGGLE_ENABLED] = n.jiggleEnabled
            p[Keys.LYRIC_JIGGLE_STRENGTH] = n.jiggleStrength
            p[Keys.LYRIC_JIGGLE_DURATION_MS] = n.jiggleDurationMs
            p[Keys.LYRIC_JIGGLE_DELAY_MS] = n.jiggleDelayMs
        }
    }

    fun getBoolean(
        key: Preferences.Key<Boolean>,
        defaultValue: Boolean = false,
    ): Flow<Boolean> =
        context.dataStore.data.map { preferences ->
            preferences[key] ?: defaultValue
        }.distinctUntilChanged()

    suspend fun setBoolean(
        key: Preferences.Key<Boolean>,
        value: Boolean,
    ) {
        context.dataStore.edit { preferences ->
            preferences[key] = value
        }
    }

    fun getInt(
        key: Preferences.Key<Int>,
        defaultValue: Int = 0,
    ): Flow<Int> =
        context.dataStore.data.map { preferences ->
            preferences[key] ?: defaultValue
        }.distinctUntilChanged()

    suspend fun setInt(
        key: Preferences.Key<Int>,
        value: Int,
    ) {
        context.dataStore.edit { preferences ->
            preferences[key] = value
        }
    }

    fun getString(
        key: Preferences.Key<String>,
        defaultValue: String = "",
    ): Flow<String> =
        context.dataStore.data.map { preferences ->
            preferences[key] ?: defaultValue
        }

    suspend fun setString(
        key: Preferences.Key<String>,
        value: String,
    ) {
        context.dataStore.edit { preferences ->
            preferences[key] = value
        }
    }

    fun getFloat(
        key: Preferences.Key<String>,
        defaultValue: Float = 0f,
    ): Flow<Float> =
        context.dataStore.data.map { preferences ->
            preferences[key]?.toFloatOrNull() ?: defaultValue
        }.distinctUntilChanged()

    suspend fun setFloat(
        key: Preferences.Key<String>,
        value: Float,
    ) {
        context.dataStore.edit { preferences ->
            preferences[key] = value.toString()
        }
    }

    fun getFloatList(
        key: Preferences.Key<String>,
        defaultValue: List<Float> = emptyList(),
    ): Flow<List<Float>> =
        context.dataStore.data.map { preferences ->
            val serialized = preferences[key]
            if (serialized.isNullOrEmpty()) {
                defaultValue
            } else {
                serialized.split(",").mapNotNull { it.toFloatOrNull() }.ifEmpty { defaultValue }
            }
        }

    suspend fun setFloatList(
        key: Preferences.Key<String>,
        value: List<Float>,
    ) {
        context.dataStore.edit { preferences ->
            preferences[key] = value.joinToString(",")
        }
    }
}

package me.spica27.spicamusic.ui.widget

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import me.spica27.spicamusic.R
import me.spica27.spicamusic.common.entity.LyricItem
import me.spica27.spicamusic.common.entity.getSentenceContent
import me.spica27.spicamusic.common.entity.voiceAgents
import me.spica27.spicamusic.core.preferences.LyricsStyleSettings
import me.spica27.spicamusic.core.preferences.TransliterationPosition
import me.spica27.spicamusic.ui.theme.Shapes
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ==================== 常量 ====================

/**
 * Left/right edge of the fullscreen lyrics text: clamp(16, 5.4% of the width, 26) dp.
 * The lyrics header uses the same value so header and lyrics line up on both sides.
 */
@Composable
fun rememberLyricsEdgePadding(): Dp {
    val widthDp = LocalConfiguration.current.screenWidthDp
    return (widthDp * 0.054f).coerceIn(16f, 26f).dp
}

private object LyricUIConstants {
    const val EMPTY_WORD_PLACEHOLDER = " · · · "

    const val SEEK_PREVIEW_END_PADDING = 16

    /** Lines other than the current one sit at 95.5% scale, the current line at 100%. */
    const val INACTIVE_SCALE = 0.955f
    const val ACTIVE_SCALE = 1f

    // 行切换动效参数

    /** 位移时长随相邻行间隔在线性范围内变化。 */
    /** Short gaps between lines shorten the move to this fraction of the Move to current line setting. */
    const val LINE_MOVE_SHORT_GAP_FRACTION = 0.8f
    const val LINE_GAP_MIN_MS = 200L
    const val LINE_GAP_MAX_MS = 750L

    /** 当前行下方各行按距离递增延迟，最多错开四行。 */
    const val LINE_STAGGER_UNIT_MS = 25f
    const val LINE_STAGGER_MAX_DISTANCE = 4

    /** 高亮和缩放略晚于位移开始。 */
    const val ACTIVE_SCALE_DELAY_MS = 150
    const val ACTIVE_SCALE_DURATION_MS = 500
    const val INACTIVE_SCALE_DELAY_MS = 50
    const val INACTIVE_SCALE_DURATION_MS = 350
    const val ACTIVE_ALPHA_DELAY_MS = 250
    const val ACTIVE_ALPHA_DURATION_MS = 250
    const val INACTIVE_ALPHA_DELAY_MS = 250
    const val INACTIVE_ALPHA_DURATION_MS = 350

    /** Opacity of a non-current line when Unsung text opacity is at its default (50%). */
    const val INACTIVE_LINE_ALPHA = 0.42f
    const val UNSUNG_DEFAULT_PERCENT = 50f

    /** Blur grows with the distance from the current line: 2.5 dp next to it, +1.2 dp per line, 6 dp at most. */
    const val BLUR_FIRST_LINE_DP = 2.5f
    const val BLUR_STEP_DP = 1.2f
    const val MAX_BLUR_RADIUS = 6f

    /** Text sizes of the reference window (sp, before the size sliders). */
    const val LYRICS_MIN_SP = 27f
    const val LYRICS_MAX_SP = 36f
    const val LYRICS_WIDTH_FRACTION = 0.08f
    const val SECONDARY_SP = 17f
    const val BG_TRANSLATION_SP = 15f
    const val BG_VOCAL_EM = 0.6f
    const val LYRICS_LINE_HEIGHT_EM = 1.17f
    const val LYRICS_LETTER_SPACING_EM = -0.012f
    const val LINE_PADDING_VERTICAL_DP = 9
    const val LINE_SPACING_DP = 8

    const val SEEK_OVERLAY_HIDE_DELAY = 450L

    // 逐字扫描的已唱与未唱透明度

    /** 主句已唱不透明，未唱透明度为 0.21。 */
    const val WORD_SUNG_ALPHA = 1f

    /** 逐字翻译和音译的已唱、未唱透明度。 */
    const val WORD_TRANSLATION_SUNG_ALPHA = 0.92f
    const val WORD_TRANSLATION_UNSUNG_ALPHA = 0.52f
    const val WORD_TRANSLITERATION_SUNG_ALPHA = 0.91f
    const val WORD_TRANSLITERATION_UNSUNG_ALPHA = 0.50f

    /**
     * Background vocals use the same colour as the main lyric (the reference window only dims them to 85%),
     * so the filled text no longer looks like a different colour.
     */
    const val ACCOMPANIMENT_SUNG_ALPHA = 0.85f
    const val ACCOMPANIMENT_SECONDARY_SUNG_ALPHA = 0.70f
    const val ACCOMPANIMENT_SECONDARY_UNSUNG_ALPHA = 0.40f

    /** A background vocal folds open 0.5 s before it starts (or with the line when it starts together with it). */
    const val ACCOMPANIMENT_OPEN_LEAD_MS = 500L

    /** The focus waits for a background vocal that outlasts the next line start, for this long at most. */
    const val ACCOMPANIMENT_HOLD_MAX_MS = 6_000L
    const val ACCOMPANIMENT_NEAR_START_MS = 500L
    const val ACCOMPANIMENT_ABOVE_THRESHOLD_MS = 100L
    const val ACCOMPANIMENT_OPEN_DURATION_MS = 1000
    const val ACCOMPANIMENT_FADE_DURATION_MS = 800
    const val ACCOMPANIMENT_CLOSED_BLUR_DP = 10f
    const val ACCOMPANIMENT_CLOSED_TRANSLATION_BLUR_DP = 8f
    const val ACCOMPANIMENT_TRANSLATION_BLUR_DELAY_MS = 150

    // Instrumental break: three dots that fill up and a countdown (reference window: .ln.gap)
    const val GAP_KEY_PREFIX = "__gap__"
    const val GAP_INTRO_MIN_MS = 5_000L
    const val GAP_BREAK_MIN_MS = 7_000L
    const val GAP_END_LEAD_MS = 300L
    const val GAP_DOT_SIZE_DP = 14
    const val GAP_DOT_SPACING_DP = 9
    const val GAP_DOT_BASE_ALPHA = 0.28f
    const val GAP_BREATH_SCALE = 1.32f
    const val GAP_BREATH_PERIOD_MS = 2400
    const val GAP_BREATH_STAGGER_MS = 260
    const val GAP_VERTICAL_PADDING_DP = 14

    /** 无逐字时间轴的翻译和音译透明度。 */
    const val TRANSLATION_TEXT_ALPHA = 0.72f
    const val TRANSLITERATION_TEXT_ALPHA = 0.7f
    const val ACTIVE_TRANSLATION_ALPHA = 0.85f
    const val INACTIVE_TRANSLATION_ALPHA = 0.8f
    const val RUBY_TEXT_ALPHA = 0.68f

    /** 已唱词整体上抬的距离，当前词按进度渐进上移。 */
    const val WORD_LIFT_DP = -1.5f

    const val WORD_GLOW_ALPHA = 0.3f
    const val WORD_GLOW_BLUR_RADIUS = 16f

    /** 长音词发光和放大时，蒙版上下额外覆盖的像素。 */
    const val MASK_ROW_OVERFLOW_PX = 16f

    const val SLOW_WORD_MIN_DURATION_MS = 1_000L
    const val SLOW_WORD_CHAR_DURATION_THRESHOLD_MS = 200f
    const val SLOW_WORD_ANIMATION_DURATION_RATIO = 0.8f
    const val SLOW_WORD_MAX_SCALE_INCREASE = 0.1f
    const val SLOW_WORD_MAX_DIP = 0.5f
    const val SLOW_WORD_MAX_FLOAT_OFFSET_PX = 4f

    /** 底部新歌词的初始下移距离。 */
    val BOTTOM_FLY_IN_OFFSET = 32.dp

    val KEEP_ALIVE_ZONE = 100.dp
}

// ==================== 行切换时序 ====================

/** CSS `ease`, used by the reference window for opacity and blur. */
private val LyricLineAlphaEasing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

/** The reference window's `--ease`, used for the scale of a line. */
private val LyricLineScaleEasing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

// Becoming the current line: opacity changes at once (its unsung words already sit at the dim of the upcoming
// lines, so nothing dips), scale and blur ease in over 0.5 s / 0.45 s. Leaving it: opacity, blur 0.45 s, scale 0.5 s.
private val ActiveScaleSpec = tween<Float>(durationMillis = 500, easing = LyricLineScaleEasing)
private val InactiveScaleSpec = tween<Float>(durationMillis = 500, easing = LyricLineScaleEasing)
private val ActiveAlphaSpec = tween<Float>(durationMillis = 0)
private val InactiveAlphaSpec = tween<Float>(durationMillis = 450, easing = LyricLineAlphaEasing)
private val LineBlurSpec = tween<Float>(durationMillis = 450, easing = LyricLineAlphaEasing)

/** Delay of a jiggling line `distanceBelow` lines under the current one: grows per line, easing off for far lines. */
private fun jiggleDelayMillis(
    distanceBelow: Int,
    delayBetweenLinesMs: Int,
): Int = (delayBetweenLinesMs * 9f * (1f - kotlin.math.exp(-distanceBelow.coerceAtLeast(0) / 9f))).roundToInt()

/** 记录行切换时机、时长及指定时刻的演唱行。 */
private class LineTimeline private constructor(
    private val lines: List<LyricItem>,
    /** 进入第 i 行的位移时长。 */
    private val durationsMs: IntArray,
    /** 进入第 i 行的位移开始时刻，保持单调递增。 */
    private val transitionStartsMs: LongArray,
    /** 前 i 行的最晚结束时刻，用于缩小扫描范围。 */
    private val maxEndPrefixMs: LongArray,
    /** Move duration used when a line has no entry (the Move to current line setting). */
    private val fallbackMs: Int,
) {
    /** 返回指定时刻对应的行，尚未开始时返回 Int.MAX_VALUE。 */
    fun indexAt(time: Long): Int {
        val index = transitionStartsMs.lastIndexAtOrBefore(time)
        return if (index < 0) Int.MAX_VALUE else index
    }

    fun durationAt(index: Int): Int = durationsMs.getOrElse(index) { fallbackMs }

    /** 返回指定时刻正在演唱的逐字行，支持多行重叠。 */
    fun activeLinesAt(time: Long): Set<Int> {
        var index = lines.indexOfLastStartedAt(time)
        if (index < 0) return emptySet()
        val active = mutableSetOf<Int>()
        while (index >= 0 && maxEndPrefixMs[index] >= time) {
            if (lines[index].isActiveAt(time)) active += index
            index--
        }
        return active
    }

    companion object {
        fun of(
            lines: List<LyricItem>,
            moveMs: Int,
        ): LineTimeline {
            val durations = IntArray(lines.size)
            val starts = LongArray(lines.size)
            val maxEnds = LongArray(lines.size)
            var maxEnd = Long.MIN_VALUE
            for (index in lines.indices) {
                val line = lines[index]
                // 逐字行使用自身结束时间，普通行使用最长时长。
                val previousEnd = (lines.getOrNull(index - 1) as? LyricItem.WordsLyric)?.endTime
                val duration =
                    if (previousEnd ==
                        null
                    ) {
                        moveMs
                    } else {
                        transitionDuration(line.time - previousEnd, moveMs)
                    }
                durations[index] = duration
                // The move starts when the line starts, but never while the previous line's background vocal is
                // still being sung: that line keeps the focus until the vocal is done (at most a few seconds).
                val previousBackgroundEnd =
                    (lines.getOrNull(index - 1) as? LyricItem.WordsLyric)
                        ?.accompaniment
                        ?.maxOfOrNull { it.endTime } ?: Long.MIN_VALUE
                starts[index] =
                    if (previousBackgroundEnd > line.time) {
                        min(previousBackgroundEnd, line.time + LyricUIConstants.ACCOMPANIMENT_HOLD_MAX_MS)
                    } else {
                        line.time
                    }
                // 提前后可能倒序，取单调上界保证二分查找有效。
                if (index > 0 && starts[index] < starts[index - 1]) starts[index] = starts[index - 1]
                maxEnd = max(maxEnd, (line as? LyricItem.WordsLyric)?.endTime ?: Long.MIN_VALUE)
                maxEnds[index] = maxEnd
            }
            return LineTimeline(lines, durations, starts, maxEnds, moveMs)
        }

        /** 根据相邻行间隔计算位移时长。 */
        private fun transitionDuration(
            gapMs: Long,
            moveMs: Int,
        ): Int {
            val gap = gapMs.coerceIn(LyricUIConstants.LINE_GAP_MIN_MS, LyricUIConstants.LINE_GAP_MAX_MS)
            val fraction =
                (gap - LyricUIConstants.LINE_GAP_MIN_MS).toFloat() /
                    (LyricUIConstants.LINE_GAP_MAX_MS - LyricUIConstants.LINE_GAP_MIN_MS)
            return lerp(
                moveMs * LyricUIConstants.LINE_MOVE_SHORT_GAP_FRACTION,
                moveMs.toFloat(),
                fraction,
            ).roundToInt()
        }
    }
}

/** 返回升序数组中最后一个不大于指定值的下标。 */
private fun LongArray.lastIndexAtOrBefore(value: Long): Int {
    var low = 0
    var high = lastIndex
    var result = -1
    while (low <= high) {
        val mid = (low + high) ushr 1
        if (this[mid] <= value) {
            result = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return result
}

/** 返回按开始时刻排序的行列表中最后一个已开始的行。 */
private fun List<LyricItem>.indexOfLastStartedAt(time: Long): Int {
    var low = 0
    var high = lastIndex
    var result = -1
    while (low <= high) {
        val mid = (low + high) ushr 1
        if (this[mid].time <= time) {
            result = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return result
}

// ==================== 显示模式 ====================

/** 歌词展示形态。 */
enum class LyricsDisplayMode {
    Fullscreen,
    Compact,
}

/** 歌词变体显示模式。 */
enum class LyricsVariantDisplayMode {
    First,
    All,
}

/** 歌词显示配置。 */
@Immutable
data class LyricsDisplayOptions(
    val translationMode: LyricsVariantDisplayMode = LyricsVariantDisplayMode.First,
    val transliterationMode: LyricsVariantDisplayMode = LyricsVariantDisplayMode.First,
    val preferredTranslationLanguage: String? = null,
    val preferredTransliterationLanguage: String? = null,
    val showRuby: Boolean = true,
    val showEmptyBeat: Boolean = false,
    val obscureObscene: Boolean = false,
    val showSongPart: Boolean = false,
    val showAgentLabel: Boolean = false,
)

/** 随展示形态变化的排版参数。 */
@Immutable
private data class LyricsUIStyle(
    val mainTextStyle: TextStyle,
    val translationTextStyle: TextStyle,
    val wordsTextStyle: TextStyle,
    val wordsTranslationTextStyle: TextStyle,
    val phoneticTextStyle: TextStyle,
    val rubyTextStyle: TextStyle,
    val accompanimentTextStyle: TextStyle,
    /** Translation under background vocals. */
    val accompanimentTranslationTextStyle: TextStyle,
    val activeScale: Float,
    val itemSpacing: Dp,
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
    /** 逐字扫描前沿的羽化宽度。 */
    val sweepFeather: Dp,
    /** Transliteration above (true) or below (false) the lyric words. */
    val transliterationOnTop: Boolean,
    /** Per-word timed transliteration (true) or a plain static line (false). */
    val attachTransliterationToWords: Boolean,
    /** Unsung text opacity relative to its default (50% = 1.0). */
    val unsungFactor: Float,
)

@Composable
private fun rememberLyricsUIStyle(
    displayMode: LyricsDisplayMode,
    settings: LyricsStyleSettings,
): LyricsUIStyle {
    val typography = MaterialTheme.typography
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    return remember(typography, displayMode, settings, screenWidthDp) {
        val built =
            when (displayMode) {
            LyricsDisplayMode.Fullscreen -> {
                // Same sizes as the reference window: lyrics 8% of the width (27..36), the rest 17 / 15.
                val lyricsSp =
                    (screenWidthDp * LyricUIConstants.LYRICS_WIDTH_FRACTION)
                        .coerceIn(LyricUIConstants.LYRICS_MIN_SP, LyricUIConstants.LYRICS_MAX_SP) *
                        settings.lyricsSize / 100f
                val weight = FontWeight(settings.lyricsWeight)
                val lyricsStyle =
                    typography.headlineMedium.copy(
                        fontSize = lyricsSp.sp,
                        fontWeight = weight,
                        lineHeight = (lyricsSp * LyricUIConstants.LYRICS_LINE_HEIGHT_EM).sp,
                        letterSpacing = (lyricsSp * LyricUIConstants.LYRICS_LETTER_SPACING_EM).sp,
                    )
                val secondary = typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                fun secondaryStyle(
                    baseSp: Float,
                    percent: Int,
                ): TextStyle {
                    val sp = baseSp * percent / 100f
                    return secondary.copy(fontSize = sp.sp, lineHeight = (sp * 1.3f).sp)
                }
                // Lyric edge padding: clamp(16, 5.4% of width, 26); 16 dp already comes from the list itself.
                val edgePad =
                    (screenWidthDp * 0.054f).coerceIn(16f, 26f) - LyricUIConstants.SEEK_PREVIEW_END_PADDING
                LyricsUIStyle(
                    mainTextStyle = lyricsStyle,
                    translationTextStyle = secondaryStyle(LyricUIConstants.SECONDARY_SP, settings.translationSize),
                    wordsTextStyle = lyricsStyle,
                    wordsTranslationTextStyle = secondaryStyle(LyricUIConstants.SECONDARY_SP, settings.translationSize),
                    phoneticTextStyle = secondaryStyle(LyricUIConstants.SECONDARY_SP, settings.transliterationSize),
                    rubyTextStyle = typography.labelSmall.copy(fontSize = 10.sp),
                    accompanimentTextStyle =
                        lyricsStyle.copy(
                            fontSize = (lyricsSp * LyricUIConstants.BG_VOCAL_EM).sp,
                            lineHeight = (lyricsSp * LyricUIConstants.BG_VOCAL_EM * 1.2f).sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    accompanimentTranslationTextStyle =
                        secondaryStyle(LyricUIConstants.BG_TRANSLATION_SP, settings.backgroundTranslationSize),
                    activeScale = LyricUIConstants.ACTIVE_SCALE,
                    itemSpacing = LyricUIConstants.LINE_SPACING_DP.dp,
                    horizontalPadding = edgePad.coerceAtLeast(0f).dp,
                    verticalPadding = LyricUIConstants.LINE_PADDING_VERTICAL_DP.dp,
                    sweepFeather = 30.dp,
                    transliterationOnTop = settings.transliterationPosition == TransliterationPosition.Top,
                    attachTransliterationToWords = settings.attachToWords,
                    unsungFactor = settings.unsungOpacity / LyricUIConstants.UNSUNG_DEFAULT_PERCENT,
                )
            }

            LyricsDisplayMode.Compact -> {
                fun scaled(
                    base: TextStyle,
                    percent: Int,
                ): TextStyle = base.copy(fontSize = base.fontSize * (percent / 100f))
                LyricsUIStyle(
                    mainTextStyle = scaled(typography.titleLarge, settings.lyricsSize),
                    translationTextStyle = scaled(typography.bodyMedium, settings.translationSize),
                    wordsTextStyle =
                        scaled(typography.titleLarge, settings.lyricsSize)
                            .copy(fontWeight = FontWeight(settings.lyricsWeight)),
                    wordsTranslationTextStyle = scaled(typography.bodySmall, settings.translationSize),
                    phoneticTextStyle = scaled(typography.labelSmall, settings.transliterationSize),
                    rubyTextStyle = typography.labelSmall.copy(fontSize = 8.sp),
                    accompanimentTextStyle = typography.bodyMedium,
                    accompanimentTranslationTextStyle = scaled(typography.bodySmall, settings.backgroundTranslationSize),
                    activeScale = 1.06f,
                    itemSpacing = 6.dp,
                    horizontalPadding = 16.dp,
                    verticalPadding = 8.dp,
                    sweepFeather = 18.dp,
                    transliterationOnTop = settings.transliterationPosition == TransliterationPosition.Top,
                    attachTransliterationToWords = settings.attachToWords,
                    unsungFactor = settings.unsungOpacity / LyricUIConstants.UNSUNG_DEFAULT_PERCENT,
                )
            }
            }
        built.withFontFamily(LyricsFontOption.lyricsFamily(settings.lyricsFont))
    }
}

/** Applies the lyrics font to every lyric text style (no-op when the lyrics follow the player font). */
private fun LyricsUIStyle.withFontFamily(family: FontFamily?): LyricsUIStyle =
    if (family == null) {
        this
    } else {
        copy(
            mainTextStyle = mainTextStyle.copy(fontFamily = family),
            translationTextStyle = translationTextStyle.copy(fontFamily = family),
            wordsTextStyle = wordsTextStyle.copy(fontFamily = family),
            wordsTranslationTextStyle = wordsTranslationTextStyle.copy(fontFamily = family),
            phoneticTextStyle = phoneticTextStyle.copy(fontFamily = family),
            rubyTextStyle = rubyTextStyle.copy(fontFamily = family),
            accompanimentTextStyle = accompanimentTextStyle.copy(fontFamily = family),
            accompanimentTranslationTextStyle = accompanimentTranslationTextStyle.copy(fontFamily = family),
        )
    }

// ==================== 主组件 ====================

/** 歌词显示组件，支持逐行、逐字高亮、拖动定位和点按跳转。 */
@Composable
fun LyricsUI(
    modifier: Modifier = Modifier,
    lyric: ImmutableList<LyricItem>,
    currentTimeProvider: () -> Long,
    displayMode: LyricsDisplayMode = LyricsDisplayMode.Fullscreen,
    displayOptions: LyricsDisplayOptions = LyricsDisplayOptions(),
    styleSettings: LyricsStyleSettings = LyricsStyleSettings(),
    isSynced: Boolean = true,
    onSeekToTime: (Long) -> Unit = {},
) {
    val sortedLines = remember(lyric) { lyric.sortedBy { it.time } }

    if (sortedLines.isEmpty()) {
        EmptyLyricState(modifier)
        return
    }

    // 无时间戳歌词静态展示，不高亮、不跟随、不可跳转。
    if (!isSynced) {
        PlainLyricsList(
            modifier = modifier,
            lines = sortedLines,
            displayMode = displayMode,
            displayOptions = displayOptions,
            styleSettings = styleSettings,
        )
        return
    }

    // Long instrumental breaks (and a long intro) get a line of three dots with a countdown, like the reference window.
    val lyricLines = remember(sortedLines) { insertGapLines(sortedLines) }

    val style = rememberLyricsUIStyle(displayMode, styleSettings)
    val lazyListState = rememberLazyListState()
    var isAutoScrolling by remember { mutableStateOf(false) }
    val isManualScrolling by remember {
        derivedStateOf { lazyListState.isScrollInProgress && !isAutoScrolling }
    }
    var showSeekOverlay by remember { mutableStateOf(false) }
    // 首次显示精确定位，后续切换使用动画滚动。
    var hasSyncedInitialPosition by remember(lyricLines) { mutableStateOf(false) }
    // 将时间读取延后到派生状态和绘制阶段，避免驱动整个组件重组。
    val currentTimeProviderState = rememberUpdatedState(currentTimeProvider)
    val currentTimeState =
        remember {
            object : State<Long> {
                override val value: Long
                    get() = currentTimeProviderState.value()
            }
        }
    // 滚动按时间表提前开始，逐字高亮仍使用真实时间。
    val timeline = remember(lyricLines, styleSettings.moveDurationMs) { LineTimeline.of(lyricLines, styleSettings.moveDurationMs) }
    val playingIndex by remember(timeline) {
        derivedStateOf { timeline.indexAt(currentTimeState.value) }
    }
    val activeLineIndices by remember(timeline) {
        derivedStateOf { timeline.activeLinesAt(currentTimeState.value) }
    }
    val voiceIds =
        remember(lyricLines) {
            lyricLines
                .flatMap { it.voiceAgents() }
                .map { it.id }
                .distinct()
        }
    val voiceIndexById =
        remember(voiceIds) {
            voiceIds.withIndex().associate { (index, id) -> id to index }
        }

    // 仅长音词逐字符测量，其余词交给文本排版；关闭测量缓存以隔离字符阴影状态。
    val textMeasurer = rememberTextMeasurer(cacheSize = 0)

    val previewIndex by remember(lyricLines) {
        derivedStateOf {
            val layoutInfo = lazyListState.layoutInfo
            val viewportCenter = (layoutInfo.viewportStartOffset) + (layoutInfo.viewportSize.height / 2)
            val visible = layoutInfo.visibleItemsInfo
            if (visible.isEmpty()) {
                playingIndex
            } else {
                visible
                    .minByOrNull { item ->
                        val itemCenter = item.offset + item.size / 2
                        abs(itemCenter - viewportCenter)
                    }?.index ?: playingIndex
            }
        }
    }

    LaunchedEffect(lazyListState) {
        snapshotFlow { lazyListState.isScrollInProgress }.collectLatest { inProgress ->
            if (inProgress && !isAutoScrolling) {
                // 用户手动定位后，后续切换直接使用动画滚动。
                hasSyncedInitialPosition = true
                showSeekOverlay = true
            } else if (!inProgress) {
                if (!isAutoScrolling) {
                    delay(LyricUIConstants.SEEK_OVERLAY_HIDE_DELAY)
                }
                showSeekOverlay = false
                isAutoScrolling = false
            }
        }
    }

    LookaheadScope {
        BoxWithConstraints(modifier = modifier) {
            val colorScheme = MaterialTheme.colorScheme
            val density = LocalDensity.current
            // 选择线位于视口中央，边缘歌词保留半个视口空白。
            val selectionPadding = with(density) { constraints.maxHeight.toDp() / 2 }
            val playbackAnchorOffsetPx =
                constraints.maxHeight * (styleSettings.currentLinePosition / 100f)
            val bottomFlyInOffsetPx = with(density) { LyricUIConstants.BOTTOM_FLY_IN_OFFSET.roundToPx() }
            val keepAliveZone = LyricUIConstants.KEEP_ALIVE_ZONE
            // Jiggle: lines below the current one spring into place (Lyrics settings > Jiggle effect).
            val jiggleSpec =
                remember(styleSettings.jiggleStrength, styleSettings.jiggleDurationMs, density.density) {
                    JiggleSpec(styleSettings.jiggleStrength, styleSettings.jiggleDurationMs, density.density)
                }
            val keepAliveZonePx = with(density) { keepAliveZone.toPx() }

            LaunchedEffect(playingIndex, showSeekOverlay, lyricLines) {
                val target = playingIndex
                if (showSeekOverlay || target == Int.MAX_VALUE || target !in lyricLines.indices) {
                    return@LaunchedEffect
                }
                isAutoScrolling = true
                try {
                    if (hasSyncedInitialPosition) {
                        lazyListState.syncToLyricIndex(
                            targetIndex = target,
                            anchorOffsetPx = (playbackAnchorOffsetPx + keepAliveZonePx).roundToInt(),
                        )
                    } else {
                        // 首次同步直接精确跳转，避免动画估算误差。
                        lazyListState.snapToLyricIndex(
                            targetIndex = target,
                            anchorOffsetPx = (playbackAnchorOffsetPx + keepAliveZonePx).roundToInt(),
                        )
                    }
                    hasSyncedInitialPosition = true
                } finally {
                    isAutoScrolling = false
                }
            }

            val highlightedIndex = if (showSeekOverlay) previewIndex else playingIndex
            // 当前行切换时长及下方各行的错峰延迟。
            val lineMoveDurationMillis = styleSettings.moveDurationMs

            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clipToBounds()
                        .layout { measurable, constraints ->
                            val extraHeightPx = (keepAliveZone * 2).roundToPx()
                            val placeable =
                                measurable.measure(
                                    constraints.copy(maxHeight = constraints.maxHeight + extraHeightPx),
                                )
                            layout(constraints.maxWidth, constraints.maxHeight) {
                                placeable.place(0, -keepAliveZone.roundToPx())
                            }
                        },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(style.itemSpacing),
                contentPadding =
                    PaddingValues(
                        vertical = selectionPadding + keepAliveZone,
                        horizontal = LyricUIConstants.SEEK_PREVIEW_END_PADDING.dp,
                    ),
                state = lazyListState,
            ) {
                itemsIndexed(
                    items = lyricLines,
                    key = { _, line -> line.key },
                    contentType = { _, line -> line::class },
                ) { index, line ->
                    val isActive =
                        index == highlightedIndex ||
                            (!showSeekOverlay && index in activeLineIndices)
                    val distanceFromActive = if (isActive) 0 else calculateDistance(index, highlightedIndex)
                    val lineAlpha = calculateLineAlpha(distanceFromActive, styleSettings.unsungOpacity)
                    // 动画值仅在图层绘制阶段读取，避免动画期间重组可见行。
                    val scaleState =
                        animateFloatAsState(
                            targetValue = if (distanceFromActive == 0) style.activeScale else LyricUIConstants.INACTIVE_SCALE,
                            label = "lyricScale",
                            animationSpec = if (distanceFromActive == 0) ActiveScaleSpec else InactiveScaleSpec,
                        )
                    val alphaState =
                        animateFloatAsState(
                            targetValue = lineAlpha,
                            label = "lyricAlpha",
                            animationSpec = if (distanceFromActive == 0) ActiveAlphaSpec else InactiveAlphaSpec,
                        )
                    val blurRadius by
                        animateFloatAsState(
                            targetValue = calculateBlurDp(distanceFromActive, styleSettings.blur),
                            animationSpec = LineBlurSpec,
                            label = "lyricBlur",
                        )

                    val isBelowPlaying = playingIndex in lyricLines.indices && index > playingIndex
                    val jiggleLine = styleSettings.jiggleEnabled && isBelowPlaying
                    // Each jiggling line waits a little longer than the one above it; the wait levels off for far lines.
                    val staggerDelayMillis =
                        if (jiggleLine) jiggleDelayMillis(index - playingIndex, styleSettings.jiggleDelayMs) else 0
                    val songPart = line.songPartLabel()
                    val previousSongPart = lyricLines.getOrNull(index - 1)?.songPartLabel()
                    val showSongPart =
                        displayOptions.showSongPart &&
                            !songPart.isNullOrBlank() &&
                            songPart != previousSongPart
                    val placementModifier =
                        Modifier.linePlacement(
                            lookaheadScope = this@LookaheadScope,
                            itemKey = line.key,
                            isManualScrolling = isManualScrolling,
                            durationMillis = lineMoveDurationMillis,
                            delayMillis = staggerDelayMillis,
                            jiggle = if (jiggleLine) jiggleSpec else null,
                            initialOffsetY =
                                if (index > playingIndex) bottomFlyInOffsetPx else 0,
                        )

                    val lineAgents = line.voiceAgents()
                    val lineVoiceIndex = lineAgents.firstOrNull()?.id?.let(voiceIndexById::get)
                    val lineAccent =
                        if (voiceIds.size > 1) voiceAccent(lineVoiceIndex, colorScheme) else null
                    val alignEnd = lineVoiceIndex?.let(::voiceAlignEnd) == true
                    when (line) {
                        is LyricItem.NormalLyric ->
                            LyricLine(
                                lyric = line,
                                isActive = isActive,
                                alphaProvider = { alphaState.value },
                                scaleProvider = { scaleState.value },
                                blurRadius = blurRadius.dp,
                                style = style,
                                voiceAccent = lineAccent,
                                alignEnd = alignEnd,
                                agents = lineAgents,
                                showSongPart = showSongPart,
                                songPart = songPart,
                                displayOptions = displayOptions,
                                modifier = placementModifier,
                            )

                        is LyricItem.WordsLyric ->
                            if (line.isGapLine()) {
                                GapLine(
                                    gap = line,
                                    currentTimeState = currentTimeState,
                                    isActive = isActive,
                                    alphaProvider = { alphaState.value },
                                    scaleProvider = { scaleState.value },
                                    blurRadius = blurRadius.dp,
                                    style = style,
                                    modifier = placementModifier,
                                )
                            } else WordsLyricLine(
                                lyric = line,
                                currentTimeState = currentTimeState,
                                isActive = isActive,
                                alphaProvider = { alphaState.value },
                                scaleProvider = { scaleState.value },
                                blurRadius = blurRadius.dp,
                                style = style,
                                textMeasurer = textMeasurer,
                                agents = lineAgents,
                                voiceAccent = lineAccent,
                                alignEnd = alignEnd,
                                voiceIndexById = voiceIndexById,
                                colorScheme = colorScheme,
                                showSongPart = showSongPart,
                                songPart = songPart,
                                displayOptions = displayOptions,
                                modifier = placementModifier,
                            )
                    }
                }
                item {
                    Spacer(
                        modifier = Modifier.height(200.dp),
                    )
                }
            }

            if (showSeekOverlay) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)),
                )
            }

            AnimatedVisibility(
                modifier = Modifier.align(Alignment.CenterEnd),
                visible = showSeekOverlay && previewIndex in lyricLines.indices,
                enter = materialSharedAxisXIn(true),
                exit = materialSharedAxisXOut(true),
            ) {
                val previewLine = lyricLines.getOrNull(previewIndex)
                if (previewLine != null) {
                    SeekPreview(
                        timeText = formatLyricTime(previewLine.time),
                        onSeek = { onSeekToTime(previewLine.time) },
                    )
                }
            }
        }
    }
}

// ==================== 辅助组件 ====================

/** 展示无时间戳的纯文本歌词。 */
@Composable
private fun PlainLyricsList(
    modifier: Modifier = Modifier,
    lines: List<LyricItem>,
    displayMode: LyricsDisplayMode,
    displayOptions: LyricsDisplayOptions,
    styleSettings: LyricsStyleSettings,
) {
    val style = rememberLyricsUIStyle(displayMode, styleSettings)
    val textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(style.itemSpacing),
        contentPadding =
            PaddingValues(
                horizontal = style.horizontalPadding,
                vertical = style.verticalPadding,
            ),
    ) {
        itemsIndexed(
            items = lines,
            key = { _, line -> line.key },
            contentType = { _, line -> line::class },
        ) { _, line ->
            val content =
                when (line) {
                    is LyricItem.NormalLyric -> line.content
                    is LyricItem.WordsLyric ->
                        line
                            .wordsForDisplay(displayOptions.obscureObscene)
                            .joinToString(separator = "") { it.content }
                            .ifBlank { line.getSentenceContent() }
                }.ifBlank { LyricUIConstants.EMPTY_WORD_PLACEHOLDER }
            Text(
                text = content,
                style = style.mainTextStyle,
                fontWeight = FontWeight.Medium,
                color = textColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 展示空歌词状态。 */
@Composable
private fun EmptyLyricState(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.no_lyrics),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}

// ==================== 普通歌词 ====================

/** 创建歌词行的模糊效果。 */
@Composable
private fun rememberLineBlurEffect(blurRadius: Dp): RenderEffect? {
    val density = LocalDensity.current
    return remember(blurRadius, density) {
        val radiusPx = with(density) { blurRadius.toPx() }
        if (radiusPx > 0f) BlurEffect(radiusPx, radiusPx, TileMode.Decal) else null
    }
}

/** 计算行缩放锚点，固定在文字对齐边的中部。 */
private fun lineTransformOrigin(
    alignEnd: Boolean,
    paddingFraction: Float,
): TransformOrigin =
    TransformOrigin(
        pivotFractionX = if (alignEnd) 1f - paddingFraction else paddingFraction,
        pivotFractionY = 0.5f,
    )

/** 展示普通歌词行。 */
@Composable
private fun LyricLine(
    lyric: LyricItem.NormalLyric,
    isActive: Boolean,
    alphaProvider: () -> Float,
    scaleProvider: () -> Float,
    blurRadius: Dp,
    style: LyricsUIStyle,
    voiceAccent: Color?,
    alignEnd: Boolean,
    agents: List<LyricItem.Agent>,
    showSongPart: Boolean,
    songPart: String?,
    displayOptions: LyricsDisplayOptions,
    modifier: Modifier = Modifier,
) {
    val inactiveTextColor = MaterialTheme.colorScheme.onSurface
    val activeTextColor = voiceAccent ?: MaterialTheme.colorScheme.onSurface
    val horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start
    val textAlign = if (alignEnd) TextAlign.End else TextAlign.Start
    val phoneticBlock: @Composable () -> Unit = {
        if (!lyric.phonetic.isNullOrBlank()) {
            val onTop = style.transliterationOnTop
            if (!onTop) Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = lyric.phonetic!!,
                style = style.phoneticTextStyle,
                color = (if (isActive) activeTextColor else inactiveTextColor).copy(alpha = 0.72f),
                textAlign = textAlign,
                overflow = TextOverflow.Ellipsis,
            )
            if (onTop) Spacer(modifier = Modifier.height(2.dp))
        }
    }
    val blurEffect = rememberLineBlurEffect(blurRadius)

    Column(
        horizontalAlignment = horizontalAlignment,
        modifier =
            modifier
                .fillMaxWidth()
                .graphicsLayer {
                    this.alpha = alphaProvider()
                    val scale = scaleProvider()
                    scaleX = scale
                    scaleY = scale
                    // 绕对齐边缩放，保持对齐边位置不变。
                    transformOrigin =
                        lineTransformOrigin(
                            alignEnd = alignEnd,
                            paddingFraction = if (size.width > 0f) style.horizontalPadding.toPx() / size.width else 0f,
                        )
                    renderEffect = blurEffect
                }.padding(
                    horizontal = style.horizontalPadding,
                    vertical = style.verticalPadding,
                ),
    ) {
        if (showSongPart || (displayOptions.showAgentLabel && agents.hasUsefulLabels())) {
            LineContextLabel(
                songPart = songPart.takeIf { showSongPart },
                agents = agents.takeIf { displayOptions.showAgentLabel },
                textAlign = textAlign,
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        if (style.transliterationOnTop) phoneticBlock()

        Text(
            text = lyric.content.ifBlank { LyricUIConstants.EMPTY_WORD_PLACEHOLDER },
            style = style.mainTextStyle,
            fontWeight = FontWeight.Medium,
            color = if (isActive) activeTextColor else inactiveTextColor,
            textAlign = textAlign,
            overflow = TextOverflow.Ellipsis,
        )

        val translations =
            lyric.translationVariantsFor(
                displayOptions.translationMode,
                displayOptions.preferredTranslationLanguage,
            )
        translations.forEach { translation ->
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = translation.content,
                style = style.translationTextStyle,
                color =
                    if (isActive) {
                        activeTextColor.copy(alpha = LyricUIConstants.ACTIVE_TRANSLATION_ALPHA)
                    } else {
                        inactiveTextColor.copy(alpha = LyricUIConstants.INACTIVE_TRANSLATION_ALPHA)
                    },
                textAlign = textAlign,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (!style.transliterationOnTop) phoneticBlock()
    }
}

// ==================== 逐字歌词 ====================

/** 计算单词播放进度，必要时将终点限制到下一词起点。 */
private fun wordProgress(
    word: LyricItem.WordsLyric.WordWithTiming,
    time: Long,
    endOverride: Long = word.endTime,
): Float {
    val duration = (endOverride - word.startTime).coerceAtLeast(1L)
    return ((time - word.startTime).toFloat() / duration).coerceIn(0f, 1f)
}

private fun slowWordAnimationIntensity(word: LyricItem.WordsLyric.WordWithTiming): Float {
    val characterCount = word.content.length
    val duration = word.endTime - word.startTime
    if (
        characterCount == 0 ||
        duration < LyricUIConstants.SLOW_WORD_MIN_DURATION_MS ||
        duration.toFloat() / characterCount <= LyricUIConstants.SLOW_WORD_CHAR_DURATION_THRESHOLD_MS ||
        word.content.shouldUseSimpleWordAnimation()
    ) {
        return 0f
    }

    return (
        duration -
            LyricUIConstants.SLOW_WORD_CHAR_DURATION_THRESHOLD_MS * characterCount
    ) / 1_000f
}

private fun slowWordScaleAmplitude(word: LyricItem.WordsLyric.WordWithTiming): Float =
    (LyricUIConstants.SLOW_WORD_MAX_SCALE_INCREASE * slowWordAnimationIntensity(word))
        .coerceIn(0f, LyricUIConstants.SLOW_WORD_MAX_SCALE_INCREASE)

private fun slowWordDipAmplitude(word: LyricItem.WordsLyric.WordWithTiming): Float =
    (LyricUIConstants.SLOW_WORD_MAX_DIP * slowWordAnimationIntensity(word))
        .coerceIn(0f, LyricUIConstants.SLOW_WORD_MAX_DIP)

private fun slowWordCharacterAnimationProgress(
    word: LyricItem.WordsLyric.WordWithTiming,
    wordProgress: Float,
    characterIndex: Int,
    characterCount: Int,
): Float {
    if (characterCount == 0) return 1f

    val duration = (word.endTime - word.startTime).coerceAtLeast(1L).toFloat()
    val animationDuration = duration * LyricUIConstants.SLOW_WORD_ANIMATION_DURATION_RATIO
    val characterRatio =
        if (characterCount > 1) characterIndex.toFloat() / (characterCount - 1) else 0.5f
    val startOffset = (duration - animationDuration) * characterRatio
    return ((wordProgress * duration - startOffset) / animationDuration).coerceIn(0f, 1f)
}

private fun slowWordCharacterScale(
    animationProgress: Float,
    amplitude: Float,
): Float {
    if (amplitude <= 0f) return 1f

    // 膨胀曲线为经过三个基准点的抛物线。
    val swell = 4f * amplitude * animationProgress * (1f - animationProgress)
    return 1f + swell
}

private fun slowWordCharacterFloatOffset(
    animationProgress: Float,
    dipAmplitude: Float,
): Float {
    if (dipAmplitude <= 0f) return 0f

    // 下沉曲线按反向时间计算，经过三个基准点。
    val reversedProgress = 1f - animationProgress
    val dipAndRise =
        (2f + 4f * dipAmplitude) * reversedProgress * reversedProgress -
            (1f + 4f * dipAmplitude) * reversedProgress
    return LyricUIConstants.SLOW_WORD_MAX_FLOAT_OFFSET_PX * dipAndRise
}

/** 判断去除空白和标点后各字符所属的书写系统。 */
private fun String.significantScripts(): List<Character.UnicodeScript> =
    filterNot { it.isWhitespace() || it.isLyricPunctuation() }
        .map { Character.UnicodeScript.of(it.code) }

private val CjkScripts =
    setOf(
        Character.UnicodeScript.HAN,
        Character.UnicodeScript.HIRAGANA,
        Character.UnicodeScript.KATAKANA,
    )

private fun String.shouldUseSimpleWordAnimation(): Boolean {
    val scripts = significantScripts()
    if (scripts.isEmpty()) return false

    val isPureCjk = scripts.all { it in CjkScripts || it == Character.UnicodeScript.HANGUL }
    return isPureCjk ||
        scripts.any {
            it == Character.UnicodeScript.ARABIC || it == Character.UnicodeScript.DEVANAGARI
        }
}

private fun Char.isLyricPunctuation(): Boolean =
    when (Character.getType(this)) {
        Character.CONNECTOR_PUNCTUATION.toInt(),
        Character.DASH_PUNCTUATION.toInt(),
        Character.END_PUNCTUATION.toInt(),
        Character.FINAL_QUOTE_PUNCTUATION.toInt(),
        Character.INITIAL_QUOTE_PUNCTUATION.toInt(),
        Character.OTHER_PUNCTUATION.toInt(),
        Character.START_PUNCTUATION.toInt(),
        -> true

        else -> false
    }

// ==================== 逐字扫描蒙版 ====================
// 词文本保持静态，由行图层在内容绘制后叠加横向渐变蒙版表示播放进度。

/** 记录逐字文本块当前词下标和词内进度。 */
@JvmInline
private value class SweepPosition(
    val packed: Long,
) {
    val wordIndex: Int get() = (packed shr 32).toInt()
    val progress: Float get() = Float.fromBits(packed.toInt())

    /** 返回指定词的播放比例：已唱为 1，未唱为 0。 */
    fun progressOf(index: Int): Float =
        when {
            index < wordIndex -> 1f
            index == wordIndex -> progress
            else -> 0f
        }

    companion object {
        fun of(
            wordIndex: Int,
            progress: Float,
        ): SweepPosition =
            SweepPosition(
                (wordIndex.toLong() shl 32) or (progress.toRawBits().toLong() and 0xFFFF_FFFFL),
            )

        val Before = of(-1, 0f)
    }
}

/** 计算按开始时间排序的词列表在指定时刻的扫描位置。 */
private fun List<LyricItem.WordsLyric.WordWithTiming>.sweepAt(time: Long): SweepPosition {
    var low = 0
    var high = lastIndex
    var index = -1
    while (low <= high) {
        val mid = (low + high) ushr 1
        if (this[mid].startTime <= time) {
            index = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    if (index < 0) return SweepPosition.Before
    val word = this[index]
    // 终点限制到下一词起点，避免时间重叠时收尾跳变。
    val endOverride = if (index < lastIndex) minOf(word.endTime, this[index + 1].startTime) else word.endTime
    return SweepPosition.of(index, wordProgress(word, time, endOverride))
}

/** 登记蒙版宿主中的文本块和长音发光项。 */
private class SweepMaskRegistry(
    val litFraction: State<Float>,
) {
    var hostCoordinates: LayoutCoordinates? = null

    /** 文本块列表变化时触发宿主重绘。 */
    val blocks = mutableStateListOf<MaskBlock>()

    /** 长音词发光在扫描蒙版后单独叠加。 */
    val glows = mutableStateListOf<SlowWordGlow>()
}

/** 将当前节点注册为蒙版宿主，在内容绘制后叠加扫描蒙版。 */
private fun Modifier.sweepMaskHost(
    registry: SweepMaskRegistry,
    featherPx: Float,
): Modifier =
    onPlaced { registry.hostCoordinates = it }
        .drawWithContent {
            drawContent()
            drawSweepMasks(registry, featherPx)
            // 发光置于扫描蒙版之后，保持整词均匀晕开。
            drawSlowWordGlows(registry)
        }

/** 判断两个词是否属于同一视觉行。 */
private fun Rect.isOnSameRowAs(next: Rect): Boolean = next.top < top + height / 2f

private class MaskBlock(
    val sweep: State<SweepPosition>,
    /** 未唱部分相对已唱部分的透明度比例。 */
    val dimAlpha: Float,
    /** 首行和末行额外覆盖的像素。 */
    val rowOverflow: Float,
    wordCount: Int,
) {
    val wordCoordinates = arrayOfNulls<LayoutCoordinates>(wordCount)

    fun rectOf(
        hostCoordinates: LayoutCoordinates,
        index: Int,
    ): Rect? {
        val coordinates = wordCoordinates[index]?.takeIf { it.isAttached } ?: return null
        return hostCoordinates.localBoundingBoxOf(coordinates, clipBounds = false)
    }
}

/** 在宿主图层内按视觉行绘制逐字扫描蒙版。 */
private fun DrawScope.drawSweepMasks(
    registry: SweepMaskRegistry,
    feather: Float,
) {
    val hostCoordinates = registry.hostCoordinates?.takeIf { it.isAttached } ?: return
    val width = size.width
    val litFraction = registry.litFraction.value
    for (block in registry.blocks) {
        val sweep = block.sweep.value
        // A line that is not current shows every word at full strength (the line's own opacity dims it).
        // The current line dims only its not-yet-sung words, down to the Unsung text opacity.
        val dimColor = Color.Black.copy(alpha = 1f - (1f - block.dimAlpha) * litFraction)
        val litAlpha = 1f
        val litColor = Color.Black.copy(alpha = litAlpha)
        val wordCount = block.wordCoordinates.size
        var rowStart = 0
        while (rowStart < wordCount) {
            val firstRect = block.rectOf(hostCoordinates, rowStart)
            if (firstRect == null) {
                rowStart++
                continue
            }
            var rowBottom = firstRect.bottom
            var lastRect = firstRect
            var rowEnd = rowStart + 1
            while (rowEnd < wordCount) {
                val rect = block.rectOf(hostCoordinates, rowEnd) ?: break
                if (!firstRect.isOnSameRowAs(rect)) break
                if (rect.bottom > rowBottom) rowBottom = rect.bottom
                lastRect = rect
                rowEnd++
            }
            val top = if (rowStart == 0) firstRect.top - block.rowOverflow else firstRect.top
            val bottom = if (rowEnd == wordCount) rowBottom + block.rowOverflow else rowBottom
            val rowHeight = bottom - top
            val rowTopLeft = Offset(0f, top)
            val rowSize = Size(width, rowHeight)
            when {
                sweep.wordIndex < rowStart ->
                    drawRect(dimColor, rowTopLeft, rowSize, blendMode = BlendMode.DstIn)

                // 扫描位置越过整行后，整行显示为已唱状态。
                sweep.wordIndex >= rowEnd ->
                    if (litAlpha < 1f) drawRect(litColor, rowTopLeft, rowSize, blendMode = BlendMode.DstIn)

                else -> {
                    val word = block.rectOf(hostCoordinates, sweep.wordIndex) ?: firstRect
                    // 根据词序和阅读方向确定扫描方向。
                    val leftToRight = lastRect.left >= firstRect.left
                    val progress = sweep.progress
                    val wordWidth = word.right - word.left
                    // 分别绘制已唱词、当前词渐变和未唱词，三段互不重叠。
                    if (leftToRight) {
                        if (word.left > 0f) {
                            drawRect(litColor, Offset(0f, top), Size(word.left, rowHeight), blendMode = BlendMode.DstIn)
                        }
                        val front = word.left + (wordWidth + feather) * progress
                        drawRect(
                            brush =
                                Brush.horizontalGradient(
                                    colors = listOf(litColor, dimColor),
                                    startX = front - feather,
                                    endX = front,
                                ),
                            topLeft = Offset(word.left, top),
                            size = Size(wordWidth, rowHeight),
                            blendMode = BlendMode.DstIn,
                        )
                        if (word.right < width) {
                            drawRect(dimColor, Offset(word.right, top), Size(width - word.right, rowHeight), blendMode = BlendMode.DstIn)
                        }
                    } else {
                        if (word.right < width) {
                            drawRect(
                                litColor,
                                Offset(word.right, top),
                                Size(width - word.right, rowHeight),
                                blendMode = BlendMode.DstIn,
                            )
                        }
                        val front = word.right - (wordWidth + feather) * progress
                        drawRect(
                            brush =
                                Brush.horizontalGradient(
                                    colors = listOf(dimColor, litColor),
                                    startX = front,
                                    endX = front + feather,
                                ),
                            topLeft = Offset(word.left, top),
                            size = Size(wordWidth, rowHeight),
                            blendMode = BlendMode.DstIn,
                        )
                        if (word.left > 0f) {
                            drawRect(dimColor, Offset(0f, top), Size(word.left, rowHeight), blendMode = BlendMode.DstIn)
                        }
                    }
                }
            }
            rowStart = rowEnd
        }
    }
}

/** 获取单词在文本布局中的边界框。 */
private fun wordBoundingBox(
    layout: TextLayoutResult,
    start: Int,
    end: Int,
): Rect {
    val safeStart = start.coerceIn(0, layout.layoutInput.text.length)
    val safeEnd = end.coerceIn(safeStart, layout.layoutInput.text.length)
    if (safeEnd <= safeStart) return Rect.Zero

    var rect = layout.getBoundingBox(safeStart)
    for (index in (safeStart + 1) until safeEnd) {
        val box = layout.getBoundingBox(index)
        rect =
            Rect(
                left = min(rect.left, box.left),
                top = min(rect.top, box.top),
                right = max(rect.right, box.right),
                bottom = max(rect.bottom, box.bottom),
            )
    }
    return rect
}

// ==================== 间奏倒计时 ====================

private fun LyricItem.isGapLine(): Boolean = key.startsWith(LyricUIConstants.GAP_KEY_PREFIX)

/** Latest moment at which this line (or one of its background vocals) is still being sung. */
private fun LyricItem.WordsLyric.lastSungMs(): Long = max(endTime, accompaniment.maxOfOrNull { it.endTime } ?: Long.MIN_VALUE)

private fun gapLineOf(
    startMs: Long,
    endMs: Long,
    ordinal: Int,
): LyricItem.WordsLyric =
    LyricItem.WordsLyric(
        words = emptyList(),
        translation = emptyList(),
        startTime = startMs,
        endTime = max(startMs, endMs),
        key = "${LyricUIConstants.GAP_KEY_PREFIX}$ordinal",
    )

/**
 * Adds a gap line at the start when the first lyric comes after at least 5 s, and between two lines when
 * nobody sings for at least 7 s. The gap ends 0.3 s before the next lyric (as in the reference window).
 */
private fun insertGapLines(lines: List<LyricItem>): List<LyricItem> {
    if (lines.isEmpty()) return lines
    val result = ArrayList<LyricItem>(lines.size + 4)
    var ordinal = 0
    val first = lines.first()
    if (first.time >= LyricUIConstants.GAP_INTRO_MIN_MS) {
        result += gapLineOf(0L, first.time - LyricUIConstants.GAP_END_LEAD_MS, ordinal++)
    }
    var latestEnd = Long.MIN_VALUE
    lines.forEachIndexed { index, line ->
        result += line
        if (line is LyricItem.WordsLyric) latestEnd = max(latestEnd, line.lastSungMs())
        val next = lines.getOrNull(index + 1)
        if (next != null && latestEnd != Long.MIN_VALUE && next.time - latestEnd >= LyricUIConstants.GAP_BREAK_MIN_MS) {
            result += gapLineOf(latestEnd, next.time - LyricUIConstants.GAP_END_LEAD_MS, ordinal++)
        }
    }
    return result
}

/** One of the three dots: fills up with the break's progress and breathes while the break is the current line. */
@Composable
private fun GapDot(
    index: Int,
    progressState: State<Float>,
    breathing: Boolean,
    color: Color,
) {
    val breath =
        if (breathing) {
            val transition = rememberInfiniteTransition(label = "gapBreath$index")
            transition.animateFloat(
                initialValue = 1f,
                targetValue = 1f,
                animationSpec =
                    infiniteRepeatable(
                        animation =
                            keyframes {
                                durationMillis = LyricUIConstants.GAP_BREATH_PERIOD_MS
                                1f at 0
                                LyricUIConstants.GAP_BREATH_SCALE at (LyricUIConstants.GAP_BREATH_PERIOD_MS / 2) using FastOutSlowInEasing
                                1f at LyricUIConstants.GAP_BREATH_PERIOD_MS using FastOutSlowInEasing
                            },
                        repeatMode = RepeatMode.Restart,
                        initialStartOffset = StartOffset(index * LyricUIConstants.GAP_BREATH_STAGGER_MS, StartOffsetType.Delay),
                    ),
                label = "gapBreathScale$index",
            )
        } else {
            null
        }
    Canvas(
        modifier =
            Modifier
                .size(LyricUIConstants.GAP_DOT_SIZE_DP.dp)
                .graphicsLayer {
                    val scale = breath?.value ?: 1f
                    scaleX = scale
                    scaleY = scale
                },
    ) {
        // 0 = not reached yet (dim), 1 = lit; each dot takes a third of the break.
        val lit = (progressState.value * 3f - index).coerceIn(0f, 1f)
        val alpha = LyricUIConstants.GAP_DOT_BASE_ALPHA + (1f - LyricUIConstants.GAP_DOT_BASE_ALPHA) * lit
        drawCircle(color = color.copy(alpha = alpha))
    }
}

/** An instrumental break: three dots that fill up, and the seconds left until the next lyric. */
@Composable
private fun GapLine(
    gap: LyricItem.WordsLyric,
    currentTimeState: State<Long>,
    isActive: Boolean,
    alphaProvider: () -> Float,
    scaleProvider: () -> Float,
    blurRadius: Dp,
    style: LyricsUIStyle,
    modifier: Modifier = Modifier,
) {
    val color = MaterialTheme.colorScheme.onSurface
    val lengthMs = max(100L, gap.endTime - gap.startTime)
    val progressState =
        remember(gap, currentTimeState) {
            derivedStateOf {
                ((currentTimeState.value - gap.startTime).toFloat() / lengthMs).coerceIn(0f, 1f)
            }
        }
    // Whole seconds until the next lyric. When the break is not current it shows the full length again.
    val fullSeconds = remember(gap) { secondsLeft(gap, gap.startTime) }
    val seconds by remember(gap, isActive, currentTimeState) {
        derivedStateOf { if (isActive) secondsLeft(gap, currentTimeState.value) else fullSeconds }
    }
    val blurEffect = rememberLineBlurEffect(blurRadius)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LyricUIConstants.GAP_DOT_SPACING_DP.dp),
        modifier =
            modifier
                .fillMaxWidth()
                .graphicsLayer {
                    this.alpha = alphaProvider()
                    val scale = scaleProvider()
                    scaleX = scale
                    scaleY = scale
                    transformOrigin =
                        lineTransformOrigin(
                            alignEnd = false,
                            paddingFraction = if (size.width > 0f) style.horizontalPadding.toPx() / size.width else 0f,
                        )
                    renderEffect = blurEffect
                }.padding(
                    horizontal = style.horizontalPadding,
                    vertical = LyricUIConstants.GAP_VERTICAL_PADDING_DP.dp,
                ),
    ) {
        repeat(3) { index ->
            GapDot(index = index, progressState = progressState, breathing = isActive, color = color)
        }
        AnimatedContent(
            targetState = seconds,
            transitionSpec = {
                // Each new number rolls up into place while the old one lifts away.
                (
                    fadeIn(tween(600, easing = LyricLineScaleEasing)) +
                        slideInVertically(tween(600, easing = LyricLineScaleEasing)) { it / 2 }
                ) togetherWith
                    (
                        fadeOut(tween(450, easing = LyricLineScaleEasing)) +
                            slideOutVertically(tween(450, easing = LyricLineScaleEasing)) { -it / 2 }
                    )
            },
            modifier = Modifier.padding(start = 2.dp),
            label = "gapCountdown",
        ) { value ->
            Text(
                text = value.toString(),
                style =
                    MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontFeatureSettings = "tnum",
                    ),
                color = color,
            )
        }
    }
}

private fun secondsLeft(
    gap: LyricItem.WordsLyric,
    timeMs: Long,
): Int =
    ceil((gap.endTime + LyricUIConstants.GAP_END_LEAD_MS - timeMs) / 1000.0)
        .toInt()
        .coerceAtLeast(1)

/** 展示支持单词级进度的逐字歌词行。 */
@Composable
private fun WordsLyricLine(
    lyric: LyricItem.WordsLyric,
    currentTimeState: State<Long>,
    isActive: Boolean,
    alphaProvider: () -> Float,
    scaleProvider: () -> Float,
    blurRadius: Dp,
    style: LyricsUIStyle,
    textMeasurer: TextMeasurer,
    agents: List<LyricItem.Agent>,
    voiceAccent: Color?,
    alignEnd: Boolean,
    voiceIndexById: Map<String, Int>,
    colorScheme: ColorScheme,
    showSongPart: Boolean,
    songPart: String?,
    displayOptions: LyricsDisplayOptions,
    modifier: Modifier = Modifier,
) {
    val textColor = voiceAccent ?: colorScheme.onSurface
    val translationColor = textColor.copy(alpha = LyricUIConstants.TRANSLATION_TEXT_ALPHA)
    val transliterationColor = textColor.copy(alpha = LyricUIConstants.TRANSLITERATION_TEXT_ALPHA)
    val horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start
    val textAlign = if (alignEnd) TextAlign.End else TextAlign.Start
    val renderWords =
        remember(lyric, displayOptions.obscureObscene) {
            lyric.wordsForDisplay(displayOptions.obscureObscene)
        }
    val sentence =
        remember(renderWords, lyric.content) {
            renderWords
                .joinToString(separator = "") { it.content }
                .ifBlank { lyric.getSentenceContent() }
        }
    val mainStartMs = remember(lyric) { lyric.words.minOfOrNull { it.startTime } ?: lyric.startTime }
    // A background vocal that starts clearly before the main lyric (or exactly with it) sits above it, otherwise below.
    val (aboveAccompaniment, belowAccompaniment) =
        remember(lyric, sentence) {
            lyric.accompaniment
                .sortedBy { it.startTime }
                .partition { sentence.isNotBlank() && it.isAboveMain(mainStartMs) }
        }

    // 已唱侧随当前行进入和退出渐变。
    val litFractionState =
        animateFloatAsState(
            targetValue = if (isActive) 1f else 0f,
            label = "lyricSungLit",
            animationSpec = if (isActive) ActiveAlphaSpec else InactiveAlphaSpec,
        )
    // 整行在同一个离屏图层中完成透明度、缩放、模糊和扫描。
    val maskRegistry = remember(litFractionState) { SweepMaskRegistry(litFractionState) }
    val transliterations =
        lyric.transliterations.visibleTransliterations(
            displayOptions.transliterationMode,
            displayOptions.preferredTransliterationLanguage,
        )
    // "Attach to words" on: the first transliteration is split over the words and sits right above (or under) each one.
    val attachedTransliteration =
        remember(renderWords, transliterations, style.attachTransliterationToWords) {
            if (style.attachTransliterationToWords) {
                transliterations.firstOrNull()?.let { mapTransliterationToWords(renderWords, it) }
            } else {
                null
            }
        }
    val plainTransliterations = if (attachedTransliteration != null) transliterations.drop(1) else transliterations
    // Off: the transliteration is one plain line above (or under) the whole lyric line.
    val transliterationBlock: @Composable () -> Unit = {
        val onTop = style.transliterationOnTop
        if (plainTransliterations.isNotEmpty()) {
            plainTransliterations.forEach { transliteration ->
                if (!onTop) Spacer(modifier = Modifier.height(2.dp))
                TranslationLine(
                    translation = transliteration,
                    currentTimeState = currentTimeState,
                    style = style,
                    color = transliterationColor,
                    sungAlpha = LyricUIConstants.WORD_TRANSLITERATION_SUNG_ALPHA,
                    unsungAlpha =
                        (LyricUIConstants.WORD_TRANSLITERATION_UNSUNG_ALPHA * style.unsungFactor)
                            .coerceIn(0f, LyricUIConstants.WORD_TRANSLITERATION_SUNG_ALPHA),
                    textAlign = textAlign,
                    maskRegistry = maskRegistry,
                    textMeasurer = textMeasurer,
                    displayOptions = displayOptions,
                    textStyle = style.phoneticTextStyle,
                    timed = false,
                )
                if (onTop) Spacer(modifier = Modifier.height(2.dp))
            }
        } else if (transliterations.isEmpty() && !lyric.phonetic.isNullOrBlank()) {
            if (!onTop) Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = lyric.phonetic!!,
                style = style.phoneticTextStyle,
                color = transliterationColor,
                textAlign = textAlign,
                overflow = TextOverflow.Ellipsis,
            )
            if (onTop) Spacer(modifier = Modifier.height(2.dp))
        }
    }
    val blurEffect = rememberLineBlurEffect(blurRadius)
    val sweepFeatherPx = with(LocalDensity.current) { style.sweepFeather.toPx() }
    Column(
        horizontalAlignment = horizontalAlignment,
        modifier =
            modifier
                .fillMaxWidth()
                .graphicsLayer {
                    this.alpha = alphaProvider()
                    val scale = scaleProvider()
                    scaleX = scale
                    scaleY = scale
                    // 绕对齐边缩放，保持对齐边位置不变。
                    transformOrigin =
                        lineTransformOrigin(
                            alignEnd = alignEnd,
                            paddingFraction = if (size.width > 0f) style.horizontalPadding.toPx() / size.width else 0f,
                        )
                    renderEffect = blurEffect
                    // 扫描蒙版只作用于本行离屏缓冲，避免穿透背景。
                    compositingStrategy = CompositingStrategy.Offscreen
                }.sweepMaskHost(maskRegistry, sweepFeatherPx)
                .padding(
                    horizontal = style.horizontalPadding,
                    vertical = style.verticalPadding,
                ),
    ) {
        if (showSongPart || (displayOptions.showAgentLabel && agents.hasUsefulLabels())) {
            LineContextLabel(
                songPart = songPart.takeIf { showSongPart },
                agents = agents.takeIf { displayOptions.showAgentLabel },
                textAlign = textAlign,
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        aboveAccompaniment.forEach { background ->
            AccompanimentLine(
                background = background,
                currentTimeState = currentTimeState,
                litFraction = litFractionState,
                mainStartMs = mainStartMs,
                style = style,
                textMeasurer = textMeasurer,
                parentAgents = agents,
                voiceIndexById = voiceIndexById,
                colorScheme = colorScheme,
                fallbackAccent = voiceAccent,
                fallbackAlignEnd = alignEnd,
                displayOptions = displayOptions,
            )
        }

        if (style.transliterationOnTop) transliterationBlock()

        if (sentence.isNotBlank()) {
            ProgressiveWordsText(
                text = sentence,
                words = renderWords,
                currentTimeState = currentTimeState,
                textStyle = style.wordsTextStyle,
                color = textColor,
                sungAlpha = LyricUIConstants.WORD_SUNG_ALPHA,
                unsungAlpha = (LyricUIConstants.INACTIVE_LINE_ALPHA * style.unsungFactor).coerceIn(0f, 1f),
                maskRegistry = maskRegistry,
                textMeasurer = textMeasurer,
                modifier = Modifier.fillMaxWidth(),
                alignEnd = alignEnd,
                rubyTextStyle = style.rubyTextStyle,
                showRuby = displayOptions.showRuby,
                showEmptyBeat = displayOptions.showEmptyBeat,
                attachedTexts = attachedTransliteration,
                attachedStyle = style.phoneticTextStyle,
                attachedColor = transliterationColor,
                attachedOnTop = style.transliterationOnTop,
            )
        }

        // Order: main lyric, background vocal (with its own translation underneath), transliteration, main translation.
        belowAccompaniment.forEach { background ->
            AccompanimentLine(
                background = background,
                currentTimeState = currentTimeState,
                litFraction = litFractionState,
                mainStartMs = mainStartMs,
                style = style,
                textMeasurer = textMeasurer,
                parentAgents = agents,
                voiceIndexById = voiceIndexById,
                colorScheme = colorScheme,
                fallbackAccent = voiceAccent,
                fallbackAlignEnd = alignEnd,
                displayOptions = displayOptions,
            )
        }

        if (!style.transliterationOnTop) transliterationBlock()

        val translations =
            lyric.translationVariantsFor(
                displayOptions.translationMode,
                displayOptions.preferredTranslationLanguage,
            )
        translations.forEach { translation ->
            Spacer(modifier = Modifier.height(3.dp))
            TranslationLine(
                translation = translation,
                currentTimeState = currentTimeState,
                style = style,
                color = translationColor,
                sungAlpha = LyricUIConstants.WORD_TRANSLATION_SUNG_ALPHA,
                unsungAlpha =
                    (LyricUIConstants.WORD_TRANSLATION_UNSUNG_ALPHA * style.unsungFactor)
                        .coerceIn(0f, LyricUIConstants.WORD_TRANSLATION_SUNG_ALPHA),
                textAlign = textAlign,
                maskRegistry = maskRegistry,
                textMeasurer = textMeasurer,
                displayOptions = displayOptions,
            )
        }
    }
}

@Composable
private fun LineContextLabel(
    songPart: String?,
    agents: List<LyricItem.Agent>?,
    textAlign: TextAlign,
) {
    val labels =
        buildList {
            songPart?.takeIf(String::isNotBlank)?.let(::add)
            agents
                ?.map { it.label }
                ?.filter(String::isNotBlank)
                ?.distinct()
                ?.joinToString(" / ")
                ?.takeIf(String::isNotBlank)
                ?.let(::add)
        }
    if (labels.isEmpty()) return
    Text(
        text = labels.joinToString(" / "),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
        textAlign = textAlign,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun List<LyricItem.Agent>.hasUsefulLabels(): Boolean = size > 1 || any { !it.name.isNullOrBlank() }

private fun LyricItem.songPartLabel(): String? =
    when (this) {
        is LyricItem.NormalLyric -> songPart
        is LyricItem.WordsLyric -> songPart
    }

private fun LyricItem.WordsLyric.WordWithTiming.displayContent(obscureObscene: Boolean): String =
    if (!obscureObscene || !obscene) {
        content
    } else {
        content.map { character -> if (character.isWhitespace()) character else '*' }.joinToString("")
    }

private fun List<LyricItem.WordsLyric.WordWithTiming>.displayWordsWithFullText(
    fullText: String?,
    obscureObscene: Boolean,
): List<LyricItem.WordsLyric.WordWithTiming> {
    val sorted = sortedBy { it.startTime }
    val normalizedText =
        fullText?.takeIf(String::isNotBlank)
            ?: return sorted.map { it.displayCopy(obscureObscene) }
    if (sorted.isEmpty()) return sorted

    val result = mutableListOf<LyricItem.WordsLyric.WordWithTiming>()
    var cursor = 0
    var matched = false
    var pendingPrefix = ""
    sorted.forEach { original ->
        val raw = original.content
        val trimmed = raw.trim()
        val match =
            normalizedText.indexOf(raw, cursor).takeIf { it >= cursor }
                ?: normalizedText.indexOf(trimmed, cursor).takeIf { it >= cursor }
        if (match != null) {
            matched = true
            val gap = normalizedText.substring(cursor, match)
            if (gap.isNotEmpty()) {
                if (result.isNotEmpty()) {
                    val previous = result.last()
                    result[result.lastIndex] =
                        previous.copy(
                            content = previous.content + gap,
                            endsWithSpace = previous.endsWithSpace || gap.last().isWhitespace(),
                        )
                } else {
                    pendingPrefix += gap
                }
            }
            cursor = match + if (normalizedText.startsWith(raw, match)) raw.length else trimmed.length
        }
        val display = original.displayCopy(obscureObscene)
        result += display.copy(content = pendingPrefix + display.content)
        pendingPrefix = ""
    }
    if (matched && cursor < normalizedText.length && result.isNotEmpty()) {
        val tail = normalizedText.substring(cursor)
        val previous = result.last()
        result[result.lastIndex] =
            previous.copy(
                content = previous.content + tail,
                endsWithSpace = previous.endsWithSpace || tail.last().isWhitespace(),
            )
    }
    return if (matched) result else sorted.map { it.displayCopy(obscureObscene) }
}

private fun LyricItem.WordsLyric.wordsForDisplay(obscureObscene: Boolean): List<LyricItem.WordsLyric.WordWithTiming> =
    words.displayWordsWithFullText(content, obscureObscene)

private fun LyricItem.WordsLyric.WordWithTiming.displayCopy(obscureObscene: Boolean): LyricItem.WordsLyric.WordWithTiming {
    val visibleContent = displayContent(obscureObscene)
    return copy(
        content = if (endsWithSpace && !visibleContent.endsWith(' ')) "$visibleContent " else visibleContent,
        ruby = if (obscureObscene && obscene) emptyList() else ruby,
    )
}

/** 首选语言优先，再按显示模式截取列表。 */
private fun List<LyricItem.WordsLyric.Translation>.orderedForDisplay(
    mode: LyricsVariantDisplayMode,
    preferredLanguage: String?,
): List<LyricItem.WordsLyric.Translation> {
    val ordered =
        if (preferredLanguage.isNullOrBlank()) {
            this
        } else {
            sortedBy { if (it.lang.equals(preferredLanguage, ignoreCase = true)) 0 else 1 }
        }
    return if (mode == LyricsVariantDisplayMode.All) ordered else ordered.take(1)
}

private fun List<LyricItem.WordsLyric.Translation>.visibleVariants(
    mode: LyricsVariantDisplayMode,
    includeBackground: Boolean = false,
    preferredLanguage: String? = null,
): List<LyricItem.WordsLyric.Translation> =
    asSequence()
        .filter { it.content.isNotBlank() || it.words.isNotEmpty() }
        .filter { includeBackground || !it.isBackground }
        .distinct()
        .toList()
        .orderedForDisplay(mode, preferredLanguage)

/** 获取主句和伴唱共用的音译列表。 */
private fun List<LyricItem.WordsLyric.Translation>.visibleTransliterations(
    mode: LyricsVariantDisplayMode,
    preferredLanguage: String?,
): List<LyricItem.WordsLyric.Translation> =
    asSequence()
        .filter { it.content.isNotBlank() || it.words.isNotEmpty() }
        .distinct()
        .toList()
        .orderedForDisplay(mode, preferredLanguage)

private fun LyricItem.NormalLyric.translationVariantsFor(
    mode: LyricsVariantDisplayMode,
    preferredLanguage: String? = null,
): List<LyricItem.WordsLyric.Translation> {
    val variants =
        translationVariants
            .ifEmpty {
                translation
                    ?.takeIf(String::isNotBlank)
                    ?.let { listOf(LyricItem.WordsLyric.Translation(content = it)) }
                    .orEmpty()
            }
    return variants.visibleVariants(mode, preferredLanguage = preferredLanguage)
}

private fun LyricItem.WordsLyric.translationVariantsFor(
    mode: LyricsVariantDisplayMode,
    preferredLanguage: String? = null,
): List<LyricItem.WordsLyric.Translation> =
    (translationVariants.ifEmpty { translation }).visibleVariants(mode, preferredLanguage = preferredLanguage)

private fun LyricItem.WordsLyric.AccompanimentLyric.translationVariantsFor(
    mode: LyricsVariantDisplayMode,
    preferredLanguage: String? = null,
): List<LyricItem.WordsLyric.Translation> =
    (translationVariants.ifEmpty { translation }).visibleVariants(
        mode,
        includeBackground = true,
        preferredLanguage = preferredLanguage,
    )

@Composable
private fun TranslationLine(
    translation: LyricItem.WordsLyric.Translation,
    currentTimeState: State<Long>,
    style: LyricsUIStyle,
    /** 无逐字时间轴时的静态文本颜色。 */
    color: Color,
    /** 有逐字时间轴时已唱和未唱的透明度。 */
    sungAlpha: Float,
    unsungAlpha: Float,
    textAlign: TextAlign,
    maskRegistry: SweepMaskRegistry,
    textMeasurer: TextMeasurer,
    displayOptions: LyricsDisplayOptions,
    textStyle: TextStyle = style.wordsTranslationTextStyle,
    /** False draws the line as plain static text instead of timing it word by word. */
    timed: Boolean = true,
    /** Shows the text in brackets, like the translation of a background vocal in the reference window. */
    wrapInParens: Boolean = false,
) {
    val words =
        remember(translation, displayOptions.obscureObscene) {
            translation.words.displayWordsWithFullText(
                translation.content,
                displayOptions.obscureObscene,
            )
        }
    val text =
        remember(translation, words) {
            words.joinToString(separator = "") { it.content }.ifBlank { translation.content }
        }
    if (words.isNotEmpty() && timed && !wrapInParens) {
        ProgressiveWordsText(
            text = text,
            words = words,
            currentTimeState = currentTimeState,
            textStyle = textStyle,
            color = color,
            sungAlpha = sungAlpha,
            unsungAlpha = unsungAlpha,
            maskRegistry = maskRegistry,
            textMeasurer = textMeasurer,
            modifier = Modifier.fillMaxWidth(),
            alignEnd = textAlign == TextAlign.End,
            rubyTextStyle = style.rubyTextStyle,
            showRuby = displayOptions.showRuby,
            showEmptyBeat = displayOptions.showEmptyBeat,
        )
    } else {
        Text(
            text = if (wrapInParens) "($text)" else text,
            style = textStyle,
            color = color,
            textAlign = textAlign,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Opens the background vocal when it is about to be sung: 0.5 s before it, or with the line if they start together. */
private fun LyricItem.WordsLyric.AccompanimentLyric.openAtMs(mainStartMs: Long): Long =
    if (abs(startTime - mainStartMs) <= LyricUIConstants.ACCOMPANIMENT_NEAR_START_MS) {
        mainStartMs
    } else {
        startTime - LyricUIConstants.ACCOMPANIMENT_OPEN_LEAD_MS
    }

private fun LyricItem.WordsLyric.AccompanimentLyric.isAboveMain(mainStartMs: Long): Boolean {
    val difference = mainStartMs - startTime
    return difference > LyricUIConstants.ACCOMPANIMENT_ABOVE_THRESHOLD_MS || difference == 0L
}

/** Blur in dp for a layer; 0 removes the effect. */
private fun GraphicsLayerScope.blurDp(radiusDp: Float) {
    renderEffect =
        if (radiusDp > 0.05f) {
            val radiusPx = radiusDp * density
            BlurEffect(radiusPx, radiusPx, TileMode.Decal)
        } else {
            null
        }
}

@Composable
private fun AccompanimentLine(
    background: LyricItem.WordsLyric.AccompanimentLyric,
    currentTimeState: State<Long>,
    litFraction: State<Float>,
    mainStartMs: Long,
    style: LyricsUIStyle,
    textMeasurer: TextMeasurer,
    parentAgents: List<LyricItem.Agent>,
    voiceIndexById: Map<String, Int>,
    colorScheme: ColorScheme,
    fallbackAccent: Color?,
    fallbackAlignEnd: Boolean,
    displayOptions: LyricsDisplayOptions,
) {
    val backgroundAgents = background.voiceAgents().ifEmpty { parentAgents }
    // A background vocal always uses the colour and the side of its main lyric, so it never looks like another voice.
    val textColor = fallbackAccent ?: colorScheme.onSurface
    val secondaryColor = textColor.copy(alpha = LyricUIConstants.ACCOMPANIMENT_SECONDARY_SUNG_ALPHA)
    val alignEnd = fallbackAlignEnd
    val horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start
    val textAlign = if (alignEnd) TextAlign.End else TextAlign.Start
    val words =
        remember(background, displayOptions.obscureObscene) {
            background.words.displayWordsWithFullText(
                background.content,
                displayOptions.obscureObscene,
            )
        }
    if (words.isEmpty()) return

    // It opens once and then stays open (it only folds again when you seek back before it).
    val openAtMs = remember(background, mainStartMs) { background.openAtMs(mainStartMs) }
    val visible by remember(openAtMs, currentTimeState) {
        derivedStateOf { currentTimeState.value >= openAtMs }
    }
    // Its space is always reserved, so opening never moves the main lyric; it only fades in.
    val alphaState =
        animateFloatAsState(
            targetValue = if (visible) 1f else 0f,
            animationSpec = tween(LyricUIConstants.ACCOMPANIMENT_FADE_DURATION_MS, easing = LyricLineAlphaEasing),
            label = "accompanimentAlpha",
        )
    // It comes into focus as it appears; its translation follows a beat later.
    val blurState =
        animateFloatAsState(
            targetValue = if (visible) 0f else LyricUIConstants.ACCOMPANIMENT_CLOSED_BLUR_DP,
            animationSpec = tween(LyricUIConstants.ACCOMPANIMENT_OPEN_DURATION_MS, easing = LyricLineScaleEasing),
            label = "accompanimentBlur",
        )
    val translationBlurState =
        animateFloatAsState(
            targetValue = if (visible) 0f else LyricUIConstants.ACCOMPANIMENT_CLOSED_TRANSLATION_BLUR_DP,
            animationSpec =
                tween(
                    LyricUIConstants.ACCOMPANIMENT_OPEN_DURATION_MS,
                    delayMillis = LyricUIConstants.ACCOMPANIMENT_TRANSLATION_BLUR_DELAY_MS,
                    easing = LyricLineScaleEasing,
                ),
            label = "accompanimentTranslationBlur",
        )

    // 伴唱动画可能覆盖主句和翻译，因此使用独立离屏宿主承载蒙版。
    val maskRegistry = remember(litFraction) { SweepMaskRegistry(litFraction) }
    val sweepFeatherPx = with(LocalDensity.current) { style.sweepFeather.toPx() }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    clip = false
                    blurDp(blurState.value)
                },
    ) {
        Column(
            horizontalAlignment = horizontalAlignment,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                        alpha = alphaState.value
                    }.sweepMaskHost(maskRegistry, sweepFeatherPx),
        ) {
            Spacer(modifier = Modifier.height(2.dp))
            if (displayOptions.showAgentLabel && backgroundAgents.hasUsefulLabels()) {
                LineContextLabel(
                    songPart = null,
                    agents = backgroundAgents,
                    textAlign = textAlign,
                )
                Spacer(modifier = Modifier.height(2.dp))
            }
            val transliterations =
                background.transliterations.visibleTransliterations(
                    displayOptions.transliterationMode,
                    displayOptions.preferredTransliterationLanguage,
                )
            val attachedTransliteration =
                if (style.attachTransliterationToWords) {
                    transliterations.firstOrNull()?.let { mapTransliterationToWords(words, it) }
                } else {
                    null
                }
            val plainTransliterations = if (attachedTransliteration != null) transliterations.drop(1) else transliterations
            ProgressiveWordsText(
                text =
                    words
                        .joinToString(separator = "") { it.content }
                        .ifBlank { background.content.orEmpty() },
                words = words,
                currentTimeState = currentTimeState,
                attachedTexts = attachedTransliteration,
                attachedStyle = style.phoneticTextStyle,
                attachedColor = secondaryColor,
                attachedOnTop = style.transliterationOnTop,
                textStyle = style.accompanimentTextStyle,
                color = textColor,
                sungAlpha = LyricUIConstants.ACCOMPANIMENT_SUNG_ALPHA,
                unsungAlpha =
                    (LyricUIConstants.INACTIVE_LINE_ALPHA * style.unsungFactor * LyricUIConstants.ACCOMPANIMENT_SUNG_ALPHA)
                        .coerceIn(0f, LyricUIConstants.ACCOMPANIMENT_SUNG_ALPHA),
                maskRegistry = maskRegistry,
                textMeasurer = textMeasurer,
                modifier = Modifier.fillMaxWidth(),
                alignEnd = alignEnd,
                rubyTextStyle = style.rubyTextStyle,
                showRuby = displayOptions.showRuby,
                showEmptyBeat = displayOptions.showEmptyBeat,
            )

            if (plainTransliterations.isNotEmpty()) {
                plainTransliterations.forEach { transliteration ->
                    Spacer(modifier = Modifier.height(2.dp))
                    TranslationLine(
                        translation = transliteration,
                        currentTimeState = currentTimeState,
                        style = style,
                        color = secondaryColor,
                        sungAlpha = LyricUIConstants.ACCOMPANIMENT_SECONDARY_SUNG_ALPHA,
                        unsungAlpha = LyricUIConstants.ACCOMPANIMENT_SECONDARY_UNSUNG_ALPHA,
                        textAlign = textAlign,
                        maskRegistry = maskRegistry,
                        textMeasurer = textMeasurer,
                        displayOptions = displayOptions,
                        textStyle = style.phoneticTextStyle,
                        timed = false,
                    )
                }
            } else if (transliterations.isEmpty() && !background.phonetic.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = background.phonetic!!,
                    style = style.phoneticTextStyle,
                    color = secondaryColor,
                    textAlign = textAlign,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // The translation of the background vocal sits right under it, in brackets.
            val translations =
                background.translationVariantsFor(
                    displayOptions.translationMode,
                    displayOptions.preferredTranslationLanguage,
                )
            translations.forEach { translation ->
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                clip = false
                                blurDp(translationBlurState.value)
                            },
                ) {
                    TranslationLine(
                        translation = translation,
                        currentTimeState = currentTimeState,
                        style = style,
                        color = secondaryColor,
                        sungAlpha = LyricUIConstants.ACCOMPANIMENT_SECONDARY_SUNG_ALPHA,
                        unsungAlpha = LyricUIConstants.ACCOMPANIMENT_SECONDARY_UNSUNG_ALPHA,
                        textAlign = textAlign,
                        maskRegistry = maskRegistry,
                        textMeasurer = textMeasurer,
                        displayOptions = displayOptions,
                        textStyle = style.accompanimentTranslationTextStyle,
                        timed = false,
                        wrapInParens = true,
                    )
                }
            }
        }
    }
}

/**
 * 逐字文本块：词文本静态排版，播放进度由扫描蒙版显示。
 * 词间空白改用边缘内间距，保证换行后的对齐边整齐。
 *
 * @param color 文字颜色，透明度由 [sungAlpha] 和 [unsungAlpha] 控制
 */
@Composable
private fun ProgressiveWordsText(
    text: String,
    words: List<LyricItem.WordsLyric.WordWithTiming>,
    currentTimeState: State<Long>,
    textStyle: TextStyle,
    color: Color,
    sungAlpha: Float,
    unsungAlpha: Float,
    maskRegistry: SweepMaskRegistry,
    textMeasurer: TextMeasurer,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = false,
    rubyTextStyle: TextStyle? = null,
    showRuby: Boolean = false,
    showEmptyBeat: Boolean = false,
    /** Transliteration attached to each word (same size as `words`), drawn above or under that word. */
    attachedTexts: List<String>? = null,
    attachedStyle: TextStyle? = null,
    attachedColor: Color = color,
    attachedOnTop: Boolean = true,
) {
    if (words.isEmpty()) {
        Text(
            text = text,
            style = textStyle,
            color = color.copy(alpha = unsungAlpha),
            modifier = modifier,
        )
        return
    }

    // 仅此状态逐帧读取时间；未开始或已唱完时值保持不变。
    val sweepState =
        remember(words, currentTimeState) {
            derivedStateOf { words.sweepAt(currentTimeState.value) }
        }
    val drawColor = color.copy(alpha = sungAlpha)
    val wordStyle = remember(textStyle, drawColor) { textStyle.copy(color = drawColor) }
    val dimAlpha = if (sungAlpha > 0f) (unsungAlpha / sungAlpha).coerceIn(0f, 1f) else 0f
    val renderWords = remember(words) { words.toRenderWords() }
    val slowWordLayouts =
        remember(words, renderWords, textStyle, textMeasurer) {
            words.mapIndexed { index, word ->
                measureSlowWord(textMeasurer, word, renderWords[index].text, textStyle)
            }
        }
    val hasSlowWord = slowWordLayouts.any { it != null }
    val maskBlock =
        remember(sweepState, dimAlpha, hasSlowWord, words.size) {
            MaskBlock(
                sweep = sweepState,
                dimAlpha = dimAlpha,
                rowOverflow = if (hasSlowWord) LyricUIConstants.MASK_ROW_OVERFLOW_PX else 0f,
                wordCount = words.size,
            )
        }
    DisposableEffect(maskRegistry, maskBlock) {
        maskRegistry.blocks.add(maskBlock)
        onDispose { maskRegistry.blocks.remove(maskBlock) }
    }
    val density = LocalDensity.current
    val wordLiftPx = with(density) { LyricUIConstants.WORD_LIFT_DP.dp.toPx() }
    val wordGap =
        remember(textStyle, textMeasurer, density) {
            with(density) {
                textMeasurer
                    .measure(" ", textStyle)
                    .getBoundingBox(0)
                    .width
                    .toDp()
            }
        }

    val hasAttached = attachedTexts != null && attachedStyle != null
    FlowRow(
        modifier = modifier,
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
        // With a transliteration above the words, words without one stay on the same baseline as their neighbours.
        itemVerticalAlignment = if (hasAttached && attachedOnTop) Alignment.Bottom else Alignment.Top,
    ) {
        // 计算当前词与下一个词之间的空白和空拍。
        fun gapAfter(index: Int): Dp {
            val space = if (renderWords[index].gapAfter) wordGap else 0.dp
            val nextBeat = words.getOrNull(index + 1)?.emptyBeat ?: 0
            val beat = if (showEmptyBeat) (nextBeat.coerceIn(0, 32) * 4).dp else 0.dp
            return space + beat
        }

        words.forEachIndexed { index, word ->
            val renderWord = renderWords[index]
            val rubyText =
                if (showRuby && rubyTextStyle != null) {
                    word.ruby
                        .joinToString(separator = "") { it.text }
                        .trim()
                } else {
                    ""
                }
            val attachedText = if (hasAttached) attachedTexts?.getOrNull(index).orEmpty() else ""
            // 间隔放在远离对齐边的一侧，避免换行后出现隐形留白。
            val leadingGap = if (alignEnd && index > 0) gapAfter(index - 1) else 0.dp
            val trailingGap = if (!alignEnd && index < words.lastIndex) gapAfter(index) else 0.dp
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier =
                    Modifier
                        .graphicsLayer {
                            // 已唱词保持上抬，当前词随进度上移。
                            translationY = sweepState.value.progressOf(index) * wordLiftPx
                        }.padding(start = leadingGap, end = trailingGap)
                        // 在内间距之后登记坐标，让扫描只覆盖字形。
                        .onPlaced { maskBlock.wordCoordinates[index] = it },
            ) {
                if (rubyText.isNotBlank()) {
                    Text(
                        text = rubyText,
                        style = rubyTextStyle!!,
                        color = color.copy(alpha = LyricUIConstants.RUBY_TEXT_ALPHA),
                        maxLines = 1,
                        // 注音超出词宽时向两侧悬出，不改变对齐位置。
                        modifier =
                            Modifier.layout { measurable, _ ->
                                val placeable = measurable.measure(Constraints())
                                layout(0, placeable.height) {
                                    placeable.place(-placeable.width / 2, 0)
                                }
                            },
                    )
                }
                if (attachedText.isNotBlank() && attachedOnTop) {
                    Text(
                        text = attachedText,
                        style = attachedStyle!!,
                        color = attachedColor,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                val slowWordLayout = slowWordLayouts[index]
                if (slowWordLayout != null) {
                    SlowWordGlyphs(
                        word = word,
                        layout = slowWordLayout,
                        sweepState = sweepState,
                        index = index,
                        color = drawColor,
                        registry = maskRegistry,
                    )
                } else {
                    BasicText(
                        text = renderWord.text,
                        style = wordStyle,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                if (attachedText.isNotBlank() && !attachedOnTop) {
                    Text(
                        text = attachedText,
                        style = attachedStyle!!,
                        color = attachedColor,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

/**
 * Splits a timed transliteration over the words of the line: every transliteration word goes to the last lyric
 * word that started at or before it. Returns one text per word (empty when a word has none, or when it only
 * repeats the word itself), or null when the transliteration has no word timing or does not line up with the
 * words (then it is shown as one plain line instead, like the reference window).
 */
private fun mapTransliterationToWords(
    words: List<LyricItem.WordsLyric.WordWithTiming>,
    transliteration: LyricItem.WordsLyric.Translation,
): List<String>? {
    val timed = transliteration.words.filter { it.content.isNotBlank() }.sortedBy { it.startTime }
    val candidates = words.indices.filter { words[it].content.isNotBlank() }
    if (timed.isEmpty() || candidates.isEmpty()) return null
    val toleranceMs = 400L
    val buckets = MutableList(words.size) { "" }
    var matched = 0
    timed.forEach { piece ->
        var target = candidates.first()
        for (index in candidates) {
            if (words[index].startTime <= piece.startTime + 120L) target = index else break
        }
        val word = words[target]
        if (piece.startTime >= word.startTime - toleranceMs && piece.startTime <= word.endTime + toleranceMs) matched++
        buckets[target] = if (buckets[target].isEmpty()) piece.content.trim() else buckets[target] + " " + piece.content.trim()
    }
    if (matched * 2 < timed.size) return null
    return words.indices.map { index ->
        val text = buckets[index]
        if (text.equals(words[index].content.trim(), ignoreCase = true)) "" else text
    }
}

/** 渲染用的词，去除首尾空白并保留词间间隔标记。 */
private class RenderWord(
    val text: String,
    val gapAfter: Boolean,
)

private fun List<LyricItem.WordsLyric.WordWithTiming>.toRenderWords(): List<RenderWord> =
    mapIndexed { index, word ->
        val text = word.content.trim()
        val next = getOrNull(index + 1)
        RenderWord(
            text = text,
            // 纯空白词不重复计算间隔。
            gapAfter =
                text.isNotEmpty() &&
                    next != null &&
                    (
                        word.endsWithSpace ||
                            word.content.lastOrNull()?.isWhitespace() == true ||
                            next.content.firstOrNull()?.isWhitespace() == true
                    ),
        )
    }

/** 绘制随进度缩放和下沉的长音词字形。 */
@Composable
private fun SlowWordGlyphs(
    word: LyricItem.WordsLyric.WordWithTiming,
    layout: SlowWordLayout,
    sweepState: State<SweepPosition>,
    index: Int,
    color: Color,
    registry: SweepMaskRegistry,
) {
    val glow =
        remember(layout, word, sweepState, index, color) {
            SlowWordGlow(layout, word, sweepState, index, color)
        }
    DisposableEffect(registry, glow) {
        registry.glows.add(glow)
        onDispose { registry.glows.remove(glow) }
    }
    val progressState = remember(sweepState, index) { derivedStateOf { sweepState.value.progressOf(index) } }
    val density = LocalDensity.current
    val canvasSize =
        remember(layout, density) {
            with(density) {
                DpSize(
                    layout.layout.size.width
                        .toDp(),
                    layout.layout.size.height
                        .toDp(),
                )
            }
        }
    Canvas(
        modifier =
            Modifier
                .size(canvasSize)
                .onPlaced { glow.coordinates = it },
    ) {
        drawSlowWordChars(layout, word, progressState.value, color, shadow = null)
    }
}

/** 记录长音词外发光所需的数据。 */
private class SlowWordGlow(
    val layout: SlowWordLayout,
    val word: LyricItem.WordsLyric.WordWithTiming,
    val sweep: State<SweepPosition>,
    val index: Int,
    val color: Color,
) {
    var coordinates: LayoutCoordinates? = null
}

/** 在字形本地坐标中逐字符绘制长音词。 */
private fun DrawScope.drawSlowWordChars(
    layout: SlowWordLayout,
    word: LyricItem.WordsLyric.WordWithTiming,
    progress: Float,
    color: Color,
    shadow: Shadow?,
) {
    val characterCount = layout.characterLayouts.size
    val wordPivot = Offset(layout.box.center.x, layout.box.bottom)
    layout.characterLayouts.forEachIndexed { characterIndex, characterLayout ->
        val characterBox = layout.characterBounds[characterIndex]
        val animationProgress =
            slowWordCharacterAnimationProgress(
                word = word,
                wordProgress = progress,
                characterIndex = characterIndex,
                characterCount = characterCount,
            )
        val scale =
            slowWordCharacterScale(
                animationProgress = animationProgress,
                amplitude = layout.scaleAmplitude,
            )
        val floatOffset = slowWordCharacterFloatOffset(animationProgress, layout.dipAmplitude)
        val characterPosition =
            Offset(
                x = characterBox.left + (characterBox.width - characterLayout.size.width) / 2f,
                y = characterBox.top + floatOffset,
            )
        withTransform({ scale(scaleX = scale, scaleY = scale, pivot = wordPivot) }) {
            drawText(
                textLayoutResult = characterLayout,
                color = color,
                topLeft = characterPosition,
                shadow = shadow,
            )
        }
    }
}

/** 在扫描蒙版后叠加随进度变化的长音词外发光。 */
private fun DrawScope.drawSlowWordGlows(registry: SweepMaskRegistry) {
    val host = registry.hostCoordinates?.takeIf { it.isAttached } ?: return
    for (glow in registry.glows) {
        val coordinates = glow.coordinates?.takeIf { it.isAttached } ?: continue
        val progress = glow.sweep.value.progressOf(glow.index)
        val swell = 4f * progress * (1f - progress)
        if (swell <= 0f) continue
        val glowColor = glow.color.copy(alpha = LyricUIConstants.WORD_GLOW_ALPHA * swell)
        val shadow =
            Shadow(
                color = glowColor,
                blurRadius = LyricUIConstants.WORD_GLOW_BLUR_RADIUS * swell,
            )
        val origin = host.localPositionOf(coordinates, Offset.Zero)
        translate(origin.x, origin.y) {
            drawSlowWordChars(glow.layout, glow.word, progress, glowColor, shadow)
        }
    }
}

/** 长音词的逐字符测量结果，普通词返回空值。 */
private fun measureSlowWord(
    textMeasurer: TextMeasurer,
    word: LyricItem.WordsLyric.WordWithTiming,
    text: String,
    textStyle: TextStyle,
): SlowWordLayout? {
    val scaleAmplitude = slowWordScaleAmplitude(word)
    val dipAmplitude = slowWordDipAmplitude(word)
    if (text.isEmpty() || (scaleAmplitude <= 0f && dipAmplitude <= 0f)) return null
    val layout = textMeasurer.measure(text = text, style = textStyle)
    return SlowWordLayout(
        layout = layout,
        box = wordBoundingBox(layout, 0, text.length),
        scaleAmplitude = scaleAmplitude,
        dipAmplitude = dipAmplitude,
        characterLayouts =
            text.map { character ->
                textMeasurer.measure(text = character.toString(), style = textStyle)
            },
        characterBounds = text.indices.map(layout::getBoundingBox),
    )
}

private class SlowWordLayout(
    val layout: TextLayoutResult,
    val box: Rect,
    val scaleAmplitude: Float,
    val dipAmplitude: Float,
    val characterLayouts: List<TextLayoutResult>,
    val characterBounds: List<Rect>,
)

// ==================== 界面辅助组件 ====================

/** 展示拖动定位预览和播放按钮。 */
@Composable
private fun SeekPreview(
    timeText: String,
    onSeek: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(end = LyricUIConstants.SEEK_PREVIEW_END_PADDING.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .clip(Shapes.ExtraLarge1CornerBasedShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = timeText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        IconButton(
            onClick = onSeek,
            modifier =
                Modifier
                    .width(88.dp)
                    .height(44.dp),
            colors =
                IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = stringResource(R.string.seek_to_lyric_line),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

// ==================== 工具函数 ====================

/** 计算目标行对齐视口锚点所需的滚动距离。 */
private fun LazyListState.distanceToAnchor(
    itemOffset: Int,
    anchorOffsetPx: Int,
): Float {
    val anchorY = layoutInfo.viewportStartOffset + anchorOffsetPx
    return (itemOffset - anchorY).toFloat()
}

/** 无动画精确定位到目标歌词行。 */
private suspend fun LazyListState.snapToLyricIndex(
    targetIndex: Int,
    anchorOffsetPx: Int,
) {
    if (anchorOffsetPx < 0 || targetIndex < 0) return
    scrollToItem(targetIndex, -anchorOffsetPx)
    val targetItem =
        snapshotFlow {
            layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetIndex }
        }.first { it != null } ?: return
    scrollBy(distanceToAnchor(targetItem.offset, anchorOffsetPx))
}

private suspend fun LazyListState.syncToLyricIndex(
    targetIndex: Int,
    anchorOffsetPx: Int,
) {
    if (targetIndex < 0 || anchorOffsetPx < 0) return

    val targetItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetIndex }
    if (targetItem != null) {
        val distance = distanceToAnchor(targetItem.offset, anchorOffsetPx)
        if (abs(distance) >= 0.5f) {
            scrollBy(distance)
        }
    } else {
        scrollToItem(targetIndex, -anchorOffsetPx)
        val settledItem =
            withTimeoutOrNull(500L) {
                snapshotFlow {
                    layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetIndex }
                }.first { it != null }
            }

        if (settledItem == null) {
            snapToLyricIndex(targetIndex, anchorOffsetPx)
            return
        }

        val residual = distanceToAnchor(settledItem.offset, anchorOffsetPx)
        if (abs(residual) >= 0.5f) {
            scrollBy(residual)
        }
    }
}

/** 将歌词时间格式化为分:秒。 */
private fun formatLyricTime(time: Long): String {
    val totalSeconds = (time / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

// ==================== 计算辅助函数 ====================

private fun LyricItem.isActiveAt(time: Long): Boolean =
    when (this) {
        is LyricItem.NormalLyric -> false
        is LyricItem.WordsLyric -> time >= startTime && time <= endTime
    }

/** 返回歌手对应的文字颜色。 */
private fun voiceAccent(
    index: Int?,
    colorScheme: ColorScheme,
): Color {
    val palette = listOf(colorScheme.onSurface)
    return palette[(index ?: 0).coerceAtLeast(0) % palette.size]
}

private fun voiceAlignEnd(index: Int): Boolean = index % 2 == 1

/** 计算歌词项与当前项的距离。 */
private fun calculateDistance(
    index: Int,
    highlightedIndex: Int,
): Int =
    if (highlightedIndex == Int.MAX_VALUE) {
        Int.MAX_VALUE
    } else {
        abs(index - highlightedIndex)
    }

/** Opacity of a line: the current line is fully visible, every other line uses the Unsung text opacity. */
private fun calculateLineAlpha(
    distance: Int,
    unsungPercent: Int,
): Float =
    if (distance == 0) {
        1f
    } else {
        (LyricUIConstants.INACTIVE_LINE_ALPHA * unsungPercent / LyricUIConstants.UNSUNG_DEFAULT_PERCENT)
            .coerceIn(0f, 1f)
    }

/** Blur of a line in dp: none on the current line, then 2.5 dp growing by 1.2 dp per line up to 6 dp, scaled by the Blur slider. */
private fun calculateBlurDp(
    distance: Int,
    blurPercent: Int,
): Float {
    if (distance == 0 || blurPercent <= 0) return 0f
    val steps = (distance - 1).coerceAtLeast(0).coerceAtMost(10)
    val base =
        (LyricUIConstants.BLUR_FIRST_LINE_DP + steps * LyricUIConstants.BLUR_STEP_DP)
            .coerceAtMost(LyricUIConstants.MAX_BLUR_RADIUS)
    return base * blurPercent / 100f
}

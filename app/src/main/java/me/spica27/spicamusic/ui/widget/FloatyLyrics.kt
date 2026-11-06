package me.spica27.spicamusic.ui.widget

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import me.spica27.spicamusic.common.entity.LyricItem
import java.text.BreakIterator
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

// =====================================================================================================================
// "Floaty" word animation.
//
// A port of the reference floaty_effects.html. Every sung word is kept whole (the timed syllables of a lyric file are
// glued back together, so "symp" + "hony" is one word that never breaks across lines), and its letters light up in a
// wave: each letter fades in, swells, lifts a little, glows and then settles back softly. Held notes get a slower, bigger
// swell and keep their glow until the note ends. Swollen letters push their neighbours aside so nothing overlaps.
//
// Performance:
//  * letters are measured once per line, never per frame;
//  * nothing recomposes while playing: time is only read in the draw phase (and in one graphicsLayer block that shifts
//    the words behind a swollen letter);
//  * a word that has not started or is already done only draws a static picture (no time read at all);
//  * one shared per-frame calculation per line, and lines that are not animating read nothing per frame.
//
// Colours come from the app theme (the `color` handed in), the lines themselves scroll / jiggle / blur exactly like the
// default animation because only the word renderer is swapped.
// =====================================================================================================================

private object FloatyTuning {
    /** Held words: the wave of letters spreads over this fraction of the word's duration. */
    const val WAVE = 0.55f

    /** A word at least this long (seconds) is a "held note". */
    const val HELD_AFTER = 0.9f

    const val FRISE = 0.35f
    const val FTAIL = 1.0f

    /** Swell of a letter (0.14 = 14%). */
    const val AMP = 0.14f

    /** Lift of a letter in dp, before the per-word factor. */
    const val LIFT_DP = 4f

    const val GLOW_DP = 7f
    const val GLOW_ALPHA = 0.45f

    /** Words shorter than this (ms) are treated as this long. */
    const val MIN_WORD_MS = 80L
    const val MIN_PIECE_MS = 40L

    /** Letters of a word are scaled around this fraction of their height (transform-origin 50% 62%). */
    const val PIVOT_Y = 0.62f

    /** A letter only gets a glow while its glow strength is above this. */
    const val GLOW_MIN = 0.08f
}

private fun smooth(x: Float): Float = x * x * (3f - 2f * x)

// ---------------------------------------------------------------------------------------------------------------------
// Static model: timed syllables -> whole words -> letters with their own timing
// ---------------------------------------------------------------------------------------------------------------------

/** A timed piece of text before it is glued into a word. */
private class FloatyPiece(
    val text: String,
    val start: Long,
    val end: Long,
    var spaceAfter: Boolean,
    val emptyBeat: Int,
    val unitIndex: Int,
)

/** One whole word with the timing of each of its letters (seconds, relative to [startMs]). */
internal class FloatySpec(
    val text: String,
    val startMs: Long,
    /** Absolute time (ms) after which every letter of this word is back at rest. */
    val doneMs: Long,
    val spaceAfter: Boolean,
    val emptyBeat: Int,
    /** First and last source unit this word covers (for the attached transliteration). */
    val unitFrom: Int,
    val unitTo: Int,
    val clusterStart: IntArray,
    val clusterEnd: IntArray,
    val t0: FloatArray,
    val fade: FloatArray,
    val tail: FloatArray,
    val done: FloatArray,
    val hold: Float,
    val fr: Float,
    val ft: Float,
    val liftFactor: Float,
    val amp: Float,
) {
    val n: Int get() = t0.size
}

private val FloatyWhitespace = Regex("\\s+")

/** Splits the timed units into pieces; a unit holding several words is split in proportion to the letter count. */
private fun List<LyricItem.WordsLyric.WordWithTiming>.toFloatyPieces(): List<FloatyPiece> {
    val out = ArrayList<FloatyPiece>(size)
    var pendingBeat = 0
    forEachIndexed { index, unit ->
        val raw = unit.content
        val text = raw.trim()
        if (raw.firstOrNull()?.isWhitespace() == true) out.lastOrNull()?.spaceAfter = true
        if (text.isEmpty()) {
            // A blank unit only marks a space (and maybe an empty beat) between two words.
            if (raw.isNotEmpty()) out.lastOrNull()?.spaceAfter = true
            pendingBeat += unit.emptyBeat ?: 0
            return@forEachIndexed
        }
        val parts = text.split(FloatyWhitespace)
        val totalChars = parts.sumOf { it.length }.coerceAtLeast(1)
        val duration = (unit.endTime - unit.startTime).coerceAtLeast(0L)
        val endsWithSpace = unit.endsWithSpace || raw.last().isWhitespace()
        var consumed = 0
        parts.forEachIndexed { k, part ->
            val start = unit.startTime + duration * consumed / totalChars
            consumed += part.length
            val end = unit.startTime + duration * consumed / totalChars
            val isLast = k == parts.lastIndex
            out +=
                FloatyPiece(
                    text = part,
                    start = start,
                    end = end,
                    spaceAfter = !isLast || endsWithSpace,
                    emptyBeat = if (k == 0) pendingBeat + (unit.emptyBeat ?: 0) else 0,
                    unitIndex = index,
                )
            if (k == 0) pendingBeat = 0
        }
    }
    return out
}

/** Glues timed syllables back into whole words: a word ends where a space follows. */
internal fun List<LyricItem.WordsLyric.WordWithTiming>.toFloatySpecs(): List<FloatySpec> {
    val pieces = toFloatyPieces()
    val specs = ArrayList<FloatySpec>()
    var from = 0
    var previousLastUnit = -1
    for (i in pieces.indices) {
        if (pieces[i].spaceAfter || i == pieces.lastIndex) {
            val group = pieces.subList(from, i + 1)
            specs += buildFloatySpec(group, pieces.getOrNull(i + 1)?.start, previousLastUnit + 1, pieces[i].spaceAfter)
            previousLastUnit = group.last().unitIndex
            from = i + 1
        }
    }
    return specs
}

private fun buildFloatySpec(
    group: List<FloatyPiece>,
    nextStartMs: Long?,
    unitFrom: Int,
    spaceAfter: Boolean,
): FloatySpec {
    val text = buildString { group.forEach { append(it.text) } }
    val startMs = group.first().start
    val rawEnd = group.maxOf { it.end }
    // A word ends where the next one starts when they overlap, otherwise at its own end.
    val endMs = if (nextStartMs != null && nextStartMs > startMs) min(rawEnd, nextStartMs) else rawEnd
    val d = max(endMs - startMs, FloatyTuning.MIN_WORD_MS) / 1000f

    // Letters = grapheme clusters, so emoji and accents never get cut in half.
    val starts = ArrayList<Int>()
    val ends = ArrayList<Int>()
    val breaker = BreakIterator.getCharacterInstance()
    breaker.setText(text)
    var s = breaker.first()
    var e = breaker.next()
    while (e != BreakIterator.DONE) {
        starts += s
        ends += e
        s = e
        e = breaker.next()
    }
    val n = starts.size.coerceAtLeast(1)
    if (starts.isEmpty()) {
        starts += 0
        ends += text.length
    }

    // Which timed piece (syllable) each letter belongs to.
    val pieceChar = IntArray(group.size)
    var offset = 0
    group.forEachIndexed { i, p ->
        pieceChar[i] = offset
        offset += p.text.length
    }
    val pieceOf = IntArray(n)
    var pi = 0
    for (j in 0 until n) {
        while (pi + 1 < group.size && pieceChar[pi + 1] <= starts[j]) pi++
        pieceOf[j] = pi
    }
    val pieceLetters = IntArray(group.size)
    for (j in 0 until n) pieceLetters[pieceOf[j]]++
    val pieceSeen = IntArray(group.size)

    fun pieceSeconds(i: Int): Float {
        val p = group[i]
        val end = if (i < group.lastIndex) min(p.end, group[i + 1].start) else p.end
        return max(end - p.start, FloatyTuning.MIN_PIECE_MS) / 1000f
    }

    val held = d >= FloatyTuning.HELD_AFTER
    val long = ((d - 0.5f) / 1.2f).coerceIn(0f, 1f)
    val speed = ((d - 0.2f) / 0.5f).coerceIn(0f, 1f)
    val heldness = ((d - 0.9f) / 4f).coerceIn(0f, 1f)
    val fr = if (held) (d * 0.26f).coerceIn(0.35f, 1.5f) else FloatyTuning.FRISE * (0.55f + 0.45f * speed)
    val ft = if (held) (d * 0.4f).coerceIn(1.2f, 2.6f) else (d * 3f).coerceIn(0.35f, FloatyTuning.FTAIL)
    val liftFull = ((d - 0.35f) / 0.85f).coerceIn(0f, 1f)
    val liftFactor = if (held) 0.5f + 0.5f * heldness else 0.2f + 0.8f * liftFull * liftFull
    val amp = if (held) FloatyTuning.AMP * (0.9f + 2.4f * heldness) else FloatyTuning.AMP * (0.6f + 0.4f * speed)
    val hold = if (held) d else 0f

    val t0 = FloatArray(n)
    val fade = FloatArray(n)
    val tail = FloatArray(n)
    val done = FloatArray(n)
    var lastDone = 0f
    for (j in 0 until n) {
        val piece = pieceOf[j]
        val seen = pieceSeen[piece]++
        // Seconds per letter inside its own syllable.
        val dl = pieceSeconds(piece) / pieceLetters[piece].coerceAtLeast(1)
        val t =
            if (held) {
                d * FloatyTuning.WAVE / n * j
            } else {
                (group[piece].start - startMs) / 1000f + dl * seen
            }
        val fd = if (held) (d * 0.5f).coerceIn(0.4f, 0.9f) else (dl * 1.1f).coerceIn(0.1f, 0.28f)
        val tl = if (held) 1f + long * 0.8f else (dl * 2.6f).coerceIn(0.18f, 0.5f)
        t0[j] = t
        fade[j] = fd
        tail[j] = tl
        done[j] = max(max(hold, t + fd) + tl, t + fr + ft)
        lastDone = max(lastDone, done[j])
    }
    return FloatySpec(
        text = text,
        startMs = startMs,
        doneMs = startMs + ceil(lastDone * 1000f).toLong() + 1L,
        spaceAfter = spaceAfter,
        emptyBeat = group.first().emptyBeat,
        unitFrom = unitFrom,
        unitTo = group.last().unitIndex,
        clusterStart = starts.toIntArray(),
        clusterEnd = ends.toIntArray(),
        t0 = t0,
        fade = fade,
        tail = tail,
        done = done,
        hold = hold,
        fr = fr,
        ft = ft,
        liftFactor = liftFactor,
        amp = amp,
    )
}

// ---------------------------------------------------------------------------------------------------------------------
// Which lines can use it
// ---------------------------------------------------------------------------------------------------------------------

private val FloatyConnectedScripts =
    setOf(
        Character.UnicodeScript.HAN,
        Character.UnicodeScript.HIRAGANA,
        Character.UnicodeScript.KATAKANA,
        Character.UnicodeScript.HANGUL,
        Character.UnicodeScript.ARABIC,
        Character.UnicodeScript.DEVANAGARI,
    )

private fun Char.isFloatyPunctuation(): Boolean =
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

/** CJK, Arabic and Devanagari are written connected or one character per word: a letter wave does not suit them. */
private fun String.isFloatyUnsupportedScript(): Boolean =
    any { !it.isWhitespace() && !it.isFloatyPunctuation() && Character.UnicodeScript.of(it.code) in FloatyConnectedScripts }

/**
 * Floaty works on lines of letter-based text. Lines with CJK / Arabic / Devanagari or with ruby annotations keep the
 * default animation, so nothing ever looks broken.
 */
internal fun List<LyricItem.WordsLyric.WordWithTiming>.supportsFloaty(showRuby: Boolean): Boolean {
    var hasText = false
    for (word in this) {
        if (word.content.isBlank()) continue
        hasText = true
        if (word.content.isFloatyUnsupportedScript()) return false
        if (showRuby && word.ruby.any { it.text.isNotBlank() }) return false
    }
    return hasText
}

// ---------------------------------------------------------------------------------------------------------------------
// Measuring (once per line)
// ---------------------------------------------------------------------------------------------------------------------

/** Pixel geometry of one word and its letters. */
internal class FloatyMeasured(
    val width: Int,
    val height: Int,
    val letterLayouts: Array<TextLayoutResult>,
    val letterX: FloatArray,
    val letterY: FloatArray,
    val pivotX: FloatArray,
    val pivotY: FloatArray,
    val letterWidth: FloatArray,
)

private fun measureFloatyWord(
    textMeasurer: TextMeasurer,
    spec: FloatySpec,
    style: TextStyle,
    glyphCache: HashMap<String, TextLayoutResult>,
): FloatyMeasured {
    val layout = textMeasurer.measure(text = spec.text, style = style)
    val n = spec.n
    val x = FloatArray(n)
    val y = FloatArray(n)
    val px = FloatArray(n)
    val py = FloatArray(n)
    val w = FloatArray(n)
    val glyphs =
        Array(n) { j ->
            val glyphText = spec.text.substring(spec.clusterStart[j], spec.clusterEnd[j])
            val glyph = glyphCache.getOrPut(glyphText) { textMeasurer.measure(text = glyphText, style = style) }
            // Box of the letter inside the whole word, so kerning is kept.
            var left = Float.MAX_VALUE
            var top = Float.MAX_VALUE
            var right = -Float.MAX_VALUE
            var bottom = -Float.MAX_VALUE
            for (i in spec.clusterStart[j] until spec.clusterEnd[j]) {
                val box = layout.getBoundingBox(i)
                left = min(left, box.left)
                top = min(top, box.top)
                right = max(right, box.right)
                bottom = max(bottom, box.bottom)
            }
            val boxWidth = right - left
            x[j] = left + (boxWidth - glyph.size.width) / 2f
            y[j] = top
            px[j] = left + boxWidth / 2f
            py[j] = top + (bottom - top) * FloatyTuning.PIVOT_Y
            w[j] = boxWidth
            glyph
        }
    return FloatyMeasured(
        width = layout.size.width,
        height = layout.size.height,
        letterLayouts = glyphs,
        letterX = x,
        letterY = y,
        pivotX = px,
        pivotY = py,
        letterWidth = w,
    )
}

// ---------------------------------------------------------------------------------------------------------------------
// Per-frame state of one line
// ---------------------------------------------------------------------------------------------------------------------

/**
 * Everything that changes while a line plays. [update] is cheap and idempotent for a given time, so the word layers and
 * the word canvases of a line can all call it in the same frame and only the first one does the work.
 */
internal class FloatyLineAnim(
    private val specs: List<FloatySpec>,
    private val measured: List<FloatyMeasured>,
    /** Right-aligned line: swollen letters push to the left instead of the right. */
    private val reverse: Boolean,
    /** Words in a row share their bottom (true) or their top (false) edge; used to tell the rows apart. */
    private val bottomAligned: Boolean,
) {
    val count: Int = specs.size
    val firstStartMs: Long = specs.minOf { it.startMs }
    val lastDoneMs: Long = specs.maxOf { it.doneMs }

    // Filled by onPlaced.
    val tops = FloatArray(count)
    val bottoms = FloatArray(count)

    // Results of update().
    val letterProgress = Array(count) { FloatArray(specs[it].n) }
    val letterGlow = Array(count) { FloatArray(specs[it].n) }
    val letterFloat = Array(count) { FloatArray(specs[it].n) }
    val letterScale = Array(count) { FloatArray(specs[it].n) { 1f } }
    val letterShiftX = Array(count) { FloatArray(specs[it].n) }
    private val extra = FloatArray(count)
    private val shift = FloatArray(count)
    private val rowOf = IntArray(count)
    private val growth = Array(count) { FloatArray(specs[it].n) }
    private var lastTime = Long.MIN_VALUE

    /** Horizontal shift (px) of a whole word caused by the swollen letters before it in its row. */
    fun shiftOf(
        index: Int,
        timeMs: Long,
    ): Float {
        update(timeMs)
        return shift[index]
    }

    fun update(timeMs: Long) {
        if (timeMs == lastTime) return
        lastTime = timeMs
        var any = false
        for (k in 0 until count) {
            val spec = specs[k]
            if (timeMs < spec.startMs || timeMs >= spec.doneMs) {
                extra[k] = 0f
                continue
            }
            any = true
            computeWord(k, (timeMs - spec.startMs) / 1000f)
        }
        if (!any) {
            shift.fill(0f)
            return
        }
        assignRows()
        if (!reverse) {
            var run = 0f
            var row = -1
            for (k in 0 until count) {
                if (rowOf[k] != row) {
                    row = rowOf[k]
                    run = 0f
                }
                shift[k] = run
                run += extra[k]
            }
        } else {
            var run = 0f
            var row = -1
            for (k in count - 1 downTo 0) {
                if (rowOf[k] != row) {
                    row = rowOf[k]
                    run = 0f
                }
                shift[k] = -run
                run += extra[k]
            }
        }
    }

    private fun computeWord(
        k: Int,
        rt: Float,
    ) {
        val spec = specs[k]
        val widths = measured[k].letterWidth
        val p = letterProgress[k]
        val g = letterGlow[k]
        val f = letterFloat[k]
        val sc = letterScale[k]
        val e = growth[k]
        var total = 0f
        for (j in 0 until spec.n) {
            val a = rt - spec.t0[j]
            var pj = 0f
            var gj = 0f
            var fj = 0f
            if (a > 0f) {
                if (rt >= spec.done[j]) {
                    pj = 1f
                } else {
                    val fade = spec.fade[j]
                    pj = smooth(min(1f, a / fade))
                    gj =
                        when {
                            a < fade -> pj
                            rt < spec.hold -> 1f
                            else ->
                                smooth(max(0f, 1f - (rt - max(spec.hold, spec.t0[j] + fade)) / spec.tail[j]))
                        }
                    fj =
                        if (a < spec.fr) {
                            smooth(a / spec.fr)
                        } else {
                            smooth(max(0f, 1f - (a - spec.fr) / spec.ft))
                        }
                }
            }
            p[j] = pj
            g[j] = gj
            f[j] = fj
            sc[j] = 1f + fj * spec.amp
            e[j] = widths[j] * (sc[j] - 1f)
            total += e[j]
        }
        extra[k] = total
        // Swollen letters push the letters after them (before them on right-aligned lines) out of the way.
        val xs = letterShiftX[k]
        if (!reverse) {
            var local = 0f
            for (j in 0 until spec.n) {
                xs[j] = local + e[j] / 2f
                local += e[j]
            }
        } else {
            var after = total
            for (j in 0 until spec.n) {
                after -= e[j]
                xs[j] = -after - e[j] / 2f
            }
        }
    }

    private fun assignRows() {
        var row = 0
        rowOf[0] = 0
        for (k in 1 until count) {
            val same =
                if (bottomAligned) abs(bottoms[k] - bottoms[k - 1]) < 1f else abs(tops[k] - tops[k - 1]) < 1f
            if (!same) row++
            rowOf[k] = row
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Composable
// ---------------------------------------------------------------------------------------------------------------------

/**
 * Drop-in replacement of the per-word renderer for lines that [supportsFloaty].
 *
 * @param litFraction 0 for a line that is not current (everything at full strength) up to 1 for the current line
 * (not yet sung letters dim down to [unsungAlpha]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FloatyWordsText(
    words: List<LyricItem.WordsLyric.WordWithTiming>,
    currentTimeState: State<Long>,
    litFraction: State<Float>,
    textStyle: TextStyle,
    color: Color,
    sungAlpha: Float,
    unsungAlpha: Float,
    textMeasurer: TextMeasurer,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = false,
    showEmptyBeat: Boolean = false,
    attachedTexts: List<String>? = null,
    attachedStyle: TextStyle? = null,
    attachedColor: Color = color,
    attachedOnTop: Boolean = true,
) {
    val density = LocalDensity.current
    val specs = remember(words) { words.toFloatySpecs() }
    val glyphCache = remember(textMeasurer, textStyle) { HashMap<String, TextLayoutResult>() }
    val measured =
        remember(specs, textMeasurer, textStyle) {
            specs.map { measureFloatyWord(textMeasurer, it, textStyle, glyphCache) }
        }
    val hasAttached = attachedTexts != null && attachedStyle != null
    val bottomAligned = hasAttached && attachedOnTop
    val anim = remember(specs, measured, alignEnd, bottomAligned) { FloatyLineAnim(specs, measured, alignEnd, bottomAligned) }
    // Only a line that is animating right now reads the time every frame.
    val busy =
        remember(anim, currentTimeState) {
            derivedStateOf {
                val t = currentTimeState.value
                t >= anim.firstStartMs && t < anim.lastDoneMs
            }
        }
    val baseColor = remember(color, sungAlpha) { color.copy(alpha = color.alpha * sungAlpha) }
    val dimAlpha = if (sungAlpha > 0f) (unsungAlpha / sungAlpha).coerceIn(0f, 1f) else 0f
    val glowPx = with(density) { FloatyTuning.GLOW_DP.dp.toPx() }
    val liftPx = with(density) { FloatyTuning.LIFT_DP.dp.toPx() }
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
    val attachedPerWord =
        remember(specs, attachedTexts) {
            specs.map { spec ->
                attachedTexts
                    ?.let { texts ->
                        (spec.unitFrom..spec.unitTo)
                            .mapNotNull { texts.getOrNull(it)?.takeIf(String::isNotBlank) }
                            .joinToString(" ")
                    }.orEmpty()
            }
        }

    FlowRow(
        modifier = modifier,
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
        itemVerticalAlignment = if (bottomAligned) Alignment.Bottom else Alignment.Top,
    ) {
        fun gapAfter(index: Int): Dp {
            val space = if (specs[index].spaceAfter) wordGap else 0.dp
            val beat = if (showEmptyBeat) ((specs.getOrNull(index + 1)?.emptyBeat ?: 0).coerceIn(0, 32) * 4).dp else 0.dp
            return space + beat
        }

        specs.forEachIndexed { index, spec ->
            val leadingGap = if (alignEnd && index > 0) gapAfter(index - 1) else 0.dp
            val trailingGap = if (!alignEnd && index < specs.lastIndex) gapAfter(index) else 0.dp
            val attachedText = if (hasAttached) attachedPerWord[index] else ""
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier =
                    Modifier
                        .graphicsLayer {
                            // Words behind a swollen letter make room for it.
                            translationX = if (busy.value) anim.shiftOf(index, currentTimeState.value) else 0f
                        }.padding(start = leadingGap, end = trailingGap)
                        .onPlaced {
                            val position = it.positionInParent()
                            anim.tops[index] = position.y
                            anim.bottoms[index] = position.y + it.size.height
                        },
            ) {
                if (attachedText.isNotBlank() && attachedOnTop) {
                    Text(
                        text = attachedText,
                        style = attachedStyle!!,
                        color = attachedColor,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                FloatyWordCanvas(
                    index = index,
                    spec = spec,
                    measured = measured[index],
                    anim = anim,
                    currentTimeState = currentTimeState,
                    litFraction = litFraction,
                    baseColor = baseColor,
                    dimAlpha = dimAlpha,
                    glowPx = glowPx,
                    liftPx = liftPx,
                )
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

@Composable
private fun FloatyWordCanvas(
    index: Int,
    spec: FloatySpec,
    measured: FloatyMeasured,
    anim: FloatyLineAnim,
    currentTimeState: State<Long>,
    litFraction: State<Float>,
    baseColor: Color,
    dimAlpha: Float,
    glowPx: Float,
    liftPx: Float,
) {
    // 0 = not started, 1 = animating, 2 = done. Only the word that is animating reads the time every frame.
    val phase =
        remember(spec, currentTimeState) {
            derivedStateOf {
                val t = currentTimeState.value
                if (t < spec.startMs) 0 else if (t >= spec.doneMs) 2 else 1
            }
        }
    val density = LocalDensity.current
    val canvasSize =
        remember(measured, density) {
            with(density) { DpSize(measured.width.toDp(), measured.height.toDp()) }
        }
    Canvas(modifier = Modifier.size(canvasSize)) {
        // A word wider than its row is shrunk to fit instead of being cut.
        val fit = if (size.width < measured.width - 1.5f) size.width / measured.width else 1f
        withTransform({
            if (fit < 1f) scale(fit, fit, Offset(0f, size.height / 2f))
        }) {
            when (phase.value) {
                0 -> drawFloatyAtRest(measured, baseColor, 1f - (1f - dimAlpha) * litFraction.value)
                2 -> drawFloatyAtRest(measured, baseColor, 1f)
                else -> {
                    anim.update(currentTimeState.value)
                    drawFloatyActive(
                        measured = measured,
                        spec = spec,
                        anim = anim,
                        index = index,
                        baseColor = baseColor,
                        unsungAlpha = 1f - (1f - dimAlpha) * litFraction.value,
                        glowPx = glowPx,
                        liftPx = liftPx * spec.liftFactor,
                    )
                }
            }
        }
    }
}

/** A word that has not started (dim) or is done (full): every letter in place, no time involved. */
private fun DrawScope.drawFloatyAtRest(
    measured: FloatyMeasured,
    baseColor: Color,
    alpha: Float,
) {
    val color = baseColor.copy(alpha = baseColor.alpha * alpha)
    for (j in measured.letterLayouts.indices) {
        drawText(
            textLayoutResult = measured.letterLayouts[j],
            color = color,
            topLeft = Offset(measured.letterX[j], measured.letterY[j]),
        )
    }
}

/** A word in the middle of its animation: each letter fades in, swells, lifts and glows on its own schedule. */
private fun DrawScope.drawFloatyActive(
    measured: FloatyMeasured,
    spec: FloatySpec,
    anim: FloatyLineAnim,
    index: Int,
    baseColor: Color,
    unsungAlpha: Float,
    glowPx: Float,
    liftPx: Float,
) {
    val progress = anim.letterProgress[index]
    val glow = anim.letterGlow[index]
    val lift = anim.letterFloat[index]
    val scale = anim.letterScale[index]
    val shiftX = anim.letterShiftX[index]
    for (j in 0 until spec.n) {
        val alpha = unsungAlpha + (1f - unsungAlpha) * progress[j]
        val color = baseColor.copy(alpha = baseColor.alpha * alpha)
        val g = glow[j]
        val shadow =
            if (g > FloatyTuning.GLOW_MIN) {
                Shadow(
                    color = baseColor.copy(alpha = baseColor.alpha * g * FloatyTuning.GLOW_ALPHA),
                    offset = Offset.Zero,
                    blurRadius = g * glowPx,
                )
            } else {
                null
            }
        val dx = shiftX[j]
        val dy = -lift[j] * liftPx
        val s = scale[j]
        if (dx == 0f && dy == 0f && s == 1f) {
            drawText(
                textLayoutResult = measured.letterLayouts[j],
                color = color,
                topLeft = Offset(measured.letterX[j], measured.letterY[j]),
                shadow = shadow,
            )
        } else {
            withTransform({
                translate(dx, dy)
                scale(s, s, Offset(measured.pivotX[j], measured.pivotY[j]))
            }) {
                drawText(
                    textLayoutResult = measured.letterLayouts[j],
                    color = color,
                    topLeft = Offset(measured.letterX[j], measured.letterY[j]),
                    shadow = shadow,
                )
            }
        }
    }
}

package me.spica27.spicamusic.ui.widget

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.DeferredTargetAnimation
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ApproachLayoutModifierNode
import androidx.compose.ui.layout.ApproachMeasureScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.round
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Glide shared by every line (current, above and below), the same curve as the reference lyrics window:
 * `1 - (1 + 9t) * e^(-9t)` with t running to 1.1 over the move time. Fast at first, then a long soft landing.
 */
internal val LyricLineMoveEasing =
    Easing { x ->
        if (x <= 0f) {
            0f
        } else if (x >= 1f) {
            1f
        } else {
            val t = x * 1.1f
            1f - (1f + 9f * t) * exp(-9f * t)
        }
    }

/**
 * Apple Music-style jiggle for the lines below the current one, as in the reference lyrics window:
 * on top of the shared glide the line gets a small wobble of FIXED size in dp (it does not depend on how far
 * the list moved) that overshoots against the direction of travel and dies out over [durationMillis].
 *
 * @param strength 10..100 (%). Sets the size of the wobble (6 dp at 0% up to 26 dp at 100%) and how fast it rings.
 * @param durationMillis time the wobble takes to settle.
 */
internal class JiggleSpec(
    val strength: Int,
    val durationMillis: Int,
    density: Float,
) {
    private val s = strength / 100f

    /** Size of the wobble in pixels. */
    val amplitudePx: Float = (6f + 20f * s) * density
    private val decay = 6.5f - 2f * s
    private val frequency = 9f + 2f * s
    private val peak: Float =
        run {
            var m = 0f
            for (i in 0..400) {
                val x = i / 400f * RUN
                m = max(m, abs(exp(-decay * x) * sin(frequency * x)))
            }
            m
        }

    /** Wobble at [progress] 0..1 of its duration, normalised to -1..1. */
    fun wobble(progress: Float): Float {
        if (progress <= 0f || progress >= 1f || peak <= 0f) return 0f
        val x = progress * RUN
        return exp(-decay * x) * sin(frequency * x) / peak
    }

    private companion object {
        const val RUN = 1.1f
    }
}

/**
 * Shared by the lyric list and its lines. A background vocal folds open by changing its height every frame, which
 * moves every line below it. The reference window (HTML) lets plain layout push those lines, so they travel rigidly
 * with the fold. This host lets the placement modifier tell such a push apart from a normal line change:
 * a push is followed at once (no glide, no jiggle), a line change keeps its glide and jiggle.
 * It also collects how much the folds above the sung line grew, so the list can scroll by the same amount and the
 * sung line stays where it is. Everything runs on the main thread.
 */
internal class LyricFoldHost {
    /** Bumped when the list is re-aimed at a new current line (that move is a glide, not a fold push). */
    var syncTick: Int = 0
        private set

    private var lastFoldNanos: Long = Long.MIN_VALUE / 2

    private class Step(
        val itemIndex: Int,
        val aboveMain: Boolean,
        val delta: Int,
    )

    private val steps = ArrayList<Step>()

    /** Wakes the scroll compensation when a fold has moved. */
    val wake = Channel<Unit>(Channel.CONFLATED)

    fun markSync() {
        syncTick++
    }

    /** True while a fold is moving (or has moved a moment ago), so a position change is a push by the fold. */
    fun isFoldDriven(): Boolean = System.nanoTime() - lastFoldNanos < FOLD_WINDOW_NANOS

    fun touch() {
        lastFoldNanos = System.nanoTime()
    }

    /** One animation step of a fold in line [itemIndex]: its height changed by [delta] pixels. */
    fun reportFoldStep(
        itemIndex: Int,
        aboveMain: Boolean,
        delta: Int,
    ) {
        touch()
        steps += Step(itemIndex, aboveMain, delta)
        wake.trySend(Unit)
    }

    /**
     * Pixels the list has to scroll so that the sung main lyric stays put: the growth of folds that sit above it
     * (in earlier lines, or above the main lyric inside its own line) and that are not scrolled out of the list.
     */
    fun takeCompensation(
        playingIndex: Int,
        firstVisibleIndex: Int,
    ): Float {
        var sum = 0f
        for (step in steps) {
            val aboveSungLine =
                step.itemIndex < playingIndex || (step.itemIndex == playingIndex && step.aboveMain)
            if (aboveSungLine && step.itemIndex >= firstVisibleIndex) sum += step.delta
        }
        steps.clear()
        return sum
    }

    private companion object {
        const val FOLD_WINDOW_NANOS = 120_000_000L
    }
}

/**
 * 处理前瞻布局后的歌词行位移。
 * 列表先立即更新滚动位置，各行再按时长和错峰延迟补间；手动滚动时直接贴合手指。
 */
private class LinePlacementModifierNode(
    var lookaheadScope: LookaheadScope,
    var itemKey: Any,
    var isManualScrolling: Boolean,
    var durationMillis: Int,
    var delayMillis: Int,
    var jiggle: JiggleSpec?,
    var initialOffsetY: Int,
    var foldHost: LyricFoldHost?,
) : Modifier.Node(),
    ApproachLayoutModifierNode {
    private var offsetAnimation = DeferredTargetAnimation(IntOffset.VectorConverter)
    private var isFirstFrame = true

    /** Progress 0..1 of the jiggle wobble of the current move. */
    private val wobble = Animatable(0f)
    private var wobbleJob: Job? = null

    /** +1 when the list moved forward (lines travel up), -1 when it moved back. */
    private var wobbleDirection = 1f
    private var lastTarget: IntOffset? = null

    /**
     * Movement that was handed to this line by a folding background vocal. It is added on top of the glide and the
     * glide aims at `target - rigidShift`, so a fold push never restarts the glide or the jiggle.
     */
    private var rigidShift = IntOffset.Zero
    private var seenSyncTick = foldHost?.syncTick ?: 0

    override fun isMeasurementApproachInProgress(lookaheadSize: IntSize): Boolean = false

    override fun Placeable.PlacementScope.isPlacementApproachInProgress(lookaheadCoordinates: LayoutCoordinates): Boolean {
        val target =
            with(lookaheadScope) {
                lookaheadScopeCoordinates.localLookaheadPositionOf(lookaheadCoordinates).round()
            }
        updatePlacementTarget(target)
        return !offsetAnimation.isIdle || wobble.isRunning
    }

    override fun ApproachMeasureScope.approachMeasure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            val coordinates = coordinates
            if (coordinates == null) {
                placeable.place(0, 0)
                return@layout
            }

            val target =
                with(lookaheadScope) {
                    lookaheadScopeCoordinates.localLookaheadPositionOf(coordinates).round()
                }
            val animatedOffset = updatePlacementTarget(target)

            val placementOffset =
                with(lookaheadScope) {
                    lookaheadScopeCoordinates.localPositionOf(coordinates, Offset.Zero).round()
                }
            val delta = animatedOffset - placementOffset
            // The wobble goes against the direction of travel, so a line overshoots and then settles back.
            val wobbleY =
                jiggle?.let { -wobbleDirection * it.amplitudePx * it.wobble(wobble.value) } ?: 0f
            placeable.place(delta.x, delta.y + wobbleY.roundToInt())
        }
    }

    private fun animationSpec(): FiniteAnimationSpec<IntOffset> =
        if (isManualScrolling) {
            snap()
        } else {
            tween(
                durationMillis = durationMillis,
                delayMillis = delayMillis,
                easing = LyricLineMoveEasing,
            )
        }

    /** Starts the wobble when the line's target position changes (a new current line). */
    private fun noteTargetChange(target: IntOffset) {
        val previous = lastTarget
        lastTarget = target
        if (previous == null || previous == target || isManualScrolling) return
        val spec = jiggle
        wobbleJob?.cancel()
        if (spec == null) {
            wobbleJob = coroutineScope.launch { wobble.snapTo(0f) }
            return
        }
        wobbleDirection = if (target.y - previous.y < 0) 1f else -1f
        val wait = delayMillis
        wobbleJob =
            coroutineScope.launch {
                wobble.snapTo(0f)
                wobble.animateTo(1f, tween(durationMillis = spec.durationMillis, delayMillis = wait, easing = LinearEasing))
            }
    }

    /** 首次组合的底部歌词先下移，再补间到目标位置。 */
    private fun updatePlacementTarget(target: IntOffset): IntOffset {
        val host = foldHost
        val syncTick = host?.syncTick ?: 0
        val reaimed = syncTick != seenSyncTick
        seenSyncTick = syncTick
        val previous = lastTarget
        if (previous != null &&
            previous != target &&
            host != null &&
            !reaimed &&
            !isManualScrolling &&
            host.isFoldDriven()
        ) {
            // A background vocal is folding: this line is pushed rigidly, like plain layout does in the HTML.
            rigidShift += target - previous
            lastTarget = target
        } else {
            noteTargetChange(target)
        }
        val base = target - rigidShift
        return animateBase(base) + rigidShift
    }

    private fun animateBase(base: IntOffset): IntOffset {
        if (isFirstFrame) {
            isFirstFrame = false
            if (initialOffsetY != 0 && !isManualScrolling) {
                offsetAnimation.updateTarget(
                    base + IntOffset(0, initialOffsetY),
                    coroutineScope,
                    snap(),
                )
                return offsetAnimation.updateTarget(base, coroutineScope, animationSpec())
            }
            return offsetAnimation.updateTarget(base, coroutineScope, snap())
        }
        return offsetAnimation.updateTarget(base, coroutineScope, animationSpec())
    }

    override fun onReset() {
        resetAnimation()
    }

    fun updateState(
        newScope: LookaheadScope,
        newKey: Any,
        newIsManualScrolling: Boolean,
        newDurationMillis: Int,
        newDelayMillis: Int,
        newJiggle: JiggleSpec?,
        newInitialOffsetY: Int,
        newFoldHost: LyricFoldHost?,
    ) {
        lookaheadScope = newScope
        isManualScrolling = newIsManualScrolling
        durationMillis = newDurationMillis
        delayMillis = newDelayMillis
        jiggle = newJiggle
        initialOffsetY = newInitialOffsetY
        foldHost = newFoldHost
        if (itemKey != newKey) {
            itemKey = newKey
            resetAnimation()
        }
    }

    private fun resetAnimation() {
        offsetAnimation = DeferredTargetAnimation(IntOffset.VectorConverter)
        isFirstFrame = true
        lastTarget = null
        rigidShift = IntOffset.Zero
        seenSyncTick = foldHost?.syncTick ?: 0
        wobbleJob?.cancel()
    }
}

private data class LinePlacementNodeElement(
    val lookaheadScope: LookaheadScope,
    val itemKey: Any,
    val isManualScrolling: Boolean,
    val durationMillis: Int,
    val delayMillis: Int,
    val jiggle: JiggleSpec?,
    val initialOffsetY: Int,
    val foldHost: LyricFoldHost?,
) : ModifierNodeElement<LinePlacementModifierNode>() {
    override fun update(node: LinePlacementModifierNode) {
        node.updateState(
            lookaheadScope,
            itemKey,
            isManualScrolling,
            durationMillis,
            delayMillis,
            jiggle,
            initialOffsetY,
            foldHost,
        )
    }

    override fun create(): LinePlacementModifierNode =
        LinePlacementModifierNode(
            lookaheadScope,
            itemKey,
            isManualScrolling,
            durationMillis,
            delayMillis,
            jiggle,
            initialOffsetY,
            foldHost,
        )
}

/** 添加歌词行位移动画，支持时长和错峰延迟。 */
internal fun Modifier.linePlacement(
    lookaheadScope: LookaheadScope,
    itemKey: Any,
    isManualScrolling: Boolean,
    durationMillis: Int,
    delayMillis: Int,
    jiggle: JiggleSpec? = null,
    initialOffsetY: Int = 0,
    foldHost: LyricFoldHost? = null,
): Modifier =
    then(
        LinePlacementNodeElement(
            lookaheadScope = lookaheadScope,
            itemKey = itemKey,
            isManualScrolling = isManualScrolling,
            durationMillis = durationMillis,
            delayMillis = delayMillis,
            jiggle = jiggle,
            initialOffsetY = initialOffsetY,
            foldHost = foldHost,
        ),
    )

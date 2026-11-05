package me.spica27.spicamusic.ui.widget

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** Appearance mode, same values as the HTML player ("auto" | "light" | "dark"). */
object VerseThemeMode {
    const val AUTO = "auto"
    const val LIGHT = "light"
    const val DARK = "dark"
}

/**
 * Colours of the player, exactly the set the HTML sets as CSS variables:
 * c1/c2/c3 = background gradient (top, middle, bottom), c4 = glow in the top-right corner,
 * fg = text / accent colour, fg2 = secondary text.
 */
data class VerseTheme(
    val c1: Color,
    val c2: Color,
    val c3: Color,
    val c4: Color,
    val fg: Color,
    val fg2: Color,
    val light: Boolean,
) {
    companion object {
        /** HTML DEFAULT_THEME (used when a song has no cover). */
        val Default =
            VerseTheme(
                c1 = Color(0xFF7D1408),
                c2 = Color(0xFF8F1A09),
                c3 = Color(0xFF4A0B04),
                c4 = Color(255, 170, 60).copy(alpha = .25f),
                fg = Color(0xFFFFC233),
                fg2 = Color(255, 194, 51).copy(alpha = .8f),
                light = false,
            )
    }
}

// ---------------------------------------------------------------------------------------------
// Colour engine (1:1 port of analyzePalette / buildTheme from the HTML)
// ---------------------------------------------------------------------------------------------

internal class Cluster(
    val rgb: IntArray,
    val n: Int,
    val h: Float,
    val s: Float,
    val l: Float,
    val w: Float,
) {
    val score: Float = sqrt(w) * (.22f + s) * (1f - .6f * abs(l - .4f))
}

/** Opaque handle to the palette of one cover, computed once per cover. */
class VersePalette internal constructor(
    internal val clusters: List<Cluster>,
)

private fun clampF(v: Float, a: Float, b: Float) = min(b, max(a, v))

private fun jsRound(v: Float): Int = kotlin.math.floor(v + .5f).toInt()

private fun rgb2hsl(r0: Int, g0: Int, b0: Int): FloatArray {
    val r = r0 / 255f
    val g = g0 / 255f
    val b = b0 / 255f
    val mx = max(r, max(g, b))
    val mn = min(r, min(g, b))
    var h = 0f
    var s = 0f
    val l = (mx + mn) / 2f
    val d = mx - mn
    if (d != 0f) {
        s = d / (1f - abs(2f * l - 1f))
        h =
            when (mx) {
                r -> ((g - b) / d) % 6f
                g -> (b - r) / d + 2f
                else -> (r - g) / d + 4f
            }
        h *= 60f
        if (h < 0f) h += 360f
    }
    return floatArrayOf(h, s, l)
}

private fun hsl2rgb(h0: Float, s: Float, l: Float): IntArray {
    val h = h0 / 360f
    fun f(n: Int): Int {
        val k = (n + h * 12f) % 12f
        val a = s * min(l, 1f - l)
        val v = l - a * max(-1f, min(min(k - 3f, 9f - k), 1f))
        return jsRound(v * 255f)
    }
    return intArrayOf(f(0), f(8), f(4))
}

private fun lum(c: IntArray): Float {
    fun f(v0: Int): Float {
        val v = v0 / 255f
        return if (v <= .03928f) v / 12.92f else ((v + .055f) / 1.055f).toDouble().pow(2.4).toFloat()
    }
    return .2126f * f(c[0]) + .7152f * f(c[1]) + .0722f * f(c[2])
}

private fun contrast(a: Float, b: Float) = (max(a, b) + .05f) / (min(a, b) + .05f)

private fun hdiff(a: Float, b: Float): Float {
    val d = abs(a - b)
    return min(d, 360f - d)
}

private fun mixRGB(a: IntArray, b: IntArray, t: Float) = IntArray(3) { jsRound(a[it] + (b[it] - a[it]) * t) }

private fun toColor(c: IntArray, a: Float = 1f) = Color(c[0].coerceIn(0, 255), c[1].coerceIn(0, 255), c[2].coerceIn(0, 255), jsRound(a * 255f).coerceIn(0, 255))

/** k-means palette of the 44x44 cover pixels, sorted by "how good a background/accent this is". */
internal fun analyzePalette(px: List<IntArray>): VersePalette? {
    if (px.size < 10) return null
    val k = 6

    fun dist(a: IntArray, b: IntArray): Float {
        val x = (a[0] - b[0]).toFloat()
        val y = (a[1] - b[1]).toFloat()
        val z = (a[2] - b[2]).toFloat()
        return x * x + y * y + z * z
    }

    val mf = FloatArray(3) { i -> px.sumOf { it[i].toDouble() }.toFloat() / px.size }
    fun dm(p: IntArray): Float {
        val x = p[0] - mf[0]
        val y = p[1] - mf[1]
        val z = p[2] - mf[2]
        return x * x + y * y + z * z
    }
    val cs = ArrayList<FloatArray>()
    val first = px.reduce { b, p -> if (dm(p) < dm(b)) p else b }
    cs.add(FloatArray(3) { first[it].toFloat() })
    fun distF(p: IntArray, c: FloatArray): Float {
        val x = p[0] - c[0]
        val y = p[1] - c[1]
        val z = p[2] - c[2]
        return x * x + y * y + z * z
    }
    while (cs.size < k) {
        var best: IntArray? = null
        var bd = -1f
        for (p in px) {
            var m = Float.MAX_VALUE
            for (q in cs) m = min(m, distF(p, q))
            if (m > bd) {
                bd = m
                best = p
            }
        }
        cs.add(FloatArray(3) { best!![it].toFloat() })
    }
    var cnt = IntArray(k)
    repeat(8) {
        val sum = Array(k) { FloatArray(3) }
        cnt = IntArray(k)
        for (p in px) {
            var bi = 0
            var bd = Float.MAX_VALUE
            cs.forEachIndexed { i, c ->
                val d = distF(p, c)
                if (d < bd) {
                    bd = d
                    bi = i
                }
            }
            cnt[bi]++
            sum[bi][0] += p[0]
            sum[bi][1] += p[1]
            sum[bi][2] += p[2]
        }
        cs.forEachIndexed { i, c ->
            if (cnt[i] > 0) {
                c[0] = sum[i][0] / cnt[i]
                c[1] = sum[i][1] / cnt[i]
                c[2] = sum[i][2] / cnt[i]
            }
        }
    }
    val total = px.size.toFloat()
    val cl =
        cs
            .mapIndexed { i, c -> IntArray(3) { jsRound(c[it]) } to cnt[i] }
            .filter { it.second > 0 }
            .map { (rgb, n) ->
                val hsl = rgb2hsl(rgb[0], rgb[1], rgb[2])
                Cluster(rgb, n, hsl[0], hsl[1], hsl[2], n / total)
            }.sortedByDescending { it.score }
    return VersePalette(cl)
}

/**
 * @param sat   saturation multiplier 0..2 (1 = original)
 * @param vib   vibrancy -1..1 (0 = original): glow, colour blending, brightness
 * @param mode  "auto" | "light" | "dark"
 * @param hue   highlight hue shift in degrees (-180..180, 0 = auto)
 */
fun buildVerseTheme(
    palette: VersePalette,
    sat: Float = 1f,
    vib: Float = 0f,
    mode: String = VerseThemeMode.AUTO,
    hue: Int = 0,
): VerseTheme {
    val cl = palette.clusters
    val satC = clampF(sat, 0f, 2f)
    val v = clampF(vib, -1f, 1f)
    fun ss(x: Float) = clampF(x * satC, 0f, 1f)

    fun fitDark(h: Float, s: Float, l0: Float, maxL: Float): Float {
        var l = l0
        while (l > .04f && lum(hsl2rgb(h, s, l)) > maxL) l -= .01f
        return l
    }

    val vivid = cl.maxByOrNull { it.s * sqrt(it.w) }!!
    val base = cl[0]
    var h = base.h
    var s = base.s
    var l = base.l
    val dark = cl.firstOrNull { it.l < .14f && it.w > .42f }
    if (dark != null) {
        h = vivid.h
        s = .38f
        l = .085f
    } else {
        if (s > .1f) s = clampF(s * 1.1f, .3f, .92f)
        l = if (l > .66f) clampF(l, .72f, .86f) else clampF(l, .13f, .44f)
    }
    if (mode == VerseThemeMode.LIGHT) {
        l = if (l > .66f) clampF(l, .72f, .86f) else .8f
    } else if (mode == VerseThemeMode.DARK && dark == null) {
        l = clampF(l, .13f, .44f)
    }
    val s0 = s
    s = ss(s)
    l = clampF(l + v * .04f, .05f, .92f)
    if (mode == VerseThemeMode.DARK) {
        // top of the gradient stays dark enough for light text
        l = max(.05f, fitDark(h, s, l + .05f, .13f) - .05f)
    }
    val bg = hsl2rgb(h, s, l)
    val bgL = lum(bg)
    val light =
        when (mode) {
            VerseThemeMode.LIGHT -> true
            VerseThemeMode.DARK -> false
            else -> bgL > .36f
        }

    class Cand(
        val h: Float,
        val s: Float,
        val l: Float,
        val base: Float,
    ) {
        var rgb: IntArray = IntArray(3)
        var ok: Boolean = false
    }

    val cands = ArrayList<Cand>()
    cl.forEach { k ->
        if (k === base && dark == null) return@forEach
        cands.add(
            Cand(
                k.h,
                k.s,
                k.l,
                (.2f + k.s) * (.6f + .4f * min(1f, hdiff(k.h, h) / 70f)) * (.4f + sqrt(k.w) * 1.2f),
            ),
        )
    }
    cands.add(Cand(h, max(s0, .5f) * .9f, .5f, .42f))
    cands.forEach { c ->
        val cs2 = ss(if (c.s < .1f && hue == 0) c.s else clampF(c.s, .32f, .95f))
        val ch = (c.h + hue + 360f) % 360f
        var ll = c.l
        for (i in 0 until 40) {
            if (contrast(lum(hsl2rgb(ch, cs2, ll)), bgL) >= 5f) break
            ll = clampF(ll + (if (light) -.03f else .03f), .05f, .95f)
        }
        c.rgb = hsl2rgb(ch, cs2, ll)
        c.ok = contrast(lum(c.rgb), bgL) >= 4.5f
    }
    val accRgb =
        cands
            .filter { it.ok }
            .sortedByDescending { it.base }
            .firstOrNull()
            ?.rgb
            ?: if (light) intArrayOf(20, 20, 20) else intArrayOf(245, 245, 245)

    val second = cl.firstOrNull { it !== base && hdiff(it.h, h) > 35f && it.s > .3f && it.w > .03f }
    val top = hsl2rgb(h, s, clampF(l + (if (light) .03f else .05f), 0f, .92f))
    val mid =
        if (second != null) {
            mixRGB(
                bg,
                hsl2rgb(second.h, ss(clampF(second.s, .3f, .9f)), clampF(second.l, .2f, if (light) .8f else .45f)),
                clampF(.38f + .2f * v, .15f, .6f),
            )
        } else {
            bg
        }
    val bot = hsl2rgb(h, s, if (light) l * .9f else l * .55f)
    val c4 =
        if (second != null) {
            toColor(hsl2rgb(second.h, ss(clampF(second.s, .4f, .95f)), .55f), clampF(.5f + .3f * v, .2f, .85f))
        } else {
            toColor(accRgb, clampF(.16f + .14f * v, .05f, .4f))
        }
    return VerseTheme(
        c1 = toColor(top),
        c2 = toColor(mid),
        c3 = toColor(bot),
        c4 = c4,
        fg = toColor(accRgb),
        fg2 = toColor(accRgb, .78f),
        light = light,
    )
}

// ---------------------------------------------------------------------------------------------
// Palette extraction + remember helpers
// ---------------------------------------------------------------------------------------------

private suspend fun extractVersePalette(
    context: android.content.Context,
    uri: Uri?,
): VersePalette? =
    withContext(Dispatchers.IO) {
        if (uri == null) return@withContext null
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 88 && bounds.outHeight / (sample * 2) >= 88) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val src =
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                    ?: return@withContext null
            // same 44x44 sampling as the HTML (alpha > 200 only)
            val n = 44
            val scaled = Bitmap.createScaledBitmap(src, n, n, true)
            val pixels = IntArray(n * n)
            scaled.getPixels(pixels, 0, n, 0, 0, n, n)
            if (scaled !== src) scaled.recycle()
            src.recycle()
            val px = ArrayList<IntArray>(pixels.size)
            for (p in pixels) {
                if (((p ushr 24) and 0xFF) > 200) px.add(intArrayOf((p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF))
            }
            analyzePalette(px)
        } catch (e: Exception) {
            null
        }
    }

/**
 * Theme of the current cover. The (slow) palette analysis runs once per cover; moving the
 * sliders only re-runs the cheap [buildVerseTheme].
 */
@Composable
fun rememberVerseTheme(
    coverUri: Uri?,
    mode: String,
    saturationPercent: Int,
    vibrancyPercent: Int,
    hueShift: Int,
): VerseTheme {
    val context = LocalContext.current
    var palette by remember(coverUri) { mutableStateOf<VersePalette?>(null) }
    LaunchedEffect(coverUri) { palette = extractVersePalette(context, coverUri) }
    val target =
        remember(palette, mode, saturationPercent, vibrancyPercent, hueShift) {
            palette?.let {
                buildVerseTheme(
                    palette = it,
                    sat = saturationPercent / 100f,
                    vib = (vibrancyPercent - 50) / 50f,
                    mode = mode,
                    hue = hueShift,
                )
            } ?: VerseTheme.Default
        }
    // smooth cross-fade between songs / when sliders move (HTML uses a colour transition too)
    val spec = tween<Color>(durationMillis = 450)
    val c1 by animateColorAsState(target.c1, spec, label = "verse_c1")
    val c2 by animateColorAsState(target.c2, spec, label = "verse_c2")
    val c3 by animateColorAsState(target.c3, spec, label = "verse_c3")
    val c4 by animateColorAsState(target.c4, spec, label = "verse_c4")
    val fg by animateColorAsState(target.fg, spec, label = "verse_fg")
    val fg2 by animateColorAsState(target.fg2, spec, label = "verse_fg2")
    return VerseTheme(c1, c2, c3, c4, fg, fg2, target.light)
}

/**
 * Makes every Material text / icon / slider inside use the Verse colours (text = fg,
 * secondary text = fg2, controls = fg on c1), so the whole player looks like the HTML.
 */
@Composable
fun VerseFlowThemeScope(
    theme: VerseTheme,
    content: @Composable () -> Unit,
) {
    val base = MaterialTheme.colorScheme
    val scheme =
        remember(base, theme) {
            base.copy(
                primary = theme.fg,
                onPrimary = theme.c1,
                primaryContainer = theme.fg.copy(alpha = .26f),
                onPrimaryContainer = theme.fg,
                secondary = theme.fg,
                onSecondary = theme.c1,
                secondaryContainer = theme.fg.copy(alpha = .26f),
                onSecondaryContainer = theme.fg,
                tertiary = theme.fg,
                onTertiary = theme.c1,
                surface = theme.c1,
                onSurface = theme.fg,
                onSurfaceVariant = theme.fg2,
                surfaceVariant = theme.fg.copy(alpha = .26f),
                surfaceTint = theme.fg,
                background = theme.c1,
                onBackground = theme.fg,
                outline = theme.fg2,
                outlineVariant = theme.fg.copy(alpha = .26f),
                surfaceContainerLowest = theme.c3.copy(alpha = .5f),
                surfaceContainerLow = theme.c3.copy(alpha = .6f),
                surfaceContainer = theme.c3.copy(alpha = .78f),
                surfaceContainerHigh = theme.c3.copy(alpha = .86f),
                // seek-bar track in the HTML: fg at 26 %
                surfaceContainerHighest = theme.fg.copy(alpha = .26f),
                inverseSurface = theme.fg,
                inverseOnSurface = theme.c1,
            )
        }
    MaterialTheme(
        colorScheme = scheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content,
    )
}

// ---------------------------------------------------------------------------------------------
// Background: `.bg` gradient (+ the `.mesh` drifting blobs in lyrics view)
// ---------------------------------------------------------------------------------------------

/**
 * CSS:
 *   radial-gradient(110% 60% at 92% 4%, var(--c4), transparent 62%),
 *   linear-gradient(180deg, var(--c1) 0%, var(--c2) 55%, var(--c3) 100%)
 * and, while the lyrics are shown, four slowly drifting radial blobs at 48 % opacity.
 */
@Composable
fun VerseFlowBackground(
    theme: VerseTheme,
    showMesh: Boolean,
    modifier: Modifier = Modifier,
    meshAnimated: Boolean = true,
) {
    val meshAlpha by animateFloatAsState(
        targetValue = if (showMesh) .48f else 0f,
        animationSpec = tween(600),
        label = "verse_mesh_alpha",
    )
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawRect(
                brush =
                    Brush.verticalGradient(
                        0f to theme.c1,
                        .55f to theme.c2,
                        1f to theme.c3,
                    ),
            )
            // ellipse 110% x 60% centred at (92 %, 4 %), fading out at 62 %
            val cx = w * .92f
            val cy = h * .04f
            val rx = w * 1.10f
            val ry = h * .60f
            scale(scaleX = 1f, scaleY = ry / rx, pivot = Offset(cx, cy)) {
                drawRect(
                    brush =
                        Brush.radialGradient(
                            colors = listOf(theme.c4, theme.c4.copy(alpha = 0f)),
                            center = Offset(cx, cy),
                            radius = rx * .62f,
                        ),
                    topLeft = Offset(-w * 4f, -h * 4f),
                    size = Size(w * 9f, h * 9f),
                )
            }
        }
        if (meshAlpha > 0.001f) {
            VerseMesh(theme = theme, alpha = meshAlpha, animated = meshAnimated, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun VerseMesh(
    theme: VerseTheme,
    alpha: Float,
    animated: Boolean,
    modifier: Modifier = Modifier,
) {
    val t = rememberInfiniteTransition(label = "verse_mesh")

    @Composable
    fun phase(seconds: Int) =
        if (animated) {
            t
                .animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec =
                        infiniteRepeatable(
                            animation = tween(seconds * 1000, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse,
                        ),
                    label = "verse_mesh_$seconds",
                ).value
        } else {
            0f
        }

    val p1 = phase(24)
    val p2 = phase(31)
    val p3 = phase(28)
    val p4 = phase(35)
    val m1 = lerp(theme.c2, theme.fg, .60f)
    val m2 = theme.c4
    val m3 = lerp(theme.c3, theme.fg, .35f)
    val m4 = lerp(theme.c2, theme.fg, .25f)

    Canvas(modifier = modifier.graphicsLayer { this.alpha = alpha }) {
        val w = size.width
        val h = size.height
        val d = w * .95f

        fun blob(
            color: Color,
            x0: Float,
            y0: Float,
            tx: Float,
            ty: Float,
            sc: Float,
            p: Float,
        ) {
            val cx = x0 + d / 2f + d * tx * p
            val cy = y0 + d / 2f + d * ty * p
            val r = d / 2f * (1f + (sc - 1f) * p)
            drawCircle(
                brush = Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), center = Offset(cx, cy), radius = r),
                radius = r,
                center = Offset(cx, cy),
            )
        }
        blob(m1, -.25f * w, -.08f * h, .38f, .22f, 1.18f, p1)
        blob(m2, w + .32f * w - d, .16f * h, -.34f, .30f, .90f, p2)
        blob(m3, -.22f * w, h - .10f * h - d, .30f, -.28f, 1.15f, p3)
        blob(m4, w + .22f * w - d, h + .10f * h - d, -.36f, -.20f, 1.05f, p4)
    }
}

// Oklch.kt
// Write colors in OKLCH, the same space as CSS `oklch(L C H)`.
//
//   Color.oklch(0.55, 0.15, 262)                 // Jetpack Compose
//   Color.oklch(0.55, 0.15, 262, alpha = 0.5f)
//   oklchColorInt(0.55, 0.15, 262)              // Views / XML-era APIs (sRGB)
//   oklchColorLong(0.55, 0.15, 262)             // Views, Display P3 (API 26+)
//
//   val brand = Oklch(0.55, 0.15, 262.0)         // value type
//   brand.mix(Color.Red.toOklch(), 0.5).toColor()
//
// L: lightness 0..1, C: chroma 0..~0.37, H: hue in degrees.
// Compose colors use Display P3 by default (sRGB optional). Colors outside the
// target gamut are mapped with the CSS Color 4 algorithm: chroma drops at
// fixed L and H until clipping is no longer noticeable.
// P3 only shows on screen when the Activity opts in with
// android:colorMode="wideColorGamut"; otherwise Android converts it to sRGB.
// Math: Björn Ottosson, "A perceptual color space for image processing".

package studio.etsoftware.oklch

import android.graphics.ColorSpace
import androidx.annotation.ColorInt
import androidx.annotation.ColorLong
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.colorspace.connect
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

enum class OklchGamut { DisplayP3, SRGB }

/**
 * A color in OKLCH. The hue [h] is in degrees. The constructor wraps it to 0..<360,
 * so `Oklch(0.7, 0.1, -30.0)` equals `Oklch(0.7, 0.1, 330.0)`.
 *
 * Equality, `hashCode`, [copy] and destructuring work as in a data class. [copy] also wraps the hue.
 */
@Immutable
class Oklch(
    /** Lightness, 0..1. */
    val l: Double,
    /** Chroma, 0..~0.37. */
    val c: Double,
    h: Double,
    /** Opacity, 0..1. */
    val alpha: Float = 1f,
) {
    /** Hue in degrees, 0..<360. */
    val h: Double = wrapHue(h)

    /**
     * Mixes this color with [other] in OKLCH, as CSS `color-mix(in oklch)` does.
     * `t = 0` gives this color and `t = 1` gives [other]. The hue takes the shorter way round.
     *
     * - **Greys** — A color with chroma below 0.000004 has no hue. It takes the hue of the
     *   other color. When both colors are grey, the hue is 0.
     * - **Alpha** — L and C are mixed premultiplied by alpha. A transparent end does not pull
     *   L and C toward its own values.
     * - **Range** — [t] is not clamped. Values outside 0..1 extrapolate, for example in a spring.
     */
    fun mix(other: Oklch, t: Double): Oklch {
        val grey1 = c < POWERLESS_CHROMA
        val grey2 = other.c < POWERLESS_CHROMA
        val hue = if (grey1 && grey2) {
            0.0
        } else {
            var h1 = if (grey1) other.h else h
            var h2 = if (grey2) h else other.h
            val d = h2 - h1
            if (d > 180) h1 += 360 else if (d < -180) h2 += 360
            h1 + (h2 - h1) * t
        }

        val a1 = alpha.toDouble()
        val a2 = other.alpha.toDouble()
        val a = a1 + (a2 - a1) * t
        return if (a == 0.0) {
            Oklch(l + (other.l - l) * t, c + (other.c - c) * t, hue, 0f)
        } else {
            Oklch(
                (l * a1 + (other.l * a2 - l * a1) * t) / a,
                (c * a1 + (other.c * a2 - c * a1) * t) / a,
                hue,
                a.toFloat(),
            )
        }
    }

    /** Jetpack Compose color in [gamut]. Same result as `Color.oklch(l, c, h, alpha, gamut)`. */
    fun toColor(gamut: OklchGamut = OklchGamut.DisplayP3): Color = oklchColor(l, c, h, alpha, gamut)

    fun copy(l: Double = this.l, c: Double = this.c, h: Double = this.h, alpha: Float = this.alpha): Oklch =
        Oklch(l, c, h, alpha)

    operator fun component1(): Double = l
    operator fun component2(): Double = c
    operator fun component3(): Double = h
    operator fun component4(): Float = alpha

    override fun equals(other: Any?): Boolean =
        other is Oklch &&
            l.compareTo(other.l) == 0 &&
            c.compareTo(other.c) == 0 &&
            h.compareTo(other.h) == 0 &&
            alpha.compareTo(other.alpha) == 0

    override fun hashCode(): Int {
        var result = l.hashCode()
        result = 31 * result + c.hashCode()
        result = 31 * result + h.hashCode()
        result = 31 * result + alpha.hashCode()
        return result
    }

    override fun toString(): String = "Oklch(l=$l, c=$c, h=$h, alpha=$alpha)"

    data class Rgb(val red: Double, val green: Double, val blue: Double)

    companion object {
        // Below this chroma the hue is powerless (CSS Color 4, as color.js).
        private const val POWERLESS_CHROMA = 0.000004

        /**
         * Converts a Compose color in any color space to OKLCH.
         * A color with chroma below 0.000004 is a grey, and its hue is 0.
         */
        fun from(color: Color): Oklch {
            // Connector.transform keeps Float precision. Color.convert rounds to half floats.
            val linear = color.colorSpace.connect(ColorSpaces.LinearExtendedSrgb)
                .transform(color.red, color.green, color.blue)
            return fromLinear(
                doubleArrayOf(linear[0].toDouble(), linear[1].toDouble(), linear[2].toDouble()),
                OklchGamut.SRGB,
                color.alpha,
            )
        }

        /** Converts OKLCH to gamma-encoded RGB components (0..1) in the given gamut. */
        fun rgb(l: Double, c: Double, h: Double, gamut: OklchGamut = OklchGamut.DisplayP3): Rgb =
            encodedRgb(l, c, h, gamut) { r, g, b -> Rgb(r, g, b) }

        // Gamma-encoded RGB in the given gamut -> OKLCH, all in Double. The tests use it to
        // check the math without the rounding of the Compose Color storage.
        internal fun fromEncoded(r: Double, g: Double, b: Double, gamut: OklchGamut, alpha: Float = 1f): Oklch =
            fromLinear(doubleArrayOf(decode(r), decode(g), decode(b)), gamut, alpha)

        private fun fromLinear(linear: DoubleArray, gamut: OklchGamut, alpha: Float): Oklch {
            val (l, a, b) = oklab(linear, gamut)
            val c = hypot(a, b)
            val h = if (c < POWERLESS_CHROMA) 0.0 else atan2(b, a) * 180 / PI
            return Oklch(l, c, h, alpha)
        }

        private fun wrapHue(h: Double): Double {
            val r = h % 360.0
            // "+ 0.0" turns -0.0 into 0.0. A tiny negative r rounds up to 360 when we add 360.
            val wrapped = if (r < 0) r + 360.0 else r + 0.0
            return if (wrapped >= 360.0) 0.0 else wrapped
        }
    }
}

/** Converts this color to OKLCH. Same as `Oklch.from(this)`. */
fun Color.toOklch(): Oklch = Oklch.from(this)

/** Jetpack Compose color from OKLCH. Display P3 by default. */
fun Color.Companion.oklch(
    l: Number,
    c: Number,
    h: Number,
    alpha: Float = 1f,
    gamut: OklchGamut = OklchGamut.DisplayP3,
): Color = oklchColor(l.toDouble(), c.toDouble(), h.toDouble(), alpha, gamut)

// Takes Doubles, not Numbers, so Oklch.toColor does not box its components.
private fun oklchColor(l: Double, c: Double, h: Double, alpha: Float, gamut: OklchGamut): Color {
    val space = if (gamut == OklchGamut.DisplayP3) ColorSpaces.DisplayP3 else ColorSpaces.Srgb
    val a = alpha.coerceIn(0f, 1f)
    return encodedRgb(l, c, h, gamut) { r, g, b -> Color(r.toFloat(), g.toFloat(), b.toFloat(), a, space) }
}

/** sRGB @ColorInt for Views and other APIs that take an Int color. Works on every API level. */
@ColorInt
fun oklchColorInt(l: Number, c: Number, h: Number, alpha: Float = 1f): Int {
    val a = (alpha.coerceIn(0f, 1f) * 255).roundToInt()
    return encodedRgb(l.toDouble(), c.toDouble(), h.toDouble(), OklchGamut.SRGB) { r, g, b ->
        (a shl 24) or ((r * 255).roundToInt() shl 16) or ((g * 255).roundToInt() shl 8) or (b * 255).roundToInt()
    }
}

/** Display P3 @ColorLong for Views (Paint.setColor(Long), etc.). */
@RequiresApi(26)
@ColorLong
fun oklchColorLong(l: Number, c: Number, h: Number, alpha: Float = 1f): Long {
    val a = alpha.coerceIn(0f, 1f)
    return encodedRgb(l.toDouble(), c.toDouble(), h.toDouble(), OklchGamut.DisplayP3) { r, g, b ->
        android.graphics.Color.pack(
            r.toFloat(), g.toFloat(), b.toFloat(), a,
            ColorSpace.get(ColorSpace.Named.DISPLAY_P3),
        )
    }
}

// OKLCH -> gamma-encoded RGB (0..1) in the given gamut, passed to [block] as three
// Doubles. Colors inside the gamut, the common case, allocate nothing; only gamut
// mapping uses arrays. Every public conversion goes through here.
private inline fun <T> encodedRgb(
    l: Double,
    c: Double,
    h: Double,
    gamut: OklchGamut,
    block: (Double, Double, Double) -> T,
): T {
    val lc = l.coerceIn(0.0, 1.0)
    if (lc >= 1) return block(1.0, 1.0, 1.0)
    if (lc <= 0) return block(0.0, 0.0, 0.0)
    val cc = c.coerceAtLeast(0.0)
    return withLinearRgb(lc, cc, h, gamut) { r, g, b ->
        if (inGamut(r, g, b)) {
            block(encode(r), encode(g), encode(b))
        } else {
            val v = mapToGamut(lc, cc, h, gamut, doubleArrayOf(r, g, b))
            block(encode(v[0]), encode(v[1]), encode(v[2]))
        }
    }
}

// CSS Color 4 gamut mapping (section 13.2.2): bisect chroma at fixed L and H,
// stopping once clipping the color changes it by less than a just-noticeable
// difference in OKLab, then return the clipped color.
// encodedRgb calls it only for colors outside the gamut with 0 < l < 1, so the
// arrays here cost nothing in the common case.
private fun mapToGamut(l: Double, c: Double, h: Double, gamut: OklchGamut, origin: DoubleArray): DoubleArray {
    val jnd = 0.02
    val epsilon = 0.0001
    var clipped = clip(origin)
    if (deltaEOK(clipped, oklab(l, c, h), gamut) < jnd) return clipped

    var low = 0.0
    var high = c
    var lowInGamut = true
    while (high - low > epsilon) {
        val chroma = (low + high) / 2
        val current = linearRgb(l, chroma, h, gamut)
        if (lowInGamut && inGamut(current)) {
            low = chroma
            continue
        }
        clipped = clip(current)
        val e = deltaEOK(clipped, oklab(l, chroma, h), gamut)
        if (e < jnd) {
            if (jnd - e < epsilon) return clipped
            lowInGamut = false
            low = chroma
        } else {
            high = chroma
        }
    }
    return clipped
}

private fun oklab(l: Double, c: Double, h: Double): DoubleArray {
    val hr = h * PI / 180
    return doubleArrayOf(l, c * cos(hr), c * sin(hr))
}

private fun linearRgb(l: Double, c: Double, h: Double, gamut: OklchGamut): DoubleArray =
    withLinearRgb(l, c, h, gamut) { r, g, b -> doubleArrayOf(r, g, b) }

// OKLCH -> linear RGB in the given gamut, passed to [block] as three Doubles so the
// in-gamut path allocates nothing.
private inline fun <T> withLinearRgb(
    l: Double,
    c: Double,
    h: Double,
    gamut: OklchGamut,
    block: (Double, Double, Double) -> T,
): T {
    val hr = h * PI / 180
    val lab0 = l
    val a = c * cos(hr)
    val b = c * sin(hr)

    val l_ = lab0 + 0.3963377774 * a + 0.2158037573 * b
    val m_ = lab0 - 0.1055613458 * a - 0.0638541728 * b
    val s_ = lab0 - 0.0894841775 * a - 1.2914855480 * b
    val lc = l_ * l_ * l_
    val mc = m_ * m_ * m_
    val sc = s_ * s_ * s_

    val r = 4.0767416621 * lc - 3.3077115913 * mc + 0.2309699292 * sc
    val g = -1.2684380046 * lc + 2.6097574011 * mc - 0.3413193965 * sc
    val bl = -0.0041960863 * lc - 0.7034186147 * mc + 1.7076147010 * sc

    return when (gamut) {
        OklchGamut.SRGB -> block(r, g, bl)
        // Linear sRGB -> linear Display P3.
        OklchGamut.DisplayP3 -> block(
            0.8224621209 * r + 0.1775378791 * g,
            0.0331941989 * r + 0.9668058011 * g,
            0.0170826307 * r + 0.0723974407 * g + 0.9105199286 * bl,
        )
    }
}

// Linear RGB in the given gamut -> OKLab (Ottosson's forward matrices).
// cbrt keeps the sign, so values outside 0..1 (extended sRGB) work.
private fun oklab(linear: DoubleArray, gamut: OklchGamut): DoubleArray {
    var (r, g, b) = linear
    if (gamut == OklchGamut.DisplayP3) {
        // Linear Display P3 -> linear sRGB.
        r = 1.2249401763 * linear[0] - 0.2249401763 * linear[1]
        g = -0.0420569547 * linear[0] + 1.0420569547 * linear[1]
        b = -0.0196375546 * linear[0] - 0.0786360456 * linear[1] + 1.0982736002 * linear[2]
    }
    val l_ = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
    val m_ = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
    val s_ = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
    return doubleArrayOf(
        0.2104542553 * l_ + 0.7936177850 * m_ - 0.0040720468 * s_,
        1.9779984951 * l_ - 2.4285922050 * m_ + 0.4505937099 * s_,
        0.0259040371 * l_ + 0.7827717662 * m_ - 0.8086757660 * s_,
    )
}

private fun deltaEOK(linear: DoubleArray, lab: DoubleArray, gamut: OklchGamut): Double {
    val other = oklab(linear, gamut)
    val dl = other[0] - lab[0]
    val da = other[1] - lab[1]
    val db = other[2] - lab[2]
    return sqrt(dl * dl + da * da + db * db)
}

private fun clip(v: DoubleArray): DoubleArray = DoubleArray(3) { v[it].coerceIn(0.0, 1.0) }

private fun inGamut(v: DoubleArray): Boolean = inGamut(v[0], v[1], v[2])

private fun inGamut(r: Double, g: Double, b: Double): Boolean {
    val e = 1e-6
    return r >= -e && r <= 1 + e && g >= -e && g <= 1 + e && b >= -e && b <= 1 + e
}

// sRGB and Display P3 share this transfer function.
private fun encode(v: Double): Double {
    val x = v.coerceIn(0.0, 1.0)
    return if (x <= 0.0031308) 12.92 * x else 1.055 * x.pow(1 / 2.4) - 0.055
}

// The inverse of encode. It keeps the sign, as extended sRGB does.
private fun decode(v: Double): Double {
    val x = abs(v)
    val linear = if (x <= 0.04045) x / 12.92 else ((x + 0.055) / 1.055).pow(2.4)
    return sign(v) * linear
}

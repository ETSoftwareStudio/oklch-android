// Oklch.kt
// Write colors in OKLCH, the same space as CSS `oklch(L C H)`.
//
//   Color.oklch(0.55, 0.15, 262)                 // Jetpack Compose
//   Color.oklch(0.55, 0.15, 262, alpha = 0.5f)
//   oklchColorInt(0.55, 0.15, 262)              // Views / XML-era APIs (sRGB)
//   oklchColorLong(0.55, 0.15, 262)             // Views, Display P3 (API 26+)
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import kotlin.math.PI
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

enum class OklchGamut { DisplayP3, SRGB }

object Oklch {
    data class Rgb(val red: Double, val green: Double, val blue: Double)

    /** Converts OKLCH to gamma-encoded RGB components (0..1) in the given gamut. */
    fun rgb(l: Double, c: Double, h: Double, gamut: OklchGamut = OklchGamut.DisplayP3): Rgb {
        val linear = mapToGamut(l.coerceIn(0.0, 1.0), c.coerceAtLeast(0.0), h, gamut)
        return Rgb(encode(linear[0]), encode(linear[1]), encode(linear[2]))
    }

    // CSS Color 4 gamut mapping (section 13.2.2): bisect chroma at fixed L and H,
    // stopping once clipping the color changes it by less than a just-noticeable
    // difference in OKLab, then return the clipped color.
    private fun mapToGamut(l: Double, c: Double, h: Double, gamut: OklchGamut): DoubleArray {
        if (l >= 1) return doubleArrayOf(1.0, 1.0, 1.0)
        if (l <= 0) return doubleArrayOf(0.0, 0.0, 0.0)
        val origin = linearRgb(l, c, h, gamut)
        if (inGamut(origin)) return origin

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

    private fun linearRgb(l: Double, c: Double, h: Double, gamut: OklchGamut): DoubleArray {
        val (lab0, a, b) = oklab(l, c, h)

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
            OklchGamut.SRGB -> doubleArrayOf(r, g, bl)
            // Linear sRGB -> linear Display P3.
            OklchGamut.DisplayP3 -> doubleArrayOf(
                0.8224621209 * r + 0.1775378791 * g,
                0.0331941989 * r + 0.9668058011 * g,
                0.0170826307 * r + 0.0723974407 * g + 0.9105199286 * bl,
            )
        }
    }

    // Linear RGB in the given gamut -> OKLab (Ottosson's forward matrices).
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

    private fun inGamut(v: DoubleArray): Boolean {
        val e = 1e-6
        return v.all { it >= -e && it <= 1 + e }
    }

    // sRGB and Display P3 share this transfer function.
    private fun encode(v: Double): Double {
        val x = v.coerceIn(0.0, 1.0)
        return if (x <= 0.0031308) 12.92 * x else 1.055 * x.pow(1 / 2.4) - 0.055
    }
}

/** Jetpack Compose color from OKLCH. Display P3 by default. */
fun Color.Companion.oklch(
    l: Number,
    c: Number,
    h: Number,
    alpha: Float = 1f,
    gamut: OklchGamut = OklchGamut.DisplayP3,
): Color {
    val v = Oklch.rgb(l.toDouble(), c.toDouble(), h.toDouble(), gamut)
    val space = if (gamut == OklchGamut.DisplayP3) ColorSpaces.DisplayP3 else ColorSpaces.Srgb
    return Color(v.red.toFloat(), v.green.toFloat(), v.blue.toFloat(), alpha.coerceIn(0f, 1f), space)
}

/** sRGB @ColorInt for Views and other APIs that take an Int color. Works on every API level. */
@ColorInt
fun oklchColorInt(l: Number, c: Number, h: Number, alpha: Float = 1f): Int {
    val v = Oklch.rgb(l.toDouble(), c.toDouble(), h.toDouble(), OklchGamut.SRGB)
    val a = (alpha.coerceIn(0f, 1f) * 255).roundToInt()
    val r = (v.red * 255).roundToInt()
    val g = (v.green * 255).roundToInt()
    val b = (v.blue * 255).roundToInt()
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}

/** Display P3 @ColorLong for Views (Paint.setColor(Long), etc.). */
@RequiresApi(26)
@ColorLong
fun oklchColorLong(l: Number, c: Number, h: Number, alpha: Float = 1f): Long {
    val v = Oklch.rgb(l.toDouble(), c.toDouble(), h.toDouble(), OklchGamut.DisplayP3)
    return android.graphics.Color.pack(
        v.red.toFloat(), v.green.toFloat(), v.blue.toFloat(), alpha.coerceIn(0f, 1f),
        ColorSpace.get(ColorSpace.Named.DISPLAY_P3),
    )
}

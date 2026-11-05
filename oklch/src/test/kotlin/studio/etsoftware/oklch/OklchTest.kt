package studio.etsoftware.oklch

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class OklchTest {
    @Test
    fun whiteAndBlackMapToGamutEdges() {
        assertRgb(Oklch.rgb(1.0, 0.0, 0.0, OklchGamut.SRGB), 1.0, 1.0, 1.0)
        assertRgb(Oklch.rgb(0.0, 0.0, 0.0, OklchGamut.SRGB), 0.0, 0.0, 0.0)
        assertRgb(Oklch.rgb(1.0, 0.0, 0.0, OklchGamut.DisplayP3), 1.0, 1.0, 1.0)
    }

    @Test
    fun knownColorsMatchSrgb() {
        // Reference values from CSS Color 4 (oklch() -> srgb).
        assertRgb(Oklch.rgb(0.6279554, 0.2576833, 29.2339, OklchGamut.SRGB), 1.0, 0.0, 0.0)
        assertRgb(Oklch.rgb(0.8664396, 0.2948272, 142.4953, OklchGamut.SRGB), 0.0, 1.0, 0.0)
        assertRgb(Oklch.rgb(0.4520137, 0.3132144, 264.0520, OklchGamut.SRGB), 0.0, 0.0, 1.0)
    }

    @Test
    fun srgbRedFitsInsideDisplayP3() {
        val rgb = Oklch.rgb(0.6279554, 0.2576833, 29.2339, OklchGamut.DisplayP3)

        assertTrue(rgb.red < 1)
        assertTrue(rgb.green > 0)
    }

    @Test
    fun outOfGamutColorsMatchCssColor4() {
        // Reference: color.js 0.5.2 (by the CSS Color 4 editors), toGamut({ method: "css" }).
        // Same cases as the Swift package, so both platforms give the same colors.
        val cases = listOf(
            Case(0.434, 0.341, 260.9, doubleArrayOf(0.0, 0.0, 0.970663), doubleArrayOf(0.0, 0.0, 0.931551)),
            Case(0.323, 0.378, 260.4, doubleArrayOf(0.0, 0.0, 0.666005), doubleArrayOf(0.0, 0.0, 0.638161)),
            Case(0.938, 0.255, 98.4, doubleArrayOf(1.0, 0.919207, 0.0), doubleArrayOf(1.0, 0.921756, 0.150577)),
            Case(0.7, 0.4, 150.0, doubleArrayOf(0.0, 0.760678, 0.280818), doubleArrayOf(0.0, 0.782484, 0.194446)),
            Case(0.65, 0.3, 30.0, doubleArrayOf(1.0, 0.075194, 0.0), doubleArrayOf(1.0, 0.016388, 0.0)),
            Case(0.5, 0.35, 320.0, doubleArrayOf(0.621752, 0.0, 0.736444), doubleArrayOf(0.578124, 0.0, 0.732095)),
            Case(0.9, 0.2, 200.0, doubleArrayOf(0.0, 0.996949, 1.0), doubleArrayOf(0.249846, 0.992796, 1.0)),
            Case(0.25, 0.3, 140.0, doubleArrayOf(0.0, 0.172754, 0.0), doubleArrayOf(0.012417, 0.173921, 0.0)),
            Case(0.8, 0.37, 60.0, doubleArrayOf(1.0, 0.626934, 0.21197), doubleArrayOf(1.0, 0.61329, 0.09717)),
        )
        for (case in cases) {
            val (sr, sg, sb) = case.srgb
            val (pr, pg, pb) = case.p3
            assertRgb(Oklch.rgb(case.l, case.c, case.h, OklchGamut.SRGB), sr, sg, sb)
            assertRgb(Oklch.rgb(case.l, case.c, case.h, OklchGamut.DisplayP3), pr, pg, pb)
        }
    }

    @Test
    fun composeColorUsesTheRequestedColorSpace() {
        assertEquals(ColorSpaces.DisplayP3, Color.oklch(0.55, 0.15, 262).colorSpace)
        assertEquals(ColorSpaces.Srgb, Color.oklch(0.55, 0.15, 262, gamut = OklchGamut.SRGB).colorSpace)
        assertEquals(0.5f, Color.oklch(0.55, 0.15, 262, alpha = 0.5f).alpha, 0.01f)
    }

    @Test
    fun colorIntPacksArgb() {
        assertEquals(0xFFFF0000.toInt(), oklchColorInt(0.6279554, 0.2576833, 29.2339))
        assertEquals(0x80FFFFFF.toInt(), oklchColorInt(1, 0, 0, alpha = 0.5f))
    }

    // Reference values for the value type: /tmp/oklch-ref/ref.py, shared with oklch-swift.

    private class From(
        val r: Double,
        val g: Double,
        val b: Double,
        val gamut: OklchGamut,
        val expected: Oklch,
        // Compose stores sRGB colors in 8 bits and other color spaces in half floats.
        val colorTolerance: Double = 1e-4,
    )

    private val fromCases = listOf(
        From(1.0, 1.0, 1.0, OklchGamut.SRGB, Oklch(1.0, 0.0, 0.0)),
        From(0.0, 0.0, 0.0, OklchGamut.SRGB, Oklch(0.0, 0.0, 0.0)),
        // Color stores 0.5 as 128 / 255.
        From(0.5, 0.5, 0.5, OklchGamut.SRGB, Oklch(0.598181, 0.0, 0.0), colorTolerance = 2e-3),
        From(1.0, 0.0, 0.0, OklchGamut.SRGB, Oklch(0.627955, 0.257683, 29.233885)),
        From(0.0, 1.0, 0.0, OklchGamut.SRGB, Oklch(0.866440, 0.294827, 142.495339)),
        From(0.0, 0.0, 1.0, OklchGamut.SRGB, Oklch(0.452014, 0.313214, 264.052021)),
        From(0.8, 0.4, 0.2, OklchGamut.SRGB, Oklch(0.622774, 0.144560, 45.069378)),
        From(1.0, 0.0, 0.0, OklchGamut.DisplayP3, Oklch(0.648574, 0.299485, 28.958137)),
        From(0.0, 1.0, 0.0, OklchGamut.DisplayP3, Oklch(0.848829, 0.368528, 145.644950)),
        // Color stores 0.8 as 0.7998 (half float).
        From(0.8, 0.4, 0.2, OklchGamut.DisplayP3, Oklch(0.632404, 0.171699, 43.230358), colorTolerance = 5e-4),
    )

    @Test
    fun fromEncodedRgbMatchesReference() {
        for (case in fromCases) {
            val actual = Oklch.fromEncoded(case.r, case.g, case.b, case.gamut)
            assertOklch(case.expected, actual, 1e-5, "${case.gamut}(${case.r}, ${case.g}, ${case.b})")
        }
    }

    @Test
    fun fromColorMatchesReference() {
        for (case in fromCases) {
            val space = if (case.gamut == OklchGamut.SRGB) ColorSpaces.Srgb else ColorSpaces.DisplayP3
            val color = Color(case.r.toFloat(), case.g.toFloat(), case.b.toFloat(), 1f, space)
            val actual = Oklch.from(color)
            assertOklab(case.expected, actual, case.colorTolerance, "${case.gamut}(${case.r}, ${case.g}, ${case.b})")
            assertEquals(actual, color.toOklch())
        }
    }

    @Test
    fun fromColorKeepsAlphaAndSetsGreyHueToZero() {
        val grey = Oklch.from(Color(0.3f, 0.3f, 0.3f, 0.5f))

        assertEquals(0.0, grey.h, 0.0)
        assertTrue(grey.c < 0.000004)
        assertEquals(0.5f, grey.alpha, 0.01f)
    }

    @Test
    fun fromColorHandlesExtendedSrgb() {
        // A P3 green is outside sRGB, so its red and blue components in sRGB are below 0.
        val color = Color(0f, 1f, 0f, 1f, ColorSpaces.DisplayP3).convert(ColorSpaces.ExtendedSrgb)

        assertTrue(color.red < 0)
        assertOklab(Oklch(0.848829, 0.368528, 145.644950), Oklch.from(color), 1e-3, "extended sRGB")
    }

    @Test
    fun constructorWrapsHue() {
        assertEquals(330.0, Oklch(0.5, 0.1, -30.0).h, 1e-9)
        assertEquals(0.0, Oklch(0.5, 0.1, 360.0).h, 1e-9)
        assertEquals(5.0, Oklch(0.5, 0.1, 725.0).h, 1e-9)
        assertEquals(Oklch(0.5, 0.1, 330.0), Oklch(0.5, 0.1, -30.0))
        assertEquals(Oklch(0.5, 0.1, 330.0).hashCode(), Oklch(0.5, 0.1, -30.0).hashCode())
        assertEquals(Oklch(0.5, 0.1, 0.0), Oklch(0.5, 0.1, -0.0))
        assertEquals(10.0, Oklch(0.5, 0.1, 30.0).copy(h = 370.0).h, 1e-9)
        assertTrue(Oklch(0.5, 0.1, -1e-14).h < 360.0)
    }

    @Test
    fun destructuringMatchesProperties() {
        val (l, c, h, alpha) = Oklch(0.5, 0.1, 400.0, 0.25f)

        assertEquals(0.5, l, 0.0)
        assertEquals(0.1, c, 0.0)
        assertEquals(40.0, h, 1e-9)
        assertEquals(0.25f, alpha, 0f)
    }

    @Test
    fun mixMatchesReference() {
        class Mix(val a: Oklch, val b: Oklch, val t: Double, val expected: Oklch)
        val cases = listOf(
            // Short way through 0.
            Mix(Oklch(0.7, 0.1, 350.0), Oklch(0.7, 0.1, 10.0), 0.5, Oklch(0.7, 0.1, 0.0)),
            Mix(Oklch(0.7, 0.1, 10.0), Oklch(0.7, 0.1, 350.0), 0.25, Oklch(0.7, 0.1, 5.0)),
            // A grey end takes the other hue.
            Mix(Oklch(1.0, 0.0, 0.0), Oklch(0.5, 0.1, 60.0), 0.5, Oklch(0.75, 0.05, 60.0)),
            Mix(Oklch(0.5, 0.1, 60.0), Oklch(0.0, 0.0, 0.0), 0.5, Oklch(0.25, 0.05, 60.0)),
            // Both grey.
            Mix(Oklch(1.0, 0.0, 0.0), Oklch(0.0, 0.0, 0.0), 0.5, Oklch(0.5, 0.0, 0.0)),
            // A transparent end keeps L and C.
            Mix(Oklch(0.6, 0.12, 30.0, 1f), Oklch(0.6, 0.12, 30.0, 0f), 0.5, Oklch(0.6, 0.12, 30.0, 0.5f)),
            // Premultiplied. Full precision of the reference's 0.652632 and 0.113158.
            Mix(
                Oklch(0.4, 0.05, 200.0, 0.2f),
                Oklch(0.8, 0.15, 100.0, 0.8f),
                0.3,
                Oklch(0.6526315789473685, 0.1131578947368421, 170.0, 0.38f),
            ),
            // t is not clamped.
            Mix(Oklch(0.5, 0.1, 100.0), Oklch(0.7, 0.2, 140.0), 1.5, Oklch(0.8, 0.25, 160.0)),
        )
        for (case in cases) {
            assertOklch(case.expected, case.a.mix(case.b, case.t), 1e-9, "mix(${case.a}, ${case.b}, ${case.t})")
        }
    }

    @Test
    fun mixOfTwoTransparentColorsInterpolatesPlainly() {
        val actual = Oklch(0.2, 0.1, 10.0, 0f).mix(Oklch(0.6, 0.3, 50.0, 0f), 0.25)

        assertOklch(Oklch(0.3, 0.15, 20.0, 0f), actual, 1e-9, "transparent mix")
    }

    @Test
    fun mixOfTwoGreysHasHueZero() {
        val actual = Oklch(0.2, 0.0, 120.0).mix(Oklch(0.6, 0.0, 240.0), 0.5)

        assertEquals(0.0, actual.h, 0.0)
    }

    @Test
    fun mixEndsReturnTheInputs() {
        val a = Oklch(0.4, 0.05, 200.0, 0.2f)
        val b = Oklch(0.8, 0.15, 100.0, 0.8f)

        assertOklch(a, a.mix(b, 0.0), 1e-9, "t = 0")
        assertOklch(b, a.mix(b, 1.0), 1e-9, "t = 1")
    }

    @Test
    fun srgbRoundTripsThroughColor() {
        // These colors land on whole 8-bit values, so the 8-bit storage adds no error.
        val exact = listOf(
            Oklch(0.627955, 0.257683, 29.233885),
            Oklch(0.866440, 0.294827, 142.495339),
            Oklch(0.452014, 0.313214, 264.052021),
            Oklch(0.622774, 0.144560, 45.069378),
        )
        for (color in exact) {
            assertOklab(color, Oklch.from(color.toColor(OklchGamut.SRGB)), 1e-4, "sRGB round trip $color")
        }

        // Other in-gamut colors round to 8 bits on the way.
        val rounded = listOf(Oklch(0.7, 0.1, 200.0), Oklch(0.5, 0.05, 30.0), Oklch(0.9, 0.03, 90.0, 0.6f))
        for (color in rounded) {
            assertOklab(color, Oklch.from(color.toColor(OklchGamut.SRGB)), 3e-3, "sRGB round trip $color")
        }
    }

    @Test
    fun displayP3RoundTripsThroughColor() {
        val colors = listOf(Oklch(0.7, 0.1, 200.0), Oklch(0.648574, 0.299485, 28.958137), Oklch(0.5, 0.2, 300.0))
        for (color in colors) {
            assertOklab(color, Oklch.from(color.toColor()), 1e-3, "P3 round trip $color")
        }
    }

    @Test
    fun greyRoundTripKeepsHueZero() {
        val back = Oklch.from(Oklch(0.6, 0.0, 0.0).toColor(OklchGamut.SRGB))

        assertEquals(0.0, back.h, 0.0)
        assertTrue(back.c < 0.000004)
        assertEquals(0.6, back.l, 3e-3)
    }

    @Test
    fun toColorMatchesColorOklch() {
        val color = Oklch(0.55, 0.15, 262.0, 0.5f)

        assertEquals(Color.oklch(0.55, 0.15, 262, alpha = 0.5f), color.toColor())
        assertEquals(
            Color.oklch(0.55, 0.15, 262, alpha = 0.5f, gamut = OklchGamut.SRGB),
            color.toColor(OklchGamut.SRGB),
        )
        assertEquals(ColorSpaces.DisplayP3, color.toColor().colorSpace)
    }

    private fun assertOklch(expected: Oklch, actual: Oklch, tolerance: Double, label: String) {
        assertEquals("$label l", expected.l, actual.l, tolerance)
        assertEquals("$label c", expected.c, actual.c, tolerance)
        assertEquals("$label h", expected.h, actual.h, tolerance)
        assertEquals("$label alpha", expected.alpha, actual.alpha, 1e-6f)
    }

    // Compares in OKLab (L, a, b), where one tolerance fits all three axes. Use it where Compose's
    // Color storage rounds the components: a hue error in degrees grows as chroma drops.
    private fun assertOklab(expected: Oklch, actual: Oklch, tolerance: Double, label: String) {
        assertEquals("$label l", expected.l, actual.l, tolerance)
        assertEquals("$label a", expected.c * cos(expected.h * PI / 180), actual.c * cos(actual.h * PI / 180), tolerance)
        assertEquals("$label b", expected.c * sin(expected.h * PI / 180), actual.c * sin(actual.h * PI / 180), tolerance)
        assertEquals("$label alpha", expected.alpha, actual.alpha, 1f / 255)
    }

    private class Case(val l: Double, val c: Double, val h: Double, val srgb: DoubleArray, val p3: DoubleArray)

    private fun assertRgb(rgb: Oklch.Rgb, red: Double, green: Double, blue: Double) {
        val tolerance = 1.0 / 255
        assertEquals(red, rgb.red, tolerance)
        assertEquals(green, rgb.green, tolerance)
        assertEquals(blue, rgb.blue, tolerance)
    }
}

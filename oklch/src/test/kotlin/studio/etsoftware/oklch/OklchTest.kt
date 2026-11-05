package studio.etsoftware.oklch

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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

    private class Case(val l: Double, val c: Double, val h: Double, val srgb: DoubleArray, val p3: DoubleArray)

    private fun assertRgb(rgb: Oklch.Rgb, red: Double, green: Double, blue: Double) {
        val tolerance = 1.0 / 255
        assertEquals(red, rgb.red, tolerance)
        assertEquals(green, rgb.green, tolerance)
        assertEquals(blue, rgb.blue, tolerance)
    }
}

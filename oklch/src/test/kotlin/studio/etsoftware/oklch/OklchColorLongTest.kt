package studio.etsoftware.oklch

import android.graphics.ColorSpace
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.graphics.Color as AndroidColor

// oklchColorLong calls android.graphics, which plain JVM tests can't run.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OklchColorLongTest {
    private val displayP3 = ColorSpace.get(ColorSpace.Named.DISPLAY_P3)

    @Test
    fun packsDisplayP3() {
        val color = oklchColorLong(0.55, 0.15, 262)

        assertEquals(displayP3, AndroidColor.colorSpace(color))
        assertEquals(1f, AndroidColor.alpha(color), 0f)
    }

    @Test
    fun matchesTheGamutMappedReference() {
        // Same case as OklchTest.outOfGamutColorsMatchCssColor4. The channels are half floats.
        val color = oklchColorLong(0.65, 0.3, 30)

        assertEquals(1.0f, AndroidColor.red(color), HALF_FLOAT)
        assertEquals(0.016388f, AndroidColor.green(color), HALF_FLOAT)
        assertEquals(0.0f, AndroidColor.blue(color), HALF_FLOAT)
    }

    @Test
    fun keepsAlphaAndClampsIt() {
        assertEquals(0.5f, AndroidColor.alpha(oklchColorLong(0.55, 0.15, 262, alpha = 0.5f)), HALF_FLOAT)
        assertEquals(1f, AndroidColor.alpha(oklchColorLong(0.55, 0.15, 262, alpha = 2f)), 0f)
        assertEquals(0f, AndroidColor.alpha(oklchColorLong(0.55, 0.15, 262, alpha = -1f)), 0f)
    }

    @Test
    fun matchesComposeColor() {
        val compose = Color.oklch(0.7, 0.2, 150, alpha = 0.8f)
        val view = oklchColorLong(0.7, 0.2, 150, alpha = 0.8f)

        assertEquals(compose.red, AndroidColor.red(view), HALF_FLOAT)
        assertEquals(compose.green, AndroidColor.green(view), HALF_FLOAT)
        assertEquals(compose.blue, AndroidColor.blue(view), HALF_FLOAT)
        assertEquals(compose.alpha, AndroidColor.alpha(view), HALF_FLOAT)
    }

    private companion object {
        const val HALF_FLOAT = 0.001f
    }
}

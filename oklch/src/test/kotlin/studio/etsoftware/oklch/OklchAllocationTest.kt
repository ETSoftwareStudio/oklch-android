package studio.etsoftware.oklch

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Method

// Apps call toColor every frame during animations. Colors inside the gamut must not
// allocate. The test task turns off HotSpot's escape analysis (see build.gradle.kts),
// because ART on a phone does not remove these allocations either.
// Color.oklch and oklchColorInt take Number, so their callers box the arguments; they
// suit colors built once, such as tokens, and are not checked here.
class OklchAllocationTest {
    // Unit tests compile against android.jar, which lacks java.lang.management, so look up
    // the JVM's per-thread allocation counter by reflection.
    private val threads: Any = Class.forName("java.lang.management.ManagementFactory")
        .getMethod("getThreadMXBean").invoke(null)!!
    private val allocatedBytes: Method = Class.forName("com.sun.management.ThreadMXBean")
        .getMethod("getThreadAllocatedBytes", Long::class.javaPrimitiveType)

    private fun allocated(thread: Long): Long = allocatedBytes.invoke(threads, thread) as Long

    @Test
    fun toColorInGamutDoesNotAllocate() {
        val color = Oklch(0.55, 0.1, 262.0, 0.8f)

        assertNoAllocation("toColor P3") { color.toColor().value.toLong() }
        assertNoAllocation("toColor sRGB") { color.toColor(OklchGamut.SRGB).value.toLong() }
    }

    private inline fun assertNoAllocation(label: String, block: () -> Long) {
        val id = Thread.currentThread().id
        var sink = 0L
        repeat(WARMUP) { sink += block() }
        val before = allocated(id)
        repeat(CALLS) { sink += block() }
        val bytes = allocated(id) - before
        // A few hundred bytes of slack for the measurement itself; one small object per call
        // would be at least 16 bytes * CALLS.
        assertTrue("$label allocated $bytes bytes over $CALLS calls (sink $sink)", bytes < 1024)
    }

    private companion object {
        const val WARMUP = 20_000
        const val CALLS = 10_000
    }
}

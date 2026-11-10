package com.echo.nightvision

import com.echo.nightvision.processing.PixelImage
import com.echo.nightvision.processing.WienerMotionDeblurrer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class WienerMotionDeblurrerTest {
    @Test
    fun zeroStrengthReturnsTheOriginalPixelsWithoutMutation() {
        val pixels = IntArray(35) { index ->
            val value = (index * 17) % 256
            0x7F000000 or (value shl 16) or (value shl 8) or value
        }
        val before = pixels.copyOf()
        val source = PixelImage(7, 5, pixels)
        val restored = WienerMotionDeblurrer.restore(
            source, motionLengthPx = 12f, angleDegrees = 0f,
            regularization = 0.01f, strength = 0f
        )
        assertEquals(before.toList(), restored.image.pixels.toList())
        assertEquals(before.toList(), pixels.toList())
    }

    @Test
    fun uniformImageRemainsStableAndAlphaIsPreserved() {
        val uniform = 0x7F555555
        val source = PixelImage(31, 19, IntArray(31 * 19) { uniform })
        val restored = WienerMotionDeblurrer.restore(
            source, motionLengthPx = 18f, angleDegrees = 37f,
            regularization = 0.02f, strength = 100f
        )
        assertEquals(source.width, restored.image.width)
        assertEquals(source.height, restored.image.height)
        assertTrue(restored.image.pixels.all { pixel ->
            (pixel ushr 24) == 0x7F &&
                abs(((pixel ushr 16) and 255) - 85) <= 1 &&
                abs(((pixel ushr 8) and 255) - 85) <= 1 &&
                abs((pixel and 255) - 85) <= 1
        })
    }

    @Test
    fun wienerRestorationReducesErrorOnKnownHorizontalMotionBlur() {
        val width = 128
        val height = 96
        val sharp = IntArray(width * height) { index ->
            val x = index % width
            val value = when {
                x in 64..69 -> 230
                x in 40..87 -> 180
                else -> 30
            }
            0xFF000000.toInt() or (value shl 16) or (value shl 8) or value
        }
        val blurred = horizontalMotionBlur(sharp, width, height, 9)
        val original = PixelImage(width, height, sharp)
        val degraded = PixelImage(width, height, blurred)
        val restored = WienerMotionDeblurrer.restore(
            degraded, motionLengthPx = 9f, angleDegrees = 0f,
            regularization = 0.003f, strength = 100f
        )

        val blurredError = meanSquaredError(sharp, blurred)
        val restoredError = meanSquaredError(sharp, restored.image.pixels)
        assertTrue(
            "Expected Wiener restoration to reduce MSE ($blurredError -> $restoredError)",
            restoredError < blurredError * 0.7
        )
        assertEquals(original.width, restored.image.width)
        assertEquals(original.height, restored.image.height)
    }

    @Test
    fun verticalMotionDirectionRestoresVerticalTrails() {
        val width = 64
        val height = 96
        val sharp = IntArray(width * height) { index ->
            val y = index / width
            val value = if (y in 38..54) 210 else 35
            0xFF000000.toInt() or (value shl 16) or (value shl 8) or value
        }
        val blurred = verticalMotionBlur(sharp, width, height, 7)
        val restored = WienerMotionDeblurrer.restore(
            PixelImage(width, height, blurred), motionLengthPx = 7f,
            angleDegrees = 90f, regularization = 0.003f, strength = 100f
        )
        assertTrue(
            meanSquaredError(sharp, restored.image.pixels) <
                meanSquaredError(sharp, blurred) * 0.75
        )
    }

    private fun horizontalMotionBlur(
        source: IntArray, width: Int, height: Int, length: Int
    ): IntArray {
        val output = IntArray(source.size)
        val radius = length / 2
        for (y in 0 until height) for (x in 0 until width) {
            var sum = 0
            var count = 0
            for (offset in -radius..radius) {
                sum += source[y * width + reflect(x + offset, width)] and 255
                count++
            }
            val value = sum / count
            output[y * width + x] = 0xFF000000.toInt() or
                (value shl 16) or (value shl 8) or value
        }
        return output
    }

    private fun verticalMotionBlur(
        source: IntArray, width: Int, height: Int, length: Int
    ): IntArray {
        val output = IntArray(source.size)
        val radius = length / 2
        for (y in 0 until height) for (x in 0 until width) {
            var sum = 0
            var count = 0
            for (offset in -radius..radius) {
                sum += source[reflect(y + offset, height) * width + x] and 255
                count++
            }
            val value = sum / count
            output[y * width + x] = 0xFF000000.toInt() or
                (value shl 16) or (value shl 8) or value
        }
        return output
    }

    private fun reflect(index: Int, size: Int): Int {
        if (size <= 1) return 0
        val period = 2 * size - 2
        val wrapped = ((index % period) + period) % period
        return if (wrapped < size) wrapped else period - wrapped
    }

    private fun meanSquaredError(expected: IntArray, actual: IntArray): Double {
        var sum = 0.0
        for (index in expected.indices) {
            val difference = (expected[index] and 255) - (actual[index] and 255)
            sum += difference * difference
        }
        return sum / expected.size
    }
    @Test
    fun highResolutionInputUsesBoundedWorkingGridAndKeepsOriginalDimensions() {
        val width = 1800
        val height = 900
        val source = PixelImage(
            width, height,
            IntArray(width * height) { index ->
                val value = if ((index % width) in 700..900) 150 else 45
                0xFF000000.toInt() or (value shl 16) or (value shl 8) or value
            }
        )
        val result = WienerMotionDeblurrer.restore(
            source, motionLengthPx = 20f, angleDegrees = 0f,
            regularization = 0.02f, strength = 60f
        )
        assertEquals(896, result.workingWidth)
        assertEquals(448, result.workingHeight)
        assertEquals(width, result.image.width)
        assertEquals(height, result.image.height)
        assertTrue(result.fftWidth <= 1024)
        assertTrue(result.fftHeight <= 512)
        assertTrue(kotlin.math.abs(result.effectiveMotionLength - 20f * 896f / width) < 0.01f)
    }

}

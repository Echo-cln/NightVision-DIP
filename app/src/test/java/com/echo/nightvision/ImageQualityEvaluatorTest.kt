package com.echo.nightvision

import com.echo.nightvision.processing.ImageQualityEvaluator
import com.echo.nightvision.processing.PixelImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.log10

class ImageQualityEvaluatorTest {
    @Test
    fun identicalImagesHaveInfinitePsnrAndUnitSsim() {
        val pixels = IntArray(13 * 9) { index ->
            val value = (index * 19) % 256
            0xFF000000.toInt() or (value shl 16) or (value shl 8) or value
        }
        val image = PixelImage(13, 9, pixels)
        val metrics = ImageQualityEvaluator.evaluate(image, image)
        assertEquals(0.0, metrics.mseRgb, 0.0)
        assertTrue(metrics.psnrDb.isInfinite())
        assertEquals(1.0, metrics.ssim8x8, 1e-12)
    }

    @Test
    fun psnrUsesMeanSquaredErrorAcrossRgbChannels() {
        val reference = PixelImage(1, 1, intArrayOf(0xFF000000.toInt()))
        val candidate = PixelImage(1, 1, intArrayOf(0xFF0A0000.toInt()))
        val metrics = ImageQualityEvaluator.evaluate(reference, candidate)
        val expectedMse = 100.0 / 3.0
        val expectedPsnr = 10.0 * log10((255.0 * 255.0) / expectedMse)
        assertEquals(expectedMse, metrics.mseRgb, 1e-12)
        assertEquals(expectedPsnr, metrics.psnrDb, 1e-12)
    }

    @Test
    fun alphaChannelDoesNotChangeRgbQualityMetrics() {
        val reference = PixelImage(2, 2, IntArray(4) { 0xFF446688.toInt() })
        val candidate = PixelImage(2, 2, IntArray(4) { 0x10446688 })
        val metrics = ImageQualityEvaluator.evaluate(reference, candidate)
        assertEquals(0.0, metrics.mseRgb, 0.0)
        assertTrue(metrics.psnrDb.isInfinite())
        assertEquals(1.0, metrics.ssim8x8, 1e-12)
    }

    @Test
    fun luminanceSsimDecreasesWhenImageStructureChanges() {
        val referencePixels = IntArray(16 * 16) { index ->
            val x = index % 16
            val y = index / 16
            val value = if ((x / 4 + y / 4) % 2 == 0) 40 else 210
            0xFF000000.toInt() or (value shl 16) or (value shl 8) or value
        }
        val changedPixels = referencePixels.mapIndexed { index, pixel ->
            if (index % 16 in 6..9) {
                val value = 110
                0xFF000000.toInt() or (value shl 16) or (value shl 8) or value
            } else pixel
        }.toIntArray()
        val reference = PixelImage(16, 16, referencePixels)
        val changed = PixelImage(16, 16, changedPixels)
        val perfect = ImageQualityEvaluator.evaluate(reference, reference)
        val degraded = ImageQualityEvaluator.evaluate(reference, changed)
        assertEquals(1.0, perfect.ssim8x8, 1e-12)
        assertTrue(degraded.ssim8x8 < perfect.ssim8x8)
        assertTrue(degraded.ssim8x8 in -1.0..1.0)
    }

    @Test
    fun mismatchedDimensionsAreRejectedInsteadOfSilentlyResized() {
        val reference = PixelImage(8, 8, IntArray(64) { 0xFF000000.toInt() })
        val candidate = PixelImage(9, 8, IntArray(72) { 0xFF000000.toInt() })
        val error = runCatching { ImageQualityEvaluator.evaluate(reference, candidate) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message?.contains("尺寸必须一致") == true)
    }
}

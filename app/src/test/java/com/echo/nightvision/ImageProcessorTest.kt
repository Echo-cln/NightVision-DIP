package com.echo.nightvision

import com.echo.nightvision.processing.DenoiseMethod
import com.echo.nightvision.processing.ImageProcessor
import com.echo.nightvision.processing.PixelImage
import com.echo.nightvision.processing.ProcessParameters
import com.echo.nightvision.processing.ProcessingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageProcessorTest {
    @Test
    fun neutralParametersPreserveGrayPixels() {
        val pixels = intArrayOf(0xFF101010.toInt(), 0xFF404040.toInt(), 0xFF808080.toInt(), 0xFFC0C0C0.toInt())
        val source = PixelImage(2, 2, pixels)
        val result = ImageProcessor.process(
            source, ProcessingMode.ENHANCE,
            ProcessParameters(0f, 0f, 0f, 100f, 0f, DenoiseMethod.GAUSSIAN, false)
        )
        assertEquals(pixels.toList(), result.image.pixels.toList())
    }

    @Test
    fun outputKeepsDimensionsAndAlphaChannel() {
        val source = PixelImage(3, 2, IntArray(6) { 0x7F303030 })
        val result = ImageProcessor.process(source, ProcessingMode.ENHANCE, ProcessParameters())
        assertEquals(3, result.image.width)
        assertEquals(2, result.image.height)
        assertTrue(result.image.pixels.all { (it ushr 24) == 0x7F })
    }

    @Test
    fun histogramAccountsForEveryPixel() {
        val source = PixelImage(4, 4, IntArray(16) { 0xFF202020.toInt() })
        val stats = ImageProcessor.analyze(source)
        assertEquals(16, stats.histogram.sum())
        assertEquals(100, stats.darkPercent)
    }

    @Test
    fun positiveBrightnessIncreasesMeanForDarkImage() {
        val source = PixelImage(4, 4, IntArray(16) { 0xFF202020.toInt() })
        val result = ImageProcessor.process(
            source, ProcessingMode.ENHANCE,
            ProcessParameters(brightness = 70f, contrast = 0f, denoise = 0f, auto = false)
        )
        assertTrue(result.report.processed.mean > result.report.original.mean)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidPixelBufferIsRejected() {
        PixelImage(2, 2, intArrayOf(1, 2, 3))
    }

    @Test
    fun medianFilterRemovesAnIsolatedImpulse() {
        val pixels = IntArray(9) { 0xFF646464.toInt() }
        pixels[4] = 0xFFFFFFFF.toInt()
        val result = ImageProcessor.process(
            PixelImage(3, 3, pixels),
            ProcessingMode.ENHANCE,
            ProcessParameters(
                brightness = 0f, contrast = 0f, denoise = 100f,
                saturation = 100f, temperature = 0f,
                denoiseMethod = DenoiseMethod.MEDIAN, auto = false
            )
        )
        assertEquals(100, (result.image.pixels[4] ushr 16) and 255)
    }

    @Test
    fun gaussianFilterSoftensAnIsolatedImpulse() {
        val pixels = IntArray(9) { 0xFF646464.toInt() }
        pixels[4] = 0xFFFFFFFF.toInt()
        val result = ImageProcessor.process(
            PixelImage(3, 3, pixels),
            ProcessingMode.ENHANCE,
            ProcessParameters(
                brightness = 0f, contrast = 0f, denoise = 100f,
                saturation = 100f, temperature = 0f,
                denoiseMethod = DenoiseMethod.GAUSSIAN, auto = false
            )
        )
        val center = (result.image.pixels[4] ushr 16) and 255
        assertTrue(center in 100..254)
    }

    @Test
    fun automaticParametersLiftAUniformDarkImage() {
        val source = PixelImage(8, 8, IntArray(64) { 0xFF202020.toInt() })
        val result = ImageProcessor.process(source, ProcessingMode.ENHANCE, ProcessParameters())
        assertTrue(result.report.effectiveParameters.brightness > 0f)
        assertTrue(result.report.processed.mean > result.report.original.mean)
    }

}

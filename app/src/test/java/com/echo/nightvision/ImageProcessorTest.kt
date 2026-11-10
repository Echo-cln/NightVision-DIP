package com.echo.nightvision

import com.echo.nightvision.processing.DenoiseMethod
import com.echo.nightvision.processing.ImageProcessor
import com.echo.nightvision.processing.HslAdjustment
import com.echo.nightvision.processing.ToneMethod
import com.echo.nightvision.processing.ToneCurve
import com.echo.nightvision.processing.PixelImage
import com.echo.nightvision.processing.ProcessParameters
import com.echo.nightvision.processing.ProcessingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageProcessorTest {
    @Test fun meanFilterMatchesImpulseAndStrength() {
        val pixels = IntArray(9) { 0x7F000000 }; pixels[4] = 0x7F909090
        val source = PixelImage(3, 3, pixels)
        fun run(strength: Float) = ImageProcessor.process(source, ProcessingMode.RESTORE,
            ProcessParameters(auto = false, denoise = strength, denoiseDetail = 0f,
                denoiseMethod = DenoiseMethod.MEAN)).image
        assertEquals(16, run(100f).pixels[4] and 255)
        assertEquals(80, run(50f).pixels[4] and 255)
        assertEquals(pixels.toList(), run(0f).pixels.toList())
        assertTrue(run(100f).pixels.all { it ushr 24 == 127 })
        assertEquals(0x7F909090, pixels[4])
    }

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
                denoiseMethod = DenoiseMethod.MEDIAN, denoiseDetail = 0f, auto = false
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
        assertTrue(result.report.effectiveParameters.gamma < 1f)
        assertTrue(result.report.processed.mean > result.report.original.mean)
    }

    @Test
    fun exposureAdjustmentRaisesMeanByApproximatelyOneStop() {
        val source = PixelImage(4, 4, IntArray(16) { 0xFF282828.toInt() })
        val result = ImageProcessor.process(
            source, ProcessingMode.ENHANCE,
            ProcessParameters(brightness = 0f, contrast = 0f, denoise = 0f, saturation = 100f, exposure = 1f, auto = false)
        )
        assertTrue(result.report.processed.mean > result.report.original.mean * 1.8)
    }

    @Test
    fun shadowRecoveryLiftsDarkPixels() {
        val source = PixelImage(4, 4, IntArray(16) { if (it < 8) 0xFF101010.toInt() else 0xFFB0B0B0.toInt() })
        val result = ImageProcessor.process(
            source, ProcessingMode.ENHANCE,
            ProcessParameters(brightness = 0f, contrast = 0f, denoise = 0f, shadows = 80f, auto = false)
        )
        assertTrue(result.image.pixels[0] > source.pixels[0])
        assertEquals(source.pixels[15], result.image.pixels[15])
    }

    @Test
    fun redHslChannelChangesOnlyMatchingHueRange() {
        val pixels = intArrayOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt(), 0xFFFFFFFF.toInt())
        val hsl = MutableList(8) { HslAdjustment() }
        hsl[0] = HslAdjustment(hue = 80f, saturation = 0f, luminance = 0f)
        val result = ImageProcessor.process(
            PixelImage(2, 2, pixels), ProcessingMode.ENHANCE,
            ProcessParameters(brightness = 0f, contrast = 0f, denoise = 0f, saturation = 100f, hslAdjustments = hsl, auto = false)
        )
        assertTrue(result.image.pixels[0] != pixels[0])
        assertEquals(pixels[1], result.image.pixels[1])
        assertEquals(pixels[2], result.image.pixels[2])
    }

    @Test
    fun histogramEqualizationIsAvailableAsAnExplicitToneMethod() {
        val pixels = IntArray(64) { index ->
            val value = if (index < 56) 20 else 80
            (0xFF000000.toInt() or (value shl 16) or (value shl 8) or value)
        }
        val result = ImageProcessor.process(
            PixelImage(8, 8, pixels), ProcessingMode.ENHANCE,
            ProcessParameters(brightness = 0f, contrast = 0f, denoise = 0f, toneMethod = ToneMethod.HISTOGRAM_EQUALIZATION, auto = false)
        )
        assertTrue(result.image.pixels.toSet().size >= 2)
        assertEquals(64, result.report.processed.histogram.sum())
    }

    @Test
    fun rgbToneCurveMapsMidpointIndependently() {
        val source = PixelImage(1, 1, intArrayOf(0xFF808080.toInt()))
        val curve = ToneCurve(rgb = listOf(0f, 64f, 190f, 220f, 255f))
        val result = ImageProcessor.process(
            source, ProcessingMode.ENHANCE,
            ProcessParameters(brightness = 0f, contrast = 0f, denoise = 0f, toneCurve = curve, auto = false)
        )
        val channel = (result.image.pixels[0] ushr 16) and 255
        assertTrue(channel >= 185)
    }

    @Test
    fun clahePreservesDimensionsAlphaAndDoesNotMutateSource() {
        val pixels = IntArray(15 * 11) { index ->
            val x = index % 15
            val y = index / 15
            val gray = (20 + x * 7 + y * 2).coerceIn(0, 255)
            0x7F000000 or (gray shl 16) or (gray shl 8) or gray
        }
        val before = pixels.copyOf()
        val image = PixelImage(15, 11, pixels)
        val result = ImageProcessor.process(
            image, ProcessingMode.ENHANCE,
            ProcessParameters(denoise = 0f, auto = false, toneMethod = ToneMethod.CLAHE,
                claheClipLimit = 2f, claheTiles = 4)
        )
        assertEquals(15, result.image.width)
        assertEquals(11, result.image.height)
        assertEquals(before.toList(), pixels.toList())
        assertTrue(result.image.pixels.all { it ushr 24 == 0x7F })
        assertTrue(result.report.steps.any { it.name.contains("CLAHE") })
        assertTrue(result.image.pixels.any { resultPixel -> resultPixel !in before })
    }

    @Test
    fun claheHandlesSinglePixelAndExtremeTileSettings() {
        val source = PixelImage(1, 1, intArrayOf(0xFF404040.toInt()))
        val result = ImageProcessor.process(
            source, ProcessingMode.ENHANCE,
            ProcessParameters(denoise = 0f, auto = false, toneMethod = ToneMethod.CLAHE,
                claheClipLimit = 8f, claheTiles = 16)
        )
        assertEquals(1, result.image.pixels.size)
        assertEquals(0xFF, result.image.pixels[0] ushr 24)
    }

    @Test
    fun claheParametersAffectLocalToneMapping() {
        val pixels = IntArray(32 * 32) { i ->
            val x = i % 32
            val y = i / 32
            val gray = (20 + (x / 4) * 6 + (y / 4) * 3).coerceIn(0, 255)
            0xFF000000.toInt() or (gray shl 16) or (gray shl 8) or gray
        }
        val input = PixelImage(32, 32, pixels)
        val a = ImageProcessor.process(
            input, ProcessingMode.ENHANCE,
            ProcessParameters(denoise = 0f, auto = false, toneMethod = ToneMethod.CLAHE,
                claheClipLimit = 0.5f, claheTiles = 2)
        )
        val b = ImageProcessor.process(
            input, ProcessingMode.ENHANCE,
            ProcessParameters(denoise = 0f, auto = false, toneMethod = ToneMethod.CLAHE,
                claheClipLimit = 6f, claheTiles = 8)
        )
        assertTrue(a.image.pixels.toList() != b.image.pixels.toList())
    }

    @Test
    fun luminanceDenoisingPreservesPixelColorDifferences() {
        val source = IntArray(25) { 0xFF464646.toInt() }
        val centerIndex = 2 * 5 + 2
        source[centerIndex] = 0xFF785028.toInt()
        val output = ImageProcessor.process(
            PixelImage(5, 5, source),
            ProcessingMode.ENHANCE,
            ProcessParameters(
                brightness = 0f, contrast = 0f, denoise = 100f, denoiseDetail = 0f,
                colorDenoise = 0f, saturation = 100f, auto = false,
                denoiseMethod = DenoiseMethod.GAUSSIAN
            )
        )
        val actual = output.image.pixels[centerIndex]
        val r = (actual ushr 16) and 255
        val g = (actual ushr 8) and 255
        val b = actual and 255
        assertTrue(kotlin.math.abs((r - g) - 40) <= 2)
        assertTrue(kotlin.math.abs((g - b) - 40) <= 2)
        assertTrue(source[centerIndex] != actual)
    }

    @Test
    fun uniformClaheTilesDoNotInventContrast() {
        val pixel = 0xFF303030.toInt()
        val image = PixelImage(16, 16, IntArray(256) { pixel })
        val output = ImageProcessor.process(
            image, ProcessingMode.ENHANCE,
            ProcessParameters(denoise = 0f, auto = false, toneMethod = ToneMethod.CLAHE,
                claheClipLimit = 2f, claheTiles = 4)
        )
        assertTrue(output.image.pixels.all { it == pixel })
    }

    @Test
    fun bilateralDenoisingPreservesHighContrastEdgesBetterThanGaussian() {
        val pixels = IntArray(7 * 5) { i ->
            val gray = if (i % 7 <= 3) 40 else 190
            0xFF000000.toInt() or (gray shl 16) or (gray shl 8) or gray
        }
        val source = PixelImage(7, 5, pixels)
        val base = ProcessParameters(
            brightness = 0f, contrast = 0f, denoise = 100f, denoiseDetail = 0f,
            saturation = 100f, auto = false
        )
        val gaussian = ImageProcessor.process(
            source, ProcessingMode.ENHANCE, base.copy(denoiseMethod = DenoiseMethod.GAUSSIAN)
        )
        val bilateral = ImageProcessor.process(
            source, ProcessingMode.ENHANCE,
            base.copy(denoiseMethod = DenoiseMethod.BILATERAL,
                bilateralSpatialSigma = 1.5f, bilateralRangeSigma = 10f)
        )
        val index = 2 * 7 + 3
        val gaussianY = gaussian.image.pixels[index] and 255
        val bilateralY = bilateral.image.pixels[index] and 255
        assertTrue(gaussianY > bilateralY)
        assertTrue(kotlin.math.abs(bilateralY - 40) <= 2)
    }

    @Test
    fun manualRestoreCanDisableWhiteBalance() {
        val pixels = IntArray(16) { 0xFF784020.toInt() }
        val input = PixelImage(4, 4, pixels)
        val base = ProcessParameters(
            brightness = 0f, contrast = 0f, denoise = 0f, saturation = 100f,
            whiteBalance = false, auto = false
        )
        val without = ImageProcessor.process(input, ProcessingMode.RESTORE, base)
        val with = ImageProcessor.process(input, ProcessingMode.RESTORE, base.copy(whiteBalance = true))
        assertEquals(pixels.toList(), without.image.pixels.toList())
        assertTrue(with.image.pixels.toList() != pixels.toList())
    }

}

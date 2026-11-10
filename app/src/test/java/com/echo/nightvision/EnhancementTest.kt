package com.echo.nightvision

import com.echo.nightvision.processing.*
import java.util.Random
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class EnhancementTest {
    private fun gray(y: Int) = 0xFF000000.toInt() or (y shl 16) or (y shl 8) or y
    private val neutral = ProcessParameters(auto = false, denoise = 0f)
    private fun run(image: PixelImage, p: ProcessParameters = ProcessParameters()) =
        ImageProcessor.process(image, ProcessingMode.ENHANCE, p)

    @Test fun sufficientLightIsNotLiftedOrBlurred() {
        val input = PixelImage(32, 32, IntArray(1024) { gray(160 + it % 40) })
        val out = run(input)
        assertArrayEquals(input.pixels, out.image.pixels)
        assertEquals(0f, out.report.effectiveParameters.denoise, 0f)
    }
    @Test fun blackFrameDoesNotInventInformation() {
        val input = PixelImage(32, 32, IntArray(1024) { gray(0) })
        assertArrayEquals(input.pixels, run(input).image.pixels)
    }
    @Test fun darkFrameUsesGammaWithoutStackedExposure() {
        val input = PixelImage(32, 32, IntArray(1024) { gray(30) })
        val out = run(input)
        assertTrue(out.report.effectiveParameters.gamma < 1f)
        assertEquals(0f, out.report.effectiveParameters.exposure, 0f)
        assertTrue(out.report.processed.mean in 80.0..120.0)
        assertEquals(0f, out.report.effectiveParameters.denoise, 0f)
    }
    @Test fun nonuniformLowContrastSceneSelectsGentleClahe() {
        val input = PixelImage(64, 64, IntArray(4096) { gray(20 + (it % 64) / 2) })
        val out = run(input)
        assertEquals(ToneMethod.CLAHE, out.report.effectiveParameters.toneMethod)
        assertTrue(out.report.effectiveParameters.claheStrength in 1f..40f)
        assertTrue(out.report.processed.mean > out.report.original.mean)
    }
    @Test fun noisyFlatSceneSelectsBilateralAndReducesResidual() {
        val random = Random(42)
        val input = PixelImage(64, 64, IntArray(4096) { gray((150 + random.nextGaussian() * 5).toInt().coerceIn(0,255)) })
        val out = run(input)
        assertEquals(DenoiseMethod.BILATERAL, out.report.effectiveParameters.denoiseMethod)
        assertTrue(out.report.effectiveParameters.denoise > 0f)
        assertTrue(out.report.processed.quality.noiseSigma!! < out.report.original.quality.noiseSigma!!)
    }
    @Test fun isolatedExtremesSelectMedianRatherThanBlurEveryEdge() {
        val pixels = IntArray(32 * 32) { gray(110) }
        for (i in 100..900 step 100) pixels[i] = gray(255)
        val out = run(PixelImage(32, 32, pixels))
        assertEquals(DenoiseMethod.MEDIAN, out.report.effectiveParameters.denoiseMethod)
        assertTrue((out.image.pixels[400] and 255) < 130)
    }
    @Test fun textureIsNotAutomaticallyClassifiedAsGaussianNoise() {
        val input = PixelImage(64, 64, IntArray(4096) { i -> gray(if ((i % 64 + i / 64) % 2 == 0) 60 else 200) })
        val stats = ImageProcessor.analyze(input)
        // A checkerboard residual is large but its neighbors do not match the center;
        // confidence/range checks must exclude it from the Gaussian estimator.
        assertTrue(stats.quality.noiseSigma == null || stats.quality.noiseSigma!! < 10)
    }
    @Test fun bothColorSpacesPreserveNeutralColoredPixelsAndAlpha() {
        val input = PixelImage(4, 1, intArrayOf(0x7F194C7A, 0xFFDD1020.toInt(), 0xFF00FF00.toInt(), 0))
        for (space in LuminanceSpace.entries) {
            assertArrayEquals(input.pixels, run(input, neutral.copy(luminanceSpace = space)).image.pixels)
        }
    }
    @Test fun hsvGammaPreservesHueAndSaturationForNonsaturatedColor() {
        val input = PixelImage(1, 1, intArrayOf(0x7F502814))
        val px = run(input, neutral.copy(luminanceSpace = LuminanceSpace.HSV, gamma = .6f)).image.pixels[0]
        val r = (px ushr 16) and 255; val g = (px ushr 8) and 255; val b = px and 255
        assertEquals(0x7F, px ushr 24)
        assertTrue(abs(r - 2 * g) <= 2 && abs(g - 2 * b) <= 2)
        assertTrue(r > 80)
    }
    @Test fun ycbcrGamutCompressionPreservesRequestedLumaAndChromaDirection() {
        val px = replaceChannel(0xFFFF1000.toInt(), 220.0, LuminanceSpace.YCBCR)
        assertEquals(220.0, channelValue(px, LuminanceSpace.YCBCR), 1.0)
        val r = (px ushr 16) and 255; val g = (px ushr 8) and 255; val b = px and 255
        assertTrue(r > g && g >= b)
    }
    @Test fun colorDenoisePreservesUniformColor() {
        val input = PixelImage(8, 8, IntArray(64) { 0xFFB45023.toInt() })
        val out = run(input, neutral.copy(colorDenoise = 100f))
        assertArrayEquals(input.pixels, out.image.pixels)
    }
    @Test fun deterministicReplayMatchesAutomaticOutput() {
        val input = PixelImage(32, 32, IntArray(1024) { gray(15 + it % 50) })
        val automatic = run(input)
        assertArrayEquals(automatic.image.pixels, run(input, automatic.report.effectiveParameters).image.pixels)
    }
    @Test fun zeroClaheBlendIsIdentityAndSmallImagesAreSafe() {
        for ((w,h) in listOf(1 to 1, 1 to 20, 20 to 1, 3 to 5)) {
            val input = PixelImage(w,h,IntArray(w*h) { gray(20 + it % 100) })
            val out = run(input, neutral.copy(toneMethod = ToneMethod.CLAHE, claheTiles = 16, claheStrength = 0f))
            assertArrayEquals(input.pixels,out.image.pixels)
        }
    }
    @Test fun reportListsDenoisingBeforeToneProcessing() {
        val input = PixelImage(8,8,IntArray(64) { gray(it * 3) })
        val out = run(input,neutral.copy(denoise = 50f, toneMethod = ToneMethod.CLAHE))
        val names=out.report.steps.map { it.name }
        assertTrue(names.indexOfFirst { it.contains("滤波") } < names.indexOfFirst { it.contains("CLAHE") })
        assertTrue(names.any { it.contains("Gamma") })
    }
    @Test fun automaticToeDoesNotExaggerateFirstJpegQuantizationStep() {
        val pixels = IntArray(256) { gray(it % 16) }
        val out = run(PixelImage(16,16,pixels))
        assertEquals(0, out.image.pixels[0] and 255)
        assertTrue((out.image.pixels[1] and 255) <= 4)
        assertTrue((out.image.pixels[2] and 255) <= 8)
    }

}

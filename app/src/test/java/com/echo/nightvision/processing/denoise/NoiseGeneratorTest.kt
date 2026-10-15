package com.echo.nightvision.processing.denoise

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NoiseGeneratorTest {

    @Test
    fun zeroGaussianNoisePreservesValuesWithoutMutatingInput() {
        val original = intArrayOf(0, 64, 128, 192, 255, 20)
        val output = NoiseGenerator.addGaussianNoise(
            pixels = original, width = 3, height = 2, mean = 0.0, sigma = 0.0
        )
        assertArrayEquals(original, output)
        assertNotSame(original, output)
    }

    @Test
    fun sameSeedMakesGaussianNoiseReproducible() {
        val original = IntArray(256) { 128 }
        val first = NoiseGenerator.addGaussianNoise(original, 16, 16, sigma = 20.0, seed = 42L)
        val second = NoiseGenerator.addGaussianNoise(original, 16, 16, sigma = 20.0, seed = 42L)
        assertArrayEquals(first, second)
        assertFalse(first.contentEquals(original))
        assertTrue(first.all { it in 0..255 })
        assertTrue(original.all { it == 128 })
    }

    @Test
    fun gaussianOutputClipsToEightBitRange() {
        val original = intArrayOf(5, 100, 255)
        val white = NoiseGenerator.addGaussianNoise(
            original, 3, 1, mean = 500.0, sigma = 0.0
        )
        val black = NoiseGenerator.addGaussianNoise(
            original, 3, 1, mean = -500.0, sigma = 0.0
        )
        assertArrayEquals(intArrayOf(255, 255, 255), white)
        assertArrayEquals(intArrayOf(0, 0, 0), black)
    }

    @Test
    fun zeroSaltPepperProbabilityPreservesImage() {
        val original = intArrayOf(0, 60, 125, 200, 255, 35)
        val output = NoiseGenerator.addSaltPepperNoise(
            original, 3, 2, saltProbability = 0.0, pepperProbability = 0.0
        )
        assertArrayEquals(original, output)
        assertNotSame(original, output)
    }

    @Test
    fun fullSaltProbabilityMakesAllPixelsWhite() {
        val original = intArrayOf(10, 128, 200, 40)
        val output = NoiseGenerator.addSaltPepperNoise(
            original, 2, 2, saltProbability = 1.0, pepperProbability = 0.0
        )
        assertArrayEquals(intArrayOf(255, 255, 255, 255), output)
    }

    @Test
    fun fullPepperProbabilityMakesAllPixelsBlack() {
        val original = intArrayOf(10, 128, 200, 40)
        val output = NoiseGenerator.addSaltPepperNoise(
            original, 2, 2, saltProbability = 0.0, pepperProbability = 1.0
        )
        assertArrayEquals(intArrayOf(0, 0, 0, 0), output)
    }

    @Test
    fun saltPepperIsDeterministicAndReplacesWithOnlyExtrema() {
        val original = IntArray(400) { 127 }
        val first = NoiseGenerator.addSaltPepperNoise(
            original, 20, 20, saltProbability = 0.15, pepperProbability = 0.15, seed = 7L
        )
        val second = NoiseGenerator.addSaltPepperNoise(
            original, 20, 20, saltProbability = 0.15, pepperProbability = 0.15, seed = 7L
        )
        assertArrayEquals(first, second)
        assertTrue(first.all { it == 0 || it == 127 || it == 255 })
        assertTrue(first.any { it == 0 })
        assertTrue(first.any { it == 255 })
        assertTrue(original.all { it == 127 })
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeGaussianSigma() {
        NoiseGenerator.addGaussianNoise(intArrayOf(30), 1, 1, sigma = -1.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidProbabilitySum() {
        NoiseGenerator.addSaltPepperNoise(
            intArrayOf(30), 1, 1, saltProbability = 0.7, pepperProbability = 0.5
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMismatchedDimensions() {
        NoiseGenerator.addGaussianNoise(intArrayOf(30), 2, 2)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonEightBitInput() {
        NoiseGenerator.addSaltPepperNoise(intArrayOf(-2, 300), 2, 1)
    }
}

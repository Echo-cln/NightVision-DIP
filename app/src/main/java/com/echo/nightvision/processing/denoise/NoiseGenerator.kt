package com.echo.nightvision.processing.denoise

import java.util.Random
import kotlin.math.roundToInt

/**
 * Reproducible synthetic noise for controlled experiments on 8-bit grayscale images.
 * This class does not denoise images or model every source of real low-light camera noise.
 */
object NoiseGenerator {

    /**
     * Adds independent Gaussian noise N(mean, sigma^2) to each grayscale pixel.
     * A fixed seed produces the same output for identical inputs and parameters.
     */
    fun addGaussianNoise(
        pixels: IntArray,
        width: Int,
        height: Int,
        mean: Double = 0.0,
        sigma: Double = 20.0,
        seed: Long = 42L
    ): IntArray {
        validateGrayscaleImage(pixels, width, height)
        require(mean.isFinite()) { "mean must be finite" }
        require(sigma.isFinite() && sigma >= 0.0) {
            "sigma must be finite and nonnegative"
        }

        val random = Random(seed)
        return IntArray(pixels.size) { index ->
            val sample = random.nextGaussian()
            val noisyPixel = pixels[index].toDouble() + mean + sigma * sample
            noisyPixel.coerceIn(0.0, 255.0).roundToInt()
        }
    }

    /**
     * Independently replaces pixels with black (pepper) or white (salt).
     * The two probabilities must sum to at most one.
     */
    fun addSaltPepperNoise(
        pixels: IntArray,
        width: Int,
        height: Int,
        saltProbability: Double = 0.025,
        pepperProbability: Double = 0.025,
        seed: Long = 42L
    ): IntArray {
        validateGrayscaleImage(pixels, width, height)
        require(saltProbability.isFinite() && saltProbability in 0.0..1.0) {
            "saltProbability must be between 0 and 1"
        }
        require(pepperProbability.isFinite() && pepperProbability in 0.0..1.0) {
            "pepperProbability must be between 0 and 1"
        }
        require(saltProbability + pepperProbability <= 1.0) {
            "saltProbability + pepperProbability must not exceed 1"
        }

        val random = Random(seed)
        return IntArray(pixels.size) { index ->
            val sample = random.nextDouble()
            when {
                sample < pepperProbability -> 0
                sample < pepperProbability + saltProbability -> 255
                else -> pixels[index]
            }
        }
    }

    private fun validateGrayscaleImage(pixels: IntArray, width: Int, height: Int) {
        require(width > 0 && height > 0) { "width and height must be positive" }
        require(width.toLong() * height.toLong() == pixels.size.toLong()) {
            "pixel array length must equal width * height"
        }
        require(pixels.all { it in 0..255 }) {
            "8-bit grayscale pixel values must be between 0 and 255"
        }
    }
}

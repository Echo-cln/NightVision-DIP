package com.echo.nightvision.processing

import kotlin.math.max
import kotlin.math.log10

data class ImageQualityMetrics(
    val mseRgb: Double,
    val psnrDb: Double,
    val ssim8x8: Double
)

data class QualityComparison(
    val before: ImageQualityMetrics,
    val after: ImageQualityMetrics?
)

/**
 * Full-reference metrics for two registered images of identical dimensions.
 *
 * PSNR uses RGB mean-squared error. SSIM is computed on Rec.709 luminance using
 * non-overlapping 8x8 uniform windows. The window convention is explicit so
 * results are reproducible and are not mislabeled as Gaussian-window SSIM.
 */
object ImageQualityEvaluator {
    private const val MAX_VALUE = 255.0
    private const val C1 = (0.01 * MAX_VALUE) * (0.01 * MAX_VALUE)
    private const val C2 = (0.03 * MAX_VALUE) * (0.03 * MAX_VALUE)
    private const val WINDOW_SIZE = 8

    fun evaluate(reference: PixelImage, candidate: PixelImage): ImageQualityMetrics {
        require(reference.width == candidate.width && reference.height == candidate.height) {
            "参考图与待评价图尺寸必须一致：参考图 " + reference.width + "×" + reference.height +
                "，待评价图 " + candidate.width + "×" + candidate.height + "。请先完成裁剪和像素配准。"
        }

        var squaredError = 0.0
        for (index in reference.pixels.indices) {
            val a = reference.pixels[index]
            val b = candidate.pixels[index]
            val red = ((a ushr 16) and 255) - ((b ushr 16) and 255)
            val green = ((a ushr 8) and 255) - ((b ushr 8) and 255)
            val blue = (a and 255) - (b and 255)
            squaredError += red * red + green * green + blue * blue
        }

        val mse = squaredError / (reference.pixels.size * 3.0)
        val psnr = if (mse == 0.0) Double.POSITIVE_INFINITY
        else 10.0 * log10((MAX_VALUE * MAX_VALUE) / mse)

        return ImageQualityMetrics(
            mseRgb = mse,
            psnrDb = psnr,
            ssim8x8 = calculateSsim8x8(reference, candidate)
        )
    }

    private fun calculateSsim8x8(reference: PixelImage, candidate: PixelImage): Double {
        var weightedSsim = 0.0
        var totalPixels = 0

        for (top in 0 until reference.height step WINDOW_SIZE) {
            val blockHeight = minOf(WINDOW_SIZE, reference.height - top)
            for (left in 0 until reference.width step WINDOW_SIZE) {
                val blockWidth = minOf(WINDOW_SIZE, reference.width - left)
                val count = blockWidth * blockHeight
                var sumA = 0.0
                var sumB = 0.0
                var sumA2 = 0.0
                var sumB2 = 0.0
                var sumAB = 0.0

                for (y in top until top + blockHeight) {
                    val rowOffset = y * reference.width
                    for (x in left until left + blockWidth) {
                        val index = rowOffset + x
                        val a = luminance(reference.pixels[index])
                        val b = luminance(candidate.pixels[index])
                        sumA += a
                        sumB += b
                        sumA2 += a * a
                        sumB2 += b * b
                        sumAB += a * b
                    }
                }

                val meanA = sumA / count
                val meanB = sumB / count
                val varianceA = max(0.0, sumA2 / count - meanA * meanA)
                val varianceB = max(0.0, sumB2 / count - meanB * meanB)
                val covariance = sumAB / count - meanA * meanB
                val numerator = (2.0 * meanA * meanB + C1) * (2.0 * covariance + C2)
                val denominator = (meanA * meanA + meanB * meanB + C1) *
                    (varianceA + varianceB + C2)
                val blockSsim = if (denominator == 0.0) 1.0
                else (numerator / denominator).coerceIn(-1.0, 1.0)

                weightedSsim += blockSsim * count
                totalPixels += count
            }
        }

        return if (totalPixels == 0) 1.0 else weightedSsim / totalPixels
    }

    private fun luminance(pixel: Int): Double {
        val red = ((pixel ushr 16) and 255).toDouble()
        val green = ((pixel ushr 8) and 255).toDouble()
        val blue = (pixel and 255).toDouble()
        return 0.2126 * red + 0.7152 * green + 0.0722 * blue
    }
}

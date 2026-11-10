package com.echo.nightvision.processing

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

/** Heuristic quality signals, not a noise-type diagnosis or a blur detector. */
data class QualitySignals(
    val noiseSigma: Double? = null,
    val flatSamples: Int = 0,
    val impulsePercent: Double = 0.0,
    val illuminationSpread: Double = 0.0
)

object EnhancementPolicy {
    fun inspect(image: PixelImage): QualitySignals {
        val residuals = ArrayList<Double>()
        val step = kotlin.math.max(1, sqrt(image.pixels.size / 4500.0).toInt())
        var samples = 0
        var impulses = 0
        fun y(x: Int, row: Int) = channelValue(image.pixels[row * image.width + x], LuminanceSpace.YCBCR)
        for (row in 1 until image.height - 1 step step) for (x in 1 until image.width - 1 step step) {
            val neighbors = doubleArrayOf(y(x - 1, row), y(x + 1, row), y(x, row - 1), y(x, row + 1))
            val center = y(x, row)
            val spread = neighbors.maxOrNull()!! - neighbors.minOrNull()!!
            val residual = abs(center - neighbors.average())
            samples++
            if ((center <= 3 || center >= 252) && residual > 50 && spread < 32) impulses++
            else if (spread < 28 && residual < 28 && center > 3 && center < 252) residuals.add(residual)
        }
        residuals.sort()
        // Median absolute high-pass residual / Gaussian consistency factor.
        val sigma = if (residuals.size >= 16) residuals[residuals.size / 2] / (.67449 * sqrt(1.25)) else null
        val means = ArrayList<Double>()
        val rows = minOf(4, image.height)
        val cols = minOf(4, image.width)
        for (ty in 0 until rows) for (tx in 0 until cols) {
            var total = 0.0
            var n = 0
            val stride = kotlin.math.max(1, minOf(image.width / cols, image.height / rows) / 32)
            for (row in ty * image.height / rows until (ty + 1) * image.height / rows step stride)
                for (x in tx * image.width / cols until (tx + 1) * image.width / cols step stride) {
                    total += y(x, row); n++
                }
            means.add(total / n)
        }
        return QualitySignals(sigma, residuals.size, if (samples == 0) 0.0 else impulses * 100.0 / samples,
            means.maxOrNull()!! - means.minOrNull()!!)
    }

    fun parameters(stats: ImageStats): ProcessParameters {
        val q = stats.quality
        val noise = q.noiseSigma ?: 0.0
        val dark = stats.mean < 105
        val localContrast = dark && stats.deviation > 2 && stats.deviation < 45 && q.illuminationSpread > 8
        val impulse = q.impulsePercent >= .3
        val method = if (impulse) DenoiseMethod.MEDIAN else DenoiseMethod.BILATERAL
        val gamma = if (stats.mean in 1.0..104.0) {
            // One tone lift rather than stacked brightness, exposure and Gamma.
            (ln(105.0 / 255) / ln(stats.mean / 255)).coerceIn(.48, 1.0).toFloat()
        } else 1f
        // Gamma can magnify weak input noise. Use its local derivative at the
        // scene mean as a conservative reference, rather than always blurring.
        val amplification = (gamma * (stats.mean.coerceAtLeast(8.0) / 255).pow(gamma - 1.0)).coerceAtLeast(1.0)
        val predictedNoise = if (noise >= .75) noise * amplification else 0.0
        val denoise = when {
            impulse -> 95f
            predictedNoise >= 2 -> (predictedNoise * 7).coerceIn(20.0, 80.0).toFloat()
            else -> 0f
        }
        return ProcessParameters(
            brightness = 0f, contrast = 0f, denoise = denoise, auto = false,
            gamma = gamma, denoiseMethod = method, denoiseDetail = if (impulse) 0f else 35f,
            bilateralSpatialSigma = 1.1f, bilateralRangeSigma = (noise * 2.5).coerceIn(8.0, 36.0).toFloat(),
            toneMethod = if (localContrast) ToneMethod.CLAHE else ToneMethod.GAMMA,
            claheClipLimit = if (noise > 5) 1.2f else 1.8f,
            claheTiles = 4, claheStrength = if (noise > 5) 20f else 35f,
            luminanceSpace = LuminanceSpace.YCBCR, protectBlack = true
        )
    }

    fun explanation(stats: ImageStats, p: ProcessParameters): String {
        val tone = if (p.toneMethod == ToneMethod.CLAHE) "明暗不均且对比度偏低，温和局部均衡后提亮" else
            if (p.gamma < 1f) "整体偏暗，使用 Gamma 提亮并保留黑场" else "亮度已足够，保持原有影调"
        val noise = if (p.denoise == 0f) "噪声证据不足，不额外平滑" else
            if (p.denoiseMethod == DenoiseMethod.MEDIAN) "孤立极值较多，采用中值降噪" else "平坦区域存在波动，采用保边双边降噪"
        return "$tone；$noise。"
    }
}

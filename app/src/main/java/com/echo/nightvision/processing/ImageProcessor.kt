package com.echo.nightvision.processing

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

enum class ProcessingMode { ENHANCE, RESTORE }
enum class DenoiseMethod { GAUSSIAN, MEDIAN }

data class PixelImage(val width: Int, val height: Int, val pixels: IntArray) {
    init {
        require(width > 0 && height > 0 && pixels.size == width * height) { "图像尺寸与像素数量不匹配" }
    }
}

data class ProcessParameters(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val denoise: Float = 18f,
    val saturation: Float = 100f,
    val temperature: Float = 0f,
    val denoiseMethod: DenoiseMethod = DenoiseMethod.GAUSSIAN,
    val auto: Boolean = true
)

data class ImageStats(
    val width: Int,
    val height: Int,
    val mean: Double,
    val deviation: Double,
    val darkPercent: Int,
    val highlightPercent: Int,
    val histogram: List<Int>,
    val channelMeans: List<Double>,
    val residualReference: Double?
)

data class ProcessingStep(val name: String, val detail: String)
data class ProcessingReport(
    val original: ImageStats,
    val processed: ImageStats,
    val gamma: Double,
    val gain: Double,
    val elapsedMillis: Long,
    val steps: List<ProcessingStep>,
    val effectiveParameters: ProcessParameters
)
data class ProcessResult(val image: PixelImage, val report: ProcessingReport)

object ImageProcessor {
    fun analyze(image: PixelImage): ImageStats {
        val histogram = IntArray(32)
        val channels = DoubleArray(3)
        var sum = 0.0
        var sum2 = 0.0
        var dark = 0
        var highlights = 0
        image.pixels.forEach { pixel ->
            val r = (pixel ushr 16) and 255
            val g = (pixel ushr 8) and 255
            val b = pixel and 255
            val y = 0.2126 * r + 0.7152 * g + 0.0722 * b
            sum += y
            sum2 += y * y
            if (y < 50.0) dark++
            if (y > 235.0) highlights++
            histogram[min(31, (y / 8.0).toInt())]++
            channels[0] += r
            channels[1] += g
            channels[2] += b
        }
        val count = image.pixels.size.toDouble()
        val mean = sum / count
        val deviation = sqrt(max(0.0, sum2 / count - mean * mean))
        return ImageStats(
            image.width, image.height, mean, deviation,
            (dark * 100.0 / count).toInt(),
            (highlights * 100.0 / count).toInt(),
            histogram.toList(), channels.map { it / count }, flatResidual(image)
        )
    }

    fun process(source: PixelImage, mode: ProcessingMode, requested: ProcessParameters): ProcessResult {
        val started = System.nanoTime()
        val before = analyze(source)
        val p = if (requested.auto) autoParameters(before, mode) else requested
        val gamma = ((100.0 - p.brightness * 0.6) / 100.0).coerceIn(0.4, 1.8)
        val gain = (1.0 + p.contrast * 0.003).coerceIn(0.7, 1.3)
        val output = source.pixels.copyOf()
        if (p.denoise > 0f) denoise(source, output, p.denoise.coerceIn(0f, 100f) / 100.0, p.denoiseMethod)
        val channelAverage = before.channelMeans.average()
        val wb = before.channelMeans.map { (channelAverage / max(1.0, it)).coerceIn(0.75, 1.3) }
        val saturation = (p.saturation / 100.0).coerceIn(0.0, 2.0)
        val temperature = p.temperature * 0.45
        for (i in output.indices) {
            val pixel = output[i]
            var r = ((pixel ushr 16) and 255).toDouble()
            var g = ((pixel ushr 8) and 255).toDouble()
            var b = (pixel and 255).toDouble()
            val y = 0.299 * r + 0.587 * g + 0.114 * b
            val mapped = 255.0 * (y / 255.0).coerceIn(0.0, 1.0).pow(gamma)
            val delta = mapped - y
            r = (r + delta - 128.0) * gain + 128.0
            g = (g + delta - 128.0) * gain + 128.0
            b = (b + delta - 128.0) * gain + 128.0
            if (mode == ProcessingMode.RESTORE) {
                r *= 1.0 + (wb[0] - 1.0) * 0.7
                g *= 1.0 + (wb[1] - 1.0) * 0.7
                b *= 1.0 + (wb[2] - 1.0) * 0.7
            }
            val luminance = 0.299 * r + 0.587 * g + 0.114 * b
            r = luminance + (r - luminance) * saturation + temperature
            g = luminance + (g - luminance) * saturation
            b = luminance + (b - luminance) * saturation - temperature
            output[i] = (pixel and -0x1000000) or
                (r.toInt().coerceIn(0, 255) shl 16) or
                (g.toInt().coerceIn(0, 255) shl 8) or
                b.toInt().coerceIn(0, 255)
        }
        val image = PixelImage(source.width, source.height, output)
        val after = analyze(image)
        val steps = buildList {
            add(ProcessingStep("图像亮度分析", "平均亮度 " + before.mean.toInt() + "/255，暗部 " + before.darkPercent + "%"))
            if (p.denoise > 0f) add(ProcessingStep(
                if (p.denoiseMethod == DenoiseMethod.MEDIAN) "3×3 中值滤波" else "3×3 高斯滤波",
                "混合强度 " + p.denoise.toInt() + "%"
            ))
            add(ProcessingStep("亮度与对比度校正", "Gamma γ = " + String.format("%.2f", gamma) + "，增益 " + String.format("%.2f", gain)))
            if (mode == ProcessingMode.RESTORE) add(ProcessingStep("灰度世界白平衡", "按 RGB 通道均值进行基础色偏校正"))
            add(ProcessingStep("色彩调整", "饱和度 " + p.saturation.toInt() + "%，色温 " + p.temperature.toInt()))
        }
        val report = ProcessingReport(
            before, after, gamma, gain,
            ((System.nanoTime() - started) / 1_000_000L).coerceAtLeast(1L),
            steps, p.copy(auto = false)
        )
        return ProcessResult(image, report)
    }

    private fun autoParameters(stats: ImageStats, mode: ProcessingMode): ProcessParameters {
        val strength = ((125.0 - stats.mean) * 0.75).toInt().coerceIn(3, 85)
        val contrast = when {
            stats.highlightPercent > 5 -> 8f
            stats.deviation < 35.0 -> 24f
            else -> 14f
        }
        val denoise = if ((stats.residualReference ?: 0.0) > 4.0) 36f else 18f
        return ProcessParameters(
            brightness = strength.toFloat(),
            contrast = contrast,
            denoise = denoise,
            saturation = if (mode == ProcessingMode.RESTORE) 106f else 100f,
            denoiseMethod = DenoiseMethod.GAUSSIAN,
            auto = false
        )
    }

    private fun denoise(source: PixelImage, out: IntArray, amount: Double, method: DenoiseMethod) {
        val width = source.width
        val height = source.height
        val src = source.pixels
        val weights = arrayOf(intArrayOf(1, 2, 1), intArrayOf(2, 4, 2), intArrayOf(1, 2, 1))
        for (y in 0 until height) for (x in 0 until width) {
            val index = y * width + x
            val center = src[index]
            val channels = Array(3) { DoubleArray(9) }
            var cursor = 0
            for (dy in -1..1) for (dx in -1..1) {
                val xx = (x + dx).coerceIn(0, width - 1)
                val yy = (y + dy).coerceIn(0, height - 1)
                val value = src[yy * width + xx]
                channels[0][cursor] = ((value ushr 16) and 255).toDouble()
                channels[1][cursor] = ((value ushr 8) and 255).toDouble()
                channels[2][cursor] = (value and 255).toDouble()
                cursor++
            }
            val original = doubleArrayOf(
                ((center ushr 16) and 255).toDouble(),
                ((center ushr 8) and 255).toDouble(),
                (center and 255).toDouble()
            )
            val filtered = DoubleArray(3)
            for (c in 0..2) {
                filtered[c] = if (method == DenoiseMethod.MEDIAN) {
                    channels[c].sort()
                    channels[c][4]
                } else {
                    var total = 0.0
                    cursor = 0
                    for (dy in -1..1) for (dx in -1..1) {
                        total += channels[c][cursor] * weights[dy + 1][dx + 1]
                        cursor++
                    }
                    total / 16.0
                }
            }
            out[index] = (center and -0x1000000) or
                ((original[0] * (1.0 - amount) + filtered[0] * amount).toInt().coerceIn(0, 255) shl 16) or
                ((original[1] * (1.0 - amount) + filtered[1] * amount).toInt().coerceIn(0, 255) shl 8) or
                (original[2] * (1.0 - amount) + filtered[2] * amount).toInt().coerceIn(0, 255)
        }
    }

    private fun flatResidual(image: PixelImage): Double? {
        if (image.width < 3 || image.height < 3) return null
        val step = max(1, sqrt(image.width * image.height / 4500.0).toInt())
        fun y(x: Int, row: Int): Double {
            val p = image.pixels[row * image.width + x]
            return 0.2126 * ((p ushr 16) and 255) + 0.7152 * ((p ushr 8) and 255) + 0.0722 * (p and 255)
        }
        var total = 0.0
        var count = 0
        for (row in 1 until image.height - 1 step step) for (x in 1 until image.width - 1 step step) {
            val n = doubleArrayOf(y(x - 1, row), y(x + 1, row), y(x, row - 1), y(x, row + 1))
            if ((n.maxOrNull()!! - n.minOrNull()!!) < 18.0) {
                total += abs(y(x, row) - n.average())
                count++
            }
        }
        return if (count == 0) null else total / count
    }
}

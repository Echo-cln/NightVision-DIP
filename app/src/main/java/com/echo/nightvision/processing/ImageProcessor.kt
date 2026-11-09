package com.echo.nightvision.processing

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class ProcessingMode { ENHANCE, RESTORE }
enum class DenoiseMethod { GAUSSIAN, MEDIAN }
enum class ToneMethod { GAMMA, HISTOGRAM_EQUALIZATION }

data class HslAdjustment(
    val hue: Float = 0f,
    val saturation: Float = 0f,
    val luminance: Float = 0f
)

fun defaultHslAdjustments(): List<HslAdjustment> = List(8) { HslAdjustment() }

private fun identityToneCurve(): List<Float> = listOf(0f, 64f, 128f, 192f, 255f)

data class ToneCurve(
    val rgb: List<Float> = identityToneCurve(),
    val red: List<Float> = identityToneCurve(),
    val green: List<Float> = identityToneCurve(),
    val blue: List<Float> = identityToneCurve()
)

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
    val auto: Boolean = true,
    val exposure: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val whites: Float = 0f,
    val blacks: Float = 0f,
    val gamma: Float = 1f,
    val tint: Float = 0f,
    val vibrance: Float = 0f,
    val clarity: Float = 0f,
    val texture: Float = 0f,
    val sharpening: Float = 0f,
    val sharpenRadius: Float = 1f,
    val sharpenDetail: Float = 50f,
    val sharpenMasking: Float = 0f,
    val denoiseDetail: Float = 50f,
    val colorDenoise: Float = 0f,
    val vignette: Float = 0f,
    val curveShadows: Float = 0f,
    val curveMidtones: Float = 0f,
    val curveHighlights: Float = 0f,
    val toneMethod: ToneMethod = ToneMethod.GAMMA,
    val whiteBalance: Boolean = false,
    val hslAdjustments: List<HslAdjustment> = defaultHslAdjustments(),
    val toneCurve: ToneCurve = ToneCurve()
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
    private val hslCenters = doubleArrayOf(0.0, 30.0, 60.0, 120.0, 180.0, 240.0, 270.0, 300.0)

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
            val y = luma(r.toDouble(), g.toDouble(), b.toDouble())
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
        val output = source.pixels.copyOf()

        if (p.denoise > 0f) {
            val denoiseAmount = (p.denoise.coerceIn(0f, 100f) / 100.0) * (1.0 - p.denoiseDetail.coerceIn(0f, 100f) / 100.0 * 0.45)
            denoise(source, output, denoiseAmount, p.denoiseMethod)
        }
        if (p.colorDenoise > 0f) {
            denoiseChroma(source, output, p.colorDenoise.coerceIn(0f, 100f) / 100.0)
        }

        val localLumaClarity = localMeanLuma(source, 2)
        val localLumaTexture = localMeanLuma(source, 1)
        val sharpenRadius = p.sharpenRadius.roundToInt().coerceIn(1, 3)
        val localLumaSharpen = localMeanLuma(source, sharpenRadius)
        val histogramLut = if (p.toneMethod == ToneMethod.HISTOGRAM_EQUALIZATION) histogramLut(source) else null
        val channelAverage = before.channelMeans.average()
        val wb = before.channelMeans.map { (channelAverage / max(1.0, it)).coerceIn(0.75, 1.3) }
        val gamma = p.gamma.coerceIn(0.2f, 3.0f).toDouble()
        val gain = (1.0 + p.contrast * 0.003).coerceIn(0.55, 1.55)
        val exposureGain = 2.0.pow(p.exposure.coerceIn(-5f, 5f).toDouble())
        val saturation = (p.saturation / 100.0).coerceIn(0.0, 2.0)
        val adjustments = p.hslAdjustments.take(8).let {
            if (it.size == 8) it else it + List(8 - it.size) { HslAdjustment() }
        }

        for (index in output.indices) {
            val pixel = output[index]
            var r = ((pixel ushr 16) and 255).toDouble()
            var g = ((pixel ushr 8) and 255).toDouble()
            var b = (pixel and 255).toDouble()
            val originalY = luma(r, g, b)
            var y = histogramLut?.get(originalY.toInt().coerceIn(0, 255))?.toDouble() ?: originalY

            y *= exposureGain
            y += p.brightness.coerceIn(-100f, 100f) * 0.55
            val normalized = (y / 255.0).coerceIn(0.0, 1.0)
            val shadowMask = 1.0 - smoothstep(0.08, 0.55, normalized)
            val highlightMask = smoothstep(0.45, 0.95, normalized)
            val whiteMask = smoothstep(0.72, 1.0, normalized)
            val blackMask = 1.0 - smoothstep(0.0, 0.28, normalized)
            val midMask = 1.0 - abs(normalized - 0.5) * 2.0

            y += p.shadows.coerceIn(-100f, 100f) * 0.58 * shadowMask
            y += p.highlights.coerceIn(-100f, 100f) * 0.58 * highlightMask
            y += p.whites.coerceIn(-100f, 100f) * 0.34 * whiteMask
            y += p.blacks.coerceIn(-100f, 100f) * 0.34 * blackMask
            y += p.curveShadows.coerceIn(-100f, 100f) * 0.28 * shadowMask
            y += p.curveMidtones.coerceIn(-100f, 100f) * 0.24 * midMask
            y += p.curveHighlights.coerceIn(-100f, 100f) * 0.28 * highlightMask

            y = 255.0 * (y / 255.0).coerceIn(0.0, 1.0).pow(gamma)
            y = (y - 128.0) * gain + 128.0

            val clarityResidual = originalY - localLumaClarity[index]
            val textureResidual = originalY - localLumaTexture[index]
            val sharpenResidual = originalY - localLumaSharpen[index]
            val maskingThreshold = p.sharpenMasking.coerceIn(0f, 100f) / 100.0 * 32.0
            val sharpenMask = smoothstep(maskingThreshold, maskingThreshold + 12.0, abs(sharpenResidual))
            val detailFactor = 0.35 + p.sharpenDetail.coerceIn(0f, 100f) / 100.0 * 0.65
            y += clarityResidual * p.clarity.coerceIn(-100f, 100f) / 100.0 * 0.55
            y += textureResidual * p.texture.coerceIn(-100f, 100f) / 100.0 * 0.30
            y += sharpenResidual * p.sharpening.coerceIn(0f, 150f) / 100.0 * detailFactor * 0.85 * sharpenMask

            val tonalDelta = y - originalY
            r += tonalDelta
            g += tonalDelta
            b += tonalDelta

            if (p.whiteBalance || mode == ProcessingMode.RESTORE) {
                r *= 1.0 + (wb[0] - 1.0) * 0.7
                g *= 1.0 + (wb[1] - 1.0) * 0.7
                b *= 1.0 + (wb[2] - 1.0) * 0.7
            }

            val warmth = p.temperature.coerceIn(-100f, 100f) * 0.42
            val tint = p.tint.coerceIn(-100f, 100f) * 0.32
            r += warmth + tint
            g -= tint * 1.35
            b -= warmth - tint

            val luminance = luma(r, g, b)
            val maxChannel = max(r, max(g, b))
            val minChannel = min(r, min(g, b))
            val chroma = if (maxChannel <= 0.0) 0.0 else ((maxChannel - minChannel) / maxChannel).coerceIn(0.0, 1.0)
            val vibranceFactor = (1.0 + p.vibrance.coerceIn(-100f, 100f) / 100.0 * (1.0 - chroma)).coerceIn(0.0, 2.0)
            val saturationFactor = saturation * vibranceFactor
            r = luminance + (r - luminance) * saturationFactor
            g = luminance + (g - luminance) * saturationFactor
            b = luminance + (b - luminance) * saturationFactor

            if (adjustments.any { it.hue != 0f || it.saturation != 0f || it.luminance != 0f }) {
                val hsl = rgbToHsl(r, g, b)
                var hue = hsl[0]
                var sat = hsl[1]
                var light = hsl[2]
                adjustments.forEachIndexed { channel, adjustment ->
                    val weight = hueWeight(hue, hslCenters[channel])
                    if (weight > 0.0) {
                        hue += adjustment.hue.coerceIn(-100f, 100f) * 0.45 * weight
                        sat *= 1.0 + adjustment.saturation.coerceIn(-100f, 100f) / 100.0 * weight
                        light += adjustment.luminance.coerceIn(-100f, 100f) / 100.0 * 0.28 * weight
                    }
                }
                val rgb = hslToRgb(hue, sat.coerceIn(0.0, 1.0), light.coerceIn(0.0, 1.0))
                r = rgb[0]
                g = rgb[1]
                b = rgb[2]
            }

            r = applyCurve(r, p.toneCurve.rgb)
            g = applyCurve(g, p.toneCurve.rgb)
            b = applyCurve(b, p.toneCurve.rgb)
            r = applyCurve(r, p.toneCurve.red)
            g = applyCurve(g, p.toneCurve.green)
            b = applyCurve(b, p.toneCurve.blue)

            val x = index % source.width
            val row = index / source.width
            val dx = (x.toDouble() / max(1, source.width - 1) - 0.5) * 2.0
            val dy = (row.toDouble() / max(1, source.height - 1) - 0.5) * 2.0
            val radius = sqrt(dx * dx + dy * dy) / sqrt(2.0)
            val edge = smoothstep(0.25, 1.0, radius)
            val vignetteFactor = 1.0 + p.vignette.coerceIn(-100f, 100f) / 100.0 * edge * 0.42
            r *= vignetteFactor
            g *= vignetteFactor
            b *= vignetteFactor

            output[index] = (pixel and -0x1000000) or
                (r.toInt().coerceIn(0, 255) shl 16) or
                (g.toInt().coerceIn(0, 255) shl 8) or
                b.toInt().coerceIn(0, 255)
        }

        val image = PixelImage(source.width, source.height, output)
        val after = analyze(image)
        val steps = buildList {
            add(ProcessingStep("图像亮度分析", "平均亮度 " + before.mean.toInt() + "/255，暗部 " + before.darkPercent + "%"))
            if (p.toneMethod == ToneMethod.HISTOGRAM_EQUALIZATION) {
                add(ProcessingStep("亮度直方图均衡化", "基于灰度累计分布进行全局映射"))
            } else {
                add(ProcessingStep("Gamma 与曝光校正", "曝光 " + String.format("%.2f", p.exposure) + " EV，Gamma γ = " + String.format("%.2f", gamma)))
            }
            if (p.denoise > 0f) add(ProcessingStep(
                if (p.denoiseMethod == DenoiseMethod.MEDIAN) "3×3 中值滤波" else "3×3 高斯滤波",
                "基础降噪强度 " + p.denoise.toInt() + "%"
            ))
            if (p.colorDenoise > 0f) add(ProcessingStep("色度降噪", "YCbCr 色度平滑强度 " + p.colorDenoise.toInt() + "%"))
            if (p.shadows != 0f || p.highlights != 0f || p.whites != 0f || p.blacks != 0f) {
                add(ProcessingStep("分区影调调整", "阴影、高光、白色色阶与黑色色阶独立映射"))
            }
            if (p.clarity != 0f || p.texture != 0f || p.sharpening != 0f) {
                add(ProcessingStep("局部细节增强", "清晰度/纹理分尺度增强；锐化半径 " + sharpenRadius + "，细节 " + p.sharpenDetail.toInt() + "%，蒙版 " + p.sharpenMasking.toInt() + "%"))
            }
            if (p.whiteBalance || mode == ProcessingMode.RESTORE) {
                add(ProcessingStep("灰度世界白平衡", "RGB 增益 " + wb.joinToString(" / ") { String.format("%.2f", it) }))
            }
            if (p.vibrance != 0f || p.saturation != 100f || p.temperature != 0f || p.tint != 0f) {
                add(ProcessingStep("色彩调整", "色温、色调、自然饱和度与整体饱和度"))
            }
            if (p.vignette != 0f) add(ProcessingStep("暗角调整", "径向渐变权重 " + p.vignette.toInt()))
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
            temperature = 0f,
            denoiseMethod = DenoiseMethod.GAUSSIAN,
            auto = false,
            exposure = if (stats.mean < 65.0) 0.35f else 0f,
            shadows = if (stats.darkPercent > 35) 18f else 8f,
            highlights = if (stats.highlightPercent > 8) -16f else 0f,
            gamma = 1f,
            whiteBalance = mode == ProcessingMode.RESTORE
        )
    }

    private fun histogramLut(source: PixelImage): IntArray {
        val histogram = IntArray(256)
        source.pixels.forEach { pixel ->
            val y = luma(
                ((pixel ushr 16) and 255).toDouble(),
                ((pixel ushr 8) and 255).toDouble(),
                (pixel and 255).toDouble()
            )
            histogram[y.toInt().coerceIn(0, 255)]++
        }
        val first = histogram.indexOfFirst { it > 0 }.coerceAtLeast(0)
        val cdfMin = histogram[first]
        val count = source.pixels.size
        val lut = IntArray(256)
        var cumulative = 0
        for (i in 0..255) {
            cumulative += histogram[i]
            lut[i] = if (count == cdfMin) i else
                (((cumulative - cdfMin).toDouble() / (count - cdfMin)) * 255.0).toInt().coerceIn(0, 255)
        }
        return lut
    }

    private fun localMeanLuma(source: PixelImage, radius: Int): FloatArray {
        val width = source.width
        val height = source.height
        val values = FloatArray(width * height)
        source.pixels.forEachIndexed { index, pixel ->
            values[index] = luma(
                ((pixel ushr 16) and 255).toDouble(),
                ((pixel ushr 8) and 255).toDouble(),
                (pixel and 255).toDouble()
            ).toFloat()
        }
        val output = FloatArray(values.size)
        for (y in 0 until height) for (x in 0 until width) {
            var sum = 0f
            var count = 0
            for (dy in -radius..radius) for (dx in -radius..radius) {
                val xx = (x + dx).coerceIn(0, width - 1)
                val yy = (y + dy).coerceIn(0, height - 1)
                sum += values[yy * width + xx]
                count++
            }
            output[y * width + x] = sum / count
        }
        return output
    }

    private fun denoise(source: PixelImage, out: IntArray, amount: Double, method: DenoiseMethod) {
        val width = source.width
        val height = source.height
        val src = source.pixels
        val weights = arrayOf(intArrayOf(1, 2, 1), intArrayOf(2, 4, 2), intArrayOf(1, 2, 1))
        val red = IntArray(9)
        val green = IntArray(9)
        val blue = IntArray(9)
        for (y in 0 until height) for (x in 0 until width) {
            val index = y * width + x
            val center = src[index]
            var cursor = 0
            for (dy in -1..1) for (dx in -1..1) {
                val xx = (x + dx).coerceIn(0, width - 1)
                val yy = (y + dy).coerceIn(0, height - 1)
                val value = src[yy * width + xx]
                red[cursor] = (value ushr 16) and 255
                green[cursor] = (value ushr 8) and 255
                blue[cursor] = value and 255
                cursor++
            }
            val filteredRed: Double
            val filteredGreen: Double
            val filteredBlue: Double
            if (method == DenoiseMethod.MEDIAN) {
                red.sort()
                green.sort()
                blue.sort()
                filteredRed = red[4].toDouble()
                filteredGreen = green[4].toDouble()
                filteredBlue = blue[4].toDouble()
            } else {
                var r = 0.0
                var g = 0.0
                var b = 0.0
                cursor = 0
                for (dy in -1..1) for (dx in -1..1) {
                    val weight = weights[dy + 1][dx + 1]
                    r += red[cursor] * weight
                    g += green[cursor] * weight
                    b += blue[cursor] * weight
                    cursor++
                }
                filteredRed = r / 16.0
                filteredGreen = g / 16.0
                filteredBlue = b / 16.0
            }
            val originalRed = ((center ushr 16) and 255).toDouble()
            val originalGreen = ((center ushr 8) and 255).toDouble()
            val originalBlue = (center and 255).toDouble()
            val outRed = (originalRed * (1.0 - amount) + filteredRed * amount).toInt().coerceIn(0, 255)
            val outGreen = (originalGreen * (1.0 - amount) + filteredGreen * amount).toInt().coerceIn(0, 255)
            val outBlue = (originalBlue * (1.0 - amount) + filteredBlue * amount).toInt().coerceIn(0, 255)
            out[index] = (center and -0x1000000) or (outRed shl 16) or (outGreen shl 8) or outBlue
        }
    }

    private fun denoiseChroma(source: PixelImage, output: IntArray, amount: Double) {
        val width = source.width
        val height = source.height
        val cb = DoubleArray(source.pixels.size)
        val cr = DoubleArray(source.pixels.size)
        source.pixels.forEachIndexed { index, pixel ->
            val r = ((pixel ushr 16) and 255).toDouble()
            val g = ((pixel ushr 8) and 255).toDouble()
            val b = (pixel and 255).toDouble()
            cb[index] = 128.0 - 0.168736 * r - 0.331264 * g + 0.5 * b
            cr[index] = 128.0 + 0.5 * r - 0.418688 * g - 0.081312 * b
        }
        for (y in 0 until height) for (x in 0 until width) {
            var cbSum = 0.0
            var crSum = 0.0
            var count = 0
            for (dy in -1..1) for (dx in -1..1) {
                val xx = (x + dx).coerceIn(0, width - 1)
                val yy = (y + dy).coerceIn(0, height - 1)
                val index = yy * width + xx
                cbSum += cb[index]
                crSum += cr[index]
                count++
            }
            val index = y * width + x
            val pixel = output[index]
            val r0 = ((pixel ushr 16) and 255).toDouble()
            val g0 = ((pixel ushr 8) and 255).toDouble()
            val b0 = (pixel and 255).toDouble()
            val yy = luma(r0, g0, b0)
            val currentCb = 128.0 - 0.168736 * r0 - 0.331264 * g0 + 0.5 * b0
            val currentCr = 128.0 + 0.5 * r0 - 0.418688 * g0 - 0.081312 * b0
            val nextCb = currentCb * (1.0 - amount) + cbSum / count * amount
            val nextCr = currentCr * (1.0 - amount) + crSum / count * amount
            val r = yy + 1.402 * (nextCr - 128.0)
            val g = yy - 0.344136 * (nextCb - 128.0) - 0.714136 * (nextCr - 128.0)
            val b = yy + 1.772 * (nextCb - 128.0)
            output[index] = (pixel and -0x1000000) or
                (r.toInt().coerceIn(0, 255) shl 16) or
                (g.toInt().coerceIn(0, 255) shl 8) or b.toInt().coerceIn(0, 255)
        }
    }

    private fun applyCurve(value: Double, points: List<Float>): Double {
        if (points.size != 5) return value.coerceIn(0.0, 255.0)
        val x = value.coerceIn(0.0, 255.0)
        val inputAnchors = doubleArrayOf(0.0, 64.0, 128.0, 192.0, 255.0)
        var segment = 0
        while (segment < 3 && x > inputAnchors[segment + 1]) segment++
        val fraction = ((x - inputAnchors[segment]) /
            (inputAnchors[segment + 1] - inputAnchors[segment])).coerceIn(0.0, 1.0)
        val a = points[segment].coerceIn(0f, 255f).toDouble()
        val b = points[segment + 1].coerceIn(0f, 255f).toDouble()
        return (a + (b - a) * fraction).coerceIn(0.0, 255.0)
    }

    private fun rgbToHsl(rValue: Double, gValue: Double, bValue: Double): DoubleArray {
        val r = (rValue / 255.0).coerceIn(0.0, 1.0)
        val g = (gValue / 255.0).coerceIn(0.0, 1.0)
        val b = (bValue / 255.0).coerceIn(0.0, 1.0)
        val maxValue = max(r, max(g, b))
        val minValue = min(r, min(g, b))
        val delta = maxValue - minValue
        var hue = 0.0
        val light = (maxValue + minValue) / 2.0
        val saturation = if (delta == 0.0) 0.0 else delta / (1.0 - abs(2.0 * light - 1.0))
        if (delta != 0.0) {
            hue = when (maxValue) {
                r -> 60.0 * (((g - b) / delta) % 6.0)
                g -> 60.0 * ((b - r) / delta + 2.0)
                else -> 60.0 * ((r - g) / delta + 4.0)
            }
        }
        if (hue < 0.0) hue += 360.0
        return doubleArrayOf(hue, saturation.coerceIn(0.0, 1.0), light)
    }

    private fun hslToRgb(hueValue: Double, saturationValue: Double, lightValue: Double): DoubleArray {
        val hue = ((hueValue % 360.0) + 360.0) % 360.0
        val saturation = saturationValue.coerceIn(0.0, 1.0)
        val light = lightValue.coerceIn(0.0, 1.0)
        val chroma = (1.0 - abs(2.0 * light - 1.0)) * saturation
        val x = chroma * (1.0 - abs((hue / 60.0) % 2.0 - 1.0))
        val m = light - chroma / 2.0
        val rgb = when {
            hue < 60.0 -> doubleArrayOf(chroma, x, 0.0)
            hue < 120.0 -> doubleArrayOf(x, chroma, 0.0)
            hue < 180.0 -> doubleArrayOf(0.0, chroma, x)
            hue < 240.0 -> doubleArrayOf(0.0, x, chroma)
            hue < 300.0 -> doubleArrayOf(x, 0.0, chroma)
            else -> doubleArrayOf(chroma, 0.0, x)
        }
        return doubleArrayOf((rgb[0] + m) * 255.0, (rgb[1] + m) * 255.0, (rgb[2] + m) * 255.0)
    }

    private fun hueWeight(hue: Double, center: Double): Double {
        val direct = abs(hue - center)
        val distance = min(direct, 360.0 - direct)
        return (1.0 - distance / 45.0).coerceIn(0.0, 1.0)
    }

    private fun smoothstep(edge0: Double, edge1: Double, value: Double): Double {
        if (edge0 == edge1) return if (value < edge0) 0.0 else 1.0
        val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0.0, 1.0)
        return t * t * (3.0 - 2.0 * t)
    }

    private fun luma(r: Double, g: Double, b: Double): Double = 0.2126 * r + 0.7152 * g + 0.0722 * b

    private fun flatResidual(image: PixelImage): Double? {
        if (image.width < 3 || image.height < 3) return null
        val step = max(1, sqrt(image.width * image.height / 4500.0).toInt())
        fun y(x: Int, row: Int): Double {
            val p = image.pixels[row * image.width + x]
            return luma(((p ushr 16) and 255).toDouble(), ((p ushr 8) and 255).toDouble(), (p and 255).toDouble())
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

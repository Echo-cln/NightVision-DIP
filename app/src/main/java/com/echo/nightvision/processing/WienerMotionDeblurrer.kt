package com.echo.nightvision.processing

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

data class WienerRestoreResult(
    val image: PixelImage,
    val workingWidth: Int,
    val workingHeight: Int,
    val fftWidth: Int,
    val fftHeight: Int,
    val effectiveMotionLength: Float,
    val applied: Boolean
)

/**
 * Frequency-domain Wiener restoration for a known, uniform linear-motion PSF.
 *
 * The luminance plane is restored at a bounded working resolution. The recovered
 * luminance correction is then interpolated onto the original image, preserving
 * its full-resolution chroma and high-frequency residual rather than exporting
 * a small upscaled image.
 *
 * H(fx, fy) is the optical transfer function of a centered uniform line segment:
 * sinc(PI * L * (fx*cos(theta) + fy*sin(theta))).
 */
object WienerMotionDeblurrer {
    private const val MAX_WORKING_DIMENSION = 896
    private const val MIN_PADDING = 8

    fun restore(
        source: PixelImage,
        motionLengthPx: Float,
        angleDegrees: Float,
        regularization: Float,
        strength: Float
    ): WienerRestoreResult {
        val requestedLength = motionLengthPx.coerceIn(0.25f, 64f)
        val blend = strength.coerceIn(0f, 100f) / 100f
        val scale = min(1.0, MAX_WORKING_DIMENSION.toDouble() / max(source.width, source.height))
        val workWidth = max(1, (source.width * scale).roundToInt())
        val workHeight = max(1, (source.height * scale).roundToInt())
        val effectiveLength = requestedLength * scale.toFloat()

        if (blend <= 0f || effectiveLength < 1f) {
            return WienerRestoreResult(
                source, workWidth, workHeight, workWidth, workHeight, effectiveLength, false
            )
        }

        val workingLuma = FloatArray(workWidth * workHeight)
        for (y in 0 until workHeight) {
            val sourceY = (y + 0.5) * source.height / workHeight - 0.5
            for (x in 0 until workWidth) {
                val sourceX = (x + 0.5) * source.width / workWidth - 0.5
                workingLuma[y * workWidth + x] = sampleSourceLuma(source, sourceX, sourceY)
            }
        }

        val padding = max(MIN_PADDING, ceil(effectiveLength / 2f).toInt() + MIN_PADDING)
        val fftWidth = nextPowerOfTwo(workWidth + padding * 2)
        val fftHeight = nextPowerOfTwo(workHeight + padding * 2)
        val fftSize = fftWidth * fftHeight
        val real = FloatArray(fftSize)
        val imaginary = FloatArray(fftSize)

        // Reflective extension reduces the wrap-around discontinuity of the DFT.
        for (y in 0 until fftHeight) {
            val sourceY = reflectIndex(y - padding, workHeight)
            val rowOffset = y * fftWidth
            val sourceOffset = sourceY * workWidth
            for (x in 0 until fftWidth) {
                val sourceX = reflectIndex(x - padding, workWidth)
                real[rowOffset + x] = workingLuma[sourceOffset + sourceX]
            }
        }

        fft2d(real, imaginary, fftWidth, fftHeight, inverse = false)

        val angle = angleDegrees.coerceIn(-90f, 90f) * PI / 180.0
        val directionX = cos(angle)
        val directionY = sin(angle)
        val length = effectiveLength.toDouble()
        val k = regularization.coerceIn(0.001f, 0.1f).toDouble()
        val frequencyX = FloatArray(fftWidth) { x ->
            val signed = if (x <= fftWidth / 2) x else x - fftWidth
            (signed.toDouble() / fftWidth * directionX).toFloat()
        }
        val frequencyY = FloatArray(fftHeight) { y ->
            val signed = if (y <= fftHeight / 2) y else y - fftHeight
            (signed.toDouble() / fftHeight * directionY).toFloat()
        }

        // Wiener transfer: H* / (|H|² + K). The centered line PSF has real H.
        for (y in 0 until fftHeight) {
            val rowOffset = y * fftWidth
            val fy = frequencyY[y].toDouble()
            for (x in 0 until fftWidth) {
                val projection = frequencyX[x].toDouble() + fy
                val argument = PI * length * projection
                val gain = if (kotlin.math.abs(argument) < 1e-7) {
                    // Motion PSFs do not attenuate frequencies perpendicular to
                    // the motion direction. Preserve those components and DC.
                    1.0
                } else {
                    val transfer = sin(argument) / argument
                    transfer / (transfer * transfer + k)
                }
                val index = rowOffset + x
                real[index] = (real[index] * gain).toFloat()
                imaginary[index] = (imaginary[index] * gain).toFloat()
            }
        }

        fft2d(real, imaginary, fftWidth, fftHeight, inverse = true)

        // Keep the deconvolution correction, not a low-resolution replacement.
        val correction = FloatArray(workWidth * workHeight)
        for (y in 0 until workHeight) {
            val restoredOffset = (y + padding) * fftWidth + padding
            val workOffset = y * workWidth
            for (x in 0 until workWidth) {
                correction[workOffset + x] = real[restoredOffset + x] - workingLuma[workOffset + x]
            }
        }

        val output = IntArray(source.pixels.size)
        for (y in 0 until source.height) {
            val workY = (y + 0.5) * workHeight / source.height - 0.5
            for (x in 0 until source.width) {
                val index = y * source.width + x
                val pixel = source.pixels[index]
                val delta = sampleField(
                    correction, workWidth, workHeight,
                    (x + 0.5) * workWidth / source.width - 0.5,
                    workY
                ) * blend
                val red = (pixel ushr 16) and 255
                val green = (pixel ushr 8) and 255
                val blue = pixel and 255
                val originalLuma = luma(red, green, blue)
                val targetLuma = (originalLuma + delta).coerceIn(0.0, 255.0)
                val tonalDelta = targetLuma - originalLuma
                val outRed = (red + tonalDelta).roundToInt().coerceIn(0, 255)
                val outGreen = (green + tonalDelta).roundToInt().coerceIn(0, 255)
                val outBlue = (blue + tonalDelta).roundToInt().coerceIn(0, 255)
                output[index] = (pixel and -0x1000000) or
                    (outRed shl 16) or (outGreen shl 8) or outBlue
            }
        }

        return WienerRestoreResult(
            PixelImage(source.width, source.height, output),
            workWidth, workHeight, fftWidth, fftHeight, effectiveLength, true
        )
    }

    private fun fft2d(
        real: FloatArray,
        imaginary: FloatArray,
        width: Int,
        height: Int,
        inverse: Boolean
    ) {
        for (y in 0 until height) {
            fft1d(real, imaginary, y * width, 1, width, inverse)
        }
        for (x in 0 until width) {
            fft1d(real, imaginary, x, width, height, inverse)
        }
    }

    private fun fft1d(
        real: FloatArray,
        imaginary: FloatArray,
        offset: Int,
        stride: Int,
        size: Int,
        inverse: Boolean
    ) {
        var j = 0
        for (i in 1 until size) {
            var bit = size shr 1
            while ((j and bit) != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val a = offset + i * stride
                val b = offset + j * stride
                val realValue = real[a]
                real[a] = real[b]
                real[b] = realValue
                val imaginaryValue = imaginary[a]
                imaginary[a] = imaginary[b]
                imaginary[b] = imaginaryValue
            }
        }

        var blockSize = 2
        while (blockSize <= size) {
            val angle = (if (inverse) 2.0 else -2.0) * PI / blockSize
            val rootReal = cos(angle).toFloat()
            val rootImaginary = sin(angle).toFloat()
            val half = blockSize / 2
            var blockStart = 0
            while (blockStart < size) {
                var weightReal = 1f
                var weightImaginary = 0f
                for (k in 0 until half) {
                    val even = offset + (blockStart + k) * stride
                    val odd = offset + (blockStart + k + half) * stride
                    val oddReal = real[odd] * weightReal - imaginary[odd] * weightImaginary
                    val oddImaginary = real[odd] * weightImaginary + imaginary[odd] * weightReal
                    val evenReal = real[even]
                    val evenImaginary = imaginary[even]
                    real[even] = evenReal + oddReal
                    imaginary[even] = evenImaginary + oddImaginary
                    real[odd] = evenReal - oddReal
                    imaginary[odd] = evenImaginary - oddImaginary

                    val nextWeightReal = weightReal * rootReal - weightImaginary * rootImaginary
                    weightImaginary = weightReal * rootImaginary + weightImaginary * rootReal
                    weightReal = nextWeightReal
                }
                blockStart += blockSize
            }
            if (blockSize == size) break
            blockSize = blockSize shl 1
        }

        if (inverse) {
            val divisor = size.toFloat()
            for (i in 0 until size) {
                val index = offset + i * stride
                real[index] /= divisor
                imaginary[index] /= divisor
            }
        }
    }

    private fun sampleSourceLuma(source: PixelImage, x: Double, y: Double): Float {
        val clampedX = x.coerceIn(0.0, (source.width - 1).toDouble())
        val clampedY = y.coerceIn(0.0, (source.height - 1).toDouble())
        val x0 = floor(clampedX).toInt()
        val y0 = floor(clampedY).toInt()
        val x1 = min(source.width - 1, x0 + 1)
        val y1 = min(source.height - 1, y0 + 1)
        val wx = clampedX - x0
        val wy = clampedY - y0
        val a = pixelLuma(source.pixels[y0 * source.width + x0])
        val b = pixelLuma(source.pixels[y0 * source.width + x1])
        val c = pixelLuma(source.pixels[y1 * source.width + x0])
        val d = pixelLuma(source.pixels[y1 * source.width + x1])
        return ((a * (1.0 - wx) + b * wx) * (1.0 - wy) +
            (c * (1.0 - wx) + d * wx) * wy).toFloat()
    }

    private fun sampleField(values: FloatArray, width: Int, height: Int, x: Double, y: Double): Double {
        val clampedX = x.coerceIn(0.0, (width - 1).toDouble())
        val clampedY = y.coerceIn(0.0, (height - 1).toDouble())
        val x0 = floor(clampedX).toInt()
        val y0 = floor(clampedY).toInt()
        val x1 = min(width - 1, x0 + 1)
        val y1 = min(height - 1, y0 + 1)
        val wx = clampedX - x0
        val wy = clampedY - y0
        val a = values[y0 * width + x0]
        val b = values[y0 * width + x1]
        val c = values[y1 * width + x0]
        val d = values[y1 * width + x1]
        return (a * (1.0 - wx) + b * wx) * (1.0 - wy) +
            (c * (1.0 - wx) + d * wx) * wy
    }

    private fun pixelLuma(pixel: Int): Double = luma(
        (pixel ushr 16) and 255,
        (pixel ushr 8) and 255,
        pixel and 255
    )

    private fun luma(red: Int, green: Int, blue: Int): Double =
        0.2126 * red + 0.7152 * green + 0.0722 * blue

    private fun reflectIndex(index: Int, size: Int): Int {
        if (size <= 1) return 0
        val period = 2 * size - 2
        val wrapped = ((index % period) + period) % period
        return if (wrapped < size) wrapped else period - wrapped
    }

    private fun nextPowerOfTwo(value: Int): Int {
        var result = 1
        while (result < value) result = result shl 1
        return result
    }
}

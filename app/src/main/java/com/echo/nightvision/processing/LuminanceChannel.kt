package com.echo.nightvision.processing

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class LuminanceSpace(val label: String) { YCBCR("YCbCr · Y"), HSV("HSV · V") }

/** Full-range BT.601 YCbCr; HSV V is max(R,G,B), with H/S held fixed. */
internal fun channelValue(pixel: Int, space: LuminanceSpace): Double {
    val r = ((pixel ushr 16) and 255).toDouble()
    val g = ((pixel ushr 8) and 255).toDouble()
    val b = (pixel and 255).toDouble()
    return if (space == LuminanceSpace.HSV) max(r, max(g, b)) else .299 * r + .587 * g + .114 * b
}

internal fun replaceChannel(pixel: Int, target: Double, space: LuminanceSpace): Int {
    val old = channelValue(pixel, space)
    val y = target.coerceIn(0.0, 255.0)
    if (kotlin.math.abs(y - old) < 1e-9) return pixel
    val r = ((pixel ushr 16) and 255).toDouble()
    val g = ((pixel ushr 8) and 255).toDouble()
    val b = (pixel and 255).toDouble()
    var nr: Double; var ng: Double; var nb: Double
    if (space == LuminanceSpace.HSV) {
        val ratio = if (old == 0.0) 0.0 else y / old
        nr = if (old == 0.0) y else r * ratio
        ng = if (old == 0.0) y else g * ratio
        nb = if (old == 0.0) y else b * ratio
    } else {
        fun limit(chroma: Double): Double = when {
            chroma > 0.0 -> (255.0 - y) / chroma
            chroma < 0.0 -> -y / chroma
            else -> 1.0
        }
        val scale = min(1.0, min(limit(r - old), min(limit(g - old), limit(b - old))))
        nr = y + (r - old) * scale
        ng = y + (g - old) * scale
        nb = y + (b - old) * scale
    }
    return (pixel and -0x1000000) or (nr.roundToInt().coerceIn(0, 255) shl 16) or
        (ng.roundToInt().coerceIn(0, 255) shl 8) or nb.roundToInt().coerceIn(0, 255)
}

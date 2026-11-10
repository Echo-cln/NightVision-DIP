package com.echo.nightvision.data

import com.echo.nightvision.processing.DenoiseMethod
import com.echo.nightvision.processing.HslAdjustment
import com.echo.nightvision.processing.ProcessParameters
import com.echo.nightvision.processing.ToneCurve
import com.echo.nightvision.processing.ToneMethod
import org.json.JSONArray
import org.json.JSONObject

fun encodeProcessParameters(parameters: ProcessParameters): String {
    val hsl = JSONArray()
    parameters.hslAdjustments.forEach { adjustment ->
        hsl.put(
            JSONObject()
                .put("hue", adjustment.hue)
                .put("saturation", adjustment.saturation)
                .put("luminance", adjustment.luminance)
        )
    }
    fun curve(values: List<Float>): JSONArray = JSONArray().also { array ->
        values.forEach { array.put(it) }
    }
    val toneCurve = JSONObject()
        .put("rgb", curve(parameters.toneCurve.rgb))
        .put("red", curve(parameters.toneCurve.red))
        .put("green", curve(parameters.toneCurve.green))
        .put("blue", curve(parameters.toneCurve.blue))

    return JSONObject()
        .put("brightness", parameters.brightness)
        .put("contrast", parameters.contrast)
        .put("denoise", parameters.denoise)
        .put("saturation", parameters.saturation)
        .put("temperature", parameters.temperature)
        .put("denoiseMethod", parameters.denoiseMethod.name)
        .put("auto", parameters.auto)
        .put("exposure", parameters.exposure)
        .put("highlights", parameters.highlights)
        .put("shadows", parameters.shadows)
        .put("whites", parameters.whites)
        .put("blacks", parameters.blacks)
        .put("gamma", parameters.gamma)
        .put("tint", parameters.tint)
        .put("vibrance", parameters.vibrance)
        .put("clarity", parameters.clarity)
        .put("texture", parameters.texture)
        .put("sharpening", parameters.sharpening)
        .put("sharpenRadius", parameters.sharpenRadius)
        .put("sharpenDetail", parameters.sharpenDetail)
        .put("sharpenMasking", parameters.sharpenMasking)
        .put("denoiseDetail", parameters.denoiseDetail)
        .put("bilateralSpatialSigma", parameters.bilateralSpatialSigma)
        .put("bilateralRangeSigma", parameters.bilateralRangeSigma)
        .put("motionDeblurEnabled", parameters.motionDeblurEnabled)
        .put("motionLengthPx", parameters.motionLengthPx)
        .put("motionAngleDegrees", parameters.motionAngleDegrees)
        .put("wienerRegularization", parameters.wienerRegularization)
        .put("motionStrength", parameters.motionStrength)
        .put("colorDenoise", parameters.colorDenoise)
        .put("vignette", parameters.vignette)
        .put("curveShadows", parameters.curveShadows)
        .put("curveMidtones", parameters.curveMidtones)
        .put("curveHighlights", parameters.curveHighlights)
        .put("toneMethod", parameters.toneMethod.name)
        .put("claheClipLimit", parameters.claheClipLimit)
        .put("claheTiles", parameters.claheTiles)
        .put("whiteBalance", parameters.whiteBalance)
        .put("hslAdjustments", hsl)
        .put("toneCurve", toneCurve)
        .toString()
}

fun decodeProcessParameters(raw: String?): ProcessParameters? {
    if (raw.isNullOrBlank()) return null
    return runCatching {
        val json = JSONObject(raw)
        val hslJson = json.optJSONArray("hslAdjustments")
        val hsl = if (hslJson == null) {
            List(8) { HslAdjustment() }
        } else {
            buildList {
                for (index in 0 until minOf(8, hslJson.length())) {
                    val item = hslJson.optJSONObject(index) ?: JSONObject()
                    add(
                        HslAdjustment(
                            hue = item.optDouble("hue", 0.0).toFloat(),
                            saturation = item.optDouble("saturation", 0.0).toFloat(),
                            luminance = item.optDouble("luminance", 0.0).toFloat()
                        )
                    )
                }
                while (size < 8) add(HslAdjustment())
            }
        }
        val curveJson = json.optJSONObject("toneCurve") ?: JSONObject()
        val identity = ToneCurve()
        fun readCurve(key: String, fallback: List<Float>): List<Float> {
            val array = curveJson.optJSONArray(key) ?: return fallback
            if (array.length() != 5) return fallback
            return List(5) { array.optDouble(it, fallback[it].toDouble()).toFloat().coerceIn(0f, 255f) }
        }
        val curve = ToneCurve(
            rgb = readCurve("rgb", identity.rgb),
            red = readCurve("red", identity.red),
            green = readCurve("green", identity.green),
            blue = readCurve("blue", identity.blue)
        )
        ProcessParameters(
            brightness = json.optDouble("brightness", 0.0).toFloat(),
            contrast = json.optDouble("contrast", 0.0).toFloat(),
            denoise = json.optDouble("denoise", 18.0).toFloat(),
            saturation = json.optDouble("saturation", 100.0).toFloat(),
            temperature = json.optDouble("temperature", 0.0).toFloat(),
            denoiseMethod = runCatching {
                DenoiseMethod.valueOf(json.optString("denoiseMethod", DenoiseMethod.GAUSSIAN.name))
            }.getOrDefault(DenoiseMethod.GAUSSIAN),
            auto = json.optBoolean("auto", false),
            exposure = json.optDouble("exposure", 0.0).toFloat(),
            highlights = json.optDouble("highlights", 0.0).toFloat(),
            shadows = json.optDouble("shadows", 0.0).toFloat(),
            whites = json.optDouble("whites", 0.0).toFloat(),
            blacks = json.optDouble("blacks", 0.0).toFloat(),
            gamma = json.optDouble("gamma", 1.0).let { if (it > 3.0) it / 100.0 else it }.toFloat(),
            tint = json.optDouble("tint", 0.0).toFloat(),
            vibrance = json.optDouble("vibrance", 0.0).toFloat(),
            clarity = json.optDouble("clarity", 0.0).toFloat(),
            texture = json.optDouble("texture", 0.0).toFloat(),
            sharpening = json.optDouble("sharpening", 0.0).toFloat(),
            sharpenRadius = json.optDouble("sharpenRadius", 1.0).toFloat(),
            sharpenDetail = json.optDouble("sharpenDetail", 50.0).toFloat(),
            sharpenMasking = json.optDouble("sharpenMasking", 0.0).toFloat(),
            denoiseDetail = json.optDouble("denoiseDetail", 50.0).toFloat(),
            bilateralSpatialSigma = json.optDouble("bilateralSpatialSigma", 1.2).toFloat().coerceIn(0.7f, 3f),
            bilateralRangeSigma = json.optDouble("bilateralRangeSigma", 25.0).toFloat().coerceIn(2f, 100f),
            motionDeblurEnabled = json.optBoolean("motionDeblurEnabled", false),
            motionLengthPx = json.optDouble("motionLengthPx", 12.0).toFloat().coerceIn(0.25f, 64f),
            motionAngleDegrees = json.optDouble("motionAngleDegrees", 0.0).toFloat().coerceIn(-90f, 90f),
            wienerRegularization = json.optDouble("wienerRegularization", 0.01).toFloat().coerceIn(0.001f, 0.1f),
            motionStrength = json.optDouble("motionStrength", 85.0).toFloat().coerceIn(0f, 100f),
            colorDenoise = json.optDouble("colorDenoise", 0.0).toFloat(),
            vignette = json.optDouble("vignette", 0.0).toFloat(),
            curveShadows = json.optDouble("curveShadows", 0.0).toFloat(),
            curveMidtones = json.optDouble("curveMidtones", 0.0).toFloat(),
            curveHighlights = json.optDouble("curveHighlights", 0.0).toFloat(),
            toneMethod = runCatching {
                ToneMethod.valueOf(json.optString("toneMethod", ToneMethod.GAMMA.name))
            }.getOrDefault(ToneMethod.GAMMA),
            whiteBalance = json.optBoolean("whiteBalance", false),
            claheClipLimit = json.optDouble("claheClipLimit", 2.0).toFloat().coerceIn(0.5f, 8f),
            claheTiles = json.optInt("claheTiles", 8).coerceIn(2, 16),
            hslAdjustments = hsl,
            toneCurve = curve
        )
    }.getOrNull()
}

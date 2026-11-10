package com.echo.nightvision

import com.echo.nightvision.data.decodeProcessParameters
import com.echo.nightvision.data.encodeProcessParameters
import com.echo.nightvision.processing.DenoiseMethod
import com.echo.nightvision.processing.ProcessParameters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WienerParametersCodecTest {
    @Test
    fun wienerAndMeanDenoiseSettingsSurviveHistoryJson() {
        val parameters = ProcessParameters(
            auto = false,
            denoiseMethod = DenoiseMethod.MEAN,
            denoise = 63f,
            denoiseDetail = 21f,
            motionDeblurEnabled = true,
            motionLengthPx = 28.5f,
            motionAngleDegrees = -33f,
            wienerRegularization = 0.012f,
            motionStrength = 74f
        )
        val decoded = decodeProcessParameters(encodeProcessParameters(parameters))
        assertEquals(parameters, decoded)
    }

    @Test
    fun legacyHistoryDefaultsWienerToDisabled() {
        val decoded = decodeProcessParameters("{}")!!
        assertFalse(decoded.motionDeblurEnabled)
        assertEquals(12f, decoded.motionLengthPx)
        assertEquals(0f, decoded.motionAngleDegrees)
        assertEquals(0.01f, decoded.wienerRegularization)
        assertEquals(85f, decoded.motionStrength)
    }
}

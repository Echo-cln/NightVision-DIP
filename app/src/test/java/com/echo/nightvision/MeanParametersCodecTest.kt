package com.echo.nightvision

import com.echo.nightvision.data.decodeProcessParameters
import com.echo.nightvision.data.encodeProcessParameters
import com.echo.nightvision.processing.DenoiseMethod
import com.echo.nightvision.processing.ProcessParameters
import org.junit.Assert.*
import org.junit.Test

class MeanParametersCodecTest {
    @Test fun meanParametersSurviveHistoryJson() {
        val parameters = ProcessParameters(auto = false, denoiseMethod = DenoiseMethod.MEAN,
            denoise = 63f, denoiseDetail = 21f)
        assertEquals(parameters, decodeProcessParameters(encodeProcessParameters(parameters)))
    }
    @Test fun oldJsonKeepsDefaultMethod() {
        assertEquals(DenoiseMethod.GAUSSIAN, decodeProcessParameters("{}")!!.denoiseMethod)
    }
}

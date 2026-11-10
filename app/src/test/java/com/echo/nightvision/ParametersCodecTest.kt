package com.echo.nightvision

import com.echo.nightvision.data.decodeProcessParameters
import com.echo.nightvision.data.encodeProcessParameters
import com.echo.nightvision.processing.*
import org.junit.Assert.*
import org.junit.Test

class ParametersCodecTest {
    @Test fun effectiveParametersRoundTripAndReplayExactly() {
        val image = PixelImage(32,32,IntArray(1024) {
            val y = 20 + it % 40
            0xFF000000.toInt() or (y shl 16) or (y shl 8) or y
        })
        val result = ImageProcessor.process(image,ProcessingMode.ENHANCE,ProcessParameters())
        val restored = decodeProcessParameters(encodeProcessParameters(result.report.effectiveParameters))!!
        assertEquals(result.report.effectiveParameters,restored)
        assertArrayEquals(result.image.pixels,ImageProcessor.process(image,ProcessingMode.ENHANCE,restored).image.pixels)
    }
    @Test fun oldRecordsUseCompatibleDefaults() {
        val restored = decodeProcessParameters("""{"brightness":12,"auto":false,"gamma":80}""")!!
        assertEquals(.8f,restored.gamma,0f)
        assertEquals(LuminanceSpace.YCBCR,restored.luminanceSpace)
        assertEquals(100f,restored.claheStrength,0f)
        assertFalse(restored.protectBlack)
    }
    @Test fun hsvAndClaheSettingsAreNotLost() {
        val original = ProcessParameters(auto=false,luminanceSpace=LuminanceSpace.HSV,
            claheStrength=27f,claheTiles=4,claheClipLimit=1.2f,protectBlack=true)
        assertEquals(original,decodeProcessParameters(encodeProcessParameters(original)))
        assertNull(decodeProcessParameters("broken JSON"))
    }
}

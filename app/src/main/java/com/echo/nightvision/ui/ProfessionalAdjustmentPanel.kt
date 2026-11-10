package com.echo.nightvision.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echo.nightvision.processing.DenoiseMethod
import com.echo.nightvision.processing.HslAdjustment
import com.echo.nightvision.processing.ProcessParameters
import com.echo.nightvision.processing.ProcessingMode
import com.echo.nightvision.processing.ToneMethod
import com.echo.nightvision.processing.ToneCurve
import java.util.Locale

private enum class AdjustmentGroup(val title: String) {
    LIGHT("光线"),
    COLOR("色彩"),
    DETAIL("细节"),
    EFFECTS("效果"),
    ALGORITHM("算法")
}

private val hslNames = listOf("红", "橙", "黄", "绿", "青", "蓝", "紫", "洋红")

@Composable
fun ProfessionalAdjustmentPanel(
    parameters: ProcessParameters,
    mode: ProcessingMode,
    accent: Color,
    onParameters: (ProcessParameters) -> Unit
) {
    var selectedGroup by remember { mutableStateOf(AdjustmentGroup.LIGHT) }
    var selectedColor by remember { mutableIntStateOf(0) }

    fun update(next: ProcessParameters) {
        onParameters(next.copy(auto = false))
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("专业调整", color = Color(0xFF182C2B), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text("实时预览 · 参数独立控制 · 原图不被覆盖", color = Color(0xFF788A83), fontSize = 10.sp)
                }
                TextButton(onClick = {
                    update(
                        when (selectedGroup) {
                            AdjustmentGroup.LIGHT -> parameters.copy(
                                brightness = 0f, contrast = 0f, exposure = 0f, highlights = 0f,
                                shadows = 0f, whites = 0f, blacks = 0f, gamma = 1f,
                                curveShadows = 0f, curveMidtones = 0f, curveHighlights = 0f, toneCurve = ToneCurve()
                            )
                            AdjustmentGroup.COLOR -> parameters.copy(
                                temperature = 0f, tint = 0f, saturation = 100f, vibrance = 0f,
                                whiteBalance = false, hslAdjustments = List(8) { HslAdjustment() }
                            )
                            AdjustmentGroup.DETAIL -> parameters.copy(
                                clarity = 0f, texture = 0f, sharpening = 0f, sharpenRadius = 1f, sharpenDetail = 50f, sharpenMasking = 0f, denoise = 0f, denoiseDetail = 50f, colorDenoise = 0f, bilateralSpatialSigma = 1.2f, bilateralRangeSigma = 25f
                            )
                            AdjustmentGroup.EFFECTS -> parameters.copy(vignette = 0f)
                            AdjustmentGroup.ALGORITHM -> parameters.copy(toneMethod = ToneMethod.GAMMA, claheClipLimit = 2f, claheTiles = 8, motionDeblurEnabled = false, motionLengthPx = 12f, motionAngleDegrees = 0f, wienerRegularization = 0.01f, motionStrength = 85f)
                        }
                    )
                }) {
                    Text("重置本组", color = accent, fontSize = 11.sp)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                AdjustmentGroup.values().forEach { group ->
                    FilterChip(
                        selected = selectedGroup == group,
                        onClick = { selectedGroup = group },
                        label = { Text(group.title, fontSize = 11.sp) }
                    )
                }
            }
            HorizontalDivider(color = Color(0xFFE8EEEA))

            when (selectedGroup) {
                AdjustmentGroup.LIGHT -> {
                    Text("基础影调", color = Color(0xFF52665D), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    AdjustmentSlider("曝光", parameters.exposure, -5f..5f, " EV", accent) {
                        update(parameters.copy(exposure = it))
                    }
                    AdjustmentSlider("亮度", parameters.brightness, -100f..100f, "", accent) {
                        update(parameters.copy(brightness = it))
                    }
                    AdjustmentSlider("对比度", parameters.contrast, -100f..100f, "", accent) {
                        update(parameters.copy(contrast = it))
                    }
                    AdjustmentSlider("高光", parameters.highlights, -100f..100f, "", accent) {
                        update(parameters.copy(highlights = it))
                    }
                    AdjustmentSlider("阴影", parameters.shadows, -100f..100f, "", accent) {
                        update(parameters.copy(shadows = it))
                    }
                    AdjustmentSlider("白色色阶", parameters.whites, -100f..100f, "", accent) {
                        update(parameters.copy(whites = it))
                    }
                    AdjustmentSlider("黑色色阶", parameters.blacks, -100f..100f, "", accent) {
                        update(parameters.copy(blacks = it))
                    }
                    AdjustmentSlider("Gamma", parameters.gamma, 0.2f..3f, "×", accent) {
                        update(parameters.copy(gamma = it))
                    }
                    HorizontalDivider(color = Color(0xFFE8EEEA))
                    Text("色调曲线", color = Color(0xFF52665D), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Text("RGB 总曲线与红、绿、蓝独立通道。拖动控制点建立非线性映射。", color = Color(0xFF788A83), fontSize = 10.sp)
                    ToneCurveEditor(parameters.toneCurve, accent) {
                        update(parameters.copy(toneCurve = it))
                    }
                }

                AdjustmentGroup.COLOR -> {
                    Text("白平衡与整体色彩", color = Color(0xFF52665D), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    AdjustmentSlider("色温", parameters.temperature, -100f..100f, "", accent) {
                        update(parameters.copy(temperature = it))
                    }
                    AdjustmentSlider("色调（绿 ↔ 洋红）", parameters.tint, -100f..100f, "", accent) {
                        update(parameters.copy(tint = it))
                    }
                    AdjustmentSlider("自然饱和度", parameters.vibrance, -100f..100f, "", accent) {
                        update(parameters.copy(vibrance = it))
                    }
                    AdjustmentSlider("整体饱和度", parameters.saturation, 0f..200f, "%", accent) {
                        update(parameters.copy(saturation = it))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("自动白平衡", color = Color(0xFF182C2B), fontSize = 12.sp)
                            Text("按 RGB 通道均值估计并校正色偏", color = Color(0xFF788A83), fontSize = 10.sp)
                        }
                        Switch(
                            checked = parameters.whiteBalance,
                            onCheckedChange = { update(parameters.copy(whiteBalance = it)) }
                        )
                    }
                    HorizontalDivider(color = Color(0xFFE8EEEA))
                    Text("HSL 色彩混合器", color = Color(0xFF52665D), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Text("选择单一颜色通道，独立调整色相、饱和度与明度。", color = Color(0xFF788A83), fontSize = 10.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        hslNames.forEachIndexed { index, name ->
                            FilterChip(
                                selected = selectedColor == index,
                                onClick = { selectedColor = index },
                                label = { Text(name, fontSize = 10.sp) }
                            )
                        }
                    }
                    val current = parameters.hslAdjustments.getOrElse(selectedColor) { HslAdjustment() }
                    fun updateHsl(next: HslAdjustment) {
                        val list = parameters.hslAdjustments.toMutableList()
                        while (list.size < 8) list.add(HslAdjustment())
                        list[selectedColor] = next
                        update(parameters.copy(hslAdjustments = list))
                    }
                    AdjustmentSlider("色相偏移", current.hue, -100f..100f, "", accent) {
                        updateHsl(current.copy(hue = it))
                    }
                    AdjustmentSlider("通道饱和度", current.saturation, -100f..100f, "", accent) {
                        updateHsl(current.copy(saturation = it))
                    }
                    AdjustmentSlider("通道明度", current.luminance, -100f..100f, "", accent) {
                        updateHsl(current.copy(luminance = it))
                    }
                }

                AdjustmentGroup.DETAIL -> {
                    Text("局部细节", color = Color(0xFF52665D), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    AdjustmentSlider("清晰度", parameters.clarity, -100f..100f, "", accent) {
                        update(parameters.copy(clarity = it))
                    }
                    AdjustmentSlider("纹理", parameters.texture, -100f..100f, "", accent) {
                        update(parameters.copy(texture = it))
                    }
                    AdjustmentSlider("锐化强度", parameters.sharpening, 0f..150f, "%", accent) {
                        update(parameters.copy(sharpening = it))
                    }
                    AdjustmentSlider("锐化半径", parameters.sharpenRadius, 1f..3f, " px", accent) {
                        update(parameters.copy(sharpenRadius = it))
                    }
                    AdjustmentSlider("锐化细节", parameters.sharpenDetail, 0f..100f, "%", accent) {
                        update(parameters.copy(sharpenDetail = it))
                    }
                    AdjustmentSlider("锐化蒙版", parameters.sharpenMasking, 0f..100f, "%", accent) {
                        update(parameters.copy(sharpenMasking = it))
                    }
                    HorizontalDivider(color = Color(0xFFE8EEEA))
                    Text("噪声抑制", color = Color(0xFF52665D), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    AdjustmentSlider("明度降噪", parameters.denoise, 0f..100f, "%", accent) {
                        update(parameters.copy(denoise = it))
                    }
                    AdjustmentSlider("细节保留", parameters.denoiseDetail, 0f..100f, "%", accent) {
                        update(parameters.copy(denoiseDetail = it))
                    }
                    AdjustmentSlider("色彩降噪", parameters.colorDenoise, 0f..100f, "%", accent) {
                        update(parameters.copy(colorDenoise = it))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        FilterChip(
                            selected = parameters.denoiseMethod == DenoiseMethod.GAUSSIAN,
                            onClick = { update(parameters.copy(denoiseMethod = DenoiseMethod.GAUSSIAN)) },
                            label = { Text("高斯滤波", fontSize = 10.sp) }
                        )
                        FilterChip(
                            selected = parameters.denoiseMethod == DenoiseMethod.MEDIAN,
                            onClick = { update(parameters.copy(denoiseMethod = DenoiseMethod.MEDIAN)) },
                            label = { Text("中值滤波", fontSize = 10.sp) }
                        )
                        FilterChip(
                            selected = parameters.denoiseMethod == DenoiseMethod.BILATERAL,
                            onClick = { update(parameters.copy(denoiseMethod = DenoiseMethod.BILATERAL)) },
                            label = { Text("双边滤波", fontSize = 10.sp) }
                        )
                    }
                    if (parameters.denoiseMethod == DenoiseMethod.BILATERAL) {
                        AdjustmentSlider("空间标准差 σs", parameters.bilateralSpatialSigma, 0.7f..3f, "", accent) {
                            update(parameters.copy(bilateralSpatialSigma = it))
                        }
                        AdjustmentSlider("亮度标准差 σr", parameters.bilateralRangeSigma, 2f..100f, "", accent) {
                            update(parameters.copy(bilateralRangeSigma = it))
                        }
                        Text("双边滤波会根据空间距离与亮度差异加权，优先保留高对比边缘。",
                             color = Color(0xFF788A83), fontSize = 10.sp)
                    }
                }

                AdjustmentGroup.EFFECTS -> {
                    Text("画面效果", color = Color(0xFF52665D), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    AdjustmentSlider("暗角", parameters.vignette, -100f..100f, "", accent) {
                        update(parameters.copy(vignette = it))
                    }
                    Text(
                        "正值提亮边缘，负值压暗边缘。局部蒙版、去雾与颗粒效果将在相应算法完成后开放。",
                        color = Color(0xFF788A83), fontSize = 10.sp
                    )
                }

                AdjustmentGroup.ALGORITHM -> {
                    Text("当前可用的影调算法", color = Color(0xFF52665D), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    FilterChip(
                        selected = parameters.toneMethod == ToneMethod.GAMMA,
                        onClick = { update(parameters.copy(toneMethod = ToneMethod.GAMMA)) },
                        label = { Text("Gamma 校正") }
                    )
                    FilterChip(
                        selected = parameters.toneMethod == ToneMethod.HISTOGRAM_EQUALIZATION,
                        onClick = { update(parameters.copy(toneMethod = ToneMethod.HISTOGRAM_EQUALIZATION)) },
                        label = { Text("直方图均衡化") }
                    )
                    FilterChip(
                        selected = parameters.toneMethod == ToneMethod.CLAHE,
                        onClick = { update(parameters.copy(toneMethod = ToneMethod.CLAHE)) },
                        label = { Text("CLAHE 局部均衡") }
                    )
                    if (parameters.toneMethod == ToneMethod.CLAHE) {
                        AdjustmentSlider("对比度裁剪阈值", parameters.claheClipLimit, 0.5f..8f, "", accent) {
                            update(parameters.copy(claheClipLimit = it))
                        }
                        AdjustmentSlider("局部网格", parameters.claheTiles.toFloat(), 2f..16f, "×", accent) {
                            update(parameters.copy(claheTiles = it.toInt().coerceIn(2, 16)))
                        }
                        Text("先裁剪各局部灰度直方图，再双线性插值合成连续映射。数值越大，局部对比越强，也可能放大噪声。",
                             color = Color(0xFF788A83), fontSize = 10.sp)
                    }
                    if (mode == ProcessingMode.RESTORE) {
                        HorizontalDivider(color = Color(0xFFE8EEEA))
                        Text("运动模糊复原 · Wiener", color = Color(0xFF52665D), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        FilterChip(
                            selected = parameters.motionDeblurEnabled,
                            onClick = { update(parameters.copy(motionDeblurEnabled = !parameters.motionDeblurEnabled)) },
                            label = { Text(if (parameters.motionDeblurEnabled) "Wiener 已启用" else "启用 Wiener 运动复原") }
                        )
                        if (parameters.motionDeblurEnabled) {
                            AdjustmentSlider("运动长度", parameters.motionLengthPx, 2f..64f, " px", accent) {
                                update(parameters.copy(motionLengthPx = it))
                            }
                            AdjustmentSlider("运动方向", parameters.motionAngleDegrees, -90f..90f, "°", accent) {
                                update(parameters.copy(motionAngleDegrees = it))
                            }
                            AdjustmentSlider(
                                "Wiener 正则化 K", parameters.wienerRegularization, 0.001f..0.1f, "", accent,
                                valueLabel = { String.format(Locale.US, "%.3f", it) }
                            ) {
                                update(parameters.copy(wienerRegularization = it))
                            }
                            AdjustmentSlider("复原强度", parameters.motionStrength, 0f..100f, "%", accent) {
                                update(parameters.copy(motionStrength = it))
                            }
                            Text(
                                "模型假设曝光期间发生匀速直线运动。0° 为水平方向，90° 为垂直方向。K 越大，噪声抑制越强，但去模糊也会更保守。请尽量依据拖影方向估计 PSF。",
                                color = Color(0xFF788A83), fontSize = 10.sp, lineHeight = 16.sp
                            )
                        } else {
                            Text(
                                "适用于近似线性、匀速运动造成的拖影。启用后指定模糊长度与方向，再点击“应用当前参数”。",
                                color = Color(0xFF788A83), fontSize = 10.sp, lineHeight = 16.sp
                            )
                        }
                    }
                    HorizontalDivider(color = Color(0xFFE8EEEA))
                    Text("其他算法研发中", color = Color(0xFF52665D), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Text(
                        "Retinex、同态滤波和非局部均值尚未接入算法内核，因此当前不开放对应调节入口。",
                        color = Color(0xFF788A83), fontSize = 10.sp, lineHeight = 16.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = false, onClick = {}, enabled = false, label = { Text("非局部均值 · 待接入") })
                        FilterChip(selected = false, onClick = {}, enabled = false, label = { Text("Retinex · 待接入") })
                        FilterChip(selected = false, onClick = {}, enabled = false, label = { Text("同态滤波 · 待接入") })
                    }
                }
            }
        }
    }
}

@Composable
private fun AdjustmentSlider(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    suffix: String,
    accent: Color,
    valueLabel: ((Float) -> String)? = null,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color(0xFF263B35), fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text(
                (valueLabel?.invoke(value) ?: String.format(Locale.getDefault(), "%.1f", value)) + suffix,
                color = accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Slider(
            value = value.coerceIn(range),
            onValueChange = onValueChange,
            valueRange = range
        )
    }
}

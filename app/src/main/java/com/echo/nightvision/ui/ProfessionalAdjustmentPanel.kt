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
                                shadows = 0f, whites = 0f, blacks = 0f, gamma = 100f,
                                curveShadows = 0f, curveMidtones = 0f, curveHighlights = 0f, toneCurve = ToneCurve()
                            )
                            AdjustmentGroup.COLOR -> parameters.copy(
                                temperature = 0f, tint = 0f, saturation = 100f, vibrance = 0f,
                                whiteBalance = false, hslAdjustments = List(8) { HslAdjustment() }
                            )
                            AdjustmentGroup.DETAIL -> parameters.copy(
                                clarity = 0f, texture = 0f, sharpening = 0f, denoise = 0f, colorDenoise = 0f
                            )
                            AdjustmentGroup.EFFECTS -> parameters.copy(vignette = 0f)
                            AdjustmentGroup.ALGORITHM -> parameters.copy(toneMethod = ToneMethod.GAMMA)
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
                    AdjustmentSlider("Gamma", parameters.gamma, 20f..300f, "%", accent) {
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
                    HorizontalDivider(color = Color(0xFFE8EEEA))
                    Text("噪声抑制", color = Color(0xFF52665D), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    AdjustmentSlider("明度降噪", parameters.denoise, 0f..100f, "%", accent) {
                        update(parameters.copy(denoise = it))
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
                    HorizontalDivider(color = Color(0xFFE8EEEA))
                    Text("专业算法实验室", color = Color(0xFF52665D), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Text(
                        "CLAHE、Retinex、同态滤波、双边滤波、非局部均值、Laplacian 锐化和 Wiener 运动模糊复原属于独立算法模块。完成算法实现和验证后再开放参数，避免出现调节了却没有效果的伪功能。",
                        color = Color(0xFF788A83), fontSize = 10.sp, lineHeight = 16.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = false, onClick = {}, enabled = false, label = { Text("CLAHE · 待接入") })
                        FilterChip(selected = false, onClick = {}, enabled = false, label = { Text("Retinex · 待接入") })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = false, onClick = {}, enabled = false, label = { Text("同态滤波 · 待接入") })
                        FilterChip(selected = false, onClick = {}, enabled = false, label = { Text("Wiener · 待接入") })
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
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color(0xFF263B35), fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text(
                String.format(Locale.getDefault(), "%.1f", value) + suffix,
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

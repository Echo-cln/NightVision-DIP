package com.echo.nightvision.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echo.nightvision.processing.ToneCurve

@Composable
fun ToneCurveEditor(
    curve: ToneCurve,
    accent: Color,
    onChange: (ToneCurve) -> Unit
) {
    var channel by remember { mutableIntStateOf(0) }
    val labels = listOf("RGB", "红", "绿", "蓝")
    val values = when (channel) {
        1 -> curve.red
        2 -> curve.green
        3 -> curve.blue
        else -> curve.rgb
    }.let { if (it.size == 5) it else listOf(0f, 64f, 128f, 192f, 255f) }
    val latestValues = rememberUpdatedState(values)
    val latestChange = rememberUpdatedState(onChange)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            labels.forEachIndexed { index, label ->
                FilterChip(
                    selected = channel == index,
                    onClick = { channel = index },
                    label = { Text(label, fontSize = 10.sp) }
                )
            }
        }
        Canvas(
            modifier = Modifier.fillMaxWidth().height(170.dp).pointerInput(channel) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val pad = 12.dp.toPx()
                    val plotWidth = (size.width - pad * 2f).coerceAtLeast(1f)
                    val plotHeight = (size.height - pad * 2f).coerceAtLeast(1f)
                    val x = ((change.position.x - pad) / plotWidth).coerceIn(0f, 1f)
                    val y = (1f - (change.position.y - pad) / plotHeight).coerceIn(0f, 1f)
                    val index = (x * 4f).toInt().coerceIn(0, 4)
                    val next = latestValues.value.toMutableList()
                    next[index] = y * 255f
                    val current = latestChange.value
                    when (channel) {
                        1 -> current(curve.copy(red = next))
                        2 -> current(curve.copy(green = next))
                        3 -> current(curve.copy(blue = next))
                        else -> current(curve.copy(rgb = next))
                    }
                }
            }
        ) {
            val pad = 12.dp.toPx()
            val plotWidth = (size.width - pad * 2f).coerceAtLeast(1f)
            val plotHeight = (size.height - pad * 2f).coerceAtLeast(1f)
            val left = pad
            val top = pad
            val right = pad + plotWidth
            val bottom = pad + plotHeight
            drawRect(Color(0xFFF7F9F7), topLeft = Offset(left, top), size = androidx.compose.ui.geometry.Size(plotWidth, plotHeight))
            for (step in 0..4) {
                val x = left + plotWidth * step / 4f
                val y = top + plotHeight * step / 4f
                drawLine(Color(0xFFDCE5DF), Offset(x, top), Offset(x, bottom), 1.dp.toPx())
                drawLine(Color(0xFFDCE5DF), Offset(left, y), Offset(right, y), 1.dp.toPx())
            }
            drawLine(Color(0xFF9EAAA3), Offset(left, bottom), Offset(right, top), 1.dp.toPx())
            val points = values.mapIndexed { index, value ->
                Offset(
                    left + plotWidth * index / 4f,
                    bottom - plotHeight * value.coerceIn(0f, 255f) / 255f
                )
            }
            for (index in 0 until points.lastIndex) {
                drawLine(accent, points[index], points[index + 1], 3.dp.toPx())
            }
            points.forEach { point ->
                drawCircle(Color.White, 7.dp.toPx(), point)
                drawCircle(accent, 5.dp.toPx(), point)
            }
        }
        Text("拖动曲线控制点调整输入亮度到输出亮度的映射。", color = Color(0xFF788A83), fontSize = 10.sp)
    }
}

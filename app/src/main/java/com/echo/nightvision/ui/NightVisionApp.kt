package com.echo.nightvision.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echo.nightvision.data.HistoryItem
import com.echo.nightvision.processing.DenoiseMethod
import com.echo.nightvision.processing.ImageStats
import com.echo.nightvision.processing.PixelImage
import com.echo.nightvision.processing.ProcessParameters
import com.echo.nightvision.processing.ProcessingMode
import com.echo.nightvision.processing.ProcessingReport
import kotlin.math.min

private val Ink = Color(0xFF182C2B)
private val Muted = Color(0xFF788A83)
private val Page = Color(0xFFF6F8F7)
private val Line = Color(0xFFE5ECE8)
private val Green = Color(0xFF006C59)
private val Rose = Color(0xFF7C405C)
private val Amber = Color(0xFF8A6332)
private val RoseSoft = Color(0xFFF7EDF1)
private val AmberSoft = Color(0xFFF7F0E6)

enum class AppScreen { HOME, EDITOR, HISTORY }
enum class ImageViewMode { RESULT, ORIGINAL, COMPARE }
enum class AppThemeChoice { GREEN, BERRY, AMBER }

@Composable
fun NightVisionApp(
    screen: AppScreen,
    mode: ProcessingMode,
    source: PixelImage?,
    result: PixelImage?,
    report: ProcessingReport?,
    parameters: ProcessParameters,
    busy: Boolean,
    history: List<HistoryItem>,
    theme: AppThemeChoice,
    viewMode: ImageViewMode,
    split: Float,
    message: String?,
    onTheme: (AppThemeChoice) -> Unit,
    onScreen: (AppScreen) -> Unit,
    onMode: (ProcessingMode) -> Unit,
    onPick: () -> Unit,
    onCamera: () -> Unit,
    onProcess: () -> Unit,
    onParameters: (ProcessParameters) -> Unit,
    onViewMode: (ImageViewMode) -> Unit,
    onSplit: (Float) -> Unit,
    onSave: () -> Unit,
    onOpenHistory: (HistoryItem) -> Unit,
    onDeleteHistory: (HistoryItem) -> Unit,
    onClearHistory: () -> Unit
) {
    val accent = when (theme) {
        AppThemeChoice.GREEN -> Green
        AppThemeChoice.BERRY -> Rose
        AppThemeChoice.AMBER -> Amber
    }
    val soft = when (theme) {
        AppThemeChoice.GREEN -> Color(0xFFEAF3EC)
        AppThemeChoice.BERRY -> RoseSoft
        AppThemeChoice.AMBER -> AmberSoft
    }
    Scaffold(
        containerColor = Page,
        bottomBar = {
            if (screen != AppScreen.EDITOR) NavigationBar(containerColor = Color.White) {
                NavigationBarItem(screen == AppScreen.HOME, { onScreen(AppScreen.HOME) }, icon = { Text("⌂") }, label = { Text("首页") })
                NavigationBarItem(screen == AppScreen.HISTORY, { onScreen(AppScreen.HISTORY) }, icon = { Text("◷") }, label = { Text("处理记录") })
            }
        }
    ) { padding ->
        when (screen) {
            AppScreen.HOME -> HomeScreen(Modifier.padding(padding), history, theme, accent, soft, onTheme, onMode, onScreen)
            AppScreen.EDITOR -> EditorScreen(
                Modifier.padding(padding), mode, source, result, report, parameters, busy,
                accent, viewMode, split, message, onScreen, onPick, onCamera, onProcess,
                onParameters, onViewMode, onSplit, onSave
            )
            AppScreen.HISTORY -> HistoryScreen(Modifier.padding(padding), history, accent, onOpenHistory, onDeleteHistory, onClearHistory)
        }
    }
}

@Composable
private fun HomeScreen(
    modifier: Modifier,
    history: List<HistoryItem>,
    theme: AppThemeChoice,
    accent: Color,
    soft: Color,
    onTheme: (AppThemeChoice) -> Unit,
    onMode: (ProcessingMode) -> Unit,
    onScreen: (AppScreen) -> Unit
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(color = accent, shape = RoundedCornerShape(12.dp), modifier = Modifier.size(38.dp)) {
                Box(contentAlignment = Alignment.Center) { Text("☾", color = Color(0xFFC9F36C), fontSize = 23.sp) }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("夜视大师", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Text("NIGHTVISION · IMAGE STUDIO", color = Muted, fontSize = 9.sp, letterSpacing = 1.sp)
            }
            TextButton(onClick = { onScreen(AppScreen.HISTORY) }) { Text("记录", color = accent) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            ThemeChip("苔绿", theme == AppThemeChoice.GREEN, Green) { onTheme(AppThemeChoice.GREEN) }
            ThemeChip("莓酒红", theme == AppThemeChoice.BERRY, Rose) { onTheme(AppThemeChoice.BERRY) }
            ThemeChip("暖棕金", theme == AppThemeChoice.AMBER, Amber) { onTheme(AppThemeChoice.AMBER) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("NIGHTVISION · IMAGE STUDIO", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
            Text("让暗处，重新清晰。", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Medium)
            Text("选择处理功能，再导入照片或现场拍摄。", color = Muted, fontSize = 13.sp)
        }
        ModuleCard("低照度增强", "提亮暗部，调整对比度，\n看清夜色里的更多细节。", "自动增强", accent, soft, "☼") {
            onMode(ProcessingMode.ENHANCE)
            onScreen(AppScreen.EDITOR)
        }
        ModuleCard("图像复原", "校正颜色与亮度，\n对照处理前后的完整画面。", "图像校正", Amber, AmberSoft, "⌁") {
            onMode(ProcessingMode.RESTORE)
            onScreen(AppScreen.EDITOR)
        }
        Surface(color = Color.White, shape = RoundedCornerShape(15.dp)) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("✓", color = accent, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text("照片在本机处理 · 原图独立保留", color = Muted, fontSize = 12.sp)
            }
        }
        if (history.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("最近处理", color = Ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = { onScreen(AppScreen.HISTORY) }) { Text("全部记录", color = accent) }
            }
            HistoryRow(history.first(), accent, { onScreen(AppScreen.HISTORY) }, null)
        }
    }
}

@Composable
private fun ThemeChip(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 11.sp) },
        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
            selectedContainerColor = accent.copy(alpha = 0.13f),
            selectedLabelColor = accent
        )
    )
}

@Composable
private fun ModuleCard(
    title: String,
    description: String,
    tag: String,
    accent: Color,
    background: Color,
    symbol: String,
    onClick: () -> Unit
) {
    Surface(onClick = onClick, color = background, shape = RoundedCornerShape(23.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = Color.White.copy(alpha = 0.8f), shape = RoundedCornerShape(12.dp), modifier = Modifier.size(42.dp)) {
                    Box(contentAlignment = Alignment.Center) { Text(symbol, color = accent, fontSize = 25.sp) }
                }
                Spacer(Modifier.weight(1f))
                Surface(color = Color.White.copy(alpha = 0.75f), shape = CircleShape) {
                    Text(tag, color = accent, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }
            Text(title, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 21.sp)
            Text(description, color = Muted, fontSize = 13.sp, lineHeight = 21.sp)
            Text("进入模块  →", color = accent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun EditorScreen(
    modifier: Modifier,
    mode: ProcessingMode,
    source: PixelImage?,
    result: PixelImage?,
    report: ProcessingReport?,
    params: ProcessParameters,
    busy: Boolean,
    accent: Color,
    viewMode: ImageViewMode,
    split: Float,
    message: String?,
    onScreen: (AppScreen) -> Unit,
    onPick: () -> Unit,
    onCamera: () -> Unit,
    onProcess: () -> Unit,
    onParams: (ProcessParameters) -> Unit,
    onViewMode: (ImageViewMode) -> Unit,
    onSplit: (Float) -> Unit,
    onSave: () -> Unit
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onScreen(AppScreen.HOME) }) { Text("‹ 返回", color = accent) }
            Spacer(Modifier.weight(1f))
            Text(if (mode == ProcessingMode.ENHANCE) "低照度增强" else "图像复原", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text("本机运行", color = Muted, fontSize = 10.sp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (mode == ProcessingMode.ENHANCE) "让暗部细节显现" else "从一张待校正的图片开始", color = Ink, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
            Text(if (mode == ProcessingMode.ENHANCE) "分析亮度分布，匹配增强参数。" else "调整亮度与颜色，原图始终保留。", color = Muted, fontSize = 12.sp)
        }
        if (source == null) {
            Surface(color = Color(0xFFEFF4F1), shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth().height(220.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("▧", color = accent, fontSize = 44.sp)
                    Text("先选择一张图片", color = Ink, fontWeight = FontWeight.SemiBold)
                    Text("支持相册导入与系统相机拍摄", color = Muted, fontSize = 11.sp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onPick, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = accent)) { Text("导入照片") }
                OutlinedButton(onClick = onCamera, modifier = Modifier.weight(1f)) { Text("现场拍照", color = accent) }
            }
            if (message != null) Text(message, color = Color(0xFF9A4C4C), fontSize = 11.sp)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("处理预览", color = Ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(source.width.toString() + " × " + source.height, color = Muted, fontSize = 11.sp)
            }
            ImageComparison(source, result, if (result == null) ImageViewMode.ORIGINAL else viewMode, split, onSplit)
            if (result != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    ViewChip("效果", viewMode == ImageViewMode.RESULT) { onViewMode(ImageViewMode.RESULT) }
                    ViewChip("原图", viewMode == ImageViewMode.ORIGINAL) { onViewMode(ImageViewMode.ORIGINAL) }
                    ViewChip("拖动对比", viewMode == ImageViewMode.COMPARE) { onViewMode(ImageViewMode.COMPARE) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onSave) { Text("保存", color = accent, fontSize = 12.sp) }
                }
            }
            if (report != null) StatsCard(report, accent)
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(19.dp)) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("处理参数", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onParams(ProcessParameters()) }) {
                            Text("恢复自动匹配", color = accent, fontSize = 10.sp)
                        }
                    }
                    ParamSlider("亮度", params.brightness, -100f..100f, { it.toInt().toString() }) { onParams(params.copy(brightness = it, auto = false)) }
                    ParamSlider("对比度", params.contrast, -100f..100f, { it.toInt().toString() }) { onParams(params.copy(contrast = it, auto = false)) }
                    ParamSlider("降噪强度", params.denoise, 0f..100f, { it.toInt().toString() + "%" }) { onParams(params.copy(denoise = it, auto = false)) }
                    ParamSlider("饱和度", params.saturation, 0f..200f, { it.toInt().toString() + "%" }) { onParams(params.copy(saturation = it, auto = false)) }
                    ParamSlider("色温", params.temperature, -40f..40f, { it.toInt().toString() }) { onParams(params.copy(temperature = it, auto = false)) }
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        FilterChip(params.denoiseMethod == DenoiseMethod.GAUSSIAN, { onParams(params.copy(denoiseMethod = DenoiseMethod.GAUSSIAN, auto = false)) }, label = { Text("高斯滤波") })
                        FilterChip(params.denoiseMethod == DenoiseMethod.MEDIAN, { onParams(params.copy(denoiseMethod = DenoiseMethod.MEDIAN, auto = false)) }, label = { Text("中值滤波") })
                    }
                    Button(onClick = onProcess, enabled = !busy, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = accent)) {
                        Text(if (busy) "正在处理…" else if (result == null) "开始处理" else "应用当前参数")
                    }
                    if (message != null) Text(message, color = Muted, fontSize = 11.sp)
                }
            }
            if (report != null) {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(19.dp)) {
                    Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("处理步骤", color = Ink, fontWeight = FontWeight.SemiBold)
                        report.steps.forEachIndexed { index, step ->
                            Row(verticalAlignment = Alignment.Top) {
                                Text((index + 1).toString(), color = accent, fontWeight = FontWeight.Bold, modifier = Modifier.width(22.dp))
                                Column {
                                    Text(step.name, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    Text(step.detail, color = Muted, fontSize = 10.sp)
                                }
                            }
                        }
                        HorizontalDivider(color = Line)
                        Text("本次计算耗时 " + report.elapsedMillis + " ms", color = Muted, fontSize = 10.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun ImageComparison(source: PixelImage, result: PixelImage?, mode: ImageViewMode, split: Float, onSplit: (Float) -> Unit) {
    val originalBitmap = remember(source) { Bitmap.createBitmap(source.pixels, source.width, source.height, Bitmap.Config.ARGB_8888).asImageBitmap() }
    val resultBitmap = remember(result) { result?.let { Bitmap.createBitmap(it.pixels, it.width, it.height, Bitmap.Config.ARGB_8888).asImageBitmap() } }
    Surface(color = Color(0xFF172522), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().height(265.dp)) {
        when (mode) {
            ImageViewMode.ORIGINAL -> Image(originalBitmap, "原图", modifier = Modifier.fillMaxSize())
            ImageViewMode.RESULT -> if (resultBitmap != null) Image(resultBitmap, "处理结果", modifier = Modifier.fillMaxSize()) else Image(originalBitmap, "原图", modifier = Modifier.fillMaxSize())
            ImageViewMode.COMPARE -> if (resultBitmap != null) {
                Canvas(Modifier.fillMaxSize().pointerInput(split) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        if (size.width > 0) onSplit((change.position.x / size.width).coerceIn(0.05f, 0.95f))
                    }
                }) {
                    val scale = min(size.width / source.width, size.height / source.height)
                    val w = (source.width * scale).toInt()
                    val h = (source.height * scale).toInt()
                    val left = ((size.width - w) / 2f).toInt()
                    val top = ((size.height - h) / 2f).toInt()
                    val dst = IntOffset(left, top)
                    val dstSize = IntSize(w, h)
                    drawImage(resultBitmap, dstOffset = dst, dstSize = dstSize)
                    val divider = left + w * split
                    clipRect(left.toFloat(), top.toFloat(), divider, (top + h).toFloat()) {
                        drawImage(originalBitmap, dstOffset = dst, dstSize = dstSize)
                    }
                    drawLine(Color.White, Offset(divider, top.toFloat()), Offset(divider, (top + h).toFloat()), 2.dp.toPx())
                    drawCircle(Color.White, 12.dp.toPx(), Offset(divider, size.height / 2f))
                }
            } else Image(originalBitmap, "原图", modifier = Modifier.fillMaxSize())
        }
    }
    if (mode == ImageViewMode.COMPARE && result != null) Text(
        "拖动分隔线 · 左侧原图 / 右侧处理结果",
        color = Muted, fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ViewChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected, onClick, label = { Text(label, fontSize = 10.sp) })
}

@Composable
private fun ParamSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, format: (Float) -> String, onChange: (Float) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Ink, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text(format(value), color = Muted, fontSize = 10.sp)
        }
        Slider(value.coerceIn(range), onChange, valueRange = range)
    }
}

@Composable
private fun StatsCard(report: ProcessingReport, accent: Color) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(17.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("图像分析", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Stat("平均亮度", report.original.mean.toInt().toString() + " → " + report.processed.mean.toInt(), accent, Modifier.weight(1f))
                Stat("暗部比例", report.original.darkPercent.toString() + "% → " + report.processed.darkPercent + "%", accent, Modifier.weight(1f))
            }
            Text("原图亮度分布", color = Muted, fontSize = 10.sp)
            Histogram(report.original, accent)
            Text("噪声参考值为平坦区域残差估计，不等同于噪声真值。", color = Muted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun Stat(title: String, value: String, accent: Color, modifier: Modifier) {
    Column(modifier.background(Color(0xFFF5F8F6), RoundedCornerShape(10.dp)).padding(9.dp)) {
        Text(title, color = Muted, fontSize = 9.sp)
        Text(value, color = accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Histogram(stats: ImageStats, accent: Color) {
    val maxValue = (stats.histogram.maxOrNull() ?: 1).coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().height(52.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        stats.histogram.forEach { value ->
            Box(
                Modifier.weight(1f).height((value.toFloat() / maxValue * 50f).coerceAtLeast(2f).dp)
                    .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                    .background(accent.copy(alpha = 0.72f))
            )
        }
    }
}

@Composable
private fun HistoryScreen(
    modifier: Modifier,
    history: List<HistoryItem>,
    accent: Color,
    onOpen: (HistoryItem) -> Unit,
    onDelete: (HistoryItem) -> Unit,
    onClear: () -> Unit
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("处理记录", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (history.isNotEmpty()) TextButton(onClick = onClear) { Text("清空", color = Color(0xFF9A4C4C)) }
        }
        Text("记录仅保存在本机应用私有目录。", color = Muted, fontSize = 11.sp)
        if (history.isEmpty()) {
            Surface(color = Color.White, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().height(170.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("◷", color = accent, fontSize = 36.sp)
                    Text("还没有处理记录", color = Ink, fontWeight = FontWeight.Medium)
                    Text("处理一张照片后，会显示在这里。", color = Muted, fontSize = 11.sp)
                }
            }
        } else history.forEach { item ->
            HistoryRow(item, accent, { onOpen(item) }, { onDelete(item) })
        }
    }
}

@Composable
private fun HistoryRow(item: HistoryItem, accent: Color, onOpen: () -> Unit, onDelete: (() -> Unit)?) {
    Surface(color = Color.White, shape = RoundedCornerShape(15.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.title, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1)
                Text(
                    (if (item.mode == "ENHANCE") "低照度增强" else "图像复原") + " · " +
                        java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(item.timestamp)),
                    color = Muted, fontSize = 9.sp
                )
            }
            TextButton(onClick = onOpen) { Text("打开", color = accent) }
            if (onDelete != null) TextButton(onClick = onDelete) { Text("删除", color = Color(0xFF9A4C4C)) }
        }
    }
}

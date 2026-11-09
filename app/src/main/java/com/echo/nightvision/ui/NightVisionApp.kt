package com.echo.nightvision.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.lifecycle.LifecycleOwner
import com.echo.nightvision.data.HistoryItem
import com.echo.nightvision.data.ImageAsset
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

enum class AppScreen { HOME, EDITOR, CAMERA, LIBRARY, HISTORY, PROFILE }
enum class ImageViewMode { RESULT, ORIGINAL, COMPARE }
enum class AppThemeChoice { GREEN, BERRY, AMBER }

@Composable
fun NightVisionApp(
    lifecycleOwner: LifecycleOwner,
    screen: AppScreen,
    mode: ProcessingMode,
    source: PixelImage?,
    result: PixelImage?,
    report: ProcessingReport?,
    parameters: ProcessParameters,
    busy: Boolean,
    history: List<HistoryItem>,
    assets: List<ImageAsset>,
    theme: AppThemeChoice,
    viewMode: ImageViewMode,
    split: Float,
    message: String?,
    onTheme: (AppThemeChoice) -> Unit,
    onScreen: (AppScreen) -> Unit,
    onMode: (ProcessingMode) -> Unit,
    onPick: () -> Unit,
    onCamera: () -> Unit,
    onCameraPhoto: (Uri) -> Unit,
    onProcess: () -> Unit,
    onParameters: (ProcessParameters) -> Unit,
    onViewMode: (ImageViewMode) -> Unit,
    onSplit: (Float) -> Unit,
    onSave: () -> Unit,
    onSaveRecord: () -> Unit,
    onOpenHistory: (HistoryItem) -> Unit,
    onOpenAsset: (ImageAsset) -> Unit,
    onDeleteAsset: (ImageAsset) -> Unit,
    onToggleFavorite: (ImageAsset) -> Unit,
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
            if (screen != AppScreen.EDITOR && screen != AppScreen.CAMERA) NavigationBar(containerColor = Color.White) {
                NavigationBarItem(screen == AppScreen.HOME, { onScreen(AppScreen.HOME) }, icon = { Text("⌂") }, label = { Text("首页") })
                NavigationBarItem(screen == AppScreen.LIBRARY, { onScreen(AppScreen.LIBRARY) }, icon = { Text("▧") }, label = { Text("图片库") })
                NavigationBarItem(screen == AppScreen.HISTORY, { onScreen(AppScreen.HISTORY) }, icon = { Text("◷") }, label = { Text("记录") })
                NavigationBarItem(screen == AppScreen.PROFILE, { onScreen(AppScreen.PROFILE) }, icon = { Text("●") }, label = { Text("我的") })
            }
        }
    ) { padding ->
        when (screen) {
            AppScreen.HOME -> HomeScreen(Modifier.padding(padding), history, theme, accent, soft, onTheme, onMode, onScreen)
            AppScreen.CAMERA -> CameraScreen(lifecycleOwner, { onScreen(AppScreen.EDITOR) }, onCameraPhoto)
            AppScreen.EDITOR -> EditorScreen(
                Modifier.padding(padding), mode, source, result, report, parameters, busy,
                accent, viewMode, split, message, onScreen, onPick, onCamera, onProcess,
                onParameters, onViewMode, onSplit, onSave, onSaveRecord
            )
            AppScreen.LIBRARY -> LibraryScreen(Modifier.padding(padding), assets, accent, onOpenAsset, onDeleteAsset, onToggleFavorite)
            AppScreen.HISTORY -> HistoryScreen(Modifier.padding(padding), history, accent, onOpenHistory, onDeleteHistory, onClearHistory)
            AppScreen.PROFILE -> ProfileScreen(Modifier.padding(padding), theme, onTheme, accent, history.size, assets.size)
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
    onSave: () -> Unit,
    onSaveRecord: () -> Unit
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
            ImageComparison(source, result, if (result == null) ImageViewMode.ORIGINAL else viewMode, split, onSplit, onViewMode)
            if (result != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onSaveRecord) { Text("保存记录", color = accent, fontSize = 12.sp) }
                    TextButton(onClick = onSave) { Text("导出图片", color = accent, fontSize = 12.sp) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = params.auto,
                    onClick = { onParams(ProcessParameters()) },
                    label = { Text("自动增强", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = !params.auto,
                    onClick = { onParams(params.copy(auto = false)) },
                    label = { Text("手动精修", fontSize = 11.sp) }
                )
                Spacer(Modifier.weight(1f))
                if (params.auto) TextButton(onClick = onProcess, enabled = !busy) {
                    Text("重新分析", color = accent, fontSize = 11.sp)
                }
            }
            if (params.auto) {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("自动增强方案", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("系统根据平均亮度、暗部比例、亮部占比与噪声参考值匹配处理参数。", color = Muted, fontSize = 11.sp)
                        if (report != null) {
                            Text("平均亮度 " + report.original.mean.toInt() + "/255 · 暗部 " + report.original.darkPercent + "% · 高光 " + report.original.highlightPercent + "%", color = accent, fontSize = 11.sp)
                            Text("本次实际执行 " + report.steps.size + " 个处理步骤 · " + report.elapsedMillis + " ms", color = Muted, fontSize = 10.sp)
                        }
                    }
                }
            } else {
                ProfessionalAdjustmentPanel(params, accent, onParams)
            }
            if (message != null) Text(message, color = Muted, fontSize = 11.sp)
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
private fun ImageComparison(
    source: PixelImage,
    result: PixelImage?,
    mode: ImageViewMode,
    split: Float,
    onSplit: (Float) -> Unit,
    onViewMode: (ImageViewMode) -> Unit
) {
    val originalBitmap = remember(source) {
        Bitmap.createBitmap(source.pixels, source.width, source.height, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
    val resultBitmap = remember(result) {
        result?.let { Bitmap.createBitmap(it.pixels, it.width, it.height, Bitmap.Config.ARGB_8888).asImageBitmap() }
    }
    val latestOnSplit = rememberUpdatedState(onSplit)
    val latestOnViewMode = rememberUpdatedState(onViewMode)
    Surface(color = Color(0xFF172522), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().height(265.dp)) {
        Box(Modifier.fillMaxSize()) {
            when (mode) {
                ImageViewMode.ORIGINAL -> Image(
                    originalBitmap,
                    "原图",
                    modifier = Modifier.fillMaxSize().pointerInput(mode, resultBitmap) {
                        detectTapGestures(onPress = {
                            if (resultBitmap != null && mode == ImageViewMode.RESULT) {
                                latestOnViewMode.value(ImageViewMode.ORIGINAL)
                                tryAwaitRelease()
                                latestOnViewMode.value(ImageViewMode.RESULT)
                            }
                        })
                    }
                )
                ImageViewMode.RESULT -> if (resultBitmap != null) {
                    Image(
                        resultBitmap,
                        "处理结果",
                        modifier = Modifier.fillMaxSize().pointerInput(resultBitmap) {
                            detectTapGestures(onPress = {
                                latestOnViewMode.value(ImageViewMode.ORIGINAL)
                                tryAwaitRelease()
                                latestOnViewMode.value(ImageViewMode.RESULT)
                            })
                        }
                    )
                } else Image(originalBitmap, "原图", modifier = Modifier.fillMaxSize())
                ImageViewMode.COMPARE -> if (resultBitmap != null) {
                    Canvas(Modifier.fillMaxSize().pointerInput(source.width, source.height) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            val canvasWidth = size.width.toFloat()
                            val canvasHeight = size.height.toFloat()
                            if (canvasWidth > 0f && canvasHeight > 0f) {
                                val imageAspect = source.width.toFloat() / source.height
                                val canvasAspect = canvasWidth / canvasHeight
                                val displayedWidth = if (imageAspect > canvasAspect) canvasWidth else canvasHeight * imageAspect
                                val imageLeft = (canvasWidth - displayedWidth) / 2f
                                if (displayedWidth > 0f) {
                                    latestOnSplit.value(
                                        ((change.position.x - imageLeft) / displayedWidth).coerceIn(0.05f, 0.95f)
                                    )
                                }
                            }
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
                        drawCircle(Color.White, 12.dp.toPx(), Offset(divider, top + h / 2f))
                    }
                } else Image(originalBitmap, "原图", modifier = Modifier.fillMaxSize())
            }
            if (resultBitmap != null) {
                Column(
                    Modifier.align(Alignment.BottomEnd).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        onClick = { onViewMode(if (mode == ImageViewMode.COMPARE) ImageViewMode.RESULT else ImageViewMode.COMPARE) },
                        color = Color.Black.copy(alpha = 0.62f),
                        shape = CircleShape,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("⇄", color = Color.White, fontSize = 20.sp)
                        }
                    }
                    Surface(
                        onClick = { onViewMode(if (mode == ImageViewMode.ORIGINAL) ImageViewMode.RESULT else ImageViewMode.ORIGINAL) },
                        color = Color.Black.copy(alpha = 0.62f),
                        shape = CircleShape,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(if (mode == ImageViewMode.ORIGINAL) "效" else "原", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
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
private fun LibraryScreen(
    modifier: Modifier,
    assets: List<ImageAsset>,
    accent: Color,
    onOpen: (ImageAsset) -> Unit,
    onDelete: (ImageAsset) -> Unit,
    onToggleFavorite: (ImageAsset) -> Unit
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("图片库", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text(assets.size.toString() + " 张本地图片 · 收藏优先", color = Muted, fontSize = 11.sp)
            }
        }
        if (assets.isEmpty()) {
            Surface(color = Color.White, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().height(180.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("▧", color = accent, fontSize = 38.sp)
                    Text("图片库还是空的", color = Ink, fontWeight = FontWeight.Medium)
                    Text("导入或拍摄图片后，会自动保存到本地图片库。", color = Muted, fontSize = 11.sp)
                }
            }
        } else {
            assets.forEach { asset ->
                val thumbnail = remember(asset.thumbnailPath) {
                    BitmapFactory.decodeFile(asset.thumbnailPath)?.asImageBitmap()
                }
                Surface(color = Color.White, shape = RoundedCornerShape(15.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (thumbnail != null) {
                            Image(thumbnail, contentDescription = asset.title, modifier = Modifier.size(76.dp).clip(RoundedCornerShape(10.dp)))
                        } else {
                            Box(Modifier.size(76.dp).background(Color(0xFFEAF0EC), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                                Text("▧", color = accent, fontSize = 24.sp)
                            }
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(asset.title, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1)
                            Text(asset.sourceType + " · " + asset.width + "×" + asset.height, color = Muted, fontSize = 9.sp)
                            Text(java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(asset.timestamp)), color = Muted, fontSize = 9.sp)
                            TextButton(onClick = { onOpen(asset) }) { Text("打开编辑", color = accent, fontSize = 11.sp) }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            TextButton(onClick = { onToggleFavorite(asset) }) {
                                Text(if (asset.favorite) "★" else "☆", color = if (asset.favorite) Color(0xFFB27B35) else Muted, fontSize = 18.sp)
                            }
                            TextButton(onClick = { onDelete(asset) }) { Text("删除", color = Color(0xFF9A4C4C), fontSize = 10.sp) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileScreen(
    modifier: Modifier,
    theme: AppThemeChoice,
    onTheme: (AppThemeChoice) -> Unit,
    accent: Color,
    recordCount: Int,
    assetCount: Int
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("我的", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Surface(color = Color.White, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = accent.copy(alpha = 0.12f), shape = CircleShape, modifier = Modifier.size(52.dp)) {
                    Box(contentAlignment = Alignment.Center) { Text("☾", color = accent, fontSize = 28.sp) }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("夜视大师工作空间", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text("本地使用 · 图片在设备端处理", color = Muted, fontSize = 11.sp)
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("外观偏好", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("主题设置集中放在这里，不再占用首页空间。", color = Muted, fontSize = 10.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    ThemeChip("苔绿", theme == AppThemeChoice.GREEN, Green) { onTheme(AppThemeChoice.GREEN) }
                    ThemeChip("莓酒红", theme == AppThemeChoice.BERRY, Rose) { onTheme(AppThemeChoice.BERRY) }
                    ThemeChip("暖棕金", theme == AppThemeChoice.AMBER, Amber) { onTheme(AppThemeChoice.AMBER) }
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("工作空间", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("已保存处理记录： " + recordCount, color = Muted, fontSize = 11.sp)
                Text("图片库资产： " + assetCount, color = Muted, fontSize = 11.sp)
                Text("默认处理：自动增强，可在编辑器中切换手动精修。", color = Muted, fontSize = 11.sp)
                Text("原始图片不会被处理结果覆盖。", color = Muted, fontSize = 11.sp)
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("关于夜视大师", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("NightVision-DIP · 数字图像处理课程项目", color = Muted, fontSize = 11.sp)
                Text("以经典图像处理算法完成低照度增强与图像复原。", color = Muted, fontSize = 11.sp)
            }
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

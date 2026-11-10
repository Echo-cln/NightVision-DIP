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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
import com.echo.nightvision.processing.QualityComparison
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
    libraryStorageBytes: Long,
    autoProcessOnImport: Boolean,
    exportQuality: Int,
    theme: AppThemeChoice,
    viewMode: ImageViewMode,
    split: Float,
    message: String?,
    qualityReferenceName: String?,
    qualityComparison: QualityComparison?,
    qualityError: String?,
    qualityBusy: Boolean,
    onPickReference: () -> Unit,
    onClearReference: () -> Unit,
    onTheme: (AppThemeChoice) -> Unit,
    onAutoProcessOnImportChange: (Boolean) -> Unit,
    onExportQualityChange: (Int) -> Unit,
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
                accent, viewMode, split, message, qualityReferenceName, qualityComparison,
                qualityError, qualityBusy, onPickReference, onClearReference,
                onScreen, onPick, onCamera, onProcess, onParameters, onViewMode, onSplit, onSave, onSaveRecord
            )
            AppScreen.LIBRARY -> LibraryScreen(Modifier.padding(padding), assets, accent, onOpenAsset, onDeleteAsset, onToggleFavorite)
            AppScreen.HISTORY -> HistoryScreen(Modifier.padding(padding), history, accent, onOpenHistory, onDeleteHistory, onClearHistory)
            AppScreen.PROFILE -> ProfileScreen(Modifier.padding(padding), theme, onTheme, accent, history.size, assets.size, libraryStorageBytes, autoProcessOnImport, exportQuality, onAutoProcessOnImportChange, onExportQualityChange)
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
    qualityReferenceName: String?,
    qualityComparison: QualityComparison?,
    qualityError: String?,
    qualityBusy: Boolean,
    onPickReference: () -> Unit,
    onClearReference: () -> Unit,
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
    var detailsExpanded by remember { mutableStateOf(false) }
    Column(
        modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onScreen(AppScreen.HOME) }) { Text("‹ 返回", color = accent, fontSize = 12.sp) }
            Text(
                if (mode == ProcessingMode.ENHANCE) "低照度增强" else "图像复原",
                modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold
            )
            if (source != null) {
                TextButton(onClick = onPick) { Text("导入", color = accent, fontSize = 11.sp) }
                TextButton(onClick = onCamera) { Text("拍摄", color = accent, fontSize = 11.sp) }
            } else Text("本地处理", color = Muted, fontSize = 10.sp)
        }

        if (source == null) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    if (mode == ProcessingMode.ENHANCE) "让暗部细节显现" else "从一张待校正的图片开始",
                    color = Ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold
                )
                Text("本机处理，原始素材独立保存。支持系统相册与现场拍摄。", color = Muted, fontSize = 12.sp)
                Surface(
                    color = Color(0xFFEFF4F1), shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth().height(220.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("▧", color = accent, fontSize = 42.sp)
                        Text("选择一张图片", color = Ink, fontWeight = FontWeight.SemiBold)
                        Text("可使用真实夜景或低照度监控截图", color = Muted, fontSize = 11.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Button(
                        onClick = onPick, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = accent)
                    ) { Text("导入照片", fontSize = 12.sp) }
                    OutlinedButton(onClick = onCamera, modifier = Modifier.weight(1f)) {
                        Text("现场拍摄", color = accent, fontSize = 12.sp)
                    }
                }
                if (message != null) Text(message, color = Color(0xFF9A4C4C), fontSize = 11.sp)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("画面预览", color = Ink, fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                    if (busy) "正在更新…" else source.width.toString() + " × " + source.height,
                    color = if (busy) accent else Muted, fontSize = 10.sp
                )
            }

            ImageComparison(
                source, result, if (result == null) ImageViewMode.ORIGINAL else viewMode,
                split, onSplit, onViewMode
            )

            // Photo preview remains visible; only the controls and diagnostics scroll.
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    FilterChip(
                        selected = params.auto,
                        onClick = { onParams(ProcessParameters()) },
                        label = { Text(if (mode == ProcessingMode.ENHANCE) "自动增强" else "自动复原", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = !params.auto,
                        onClick = { onParams((report?.effectiveParameters ?: params).copy(auto = false)) },
                        label = { Text("手动精修", fontSize = 11.sp) }
                    )
                    Spacer(Modifier.weight(1f))
                    if (params.auto) TextButton(onClick = onProcess, enabled = !busy) {
                        Text("重新分析", color = accent, fontSize = 11.sp)
                    }
                }

                if (params.auto) {
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text("系统推荐参数", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("根据亮度、暗部、高光及平坦区域噪声参考值自动匹配处理策略。",
                                color = Muted, fontSize = 11.sp)
                            if (report != null) {
                                val effective = report.effectiveParameters
                                Text(
                                    "平均亮度 " + report.original.mean.toInt() + "/255  ·  暗部 " +
                                        report.original.darkPercent + "%  ·  高光 " + report.original.highlightPercent + "%",
                                    color = accent, fontSize = 11.sp
                                )
                                Text(
                                    "曝光 " + String.format(java.util.Locale.getDefault(), "%.2f", effective.exposure) +
                                        " EV  ·  对比度 " + effective.contrast.toInt() +
                                        "  ·  降噪 " + effective.denoise.toInt() + "%",
                                    color = Ink, fontSize = 11.sp
                                )
                            } else {
                                Button(onClick = onProcess, enabled = !busy,
                                    colors = ButtonDefaults.buttonColors(containerColor = accent)) {
                                    Text(if (busy) "正在处理…" else "开始自动处理")
                                }
                            }
                        }
                    }
                } else {
                    ProfessionalAdjustmentPanel(params, mode, accent, onParams)
                    if (result == null) Button(onClick = onProcess, enabled = !busy,
                        colors = ButtonDefaults.buttonColors(containerColor = accent),
                        modifier = Modifier.fillMaxWidth()) {
                        Text(if (busy) "正在处理…" else "应用当前参数")
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("图像分析与处理详情", color = Ink, fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { detailsExpanded = !detailsExpanded }) {
                        Text(if (detailsExpanded) "收起 ˄" else "查看 ˅", color = accent, fontSize = 11.sp)
                    }
                }
                if (detailsExpanded) {
                    if (report != null) StatsCard(report, accent)
                    QualityEvaluationCard(
                        referenceName = qualityReferenceName,
                        comparison = qualityComparison,
                        error = qualityError,
                        busy = qualityBusy,
                        accent = accent,
                        onPickReference = onPickReference,
                        onClearReference = onClearReference
                    )
                    if (report != null) {
                        Card(colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(17.dp)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                Text("实际执行步骤", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                report.steps.forEachIndexed { index, step ->
                                    Row(verticalAlignment = Alignment.Top) {
                                        Text((index + 1).toString(), color = accent,
                                            fontWeight = FontWeight.SemiBold, modifier = Modifier.width(22.dp))
                                        Column {
                                            Text(step.name, color = Ink, fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium)
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
                if (message != null) Text(message, color = Muted, fontSize = 11.sp)
                Spacer(Modifier.height(4.dp))
            }

            // Export and record actions are fixed at the bottom.
            if (result != null) Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onSaveRecord, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text("保存记录", color = accent, fontSize = 12.sp)
                }
                Button(
                    onClick = onSave, enabled = !busy, modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = accent)
                ) { Text("导出图片", fontSize = 12.sp) }
            }
        }
    }
}

@Composable
private fun QualityEvaluationCard(
    referenceName: String?,
    comparison: QualityComparison?,
    error: String?,
    busy: Boolean,
    accent: Color,
    onPickReference: () -> Unit,
    onClearReference: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(17.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("参考图质量评价", color = Ink, fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (busy) Text("计算中…", color = accent, fontSize = 10.sp)
            }
            Text(
                "选择同一场景、同尺寸且像素对齐的清晰参考图。系统将比较原图与处理结果相对参考图的 PSNR 和 SSIM。",
                color = Muted, fontSize = 10.sp, lineHeight = 15.sp
            )
            if (referenceName != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("参考图：$referenceName", color = Ink, fontSize = 10.sp,
                        modifier = Modifier.weight(1f), maxLines = 1)
                    TextButton(onClick = onClearReference) {
                        Text("移除", color = Color(0xFF9A4C4C), fontSize = 10.sp)
                    }
                }
            }
            if (comparison != null) {
                QualityMetricRow(
                    "PSNR · RGB",
                    formatPsnr(comparison.before.psnrDb),
                    comparison.after?.let { formatPsnr(it.psnrDb) } ?: "尚无处理结果"
                )
                QualityMetricRow(
                    "SSIM · Y 8×8",
                    String.format(java.util.Locale.getDefault(), "%.4f", comparison.before.ssim8x8),
                    comparison.after?.let {
                        String.format(java.util.Locale.getDefault(), "%.4f", it.ssim8x8)
                    } ?: "尚无处理结果"
                )
                QualityMetricRow(
                    "MSE · RGB",
                    String.format(java.util.Locale.getDefault(), "%.2f", comparison.before.mseRgb),
                    comparison.after?.let {
                        String.format(java.util.Locale.getDefault(), "%.2f", it.mseRgb)
                    } ?: "尚无处理结果"
                )
                Text("PSNR 越高越好；SSIM 越接近 1 越好；MSE 越低越好。", color = Muted, fontSize = 10.sp)
            }
            if (error != null) {
                Text(error, color = Color(0xFF9A4C4C), fontSize = 10.sp, lineHeight = 15.sp)
            }
            OutlinedButton(onClick = onPickReference, modifier = Modifier.fillMaxWidth()) {
                Text(if (referenceName == null) "选择清晰参考图" else "更换参考图", color = accent, fontSize = 11.sp)
            }
            Text(
                "SSIM 使用 8×8 非重叠亮度窗口；尺寸不一致或未配准的图片不会自动缩放计算。无干净真值时，不应将原图与增强图之间的 PSNR/SSIM 当作画质提升证据。",
                color = Muted, fontSize = 9.sp, lineHeight = 14.sp
            )
        }
    }
}

private fun formatPsnr(value: Double): String =
    if (value.isInfinite()) "∞ dB"
    else String.format(java.util.Locale.getDefault(), "%.2f dB", value)

@Composable
private fun QualityMetricRow(label: String, before: String, after: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Muted, fontSize = 10.sp, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text("原图  $before", color = Muted, fontSize = 10.sp)
            Text("处理后  $after", color = Ink, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
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
                ImageViewMode.ORIGINAL -> Image(originalBitmap, "原图", modifier = Modifier.fillMaxSize())
                ImageViewMode.RESULT -> if (resultBitmap != null) {
                    Image(
                        resultBitmap,
                        "处理结果",
                        modifier = Modifier.fillMaxSize().pointerInput(resultBitmap) {
                            detectTapGestures(
                                onLongPress = { latestOnViewMode.value(ImageViewMode.ORIGINAL) },
                                onPress = {
                                    tryAwaitRelease()
                                    latestOnViewMode.value(ImageViewMode.RESULT)
                                }
                            )
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
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Stat("高光比例", report.original.highlightPercent.toString() + "%", accent, Modifier.weight(1f))
                Stat("噪声参考", report.original.residualReference?.let { String.format(java.util.Locale.getDefault(), "%.2f", it) } ?: "不可估计", accent, Modifier.weight(1f))
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
    var search by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("全部") }
    var pendingDelete by remember { mutableStateOf<ImageAsset?>(null) }
    val visibleAssets = assets.filter { asset ->
        val matchesFilter = when (selectedFilter) {
            "原图" -> asset.sourceType != "处理结果"
            "处理结果" -> asset.sourceType == "处理结果"
            "收藏" -> asset.favorite
            else -> true
        }
        matchesFilter && asset.title.contains(search.trim(), ignoreCase = true)
    }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("图片库", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text(assets.size.toString() + " 张本地图片 · 原始文件独立保存", color = Muted, fontSize = 11.sp)
            }
        }
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("搜索图片名称", fontSize = 12.sp) }
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("全部", "原图", "处理结果", "收藏").forEach { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { selectedFilter = filter },
                    label = { Text(filter, fontSize = 10.sp) }
                )
            }
        }
        if (visibleAssets.isEmpty()) {
            Surface(color = Color.White, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().height(180.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("▧", color = accent, fontSize = 38.sp)
                    Text(if (assets.isEmpty()) "图片库还是空的" else "没有找到匹配的图片", color = Ink, fontWeight = FontWeight.Medium)
                    Text(
                        if (assets.isEmpty()) "导入或拍摄图片后，会自动保存到本地图片库。" else "尝试更换筛选条件或搜索关键词。",
                        color = Muted, fontSize = 11.sp
                    )
                }
            }
        } else {
            visibleAssets.forEach { asset ->
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
                            TextButton(onClick = { pendingDelete = asset }) { Text("删除", color = Color(0xFF9A4C4C), fontSize = 10.sp) }
                        }
                    }
                }
            }
        }
    }
    pendingDelete?.let { asset ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除图片？") },
            text = { Text("将从夜视大师图片库删除“" + asset.title + "”的本地副本。系统相册中的原文件不会被删除。") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(asset)
                    pendingDelete = null
                }) { Text("删除", color = Color(0xFF9A4C4C)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun ProfileScreen(
    modifier: Modifier,
    theme: AppThemeChoice,
    onTheme: (AppThemeChoice) -> Unit,
    accent: Color,
    recordCount: Int,
    assetCount: Int,
    libraryStorageBytes: Long,
    autoProcessOnImport: Boolean,
    exportQuality: Int,
    onAutoProcessOnImportChange: (Boolean) -> Unit,
    onExportQualityChange: (Int) -> Unit
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
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("处理偏好", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("导入后自动增强", color = Ink, fontSize = 12.sp)
                        Text("关闭后先进入原图与手动精修工作区。", color = Muted, fontSize = 10.sp)
                    }
                    Switch(checked = autoProcessOnImport, onCheckedChange = onAutoProcessOnImportChange)
                }
                HorizontalDivider(color = Line)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("默认 JPEG 导出质量", color = Ink, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text(exportQuality.toString(), color = accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Slider(
                    value = exportQuality.toFloat().coerceIn(85f, 100f),
                    onValueChange = { onExportQualityChange(it.toInt()) },
                    valueRange = 85f..100f
                )
                Text("质量越高，文件体积通常越大。原始图片与应用内处理结果不会因导出设置而改变。", color = Muted, fontSize = 10.sp)
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("工作空间", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("已保存处理记录： " + recordCount, color = Muted, fontSize = 11.sp)
                Text("图片库资产： " + assetCount, color = Muted, fontSize = 11.sp)
                Text("图片库空间： " + String.format(java.util.Locale.getDefault(), "%.1f MB", libraryStorageBytes / 1048576.0), color = Muted, fontSize = 11.sp)
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

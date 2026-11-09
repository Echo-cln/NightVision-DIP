package com.echo.nightvision

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.echo.nightvision.data.HistoryItem
import com.echo.nightvision.data.HistoryStore
import com.echo.nightvision.data.ImageAsset
import com.echo.nightvision.data.ImageLibraryStore
import com.echo.nightvision.data.encodeProcessParameters
import com.echo.nightvision.data.decodeProcessParameters
import com.echo.nightvision.processing.ImageProcessor
import com.echo.nightvision.processing.PixelImage
import com.echo.nightvision.processing.ProcessParameters
import com.echo.nightvision.processing.ProcessingMode
import com.echo.nightvision.ui.AppScreen
import com.echo.nightvision.ui.AppThemeChoice
import com.echo.nightvision.ui.ImageViewMode
import com.echo.nightvision.ui.NightVisionApp
import com.echo.nightvision.ui.theme.NightVisionTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NightVisionTheme {
                var screen by remember { mutableStateOf(AppScreen.HOME) }
                var mode by remember { mutableStateOf(ProcessingMode.ENHANCE) }
                var source by remember { mutableStateOf<PixelImage?>(null) }
                var result by remember { mutableStateOf<PixelImage?>(null) }
                var report by remember { mutableStateOf<com.echo.nightvision.processing.ProcessingReport?>(null) }
                var params by remember { mutableStateOf(ProcessParameters()) }
                var busy by remember { mutableStateOf(false) }
                var history by remember { mutableStateOf(HistoryStore.list(this@MainActivity)) }
                var assets by remember { mutableStateOf(ImageLibraryStore.list(this@MainActivity)) }
                var theme by remember {
                    mutableStateOf(
                        runCatching {
                            AppThemeChoice.valueOf(
                                getSharedPreferences("nightvision_settings", MODE_PRIVATE)
                                    .getString("theme", AppThemeChoice.GREEN.name) ?: AppThemeChoice.GREEN.name
                            )
                        }.getOrDefault(AppThemeChoice.GREEN)
                    )
                }
                var viewMode by remember { mutableStateOf(ImageViewMode.RESULT) }
                var split by remember { mutableStateOf(0.5f) }
                var message by remember { mutableStateOf<String?>(null) }
                var previewJob by remember { mutableStateOf<Job?>(null) }
                var previewVersion by remember { mutableStateOf(0) }
                val scope = rememberCoroutineScope()

                fun loadImage(uri: Uri, sourceType: String = "导入图片") {
                    scope.launch {
                        busy = true
                        message = null
                        try {
                            previewJob?.cancel()
                            previewVersion += 1
                            val image = withContext(Dispatchers.IO) { decodeImage(uri) }
                            val asset = withContext(Dispatchers.IO) {
                                ImageLibraryStore.save(this@MainActivity, "夜景照片", sourceType, image)
                            }
                            assets = listOf(asset) + assets.filterNot { it.id == asset.id }
                            source = image
                            params = ProcessParameters()
                            val processed = withContext(Dispatchers.Default) {
                                ImageProcessor.process(image, mode, ProcessParameters())
                            }
                            result = processed.image
                            report = processed.report
                            viewMode = ImageViewMode.RESULT
                            split = 0.5f
                            screen = AppScreen.EDITOR
                        } catch (error: Exception) {
                            message = error.message ?: "图片读取失败，请换一张图片重试。"
                        } finally {
                            busy = false
                        }
                    }
                }

                val photoPicker = rememberLauncherForActivityResult(
                    ActivityResultContracts.PickVisualMedia()
                ) { uri -> if (uri != null) loadImage(uri) }

                LaunchedEffect(Unit) {
                    history = withContext(Dispatchers.IO) { HistoryStore.list(this@MainActivity) }
                    assets = withContext(Dispatchers.IO) { ImageLibraryStore.list(this@MainActivity) }
                }

                NightVisionApp(
                    lifecycleOwner = this@MainActivity,
                    screen = screen,
                    mode = mode,
                    source = source,
                    result = result,
                    report = report,
                    parameters = params,
                    busy = busy,
                    history = history,
                    assets = assets,
                    theme = theme,
                    viewMode = viewMode,
                    split = split,
                    message = message,
                    onTheme = {
                        theme = it
                        getSharedPreferences("nightvision_settings", MODE_PRIVATE)
                            .edit().putString("theme", it.name).apply()
                    },
                    onScreen = { screen = it },
                    onMode = {
                        mode = it
                        params = ProcessParameters()
                        val input = source
                        if (input != null) {
                            previewJob?.cancel()
                            previewVersion += 1
                            val version = previewVersion
                            scope.launch {
                                val processed = withContext(Dispatchers.Default) {
                                    ImageProcessor.process(input, it, ProcessParameters())
                                }
                                if (version == previewVersion) {
                                    result = processed.image
                                    report = processed.report
                                    viewMode = ImageViewMode.RESULT
                                }
                            }
                        }
                    },
                    onPick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onCamera = { screen = AppScreen.CAMERA },
                    onCameraPhoto = { uri -> loadImage(uri, "现场拍摄") },
                    onProcess = {
                        val input = source
                        if (input != null && !busy) {
                            previewJob?.cancel()
                            previewVersion += 1
                            busy = true
                            message = null
                            val requested = params
                            scope.launch {
                                try {
                                    val processed = withContext(Dispatchers.Default) {
                                        ImageProcessor.process(input, mode, requested)
                                    }
                                    result = processed.image
                                    report = processed.report
                                    viewMode = ImageViewMode.RESULT
                                    message = "处理完成。原图保持独立，结果可单独导出或保存记录。"
                                } catch (error: Exception) {
                                    message = error.message ?: "图像处理失败，请重试。"
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    },
                    onParameters = { next ->
                        params = next
                        val input = source
                        if (input != null) {
                            previewVersion += 1
                            val version = previewVersion
                            previewJob?.cancel()
                            previewJob = scope.launch {
                                try {
                                    delay(90)
                                    val previewInput = withContext(Dispatchers.Default) {
                                        resizeForPreview(input, 720)
                                    }
                                    val preview = withContext(Dispatchers.Default) {
                                        ImageProcessor.process(previewInput, mode, next)
                                    }
                                    if (version != previewVersion) return@launch
                                    result = preview.image
                                    report = preview.report
                                    viewMode = ImageViewMode.RESULT
                                    delay(280)
                                    val full = withContext(Dispatchers.Default) {
                                        ImageProcessor.process(input, mode, next)
                                    }
                                    if (version == previewVersion) {
                                        result = full.image
                                        report = full.report
                                    }
                                } catch (_: kotlinx.coroutines.CancellationException) {
                                    // A newer slider value superseded this preview.
                                } catch (error: Exception) {
                                    if (version == previewVersion) message = error.message ?: "实时预览失败。"
                                }
                            }
                        }
                    },
                    onViewMode = { viewMode = it },
                    onSplit = { split = it },
                    onSaveRecord = {
                        val original = source
                        val processed = result
                        if (original != null && processed != null && !busy) {
                            busy = true
                            scope.launch {
                                try {
                                    val saved = withContext(Dispatchers.IO) {
                                        HistoryStore.save(
                                            this@MainActivity,
                                            "夜景照片",
                                            mode.name,
                                            original,
                                            processed,
                                            "本次参数已结构化保存",
                                            encodeProcessParameters(params)
                                        )
                                    }
                                    history = listOf(saved) + history.filterNot { it.id == saved.id }
                                    message = "原图、处理结果与本次参数已保存到处理记录。"
                                } catch (error: Exception) {
                                    message = error.message ?: "保存处理记录失败。"
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    },
                    onSave = {
                        val image = result
                        if (image != null && !busy) {
                            busy = true
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        saveImageToGallery(image)
                                        ImageLibraryStore.save(this@MainActivity, "夜景处理结果", "处理结果", image)
                                    }
                                    assets = withContext(Dispatchers.IO) { ImageLibraryStore.list(this@MainActivity) }
                                    message = "已保存到 Pictures/NightVision，并加入应用图片库。"
                                } catch (error: Exception) {
                                    message = error.message ?: "保存失败，请重试。"
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    },
                    onOpenAsset = { asset: ImageAsset ->
                        scope.launch {
                            busy = true
                            try {
                                val image = withContext(Dispatchers.IO) { ImageLibraryStore.load(asset) }
                                if (image != null) {
                                    source = image
                                    mode = ProcessingMode.ENHANCE
                                    params = ProcessParameters()
                                    val processed = withContext(Dispatchers.Default) {
                                        ImageProcessor.process(image, mode, ProcessParameters())
                                    }
                                    result = processed.image
                                    report = processed.report
                                    viewMode = ImageViewMode.RESULT
                                    screen = AppScreen.EDITOR
                                    message = "已从图片库打开原图，并自动生成增强预览。"
                                } else {
                                    message = "图片文件已不存在或无法读取。"
                                }
                            } catch (error: Exception) {
                                message = error.message ?: "无法打开图片。"
                            } finally {
                                busy = false
                            }
                        }
                    },
                    onDeleteAsset = { asset ->
                        scope.launch {
                            withContext(Dispatchers.IO) { ImageLibraryStore.delete(this@MainActivity, asset) }
                            assets = assets.filterNot { it.id == asset.id }
                        }
                    },
                    onToggleFavorite = { asset ->
                        scope.launch {
                            withContext(Dispatchers.IO) { ImageLibraryStore.setFavorite(this@MainActivity, asset.id, !asset.favorite) }
                            assets = withContext(Dispatchers.IO) { ImageLibraryStore.list(this@MainActivity) }
                        }
                    },
                    onOpenHistory = { item: HistoryItem ->
                        scope.launch {
                            busy = true
                            try {
                                val pair = withContext(Dispatchers.IO) { HistoryStore.load(item) }
                                if (pair != null) {
                                    source = pair.first
                                    result = pair.second
                                    mode = if (item.mode == ProcessingMode.RESTORE.name) ProcessingMode.RESTORE else ProcessingMode.ENHANCE
                                    report = null
                                    params = decodeProcessParameters(item.parametersJson) ?: ProcessParameters(auto = false)
                                    viewMode = ImageViewMode.RESULT
                                    screen = AppScreen.EDITOR
                                } else {
                                    message = "这条记录的图片文件已不存在。"
                                }
                            } catch (error: Exception) {
                                message = error.message ?: "无法打开这条记录。"
                            } finally {
                                busy = false
                            }
                        }
                    },
                    onDeleteHistory = { item ->
                        scope.launch {
                            withContext(Dispatchers.IO) { HistoryStore.delete(this@MainActivity, item.id) }
                            history = history.filterNot { it.id == item.id }
                        }
                    },
                    onClearHistory = {
                        scope.launch {
                            withContext(Dispatchers.IO) { HistoryStore.clear(this@MainActivity) }
                            history = emptyList()
                        }
                    }
                )
            }
        }
    }

    private fun resizeForPreview(source: PixelImage, maxDimension: Int): PixelImage {
        val largest = maxOf(source.width, source.height)
        if (largest <= maxDimension) return source
        val scale = maxDimension.toFloat() / largest
        val width = (source.width * scale).toInt().coerceAtLeast(1)
        val height = (source.height * scale).toInt().coerceAtLeast(1)
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val sourceY = (y * source.height / height).coerceIn(0, source.height - 1)
            for (x in 0 until width) {
                val sourceX = (x * source.width / width).coerceIn(0, source.width - 1)
                pixels[y * width + x] = source.pixels[sourceY * source.width + sourceX]
            }
        }
        return PixelImage(width, height, pixels)
    }

    private fun decodeImage(uri: Uri): PixelImage {
        val imageSource = ImageDecoder.createSource(contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(imageSource) { decoder, info, _ ->
            var sample = 1
            val maxDimension = maxOf(info.size.width, info.size.height)
            while (maxDimension / sample > 1800) sample *= 2
            decoder.setTargetSampleSize(sample)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val image = PixelImage(bitmap.width, bitmap.height, pixels)
        bitmap.recycle()
        return image
    }

    private fun saveImageToGallery(image: PixelImage) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "NightVision_" + System.currentTimeMillis() + ".jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/NightVision")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("系统相册不可用，无法创建图片。")
        try {
            val bitmap = Bitmap.createBitmap(image.pixels, image.width, image.height, Bitmap.Config.ARGB_8888)
            contentResolver.openOutputStream(uri, "w").use { stream ->
                check(stream != null && bitmap.compress(Bitmap.CompressFormat.JPEG, 94, stream)) { "图片写入失败。" }
            }
            bitmap.recycle()
            contentResolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                null,
                null
            )
        } catch (error: Exception) {
            contentResolver.delete(uri, null, null)
            throw error
        }
    }
}

package com.echo.nightvision

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
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
import kotlinx.coroutines.CancellationException
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
                var saving by remember { mutableStateOf(false) }
                var history by remember { mutableStateOf<List<HistoryItem>>(emptyList()) }
                var assets by remember { mutableStateOf<List<ImageAsset>>(emptyList()) }
                var currentAssetPath by remember { mutableStateOf<String?>(null) }
                var libraryStorageBytes by remember { mutableStateOf(0L) }
                var autoProcessOnImport by remember { mutableStateOf(getSharedPreferences("nightvision_settings", MODE_PRIVATE).getBoolean("autoProcessOnImport", true)) }
                var exportQuality by remember { mutableStateOf(getSharedPreferences("nightvision_settings", MODE_PRIVATE).getInt("exportQuality", 94)) }
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
                var viewMode by remember { mutableStateOf(ImageViewMode.COMPARE) }
                var split by remember { mutableStateOf(0.5f) }
                var message by remember { mutableStateOf<String?>(null) }
                var previewJob by remember { mutableStateOf<Job?>(null) }
                var previewVersion by remember { mutableStateOf(0) }
                val scope = rememberCoroutineScope()

                // One cancellable pipeline owns image state. A late import, mode switch or
                // preview is never allowed to replace a newer image or enable stale export.
                fun invalidateImageTask() {
                    previewVersion += 1
                    previewJob?.cancel()
                    busy = false
                }
                fun runImageTask(block: suspend (Int) -> Unit) {
                    invalidateImageTask()
                    val version = previewVersion
                    busy = true
                    message = null
                    previewJob = scope.launch {
                        try { block(version) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (error: Exception) {
                            if (version == previewVersion) message = error.message ?: "图片处理失败，请重试。"
                        } finally {
                            if (version == previewVersion) busy = false
                        }
                    }
                }
                fun loadImage(uri: Uri, sourceType: String = "导入图片") {
                    val selectedMode = mode
                    val automatic = autoProcessOnImport
                    runImageTask { version ->
                        val image = withContext(Dispatchers.IO) { decodeImage(uri) }
                        val asset = withContext(Dispatchers.IO) {
                            ImageLibraryStore.save(this@MainActivity, importedImageName(uri), sourceType, image, uri)
                        }
                        val library = withContext(Dispatchers.IO) { ImageLibraryStore.list(this@MainActivity) }
                        val bytes = withContext(Dispatchers.IO) { ImageLibraryStore.storageBytes(this@MainActivity) }
                        val processed = if (automatic) withContext(Dispatchers.Default) {
                            ImageProcessor.process(image, selectedMode, ProcessParameters())
                        } else null
                        if (version == previewVersion) {
                            assets = library
                            libraryStorageBytes = bytes
                            currentAssetPath = asset.imagePath
                            source = image
                            params = ProcessParameters(auto = automatic)
                            result = processed?.image
                            report = processed?.report
                            viewMode = if (processed == null) ImageViewMode.ORIGINAL else ImageViewMode.COMPARE
                            screen = AppScreen.EDITOR
                        }
                    }
                }

                val photoPicker = rememberLauncherForActivityResult(
                    ActivityResultContracts.PickVisualMedia()
                ) { uri -> if (uri != null) loadImage(uri) }

                LaunchedEffect(Unit) {
                    history = withContext(Dispatchers.IO) { HistoryStore.list(this@MainActivity) }
                    assets = withContext(Dispatchers.IO) { ImageLibraryStore.list(this@MainActivity) }
                    libraryStorageBytes = withContext(Dispatchers.IO) { ImageLibraryStore.storageBytes(this@MainActivity) }
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
                    libraryStorageBytes = libraryStorageBytes,
                    autoProcessOnImport = autoProcessOnImport,
                    exportQuality = exportQuality,
                    theme = theme,
                    viewMode = viewMode,
                    split = split,
                    message = message,
                    onTheme = {
                        theme = it
                        getSharedPreferences("nightvision_settings", MODE_PRIVATE)
                            .edit().putString("theme", it.name).apply()
                    },
                    onAutoProcessOnImportChange = {
                        autoProcessOnImport = it
                        getSharedPreferences("nightvision_settings", MODE_PRIVATE)
                            .edit().putBoolean("autoProcessOnImport", it).apply()
                    },
                    onExportQualityChange = {
                        exportQuality = it.coerceIn(85, 100)
                        getSharedPreferences("nightvision_settings", MODE_PRIVATE)
                            .edit().putInt("exportQuality", exportQuality).apply()
                    },
                    onScreen = {
                        if (!saving) {
                            if (it != screen) invalidateImageTask()
                            screen = it
                        }
                    },
                    onMode = modeChange@ { selectedMode ->
                        if (saving) return@modeChange
                        invalidateImageTask()
                        mode = selectedMode
                        params = ProcessParameters()
                        // Entering either home module starts its own import/camera flow.
                        source = null
                        result = null
                        report = null
                        currentAssetPath = null
                        viewMode = ImageViewMode.COMPARE
                    },
                    onPick = { if (!saving) photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onCamera = { if (!saving) { invalidateImageTask(); screen = AppScreen.CAMERA } },
                    onCameraPhoto = { uri -> loadImage(uri, "现场拍摄") },
                    onProcess = {
                        val input = source
                        if (input != null && !busy) {
                            val requested = params
                            val selectedMode = mode
                            runImageTask { version ->
                                val processed = withContext(Dispatchers.Default) {
                                    ImageProcessor.process(input, selectedMode, requested)
                                }
                                if (version == previewVersion) {
                                    result = processed.image
                                    report = processed.report
                                    viewMode = ImageViewMode.COMPARE
                                }
                            }
                        }
                    },
                    onParameters = adjust@ { next ->
                        if (saving) return@adjust
                        params = next
                        val input = source
                        val selectedMode = mode
                        if (input != null) runImageTask { version ->
                            delay(90)
                            // Resolve automatic recommendations on the full working image,
                            // so preview downsampling cannot choose a different noise strategy.
                            val preview = withContext(Dispatchers.Default) {
                                val resolved = if (next.auto) ImageProcessor.recommend(input, selectedMode) else next
                                ImageProcessor.process(resizeForPreview(input, 720), selectedMode, resolved)
                            }
                            if (version == previewVersion) {
                                result = preview.image
                                // Keep export blocked until the full working image is ready.
                                delay(180)
                                val full = withContext(Dispatchers.Default) {
                                    ImageProcessor.process(input, selectedMode, next)
                                }
                                if (version == previewVersion) {
                                    result = full.image
                                    report = full.report
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
                            saving = true
                            val savedMode = mode.name
                            val savedParameters = encodeProcessParameters(report?.effectiveParameters ?: params.copy(auto = false))
                            val originalPath = currentAssetPath
                            scope.launch {
                                try {
                                    val saved = withContext(Dispatchers.IO) {
                                        HistoryStore.save(
                                            this@MainActivity,
                                            "夜景处理 · " + java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date()),
                                            savedMode,
                                            original,
                                            processed,
                                            "本次参数已结构化保存",
                                            savedParameters,
                                            originalPath
                                        )
                                    }
                                    history = withContext(Dispatchers.IO) { HistoryStore.list(this@MainActivity) }
                                    message = "原图、处理结果与本次参数已保存到处理记录。"
                                } catch (error: Exception) {
                                    message = error.message ?: "保存处理记录失败。"
                                } finally {
                                    saving = false
                                    busy = false
                                }
                            }
                        }
                    },
                    onSave = {
                        val image = result
                        if (image != null && !busy) {
                            busy = true
                            saving = true
                            val quality = exportQuality
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        saveImageToGallery(image, quality)
                                        ImageLibraryStore.save(this@MainActivity, "处理结果 · " + java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date()), "处理结果", image)
                                    }
                                    assets = withContext(Dispatchers.IO) { ImageLibraryStore.list(this@MainActivity) }
                                    libraryStorageBytes = withContext(Dispatchers.IO) { ImageLibraryStore.storageBytes(this@MainActivity) }
                                    message = "已保存到 Pictures/NightVision，并加入应用图片库。"
                                } catch (error: Exception) {
                                    message = error.message ?: "保存失败，请重试。"
                                } finally {
                                    saving = false
                                    busy = false
                                }
                            }
                        }
                    },
                    onOpenAsset = { asset: ImageAsset ->
                        runImageTask { version ->
                            val image = withContext(Dispatchers.IO) { ImageLibraryStore.load(asset) }
                                ?: error("图片文件已不存在或无法读取。")
                            val processed = withContext(Dispatchers.Default) {
                                ImageProcessor.process(image, ProcessingMode.ENHANCE, ProcessParameters())
                            }
                            if (version == previewVersion) {
                                currentAssetPath = asset.imagePath
                                source = image
                                mode = ProcessingMode.ENHANCE
                                params = ProcessParameters()
                                result = processed.image
                                report = processed.report
                                viewMode = ImageViewMode.COMPARE
                                screen = AppScreen.EDITOR
                            }
                        }
                    },
                    onDeleteAsset = { asset ->
                        scope.launch {
                            withContext(Dispatchers.IO) { ImageLibraryStore.delete(this@MainActivity, asset) }
                            assets = assets.filterNot { it.id == asset.id }
                            libraryStorageBytes = withContext(Dispatchers.IO) { ImageLibraryStore.storageBytes(this@MainActivity) }
                        }
                    },
                    onToggleFavorite = { asset ->
                        scope.launch {
                            withContext(Dispatchers.IO) { ImageLibraryStore.setFavorite(this@MainActivity, asset.id, !asset.favorite) }
                            assets = withContext(Dispatchers.IO) { ImageLibraryStore.list(this@MainActivity) }
                        }
                    },
                    onOpenHistory = { item: HistoryItem ->
                        runImageTask { version ->
                            val pair = withContext(Dispatchers.IO) { HistoryStore.load(item) }
                                ?: error("这条记录的图片文件已不存在。")
                            val restoredMode = if (item.mode == ProcessingMode.RESTORE.name) ProcessingMode.RESTORE else ProcessingMode.ENHANCE
                            val restoredParams = decodeProcessParameters(item.parametersJson) ?: ProcessParameters(auto = false)
                            val restoredReport = withContext(Dispatchers.Default) {
                                ImageProcessor.process(pair.first, restoredMode, restoredParams).report.copy(
                                    processed = ImageProcessor.analyze(pair.second))
                            }
                            if (version == previewVersion) {
                                currentAssetPath = item.originalPath
                                source = pair.first
                                result = pair.second
                                mode = restoredMode
                                report = restoredReport
                                params = restoredReport.effectiveParameters
                                viewMode = ImageViewMode.COMPARE
                                screen = AppScreen.EDITOR
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

    private fun importedImageName(uri: Uri): String {
        val name = runCatching {
            contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
        return name?.substringBeforeLast('.')?.trim()?.take(70)
            ?.takeIf { it.isNotBlank() }
            ?: "夜景_" + java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
                .format(java.util.Date())
    }

    private fun resizeForPreview(source: PixelImage, maxDimension: Int): PixelImage {
        val largest = maxOf(source.width, source.height)
        if (largest <= maxDimension) return source
        val scale = maxDimension.toFloat() / largest
        val width = (source.width * scale).toInt().coerceAtLeast(1)
        val height = (source.height * scale).toInt().coerceAtLeast(1)
        val original = Bitmap.createBitmap(source.pixels, source.width, source.height, Bitmap.Config.ARGB_8888)
        val resized = Bitmap.createScaledBitmap(original, width, height, true)
        return try {
            val pixels = IntArray(width * height)
            resized.getPixels(pixels, 0, width, 0, 0, width, height)
            PixelImage(width, height, pixels)
        } finally {
            if (resized !== original) resized.recycle()
            original.recycle()
        }
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

    private fun saveImageToGallery(image: PixelImage, quality: Int) {
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
            try {
                contentResolver.openOutputStream(uri, "w").use { stream ->
                    check(stream != null && bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(85, 100), stream)) { "图片写入失败。" }
                }
            } finally { bitmap.recycle() }
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

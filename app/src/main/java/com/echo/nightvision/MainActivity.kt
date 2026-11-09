package com.echo.nightvision

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
                var theme by remember { mutableStateOf(AppThemeChoice.GREEN) }
                var viewMode by remember { mutableStateOf(ImageViewMode.RESULT) }
                var split by remember { mutableStateOf(0.5f) }
                var message by remember { mutableStateOf<String?>(null) }
                var cameraUri by remember { mutableStateOf<Uri?>(null) }
                val scope = rememberCoroutineScope()

                fun loadImage(uri: Uri) {
                    scope.launch {
                        busy = true
                        message = null
                        try {
                            val image = withContext(Dispatchers.IO) { decodeImage(uri) }
                            source = image
                            result = null
                            report = null
                            params = ProcessParameters()
                            viewMode = ImageViewMode.ORIGINAL
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

                val cameraCapture = rememberLauncherForActivityResult(
                    ActivityResultContracts.TakePicture()
                ) { success ->
                    val uri = cameraUri
                    if (success && uri != null) {
                        contentResolver.update(
                            uri,
                            ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                            null,
                            null
                        )
                        loadImage(uri)
                    } else if (uri != null) {
                        contentResolver.delete(uri, null, null)
                        message = "已取消拍摄。"
                    }
                }

                LaunchedEffect(Unit) {
                    history = withContext(Dispatchers.IO) { HistoryStore.list(this@MainActivity) }
                }

                NightVisionApp(
                    screen = screen,
                    mode = mode,
                    source = source,
                    result = result,
                    report = report,
                    parameters = params,
                    busy = busy,
                    history = history,
                    theme = theme,
                    viewMode = viewMode,
                    split = split,
                    message = message,
                    onTheme = { theme = it },
                    onScreen = { screen = it },
                    onMode = {
                        mode = it
                        params = ProcessParameters()
                        result = null
                        report = null
                    },
                    onPick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onCamera = {
                        val uri = createCameraUri()
                        cameraUri = uri
                        if (uri != null) cameraCapture.launch(uri)
                        else message = "无法创建拍摄文件，请检查存储空间。"
                    },
                    onProcess = {
                        val input = source
                        if (input != null && !busy) {
                            busy = true
                            message = null
                            scope.launch {
                                try {
                                    val processed = withContext(Dispatchers.Default) {
                                        ImageProcessor.process(input, mode, params)
                                    }
                                    result = processed.image
                                    report = processed.report
                                    params = processed.report.effectiveParameters
                                    viewMode = ImageViewMode.RESULT
                                    val saved = withContext(Dispatchers.IO) {
                                        HistoryStore.save(
                                            this@MainActivity,
                                            "夜景照片",
                                            mode.name,
                                            input,
                                            processed.image,
                                            "Gamma=" + processed.report.gamma + "; gain=" + processed.report.gain
                                        )
                                    }
                                    history = listOf(saved) + history.filterNot { it.id == saved.id }
                                    message = "处理完成，原图未被覆盖。"
                                } catch (error: Exception) {
                                    message = error.message ?: "图像处理失败，请重试。"
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    },
                    onParameters = { params = it },
                    onViewMode = { viewMode = it },
                    onSplit = { split = it },
                    onSave = {
                        val image = result
                        if (image != null && !busy) {
                            busy = true
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) { saveImageToGallery(image) }
                                    message = "已保存到 Pictures/NightVision。"
                                } catch (error: Exception) {
                                    message = error.message ?: "保存失败，请重试。"
                                } finally {
                                    busy = false
                                }
                            }
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
                                    params = ProcessParameters(auto = false)
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

    private fun decodeImage(uri: Uri): PixelImage {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri).use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) error("无法识别该图片格式。")
        var sample = 1
        while (maxOf(bounds.outWidth / sample, bounds.outHeight / sample) > 1800) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = contentResolver.openInputStream(uri).use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        } ?: error("图片解码失败。")
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val image = PixelImage(bitmap.width, bitmap.height, pixels)
        bitmap.recycle()
        return image
    }

    private fun createCameraUri(): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "NightVision_Capture_" + System.currentTimeMillis() + ".jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/NightVision/Captures")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        return contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
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

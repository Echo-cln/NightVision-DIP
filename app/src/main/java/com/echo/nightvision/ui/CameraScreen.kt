package com.echo.nightvision.ui

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.camera.view.CameraController
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File

@Composable
fun CameraScreen(
    lifecycleOwner: LifecycleOwner,
    onBack: () -> Unit,
    onPhotoCaptured: (Uri) -> Unit
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var flashOn by remember { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (!granted) message = "需要相机权限才能进行现场拍摄。"
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val controller = remember(context) {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
        }
    }

    LaunchedEffect(flashOn, hasPermission) {
        if (hasPermission) {
            controller.enableTorch(flashOn)
        }
    }

    DisposableEffect(controller, lifecycleOwner, hasPermission) {
        if (hasPermission) controller.bindToLifecycle(lifecycleOwner)
        onDispose { controller.unbind() }
    }

    fun capture() {
        if (capturing || !hasPermission) return
        capturing = true
        message = null
        val file = File(context.cacheDir, "nightvision-capture-" + System.currentTimeMillis() + ".jpg")
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        controller.takePicture(
            options,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    var destination: Uri? = null
                    try {
                        val values = ContentValues().apply {
                            put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
                            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/NightVision/Captures")
                            put(MediaStore.Images.Media.IS_PENDING, 1)
                        }
                        val savedUri = context.contentResolver.insert(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            values
                        ) ?: error("系统相册不可用，无法创建拍摄文件。")
                        destination = savedUri
                        val output = context.contentResolver.openOutputStream(savedUri, "w")
                            ?: error("无法打开照片保存流。")
                        output.use { stream ->
                            file.inputStream().use { input -> input.copyTo(stream) }
                        }
                        context.contentResolver.update(
                            savedUri,
                            ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                            null,
                            null
                        )
                        file.delete()
                        capturing = false
                        onPhotoCaptured(savedUri)
                    } catch (error: Exception) {
                        destination?.let { context.contentResolver.delete(it, null, null) }
                        file.delete()
                        capturing = false
                        message = error.message ?: "照片保存失败，请重试。"
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    file.delete()
                    capturing = false
                    message = "拍摄失败：" + (exception.message ?: "相机暂时不可用")
                }
            }
        )
    }

    if (!hasPermission) {
        Box(Modifier.fillMaxSize().background(Color(0xFF101614)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("需要使用相机权限", color = Color.White, fontSize = 18.sp)
                Text("夜视大师只在你主动拍摄时使用摄像头。", color = Color(0xFFB8C6C0), fontSize = 12.sp)
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("授予相机权限")
                }
                Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3A4A44))) {
                    Text("返回")
                }
            }
        }
    } else {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { viewContext ->
                    PreviewView(viewContext).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        this.controller = controller
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(onClick = onBack, color = Color.Black.copy(alpha = 0.48f), shape = CircleShape) {
                        Text("‹", color = Color.White, fontSize = 28.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("现场拍摄", color = Color.White, fontSize = 17.sp)
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = { flashOn = !flashOn },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.48f))
                    ) {
                        Text(if (flashOn) "补光灯开" else "补光灯关", color = Color.White, fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.weight(1f))
                if (message != null) {
                    Surface(color = Color.Black.copy(alpha = 0.65f), shape = CircleShape) {
                        Text(message ?: "", color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = { if (!capturing) capture() },
                        enabled = !capturing,
                        color = Color.White,
                        shape = CircleShape,
                        modifier = Modifier.size(76.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Surface(
                                color = if (capturing) Color(0xFF9AA49F) else Color.White,
                                shape = CircleShape,
                                modifier = Modifier.size(62.dp)
                            ) {}
                        }
                    }
                }
                Text(
                    if (capturing) "正在保存照片…" else "拍摄后自动进入低照度增强",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

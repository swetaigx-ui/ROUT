package com.rout.aiassistant.features.gesture

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.rout.aiassistant.service.RoutAccessibilityService

/**
 * Hand Magic: camera hand-gesture phone control. Opt-in screen (default OFF),
 * camera frames stay on-device. Mapping:
 *  Khuli muthi = scroll up | Muthi = scroll down | Victory = back | Teen = home | Thumbs up = ROUT bolna (LIVE)
 */
@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
fun HandMagicScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var gesture by remember { mutableStateOf("Haath dikhao…") }
    var active by remember { mutableStateOf(true) }
    var lastFire by remember { mutableStateOf(0L) }
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) launcher.launch(android.Manifest.permission.CAMERA)
    }
    val engine = remember { GestureEngine(context) }
    DisposableEffect(Unit) { onDispose { engine.close() } }

    Column(Modifier.fillMaxSize().background(Color(0xFF0A0A12)).statusBarsPadding().padding(16.dp)) {
        Text("Hand Magic", color = Color(0xFFEEECF8), fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(18.dp))) {
            AndroidView(factory = { ctx ->
                val pv = PreviewView(ctx)
                val future = ProcessCameraProvider.getInstance(ctx)
                future.addListener({
                    try {
                        val provider = future.get()
                        val preview = Preview.Builder().build().also { it.setSurfaceProvider(pv.surfaceProvider) }
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888).build()
                        analysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { proxy ->
                            try {
                                if (active) {
                                    val bmp = android.graphics.Bitmap.createBitmap(proxy.width, proxy.height, android.graphics.Bitmap.Config.ARGB_8888)
                                    bmp.copyPixelsFromBuffer(proxy.planes[0].buffer)
                                    val mpImage = BitmapImageBuilder(bmp).build()
                                    val g = engine.detect(mpImage)
                                    gesture = g.label
                                    val now = System.currentTimeMillis()
                                    if (g != GestureEngine.Gesture.NONE && now - lastFire > 1500) {
                                        lastFire = now
                                        runGesture(context, g)
                                    }
                                }
                            } catch (_: Exception) {} finally { proxy.close() }
                        }
                        provider.unbindAll()
                        provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis)
                    } catch (_: Exception) {}
                }, ContextCompat.getMainExecutor(ctx))
                pv
            }, modifier = Modifier.fillMaxSize())
            Column(Modifier.align(Alignment.BottomCenter).padding(14.dp)) {
                Box(Modifier.clip(RoundedCornerShape(50)).background(Color(0xCC161328)).padding(horizontal = 18.dp, vertical = 8.dp)) {
                    Text(gesture, color = Color(0xFF67F0FF), fontSize = 16.sp)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { active = !active }, modifier = Modifier.weight(1f)) { Text(if (active) "Pause" else "Resume") }
        }
        Text("Gestures on-device pehchaane jaate hain (MediaPipe) - koi frame kahin nahi jaata. Kaam karne ke liye ROUT Accessibility ON honi chahiye.",
            color = Color(0xFF7F7B99), fontSize = 12.sp)
    }
}

private fun runGesture(context: Context, g: GestureEngine.Gesture) {
    val svc = RoutAccessibilityService.instance ?: return
    when (g) {
        GestureEngine.Gesture.OPEN_PALM -> svc.scroll(forward = false)
        GestureEngine.Gesture.FIST -> svc.scroll(forward = true)
        GestureEngine.Gesture.VICTORY -> svc.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        GestureEngine.Gesture.THREE -> svc.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
        GestureEngine.Gesture.THUMB_UP -> Unit // reserved: start LIVE
        GestureEngine.Gesture.NONE -> Unit
    }
}

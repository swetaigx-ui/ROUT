package com.rout.aiassistant.features.tools

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val ToolBg = Color(0xFF0A0A12)
private val ToolCard = Color(0xC2161328)
private val ToolInk = Color(0xFFEEECF8)
private val ToolAccent = Color(0xFF67F0FF)

@Composable
private fun ToolScaffold(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(ToolBg).statusBarsPadding().padding(16.dp)) {
        Text(title, color = ToolInk, fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            content()
        }
    }
}

@Composable
fun CodeStudioScreen() {
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("// JavaScript yahan likho\nprint('Namaste Gyan!')\nfor (let i=1;i<=3;i++) print('line', i)\n") }
    var output by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    ToolScaffold("Code Studio") {
        OutlinedTextField(
            value = code, onValueChange = { code = it },
            modifier = Modifier.fillMaxWidth().height(220.dp),
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = ToolInk),
            shape = RoundedCornerShape(14.dp)
        )
        Button(onClick = {
            if (running) return@Button
            running = true; output = "Chal raha hai…"
            scope.launch {
                val r = withContext(Dispatchers.Default) { CodeRunner.runJs(code) }
                output = r.output + (r.error?.let { "\n$it" } ?: "")
                running = false
            }
        }, modifier = Modifier.fillMaxWidth()) { Text(if (running) "Running…" else "Run karo") }
        Text("Output:", color = ToolAccent, fontSize = 13.sp)
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(ToolCard).padding(12.dp)) {
            Text(output.ifBlank { "-" }, color = ToolInk, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
        }
        Text("On-device JavaScript (Rhino). Offline, free, kuch upload nahi hota.", color = Color(0xFF7F7B99), fontSize = 12.sp)
    }
}

@Composable
fun ScannerScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var result by remember { mutableStateOf("") }
    var permDenied by remember { mutableStateOf(false) }
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
        permDenied = !granted
    }
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) launcher.launch(android.Manifest.permission.CAMERA)
    }
    ToolScaffold("Scanner") {
        if (permDenied) {
            Text("Camera permission nahi mili - scan nahi hoga.", color = Color(0xFFFF9DB5))
        } else {
            AndroidView(factory = { ctx ->
                val pv = PreviewView(ctx)
                val future = ProcessCameraProvider.getInstance(ctx)
                future.addListener({
                    try {
                        val provider = future.get()
                        val preview = Preview.Builder().build().also { it.setSurfaceProvider(pv.surfaceProvider) }
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                        val scanner = BarcodeScanning.getClient()
                        analysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { proxy ->
                            @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
                            val media = proxy.image
                            if (media != null) {
                                val img = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                                scanner.process(img)
                                    .addOnSuccessListener { codes ->
                                        codes.firstOrNull()?.rawValue?.let { v ->
                                            if (result != v) result = v
                                        }
                                    }
                                    .addOnCompleteListener { proxy.close() }
                            } else proxy.close()
                        }
                        provider.unbindAll()
                        provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                    } catch (_: Exception) {}
                }, ContextCompat.getMainExecutor(ctx))
                pv
            }, modifier = Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(18.dp)))
        }
        if (result.isNotBlank()) {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(ToolCard).padding(12.dp)) {
                Column {
                    Text("Scanned:", color = ToolAccent, fontSize = 13.sp)
                    Text(result, color = ToolInk, fontSize = 14.sp)
                }
            }
            if (result.startsWith("http")) {
                TextButton(onClick = {
                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(result))
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Text("Link kholo") }
            }
        }
        Text("QR / barcode camera ke saamne rakho. On-device scan (ML Kit), photo kahin nahi jaati.", color = Color(0xFF7F7B99), fontSize = 12.sp)
    }
}

@Composable
fun MirrorScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) launcher.launch(android.Manifest.permission.CAMERA)
    }
    ToolScaffold("Mirror") {
        AndroidView(factory = { ctx ->
            val pv = PreviewView(ctx).apply {
                scaleX = -1f // mirror flip
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
            val future = ProcessCameraProvider.getInstance(ctx)
            future.addListener({
                try {
                    val provider = future.get()
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(pv.surfaceProvider) }
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview)
                } catch (_: Exception) {}
            }, ContextCompat.getMainExecutor(ctx))
            pv
        }, modifier = Modifier.fillMaxWidth().height(420.dp).clip(RoundedCornerShape(18.dp)))
        Text("Front camera mirror. Kuch record ya save nahi hota.", color = Color(0xFF7F7B99), fontSize = 12.sp)
    }
}

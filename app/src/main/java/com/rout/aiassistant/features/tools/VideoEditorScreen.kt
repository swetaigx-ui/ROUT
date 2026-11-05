package com.rout.aiassistant.features.tools

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.launch
import java.io.File

/** Video Editor: pick a video, trim start/end seconds, export on-device (Media3 Transformer). No upload. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoEditorScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var src by remember { mutableStateOf<Uri?>(null) }
    var startS by remember { mutableStateOf("0") }
    var endS by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var outFile by remember { mutableStateOf<File?>(null) }
    var busy by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> src = uri; outFile = null; status = "" }

    Column(Modifier.fillMaxSize().background(Color(0xFF0A0A12)).statusBarsPadding().padding(16.dp)
        .verticalScroll(rememberScrollState())) {
        Text("Video Editor", color = Color(0xFFEEECF8), fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Button(onClick = { picker.launch("video/*") }, modifier = Modifier.fillMaxWidth()) {
            Text(if (src == null) "Video chuno" else "Video badlo")
        }
        if (src != null) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = startS, onValueChange = { startS = it.filter(Char::isDigit).take(5) },
                    label = { Text("Start (sec)") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp))
                OutlinedTextField(value = endS, onValueChange = { endS = it.filter(Char::isDigit).take(5) },
                    label = { Text("End (sec, khali = poora)") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp))
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = {
                if (busy) return@Button
                busy = true; status = "Export ho raha hai…"
                scope.launch {
                    try {
                        val out = File(context.cacheDir, "rout_edit_${System.currentTimeMillis()}.mp4")
                        val startMs = (startS.toLongOrNull() ?: 0L) * 1000
                        val endMs = endS.toLongOrNull()?.let { it * 1000 }
                        val clip = MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(startMs)
                            .apply { if (endMs != null && endMs > startMs) setEndPositionMs(endMs) }
                            .build()
                        val mediaItem = MediaItem.Builder().setUri(src!!).setClippingConfiguration(clip).build()
                        val item = EditedMediaItem.Builder(mediaItem).build()
                        val transformer = Transformer.Builder(context).build()
                        transformer.start(item, out.absolutePath)
                        var done = false
                        transformer.addListener(object : Transformer.Listener {
                            override fun onCompleted(composition: Composition, result: ExportResult) { done = true; outFile = out; status = "Ready! Size: ${out.length() / 1024} KB" }
                            override fun onError(composition: Composition, result: ExportResult, exception: ExportException) { done = true; status = "Export nahi hua: ${exception.message}" }
                        })
                        while (!done) kotlinx.coroutines.delay(300)
                    } catch (e: Exception) { status = "Export nahi hua: ${e.message}" }
                    busy = false
                }
            }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Exporting…" else "Trim karke save karo") }
            if (outFile != null) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = {
                    val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", outFile!!)
                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "video/mp4"; putExtra(android.content.Intent.EXTRA_STREAM, uri)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                }, modifier = Modifier.fillMaxWidth()) { Text("Share / Save") }
            }
        }
        if (status.isNotBlank()) { Spacer(Modifier.height(10.dp)); Text(status, color = Color(0xFF67F0FF), fontSize = 13.sp) }
        Spacer(Modifier.height(10.dp))
        Text("Trim on-device hota hai (Media3) - video kahin upload nahi hota. Filters/effects baad mein.", color = Color(0xFF7F7B99), fontSize = 12.sp)
    }
}

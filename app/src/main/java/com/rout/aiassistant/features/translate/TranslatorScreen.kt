package com.rout.aiassistant.features.translate

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun TranslatorScreen() {
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("Hindi") }
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Color(0xFF0A0A12)).statusBarsPadding().padding(16.dp)) {
        Text("Translator", color = Color(0xFFEEECF8), fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TranslatorManager.languages.forEach { (name, _) ->
                FilterChip(selected = target == name, onClick = { target = name }, label = { Text(name, fontSize = 12.sp) })
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(value = input, onValueChange = { input = it },
            label = { Text("Text likho") }, modifier = Modifier.fillMaxWidth().height(130.dp),
            shape = RoundedCornerShape(14.dp))
        Spacer(Modifier.height(10.dp))
        Button(onClick = {
            if (busy || input.isBlank()) return@Button
            busy = true; output = "…"
            scope.launch {
                val code = TranslatorManager.languages.first { it.first == target }.second
                try {
                    val (src, out) = TranslatorManager.autoTranslate(input, code)
                    output = "($src -> $target)\n$out"
                } catch (e: Exception) {
                    output = "Abhi nahi hua: ${e.message ?: "model download chal raha hai (pehli baar net chahiye)"}"
                }
                busy = false
            }
        }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Translating…" else "Translate karo") }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xC2161328)).padding(12.dp)) {
            Text(output.ifBlank { "-" }, color = Color(0xFFEEECF8), fontSize = 15.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text("On-device (ML Kit) - text kahin upload nahi hota. Pehli baar har bhasha ka chhota model download hota hai (free).",
            color = Color(0xFF7F7B99), fontSize = 12.sp)
    }
}

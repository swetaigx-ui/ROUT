package com.rout.aiassistant.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.rout.aiassistant.MainViewModel
import com.rout.aiassistant.ui.components.GlassBox
import com.rout.aiassistant.ui.components.RoutBackground
import com.rout.aiassistant.ui.components.RoutDock
import com.rout.aiassistant.ui.theme.RoutCyan
import com.rout.aiassistant.voice.AssistantState

/** Chat view from the reference design: glass message panel, ask field and mic button, floating dock. */
@Composable
fun ChatScreen(vm: MainViewModel, nav: NavController) {
    val messages by vm.messages.collectAsState()
    val state by vm.assistantState.collectAsState()
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var askClear by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    var micGranted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        micGranted = granted
        if (granted) vm.onMicTap() else vm.setBanner("Microphone denied. Text input still works.")
    }
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1) }

    RoutBackground {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Chat", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                GlassBox(radius = 50.dp) {
                    Text("Clear", color = Color(0xFFC9D2DE), fontSize = 13.sp,
                        modifier = Modifier.clickable { askClear = true }.padding(horizontal = 14.dp, vertical = 8.dp))
                }
            }
            GlassBox(Modifier.weight(1f).fillMaxWidth(), radius = 26.dp) {
                if (messages.isEmpty()) {
                    Text("Abhi koi baat nahi hui. Neeche type karo ya mic dabao.", color = Color(0xFF8D97A6), fontSize = 14.sp, modifier = Modifier.padding(20.dp))
                }
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(messages, key = { it.id }) { m ->
                        val mine = m.role == "user"
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                            Box(
                                Modifier.widthIn(max = 300.dp)
                                    .then(if (mine) Modifier.background(Brush.linearGradient(listOf(Color(0xFF2563EB), Color(0xFF7C3AED))), RoundedCornerShape(20.dp))
                                          else Modifier.background(Color(0x12FFFFFF), RoundedCornerShape(20.dp)).border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(20.dp)))
                                    .clickable(enabled = !mine) { vm.replay(m.text) }
                                    .padding(horizontal = 15.dp, vertical = 11.dp)
                            ) { Text(m.text, color = Color(0xFFF5F7FA), fontSize = 15.sp, lineHeight = 21.sp) }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().imePadding(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.weight(1f).background(Color(0x0FFFFFFF), RoundedCornerShape(50)).border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(50))
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = text, onValueChange = { text = it }, singleLine = true,
                        textStyle = TextStyle(color = Color(0xFFF5F7FA), fontSize = 15.sp),
                        cursorBrush = Brush.linearGradient(listOf(RoutCyan, RoutCyan)),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (text.isEmpty()) Text("Type karo ya mic dabao\u2026", color = Color(0xFF8D97A6), fontSize = 15.sp, maxLines = 1)
                            inner()
                        }
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.size(50.dp).background(Brush.linearGradient(listOf(Color(0xFF2563EB), Color(0xFF7C3AED))), RoundedCornerShape(50))
                        .clickable {
                            if (text.isNotBlank()) { vm.onUserText(text, false); text = "" }
                            else if (!micGranted) micLauncher.launch(Manifest.permission.RECORD_AUDIO) else vm.onMicTap()
                        },
                    contentAlignment = Alignment.Center
                ) { Text(if (text.isNotBlank()) "\u2192" else if (state == AssistantState.LISTENING) "\u25A0" else "\uD83C\uDF99", color = Color.White, fontSize = 18.sp) }
            }
            RoutDock("chat", nav, Modifier.align(Alignment.CenterHorizontally).padding(bottom = 10.dp))
        }
    }
    if (askClear) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { askClear = false },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { askClear = false; vm.clearHistory() }) { Text("Clear", color = Color(0xFFFF6B7A)) } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { askClear = false }) { Text("Cancel", color = Color(0xFF8D97A6)) } },
            title = { Text("Clear chat?", color = Color.White) },
            text = { Text("This deletes the chat and saved history on this phone.", color = Color(0xFF8D97A6)) },
            containerColor = Color(0xFF12151B)
        )
    }
}

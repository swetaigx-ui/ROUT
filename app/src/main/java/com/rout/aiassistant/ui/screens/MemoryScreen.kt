package com.rout.aiassistant.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.rout.aiassistant.MainViewModel
import com.rout.aiassistant.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun MemoryScreen(vm: MainViewModel, nav: NavController) {
    val facts by vm.facts.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    ScreenScaffold(title = "ROUT Memory", nav = nav) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("Facts you asked ROUT to remember. Delete any item, export everything, or clear it all in Settings.",
                color = RoutDim, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                scope.launch {
                    val text = vm.exportMemoryText()
                    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
                    val f = File(dir, "rout-memory.txt")
                    f.writeText(text)
                    val uri = FileProvider.getUriForFile(context, "com.rout.aiassistant.fileprovider", f)
                    val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"; putExtra(android.content.Intent.EXTRA_STREAM, uri)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(android.content.Intent.createChooser(share, "Export ROUT memory").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }) { Text("Export memory", color = RoutCyan) }
            Spacer(Modifier.height(12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(facts, key = { it.id }) { f ->
                    Surface(color = RoutPanel, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(f.text, color = RoutText, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            IconButton(onClick = { vm.deleteFact(f.id) }) { Icon(Icons.Default.Delete, "Delete", tint = RoutError) }
                        }
                    }
                }
            }
        }
    }
}

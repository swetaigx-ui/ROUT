package com.rout.aiassistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.rout.aiassistant.MainViewModel
import com.rout.aiassistant.ui.theme.*

@Composable
fun NotesScreen(vm: MainViewModel, nav: NavController) {
    val notes by vm.notes.collectAsState()
    var body by remember { mutableStateOf("") }
    ScreenScaffold(title = "ROUT Notes", nav = nav) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = body, onValueChange = { body = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("New note...") }, singleLine = true,
                    colors = fieldColors()
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = { if (body.isNotBlank()) { vm.addNote("", body); body = "" } },
                    colors = ButtonDefaults.buttonColors(containerColor = RoutCyan, contentColor = RoutBlack)) { Text("Save", color = RoutBlack) }
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(notes, key = { it.id }) { n ->
                    Surface(color = RoutPanel, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(n.title, color = RoutIce, style = MaterialTheme.typography.titleMedium)
                                Text(n.body, color = RoutText, style = MaterialTheme.typography.bodyMedium)
                            }
                            IconButton(onClick = { vm.deleteNote(n.id) }) { Icon(Icons.Default.Delete, "Delete", tint = RoutError) }
                        }
                    }
                }
            }
        }
    }
}

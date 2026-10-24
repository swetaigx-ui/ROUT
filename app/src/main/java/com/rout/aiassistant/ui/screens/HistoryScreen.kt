package com.rout.aiassistant.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.rout.aiassistant.MainViewModel
import com.rout.aiassistant.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(vm: MainViewModel, nav: NavController) {
    val conversations by vm.conversations.collectAsState()
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<String>?>(null) }
    var renameTarget by remember { mutableStateOf<com.rout.aiassistant.data.Conversation?>(null) }
    var renameText by remember { mutableStateOf("") }

    ScreenScaffold(title = "Conversation history", nav = nav) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("Your past conversations, stored only on this phone. Rename, delete, or search them.",
                color = RoutDim, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text("Search history...") },
                    modifier = Modifier.weight(1f), singleLine = true, colors = fieldColors()
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = { scope.launch { results = vm.searchHistory(query) } },
                    colors = ButtonDefaults.buttonColors(containerColor = RoutCyan, contentColor = RoutBlack)) { Text("Search", color = RoutBlack) }
            }
            results?.let { hits ->
                Spacer(Modifier.height(8.dp))
                Surface(color = RoutPanel, shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        if (hits.isEmpty()) Text("Nothing found for \"$query\".", color = RoutDim)
                        else hits.forEach { Text("- ${it.take(80)}", color = RoutText, modifier = Modifier.padding(vertical = 3.dp)) }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { vm.newConversation(); nav.navigate("home") { popUpTo("home") { inclusive = true } } }) {
                Text("+ Start a new conversation", color = RoutCyan)
            }
            if (conversations.isEmpty()) {
                Text("No conversations yet.", color = RoutDim, modifier = Modifier.padding(top = 16.dp))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                    items(conversations, key = { it.id }) { c ->
                        Surface(color = RoutPanel, shape = RoundedCornerShape(14.dp)) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(c.title, color = RoutText)
                                    Text(SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(c.createdAt)),
                                        color = RoutDim, style = MaterialTheme.typography.labelSmall)
                                }
                                TextButton(onClick = { vm.openConversation(c.id); nav.navigate("home") { popUpTo("home") { inclusive = true } } }) {
                                    Text("Open", color = RoutCyan)
                                }
                                IconButton(onClick = { renameTarget = c; renameText = c.title }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Rename", tint = RoutDim)
                                }
                                IconButton(onClick = { vm.deleteConversation(c.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = RoutError)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename conversation", color = RoutText) },
            text = {
                OutlinedTextField(value = renameText, onValueChange = { renameText = it },
                    singleLine = true, colors = fieldColors())
            },
            confirmButton = {
                TextButton(onClick = { vm.renameConversation(target.id, renameText); renameTarget = null }) {
                    Text("Save", color = RoutCyan)
                }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel", color = RoutDim) } },
            containerColor = RoutGraphite
        )
    }
}

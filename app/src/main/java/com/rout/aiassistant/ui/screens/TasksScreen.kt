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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.rout.aiassistant.MainViewModel
import com.rout.aiassistant.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TasksScreen(vm: MainViewModel, nav: NavController) {
    val tasks by vm.tasks.collectAsState()
    var title by remember { mutableStateOf("") }
    val fmt = remember { SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()) }
    ScreenScaffold(title = "ROUT Tasks", nav = nav) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("New task...") }, singleLine = true,
                    colors = fieldColors()
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = { if (title.isNotBlank()) { vm.addTask(title); title = "" } },
                    colors = ButtonDefaults.buttonColors(containerColor = RoutCyan, contentColor = RoutBlack)) { Text("Add", color = RoutBlack) }
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tasks, key = { it.id }) { t ->
                    Surface(color = RoutPanel, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = t.done, onCheckedChange = { vm.setTaskDone(t.id, it) },
                                colors = CheckboxDefaults.colors(checkedColor = RoutCyan))
                            Column(Modifier.weight(1f)) {
                                Text(t.title, color = if (t.done) RoutDim else RoutText, style = MaterialTheme.typography.bodyLarge)
                                t.dueAt?.let { Text("Reminder: " + fmt.format(Date(it)), color = RoutViolet, style = MaterialTheme.typography.labelSmall) }
                            }
                            IconButton(onClick = { vm.deleteTask(t.id) }) { Icon(Icons.Default.Delete, "Delete", tint = RoutError) }
                        }
                    }
                }
            }
        }
    }
}

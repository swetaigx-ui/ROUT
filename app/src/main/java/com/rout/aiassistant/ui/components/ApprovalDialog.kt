package com.rout.aiassistant.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/** The exact layout of every ROUT approval/confirm dialog: title, scrollable body (buttons stay reachable), two buttons. */
@Composable
fun RoutApprovalSurface(
    title: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmColor: Color = Color(0xFF4DE8FF),
    dismissColor: Color = Color(0xFFFF6B7A),
    container: Color = Color(0xFF1B1B2B),
    body: @Composable () -> Unit
) {
    Surface(shape = RoundedCornerShape(24.dp), color = container, contentColor = Color(0xFFE6E3F5)) {
        Column(Modifier.padding(24.dp)) {
            Text(title, color = Color.White, fontSize = 20.sp)
            Spacer(Modifier.height(14.dp))
            val scroll = rememberScrollState()
            Column(Modifier.weight(1f, fill = false).heightIn(max = 500.dp).verticalScroll(scroll)) { body() }
            if (scroll.maxValue > 0 && scroll.value < scroll.maxValue) {
                Text("Neeche scroll karke poora dekho", color = Color(0xFFFFB84D), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text(dismissLabel, color = dismissColor) }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onConfirm) { Text(confirmLabel, color = confirmColor) }
            }
        }
    }
}

@Composable
fun RoutApprovalDialog(
    title: String, confirmLabel: String, dismissLabel: String,
    onConfirm: () -> Unit, onDismiss: () -> Unit,
    confirmColor: Color = Color(0xFF4DE8FF), dismissColor: Color = Color(0xFFFF6B7A),
    container: Color = Color(0xFF1B1B2B),
    body: @Composable () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        RoutApprovalSurface(title, confirmLabel, dismissLabel, onConfirm, onDismiss, confirmColor, dismissColor, container, body)
    }
}

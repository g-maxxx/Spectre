package dev.thomasbuilds.spectre.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun InfoButton(
  title: String,
  body: String,
  modifier: Modifier = Modifier,
  buttonSize: Dp = 28.dp,
  iconSize: Dp = 18.dp
) {
  var showDialog by remember { mutableStateOf(false) }
  IconButton(
    onClick = { showDialog = true },
    modifier = modifier.size(buttonSize)
  ) {
    Icon(
      imageVector = Icons.Outlined.Info,
      contentDescription = "Help: $title",
      modifier = Modifier.size(iconSize),
      tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
  if (showDialog) {
    HelpDialog(title, body) { showDialog = false }
  }
}

@Composable
internal fun HelpDialog(
  title: String,
  body: String,
  onDismiss: () -> Unit
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = { Text(body, style = MaterialTheme.typography.bodyMedium) },
    confirmButton = {
      TextButton(onClick = onDismiss) { Text("Got it") }
    }
  )
}

package io.brokentooth.soma.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.brokentooth.soma.ui.theme.SomaAccentBlue
import io.brokentooth.soma.ui.theme.SomaAccentRed
import io.brokentooth.soma.ui.theme.SomaSurface
import io.brokentooth.soma.ui.theme.SomaSurfaceElevated
import io.brokentooth.soma.ui.theme.SomaTextMuted
import io.brokentooth.soma.ui.theme.SomaTextPrimary

@Composable
fun ChatInput(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    isStreaming: Boolean,
    onSlashModel: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Show slash command popup when text starts with "/"
    val showSlashMenu = text.startsWith("/") && !isStreaming
    val isModelCommand = text.trim().equals("/model", ignoreCase = true)

    Surface(
        color = SomaSurface,
        tonalElevation = 0.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            // Slash command dropdown
            if (showSlashMenu && !isModelCommand) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SomaSurfaceElevated)
                        .clickable {
                            onTextChange("")
                            onSlashModel()
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "/model  —  Switch AI model",
                        fontSize = 14.sp,
                        color = SomaTextPrimary
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                TextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text("Message SOMA...", color = SomaTextMuted)
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = SomaSurfaceElevated,
                        unfocusedContainerColor = SomaSurfaceElevated,
                        focusedTextColor = SomaTextPrimary,
                        unfocusedTextColor = SomaTextPrimary,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        cursorColor = SomaAccentBlue
                    ),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (isModelCommand) {
                                onTextChange("")
                                onSlashModel()
                            } else if (text.isNotBlank() && !isStreaming) {
                                onSend()
                            }
                        }
                    ),
                    maxLines = 5
                )

                Spacer(Modifier.width(8.dp))

                if (isStreaming) {
                    IconButton(onClick = onStop) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Stop streaming",
                            tint = SomaAccentRed
                        )
                    }
                } else {
                    IconButton(
                        onClick = {
                            if (isModelCommand) {
                                onTextChange("")
                                onSlashModel()
                            } else {
                                onSend()
                            }
                        },
                        enabled = text.isNotBlank()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send",
                            tint = if (text.isNotBlank()) SomaAccentBlue else SomaTextMuted
                        )
                    }
                }
            }
        }
    }
}

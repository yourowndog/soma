package io.brokentooth.soma.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.brokentooth.soma.data.model.Message
import io.brokentooth.soma.ui.theme.SomaBackground
import io.brokentooth.soma.ui.theme.SomaSurface
import io.brokentooth.soma.ui.theme.SomaTextMuted
import io.brokentooth.soma.ui.theme.SomaTextPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentModel by viewModel.currentModel.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }
    var showModelSelector by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val isKeyboardVisible = WindowInsets.ime.getBottom(density) > 0

    // Total item count including the in-progress streaming bubble
    val totalItems = uiState.messages.size + if (uiState.streamingText != null) 1 else 0

    // Scroll to bottom whenever:
    // 1. A new message is added
    // 2. The assistant is streaming text
    // 3. The keyboard opens
    LaunchedEffect(totalItems, uiState.isStreaming, isKeyboardVisible) {
        if (totalItems > 0) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    // Model selector bottom sheet
    if (showModelSelector) {
        ModelSelectorSheet(
            models = viewModel.availableModels,
            currentModelId = currentModel.id,
            onModelSelected = { viewModel.switchModel(it) },
            onDismiss = { showModelSelector = false }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "⚡ SOMA",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = SomaTextPrimary
                    )
                },
                actions = {
                    Text(
                        text = currentModel.displayName,
                        fontSize = 12.sp,
                        color = SomaTextMuted,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SomaSurface,
                    titleContentColor = SomaTextPrimary
                )
            )
        },
        bottomBar = {
            ChatInput(
                text = inputText,
                onTextChange = { inputText = it },
                onSend = {
                    viewModel.sendMessage(inputText)
                    inputText = ""
                },
                onStop = { viewModel.stopStreaming() },
                isStreaming = uiState.isStreaming,
                onSlashModel = { showModelSelector = true },
                modifier = Modifier
                    .imePadding()
                    .navigationBarsPadding()
            )
        },
        containerColor = SomaBackground
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(SomaBackground)
        ) {
            if (uiState.messages.isEmpty() && !uiState.isStreaming) {
                EmptyState(modifier = Modifier.align(Alignment.Center))
            }

            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 16.dp, top = 8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(uiState.messages, key = { it.id }) { message ->
                    if (message.role == "system") {
                        SystemMessage(text = message.content)
                    } else {
                        MessageBubble(message = message)
                    }
                }

                // Streaming bubble — rendered live as tokens arrive
                uiState.streamingText?.let { partial ->
                    item(key = "streaming") {
                        MessageBubble(
                            message = Message(
                                id = "streaming",
                                sessionId = uiState.sessionId,
                                role = "assistant",
                                content = partial
                            ),
                            isStreaming = true
                        )
                    }
                }

                // Error banner
                uiState.error?.let { error ->
                    item(key = "error") {
                        Text(
                            text = "Error: $error",
                            color = androidx.compose.ui.graphics.Color(0xFFF85149),
                            fontSize = 12.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }

                item { Spacer(Modifier.height(4.dp)) }
            }
        }
    }
}

@Composable
private fun SystemMessage(text: String) {
    Text(
        text = text,
        color = SomaTextMuted,
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    )
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Text(
        text = "Start a conversation",
        color = SomaTextMuted,
        fontSize = 14.sp,
        modifier = modifier
    )
}

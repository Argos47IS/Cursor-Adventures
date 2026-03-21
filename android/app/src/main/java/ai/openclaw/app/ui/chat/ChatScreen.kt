package ai.openclaw.app.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ai.openclaw.app.network.ChatMessage
import ai.openclaw.app.network.ConnectionState
import ai.openclaw.app.network.GatewayEvent
import ai.openclaw.app.service.ServiceLocator
import ai.openclaw.app.util.MarkdownRenderer
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

@Composable
fun ChatScreen() {
    val gatewayClient = ServiceLocator.gatewayClient
    val connectionState by gatewayClient.connectionState.collectAsState()
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var selectedSession by remember { mutableStateOf("main") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(connectionState) {
        if (connectionState == ConnectionState.CONNECTED) {
            val response = gatewayClient.getChatHistory(selectedSession)
            if (response?.ok == true) {
                val payload = response.payload?.jsonObject
                val history = payload?.get("messages")?.jsonArray
                history?.forEach { elem ->
                    try {
                        val msg = kotlinx.serialization.json.Json.decodeFromJsonElement(
                            ChatMessage.serializer(),
                            elem,
                        )
                        messages.add(msg)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        gatewayClient.events.collect { event ->
            when (event) {
                is GatewayEvent.ChatMessageReceived -> {
                    messages.add(event.message)
                    coroutineScope.launch {
                        listState.animateScrollToItem(messages.size - 1)
                    }
                }
                is GatewayEvent.ChatStreamChunk -> {
                    val lastIdx = messages.indexOfLast {
                        it.id == event.messageId && it.role == "assistant"
                    }
                    if (lastIdx >= 0) {
                        messages[lastIdx] = messages[lastIdx].copy(
                            content = messages[lastIdx].content + event.chunk,
                        )
                    } else {
                        messages.add(
                            ChatMessage(
                                id = event.messageId,
                                role = "assistant",
                                content = event.chunk,
                                streaming = true,
                            ),
                        )
                    }
                }
                else -> {}
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Session selector
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 2.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Forum,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Session: $selectedSession",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (connectionState != ConnectionState.CONNECTED) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.WifiOff,
                        null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(64.dp),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Not connected to gateway",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Go to Connect tab to set up your gateway connection.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                }
            }
        } else {
            // Messages list
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                if (messages.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillParentMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "\uD83E\uDD9E",
                                    style = MaterialTheme.typography.headlineLarge,
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "Start a conversation",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    "Type a message below to talk to your AI assistant.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }

                items(messages, key = { it.hashCode() }) { message ->
                    ChatBubble(message)
                }
            }

            // Input bar
            Surface(
                tonalElevation = 3.dp,
                shadowElevation = 8.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Type a message…") },
                        maxLines = 4,
                        shape = RoundedCornerShape(24.dp),
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    FilledIconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                val text = inputText.trim()
                                inputText = ""
                                messages.add(
                                    ChatMessage(
                                        role = "user",
                                        content = text,
                                        timestamp = System.currentTimeMillis(),
                                        session = selectedSession,
                                    ),
                                )
                                isLoading = true
                                coroutineScope.launch {
                                    gatewayClient.sendChatMessage(text, selectedSession)
                                    isLoading = false
                                    listState.animateScrollToItem(messages.size - 1)
                                }
                            }
                        },
                        enabled = inputText.isNotBlank() && !isLoading,
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Send, "Send")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val containerColor = if (isUser) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (isUser) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment,
    ) {
        Surface(
            color = containerColor,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp,
            ),
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(16.dp)),
        ) {
            if (isUser) {
                Text(
                    text = message.content,
                    modifier = Modifier.padding(12.dp),
                    color = contentColor,
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                MarkdownRenderer(
                    markdown = message.content,
                    modifier = Modifier.padding(12.dp),
                    color = contentColor,
                )
            }
        }

        if (message.streaming == true) {
            LinearProgressIndicator(
                modifier = Modifier
                    .width(60.dp)
                    .padding(top = 4.dp),
                strokeCap = StrokeCap.Round,
            )
        }
    }
}

package io.brokentooth.soma.ui.chat

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.brokentooth.soma.data.model.Message
import io.brokentooth.soma.ui.theme.CodeFontFamily
import io.brokentooth.soma.ui.theme.SomaAccentBlue
import io.brokentooth.soma.ui.theme.SomaAccentPurple
import io.brokentooth.soma.ui.theme.SomaBackground
import io.brokentooth.soma.ui.theme.SomaSurface
import io.brokentooth.soma.ui.theme.SomaSurfaceElevated
import io.brokentooth.soma.ui.theme.SomaTextMuted
import io.brokentooth.soma.ui.theme.SomaTextPrimary
import io.brokentooth.soma.ui.theme.SomaTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

@Composable
fun MessageBubble(message: Message, isStreaming: Boolean = false) {
    val isUser = message.role == "user"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        // Label + timestamp row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (!isUser) {
                Text(
                    text = "SOMA",
                    fontSize = 11.sp,
                    color = SomaAccentBlue,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = timeFormat.format(Date(message.timestamp)),
                    fontSize = 10.sp,
                    color = SomaTextMuted
                )
            } else {
                Text(
                    text = timeFormat.format(Date(message.timestamp)),
                    fontSize = 10.sp,
                    color = SomaTextMuted
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "You",
                    fontSize = 11.sp,
                    color = SomaTextSecondary,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }

        Spacer(Modifier.height(2.dp))

        // Content area
        Box(
            modifier = if (isUser) {
                Modifier
                    .clip(RoundedCornerShape(12.dp, 4.dp, 12.dp, 12.dp))
                    .background(SomaSurfaceElevated)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            } else {
                Modifier.padding(end = 48.dp) // leave breathing room on right
            }
        ) {
            if (isUser) {
                SelectionContainer {
                    Text(
                        text = message.content,
                        color = SomaTextPrimary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            } else {
                AssistantContent(text = message.content, isStreaming = isStreaming)
            }
        }
    }
}

// ── Assistant markdown renderer ─────────────────────────────────────────────

@Composable
private fun AssistantContent(text: String, isStreaming: Boolean) {
    val segments = parseSegments(text)

    Column {
        segments.forEachIndexed { index, segment ->
            when (segment) {
                is Segment.Plain -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SelectionContainer {
                            Text(
                                text = buildInlineAnnotated(segment.text),
                                color = SomaTextPrimary,
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                        }
                        // Blinking cursor only on the last segment while streaming
                        if (isStreaming && index == segments.lastIndex) {
                            StreamingCursor()
                        }
                    }
                }
                is Segment.Code -> {
                    Spacer(Modifier.height(6.dp))
                    CodeBlock(language = segment.language, code = segment.code)
                    Spacer(Modifier.height(6.dp))
                    // Blinking cursor after code block if it's the last segment
                    if (isStreaming && index == segments.lastIndex) {
                        StreamingCursor()
                    }
                }
            }
        }
        // If text is empty (stream just started), show cursor alone
        if (isStreaming && text.isEmpty()) {
            StreamingCursor()
        }
    }
}

@Composable
private fun StreamingCursor() {
    val transition = rememberInfiniteTransition(label = "cursor")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "cursor_alpha"
    )
    Text(
        text = "▋",
        color = SomaAccentBlue.copy(alpha = alpha),
        fontSize = 14.sp,
        modifier = Modifier.padding(start = 1.dp)
    )
}

@Composable
private fun CodeBlock(language: String, code: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SomaSurface)
    ) {
        if (language.isNotBlank()) {
            Text(
                text = language,
                fontSize = 11.sp,
                color = SomaTextMuted,
                fontFamily = CodeFontFamily,
                modifier = Modifier
                    .background(SomaBackground)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
        SelectionContainer {
            Text(
                text = code,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontFamily = CodeFontFamily,
                color = SomaTextPrimary,
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            )
        }
    }
}

// ── Markdown parsing helpers ────────────────────────────────────────────────

private sealed class Segment {
    data class Plain(val text: String) : Segment()
    data class Code(val language: String, val code: String) : Segment()
}

private val codeBlockRegex = Regex("```(\\w*)\\n?([\\s\\S]*?)```")

private fun parseSegments(text: String): List<Segment> {
    val result = mutableListOf<Segment>()
    var cursor = 0
    for (match in codeBlockRegex.findAll(text)) {
        val start = match.range.first
        if (start > cursor) {
            result.add(Segment.Plain(text.substring(cursor, start)))
        }
        result.add(Segment.Code(match.groupValues[1], match.groupValues[2].trimEnd()))
        cursor = match.range.last + 1
    }
    if (cursor < text.length) {
        result.add(Segment.Plain(text.substring(cursor)))
    }
    return result.ifEmpty { listOf(Segment.Plain(text)) }
}

private val inlineCodeRegex = Regex("`([^`\n]+)`")

private fun buildInlineAnnotated(text: String): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    for (match in inlineCodeRegex.findAll(text)) {
        val start = match.range.first
        if (start > cursor) append(text.substring(cursor, start))
        withStyle(
            SpanStyle(
                fontFamily = CodeFontFamily,
                background = Color(0xFF1C2128),
                color = SomaAccentPurple,
                fontSize = 13.sp
            )
        ) {
            append(match.groupValues[1])
        }
        cursor = match.range.last + 1
    }
    if (cursor < text.length) append(text.substring(cursor))
}

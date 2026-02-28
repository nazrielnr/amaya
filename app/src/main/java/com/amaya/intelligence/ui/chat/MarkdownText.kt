package com.amaya.intelligence.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ═══════════════════════════════════════════════════════════════════
//  Block-level AST
// ═══════════════════════════════════════════════════════════════════

private sealed class MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock()
    data class Paragraph(val text: String) : MdBlock()
    data class CodeBlock(val lang: String, val code: String) : MdBlock()
    data class UnorderedList(val items: List<ListEntry>) : MdBlock()
    data class OrderedList(val items: List<ListEntry>) : MdBlock()
    data class TaskList(val items: List<TaskEntry>) : MdBlock()
    data class BlockQuote(val text: String) : MdBlock()
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MdBlock()
    object HorizontalRule : MdBlock()
}

private data class ListEntry(val text: String, val indent: Int = 0)
private data class TaskEntry(val text: String, val checked: Boolean)

// ═══════════════════════════════════════════════════════════════════
//  Regex constants — compiled once
// ═══════════════════════════════════════════════════════════════════

private val HEADING_RE = Regex("^(#{1,6})\\s+(.*)")
private val HR_RE = Regex("^[-*_]{3,}\\s*$")
private val UL_RE = Regex("^(\\s*)[-*+]\\s+(.*)")
private val OL_RE = Regex("^(\\s*)\\d+[.)\\s]\\s*(.*)")
private val TASK_RE = Regex("^\\s*[-*+]\\s+\\[([ xX])]\\s+(.*)")
private val TABLE_SEP_RE = Regex("^\\|?[\\s:]*-{2,}[\\s:]*([|][\\s:]*-{2,}[\\s:]*)*\\|?\\s*$")
private val BQ_RE = Regex("^>\\s?(.*)")

// ═══════════════════════════════════════════════════════════════════
//  Top-level composable
// ═══════════════════════════════════════════════════════════════════

@Composable
fun MarkdownText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    // fontSize for paragraph/list body text (default 16sp to match user bubble)
    fontSize: androidx.compose.ui.unit.TextUnit = 16.sp,
    lineHeight: androidx.compose.ui.unit.TextUnit = 24.sp
) {
    val blocks = remember(text) { parseBlocks(text) }
    val scheme   = MaterialTheme.colorScheme
    val typo     = MaterialTheme.typography
    val spacing  = if (compact) 4.dp else 12.dp

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(spacing)) {
        blocks.forEach { block ->
            when (block) {

                is MdBlock.Heading -> {
                    val style = if (compact) when (block.level) {
                        1    -> typo.labelLarge.copy(fontSize = 12.sp, lineHeight = 16.sp)
                        2    -> typo.labelMedium.copy(fontSize = 11.sp, lineHeight = 15.sp)
                        else -> typo.labelSmall.copy(fontSize = 10.sp, lineHeight = 14.sp)
                    } else when (block.level) {
                        1    -> typo.headlineLarge
                        2    -> typo.headlineMedium
                        3    -> typo.headlineSmall
                        4    -> typo.titleLarge
                        5    -> typo.titleMedium
                        else -> typo.titleSmall
                    }
                    InlineText(
                        text     = block.text,
                        color    = color,
                        style    = style.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(top = if (!compact && block.level <= 2) 8.dp else 2.dp),
                        compact  = compact
                    )
                    if (!compact && block.level <= 2) {
                        HorizontalDivider(
                            color     = scheme.outlineVariant.copy(alpha = 0.4f),
                            thickness = 0.5.dp
                        )
                    }
                }

                is MdBlock.Paragraph -> {
                    val style = if (compact) typo.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp)
                                else typo.bodyMedium.copy(fontSize = fontSize, lineHeight = lineHeight)
                    InlineText(
                        text    = block.text,
                        color   = color,
                        style   = style,
                        compact = compact
                    )
                }

                is MdBlock.CodeBlock -> {
                    val isLight   = !isSystemInDarkTheme()
                    val bgColor   = if (isLight) Color(0xFFF2F2F7) else Color(0xFF1C1C1E)
                    val codeColor = if (isLight) Color(0xFF3A3A3C) else Color(0xFFD1D1D6)
                    Surface(shape = RoundedCornerShape(if (compact) 6.dp else 8.dp), color = bgColor,
                        modifier = Modifier.fillMaxWidth()) {
                        Column {
                            if (block.lang.isNotBlank() && !compact) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().background(bgColor).padding(horizontal = 12.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(block.lang, style = typo.labelSmall, color = codeColor.copy(alpha = 0.6f),
                                        fontFamily = FontFamily.Monospace)
                                    var copied by remember { mutableStateOf(false) }
                                    val clipboard = LocalClipboardManager.current
                                    val scope = rememberCoroutineScope()
                                    IconButton(onClick = {
                                        clipboard.setText(AnnotatedString(block.code))
                                        copied = true
                                        scope.launch { delay(2000); copied = false }
                                    }, modifier = Modifier.size(28.dp)) {
                                        Icon(if (copied) Icons.Default.Done else Icons.Default.ContentCopy, null,
                                            modifier = Modifier.size(14.dp), tint = codeColor.copy(alpha = 0.6f))
                                    }
                                }
                                HorizontalDivider(color = codeColor.copy(alpha = 0.12f))
                            }
                            val codeFontSize = if (compact) 10.sp else 12.sp
                            val codeLineHeight = if (compact) 14.sp else 18.sp
                            Text(
                                block.code,
                                style = typo.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize   = codeFontSize,
                                    lineHeight = codeLineHeight
                                ),
                                color    = codeColor,
                                modifier = Modifier.padding(if (compact) 8.dp else 12.dp)
                            )
                        }
                    }
                }

                is MdBlock.UnorderedList -> {
                    val bodyStyle = if (compact) typo.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp)
                                   else typo.bodyMedium.copy(fontSize = fontSize, lineHeight = lineHeight)
                    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 4.dp)) {
                        block.items.forEach { item ->
                            Row(modifier = Modifier.padding(start = (item.indent * 12).dp)) {
                                Text(
                                    "\u2022",
                                    style = bodyStyle,
                                    color = color.copy(alpha = 0.6f),
                                    modifier = Modifier.width(if (compact) 12.dp else 16.dp)
                                )
                                InlineText(text = item.text, color = color,
                                    style = bodyStyle, compact = compact)
                            }
                        }
                    }
                }

                is MdBlock.OrderedList -> {
                    val bodyStyle = if (compact) typo.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp)
                                   else typo.bodyMedium.copy(fontSize = fontSize, lineHeight = lineHeight)
                    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 4.dp)) {
                        block.items.forEachIndexed { idx, item ->
                            Row(modifier = Modifier.padding(start = (item.indent * 12).dp)) {
                                Text(
                                    "${idx + 1}.",
                                    style = bodyStyle,
                                    color = color.copy(alpha = 0.6f),
                                    modifier = Modifier.width(if (compact) 18.dp else 24.dp)
                                )
                                InlineText(text = item.text, color = color,
                                    style = bodyStyle, compact = compact)
                            }
                        }
                    }
                }

                is MdBlock.TaskList -> {
                    val bodyStyle = if (compact) typo.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp)
                                   else typo.bodyMedium.copy(fontSize = fontSize, lineHeight = lineHeight)
                    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 4.dp)) {
                        block.items.forEach { item ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (item.checked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                    null,
                                    modifier = Modifier.size(if (compact) 14.dp else 18.dp),
                                    tint = if (item.checked) scheme.primary else color.copy(alpha = 0.4f)
                                )
                                Spacer(Modifier.width(if (compact) 4.dp else 6.dp))
                                InlineText(text = item.text, color = if (item.checked) color.copy(alpha = 0.5f) else color,
                                    style = bodyStyle, compact = compact)
                            }
                        }
                    }
                }

                is MdBlock.BlockQuote -> {
                    Row {
                        Box(modifier = Modifier
                            .width(if (compact) 2.dp else 3.dp)
                            .fillMaxHeight()
                            .background(scheme.primary.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                        )
                        Spacer(Modifier.width(if (compact) 6.dp else 10.dp))
                        InlineText(text = block.text, color = color.copy(alpha = 0.7f),
                            style = if (compact) typo.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp)
                                    else typo.bodyMedium.copy(fontSize = fontSize, lineHeight = lineHeight, fontStyle = FontStyle.Italic),
                            compact = compact)
                    }
                }

                is MdBlock.Table -> {
                    if (!compact) {
                        // Full table only in normal mode
                        Surface(shape = RoundedCornerShape(8.dp),
                            color = scheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                            Column {
                                Row(modifier = Modifier.fillMaxWidth().background(scheme.surfaceContainerHigh).padding(horizontal = 8.dp, vertical = 6.dp)) {
                                    block.headers.forEach { h ->
                                        Text(h, modifier = Modifier.weight(1f), style = typo.labelSmall,
                                            color = color, fontWeight = FontWeight.Bold)
                                    }
                                }
                                block.rows.forEach { row ->
                                    HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.3f))
                                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                                        row.forEach { cell ->
                                            InlineText(cell, color = color, style = typo.bodySmall,
                                                modifier = Modifier.weight(1f), compact = false)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Compact: just show as plain text
                        val flat = (listOf(block.headers) + block.rows).joinToString("\n") { it.joinToString(" | ") }
                        Text(flat, style = typo.bodySmall.copy(fontSize = 10.sp, fontFamily = FontFamily.Monospace),
                            color = color.copy(alpha = 0.8f))
                    }
                }

                is MdBlock.HorizontalRule -> {
                    HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.4f))
                }

                else -> {}
            }
        }
    }
}

@Composable
private fun InlineText(
    text: String,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val scheme = MaterialTheme.colorScheme
    val uriHandler = LocalUriHandler.current
    val annotated = remember(text, color) {
        parseInline(text, color, scheme.primary, scheme.surfaceVariant, scheme.onSurface)
    }

    ClickableText(
        text = annotated,
        style = style.copy(color = color),
        modifier = modifier,
        onClick = { offset ->
            annotated.getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    try { uriHandler.openUri(annotation.item) } catch (_: Exception) {}
                }
        }
    )
}

private fun parseInline(
    text: String,
    color: Color,
    linkColor: Color,
    codeBg: Color,
    codeFg: Color
): AnnotatedString = buildAnnotatedString {
    var i = 0
    val src = text

    while (i < src.length) {
        when {
            // Escaped character
            src[i] == '\\' && i + 1 < src.length -> {
                append(src[i + 1]); i += 2
            }

            // Bold italic  ***text*** or ___text___
            matchesAt(src, i, "***") || matchesAt(src, i, "___") -> {
                val delim = src.substring(i, i + 3)
                val end = src.indexOf(delim, i + 3)
                if (end > i) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) {
                        append(src.substring(i + 3, end))
                    }
                    i = end + 3
                } else { append(src[i]); i++ }
            }

            // Bold  **text** or __text__
            matchesAt(src, i, "**") || matchesAt(src, i, "__") -> {
                val delim = src.substring(i, i + 2)
                val end = src.indexOf(delim, i + 2)
                if (end > i) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(src.substring(i + 2, end))
                    }
                    i = end + 2
                } else { append(src[i]); i++ }
            }

            // Strikethrough ~~text~~
            matchesAt(src, i, "~~") -> {
                val end = src.indexOf("~~", i + 2)
                if (end > i) {
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        append(src.substring(i + 2, end))
                    }
                    i = end + 2
                } else { append(src[i]); i++ }
            }

            // Italic  *text* or _text_
            (src[i] == '*' || src[i] == '_') && i + 1 < src.length && src[i + 1] != ' ' -> {
                val delim = src[i]
                val end = src.indexOf(delim, i + 1)
                if (end > i && end < src.length) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(src.substring(i + 1, end))
                    }
                    i = end + 1
                } else { append(src[i]); i++ }
            }

            // Inline code `text`
            src[i] == '`' -> {
                val end = src.indexOf('`', i + 1)
                if (end > i) {
                    withStyle(SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        background = codeBg,
                        color = codeFg,
                        letterSpacing = 0.3.sp
                    )) {
                        append("\u00A0${src.substring(i + 1, end)}\u00A0")
                    }
                    i = end + 1
                } else { append(src[i]); i++ }
            }

            // Link [text](url)
            src[i] == '[' -> {
                val closeBracket = src.indexOf(']', i + 1)
                if (closeBracket > i && closeBracket + 1 < src.length && src[closeBracket + 1] == '(') {
                    val closeParens = src.indexOf(')', closeBracket + 2)
                    if (closeParens > closeBracket) {
                        val label = src.substring(i + 1, closeBracket)
                        val url = src.substring(closeBracket + 2, closeParens)
                        pushStringAnnotation(tag = "URL", annotation = url)
                        withStyle(SpanStyle(
                            color = linkColor,
                            textDecoration = TextDecoration.Underline,
                            fontWeight = FontWeight.Medium
                        )) {
                            append(label)
                        }
                        pop()
                        i = closeParens + 1
                    } else { append(src[i]); i++ }
                } else { append(src[i]); i++ }
            }

            // Image ![alt](url) — show as text label
            src[i] == '!' && i + 1 < src.length && src[i + 1] == '[' -> {
                val closeBracket = src.indexOf(']', i + 2)
                if (closeBracket > i) {
                    val closeParens = src.indexOf(')', closeBracket + 2)
                    if (closeParens > closeBracket) {
                        val alt = src.substring(i + 2, closeBracket)
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = color.copy(alpha = 0.5f))) {
                            append("📷 $alt")
                        }
                        i = closeParens + 1
                    } else { append(src[i]); i++ }
                } else { append(src[i]); i++ }
            }

            // Bare URL (https://... or http://...)
            matchesAt(src, i, "https://") || matchesAt(src, i, "http://") -> {
                val urlEnd = findUrlEnd(src, i)
                val url = src.substring(i, urlEnd)
                pushStringAnnotation(tag = "URL", annotation = url)
                withStyle(SpanStyle(
                    color = linkColor,
                    textDecoration = TextDecoration.Underline
                )) {
                    append(url)
                }
                pop()
                i = urlEnd
            }

            // Normal character
            else -> { append(src[i]); i++ }
        }
    }
}

/** Find the end of a bare URL. */
private fun findUrlEnd(src: String, start: Int): Int {
    var i = start
    while (i < src.length && src[i] != ' ' && src[i] != '\n' && src[i] != '\r' && src[i] != '\t') {
        i++
    }
    // trim trailing punctuation that's unlikely part of the URL
    while (i > start && src[i - 1] in ".,;:!?)\"'") i--
    return i
}

private fun matchesAt(s: String, i: Int, sub: String): Boolean =
    i + sub.length <= s.length && s.substring(i, i + sub.length) == sub

// ═══════════════════════════════════════════════════════════════════
//  Code block card
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun CodeBlockCard(language: String, code: String) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme

    // Code block uses surfaceContainerHighest as background (darkest tonal surface)
    // so it visually "pops" from the chat background in both light and dark mode.
    val codeBackground  = scheme.surfaceContainerHighest
    val codeHeaderBg    = scheme.surfaceContainer
    val codeLangColor   = scheme.onSurfaceVariant
    val codeTextColor   = scheme.onSurface
    val codeCopiedColor = scheme.primary

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = codeBackground,
        shadowElevation = 2.dp
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(codeHeaderBg)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language.ifBlank { "text" }.lowercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = codeLangColor,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp
                )
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            clipboard.setText(AnnotatedString(code))
                            scope.launch { copied = true; delay(2000); copied = false }
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        if (copied) Icons.Default.Done else Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = if (copied) codeCopiedColor else codeLangColor
                    )
                    Text(
                        if (copied) "Copied!" else "Copy",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (copied) codeCopiedColor else codeLangColor
                    )
                }
            }

            // Code body
            Box(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(14.dp)
            ) {
                Text(
                    text = code,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = codeTextColor
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
//  Table card
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun TableCard(
    headers: List<String>,
    rows: List<List<String>>,
    textColor: Color,
    scheme: ColorScheme
) {
    val colCount = headers.size

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = scheme.surfaceContainerLow,
        tonalElevation = 1.dp
    ) {
        Column {
            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(scheme.surfaceContainerHigh)
                    .padding(vertical = 10.dp)
            ) {
                headers.forEach { h ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp)
                    ) {
                        InlineText(
                            text = h,
                            color = textColor,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.3f))

            // Data rows
            rows.forEachIndexed { idx, row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (idx % 2 == 1) Modifier.background(scheme.surfaceContainerLowest)
                            else Modifier
                        )
                        .padding(vertical = 8.dp)
                ) {
                    // Pad row to match column count
                    for (c in 0 until colCount) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp)
                        ) {
                            InlineText(
                                text = row.getOrElse(c) { "" },
                                color = textColor.copy(alpha = 0.85f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                if (idx < rows.lastIndex) {
                    HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.12f))
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
//  Block parser
// ═══════════════════════════════════════════════════════════════════

private fun parseBlocks(text: String): List<MdBlock> {
    val result = mutableListOf<MdBlock>()
    val lines = text.lines()
    var i = 0

    while (i < lines.size) {
        val raw = lines[i]
        val trimmed = raw.trim()

        when {
            // Empty line — skip
            trimmed.isEmpty() -> { i++ }

            // Fenced code block
            trimmed.startsWith("```") -> {
                val lang = trimmed.removePrefix("```").trim()
                val buf = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    buf.add(lines[i]); i++
                }
                result += MdBlock.CodeBlock(lang, buf.joinToString("\n"))
                i++ // skip closing ```
            }

            // Heading
            HEADING_RE.matches(trimmed) -> {
                val m = HEADING_RE.find(trimmed)!!
                result += MdBlock.Heading(m.groupValues[1].length, m.groupValues[2])
                i++
            }

            // Horizontal rule  (---, ***, ___)
            HR_RE.matches(trimmed) -> {
                result += MdBlock.HorizontalRule; i++
            }

            // Blockquote
            BQ_RE.matches(trimmed) -> {
                val bqLines = mutableListOf<String>()
                while (i < lines.size) {
                    val l = lines[i].trim()
                    val m = BQ_RE.find(l)
                    if (m != null) { bqLines += m.groupValues[1]; i++ }
                    else if (l.isEmpty() || l == ">") { i++ } // skip blank bq lines
                    else break
                }
                result += MdBlock.BlockQuote(bqLines.joinToString(" "))
            }

            // Table  —  line starts with | and has at least 2 |
            trimmed.startsWith("|") && trimmed.count { it == '|' } >= 3 -> {
                val tableLines = mutableListOf<String>()
                while (i < lines.size && lines[i].trim().startsWith("|")) {
                    val l = lines[i].trim()
                    if (!TABLE_SEP_RE.matches(l)) tableLines += l
                    i++
                }
                if (tableLines.size >= 2) {
                    val splitRow = { r: String -> r.split("|").map { it.trim() }.filter { it.isNotEmpty() } }
                    result += MdBlock.Table(splitRow(tableLines[0]), tableLines.drop(1).map { splitRow(it) })
                } else if (tableLines.size == 1) {
                    result += MdBlock.Paragraph(tableLines[0].replace("|", " ").trim())
                }
            }

            // Skip standalone table separator rows
            TABLE_SEP_RE.matches(trimmed) -> { i++ }

            // Task list item
            TASK_RE.matches(raw) -> {
                val tasks = mutableListOf<TaskEntry>()
                while (i < lines.size && TASK_RE.matches(lines[i])) {
                    val m = TASK_RE.find(lines[i])!!
                    tasks += TaskEntry(m.groupValues[2], m.groupValues[1].lowercase() == "x")
                    i++
                }
                result += MdBlock.TaskList(tasks)
            }

            // Unordered list
            UL_RE.matches(raw) -> {
                val items = mutableListOf<ListEntry>()
                while (i < lines.size && UL_RE.matches(lines[i])) {
                    val m = UL_RE.find(lines[i])!!
                    items += ListEntry(m.groupValues[2], m.groupValues[1].length / 2)
                    i++
                }
                result += MdBlock.UnorderedList(items)
            }

            // Ordered list
            OL_RE.matches(raw) -> {
                val items = mutableListOf<ListEntry>()
                while (i < lines.size && OL_RE.matches(lines[i])) {
                    val m = OL_RE.find(lines[i])!!
                    items += ListEntry(m.groupValues[2], m.groupValues[1].length / 2)
                    i++
                }
                result += MdBlock.OrderedList(items)
            }

            // Fall-through: paragraph
            else -> {
                // Gather consecutive non-blank, non-special lines
                val paraLines = mutableListOf<String>()
                while (i < lines.size) {
                    val l = lines[i]
                    val t = l.trim()
                    if (t.isEmpty() || t.startsWith("```") || HEADING_RE.matches(t) ||
                        HR_RE.matches(t) || BQ_RE.matches(t) ||
                        (t.startsWith("|") && t.count { it == '|' } >= 3) ||
                        TABLE_SEP_RE.matches(t) || TASK_RE.matches(l) ||
                        UL_RE.matches(l) || OL_RE.matches(l)
                    ) break
                    paraLines += t
                    i++
                }
                if (paraLines.isNotEmpty()) {
                    result += MdBlock.Paragraph(paraLines.joinToString("\n"))
                }
            }
        }
    }
    return result
}

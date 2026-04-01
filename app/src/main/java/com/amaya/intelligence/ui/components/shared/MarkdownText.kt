@file:Suppress("DEPRECATION")

package com.amaya.intelligence.ui.components.shared

import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
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
    fontSize: androidx.compose.ui.unit.TextUnit = 16.sp,
    lineHeight: androidx.compose.ui.unit.TextUnit = 24.sp,
    onLocalhostLinkClick: ((String) -> Unit)? = null,
    enableFileReferenceIcons: Boolean = false
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
                        compact  = compact,
                        onLocalhostLinkClick = onLocalhostLinkClick,
                        enableFileReferenceIcons = enableFileReferenceIcons
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
                        compact = compact,
                        onLocalhostLinkClick = onLocalhostLinkClick,
                        enableFileReferenceIcons = enableFileReferenceIcons
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
                                    style = bodyStyle, compact = compact, onLocalhostLinkClick = onLocalhostLinkClick, enableFileReferenceIcons = enableFileReferenceIcons)
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
                                    style = bodyStyle, compact = compact, onLocalhostLinkClick = onLocalhostLinkClick, enableFileReferenceIcons = enableFileReferenceIcons)
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
                                    style = bodyStyle, compact = compact, onLocalhostLinkClick = onLocalhostLinkClick, enableFileReferenceIcons = enableFileReferenceIcons)
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
                            compact = compact, onLocalhostLinkClick = onLocalhostLinkClick, enableFileReferenceIcons = enableFileReferenceIcons)
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
                                                modifier = Modifier.weight(1f), compact = false, onLocalhostLinkClick = onLocalhostLinkClick, enableFileReferenceIcons = enableFileReferenceIcons)
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

// ═══════════════════════════════════════════════════════════════════
//  Inline segment model — for mixed text + file-icon rendering
// ═══════════════════════════════════════════════════════════════════

private sealed class InlineSegment {
    /** A run of styled/annotated text rendered with ClickableText. */
    data class PlainText(val annotated: AnnotatedString) : InlineSegment()
    /** A [label](file:///...) link that gets a file-type icon. */
    data class FileLink(val label: String, val url: String, val filePath: String) : InlineSegment()
}

// ═══════════════════════════════════════════════════════════════════
//  InlineText — routes to plain or icon-aware renderer
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun InlineText(
    text: String,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onLocalhostLinkClick: ((String) -> Unit)? = null,
    enableFileReferenceIcons: Boolean = false
) {
    val scheme = MaterialTheme.colorScheme
    val uriHandler = LocalUriHandler.current

    if (enableFileReferenceIcons && text.contains("](file:///")) {
        // Use segment-based rendering to show file icons inline
        val context = LocalContext.current
        val assetNames = remember(context) { loadFileTypeIconAssetNames(context) }
        val segments = remember(text, color, scheme.primary, scheme.surfaceVariant, scheme.onSurface) {
            parseInlineSegments(text, color, scheme.primary, scheme.surfaceVariant, scheme.onSurface)
        }
        InlineContentRenderer(
            segments = segments,
            style = style,
            color = color,
            linkColor = scheme.primary,
            compact = compact,
            assetNames = assetNames,
            modifier = modifier,
            uriHandler = uriHandler,
            onLocalhostLinkClick = onLocalhostLinkClick
        )
    } else {
        val annotated = remember(text, color) {
            parseInline(text, color, scheme.primary, scheme.surfaceVariant, scheme.onSurface)
        }
        ClickableText(
            text = annotated,
            style = style.copy(color = color),
            modifier = modifier,
            onClick = { offset ->
                val localhostAnnotations = annotated.getStringAnnotations(tag = "LOCALHOST", start = offset, end = offset)
                val urlAnnotations = annotated.getStringAnnotations(tag = "URL", start = offset, end = offset)
                localhostAnnotations.firstOrNull()?.let { onLocalhostLinkClick?.invoke(it.item) }
                urlAnnotations.firstOrNull()?.let { uriHandler.openUri(it.item) }
            }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
//  InlineContentRenderer — renders segments with optional file icons
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun InlineContentRenderer(
    segments: List<InlineSegment>,
    style: TextStyle,
    color: Color,
    linkColor: Color,
    compact: Boolean,
    assetNames: Set<String>,
    modifier: Modifier = Modifier,
    uriHandler: androidx.compose.ui.platform.UriHandler,
    onLocalhostLinkClick: ((String) -> Unit)? = null
) {
    val iconSize = if (compact) 13.dp else 15.dp
    val iconSpacing = if (compact) 3.dp else 4.dp
    val chipSpacing = if (compact) 4.dp else 6.dp

    Column(modifier = modifier) {
        // Group consecutive plain-text segments; each FileLink breaks into its own row context.
        // We render each run of (optional plain text)(file link chips)(optional plain text) as a Column of rows.
        var i = 0
        while (i < segments.size) {
            val seg = segments[i]
            when (seg) {
                is InlineSegment.PlainText -> {
                    if (seg.annotated.isNotEmpty()) {
                        ClickableText(
                            text = seg.annotated,
                            style = style.copy(color = color),
                            onClick = { offset ->
                                seg.annotated.getStringAnnotations("LOCALHOST", offset, offset)
                                    .firstOrNull()?.let { onLocalhostLinkClick?.invoke(it.item) }
                                seg.annotated.getStringAnnotations("URL", offset, offset)
                                    .firstOrNull()?.let { uriHandler.openUri(it.item) }
                            }
                        )
                    }
                    i++
                }
                is InlineSegment.FileLink -> {
                    // Collect consecutive file links into one row of chips
                    val fileLinks = mutableListOf<InlineSegment.FileLink>()
                    while (i < segments.size && segments[i] is InlineSegment.FileLink) {
                        fileLinks += segments[i] as InlineSegment.FileLink
                        i++
                    }
                    Row(
                        modifier = Modifier.padding(vertical = if (compact) 1.dp else 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(chipSpacing),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        fileLinks.forEach { link ->
                            val assetName = remember(link.filePath, assetNames) {
                                resolveFileTypeIconAssetName(link.filePath, assetNames)
                            }
                            Row(
                                modifier = Modifier
                                    .clickable { uriHandler.openUri(link.url) },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(iconSpacing)
                            ) {
                                if (assetName != null) {
                                    FileTypeHeaderIcon(
                                        filePath = link.filePath,
                                        resolvedAssetName = assetName,
                                        modifier = Modifier.size(iconSize)
                                    )
                                }
                                Text(
                                    text = link.label,
                                    style = style.copy(
                                        color = linkColor,
                                        fontWeight = FontWeight.Medium,
                                        textDecoration = TextDecoration.None
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun parseInline(
    text: String,
    color: Color,
    linkColor: Color,
    codeBg: Color,
    codeFg: Color
): AnnotatedString = buildAnnotatedString {
    parseInlineToBuilder(text, this, color, linkColor, codeBg, codeFg)
}

private fun parseInlineToBuilder(
    src: String,
    builder: AnnotatedString.Builder,
    color: Color,
    linkColor: Color,
    codeBg: Color,
    codeFg: Color
) {
    var i = 0
    with(builder) {
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
                            parseInlineToBuilder(src.substring(i + 3, end), builder, color, linkColor, codeBg, codeFg)
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
                            parseInlineToBuilder(src.substring(i + 2, end), builder, color, linkColor, codeBg, codeFg)
                        }
                        i = end + 2
                    } else { append(src[i]); i++ }
                }

                // Strikethrough ~~text~~
                matchesAt(src, i, "~~") -> {
                    val end = src.indexOf("~~", i + 2)
                    if (end > i) {
                        withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                            parseInlineToBuilder(src.substring(i + 2, end), builder, color, linkColor, codeBg, codeFg)
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
                            parseInlineToBuilder(src.substring(i + 1, end), builder, color, linkColor, codeBg, codeFg)
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
                                textDecoration = TextDecoration.None,
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

                // Detect localhost URLs before bare URLs
                matchesLocalhostLink(src, i) != null -> {
                    val matchResult = matchesLocalhostLink(src, i)!!
                    val fullMatch = matchResult.value
                    val isUrlMatch = LOCALHOST_URL_REGEX.find(fullMatch) != null
                    
                    android.util.Log.d("MarkdownText", "Localhost link detected: $fullMatch")

                    val host: String
                    val portGroup: String?
                    val pathGroup: String?

                    if (isUrlMatch) {
                        // URL pattern: (https?)://(host)(:port)?(/path)?
                        // Groups: 0=whole, 1=protocol, 2=host, 3=port, 4=path
                        host = matchResult.groupValues[2]
                        portGroup = matchResult.groupValues[3].ifEmpty { null }
                        pathGroup = matchResult.groupValues[4].ifEmpty { null }
                    } else {
                        // Plain pattern: (host)(:port)?(/path)?
                        // Groups: 0=whole, 1=host, 2=port, 3=path
                        host = matchResult.groupValues[1]
                        portGroup = matchResult.groupValues[2].ifEmpty { null }
                        pathGroup = matchResult.groupValues[3].ifEmpty { null }
                    }

                    val (annotation, displayText) = parseLocalhostAnnotation(host, portGroup, pathGroup, isUrlMatch)
                    android.util.Log.d("MarkdownText", "Annotation: $annotation, Display: $displayText")
                    
                    pushStringAnnotation(tag = "LOCALHOST", annotation = annotation)
                    withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.None, fontWeight = FontWeight.Medium)) {
                        append(displayText)
                    }
                    pop()
                    i += fullMatch.length
                }

                // Bare URL (https://... or http://...)
                matchesAt(src, i, "https://") || matchesAt(src, i, "http://") -> {
                    val urlEnd = findUrlEnd(src, i)
                    val url = src.substring(i, urlEnd)
                    pushStringAnnotation(tag = "URL", annotation = url)
                    withStyle(SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.None
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

private val LOCALHOST_HOSTS = "localhost|127\\.0\\.0\\.1|0\\.0\\.0\\.0|::1"
private val LOCALHOST_URL_REGEX = Regex("(https?)://($LOCALHOST_HOSTS)(:\\d{1,5})?(/[^\\s]*)?", RegexOption.IGNORE_CASE)
// Require a port for plain localhost/IP to avoid matching filenames or casual mentions
private val LOCALHOST_PLAIN_REGEX = Regex("($LOCALHOST_HOSTS)(:\\d{1,5})(/[^\\s]*)?", RegexOption.IGNORE_CASE)
private const val LOCALHOST_URL_DELIMITER = "\u0000LOCALHOST\u0000"

private fun matchesLocalhostLink(src: String, i: Int): MatchResult? {
    val urlMatch = LOCALHOST_URL_REGEX.find(src, i)
    if (urlMatch != null && urlMatch.range.first == i) return urlMatch
    val plainMatch = LOCALHOST_PLAIN_REGEX.find(src, i)
    if (plainMatch != null && plainMatch.range.first == i) return plainMatch
    return null
}

private fun parseLocalhostAnnotation(host: String, port: String?, path: String?, isHttpScheme: Boolean): Pair<String, String> {
    val actualHost = when (host.lowercase()) {
        "localhost", "127.0.0.1", "0.0.0.0", "::1" -> "LOCALHOST_IP_PLACEHOLDER"
        else -> host
    }
    
    // Extract port number without colon prefix
    val actualPort = port?.removePrefix(":")?.ifEmpty { null }
    val actualPath = path?.ifEmpty { null } ?: "/"
    
    val protocol = if (isHttpScheme && host.startsWith("https")) "https" else "http"
    
    // Build full URL with IP placeholder for annotation
    val fullUrl = if (actualPort != null) {
        "$protocol://$actualHost:$actualPort$actualPath"
    } else {
        "$protocol://$actualHost$actualPath"
    }
    
    // Display text should show original host (localhost), not the placeholder
    val displayHost = host.lowercase()
    val displayText = if (actualPort != null) {
        if (isHttpScheme) "$protocol://$displayHost:$actualPort$actualPath" else "$displayHost:$actualPort$actualPath"
    } else {
        if (isHttpScheme) "$protocol://$displayHost$actualPath" else "$displayHost$actualPath"
    }
    
    return "$LOCALHOST_URL_DELIMITER$fullUrl$LOCALHOST_URL_DELIMITER" to displayText
}

// ═══════════════════════════════════════════════════════════════════
//  parseInlineSegments — splits text into PlainText / FileLink segments
// ═══════════════════════════════════════════════════════════════════

/**
 * Walks the same inline syntax as [parseInlineToBuilder] but emits [InlineSegment]s.
 * When a `[label](file:///...)` link is encountered it becomes a [InlineSegment.FileLink]
 * so the caller can render a file-type icon alongside it.
 * All other content is accumulated into [InlineSegment.PlainText] spans.
 */
private fun parseInlineSegments(
    text: String,
    color: Color,
    linkColor: Color,
    codeBg: Color,
    codeFg: Color
): List<InlineSegment> {
    val result = mutableListOf<InlineSegment>()
    val src = text
    var i = 0
    var builder = AnnotatedString.Builder()

    fun flushBuilder() {
        val a = builder.toAnnotatedString()
        if (a.isNotEmpty()) result += InlineSegment.PlainText(a)
        builder = AnnotatedString.Builder()
    }

    while (i < src.length) {
        // File link: [label](file:///...)
        if (src[i] == '[') {
            val closeBracket = src.indexOf(']', i + 1)
            if (closeBracket > i && closeBracket + 1 < src.length && src[closeBracket + 1] == '(') {
                val closeParens = src.indexOf(')', closeBracket + 2)
                if (closeParens > closeBracket) {
                    val label = src.substring(i + 1, closeBracket)
                    val url   = src.substring(closeBracket + 2, closeParens)
                    if (url.startsWith("file:///")) {
                        // Decode percent-encoded URI to get a real file path for icon resolution
                        val filePath = try {
                            Uri.decode(url.removePrefix("file:///")).let { p ->
                                if (p.getOrNull(1) == ':') p.replace('/', '\\') else "/$p"
                            }
                        } catch (_: Exception) { url.removePrefix("file:///") }
                        flushBuilder()
                        result += InlineSegment.FileLink(label, url, filePath)
                        i = closeParens + 1
                        continue
                    }
                }
            }
        }
        // For everything else, delegate character-by-character to parseInlineToBuilder logic.
        // We pass a temporary builder just for this character run; simpler: parse entire tail
        // as plain text once we know no more file links exist, or just handle char-by-char.
        // For simplicity and zero-duplication: re-use the existing parser on the non-file-link
        // portions by processing one character at a time into the current builder.
        parseInlineSingleChar(src, i, builder, color, linkColor, codeBg, codeFg).let { newI ->
            i = newI
        }
    }
    flushBuilder()
    return result
}

/**
 * Processes one inline token starting at [i] in [src], appends to [builder],
 * and returns the new index after the token.
 * This is a lightweight character-dispatch that mirrors [parseInlineToBuilder].
 */
private fun parseInlineSingleChar(
    src: String,
    i: Int,
    builder: AnnotatedString.Builder,
    color: Color,
    linkColor: Color,
    codeBg: Color,
    codeFg: Color
): Int {
    // Find the next '[' after i — everything before it is safe to parse as a chunk.
    val nextBracket = src.indexOf('[', i)
    val chunkEnd = if (nextBracket == -1) src.length else nextBracket
    if (chunkEnd > i) {
        // No '[' in this range → parse safely as a chunk (bold, italic, code, URLs etc.)
        parseInlineToBuilder(src.substring(i, chunkEnd), builder, color, linkColor, codeBg, codeFg)
        return chunkEnd
    }
    // We are AT a '['. Find the matching ](…) to pass the full link token as one chunk,
    // so parseInlineToBuilder can handle it correctly (e.g. [label](https://...)).
    val closeBracket = src.indexOf(']', i + 1)
    if (closeBracket > i && closeBracket + 1 < src.length && src[closeBracket + 1] == '(') {
        val closeParens = src.indexOf(')', closeBracket + 2)
        if (closeParens > closeBracket) {
            // Full [label](url) token — pass to parseInlineToBuilder in one shot
            parseInlineToBuilder(src.substring(i, closeParens + 1), builder, color, linkColor, codeBg, codeFg)
            return closeParens + 1
        }
    }
    // Bare '[' with no matching structure — just emit the character
    parseInlineToBuilder("[", builder, color, linkColor, codeBg, codeFg)
    return i + 1
}

// ═══════════════════════════════════════════════════════════════════
//  Regex helper for extracting file paths from markdown text
// ═══════════════════════════════════════════════════════════════════

private val MARKDOWN_FILE_LINK_RE = Regex("\\[([^\\]]+)\\]\\((file:///[^)]+)\\)")

/**
 * Extracts local file paths from `[label](file:///...)` markdown links.
 * Used by [ChatMessageList] to prefetch file-type icons.
 */
internal fun extractMarkdownFilePaths(text: String): List<String> {
    return MARKDOWN_FILE_LINK_RE.findAll(text).mapNotNull { match ->
        val url = match.groupValues[2]
        try {
            Uri.decode(url.removePrefix("file:///")).let { p ->
                if (p.getOrNull(1) == ':') p.replace('/', '\\') else "/$p"
            }
        } catch (_: Exception) { null }
    }.toList()
}

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

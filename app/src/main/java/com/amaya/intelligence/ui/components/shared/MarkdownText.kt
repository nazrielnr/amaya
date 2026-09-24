@file:Suppress("DEPRECATION")

package com.amaya.intelligence.ui.components.shared

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.amaya.intelligence.ui.components.markdown.ListEntry
import com.amaya.intelligence.ui.components.markdown.MdBlock
import com.amaya.intelligence.ui.components.markdown.parseBlocksCached
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ═══════════════════════════════════════════════════════════════════
//  Block-level AST
// ═══════════════════════════════════════════════════════════════════

//  Type scale
// ═══════════════════════════════════════════════════════════════════

/**
 * Headings are derived from the *effective body size* rather than from
 * `MaterialTheme.typography`, for two reasons: the app's `PremiumTypography` leaves
 * `headlineLarge`/`headlineSmall`/`titleSmall` undefined — so `#` fell through to the
 * Material default of 32sp against 16sp body text, while `######` landed at 14sp, i.e.
 * *smaller* than body — and callers that pass a custom `fontSize` used to get headings
 * that ignored it entirely.
 */
// An even ladder — one consistent step per level, wide enough that neighbouring levels are
// still tellable apart. Against a 16sp body that lands on 24 / 22 / 20 / 18 / 17 / 16.
private val HEADING_SCALE = floatArrayOf(1.5f, 1.375f, 1.25f, 1.125f, 1.0625f, 1f)
private val HEADING_SCALE_COMPACT = floatArrayOf(1.36f, 1.27f, 1.18f, 1.09f, 1.05f, 1f)

private fun headingStyle(base: TextStyle, level: Int, compact: Boolean): TextStyle {
    val scale = (if (compact) HEADING_SCALE_COMPACT else HEADING_SCALE)[(level - 1).coerceIn(0, 5)]
    val size = base.fontSize * scale
    return base.copy(
        fontSize      = size,
        lineHeight    = size * 1.35f,
        fontWeight    = FontWeight.SemiBold,
        letterSpacing = 0.sp
    )
}

/** Weight used for `**bold**` and headings — SemiBold, not Bold, so it sits closer to body text. */
private val MD_BOLD = FontWeight.SemiBold

// Inline `code` carries no background — it is distinguished by its letterforms alone: a
// monospace face set lighter than the body it sits in, with the tracking opened up slightly
// so the fixed-width glyphs read as deliberate rather than cramped.
private val MD_CODE_WEIGHT = FontWeight.Light
private val MD_CODE_TRACKING = 0.6.sp

/** `==mark==` tint. Amber reads as a highlighter in both light and dark surfaces. */
private val MARK_TINT = Color(0xFFFFD60A)

// The monochrome scheme makes `primary` almost indistinguishable from body text, so links
// get a dedicated accent picked against the *text* colour: light text implies a dark (or
// blue-bubble) background, dark text implies a light one.
private val LINK_ON_DARK  = Color(0xFF64D2FF)
private val LINK_ON_LIGHT = Color(0xFF0A84FF)

private fun linkColorFor(textColor: Color): Color =
    if (textColor.luminance() > 0.6f) LINK_ON_DARK else LINK_ON_LIGHT

private fun codeFontSizeFor(style: TextStyle): TextUnit =
    if (style.fontSize.isSpecified) style.fontSize * 0.92f else 13.sp

/**
 * Long code spans are usually file paths or URLs, which contain no space and so lay out as one
 * unbreakable run — it either overflowed the bubble or pushed the whole line onto its own row.
 * Zero-width spaces after path separators give the layout somewhere to wrap without adding any
 * visible character.
 */
private fun codeWithSoftBreaks(code: String): String {
    if (code.length <= 20 || code.any { it.isWhitespace() }) return code
    return buildString(code.length + 8) {
        code.forEachIndexed { idx, c ->
            append(c)
            if (idx < code.lastIndex && (c == '/' || c == '\\' || c == '.' || c == '_' || c == '-')) {
                append('\u200B')
            }
        }
    }
}

/**
 * Ordered lists are numbered per indent level, so nested lists restart at their own first
 * number instead of continuing the parent's run, and a list that starts at `3.` keeps its
 * numbering instead of being renumbered from 1.
 */
private fun orderedListNumbers(items: List<ListEntry>): List<Int> {
    val counters = HashMap<Int, Int>()
    var prevIndent = -1
    return items.map { item ->
        when {
            item.indent > prevIndent -> counters[item.indent] = item.number
            else -> {
                if (item.indent < prevIndent) {
                    counters.keys.filter { it > item.indent }.forEach { counters.remove(it) }
                }
                counters[item.indent] = counters[item.indent]?.plus(1) ?: item.number
            }
        }
        prevIndent = item.indent
        counters[item.indent] ?: item.number
    }
}

private fun bulletGlyph(indent: Int): String = when (indent % 3) {
    0    -> "•"
    1    -> "◦"
    else -> "▪"
}

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
    enableFileReferenceIcons: Boolean = false,
    /**
     * Overrides the gap between markdown blocks without touching type size. Lets text
     * keep normal reading metrics while adopting a tighter vertical rhythm — e.g. body
     * text sitting inside a card, between blocks that are spaced much more closely than
     * the conversation is.
     */
    blockSpacing: Dp? = null
) {
    val blocks = remember(text) { parseBlocksCached(text, compact) }
    val scheme   = MaterialTheme.colorScheme
    val typo     = MaterialTheme.typography
    val spacing  = blockSpacing ?: if (compact) 4.dp else 12.dp

    // One body style for every text block. Weight and tracking are pinned here (Normal,
    // 0.sp) instead of inherited from `PremiumTypography.bodyMedium`, so markdown keeps
    // stable reading metrics no matter how the theme's body style is tuned — and the jump
    // to bold never drifts.
    val bodyStyle = if (compact) {
        typo.bodySmall.copy(
            fontSize = 11.sp, lineHeight = 16.sp,
            fontWeight = FontWeight.Normal, letterSpacing = 0.sp
        )
    } else {
        typo.bodyMedium.copy(
            fontSize = fontSize, lineHeight = lineHeight,
            fontWeight = FontWeight.Normal, letterSpacing = 0.sp
        )
    }
    val listIndent = if (compact) 12.dp else 16.dp
    val listGap    = if (compact) 2.dp else 5.dp

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(spacing)) {
        blocks.forEach { block ->
            when (block) {

                is MdBlock.Heading -> {
                    // No rule under h1/h2 — block spacing already separates sections, and an
                    // underline is a document convention rather than a chat one. The lead-in
                    // gap shrinks with depth, so rank reads from the spacing as well as the
                    // size — which is what keeps the lower levels distinguishable once their
                    // sizes converge on the body.
                    val headingLead = when (block.level) {
                        1    -> if (compact) 8.dp else 16.dp
                        2    -> if (compact) 6.dp else 13.dp
                        3    -> if (compact) 5.dp else 10.dp
                        else -> if (compact) 3.dp else 7.dp
                    }
                    InlineText(
                        text     = block.text,
                        color    = color,
                        style    = headingStyle(bodyStyle, block.level, compact),
                        modifier = Modifier.padding(top = headingLead),
                        compact  = compact,
                        onLocalhostLinkClick = onLocalhostLinkClick,
                        enableFileReferenceIcons = enableFileReferenceIcons
                    )
                }

                is MdBlock.Paragraph -> {
                    InlineText(
                        text    = block.text,
                        color   = color,
                        style   = bodyStyle,
                        compact = compact,
                        onLocalhostLinkClick = onLocalhostLinkClick,
                        enableFileReferenceIcons = enableFileReferenceIcons
                    )
                }

                is MdBlock.CodeBlock -> {
                    val isLight   = !isSystemInDarkTheme()
                    val bgColor   = if (isLight) Color(0xFFF2F2F7) else Color(0xFF1C1C1E)
                    val codeColor = if (isLight) Color(0xFF3A3A3C) else Color(0xFFD1D1D6)
                    val blockBorderColor = if (isLight) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.08f)
                    Surface(
                        shape = RoundedCornerShape(if (compact) 8.dp else 10.dp),
                        color = bgColor,
                        border = BorderStroke(1.dp, blockBorderColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
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
                                    }, modifier = Modifier.size(32.dp)) {
                                        Icon(if (copied) Icons.Default.Done else Icons.Default.ContentCopy,
                                            contentDescription = "Copy code",
                                            modifier = Modifier.size(14.dp), tint = codeColor.copy(alpha = 0.6f))
                                    }
                                }
                                HorizontalDivider(color = blockBorderColor)
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
                    Column(verticalArrangement = Arrangement.spacedBy(listGap)) {
                        block.items.forEach { item ->
                            Row(
                                modifier = Modifier.padding(start = listIndent * item.indent),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    bulletGlyph(item.indent),
                                    style = bodyStyle,
                                    color = color.copy(alpha = 0.55f),
                                    modifier = Modifier.width(if (compact) 14.dp else 18.dp)
                                )
                                InlineText(text = item.text, color = color,
                                    style = bodyStyle, compact = compact, onLocalhostLinkClick = onLocalhostLinkClick, enableFileReferenceIcons = enableFileReferenceIcons)
                            }
                        }
                    }
                }

                is MdBlock.OrderedList -> {
                    val numbers = remember(block.items) { orderedListNumbers(block.items) }
                    Column(verticalArrangement = Arrangement.spacedBy(listGap)) {
                        block.items.forEachIndexed { idx, item ->
                            Row(
                                modifier = Modifier.padding(start = listIndent * item.indent),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    "${numbers.getOrElse(idx) { idx + 1 }}.",
                                    style = bodyStyle,
                                    color = color.copy(alpha = 0.55f),
                                    // widthIn, not width — a fixed column clipped "10." into the text.
                                    modifier = Modifier
                                        .widthIn(min = if (compact) 16.dp else 20.dp)
                                        .padding(end = if (compact) 3.dp else 5.dp)
                                )
                                InlineText(text = item.text, color = color,
                                    style = bodyStyle, compact = compact, onLocalhostLinkClick = onLocalhostLinkClick, enableFileReferenceIcons = enableFileReferenceIcons)
                            }
                        }
                    }
                }

                is MdBlock.TaskList -> {
                    Column(verticalArrangement = Arrangement.spacedBy(listGap)) {
                        block.items.forEach { item ->
                            Row(
                                modifier = Modifier.padding(start = listIndent * item.indent),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    if (item.checked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                    null,
                                    // Nudged down so the box sits on the first line's optical
                                    // centre now that the row is top-aligned for wrapped text.
                                    modifier = Modifier
                                        .padding(top = if (compact) 1.dp else 2.dp)
                                        .size(if (compact) 14.dp else 18.dp),
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
                    // IntrinsicSize.Min gives the Row a bounded height so the rule can fill it.
                    // Inside a LazyColumn the incoming maxHeight is Infinity, and fillMaxHeight is
                    // a no-op there — the bar collapsed to zero and the quote lost its marker.
                    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                        Box(modifier = Modifier
                            .width(if (compact) 2.dp else 3.dp)
                            .fillMaxHeight()
                            .background(color.copy(alpha = 0.28f), RoundedCornerShape(2.dp))
                        )
                        Spacer(Modifier.width(if (compact) 6.dp else 10.dp))
                        InlineText(text = block.text, color = color.copy(alpha = 0.75f),
                            style = bodyStyle,
                            compact = compact, onLocalhostLinkClick = onLocalhostLinkClick, enableFileReferenceIcons = enableFileReferenceIcons)
                    }
                }

                is MdBlock.Table -> {
                    val isLight = !isSystemInDarkTheme()
                    val tableBorderColor = if (isLight) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.08f)
                    val headerBg = scheme.surfaceContainerHigh
                    val cellBg = scheme.surfaceContainerLow
                    val baseColumnWidths = remember(block.headers, block.rows, compact) { tableColumnWidthValues(block.headers, block.rows, compact) }
                    val tablePaddingH = if (compact) 8.dp else 10.dp
                    val tablePaddingV = if (compact) 6.dp else 8.dp
                    val cellSpacing = if (compact) 6.dp else 8.dp
                    val scrollState = rememberScrollState()

                    Surface(
                        shape = RoundedCornerShape(if (compact) 8.dp else 10.dp),
                        color = cellBg,
                        border = BorderStroke(1.dp, tableBorderColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val spacingWidth = cellSpacing.value * (block.headers.size - 1).coerceAtLeast(0) + tablePaddingH.value * 2
                            val rawTableWidth = (baseColumnWidths.sum() + spacingWidth).dp
                            val tableWidth = maxOf(maxWidth, rawTableWidth)
                            val extraWidth = (tableWidth.value - rawTableWidth.value).coerceAtLeast(0f)
                            val expandable = baseColumnWidths.map { it >= if (compact) 96f else 120f }
                            val expandableCount = expandable.count { it }.takeIf { it > 0 } ?: block.headers.size.coerceAtLeast(1)
                            val columnWidths = baseColumnWidths.mapIndexed { index, width ->
                                width + if (expandable.getOrElse(index) { false } || expandable.none { it }) extraWidth / expandableCount else 0f
                            }

                            Box(modifier = Modifier.horizontalScroll(scrollState)) {
                                Column(modifier = Modifier.width(tableWidth)) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(headerBg)
                                            .padding(horizontal = tablePaddingH, vertical = tablePaddingV),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(cellSpacing)
                                    ) {
                                        block.headers.forEachIndexed { index, h ->
                                            Text(
                                                tableWrapText(h),
                                                style = if (compact) typo.labelSmall.copy(fontSize = 9.sp, lineHeight = 12.sp) else typo.labelSmall,
                                                color = color,
                                                fontWeight = MD_BOLD,
                                                modifier = Modifier.width(columnWidths[index].dp)
                                            )
                                        }
                                    }
                                    block.rows.forEachIndexed { idx, row ->
                                        HorizontalDivider(color = tableBorderColor.copy(alpha = 0.65f), thickness = 0.5.dp)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .then(if (idx % 2 == 1) Modifier.background(scheme.surfaceContainerLowest) else Modifier)
                                                .padding(horizontal = tablePaddingH, vertical = tablePaddingV),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(cellSpacing)
                                        ) {
                                            repeat(block.headers.size) { index ->
                                                val cell = row.getOrElse(index) { "" }
                                                InlineText(
                                                    tableWrapText(cell),
                                                    color = color.copy(alpha = 0.84f),
                                                    style = if (compact) typo.bodySmall.copy(fontSize = 9.sp, lineHeight = 13.sp, fontWeight = FontWeight.Normal)
                                                            else typo.bodySmall.copy(fontWeight = FontWeight.Normal),
                                                    modifier = Modifier.width(columnWidths[index].dp),
                                                    compact = compact,
                                                    onLocalhostLinkClick = onLocalhostLinkClick,
                                                    enableFileReferenceIcons = enableFileReferenceIcons
                                                )
                                            }
                                        }
                                        if (idx < block.rows.lastIndex) {
                                            HorizontalDivider(color = tableBorderColor.copy(alpha = 0.35f), thickness = 0.5.dp)
                                        }
                                    }
                                }
                            }
                        }
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

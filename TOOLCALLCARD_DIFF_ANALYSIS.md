# ToolCallCard and MessageBubble UI Diff Analysis
## HEAD~2 (5dd1207) vs HEAD (fcb876a)

---

## Summary

**CRITICAL FINDING: The ToolCallCard has been COMPLETELY REWRITTEN between HEAD~2 and HEAD.**

The old version (HEAD~2) used a **minimal exit-code style** design with simple green/red backgrounds.
The new version (HEAD) has **complex hierarchical UI** with icon pills, shimmer effects, subagent children, and result previews.

---

## 1. ToolCallCard - MAJOR REGRESSION

### OLD CODE (HEAD~2) - Lines 1135-1243

```kotlin
@Composable
fun ToolCallCard(execution: ToolExecution) {
    val isExit0 = execution.result?.trim() == "exit 0"
    val isExit1 = execution.result?.trim() == "exit 1"

    if (isExit0 || isExit1) {
        val bg = if (isExit0) Color(0xFF4CAF50).copy(alpha = 0.2f) else MaterialTheme.colorScheme.errorContainer
        val fg = if (isExit0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onErrorContainer
        Surface(
            color = bg,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
        ) {
            Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.BuildCircle, null, modifier = Modifier.size(14.dp), tint = fg)
                Spacer(Modifier.width(6.dp))
                Text(execution.result!!.trim(), style = MaterialTheme.typography.labelSmall, color = fg, fontWeight = FontWeight.SemiBold)
            }
        }
        return
    }

    var expanded by remember { mutableStateOf(false) }
    val statusColor = when (execution.status) {
        ToolStatus.PENDING -> MaterialTheme.colorScheme.outline
        ToolStatus.RUNNING -> MaterialTheme.colorScheme.primary
        ToolStatus.SUCCESS -> Color(0xFF4CAF50)
        ToolStatus.ERROR -> MaterialTheme.colorScheme.error
    }
    val containerColor = if (execution.status == ToolStatus.ERROR) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (execution.status == ToolStatus.ERROR) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp).clip(MaterialTheme.shapes.small),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { if (execution.result != null) expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.BuildCircle, null, modifier = Modifier.size(16.dp), tint = contentColor)
                Spacer(Modifier.width(8.dp))
                Text(
                    formatToolName(execution.name, execution.arguments),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                    modifier = Modifier.weight(1f)
                )
                if (execution.result != null) {
                    Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(18.dp), tint = contentColor)
                }
                if (execution.status == ToolStatus.RUNNING) {
                    Spacer(Modifier.width(8.dp))
                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = MaterialTheme.colorScheme.primary)
                }
            }
            AnimatedVisibility(visible = expanded) {
                execution.result?.let { result ->
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        HorizontalDivider(color = contentColor.copy(alpha = 0.2f), modifier = Modifier.padding(bottom = 8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
                            Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(14.dp), tint = statusColor.copy(alpha = 0.8f))
                            Spacer(Modifier.width(6.dp))
                            Text("Execution Result", style = MaterialTheme.typography.labelSmall, color = contentColor)
                        }
                        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Text(
                                result.take(3000),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                        if (result.length > 3000) {
                            Text("... (${result.length - 3000} more chars)", style = MaterialTheme.typography.labelSmall, color = contentColor, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
        }
    }
}
```

### NEW CODE (HEAD) - Lines 1145-1363

```kotlin
@Composable
fun ToolCallCard(
    execution: ToolExecution
) {
    var expanded by remember { mutableStateOf(false) }
    val isDark    = isSystemInDarkTheme()

    // -- Colors -----------------------------------------------------------
    val iosGreen = MaterialTheme.colorScheme.primary
    val iosBlue  = MaterialTheme.colorScheme.secondary
    val iosRed   = MaterialTheme.colorScheme.error

    val statusColor = when (execution.status) {
        ToolStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        ToolStatus.RUNNING -> iosBlue
        ToolStatus.SUCCESS -> iosGreen
        ToolStatus.ERROR   -> iosRed
    }
    val statusIcon = when (execution.status) {
        ToolStatus.PENDING -> Icons.Default.RadioButtonUnchecked
        ToolStatus.RUNNING -> Icons.Default.Autorenew
        ToolStatus.SUCCESS -> Icons.Default.CheckCircle
        ToolStatus.ERROR   -> Icons.Default.Cancel
    }

    // -- Tool icon ---------------------------------------------------------
    val toolIcon = when {
        execution.name.contains("read",     ignoreCase = true) -> Icons.Default.Description
        execution.name.contains("write",    ignoreCase = true) -> Icons.Default.Edit
        execution.name.contains("list",     ignoreCase = true) -> Icons.Default.FolderOpen
        execution.name.contains("shell",    ignoreCase = true) -> Icons.Default.Terminal
        execution.name.contains("search",   ignoreCase = true) -> Icons.Default.Search
        execution.name.contains("delete",   ignoreCase = true) -> Icons.Default.Delete
        execution.name.contains("create",   ignoreCase = true) -> Icons.Default.CreateNewFolder
        execution.name.contains("transfer", ignoreCase = true) -> Icons.Default.ContentCopy
        execution.name.contains("todo",     ignoreCase = true) -> Icons.Default.CheckCircle
        execution.name.contains("memory",   ignoreCase = true) -> Icons.Default.Psychology
        execution.name.contains("remind",   ignoreCase = true) -> Icons.Default.Alarm
        execution.name.contains("invoke",   ignoreCase = true) -> Icons.Default.AccountTree
        execution.name.contains("find",     ignoreCase = true) -> Icons.Default.FindInPage
        execution.name.contains("undo",     ignoreCase = true) -> Icons.Default.Undo
        execution.name.startsWith("mcp__")                     -> Icons.Default.Extension
        else                                                    -> Icons.Default.Terminal
    }

    // -- Shimmer (identical to Thinking.. / TodoBar technique) -------------
    val shimmerTransition = rememberInfiniteTransition(label = "tool_shimmer")
    val shimmerOffset by shimmerTransition.animateFloat(
        initialValue = -500f,
        targetValue  = 800f,
        animationSpec = infiniteRepeatable(
            animation  = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "tool_shimmer_x"
    )
    val baseTextColor = MaterialTheme.colorScheme.onSurface
    val shimmerBrush  = Brush.linearGradient(
        colors = listOf(baseTextColor.copy(alpha = 0.3f), iosBlue, baseTextColor.copy(alpha = 0.3f)),
        start  = Offset(shimmerOffset, 0f),
        end    = Offset(shimmerOffset + 400f, 0f)
    )

    // -- Container color ---------------------------------------------------
    val bgColor = when (execution.status) {
        ToolStatus.ERROR   ->
            if (isDark) iosRed.copy(alpha = 0.10f)
            else iosRed.copy(alpha = 0.06f)
        ToolStatus.SUCCESS ->
            if (isDark) MaterialTheme.colorScheme.surfaceContainerLow
            else MaterialTheme.colorScheme.surfaceContainerLowest
        ToolStatus.RUNNING ->
            if (isDark) iosBlue.copy(alpha = 0.08f)
            else iosBlue.copy(alpha = 0.04f)
        ToolStatus.PENDING ->
            MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f)
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = bgColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // -- Header row – tap to expand/collapse -----------------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (execution.result != null)
                            Modifier.clickable { expanded = !expanded }
                        else Modifier
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // -- Tool icon pill ----------------------------------------
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusColor.copy(alpha = if (isDark) 0.18f else 0.12f),
                    modifier = Modifier.size(30.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = toolIcon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = statusColor
                        )
                    }
                }

                // -- Tool name – shimmer when RUNNING ---------------------
                val toolLabel = formatToolName(execution.name, execution.arguments)
                if (execution.status == ToolStatus.RUNNING) {
                    Text(
                        text = toolLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = baseTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .drawWithContent {
                                drawContent()
                                drawRect(brush = shimmerBrush, blendMode = BlendMode.SrcAtop)
                            }
                    )
                } else {
                    Text(
                        text = toolLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                // -- Status icon --------------------------------------------
                Icon(
                    imageVector = statusIcon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = statusColor
                )

                // -- Expand chevron – only when done & has content ---------
                val canExpand = (execution.status == ToolStatus.SUCCESS || execution.status == ToolStatus.ERROR)
                    && (execution.result != null || execution.children.isNotEmpty())
                if (canExpand) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp
                                      else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier
                            .size(14.dp)
                            .clickable { expanded = !expanded },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
            }

            // -- Subagent children -----------------------------------------
            if (execution.name == "invoke_subagents" && execution.children.isNotEmpty()) {
                AnimatedVisibility(
                    visible = execution.status == ToolStatus.RUNNING || expanded,
                    enter = expandVertically(tween(200)) + fadeIn(tween(180)),
                    exit  = shrinkVertically(tween(160)) + fadeOut(tween(140))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 10.dp, end = 10.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        execution.children.forEach { child ->
                            SubagentChildCard(
                                child = child,
                                isDark = isDark,
                                iosGreen = iosGreen,
                                iosBlue = iosBlue,
                                iosRed = iosRed,
                                shimmerBrush = shimmerBrush
                            )
                        }
                    }
                }
            }

            // -- Expanded result (non-subagent) ----------------------------
            AnimatedVisibility(
                visible = expanded && execution.name != "invoke_subagents",
                enter = expandVertically(tween(200, easing = FastOutSlowInEasing)) + fadeIn(tween(160)),
                exit  = shrinkVertically(tween(160, easing = FastOutSlowInEasing)) + fadeOut(tween(120))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                    ToolResultPreview(
                        toolName  = execution.name,
                        arguments = execution.arguments,
                        result    = execution.result ?: "",
                        isDark    = isDark
                    )
                }
            }
        }
    }
}
```

---

## Key Changes in ToolCallCard

| Feature | HEAD~2 | HEAD | Status |
|---------|--------|------|--------|
| **Exit code shortcut** | YES (isExit0/isExit1) | REMOVED | ❌ REGRESSION |
| **Simple minimal UI** | YES | NO | ❌ REGRESSION |
| **Tool icon selection** | NO | YES (14-case when statement) | ✅ ADDED |
| **Icon pill container** | NO | YES (30.dp Surface) | ✅ ADDED |
| **Shimmer effect** | NO | YES (infinite gradient) | ✅ ADDED |
| **Status icons** | Simple icons | Dynamic icons per status | ✅ IMPROVED |
| **Subagent children support** | NO | YES | ✅ ADDED |
| **ToolResultPreview** | Inline result display | Delegated to ToolResultPreview() | ✅ MODULARIZED |
| **Card shape** | Small (default) | RoundedCornerShape(14.dp) | ✅ IMPROVED |
| **Container color logic** | Simple 2-color | Complex dark/light theme aware | ✅ IMPROVED |

---

## 2. MessageBubble - NO CHANGES

### OLD (HEAD~2) Lines 1057-1154
### NEW (HEAD) Lines 1067-1142

**IDENTICAL.** No UI changes to MessageBubble between these commits.

```kotlin
fun MessageBubble(message: UiMessage) {
    val isUser = message.role == MessageRole.USER
    if (isUser) {
        // User bubble: right-aligned, shrink-wrapped
        // ... exact same code ...
    } else {
        // AI message: full-width markdown + tool executions
        // ... exact same code ...
    }
}
```

---

## 3. Tool Call Display in MessageBubble

The tool execution rendering in `MessageBubble` **delegates to `ToolCallCard(execution)`**, which is identical in structure between HEAD~2 and HEAD:

```kotlin
message.toolExecutions.forEach { execution ->
    key(execution.toolCallId) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(tween(500)) + expandVertically()
        ) {
            ToolCallCard(execution = execution)  // ← Calls the redesigned ToolCallCard
        }
    }
}
```

---

## UI Regression Summary

### ❌ MAJOR REGRESSION: Lost Minimal Exit Code Display

**OLD behavior** (HEAD~2):
```
If execution.result == "exit 0":
┌─────────────────┐
│ ✓ exit 0        │  ← Minimal green pill
└─────────────────┘

If execution.result == "exit 1":
┌─────────────────┐
│ ✗ exit 1        │  ← Minimal red pill
└─────────────────┘
```

**NEW behavior** (HEAD):
- The `isExit0 || isExit1` early return is **completely removed**
- Exit code results are now treated like any other tool execution
- They display with full complex UI: icon pills, shimmer, expandable sections
- **Loss of visual clarity for simple success/failure messages**

### ✅ IMPROVEMENT: Enhanced Tool Metadata

NEW features in HEAD:
1. **Tool-specific icons** (14 different tool types)
2. **Animated shimmer** during RUNNING status
3. **Status icons** per execution status
4. **Subagent children** support for `invoke_subagents`
5. **Theme-aware colors** (dark/light mode)

---

## Commits Involved

- **HEAD~2** (5dd1207): `fix: color audit — fix all hardcoded colors, theme background/surface mismatch, dark mode VS Code colors`
- **HEAD~1** (fcb876a): `feat: redesign ChatScreen with iOS-style floating header/input gradients`
  - This commit also rewrote ToolCallCard
- **HEAD** (15a6eaa): `chore: fix .gitignore and remove tracked build artifacts`
  - No UI changes, just build artifacts

The major ToolCallCard redesign happened in **fcb876a**.

---

## Recommendation

The ToolCallCard redesign is feature-rich but loses the minimalist exit-code shortcut. To preserve UI clarity:

**Option A:** Restore the early exit code return:
```kotlin
if (isExit0 || isExit1) {
    val bg = if (isExit0) Color(0xFF4CAF50).copy(alpha = 0.2f) else MaterialTheme.colorScheme.errorContainer
    val fg = if (isExit0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onErrorContainer
    Surface(
        color = bg,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
    ) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.BuildCircle, null, modifier = Modifier.size(14.dp), tint = fg)
            Spacer(Modifier.width(6.dp))
            Text(execution.result!!.trim(), style = MaterialTheme.typography.labelSmall, color = fg, fontWeight = FontWeight.SemiBold)
        }
    }
    return
}
// ... rest of new ToolCallCard code
```

This preserves both the minimal exit-code display AND the new rich features for complex tool results.

package com.amaya.intelligence.ui.screens.chat.shared

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.amaya.intelligence.data.remote.api.MessageRole
import com.amaya.intelligence.domain.models.UiMessage
import com.amaya.intelligence.domain.models.ToolExecution
import com.amaya.intelligence.domain.models.MessageStep
import com.amaya.intelligence.ui.components.shared.LoadingIndicator
import com.amaya.intelligence.ui.components.shared.MessageBubble
import com.amaya.intelligence.ui.components.shared.extractMarkdownFilePaths
import com.amaya.intelligence.ui.components.shared.prefetchFileTypeIcons
import com.amaya.intelligence.ui.components.shared.resolveFileTypeSourcePath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

private const val CHAT_WINDOW_PAGE_SIZE = 45
private const val CHAT_WINDOW_MAX_ITEMS = 180
private const val CHAT_WINDOW_EDGE_THRESHOLD = 8

@Composable
fun ChatMessageList(
    listState: LazyListState,
    displayMessages: List<UiMessage>,
    isLoading: Boolean,
    isRemoteMode: Boolean,
    headerDp: Dp,
    inputBarHeight: Int,
    drawerOpen: Boolean,
    onToolAccept: ((ToolExecution) -> Unit)?,
    onToolDecline: ((ToolExecution) -> Unit)?,
    onLocalhostLinkClick: ((String) -> Unit)?,
    scope: CoroutineScope
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var windowStart by remember(displayMessages) {
        mutableIntStateOf(max(0, displayMessages.size - CHAT_WINDOW_PAGE_SIZE))
    }
    var windowEnd by remember(displayMessages) {
        mutableIntStateOf(displayMessages.size)
    }

    val safeStart = windowStart.coerceIn(0, displayMessages.size)
    val safeEnd = windowEnd.coerceIn(safeStart, displayMessages.size)
    val windowedMessages = remember(displayMessages, safeStart, safeEnd) {
        displayMessages.subList(safeStart, safeEnd)
    }

    LaunchedEffect(displayMessages.size) {
        if (displayMessages.isEmpty()) {
            windowStart = 0
            windowEnd = 0
            return@LaunchedEffect
        }

        val total = displayMessages.size
        val visibleInfo = listState.layoutInfo.visibleItemsInfo
        val isNearBottom = visibleInfo.lastOrNull()?.index?.let { idx ->
            idx >= listState.layoutInfo.totalItemsCount - CHAT_WINDOW_EDGE_THRESHOLD
        } ?: true

        if (isNearBottom || safeEnd == 0) {
            windowEnd = total
            windowStart = max(0, total - CHAT_WINDOW_MAX_ITEMS)
        } else {
            windowEnd = min(total, safeEnd)
            if (windowEnd - safeStart > CHAT_WINDOW_MAX_ITEMS) {
                windowStart = windowEnd - CHAT_WINDOW_MAX_ITEMS
            }
        }
    }
    val fileIconPrefetchPaths = remember(windowedMessages) {
        val fromTools = windowedMessages
            .asSequence()
            .flatMap { msg -> msg.steps.filterIsInstance<MessageStep.ToolCall>().map { it.execution }.asSequence() }
            .mapNotNull { resolveFileTypeSourcePath(it) }

        val fromMarkdown = windowedMessages
            .asSequence()
            .mapNotNull { msg -> (msg.formattedContent ?: msg.content).takeIf { it.contains("](file:///") } }
            .flatMap { extractMarkdownFilePaths(it).asSequence() }

        (fromTools + fromMarkdown)
            .distinct()
            .take(120)
            .toList()
    }

    LaunchedEffect(fileIconPrefetchPaths) {
        if (fileIconPrefetchPaths.isEmpty()) return@LaunchedEffect
        withContext(Dispatchers.Default) {
            prefetchFileTypeIcons(context, fileIconPrefetchPaths)
        }
    }

    LaunchedEffect(listState, displayMessages.size, safeStart, safeEnd) {
        snapshotFlow {
            val info = listState.layoutInfo
            val first = info.visibleItemsInfo.firstOrNull()?.index ?: 0
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            Triple(first, last, info.totalItemsCount)
        }.collect { (firstVisible, lastVisible, totalVisibleCount) ->
            if (totalVisibleCount <= 0 || displayMessages.isEmpty()) return@collect

            var nextStart = safeStart
            var nextEnd = safeEnd
            var indexShift = 0

            if (firstVisible <= CHAT_WINDOW_EDGE_THRESHOLD && safeStart > 0) {
                val loadCount = min(CHAT_WINDOW_PAGE_SIZE, safeStart)
                nextStart = safeStart - loadCount
                indexShift += loadCount
            }

            if (lastVisible >= totalVisibleCount - 1 - CHAT_WINDOW_EDGE_THRESHOLD && safeEnd < displayMessages.size) {
                nextEnd = min(displayMessages.size, safeEnd + CHAT_WINDOW_PAGE_SIZE)
            }

            if (nextEnd - nextStart > CHAT_WINDOW_MAX_ITEMS) {
                val overflow = (nextEnd - nextStart) - CHAT_WINDOW_MAX_ITEMS
                if (firstVisible > CHAT_WINDOW_EDGE_THRESHOLD * 2) {
                    nextStart += overflow
                    indexShift -= overflow
                } else {
                    nextEnd -= overflow
                }
            }

            if (nextStart != safeStart || nextEnd != safeEnd) {
                val currentOffset = listState.firstVisibleItemScrollOffset
                windowStart = nextStart
                windowEnd = nextEnd

                if (indexShift != 0) {
                    listState.scrollToItem(
                        index = (firstVisible + indexShift).coerceAtLeast(0),
                        scrollOffset = currentOffset
                    )
                }
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .then(if (!drawerOpen) Modifier.imePadding() else Modifier),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = headerDp + 8.dp,
            bottom = with(density) { inputBarHeight.toDp() } + 8.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(
            count = windowedMessages.size,
            key = { index -> windowedMessages[index].id },
            contentType = { index -> windowedMessages[index].role }
        ) { index ->
            val message = windowedMessages[index]
            var hideThinking = false
            if (index > 0 && message.role == MessageRole.ASSISTANT && !message.thinking.isNullOrBlank()) {
                val prevMessage = windowedMessages[index - 1]
                if (prevMessage.role == MessageRole.ASSISTANT && prevMessage.thinking == message.thinking) {
                    hideThinking = true
                }
            }
            MessageBubble(
                message = message,
                hideThinkingHeader = hideThinking,
                onToolAccept = onToolAccept,
                onToolDecline = onToolDecline,
                onLocalhostLinkClick = onLocalhostLinkClick
            )
        }
        if (isLoading) {
            item(key = "loading", contentType = "loading") {
                LoadingIndicator()
            }
        }
    }

    // Scroll-to-bottom FAB
    val showFab by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            last != null && last.index < info.totalItemsCount - 1
        }
    }
    if (showFab) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (!drawerOpen) Modifier.imePadding() else Modifier)
                .padding(
                    bottom = with(density) { inputBarHeight.toDp() } + 12.dp,
                    end = 16.dp
                ),
            contentAlignment = Alignment.BottomEnd
        ) {
            SmallFloatingActionButton(
                onClick = {
                    scope.launch {
                        listState.animateScrollToItem((windowedMessages.size - 1).coerceAtLeast(0))
                    }
                },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 2.dp,
                    pressedElevation = 4.dp
                )
            ) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = "Scroll to bottom",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

package com.amaya.intelligence.ui.screens.chat.shared

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
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
import com.amaya.intelligence.ui.components.shared.mountFade
import com.amaya.intelligence.ui.components.shared.extractMarkdownFilePaths
import com.amaya.intelligence.ui.components.shared.prefetchFileTypeIcons
import com.amaya.intelligence.ui.components.shared.resolveFileTypeSourcePath
import com.amaya.intelligence.util.LocalStreamPerfLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/** How many messages a scroll to either edge pulls into the window. */
private const val CHAT_WINDOW_PAGE_SIZE = 45

/**
 * How many messages the window holds when a conversation first opens.
 *
 * Deliberately much smaller than the page size: every message composed here costs a
 * markdown parse plus its tool and thinking cards, and opening a long session used to
 * pay for 45 of them before the first frame. The edge pager below grows the window as
 * soon as the user scrolls.
 */
private const val CHAT_WINDOW_INITIAL_SIZE = 12

private const val CHAT_WINDOW_MAX_ITEMS = 180
private const val CHAT_WINDOW_EDGE_THRESHOLD = 8

/** Fixed height of the streaming spinner slot — bounded so it can never resize itself. */
private val CHAT_SPINNER_SLOT_HEIGHT = 26.dp

/** Breathing room between the spinner and the composer below it. Collapses with the spinner. */
private val CHAT_SPINNER_BOTTOM_GAP = 20.dp

/** How long loading has to stay quiet before the spinner is allowed to fade out. */
private const val CHAT_SPINNER_HIDE_DELAY_MS = 240L

@Composable
fun ChatMessageList(
    listState: LazyListState,
    displayMessages: List<UiMessage>,
    conversationKey: String,
    isLoading: Boolean,
    isStreaming: Boolean = false,
    isRemoteMode: Boolean,
    headerDp: Dp,
    inputBarHeight: State<Int>,
    drawerOpen: Boolean,
    onToolAccept: ((ToolExecution) -> Unit)?,
    onToolDecline: ((ToolExecution) -> Unit)?,
    onClarify: ((ToolExecution, String?) -> Unit)? = null,
    onCopyMessage: ((String) -> Unit)? = null,
    onEditUserMessage: ((id: String, content: String) -> Unit)? = null,
    onResendUserMessage: ((id: String) -> Unit)? = null,
    onRegenerate: (() -> Unit)? = null,
    onLocalhostLinkClick: ((String) -> Unit)?,
    onScrollToBottomClick: () -> Unit = {},
    shouldAutoScroll: Boolean = false
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var windowStart by remember(conversationKey) {
        mutableIntStateOf(max(0, displayMessages.size - CHAT_WINDOW_INITIAL_SIZE))
    }
    var windowEnd by remember(conversationKey) {
        mutableIntStateOf(displayMessages.size)
    }

    val safeStart = windowStart.coerceIn(0, displayMessages.size)
    val safeEnd = windowEnd.coerceIn(safeStart, displayMessages.size)
    val windowedMessages = remember(displayMessages, safeStart, safeEnd) {
        displayMessages.subList(safeStart, safeEnd)
    }

    // Ids that have already been on screen once. Held here rather than inside the row,
    // because LazyColumn destroys a row's state the moment it scrolls out of view — the
    // mount fade used to live there, so it fired for every row that composed rather than
    // for every row that was new. That is what made a whole session fade in at once, and
    // re-fade on the way back up. Seeded with whatever is already loaded: history has to
    // appear instantly.
    val mountedMessageIds = remember(conversationKey) {
        displayMessages.mapTo(mutableSetOf()) { it.id }
    }

    // Hold the list invisible until it has been placed at the tail. Otherwise the first
    // painted frame is the top of the history and the user watches it jump.
    var positioned by remember { mutableStateOf(false) }
    val revealAlpha by animateFloatAsState(
        targetValue = if (positioned) 1f else 0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "chat_list_reveal"
    )
    LaunchedEffect(Unit) {
        try {
            if (displayMessages.isNotEmpty()) {
                // Wait for the first layout pass so there is something to scroll to.
                val total = snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > 0 }
                listState.scrollToItem((total - 1).coerceAtLeast(0), Int.MAX_VALUE)
            }
        } finally {
            positioned = true
        }
    }

    LaunchedEffect(displayMessages.size) {
        if (displayMessages.isEmpty()) {
            windowStart = 0
            windowEnd = 0
            return@LaunchedEffect
        }

        val total = displayMessages.size
        // Reuse the continuously-tracked follow flag instead of re-deriving
        // "am I near the bottom?" from layoutInfo right now — at this point the
        // list has not been laid out for the new message yet, so that read would
        // race the layout pass and could leave the new message outside the window.
        if (shouldAutoScroll || safeEnd == 0) {
            windowEnd = total
            windowStart = max(0, total - CHAT_WINDOW_MAX_ITEMS)
        } else {
            windowEnd = min(total, safeEnd)
            if (windowEnd - safeStart > CHAT_WINDOW_MAX_ITEMS) {
                windowStart = windowEnd - CHAT_WINDOW_MAX_ITEMS
            }
        }
    }

    // Debug builds only, and registered conditionally so release does not even allocate
    // the effect: this reads layoutInfo and logs on every single composition.
    if (LocalStreamPerfLog.ENABLED && !isRemoteMode) {
        SideEffect {
            val info = listState.layoutInfo
            LocalStreamPerfLog.onChatListComposed(
                messages = displayMessages.size,
                lastChars = displayMessages.lastOrNull()?.content?.length ?: 0,
                visibleItems = info.visibleItemsInfo.size,
                totalItems = info.totalItemsCount,
                isStreaming = isStreaming
            )
        }
    }

    // Keyed on cheap scalars. This used to hash a string joined from every message in
    // the window, and `windowedMessages` is a fresh subList on every recomposition — so
    // the whole key plus the path pipeline below was rebuilt on every streaming token.
    // Tool steps land discretely, so tracking the tail message's step counts is enough
    // to pick up new file references.
    val lastMessage = displayMessages.lastOrNull()
    val fileIconPrefetchPaths = remember(
        safeStart,
        safeEnd,
        displayMessages.size,
        lastMessage?.steps?.size,
        lastMessage?.toolExecutions?.size
    ) {
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

    // One long-lived collector. Keying it on the window bounds tore the snapshotFlow
    // down and re-subscribed it every time the window moved or a message arrived; the
    // bounds are read through rememberUpdatedState instead.
    val currentStart by rememberUpdatedState(safeStart)
    val currentEnd by rememberUpdatedState(safeEnd)
    val currentTotal by rememberUpdatedState(displayMessages.size)
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val first = info.visibleItemsInfo.firstOrNull()?.index ?: 0
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            Triple(first, last, info.totalItemsCount)
        }.collect { (firstVisible, lastVisible, totalVisibleCount) ->
            val start = currentStart
            val end = currentEnd
            val total = currentTotal
            if (totalVisibleCount <= 0 || total == 0) return@collect

            var nextStart = start
            var nextEnd = end
            var indexShift = 0

            if (firstVisible <= CHAT_WINDOW_EDGE_THRESHOLD && start > 0) {
                val loadCount = min(CHAT_WINDOW_PAGE_SIZE, start)
                nextStart = start - loadCount
                indexShift += loadCount
            }

            if (lastVisible >= totalVisibleCount - 1 - CHAT_WINDOW_EDGE_THRESHOLD && end < total) {
                nextEnd = min(total, end + CHAT_WINDOW_PAGE_SIZE)
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

            if (nextStart != start || nextEnd != end) {
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
            .graphicsLayer { alpha = revealAlpha }
            .then(if (!drawerOpen) Modifier.imePadding() else Modifier),
        contentPadding = PaddingValues(
            start = 18.dp,  // sejajar topbar kiri (☰)
            end = 18.dp,    // sejajar topbar kanan (⋮)
            top = headerDp + 8.dp,
            // No bottom padding: the composer-spacer item below already reserves
            // the composer's height, and the 16dp arrangement spacing supplies
            // the gap between the last message and it.
            bottom = 0.dp
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
            // `add` returns true only the first time this id is ever laid out in this
            // conversation. Captured in a remember so it stays stable for the row's
            // lifetime — recomputing it would drop the wrapper mid-animation.
            val animateMount = remember(message.id) { mountedMessageIds.add(message.id) }

            // The same fade every other timeline event uses: alpha only, at full height
            // from the first frame. A height animation here would give the auto-follow
            // loop a target that is still moving while the answer streams into it.
            Box(modifier = Modifier.mountFade(animateMount)) {
                MessageBubble(
                    message = message,
                    hideThinkingHeader = hideThinking,
                    onToolAccept = onToolAccept,
                    onToolDecline = onToolDecline,
                    onClarify = onClarify,
                    onCopyMessage = onCopyMessage,
                    onEditUserMessage = onEditUserMessage,
                    onResendUserMessage = onResendUserMessage,
                    onRegenerate = onRegenerate,
                    onLocalhostLinkClick = onLocalhostLinkClick
                )
            }
        }
        // One permanent tail item holding the spinner slot and the composer
        // reservation. The spinner used to be its own conditional item, so every
        // flip of isLoading added or removed a whole list entry — its height plus
        // the 16dp arrangement gap — in a single frame. At high token rates that
        // flag flips several times a second, which is what made the indicator
        // strobe and shove the text above it around.
        item(key = "composer-tail", contentType = "tail") {
            Column(modifier = Modifier.fillMaxWidth()) {
                ChatLoadingSlot(isLoading = isLoading)
                // The composer floats above this list rather than taking part in
                // its layout, so the list reserves the composer's current height
                // here. The value is re-reported whenever the composer grows or
                // shrinks, so the last message can never end up hidden behind it.
                Spacer(Modifier.height(with(density) { inputBarHeight.value.toDp() }))
            }
        }
    }

    // Scroll-to-bottom FAB
    val showFab by remember(shouldAutoScroll, positioned) {
        derivedStateOf {
            if (shouldAutoScroll || !positioned) return@derivedStateOf false
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            if (total == 0) return@derivedStateOf false
            val lastVisible = info.visibleItemsInfo.lastOrNull()
            if (lastVisible == null) return@derivedStateOf false

            if (lastVisible.index < total - 1) return@derivedStateOf true

            val distance = (lastVisible.offset + lastVisible.size) - info.viewportEndOffset
            distance > 10
        }
    }

    ChatScrollToBottomButton(
        visible = showFab,
        inputBarHeight = inputBarHeight,
        drawerOpen = drawerOpen,
        onClick = onScrollToBottomClick
    )
}

/**
 * The streaming spinner, given a fixed slot so it can only ever contribute one
 * known height to the list.
 *
 * Visibility is latched rather than mirroring `isLoading` directly: at high
 * token rates that flag flips repeatedly within a second, and driving layout
 * straight from it makes the indicator strobe. Once shown the spinner stays up
 * until loading has been quiet for [CHAT_SPINNER_HIDE_DELAY_MS], so a burst of
 * flips reads as one continuous spin.
 *
 * Both directions animate size as well as alpha, so the text above settles down
 * into the freed space instead of snapping into it when the answer completes.
 */
@Composable
private fun ChatLoadingSlot(isLoading: Boolean) {
    var spinnerVisible by remember { mutableStateOf(false) }
    LaunchedEffect(isLoading) {
        if (isLoading) {
            spinnerVisible = true
        } else if (spinnerVisible) {
            delay(CHAT_SPINNER_HIDE_DELAY_MS)
            spinnerVisible = false
        }
    }

    AnimatedVisibility(
        visible = spinnerVisible,
        enter = fadeIn(animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)) +
            expandVertically(animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)),
        exit = fadeOut(animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing)) +
            shrinkVertically(animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // Padding sits outside the fixed height, so the slot stays bounded
                // and the gap collapses together with the spinner.
                .padding(start = 4.dp, bottom = CHAT_SPINNER_BOTTOM_GAP)
                .height(CHAT_SPINNER_SLOT_HEIGHT),
            contentAlignment = Alignment.CenterStart
        ) {
            LoadingIndicator()
        }
    }
}

@Composable
private fun ChatScrollToBottomButton(
    visible: Boolean,
    inputBarHeight: State<Int>,
    drawerOpen: Boolean,
    onClick: () -> Unit
) {
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(if (!drawerOpen) Modifier.imePadding() else Modifier)
            .padding(bottom = with(density) { inputBarHeight.value.toDp() } + 12.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            SmallFloatingActionButton(
                onClick = onClick,
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

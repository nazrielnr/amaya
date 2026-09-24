package com.amaya.intelligence.ui.components.shared

import com.amaya.intelligence.domain.models.AssistantMode
import com.amaya.intelligence.domain.models.ConversationMode
import com.amaya.intelligence.domain.models.parseComposerReferences

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.zIndex

data class ChatMentionAgent(val id: Long, val name: String, val role: String, val groupName: String)

@Composable
fun ChatInput(
    text: String,
    onTextChange: (String) -> Unit,
    resetKey: Any? = null,
    isStreaming: Boolean,
    isCompressing: Boolean = false,
    /** Host-driven compaction mid-turn. Shows the pill but never arms the cancel affordance. */
    isAutoCompacting: Boolean = false,
    attachedFilePath: String? = null,
    attachedImageBase64: String? = null,
    attachedImageName: String? = null,
    onAttachFile: () -> Unit = {},
    onAttachImage: (() -> Unit)? = null,
    onClearAttachment: () -> Unit = {},
    onClearImageAttachment: () -> Unit = {},
    conversationMode: ConversationMode = ConversationMode.PLANNING,
    conversationModeLabel: String? = null,
    conversationModeIsFast: Boolean = conversationMode == ConversationMode.FAST,
    showConversationModeSelector: Boolean = false,
    onShowConversationModeSelector: () -> Unit = {},
    workspacePath: String? = null,
    assistantMode: AssistantMode = AssistantMode.CHAT,
    ownerLabel: String = "Chat",
    showWorkspaceCard: Boolean = true,
    onWorkspaceClick: () -> Unit = {},
    mentionAgents: List<ChatMentionAgent> = emptyList(),
    onSearchWorkspaceFiles: suspend (String) -> List<com.amaya.intelligence.domain.models.ProjectFileEntry> = { emptyList() },
    modelLabel: String = "Select Model",
    modelId: String = "",
    modelProviderId: String? = null,
    modelIconType: String? = null,
    onSelectModel: () -> Unit = {},
    effort: com.amaya.intelligence.data.remote.api.ThinkingEffort = com.amaya.intelligence.data.remote.api.ThinkingEffort.MEDIUM,
    onEffortChange: (com.amaya.intelligence.data.remote.api.ThinkingEffort) -> Unit = {},
    onCompactConversation: (String) -> Unit = {},
    onCancelCompactConversation: () -> Unit = {},
    onSendMessage: (String) -> Unit,
    onStopGeneration: () -> Unit,
    /** Increment to request focus on the composer (e.g. tapping "Edit" on a user bubble). */
    editRequest: Int = 0
) {
    val isDark = isSystemInDarkTheme()
    val hasAttachment = attachedFilePath != null || attachedImageBase64 != null
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    // Token-based external focus request (edit bubble → refill + focus + keyboard).
    LaunchedEffect(editRequest) {
        if (editRequest > 0) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }
    val referenceColor = MaterialTheme.colorScheme.primary
    val referenceTransformation = remember(referenceColor) { ComposerReferenceTransformation(referenceColor) }
    val expansion = imeAnimationProgress()
    val composerCornerRadius = lerp(28.dp, 24.dp, expansion)
    val composerHorizontalPadding = lerp(8.dp, 12.dp, expansion)
    val composerVerticalPadding = lerp(8.dp, 10.dp, expansion)
    val composerOuterTopPadding = lerp(8.dp, 6.dp, expansion)
    val composerOuterBottomPadding = lerp(10.dp, 8.dp, expansion)

    val wsName = remember(workspacePath) {
        workspacePath?.substringAfterLast("/").orEmpty()
    }
    val hasWorkspace = remember(workspacePath) { !workspacePath.isNullOrBlank() }
    var fileResults by remember(resetKey) { mutableStateOf(emptyList<com.amaya.intelligence.domain.models.ProjectFileEntry>()) }
    var isSearchingFiles by remember(resetKey) { mutableStateOf(false) }

    // Derived synchronously, not through a LaunchedEffect: routing '@' through a coroutine costs a
    // recomposition plus a dispatch before the card can even start animating in.
    var dismissedForText by remember(resetKey) { mutableStateOf<String?>(null) }
    val commandToken = remember(text) { text.substringAfterLast(' ') }
    val commandMode = remember(commandToken, text, dismissedForText) {
        if (text == dismissedForText) null else when (commandToken.firstOrNull()) {
            '@' -> ComposerCommand.MENTIONS
            '/' -> ComposerCommand.ACTIONS
            else -> null
        }
    }
    val commandQuery = remember(commandToken, commandMode) {
        if (commandMode == null) "" else commandToken.drop(1).trim()
    }

    LaunchedEffect(commandMode, commandQuery, hasWorkspace) {
        if (!hasWorkspace) {
            fileResults = emptyList()
            isSearchingFiles = false
            return@LaunchedEffect
        }
        if (commandMode != ComposerCommand.MENTIONS) {
            // Results are deliberately kept while in '/' mode. Clearing them means every swap back
            // to '@' restarts from an empty list -> spinner -> full list, which is why '@' and '/'
            // never looked like the same transition.
            isSearchingFiles = false
            return@LaunchedEffect
        }
        // Debounce refinements only. Opening the card searches immediately so the list is already
        // populated on the first frame of the enter animation; every keystroke after that waits,
        // so typing does not restart the card's size animation or spawn a walk per character.
        if (commandQuery.isNotEmpty()) kotlinx.coroutines.delay(140)
        isSearchingFiles = true
        val results = try {
            onSearchWorkspaceFiles(commandQuery)
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            // Must not be swallowed: a superseded search would otherwise blank the list to empty
            // before the replacement lands, which reads as a flicker.
            throw cancellation
        } catch (_: Throwable) {
            emptyList()
        }
        fileResults = results
        isSearchingFiles = false
    }

    val anyCompacting = isCompressing || isAutoCompacting

    val pillColor = remember(isDark) {
        if (isDark) Color(0xFF1F2126).copy(alpha = 0.94f)
        else Color(0xFFF8F8FA).copy(alpha = 0.96f)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp)
            .padding(top = composerOuterTopPadding, bottom = composerOuterBottomPadding),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (showConversationModeSelector) {
            Surface(
                onClick = onShowConversationModeSelector,
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        if (conversationModeIsFast) Icons.Default.Bolt else Icons.Default.Lightbulb,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = conversationModeLabel
                            ?: if (conversationMode == ConversationMode.PLANNING) "Planning" else "Fast",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }

        // Attached file pill
        if (attachedFilePath != null) {
            val fileName = attachedFilePath.substringAfterLast("/")
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.padding(start = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.AttachFile, contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(text = fileName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 200.dp))
                    Box(
                        // 24dp tap target (was 14dp): small enough to stay a compact
                        // chip, large enough to hit reliably on a touchscreen.
                        modifier = Modifier.size(24.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))
                            .clickable { onClearAttachment() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Remove",
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }

        // Attached image pill
        if (attachedImageBase64 != null) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.padding(start = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Image, contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(text = attachedImageName ?: "Image",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 200.dp))
                    Box(
                        // 24dp tap target (was 14dp): small enough to stay a compact
                        // chip, large enough to hit reliably on a touchscreen.
                        modifier = Modifier.size(24.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))
                            .clickable { onClearImageAttachment() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Remove",
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }

        val placeholderText = remember(assistantMode, hasWorkspace, wsName) {
            when (assistantMode) {
                AssistantMode.CHAT -> "Ask anything"
                AssistantMode.PROJECT -> "Ask about $wsName"
                AssistantMode.AGENT -> "Message the agent group"
            }
        }
        val placeholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.46f)
        val canSend = text.isNotBlank() || hasAttachment
        val submitMessage = {
            if (isCompressing) {
                onCancelCompactConversation()
            } else if (canSend) {
                val message = text.trim()
                onTextChange("")
                if (isStreaming) onStopGeneration()
                if (parseComposerReferences(message).commands.singleOrNull() == "compact") {
                    onCompactConversation(message.replaceFirst(COMPACT_COMMAND_PREFIX, "").trim())
                } else onSendMessage(message)
            } else if (isStreaming) {
                onStopGeneration()
            }
        }


        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.Bottom
        ) {
            val pillListState = androidx.compose.foundation.lazy.rememberLazyListState()

            // Passed as a lambda, not a value: reading the scroll state here in the composition
            // phase would recompose all of ChatInput — text field, custom Layout and the whole
            // suggestion list — on every frame the user scrolls the card.
            val topShadowAlpha = remember(pillListState) {
                {
                    if (pillListState.firstVisibleItemIndex > 0) 0.35f
                    else 0.35f * (pillListState.firstVisibleItemScrollOffset / 60f).coerceIn(0f, 1f)
                }
            }

            val pillVisible = commandMode != null || anyCompacting

            // Hoisted out of AnimatedVisibility so the content below can tell whether an enter or
            // exit is currently in flight.
            val pillTransition = remember(resetKey) { MutableTransitionState(false) }
            pillTransition.targetState = pillVisible

            // The card renders from a frozen snapshot so the content does not vanish on the first
            // frame of the exit, leaving an empty card to collapse.
            val livePill = if (pillVisible) {
                ComposerPillState(
                    mode = commandMode,
                    query = commandQuery,
                    agents = mentionAgents,
                    files = fileResults,
                    isSearching = isSearchingFiles,
                    showCompact = anyCompacting,
                    compactDone = false,
                    compactCanceled = false
                )
            } else null
            val lastPill = remember { mutableStateOf(livePill) }
            if (livePill != null && livePill != lastPill.value) lastPill.value = livePill
            val pill = livePill ?: lastPill.value

            // shrinkVertically is what makes the close visible: it shrinks the clip around content
            // that is still being drawn, so the card collapses down behind the composer. An outer
            // animateContentSize cannot do that — it only sees the child disappear once the exit
            // has already finished, so the card would vanish and then an empty gap would close.
            AnimatedVisibility(
                visibleState = pillTransition,
                enter = expandVertically(
                    expandFrom = Alignment.Bottom,
                    animationSpec = spring(stiffness = 800f, dampingRatio = 1f)
                ) + fadeIn(animationSpec = tween(140)),
                exit = shrinkVertically(
                    shrinkTowards = Alignment.Bottom,
                    animationSpec = spring(stiffness = 800f, dampingRatio = 1f)
                ) + fadeOut(animationSpec = tween(180)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .offset(y = 1.dp)
                    .zIndex(0f)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            ) {
                Surface(
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(0.7.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    // The overlay lives inside the content lambda so it sits under the same alpha
                    // layer as fadeIn/fadeOut. On the AnimatedVisibility modifier it would be
                    // outside the fade, and the gradient would keep painting at full strength after
                    // the card itself had already faded away.
                    modifier = Modifier
                        .fillMaxWidth()
                        .pillShadowOverlay(topShadowAlpha = topShadowAlpha)
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .animateContentSize(
                                // Only animates in-place resize, and only while the card is settled.
                                // While an enter or exit is running, expand/shrink already owns this
                                // axis, so resize snaps instead. Two springs on one axis is exactly
                                // what made swapping '@' for '/' — a close immediately followed by
                                // an open, with the content changing mid-flight — jump.
                                animationSpec = if (pillTransition.isIdle) {
                                    spring(stiffness = 800f, dampingRatio = 1f)
                                } else snap(),
                                alignment = Alignment.BottomStart
                            )
                    ) {
                        if (pill?.mode != null) {
                            ComposerCommandPill(
                                mode = pill.mode,
                                query = pill.query,
                                agents = pill.agents,
                                files = pill.files,
                                isSearching = pill.isSearching,
                                listState = pillListState,
                                onSelect = { value ->
                                    // Dismiss on this exact text so the card closes on the same
                                    // frame, without waiting for onTextChange to round-trip.
                                    dismissedForText = text
                                    onTextChange(text.dropLast(commandToken.length) + value + " ")
                                }
                            )
                        }
                        if (pill?.showCompact == true) {
                            CompactProgressPill(
                                isDone = pill.compactDone,
                                isCanceled = pill.compactCanceled
                            )
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(composerCornerRadius),
                color = pillColor,
                border = BorderStroke(
                    0.7.dp,
                    MaterialTheme.colorScheme.onSurface.copy(alpha = if (isDark) 0.15f else 0.10f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(1f),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                ComposerLayout(
                    expansion = expansion,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = composerHorizontalPadding,
                            vertical = composerVerticalPadding
                        ),
                    attachment = {
                        ComposerAttachmentButton(
                            isStreaming = isStreaming || isCompressing,
                            onAttachFile = onAttachFile,
                            onAttachImage = onAttachImage
                        )
                    },
                    input = {
                        Box(
                            modifier = Modifier.heightIn(min = 40.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            BasicTextField(
                                value = text,
                                onValueChange = onTextChange,
                                visualTransformation = referenceTransformation,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester),
                                maxLines = 5,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                decorationBox = { inner ->
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        if (text.isEmpty()) {
                                            Text(
                                                text = placeholderText,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = placeholderColor,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        inner()
                                    }
                                }
                            )
                        }
                    },
                    model = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ReasoningEffortButton(
                                effort = effort,
                                onEffortChange = onEffortChange,
                                enabled = true
                            )
                            Spacer(Modifier.width(8.dp))
                        Surface(
                            onClick = onSelectModel,
                            enabled = expansion > 0.5f,
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                            modifier = Modifier.height(40.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .widthIn(max = 240.dp)
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ModelLeadingIcon(
                                    modelId = modelId,
                                    providerId = modelProviderId,
                                    iconType = modelIconType,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = modelLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                            }
                        }
                        }
                    },
                    send = {
                        ComposerSendButton(
                            isStreaming = isStreaming || isCompressing,
                            isCompressing = isCompressing,
                            canSend = canSend,
                            onClick = submitMessage,
                            onEmptyClick = {
                                focusRequester.requestFocus()
                                keyboardController?.show()
                            }
                        )
                    }
                )
            }
        }
    }
}

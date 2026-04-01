package com.amaya.intelligence.impl.ide.antigravity.services.event

import com.amaya.intelligence.domain.models.*
import com.amaya.intelligence.impl.ide.antigravity.client.*
import com.amaya.intelligence.impl.ide.antigravity.client.RemoteWorkspace as ClientRemoteWorkspace
import com.amaya.intelligence.impl.ide.antigravity.services.streaming.StreamingStateManager
import com.amaya.intelligence.data.local.entity.ConversationEntity
import kotlinx.coroutines.CoroutineScope

/**
 * Main event handler that delegates to specialized sub-handlers.
 * Coordinates event handling across state sync, streaming, tool calls, messages, workspace, and errors.
 */
class AntigravityEventHandler(
    scope: CoroutineScope,
    client: RemoteSessionClient,
    private val stateManager: StreamingStateManager,
    onUiStateUpdate: ((ChatUiState) -> ChatUiState) -> Unit,
    onConversationsUpdate: (List<ConversationEntity>) -> Unit,
    onProjectFilesUpdate: (List<ProjectFileEntry>, String) -> Unit,
    onWorkspacesUpdate: (List<com.amaya.intelligence.domain.models.RemoteWorkspace>) -> Unit
) {
    private val stateSyncHandler = StateSyncEventHandler(stateManager, onUiStateUpdate)
    private val streamingHandler = StreamingEventHandler(stateManager, onUiStateUpdate)
    private val toolCallHandler = ToolCallEventHandler(stateManager, onUiStateUpdate)
    private val messageHandler = MessageEventHandler(stateManager, onUiStateUpdate)
    private val workspaceHandler = WorkspaceEventHandler(onUiStateUpdate, onConversationsUpdate, onProjectFilesUpdate, onWorkspacesUpdate, client)
    private val errorHandler = ErrorEventHandler(scope, client, stateManager, onUiStateUpdate)
    
    fun handleEvent(event: RemoteEvent, currentConversationId: String?) {
        runCatching {
            when (event) {
                is RemoteEvent.StateSync -> stateSyncHandler.handleStateSync(event, currentConversationId)
                is RemoteEvent.ConversationLoaded -> stateSyncHandler.handleConversationLoaded(event)
                is RemoteEvent.StateUpdate -> stateSyncHandler.handleStateUpdate(event, currentConversationId)
                is RemoteEvent.TextDelta -> streamingHandler.handleTextDelta(event, currentConversationId)
                is RemoteEvent.AiThinking -> streamingHandler.handleAiThinking(event, currentConversationId)
                is RemoteEvent.NewAssistantMessage -> messageHandler.handleNewAssistantMessage(event, currentConversationId)
                is RemoteEvent.UserMessage -> messageHandler.handleUserMessage(event, currentConversationId)
                is RemoteEvent.ToolCallStart -> toolCallHandler.handleToolCallStart(event, currentConversationId)
                is RemoteEvent.ToolCallResult -> toolCallHandler.handleToolCallResult(event, currentConversationId)
                is RemoteEvent.ToolActivity -> toolCallHandler.handleToolActivity(event, currentConversationId)
                is RemoteEvent.ConversationsList -> workspaceHandler.handleConversationsList(event)
                is RemoteEvent.ModelSelected -> workspaceHandler.handleModelSelected(event)
                is RemoteEvent.ActiveConversation -> workspaceHandler.handleActiveConversation(event, currentConversationId, stateManager)
                is RemoteEvent.CurrentWorkspace -> workspaceHandler.handleCurrentWorkspace(event)
                is RemoteEvent.StreamDone -> streamingHandler.handleStreamDone(event, currentConversationId)
                is RemoteEvent.NewConversation -> workspaceHandler.handleNewConversation(event, stateManager)
                is RemoteEvent.ProjectFiles -> workspaceHandler.handleProjectFiles(event)
                is RemoteEvent.WorkspacesList -> workspaceHandler.handleWorkspacesList(event)
                is RemoteEvent.ModelsList -> workspaceHandler.handleModelsList(event)
                is RemoteEvent.TitleGenerated -> workspaceHandler.handleTitleGenerated(event)
                is RemoteEvent.Error -> errorHandler.handleError(event, currentConversationId)
                else -> {}
            }
        }.onFailure { ex ->
            android.util.Log.e("AntigravityEventHandler", "Error in handleEvent: ${ex.message}", ex)
        }
    }
    
    fun resolveConversationId(id: String): String {
        return workspaceHandler.resolveConversationId(id)
    }
}

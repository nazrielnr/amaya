package com.amaya.intelligence.ui.screens.chat.remote

import com.amaya.intelligence.domain.models.ConnectionState
import com.amaya.intelligence.domain.models.RemoteWorkspace
import com.amaya.intelligence.domain.models.ProjectFileEntry

/**
 * State specific to remote chat mode.
 * Contains remote-only features like connection status, workspaces, and remote project files.
 */
data class RemoteChatState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val workspaces: List<RemoteWorkspace> = emptyList(),
    val projectFiles: List<ProjectFileEntry> = emptyList(),
    val projectPath: String = "",
    val serverInfo: String? = null
)

/**
 * Actions available in remote chat context.
 */
interface RemoteChatActions {
    fun connect(ip: String, port: Int)
    fun disconnect()
    fun selectWorkspace(workspaceId: String)
    fun refreshProjectFiles()
}

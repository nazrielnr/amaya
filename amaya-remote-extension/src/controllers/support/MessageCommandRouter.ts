import * as WebSocket from 'ws';
import { IIDEClient, IDEConversationMode } from '../../interfaces/IIDEClient';
import { IIDECommandExecutor } from '../../interfaces/IIDECommandExecutor';
import { AppState } from '../../types';
import { AttachmentValidator } from './AttachmentValidator';
import { ConversationStateController } from './ConversationStateController';
import { MessageFlowController } from './MessageFlowController';
import { StreamOrchestrator } from './StreamOrchestrator';
import { WorkspaceCommandController } from './WorkspaceCommandController';

interface MessageCommandRouterDeps {
    api: IIDEClient;
    state: AppState;
    commandExecutor: IIDECommandExecutor;
    attachmentValidator: AttachmentValidator;
    workspaceCommandController: WorkspaceCommandController;
    conversationStateController: ConversationStateController;
    messageFlowController: MessageFlowController;
    streamOrchestrator: StreamOrchestrator;
    broadcastEvent: (event: string, data: any, sessionId?: string) => void;
    shouldLogCommand: (action: string) => boolean;
    shouldRunCommand: (command: string, debounceMs: number) => boolean;
    getActiveSessionId: () => string | null;
    setConversationMode: (sessionId: string, mode: IDEConversationMode) => void;
    getModelsCache: () => { models: any[]; timestamp: number } | null;
    handleGetEventsSince: (lastSeqId: number, ws: WebSocket.WebSocket) => Promise<void>;
}

export class MessageCommandRouter {
    constructor(private readonly deps: MessageCommandRouterDeps) {}

    public async handleCommand(msg: any, ws: WebSocket.WebSocket): Promise<void> {
        const { action, data = {} } = msg;
        if (this.deps.shouldLogCommand(action)) {
            console.log(`[Amaya MessageHandler] Command: ${action}`);
        }

        switch (action) {
            case 'send_message':
                await this.handleSendMessage(data, ws);
                break;
            case 'set_conversation_mode':
                await this.handleSetConversationMode(data, ws);
                break;
            case 'new_chat':
                await this.deps.messageFlowController.handleNewChat();
                break;
            case 'pong':
                break;
            case 'stop_generation':
                this.handleStopGeneration(data);
                break;
            case 'tool_interaction':
                await this.handleToolInteraction(data, ws);
                break;
            case 'get_conversations':
                if (!this.deps.shouldRunCommand('get_conversations', 1500)) return;
                await this.deps.conversationStateController.handleGetConversations(ws);
                break;
            case 'load_conversation':
                await this.deps.conversationStateController.handleLoadConversation(data.id || data.conversationId, ws);
                break;
            case 'get_models':
                if (!this.deps.shouldRunCommand('get_models', 1500)) return;
                await this.deps.conversationStateController.handleGetModels(ws);
                break;
            case 'select_model':
                this.handleSelectModel(data);
                break;
            case 'get_state':
                if (!this.deps.shouldRunCommand('get_state', 750)) return;
                await this.deps.conversationStateController.handleGetState(ws);
                break;
            case 'confirm_action':
                try {
                    await this.deps.commandExecutor.confirmAction(!!data.confirmed);
                } catch { }
                break;
            case 'get_workspaces':
                await this.deps.workspaceCommandController.handleGetWorkspaces(ws);
                break;
            case 'get_project_files':
                await this.deps.workspaceCommandController.handleGetProjectFiles(data.path || '', ws);
                break;
            case 'get_file_diff':
                await this.deps.workspaceCommandController.handleGetFileDiff(ws);
                break;
            case 'get_file_content':
                await this.deps.workspaceCommandController.handleGetFileContent(data.path || '', ws);
                break;
            case 'get_events_since':
                await this.deps.handleGetEventsSince(data.lastSeqId || 0, ws);
                break;
            case 'debug_log':
                if (data.message) {
                    console.log(`[Amaya Remote DEBUG] ${data.message}`);
                    this.deps.broadcastEvent('debug_log', { message: data.message, timestamp: Date.now() });
                }
                break;
        }
    }

    private async handleSendMessage(data: any, ws: WebSocket.WebSocket): Promise<void> {
        const parsedAttachments = this.deps.attachmentValidator.parse(data.attachments);
        if (parsedAttachments.errors.length > 0) {
            this.deps.broadcastEvent('error', {
                message: parsedAttachments.errors.join('; '),
                conversationId: data.id || data.conversationId || this.deps.getActiveSessionId(),
            });
        }
        await this.deps.messageFlowController.handleSendMessage(
            data.content || '',
            ws,
            data.id || data.conversationId,
            data.mode === 'fast' ? 'fast' : 'planning',
            parsedAttachments.attachments
        );
    }

    private async handleSetConversationMode(data: any, ws: WebSocket.WebSocket): Promise<void> {
        try {
            const rawMode = String(data.mode || '').toLowerCase();
            const mode: IDEConversationMode = rawMode === 'fast' ? 'fast' : 'planning';
            const targetId = data.id || data.conversationId || this.deps.getActiveSessionId();
            if (targetId) {
                this.deps.setConversationMode(targetId, mode);
                this.deps.broadcastEvent('state_update', {
                    conversationId: targetId,
                    conversationMode: mode,
                }, targetId);
            }
        } catch (e: any) {
            this.deps.broadcastEvent('error', { message: e?.message || 'Failed to set conversation mode' });
        }
        await this.deps.conversationStateController.handleGetState(ws);
    }

    private handleStopGeneration(data: any) {
        const stopId = data.id || data.conversationId || this.deps.getActiveSessionId();
        if (stopId) {
            this.deps.streamOrchestrator.invalidateStream(stopId);
            this.deps.api.stopStreaming(stopId);
            this.deps.streamOrchestrator.setStreamingState(stopId, false, false);
            this.deps.broadcastEvent('stream_done', { stopReason: 'USER_ABORTED', conversationId: stopId }, stopId);
        } else {
            this.deps.api.stopStreaming();
            this.deps.state.isLoading = false;
            this.deps.state.isStreaming = false;
            this.deps.broadcastEvent('stream_done', { stopReason: 'USER_ABORTED' });
        }
    }

    private async handleToolInteraction(data: any, ws: WebSocket.WebSocket): Promise<void> {
        if ((data.id || data.conversationId || this.deps.getActiveSessionId()) && data.toolCallId) {
            const interactionId = data.id || data.conversationId || this.deps.getActiveSessionId();
            const accepted = !!data.accepted;
            const ok = await this.deps.api.respondToToolInteraction(
                interactionId,
                data.toolCallId,
                accepted,
                data.arguments || {},
                data.metadata || {}
            );
            if (!ok) {
                this.deps.broadcastEvent('error', { message: 'Failed to respond to terminal action', conversationId: interactionId }, interactionId);
                return;
            }
            if (accepted) {
                this.deps.broadcastEvent('tool_call_start', {
                    toolCallId: data.toolCallId,
                    name: data.name || 'run_shell',
                    arguments: data.arguments || {},
                    status: 'RUNNING',
                    metadata: { ...(data.metadata || {}), approvalState: 'resolved' },
                    conversationId: interactionId,
                }, interactionId);
            } else {
                this.deps.streamOrchestrator.invalidateStream(interactionId);
                this.deps.streamOrchestrator.setStreamingState(interactionId, false, false);
                this.deps.broadcastEvent('tool_call_result', {
                    toolCallId: data.toolCallId,
                    name: data.name || 'run_shell',
                    result: 'Declined by user',
                    isError: false,
                    conversationId: interactionId,
                }, interactionId);
                this.deps.broadcastEvent('stream_done', { stopReason: 'USER_ABORTED', conversationId: interactionId }, interactionId);
            }
            await this.deps.conversationStateController.handleGetState(ws);
        }
    }

    private handleSelectModel(data: any) {
        if (!data.modelId) return;
        const models = this.deps.state.models.length > 0 ? this.deps.state.models : (this.deps.getModelsCache()?.models || []);
        const model = models.find(m => m.id === data.modelId || m.label === data.modelId);
        if (model) {
            this.deps.state.selectedModelId = model.id;
            this.deps.state.models = models;
            this.deps.broadcastEvent('model_selected', { modelId: model.id });
        } else {
            this.deps.state.selectedModelId = data.modelId;
            this.deps.broadcastEvent('model_selected', { modelId: data.modelId });
        }
    }
}
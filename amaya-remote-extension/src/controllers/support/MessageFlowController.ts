import * as WebSocket from 'ws';
import { IIDEClient, IDEConversationMode, IDEMessageAttachment } from '../../interfaces/IIDEClient';
import { AppState } from '../../types';
import { AttachmentValidator } from './AttachmentValidator';
import { ModelQuotaGuard } from './ModelQuotaGuard';
import { StreamOrchestrator } from './StreamOrchestrator';

interface MessageFlowControllerDeps {
    api: IIDEClient;
    state: AppState;
    attachmentValidator: AttachmentValidator;
    modelQuotaGuard: ModelQuotaGuard;
    streamOrchestrator: StreamOrchestrator;
    broadcastEvent: (event: string, data: any, sessionId?: string) => void;
    getModelsCache: () => { models: any[]; timestamp: number } | null;
    setModelsCache: (cache: { models: any[]; timestamp: number } | null) => void;
    getConversationMode: (sessionId?: string | null) => IDEConversationMode;
    setConversationMode: (sessionId: string, mode: IDEConversationMode) => void;
    onLastUserSendAt: (timestamp: number) => void;
}

export class MessageFlowController {
    constructor(private readonly deps: MessageFlowControllerDeps) {}

    public async handleSendMessage(
        content: string,
        ws: WebSocket.WebSocket,
        conversationId?: string,
        conversationMode: IDEConversationMode = 'planning',
        attachments: IDEMessageAttachment[] = []
    ): Promise<void> {
        console.log(`[Amaya MessageHandler] handleSendMessage: content="${content.substring(0, 50)}...", attachments=${attachments.length}`);
        if (attachments.length > 0) {
            attachments.forEach((attachment, idx) => {
                console.log(`[Amaya MessageHandler] Attachment[${idx}]: mimeType=${attachment.mimeType}, dataLen=${attachment.dataBase64?.length || 0}, fileName=${attachment.fileName || 'N/A'}`);
            });
        }
        if (!content.trim() && attachments.length === 0) return;

        this.deps.onLastUserSendAt(Date.now());

        if (attachments.length > 0) {
            const selectedModel = this.deps.state.models.find(model => model.id === this.deps.state.selectedModelId);
            const hasImageAttachments = attachments.some(attachment => attachment.mimeType.startsWith('image/'));
            if (hasImageAttachments && selectedModel && !selectedModel.supportsImages) {
                const convId = conversationId || this.deps.state.activeSessionId || undefined;
                this.deps.broadcastEvent('error', {
                    message: `Model "${selectedModel.label}" does not support images. Please select a different model.`,
                    conversationId: convId
                }, convId);
                return;
            }
        }

        const now = Date.now();
        const modelsCache = this.deps.getModelsCache();
        if (!modelsCache || now - modelsCache.timestamp > 10000 || !this.deps.state.models.length) {
            try {
                this.deps.state.models = await this.deps.api.getModels();
                this.deps.setModelsCache({ models: this.deps.state.models, timestamp: now });
                console.log('[Amaya MessageHandler] Refreshed quota info before send_message');
            } catch (error: any) {
                console.error('[Amaya MessageHandler] Failed to refresh quota info:', error.message);
            }
        }

        const quotaError = this.deps.modelQuotaGuard.validateSend(this.deps.state.models, this.deps.state.selectedModelId);
        if (quotaError) {
            const convId = conversationId || this.deps.state.activeSessionId || undefined;
            this.deps.broadcastEvent('error', {
                message: quotaError,
                conversationId: convId
            }, convId);
            return;
        }

        let targetId = conversationId || this.deps.state.activeSessionId;
        if (!targetId) {
            try {
                const metas = await this.deps.api.getConversationsMetadata();
                if (metas && metas.length > 0) {
                    const latest = metas.reduce((a, b) => (a.lastModified >= b.lastModified ? a : b));
                    targetId = latest?.id || null;
                }
            } catch {
                // ignore
            }

            if (!targetId) {
                const id = await this.deps.api.startSession();
                if (!id) {
                    this.deps.broadcastEvent('error', { message: 'Failed to create new conversation' });
                    return;
                }
                targetId = id;
            }
            this.deps.state.activeSessionId = targetId;
        }

        this.deps.setConversationMode(targetId, conversationMode);
        this.deps.broadcastEvent('active_conversation', { conversationId: targetId }, targetId);
        this.deps.broadcastEvent('user_message', {
            content,
            attachments,
            conversationId: targetId
        }, targetId);
        this.deps.streamOrchestrator.setStreamingState(targetId, true, true);

        let ignoreBeforeIndex = 0;
        let previousSteps: any[] = [];
        if (!this.deps.streamOrchestrator.isStreamAttached(targetId)) {
            try {
                previousSteps = await Promise.race([
                    this.deps.api.getSessionTrajectory(targetId),
                    new Promise<any[]>((resolve) => setTimeout(() => resolve([]), 1500))
                ]);
                ignoreBeforeIndex = Array.isArray(previousSteps) ? previousSteps.length : 0;
            } catch {
                ignoreBeforeIndex = 0;
                previousSteps = [];
            }
        }

        console.log(`[Amaya MessageHandler] Send to ${targetId}, ignoreBeforeIndex: ${ignoreBeforeIndex}`);

        const sent = await this.deps.api.sendMessage(targetId, content, this.deps.state.selectedModelId, conversationMode, attachments);
        if (!sent) {
            const errDesc = (this.deps.api as any).getLastHttpError?.() || 'Unknown';
            this.deps.streamOrchestrator.setStreamingState(targetId, false, false);
            this.deps.broadcastEvent('error', { message: `Failed to send message: ${errDesc}`, conversationId: targetId }, targetId);
            return;
        }

        this.deps.broadcastEvent('new_assistant_message', { conversationId: targetId }, targetId);

        try {
            if (!this.deps.streamOrchestrator.isStreamAttached(targetId)) {
                const token = this.deps.streamOrchestrator.getNextStreamToken(targetId);
                this.deps.streamOrchestrator.setStreamToken(targetId, token);
                const callbacks = this.deps.streamOrchestrator.createStreamCallbacks(targetId, token);
                await this.deps.api.streamForResponse(targetId, callbacks, ignoreBeforeIndex, previousSteps);
            }
        } catch (error: any) {
            console.error('[Amaya MessageHandler] Send stream error:', error.message);
            this.deps.streamOrchestrator.setStreamingState(targetId, false, false);
            this.deps.broadcastEvent('error', { message: error.message, conversationId: targetId }, targetId);
        }
    }

    public async handleNewChat(): Promise<void> {
        const id = await this.deps.api.startSession();
        if (id) {
            this.deps.state.activeSessionId = id;
            this.deps.setConversationMode(id, 'planning');
            this.deps.broadcastEvent('active_conversation', { conversationId: id }, id);
            this.deps.broadcastEvent('new_conversation', { conversationId: id }, id);
        } else {
            this.deps.broadcastEvent('error', { message: 'Failed to create new conversation' });
        }
    }
}

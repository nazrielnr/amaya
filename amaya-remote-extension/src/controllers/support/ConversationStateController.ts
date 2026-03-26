import * as WebSocket from 'ws';
import { IIDEClient, IDEConversationMode } from '../../interfaces/IIDEClient';
import { AppState } from '../../types';
import { IHostWorkspaceService } from './HostWorkspaceService';
import { StreamOrchestrator } from './StreamOrchestrator';

type ModelsCache = { models: any[]; timestamp: number } | null;

interface ConversationStateControllerDeps {
    api: IIDEClient;
    state: AppState;
    hostWorkspaceService: IHostWorkspaceService;
    sendToClient: (ws: WebSocket.WebSocket, msg: any) => void;
    broadcastEvent: (event: string, data: any, sessionId?: string) => void;
    streamOrchestrator: StreamOrchestrator;
    getModelsCache: () => ModelsCache;
    setModelsCache: (cache: ModelsCache) => void;
}

export class ConversationStateController {
    constructor(private readonly deps: ConversationStateControllerDeps) {}

    public async handleGetConversations(ws: WebSocket.WebSocket): Promise<void> {
        const currentWs = this.deps.hostWorkspaceService.getCurrentWorkspace();
        const activeId = (this.deps.api as any).httpCore?.sessionId;
        const metadatas = await this.deps.api.getConversationsMetadata();

        const toReadableTitle = (input: string): string => {
            const cleaned = (input || '')
                .replace(/[#*_`>\-]+/g, ' ')
                .replace(/\s+/g, ' ')
                .trim();
            if (!cleaned) return '';
            return cleaned.length > 50 ? `${cleaned.substring(0, 47)}...` : cleaned;
        };

        const isFallbackTitle = (title: string, id: string): boolean => {
            if (!title) return true;
            if (title === `Chat ${id.substring(0, 6)}`) return true;
            return /^Chat\s+[a-f0-9]{6}$/i.test(title);
        };

        const enriched = metadatas.map((metadata) => {
            let title = metadata.title;
            let workspacePath = metadata.workspacePath;

            if (!workspacePath && metadata.id === activeId && currentWs) {
                workspacePath = currentWs.path;
            } else if (!workspacePath && currentWs) {
                workspacePath = currentWs.path;
            }

            if (isFallbackTitle(title, metadata.id) && metadata.preview && metadata.preview !== '...') {
                const fromPreview = toReadableTitle(metadata.preview.split('\n')[0] || metadata.preview);
                if (fromPreview) {
                    title = fromPreview;
                }
            }

            return { ...metadata, title, workspacePath };
        });

        this.deps.sendToClient(ws, {
            event: 'conversations_list',
            data: {
                conversations: enriched,
                currentWorkspacePath: currentWs?.path || null,
            },
        });
    }

    public async handleGetModels(ws: WebSocket.WebSocket): Promise<void> {
        const now = Date.now();
        let models = this.deps.state.models;
        const modelsCache = this.deps.getModelsCache();

        if (!modelsCache || now - modelsCache.timestamp > 60000 || !models.length) {
            models = await this.deps.api.getModels();
            this.deps.state.models = models;
            this.deps.setModelsCache({ models, timestamp: now });
        } else {
            models = modelsCache.models;
        }

        const selectedModel = this.deps.state.models.find((model) => model.id === this.deps.state.selectedModelId);
        this.deps.sendToClient(ws, {
            event: 'models_list',
            data: {
                models: models.map((model) => ({
                    id: model.id,
                    label: model.label,
                    isRecommended: model.isRecommended,
                    quota: model.quotaInfo ? (model.quotaInfo.remainingFraction ?? 0.0) : 1.0,
                    quotaLabel: model.quotaLabel,
                    resetTime: model.quotaInfo?.resetTime || undefined,
                    tagTitle: model.tagTitle,
                    supportsImages: model.supportsImages,
                })),
                selectedModelId: selectedModel?.id || models[0]?.id || '',
            },
        });
    }

    public async handleGetState(ws: WebSocket.WebSocket): Promise<void> {
        await this.handleGetModels(ws);

        let messages: any[] = [];
        let activeId = this.deps.state.activeSessionId;

        if (!activeId) {
            const lastActive = this.deps.api.getLastActiveSessionId();
            if (lastActive) {
                activeId = lastActive;
                this.deps.state.activeSessionId = lastActive;
                console.log(`[Amaya MessageHandler] Restored active conversation from last active session: ${activeId}`);
            }
        }

        if (!activeId) {
            const streamingCandidates: string[] = [];
            for (const id of this.deps.streamOrchestrator.getStreamingSessionIds()) {
                streamingCandidates.push(id);
            }
            if (streamingCandidates.length > 0) {
                activeId = streamingCandidates[0];
                this.deps.state.activeSessionId = activeId;
                console.log(`[Amaya MessageHandler] Using streaming conversation as active: ${activeId}`);
            }
        }

        if (!activeId) {
            try {
                const metas = await this.deps.api.getConversationsMetadata();
                if (metas && metas.length > 0) {
                    const latest = metas.reduce((a, b) => (a.lastModified >= b.lastModified ? a : b));
                    if (latest?.id) {
                        activeId = latest.id;
                        this.deps.state.activeSessionId = latest.id;
                        console.log(`[Amaya MessageHandler] Inferred active conversation: ${activeId}`);
                    }
                }
            } catch (error: any) {
                console.error('[Amaya MessageHandler] Failed to infer active conversation:', error.message);
            }
        }

        if (activeId) {
            console.log(`[Amaya MessageHandler] Broadcasting active_conversation: ${activeId}`);
            this.deps.broadcastEvent('active_conversation', { conversationId: activeId }, activeId);
        }

        if (activeId) {
            try {
                const hotMessages = this.deps.api.getCurrentSessionMessages(activeId);
                if (hotMessages && hotMessages.length > 0) {
                    messages = this.deps.streamOrchestrator.mapMessagesForUi(hotMessages);
                } else {
                    const rawMessages = await this.deps.api.getSessionMessages(activeId);
                    messages = this.deps.streamOrchestrator.mapMessagesForUi(rawMessages);
                }
            } catch (error: any) {
                console.error('[Amaya MessageHandler] Failed to fetch state messages:', error.message);
            }

            await this.deps.streamOrchestrator.ensureStreamAttached(activeId);
        }

        const selectedModel = this.deps.state.models.find((model) => model.id === this.deps.state.selectedModelId);
        const currentWs = this.deps.hostWorkspaceService.getCurrentWorkspace();

        const isLoading = activeId ? this.deps.streamOrchestrator.getLoadingState(activeId) : false;
        const isStreaming = activeId ? this.deps.streamOrchestrator.getStreamingState(activeId) : false;

        this.deps.state.isLoading = isLoading;
        this.deps.state.isStreaming = isStreaming;

        this.deps.sendToClient(ws, {
            event: 'state_sync',
            data: {
                messages,
                isLoading,
                isStreaming,
                currentModel: selectedModel?.id || this.deps.state.selectedModelId || '',
                conversationMode: this.deps.streamOrchestrator.getConversationMode(activeId),
                toolExecutions: [],
                appName: 'Remote IDE Framework',
                appVersion: '2.0.0',
                currentWorkspace: currentWs ? { name: currentWs.name, path: currentWs.path } : null,
                conversationId: activeId,
            },
        });
    }

    public async handleLoadConversation(id: string, ws: WebSocket.WebSocket): Promise<void> {
        if (!id) return;

        this.deps.state.activeSessionId = id;
        this.deps.state.isLoading = this.deps.streamOrchestrator.getLoadingState(id);
        this.deps.state.isStreaming = this.deps.streamOrchestrator.getStreamingState(id);

        let messages: any[] = [];
        try {
            const hotMessages = this.deps.api.getCurrentSessionMessages(id);
            if (hotMessages && hotMessages.length > 0) {
                messages = this.deps.streamOrchestrator.mapMessagesForUi(hotMessages);
            } else {
                const rawMessages = await this.deps.api.getSessionMessages(id);
                messages = this.deps.streamOrchestrator.mapMessagesForUi(rawMessages);
            }
        } catch (error: any) {
            console.error('[Amaya MessageHandler] Failed to load conversation messages:', error.message);
        }

        await this.deps.streamOrchestrator.ensureStreamAttached(id);

        this.deps.sendToClient(ws, {
            event: 'conversation_loaded',
            data: {
                conversationId: id,
                messages,
                conversationMode: this.deps.streamOrchestrator.getConversationMode(id),
            },
        });
    }
}

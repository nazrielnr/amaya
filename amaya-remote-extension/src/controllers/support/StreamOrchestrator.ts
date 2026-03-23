import * as WebSocket from 'ws';
import { IIDEClient, IDEConversationMode } from '../../interfaces/IIDEClient';
import { IIDERunStatusMapper } from '../../interfaces/IIDERunStatusMapper';
import { AppState } from '../../types';
import { formatLocalhostAsPlainLink, hasLocalhostLink } from '../../utils/LocalhostLinker';

interface StreamOrchestratorDeps {
    api: IIDEClient;
    state: AppState;
    runStatusMapper: IIDERunStatusMapper;
    broadcastEvent: (event: string, data: any, sessionId?: string) => void;
    mapMessagesForUi: (messages: any[]) => any[];
    getConversationMode: (sessionId?: string | null) => IDEConversationMode;
    formatErrorMessage: (input: any) => { message: string; raw: string };
    getLocalIp: () => string;
}

export class StreamOrchestrator {
    private readonly attachedStreamIds: Set<string> = new Set();
    private readonly loadingMap: Map<string, boolean> = new Map();
    private readonly streamingMap: Map<string, boolean> = new Map();
    private readonly streamSessionToken: Map<string, number> = new Map();
    private readonly lastAiThinkingAt: Map<string, number> = new Map();
    private readonly AI_THINKING_THROTTLE_MS = 500;

    constructor(private readonly deps: StreamOrchestratorDeps) {}

    public getConversationMode(sessionId?: string | null): IDEConversationMode {
        return this.deps.getConversationMode(sessionId);
    }

    public mapMessagesForUi(messages: any[]): any[] {
        return this.deps.mapMessagesForUi(messages);
    }

    public isStreamAttached(sessionId: string): boolean {
        return this.attachedStreamIds.has(sessionId);
    }

    public getLoadingState(sessionId: string): boolean {
        return this.loadingMap.get(sessionId) || false;
    }

    public getStreamingState(sessionId: string): boolean {
        return this.streamingMap.get(sessionId) || false;
    }

    public getStreamingSessionIds(): string[] {
        return Array.from(this.streamingMap.entries())
            .filter(([, isStreaming]) => isStreaming)
            .map(([sessionId]) => sessionId);
    }

    public setStreamingState(sessionId: string, isLoading: boolean, isStreaming: boolean, emitStatusChange: boolean = false) {
        this.loadingMap.set(sessionId, isLoading);
        this.streamingMap.set(sessionId, isStreaming);
        if (this.deps.state.activeSessionId === sessionId) {
            this.deps.state.isLoading = isLoading;
            this.deps.state.isStreaming = isStreaming;
        }
        this.deps.broadcastEvent('state_update', { isLoading, isStreaming, conversationId: sessionId }, sessionId);
        if (emitStatusChange) {
            const status = this.deps.runStatusMapper.toClientStatus(isLoading, isStreaming);
            this.deps.broadcastEvent('status_change', { status, conversationId: sessionId }, sessionId);
        }
    }

    public invalidateStream(sessionId: string) {
        this.attachedStreamIds.delete(sessionId);
        this.streamSessionToken.set(sessionId, (this.streamSessionToken.get(sessionId) || 0) + 1);
    }

    public getNextStreamToken(sessionId: string): number {
        return (this.streamSessionToken.get(sessionId) || 0) + 1;
    }

    public setStreamToken(sessionId: string, token: number) {
        this.streamSessionToken.set(sessionId, token);
    }

    public createStreamCallbacks(sessionId: string, token: number) {
        const checkToken = () => (this.streamSessionToken.get(sessionId) || 0) === token;
        return {
            onToolCall: (tc: any) => {
                if (!checkToken()) return;
                this.setStreamingState(sessionId, false, true);
                this.deps.broadcastEvent('tool_call_start', {
                    toolCallId: tc.id,
                    name: tc.name,
                    arguments: this.safeParseJson(tc.args),
                    status: tc.status || 'RUNNING',
                    metadata: tc.metadata || {},
                    conversationId: sessionId,
                }, sessionId);
            },
            onToolResult: (name: string, result: string, id: string, isError: boolean) => {
                if (!checkToken()) return;
                this.setStreamingState(sessionId, false, true);
                this.deps.broadcastEvent('tool_call_result', { toolCallId: id, name, result, isError, conversationId: sessionId }, sessionId);
            },
            onUserMessage: (text: string, attachments?: { mimeType: string; dataBase64?: string; fileName?: string; uri?: string }[]) => {
                if (!checkToken()) return;
                this.deps.broadcastEvent('user_message', {
                    content: text,
                    attachments: attachments,
                    conversationId: sessionId,
                }, sessionId);
            },
            onThinking: (text: string, id?: string, isRunning?: boolean) => {
                if (!checkToken()) return;
                this.setStreamingState(sessionId, false, true);
                const now = Date.now();
                const lastEmit = this.lastAiThinkingAt.get(sessionId) || 0;
                if (now - lastEmit >= this.AI_THINKING_THROTTLE_MS) {
                    this.lastAiThinkingAt.set(sessionId, now);
                    this.deps.broadcastEvent('ai_thinking', { text, stepIndex: id, isRunning, conversationId: sessionId }, sessionId);
                }
            },
            onTextDelta: (text: string, stepIndex?: string) => {
                if (!checkToken()) return;
                this.setStreamingState(sessionId, false, true);
                const localIp = this.deps.getLocalIp();
                const processedText = hasLocalhostLink(text)
                    ? formatLocalhostAsPlainLink(text, localIp)
                    : text;
                this.deps.broadcastEvent('text_delta', { text: processedText, stepIndex, conversationId: sessionId }, sessionId);
            },
            onStateSync: (messages: any[]) => {
                if (!checkToken()) return;
                const mappedMessages = this.deps.mapMessagesForUi(messages);
                const selectedModel = this.deps.state.models.find((m) => m.id === this.deps.state.selectedModelId);
                this.deps.broadcastEvent('state_sync', {
                    messages: mappedMessages,
                    isLoading: this.getLoadingState(sessionId),
                    isStreaming: this.getStreamingState(sessionId),
                    currentModel: selectedModel?.label || '',
                    conversationMode: this.deps.getConversationMode(sessionId),
                    conversationId: sessionId,
                }, sessionId);
            },
            onDone: (finalText: string, stopReason?: string) => {
                if (!checkToken()) return;
                this.setStreamingState(sessionId, false, false);
                this.deps.broadcastEvent('stream_done', { finalText, stopReason: stopReason || 'DONE', conversationId: sessionId }, sessionId);
            },
            onError: (err: string) => {
                if (!checkToken()) return;
                if (/aborted/i.test(err || '')) {
                    this.setStreamingState(sessionId, false, false);
                    this.attachedStreamIds.delete(sessionId);
                    return;
                }
                console.error('[Amaya MessageHandler] Stream error:', err);
                this.setStreamingState(sessionId, false, false);
                this.attachedStreamIds.delete(sessionId);
                const formatted = this.deps.formatErrorMessage(err);
                this.deps.broadcastEvent('error', { message: formatted.message, raw: formatted.raw, conversationId: sessionId }, sessionId);
            },
            onTitleGenerated: (title: string) => {
                if (!checkToken()) return;
                this.deps.broadcastEvent('title_generated', { title, conversationId: sessionId }, sessionId);
            },
            onStatusChange: (status: string) => {
                if (!checkToken()) return;
                const { isLoading, isStreaming } = this.deps.runStatusMapper.fromProviderStatus(status);
                this.setStreamingState(sessionId, isLoading, isStreaming, true);
            },
        };
    }

    public async ensureStreamAttached(sessionId: string) {
        if (!sessionId) return;
        if (this.attachedStreamIds.has(sessionId)) return;

        this.attachedStreamIds.add(sessionId);
        const token = this.getNextStreamToken(sessionId);
        this.streamSessionToken.set(sessionId, token);
        console.log(`[Amaya MessageHandler] Stream attach for ${sessionId}`);
        this.setStreamingState(sessionId, true, false);

        (async () => {
            this.setStreamingState(sessionId, false, false);
            const initialMessages = this.deps.mapMessagesForUi(this.deps.api.getCurrentSessionMessages(sessionId) || []);
            const selectedModel = this.deps.state.models.find((m) => m.id === this.deps.state.selectedModelId);
            this.deps.broadcastEvent('state_sync', {
                messages: initialMessages,
                isLoading: false,
                isStreaming: false,
                currentModel: selectedModel?.label || '',
                conversationId: sessionId,
            }, sessionId);

            try {
                const callbacks = this.createStreamCallbacks(sessionId, token);
                await this.deps.api.streamForResponse(sessionId, callbacks, 0, []);
                if (this.attachedStreamIds.has(sessionId) && (this.streamSessionToken.get(sessionId) || 0) === token) {
                    this.setStreamingState(sessionId, false, false);
                    this.attachedStreamIds.delete(sessionId);
                }
            } catch (error: any) {
                console.error('[Amaya MessageHandler] Stream attach error:', error.message);
                this.setStreamingState(sessionId, false, false);
                this.attachedStreamIds.delete(sessionId);
                this.deps.broadcastEvent('error', { message: error.message, conversationId: sessionId }, sessionId);
            }
        })();
    }

    private safeParseJson(str: string): Record<string, any> {
        try {
            return JSON.parse(str || '{}');
        } catch {
            return {};
        }
    }
}
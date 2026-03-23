import { IIDEClient, IDEConversationMode, IDEMessageAttachment } from '../../interfaces/IIDEClient';
import { IDEModel, IDEStreamCallbacks } from '../../interfaces/IDETypes';

export class StubIDEClient implements IIDEClient {
    private initialized = false;

    async initialize(): Promise<boolean> {
        this.initialized = true;
        return true;
    }

    isReady(): boolean {
        return this.initialized;
    }

    getDiagnostics(): string {
        return this.initialized ? 'Stub IDE ready' : 'Stub IDE not initialized';
    }

    setCredentials(_port: number, _csrfToken: string, _apiKey: string): void {
        this.initialized = true;
    }

    async startSession(): Promise<string | null> {
        return `stub-${Date.now()}`;
    }

    getLastActiveSessionId(): string | null {
        return null;
    }

    async getModels(): Promise<IDEModel[]> {
        return [
            {
                id: 'STUB_MODEL_1',
                label: 'Stub Model',
                isRecommended: true,
            },
        ];
    }

    async getConversationIds(): Promise<string[]> {
        return [];
    }

    async getWorkspaces(): Promise<Array<{ name: string; path: string; isCurrent: boolean }>> {
        return [];
    }

    async sendMessage(
        _sessionId: string,
        _text: string,
        _modelId?: string,
        _conversationMode?: IDEConversationMode,
        _attachments?: IDEMessageAttachment[]
    ): Promise<boolean> {
        return true;
    }

    async getSessionTrajectory(_sessionId: string): Promise<any[]> {
        return [];
    }

    async getSessionMessages(_sessionId: string): Promise<any[]> {
        return [];
    }

    getCurrentSessionMessages(_sessionId?: string): any[] | null {
        return [];
    }

    async streamForResponse(
        _sessionId: string,
        callbacks: IDEStreamCallbacks,
        _ignoreBeforeIndex?: number,
        _initialSteps?: any[]
    ): Promise<void> {
        callbacks.onStatusChange?.('STUB_STATUS_RUNNING');
        callbacks.onThinking?.('Stub provider streaming simulation', '0', true);
        callbacks.onTextDelta?.('Stub provider response');
        callbacks.onDone?.('Stub provider response', 'STUB_DONE');
        callbacks.onStatusChange?.('STUB_STATUS_IDLE');
    }

    async getConversationsMetadata(): Promise<Array<{ id: string; title: string; preview: string; workspacePath: string | null; lastModified: number; size: number }>> {
        return [];
    }

    stopStreaming(_sessionId?: string): void {}

    async respondToToolInteraction(
        _sessionId: string,
        _toolCallId: string,
        _accepted: boolean,
        _argumentsPayload?: Record<string, any>,
        _metadata?: Record<string, string>
    ): Promise<boolean> {
        return true;
    }

    getBackendName(): string {
        return 'Stub IDE';
    }
}

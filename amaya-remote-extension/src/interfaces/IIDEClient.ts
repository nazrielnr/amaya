import { IDEModel, IDEStreamCallbacks } from './IDETypes';

export type IDEConversationMode = 'planning' | 'fast';

export interface IDEMessageAttachment {
    mimeType: string;
    dataBase64: string;
    fileName?: string;
}

/**
 * IIDEClient.ts
 * 
 * The strict contract that every IDE Backend implementation must honor.
 * The VS Code Extension routing logic only interacts with these methods.
 */
export interface IIDEClient {
    /** 
     * Perform discovery, probe ports, authenticate, etc.
     * Returns true if successfully connected and ready to serve.
     */
    initialize(): Promise<boolean>;

    /** 
     * Returns true if the client is currently initialized and connected.
     */
    isReady(): boolean;

    /** 
     * Get diagnostic status string (for logs or UI debug) 
     */
    getDiagnostics(): string;

    /**
     * Manually set connection credentials.
     */
    setCredentials(port: number, csrfToken: string, apiKey: string): void;

    /**
        * Start a new chat session. Returns the unique session ID.
     */
    startSession(): Promise<string | null>;

        /**
        * Return the most recently active session, if known by the provider.
        */
        getLastActiveSessionId(): string | null;

    /**
     * Fetch all available AI models supported by this backend.
     */
    getModels(): Promise<IDEModel[]>;

    /**
     * Get history of conversation IDs.
     */
    getConversationIds(): Promise<string[]>;

    /**
     * Get list of all known workspaces connected to this backend.
     */
    getWorkspaces(): Promise<Array<{ name: string; path: string; isCurrent: boolean }>>;

    /**
     * Manually send a one-off text message (often without streaming callbacks).
     */
    sendMessage(sessionId: string, text: string, modelId?: string, conversationMode?: IDEConversationMode, attachments?: IDEMessageAttachment[]): Promise<boolean>;

    /**
    * Fetch all raw steps for a given session.
     */
    getSessionTrajectory(sessionId: string): Promise<any[]>;

    /**
    * Fetch mapped messages for a given session.
     */
    getSessionMessages(sessionId: string): Promise<any[]>;

    /**
     * If streaming, return the current hot messages.
     */
    getCurrentSessionMessages(sessionId?: string): any[] | null;

    /**
     * Main streaming entry hook. Initiates context loop and passes deltas via callbacks.
     */
    streamForResponse(
        sessionId: string,
        callbacks: IDEStreamCallbacks,
        ignoreBeforeIndex?: number,
        initialSteps?: any[]
    ): Promise<void>;

    /**
     * Get all metadata for all conversations in one go (optimized).
     */
    getConversationsMetadata(): Promise<Array<{
        id: string;
        title: string;
        preview: string;
        workspacePath: string | null;
        lastModified: number;
        size: number;
    }>>;

    /**
    * Abort ongoing stream processes. If sessionId is provided, only stops that one.
     */
    stopStreaming(sessionId?: string): void;

    /**
     * Respond to a pending tool interaction such as a terminal approval prompt.
     */
    respondToToolInteraction(
        sessionId: string,
        toolCallId: string,
        accepted: boolean,
        argumentsPayload?: Record<string, any>,
        metadata?: Record<string, string>
    ): Promise<boolean>;

    /**
     * Get the human-readable name of the detected backend/IDE.
     */
    getBackendName(): string;
}

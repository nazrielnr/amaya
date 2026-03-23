/**
 * IDETypes.ts
 * 
 * Defines pure, IDE-agnostic domain interfaces. 
 * Any AI backend must map their internal structures
 * to these standard definitions to communicate with the Amaya VS Code Extension.
 */

export interface IDEModel {
    id: string;               // Universal identifier (e.g. 'MODEL_PLACEHOLDER_M18')
    label: string;            // Human readable name
    supportsImages?: boolean; // Whether the model can handle image attachments
    isRecommended?: boolean;  // UI hint
    tagTitle?: string;
    quotaInfo?: {
        remainingFraction?: number;
        resetTime?: string;
    };
}

export interface IDEStreamCallbacks {
    onDone?: (text: string, reason?: string) => void;
    onError?: (err: string) => void;
    onTextDelta?: (text: string, stepIndex?: string) => void;
    onUserMessage?: (text: string, attachments?: IDEMessageAttachment[]) => void;
    onThinking?: (text: string, id?: string, isRunning?: boolean) => void;
    onToolCall?: (info: { id: string; name: string; args: string; status?: string; metadata?: Record<string, string> }) => void;
    onToolResult?: (name: string, result: string, id: string, isError: boolean) => void;
    onStateSync?: (messages: any[]) => void;
    onTitleGenerated?: (title: string) => void;
    onStatusChange?: (status: string) => void;
}

export interface IDEMessageAttachment {
    mimeType: string;
    dataBase64?: string;
    fileName?: string;
    uri?: string;
}

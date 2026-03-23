/**
 * AntigravityTypes.ts
 * 
 * Strict definitions mapped specifically to the Antigravity local Language Server backend.
 * These types govern how data looks coming directly out of the RPC streams.
 */

// ── Model Enum Mapping ──────────────────────────────────────────
// Server accepts enum string names natively via JSON serialization.
export const KNOWN_MODELS = [
    { id: 'MODEL_PLACEHOLDER_M18', label: 'Gemini 3 Flash' },
    { id: 'MODEL_PLACEHOLDER_M7', label: 'Gemini 3.1 Pro (High)' },
    { id: 'MODEL_PLACEHOLDER_M8', label: 'Gemini 3.1 Pro (Low)' },
    { id: 'MODEL_PLACEHOLDER_M26', label: 'Claude Sonnet 4.6 (Thinking)' },
    { id: 'MODEL_PLACEHOLDER_M21', label: 'Claude Opus 4.6 (Thinking)' }
];

export const DEFAULT_MODEL_ID = 'MODEL_PLACEHOLDER_M18';


export interface TrajectoryStep {
    type: string;
    status: string;
    metadata?: {
        createdAt?: string;
        source?: string;
        executionId?: string;
        toolCall?: { id: string; name: string; argumentsJson?: string };
        sourceTrajectoryStepInfo?: {
            trajectoryId?: string;
            stepIndex?: number;
            metadataIndex?: number;
            cascadeId?: string;
        };
    };
    userInput?: {
        items?: Array<{ text?: string; uri?: string; mimeType?: string; fileName?: string }>;
        media?: Array<{ mimeType?: string; inlineData?: string; uri?: string; fileName?: string }>;
        clientType?: string;
    };
    plannerResponse?: {
        response?: string;
        modifiedResponse?: string;
        thinking?: string;
        thinkingDuration?: string;
        messageId?: string;
        stopReason?: string;
        toolCalls?: Array<{
            id: string;
            name: string;
            argumentsJson: string;
        }>;
    };
    // All other step data is stored dynamically under their camelCase key
    // e.g. viewFile, listDirectory, codeAction, runCommand, taskBoundary, notifyUser
    [key: string]: any;
}

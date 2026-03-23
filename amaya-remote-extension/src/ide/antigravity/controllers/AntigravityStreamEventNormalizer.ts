import { IDEStreamCallbacks } from '../../../interfaces/IDETypes';
import { AntigravityMappers } from '../mappers/AntigravityMappers';
import { AntigravityStreamErrorAnalyzer } from './AntigravityStreamErrorAnalyzer';
import { AntigravityStreamStateController } from './AntigravityStreamStateController';
import { AntigravityStreamUserInputMapper } from './AntigravityStreamUserInputMapper';
import { ANTIGRAVITY_STEP_STATUS_VALUES, ANTIGRAVITY_STEP_TYPES } from '../core/AntigravityProtocol';

export interface NormalizeStreamFrameInput {
    data: any;
    cascadeId: string;
    callbacks: IDEStreamCallbacks;
    localSteps: Map<number, any>;
    currentTurnIgnoreBeforeIndex: number;
    hasStartedTurn: boolean;
    checkpointUpdatedDuringStream: boolean;
    emittedToolCalls: Set<string>;
    emittedToolResults: Set<string>;
    emittedToolStateSignatures: Map<string, string>;
    emittedThinkingSignatures: Map<number, string>;
    emittedTextSignatures: Map<number, string>;
    terminalToolCalls: Set<string>;
    emittedUserInputs: Set<number>;
    latestHotMessagesMap: Map<string, any[]>;
    latestHotMessagesTimestampMap: Map<string, number>;
}

export interface NormalizeStreamFrameResult {
    statusCandidate: string | null;
    sortedIndices: number[];
    sortedSteps: any[];
    hasStartedTurn: boolean;
    checkpointUpdatedDuringStream: boolean;
    newTurnFullText: string;
    quotaErrorText?: string;
}

export class AntigravityStreamEventNormalizer {
    public static normalizeFrame(input: NormalizeStreamFrameInput): NormalizeStreamFrameResult {
        const {
            data,
            cascadeId,
            callbacks,
            localSteps,
            currentTurnIgnoreBeforeIndex,
            emittedToolCalls,
            emittedToolResults,
            emittedToolStateSignatures,
            emittedThinkingSignatures,
            emittedTextSignatures,
            terminalToolCalls,
            emittedUserInputs,
            latestHotMessagesMap,
            latestHotMessagesTimestampMap,
        } = input;

        let hasStartedTurn = input.hasStartedTurn;
        let checkpointUpdatedDuringStream = input.checkpointUpdatedDuringStream;
        let newTurnFullText = '';

        const statusCandidate = AntigravityStreamStateController.extractStatusCandidate(data);
        const stepsUpdate = data.update?.agentStateUpdate?.mainTrajectoryUpdate?.stepsUpdate || data.update?.mainTrajectoryUpdate?.stepsUpdate;
        if (AntigravityStreamStateController.applyStepsUpdate(localSteps, stepsUpdate, currentTurnIgnoreBeforeIndex)) {
            checkpointUpdatedDuringStream = true;
        }

        const { sortedIndices, sortedSteps } = AntigravityStreamStateController.getSortedSteps(localSteps);

        if (callbacks.onStateSync) {
            latestHotMessagesMap.set(cascadeId, AntigravityMappers.stepsToMessages(sortedSteps));
            latestHotMessagesTimestampMap.set(cascadeId, Date.now());
        }

        sortedIndices.forEach(idx => {
            if (idx < currentTurnIgnoreBeforeIndex || emittedUserInputs.has(idx)) return;
            const s = localSteps.get(idx);
            const { text, attachments } = AntigravityStreamUserInputMapper.extractFromStep(s);
            if (text && text.trim()) {
                emittedUserInputs.add(idx);
                callbacks.onUserMessage?.(text.trim(), attachments.length > 0 ? attachments : undefined);
            }
        });

        sortedIndices.forEach(idx => {
            if (idx < currentTurnIgnoreBeforeIndex) return;
            const s = localSteps.get(idx);

            if (s.type === ANTIGRAVITY_STEP_TYPES.plannerResponse && s.plannerResponse) {
                const pr = s.plannerResponse;

                let handledAsThinkingTool = false;
                if (pr.thinking) {
                    const isRunning = s.status !== ANTIGRAVITY_STEP_STATUS_VALUES.done && s.status !== ANTIGRAVITY_STEP_STATUS_VALUES.failed;
                    handledAsThinkingTool = true;
                    const sig = JSON.stringify({ text: pr.thinking, isRunning });
                    if (emittedThinkingSignatures.get(idx) !== sig) {
                        emittedThinkingSignatures.set(idx, sig);
                        callbacks.onThinking?.(pr.thinking, idx.toString(), isRunning);
                        hasStartedTurn = true;
                    }
                }

                let text = pr.modifiedResponse || pr.response || '';
                if (!handledAsThinkingTool && pr.thinking && !text) {
                    text = pr.thinking;
                }

                if (text && pr.thinking && text.trim() === pr.thinking.trim()) {
                    text = '';
                }

                if (pr.toolCalls && pr.toolCalls.length > 0) {
                    const notificationTexts = pr.toolCalls
                        .filter((tc: any) => AntigravityMappers.stepTypeToToolName(tc.name || '') === 'notify_user')
                        .map((tc: any) => {
                            const args = AntigravityMappers.safeParseJson(tc.argumentsJson || '{}');
                            return args.Message || args.message || args.Content || args.content || '';
                        })
                        .filter((t: any) => t && String(t).trim().length > 0);

                    if (notificationTexts.length > 0) {
                        text += (text ? '\n\n---\n' : '') + notificationTexts.join('\n\n---\n');
                    }
                }

                if (text) {
                    text = text.replace(/\\0/g, '');
                    text = text.replace(/\[([^\]]+)\]\(cci:[^)]+\)/g, '$1');
                    text = text.replace(/cci:[^\s)]+/g, '');
                    newTurnFullText += (newTurnFullText ? '\n\n' : '') + text;

                    const stepSig = JSON.stringify({ text });
                    if (emittedTextSignatures.get(idx) !== stepSig) {
                        emittedTextSignatures.set(idx, stepSig);
                        callbacks.onTextDelta?.(text, idx.toString());
                        hasStartedTurn = true;
                    }
                }

                if (pr.toolCalls && pr.toolCalls.length > 0) {
                    pr.toolCalls.forEach((tc: any) => {
                        const amayaToolName = AntigravityMappers.stepTypeToToolName(tc.name || '');
                        if (amayaToolName === 'notify_user') return;

                        if (terminalToolCalls.has(tc.id)) return;
                        const priorSignature = emittedToolStateSignatures.get(tc.id) || '';
                        if (priorSignature.includes('"status":"SUCCESS"') || priorSignature.includes('"status":"ERROR"')) return;
                        const toolName = AntigravityMappers.stepTypeToToolName(tc.name || '');
                        const rawArgs = AntigravityMappers.safeParseJson(tc.argumentsJson || '{}');
                        const remappedArgs = AntigravityMappers.remapToolArgs(toolName, rawArgs);

                        const sig = JSON.stringify({ status: 'RUNNING', args: remappedArgs });
                        if (emittedToolStateSignatures.get(tc.id) !== sig) {
                            emittedToolStateSignatures.set(tc.id, sig);
                            callbacks.onToolCall?.({ id: tc.id, name: toolName, args: JSON.stringify(remappedArgs), status: 'RUNNING' });
                            hasStartedTurn = true;
                        }
                        if (!emittedToolCalls.has(tc.id)) {
                            emittedToolCalls.add(tc.id);
                        }
                    });
                }
            }

            if (AntigravityMappers.isToolExecutionStep(s.type)) {
                const toolCallId = s.metadata?.toolCall?.id || `stream-step-${idx}`;
                const toolName = AntigravityMappers.stepTypeToToolName(s.metadata?.toolCall?.name || s.type);
                let remappedArgs = AntigravityMappers.extractToolArgsFromStep(s);

                if (Object.keys(remappedArgs).length === 0 || (remappedArgs.command === undefined && remappedArgs.path === undefined)) {
                    const plannerStep = Array.from(localSteps.values())
                        .reverse()
                        .find(prev => prev.type === ANTIGRAVITY_STEP_TYPES.plannerResponse &&
                            prev.plannerResponse?.toolCalls?.some((tc: any) => tc.id === toolCallId));
                    if (plannerStep) {
                        const tc = plannerStep.plannerResponse.toolCalls.find((t: any) => t.id === toolCallId);
                        remappedArgs = AntigravityMappers.remapToolArgs(toolName, AntigravityMappers.safeParseJson(tc.argumentsJson || '{}'));
                    }
                }

                const toolMetadata = AntigravityMappers.extractToolExecutionMetadata(s, idx);
                const toolStatus = AntigravityMappers.mapStepStatus(s);

                const signature = JSON.stringify({ status: toolStatus, args: remappedArgs });

                if (emittedToolStateSignatures.get(toolCallId) !== signature) {
                    emittedToolStateSignatures.set(toolCallId, signature);
                    callbacks.onToolCall?.({
                        id: toolCallId,
                        name: toolName,
                        args: JSON.stringify(remappedArgs),
                        status: toolStatus,
                        metadata: toolMetadata,
                    });
                    hasStartedTurn = true;
                }

                if (toolStatus === 'SUCCESS' || toolStatus === 'ERROR') {
                    terminalToolCalls.add(toolCallId);
                }

                if (s.status === ANTIGRAVITY_STEP_STATUS_VALUES.done || s.status === ANTIGRAVITY_STEP_STATUS_VALUES.failed) {
                    if (!emittedToolResults.has(toolCallId)) {
                        emittedToolResults.add(toolCallId);
                        const result = AntigravityMappers.getToolResult(s);
                        callbacks.onToolResult?.(toolName, result, s.metadata?.toolCall?.id || '', s.status === ANTIGRAVITY_STEP_STATUS_VALUES.failed);
                        hasStartedTurn = true;
                    }
                }
            }

            if (s.type === ANTIGRAVITY_STEP_TYPES.checkpoint && s.status === ANTIGRAVITY_STEP_STATUS_VALUES.done) {
                if (s.checkpoint?.userIntent && !emittedToolCalls.has(`checkpoint-${idx}`)) {
                    emittedToolCalls.add(`checkpoint-${idx}`);
                    callbacks.onTitleGenerated?.(s.checkpoint.userIntent);
                }
            }
        });

        const quotaErrorText =
            newTurnFullText && AntigravityStreamErrorAnalyzer.isQuotaErrorText(newTurnFullText)
                ? newTurnFullText.substring(0, 500)
                : undefined;

        return {
            statusCandidate,
            sortedIndices,
            sortedSteps,
            hasStartedTurn,
            checkpointUpdatedDuringStream,
            newTurnFullText,
            quotaErrorText,
        };
    }
}

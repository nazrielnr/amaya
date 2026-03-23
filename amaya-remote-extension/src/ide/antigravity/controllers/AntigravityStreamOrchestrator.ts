import * as crypto from 'crypto';
import { IDEStreamCallbacks } from '../../../interfaces/IDETypes';
import { AntigravityMappers } from '../mappers/AntigravityMappers';
import { AntigravityStreamingTransport } from '../core/AntigravityStreamingTransport';
import { AntigravityStreamErrorAnalyzer } from './AntigravityStreamErrorAnalyzer';
import { AntigravityStreamEventNormalizer } from './AntigravityStreamEventNormalizer';
import { AntigravityStreamLifecycleController } from './AntigravityStreamLifecycleController';
import { AntigravityStreamStateController } from './AntigravityStreamStateController';
import { AntigravityStreamStateManager } from './AntigravityStreamStateManager';
import { ANTIGRAVITY_STATUS_VALUES } from '../core/AntigravityProtocol';
import { TrajectoryStep } from '../types/AntigravityTypes';

export interface AntigravityStreamOrchestratorInput {
    cascadeId: string;
    callbacks: IDEStreamCallbacks;
    ignoreBeforeIndex?: number;
    initialSteps?: any[];
    streamingTransport: AntigravityStreamingTransport;
    streamStateManager: AntigravityStreamStateManager;
    getSessionTrajectory: (cascadeId: string) => Promise<TrajectoryStep[]>;
    stopStreaming: (cascadeId?: string) => void;
    sleep: (ms: number) => Promise<void>;
}

export class AntigravityStreamOrchestrator {
    static async streamForResponse(input: AntigravityStreamOrchestratorInput): Promise<void> {
        const {
            cascadeId,
            callbacks,
            streamingTransport,
            streamStateManager,
            getSessionTrajectory,
            stopStreaming,
            sleep,
        } = input;

        const ignoreBeforeIndex = input.ignoreBeforeIndex ?? 0;
        const initialSteps = input.initialSteps ?? [];

        streamStateManager.markStreaming(cascadeId);
        const subscriberId = crypto.randomUUID();
        const localSteps: Map<number, any> = new Map();
        let currentTurnIgnoreBeforeIndex = ignoreBeforeIndex;
        const { latestHotMessagesMap, latestHotMessagesTimestampMap } = streamStateManager.getHotMessageMaps();

        if (initialSteps && initialSteps.length > 0) {
            initialSteps.forEach((step, idx) => localSteps.set(idx, step));
        }

        const emittedToolCalls = new Set<string>();
        const emittedToolResults = new Set<string>();
        const emittedToolStateSignatures = new Map<string, string>();
        const emittedThinkingSignatures = new Map<number, string>();
        const emittedTextSignatures = new Map<number, string>();
        const terminalToolCalls = new Set<string>();
        const emittedUserInputs = new Set<number>();
        let currentThinking = '';
        let currentFullText = '';
        let lastGlobalStatus: string = ANTIGRAVITY_STATUS_VALUES.loading;
        let isOfficiallyTerminated = false;
        let hasStartedTurn = false;
        let retryCount = 0;
        const MAX_RETRIES = 12;
        let checkpointUpdatedDuringStream = false;
        let lastDataTimestamp = Date.now();
        const ACTIVE_STALL_TIMEOUT = 15000;
        const IDLE_STALL_TIMEOUT = 120000;
        let abortDueToStall = false;
        let stallWhileIdle = false;
        let hasEmittedTermination = false;
        let consecutiveStalls = 0;
        const MAX_CONSECUTIVE_STALLS = 3;
        let idleDoneTimer: NodeJS.Timeout | null = null;

        const resetTurnState = (nextIgnoreBeforeIndex: number) => {
            currentTurnIgnoreBeforeIndex = nextIgnoreBeforeIndex;
            currentThinking = '';
            currentFullText = '';
            hasStartedTurn = false;
            checkpointUpdatedDuringStream = false;
            hasEmittedTermination = false;
            emittedToolCalls.clear();
            emittedToolResults.clear();
            emittedToolStateSignatures.clear();
            emittedThinkingSignatures.clear();
            emittedTextSignatures.clear();
            terminalToolCalls.clear();
            emittedUserInputs.clear();
            if (idleDoneTimer) {
                clearTimeout(idleDoneTimer);
                idleDoneTimer = null;
            }
        };

        while (streamStateManager.isStreaming(cascadeId) && !isOfficiallyTerminated && retryCount < MAX_RETRIES) {
            abortDueToStall = false;
            stallWhileIdle = false;
            await new Promise<void>((resolveIteration) => {
                const req = streamingTransport.stream(
                    cascadeId,
                    subscriberId,
                    {
                        onData: (data) => {
                            lastDataTimestamp = Date.now();
                            consecutiveStalls = 0;
                            if (idleDoneTimer) {
                                clearTimeout(idleDoneTimer);
                                idleDoneTimer = null;
                            }
                            try {
                                const normalized = AntigravityStreamEventNormalizer.normalizeFrame({
                                    data,
                                    cascadeId,
                                    callbacks,
                                    localSteps,
                                    currentTurnIgnoreBeforeIndex,
                                    hasStartedTurn,
                                    checkpointUpdatedDuringStream,
                                    emittedToolCalls,
                                    emittedToolResults,
                                    emittedToolStateSignatures,
                                    emittedThinkingSignatures,
                                    emittedTextSignatures,
                                    terminalToolCalls,
                                    emittedUserInputs,
                                    latestHotMessagesMap,
                                    latestHotMessagesTimestampMap,
                                });

                                const statusCandidate = normalized.statusCandidate;
                                hasStartedTurn = normalized.hasStartedTurn;
                                checkpointUpdatedDuringStream = normalized.checkpointUpdatedDuringStream;
                                const sortedIndices = normalized.sortedIndices;
                                const sortedSteps = normalized.sortedSteps;
                                const newTurnFullText = normalized.newTurnFullText;

                                if (statusCandidate && statusCandidate !== lastGlobalStatus) {
                                    lastGlobalStatus = statusCandidate;
                                    callbacks.onStatusChange?.(lastGlobalStatus);
                                }

                                if (normalized.quotaErrorText) {
                                    isOfficiallyTerminated = true;
                                    callbacks.onError?.(normalized.quotaErrorText);
                                    stopStreaming(cascadeId);
                                    resolveIteration();
                                    return;
                                }

                                if (newTurnFullText && newTurnFullText !== currentFullText) {
                                    currentFullText = newTurnFullText;
                                    if (AntigravityStreamErrorAnalyzer.isQuotaErrorText(currentFullText)) {
                                        isOfficiallyTerminated = true;
                                        callbacks.onError?.(currentFullText.substring(0, 500));
                                        stopStreaming(cascadeId);
                                        resolveIteration();
                                        return;
                                    }
                                }

                                const {
                                    anyStillProcessing,
                                    hasCheckpointDone,
                                    lastPlannerStep,
                                } = AntigravityStreamStateController.computeProgressState(localSteps, currentTurnIgnoreBeforeIndex);

                                if (!statusCandidate) {
                                    const derivedStatus = anyStillProcessing
                                        ? ANTIGRAVITY_STATUS_VALUES.running
                                        : (hasStartedTurn ? ANTIGRAVITY_STATUS_VALUES.idle : lastGlobalStatus);
                                    if (derivedStatus && derivedStatus !== lastGlobalStatus) {
                                        lastGlobalStatus = derivedStatus;
                                        callbacks.onStatusChange?.(lastGlobalStatus);
                                    }
                                }

                                if (!anyStillProcessing && hasStartedTurn && lastGlobalStatus === ANTIGRAVITY_STATUS_VALUES.idle && !hasEmittedTermination && !idleDoneTimer) {
                                    idleDoneTimer = setTimeout(() => {
                                        if (hasEmittedTermination || isOfficiallyTerminated || !streamStateManager.isStreaming(cascadeId)) return;
                                        if (lastGlobalStatus === ANTIGRAVITY_STATUS_VALUES.running) return;
                                        hasEmittedTermination = true;
                                        callbacks.onDone?.(currentFullText || currentThinking || '', 'IDLE_TIMER_FALLBACK');
                                        stopStreaming(cascadeId);
                                        const nextIgnoreBeforeIndex = localSteps.size > 0
                                            ? (Math.max(...Array.from(localSteps.keys())) + 1)
                                            : currentTurnIgnoreBeforeIndex;
                                        resetTurnState(nextIgnoreBeforeIndex);
                                    }, 1200);
                                }

                                const isClientCanceled = lastPlannerStep?.plannerResponse?.stopReason === 'STOP_REASON_CLIENT_CANCELED';
                                const progressDecision = AntigravityStreamLifecycleController.shouldTerminateByProgress({
                                    lastGlobalStatus,
                                    anyStillProcessing,
                                    hasStartedTurn,
                                    hasCheckpointDone,
                                    checkpointUpdatedDuringStream,
                                    isClientCanceled,
                                });

                                if (progressDecision.shouldTerminate) {
                                    if (hasEmittedTermination) {
                                        resolveIteration();
                                        return;
                                    }
                                    hasEmittedTermination = true;
                                    const stopReason = lastPlannerStep?.plannerResponse?.stopReason || (progressDecision.forceGlobalDone ? ANTIGRAVITY_STATUS_VALUES.done : 'NATURAL_TERMINATION');

                                    if (callbacks.onStateSync) {
                                        const syncMessages = streamStateManager.setHotMessages(cascadeId, AntigravityMappers.stepsToMessages(sortedSteps));
                                        callbacks.onStateSync(syncMessages);
                                    }
                                    callbacks.onDone?.(currentFullText, stopReason);
                                    const nextIgnoreBeforeIndex = sortedIndices.length > 0 ? (sortedIndices[sortedIndices.length - 1] + 1) : currentTurnIgnoreBeforeIndex;
                                    resetTurnState(nextIgnoreBeforeIndex);
                                }
                            } catch (e: any) {
                                console.error('[Antigravity Stream] Data processing error:', e.message);
                            }
                        },
                        onError: (err) => {
                            streamStateManager.clearRequest(cascadeId);
                            if (AntigravityStreamLifecycleController.shouldIgnoreTransportAbort({
                                isOfficiallyTerminated,
                                abortDueToStall,
                                err,
                            })) {
                                resolveIteration();
                                return;
                            }
                            console.error(`[Antigravity Stream] Stream error for ${cascadeId}:`, err);
                            callbacks.onError?.(err);
                            resolveIteration();
                        },
                        onEnd: () => {
                            streamStateManager.clearRequest(cascadeId);
                            if (AntigravityStreamLifecycleController.shouldEmitOnEndFallback({
                                abortDueToStall,
                                isOfficiallyTerminated,
                                hasStartedTurn,
                                hasEmittedTermination,
                                lastGlobalStatus,
                            })) {
                                hasEmittedTermination = true;
                                callbacks.onDone?.(currentFullText || currentThinking || '', 'STREAM_ENDED_IDLE_FALLBACK');
                                const nextIgnoreBeforeIndex = localSteps.size > 0
                                    ? (Math.max(...Array.from(localSteps.keys())) + 1)
                                    : currentTurnIgnoreBeforeIndex;
                                resetTurnState(nextIgnoreBeforeIndex);
                            }
                            resolveIteration();
                        }
                    }
                );
                if (!req) {
                    resolveIteration();
                    return;
                }

                streamStateManager.setRequest(cascadeId, req);

                const stallCheck = setInterval(() => {
                    const stallTimeout = hasStartedTurn ? ACTIVE_STALL_TIMEOUT : IDLE_STALL_TIMEOUT;
                    if (Date.now() - lastDataTimestamp > stallTimeout && !isOfficiallyTerminated) {
                        consecutiveStalls++;
                        abortDueToStall = true;

                        if (consecutiveStalls > MAX_CONSECUTIVE_STALLS) {
                            console.error(`[Antigravity Stream] Too many stalls for ${cascadeId}, giving up`);
                            callbacks.onError?.('Connection unstable - too many disconnections');
                            stopStreaming(cascadeId);
                            clearInterval(stallCheck);
                            resolveIteration();
                            return;
                        }

                        if (lastGlobalStatus === ANTIGRAVITY_STATUS_VALUES.idle) {
                            stallWhileIdle = true;
                        }
                        const r = streamStateManager.getRequest(cascadeId);
                        if (r) {
                            r.destroy();
                            streamStateManager.clearRequest(cascadeId);
                        }
                        clearInterval(stallCheck);
                        resolveIteration();
                    }
                }, 2000);

                req.on('close', () => clearInterval(stallCheck));
            });

            if (isOfficiallyTerminated) break;

            if (stallWhileIdle) {
                let nextIgnoreBeforeIndex = currentTurnIgnoreBeforeIndex;
                try {
                    const steps = await getSessionTrajectory(cascadeId);
                    nextIgnoreBeforeIndex = steps.length;
                    const outcome = AntigravityStreamLifecycleController.buildIdleStallOutcome({
                        steps,
                        currentFullText,
                        currentThinking,
                    });
                    if (outcome.asError) {
                        callbacks.onError?.(outcome.message);
                    } else {
                        callbacks.onDone?.(outcome.message || currentFullText || '', 'CASCADE_IDLE_STALL_NO_ERROR');
                    }
                } catch {
                    const fallback = currentFullText || currentThinking || '';
                    if (AntigravityStreamErrorAnalyzer.isLikelyErrorMessage(fallback)) {
                        callbacks.onError?.(fallback || 'Stream stalled');
                    } else {
                        callbacks.onDone?.(fallback, 'CASCADE_IDLE_STALL_NO_ERROR');
                    }
                }
                resetTurnState(nextIgnoreBeforeIndex);
                continue;
            }

            if (lastGlobalStatus === ANTIGRAVITY_STATUS_VALUES.canceled || lastGlobalStatus === ANTIGRAVITY_STATUS_VALUES.failed) {
                break;
            }

            retryCount++;
            if (AntigravityStreamLifecycleController.shouldRetryStreaming(retryCount, MAX_RETRIES)) {
                await sleep(1500);
            }
        }

        if (!isOfficiallyTerminated && (lastGlobalStatus === ANTIGRAVITY_STATUS_VALUES.failed || lastGlobalStatus === ANTIGRAVITY_STATUS_VALUES.canceled)) {
            callbacks.onError?.(AntigravityStreamLifecycleController.buildTerminalFailureMessage({
                lastGlobalStatus,
                localSteps,
                currentFullText,
                currentThinking,
            }));
        }

        if (!isOfficiallyTerminated && currentFullText) {
            callbacks.onDone?.(currentFullText, 'TIMEOUT_OR_TERMINATION_UNCERTAIN');
        }
    }
}

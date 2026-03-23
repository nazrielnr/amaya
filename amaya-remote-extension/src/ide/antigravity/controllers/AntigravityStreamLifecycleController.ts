import { AntigravityStreamErrorAnalyzer } from './AntigravityStreamErrorAnalyzer';
import { ANTIGRAVITY_STATUS_VALUES } from '../core/AntigravityProtocol';

export class AntigravityStreamLifecycleController {
    public static shouldTerminateByProgress(args: {
        lastGlobalStatus: string;
        anyStillProcessing: boolean;
        hasStartedTurn: boolean;
        hasCheckpointDone: boolean;
        checkpointUpdatedDuringStream: boolean;
        isClientCanceled: boolean;
    }): { shouldTerminate: boolean; forceGlobalDone: boolean } {
        const {
            lastGlobalStatus,
            anyStillProcessing,
            hasStartedTurn,
            hasCheckpointDone,
            checkpointUpdatedDuringStream,
            isClientCanceled,
        } = args;

        const forceGlobalDone = lastGlobalStatus === ANTIGRAVITY_STATUS_VALUES.done;
        const naturalDone = !anyStillProcessing && hasStartedTurn && (
            lastGlobalStatus === ANTIGRAVITY_STATUS_VALUES.idle ||
            hasCheckpointDone ||
            checkpointUpdatedDuringStream ||
            isClientCanceled
        );

        return { shouldTerminate: forceGlobalDone || naturalDone, forceGlobalDone };
    }

    public static shouldIgnoreTransportAbort(args: {
        isOfficiallyTerminated: boolean;
        abortDueToStall: boolean;
        err: string;
    }): boolean {
        const { isOfficiallyTerminated, abortDueToStall, err } = args;
        if (/aborted/i.test(err || '') && isOfficiallyTerminated) return true;
        if (/aborted/i.test(err || '') && abortDueToStall) return true;
        return false;
    }

    public static shouldEmitOnEndFallback(args: {
        abortDueToStall: boolean;
        isOfficiallyTerminated: boolean;
        hasStartedTurn: boolean;
        hasEmittedTermination: boolean;
        lastGlobalStatus: string;
    }): boolean {
        const {
            abortDueToStall,
            isOfficiallyTerminated,
            hasStartedTurn,
            hasEmittedTermination,
            lastGlobalStatus,
        } = args;

        return !abortDueToStall
            && !isOfficiallyTerminated
            && hasStartedTurn
            && !hasEmittedTermination
            && lastGlobalStatus !== ANTIGRAVITY_STATUS_VALUES.running;
    }

    public static buildIdleStallOutcome(args: {
        steps: any[];
        currentFullText: string;
        currentThinking: string;
    }): { asError: boolean; message: string } {
        const { steps, currentFullText, currentThinking } = args;
        const message = AntigravityStreamErrorAnalyzer.extractFailureMessage(
            new Map(steps.map((s, idx) => [idx, s] as any)),
            currentFullText || currentThinking
        );
        if (AntigravityStreamErrorAnalyzer.isLikelyErrorMessage(message)) {
            return { asError: true, message };
        }
        return { asError: false, message: message || currentFullText || '' };
    }

    public static buildTerminalFailureMessage(args: {
        lastGlobalStatus: string;
        localSteps: Map<number, any>;
        currentFullText: string;
        currentThinking: string;
    }): string {
        const { lastGlobalStatus, localSteps, currentFullText, currentThinking } = args;
        const message = AntigravityStreamErrorAnalyzer.extractFailureMessage(localSteps, currentFullText || currentThinking);
        if (AntigravityStreamErrorAnalyzer.isLikelyErrorMessage(message)) {
            return message;
        }
        return lastGlobalStatus === ANTIGRAVITY_STATUS_VALUES.canceled ? 'Request canceled' : 'Request failed';
    }

    public static shouldRetryStreaming(retryCount: number, maxRetries: number): boolean {
        return retryCount < maxRetries;
    }
}

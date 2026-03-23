import { ANTIGRAVITY_STEP_STATUS_VALUES, ANTIGRAVITY_STEP_TYPES } from '../core/AntigravityProtocol';

export class AntigravityStreamStateController {
    public static extractStatusCandidate(data: any): string | null {
        return (
            data.update?.status
            || data.update?.agentStateUpdate?.status
            || data.update?.mainTrajectoryUpdate?.status
            || data.update?.agentState?.status
            || data.update?.agentStateUpdate?.agentState?.status
            || null
        );
    }

    public static applyStepsUpdate(
        localSteps: Map<number, any>,
        stepsUpdate: any,
        minStepIndex: number
    ): boolean {
        if (!stepsUpdate || !stepsUpdate.steps || !stepsUpdate.indices) return false;

        let checkpointUpdated = false;
        for (let i = 0; i < stepsUpdate.steps.length; i++) {
            const step = stepsUpdate.steps[i];
            const stepIdx = stepsUpdate.indices[i] !== undefined ? stepsUpdate.indices[i] : -1;

            if (step.type === ANTIGRAVITY_STEP_TYPES.checkpoint && step.status === ANTIGRAVITY_STEP_STATUS_VALUES.done) {
                checkpointUpdated = true;
            }
            if (stepIdx < minStepIndex) continue;
            localSteps.set(stepIdx, step);
        }

        return checkpointUpdated;
    }

    public static getSortedSteps(localSteps: Map<number, any>): { sortedIndices: number[]; sortedSteps: any[] } {
        const sortedIndices = Array.from(localSteps.keys()).sort((a, b) => a - b);
        const sortedSteps = sortedIndices.map(idx => localSteps.get(idx));
        return { sortedIndices, sortedSteps };
    }

    public static computeProgressState(localSteps: Map<number, any>, minStepIndex: number): {
        anyStillProcessing: boolean;
        hasCheckpointDone: boolean;
        lastPlannerStep: any | undefined;
    } {
        const filtered = Array.from(localSteps.entries()).filter(([idx]) => idx >= minStepIndex);

        const anyStillProcessing = filtered.some(([_, s]) =>
            s.status === ANTIGRAVITY_STEP_STATUS_VALUES.running
            || s.status === ANTIGRAVITY_STEP_STATUS_VALUES.generating
        );

        const hasCheckpointDone = filtered.some(([_, s]) =>
            s.type === ANTIGRAVITY_STEP_TYPES.checkpoint && s.status === ANTIGRAVITY_STEP_STATUS_VALUES.done
        );

        const lastPlannerStep = filtered
            .map(([_, s]) => s)
            .reverse()
            .find(s => s.type === ANTIGRAVITY_STEP_TYPES.plannerResponse);

        return { anyStillProcessing, hasCheckpointDone, lastPlannerStep };
    }
}
import { AntigravityHttpCore } from '../core/AntigravityHttpCore';
import { AntigravityRpcClient } from '../core/AntigravityRpcClient';
import { ANTIGRAVITY_STEP_TYPES } from '../core/AntigravityProtocol';
import { TrajectoryStep } from '../types/AntigravityTypes';

type RunCommandInteraction = {
    trajectoryId: string;
    stepIndex: number;
    proposedCommandLine: string;
    submittedCommandLine: string;
};

export class AntigravityToolInteractionController {
    public static async resolveRunCommandInteraction(
        sessionId: string,
        toolCallId: string,
        argumentsPayload: Record<string, any>,
        metadata: Record<string, string>,
        getSessionTrajectory: (id: string) => Promise<TrajectoryStep[]>
    ): Promise<RunCommandInteraction | null> {
        const fromMetadata = this.buildRunCommandInteractionFromMetadata(argumentsPayload, metadata);
        if (fromMetadata) return fromMetadata;
        return this.findRunCommandInteractionFromSteps(sessionId, toolCallId, getSessionTrajectory);
    }

    public static async submitRunCommandInteraction(
        httpCore: AntigravityHttpCore,
        sessionId: string,
        accepted: boolean,
        interaction: RunCommandInteraction
    ): Promise<boolean> {
        const rpc = new AntigravityRpcClient(httpCore);
        const res = await rpc.handleCascadeUserInteraction({
            cascadeId: sessionId,
            interaction: {
                trajectoryId: interaction.trajectoryId,
                stepIndex: interaction.stepIndex,
                runCommand: {
                    ...(accepted ? { confirm: true } : {}),
                    proposedCommandLine: interaction.proposedCommandLine,
                    submittedCommandLine: interaction.submittedCommandLine,
                },
            },
        });

        if (!accepted) {
            await rpc.cancelCascadeInvocation(sessionId);
        }

        return res !== null || !accepted;
    }

    private static buildRunCommandInteractionFromMetadata(
        argumentsPayload: Record<string, any> = {},
        metadata: Record<string, string> = {}
    ): RunCommandInteraction | null {
        const trajectoryId = String(metadata.trajectoryId || '').trim();
        const stepIndex = Number.parseInt(String(metadata.stepIndex || ''), 10);
        if (!trajectoryId || Number.isNaN(stepIndex)) return null;

        const argCommand = String(
            argumentsPayload.commandLine
            || argumentsPayload.proposedCommandLine
            || argumentsPayload.submittedCommandLine
            || argumentsPayload.command
            || ''
        );

        return {
            trajectoryId,
            stepIndex,
            proposedCommandLine: String(metadata.proposedCommandLine || metadata.commandLine || metadata.submittedCommandLine || argCommand).trim(),
            submittedCommandLine: String(metadata.submittedCommandLine || metadata.commandLine || metadata.proposedCommandLine || argCommand).trim(),
        };
    }

    private static async findRunCommandInteractionFromSteps(
        sessionId: string,
        toolCallId: string,
        getSessionTrajectory: (id: string) => Promise<TrajectoryStep[]>
    ): Promise<RunCommandInteraction | null> {
        const steps = await getSessionTrajectory(sessionId);
        for (const step of steps) {
            if (step.type !== ANTIGRAVITY_STEP_TYPES.runCommand) continue;
            const stepToolCallId = step.metadata?.toolCall?.id;
            if (stepToolCallId !== toolCallId) continue;
            const sourceInfo = step.metadata?.sourceTrajectoryStepInfo;
            const runCommand = step.runCommand || {};
            const trajectoryId = sourceInfo?.trajectoryId || '';
            if (!trajectoryId || typeof sourceInfo?.stepIndex !== 'number') continue;
            const submittedCommandLine = String(runCommand.submittedCommandLine || runCommand.commandLine || runCommand.proposedCommandLine || '');
            const proposedCommandLine = String(runCommand.proposedCommandLine || submittedCommandLine);
            return {
                trajectoryId,
                stepIndex: sourceInfo.stepIndex,
                proposedCommandLine,
                submittedCommandLine,
            };
        }
        return null;
    }
}
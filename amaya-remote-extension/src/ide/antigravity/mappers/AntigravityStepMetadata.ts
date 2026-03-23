import { TrajectoryStep } from '../types/AntigravityTypes';
import { ANTIGRAVITY_STEP_STATUS_VALUES, ANTIGRAVITY_STEP_TYPE_PREFIX, ANTIGRAVITY_STEP_TYPES } from '../core/AntigravityProtocol';

export function getSourceStepInfo(step: TrajectoryStep, fallbackIndex: number = -1): { trajectoryId?: string; stepIndex: number; cascadeId?: string } {
    const sourceInfo = step.metadata?.sourceTrajectoryStepInfo;
    return {
        trajectoryId: sourceInfo?.trajectoryId,
        stepIndex: typeof sourceInfo?.stepIndex === 'number' ? sourceInfo.stepIndex : fallbackIndex,
        cascadeId: sourceInfo?.cascadeId,
    };
}

export function mapStepStatus(step: TrajectoryStep): 'PENDING' | 'RUNNING' | 'SUCCESS' | 'ERROR' {
    if (step.status === ANTIGRAVITY_STEP_STATUS_VALUES.failed) return 'ERROR';
    if (step.status === ANTIGRAVITY_STEP_STATUS_VALUES.done) return 'SUCCESS';
    if (step.status === ANTIGRAVITY_STEP_STATUS_VALUES.running || step.status === ANTIGRAVITY_STEP_STATUS_VALUES.generating) return 'RUNNING';
    return 'PENDING';
}

export function extractToolExecutionMetadata(step: TrajectoryStep, fallbackIndex: number = -1): Record<string, string> {
    const info = getSourceStepInfo(step, fallbackIndex);
    const metadata: Record<string, string> = {
        stepIndex: `${info.stepIndex}`,
    };
    if (info.trajectoryId) metadata.trajectoryId = info.trajectoryId;
    if (info.cascadeId) metadata.cascadeId = info.cascadeId;

    if (step.type === ANTIGRAVITY_STEP_TYPES.runCommand && step.runCommand) {
        metadata.isTerminal = 'true';
        if (step.runCommand.proposedCommandLine) metadata.proposedCommandLine = String(step.runCommand.proposedCommandLine);
        if (step.runCommand.submittedCommandLine) metadata.submittedCommandLine = String(step.runCommand.submittedCommandLine);
        if (step.runCommand.commandLine) metadata.commandLine = String(step.runCommand.commandLine);
        if (step.runCommand.cwd) metadata.cwd = String(step.runCommand.cwd);
        if (typeof step.runCommand.usedIdeTerminal !== 'undefined') metadata.usedIdeTerminal = String(!!step.runCommand.usedIdeTerminal);
        if (step.runCommand.autoRunDecision) metadata.autoRunDecision = String(step.runCommand.autoRunDecision);

        const autoRunDecision = String(step.runCommand.autoRunDecision || '');
        const submittedCommandLine = String(step.runCommand.submittedCommandLine || step.runCommand.commandLine || '');
        const proposedCommandLine = String(step.runCommand.proposedCommandLine || '');
        const approvalRequired = step.status === ANTIGRAVITY_STEP_STATUS_VALUES.pending
            || /DENY|PROMPT|BLOCK|CONFIRM|OFF/i.test(autoRunDecision)
            || (!!proposedCommandLine && !submittedCommandLine)
            || (!!proposedCommandLine && proposedCommandLine === submittedCommandLine && step.status !== ANTIGRAVITY_STEP_STATUS_VALUES.done && step.status !== ANTIGRAVITY_STEP_STATUS_VALUES.failed);
        metadata.approvalRequired = String(approvalRequired);
        metadata.approvalState = approvalRequired && step.status === ANTIGRAVITY_STEP_STATUS_VALUES.pending
            ? 'pending'
            : (approvalRequired ? 'resolved' : 'none');
    }

    return metadata;
}

export function mergeMessageMetadata(target: Record<string, string> | undefined, step: TrajectoryStep, fallbackIndex: number = -1): Record<string, string> {
    const info = getSourceStepInfo(step, fallbackIndex);
    const merged: Record<string, string> = { ...(target || {}) };
    const existingStart = Number.parseInt(merged.startStepIndex || '', 10);
    const existingEnd = Number.parseInt(merged.endStepIndex || '', 10);

    if (!Number.isNaN(info.stepIndex)) {
        merged.startStepIndex = `${Number.isNaN(existingStart) ? info.stepIndex : Math.min(existingStart, info.stepIndex)}`;
        merged.endStepIndex = `${Number.isNaN(existingEnd) ? info.stepIndex : Math.max(existingEnd, info.stepIndex)}`;
    }

    if (info.trajectoryId) merged.trajectoryId = info.trajectoryId;
    if (info.cascadeId) merged.cascadeId = info.cascadeId;

    return merged;
}

export function extractUserInputContent(step: TrajectoryStep): { content: string; metadata: Record<string, string> } {
    const items = step.userInput?.items || [];
    const metadata: Record<string, string> = {};
    const textParts = items
        .map(item => String(item?.text || '').trim())
        .filter(Boolean);

    const getItemUri = (item: any): string => {
        if (!item || typeof item !== 'object') return '';
        const candidates = [
            item.uri,
            item.imageUri,
            item.artifactUri,
            item.mediaArtifactUri,
            item.media?.uri,
        ];
        return candidates.find((value: any) => typeof value === 'string' && value.trim().length > 0) || '';
    };

    const imageItem = items.find(item => getItemUri(item));
    const imageUri = imageItem ? getItemUri(imageItem) : '';
    if (imageUri) {
        metadata.attachmentUri = imageUri;
        metadata.attachmentType = 'image';
        if (imageItem?.mimeType) metadata.attachmentMimeType = imageItem.mimeType;
        const fileName = imageItem?.fileName || imageUri.replace(/^.*[\\/]/, '');
        if (fileName) metadata.attachmentFileName = fileName;
    } else {
        const inlineImage = items.find(item => item?.mimeType && /^image\//i.test(String(item.mimeType)));
        if (inlineImage) {
            metadata.attachmentType = 'image';
            metadata.attachmentMimeType = String(inlineImage.mimeType);
            const fileName = typeof inlineImage?.fileName === 'string' ? inlineImage.fileName : '';
            if (fileName) metadata.attachmentFileName = fileName;
        }
    }

    const label = metadata.attachmentFileName || '';
    const content = textParts.join('\n\n').trim() || (imageUri ? `[Image${label ? `: ${label}` : ''}]` : '');
    return { content, metadata };
}

export function getToolResult(step: TrajectoryStep): string {
    const type = step.type;
    const status = step.status;
    if (status === ANTIGRAVITY_STEP_STATUS_VALUES.failed) {
        return `Error: ${step.error?.message || 'Tool failed'}`;
    }

    const camelKey = type.replace(ANTIGRAVITY_STEP_TYPE_PREFIX, '').toLowerCase().replace(/_([a-z])/g, (g) => g[1].toUpperCase());
    const toolData = (step as any)[camelKey];
    if (!toolData) return 'Success';

    if (type === ANTIGRAVITY_STEP_TYPES.runCommand) {
        const candidate = (
            toolData.combinedOutput?.full ??
            toolData.rawDebugOutput ??
            toolData.output ??
            toolData.stdout ??
            toolData.stderr ??
            toolData.result ??
            toolData.summary ??
            toolData.message ??
            toolData.text
        );
        if (candidate !== undefined && candidate !== null && String(candidate).trim().length > 0) {
            const raw = String(candidate);
            const cleaned = raw
                .replace(/\x1b\[[0-9;?]*[a-zA-Z]/g, '')
                .replace(/\x1b\][^\u0007]*\u0007/g, '')
                .replace(/[\x1b\u001b\u009b][[()#;?]*(?:[0-9]{1,4}(?:;[0-9]{0,4})*)?[0-9A-ORZcf-nqry=><]/g, '')
                .replace(/\r?\n/g, '\n')
                .replace(/\n{3,}/g, '\n\n')
                .trim();
            return cleaned.length > 0 ? cleaned : 'Done';
        }
        return 'Done';
    }
    if (type === ANTIGRAVITY_STEP_TYPES.viewFile) return toolData.content || 'File read successfully';
    if (type === ANTIGRAVITY_STEP_TYPES.listDirectory) return toolData.output || 'Directory listed';
    if (type === ANTIGRAVITY_STEP_TYPES.grepSearch) return toolData.output || 'Search complete';
    if (type === ANTIGRAVITY_STEP_TYPES.findByName) return toolData.output || 'Files found';
    if (type === ANTIGRAVITY_STEP_TYPES.codeAction) return 'File updated';
    if (type === ANTIGRAVITY_STEP_TYPES.replaceFileContent) {
        const tc = toolData.TargetContent || '';
        const rc = toolData.ReplacementContent || '';
        return `--- replace_file\n+++ replace_file\n- ${tc}\n+ ${rc}`;
    }
    if (type === ANTIGRAVITY_STEP_TYPES.multiReplaceFileContent) {
        const chunks = toolData.ReplacementChunks || [];
        return chunks.map((c: any) => `- ${c.TargetContent}\n+ ${c.ReplacementContent}`).join('\n\n');
    }
    if (type === ANTIGRAVITY_STEP_TYPES.viewCodeItem) return toolData.content || 'Code item viewed';
    if (type === ANTIGRAVITY_STEP_TYPES.viewFileOutline) return toolData.outline || 'File outline viewed';
    if (type === ANTIGRAVITY_STEP_TYPES.generateImage) return `Image: ${toolData.prompt}\nURL: ${toolData.imageUrl || ''}`;
    if (type === ANTIGRAVITY_STEP_TYPES.browserSubagent) return toolData.report || 'Browser task complete';
    if (type === ANTIGRAVITY_STEP_TYPES.taskBoundary) return `Task: ${toolData.TaskName}\nSummary: ${toolData.TaskSummary}\nStatus: ${toolData.TaskStatus}`;
    if (type === ANTIGRAVITY_STEP_TYPES.notifyUser) return toolData.Message || 'User notified';

    return toolData.output || toolData.content || toolData.report || toolData.result || toolData.summary || 'Completed';
}
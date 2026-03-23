import { TrajectoryStep } from '../types/AntigravityTypes';
import { ANTIGRAVITY_STEP_STATUS_VALUES, ANTIGRAVITY_STEP_TYPES, ANTIGRAVITY_TOOL_MARKERS } from '../core/AntigravityProtocol';

export interface AntigravityMessageComposerContext {
    safeParseJson(str: string): Record<string, any>;
    stepTypeToToolName(stepType: string): string;
    remapToolArgs(name: string, args: Record<string, any>): Record<string, any>;
    mergeMessageMetadata(target: Record<string, string> | undefined, step: TrajectoryStep, fallbackIndex?: number): Record<string, string>;
    extractThoughtTitle(markdown: string): string | undefined;
    extractToolExecutionMetadata(step: TrajectoryStep, fallbackIndex?: number): Record<string, string>;
    extractToolArgsFromStep(step: TrajectoryStep): Record<string, any>;
    mapStepStatus(step: TrajectoryStep): 'PENDING' | 'RUNNING' | 'SUCCESS' | 'ERROR';
    getToolResult(step: TrajectoryStep): string;
    isToolExecutionStep(stepType: string): boolean;
}

export function buildAntigravityMessagesFromSteps(
    steps: TrajectoryStep[],
    context: AntigravityMessageComposerContext
): Array<{
    role: 'user' | 'assistant';
    content: string;
    thinking?: string;
    toolCalls?: Array<{ id: string; name: string; args: string; result?: string; isError?: boolean; status?: string; metadata?: Record<string, string> }>;
    intent?: string;
    metadata?: Record<string, string>;
    attachments?: Array<{ mimeType: string; dataBase64?: string; fileName?: string; uri?: string }>;
}> {
    const messages: Array<{
        role: 'user' | 'assistant';
        content: string;
        thinking?: string;
        toolCalls?: Array<{ id: string; name: string; args: string; result?: string; isError?: boolean; status?: string; metadata?: Record<string, string> }>;
        intent?: string;
        metadata?: Record<string, string>;
        attachments?: Array<{ mimeType: string; dataBase64?: string; fileName?: string; uri?: string }>;
    }> = [];

    const resultsMap: Record<string, { result: string; isError: boolean; status: 'PENDING' | 'RUNNING' | 'SUCCESS' | 'ERROR'; metadata: Record<string, string>; arguments: Record<string, any> }> = {};
    for (let rawIndex = 0; rawIndex < steps.length; rawIndex++) {
        const step = steps[rawIndex];
        if (context.isToolExecutionStep(step.type)) {
            const callId = step.metadata?.toolCall?.id;
            if (callId) {
                resultsMap[callId] = {
                    result: context.getToolResult(step),
                    isError: step.status === ANTIGRAVITY_STEP_STATUS_VALUES.failed,
                    status: context.mapStepStatus(step),
                    metadata: context.extractToolExecutionMetadata(step, rawIndex),
                    arguments: context.extractToolArgsFromStep(step),
                };
            }
        }
    }

    const emittedToolIds = new Set<string>();
    const emittedThinkingContent = new Set<string>();

    for (let rawIndex = 0; rawIndex < steps.length; rawIndex++) {
        const step = steps[rawIndex];
        if (step.type === ANTIGRAVITY_STEP_TYPES.userInput) {
            const userInput = extractUserInputContent(step, context);
            const mergedMetadata = context.mergeMessageMetadata(userInput.metadata, step, rawIndex);

            const attachments: Array<{ mimeType: string; dataBase64?: string; fileName?: string; uri?: string }> = [];
            const mediaArray = step.userInput?.media || [];
            for (const media of mediaArray) {
                if (media?.mimeType && (media.inlineData || media.uri)) {
                    let dataBase64 = media.inlineData;
                    if (!dataBase64 && media.uri) {
                        try {
                            const fs = require('fs');
                            const filePath = media.uri.replace(/^file:\/\//, '').replace(/\//g, '\\');
                            if (fs.existsSync(filePath)) {
                                const buffer = fs.readFileSync(filePath);
                                dataBase64 = buffer.toString('base64');
                            }
                        } catch {
                            // Ignore file read errors
                        }
                    }

                    attachments.push({
                        mimeType: media.mimeType,
                        dataBase64,
                        fileName: media.fileName,
                        uri: media.uri,
                    });
                }
            }

            if (userInput.content || Object.keys(userInput.metadata).length > 0 || attachments.length > 0) {
                messages.push({ role: 'user', content: userInput.content, metadata: mergedMetadata, attachments });
            }
            emittedToolIds.clear();
            emittedThinkingContent.clear();
        } else if (step.type === ANTIGRAVITY_STEP_TYPES.plannerResponse) {
            const pr = step.plannerResponse;
            if (!pr) { continue; }

            let text = pr.modifiedResponse || pr.response || '';
            if (text) {
                text = text.replace(/\\0/g, '');
                text = text.replace(/\[([^\]]+)\]\(cci:[^)]+\)/g, '$1');
                text = text.replace(/cci:[^\s)]+/g, '');
            }

            const notificationTexts = (pr.toolCalls || [])
                .filter(tc => context.stepTypeToToolName(tc.name || '') === 'notify_user')
                .map(tc => {
                    const res = resultsMap[tc.id];
                    const args = context.safeParseJson(tc.argumentsJson || '{}');
                    const argsText = args.Message || args.message || args.Content || args.content || '';
                    const resText = res?.result || '';
                    const isGenericResult = typeof resText === 'string' && resText.trim().toLowerCase() === 'user notified';
                    return (String(argsText).trim() ? argsText : (isGenericResult ? '' : resText)) || '';
                })
                .filter(t => t && String(t).trim().length > 0);

            if (notificationTexts.length > 0) {
                text += (text ? '\n\n---\n' : '') + notificationTexts.join('\n\n---\n');
            }

            const toolCalls = (pr.toolCalls || [])
                .filter(tc => {
                    if (context.stepTypeToToolName(tc.name || '') === 'notify_user') return false;
                    if (emittedToolIds.has(tc.id)) return false;
                    emittedToolIds.add(tc.id);
                    return true;
                })
                .map(tc => {
                    const amayaName = context.stepTypeToToolName(tc.name || '');
                    const rawArgs = context.safeParseJson(tc.argumentsJson || '{}');
                    const res = resultsMap[tc.id];
                    const plannerArgs = context.remapToolArgs(amayaName, rawArgs);
                    const remappedArgs = res?.arguments && Object.keys(res.arguments).length > 0
                        ? ({ ...plannerArgs, ...res.arguments })
                        : plannerArgs;
                    return {
                        id: tc.id,
                        name: amayaName,
                        args: JSON.stringify(remappedArgs),
                        result: res?.result,
                        isError: res?.isError,
                        status: res?.status,
                        metadata: res?.metadata,
                    };
                });

            let currentThinking = pr.thinking || '';
            let thinkingToolCall: typeof toolCalls[0] | null = null;

            if (currentThinking.trim()) {
                const sInfo = step.metadata?.sourceTrajectoryStepInfo;
                const stepIndex = sInfo?.stepIndex;
                const normalizedContent = currentThinking.trim().toLowerCase().substring(0, 1000);
                const shortContent = currentThinking.trim().substring(0, 100);
                const hashedId = Buffer.from(shortContent).toString('base64').substring(0, 12);
                const stableId = typeof stepIndex === 'number' ? `thinking-${stepIndex}` : `thinking-h-${hashedId}`;
                const isNewId = !emittedToolIds.has(stableId);
                const isNewContent = !emittedThinkingContent.has(normalizedContent);

                if (isNewId && isNewContent) {
                    const thoughtTitle = context.extractThoughtTitle(currentThinking) || 'Thinking';
                    const thinkingToolId = stableId;
                    emittedToolIds.add(thinkingToolId);
                    emittedThinkingContent.add(normalizedContent);

                    const isGenerating = step.status === ANTIGRAVITY_STEP_STATUS_VALUES.generating;
                    const toolStatus = isGenerating ? 'RUNNING' : 'SUCCESS';

                    thinkingToolCall = {
                        id: thinkingToolId,
                        name: ANTIGRAVITY_TOOL_MARKERS.thinkingToolName,
                        args: JSON.stringify({ thinking: currentThinking, thoughtTitle }),
                        result: currentThinking,
                        isError: false,
                        status: toolStatus as any,
                        metadata: {
                            [ANTIGRAVITY_TOOL_MARKERS.thinkingToolMetaKey]: 'true',
                            thoughtTitle,
                        },
                    };
                }

                if (thinkingToolCall) {
                    const lastMsg = messages.length > 0 ? messages[messages.length - 1] : null;
                    const canMerge = lastMsg && lastMsg.role === 'assistant' && !lastMsg.toolCalls?.some(tc => tc.metadata?.[ANTIGRAVITY_TOOL_MARKERS.thinkingToolMetaKey] !== 'true') && !lastMsg.content?.trim();
                    let thinkingMsg = canMerge ? lastMsg : null;
                    if (!thinkingMsg) {
                        thinkingMsg = { role: 'assistant', content: '', toolCalls: [], metadata: context.mergeMessageMetadata(undefined, step, rawIndex) };
                        messages.push(thinkingMsg);
                    }
                    thinkingMsg.toolCalls = (thinkingMsg.toolCalls || []).filter(tc => tc.metadata?.[ANTIGRAVITY_TOOL_MARKERS.thinkingToolMetaKey] !== 'true');
                    thinkingMsg.toolCalls.push(thinkingToolCall);
                    thinkingMsg.metadata = context.mergeMessageMetadata(thinkingMsg.metadata, step, rawIndex);
                }
            }

            if (text) {
                const lastMsg = messages.length > 0 ? messages[messages.length - 1] : null;
                const canMergeText = lastMsg && lastMsg.role === 'assistant' && !lastMsg.toolCalls?.some(tc => tc.metadata?.[ANTIGRAVITY_TOOL_MARKERS.thinkingToolMetaKey] !== 'true');
                let textMsg = canMergeText ? lastMsg : null;
                if (!textMsg) {
                    textMsg = { role: 'assistant', content: '', metadata: context.mergeMessageMetadata(undefined, step, rawIndex) };
                    messages.push(textMsg);
                }
                textMsg.content += (textMsg.content ? '\n\n' : '') + text;
                textMsg.metadata = context.mergeMessageMetadata(textMsg.metadata, step, rawIndex);
            }

            if (toolCalls.length > 0) {
                const lastMsg = messages.length > 0 ? messages[messages.length - 1] : null;
                const canMergeTool = lastMsg && lastMsg.role === 'assistant' && !lastMsg.content?.trim();
                let toolMsg = canMergeTool ? lastMsg : null;
                if (!toolMsg) {
                    toolMsg = { role: 'assistant', content: '', toolCalls: [], metadata: context.mergeMessageMetadata(undefined, step, rawIndex) };
                    messages.push(toolMsg);
                }
                toolMsg.toolCalls = [...(toolMsg.toolCalls || []), ...toolCalls];
                toolMsg.metadata = context.mergeMessageMetadata(toolMsg.metadata, step, rawIndex);
            }
        } else if (step.type === ANTIGRAVITY_STEP_TYPES.checkpoint) {
            const intent = step.checkpoint?.userIntent || '';
            if (intent && messages.length > 0) {
                for (let i = messages.length - 1; i >= 0; i--) {
                    if (messages[i].role === 'assistant') {
                        messages[i].intent = intent;
                        messages[i].metadata = context.mergeMessageMetadata(messages[i].metadata, step, rawIndex);
                        break;
                    }
                }
            }
        } else if (context.isToolExecutionStep(step.type) && messages.length > 0) {
            for (let i = messages.length - 1; i >= 0; i--) {
                if (messages[i].role === 'assistant') {
                    messages[i].metadata = context.mergeMessageMetadata(messages[i].metadata, step, rawIndex);
                    break;
                }
            }
        }
    }

    return messages;
}

function extractUserInputContent(
    step: TrajectoryStep,
    context: AntigravityMessageComposerContext
): { content: string; metadata: Record<string, string> } {
    const items = step.userInput?.items || [];
    const metadata: Record<string, string> = {};
    const textParts = items
        .map(item => String(item?.text || '').trim())
        .filter(Boolean);

    const getItemUri = (item: any): string => {
        if (!item || typeof item !== 'object') return '';
        const candidates = [item.uri, item.imageUri, item.artifactUri, item.mediaArtifactUri, item.media?.uri];
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
import { TrajectoryStep } from '../types/AntigravityTypes';
import {
    extractToolExecutionMetadata,
    extractUserInputContent,
    getSourceStepInfo,
    getToolResult,
    mapStepStatus,
    mergeMessageMetadata,
} from './AntigravityStepMetadata';
import {
    extractToolArgsFromStep,
    isToolExecutionStep,
    remapToolArgs,
    stepTypeToToolName,
} from './AntigravityToolMapping';
import { buildAntigravityMessagesFromSteps } from './AntigravityMessageComposer';

/**
 * AntigravityMappers.ts
 * 
 * Centralizes all logic for transforming Antigravity's CORTEX JSON stream objects 
 * into generic UI arrays and parameters expected by the Amaya UI frontend.
 */
export class AntigravityMappers {
    static safeParseJson(str: string): Record<string, any> {
        try {
            const parsed = JSON.parse(str);
            return typeof parsed === 'object' && parsed !== null ? parsed : {};
        } catch {
            return {};
        }
    }

    static stepTypeToToolName(stepType: string): string {
        return stepTypeToToolName(stepType);
    }

    static remapToolArgs(name: string, args: Record<string, any>): Record<string, any> {
        return remapToolArgs(name, args);
    }

    static extractToolArgsFromStep(step: TrajectoryStep): Record<string, any> {
        return extractToolArgsFromStep(step);
    }

    static getSourceStepInfo(step: TrajectoryStep, fallbackIndex: number = -1): { trajectoryId?: string; stepIndex: number; cascadeId?: string } {
        return getSourceStepInfo(step, fallbackIndex);
    }

    static mapStepStatus(step: TrajectoryStep): 'PENDING' | 'RUNNING' | 'SUCCESS' | 'ERROR' {
        return mapStepStatus(step);
    }

    static extractToolExecutionMetadata(step: TrajectoryStep, fallbackIndex: number = -1): Record<string, string> {
        return extractToolExecutionMetadata(step, fallbackIndex);
    }

    public static extractThoughtTitle(markdown: string): string | undefined {
        const lines = markdown.trim().split('\n');
        if (lines.length === 0) return undefined;
        
        // Find ALL bold matches and take the LAST one
        const boldRegex = /(\*\*|__)(.*?)\1/g;
        let lastMatch: string | undefined = undefined;
        
        for (const line of lines) {
            let match;
            while ((match = boldRegex.exec(line)) !== null) {
                const title = match[2].trim();
                if (title.length > 0) {
                    lastMatch = title;
                }
            }
        }
        
        if (lastMatch) return lastMatch;

        // Fallback: Use the first sentence or truncated paragraph
        let firstLine = lines[0].trim();
        // Remove list markers if present
        firstLine = firstLine.replace(/^[-*]\s+/, '');
        
        let truncated = firstLine;
        const sentenceEnd = firstLine.match(/[.!?]/);
        if (sentenceEnd && sentenceEnd.index !== undefined) {
            truncated = firstLine.substring(0, sentenceEnd.index + 1);
        }
        
        if (truncated.length > 50) {
            truncated = truncated.substring(0, 47).trim() + '...';
        }
        
        return truncated.trim() || undefined;
    }

    static mergeMessageMetadata(target: Record<string, string> | undefined, step: TrajectoryStep, fallbackIndex: number = -1): Record<string, string> {
        return mergeMessageMetadata(target, step, fallbackIndex);
    }

    static extractUserInputContent(step: TrajectoryStep): { content: string; metadata: Record<string, string> } {
        return extractUserInputContent(step);
    }

    static getToolResult(step: TrajectoryStep): string {
        return getToolResult(step);
    }

    static isToolExecutionStep(stepType: string): boolean {
        return isToolExecutionStep(stepType);
    }

    static stepsToMessages(steps: TrajectoryStep[]): Array<{
        role: 'user' | 'assistant';
        content: string;
        thinking?: string;
        toolCalls?: Array<{ id: string; name: string; args: string; result?: string; isError?: boolean; status?: string; metadata?: Record<string, string> }>;
        intent?: string;
        metadata?: Record<string, string>;
        attachments?: Array<{ mimeType: string; dataBase64?: string; fileName?: string; uri?: string }>;
    }> {
        return buildAntigravityMessagesFromSteps(steps, {
            safeParseJson: this.safeParseJson.bind(this),
            stepTypeToToolName: this.stepTypeToToolName.bind(this),
            remapToolArgs: this.remapToolArgs.bind(this),
            mergeMessageMetadata: this.mergeMessageMetadata.bind(this),
            extractThoughtTitle: this.extractThoughtTitle.bind(this),
            extractToolExecutionMetadata: this.extractToolExecutionMetadata.bind(this),
            extractToolArgsFromStep: this.extractToolArgsFromStep.bind(this),
            mapStepStatus: this.mapStepStatus.bind(this),
            getToolResult: this.getToolResult.bind(this),
            isToolExecutionStep: this.isToolExecutionStep.bind(this),
        });
    }
}

import * as vscode from 'vscode';
import * as path from 'path';
import * as os from 'os';
import { AntigravityStreamOrchestrator } from './controllers/AntigravityStreamOrchestrator';

import { IDEConversationMode, IDEMessageAttachment, IIDEClient } from '../../interfaces/IIDEClient';
import { IDEModel, IDEStreamCallbacks } from '../../interfaces/IDETypes';
import { IIDEDiscovery } from '../../interfaces/IIDEDiscovery';

import { AntigravityDiscoveryAdapter } from './discovery/AntigravityDiscoveryAdapter';
import { AntigravityHttpCore } from './core/AntigravityHttpCore';
import { AntigravityRpcClient } from './core/AntigravityRpcClient';
import { AntigravityMappers } from './mappers/AntigravityMappers';
import { AntigravityMediaController } from './controllers/AntigravityMediaController';
import { AntigravitySessionManager } from './controllers/AntigravitySessionManager';
import { AntigravityStreamStateManager } from './controllers/AntigravityStreamStateManager';
import { AntigravityStreamUserInputMapper } from './controllers/AntigravityStreamUserInputMapper';
import { AntigravityToolInteractionController } from './controllers/AntigravityToolInteractionController';
import { AntigravityStreamingTransport } from './core/AntigravityStreamingTransport';
import { TrajectoryStep, KNOWN_MODELS, DEFAULT_MODEL_ID } from './types/AntigravityTypes';
import { ANTIGRAVITY_CLIENT_CONSTANTS, ANTIGRAVITY_CONVERSATION_CONSTANTS, ANTIGRAVITY_MODEL_IDS, ANTIGRAVITY_POLICIES, ANTIGRAVITY_SOURCES } from './core/AntigravityProtocol';

/**
 * AntigravityClient.ts
 * 
 * The ultimate Facade for the Antigravity Language Server.
 * Implements the strict IIDEClient interface, isolating all internal Google-specific
 * logic, protocols, and payloads from the rest of the VS Code extension.
 */
export class AntigravityClient implements IIDEClient {
    private readonly sessionManager: AntigravitySessionManager;
    private readonly streamStateManager: AntigravityStreamStateManager;

    constructor(private readonly discovery: IIDEDiscovery = new AntigravityDiscoveryAdapter()) {
        this.sessionManager = new AntigravitySessionManager(this.discovery);
        this.streamStateManager = new AntigravityStreamStateManager();
    }

    private get httpCore(): AntigravityHttpCore | null {
        return this.sessionManager.getHttpCore();
    }

    private get streamingTransport(): AntigravityStreamingTransport | null {
        return this.sessionManager.getStreamingTransport();
    }

    private get initialized(): boolean {
        return this.sessionManager.isReady();
    }

    private sleep(ms: number): Promise<void> {
        return new Promise(r => setTimeout(r, ms));
    }

    private get rpc(): AntigravityRpcClient | null {
        if (!this.httpCore) return null;
        return new AntigravityRpcClient(this.httpCore);
    }

    public lastActiveCascadeId: string | null = null;
    async initialize(): Promise<boolean> {
        return this.sessionManager.initialize();
    }

    setCredentials(port: number, csrfToken: string, apiKey: string): void {
        this.sessionManager.setCredentials(port, csrfToken, apiKey);
    }

    isReady(): boolean {
        return this.sessionManager.isReady();
    }

    getBackendName(): string {
        const ide = this.discovery.getHostIDEEnv();
        if (ide === 'Antigravity') return ANTIGRAVITY_CLIENT_CONSTANTS.backendDisplayName;
        if (ide === 'Windsurf') return 'Windsurf (Forks)';
        return ide;
    }

    getDiagnostics(): string {
        if (!this.httpCore) return "NOT INITIALIZED";
        return [
            `IDE: ${this.getBackendName()}`,
            `Protocol: ${this.httpCore.getUseHttps() ? 'HTTPS' : 'HTTP'}`,
            `Port: ${this.httpCore.getPort()}`,
            `CSRF: ${this.httpCore.getCsrfToken() ? 'Γ£à' : 'Γ¥î'}`,
            `API Key: ${this.httpCore.getApiKey() ? 'Γ£à' : 'Γ¥î'}`,
            `Initialized: ${this.initialized}`,
        ].join('\n');
    }

    getLastActiveSessionId(): string | null {
        return this.lastActiveCascadeId;
    }

    async startSession(): Promise<string | null> {
        if (!this.rpc) return null;
        const res = await this.rpc.startCascade({
            metadata: this.rpc.getMetadata(),
            source: ANTIGRAVITY_SOURCES.cascadeClient,
        });
        const id = res?.cascadeId || null;
        if (id) {
            this.lastActiveCascadeId = id;
        }
        return id;
    }

    private extractNumericModelId(modelId: string): number {
        if (!modelId) return 1018;
        if (modelId === ANTIGRAVITY_MODEL_IDS.claudeSonnetThinking) return 334;
        if (modelId === ANTIGRAVITY_MODEL_IDS.claudeOpusThinking) return 291;
        if (modelId === ANTIGRAVITY_MODEL_IDS.openaiGptOss120bMedium) return 342;
        const mMatch = modelId.match(/_M(\d+)$/);
        if (mMatch) return 1000 + parseInt(mMatch[1], 10);
        const num = parseInt(modelId, 10);
        if (!isNaN(num)) return num;
        return 1018;
    }

    private buildConversationConfig(modelId: string, conversationMode: IDEConversationMode) {
        return {
            plannerConfig: {
                conversational: {
                    plannerMode: ANTIGRAVITY_CONVERSATION_CONSTANTS.plannerModeDefault,
                    agenticMode: conversationMode !== 'fast',
                },
                toolConfig: {
                    runCommand: {
                        autoCommandConfig: {
                            userAllowlist: ['node', 'Get-Content'],
                            autoExecutionPolicy: ANTIGRAVITY_POLICIES.autoExecutionOff,
                        },
                    },
                    notifyUser: {
                        artifactReviewMode: ANTIGRAVITY_CONVERSATION_CONSTANTS.artifactReviewAlways,
                    },
                },
                requestedModel: { model: modelId || DEFAULT_MODEL_ID },
                ephemeralMessagesConfig: { enabled: true },
                knowledgeConfig: { enabled: true },
            },
            conversationHistoryConfig: { enabled: true },
        };
    }

    async sendMessage(
        cascadeId: string,
        text: string,
        modelId: string = DEFAULT_MODEL_ID,
        conversationMode: IDEConversationMode = 'planning',
        attachments: IDEMessageAttachment[] = []
    ): Promise<boolean> {
        if (!this.rpc) return false;
        this.lastActiveCascadeId = cascadeId;
        const cleanedText = String(text || '');
        const textItems = cleanedText.trim() ? [{ text: cleanedText }] : [];

        // Log attachment processing for debugging
        if (attachments.length > 0) {
            console.log(`[AntigravityClient] Processing ${attachments.length} attachment(s)`);
        }

        const media = await AntigravityMediaController.buildOutboundMedia(attachments, this.httpCore!);

        if (textItems.length === 0 && media.length === 0) return false;

        if (media.length > 0) {
            console.log(`[AntigravityClient] Sending message with ${media.length} media attachment(s)`);
        }

        const res = await this.rpc.sendUserCascadeMessage({
            metadata: this.rpc.getMetadata(),
            cascadeId,
            items: textItems,
            cascadeConfig: this.buildConversationConfig(modelId, conversationMode),
            clientType: 'CHAT_CLIENT_REQUEST_STREAM_CLIENT_TYPE_IDE',
            ...(media.length > 0 ? { media } : {}),
        });
        return res !== null;
    }

    async respondToToolInteraction(
        cascadeId: string,
        toolCallId: string,
        accepted: boolean,
        argumentsPayload: Record<string, any> = {},
        metadata: Record<string, string> = {}
    ): Promise<boolean> {
        if (!this.httpCore) return false;
        const interaction = await AntigravityToolInteractionController.resolveRunCommandInteraction(
            cascadeId,
            toolCallId,
            argumentsPayload,
            metadata,
            (id) => this.getSessionTrajectory(id)
        );
        if (!interaction) return false;

        return AntigravityToolInteractionController.submitRunCommandInteraction(
            this.httpCore,
            cascadeId,
            accepted,
            interaction
        );
    }

    stopStreaming(cascadeId?: string): void {
        // Ensure server-side invocation is canceled too, otherwise the language server
        // can continue running and streaming updates even after we stop the local SSE connection.
        const cancelRemote = async (id: string) => {
            try {
                await this.rpc?.cancelCascadeInvocation(id);
            } catch {
                // ignore
            }
        };

        if (cascadeId) {
            this.streamStateManager.clearStreaming(cascadeId);
            const req = this.streamStateManager.getRequest(cascadeId);
            if (req) {
                req.destroy();
                this.streamStateManager.clearRequest(cascadeId);
            }
            void cancelRemote(cascadeId);
        } else {
            // Global stop
            for (const id of this.streamStateManager.getActiveRequestCascadeIds()) {
                this.stopStreaming(id);
            }
            this.streamStateManager.clearAll();
        }
    }

    getLastHttpError(): string {
        return this.httpCore ? (this.httpCore as any).lastError || '' : 'No HTTP core';
    }

    async getSessionTrajectory(cascadeId: string): Promise<TrajectoryStep[]> {
        if (!this.rpc) return [];
        const res = await this.rpc.getCascadeTrajectory({
            metadata: this.rpc.getMetadata(),
            cascadeId,
            source: ANTIGRAVITY_SOURCES.cascadeClient,
        });
        return res?.trajectory?.steps || [];
    }

    async getSessionMessages(cascadeId: string): Promise<any[]> {
        const steps = await this.getSessionTrajectory(cascadeId);
        return AntigravityMappers.stepsToMessages(steps);
    }

    getCurrentSessionMessages(cascadeId?: string): any[] | null {
        if (!cascadeId) return null;
        return this.streamStateManager.getCurrentHotMessages(cascadeId);
    }

    async getModels(): Promise<IDEModel[]> {
        if (!this.rpc) return [];
        const res = await this.rpc.getUserStatus({
            metadata: this.rpc.getMetadata(),
        });

        const configs: IDEModel[] = [];
        const clientModelConfigs = res?.userStatus?.cascadeModelConfigData?.clientModelConfigs || [];

        for (const c of clientModelConfigs) {
            const modelStr = c.modelOrAlias?.model || '';
            if (modelStr) {
                // Convert resetTime to dd/mm/yyyy HH:mm format
                    let formattedResetTime = c.quotaInfo?.resetTime;
                    if (formattedResetTime) {
                        // Handle ISO 8601 format
                        const isoRe = /(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:\d{2}))/g;
                        if (isoRe.test(formattedResetTime)) {
                            const date = new Date(formattedResetTime);
                            const pad = (n: number) => (n < 10 ? `0${n}` : `${n}`);
                            formattedResetTime = `${pad(date.getDate())}/${pad(date.getMonth() + 1)}/${date.getFullYear()} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
                        }
                        // Handle dd-mm-yyThh:mm:ssz format
                        else {
                            const altRe = /(\d{2}-\d{2}-\d{2}t\d{2}:\d{2}:\d{2}z)/gi;
                            formattedResetTime = formattedResetTime.replace(altRe, (altStr: string) => {
                                try {
                                    const parts = altStr.toLowerCase().split('t')[0].split('-');
                                    const day = parseInt(parts[0], 10);
                                    const month = parseInt(parts[1], 10);
                                    let year = parseInt(parts[2], 10);
                                    if (year < 100) year += 2000;
                                    
                                    const timeParts = altStr.toLowerCase().split('t')[1].replace('z', '').split(':');
                                    const hours = parseInt(timeParts[0], 10);
                                    const minutes = parseInt(timeParts[1], 10);
                                    
                                    const date = new Date(year, month - 1, day, hours, minutes);
                                    const pad = (n: number) => (n < 10 ? `0${n}` : `${n}`);
                                    return `${pad(date.getDate())}/${pad(date.getMonth() + 1)}/${date.getFullYear()} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
                                } catch {
                                    return altStr;
                                }
                            });
                        }
                    }
                    
                    configs.push({
                        id: modelStr,
                        label: c.label || modelStr,
                        supportsImages: c.supportsImages || false,
                        isRecommended: c.isRecommended || false,
                        tagTitle: c.tagTitle,
                        quotaInfo: c.quotaInfo ? {
                            remainingFraction: c.quotaInfo.remainingFraction,
                            resetTime: formattedResetTime
                        } : undefined
                    });
            }
        }

        return configs;
    }

    async getConversationIds(): Promise<string[]> {
        if (!this.rpc) return [];
        try {
            const res = await this.rpc.getAllCascadeTrajectories({
                metadata: this.rpc.getMetadata(),
            });
            if (res && res.trajectorySummaries) {
                return Object.keys(res.trajectorySummaries);
            }
        } catch (e) {
            console.error('[AntigravityClient] getConversationIds error:', e);
        }
        return [];
    }

    async getConversationsMetadata(): Promise<Array<{ id: string, title: string, preview: string, workspacePath: string | null, lastModified: number, size: number }>> {
        if (!this.rpc) return [];
        try {
            const res = await this.rpc.getAllCascadeTrajectories({
                metadata: this.rpc.getMetadata(),
                limit: 1000 // Ensure we fetch an ample amount
            });
            
            const results: any[] = [];
            if (res && res.trajectorySummaries) {
                for (const [id, data] of Object.entries<any>(res.trajectorySummaries)) {
                    results.push({
                        id,
                        title: data.summary || `Chat ${id.substring(0, 6)}`,
                        preview: '...', // Server summary does not natively return snippet previews, kept minimal for speed
                        workspacePath: data.workspaces?.[0]?.workspaceFolderAbsoluteUri 
                            ? decodeURIComponent(data.workspaces[0].workspaceFolderAbsoluteUri).replace('file:///', '').replace(/\//g, path.sep) 
                            : null,
                        lastModified: new Date(data.lastModifiedTime).getTime(),
                        size: 0 // Size stat is irrelevant as we no longer read chunks of local files
                    });
                }
            }
            results.sort((a, b) => b.lastModified - a.lastModified);
            return results;
        } catch (e) {
            console.error('[AntigravityClient] getConversationsMetadata error:', e);
            return [];
        }
    }


    private getCurrentWorkspace(): { name: string, path: string } | null {
        const wsFolder = vscode.workspace.workspaceFolders?.[0];
        if (wsFolder) return { name: wsFolder.name, path: wsFolder.uri.fsPath };
        const activeFile = vscode.window.activeTextEditor?.document.uri.fsPath;
        if (activeFile) {
            const dir = path.dirname(activeFile);
            return { name: path.basename(dir), path: dir };
        }
        return null;
    }

    async getWorkspaces(): Promise<Array<{ name: string; path: string; isCurrent: boolean }>> {
        if (!this.rpc) return [];
        const res = await this.rpc.getUserTrajectoryDescriptions({
            metadata: this.rpc.getMetadata(),
        });
        const trajectories = res?.trajectories || [];
        const workspaces: Array<{ name: string; path: string; isCurrent: boolean }> = [];
        const seen = new Set<string>();

        for (const t of trajectories) {
            const scope = t.trajectoryScope;
            if (!scope?.workspaceUri) continue;
            const wsPath = decodeURIComponent(scope.workspaceUri).replace('file:///', '').replace(/\//g, path.sep);
            if (seen.has(wsPath)) continue;
            seen.add(wsPath);
            workspaces.push({
                name: path.basename(wsPath),
                path: wsPath,
                isCurrent: t.current === true,
            });
        }

        const currentWs = this.getCurrentWorkspace();
        if (currentWs && !seen.has(currentWs.path)) {
            workspaces.unshift({ name: currentWs.name, path: currentWs.path, isCurrent: true });
        }
        return workspaces;
    }
    async streamForResponse(cascadeId: string, callbacks: IDEStreamCallbacks, ignoreBeforeIndex: number = 0, initialSteps: any[] = []): Promise<void> {
        if (!this.initialized || !this.httpCore || !this.streamingTransport) return;

        this.lastActiveCascadeId = cascadeId;

        await AntigravityStreamOrchestrator.streamForResponse({
            cascadeId,
            callbacks,
            ignoreBeforeIndex,
            initialSteps,
            streamingTransport: this.streamingTransport,
            streamStateManager: this.streamStateManager,
            getSessionTrajectory: (id) => this.getSessionTrajectory(id),
            stopStreaming: (id) => this.stopStreaming(id),
            sleep: (ms) => this.sleep(ms),
        });
    }
}

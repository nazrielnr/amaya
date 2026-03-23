import * as WebSocket from 'ws';
import { IIDEClient, IDEConversationMode, IDEMessageAttachment } from '../interfaces/IIDEClient';
import { IIDECommandExecutor } from '../interfaces/IIDECommandExecutor';
import { IIDERunStatusMapper } from '../interfaces/IIDERunStatusMapper';
import { AppState } from '../types/index';
import { ErrorMessageFormatter } from './support/ErrorMessageFormatter';
import { UiMessageProjector } from './support/UiMessageProjector';
import { AttachmentValidator } from './support/AttachmentValidator';
import { ModelQuotaGuard } from './support/ModelQuotaGuard';
import { IHostWorkspaceService, VscodeHostWorkspaceService } from './support/HostWorkspaceService';
import { WorkspaceCommandController } from './support/WorkspaceCommandController';
import { ConversationStateController } from './support/ConversationStateController';
import { MessageFlowController } from './support/MessageFlowController';
import { EventBuffer } from './support/EventBuffer';
import { getLocalIp } from '../utils/LocalhostLinker';
import { StreamOrchestrator } from './support/StreamOrchestrator';
import { MessageLifecycleController } from './support/MessageLifecycleController';
import { MessageCommandRouter } from './support/MessageCommandRouter';

/**
 * MessageHandler.ts
 * 
 * Extracts the monolithic WebSocket command processor from `extension.ts`.
 * It routes Android APK JSON payloads directly into generic IIDEClient standard methods,
 * making the extension agnostic to which IDE backend is powering it.
 */
export class MessageHandler {
    private api: IIDEClient;
    private commandExecutor: IIDECommandExecutor;
    private runStatusMapper: IIDERunStatusMapper;
    private state: AppState;
    private wss: WebSocket.Server | null;
    private readonly serverSessionId: string = `${Date.now()}-${Math.random().toString(16).slice(2)}`;
    private lastUserSendAt: number = 0;

    private conversationModeMap: Map<string, IDEConversationMode> = new Map();
    private localIp: string = '';
    private readonly errorMessageFormatter = new ErrorMessageFormatter();
    private readonly uiMessageProjector = new UiMessageProjector();
    private readonly attachmentValidator = new AttachmentValidator();
    private readonly modelQuotaGuard = new ModelQuotaGuard();
    private readonly hostWorkspaceService: IHostWorkspaceService;
    private readonly workspaceCommandController: WorkspaceCommandController;
    private readonly conversationStateController: ConversationStateController;
    private readonly messageFlowController: MessageFlowController;
    private readonly eventBuffer: EventBuffer;
    private readonly streamOrchestrator: StreamOrchestrator;
    private readonly lifecycleController: MessageLifecycleController;
    private readonly commandRouter: MessageCommandRouter;

    // Debounce helper for noisy commands
    private lastCommandAt: Map<string, number> = new Map();
    private shouldRunCommand(command: string, debounceMs: number): boolean {
        const now = Date.now();
        const last = this.lastCommandAt.get(command) || 0;
        if (now - last < debounceMs) return false;
        this.lastCommandAt.set(command, now);
        return true;
    }

    private shouldLogCommand(action: string): boolean {
        return !['pong', 'get_state', 'get_conversations', 'get_models', 'get_events_since'].includes(action);
    }

    private getConversationMode(sessionId?: string | null): IDEConversationMode {
        if (!sessionId) return 'planning';
        return this.conversationModeMap.get(sessionId) || 'planning';
    }

    private formatErrorMessage(input: any): { message: string; raw: string } {
        return this.errorMessageFormatter.format(input);
    }

    constructor(
        api: IIDEClient,
        state: AppState,
        wss: WebSocket.Server | null,
        commandExecutor: IIDECommandExecutor,
        runStatusMapper: IIDERunStatusMapper,
        hostWorkspaceService: IHostWorkspaceService = new VscodeHostWorkspaceService()
    ) {
        this.api = api;
        this.state = state;
        this.wss = wss;
        this.commandExecutor = commandExecutor;
        this.runStatusMapper = runStatusMapper;
        this.hostWorkspaceService = hostWorkspaceService;
        this.eventBuffer = new EventBuffer(this.serverSessionId);
        this.streamOrchestrator = new StreamOrchestrator({
            api: this.api,
            state: this.state,
            runStatusMapper: this.runStatusMapper,
            broadcastEvent: (event, data, sessionId) => this.broadcastEvent(event, data, sessionId),
            mapMessagesForUi: (messages) => this.mapMessagesForUi(messages),
            getConversationMode: (sessionId) => this.getConversationMode(sessionId),
            formatErrorMessage: (input) => this.formatErrorMessage(input),
            getLocalIp: () => this.localIp,
        });
        this.workspaceCommandController = new WorkspaceCommandController(
            this.api,
            this.hostWorkspaceService,
            (client, message) => this.sendToClient(client, message)
        );
        this.conversationStateController = new ConversationStateController({
            api: this.api,
            state: this.state,
            hostWorkspaceService: this.hostWorkspaceService,
            sendToClient: (client, message) => this.sendToClient(client, message),
            broadcastEvent: (event, data, sessionId) => this.broadcastEvent(event, data, sessionId),
            streamOrchestrator: this.streamOrchestrator,
            getModelsCache: () => this.modelsCache,
            setModelsCache: (cache) => {
                this.modelsCache = cache;
            },
        });
        this.messageFlowController = new MessageFlowController({
            api: this.api,
            state: this.state,
            attachmentValidator: this.attachmentValidator,
            modelQuotaGuard: this.modelQuotaGuard,
            streamOrchestrator: this.streamOrchestrator,
            broadcastEvent: (event, data, sessionId) => this.broadcastEvent(event, data, sessionId),
            getModelsCache: () => this.modelsCache,
            setModelsCache: (cache) => { this.modelsCache = cache; },
            getConversationMode: (sessionId) => this.getConversationMode(sessionId),
            setConversationMode: (sessionId, mode) => { this.conversationModeMap.set(sessionId, mode); },
            onLastUserSendAt: (timestamp) => { this.lastUserSendAt = timestamp; },
        });
        this.localIp = getLocalIp();
        this.lifecycleController = new MessageLifecycleController({
            api: this.api,
            state: this.state,
            getWss: () => this.wss,
            setWss: (wss) => { this.wss = wss; },
            setLocalIp: (ip) => { this.localIp = ip; },
            broadcastEvent: (event, data, sessionId) => this.broadcastEvent(event, data, sessionId),
            streamOrchestrator: this.streamOrchestrator,
            handleGetState: (ws) => this.conversationStateController.handleGetState(ws),
            getLastUserSendAt: () => this.lastUserSendAt,
        });
        this.commandRouter = new MessageCommandRouter({
            api: this.api,
            state: this.state,
            commandExecutor: this.commandExecutor,
            attachmentValidator: this.attachmentValidator,
            workspaceCommandController: this.workspaceCommandController,
            conversationStateController: this.conversationStateController,
            messageFlowController: this.messageFlowController,
            streamOrchestrator: this.streamOrchestrator,
            broadcastEvent: (event, data, sessionId) => this.broadcastEvent(event, data, sessionId),
            shouldLogCommand: (action) => this.shouldLogCommand(action),
            shouldRunCommand: (command, debounceMs) => this.shouldRunCommand(command, debounceMs),
            getActiveSessionId: () => this.state.activeSessionId,
            setConversationMode: (sessionId, mode) => { this.conversationModeMap.set(sessionId, mode); },
            getModelsCache: () => this.modelsCache,
            handleGetEventsSince: (lastSeqId, ws) => this.handleGetEventsSince(lastSeqId, ws),
        });
        this.lifecycleController.startHeartbeat();
        this.lifecycleController.startAutoAttach();
    }

    public updateServer(wss: WebSocket.Server | null) {
        this.lifecycleController.updateServer(wss);
    }

    public async handleCommand(msg: any, ws: WebSocket.WebSocket) {
        await this.commandRouter.handleCommand(msg, ws);
    }

    private modelsCache: { models: any[], timestamp: number } | null = null;

    // ── Utility ────────────────────────────────────────────────────────

    private async handleGetEventsSince(lastSeqId: number, ws: WebSocket.WebSocket) {
        const { missedEvents, maxSeqId } = this.eventBuffer.replaySince(lastSeqId);

        console.log(`[Amaya MessageHandler] Client requesting catch-up from seqId ${lastSeqId}. Found ${missedEvents.length} events.`);

        // If client is requesting events with seqId higher than we have,
        // the server probably restarted. Send a state_sync to force full refresh.
        if (lastSeqId > maxSeqId) {
            console.log(`[Amaya MessageHandler] Client seqId ${lastSeqId} > server max ${maxSeqId}. Server likely restarted, forcing full state sync.`);
            // Force full state sync
            await this.conversationStateController.handleGetState(ws);
            return;
        }

        for (const event of missedEvents) {
            this.sendToClient(ws, event);
        }
    }

    private broadcastEvent(event: string, data: any, sessionId?: string) {
        if (!this.wss) return;

        try {
            // console.log intentionally removed to prevent log spam (~50 lines per response)
        } catch { /* ignore */ }
        const payload = this.eventBuffer.record(event, data, sessionId, this.state.activeSessionId || undefined, this.localIp);

        const msg = JSON.stringify(payload);
        this.wss.clients.forEach(client => {
            if (client.readyState === WebSocket.OPEN) {
                client.send(msg);
            }
        });
    }

    public broadcastTerminalData(terminalName: string, data: string) {
        // More resilient sessionId resolution:
        // 1. Current active ID
        // 2. ID of any currently streaming conversation
        // 3. Fallback to global buffer if everything else fails
        let sessionId = this.state.activeSessionId;
        if (!sessionId) {
            const streamingSessions = this.streamOrchestrator.getStreamingSessionIds();
            if (streamingSessions.length > 0) {
                sessionId = streamingSessions[0];
            }
        }

        if (sessionId) {
            // (Buffered data logic for tool hijacking removed as requested)
            

            this.broadcastEvent('tool_activity', {
                type: 'terminal',
                name: terminalName,
                data: data,
                conversationId: sessionId
            }, sessionId);
        } else {
            // No active session, just broadcast as global to keep it visible if possible
            this.broadcastEvent('tool_activity', {
                type: 'terminal',
                name: terminalName,
                data: data
            }, 'global');
        }
    }

    private sendToClient(ws: WebSocket.WebSocket, msg: any) {
        if (ws.readyState === WebSocket.OPEN) {
            // Ensure every message has an incremental seqId to prevent deduplication drop by Android
            if (msg.event && typeof msg.seqId === 'undefined') {
                msg.seqId = this.eventBuffer.nextSeqId();
            }
            if (typeof msg.serverSessionId === 'undefined') {
                msg.serverSessionId = this.serverSessionId;
            }
            ws.send(JSON.stringify(msg));
        }
    }

    private safeParseJson(str: string): Record<string, any> {
        return this.uiMessageProjector.safeParseJson(str);
    }


    private mapMessagesForUi(messages: any[]): any[] {
        return this.uiMessageProjector.mapMessagesForUi(messages);
    }
}


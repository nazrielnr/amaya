import * as WebSocket from 'ws';
import { IIDEClient } from '../../interfaces/IIDEClient';
import { AppState } from '../../types';
import { getLocalIp } from '../../utils/LocalhostLinker';
import { StreamOrchestrator } from './StreamOrchestrator';

interface MessageLifecycleControllerDeps {
    api: IIDEClient;
    state: AppState;
    getWss: () => WebSocket.Server | null;
    setWss: (wss: WebSocket.Server | null) => void;
    setLocalIp: (ip: string) => void;
    broadcastEvent: (event: string, data: any, sessionId?: string) => void;
    streamOrchestrator: StreamOrchestrator;
    handleGetState: (ws: WebSocket.WebSocket) => Promise<void>;
    getLastUserSendAt: () => number;
}

export class MessageLifecycleController {
    private heartbeatInterval: NodeJS.Timeout | null = null;
    private autoAttachInterval: NodeJS.Timeout | null = null;
    private lastAutoAttachSessionId: string | null = null;
    private lastAutoAttachAt: number = 0;
    private lastMetadataCheckAt: number = 0;
    private lastAutoSyncAt: number = 0;

    constructor(private readonly deps: MessageLifecycleControllerDeps) {}

    public startHeartbeat() {
        if (this.heartbeatInterval) return;
        this.heartbeatInterval = setInterval(() => {
            if (this.deps.getWss()) {
                this.deps.broadcastEvent('ping', { timestamp: Date.now() });
            }
        }, 15000);
    }

    public stopHeartbeat() {
        if (this.heartbeatInterval) {
            clearInterval(this.heartbeatInterval);
            this.heartbeatInterval = null;
        }
    }

    public startAutoAttach() {
        if (this.autoAttachInterval) return;
        this.autoAttachInterval = setInterval(async () => {
            const wss = this.deps.getWss();
            if (!wss || wss.clients.size === 0) return;

            const now = Date.now();
            if (now - this.lastAutoSyncAt < 10000) return;
            if (now - this.deps.getLastUserSendAt() < 3000) return;

            let activeId = this.deps.api.getLastActiveSessionId() || this.deps.state.activeSessionId;
            if (!activeId && now - this.lastMetadataCheckAt > 10000) {
                this.lastMetadataCheckAt = now;
                try {
                    const metas = await this.deps.api.getConversationsMetadata();
                    if (metas && metas.length > 0) {
                        const latest = metas.reduce((a, b) => (a.lastModified >= b.lastModified ? a : b));
                        activeId = latest?.id || null;
                    }
                } catch { /* ignore */ }
            }

            if (!activeId) return;
            if (this.deps.streamOrchestrator.isStreamAttached(activeId)) return;

            if (activeId === this.lastAutoAttachSessionId && now - this.lastAutoAttachAt < 15000) return;
            this.lastAutoAttachSessionId = activeId;
            this.lastAutoAttachAt = now;
            this.lastAutoSyncAt = now;

            console.log(`[Amaya MessageHandler] Auto-attach stream for ${activeId}`);
            for (const ws of wss.clients) {
                if (ws.readyState === WebSocket.OPEN) {
                    this.deps.handleGetState(ws as WebSocket.WebSocket).catch((e: any) => {
                        console.error('[Amaya MessageHandler] Auto get_state failed:', e?.message || e);
                    });
                }
            }
        }, 2000);
    }

    public stopAutoAttach() {
        if (this.autoAttachInterval) {
            clearInterval(this.autoAttachInterval);
            this.autoAttachInterval = null;
        }
    }

    public updateServer(wss: WebSocket.Server | null) {
        this.deps.setWss(wss);
        const localIp = getLocalIp();
        this.deps.setLocalIp(localIp);
        console.log(`[Amaya MessageHandler] Server updated. Local IP: ${localIp}`);
        if (wss) {
            this.startHeartbeat();
            this.startAutoAttach();
        } else {
            this.stopHeartbeat();
            this.stopAutoAttach();
        }
    }
}
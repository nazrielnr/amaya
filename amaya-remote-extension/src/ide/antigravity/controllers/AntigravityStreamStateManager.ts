export class AntigravityStreamStateManager {
    private readonly isStreamingMap: Map<string, boolean> = new Map();
    private readonly streamRequestsMap: Map<string, any> = new Map();
    private readonly latestHotMessagesMap: Map<string, any[]> = new Map();
    private readonly latestHotMessagesTimestampMap: Map<string, number> = new Map();

    constructor(private readonly hotMessagesTtlMs: number = 2 * 60 * 1000) {}

    markStreaming(cascadeId: string): void {
        this.isStreamingMap.set(cascadeId, true);
    }

    clearStreaming(cascadeId: string): void {
        this.isStreamingMap.delete(cascadeId);
    }

    isStreaming(cascadeId: string): boolean {
        return this.isStreamingMap.get(cascadeId) === true;
    }

    setRequest(cascadeId: string, request: any): void {
        this.streamRequestsMap.set(cascadeId, request);
    }

    getRequest(cascadeId: string): any {
        return this.streamRequestsMap.get(cascadeId);
    }

    clearRequest(cascadeId: string): void {
        this.streamRequestsMap.delete(cascadeId);
    }

    getActiveRequestCascadeIds(): string[] {
        return Array.from(this.streamRequestsMap.keys());
    }

    getHotMessageMaps(): {
        latestHotMessagesMap: Map<string, any[]>;
        latestHotMessagesTimestampMap: Map<string, number>;
    } {
        return {
            latestHotMessagesMap: this.latestHotMessagesMap,
            latestHotMessagesTimestampMap: this.latestHotMessagesTimestampMap,
        };
    }

    setHotMessages(cascadeId: string, messages: any[]): any[] {
        this.latestHotMessagesMap.set(cascadeId, messages);
        this.latestHotMessagesTimestampMap.set(cascadeId, Date.now());
        return messages;
    }

    getCurrentHotMessages(cascadeId: string): any[] | null {
        const ts = this.latestHotMessagesTimestampMap.get(cascadeId);
        if (typeof ts === 'number' && Date.now() - ts > this.hotMessagesTtlMs) {
            this.latestHotMessagesMap.delete(cascadeId);
            this.latestHotMessagesTimestampMap.delete(cascadeId);
            return null;
        }
        return this.latestHotMessagesMap.get(cascadeId) || null;
    }

    clearAll(): void {
        this.isStreamingMap.clear();
        this.streamRequestsMap.clear();
        this.latestHotMessagesMap.clear();
        this.latestHotMessagesTimestampMap.clear();
    }
}
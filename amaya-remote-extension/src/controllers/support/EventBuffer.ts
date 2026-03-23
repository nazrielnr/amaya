export class EventBuffer {
    private sequenceId: number = 0;
    private readonly buffers: Map<string, any[]> = new Map();

    constructor(
        private readonly serverSessionId: string,
        private readonly bufferSize: number = 100
    ) {}

    public record(event: string, data: any, sessionId?: string, activeSessionId?: string, localIp: string = '') {
        const seqId = ++this.sequenceId;
        const targetSessionId = sessionId || data.conversationId || data.sessionId || activeSessionId || 'global';
        const enrichedData = { ...data, conversationId: targetSessionId, serverIp: localIp };
        const payload = { event, data: enrichedData, seqId, serverSessionId: this.serverSessionId };

        this.push(targetSessionId, payload);
        this.push('global', payload);

        return payload;
    }

    public nextSeqId(): number {
        return ++this.sequenceId;
    }

    public replaySince(lastSeqId: number): { missedEvents: any[]; maxSeqId: number } {
        let allEvents: any[] = [];

        const globalBuffer = this.buffers.get('global') || [];
        allEvents = [...globalBuffer];

        for (const [key, buffer] of this.buffers) {
            if (key !== 'global') {
                allEvents.push(...buffer);
            }
        }

        allEvents.sort((a, b) => a.seqId - b.seqId);
        const missedEvents = allEvents.filter((event) => event.seqId > lastSeqId);
        const maxSeqId = allEvents.length > 0 ? allEvents[allEvents.length - 1].seqId : 0;

        return { missedEvents, maxSeqId };
    }

    private push(bufferKey: string, payload: any) {
        let buffer = this.buffers.get(bufferKey);
        if (!buffer) {
            buffer = [];
            this.buffers.set(bufferKey, buffer);
        }

        buffer.push(payload);
        if (buffer.length > this.bufferSize) {
            buffer.shift();
        }
    }
}
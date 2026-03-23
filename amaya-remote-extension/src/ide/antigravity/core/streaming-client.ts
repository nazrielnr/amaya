/**
 * Connect Protocol Streaming Client for Antigravity
 * Handles gRPC-Web envelope format for StreamCascadeReactiveUpdates
 */

export function encodeConnectEnvelope(jsonBody: any): Buffer {
    const jsonStr = JSON.stringify(jsonBody);
    const bodyBytes = Buffer.from(jsonStr, 'utf8');
    const length = bodyBytes.length;

    // Connect envelope: [flags:1byte][length:4bytes][data:length bytes]
    const envelope = Buffer.alloc(5 + length);
    envelope[0] = 0; // flags: 0 = message, 1 = trailers
    envelope.writeUInt32BE(length, 1);
    bodyBytes.copy(envelope, 5);

    return envelope;
}

export function decodeConnectEnvelope(buffer: Buffer): { messages: any[], remaining: Buffer } {
    const messages: any[] = [];
    let offset = 0;

    while (offset + 5 <= buffer.length) {
        const flags = buffer[offset];
        const length = buffer.readUInt32BE(offset + 1);

        if (offset + 5 + length > buffer.length) break;

        const data = buffer.slice(offset + 5, offset + 5 + length);
        try {
            messages.push(JSON.parse(data.toString('utf8')));
        } catch { }

        offset += 5 + length;
    }

    return { messages, remaining: buffer.slice(offset) };
}

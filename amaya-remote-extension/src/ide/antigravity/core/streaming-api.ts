import * as http from 'http';
import * as https from 'https';
import { encodeConnectEnvelope, decodeConnectEnvelope } from './streaming-client';

export interface StreamCallbacks {
    onData?: (data: any) => void;
    onError?: (error: string) => void;
    onEnd?: () => void;
}

export function streamCascadeUpdates(
    port: number,
    useHttps: boolean,
    csrfToken: string,
    conversationId: string,
    subscriberId: string,
    callbacks: StreamCallbacks,
    agent?: http.Agent | https.Agent
): http.ClientRequest {
    const body = { protocolVersion: 1, conversationId, subscriberId };
    const envelope = encodeConnectEnvelope(body);

    const httpClient = useHttps ? https : http;
    const options: https.RequestOptions = {
        hostname: '127.0.0.1',
        port,
        path: '/exa.language_server_pb.LanguageServerService/StreamAgentStateUpdates',
        method: 'POST',
        rejectUnauthorized: false,
        headers: {
            'Content-Type': 'application/connect+json',
            'connect-protocol-version': '1',
            'x-codeium-csrf-token': csrfToken,
            'Origin': 'vscode-file://vscode-app'
        },
    };

    // Use the keep-alive agent if provided
    if (agent) {
        options.agent = agent;
    }

    const req = httpClient.request(options, (res: http.IncomingMessage) => {
        const status = res.statusCode || 0;
        if (status < 200 || status >= 300) {
            let body = '';
            res.on('data', (chunk: Buffer) => {
                body += chunk.toString('utf8');
            });
            res.on('end', () => {
                if (callbacks.onError) callbacks.onError(`[streamCascadeUpdates] HTTP ${status}: ${body.substring(0, 500)}`);
            });
            res.on('error', (err: Error) => {
                if (callbacks.onError) callbacks.onError(err.message);
            });
            return;
        }

        let buffer: any = Buffer.alloc(0);

        res.on('data', (chunk: Buffer) => {
            buffer = Buffer.concat([buffer, chunk]);
            const { messages, remaining } = decodeConnectEnvelope(buffer);
            buffer = remaining as any;

            for (const msg of messages) {
                if (callbacks.onData) callbacks.onData(msg);
            }
        });

        res.on('end', () => {
            if (callbacks.onEnd) callbacks.onEnd();
        });

        res.on('error', (err: Error) => {
            if (callbacks.onError) callbacks.onError(err.message);
        });
    });

    req.on('error', (err: Error) => {
        if (callbacks.onError) callbacks.onError(err.message);
    });

    req.write(envelope);
    req.end();

    return req;
}

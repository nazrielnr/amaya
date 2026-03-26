const http = require('http');
const https = require('https');
const fs = require('fs');
const { discoverCredentials } = require('../auth-discovery/discovery-utils');

/**
 * capture-raw-cortex.js
 * 
 * Captures raw CORTEX JSON stream from Antigravity Language Server.
 * Targeted at observing the "thinking" iteration behavior.
 */

// --- CONFIG ---
const PROMPT = "cek workspace";
const OUTPUT_FILE = "raw-cortex-capture.json";
const API_KEY = process.argv[2] || "INSERT_API_KEY_HERE"; // Need Google OAuth token (ya29...)

// --- PROTOCOL HELPERS ---

function encodeConnectEnvelope(jsonBody) {
    const jsonStr = JSON.stringify(jsonBody);
    const bodyBytes = Buffer.from(jsonStr, 'utf8');
    const length = bodyBytes.length;
    const envelope = Buffer.alloc(5 + length);
    envelope[0] = 0; // flags
    envelope.writeUInt32BE(length, 1);
    bodyBytes.copy(envelope, 5);
    return envelope;
}

function decodeConnectEnvelope(buffer) {
    const messages = [];
    let offset = 0;
    while (offset + 5 <= buffer.length) {
        // const flags = buffer[offset];
        const length = buffer.readUInt32BE(offset + 1);
        if (offset + 5 + length > buffer.length) break;
        const data = buffer.slice(offset + 5, offset + 5 + length);
        try {
            messages.push(JSON.parse(data.toString('utf8')));
        } catch { }
        offset += 5 + length;
    }
    return { messages, offset };
}

// --- API CLIENT ---

async function callEndpoint(creds, methodName, payload) {
    return new Promise((resolve, reject) => {
        const data = JSON.stringify(payload);
        const apiPath = `/exa.language_server_pb.LanguageServerService/${methodName}`;
        const options = {
            hostname: '127.0.0.1',
            port: creds.port,
            path: apiPath,
            method: 'POST',
            rejectUnauthorized: false,
            headers: {
                'Content-Type': 'application/json',
                'connect-protocol-version': '1',
                'x-codeium-csrf-token': creds.csrfToken,
                'Origin': 'vscode-file://vscode-app'
            }
        };

        const client = http; // Extension port usually HTTP
        const req = client.request(options, (res) => {
            let body = '';
            res.on('data', (d) => body += d);
            res.on('end', () => {
                if (res.statusCode >= 200 && res.statusCode < 300) {
                    try {
                        resolve(JSON.parse(body || '{}'));
                    } catch (e) {
                        resolve({});
                    }
                } else {
                    reject(new Error(`HTTP ${res.statusCode}: ${body}`));
                }
            });
        });
        req.on('error', reject);
        req.write(data);
        req.end();
    });
}

function streamUpdates(creds, cascadeId, onData) {
    const body = { protocolVersion: 1, conversationId: cascadeId, subscriberId: "capture-client-" + Date.now() };
    const envelope = encodeConnectEnvelope(body);
    const options = {
        hostname: '127.0.0.1',
        port: creds.port,
        path: '/exa.language_server_pb.LanguageServerService/StreamAgentStateUpdates',
        method: 'POST',
        headers: {
            'Content-Type': 'application/connect+json',
            'connect-protocol-version': '1',
            'x-codeium-csrf-token': creds.csrfToken,
            'Origin': 'vscode-file://vscode-app'
        }
    };

    const req = http.request(options, (res) => {
        let buffer = Buffer.alloc(0);
        res.on('data', (chunk) => {
            buffer = Buffer.concat([buffer, chunk]);
            const { messages, offset } = decodeConnectEnvelope(buffer);
            buffer = buffer.slice(offset);
            messages.forEach(onData);
        });
    });
    req.on('error', (e) => console.error('[Stream] Error:', e.message));
    req.write(envelope);
    req.end();
    return req;
}

// --- MAIN ---

async function main() {
    const creds = discoverCredentials();
    if (!creds) {
        console.error('❌ Could not discover Antigravity credentials. Ensure VS Code with Antigravity is running.');
        process.exit(1);
    }
    console.log(`✅ Discovered credentials: Port ${creds.port}`);

    if (API_KEY === "INSERT_API_KEY_HERE") {
        console.warn('⚠️ WARNING: API_KEY is missing. Providing a dummy key, but request might fail.');
        console.log('Run with: node capture-raw-cortex.js "your-google-oauth-token"');
    }

    const metadata = {
        ideName: 'antigravity',
        apiKey: API_KEY,
        locale: 'en',
        ideVersion: '1.19.6',
        extensionName: 'antigravity'
    };

    const outStream = fs.createWriteStream(OUTPUT_FILE);
    outStream.write('[\n');
    let first = true;

    try {
        console.log(`[1] Starting new Cascade...`);
        const startRes = await callEndpoint(creds, 'StartCascade', {
            metadata,
            source: 'CORTEX_TRAJECTORY_SOURCE_CASCADE_CLIENT'
        });
        const cascadeId = startRes.cascadeId;
        console.log(`✅ Cascade ID: ${cascadeId}`);

        console.log(`[2] Subscribing to Stream...`);
        streamUpdates(creds, cascadeId, (data) => {
            if (!first) outStream.write(',\n');
            outStream.write(JSON.stringify(data, null, 2));
            first = false;
            console.log(`[Stream] Received update: ${data.update?.status || 'UNKNOWN'}`);
        });

        console.log(`[3] Sending prompt: "${PROMPT}"...`);
        await callEndpoint(creds, 'SendUserCascadeMessage', {
            metadata,
            cascadeId,
            items: [{ text: PROMPT }],
            cascadeConfig: {
                plannerConfig: {
                    conversational: {
                        plannerMode: 'CONVERSATIONAL_PLANNER_MODE_DEFAULT',
                        agenticMode: true
                    },
                    requestedModel: { model: 1018 } // Standard GPT or Gemini model
                }
            }
        });

        console.log(`✅ Message sent. Capturing logs to ${OUTPUT_FILE}. Press Ctrl+C to stop.`);

    } catch (e) {
        console.error('❌ Error:', e.message);
        process.exit(1);
    }
}

main();

// Handle Ctrl+C
process.on('SIGINT', () => {
    console.log('\n[Exit] Closing capture file...');
    // Note: The file might still be "in-flight" so it won't be valid JSON array closing bracket.
    // User can manually fix it or we can try to append here.
    process.exit();
});

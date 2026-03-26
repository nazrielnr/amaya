const http = require('http');
const fs = require('fs');
const path = require('path');
const { discoverWorkingCredentials } = require('../auth-discovery/discovery-utils');

// --- CONFIG (override via CLI args) ---
const DEFAULT_PROMPT = 'ini apa?';
const DEFAULT_OUTPUT_FILE = 'raw-cortex-capture-image.json';

// CLI:
// node capture-raw-cortex-image.js <GOOGLE_OAUTH_TOKEN> <IMAGE_PATH> [OUTPUT_FILE]
// Example:
// node capture-raw-cortex-image.js "ya29..." "C:\\temp\\image.png" "out.json"
const API_KEY = process.argv[2] || 'INSERT_API_KEY_HERE';
const IMAGE_PATH = process.argv[3] || '';
const OUTPUT_FILE = process.argv[4] || DEFAULT_OUTPUT_FILE;

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
        const length = buffer.readUInt32BE(offset + 1);
        if (offset + 5 + length > buffer.length) break;
        const data = buffer.slice(offset + 5, offset + 5 + length);
        try {
            messages.push(JSON.parse(data.toString('utf8')));
        } catch {
            // ignore
        }
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
                'Accept': 'application/json, text/event-stream',
                'connect-protocol-version': '1',
                'x-codeium-csrf-token': creds.csrfToken,
                'Origin': 'vscode-file://vscode-app'
            }
        };

        const req = http.request(options, (res) => {
            let body = '';
            res.on('data', (d) => body += d);
            res.on('end', () => {
                if (res.statusCode >= 200 && res.statusCode < 300) {
                    try {
                        resolve(JSON.parse(body || '{}'));
                    } catch {
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
    const body = { protocolVersion: 1, conversationId: cascadeId, subscriberId: 'capture-client-' + Date.now() };
    const envelope = encodeConnectEnvelope(body);
    const options = {
        hostname: '127.0.0.1',
        port: creds.port,
        path: '/exa.language_server_pb.LanguageServerService/StreamAgentStateUpdates',
        method: 'POST',
        headers: {
            'Content-Type': 'application/connect+json',
            'Accept': 'application/json, text/event-stream',
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

function detectMimeType(filePath) {
    const ext = path.extname(filePath).toLowerCase();
    if (ext === '.png') return 'image/png';
    if (ext === '.jpg' || ext === '.jpeg') return 'image/jpeg';
    if (ext === '.webp') return 'image/webp';
    if (ext === '.gif') return 'image/gif';
    return 'application/octet-stream';
}

function readImageBase64(imagePath) {
    const bytes = fs.readFileSync(imagePath);
    return bytes.toString('base64');
}

// --- MAIN ---
let outStream = null;
let first = true;
let closed = false;

function closeOutput() {
    if (!outStream || closed) return;
    closed = true;
    try {
        outStream.write('\n]\n');
    } catch {
        // ignore
    }
    try {
        outStream.end();
    } catch {
        // ignore
    }
}

async function main() {
    const creds = await discoverWorkingCredentials();
    if (!creds) {
        console.error('❌ Could not discover working Antigravity credentials. Ensure Antigravity IDE is running.');
        process.exit(1);
    }
    
    console.log(`✅ Working credentials: Port ${creds.port}, CSRF ${creds.csrfToken.substring(0, 8)}...`);

    if (!IMAGE_PATH) {
        console.error('❌ Missing IMAGE_PATH. Usage: node capture-raw-cortex-image.js <TOKEN> <IMAGE_PATH> [OUTPUT_FILE]');
        process.exit(1);
    }

    if (API_KEY === 'INSERT_API_KEY_HERE') {
        console.warn('⚠️ WARNING: API_KEY is missing. Request might fail.');
        console.log('Usage: node capture-raw-cortex-image.js "ya29..." "C:\\path\\image.png"');
    }

    const inlineData = readImageBase64(IMAGE_PATH);
    const mimeType = detectMimeType(IMAGE_PATH);
    console.log(`🖼️ Image: ${IMAGE_PATH}`);
    console.log(`🧾 mimeType: ${mimeType}`);
    console.log(`🧾 base64 length: ${inlineData.length}`);

    const metadata = {
        ideName: 'antigravity',
        apiKey: API_KEY,
        locale: 'en',
        ideVersion: '1.19.6',
        extensionName: 'antigravity'
    };

    outStream = fs.createWriteStream(OUTPUT_FILE);
    outStream.write('[\n');
    first = true;

    try {
        console.log('[1] Starting new Cascade...');
        const startRes = await callEndpoint(creds, 'StartCascade', {
            metadata,
            source: 'CORTEX_TRAJECTORY_SOURCE_CASCADE_CLIENT'
        });
        const cascadeId = startRes.cascadeId;
        console.log(`✅ Cascade ID: ${cascadeId}`);

        console.log('[2] Subscribing to Stream...');
        streamUpdates(creds, cascadeId, (data) => {
            if (!first) outStream.write(',\n');
            outStream.write(JSON.stringify(data, null, 2));
            first = false;
            console.log(`[Stream] Received update: ${data.update?.status || 'UNKNOWN'}`);
        });

        console.log(`[3] Sending prompt (with image): "${DEFAULT_PROMPT}" ...`);
        await callEndpoint(creds, 'SendUserCascadeMessage', {
            metadata,
            cascadeId,
            items: [{ text: DEFAULT_PROMPT }],
            media: [{ mimeType, inlineData }],
            cascadeConfig: {
                plannerConfig: {
                    conversational: {
                        plannerMode: 'CONVERSATIONAL_PLANNER_MODE_DEFAULT',
                        agenticMode: true
                    },
                    requestedModel: { model: 1018 }
                }
            }
        });

        console.log(`✅ Message sent. Capturing raw stream to ${OUTPUT_FILE}. Press Ctrl+C to stop.`);

    } catch (e) {
        console.error('❌ Error:', e.message);
        closeOutput();
        process.exit(1);
    }
}

main();

process.on('SIGINT', () => {
    console.log('\n[Exit] Closing capture file...');
    closeOutput();
    process.exit();
});

process.on('exit', () => {
    closeOutput();
});

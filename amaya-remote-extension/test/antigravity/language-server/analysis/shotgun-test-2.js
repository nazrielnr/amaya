const https = require('https');
const path = require('path');

require('dotenv').config({ path: path.join(__dirname, '..', '..', '.env.local') });

const CONFIG = {
    host: '127.0.0.1',
    port: 53125,
    csrfToken: process.env.ANTIGRAVITY_CSRF_TOKEN || '',
    apiKey: process.env.ANTIGRAVITY_API_KEY || '',
};

if (!CONFIG.csrfToken || !CONFIG.apiKey) {
    console.error('ERROR: Missing ANTIGRAVITY_CSRF_TOKEN or ANTIGRAVITY_API_KEY');
    console.error('Copy .env.local.example to .env.local and fill in your credentials');
    process.exit(1);
}

const metadata = {
    ideName: 'antigravity',
    apiKey: CONFIG.apiKey,
    locale: 'en',
    ideVersion: '1.19.6',
    extensionName: 'antigravity'
};

function callEndpoint(methodName, body) {
    return new Promise((resolve) => {
        const bodyStr = JSON.stringify(body);
        const options = {
            hostname: CONFIG.host,
            port: CONFIG.port,
            path: `/exa.language_server_pb.LanguageServerService/${methodName}`,
            method: 'POST',
            rejectUnauthorized: false,
            headers: {
                'Accept': 'application/json',
                'Content-Type': 'application/json',
                'connect-protocol-version': '1',
                'x-codeium-csrf-token': CONFIG.csrfToken,
                'Origin': 'vscode-file://vscode-app'
            }
        };
        const req = https.request(options, (res) => {
            let data = '';
            res.on('data', (chunk) => data += chunk);
            res.on('end', () => resolve({ status: res.statusCode, data }));
        });
        req.on('error', (e) => resolve({ error: e.message }));
        req.write(bodyStr);
        req.end();
    });
}

async function testSnakeCase() {
    const cascadeId = 'ed4dbaa8-c664-4029-9f1c-f1c4ade8f289';

    const variations = [
        {
            name: 'snake_case in cascadeConfig',
            body: {
                metadata, cascadeId, items: [{ text: 'test' }],
                cascade_config: { plan_model: 'MODEL_PLACEHOLDER_M18', requested_model: { model: 'MODEL_PLACEHOLDER_M18' } }
            }
        },
        {
            name: 'plan_model at top level',
            body: {
                metadata, cascadeId, items: [{ text: 'test' }],
                plan_model: 'MODEL_PLACEHOLDER_M18', requested_model: { model: 'MODEL_PLACEHOLDER_M18' }
            }
        },
        {
            name: 'Nested chat_model in cascade_config',
            body: {
                metadata, cascadeId, items: [{ text: 'test' }],
                cascade_config: { chat_model: { plan_model: 'MODEL_PLACEHOLDER_M18' } }
            }
        },
        {
            name: 'Using numerical enum - 18',
            body: {
                metadata, cascadeId, items: [{ text: 'test' }],
                cascadeConfig: { planModel: 18, requestedModel: { model: 18 } }
            }
        }
    ];

    for (const v of variations) {
        console.log(`\nTesting: ${v.name}`);
        const res = await callEndpoint('SendUserCascadeMessage', v.body);
        console.log(`Status: ${res.status}, Body: ${res.data}`);
    }
}

testSnakeCase().catch(console.error);

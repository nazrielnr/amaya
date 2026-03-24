const { discoverWorkingCredentials } = require('../auth-discovery/discovery-utils');
const http = require('http');
const fs = require('fs');

async function testLanguageServerError() {
    console.log('=== Testing Language Server Error Response ===\n');
    
    const creds = await discoverWorkingCredentials();
    if (!creds) {
        console.log('No credentials found');
        return;
    }
    
    console.log('[1] Using port:', creds.port);
    
    // Start cascade with quota-exhausted model
    const startBody = JSON.stringify({
        metadata: {
            ideName: 'antigravity',
            apiKey: creds.apiKey,
            locale: 'en'
        },
        modelId: 'MODEL_PLACEHOLDER_M26',
        workspacePath: 'C:\\Users\\BiuBiu\\Documents\\my app\\amaya',
        conversationMode: 'chat'
    });
    
    console.log('\n[2] Starting cascade with MODEL_PLACEHOLDER_M26 (Claude Opus - quota exhausted)...');
    
    const startRes = await new Promise((resolve, reject) => {
        const req = http.request({
            hostname: '127.0.0.1',
            port: creds.port,
            path: '/exa.language_server_pb.LanguageServerService/StartCascade',
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json',
                'connect-protocol-version': '1',
                'x-codeium-csrf-token': creds.csrfToken,
                'Origin': 'vscode-file://vscode-app'
            },
            timeout: 10000
        }, (res) => {
            let data = '';
            res.on('data', (d) => data += d);
            res.on('end', () => resolve({ status: res.statusCode, data }));
        });
        req.on('error', (e) => resolve({ error: e.message }));
        req.on('timeout', () => { req.destroy(); resolve({ error: 'timeout' }); });
        req.write(startBody);
        req.end();
    });
    
    if (startRes.error) {
        console.log('Start error:', startRes.error);
        return;
    }
    
    let cascadeId;
    try {
        const parsed = JSON.parse(startRes.data);
        cascadeId = parsed.cascadeId;
        console.log('    Cascade ID:', cascadeId);
        console.log('    Status:', startRes.status);
    } catch (e) {
        console.log('Parse error:', e.message);
        console.log('Raw:', startRes.data.substring(0, 500));
        return;
    }
    
    // Send message to trigger quota error
    console.log('\n[3] Sending message to trigger quota error...');
    
    const sendBody = JSON.stringify({
        metadata: {
            ideName: 'antigravity',
            apiKey: creds.apiKey,
            locale: 'en'
        },
        cascadeId: cascadeId,
        prompt: 'hello test'
    });
    
    const sendRes = await new Promise((resolve, reject) => {
        const req = http.request({
            hostname: '127.0.0.1',
            port: creds.port,
            path: '/exa.language_server_pb.LanguageServerService/SendUserCascadeMessage',
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json, text/event-stream',
                'connect-protocol-version': '1',
                'x-codeium-csrf-token': creds.csrfToken,
                'Origin': 'vscode-file://vscode-app'
            },
            timeout: 15000
        }, (res) => {
            let data = '';
            res.on('data', (d) => data += d);
            res.on('end', () => resolve({ status: res.statusCode, data }));
        });
        req.on('error', (e) => resolve({ error: e.message }));
        req.on('timeout', () => { req.destroy(); resolve({ error: 'timeout' }); });
        req.write(sendBody);
        req.end();
    });
    
    console.log('\n[4] Response:');
    console.log('    Status:', sendRes.status);
    console.log('    Error:', sendRes.error);
    
    if (sendRes.data) {
        console.log('\n[5] Raw response (first 1500 chars):');
        console.log(sendRes.data.substring(0, 1500));
        
        // Save full response
        fs.writeFileSync('language-server-error-response.txt', sendRes.data);
        console.log('\n[6] Full response saved to language-server-error-response.txt');
        
        // Parse for error indicators
        console.log('\n[7] Searching for error patterns...');
        const errorPatterns = [
            /userErrorMessage/i,
            /shortError/i,
            /modelErrorMessage/i,
            /fullError/i,
            /exhausted/i,
            /quota/i,
            /CORTEX_STEP_TYPE_ERROR_MESSAGE/i,
            /CASCADE_RUN_STATUS_FAILED/i
        ];
        
        for (const pattern of errorPatterns) {
            const matches = sendRes.data.match(pattern);
            if (matches) {
                console.log(`    Found: ${pattern}`);
            }
        }
    }
}

testLanguageServerError().catch(console.error);

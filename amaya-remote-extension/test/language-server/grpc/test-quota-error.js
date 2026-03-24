const { discoverWorkingCredentials } = require('../auth-discovery/discovery-utils');
const http = require('http');

async function testQuotaError() {
    const creds = await discoverWorkingCredentials();
    if (!creds) {
        console.log('No credentials found');
        return;
    }
    
    console.log('Using port:', creds.port);
    
    // Start a new cascade with MODEL_PLACEHOLDER_M26 (Claude Opus - quota exhausted)
    const startBody = JSON.stringify({
        metadata: {
            ideName: 'antigravity',
            apiKey: creds.apiKey,
            locale: 'en'
        },
        modelId: 'MODEL_PLACEHOLDER_M26',
        workspacePath: 'C:\\Users\\BiuBiu\\Documents\\my app\\amaya',
        conversationMode: 'planning'
    });
    
    console.log('\n[1] Starting cascade with quota-exhausted model...');
    
    const startRes = await new Promise((resolve, reject) => {
        const req = http.request({
            hostname: '127.0.0.1',
            port: creds.port,
            path: '/exa.language_server_pb.LanguageServerService/StartCascade',
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json, text/event-stream',
                'connect-protocol-version': '1',
                'x-codeium-csrf-token': creds.csrfToken,
                'Origin': 'vscode-file://vscode-app'
            }
        }, (res) => {
            let data = '';
            res.on('data', (d) => data += d);
            res.on('end', () => resolve({ status: res.statusCode, data }));
        });
        req.on('error', reject);
        req.write(startBody);
        req.end();
    });
    
    console.log('Start Status:', startRes.status);
    
    let cascadeId;
    try {
        const startParsed = JSON.parse(startRes.data);
        cascadeId = startParsed.cascadeId;
        console.log('Cascade ID:', cascadeId);
    } catch (e) {
        console.log('Start response parse error');
        console.log('Raw data:', startRes.data.substring(0, 500));
        return;
    }
    
    // Send a message to trigger quota error
    console.log('\n[2] Sending message to trigger quota error...');
    
    const sendBody = JSON.stringify({
        metadata: {
            ideName: 'antigravity',
            apiKey: creds.apiKey,
            locale: 'en'
        },
        cascadeId: cascadeId,
        prompt: 'hello'
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
            timeout: 10000
        }, (res) => {
            let data = '';
            res.on('data', (d) => data += d);
            res.on('end', () => resolve({ status: res.statusCode, data }));
            res.on('error', (e) => resolve({ status: res.statusCode, data, error: e.message }));
        });
        req.on('error', (e) => resolve({ error: e.message }));
        req.on('timeout', () => { req.destroy(); resolve({ error: 'timeout' }); });
        req.write(sendBody);
        req.end();
    });
    
    console.log('Send Status:', sendRes.status);
    console.log('Send Error:', sendRes.error);
    
    if (sendRes.data) {
        console.log('\n[3] Raw response (first 2000 chars):');
        console.log(sendRes.data.substring(0, 2000));
        
        // Save full response
        require('fs').writeFileSync('quota-error-response.json', sendRes.data);
        console.log('\n[4] Full response saved to quota-error-response.json');
        
        // Parse and look for error fields
        console.log('\n[5] Parsing response for error indicators...');
        const lines = sendRes.data.split('\n');
        for (const line of lines) {
            if (line.includes('error') || line.includes('Error') || line.includes('ERROR') ||
                line.includes('quota') || line.includes('Quota') || line.includes('exhausted') ||
                line.includes('failed') || line.includes('FAILED')) {
                console.log('ERROR LINE:', line.substring(0, 300));
            }
        }
    } else {
        console.log('No response data - connection may have been reset');
    }
}

testQuotaError().catch(console.error);

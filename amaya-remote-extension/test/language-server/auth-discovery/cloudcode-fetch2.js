const fs = require('fs');
const path = require('path');
const https = require('https');

let apiKey = '';
const stateDb = path.join(process.env.APPDATA || '', 'Antigravity', 'User', 'globalStorage', 'state.vscdb');
if (fs.existsSync(stateDb)) {
    const bin = fs.readFileSync(stateDb);
    const match = bin.toString('latin1').match(/ya29\.[A-Za-z0-9_.-]{50,}/);
    if (match) apiKey = match[0];
}

async function requestJson(endpoint, body) {
    return new Promise((resolve) => {
        const options = {
            hostname: 'daily-cloudcode-pa.googleapis.com',
            port: 443,
            path: endpoint,
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${apiKey}`,
                'Content-Type': 'application/json',
            },
        };

        const req = https.request(options, (res) => {
            let data = '';
            res.on('data', c => data += c);
            res.on('end', () => resolve(data));
        });

        req.on('error', e => resolve('Error: ' + e.message));
        req.write(JSON.stringify(body));
        req.end();
    });
}

async function main() {
    if (!apiKey) {
        console.log("No API Key");
        return;
    }

    console.log("1. Calling loadCodeAssist...");
    const assistRes = await requestJson('/v1internal:loadCodeAssist', {
        metadata: { ideName: 'antigravity', locale: 'en', ideVersion: '1.19.6', extensionName: 'antigravity' }
    });

    let projectId;
    try {
        const parsed = JSON.parse(assistRes);
        projectId = parsed.cloudaicompanionProject?.projectId || parsed.cloudaicompanionProject;
        console.log("Found Project ID:", projectId);
    } catch (e) {
        console.log("Failed to parse assistRes");
        return;
    }

    console.log("2. Calling fetchAvailableModels...");
    const modelsRes = await requestJson('/v1internal:fetchAvailableModels', {
        metadata: { ideName: 'antigravity', locale: 'en', ideVersion: '1.19.6', extensionName: 'antigravity' },
        project: projectId
    });

    try {
        const parsed = JSON.parse(modelsRes);
        if (parsed.models) {
            console.log("\n---- DYNAMIC MODELS FOUND ----\n");
            console.log(parsed.models.map(m => `[${m.modelId || m.model}] ${m.displayName}`).join('\n'));
        } else {
            console.log("\nNo models array, full output:", modelsRes.substring(0, 1000));
        }
    } catch (e) {
        console.log("Failed to parse", modelsRes.substring(0, 100));
    }
}
main();

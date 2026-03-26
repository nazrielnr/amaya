const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');
const os = require('os');
const http = require('http');

// Get credentials
const tmpPs1 = path.join(os.tmpdir(), 'get-creds.ps1');
fs.writeFileSync(tmpPs1, [
    '$p = Get-WmiObject Win32_Process | Where-Object { $_.Name -like "*language_server*" } | Select-Object -First 1',
    'if (-not $p) { exit 0 }',
    'Write-Host "CMDLINE:$($p.CommandLine)"',
    'Get-NetTCPConnection -OwningProcess $p.ProcessId -State Listen -ErrorAction SilentlyContinue | ForEach-Object {',
    '  Write-Host "LSPORT:$($_.LocalPort)"',
    '}',
].join('\r\n'));
const output = execSync(`powershell.exe -NoProfile -ExecutionPolicy Bypass -File "${tmpPs1}"`, { timeout: 10000, encoding: 'utf8' }).trim();
fs.unlinkSync(tmpPs1);

const csrf = (output.match(/--csrf_token\s+([a-f0-9-]{36})/) || [])[1];
const ports = [...output.matchAll(/LSPORT:(\d+)/g)].map(m => parseInt(m[1]));

let apiKey = '';
const stateDb = path.join(process.env.APPDATA || '', 'Antigravity', 'User', 'globalStorage', 'state.vscdb');
if (fs.existsSync(stateDb)) {
    const bin = fs.readFileSync(stateDb);
    const match = bin.toString('latin1').match(/ya29\.[A-Za-z0-9_.-]{50,}/);
    if (match) apiKey = match[0];
}

function callEndpoint(port, endpointName, body) {
    return new Promise(resolve => {
        const data = JSON.stringify(body);
        const req = http.request({
            hostname: '127.0.0.1', port,
            path: `/exa.language_server_pb.LanguageServerService/${endpointName}`,
            method: 'POST', timeout: 10000,
            headers: {
                'Content-Type': 'application/json',
                'connect-protocol-version': '1',
                'x-codeium-csrf-token': csrf,
                'Content-Length': Buffer.byteLength(data),
            },
        }, res => {
            let d = '';
            res.on('data', c => d += c);
            res.on('end', () => resolve({ status: res.statusCode, body: d }));
        });
        req.on('error', e => resolve({ error: e.message }));
        req.on('timeout', () => { req.destroy(); resolve({ error: 'timeout' }); });
        req.write(data);
        req.end();
    });
}

function probe(port) {
    return new Promise(resolve => {
        const req = http.request({
            hostname: '127.0.0.1', port,
            path: '/exa.language_server_pb.LanguageServerService/GetCascadeNuxes',
            method: 'POST', timeout: 500,
            headers: { 'Content-Type': 'application/json', 'connect-protocol-version': '1', 'x-codeium-csrf-token': csrf },
        }, res => { let d = ''; res.on('data', c => d += c); res.on('end', () => resolve({ port, status: res.statusCode })); });
        req.on('error', () => resolve(null));
        req.on('timeout', () => { req.destroy(); resolve(null); });
        req.write('{}');
        req.end();
    });
}

function sleep(ms) {
    return new Promise(res => setTimeout(res, ms));
}

async function main() {
    let grpcPort = 0;
    for (const p of ports) {
        const r = await probe(p);
        if (r && r.status === 200) { grpcPort = p; break; }
    }
    if (!grpcPort) {
        console.error("Could not find a valid Antigravity grpc port.");
        return;
    }

    const metadata = { ideName: 'antigravity', apiKey, locale: 'en', ideVersion: '1.19.6', extensionName: 'antigravity' };
    const prompt = process.argv[2] || 'Halo, model apa ini? Jawablah dengan sangat singkat.';
    const modelId = process.argv[3] || 'MODEL_GOOGLE_GEMINI_2_5_FLASH_THINKING';

    console.log(`Sending prompt to model [${modelId}]: "${prompt}"\n`);

    // 1. Start Cascade
    const startRes = await callEndpoint(grpcPort, 'StartCascade', {
        metadata, source: 'CORTEX_TRAJECTORY_SOURCE_CASCADE_CLIENT'
    });
    if (startRes.status !== 200) { console.error('Failed to StartCascade:', startRes); return; }
    const cascadeId = JSON.parse(startRes.body).cascadeId;
    console.log('Cascade started with ID:', cascadeId);

    // 2. Send Message
    const sendRes = await callEndpoint(grpcPort, 'SendUserCascadeMessage', {
        metadata,
        cascadeId,
        items: [{ text: prompt }],
        cascadeConfig: {
            plannerConfig: {
                plannerTypeConfig: { case: 'conversational', value: {} },
                planModel: modelId,
                requestedModel: { model: modelId },
            },
        },
    });
    if (sendRes.status !== 200) { console.error('Failed to SendMessage:', sendRes); return; }

    // 3. Poll for response
    let lastStepCount = 0;
    let isDone = false;
    let lastPrintedText = '';

    while (!isDone) {
        await sleep(1000);
        const trajRes = await callEndpoint(grpcPort, 'GetCascadeTrajectory', {
            metadata, cascadeId, source: 'CORTEX_TRAJECTORY_SOURCE_CASCADE_CLIENT'
        });

        if (trajRes.status === 200) {
            const steps = JSON.parse(trajRes.body).trajectory?.steps || [];
            if (steps.length === 0) continue;

            const lastStep = steps[steps.length - 1];

            // Print intermediate planning response text
            if (lastStep.type === 'CORTEX_STEP_TYPE_PLANNER_RESPONSE' && lastStep.plannerResponse) {
                const text = lastStep.plannerResponse.response || lastStep.plannerResponse.modifiedResponse || '';
                if (text && text !== lastPrintedText) {
                    process.stdout.write(text.substring(lastPrintedText.length)); // Print only the delta
                    lastPrintedText = text;
                }

                if (lastStep.status === 'CORTEX_STEP_STATUS_DONE') {
                    const hasTools = lastStep.plannerResponse.toolCalls?.length > 0;
                    if (!hasTools) {
                        isDone = true;
                        console.log('\n\n--- COMPLETED ---');
                        break;
                    }
                }
            } else if (lastStep.type === 'CORTEX_STEP_TYPE_TOOL_EXECUTION_RESULT') {
                // For now, standalone script doesn't execute tools
                // So we just break if it gets stuck waiting for tools.
            }
        }
    }
}

main().catch(console.error);

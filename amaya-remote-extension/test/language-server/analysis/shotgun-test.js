const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');
const os = require('os');
const http = require('http');

const tmpPs1 = path.join(os.tmpdir(), 'get-creds.ps1');
fs.writeFileSync(tmpPs1, [
    '$p = Get-WmiObject Win32_Process | Where-Object { $_.Name -like "*language_server*" } | Select-Object -First 1',
    'if (-not $p) { exit 0 }',
    'Get-NetTCPConnection -OwningProcess $p.ProcessId -State Listen -ErrorAction SilentlyContinue | ForEach-Object {',
    '  Write-Host "LSPORT:$($_.LocalPort)"',
    '}',
    'Write-Host "CMDLINE:$($p.CommandLine)"',
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
            method: 'POST', timeout: 5000,
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

async function checkModel(grpcPort, modelId) {
    const metadata = { ideName: 'antigravity', apiKey, locale: 'en', ideVersion: '1.19.6', extensionName: 'antigravity' };

    const startRes = await callEndpoint(grpcPort, 'StartCascade', {
        metadata, source: 'CORTEX_TRAJECTORY_SOURCE_CASCADE_CLIENT'
    });
    if (startRes.status !== 200) return null;
    const cascadeId = JSON.parse(startRes.body).cascadeId;

    const sendRes = await callEndpoint(grpcPort, 'SendUserCascadeMessage', {
        metadata,
        cascadeId,
        items: [{ text: "Siapa kamu?" }],
        cascadeConfig: {
            plannerConfig: {
                plannerTypeConfig: { case: 'conversational', value: {} },
                planModel: modelId,
                requestedModel: { model: modelId },
            },
        },
    });

    if (sendRes.status === 200) {
        return "SUCCESS";
    } else {
        return JSON.parse(sendRes.body).message;
    }
}

async function main() {
    let grpcPort = 0;
    for (const p of ports) {
        const r = await probe(p);
        if (r && r.status === 200) { grpcPort = p; break; }
    }
    if (!grpcPort) {
        console.error("No grpc port found.");
        return;
    }

    console.log("Starting shotgun test...");
    const validModels = [];

    // Test Placeholders
    for (let i = 1; i <= 30; i++) {
        const modelId = `MODEL_PLACEHOLDER_M${i}`;
        process.stdout.write(`Testing ${modelId}... `);
        const res = await checkModel(grpcPort, modelId);
        console.log(res === "SUCCESS" ? "✅" : `❌ (${res})`);
        if (res === "SUCCESS") {
            validModels.push(modelId);
        }
    }

    console.log("\nFound valid placeholder models:", validModels);
}

main().catch(console.error);

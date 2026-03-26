// Fetch a SMALL conversation trajectory and print step types clearly
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

async function main() {
    let grpcPort = 0;
    for (const p of ports) {
        const r = await probe(p);
        if (r && r.status === 200) { grpcPort = p; break; }
    }

    const metadata = { ideName: 'antigravity', apiKey, locale: 'en', ideVersion: '1.19.6', extensionName: 'antigravity' };

    // Find a SMALL conversation
    const convDir = path.join(os.homedir(), '.gemini', 'antigravity', 'conversations');
    const convFiles = fs.readdirSync(convDir).filter(f => f.endsWith('.pb'));
    // Force use current conversation ID
    const target = { id: 'f6f674c8-5490-455e-8586-131424a103ee', size: 0 };
    console.log(`Target: ${target.id} (FORCE)\n`);
    console.log(`Target: ${target.id} (size: ${target.size})\n`);

    const trajRes = await callEndpoint(grpcPort, 'GetCascadeTrajectory', {
        metadata, cascadeId: target.id, source: 'CORTEX_TRAJECTORY_SOURCE_CASCADE_CLIENT'
    });
    if (trajRes.status !== 200) { console.log('ERROR:', trajRes); return; }

    const parsed = JSON.parse(trajRes.body);
    const steps = parsed?.trajectory?.steps || [];
    console.log(`Total steps: ${steps.length}\n`);

    // Print EVERY step with type and key data
    for (let i = 0; i < steps.length; i++) {
        const s = steps[i];
        const type = (s.type || 'UNKNOWN').replace('CORTEX_STEP_TYPE_', '');
        const status = (s.status || '').replace('CORTEX_STEP_STATUS_', '');
        let info = '';

        switch (s.type) {
            case 'CORTEX_STEP_TYPE_USER_INPUT':
                info = `"${(s.userInput?.items?.[0]?.text || '').substring(0, 80)}"`;
                break;
            case 'CORTEX_STEP_TYPE_PLANNER_RESPONSE':
                const pr = s.plannerResponse || {};
                const tools = pr.toolCalls?.map(t => t.name) || [];
                info = `stop=${pr.stopReason || 'N/A'}`;
                if (tools.length) info += ` tools=[${tools.join(',')}]`;
                if (pr.response) info += ` resp="${pr.response.substring(0, 60)}"`;
                if (pr.thinking) info += ` think="${pr.thinking.substring(0, 40)}"`;
                break;
            case 'CORTEX_STEP_TYPE_VIEW_FILE':
                info = JSON.stringify(s.viewFile || s).substring(0, 120);
                break;
            case 'CORTEX_STEP_TYPE_RUN_COMMAND':
                info = JSON.stringify(s.runCommand || s).substring(0, 120);
                break;
            default:
                // Show what keys exist
                const keys = Object.keys(s).filter(k => !['type', 'status', 'metadata'].includes(k));
                info = keys.length ? `keys=[${keys.join(',')}]` : '(no data)';
                break;
        }

        console.log(`[${i}] ${type.padEnd(25)} ${status.padEnd(12)} ${info}`);
    }

    // Save FULL steps JSON for 1 conversation
    fs.writeFileSync(path.join(os.tmpdir(), 'traj-small.json'), JSON.stringify(steps, null, 2));
    console.log(`\nSaved to ${path.join(os.tmpdir(), 'traj-small.json')}`);
}

main().catch(console.error);

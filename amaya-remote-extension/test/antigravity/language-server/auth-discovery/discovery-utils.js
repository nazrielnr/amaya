const { execSync } = require('child_process');
const http = require('http');
const https = require('https');
const fs = require('fs');
const os = require('os');
const path = require('path');

function discoverApiKeyFromStateDB() {
    try {
        const appData = path.join(process.env.APPDATA || path.join(os.homedir(), 'AppData', 'Roaming'), 'Antigravity');
        const stateDB = path.join(appData, 'User', 'globalStorage', 'state.vscdb');
        if (!fs.existsSync(stateDB)) return '';
        
        const content = fs.readFileSync(stateDB, 'latin1');
        const match = content.match(/(ya29\.[A-Za-z0-9_-]{50,})/);
        if (match) {
            console.log(`[Discovery] API key from state.vscdb: ${match[1].substring(0, 15)}...`);
            return match[1];
        }
    } catch (e) {
        console.error('[Discovery] State DB read error:', e.message);
    }
    return '';
}

function discoverCredentials() {
    // Use the same logic as AntigravityDiscovery.ts
    try {
        // Step 1: Find language_server process filtered by 'antigravity'
        const psScript = `
$processes = Get-WmiObject Win32_Process | Where-Object { $_.Name -like "*language_server*" -and ($_.ExecutablePath -like "*antigravity*" -or $_.CommandLine -like "*antigravity*") }
if (-not $processes) { exit 0 }
$p = $processes | Sort-Object CreationDate -Descending | Select-Object -First 1
Write-Host "PID:$($p.ProcessId)"
Write-Host "CMDLINE:$($p.CommandLine)"
Get-NetTCPConnection -OwningProcess $p.ProcessId -State Listen -ErrorAction SilentlyContinue | ForEach-Object {
  Write-Host "LSPORT:$($_.LocalPort)"
}`;
        const tmpPs1 = require('os').tmpdir() + '\\amaya-find-creds.ps1';
        require('fs').writeFileSync(tmpPs1, psScript.replace(/\n/g, '\r\n'), 'utf8');
        const output = execSync(`powershell.exe -NoProfile -ExecutionPolicy Bypass -File "${tmpPs1}"`, { timeout: 10000, encoding: 'utf8' }).trim();
        try { require('fs').unlinkSync(tmpPs1); } catch {}

        if (!output) {
            console.error('[Discovery] No antigravity language_server found');
            return null;
        }

        // Step 2: Extract CSRF from commandline
        const cmdLineMatch = output.match(/CMDLINE:(.*)/);
        if (!cmdLineMatch) return null;
        const cmdLine = cmdLineMatch[1];
        
        // There are TWO CSRF tokens:
        // --csrf_token = internal token
        // --extension_server_csrf_token = for extension server API (port 58203)
        // We need the extension_server_csrf_token for the extension server port
        const extCsrfMatch = cmdLine.match(/--extension_server_csrf_token\s+([a-f0-9-]{36})/);
        const mainCsrfMatch = cmdLine.match(/--csrf_token\s+([a-f0-9-]{36})/);
        
        // Prefer extension_server_csrf_token if extension_server_port is present
        const hasExtPort = cmdLine.match(/--extension_server_port\s+(\d+)/);
        const csrfToken = (hasExtPort && extCsrfMatch) ? extCsrfMatch[1] : (mainCsrfMatch ? mainCsrfMatch[1] : null);
        
        if (!csrfToken) return null;

        // Step 3: Collect all ports
        const ports = new Set();
        const fromCmdGrpc = cmdLine.match(/--grpc_port\s+(\d+)/);
        const fromCmdExt = cmdLine.match(/--extension_server_port\s+(\d+)/);
        if (fromCmdGrpc) ports.add(parseInt(fromCmdGrpc[1], 10));
        if (fromCmdExt) ports.add(parseInt(fromCmdExt[1], 10));
        
        [...output.matchAll(/LSPORT:(\d+)/g)]
            .map(m => parseInt(m[1], 10))
            .forEach(p => ports.add(p));

        const lsPorts = Array.from(ports);
        console.log(`[Discovery] Found ports: ${lsPorts.join(', ')}`);

        // Step 4: Probe each port (like authenticatedProbe in AntigravityDiscovery.ts)
        // We'll return the first port that responds
        return { ports: lsPorts, csrfToken };
        
    } catch (e) {
        console.error('[Discovery] Error:', e.message);
        return null;
    }
}

async function probePort(port, csrfToken) {
    return new Promise((resolve) => {
        const req = http.request({
            hostname: '127.0.0.1',
            port,
            path: '/exa.language_server_pb.LanguageServerService/GetCascadeNuxes',
            method: 'POST',
            timeout: 2000,
            headers: {
                'Accept': 'application/json, text/event-stream',
                'Content-Type': 'application/json',
                'connect-protocol-version': '1',
                'x-codeium-csrf-token': csrfToken,
                'Origin': 'vscode-file://vscode-app',
            },
        }, (res) => {
            let body = '';
            res.on('data', (d) => body += d);
            res.on('end', () => {
                console.log(`[Probe] Port ${port}: Status ${res.statusCode}`);
                // 200 = valid RPC endpoint, 403 = valid endpoint but need auth, 406 = valid endpoint
                resolve(res.statusCode === 200 || res.statusCode === 403 || res.statusCode === 406 || res.statusCode === 401);
            });
        });
        req.on('error', (e) => {
            console.log(`[Probe] Port ${port}: Error ${e.message}`);
            resolve(false);
        });
        req.on('timeout', () => { 
            console.log(`[Probe] Port ${port}: Timeout`);
            req.destroy(); 
            resolve(false); 
        });
        req.write(JSON.stringify({ metadata: { ideName: 'antigravity', apiKey: 'probe', locale: 'en' } }));
        req.end();
    });
}

async function findWorkingPort(ports, csrfToken) {
    // Try each port with the given CSRF token
    for (const port of ports) {
        const ok = await probePort(port, csrfToken);
        if (ok) {
            console.log(`[Discovery] Working port found: ${port}`);
            return port;
        }
    }
    return null;
}

async function probePortWithAnyCsrf(port, csrfTokens, apiKey = 'probe') {
    for (const csrf of csrfTokens) {
        const result = await new Promise((resolve) => {
            const req = http.request({
                hostname: '127.0.0.1',
                port,
                path: '/exa.language_server_pb.LanguageServerService/GetCascadeNuxes',
                method: 'POST',
                timeout: 2000,
                headers: {
                    'Accept': 'application/json, text/event-stream',
                    'Content-Type': 'application/json',
                    'connect-protocol-version': '1',
                    'x-codeium-csrf-token': csrf,
                    'Origin': 'vscode-file://vscode-app',
                },
            }, (res) => {
                let body = '';
                res.on('data', (d) => body += d);
                res.on('end', () => {
                    console.log(`[Probe] Port ${port} CSRF ${csrf.substring(0,8)}...: Status ${res.statusCode}`);
                    // Only 200 means authenticated successfully
                    if (res.statusCode === 200) {
                        resolve({ ok: true, csrf, status: res.statusCode });
                    } else {
                        resolve({ ok: false, status: res.statusCode, body });
                    }
                });
            });
            req.on('error', (e) => {
                console.log(`[Probe] Port ${port}: Error ${e.message}`);
                resolve({ ok: false });
            });
            req.on('timeout', () => { 
                console.log(`[Probe] Port ${port}: Timeout`);
                req.destroy(); 
                resolve({ ok: false }); 
            });
            req.write(JSON.stringify({ metadata: { ideName: 'antigravity', apiKey, locale: 'en' } }));
            req.end();
        });
        if (result.ok) return result;
    }
    return null;
}

async function discoverWorkingCredentials() {
    const rawCreds = discoverCredentials();
    if (!rawCreds) return null;
    
    // Get API key from state.vscdb
    const apiKey = discoverApiKeyFromStateDB();
    
    // Get both CSRF tokens from commandline
    const tmpPs1 = os.tmpdir() + '\\amaya-find-creds.ps1';
    const psScript = `
$processes = Get-WmiObject Win32_Process | Where-Object { $_.Name -like "*language_server*" -and ($_.ExecutablePath -like "*antigravity*" -or $_.CommandLine -like "*antigravity*") }
if (-not $processes) { exit 0 }
$p = $processes | Sort-Object CreationDate -Descending | Select-Object -First 1
Write-Host "CMDLINE:$($p.CommandLine)"`;
    fs.writeFileSync(tmpPs1, psScript.replace(/\n/g, '\r\n'), 'utf8');
    const output = execSync(`powershell.exe -NoProfile -ExecutionPolicy Bypass -File "${tmpPs1}"`, { timeout: 10000, encoding: 'utf8' }).trim();
    try { fs.unlinkSync(tmpPs1); } catch {}
    
    const cmdLineMatch = output.match(/CMDLINE:(.*)/);
    if (!cmdLineMatch) return null;
    const cmdLine = cmdLineMatch[1];
    
    const extCsrfMatch = cmdLine.match(/--extension_server_csrf_token\s+([a-f0-9-]{36})/);
    const mainCsrfMatch = cmdLine.match(/--csrf_token\s+([a-f0-9-]{36})/);
    const csrfTokens = [extCsrfMatch?.[1], mainCsrfMatch?.[1]].filter(Boolean);
    
    console.log(`[Discovery] CSRF tokens: ${csrfTokens.map(t => t.substring(0,8)+'...').join(', ')}`);
    console.log(`[Discovery] API key: ${apiKey ? apiKey.substring(0,15)+'...' : 'NOT FOUND'}`);
    
    // Try each port with each CSRF token (with API key for auth)
    for (const port of rawCreds.ports) {
        const result = await probePortWithAnyCsrf(port, csrfTokens, apiKey || 'probe');
        if (result) {
            return { port, csrfToken: result.csrf, apiKey };
        }
    }
    return null;
}

module.exports = { discoverCredentials, findWorkingPort, probePort, discoverWorkingCredentials };

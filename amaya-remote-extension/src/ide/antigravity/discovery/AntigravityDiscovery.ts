import * as os from 'os';
import * as fs from 'fs';
import * as path from 'path';
import * as vscode from 'vscode';
import { execSync } from 'child_process';
import * as http from 'http';
import * as https from 'https';
import { ANTIGRAVITY_CLIENT_CONSTANTS } from '../core/AntigravityProtocol';

/**
 * AntigravityDiscovery.ts
 * 
 * Handles all complex heuristics required to locate and authenticate with 
 * a locally running Antigravity Language Server instance.
 */
export class AntigravityDiscovery {

    public static getHostIDEEnv(): 'Antigravity' | 'Windsurf' | 'VSCode' {
        const appName = (vscode.env.appName || '').toLowerCase();
        if (appName.includes('antigravity')) return 'Antigravity';
        if (appName.includes('windsurf')) return 'Windsurf';
        return 'VSCode';
    }

    private static getAppDataRoot(): string {
        const ide = this.getHostIDEEnv();
        const base = process.env.APPDATA || path.join(os.homedir(), 'AppData', 'Roaming');
        if (ide === 'Windsurf') return path.join(base, 'Windsurf');
        // Default to Antigravity as it's the primary target
        return path.join(base, 'Antigravity');
    }

    static discoverFromModuleCache(): { port: number; csrfToken: string } | null {
        try {
            const ide = this.getHostIDEEnv();
            const cache = require.cache;
            for (const [key, mod] of Object.entries(cache)) {
                // Filter by keyword depending on which IDE we are running in
                if (!key.includes('antigravity') || !mod?.exports) { continue; }
                const exp = mod.exports;

                const candidates = [
                    exp.LanguageServerClient,
                    exp.default?.LanguageServerClient,
                    exp.languageServerClient,
                    exp.default?.languageServerClient,
                ];

                for (const cls of candidates) {
                    if (!cls) { continue; }
                    const instance = typeof cls.getInstance === 'function' ? cls.getInstance() : cls;
                    if (!instance) { continue; }

                    const procCandidates = [
                        instance.process,
                        instance._process,
                        instance.serverProcess,
                        instance._serverProcess,
                    ];

                    for (const proc of procCandidates) {
                        if (!proc || !proc.csrfToken) { continue; }
                        const port = proc.httpsPort || proc.httpPort || proc.port || proc.lspPort || proc.grpcPort || 0;
                        if (port && proc.csrfToken) {
                            console.log(`[AntigravityDiscovery] Module cache: port=${port} csrf=${proc.csrfToken.substring(0, 8)}`);
                            return { port, csrfToken: proc.csrfToken };
                        }
                    }

                    if (typeof instance === 'object') {
                        for (const prop of Object.keys(instance)) {
                            const val = instance[prop];
                            if (val && typeof val === 'object' && val.csrfToken) {
                                const port = val.httpsPort || val.httpPort || val.port || val.lspPort || val.grpcPort || 0;
                                if (port) {
                                    console.log(`[AntigravityDiscovery] Module cache (deep): port=${port} csrf=${val.csrfToken.substring(0, 8)}`);
                                    return { port, csrfToken: val.csrfToken };
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: any) {
            console.error('[AntigravityDiscovery] Module cache scan error:', e.message);
        }
        return null;
    }

    static discoverFromExtensionApi(): { port: number; csrfToken: string } | null {
        try {
            const antiExt = vscode.extensions.getExtension('google.antigravity');
            if (!antiExt || !antiExt.isActive || !antiExt.exports) return null;

            const api = antiExt.exports;
            const candidates = [
                api.LanguageServerClient,
                api.languageServerClient,
                api.default?.LanguageServerClient,
                api.cascade,
            ];

            for (const cls of candidates) {
                if (!cls) { continue; }
                const instance = typeof cls.getInstance === 'function' ? cls.getInstance() : cls;
                if (!instance) { continue; }

                const propsToCheck = ['process', '_process', 'serverProcess', '_serverProcess', ...Object.keys(instance)];
                for (const prop of propsToCheck) {
                    try {
                        const val = (instance as any)[prop];
                        if (val && typeof val === 'object' && val.csrfToken) {
                            const port = val.httpsPort || val.httpPort || val.port || val.lspPort || val.grpcPort || 0;
                            if (port) {
                                console.log(`[AntigravityDiscovery] ExtAPI: port=${port} csrf=${val.csrfToken.substring(0, 8)}`);
                                return { port, csrfToken: val.csrfToken };
                            }
                        }
                    } catch { }
                }
            }

            const visited = new Set();
            const search = (obj: any, depth: number): { port: number; csrfToken: string } | null => {
                if (depth > 3 || !obj || typeof obj !== 'object' || visited.has(obj)) { return null; }
                visited.add(obj);
                if (obj.csrfToken && (obj.httpsPort || obj.httpPort || obj.port)) {
                    const port = obj.httpsPort || obj.httpPort || obj.port || 0;
                    return { port, csrfToken: obj.csrfToken };
                }
                for (const key of Object.keys(obj)) {
                    try {
                        const result = search(obj[key], depth + 1);
                        if (result) { return result; }
                    } catch { }
                }
                return null;
            };
            return search(api, 0);
        } catch (e: any) {
            console.error('[AntigravityDiscovery] ExtAPI error:', e.message);
        }
        return null;
    }

    static discoverFromLogs(): { port: number; csrfToken: string } | null {
        try {
            const ide = this.getHostIDEEnv();
            const appData = this.getAppDataRoot();
            const logDir = path.join(appData, 'logs');
            if (!fs.existsSync(logDir)) { return null; }

            const folders = fs.readdirSync(logDir).sort().reverse();
            for (const folder of folders.slice(0, 5)) {
                const sessionDir = path.join(logDir, folder);
                if (!fs.statSync(sessionDir).isDirectory()) { continue; }

                let items: string[] = [];
                try { items = fs.readdirSync(sessionDir); } catch { continue; }

                for (const item of items) {
                    if (!item.startsWith('window')) { continue; }
                    
                    // Windsurf and Antigravity log structures usually follow the same pattern for the fork
                    const logFile = path.join(sessionDir, item, 'exthost', 'google.antigravity', 'Antigravity.log');
                    if (!fs.existsSync(logFile)) { continue; }

                    try {
                        const content = fs.readFileSync(logFile, 'utf8');
                        const portMatch = content.match(/server client at port (\d+)/i) ||
                            content.match(/port:\s*(\d{4,5})/i);
                        const csrfMatch = content.match(/csrfToken['": ]+([a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12})/i);

                        if (portMatch && csrfMatch) {
                            console.log(`[AntigravityDiscovery] Credentials from logs: port=${portMatch[1]} csrf=${csrfMatch[1].substring(0, 8)}`);
                            return { port: parseInt(portMatch[1]), csrfToken: csrfMatch[1] };
                        }
                    } catch { }
                }
            }
        } catch (e: any) {
            console.error('[AntigravityDiscovery] Log discovery error:', e.message);
        }
        return null;
    }

    static discoverApiKeyFromStateDB(): string {
        try {
            const appData = this.getAppDataRoot();
            const stateDB = path.join(appData, 'User', 'globalStorage', 'state.vscdb');
            if (!fs.existsSync(stateDB)) { return ''; }

            const content = fs.readFileSync(stateDB, 'latin1');
            const match = content.match(/(ya29\.[A-Za-z0-9_-]{50,})/);
            if (match) {
                console.log(`[AntigravityDiscovery] API key from state.vscdb: ${match[1].substring(0, 15)}...`);
                return match[1];
            }
        } catch (e: any) {
            console.error('[AntigravityDiscovery] State DB read error:', e.message);
        }
        return '';
    }

    static async discoverFromProcessCmdLine(apiKey: string = ''): Promise<{ port: number; csrfToken: string } | null> {
        try {
            const ide = this.getHostIDEEnv();
            const targetTag = ide.toLowerCase(); // 'antigravity' or 'windsurf'

            const tmpPs1 = path.join(os.tmpdir(), 'amaya-find-creds.ps1');
            const psLines = [
                `$processes = Get-WmiObject Win32_Process | Where-Object { $_.Name -like "*language_server*" -and ($_.ExecutablePath -like "*${targetTag}*" -or $_.CommandLine -like "*${targetTag}*") }`,
                'if (-not $processes) { exit 0 }',
                '$p = $processes | Sort-Object CreationDate -Descending | Select-Object -First 1',
                'Write-Host "PID:$($p.ProcessId)"',
                'Write-Host "CMDLINE:$($p.CommandLine)"',
                'Get-NetTCPConnection -OwningProcess $p.ProcessId -State Listen -ErrorAction SilentlyContinue | ForEach-Object {',
                '  Write-Host "LSPORT:$($_.LocalPort)"',
                '}'
            ];
            fs.writeFileSync(tmpPs1, psLines.join('\r\n'), 'utf8');
            const output = execSync(
                `powershell.exe -NoProfile -ExecutionPolicy Bypass -File "${tmpPs1}"`,
                { timeout: 10000, encoding: 'utf8' }
            ).trim();
            try { fs.unlinkSync(tmpPs1); } catch { }

            if (!output) {
                console.log(`[AntigravityDiscovery] No language_server process found for tags: ${targetTag}`);
                return null;
            }

            const cmdLineMatch = output.match(/CMDLINE:(.*)/);
            if (!cmdLineMatch) return null;
            const cmdLine = cmdLineMatch[1];
            const pidMatch = output.match(/PID:(\d+)/);
            const pid = pidMatch ? parseInt(pidMatch[1], 10) : 0;

            const csrfMatch = cmdLine.match(/--csrf_token\s+([a-f0-9-]{36})/);
            if (!csrfMatch) return null;
            const csrfToken = csrfMatch[1];

            const ports = new Set<number>();
            const fromCmdGrpc = cmdLine.match(/--grpc_port\s+(\d+)/);
            const fromCmdExt = cmdLine.match(/--extension_server_port\s+(\d+)/);
            if (fromCmdGrpc) ports.add(parseInt(fromCmdGrpc[1], 10));
            if (fromCmdExt) ports.add(parseInt(fromCmdExt[1], 10));

            [...output.matchAll(/LSPORT:(\d+)/g)]
                .map(m => parseInt(m[1], 10))
                .forEach(p => ports.add(p));

            // Extra fallback from netstat (same PID)
            if (pid > 0) {
                try {
                    const net = execSync('netstat -ano -p tcp', { encoding: 'utf8' });
                    const lines = net.split(/\r?\n/);
                    for (const ln of lines) {
                        const line = ln.trim();
                        if (!line.startsWith('TCP')) continue;
                        const parts = line.split(/\s+/);
                        if (parts.length < 5) continue;
                        const state = parts[3];
                        const owner = parseInt(parts[4], 10);
                        if (state !== 'LISTENING' || owner !== pid) continue;
                        const addr = parts[1];
                        const p = parseInt(addr.split(':').pop() || '', 10);
                        if (!isNaN(p)) ports.add(p);
                    }
                } catch {
                    // ignore netstat failures
                }
            }

            const lsPorts = Array.from(ports);

            // Prefer authenticated probe: only real language-server RPC port should return 200.
            if (apiKey) {
                for (const lsPort of lsPorts) {
                    if (await this.authenticatedProbe(lsPort, csrfToken, apiKey)) {
                        console.log(`[AntigravityDiscovery] LS authenticated port: ${lsPort}`);
                        return { port: lsPort, csrfToken };
                    }
                }
            }

            for (const lsPort of lsPorts) {
                if (await this.quickProbe(lsPort, false)) {
                    console.log(`[AntigravityDiscovery] LS HTTP gRPC port: ${lsPort}`);
                    return { port: lsPort, csrfToken };
                }
            }
            for (const lsPort of lsPorts) {
                if (await this.quickProbe(lsPort, true)) {
                    console.log(`[AntigravityDiscovery] LS HTTPS gRPC port: ${lsPort}`);
                    return { port: lsPort, csrfToken };
                }
            }

            return { port: lsPorts[0] || 0, csrfToken };
        } catch (e: any) {
            console.error('[AntigravityDiscovery] Process cmdline error:', e.message);
        }
        return null;
    }

    private static async authenticatedProbe(port: number, csrfToken: string, apiKey: string): Promise<boolean> {
        return new Promise((resolve) => {
            const req = http.request({
                hostname: '127.0.0.1',
                port,
                path: '/exa.language_server_pb.LanguageServerService/GetCascadeNuxes',
                method: 'POST',
                timeout: 1200,
                headers: {
                    'Accept': 'application/json, text/event-stream',
                    'Content-Type': 'application/json',
                    'connect-protocol-version': '1',
                    'x-codeium-csrf-token': csrfToken,
                    'Origin': 'vscode-file://vscode-app',
                },
            }, (res) => {
                // 200 indicates this is the real RPC endpoint with valid auth context.
                // 403/404 usually means wrong port/token combo.
                res.resume();
                resolve(res.statusCode === 200);
            });
            req.on('error', () => resolve(false));
            req.on('timeout', () => { req.destroy(); resolve(false); });
            req.write(JSON.stringify({
                metadata: {
                    ideName: ANTIGRAVITY_CLIENT_CONSTANTS.ideName,
                    apiKey,
                    locale: 'en',
                    ideVersion: ANTIGRAVITY_CLIENT_CONSTANTS.ideVersion,
                    extensionName: ANTIGRAVITY_CLIENT_CONSTANTS.extensionName,
                }
            }));
            req.end();
        });
    }

    static async quickProbe(port: number, useHttps: boolean): Promise<boolean> {
        return new Promise((resolve) => {
            const mod = useHttps ? https : http;
            const req = mod.request({
                hostname: '127.0.0.1',
                port,
                path: '/exa.language_server_pb.LanguageServerService/GetCascadeNuxes',
                method: 'POST',
                rejectUnauthorized: false,
                timeout: 300,
                headers: {
                    'Content-Type': 'application/json',
                    'connect-protocol-version': '1',
                },
            } as any, (res) => {
                res.resume();
                resolve(res.statusCode === 403 || res.statusCode === 200 || res.statusCode === 401);
            });
            req.on('error', () => resolve(false));
            req.on('timeout', () => { req.destroy(); resolve(false); });
            req.write('{}');
            req.end();
        });
    }

    static async getApiKeyFromAuth(): Promise<string> {
        try {
            const session = await vscode.authentication.getSession('google', ['openid', 'email'], { silent: true });
            if (session?.accessToken) {
                return session.accessToken;
            }
        } catch (e: any) {
            console.error('[AntigravityDiscovery] Auth API error:', e.message);
        }
        return '';
    }

    static async probeCsrfToken(): Promise<string> {
        try {
            const cache = require.cache;
            for (const [key, mod] of Object.entries(cache)) {
                if (!key.includes('antigravity') || !mod?.exports) { continue; }
                const str = String(mod.exports).substring(0, 10000);
                const match = str.match(/csrfToken['":\s]+([a-f0-9-]{36})/i);
                if (match) { return match[1]; }
            }
        } catch { }
        return '';
    }
}

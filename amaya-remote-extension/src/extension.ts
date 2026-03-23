import * as vscode from 'vscode';
import * as WebSocket from 'ws';
import { createIDEServices, parseIDEProvider, IDEProviderId } from './ide/IDEBootstrap';
import { MessageHandler } from './controllers/MessageHandler';
import { AppState } from './types';

// ── State ────────────────────────────────────────────────────────

const state: AppState = {
    isLoading: false,
    isStreaming: false,
    activeSessionId: null,
    selectedModelId: '',
    models: [],
};

let wss: WebSocket.Server | null = null;
let statusBar: vscode.StatusBarItem;
let currentProvider: IDEProviderId = 'antigravity';
let ideServices = createIDEServices(currentProvider);
let api = ideServices.api;
let commandExecutor = ideServices.commandExecutor;
let runStatusMapper = ideServices.runStatusMapper;
let handler: MessageHandler | null = null;
let isStarting = false;
let isStopping = false;
let startServerPromise: Promise<void> | null = null;

// ── Activation ───────────────────────────────────────────────────

export async function activate(context: vscode.ExtensionContext) {
    try {
        console.log('[Amaya Remote] Activating with Modular OOP Architecture...');

    currentProvider = parseIDEProvider(
        vscode.workspace.getConfiguration('amayaRemote').get<string>('provider', 'antigravity')
    );
    ideServices = createIDEServices(currentProvider);
    api = ideServices.api;
    commandExecutor = ideServices.commandExecutor;
    runStatusMapper = ideServices.runStatusMapper;

    statusBar = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Right, 100);
    context.subscriptions.push(statusBar);
    updateStatusBar();

    // Register commands
    context.subscriptions.push(
        vscode.commands.registerCommand('amayaRemote.startServer', () => startServer()),
        vscode.commands.registerCommand('amayaRemote.stopServer', () => stopServer()),
        vscode.commands.registerCommand('amayaRemote.setCredentials', () => promptCredentials()),
        vscode.commands.registerCommand('amayaRemote.diagnose', async () => {
            const diag = api.getDiagnostics();
            vscode.window.showInformationMessage(
                `Amaya Remote Diagnostics:\n${diag}`,
                'Re-initialize', 'Set Credentials'
            ).then(async (choice) => {
                if (choice === 'Re-initialize') {
                    const ok = await api.initialize();
                    if (ok) {
                        vscode.window.showInformationMessage(`✅ Connected to ${api.getBackendName()}!\n${api.getDiagnostics()}`);
                    } else {
                        vscode.window.showInformationMessage(`❌ Still failed.\n${api.getDiagnostics()}`);
                    }
                } else if (choice === 'Set Credentials') {
                    promptCredentials();
                }
            });
        }),
    );

    // Auto-initialize API with retry
    let ok = false;
    for (let attempt = 1; attempt <= 3; attempt++) {
        console.log(`[Amaya Remote] Init attempt ${attempt}/3...`);
        ok = await api.initialize();
        if (ok) {
            console.log(`[Amaya Remote] ✅ IDE client initialized! (IDE: ${api.getBackendName()})`);
            vscode.window.showInformationMessage(`Amaya Remote: Connected to ${api.getBackendName()}`);
            break;
        }
        if (attempt < 3) {
            console.log(`[Amaya Remote] Waiting 5s before retry...`);
            await new Promise(r => setTimeout(r, 5000));
        }
    }

    if (!ok) {
                        vscode.window.showInformationMessage(`✅ Connected to ${api.getBackendName()} (${currentProvider})!\n${api.getDiagnostics()}`);
        vscode.window.showWarningMessage(
            'Amaya Remote: Cannot connect to underlying Language Server. Server will not start until initialized.',
            'Set Credentials', 'Diagnose'
        ).then((choice) => {
            if (choice === 'Set Credentials') { promptCredentials(); }
            else if (choice === 'Diagnose') { vscode.commands.executeCommand('amayaRemote.diagnose'); }
        });
        return; // Don't auto-start if initialization failed
    }

        // Auto-start if configured
        const autoStart = vscode.workspace.getConfiguration('amayaRemote').get<boolean>('autoStart', false);
        if (autoStart && ok) {
            await startServer();
        }
    } catch (err: any) {
        console.error('[Amaya Remote] CRITICAL CRASH DURING ACTIVATION:', err);
        vscode.window.showErrorMessage(`Amaya Remote failed to activate: ${err.message}`);
    }
}

export function deactivate() {
    stopServer();
}

// ── Manual Credentials Prompt ────────────────────────────────────

async function promptCredentials() {
    const port = await vscode.window.showInputBox({ prompt: 'Language Server Port', value: '53125' });
    const csrf = await vscode.window.showInputBox({ prompt: 'CSRF Token (UUID)' });
    const apiKey = await vscode.window.showInputBox({ prompt: 'API Key (ya29...)' });

    if (port && csrf && apiKey) {
        api.setCredentials(parseInt(port), csrf, apiKey);
        vscode.window.showInformationMessage('Amaya Remote: Credentials set!');
    }
}

// ── WebSocket Server ─────────────────────────────────────────────

import { ConnectivityManager } from './connectivity/ConnectivityManager';

// ... existing code ...

async function startServer() {
    // Check if API is initialized first
    if (!api.isReady()) {
        vscode.window.showErrorMessage(
            'Amaya Remote: API not initialized. Please wait for initialization or set credentials.',
            'Set Credentials', 'Diagnose'
        ).then((choice) => {
            if (choice === 'Set Credentials') { promptCredentials(); }
            else if (choice === 'Diagnose') { vscode.commands.executeCommand('amayaRemote.diagnose'); }
        });
        return;
    }

    // Prevent concurrent start attempts
    if (isStarting) {
        console.warn('[Amaya Remote] Start already in progress, ignoring duplicate request');
        return;
    }
    if (isStopping) {
        vscode.window.showWarningMessage('Amaya Remote: Server is stopping, please wait...');
        return;
    }
    if (wss) {
        vscode.window.showWarningMessage('Amaya Remote: Server already running');
        return;
    }

    isStarting = true;
    updateStatusBar();

    // Store the promise to track this start operation
    startServerPromise = (async () => {
        await vscode.window.withProgress({
            location: vscode.ProgressLocation.Notification,
            title: "Starting Amaya Remote Server..."
        }, async (progress) => {
            try {
                const port = vscode.workspace.getConfiguration('amayaRemote').get<number>('port', 8765);
                console.log(`[Amaya Remote] Starting server on port ${port}...`);

                const server = new WebSocket.Server({ port });

                // Heartbeat to prune stale connections
                const interval = setInterval(() => {
                    server.clients.forEach((ws: any) => {
                        if (ws.isAlive === false) return ws.terminate();
                        ws.isAlive = false;
                        ws.ping();
                    });
                }, 30000);

                server.on('close', () => clearInterval(interval));

                await new Promise<void>((resolve, reject) => {
                    server.once('listening', () => resolve());
                    server.once('error', (err) => reject(err));
                });

                wss = server;
                if (!handler) {
                    handler = new MessageHandler(api, state, wss, commandExecutor, runStatusMapper);
                } else {
                    handler.updateServer(wss);
                }

                wss.on('connection', (ws: any) => {
                    ws.isAlive = true;
                    ws.on('pong', () => { ws.isAlive = true; });

                    console.log('[Amaya Remote] Client connected');
                    updateStatusBar();

                    // Auto-sync state on connect so Android receives state/events
                    handler?.handleCommand({ action: 'get_state', data: {} }, ws).catch((e: any) => {
                        console.error('[Amaya Remote] Auto get_state failed:', e?.message || e);
                    });

                    ws.on('message', async (raw: WebSocket.RawData) => {
                        try {
                            const msg = JSON.parse(raw.toString());
                            await handler?.handleCommand(msg, ws);
                        } catch (e: any) {
                            console.error('[Amaya Remote] Message parse error:', e.message);
                        }
                    });

                    ws.on('close', () => {
                        console.log('[Amaya Remote] Client disconnected');
                        updateStatusBar();
                    });

                    ws.on('error', (err: any) => {
                        console.error('[Amaya Remote] Socket error:', err.message);
                        ws.terminate();
                        updateStatusBar();
                    });
                });

                wss.on('error', (err) => {
                    console.error('[Amaya Remote] Server runtime error:', err.message);
                    stopServer();
                });

                console.log(`[Amaya Remote] ✅ WebSocket server started on port ${port}`);
                vscode.window.showInformationMessage(`Amaya Remote: Server running on port ${port}`);

                ConnectivityManager.showConnectionInfo(port);
            } catch (err: any) {
                console.error('[Amaya Remote] ❌ Server start failure:', err.message);
                vscode.window.showErrorMessage(`Amaya Remote: Failed to start server — ${err.message}`);
                // Full cleanup on error
                wss = null;
                handler = null;
                state.isLoading = false;
                state.isStreaming = false;
            } finally {
                isStarting = false;
                startServerPromise = null;
                updateStatusBar();
            }
        });
    })();

    await startServerPromise;
}

async function stopServer() {
    if (isStopping) {
        console.warn('[Amaya Remote] Stop already in progress');
        return;
    }

    if (!wss) {
        // Force reset state if somehow stuck
        console.warn('[Amaya Remote] Server not running, resetting stuck state');
        isStarting = false;
        isStopping = false;
        startServerPromise = null;
        // Keep handler instance so it can preserve active conversation anchors across restarts.
        handler?.updateServer(null);
        state.isLoading = false;
        state.isStreaming = false;
        updateStatusBar();
        return;
    }

    isStopping = true;
    updateStatusBar();

    await vscode.window.withProgress({
        location: vscode.ProgressLocation.Notification,
        title: "Stopping Amaya Remote Server..."
    }, async () => {
        try {
            console.log('[Amaya Remote] Stopping server...');
            
            // Stop any ongoing operations
            api.stopStreaming();
            state.isLoading = false;
            state.isStreaming = false;

            const serverToClose = wss;
            // Immediate nulling to prevent logic races
            wss = null;
            // Detach server but keep handler instance to preserve activeSessionId/streaming maps.
            handler?.updateServer(null);
            startServerPromise = null;

            if (serverToClose) {
                // 1. Terminate all clients immediately
                for (const client of serverToClose.clients) {
                    try { 
                        client.terminate(); 
                    } catch (e) {
                        console.error('[Amaya Remote] Error terminating client:', e);
                    }
                }

                // 2. Close the server and wait with a safety timeout
                await Promise.race([
                    new Promise<void>(resolve => serverToClose.close(() => resolve())),
                    new Promise<void>(resolve => setTimeout(resolve, 2000)) // 2s safety timeout
                ]);
            }

            console.log('[Amaya Remote] ✅ Server stopped');
            vscode.window.showInformationMessage('Amaya Remote: Server stopped');
        } catch (err: any) {
            console.error('[Amaya Remote] ❌ Error during stop:', err);
        } finally {
            isStopping = false;
            isStarting = false; // Reset both in case of weirdness
            updateStatusBar();
        }
    });
}

function updateStatusBar() {
    if (!statusBar) return;

    if (isStarting) {
        statusBar.text = `$(sync~spin) Starting...`;
        statusBar.tooltip = `Amaya Remote is starting...`;
        statusBar.command = undefined;
        statusBar.color = new vscode.ThemeColor('statusBarItem.prominentForeground');
    } else if (isStopping) {
        statusBar.text = `$(sync~spin) Stopping...`;
        statusBar.tooltip = `Amaya Remote is stopping...`;
        statusBar.command = undefined;
        statusBar.color = new vscode.ThemeColor('errorForeground');
    } else if (wss) {
        const count = wss.clients.size;
        statusBar.text = `$(broadcast) Amaya [${count}]`;
        statusBar.tooltip = `${count} client(s) connected. Click to Stop.`;
        statusBar.command = 'amayaRemote.stopServer';
        statusBar.color = undefined;
    } else {
        statusBar.text = `$(circle-slash) Amaya OFF`;
        statusBar.tooltip = `Amaya Remote is Offline. Click to Start.`;
        statusBar.command = 'amayaRemote.startServer';
        statusBar.color = undefined;
    }
    statusBar.show();
}

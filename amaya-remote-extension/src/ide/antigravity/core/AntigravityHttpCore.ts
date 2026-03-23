import * as http from 'http';
import * as https from 'https';
import { IIDETransport } from '../../../interfaces/IIDETransport';
import { ANTIGRAVITY_CLIENT_CONSTANTS, ANTIGRAVITY_ENDPOINTS } from './AntigravityProtocol';

/**
 * AntigravityHttpCore.ts
 * 
 * Manages the low-level HTTP and SSL (HTTPS) RPC envelope logic.
 * Encapsulates payload formatting and execution to the language server.
 * 
 * Key resilience features:
 * - HTTP Keep-Alive agent (reuses TCP connections)
 * - Request deduplication (prevents parallel identical requests)
 * - Generous timeouts with exponential backoff retries
 * - Log deduplication to prevent console spam
 */
export class AntigravityHttpCore implements IIDETransport {
    private port: number;
    private useHttps: boolean;
    private csrfToken: string;
    private apiKey: string;
    private cascadeId: string | null = null;

    // === Keep-Alive Agent ===
    // Reuses TCP+TLS connections instead of creating a new one per request.
    // This is the single biggest fix for "socket hang up" errors caused by
    // connection churn overwhelming the local language server.
    private agent: http.Agent | https.Agent;

    // === Log deduplication ===
    // Window increased to 30s to prevent the same error from spamming the log
    private lastErrorLogAt: Map<string, number> = new Map();
    private readonly ERROR_LOG_DEDUPE_MS = 30000;

    // === Connection health tracking ===
    private connectionHealthy: boolean = true;
    private lastSuccessfulRequestAt: number = 0;

    // === Request deduplication ===
    // Prevents multiple identical in-flight requests (e.g. 5 concurrent
    // GetCascadeTrajectory calls for the same cascadeId all wait on one request)
    private pendingRequests: Map<string, Promise<any>> = new Map();

    constructor(port: number, useHttps: boolean, csrfToken: string, apiKey: string) {
        this.port = port;
        this.useHttps = useHttps;
        this.csrfToken = csrfToken;
        this.apiKey = apiKey;

        // Create a persistent keep-alive agent
        if (useHttps) {
            this.agent = new https.Agent({
                keepAlive: true,
                maxSockets: 8,
                keepAliveMsecs: 30000,
                rejectUnauthorized: false,
            });
        } else {
            this.agent = new http.Agent({
                keepAlive: true,
                maxSockets: 8,
                keepAliveMsecs: 30000,
            });
        }
    }

    public getPort(): number { return this.port; }
    public getUseHttps(): boolean { return this.useHttps; }
    public getCsrfToken(): string { return this.csrfToken; }
    public getApiKey(): string { return this.apiKey; }

    /** Expose the keep-alive agent for use by streaming-api.ts */
    public getAgent(): http.Agent | https.Agent { return this.agent; }

    getMetadata() {
        return {
            ideName: ANTIGRAVITY_CLIENT_CONSTANTS.ideName,
            apiKey: this.apiKey,
            locale: 'en',
            ideVersion: ANTIGRAVITY_CLIENT_CONSTANTS.ideVersion,
            extensionName: ANTIGRAVITY_CLIENT_CONSTANTS.extensionName,
            cascadeId: this.cascadeId
        };
    }

    get isUsingHttps(): boolean {
        return this.useHttps;
    }

    set isUsingHttps(val: boolean) {
        this.useHttps = val;
        // Recreate agent when protocol changes
        if (val) {
            this.agent = new https.Agent({
                keepAlive: true,
                maxSockets: 8,
                keepAliveMsecs: 30000,
                rejectUnauthorized: false,
            });
        } else {
            this.agent = new http.Agent({
                keepAlive: true,
                maxSockets: 8,
                keepAliveMsecs: 30000,
            });
        }
    }

    private shouldLogError(key: string): boolean {
        const now = Date.now();
        const last = this.lastErrorLogAt.get(key) || 0;
        if (now - last < this.ERROR_LOG_DEDUPE_MS) return false;
        this.lastErrorLogAt.set(key, now);
        return true;
    }

    private getTimeoutMs(methodName: string): number {
        // Generous timeouts to prevent premature timeout under concurrent load.
        // The local language server can be slow when handling multiple requests.
        switch (methodName) {
            case ANTIGRAVITY_ENDPOINTS.getCascadeTrajectory:          return 30000;  // Was 10s — most spammed
            case ANTIGRAVITY_ENDPOINTS.sendUserCascadeMessage:         return 60000;  // Was 20s — can block on model work
            case ANTIGRAVITY_ENDPOINTS.saveMediaAsArtifact:            return 60000;
            case ANTIGRAVITY_ENDPOINTS.startCascade:                   return 15000;  // Was 8s
            case ANTIGRAVITY_ENDPOINTS.getCommandModelConfigs:         return 20000;  // Was 8s — also spamming timeouts
            case ANTIGRAVITY_ENDPOINTS.getUserTrajectoryDescriptions:  return 30000;  // Was 15s
            case ANTIGRAVITY_ENDPOINTS.getCascadeNuxes:                return 15000;  // New
            default:                               return 15000;  // Was 5s
        }
    }

    private shouldRetry(methodName: string, attempt: number, errorType: 'timeout' | 'network'): boolean {
        if (attempt >= 3) return false; // Max 3 retries (4 total attempts)
        
        // Always retry on network errors (socket hang up, ECONNREFUSED, etc.)
        if (errorType === 'network') return true;
        
        // Retry timeouts for safe read-only endpoints
        const retryableMethods: string[] = [
            ANTIGRAVITY_ENDPOINTS.getCascadeTrajectory,
            ANTIGRAVITY_ENDPOINTS.getUserTrajectoryDescriptions,
            ANTIGRAVITY_ENDPOINTS.getCommandModelConfigs,
            ANTIGRAVITY_ENDPOINTS.getCascadeNuxes
        ];
        return retryableMethods.includes(methodName);
    }

    private getRetryDelayMs(attempt: number): number {
        // Slower exponential backoff: 1s, 3s, 5s
        // Gives the server more time to recover between retries.
        const delays = [1000, 3000, 5000];
        return delays[Math.min(attempt, delays.length - 1)];
    }

    /**
     * Build a deduplication key for a request.
     * For read-only methods, we use methodName + cascadeId so that
     * multiple concurrent calls for the same data share one in-flight request.
     */
    private getDedupeKey(methodName: string, payload: any): string | null {
        const dedupeableMethods: string[] = [
            ANTIGRAVITY_ENDPOINTS.getCascadeTrajectory,
            ANTIGRAVITY_ENDPOINTS.getCommandModelConfigs,
            ANTIGRAVITY_ENDPOINTS.getUserTrajectoryDescriptions,
            ANTIGRAVITY_ENDPOINTS.getCascadeNuxes,
        ];
        if (!dedupeableMethods.includes(methodName)) return null;
        const cascadeId = payload?.cascadeId || '';
        return `${methodName}:${cascadeId}`;
    }

    async callEndpoint(methodName: string, payload: any): Promise<any> {
        // === Request Deduplication ===
        // If an identical request is already in-flight, return that promise
        // instead of firing a new HTTP request.
        const dedupeKey = this.getDedupeKey(methodName, payload);
        if (dedupeKey) {
            const existing = this.pendingRequests.get(dedupeKey);
            if (existing) {
                return existing;
            }
        }

        const executeRequest = async (): Promise<any> => {
            const doRequest = (attempt: number): Promise<any> => new Promise((resolve) => {
                const data = JSON.stringify(payload);
                const reqLib = this.useHttps ? https : http;
                let settled = false;

                const req = reqLib.request(
                    {
                        hostname: '127.0.0.1',
                        port: this.port,
                        path: `/exa.language_server_pb.LanguageServerService/${methodName}`,
                        method: 'POST',
                        headers: {
                            'Accept': 'application/json',
                            'Content-Type': 'application/json',
                            'connect-protocol-version': '1',
                            'x-codeium-csrf-token': this.csrfToken,
                            'Origin': 'vscode-file://vscode-app',
                        },
                        agent: this.agent,
                        timeout: this.getTimeoutMs(methodName)
                    },
                    (res) => {
                        let responseData = '';
                        res.on('data', (chunk) => {
                            responseData += chunk;
                        });
                        res.on('end', () => {
                            if (settled) return;
                            settled = true;
                            try {
                                if (res.statusCode && res.statusCode >= 200 && res.statusCode < 300) {
                                    const resBody = JSON.parse(responseData);
                                    if (resBody.cascadeId) {
                                        this.cascadeId = resBody.cascadeId;
                                    }
                                    this.connectionHealthy = true;
                                    this.lastSuccessfulRequestAt = Date.now();
                                    resolve(resBody);
                                } else {
                                    if (this.shouldLogError(`${methodName}:failed`)) {
                                        console.error(`[AntigravityHttpCore] ${methodName} failed (${res.statusCode}): ${responseData.substring(0, 200)}`);
                                    }
                                    (this as any).lastError = `${methodName} failed (${res.statusCode})`;
                                    resolve(null);
                                }
                            } catch (e: any) {
                                if (this.shouldLogError(`${methodName}:parse`)) {
                                    console.error(`[AntigravityHttpCore] ${methodName} Parse error: ${e.message}`);
                                }
                                (this as any).lastError = `${methodName} Parse error`;
                                resolve(null);
                            }
                        });
                    }
                );

                req.on('error', (e: Error) => {
                    if (settled) return; // Prevent double-fire after timeout
                    settled = true;
                    if (this.shouldLogError(`${methodName}:net`)) {
                        console.error(`[AntigravityHttpCore] ${methodName} network error: ${e.message}`);
                    }
                    (this as any).lastError = `${methodName} network error: ${e.message}`;
                    this.connectionHealthy = false;
                    
                    if (this.shouldRetry(methodName, attempt, 'network')) {
                        resolve({ __retry: true, __errorType: 'network' });
                    } else {
                        resolve(null);
                    }
                });

                req.on('timeout', () => {
                    if (settled) return;
                    settled = true;
                    if (this.shouldLogError(`${methodName}:timeout`)) {
                        console.error(`[AntigravityHttpCore] ${methodName} timeout (${this.getTimeoutMs(methodName)}ms)`);
                    }
                    (this as any).lastError = `${methodName} timeout`;

                    // CRITICAL FIX: Remove error listener BEFORE destroying the socket.
                    // Previously, req.destroy() would trigger the 'error' event with
                    // "socket hang up", causing a SECONDARY error log for every timeout.
                    // This was the #1 cause of log spam.
                    req.removeAllListeners('error');
                    try {
                        req.destroy();
                    } catch { }
                    
                    if (this.shouldRetry(methodName, attempt, 'timeout')) {
                        resolve({ __retry: true, __errorType: 'timeout' });
                    } else {
                        resolve(null);
                    }
                });

                req.write(data);
                req.end();
            });

            // Retry loop with exponential backoff
            let result = await doRequest(0);
            let attempt = 0;
            
            while (result && (result as any).__retry && attempt < 3) {
                const delay = this.getRetryDelayMs(attempt);
                const errorType = (result as any).__errorType || 'unknown';
                if (this.shouldLogError(`${methodName}:retry:${attempt}`)) {
                    console.log(`[AntigravityHttpCore] ${methodName} retrying (attempt ${attempt + 1}) after ${delay}ms due to ${errorType}`);
                }
                await new Promise(r => setTimeout(r, delay));
                attempt++;
                result = await doRequest(attempt);
            }
            
            return result;
        };

        // Wrap in deduplication
        if (dedupeKey) {
            const promise = executeRequest().finally(() => {
                this.pendingRequests.delete(dedupeKey!);
            });
            this.pendingRequests.set(dedupeKey, promise);
            return promise;
        }

        return executeRequest();
    }
}

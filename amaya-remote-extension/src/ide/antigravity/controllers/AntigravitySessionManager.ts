import { IIDEDiscovery } from '../../../interfaces/IIDEDiscovery';
import { AntigravityHttpCore } from '../core/AntigravityHttpCore';
import { AntigravityRpcClient } from '../core/AntigravityRpcClient';
import { AntigravityStreamingTransport } from '../core/AntigravityStreamingTransport';

export class AntigravitySessionManager {
    private httpCore: AntigravityHttpCore | null = null;
    private streamingTransport: AntigravityStreamingTransport | null = null;
    private initialized: boolean = false;
    private isInitializing: boolean = false;

    constructor(private readonly discovery: IIDEDiscovery) {}

    async initialize(): Promise<boolean> {
        if (this.initialized) return true;
        if (this.isInitializing) {
            console.warn('[AntigravityClient] Initialization already in progress, waiting...');
            while (this.isInitializing) {
                await new Promise(r => setTimeout(r, 100));
            }
            return this.initialized;
        }

        this.isInitializing = true;

        try {
            console.log('[AntigravityClient] Starting deep discovery...');
            const apiKey = await this.discovery.discoverApiKey();
            console.log(`[AntigravityClient] API key from auth: ${apiKey ? '[OK]' : '[NO]'}`);

            const credentials = await this.discovery.discoverCredentials(apiKey);
            const port = credentials?.port || 0;
            const csrfToken = credentials?.csrfToken || '';
            const useHttps = credentials?.useHttps || false;

            console.log(`[AntigravityClient] Discovery results - port:${port} csrf:${csrfToken ? '[OK]' : '[NO]'} apiKey:${apiKey ? '[OK]' : '[NO]'}`);

            this.initialized = port > 0 && csrfToken.length > 0 && apiKey.length > 0;

            if (this.initialized) {
                this.httpCore = new AntigravityHttpCore(port, useHttps, csrfToken, apiKey);
                this.streamingTransport = new AntigravityStreamingTransport(this.httpCore);
                const rpc = new AntigravityRpcClient(this.httpCore);

                console.log('[AntigravityClient] Verifying connection...');
                const test = await rpc.getCascadeNuxes({ metadata: rpc.getMetadata() });
                if (test === null) {
                    console.log('[AntigravityClient] Probe failed, trying mode flip...');
                    this.httpCore.isUsingHttps = !this.httpCore.isUsingHttps;
                    const test2 = await rpc.getCascadeNuxes({ metadata: rpc.getMetadata() });
                    if (test2 === null) {
                        console.log('[AntigravityClient] Verification failed after mode flip');
                        this.initialized = false;
                        this.httpCore = null;
                        this.streamingTransport = null;
                    }
                }
            }

            console.log(this.initialized ? '[AntigravityClient] READY' : '[AntigravityClient] FAILED');
            return this.initialized;
        } catch (e: any) {
            console.error('[AntigravityClient] Init error:', e.message);
            this.initialized = false;
            this.httpCore = null;
            this.streamingTransport = null;
            return false;
        } finally {
            this.isInitializing = false;
        }
    }

    setCredentials(port: number, csrfToken: string, apiKey: string): void {
        this.httpCore = new AntigravityHttpCore(port, false, csrfToken, apiKey);
        this.streamingTransport = new AntigravityStreamingTransport(this.httpCore);
        this.initialized = true;
        console.log(`[AntigravityClient] Credentials set manually - port:${port}`);
    }

    isReady(): boolean {
        return this.initialized;
    }

    getHttpCore(): AntigravityHttpCore | null {
        return this.httpCore;
    }

    getStreamingTransport(): AntigravityStreamingTransport | null {
        return this.streamingTransport;
    }
}
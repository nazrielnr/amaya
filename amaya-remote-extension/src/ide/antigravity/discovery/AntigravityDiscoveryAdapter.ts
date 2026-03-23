import { IIDECredentials, IIDEDiscovery } from '../../../interfaces/IIDEDiscovery';
import { AntigravityDiscovery } from './AntigravityDiscovery';

export class AntigravityDiscoveryAdapter implements IIDEDiscovery {
    getHostIDEEnv(): string {
        return AntigravityDiscovery.getHostIDEEnv();
    }

    async discoverApiKey(): Promise<string> {
        let apiKey = await AntigravityDiscovery.getApiKeyFromAuth();
        if (!apiKey) {
            apiKey = AntigravityDiscovery.discoverApiKeyFromStateDB();
        }
        return apiKey;
    }

    async discoverCredentials(apiKey: string): Promise<IIDECredentials | null> {
        let port = 0;
        let csrfToken = '';
        let useHttps = false;

        const processInfo = await AntigravityDiscovery.discoverFromProcessCmdLine(apiKey);
        if (processInfo) {
            port = processInfo.port;
            csrfToken = processInfo.csrfToken;
            useHttps = false;
        }

        if (!port) {
            const moduleInfo = AntigravityDiscovery.discoverFromModuleCache();
            if (moduleInfo) {
                port = moduleInfo.port;
                csrfToken = moduleInfo.csrfToken;
            }
        }

        if (!port) {
            const extensionInfo = AntigravityDiscovery.discoverFromExtensionApi();
            if (extensionInfo) {
                port = extensionInfo.port;
                csrfToken = extensionInfo.csrfToken;
            }
        }

        if (!port) {
            const logInfo = AntigravityDiscovery.discoverFromLogs();
            if (logInfo) {
                port = logInfo.port;
                csrfToken = logInfo.csrfToken;
            }
        }

        if (!csrfToken) {
            csrfToken = await AntigravityDiscovery.probeCsrfToken();
        }

        if (!(port > 0) || !csrfToken || !apiKey) {
            return null;
        }

        return {
            port,
            csrfToken,
            apiKey,
            useHttps,
        };
    }
}

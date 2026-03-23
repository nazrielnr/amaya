export interface IIDECredentials {
    port: number;
    csrfToken: string;
    apiKey?: string;
    useHttps?: boolean;
}

export interface IIDEDiscovery {
    getHostIDEEnv(): string;
    discoverApiKey(): Promise<string>;
    discoverCredentials(apiKey: string): Promise<IIDECredentials | null>;
}

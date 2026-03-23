export interface IIDERunStatusMapper {
    toClientStatus(isLoading: boolean, isStreaming: boolean): string;
    fromProviderStatus(status: string): { isLoading: boolean; isStreaming: boolean };
}

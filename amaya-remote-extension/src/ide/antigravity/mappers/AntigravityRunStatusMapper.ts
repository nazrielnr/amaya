import { IIDERunStatusMapper } from '../../../interfaces/IIDERunStatusMapper';
import { ANTIGRAVITY_STATUS_VALUES } from '../core/AntigravityProtocol';

export class AntigravityRunStatusMapper implements IIDERunStatusMapper {
    toClientStatus(isLoading: boolean, isStreaming: boolean): string {
        if (isStreaming) return ANTIGRAVITY_STATUS_VALUES.running;
        if (isLoading) return ANTIGRAVITY_STATUS_VALUES.loading;
        return ANTIGRAVITY_STATUS_VALUES.idle;
    }

    fromProviderStatus(status: string): { isLoading: boolean; isStreaming: boolean } {
        const isStreaming = status === ANTIGRAVITY_STATUS_VALUES.running;
        const isLoading = status === ANTIGRAVITY_STATUS_VALUES.loading || isStreaming;
        return { isLoading, isStreaming };
    }
}

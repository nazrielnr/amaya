import { IIDERunStatusMapper } from '../../../interfaces/IIDERunStatusMapper';

export class StubRunStatusMapper implements IIDERunStatusMapper {
    toClientStatus(isLoading: boolean, isStreaming: boolean): string {
        if (isStreaming) return 'STUB_STATUS_RUNNING';
        if (isLoading) return 'STUB_STATUS_LOADING';
        return 'STUB_STATUS_IDLE';
    }

    fromProviderStatus(status: string): { isLoading: boolean; isStreaming: boolean } {
        const normalized = (status || '').toUpperCase();
        return {
            isLoading: normalized.includes('LOADING') || normalized.includes('RUNNING'),
            isStreaming: normalized.includes('RUNNING'),
        };
    }
}

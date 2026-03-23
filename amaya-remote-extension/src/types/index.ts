import { IDEModel } from '../interfaces/IDETypes';

/**
 * Global extension state interface.
 */
export interface AppState {
    isLoading: boolean;
    isStreaming: boolean;
    activeSessionId: string | null;
    selectedModelId: string;
    models: IDEModel[];
}

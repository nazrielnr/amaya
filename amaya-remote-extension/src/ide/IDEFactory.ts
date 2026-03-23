import { IIDEClient } from '../interfaces/IIDEClient';
import { IIDECommandExecutor } from '../interfaces/IIDECommandExecutor';
import { IIDERunStatusMapper } from '../interfaces/IIDERunStatusMapper';

export type IDEProviderId = 'antigravity' | 'stub';

export interface IDEServices {
    api: IIDEClient;
    commandExecutor: IIDECommandExecutor;
    runStatusMapper: IIDERunStatusMapper;
}

type IDEBuilder = () => IDEServices;

function createAntigravityServices(): IDEServices {
    const { AntigravityClient } = require('./antigravity/AntigravityClient');
    const { AntigravityCommandExecutor } = require('./antigravity/controllers/AntigravityCommandExecutor');
    const { AntigravityRunStatusMapper } = require('./antigravity/mappers/AntigravityRunStatusMapper');

    return {
        api: new AntigravityClient(),
        commandExecutor: new AntigravityCommandExecutor(),
        runStatusMapper: new AntigravityRunStatusMapper(),
    };
}

function createStubServices(): IDEServices {
    const { StubIDEClient } = require('./stub/StubIDEClient');
    const { StubCommandExecutor } = require('./stub/controllers/StubCommandExecutor');
    const { StubRunStatusMapper } = require('./stub/mappers/StubRunStatusMapper');

    return {
        api: new StubIDEClient(),
        commandExecutor: new StubCommandExecutor(),
        runStatusMapper: new StubRunStatusMapper(),
    };
}

const providerRegistry: Record<IDEProviderId, IDEBuilder> = {
    antigravity: createAntigravityServices,
    stub: createStubServices,
};

export function registerIDEProvider(provider: IDEProviderId, builder: IDEBuilder): void {
    providerRegistry[provider] = builder;
}

export function createIDEServices(provider: IDEProviderId = 'antigravity'): IDEServices {
    const builder = providerRegistry[provider] || providerRegistry.antigravity;
    return builder();
}

export function parseIDEProvider(raw: string | undefined | null): IDEProviderId {
    const normalized = (raw || '').trim().toLowerCase();
    if (normalized === 'antigravity' || normalized === '') return 'antigravity';
    if (normalized === 'stub') return 'stub';
    return 'antigravity';
}

import { IIDECommandExecutor } from '../../../interfaces/IIDECommandExecutor';

export class StubCommandExecutor implements IIDECommandExecutor {
    async confirmAction(_confirmed: boolean): Promise<void> {
        return;
    }
}

import * as vscode from 'vscode';
import { IIDECommandExecutor } from '../../../interfaces/IIDECommandExecutor';

export class AntigravityCommandExecutor implements IIDECommandExecutor {
    async confirmAction(confirmed: boolean): Promise<void> {
        if (confirmed) {
            await vscode.commands.executeCommand('antigravity.agent.acceptAgentStep');
            return;
        }

        await vscode.commands.executeCommand('antigravity.agent.rejectAgentStep');
    }
}

import * as WebSocket from 'ws';
import { IIDEClient } from '../../interfaces/IIDEClient';
import { IHostWorkspaceService } from './HostWorkspaceService';

export class WorkspaceCommandController {
    constructor(
        private readonly api: IIDEClient,
        private readonly hostWorkspaceService: IHostWorkspaceService,
        private readonly sendToClient: (ws: WebSocket.WebSocket, msg: any) => void
    ) {}

    public async handleGetWorkspaces(ws: WebSocket.WebSocket): Promise<void> {
        const workspaces = await this.api.getWorkspaces();
        this.sendToClient(ws, { event: 'workspaces_list', data: { workspaces } });
    }

    public async handleGetProjectFiles(dirPath: string, ws: WebSocket.WebSocket): Promise<void> {
        const result = await this.hostWorkspaceService.listProjectFiles(dirPath);
        this.sendToClient(ws, { event: 'project_files', data: result });
    }

    public async handleGetFileDiff(ws: WebSocket.WebSocket): Promise<void> {
        const result = await this.hostWorkspaceService.getFileDiff();
        this.sendToClient(ws, { event: 'file_diff', data: result });
    }

    public async handleGetFileContent(filePath: string, ws: WebSocket.WebSocket): Promise<void> {
        const result = await this.hostWorkspaceService.getFileContent(filePath);
        this.sendToClient(ws, { event: 'file_content', data: result });
    }
}

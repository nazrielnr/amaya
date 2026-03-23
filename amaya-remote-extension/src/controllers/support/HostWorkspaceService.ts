import * as vscode from 'vscode';
import * as path from 'path';
import { exec } from 'child_process';
import { promises as fsPromises } from 'fs';

export interface WorkspaceSummary {
    name: string;
    path: string;
}

export interface ProjectFileEntry {
    name: string;
    path: string;
    type: 'file' | 'directory';
    size: number;
}

export interface IHostWorkspaceService {
    getCurrentWorkspace(): WorkspaceSummary | null;
    listProjectFiles(dirPath: string): Promise<{ files: ProjectFileEntry[]; path: string }>;
    getFileDiff(): Promise<{ diff: string; error?: string }>;
    getFileContent(filePath: string): Promise<{ path: string; content: string; error?: string }>;
}

export class VscodeHostWorkspaceService implements IHostWorkspaceService {
    public getCurrentWorkspace(): WorkspaceSummary | null {
        const wsFolder = vscode.workspace.workspaceFolders?.[0];
        if (wsFolder) {
            return { name: wsFolder.name, path: wsFolder.uri.fsPath };
        }

        const activeFile = vscode.window.activeTextEditor?.document.uri.fsPath;
        if (activeFile) {
            const dir = path.dirname(activeFile);
            return { name: path.basename(dir), path: dir };
        }

        return null;
    }

    public async listProjectFiles(dirPath: string): Promise<{ files: ProjectFileEntry[]; path: string }> {
        let targetPath = dirPath;
        if (!targetPath) {
            const currentWorkspace = this.getCurrentWorkspace();
            if (!currentWorkspace) {
                return { files: [], path: '' };
            }
            targetPath = currentWorkspace.path;
        }

        try {
            const entries = await fsPromises.readdir(targetPath, { withFileTypes: true });

            const filePromises = entries
                .filter((entry) => !entry.name.startsWith('.') && entry.name !== 'node_modules')
                .map(async (entry) => {
                    const fullPath = path.join(targetPath, entry.name);
                    let size = 0;
                    if (!entry.isDirectory()) {
                        try {
                            const stat = await fsPromises.stat(fullPath);
                            size = stat.size;
                        } catch {
                            size = 0;
                        }
                    }

                    return {
                        name: entry.name,
                        path: fullPath,
                        type: entry.isDirectory() ? 'directory' : 'file',
                        size,
                    } as ProjectFileEntry;
                });

            const files = await Promise.all(filePromises);
            files.sort((a, b) => {
                if (a.type !== b.type) {
                    return a.type === 'directory' ? -1 : 1;
                }
                return a.name.localeCompare(b.name);
            });

            return { files, path: targetPath };
        } catch {
            return { files: [], path: targetPath };
        }
    }

    public async getFileDiff(): Promise<{ diff: string; error?: string }> {
        const wsFolder = vscode.workspace.workspaceFolders?.[0];
        if (!wsFolder) {
            return { diff: '', error: 'No workspace' };
        }

        return new Promise((resolve) => {
            exec('git diff', { cwd: wsFolder.uri.fsPath, maxBuffer: 1024 * 1024 }, (error, stdout) => {
                if (error) {
                    resolve({ diff: '', error: error.message });
                    return;
                }

                resolve({ diff: stdout.substring(0, 50000) });
            });
        });
    }

    public async getFileContent(filePath: string): Promise<{ path: string; content: string; error?: string }> {
        try {
            const content = await fsPromises.readFile(filePath, 'utf-8');
            return { path: filePath, content: content.substring(0, 50000) };
        } catch (error: any) {
            return { path: filePath, content: '', error: error.message };
        }
    }
}

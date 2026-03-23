import { TrajectoryStep } from '../types/AntigravityTypes';
import { ANTIGRAVITY_STEP_TYPE_PREFIX, ANTIGRAVITY_STEP_TYPES } from '../core/AntigravityProtocol';

const lastTaskState: Record<string, any> = {};

export function stepTypeToToolName(stepType: string): string {
    const raw = stepType.replace(ANTIGRAVITY_STEP_TYPE_PREFIX, '').toLowerCase();
    const nameMap: Record<string, string> = {
        'view_file': 'read_file',
        'list_directory': 'list_files',
        'list_dir': 'list_files',
        'code_action': 'write_file',
        'run_command': 'run_shell',
        'grep_search': 'find_files',
        'grep': 'find_files',
        'find_by_name': 'find_files',
        'search_files': 'find_files',
        'task_boundary': 'task_boundary',
        'notify_user': 'notify_user',
        'search': 'find_files',
        'view_file_outline': 'read_file',
        'view_code_item': 'read_file',
        'replace_file_content': 'edit_file',
        'multi_replace_file_content': 'edit_file',
        'write_to_file': 'write_file',
        'generate_image': 'generate_image',
        'browser_subagent': 'browser',
    };
    return nameMap[raw] || raw;
}

export function remapToolArgs(name: string, args: Record<string, any>): Record<string, any> {
    const normalizedName = stepTypeToToolName(name || '');
    const remapped: Record<string, any> = {
        ...args,
        original_name: name,
    };

    const takeValue = (...keys: string[]) => {
        for (const key of keys) {
            if (args[key] !== undefined && args[key] !== null && args[key] !== '') {
                return args[key];
            }
        }
        return undefined;
    };

    const pathValue = takeValue(
        'AbsolutePath', 'absolutePath', 'TargetFile', 'targetFile',
        'DirectoryPath', 'directoryPath', 'SearchPath', 'searchPath',
        'SearchDirectory', 'searchDirectory', 'File', 'file', 'filePath',
        'path', 'uri'
    );

    if (pathValue && typeof pathValue === 'string') {
        const cleanPath = pathValue.replace(/^file:\/\//, '').replace(/\//g, (process.platform === 'win32' ? '\\' : '/'));
        remapped.path = cleanPath;
        if (!remapped.uri || typeof remapped.uri !== 'string') {
            const uriPath = cleanPath.replace(/\\/g, '/');
            remapped.uri = `file:///${uriPath.startsWith('/') ? uriPath.substring(1) : uriPath}`;
        }
    }

    remapped.content = takeValue('Description', 'description', 'Message', 'message', 'Instruction', 'instruction', 'content') || remapped.content;
    remapped.complexity = takeValue('Complexity', 'complexity') || remapped.complexity;

    if (normalizedName === 'task_boundary') {
        const rawTitle = takeValue('TaskName', 'taskName');
        const rawStatus = takeValue('TaskStatus', 'taskStatus');
        const rawSummary = takeValue('TaskSummary', 'taskSummary');

        if (rawTitle && rawTitle !== '%SAME%') lastTaskState.title = rawTitle;
        if (rawStatus && rawStatus !== '%SAME%') lastTaskState.status = rawStatus;
        if (rawSummary && rawSummary !== '%SAME%') lastTaskState.summary = rawSummary;

        remapped.title = lastTaskState.title || rawTitle;
        remapped.summary = lastTaskState.summary || rawSummary;
        remapped.status_text = lastTaskState.status || rawStatus;
    }

    if (normalizedName === 'run_shell') {
        remapped.command = takeValue('CommandLine', 'commandLine', 'submittedCommandLine', 'proposedCommandLine', 'cmd', 'command') || remapped.command;
    } else if (normalizedName === 'find_files') {
        remapped.pattern = takeValue('Pattern', 'pattern') || remapped.pattern;
        remapped.content = takeValue('Query', 'query', 'content') || remapped.content;
    }

    remapped.details = args;
    return remapped;
}

export function extractToolArgsFromStep(step: TrajectoryStep): Record<string, any> {
    const toolName = stepTypeToToolName(step.metadata?.toolCall?.name || step.type || '');
    const baseArgs = safeParseJson(step.metadata?.toolCall?.argumentsJson || '{}');
    const mergedArgs: Record<string, any> = { ...baseArgs };
    const camelKey = step.type
        .replace(ANTIGRAVITY_STEP_TYPE_PREFIX, '')
        .toLowerCase()
        .replace(/_([a-z])/g, (_, char) => char.toUpperCase());
    const toolData = (step as any)[camelKey] || {};

    const assignIfMissing = (key: string, value: any) => {
        if (mergedArgs[key] === undefined && value !== undefined && value !== null && value !== '') {
            mergedArgs[key] = value;
        }
    };

    if (step.type === ANTIGRAVITY_STEP_TYPES.runCommand && step.runCommand) {
        assignIfMissing('commandLine', step.runCommand.commandLine);
        assignIfMissing('commandLine', step.runCommand.submittedCommandLine);
        assignIfMissing('commandLine', step.runCommand.proposedCommandLine);
        assignIfMissing('submittedCommandLine', step.runCommand.submittedCommandLine);
        assignIfMissing('proposedCommandLine', step.runCommand.proposedCommandLine);
        assignIfMissing('cwd', step.runCommand.cwd);
    }

    if (step.type === ANTIGRAVITY_STEP_TYPES.viewFile && step.viewFile) {
        assignIfMissing('AbsolutePath', step.viewFile.absolutePath);
        assignIfMissing('AbsolutePath', step.viewFile.path);
        assignIfMissing('AbsolutePath', step.viewFile.filePath);
        assignIfMissing('AbsolutePath', step.viewFile.file);
    }

    if (step.type === ANTIGRAVITY_STEP_TYPES.viewCodeItem && step.viewCodeItem) {
        assignIfMissing('File', step.viewCodeItem.file);
        assignIfMissing('File', step.viewCodeItem.path);
        assignIfMissing('File', step.viewCodeItem.filePath);
    }

    if (step.type === ANTIGRAVITY_STEP_TYPES.viewFileOutline && step.viewFileOutline) {
        assignIfMissing('AbsolutePath', step.viewFileOutline.absolutePath);
        assignIfMissing('AbsolutePath', step.viewFileOutline.path);
        assignIfMissing('AbsolutePath', step.viewFileOutline.filePath);
        assignIfMissing('AbsolutePath', step.viewFileOutline.file);
    }

    if (step.type === ANTIGRAVITY_STEP_TYPES.listDirectory && step.listDirectory) {
        assignIfMissing('DirectoryPath', step.listDirectory.directoryPath);
        assignIfMissing('DirectoryPath', step.listDirectory.path);
    }

    if (step.type === ANTIGRAVITY_STEP_TYPES.grepSearch && step.grepSearch) {
        assignIfMissing('SearchPath', step.grepSearch.searchPath);
        assignIfMissing('Query', step.grepSearch.query);
    }

    if (step.type === ANTIGRAVITY_STEP_TYPES.findByName && step.findByName) {
        assignIfMissing('SearchDirectory', step.findByName.searchDirectory);
        assignIfMissing('Pattern', step.findByName.pattern);
    }

    if (step.type === ANTIGRAVITY_STEP_TYPES.codeAction && step.codeAction) {
        assignIfMissing('TargetFile', step.codeAction.targetFile);
        assignIfMissing('TargetFile', step.codeAction.filePath);
        assignIfMissing('TargetFile', step.codeAction.path);
    }

    if (step.type === ANTIGRAVITY_STEP_TYPES.replaceFileContent && step.replaceFileContent) {
        assignIfMissing('TargetFile', step.replaceFileContent.targetFile);
        assignIfMissing('TargetFile', step.replaceFileContent.filePath);
        assignIfMissing('TargetFile', step.replaceFileContent.path);
    }

    if (step.type === ANTIGRAVITY_STEP_TYPES.multiReplaceFileContent && step.multiReplaceFileContent) {
        assignIfMissing('TargetFile', step.multiReplaceFileContent.targetFile);
        assignIfMissing('TargetFile', step.multiReplaceFileContent.filePath);
        assignIfMissing('TargetFile', step.multiReplaceFileContent.path);
    }

    if (toolName === 'read_file') {
        assignIfMissing('AbsolutePath', toolData.absolutePath);
        assignIfMissing('AbsolutePath', toolData.path);
        assignIfMissing('AbsolutePath', toolData.filePath);
        assignIfMissing('AbsolutePath', toolData.file);
        assignIfMissing('File', toolData.file);
    }

    if (toolName === 'write_file' || toolName === 'edit_file') {
        assignIfMissing('TargetFile', toolData.targetFile);
        assignIfMissing('TargetFile', toolData.absolutePath);
        assignIfMissing('TargetFile', toolData.filePath);
        assignIfMissing('TargetFile', toolData.file);
        assignIfMissing('TargetFile', toolData.path);
    }

    if (toolName === 'list_files') {
        assignIfMissing('DirectoryPath', toolData.directoryPath);
        assignIfMissing('DirectoryPath', toolData.searchDirectory);
        assignIfMissing('DirectoryPath', toolData.path);
    }

    if (toolName === 'find_files') {
        assignIfMissing('SearchDirectory', toolData.searchDirectory);
        assignIfMissing('SearchPath', toolData.searchPath);
        assignIfMissing('SearchPath', toolData.directoryPath);
        assignIfMissing('SearchPath', toolData.path);
        assignIfMissing('Pattern', toolData.pattern);
        assignIfMissing('Query', toolData.query);
        assignIfMissing('Query', toolData.content);
    }

    if (toolName === 'browser') {
        assignIfMissing('Task', toolData.task);
        assignIfMissing('Task', toolData.query);
        assignIfMissing('Task', toolData.url);
    }

    if (toolName === 'task_boundary') {
        assignIfMissing('TaskName', toolData.taskName);
        assignIfMissing('TaskName', toolData.title);
        assignIfMissing('TaskSummary', toolData.taskSummary);
        assignIfMissing('TaskSummary', toolData.description);
    }

    if (toolName === 'notify_user') {
        assignIfMissing('Message', toolData.message);
        assignIfMissing('Message', toolData.content);
    }

    return remapToolArgs(toolName, mergedArgs);
}

export function isToolExecutionStep(stepType: string): boolean {
    const nonToolTypes: string[] = [
        ANTIGRAVITY_STEP_TYPES.userInput,
        ANTIGRAVITY_STEP_TYPES.plannerResponse,
        ANTIGRAVITY_STEP_TYPES.conversationHistory,
        ANTIGRAVITY_STEP_TYPES.knowledgeArtifacts,
        ANTIGRAVITY_STEP_TYPES.ephemeralMessage,
        ANTIGRAVITY_STEP_TYPES.checkpoint,
    ];
    return !nonToolTypes.includes(stepType);
}

function safeParseJson(str: string): Record<string, any> {
    try {
        const parsed = JSON.parse(str);
        return typeof parsed === 'object' && parsed !== null ? parsed : {};
    } catch {
        return {};
    }
}
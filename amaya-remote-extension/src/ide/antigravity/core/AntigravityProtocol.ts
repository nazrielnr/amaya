export const ANTIGRAVITY_ENDPOINTS = {
    getCascadeNuxes: 'GetCascadeNuxes',
    startCascade: 'StartCascade',
    sendUserCascadeMessage: 'SendUserCascadeMessage',
    cancelCascadeInvocation: 'CancelCascadeInvocation',
    getCascadeTrajectory: 'GetCascadeTrajectory',
    getCommandModelConfigs: 'GetCommandModelConfigs',
    getUserStatus: 'GetUserStatus',
    getAllCascadeTrajectories: 'GetAllCascadeTrajectories',
    getUserTrajectoryDescriptions: 'GetUserTrajectoryDescriptions',
    handleCascadeUserInteraction: 'HandleCascadeUserInteraction',
    saveMediaAsArtifact: 'SaveMediaAsArtifact',
} as const;

export const ANTIGRAVITY_STATUS_VALUES = {
    running: 'CASCADE_RUN_STATUS_RUNNING',
    loading: 'CASCADE_RUN_STATUS_UNSPECIFIED',
    idle: 'CASCADE_RUN_STATUS_IDLE',
    done: 'CASCADE_RUN_STATUS_DONE',
    canceled: 'CASCADE_RUN_STATUS_CANCELED',
    failed: 'CASCADE_RUN_STATUS_FAILED',
} as const;

export const ANTIGRAVITY_STEP_STATUS_VALUES = {
    pending: 'CORTEX_STEP_STATUS_PENDING',
    running: 'CORTEX_STEP_STATUS_RUNNING',
    generating: 'CORTEX_STEP_STATUS_GENERATING',
    done: 'CORTEX_STEP_STATUS_DONE',
    failed: 'CORTEX_STEP_STATUS_FAILED',
} as const;

export const ANTIGRAVITY_STEP_TYPES = {
    readFile: 'CORTEX_STEP_TYPE_READ_FILE',
    writeFile: 'CORTEX_STEP_TYPE_WRITE_FILE',
    editFile: 'CORTEX_STEP_TYPE_EDIT_FILE',
    runCommand: 'CORTEX_STEP_TYPE_RUN_COMMAND',
    search: 'CORTEX_STEP_TYPE_SEARCH',
    viewFile: 'CORTEX_STEP_TYPE_VIEW_FILE',
    viewCodeItem: 'CORTEX_STEP_TYPE_VIEW_CODE_ITEM',
    viewFileOutline: 'CORTEX_STEP_TYPE_VIEW_FILE_OUTLINE',
    listDirectory: 'CORTEX_STEP_TYPE_LIST_DIRECTORY',
    grepSearch: 'CORTEX_STEP_TYPE_GREP_SEARCH',
    findByName: 'CORTEX_STEP_TYPE_FIND_BY_NAME',
    codeAction: 'CORTEX_STEP_TYPE_CODE_ACTION',
    replaceFileContent: 'CORTEX_STEP_TYPE_REPLACE_FILE_CONTENT',
    multiReplaceFileContent: 'CORTEX_STEP_TYPE_MULTI_REPLACE_FILE_CONTENT',
    generateImage: 'CORTEX_STEP_TYPE_GENERATE_IMAGE',
    browserSubagent: 'CORTEX_STEP_TYPE_BROWSER_SUBAGENT',
    taskBoundary: 'CORTEX_STEP_TYPE_TASK_BOUNDARY',
    notifyUser: 'CORTEX_STEP_TYPE_NOTIFY_USER',
    userInput: 'CORTEX_STEP_TYPE_USER_INPUT',
    plannerResponse: 'CORTEX_STEP_TYPE_PLANNER_RESPONSE',
    conversationHistory: 'CORTEX_STEP_TYPE_CONVERSATION_HISTORY',
    knowledgeArtifacts: 'CORTEX_STEP_TYPE_KNOWLEDGE_ARTIFACTS',
    ephemeralMessage: 'CORTEX_STEP_TYPE_EPHEMERAL_MESSAGE',
    checkpoint: 'CORTEX_STEP_TYPE_CHECKPOINT',
} as const;

export const ANTIGRAVITY_STEP_TYPE_PREFIX = 'CORTEX_STEP_TYPE_';

export const ANTIGRAVITY_SOURCES = {
    cascadeClient: 'CORTEX_TRAJECTORY_SOURCE_CASCADE_CLIENT',
} as const;

export const ANTIGRAVITY_POLICIES = {
    autoExecutionOff: 'CASCADE_COMMANDS_AUTO_EXECUTION_OFF',
} as const;

export const ANTIGRAVITY_CONVERSATION_CONSTANTS = {
    plannerModeDefault: 'CONVERSATIONAL_PLANNER_MODE_DEFAULT',
    artifactReviewAlways: 'ARTIFACT_REVIEW_MODE_ALWAYS',
} as const;

export const ANTIGRAVITY_MODEL_IDS = {
    claudeSonnetThinking: 'CLAUDE_4_5_SONNET_THINKING',
    claudeOpusThinking: 'CLAUDE_4_OPUS_THINKING',
    openaiGptOss120bMedium: 'OPENAI_GPT_OSS_120B_MEDIUM',
} as const;

export const ANTIGRAVITY_CLIENT_CONSTANTS = {
    backendDisplayName: 'Google Antigravity',
    ideName: 'antigravity',
    ideVersion: '1.19.6',
    extensionName: 'antigravity',
} as const;

export const ANTIGRAVITY_TOOL_MARKERS = {
    thinkingToolName: 'thinking',
    thinkingToolMetaKey: 'syntheticThinking',
} as const;

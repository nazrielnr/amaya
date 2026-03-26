const assert = require('assert');

const { AntigravityStreamEventNormalizer } = require('../out/ide/antigravity/controllers/AntigravityStreamEventNormalizer');

function createCallbacksLog() {
  return {
    userMessages: [],
    statusChanges: [],
    textDeltas: [],
    toolCalls: [],
    toolResults: [],
    done: [],
  };
}

function createCallbacks(log) {
  return {
    onUserMessage: (text) => log.userMessages.push(text),
    onStatusChange: (status) => log.statusChanges.push(status),
    onTextDelta: (text) => log.textDeltas.push(text),
    onToolCall: (payload) => log.toolCalls.push(payload),
    onToolResult: (name, result) => log.toolResults.push({ name, result }),
    onDone: (text, reason) => log.done.push({ text, reason }),
  };
}

function run() {
  const localSteps = new Map();
  const latestHotMessagesMap = new Map();
  const latestHotMessagesTimestampMap = new Map();
  const log = createCallbacksLog();

  const result = AntigravityStreamEventNormalizer.normalizeFrame({
    data: {
      update: {
        agentStateUpdate: {
          runStatus: 'CASCADE_RUN_STATUS_RUNNING',
        },
      },
    },
    cascadeId: 'contract-cascade',
    callbacks: createCallbacks(log),
    localSteps,
    currentTurnIgnoreBeforeIndex: 0,
    hasStartedTurn: false,
    checkpointUpdatedDuringStream: false,
    emittedToolCalls: new Set(),
    emittedToolResults: new Set(),
    emittedToolStateSignatures: new Map(),
    emittedThinkingSignatures: new Map(),
    emittedTextSignatures: new Map(),
    terminalToolCalls: new Set(),
    emittedUserInputs: new Set(),
    latestHotMessagesMap,
    latestHotMessagesTimestampMap,
  });

  assert.strictEqual(typeof result, 'object', 'Result must be an object');
  assert.ok(Array.isArray(result.sortedIndices), 'sortedIndices should be an array');
  assert.ok(Array.isArray(result.sortedSteps), 'sortedSteps should be an array');
  assert.strictEqual(typeof result.hasStartedTurn, 'boolean', 'hasStartedTurn should be boolean');
  assert.strictEqual(typeof result.checkpointUpdatedDuringStream, 'boolean', 'checkpointUpdatedDuringStream should be boolean');
  assert.strictEqual(typeof result.newTurnFullText, 'string', 'newTurnFullText should be string');
  assert.ok(result.statusCandidate === null || typeof result.statusCandidate === 'string', 'statusCandidate should be string|null');

  console.log('normalizer-contracts: OK');
}

run();

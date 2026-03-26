const assert = require('assert');

const { createIDEServices, parseIDEProvider } = require('../out/ide/IDEFactory');

function assertClientContract(api) {
  const requiredMethods = [
    'initialize',
    'isReady',
    'getDiagnostics',
    'setCredentials',
    'startSession',
    'getLastActiveSessionId',
    'getModels',
    'getConversationIds',
    'getWorkspaces',
    'sendMessage',
    'getSessionTrajectory',
    'getSessionMessages',
    'getCurrentSessionMessages',
    'streamForResponse',
    'getConversationsMetadata',
    'stopStreaming',
    'respondToToolInteraction',
    'getBackendName',
  ];

  for (const method of requiredMethods) {
    assert.strictEqual(typeof api[method], 'function', `Missing IIDEClient method: ${method}`);
  }
}

function assertCommandExecutorContract(commandExecutor) {
  assert.strictEqual(typeof commandExecutor.confirmAction, 'function', 'Missing commandExecutor.confirmAction');
}

function assertRunStatusMapperContract(runStatusMapper) {
  assert.strictEqual(typeof runStatusMapper.toClientStatus, 'function', 'Missing runStatusMapper.toClientStatus');
  assert.strictEqual(typeof runStatusMapper.fromProviderStatus, 'function', 'Missing runStatusMapper.fromProviderStatus');
}

function run() {
  const normalizedStub = parseIDEProvider('stub');
  assert.strictEqual(normalizedStub, 'stub', 'Provider parser should resolve stub');

  const services = createIDEServices('stub');
  assert.ok(services.api, 'Missing api service');
  assert.ok(services.commandExecutor, 'Missing commandExecutor service');
  assert.ok(services.runStatusMapper, 'Missing runStatusMapper service');

  assertClientContract(services.api);
  assertCommandExecutorContract(services.commandExecutor);
  assertRunStatusMapperContract(services.runStatusMapper);

  console.log('provider-contracts: OK');
}

run();

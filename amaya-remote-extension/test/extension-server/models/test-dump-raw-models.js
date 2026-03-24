const _Module = require('module');
const originalRequire = _Module.prototype.require;
_Module.prototype.require = function (mdl) {
    if (mdl === 'vscode') {
        return {
            extensions: { getExtension: () => null },
            authentication: { getSession: async () => null },
            Uri: { file: (p) => ({ fsPath: p }) }
        };
    }
    return originalRequire.call(this, mdl);
};

const { AntigravityApi } = require('../out/antigravity-api');

async function test() {
    const api = new AntigravityApi();
    const ok = await api.initialize();
    if (!ok) {
        console.error("Failed to init. Is VS Code and Antigravity running?");
        return;
    }

    console.log("Fetching GetCommandModelConfigs directly...");
    const res = await api.callEndpoint('GetCommandModelConfigs', {
        metadata: api.getMetadata(),
    });
    console.log("=== RAW MODEL CONFIGS ===");
    console.log(JSON.stringify(res, null, 2));

    console.log("Fetching getCloudcodeModels directly...");
    const cloud = await api.getCloudcodeModels();
    console.log("=== CLOUDCODE MODELS ===");
    console.log(JSON.stringify(cloud, null, 2));
}

test();

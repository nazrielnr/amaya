const fs = require('fs');
const http = require('http');
const https = require('https');
const os = require('os');
const path = require('path');

const { discoverWorkingCredentials } = require('../auth-discovery/discovery-utils');

const TARGET_MODELS = ['MODEL_PLACEHOLDER_M35', 'MODEL_PLACEHOLDER_M26'];
const OUTPUT_PATH = path.join(__dirname, 'raw-model-histories-M35-M26.raw.json');
const META_PATH = path.join(__dirname, 'raw-model-histories-M35-M26.meta.json');

function log(message) {
    process.stdout.write(`${message}\n`);
}

function buildMetadata(apiKey) {
    return {
        metadata: {
            ideName: 'antigravity',
            apiKey,
            locale: 'en',
            ideVersion: '1.19.6',
            extensionName: 'antigravity',
        },
    };
}

function createRequester(useHttps) {
    const transport = useHttps ? https : http;
    return (port, methodName, body, csrfToken) => new Promise((resolve) => {
        const payload = JSON.stringify(body);
        const req = transport.request({
            hostname: '127.0.0.1',
            port,
            path: `/exa.language_server_pb.LanguageServerService/${methodName}`,
            method: 'POST',
            rejectUnauthorized: false,
            timeout: 15000,
            headers: {
                'Accept': 'application/json, text/event-stream',
                'Content-Type': 'application/json',
                'Content-Length': Buffer.byteLength(payload),
                'connect-protocol-version': '1',
                'x-codeium-csrf-token': csrfToken,
                'Origin': 'vscode-file://vscode-app',
            },
        }, (res) => {
            let responseText = '';
            res.on('data', (chunk) => { responseText += chunk; });
            res.on('end', () => {
                let json = null;
                try {
                    json = JSON.parse(responseText);
                } catch {
                    json = null;
                }
                resolve({ status: res.statusCode || 0, text: responseText, json });
            });
        });

        req.on('error', (error) => resolve({ error: error.message }));
        req.on('timeout', () => {
            req.destroy();
            resolve({ error: 'timeout' });
        });
        req.write(payload);
        req.end();
    });
}

async function callEndpoint(request, port, useHttps, csrfToken, methodName, body) {
    const result = await request(port, methodName, body, csrfToken);
    if (result.error) {
        return { ok: false, error: result.error, useHttps };
    }
    return {
        ok: result.status >= 200 && result.status < 300,
        status: result.status,
        json: result.json,
        text: result.text,
        useHttps,
    };
}

async function probeProtocol(port, csrfToken, apiKey) {
    for (const useHttps of [true, false]) {
        const request = createRequester(useHttps);
        const result = await callEndpoint(request, port, useHttps, csrfToken, 'GetCascadeNuxes', buildMetadata(apiKey));
        if (result.ok) {
            return { useHttps, request };
        }
    }
    return null;
}

function unique(values) {
    return [...new Set(values.filter(Boolean))];
}

function findTargetHits(trajectoryResponse) {
    const steps = trajectoryResponse?.trajectory?.steps || [];
    const hitsByModel = {};

    for (const target of TARGET_MODELS) {
        hitsByModel[target] = [];
    }

    steps.forEach((step, index) => {
        const serialized = JSON.stringify(step);
        for (const target of TARGET_MODELS) {
            if (serialized.includes(target)) {
                hitsByModel[target].push(index);
            }
        }
    });

    const matchedModelIds = TARGET_MODELS.filter((target) => hitsByModel[target].length > 0);
    return {
        matchedModelIds,
        hitsByModel,
        stepCount: steps.length,
    };
}

function findTargetHitsInSerialized(value) {
    const text = typeof value === 'string' ? value : JSON.stringify(value || {});
    const hitsByModel = {};

    for (const target of TARGET_MODELS) {
        hitsByModel[target] = text.includes(target) ? [0] : [];
    }

    return {
        matchedModelIds: TARGET_MODELS.filter((target) => hitsByModel[target].length > 0),
        hitsByModel,
        stepCount: 0,
    };
}

async function listConversationIds(request, port, useHttps, csrfToken, apiKey) {
    const metadataPayload = buildMetadata(apiKey);
    const discovered = [];

    const allTrajectories = await callEndpoint(request, port, useHttps, csrfToken, 'GetAllCascadeTrajectories', metadataPayload);
    if (allTrajectories.ok && allTrajectories.json?.trajectorySummaries) {
        discovered.push(...Object.keys(allTrajectories.json.trajectorySummaries));
    }

    const descriptions = await callEndpoint(request, port, useHttps, csrfToken, 'GetUserTrajectoryDescriptions', metadataPayload);
    if (descriptions.ok && Array.isArray(descriptions.json?.trajectories)) {
        discovered.push(...descriptions.json.trajectories
            .map((item) => item?.trajectoryId)
            .filter(Boolean));
    }

    const convDir = path.join(os.homedir(), '.gemini', 'antigravity', 'conversations');
    if (fs.existsSync(convDir)) {
        discovered.push(...fs.readdirSync(convDir)
            .filter((fileName) => fileName.endsWith('.pb'))
            .map((fileName) => fileName.replace(/\.pb$/i, '')));
    }

    return unique(discovered);
}

async function main() {
    log('[Discovery] Locating Antigravity credentials...');
    const creds = await discoverWorkingCredentials();
    if (!creds) {
        throw new Error('Unable to discover a working Antigravity language server port, CSRF token, and API key.');
    }

    const port = creds.port;
    const csrfToken = creds.csrfToken;
    const apiKey = creds.apiKey;

    if (!apiKey) {
        throw new Error('API key was not discovered. The workspace state DB may not contain a valid ya29 token.');
    }

    const protocolProbe = await probeProtocol(port, csrfToken, apiKey);
    if (!protocolProbe) {
        throw new Error(`Port ${port} did not respond to GetCascadeNuxes over HTTPS or HTTP.`);
    }

    const useHttps = protocolProbe.useHttps;
    const request = protocolProbe.request;

    log(`[Discovery] Port: ${port}`);
    log(`[Discovery] Protocol: ${useHttps ? 'https' : 'http'}`);
    log(`[Discovery] CSRF token: ${csrfToken.substring(0, 8)}...`);
    log(`[Discovery] API key: ${apiKey.substring(0, 15)}...`);

    const conversationIds = unique(await listConversationIds(request, port, useHttps, csrfToken, apiKey));
    log(`[Scan] Conversation IDs discovered: ${conversationIds.length}`);

    const matchedTrajectories = [];
    const rejected = [];

    for (const [index, cascadeId] of conversationIds.entries()) {
        const trajectoryResult = await callEndpoint(
            request,
            port,
            useHttps,
            csrfToken,
            'GetCascadeTrajectory',
            {
                metadata: buildMetadata(apiKey).metadata,
                cascadeId,
                source: 'CORTEX_TRAJECTORY_SOURCE_CASCADE_CLIENT',
            }
        );

        if (!trajectoryResult.ok || !trajectoryResult.json) {
            rejected.push({ cascadeId, status: trajectoryResult.status || 0, error: trajectoryResult.error || 'non-json response' });
            continue;
        }

        const generatorResult = await callEndpoint(
            request,
            port,
            useHttps,
            csrfToken,
            'GetCascadeTrajectoryGeneratorMetadata',
            {
                metadata: buildMetadata(apiKey).metadata,
                cascadeId,
            }
        );

        const trajectoryHitInfo = findTargetHits(trajectoryResult.json);
        const generatorHitInfo = generatorResult.ok && generatorResult.json
            ? findTargetHitsInSerialized(generatorResult.json)
            : { matchedModelIds: [], hitsByModel: {}, stepCount: 0 };

        const matchedModelIds = unique([
            ...trajectoryHitInfo.matchedModelIds,
            ...generatorHitInfo.matchedModelIds,
        ]);

        if (matchedModelIds.length > 0) {
            matchedTrajectories.push({
                cascadeId,
                matchedModelIds,
                hitsByModel: {
                    trajectory: trajectoryHitInfo.hitsByModel,
                    generatorMetadata: generatorHitInfo.hitsByModel,
                },
                stepCount: trajectoryHitInfo.stepCount,
                generatorMetadata: generatorResult.json || null,
                trajectory: trajectoryResult.json,
            });
            log(`[Match ${matchedTrajectories.length}] ${cascadeId} -> ${matchedModelIds.join(', ')}`);
        }

        if ((index + 1) % 10 === 0) {
            log(`[Scan] Processed ${index + 1}/${conversationIds.length}`);
        }
    }

    const rawOutput = matchedTrajectories.map((item) => item.trajectory);
    const metaOutput = {
        generatedAt: new Date().toISOString(),
        discovery: {
            port,
            protocol: useHttps ? 'https' : 'http',
            csrfTokenPresent: Boolean(csrfToken),
            apiKeyPresent: Boolean(apiKey),
        },
        targets: TARGET_MODELS,
        matchedCount: matchedTrajectories.length,
        matchedTrajectories,
        rejected,
    };

    fs.writeFileSync(OUTPUT_PATH, `${JSON.stringify(rawOutput, null, 2)}\n`, 'utf8');
    fs.writeFileSync(META_PATH, `${JSON.stringify(metaOutput, null, 2)}\n`, 'utf8');
    log(`[Saved] ${OUTPUT_PATH}`);
    log(`[Saved] ${META_PATH}`);
    log(`[Saved] Matches: ${matchedTrajectories.length}`);
}

main().catch((error) => {
    console.error('[Fatal]', error.message);
    process.exitCode = 1;
});
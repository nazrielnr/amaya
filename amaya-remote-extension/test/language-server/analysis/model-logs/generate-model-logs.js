const fs = require('fs');
const http = require('http');
const https = require('https');
const path = require('path');

const { discoverWorkingCredentials } = require('../discovery-utils');

const TARGETS = [
    { modelId: 'MODEL_PLACEHOLDER_M26', label: 'MODEL_PLACEHOLDER_M26', folder: 'MODEL_PLACEHOLDER_M26' },
    { modelId: 'MODEL_PLACEHOLDER_M35', label: 'MODEL_PLACEHOLDER_M35', folder: 'MODEL_PLACEHOLDER_M35' },
];

const BASE_DIR = path.join(__dirname, 'model-logs');
const PER_MODEL_TARGET = 5;
const PROMPTS_BY_MODEL = {
    MODEL_PLACEHOLDER_M26: [
        'cek random bug secara read-only lalu sebutkan 3 file paling relevan',
        'cek status dokumentasi secara read-only dan ringkas struktur dokumen utama',
        'cek dependensi project secara read-only dan sebutkan potensi risiko versi',
        'cek alur chat secara read-only lalu jelaskan file handler yang terlibat',
        'cek struktur workspace secara read-only dan daftar area kode yang paling aktif',
    ],
    MODEL_PLACEHOLDER_M35: [
        'cek random bug secara read-only lalu sebutkan kemungkinan penyebabnya',
        'cek status dokumentasi secara read-only dan identifikasi file yang perlu dibaca dulu',
        'cek struktur event stream secara read-only dan jelaskan komponen utamanya',
        'cek integrasi extension server secara read-only dan ringkas jalur request',
        'cek workspace secara read-only dan buat daftar area yang kemungkinan sering berubah',
    ],
};

function slugifyPrompt(prompt) {
    return String(prompt)
        .toLowerCase()
        .replace(/[^a-z0-9]+/g, '-')
        .replace(/^-+|-+$/g, '')
        .slice(0, 80) || 'prompt';
}

function log(message) {
    process.stdout.write(`${message}\n`);
}

function buildMetadata(apiKey) {
    return {
        ideName: 'antigravity',
        apiKey,
        locale: 'en',
        ideVersion: '1.19.6',
        extensionName: 'antigravity',
    };
}

function createRequestFn(port, csrfToken, useHttps) {
    const transport = useHttps ? https : http;
    return (methodName, body) => new Promise((resolve) => {
        const payload = JSON.stringify(body);
        const req = transport.request({
            hostname: '127.0.0.1',
            port,
            path: `/exa.language_server_pb.LanguageServerService/${methodName}`,
            method: 'POST',
            rejectUnauthorized: false,
            timeout: 45000,
            headers: {
                'Accept': 'application/json, text/event-stream',
                'Content-Type': 'application/json',
                'Content-Length': Buffer.byteLength(payload),
                'connect-protocol-version': '1',
                'x-codeium-csrf-token': csrfToken,
                'Origin': 'vscode-file://vscode-app',
            },
        }, (res) => {
            let text = '';
            res.on('data', (chunk) => { text += chunk; });
            res.on('end', () => {
                let json = null;
                try {
                    json = JSON.parse(text);
                } catch {
                    json = null;
                }
                resolve({ status: res.statusCode || 0, text, json });
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

async function probeProtocol(port, csrfToken, apiKey) {
    for (const useHttps of [true, false]) {
        const request = createRequestFn(port, csrfToken, useHttps);
        const response = await request('GetCascadeNuxes', { metadata: buildMetadata(apiKey) });
        if (response.status === 200) {
            return { useHttps, request };
        }
    }
    return null;
}

async function waitForDone(request, metadata, cascadeId) {
    let stableCount = 0;
    let lastStepCount = -1;
    let finalTrajectory = null;

    for (let attempt = 0; attempt < 120; attempt++) {
        const response = await request('GetCascadeTrajectory', {
            metadata,
            cascadeId,
            source: 'CORTEX_TRAJECTORY_SOURCE_CASCADE_CLIENT',
        });

        if (response.status !== 200 || !response.json) {
            await new Promise((resolve) => setTimeout(resolve, 1500));
            continue;
        }

        finalTrajectory = response.json;
        const steps = finalTrajectory?.trajectory?.steps || [];
        const stepCount = steps.length;
        const lastStep = steps[steps.length - 1];

        if (stepCount === lastStepCount) {
            stableCount += 1;
        } else {
            stableCount = 0;
            lastStepCount = stepCount;
        }

        const done = lastStep && lastStep.status === 'CORTEX_STEP_STATUS_DONE';
        if (done && stableCount >= 2) {
            return finalTrajectory;
        }

        await new Promise((resolve) => setTimeout(resolve, 2000));
    }

    return finalTrajectory;
}

function ensureDir(dirPath) {
    fs.mkdirSync(dirPath, { recursive: true });
}

async function createOneLog(request, metadata, modelId, label, index, prompt) {
    const startResponse = await request('StartCascade', {
        metadata,
        source: 'CORTEX_TRAJECTORY_SOURCE_CASCADE_CLIENT',
    });

    if (startResponse.status !== 200 || !startResponse.json?.cascadeId) {
        return {
            ok: false,
            modelId,
            label,
            index,
            stage: 'StartCascade',
            status: startResponse.status || 0,
            error: startResponse.error || startResponse.text || 'failed to start cascade',
        };
    }

    const cascadeId = startResponse.json.cascadeId;
    const sendResponse = await request('SendUserCascadeMessage', {
        metadata,
        cascadeId,
        items: [{ text: prompt }],
        cascadeConfig: {
            plannerConfig: {
                plannerTypeConfig: { case: 'conversational', value: {} },
                planModel: modelId,
                requestedModel: { model: modelId },
            },
        },
    });

    const outputDir = path.join(BASE_DIR, modelId);
    ensureDir(outputDir);

    const logBase = {
        modelId,
        label,
        index,
        prompt,
        cascadeId,
        startResponse,
        sendResponse,
    };

    if (sendResponse.status !== 200) {
        const errorPath = path.join(outputDir, `${cascadeId}.json`);
        fs.writeFileSync(errorPath, `${JSON.stringify(logBase, null, 2)}\n`, 'utf8');
        return {
            ok: false,
            modelId,
            label,
            index,
            cascadeId,
            stage: 'SendUserCascadeMessage',
            status: sendResponse.status || 0,
            path: errorPath,
        };
    }

    const trajectory = await waitForDone(request, metadata, cascadeId);
    const finalPayload = {
        ...logBase,
        trajectory,
    };

    const outputPath = path.join(outputDir, `${slugifyPrompt(prompt)}.json`);
    fs.writeFileSync(outputPath, `${JSON.stringify(finalPayload, null, 2)}\n`, 'utf8');

    return {
        ok: true,
        modelId,
        label,
        index,
        cascadeId,
        stage: 'completed',
        path: outputPath,
    };
}

async function main() {
    const creds = await discoverWorkingCredentials();
    if (!creds) {
        throw new Error('Unable to discover Antigravity credentials.');
    }

    const probe = await probeProtocol(creds.port, creds.csrfToken, creds.apiKey);
    if (!probe) {
        throw new Error(`Unable to reach language server on port ${creds.port}.`);
    }

    const metadata = buildMetadata(creds.apiKey);
    ensureDir(BASE_DIR);

    const report = {
        generatedAt: new Date().toISOString(),
        port: creds.port,
        protocol: probe.useHttps ? 'https' : 'http',
        targets: TARGETS.map((item) => item.modelId),
        results: {},
    };

    for (const target of TARGETS) {
        const targetDir = path.join(BASE_DIR, target.folder);
        ensureDir(targetDir);
        report.results[target.modelId] = [];

        const prompts = PROMPTS_BY_MODEL[target.modelId] || [];

        for (let attempt = 0; attempt < PER_MODEL_TARGET; attempt += 1) {
            const prompt = prompts[attempt] || `read-only check ${attempt + 1}`;
            log(`[${target.modelId}] attempt ${attempt + 1}/${PER_MODEL_TARGET}`);
            const result = await createOneLog(probe.request, metadata, target.modelId, target.label, attempt + 1, prompt);
            report.results[target.modelId].push(result);
            if (result.ok) {
                log(`  saved ${result.cascadeId}`);
            } else {
                log(`  failed (${result.stage}): ${result.cascadeId || '-'} ${result.status || ''}`.trim());
            }
        }
    }

    const reportPath = path.join(BASE_DIR, '_report.json');
    fs.writeFileSync(reportPath, `${JSON.stringify(report, null, 2)}\n`, 'utf8');
    log(`[Saved] ${reportPath}`);
}

main().catch((error) => {
    console.error('[Fatal]', error.message);
    process.exitCode = 1;
});
/**
 * ═══════════════════════════════════════════════════════════════
 * Antigravity gRPC-Web API — Complete Endpoint Tester
 * ═══════════════════════════════════════════════════════════════
 * 
 * Cara pakai:
 *   1. Buka Antigravity → DevTools (Ctrl+Shift+I) → Network tab
 *   2. Kirim chat apapun → klik request StartCascade → copy:
 *      - x-codeium-csrf-token (dari Request Headers)
 *      - apiKey (dari Request Body > metadata.apiKey)
 *   3. Paste ke CONFIG di bawah
 *   4. Jalankan: node test-all-endpoints.js
 * 
 * Output:
 *   - Console: summary setiap endpoint
 *   - File JSON: c:\tmp\antigravity-api\*.json (detail response)
 */

const https = require('https');
const fs = require('fs');
const path = require('path');
const os = require('os');

// ═══════════════════════════════════════════
// CONFIG — Load from environment or .env.local
// ═══════════════════════════════════════════
require('dotenv').config({ path: path.join(__dirname, '..', '..', '.env.local') });

const CONFIG = {
    host: '127.0.0.1',
    port: 53125,
    csrfToken: process.env.ANTIGRAVITY_CSRF_TOKEN || '',
    apiKey: process.env.ANTIGRAVITY_API_KEY || '',
    outputDir: 'c:/tmp/antigravity-api',
};

if (!CONFIG.csrfToken || !CONFIG.apiKey) {
    console.error('ERROR: Missing ANTIGRAVITY_CSRF_TOKEN or ANTIGRAVITY_API_KEY');
    console.error('Copy .env.local.example to .env.local and fill in your credentials');
    process.exit(1);
}

const metadata = {
    ideName: 'antigravity',
    apiKey: CONFIG.apiKey,
    locale: 'en',
    ideVersion: '1.19.6',
    extensionName: 'antigravity'
};

// ═══════════════════════════════════════════
// CORE: gRPC-Web Caller (Connect JSON protocol)
// ═══════════════════════════════════════════
function callEndpoint(methodName, body) {
    return new Promise((resolve, reject) => {
        const options = {
            hostname: CONFIG.host,
            port: CONFIG.port,
            path: `/exa.language_server_pb.LanguageServerService/${methodName}`,
            method: 'POST',
            rejectUnauthorized: false,
            headers: {
                'Accept': 'application/json, text/event-stream',
                'Content-Type': 'application/json',
                'connect-protocol-version': '1',
                'x-codeium-csrf-token': CONFIG.csrfToken,
                'Origin': 'vscode-file://vscode-app'
            }
        };
        const req = https.request(options, (res) => {
            let data = '';
            res.on('data', (chunk) => data += chunk);
            res.on('end', () => {
                let parsed = null;
                try { parsed = JSON.parse(data); } catch (e) { /* not JSON */ }
                resolve({ status: res.statusCode, body: data, json: parsed });
            });
        });
        req.on('error', (e) => reject(e));
        req.write(JSON.stringify(body));
        req.end();
    });
}

function save(filename, data) {
    fs.mkdirSync(CONFIG.outputDir, { recursive: true });
    const fp = path.join(CONFIG.outputDir, filename);
    fs.writeFileSync(fp, JSON.stringify(data, null, 2));
    return fp;
}

function log(icon, msg) { console.log(`  ${icon} ${msg}`); }
function header(title) { console.log(`\n${'═'.repeat(60)}\n  ${title}\n${'═'.repeat(60)}`); }

// ═══════════════════════════════════════════
// TESTS
// ═══════════════════════════════════════════
async function testAll() {
    console.log(`\n🚀 Antigravity gRPC-Web API Tester`);
    console.log(`   Host: https://${CONFIG.host}:${CONFIG.port}`);
    console.log(`   Time: ${new Date().toISOString()}\n`);

    const report = { timestamp: new Date().toISOString(), results: {} };

    // ── 1. GetCommandModelConfigs ──────────────
    header('1. GetCommandModelConfigs — Daftar Model AI');
    try {
        const r = await callEndpoint('GetCommandModelConfigs', { metadata });
        report.results.GetCommandModelConfigs = { status: r.status };
        if (r.status === 200 && r.json) {
            save('1-model-configs.json', r.json);
            const models = r.json.clientModelConfigs || [];
            log('✅', `${models.length} model(s) ditemukan`);
            models.forEach(m => {
                const mimes = Object.keys(m.supportedMimeTypes || {}).length;
                log('  ', `"${m.label}" (${m.modelOrAlias?.model}) — images: ${m.supportsImages}, MIME: ${mimes} types`);
                log('  ', `Quota: ${(m.quotaInfo?.remainingFraction * 100).toFixed(0)}% — Reset: ${m.quotaInfo?.resetTime}`);
            });
            report.results.GetCommandModelConfigs.models = models.map(m => m.label);
        } else { log('❌', `Status ${r.status}: ${r.body.substring(0, 100)}`); }
    } catch (e) { log('❌', e.message); }

    // ── 2. GetCascadeTrajectory — Baca History Chat ──
    header('2. GetCascadeTrajectory — Baca History Chat');
    // Get all conversation IDs from .pb files
    const convDir = path.join(os.homedir(), '.gemini', 'antigravity', 'conversations');
    let allConvIds = [];
    try {
        allConvIds = fs.readdirSync(convDir).filter(f => f.endsWith('.pb')).map(f => f.replace('.pb', ''));
        log('📁', `${allConvIds.length} file .pb ditemukan`);
    } catch (e) { log('❌', `Tidak bisa baca folder: ${e.message}`); }

    const convSummaries = [];
    let successCount = 0, failCount = 0;

    for (const cid of allConvIds) {
        try {
            const r = await callEndpoint('GetCascadeTrajectory', {
                metadata, cascadeId: cid, source: 'CORTEX_TRAJECTORY_SOURCE_CASCADE_CLIENT'
            });
            if (r.status === 200 && r.json) {
                successCount++;
                const steps = r.json.trajectory?.steps || [];
                const userSteps = steps.filter(s => s.type === 'CORTEX_STEP_TYPE_USER_INPUT');
                const aiSteps = steps.filter(s => s.type === 'CORTEX_STEP_TYPE_PLANNER_RESPONSE');
                const firstMsg = userSteps[0]?.userInput?.items?.[0]?.text || '(system)';
                const firstAi = (aiSteps[0]?.plannerResponse?.response || '').substring(0, 80);
                const created = steps[0]?.metadata?.createdAt || 'unknown';
                convSummaries.push({
                    cascadeId: cid,
                    stepCount: steps.length,
                    userMessages: userSteps.length,
                    aiMessages: aiSteps.length,
                    firstUserMessage: firstMsg.substring(0, 80),
                    firstAiResponse: firstAi,
                    createdAt: created
                });
            } else { failCount++; }
        } catch (e) { failCount++; }
    }

    log('✅', `${successCount}/${allConvIds.length} conversations terbaca (${failCount} gagal)`);
    convSummaries.sort((a, b) => b.stepCount - a.stepCount);
    log('📊', `Top 5 conversations (by step count):`);
    convSummaries.slice(0, 5).forEach(s => {
        log('  ', `[${s.cascadeId.substring(0, 8)}] ${s.stepCount} steps — "${s.firstUserMessage}"`);
    });
    save('2-all-conversations.json', convSummaries);
    report.results.GetCascadeTrajectory = { total: allConvIds.length, success: successCount, fail: failCount };

    // Save one full conversation as example
    if (convSummaries.length > 0) {
        const exampleId = convSummaries.find(s => s.stepCount > 3 && s.stepCount < 20)?.cascadeId || convSummaries[0].cascadeId;
        const r = await callEndpoint('GetCascadeTrajectory', {
            metadata, cascadeId: exampleId, source: 'CORTEX_TRAJECTORY_SOURCE_CASCADE_CLIENT'
        });
        if (r.status === 200) save('2-example-conversation.json', r.json);
    }

    // ── 3. GetUserTrajectoryDescriptions ──────────
    header('3. GetUserTrajectoryDescriptions — Workspace Trajectories');
    try {
        const r = await callEndpoint('GetUserTrajectoryDescriptions', { metadata });
        report.results.GetUserTrajectoryDescriptions = { status: r.status };
        if (r.status === 200 && r.json) {
            save('3-trajectory-descriptions.json', r.json);
            const trajs = r.json.trajectories || [];
            log('✅', `${trajs.length} trajectory(s)`);
            trajs.forEach(t => {
                log('  ', `ID: ${t.trajectoryId} — Current: ${t.current} — Branch: ${t.trajectoryScope?.branchName}`);
            });
        } else { log('❌', `Status ${r.status}`); }
    } catch (e) { log('❌', e.message); }

    // ── 4. GetCascadeModelConfigs ─────────────────
    header('4. GetCascadeModelConfigs — Config Model per Cascade');
    try {
        const r = await callEndpoint('GetCascadeModelConfigs', { metadata });
        report.results.GetCascadeModelConfigs = { status: r.status };
        if (r.status === 200 && r.json) {
            save('4-cascade-model-configs.json', r.json);
            const isEmpty = JSON.stringify(r.json) === '{}';
            log(isEmpty ? '⚪' : '✅', isEmpty ? 'Kosong (pakai default)' : 'Custom config ditemukan');
        } else { log('❌', `Status ${r.status}`); }
    } catch (e) { log('❌', e.message); }

    // ── 5. GetUnleashData ─────────────────────────
    header('5. GetUnleashData — Feature Flags & Experiments');
    try {
        const r = await callEndpoint('GetUnleashData', { metadata });
        report.results.GetUnleashData = { status: r.status };
        if (r.status === 200 && r.json) {
            save('5-unleash-data.json', r.json);
            const tier = r.json.context?.properties?.userTierId || 'unknown';
            const exps = r.json.experimentConfig?.experiments || [];
            log('✅', `User tier: "${tier}" | ${exps.length} experiment(s)`);
            exps.forEach(e => log('  ', `🧪 ${e.keyString}`));
        } else { log('❌', `Status ${r.status}`); }
    } catch (e) { log('❌', e.message); }

    // ── 6. GetCascadeTrajectorySteps ──────────────
    header('6. GetCascadeTrajectorySteps — Steps (Lightweight)');
    const testCid = convSummaries[0]?.cascadeId || allConvIds[0];
    try {
        const r = await callEndpoint('GetCascadeTrajectorySteps', {
            metadata, cascadeId: testCid
        });
        report.results.GetCascadeTrajectorySteps = { status: r.status };
        if (r.status === 200 && r.json) {
            save('6-trajectory-steps.json', r.json);
            const steps = r.json.steps || [];
            log('✅', `${steps.length} steps untuk [${testCid.substring(0, 8)}]`);
            const types = {};
            steps.forEach(s => { types[s.type] = (types[s.type] || 0) + 1; });
            Object.entries(types).forEach(([t, c]) => log('  ', `${t.replace('CORTEX_STEP_TYPE_', '')}: ${c}`));
        } else { log('❌', `Status ${r.status}`); }
    } catch (e) { log('❌', e.message); }

    // ── 7. GetCascadeTrajectoryGeneratorMetadata ──
    header('7. GetCascadeTrajectoryGeneratorMetadata — System Prompt');
    try {
        const r = await callEndpoint('GetCascadeTrajectoryGeneratorMetadata', {
            metadata, cascadeId: testCid
        });
        report.results.GetCascadeTrajectoryGeneratorMetadata = { status: r.status };
        if (r.status === 200 && r.json) {
            save('7-generator-metadata.json', r.json);
            const meta = r.json.generatorMetadata || [];
            log('✅', `${meta.length} generator metadata entries`);
            meta.forEach(m => {
                const prompt = m.chatModel?.systemPrompt || '';
                log('  ', `Steps: [${m.stepIndices}] — System prompt: ${prompt.substring(0, 80)}...`);
            });
        } else { log('❌', `Status ${r.status}`); }
    } catch (e) { log('❌', e.message); }

    // ── 8. GetCascadeNuxes ────────────────────────
    header('8. GetCascadeNuxes — Tips & Announcements');
    try {
        const r = await callEndpoint('GetCascadeNuxes', { metadata });
        report.results.GetCascadeNuxes = { status: r.status };
        if (r.status === 200 && r.json) {
            save('8-nuxes.json', r.json);
            const nuxes = r.json.nuxes || [];
            log('✅', `${nuxes.length} nux(es)`);
            nuxes.forEach(n => log('  ', `[${n.location}] "${n.title}" — ${n.body}`));
        } else { log('❌', `Status ${r.status}`); }
    } catch (e) { log('❌', e.message); }

    // ── 9. GetAgentScripts ────────────────────────
    header('9. GetAgentScripts — Custom Agent Scripts');
    try {
        const r = await callEndpoint('GetAgentScripts', { metadata });
        report.results.GetAgentScripts = { status: r.status };
        if (r.status === 200 && r.json) {
            save('9-agent-scripts.json', r.json);
            const isEmpty = JSON.stringify(r.json) === '{}';
            log(isEmpty ? '⚪' : '✅', isEmpty ? 'Tidak ada custom scripts' : JSON.stringify(r.json).substring(0, 100));
        } else { log('❌', `Status ${r.status}`); }
    } catch (e) { log('❌', e.message); }

    // ── 10. SendUserCascadeMessage (DRY RUN) ──────
    header('10. SendUserCascadeMessage — Kirim Pesan (INFO ONLY)');
    log('ℹ️', 'Endpoint ini TIDAK dites otomatis (akan mengirim pesan asli ke AI)');
    log('  ', 'Method: POST /exa.language_server_pb.LanguageServerService/SendUserCascadeMessage');
    log('  ', 'Body: { metadata, cascadeId: "...", userMessage: { text: "..." } }');
    report.results.SendUserCascadeMessage = { status: 'skipped', reason: 'would send real message' };

    // ── 11. StreamCascadeReactiveUpdates (INFO) ───
    header('11. StreamCascadeReactiveUpdates — Real-time Streaming (INFO ONLY)');
    log('ℹ️', 'Endpoint ini menggunakan SSE (Server-Sent Events)');
    log('  ', 'Method: POST /exa.language_server_pb.LanguageServerService/StreamCascadeReactiveUpdates');
    log('  ', 'Response: text/event-stream (long-lived connection)');
    report.results.StreamCascadeReactiveUpdates = { status: 'skipped', reason: 'requires SSE client' };

    // ═══ FINAL REPORT ═══
    header('📋 FINAL REPORT');
    const tested = Object.entries(report.results);
    const succeeded = tested.filter(([_, v]) => v.status === 200 || v.status === 'skipped');
    const failed = tested.filter(([_, v]) => v.status !== 200 && v.status !== 'skipped');
    console.log(`\n  Total endpoints: ${tested.length}`);
    console.log(`  ✅ Berhasil: ${succeeded.length}`);
    console.log(`  ❌ Gagal: ${failed.length}`);
    if (failed.length > 0) {
        console.log(`  Failed:`, failed.map(([k]) => k).join(', '));
    }
    console.log(`\n  📂 Output directory: ${CONFIG.outputDir}`);
    save('_report.json', report);
    console.log(`  📄 Report: ${CONFIG.outputDir}/_report.json\n`);
}

testAll().catch(e => console.error('Fatal:', e));

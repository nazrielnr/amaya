const https = require('https');

const CONFIG = {
    host: '127.0.0.1',
    port: 53125,
    csrfToken: process.env.ANTIGRAVITY_CSRF_TOKEN || '',
    apiKey: process.env.ANTIGRAVITY_API_KEY || '',
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

const MODEL_GEMINI_3_FLASH = 1018;

function callEndpoint(methodName, body) {
    return new Promise((resolve, reject) => {
        const bodyStr = JSON.stringify(body);
        const options = {
            hostname: CONFIG.host,
            port: CONFIG.port,
            path: `/exa.language_server_pb.LanguageServerService/${methodName}`,
            method: 'POST',
            rejectUnauthorized: false,
            headers: {
                'Accept': 'application/json',
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
                try { resolve({ status: res.statusCode, json: JSON.parse(data), raw: data }); }
                catch (e) { resolve({ status: res.statusCode, json: null, raw: data }); }
            });
        });
        req.on('error', (e) => reject(e));
        req.write(bodyStr);
        req.end();
    });
}

async function runFullChatTest(prompt) {
    console.log('═══════════════════════════════════════════════');
    console.log('  Antigravity gRPC-Web — Full Chat Flow Test');
    console.log('═══════════════════════════════════════════════\n');

    console.log('🚀 STEP 1: Creating new conversation...');
    const startRes = await callEndpoint('StartCascade', {
        metadata,
        source: 'CORTEX_TRAJECTORY_SOURCE_CASCADE_CLIENT'
    });

    if (!startRes.json?.cascadeId) {
        console.error('❌ Failed to start cascade:', startRes.raw);
        return;
    }
    const cascadeId = startRes.json.cascadeId;
    console.log(`✅ Cascade ID: ${cascadeId}\n`);

    console.log(`🚀 STEP 2: Sending prompt: "${prompt}"...`);
    const sendRes = await callEndpoint('SendUserCascadeMessage', {
        metadata,
        cascadeId,
        items: [{ text: prompt }],
        cascadeConfig: {
            plannerConfig: {
                plannerTypeConfig: {
                    case: 'conversational',
                    value: {}
                },
                planModel: MODEL_GEMINI_3_FLASH,
                requestedModel: { model: MODEL_GEMINI_3_FLASH }
            }
        }
    });

    if (sendRes.status !== 200) {
        console.error('❌ Failed to send message:', sendRes.raw);
        return;
    }
    console.log('✅ Message sent! Waiting for AI response...\n');

    console.log('🚀 STEP 3: Polling for AI response...');
    let found = false;
    let attempts = 0;
    const startTime = Date.now();

    while (!found && attempts < 60) {
        attempts++;
        await new Promise(r => setTimeout(r, 1500));

        const tRes = await callEndpoint('GetCascadeTrajectory', {
            metadata,
            cascadeId,
            source: 'CORTEX_TRAJECTORY_SOURCE_CASCADE_CLIENT'
        });

        const steps = tRes.json?.trajectory?.steps || [];
        const plannerStep = steps.find(s => s.type === 'CORTEX_STEP_TYPE_PLANNER_RESPONSE');

        if (plannerStep?.status === 'CORTEX_STEP_STATUS_DONE') {
            const elapsed = ((Date.now() - startTime) / 1000).toFixed(1);
            console.log(`\n✅ AI response received in ${elapsed}s!\n`);
            console.log('══════════════════════════════════════════════');
            console.log('  🤖 AI RESPONSE:');
            console.log('══════════════════════════════════════════════');
            console.log(plannerStep.plannerResponse?.response || '(empty)');
            console.log('══════════════════════════════════════════════');

            if (plannerStep.plannerResponse?.thinking) {
                console.log('\n  🧠 THINKING PROCESS:');
                console.log('──────────────────────────────────────────────');
                console.log(plannerStep.plannerResponse.thinking);
            }

            console.log('\n  📊 METADATA:');
            console.log('  Stop reason:', plannerStep.plannerResponse?.stopReason);
            console.log('  Think time:', plannerStep.plannerResponse?.thinkingDuration);
            console.log('  Message ID:', plannerStep.plannerResponse?.messageId);
            console.log('  Cascade ID:', cascadeId);
            found = true;
        } else if (plannerStep) {
            process.stdout.write('💭');
        } else {
            process.stdout.write('⏳');
        }
    }

    if (!found) {
        console.log('\n❌ Timed out — no response after 90 seconds.');
    }
}

runFullChatTest('ini projek tentang apa').catch(console.error);
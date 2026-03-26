const WebSocket = require('ws');

console.log('Connecting to ws://localhost:8765...');
const ws = new WebSocket('ws://localhost:8765');

let modelsToTest = [];
let currentModelIndex = 0;
let testingModel = false;

ws.on('open', () => {
    console.log('✅ Connected to Extension WebSocket');
    console.log('\n[1] Fetching all models...');
    ws.send(JSON.stringify({ action: 'get_models' }));
});

ws.on('message', (rawData) => {
    try {
        const msg = JSON.parse(rawData);

        if (msg.event === 'error') {
            console.error('❌ Error event:', msg.data);
            return;
        }

        switch (msg.event) {
            case 'models_list':
                if (modelsToTest.length === 0 && !testingModel) {
                    modelsToTest = msg.data.models || [];
                    console.log(`✅ Loaded ${modelsToTest.length} models:`);
                    modelsToTest.forEach((m, idx) => {
                        console.log(`  ${idx + 1}. ${m.label} (ID: ${m.id})`);
                    });

                    if (modelsToTest.length > 0) {
                        startNextModelTest();
                    } else {
                        console.log('No models found to test.');
                        ws.close();
                    }
                }
                break;

            case 'new_conversation':
                if (testingModel) {
                    const model = modelsToTest[currentModelIndex];
                    console.log(`   ✅ New conversation created. Sending message using model: ${model.label}...`);
                    ws.send(JSON.stringify({
                        action: 'send_message',
                        data: {
                            content: `Hello! Please reply with exactly one sentence confirming you are ${model.label}.`,
                            modelId: model.id
                        }
                    }));
                }
                break;

            case 'new_assistant_message':
                if (testingModel) {
                    console.log(`   ✅ Assistant started replying...`);
                }
                break;

            case 'ai_thinking':
                if (testingModel) {
                    process.stdout.write('🤔');
                }
                break;

            case 'stream_done':
                if (testingModel) {
                    console.log(`\n   ✅ Stream Done! Response length: ${new String(msg.data?.finalText || '').length} chars`);
                    console.log(`   📝 Final Text: "${(msg.data?.finalText || '').substring(0, 100)}..."\n`);

                    currentModelIndex++;
                    startNextModelTest();
                }
                break;

            case 'text_delta':
            case 'state_sync':
            case 'conversations_list':
            case 'state_update':
                // Ignore these to keep logs clean
                break;

            default:
                break;
        }

    } catch (e) {
        console.error('Failed to parse message:', e);
    }
});

function startNextModelTest() {
    if (currentModelIndex < modelsToTest.length) {
        const model = modelsToTest[currentModelIndex];
        testingModel = true;
        console.log(`\n==================================================`);
        console.log(`🧪 Testing Model ${currentModelIndex + 1}/${modelsToTest.length}: ${model.label} (ID: ${model.id})`);
        console.log(`==================================================`);

        // Ensure the model is selected
        ws.send(JSON.stringify({ action: 'select_model', data: { modelId: model.id } }));

        // Start a new chat for this model
        setTimeout(() => {
            ws.send(JSON.stringify({ action: 'new_chat' }));
        }, 500);

    } else {
        console.log('\n🎉 ALL MODELS TESTED! Closing connection...');
        ws.close();
    }
}

ws.on('error', (err) => {
    console.error('WebSocket Error:', err);
});

ws.on('close', () => {
    console.log('🔌 Connection closed.');
});

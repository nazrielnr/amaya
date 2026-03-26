const WebSocket = require('ws');

console.log('Connecting to ws://localhost:8765...');
const ws = new WebSocket('ws://localhost:8765');

let step = 0;

ws.on('open', () => {
    console.log('✅ Connected to Extension WebSocket');

    console.log('\n[1] Testing get_models...');
    ws.send(JSON.stringify({ action: 'get_models' }));
});

ws.on('message', (rawData) => {
    try {
        const msg = JSON.parse(rawData);
        console.log(`\n📥 Received Event: ${msg.event}`);

        if (msg.event === 'error') {
            console.error('❌ Error event:', msg.data);
            return;
        }

        switch (msg.event) {
            case 'models_list':
                console.log(`✅ Models loaded: ${msg.data.models?.length || 0} models`);
                console.log(`Selected model ID: ${msg.data.selectedModelId}`);
                if (msg.data.models?.length > 0) {
                    console.log(`Sample model: ${msg.data.models[0].label} (ID: ${msg.data.models[0].id})`);
                }

                if (step === 0) {
                    step++;
                    console.log('\n[2] Testing get_conversations...');
                    ws.send(JSON.stringify({ action: 'get_conversations' }));
                }
                break;

            case 'conversations_list':
                const convs = msg.data.conversations || [];
                console.log(`✅ Conversations loaded: ${convs.length}`);

                if (convs.length > 0) {
                    const firstConv = convs[0];
                    console.log(`Latest conversation: ${firstConv.id} - Preview: ${firstConv.preview ? 'YES' : 'NO'}`);

                    // The extension sends conversations_list twice (once without preview, once with preview)
                    // We'll proceed to the next step when we get the preview
                    if (firstConv.preview && step === 1) {
                        step++;
                        console.log('\n[3] Testing get_state...');
                        ws.send(JSON.stringify({ action: 'get_state' }));
                    } else if (convs.length === 0 && step === 1) {
                        step++;
                        console.log('\n[3] Testing get_state...');
                        ws.send(JSON.stringify({ action: 'get_state' }));
                    }
                }
                break;

            case 'state_sync':
                console.log(`✅ State synced. Loading: ${msg.data.isLoading}, Streaming: ${msg.data.isStreaming}`);
                console.log(`Messages count: ${msg.data.messages?.length || 0}`);

                if (step === 2) {
                    step++;
                    console.log('\n[4] Testing new_chat...');
                    ws.send(JSON.stringify({ action: 'new_chat' }));
                }
                break;

            case 'new_conversation':
                console.log(`✅ New conversation created! ID: ${msg.data.conversationId}`);
                if (step === 3) {
                    step++;
                    console.log('\n[5] Testing send_message...');
                    ws.send(JSON.stringify({ action: 'send_message', data: { content: 'test message, reply short' } }));
                }
                break;

            case 'new_assistant_message':
                console.log(`✅ Assistant started replying...`);
                break;

            case 'ai_thinking':
                console.log(`🤔 AI Thinking... length: ${new String(msg.data?.text || '').length} chars`);
                break;

            case 'stream_done':
                console.log(`✅ Stream Done! Final text length: ${new String(msg.data?.finalText || '').length}`);
                if (step === 4) {
                    step++;
                    console.log('\n🎉 ALL TESTS PASSED! Closing connection in 2 seconds...');
                    setTimeout(() => ws.close(), 2000);
                }
                break;

            default:
                // Other events (e.g. tool_call_start, tool_call_result, text_delta)
                console.log(`ℹ️ Unhandled in test script: ${msg.event} (Data keys: ${Object.keys(msg.data || {}).join(', ')})`);
                break;
        }

    } catch (e) {
        console.error('Failed to parse message:', e);
    }
});

ws.on('error', (err) => {
    console.error('WebSocket Error Object:', err);
    if (err && err.message && err.message.includes('ECONNREFUSED')) {
        console.error('Make sure the Amaya Remote Extension is running in VS Code (Reload Window if necessary).');
    }
});

ws.on('close', () => {
    console.log('🔌 Connection closed.');
});

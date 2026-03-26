const WebSocket = require('ws');

console.log('=== Full Flow Trace ===\n');

const ws = new WebSocket('ws://127.0.0.1:8765');
let step = 0;

ws.on('open', () => {
    console.log('[CONNECTED]');
    
    // Step 1: Get models
    console.log('\n[STEP 1] Sending get_models...');
    ws.send(JSON.stringify({ type: 'get_models' }));
});

ws.on('message', (data) => {
    const msg = JSON.parse(data.toString());
    console.log(`\n[RECEIVED] event: ${msg.event}`);
    
    if (msg.event === 'models_list') {
        console.log('  models count:', msg.data?.models?.length);
        const m26 = msg.data?.models?.find(m => m.id === 'MODEL_PLACEHOLDER_M26');
        console.log('  M26 quota:', m26?.quota);
        
        // Step 2: Select model
        console.log('\n[STEP 2] Sending select_model MODEL_PLACEHOLDER_M26...');
        ws.send(JSON.stringify({ type: 'select_model', modelId: 'MODEL_PLACEHOLDER_M26' }));
    }
    
    if (msg.event === 'model_selected') {
        console.log('  modelId:', msg.data?.modelId);
        
        // Step 3: Send message
        console.log('\n[STEP 3] Sending send_message...');
        ws.send(JSON.stringify({
            type: 'send_message',
            content: 'test',
            conversationId: null,
            conversationMode: 'chat'
        }));
    }
    
    if (msg.event === 'error') {
        console.log('\n!!! ERROR RECEIVED !!!');
        console.log('  message:', msg.data?.message);
    }
    
    if (msg.event === 'active_conversation') {
        console.log('  conversationId:', msg.data?.conversationId);
    }
    
    if (msg.event === 'stream_done') {
        console.log('  stopReason:', msg.data?.stopReason);
        console.log('\n[DONE]');
        ws.close();
    }
});

ws.on('error', (e) => console.error('WS ERROR:', e.message));

setTimeout(() => {
    console.log('\n[TIMEOUT]');
    ws.close();
}, 15000);

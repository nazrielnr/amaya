const WebSocket = require('ws');

console.log('=== Trace with Delay ===\n');

const ws = new WebSocket('ws://127.0.0.1:8765');

ws.on('open', () => {
    console.log('[CONNECTED]');
    console.log('[STEP 1] get_models...');
    ws.send(JSON.stringify({ type: 'get_models' }));
});

ws.on('message', (data) => {
    const msg = JSON.parse(data.toString());
    console.log(`[RECV] ${msg.event}`);
    
    if (msg.event === 'models_list') {
        console.log('  models:', msg.data?.models?.length);
        console.log('  selectedModelId:', msg.data?.selectedModelId);
        
        // Wait for state to populate
        console.log('\n[STEP 2] Waiting 500ms...');
        setTimeout(() => {
            console.log('[STEP 3] select_model MODEL_PLACEHOLDER_M26...');
            ws.send(JSON.stringify({ type: 'select_model', modelId: 'MODEL_PLACEHOLDER_M26' }));
        }, 500);
    }
    
    if (msg.event === 'model_selected') {
        console.log('  !!! MODEL_SELECTED !!!');
        console.log('  modelId:', msg.data?.modelId);
        
        // Now send message
        console.log('\n[STEP 4] send_message...');
        ws.send(JSON.stringify({
            type: 'send_message',
            content: 'test',
            conversationId: null,
            conversationMode: 'chat'
        }));
    }
    
    if (msg.event === 'error') {
        console.log('\n!!! ERROR !!!');
        console.log('  message:', msg.data?.message);
    }
    
    if (msg.event === 'stream_done') {
        console.log('  stopReason:', msg.data?.stopReason);
        ws.close();
    }
});

setTimeout(() => {
    console.log('\n[TIMEOUT]');
    ws.close();
}, 15000);

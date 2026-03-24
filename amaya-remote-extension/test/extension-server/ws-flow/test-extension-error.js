const WebSocket = require('ws');

console.log('=== Testing Extension Server Error Response ===\n');

const ws = new WebSocket('ws://127.0.0.1:8765');

ws.on('open', () => {
    console.log('[1] Connected to extension server');
    
    // First get models to find quota-exhausted one
    ws.send(JSON.stringify({ type: 'get_models' }));
});

let models = [];
let selectedModelId = null;

ws.on('message', (data) => {
    const msg = JSON.parse(data.toString());
    
    if (msg.event === 'models_list') {
        console.log('[2] Received models list');
        models = msg.data.models;
        
        // Find Claude Opus (quota exhausted based on earlier test)
        const opus = models.find(m => m.id === 'MODEL_PLACEHOLDER_M26');
        if (opus) {
            console.log('    Found Claude Opus 4.6:', opus.label);
            console.log('    Quota:', opus.quota);
            console.log('    Reset Time:', opus.resetTime);
            selectedModelId = opus.id;
        }
        
        // Select the model
        console.log('\n[3] Selecting quota-exhausted model...');
        ws.send(JSON.stringify({ type: 'select_model', modelId: 'MODEL_PLACEHOLDER_M26' }));
        
        // Wait then send message
        setTimeout(() => {
            console.log('\n[4] Sending message with quota-exhausted model...');
            ws.send(JSON.stringify({
                type: 'send_message',
                content: 'hello test',
                conversationId: null,
                conversationMode: 'chat'
            }));
        }, 500);
    }
    
    if (msg.event === 'error') {
        console.log('\n=== ERROR RESPONSE FROM EXTENSION ===');
        console.log('Event:', msg.event);
        console.log('Message:', msg.data?.message);
        console.log('Raw:', msg.data?.raw);
        console.log('ConversationId:', msg.data?.conversationId);
        console.log('=====================================\n');
        
        // Save to file
        require('fs').writeFileSync('extension-error-response.json', JSON.stringify(msg, null, 2));
        console.log('Saved to extension-error-response.json');
        
        ws.close();
    }
    
    if (msg.event === 'active_conversation') {
        console.log('[5] Active conversation:', msg.data?.conversationId);
    }
    
    if (msg.event === 'stream_done') {
        console.log('[6] Stream done:', msg.data?.stopReason);
    }
    
    if (msg.event === 'state_sync') {
        console.log('[7] State sync received');
    }
});

ws.on('error', (e) => {
    console.error('WebSocket error:', e.message);
});

setTimeout(() => {
    console.log('\nTimeout - closing');
    ws.close();
}, 10000);

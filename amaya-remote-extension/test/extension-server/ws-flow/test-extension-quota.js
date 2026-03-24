const WebSocket = require('ws');
const fs = require('fs');

console.log('=== Testing Extension Error Handling with Quota-Exhausted Model ===\n');

const ws = new WebSocket('ws://127.0.0.1:8765');
let testPhase = 0;
let errorReceived = false;

ws.on('open', () => {
    console.log('[1] Connected to extension server');
    
    // Get models first
    ws.send(JSON.stringify({ type: 'get_models' }));
});

ws.on('message', (data) => {
    const msg = JSON.parse(data.toString());
    
    if (msg.event === 'models_list') {
        console.log('[2] Models received');
        const models = msg.data.models;
        
        // Find Claude Opus (quota exhausted)
        const opus = models.find(m => m.id === 'MODEL_PLACEHOLDER_M26');
        console.log('    Claude Opus 4.6 (MODEL_PLACEHOLDER_M26):');
        console.log('      quota:', opus?.quota);
        console.log('      resetTime:', opus?.resetTime);
        
        // Select the model
        console.log('\n[3] Selecting MODEL_PLACEHOLDER_M26...');
        ws.send(JSON.stringify({ type: 'select_model', modelId: 'MODEL_PLACEHOLDER_M26' }));
        
        setTimeout(() => {
            console.log('\n[4] Sending message with quota-exhausted model...');
            testPhase = 1;
            ws.send(JSON.stringify({
                type: 'send_message',
                content: 'test quota error',
                conversationId: null,
                conversationMode: 'chat'
            }));
        }, 500);
    }
    
    if (msg.event === 'model_selected') {
        console.log('[3.1] Model selected:', msg.data?.modelId);
    }
    
    if (msg.event === 'active_conversation') {
        console.log('[5] Active conversation:', msg.data?.conversationId);
    }
    
    if (msg.event === 'error') {
        console.log('\n=== ERROR RECEIVED FROM EXTENSION ===');
        console.log('Message:', msg.data?.message);
        console.log('Raw:', msg.data?.raw);
        console.log('ConversationId:', msg.data?.conversationId);
        console.log('=====================================\n');
        errorReceived = true;
        
        // Save response
        fs.writeFileSync('extension-quota-error.json', JSON.stringify(msg, null, 2));
        console.log('Saved to extension-quota-error.json');
        
        ws.close();
    }
    
    if (msg.event === 'stream_done') {
        console.log('[6] Stream done:', msg.data?.stopReason);
        if (!errorReceived && testPhase === 1) {
            console.log('\n!!! NO ERROR RECEIVED - Quota check may not be working !!!\n');
        }
        ws.close();
    }
    
    if (msg.event === 'state_sync') {
        console.log('[7] State sync received, messages:', msg.data?.messages?.length || 0);
    }
    
    if (msg.event === 'text_delta') {
        console.log('[8] Text delta:', msg.data?.text?.substring(0, 50));
    }
});

ws.on('error', (e) => {
    console.error('WebSocket error:', e.message);
});

setTimeout(() => {
    console.log('\nTimeout - closing');
    if (!errorReceived) {
        console.log('!!! NO ERROR RECEIVED !!!');
    }
    ws.close();
}, 15000);

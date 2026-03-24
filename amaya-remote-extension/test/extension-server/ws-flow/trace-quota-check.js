const WebSocket = require('ws');

console.log('=== Tracing Quota Check in Extension ===\n');

const ws = new WebSocket('ws://127.0.0.1:8765');

ws.on('open', () => {
    console.log('[1] Connected');
    // Get full state
    ws.send(JSON.stringify({ type: 'get_state' }));
});

ws.on('message', (data) => {
    const msg = JSON.parse(data.toString());
    
    if (msg.event === 'state_update' || msg.event === 'state_sync') {
        console.log('\n=== STATE RECEIVED ===');
        console.log('selectedModelId:', msg.data?.selectedModelId);
        console.log('activeCascadeId:', msg.data?.activeCascadeId);
        
        const models = msg.data?.models || [];
        console.log('\nModels count:', models.length);
        
        // Find M26
        const m26 = models.find(m => m.id === 'MODEL_PLACEHOLDER_M26');
        if (m26) {
            console.log('\nMODEL_PLACEHOLDER_M26:');
            console.log('  id:', m26.id);
            console.log('  label:', m26.label);
            console.log('  quota:', m26.quota, typeof m26.quota);
            console.log('  resetTime:', m26.resetTime);
            console.log('  FULL:', JSON.stringify(m26, null, 2));
        } else {
            console.log('MODEL_PLACEHOLDER_M26 NOT FOUND');
        }
        
        // Check all models quota
        console.log('\n=== ALL MODELS QUOTA ===');
        models.forEach(m => {
            console.log(`${m.id}: quota=${m.quota} (${typeof m.quota})`);
        });
        
        ws.close();
    }
});

ws.on('error', (e) => console.error('WS Error:', e.message));

setTimeout(() => {
    console.log('\nTimeout');
    ws.close();
}, 5000);

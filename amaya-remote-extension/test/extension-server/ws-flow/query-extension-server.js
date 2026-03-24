const WebSocket = require('ws');

const PORT = 8765;
const ws = new WebSocket(`ws://127.0.0.1:${PORT}`);

ws.on('open', async () => {
    console.log('[WS] Connected to extension server');
    
    // Wait for initial state_sync
    console.log('\n[1] Waiting for initial state...');
    await waitForEvent('state_sync', 3000);
    
    // Get state directly
    console.log('\n[2] Getting state...');
    ws.send(JSON.stringify({ type: 'get_state' }));
    const state = await waitForEvent('state_sync', 3000);
    
    if (state?.data?.messages) {
        console.log('\n[3] Messages count:', state.data.messages.length);
        
        state.data.messages.forEach((msg, idx) => {
            console.log(`\n--- Message ${idx} (${msg.role}) ---`);
            console.log('Content:', msg.content?.substring(0, 100));
            console.log('Attachments:', JSON.stringify(msg.attachments, null, 2));
            if (msg.metadata) {
                const metaKeys = Object.keys(msg.metadata);
                console.log('Metadata keys:', metaKeys.join(', '));
            }
        });
        
        // Save raw state
        require('fs').writeFileSync('raw-extension-state.json', JSON.stringify(state, null, 2));
        console.log('\n[4] Saved to raw-extension-state.json');
    }
    
    ws.close();
});

ws.on('message', (data) => {
    const msg = JSON.parse(data.toString());
    console.log('[RX]', msg.event, '| seqId:', msg.seqId);
});

ws.on('error', (e) => console.error('[WS] Error:', e.message));

function waitForEvent(eventType, timeoutMs) {
    return new Promise((resolve) => {
        const handler = (data) => {
            const msg = JSON.parse(data.toString());
            if (msg.event === eventType) {
                ws.off('message', handler);
                resolve(msg);
            }
        };
        ws.on('message', handler);
        setTimeout(() => {
            ws.off('message', handler);
            resolve(null);
        }, timeoutMs);
    });
}

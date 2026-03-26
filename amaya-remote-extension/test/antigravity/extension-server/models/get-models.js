const WebSocket = require('ws');

const ws = new WebSocket('ws://127.0.0.1:8765');

ws.on('open', () => {
    console.log('Connected to extension server');
    ws.send(JSON.stringify({type: 'get_models'}));
});

ws.on('message', (data) => {
    const msg = JSON.parse(data.toString());
    
    if (msg.event === 'models_list') {
        console.log('Available Models:');
        console.log('================');
        
        msg.data.models.forEach(m => {
            console.log(`- ${m.label}`);
            console.log(`  ID: ${m.id}`);
            console.log(`  Recommended: ${m.isRecommended}`);
            console.log(`  Supports Images: ${m.supportsImages}`);
            console.log(`  Quota: ${m.quota}`);
            console.log(`  Reset Time: ${m.resetTime}`);
            if (m.tagTitle) console.log(`  Tag: ${m.tagTitle}`);
            console.log('');
        });
        
        ws.close();
    }
});

ws.on('error', (e) => console.error('Error:', e.message));

setTimeout(() => {
    console.log('Timeout - closing connection');
    ws.close();
}, 5000);

const WebSocket = require('ws');

const host = '127.0.0.1';
const port = '8765';
const url = `ws://${host}:${port}`;

console.log(`🔌 Connecting to ${url}...`);

const ws = new WebSocket(url);

ws.on('open', () => {
    console.log('✅ Connected!');
    console.log('📤 Sending get_models request...');
    ws.send(JSON.stringify({ action: 'get_models', data: {} }));
});

ws.on('message', (data) => {
    try {
        const msg = JSON.parse(data.toString());
        console.log(`📨 Received Event: ${msg.event}`);
        if (msg.event === 'models_list') {
            console.log('Models Data:', JSON.stringify(msg.data, null, 2));
            ws.close();
            process.exit(0);
        }
    } catch (e) {
        console.log('Error parsing message:', e.message);
    }
});

ws.on('error', (err) => {
    console.error(`❌ Connection error: ${err.message}`);
    process.exit(1);
});

setTimeout(() => {
    console.log('⏰ Timeout waiting for models_list');
    process.exit(1);
}, 5000);

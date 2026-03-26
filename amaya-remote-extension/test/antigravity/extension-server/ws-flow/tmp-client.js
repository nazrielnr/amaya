const WebSocket = require('ws');
const ws = new WebSocket('ws://localhost:8765');

ws.on('open', () => {
    console.log('Connected to test server');
});

ws.on('message', (data) => {
    console.log('--- RAW JSON DATA START ---');
    console.log(data.toString());
    console.log('--- RAW JSON DATA END ---');
    ws.close();
    process.exit(0);
});

ws.on('error', (err) => {
    console.error('Connection error:', err.message);
    process.exit(1);
});

const WebSocket = require('ws');

const ws = new WebSocket('ws://localhost:8765');

ws.on('open', () => {
    console.log('Fetching models from Extension...');
    ws.send(JSON.stringify({ action: 'get_models' }));
});

ws.on('message', (rawData) => {
    const msg = JSON.parse(rawData);
    if (msg.event === 'models_list') {
        console.log("Returned Models:\n", JSON.stringify(msg.data.models, null, 2));
        ws.close();
    }
});

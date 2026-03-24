// Save ALL send debug data to JSON file for clean analysis
const WebSocket = require('ws');
const fs = require('fs');
const ws = new WebSocket('ws://localhost:8765');

const allEvents = [];

ws.on('message', (d) => {
    const msg = JSON.parse(d.toString());
    allEvents.push({ ts: Date.now(), ...msg });
});

ws.on('open', async () => {
    console.log('Connected');
    await new Promise(r => setTimeout(r, 2000));

    // New chat
    ws.send(JSON.stringify({ action: 'new_chat' }));
    await new Promise(r => setTimeout(r, 3000));

    // Send message
    ws.send(JSON.stringify({ action: 'send_message', data: { content: 'TEST_MESSAGE_XYZ' } }));
    await new Promise(r => setTimeout(r, 12000));

    // Save all events to file
    fs.writeFileSync('C:\\tmp\\send-debug.json', JSON.stringify(allEvents, null, 2));
    console.log('Saved to C:\\tmp\\send-debug.json');

    ws.close();
    process.exit(0);
});

ws.on('error', (e) => { console.error(e.message); process.exit(1); });
setTimeout(() => process.exit(0), 25000);

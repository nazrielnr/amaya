const WebSocket = require('ws');
const ws = new WebSocket('ws://localhost:8765');

ws.on('open', () => {
    console.log('Connected. Getting state...');
    ws.send(JSON.stringify({ action: 'get_state' }));
});

ws.on('message', (data) => {
    const msg = JSON.parse(data.toString());
    if (msg.event === 'state_sync') {
        const messages = msg.data.messages;
        console.log(`Received state_sync with ${messages.length} messages.`);
        let i = 0;
        for (const m of messages) {
            i++;
            console.log(`\n--- Message ${i} (${m.role}) ---`);
            if (m.thinking) {
                console.log(`Thinking (${m.thinking.length} chars):\n"${m.thinking.replace(/\n/g, '\\n')}"`);
            }
            if (m.toolExecutions && m.toolExecutions.length > 0) {
                console.log(`Tools: ${m.toolExecutions.map(t => t.name).join(', ')}`);
            }
            if (m.content) {
                console.log(`Content (${m.content.length} chars): "${m.content.substring(0, 50).replace(/\n/g, '\\n')}..."`);
            }
        }
        process.exit(0);
    }
});

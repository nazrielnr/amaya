// Test chat interceptor — captures ALL events including intercepted chat content
const WebSocket = require('ws');
const fs = require('fs');
const ws = new WebSocket('ws://localhost:8765');

const allEvents = [];
const startTime = Date.now();

function ts() { return ((Date.now() - startTime) / 1000).toFixed(1) + 's'; }

ws.on('message', (d) => {
    const msg = JSON.parse(d.toString());
    allEvents.push({ t: ts(), ...msg });

    // Log interesting events
    const skip = ['state_sync', 'state_update'];
    if (!skip.includes(msg.event)) {
        console.log(`[${ts()}] ${msg.event}: ${JSON.stringify(msg.data || {}).substring(0, 200)}`);
    }
});

ws.on('open', async () => {
    console.log(`[${ts()}] Connected. Listening for all events...`);
    await new Promise(r => setTimeout(r, 2000));

    // Step 1: Send a test message and watch for streaming + intercepted events
    console.log(`\n[${ts()}] === SENDING MESSAGE ===`);
    ws.send(JSON.stringify({ action: 'send_message', data: { content: 'say hello in one word' } }));

    // Wait and collect events for 20 seconds
    await new Promise(r => setTimeout(r, 20000));

    // Save all events
    fs.writeFileSync('C:\\tmp\\intercept-results.json', JSON.stringify(allEvents, null, 2));
    console.log(`\n[${ts()}] Saved ${allEvents.length} events to C:\\tmp\\intercept-results.json`);

    // Summary
    const eventTypes = {};
    allEvents.forEach(e => { eventTypes[e.event] = (eventTypes[e.event] || 0) + 1; });
    console.log('\n=== EVENT SUMMARY ===');
    Object.entries(eventTypes).sort((a, b) => b[1] - a[1]).forEach(([k, v]) => {
        console.log(`  ${k}: ${v}x`);
    });

    ws.close();
    process.exit(0);
});

ws.on('error', (e) => { console.error('Error:', e.message); process.exit(1); });
setTimeout(() => process.exit(0), 30000);

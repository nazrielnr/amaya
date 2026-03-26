/**
 * Test ONLY the send message functionality.
 * Tries each method one at a time with delays so user can observe Antigravity.
 * Usage: node test-send.js [host] [port]
 */
const WebSocket = require('ws');
const host = process.argv[2] || 'localhost';
const port = process.argv[3] || '8765';
const ws = new WebSocket(`ws://${host}:${port}`);

function log(msg) {
    console.log(`[${new Date().toISOString().substring(11, 23)}] ${msg}`);
}
function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }

const received = [];
ws.on('message', (d) => {
    const msg = JSON.parse(d.toString());
    received.push(msg);
    // Only log important events
    if (['user_message', 'stream_progress', 'stream_done', 'error', 'new_conversation'].includes(msg.event)) {
        log(`📨 ${msg.event}: ${JSON.stringify(msg.data).substring(0, 200)}`);
    }
});

ws.on('open', async () => {
    log('✅ Connected');
    await sleep(2000); // Wait for state_sync

    // Step 1: First create a new chat so we have a clean slate
    log('\n═══ STEP 1: Create new chat ═══');
    ws.send(JSON.stringify({ action: 'new_chat' }));
    await sleep(3000);
    log('New chat created. Check Antigravity — should show empty chat.\n');

    // Step 2: Try sending a message 
    log('═══ STEP 2: Sending "hello from amaya test 123" ═══');
    received.length = 0;
    ws.send(JSON.stringify({ action: 'send_message', data: { content: 'hello from amaya test 123' } }));

    // Wait and observe
    log('Waiting 10 seconds for response...');
    await sleep(10000);

    // Check what we got
    const userMsgs = received.filter(e => e.event === 'user_message');
    const streamDones = received.filter(e => e.event === 'stream_done');
    const errors = received.filter(e => e.event === 'error');
    const stateUpdates = received.filter(e => e.event === 'state_update');

    log('\n── Results ──');
    log(`user_message events: ${userMsgs.length}`);
    log(`stream_done events: ${streamDones.length}`);
    log(`state_update events: ${stateUpdates.length}`);
    log(`error events: ${errors.length}`);

    if (errors.length > 0) {
        log('ERRORS: ' + JSON.stringify(errors.map(e => e.data)));
    }

    // Check if any stream_done has actual data
    for (const sd of streamDones) {
        log('stream_done data: ' + JSON.stringify(sd.data).substring(0, 300));
    }

    log('\n══════════════════════════════════════════');
    log('CHECK ANTIGRAVITY:');
    log('  - Did "hello from amaya test 123" appear in the chat input?');
    log('  - Did Antigravity start responding?');
    log('  - If nothing happened, the send command is NOT working.');
    log('══════════════════════════════════════════\n');

    ws.close();
    process.exit(0);
});

ws.on('error', (e) => { log('Error: ' + e.message); process.exit(1); });
setTimeout(() => process.exit(0), 30000);

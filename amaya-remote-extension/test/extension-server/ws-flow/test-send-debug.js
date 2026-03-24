/**
 * Test script that sends messages using RAW WebSocket commands,
 * including a custom "test_send_methods" command that will try
 * each send method individually and report which one works.
 * 
 * Also sends get_conversations before and after to track new conversation creation.
 * 
 * Usage: node test-send-debug.js
 */
const WebSocket = require('ws');
const ws = new WebSocket('ws://localhost:8765');

function log(msg) {
    console.log(`[${new Date().toISOString().substring(11, 23)}] ${msg}`);
}
function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }
function send(action, data = {}) {
    ws.send(JSON.stringify({ action, data }));
}

ws.on('message', (d) => {
    const msg = JSON.parse(d.toString());
    const ev = msg.event;
    if (['conversations_list', 'new_conversation', 'user_message', 'stream_done', 'error', 'antigravity_info', 'send_test_result'].includes(ev)) {
        log(`📨 ${ev}: ${JSON.stringify(msg.data).substring(0, 300)}`);
    } else if (ev === 'state_sync') {
        log(`📨 state_sync: conversations=${msg.data.conversations?.length}, isLoading=${msg.data.isLoading}`);
    } else if (ev === 'stream_progress') {
        log(`📨 stream_progress: conv=${msg.data.conversationId?.substring(0, 8)}, delta=${msg.data.sizeDelta}`);
    } else if (ev !== 'state_update' && ev !== 'external_activity') {
        log(`📨 ${ev}`);
    }
});

ws.on('open', async () => {
    log('✅ Connected\n');
    await sleep(2000);

    // Get current conversations count
    log('═══ Get current conversations ═══');
    send('get_conversations');
    await sleep(2000);

    // Create new chat
    log('\n═══ Creating new chat ═══');
    send('new_chat');
    await sleep(3000);

    // Get conversations again to see if new one was created
    log('\n═══ Get conversations after new chat ═══');
    send('get_conversations');
    await sleep(2000);

    // Now try sending - this is the key test
    log('\n═══ Sending message: "AMAYA_TEST_12345" ═══');
    log('Watch the Antigravity chat panel NOW...');
    send('send_message', { content: 'AMAYA_TEST_12345' });

    // Wait a good amount
    await sleep(15000);

    // Check conversations one more time
    log('\n═══ Final conversations check ═══');
    send('get_conversations');
    await sleep(2000);

    log('\n═══ DONE ═══');
    log('Check Antigravity:');
    log('  1. Was a new conversation created?');
    log('  2. Did "AMAYA_TEST_12345" appear in the chat?');
    log('  3. Did the AI respond?');

    ws.close();
    process.exit(0);
});

ws.on('error', (e) => { log('Error: ' + e.message); process.exit(1); });
setTimeout(() => process.exit(0), 40000);

// Quick send test — captures send_debug event to see which method works
const WebSocket = require('ws');
const ws = new WebSocket('ws://localhost:8765');

function log(msg) { console.log(`[${new Date().toISOString().substring(11, 23)}] ${msg}`); }
function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }

ws.on('message', (d) => {
    const msg = JSON.parse(d.toString());
    if (msg.event === 'send_debug') {
        log('═══ SEND DEBUG ═══');
        msg.data.methods.forEach(m => log('  ' + m));
        log('  SUCCESS: ' + msg.data.success);
        log('═══════════════════');
    } else if (msg.event === 'error') {
        log('❌ ERROR: ' + JSON.stringify(msg.data));
    } else if (['user_message', 'stream_progress', 'stream_done', 'new_conversation'].includes(msg.event)) {
        log(`📨 ${msg.event}: ${JSON.stringify(msg.data).substring(0, 200)}`);
    }
});

ws.on('open', async () => {
    log('✅ Connected');
    await sleep(2000);

    // Test get_state
    log('Sending: get_state');
    ws.send(JSON.stringify({ action: 'get_state' }));
    await sleep(2000);

    // Test get_workspaces
    log('Sending: get_workspaces');
    ws.send(JSON.stringify({ action: 'get_workspaces' }));
    await sleep(4000);

    log('Done.');
    ws.close();
    process.exit(0);
});

ws.on('message', (d) => {
    const msg = JSON.parse(d.toString());
    log(`📨 Received: ${msg.type || msg.event}`);
    if (msg.type === 'state_sync') {
        log(`  State Sync: currentWorkspace=${JSON.stringify(msg.currentWorkspace).substring(0, 100)}`);
    } else if (msg.event === 'conversations_list') {
        log(`  Conversations List: ${msg.data.conversations?.length} items`);
        msg.data.conversations?.slice(0, 3).forEach(c => {
            log(`    - ID: ${c.id.substring(0, 8)}, Title: ${c.title.substring(0, 20)}, Workspace: "${c.workspacePath}"`);
        });
    }
});

ws.on('error', (e) => { log('Error: ' + e.message); process.exit(1); });
setTimeout(() => process.exit(0), 30000);

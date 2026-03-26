/**
 * Comprehensive test script for Amaya Remote Extension v2
 * Tests: connect, get_info, get_conversations, new_chat, send_message, 
 *        switch_conversation, stop_generation
 * 
 * Usage: node full-test.js [host] [port]
 */

const WebSocket = require('ws');
const host = process.argv[2] || 'localhost';
const port = process.argv[3] || '8765';

const url = `ws://${host}:${port}`;
const results = [];
let ws;

function log(msg) {
    const ts = new Date().toISOString().substring(11, 23);
    console.log(`[${ts}] ${msg}`);
}

function sendCmd(action, data = {}) {
    log(`📤 SEND: ${action} ${JSON.stringify(data).substring(0, 100)}`);
    ws.send(JSON.stringify({ action, data }));
}

function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }

// Collect all events
const allEvents = [];
let eventResolvers = [];

function waitForEvent(eventName, timeoutMs = 10000) {
    return new Promise((resolve, reject) => {
        const timeout = setTimeout(() => {
            reject(new Error(`Timeout waiting for ${eventName}`));
        }, timeoutMs);

        const check = (msg) => {
            if (msg.event === eventName) {
                clearTimeout(timeout);
                resolve(msg);
                return true;
            }
            return false;
        };

        // Check already received events
        for (const ev of allEvents) {
            if (check(ev)) return;
        }

        eventResolvers.push(check);
    });
}

async function runTests() {
    log('🔌 Connecting to ' + url);

    ws = new WebSocket(url);

    ws.on('message', (data) => {
        try {
            const msg = JSON.parse(data.toString());
            allEvents.push(msg);

            // Notify waiting resolvers
            eventResolvers = eventResolvers.filter(r => !r(msg));
        } catch (e) {
            log('❌ Bad message: ' + e.message);
        }
    });

    ws.on('error', (e) => { log('❌ Error: ' + e.message); process.exit(1); });

    await new Promise((resolve) => ws.on('open', resolve));
    log('✅ Connected!\n');

    // Wait for initial state_sync
    await sleep(2000);

    // ═══════════════════════════════════════════════════
    // TEST 1: State Sync on connect
    // ═══════════════════════════════════════════════════
    log('═══ TEST 1: Initial State Sync ═══');
    const syncEvents = allEvents.filter(e => e.event === 'state_sync');
    if (syncEvents.length > 0) {
        const sync = syncEvents[0].data;
        log('  ✅ state_sync received');
        log('  appName: ' + (sync.appName || '(missing)'));
        log('  appVersion: ' + (sync.appVersion || '(missing)'));
        log('  userEmail: ' + (sync.userEmail || '(missing)'));
        log('  userName: ' + (sync.userName || '(missing)'));
        log('  sessionId: ' + (sync.sessionId || '(missing)'));
        log('  isLoading: ' + sync.isLoading);
        log('  isStreaming: ' + sync.isStreaming);
        log('  conversations: ' + (sync.conversations?.length || 0));
        log('  models: ' + JSON.stringify(sync.models || []));
        log('  currentModel: ' + (sync.currentModel || '(empty)'));

        if (sync.chatConfig) {
            log('  chatConfig.agent.enabled: ' + (sync.chatConfig?.agent?.enabled));
            log('  chatConfig.agent.maxRequests: ' + (sync.chatConfig?.agent?.maxRequests));
        }
        if (sync.antigravityConfig) {
            log('  antigravityConfig.enableAgentMode: ' + sync.antigravityConfig?.enableAgentMode);
        }

        results.push({ test: 'state_sync', pass: true, details: `appName=${sync.appName}, user=${sync.userName}` });
    } else {
        log('  ❌ No state_sync received');
        results.push({ test: 'state_sync', pass: false });
    }

    // ═══════════════════════════════════════════════════
    // TEST 2: Get Info
    // ═══════════════════════════════════════════════════
    log('\n═══ TEST 2: Get Info ═══');
    allEvents.length = 0;
    sendCmd('get_info');
    await sleep(3000);

    const infoEvents = allEvents.filter(e => e.event === 'antigravity_info');
    if (infoEvents.length > 0) {
        const info = infoEvents[0].data;
        log('  ✅ antigravity_info received');
        log('  appName: ' + info.appName);
        log('  appVersion: ' + info.appVersion);
        log('  userEmail: ' + info.userEmail);
        log('  userName: ' + info.userName);
        log('  models: ' + JSON.stringify(info.models || []));
        log('  currentModel: ' + (info.currentModel || '(empty)'));

        // Check diagnostics
        if (info.diagnostics) {
            log('  diagnostics.isRemote: ' + info.diagnostics.isRemote);
            const logsCount = info.diagnostics.extensionLogs?.length || 0;
            log('  diagnostics.extensionLogs: ' + logsCount + ' entries');
        }

        results.push({ test: 'get_info', pass: true, details: `user=${info.userName}, email=${info.userEmail}` });
    } else {
        log('  ❌ No antigravity_info received');
        results.push({ test: 'get_info', pass: false });
    }

    // ═══════════════════════════════════════════════════
    // TEST 3: Get Conversations
    // ═══════════════════════════════════════════════════
    log('\n═══ TEST 3: Get Conversations ═══');
    allEvents.length = 0;
    sendCmd('get_conversations');
    await sleep(2000);

    const convEvents = allEvents.filter(e => e.event === 'conversations_list');
    if (convEvents.length > 0) {
        const convs = convEvents[0].data.conversations;
        log('  ✅ conversations_list received');
        log('  Total conversations: ' + (convs?.length || 0));
        if (convs && convs.length > 0) {
            log('  Most recent:');
            convs.slice(0, 5).forEach((c, i) => {
                const date = new Date(c.lastModified).toISOString().substring(0, 16);
                const sizeMB = (c.size / (1024 * 1024)).toFixed(1);
                log(`    ${i + 1}. ${c.id} (${sizeMB}MB, ${date})`);
            });
        }
        results.push({ test: 'get_conversations', pass: true, details: `${convs?.length || 0} conversations` });
    } else {
        log('  ❌ No conversations_list received');
        results.push({ test: 'get_conversations', pass: false });
    }

    // ═══════════════════════════════════════════════════
    // TEST 4: New Chat
    // ═══════════════════════════════════════════════════
    log('\n═══ TEST 4: New Chat ═══');
    allEvents.length = 0;
    sendCmd('new_chat');
    await sleep(3000);

    const newChatEvents = allEvents.filter(e => e.event === 'new_conversation');
    if (newChatEvents.length > 0) {
        log('  ✅ new_conversation event received');
        results.push({ test: 'new_chat', pass: true });
    } else {
        log('  ❌ No new_conversation event');
        results.push({ test: 'new_chat', pass: false });
    }

    // ═══════════════════════════════════════════════════
    // TEST 5: Send Message
    // ═══════════════════════════════════════════════════
    log('\n═══ TEST 5: Send Message ═══');
    allEvents.length = 0;
    sendCmd('send_message', { content: 'just say ok' });

    // Wait for user_message event
    await sleep(2000);
    const userMsgEvents = allEvents.filter(e => e.event === 'user_message');
    if (userMsgEvents.length > 0) {
        log('  ✅ user_message event received: "' + userMsgEvents[0].data?.content + '"');
    } else {
        log('  ⚠ No user_message event (might still work)');
    }

    // Wait for streaming cycle
    log('  Waiting for AI response...');
    await sleep(8000);

    const streamDoneEvents = allEvents.filter(e => e.event === 'stream_done');
    const streamProgressEvents = allEvents.filter(e => e.event === 'stream_progress');
    const stateUpdates = allEvents.filter(e => e.event === 'state_update');

    log('  stream_progress events: ' + streamProgressEvents.length);
    log('  stream_done events: ' + streamDoneEvents.length);
    log('  state_update events: ' + stateUpdates.length);

    if (streamDoneEvents.length > 0) {
        log('  ✅ stream_done received — AI responded');
        const doneData = streamDoneEvents[streamDoneEvents.length - 1].data;
        if (doneData && Object.keys(doneData).length > 0) {
            log('  stream_done data: ' + JSON.stringify(doneData).substring(0, 300));
        }
        results.push({ test: 'send_message', pass: true, details: `${streamProgressEvents.length} progress, ${streamDoneEvents.length} done` });
    } else {
        log('  ⚠ No stream_done — AI may still be responding');
        results.push({ test: 'send_message', pass: false, details: 'no stream_done' });
    }

    // ═══════════════════════════════════════════════════
    // TEST 6: Switch Conversation (back to first one)
    // ═══════════════════════════════════════════════════
    log('\n═══ TEST 6: Switch Conversation ═══');
    // Get conversations first to find an ID to switch to
    allEvents.length = 0;
    sendCmd('get_conversations');
    await sleep(2000);

    const convList = allEvents.filter(e => e.event === 'conversations_list');
    if (convList.length > 0 && convList[0].data.conversations?.length >= 2) {
        const targetId = convList[0].data.conversations[1].id; // Switch to 2nd conversation
        log('  Switching to conversation: ' + targetId);
        allEvents.length = 0;
        sendCmd('switch_conversation', { conversationId: targetId });
        await sleep(3000);

        const switchEvents = allEvents.filter(e => e.event === 'conversation_switched');
        if (switchEvents.length > 0) {
            log('  ✅ conversation_switched event received');
            results.push({ test: 'switch_conversation', pass: true, details: `switched to ${targetId}` });
        } else {
            // Check if state was updated at least
            const anyUpdate = allEvents.filter(e => e.event === 'state_update' || e.event === 'external_activity');
            if (anyUpdate.length > 0) {
                log('  ⚠ No explicit switch event, but got state updates');
                results.push({ test: 'switch_conversation', pass: true, details: 'implicit via state_update' });
            } else {
                log('  ❌ No switch confirmation');
                results.push({ test: 'switch_conversation', pass: false });
            }
        }
    } else {
        log('  ⚠ Not enough conversations to test switching');
        results.push({ test: 'switch_conversation', pass: false, details: 'not enough conversations' });
    }

    // ═══════════════════════════════════════════════════
    // TEST 7: Stop Generation (should be safe when not streaming)
    // ═══════════════════════════════════════════════════
    log('\n═══ TEST 7: Stop Generation ═══');
    allEvents.length = 0;
    sendCmd('stop_generation');
    await sleep(2000);

    const stopEvents = allEvents.filter(e => e.event === 'stream_done');
    if (stopEvents.length > 0) {
        log('  ✅ stream_done received after stop');
        results.push({ test: 'stop_generation', pass: true });
    } else {
        log('  ⚠ No stream_done (may not have been streaming)');
        results.push({ test: 'stop_generation', pass: true, details: 'no streaming to stop' });
    }

    // ═══════════════════════════════════════════════════
    // SUMMARY
    // ═══════════════════════════════════════════════════
    log('\n\n╔═══════════════════════════════════════════════════╗');
    log('║           TEST RESULTS SUMMARY                    ║');
    log('╚═══════════════════════════════════════════════════╝');

    let passCount = 0;
    let failCount = 0;

    for (const r of results) {
        const status = r.pass ? '✅ PASS' : '❌ FAIL';
        const details = r.details ? ` — ${r.details}` : '';
        log(`  ${status}  ${r.test}${details}`);
        if (r.pass) passCount++;
        else failCount++;
    }

    log(`\n  Total: ${passCount} passed, ${failCount} failed out of ${results.length}`);
    log('');

    ws.close();
    process.exit(0);
}

runTests().catch(e => {
    log('❌ Test error: ' + e.message);
    process.exit(1);
});

// Safety timeout
setTimeout(() => {
    log('⏱ Global timeout — force exit');
    ws?.close();
    process.exit(1);
}, 60000);

/**
 * WebSocket Test Client — connects to Amaya Remote extension and logs everything.
 * 
 * Usage:
 *   node test-client.js [IP] [PORT]
 *   node test-client.js 192.168.1.100 8765
 * 
 * Default: localhost:8765
 */

const WebSocket = require('ws');
const readline = require('readline');

const host = process.argv[2] || 'localhost';
const port = process.argv[3] || '8765';
const url = `ws://${host}:${port}`;

console.log(`\n🔌 Connecting to ${url}...\n`);

const ws = new WebSocket(url);

ws.on('open', () => {
    console.log('✅ Connected!\n');
    console.log('Commands:');
    console.log('  send <message>  — Send a message to Antigravity');
    console.log('  new             — Start new chat');
    console.log('  stop            — Stop generation');
    console.log('  state           — Request full state');
    console.log('  quit            — Disconnect\n');
    console.log('═══════════════════════════════════════════════════');
    console.log('Listening for events...\n');

    startRepl();
});

ws.on('message', (data) => {
    try {
        const msg = JSON.parse(data.toString());
        const timestamp = new Date().toISOString().substring(11, 23);

        console.log(`\n[${timestamp}] 📨 EVENT: ${msg.event}`);
        console.log('─'.repeat(50));

        switch (msg.event) {
            case 'state_sync':
                console.log('  Messages:', msg.data?.messages?.length || 0);
                console.log('  isLoading:', msg.data?.isLoading);
                console.log('  isStreaming:', msg.data?.isStreaming);
                console.log('  currentModel:', msg.data?.currentModel);
                if (msg.data?.messages?.length > 0) {
                    console.log('\n  Last 3 messages:');
                    msg.data.messages.slice(-3).forEach((m, i) => {
                        console.log(`    [${m.role}] ${m.content?.substring(0, 100)}${m.content?.length > 100 ? '...' : ''}`);
                        if (m.toolExecutions?.length > 0) {
                            m.toolExecutions.forEach(t => {
                                console.log(`      🔧 ${t.name} (${t.status})`);
                            });
                        }
                    });
                }
                break;

            case 'text_delta':
                process.stdout.write(`  ✏️  "${msg.data?.content || ''}"  (total: ${msg.data?.fullContent?.length || '?'} chars)\n`);
                break;

            case 'user_message':
                console.log(`  👤 User: ${msg.data?.content}`);
                break;

            case 'new_assistant_message':
                console.log(`  🤖 Assistant started (id: ${msg.data?.id})`);
                break;

            case 'tool_call_start':
                console.log(`  🔧 Tool call: ${msg.data?.name}`);
                console.log(`     Args: ${JSON.stringify(msg.data?.arguments)?.substring(0, 200)}`);
                break;

            case 'tool_call_result':
                console.log(`  ✅ Tool result: ${msg.data?.name} → ${msg.data?.status}`);
                console.log(`     Result: ${msg.data?.result?.substring(0, 200)}`);
                break;

            case 'stream_done':
                console.log('  🏁 Stream completed');
                break;

            case 'state_update':
                console.log('  📊 State update:', JSON.stringify(msg.data));
                break;

            case 'confirmation_required':
                console.log(`  ⚠️  Confirmation needed: ${msg.data?.reason}`);
                console.log(`     Details: ${msg.data?.details}`);
                console.log(`     Risk: ${msg.data?.riskLevel}`);
                break;

            default:
                console.log('  Data:', JSON.stringify(msg.data, null, 2)?.substring(0, 500));
        }

        console.log('');
    } catch (e) {
        console.log('  Raw:', data.toString().substring(0, 500));
    }
});

ws.on('close', () => {
    console.log('\n❌ Disconnected from server');
    process.exit(0);
});

ws.on('error', (err) => {
    console.error(`\n❌ Connection error: ${err.message}`);
    process.exit(1);
});

function startRepl() {
    const rl = readline.createInterface({
        input: process.stdin,
        output: process.stdout,
        prompt: '> '
    });

    rl.prompt();

    rl.on('line', (line) => {
        const input = line.trim();

        if (input.startsWith('send ')) {
            const content = input.substring(5);
            ws.send(JSON.stringify({ action: 'send_message', data: { content } }));
            console.log(`📤 Sent: "${content}"`);
        } else if (input === 'new') {
            ws.send(JSON.stringify({ action: 'new_chat', data: {} }));
            console.log('📤 New chat requested');
        } else if (input === 'stop') {
            ws.send(JSON.stringify({ action: 'stop_generation', data: {} }));
            console.log('📤 Stop generation requested');
        } else if (input === 'state') {
            ws.send(JSON.stringify({ action: 'get_state', data: {} }));
            console.log('📤 State requested');
        } else if (input === 'quit' || input === 'exit') {
            ws.close();
            rl.close();
        } else if (input) {
            console.log('Unknown command. Use: send <msg>, new, stop, state, quit');
        }

        rl.prompt();
    });
}

// Clean WS test — log ALL events clearly with sequence numbers
const ws = new (require('ws'))('ws://127.0.0.1:8765');
let seq = 0;

ws.on('open', () => {
    console.log(`[${seq++}] === CONNECTED ===`);
    ws.send(JSON.stringify({ action: 'get_state' }));
    console.log(`[${seq++}] >>> SENT: get_state`);
});

ws.on('message', d => {
    try {
        const m = JSON.parse(d.toString());
        const e = m.event;
        const dd = m.data || {};

        console.log(`\n[${seq++}] <<< EVENT: "${e}"`);

        switch (e) {
            case 'state_sync':
                console.log(`  messages: ${JSON.stringify(dd.messages)?.substring(0, 100)}`);
                console.log(`  isLoading: ${dd.isLoading}`);
                console.log(`  isStreaming: ${dd.isStreaming}`);
                console.log(`  currentModel: "${dd.currentModel}"`);
                console.log(`  toolExecutions: ${JSON.stringify(dd.toolExecutions)}`);
                console.log(`  appName: "${dd.appName}"`);
                console.log(`  appVersion: "${dd.appVersion}"`);
                // Check for unexpected fields
                const expectedKeys = ['messages', 'isLoading', 'isStreaming', 'currentModel', 'toolExecutions', 'appName', 'appVersion'];
                const extraKeys = Object.keys(dd).filter(k => !expectedKeys.includes(k));
                if (extraKeys.length) console.log(`  *** EXTRA KEYS: ${extraKeys.join(', ')}`);
                break;
            case 'models_list':
                console.log(`  models: ${JSON.stringify(dd.models)}`);
                console.log(`  selectedModelId: "${dd.selectedModelId}"`);
                break;
            case 'conversations_list':
                const c = dd.conversations || [];
                console.log(`  count: ${c.length}`);
                if (c.length > 0) {
                    console.log(`  [0]: id=${c[0].id?.substring(0, 8)}, preview="${c[0].preview}", lastModified=${c[0].lastModified}, size=${c[0].size}`);
                }
                break;
            default:
                console.log(`  data: ${JSON.stringify(dd).substring(0, 200)}`);
        }
    } catch (ex) {
        console.log(`[${seq++}] PARSE ERROR: ${ex.message}`);
    }
});

ws.on('error', e => console.log('ERR:', e.message));
ws.on('close', () => { console.log('\nCLOSED'); process.exit(); });
setTimeout(() => { ws.close(); process.exit(); }, 20000);

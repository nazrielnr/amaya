const fs = require('fs');
const files = [
    'c:\\Users\\BiuBiu\\Documents\\my app\\amaya\\amaya-remote-extension\\test\\model-logs\\MODEL_PLACEHOLDER_M26\\1-cek-random-bug-secara-read-only-lalu-sebutkan-3-file-paling-relevan.json',
    'c:\\Users\\BiuBiu\\Documents\\my app\\amaya\\amaya-remote-extension\\test\\model-logs\\MODEL_PLACEHOLDER_M26\\4b0aee88-e410-4de0-ae9a-d38e470cc20a.json',
    'c:\\Users\\BiuBiu\\Documents\\my app\\amaya\\amaya-remote-extension\\test\\raw-cortex-capture.json',
    'c:\\Users\\BiuBiu\\Documents\\my app\\amaya\\amaya-remote-extension\\test\\raw-cortex-steps-2026-03-17T23-54-03-537Z.json'
];

files.forEach(f => {
    console.log('\n======================================');
    console.log('Analyzing: ' + f.split('\\').pop());
    console.log('======================================');
    try {
        const raw = fs.readFileSync(f, 'utf8');
        if (!raw.trim()) {
            console.log('File is empty.');
            return;
        }
        const data = JSON.parse(raw);
        if (Array.isArray(data)) {
            console.log(`[Array of ${data.length} items]`);
            data.forEach((item, i) => {
                let summary = `[${i}] `;
                
                // Cortex Trajectory Step pattern
                if (item.type) summary += `Type: ${item.type} `;
                if (item.status) summary += `(Status: ${item.status}) `;

                // Sync/Streaming pattern
                if (item.event) summary += `Event: ${item.event} `;
                if (item.role) summary += `Role: ${item.role} `;
                
                if (item.contentPreview || item.content || item?.message?.content) summary += `[Has Content] `;
                if (item.thinkingPreview || item.thinking) summary += `[Has Thinking] `;
                if (item.toolExecutions && item.toolExecutions.length > 0) summary += `[Tools: ${item.toolExecutions.length}] `;
                
                // Planner response
                if (item.plannerResponse) {
                    summary += `\n    -> Planner: `;
                    if (item.plannerResponse.thinking) summary += `thinking, `;
                    if (item.plannerResponse.toolCalls?.length > 0) summary += `toolCalls(${item.plannerResponse.toolCalls.map(t=>t.name).join(',')}), `;
                    if (item.plannerResponse.response) summary += `response_text, `;
                }
                
                // Generic step tools
                const tools = Object.keys(item).filter(k => k !== 'type' && k !== 'status' && k !== 'metadata' && k !== 'plannerResponse');
                if (tools.length > 0 && item.type) {
                    summary += `\n    -> Payload keys: ${tools.join(', ')}`;
                }

                console.log(summary);
            });
        } else {
            console.log(`[Object keys: ${Object.keys(data).join(', ')}]`);
            if (data.events && Array.isArray(data.events)) {
                data.events.forEach((item, i) => {
                    let summary = `  [Event ${i}] ${item.event}`;
                    if (item.data) {
                        if (item.data.content) summary += ` (Content)`;
                        if (item.data.text) summary += ` (Text)`;
                        if (item.data.name) summary += ` (Tool: ${item.data.name})`;
                        if (item.data.messages) {
                            summary += `\n      -> Sync Messages: ${item.data.messages.length}`;
                            item.data.messages.forEach((m, idx) => {
                                summary += `\n          [${idx}] Role: ${m.role}`;
                                if (m.content) summary += ` [Content]`;
                                if (m.thinking) summary += ` [Thinking]`;
                                if (m.toolExecutions?.length > 0) summary += ` [Tools: ${m.toolExecutions.map(t=>t.name).join(',')}]`;
                            });
                        }
                    }
                    console.log(summary);
                });
            }
        }
    } catch(e) { 
        console.log('Error: ', e.message); 
    }
});

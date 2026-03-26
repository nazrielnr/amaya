const WebSocket = require('ws');
const fs = require('fs');
const path = require('path');

const PORT = 8765;
const wss = new WebSocket.Server({ port: PORT, host: '0.0.0.0' });

console.log(`[RAW SERVER] Memulai server di port ${PORT}...`);

wss.on('connection', (ws) => {
    console.log('[RAW SERVER] Client connected. Simulating Thinking -> Tool -> Thinking stream...');
    
    const conversationId = "simulated-conv-" + Date.now();
    
    const sendEvent = (event) => {
        const payload = JSON.stringify({
            ...event,
            conversationId: conversationId,
            seqId: Math.floor(Math.random() * 1000)
        });
        ws.send(payload);
        console.log(`[RAW SERVER] Sent event: ${event.kind || event.event}`);
    };

    // 1. New Assistant Message
    sendEvent({ kind: "new_assistant_message" });

    // 2. Initial Thinking
    setTimeout(() => {
        sendEvent({ 
            kind: "AiThinking", 
            id: "think-1", 
            text: "**Analyzing workspace context...**\nI'm looking at the project structure to understand the requirements." 
        });
    }, 500);

    // 3. Tool Call Start
    setTimeout(() => {
        sendEvent({
            kind: "tool_call_start",
            toolCallId: "tool-1",
            name: "list_files",
            arguments: { path: "./" }
        });
    }, 1500);

    // 4. Tool Call Result
    setTimeout(() => {
        sendEvent({
            kind: "tool_call_result",
            toolCallId: "tool-1",
            result: "[\"file1.kt\", \"file2.kt\", \"test-server.js\"]",
            isError: false
        });
    }, 2500);

    // 5. MORE Thinking (Different ID -> Should Split into new block)
    setTimeout(() => {
        sendEvent({ 
            kind: "AiThinking", 
            id: "think-2", 
            text: "**Found relevant files.**\nNow I will examine the contents of `test-server.js` to see how it handles requests." 
        });
    }, 3500);

    // 6. Text Delta (Answer)
    setTimeout(() => {
        sendEvent({ kind: "text_delta", text: "I have analyzed the workspace. The server is correctly configured to handle raw JSON output." });
    }, 4500);

    // 7. Stream Done
    setTimeout(() => {
        sendEvent({ kind: "stream_done" });
    }, 5500);

    ws.on('message', (msg) => {
        console.log(`[RAW SERVER] Received: ${msg}`);
    });
    
    ws.on('close', () => console.log('[RAW SERVER] Client disconnected.'));
});

console.log(`[RAW SERVER] ✅ READY. Simulating "cek workspace" flow.`);

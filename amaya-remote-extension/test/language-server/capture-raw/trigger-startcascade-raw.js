/*
 * Trigger a fresh cascade via Amaya Remote WS server and capture raw JSON events.
 *
 * Usage:
 *   node test/trigger-startcascade-raw.js [host] [port] [prompt]
 *
 * Example:
 *   node test/trigger-startcascade-raw.js 127.0.0.1 8765 "cek workspace"
 */

const fs = require('fs');
const path = require('path');
const WebSocket = require('ws');

const host = process.argv[2] || '127.0.0.1';
const port = process.argv[3] || '8765';
const prompt = process.argv.slice(4).join(' ').trim() || 'cek workspace';
const wsUrl = `ws://${host}:${port}`;

const startedAt = Date.now();
const outDir = __dirname;
const stamp = new Date().toISOString().replace(/[:.]/g, '-');
const rawOutPath = path.join(outDir, `raw-cascade-events-${stamp}.json`);
const summaryOutPath = path.join(outDir, `raw-cortex-steps-${stamp}.json`);

const events = [];
let activeConversationId = null;
let sentMessage = false;
let gotStreamDone = false;
let closed = false;
let pollTimer = null;
let sawExtensionStyleEvent = false;
let sawStartCascadeOnly = false;

function ts() {
  return new Date().toISOString();
}

function log(line) {
  process.stdout.write(`${line}\n`);
}

function send(ws, action, data = {}) {
  const payload = { action, data };
  ws.send(JSON.stringify(payload));
  log(`[SEND] ${action} ${JSON.stringify(data)}`);
}

function pushEvent(obj) {
  events.push({
    receivedAt: ts(),
    event: obj.event || null,
    seqId: obj.seqId ?? null,
    conversationId: (obj.data && (obj.data.conversationId || obj.data.id)) || null,
    raw: obj,
  });
}

function extractCortexStepsFromEvents(all) {
  const list = [];

  for (const item of all) {
    const evt = item.raw || {};
    const data = evt.data || {};

    if (evt.event === 'ai_thinking') {
      list.push({
        kind: 'thinking_delta',
        seqId: evt.seqId ?? null,
        conversationId: data.conversationId || null,
        textPreview: String(data.text || '').slice(0, 300),
      });
    }

    if (evt.event === 'text_delta') {
      list.push({
        kind: 'text_delta',
        seqId: evt.seqId ?? null,
        conversationId: data.conversationId || null,
        textPreview: String(data.text || '').slice(0, 300),
      });
    }

    if (evt.event === 'tool_call_start') {
      list.push({
        kind: 'tool_call_start',
        seqId: evt.seqId ?? null,
        conversationId: data.conversationId || null,
        toolCallId: data.toolCallId || null,
        name: data.name || null,
        metadata: data.metadata || {},
      });
    }

    if (evt.event === 'tool_call_result') {
      list.push({
        kind: 'tool_call_result',
        seqId: evt.seqId ?? null,
        conversationId: data.conversationId || null,
        toolCallId: data.toolCallId || null,
        name: data.name || null,
        isError: !!data.isError,
        resultPreview: String(data.result || '').slice(0, 300),
      });
    }

    if (evt.event === 'state_sync' && Array.isArray(data.messages)) {
      data.messages.forEach((m, idx) => {
        const meta = m.metadata || {};
        const hasStepMeta = meta.trajectoryId || meta.stepIndex || meta.cascadeId;
        if (hasStepMeta || m.thinking || (m.toolExecutions && m.toolExecutions.length)) {
          list.push({
            kind: 'state_sync_message',
            seqId: evt.seqId ?? null,
            conversationId: data.conversationId || null,
            index: idx,
            role: m.role,
            hasThinking: !!m.thinking,
            contentPreview: String(m.content || '').slice(0, 180),
            thinkingPreview: String(m.thinking || '').slice(0, 180),
            metadata: meta,
            toolExecutions: (m.toolExecutions || []).map(t => ({
              toolCallId: t.toolCallId,
              name: t.name,
              status: t.status,
              metadata: t.metadata || {},
            })),
          });
        }
      });
    }
  }

  return list;
}

function persist() {
  fs.writeFileSync(rawOutPath, JSON.stringify(events, null, 2), 'utf8');

  const cortexSteps = extractCortexStepsFromEvents(events);
  fs.writeFileSync(summaryOutPath, JSON.stringify(cortexSteps, null, 2), 'utf8');

  log(`\n[SAVED] Raw events: ${rawOutPath}`);
  log(`[SAVED] Cortex-like step summary: ${summaryOutPath}`);
  log(`[INFO] Total events: ${events.length}`);
  log(`[INFO] Total extracted step rows: ${cortexSteps.length}`);
}

function finish(ws, code = 0) {
  if (closed) return;
  closed = true;
  if (pollTimer) {
    clearInterval(pollTimer);
    pollTimer = null;
  }
  try {
    persist();
  } catch (e) {
    log(`[ERROR] Persist failed: ${e.message}`);
  }
  try {
    ws.close();
  } catch (_) {}
  process.exit(code);
}

log(`[CONNECT] ${wsUrl}`);
const ws = new WebSocket(wsUrl);

ws.on('open', () => {
  log('[OPEN] Connected to extension server');
  send(ws, 'new_chat');
  send(ws, 'get_state');

  // Keep nudging state sync in case server debounces initial state.
  pollTimer = setInterval(() => {
    if (!closed) send(ws, 'get_state');
  }, 2500);
});

ws.on('message', (buf) => {
  let msg;
  try {
    msg = JSON.parse(buf.toString());
  } catch (e) {
    log(`[WARN] Non-JSON message: ${String(buf).slice(0, 200)}`);
    return;
  }

  pushEvent(msg);

  const e = msg.event;
  const d = msg.data || {};

  if (['state_sync', 'active_conversation', 'new_conversation', 'ai_thinking', 'text_delta', 'stream_done', 'tool_call_start', 'tool_call_result', 'error'].includes(e)) {
    sawExtensionStyleEvent = true;
  }

  if (e === 'startCascade') {
    sawStartCascadeOnly = true;
    log('[EVENT] startCascade received (likely raw/mock server mode)');
    // Be polite to mock servers that expect an ACK.
    try {
      ws.send(JSON.stringify({ event: 'ACK', received: true, prompt }));
      log('[SEND] ACK startCascade');
    } catch (_) {}
  }

  if (e === 'active_conversation' && d.conversationId) {
    activeConversationId = d.conversationId;
    log(`[EVENT] active_conversation: ${activeConversationId}`);

    if (!sentMessage) {
      sentMessage = true;
      send(ws, 'send_message', {
        content: prompt,
        conversationId: activeConversationId,
      });
      log(`[TRIGGER] Prompt sent via active_conversation: ${prompt}`);
    }
  }

  if (e === 'new_conversation' && d.conversationId) {
    activeConversationId = d.conversationId;
    log(`[EVENT] new_conversation: ${activeConversationId}`);

    if (!sentMessage) {
      sentMessage = true;
      send(ws, 'send_message', {
        content: prompt,
        conversationId: activeConversationId,
      });
      log(`[TRIGGER] Prompt sent via new_conversation: ${prompt}`);
    }
  }

  if (e === 'state_sync') {
    log(`[EVENT] state_sync: messages=${Array.isArray(d.messages) ? d.messages.length : 0} isStreaming=${!!d.isStreaming} conv=${d.conversationId || activeConversationId || '-'}`);

    if (!sentMessage && activeConversationId) {
      sentMessage = true;
      send(ws, 'send_message', {
        content: prompt,
        conversationId: activeConversationId,
      });
      log(`[TRIGGER] Prompt sent via state_sync: ${prompt}`);
    }
  }

  if (e === 'ai_thinking') {
    log(`[EVENT] ai_thinking: ${String(d.text || '').slice(0, 120)}`);
  }

  if (e === 'text_delta') {
    log(`[EVENT] text_delta: ${String(d.text || '').slice(0, 120)}`);
  }

  if (e === 'tool_call_start') {
    log(`[EVENT] tool_call_start: ${d.name || '-'} (${d.toolCallId || '-'})`);
  }

  if (e === 'tool_call_result') {
    log(`[EVENT] tool_call_result: ${d.name || '-'} err=${!!d.isError}`);
  }

  if (e === 'stream_done') {
    gotStreamDone = true;
    log(`[EVENT] stream_done: reason=${d.stopReason || '-'} conv=${d.conversationId || activeConversationId || '-'}`);
    setTimeout(() => finish(ws, 0), 700);
  }

  if (e === 'error') {
    log(`[EVENT] error: ${String(d.message || '').slice(0, 240)}`);
  }
});

ws.on('error', (e) => {
  log(`[ERROR] WS error: ${e.message}`);
  finish(ws, 1);
});

ws.on('close', () => {
  if (!closed) {
    log('[CLOSE] Socket closed by server');
    finish(ws, gotStreamDone ? 0 : 1);
  }
});

setTimeout(() => {
  if (!sawExtensionStyleEvent && sawStartCascadeOnly) {
    log('[WARN] Hanya menerima event startCascade/ACK. Ini terlihat seperti raw mock server, bukan stream event extension normal.');
  }
  log(`[TIMEOUT] ${(Date.now() - startedAt) / 1000}s elapsed, stopping capture`);
  finish(ws, gotStreamDone ? 0 : 1);
}, 90000);

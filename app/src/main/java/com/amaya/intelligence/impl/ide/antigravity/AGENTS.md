# Android Antigravity Runtime Instructions

## Scope
- This file applies to `app/src/main/java/com/amaya/intelligence/impl/ide/antigravity/` and its children.

## Remote Runtime Rules
- Keep Antigravity-specific runtime logic isolated in this subtree.
- Use the existing provider abstraction to keep the rest of the app provider-neutral.
- Keep streaming, session sync, message mapping, and protocol details here rather than in shared UI or local tool code.
- Do not mix device-local tool behavior into this subtree.

## Editing Guidance
- Prefer small, explicit changes in clients, protocol helpers, stream managers, and mappers.
- Preserve the separation between transport/discovery and higher-level application state.

## File Tree
```text
impl/ide/antigravity/
├─ AGENTS.md
├─ AntigravityProvider.kt
├─ AntigravityProtocol.kt
├─ client/
└─ services/
	├─ event/
	├─ mapper/
	└─ streaming/
```

## File Functions
- `AGENTS.md`: rules for Antigravity-specific runtime work.
- `AntigravityProvider.kt`: IDE metadata and capability definition.
- `AntigravityProtocol.kt`: protocol constants and shared wire markers.
- `client/`: WebSocket/client transport and remote session handling.
- `services/event/`: event routing and state synchronization handlers.
- `services/mapper/`: conversion between remote payloads and UI/domain models.
- `services/streaming/`: streaming state tracking and merge logic.

## Key Source Code
- `AntigravityProvider.kt`: provider identity and capability metadata.
- `AntigravityProtocol.kt`: wire constants for step types and synthetic thinking markers.
- `client/RemoteSessionClient.kt`: WebSocket client, reconnect logic, and message dispatch.
- `client/RemoteSessionForegroundService.kt`: foreground service lifecycle for remote sessions.
- `services/AntigravityIntelligenceService.kt`: service facade bridging remote events to domain state.
- `services/event/AntigravityEventHandler.kt`: event router that delegates to specialized handlers.
- `services/event/StateSyncEventHandler.kt`: state sync reconciliation and optimistic UI merging.
- `services/event/StreamingEventHandler.kt`: text/thinking streaming assembly.
- `services/event/ToolCallEventHandler.kt`: remote tool call lifecycle and result updates.
- `services/event/WorkspaceEventHandler.kt`: conversation, model, workspace, and project-file updates.
- `services/event/ErrorEventHandler.kt`: remote error handling and recovery.
- `services/mapper/AntigravityMessageMapper.kt`: conversion from remote messages to UI models.
- `tools/AntigravityToolMapper.kt`: normalization of remote tool names, args, and UI metadata.

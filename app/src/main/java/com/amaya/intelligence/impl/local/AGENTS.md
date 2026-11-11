# Android Local Runtime Instructions

## Scope
- This file applies to `app/src/main/java/com/amaya/intelligence/impl/local/` and its children.

## Current Focus

Local/core behavior is the active audit and repair focus. Remote-session, Antigravity, Opencode, and Windows Bridge behavior remain incomplete/deferred; see `docs/local/audits/VISION-HOLD.md`.

## Local Runtime Rules
- Keep device-local execution, persistence, browser automation, and runtime services in this subtree.
- This is the correct place for local tool orchestration, background services, browser control, and non-remote behaviors.
- Keep remote API assumptions out of this layer.
- Prefer Android-native patterns for services, background work, and local state.
- Local conversation renaming uses the existing save mutex and cancels pending title generation; automatic titles must not overwrite a user-entered name.
- Local chat must persist or resolve the active conversation id before starting a model turn when downstream session-memory or reflection code needs a stable session id.
- Before changing local runtime flow, crosscheck the current workspace diff and recent commits for the touched subtree.

## Coordination
- Coordinate with `data/local/` for storage-backed behavior, `data/repository/` for memory/skill/session repositories, and with `tools/` and `service/` for execution/runtime behavior.
- If a change needs remote integration, move the remote-specific part to the remote instruction subtree instead of broadening this file.
- If files, folders, or features change here, update this AGENTS file and the nearest runtime docs in the same change.

## File Tree
```text
impl/local/
├─ AGENTS.md
├─ LocalIntelligenceService.kt
├─ browser/
├─ chat/                         # extracted local chat projections/collaborators
├─ tools/
└─ providers/
```

## File Functions
- `AGENTS.md`: rules for local runtime and services.
- `LocalIntelligenceService.kt`: local AI orchestration and persistence-backed chat facade.
- `LocalConversationContext.kt`: pure interruption repair, bounded stored-tool payloads, compaction context, canonical-history replay, and UI-to-provider context mapping.
- `chat/LocalTurnCoordinator.kt`: concurrent turn registry, lifecycle IDs, pending-message state, and turn model.
- `chat/LocalSessionProjection.kt`: pure running-session phase/detail projection extracted from the service.
- `chat/LocalAgentEventReducer.kt`: pure local `AgentEvent` projection for text, thinking, tool lifecycle, canonical history, subagents, and terminal repair; service-owned browser, notification, persistence, and compaction side effects remain outside.
- `ToolConfirmationRegistry.kt`: turn-bound, idempotent inline tool approval state.
- `browser/`: GeckoView controller, WebExtension JavaScript bridge, session manager, DOM inspection, and browser safety handling.
- `tools/`: local tool mapping, browser tool wrapping, and execution helpers.
- `providers/`: local provider adapters and implementation-specific helpers.

## Key Source Code
- `LocalIntelligenceService.kt`: local conversation facade, state ownership, lifecycle API, and collaborator wiring.
- `LocalConversationEntry.kt` and `LocalDelegatedTurn.kt`: persisted direct/delegated conversation entry paths.
- `LocalTurnStart.kt`, `LocalTurnNotifications.kt`, and `LocalTurnProjection.kt`: turn launch, notification projection, and streamed event projection.
- `LocalTurnPersistence.kt`, `LocalStreamProjection.kt`, and `LocalConversationCodecWriter.kt`: atomic turn persistence, stream/tool approval projection, and conversation JSON writes.
- `browser/AndroidBrowserController.kt`: GeckoView interaction, navigation, DOM-backed browser actions, and content-process kill/crash reporting.
- `browser/GeckoBrowserRuntime.kt`: process-wide Gecko runtime plus built-in WebExtension JavaScript bridge. Bridge attach is bounded (single delegate registration, one reload-and-wait recovery) and a killed content process is reported as unrecoverable instead of being waited out; a stale port that no longer answers is detected by a liveness probe because Gecko delivers no disconnect for a reclaimed process.
- `browser/BrowserSessionManager.kt`: synchronized conversation-session registry, visible-session projection, LRU trimming, wake-lock boundary, and operator view ownership.
- `browser/BrowserConversationSession.kt`: per-conversation browser state, GeckoSession ownership, and resumable approval checkpoints before workspace files are selected for a web form.
- `browser/BrowserAssistantStream.kt`: browser assistant-stream projection callbacks.
- `browser/BrowserTaskExecutor.kt`: parent browser-task parsing, progress snapshots, and normalized subtool execution.
- `browser/BrowserToolDispatcher.kt`: canonical browser action dispatch.
- `browser/BrowserFileTransfer.kt`: workspace upload transfer and file-accept validation.
- `browser/BrowserDomActions.kt`: DOM-backed browser actions and result formatting.
- `browser/BrowserConversationState.kt`: browser session lifecycle, uploads, downloads, tabs, and persisted state extensions.
- `browser/BrowserSessionPersistence.kt`: concrete SharedPreferences codec/store for browser tabs, history, active tab, and active URL; no Gecko or UI side effects.
- `browser/BrowserRuntimeLimits.kt`: browser-owned timeout, output, viewport, and buffer limits; callers must use these constants instead of repeating policy literals. CAPTCHA/challenge pages receive no special detection or pause behavior. The active tab keeps its offscreen display and high priority between tool calls and while the host is backgrounded, because Android reclaims an inactive Gecko content process within seconds; a reclaimed tab is rebuilt and reloaded from persisted tab state on the next action. Offscreen surface slots stay capped: a busy session evicts an idle holder and otherwise runs without one.
- `browser/DomInspector.kt`: typed DOM script builders for escaped dynamic values.
- `browser/BrowserScriptAssets.kt` and `assets/browser-bridge/dom-inspector.js`: cached static DOM-inspector JavaScript template.
- `browser/BrowserResponseFormatter.kt`: compact browser JSON formatting for parent and sub-tool responses.
- `browser/BrowserActionCatalog.kt`: canonical model-exposed browser action inventory shared by tool schema and debug coverage.
- `browser/SafetyGuard.kt`: documents unrestricted local browser input policy; no credential/OTP gating.
- `src/debug/.../BrowserDebugActivity.kt`: debug-only ADB-launchable browser action harness using the production parent-tool path and an in-process test page.
- `src/debug/.../DebugActivity.kt`: debug-only ADB/UI harness for local streaming, tool routing/lifecycle, delegation, persisted-conversation integrity, background survival, and stress reports.
- `tools/LocalToolMapper.kt`: local tool normalization, capability display-name mapping, and UI metadata mapping.
- `tools/BrowserUseToolset.kt`: parent browser tool wrapper and legacy alias compatibility.
- `providers/`: local provider implementations and compatibility adapters.
- Background session status is projected by `service/AiSessionNotificationService`; the local service must persist turn state before streaming and never cancel a turn merely because the visible target changes. Activity, completed messages, approvals, and issues use separate notification channels. Completed message history keys by Chat conversation, Project owner, or Agent group; Agent sender names remain distinct within the shared group thread. Completed messages are suppressed only while their exact source conversation is resumed, while approval remains alerting. Notification inline replies start directly from the persisted target conversation and must not mutate visible ChatScreen selection. Any host-authorized tool confirmation uses `tools/LocalToolMapper.displayLabel`; notification actions appear only while that request is pending, remain turn-bound, and fail closed when stale. `delegate_agent` emits named start/completion progress; only one stable active named delegation requests Live Update promotion.
- `providers/LocalProviderFactory.kt` if present: provider registration and lookup for local mode.
- `services/` if added in this subtree: local background orchestration and execution helpers.

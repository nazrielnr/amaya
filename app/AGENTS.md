# Android App Instructions

## Scope
- This file applies to `app/` and its children.
- It covers the Android module, Gradle configuration, Compose UI, Hilt wiring, persistence, and runtime services.

## Android Rules
- Keep Kotlin, Compose, Hilt, and Gradle changes consistent with the current code style.
- Preserve the split between remote and local responsibilities.
- Keep UI, domain, data, implementation, and service code separated by package intent.
- Keep memory/skills domain rules in `domain/memory/` and `domain/skills/`; keep persistence implementations in `data/local/` + `data/repository/`; keep user-facing controls in `ui/screens/amaya/` and `ui/screens/selfimprovement/`.
- Keep bridge contract and runtime code in `domain/bridge/` and `impl/bridge/windows/`; keep bridge entry points in `ui/activities/bridge/WindowsBridgeChatActivity.kt` and `ui/screens/chat/bridge/`. Chat-side bridge surfaces (banner, approval card, welcome pill, connection setup sheet, session info sheet, agent control dialog, shared ViewModel) live in `ui/components/remote/` so they can be reused across local and bridge chat. Current bridge/remote behavior is incomplete and deferred; see `docs/local/audits/VISION-HOLD.md`.
- Keep browser automation logic inside `impl/local/browser/`, browser UI inside `ui/activities/browser/` and `ui/screens/browser/`, and the parent tool wrapper inside `tools/BrowserUseToolset.kt`.
- Do not move extension-specific logic into the Android module.

## Remote vs Local
- Remote Android work is handled by the deeper instruction files under `data/remote/` and `impl/ide/antigravity/`. Antigravity, Opencode, remote-session, and Windows Bridge logic remain explicitly incomplete/deferred; do not optimize their architecture without a renewed product requirement.
- Local Android work is handled by the deeper instruction files under `data/local/` and `impl/local/`.
- Bridge work is handled by the shared bridge contract and Android bridge runtime under `domain/bridge/` and `impl/bridge/windows/`. The current bridge direction is on hold; modify only for blockers or explicit requests.
- If a change touches both, update the shared Android file first, then the more specific subtree file.

## Localization
- User-visible UI text belongs in `src/main/res/values/strings.xml` and `values-zh-rCN/strings.xml`; use `stringResource`/`pluralStringResource` in Compose and locale-aware Context resource access outside composition. `ui/res/UiStrings.kt` has been replaced by Android resources.
- `src/main/res/xml/locales_config.xml` declares English and Simplified Chinese. Settings uses AppCompat per-app locales, with automatic persistence on Android 12 and earlier and platform language settings on Android 13+. An empty locale list follows the system.
- Preserve format argument indices and types, plural quantities, protocol strings, model identifiers, paths, commands, and user-authored content when maintaining translations.

## Testing and Runtime
- Keep JVM test behavior stable. If you touch local unit tests that call Android logging APIs, preserve the existing default-values setup used by the project.
- Respect existing foreground service, WorkManager, and persistence patterns.
- File-backed local repositories must serialize read-modify-write operations with a repository-local lock/mutex and must not report queued/ignored work as applied.

## File Tree
```text
app/
├─ AGENTS.md
├─ build.gradle.kts
├─ proguard-rules.pro
├─ schemas/
└─ src/
	└─ main/
		├─ AndroidManifest.xml
		├─ assets/
		├─ java/
		│	├─ com/amaya/intelligence/data/local/{dao,db,entity,files}/
		│	├─ com/amaya/intelligence/data/remote/{api,auth,mcp,provider,settings}/
		│	├─ com/amaya/intelligence/data/repository/{chat,context,memory,session,skills}/ # includes signed GitHub APK update verification/install
		│	├─ com/amaya/intelligence/domain/{ai,bridge,memory,models,security,skills}/
		│	├─ com/amaya/intelligence/impl/common/{conversation,mappers}/
		│	├─ com/amaya/intelligence/impl/bridge/windows/{pairing,service,services,tools,transport}/
		│	├─ com/amaya/intelligence/impl/ide/{antigravity,opencode}/
		│	├─ com/amaya/intelligence/impl/local/{browser,chat,tools}/
		│	├─ com/amaya/intelligence/service/
		│	├─ com/amaya/intelligence/tools/{file,memory,session}/
		│	└─ com/amaya/intelligence/ui/{activities,components,screens,theme,viewmodels}/
		└─ res/
```

## File Functions
- `AGENTS.md`: Android-wide development rules and scope routing.
- `build.gradle.kts`: Android module build config, dependencies, and test settings. Notification Live Update requires `compileSdk 36`, AndroidX Core `1.17.0+`, AGP `8.9.1+`, and Gradle `8.11.1+`.
- `proguard-rules.pro`: app-owned release R8 rules; currently an intentional placeholder while library consumer rules and optimized Android defaults apply.
- `schemas/`: exported Room schema snapshots.
- `src/main/AndroidManifest.xml`: app components, services, receivers, and permissions.
- `src/main/java/com/amaya/intelligence/data/remote/`: remote APIs, provider presets/model discovery, settings, and provider models.
- `src/main/java/com/amaya/intelligence/data/local/`: local storage and database layer, including Room, separate rendered-history/model-context conversation payloads, file-backed session/skill stores, and stable workspace-memory UUID metadata/remapping.
- `src/main/java/com/amaya/intelligence/domain/bridge/AGENTS.md`: shared bridge contract rules.
- `src/main/java/com/amaya/intelligence/impl/bridge/windows/AGENTS.md`: Android Windows bridge runtime rules.
- `src/main/java/com/amaya/intelligence/impl/ide/antigravity/`: remote IDE runtime and Antigravity integration.
- `src/main/java/com/amaya/intelligence/impl/local/`: local runtime, browser automation, services, and background behavior.
- `src/main/java/com/amaya/intelligence/impl/local/browser/`: GeckoView controller, runtime/WebExtension bridge, session manager, DOM inspection, and safety guard.
- `src/main/java/com/amaya/intelligence/tools/`: built-in local tools, memory/skill/recall tools, browser tool wrappers, and tool execution helpers including model-argument sanitization. `DocumentTextExtractor.kt` owns bounded document-to-text extraction; `ReadFileTool.kt` owns path validation, read orchestration, and result shaping. `DocumentWriter.kt` owns office-container serialization; `WriteFileTool.kt` owns path validation, atomic text writes, and syntax checks.
- `src/main/java/com/amaya/intelligence/service/`: app services, receivers, and workers.
- `src/main/java/com/amaya/intelligence/ui/activities/browser/`: fullscreen GeckoView browser operator activity.
- `src/main/java/com/amaya/intelligence/ui/`: Compose UI screens, activities, and theme; Antigravity-specific session/chat entry points use `activities/antigravity/`, `screens/antigravity/`, and `screens/chat/antigravity/`; shared chat chrome is in `screens/chat/shared/ChatScreenChrome.kt`, auto-follow ownership in `screens/chat/shared/ChatScrollController.kt`, drawer modes in `screens/chat/shared/ChatDrawerModes.kt`, reusable drawer rows/colors in `screens/chat/shared/ChatDrawerComponents.kt`, and composer UI helpers in `components/shared/ChatComposerComponents.kt`. Tool result dispatch stays in `components/shared/ToolResultPreview.kt`; category-specific bodies live in `components/shared/ToolResultBlocks.kt`.
- `src/main/java/com/amaya/intelligence/ui/screens/chat/bridge/`: bridge chat screen wiring and chat-specific bridge UI entry points.
- `src/main/java/com/amaya/intelligence/ui/screens/browser/`: browser operator screen, control dock, and shared pure presentation mappings in `BrowserPresentation.kt`.
- `src/main/java/com/amaya/intelligence/ui/components/shared/`: reusable shared UI components, including browser tool cards, conversation-event separators in `ConversationEventUi.kt`, and `ModelIcon.kt` model/provider leading icons.
- `src/main/java/com/amaya/intelligence/util/`: consolidated debug/error logging, network helpers, debug-only local stream profiling, and redacted tool/stream lifecycle traces.
- `src/debug/java/com/amaya/intelligence/ui/activities/debug/DebugActivity.kt`: debug-only ADB/UI harness for streaming, all-tool lifecycle, delegation (including provider-streamed multi-agent completion-race patterns), conversation integrity, background survival, and stress reports.
- `../scripts/audit-android-code.py`: reusable source-tree inventory; JSON/text output goes under `app/build/reports/`.
- `../scripts/map-android-filetree.py`: maps every Android text source/config/resource file to `docs/local/audits/ANDROID-FILETREE-LINE-MAP.md` with current line counts.

## Key Source Code
- `src/main/java/com/amaya/intelligence/domain/`: shared state, models, memory/skill domain logic, service contracts, and tool target policy used across remote/local flows.
- `src/main/java/com/amaya/intelligence/domain/bridge/`: bridge envelope, tool, approval, risk, audit, and session-state contract types.
- `src/main/java/com/amaya/intelligence/data/remote/api/`: provider clients such as Gemini, OpenAI, Anthropic, and settings managers. Includes `ReasoningContract.kt` — the universal thinking-effort contract (`ThinkingEffort`, `ReasoningCatalog`, `ReasoningRequestBuilder`, `ReasoningStreamParser`, `InlineThinkStripper`) shared by all providers; vendor additions are data-only via `RequestShape` + catalog.
- `src/main/java/com/amaya/intelligence/data/remote/mcp/`: MCP client and tool executor integration.
- `src/main/java/com/amaya/intelligence/data/repository/`: repository layer that orchestrates AI, files, signed GitHub APK updates, Chat/Project/Agent-group-owned conversations, persistent target-agent delegation turns, bounded owner-scoped recall, imported reference documents, active/superseded user/workspace memory, evidence-backed skills, pending proposals, terminal wildcard policy, and maintenance. Persona, user-memory proposals, and self-improvement auto-save modes are absent. Daily logs, global catch-all memory, model-owned importance, and memory archive/delete/restore are intentionally absent.
- `src/main/java/com/amaya/intelligence/data/local/db/`: Room database, entities, and DAOs. Conversation list queries must not select history/context JSON; fetch payloads by ID only.
- `src/main/java/com/amaya/intelligence/data/local/files/`: file-backed stores for local session recall and reusable skill documents.
- `src/main/java/com/amaya/intelligence/impl/common/`: shared implementation utilities, including provider/model-to-UI mapping, model-key parsing, `ConversationJsonCodec`, and concrete `ConversationPersistence` DAO mechanics.
- `src/main/java/com/amaya/intelligence/impl/bridge/windows/`: Android bridge client, controller, event handling, tool mapping, and session sync.
- `src/main/java/com/amaya/intelligence/impl/ide/antigravity/`: remote IDE provider, protocol, event handling, and streaming client.
- `src/main/java/com/amaya/intelligence/impl/local/`: local AI service, browser runtime, local runtime integrations, Agent history clearing, and model-summary context compression; `chat/` is reserved for extracted local turn/state/persistence collaborators.
- `src/main/java/com/amaya/intelligence/tools/`: file, standard Android shell, active-memory list/search/update, Agent-owned todo/reminder, readonly subagent, named intra-group delegation, browser tools, plus strict host-owned workspace resolution, Chat/Project/Agent capability enforcement, integer argument normalization, capability-operation dispatch, and untruncated tool/subagent reports.
- `src/main/java/com/amaya/intelligence/ui/`: chat, settings, browser, bridge, Manage Models, and remote/local UI entry points. Settings has Global/Project/Agent tabs: Global owns About You and terminal policy; Project and Agent tabs render their lists directly. Context/Recall and Privacy/Safety settings screens are absent. Local Project and Agent management use card lists with stable IDs → dedicated detail/config screens; project/group identity edits use standard modal sheets with per-field save actions; Agent identity fields use modal sheets and Agent config auto-saves. Project/group/Agent reference rows open dedicated reference inventory screens. Agent defaults can select active Manage Models entries or inherit the global model; bottom-right `+` actions create projects, groups, and group members. Agent configuration owns per-agent references, private Agent Memory, and independent tool switches; group references remain shared. Each Agent owns one persistent conversation; Agent UI must not expose session lists or New Chat actions. Agent IDs exposed to models/mentions are group-local and restart at 1 per group; Room primary keys remain internal. Local chat can keep multiple conversation turns running concurrently; switching targets only changes the visible projection. The drawer marks active sessions with progress indicators. Notifications use four independent channels and one hierarchy: session title, then event/detail. Completed responses use bounded native `MessagingStyle` thread history: Chat threads key by conversation, Project threads by project, Agent threads by group; sender is `AI` for Chat/Project and the Agent name for Agent sessions, while inline replies append as `You` and route to the latest source conversation. Chat titles use the session title, Project titles the project name, Agent titles the group name. The monochrome small icon is always `ic_notification` (app identity); conversation identity comes from neutral Material-style dynamic shortcuts (`ic_shortcut_chat`, `ic_shortcut_group`, `ic_shortcut_agent`). Every visible `MessagingStyle` participant has a lead icon: AI uses the robot `ic_lead_ai`; named Agents and `You` use `ic_lead_person`. Approval and issue notifications also use `MessagingStyle` with their own channels/categories and no `setLargeIcon`; approvals remain isolated by bound `turnId + toolCallId`. Running activity stays silent and uses `ProgressStyle` only for real delegation progress. `AI activity` is silent, unbadged, grouped foreground progress; `Messages` is high-importance completion with sound, heads-up, inline Reply, and one unwrapped `MessagingStyle` notification per conversation shortcut; no `InboxStyle` summary may wrap these threads because it hides shortcut and participant icons; `Approvals` is high-importance standalone sound/heads-up with turn-bound Approve/Decline, while high/root exposes Review instead; `Session issues` is default-importance failure/interruption output. A completed message is suppressed while its exact conversation is resumed. Approval remains visible and alerting because the turn is blocked. Tools that do not request approval never get approval UI. Live Update promotion is reserved for one stable active named-Agent delegation, uses AndroidX ProgressStyle plus official promoted-ongoing APIs, and stops re-requesting promotion after dismissal for that conversation. Background Reply starts the target turn without changing visible ChatScreen state. User prompts, reasoning text, raw tool arguments, secrets, and absolute paths never enter notification content. The chat input bar exposes a reasoning-effort bulb (`Icons.Default.Psychology`, `ChatInput.kt`) driving `ThinkingEffort` (NONE/LOW/MEDIUM/HIGH); all reasoning (provider `reasoning_content`/ThinkingDelta + inline `<think>` tags) renders through one dedicated `ThinkingCard` (`ui/components/shared/ThinkingCard.kt`), invoked at the top of the assistant bubble via `MessageThinkingBlock` so it shows in both the streaming and completed branches. `ThinkingCard` mirrors the `ToolCallCard` block UX (reuses `ToolCallMotion`/`ToolCallAnimatedSection`/`ToolLeadIconPill` with the standard `MaterialTheme.colorScheme.primary` lead tint), shows a "Thinking" label while streaming, and shows "Thought for {duration}" once done. Block-level while streaming is tinted with `iosBlue` (ToolCallCard RUNNING convention) so reasoning reads as a live timeline event. The auto-collapse rule uses an explicit two-state lifecycle enum (`ThinkingLifecycle.PROCESSING` → `ThinkingLifecycle.DONE`): expanded by default while `PROCESSING`, collapsed by default while `DONE`, with a `LaunchedEffect(lifecycle)` that forces collapse only on the `PROCESSING → DONE` transition (so transient isStreaming blips during interleaved reasoning+tool flows cannot collapse prematurely). The duration is captured on turn completion into `UiMessage.thinkingDurationMs` and persisted alongside the message JSON in `LocalIntelligenceService`, `OpencodeIntelligenceService`, the Antigravity `StreamingEventHandler`, and the Windows bridge so the label survives conversation reloads without recomputing from `startedAt`/`completedAt`. `MessageBubble.kt` no longer wraps thinking in `ToolCallCard`.
- `src/main/java/com/amaya/intelligence/ui/screens/models/`: provider connection inventory, model visibility, per-model context/input/output and capability configuration, setup, credential replacement, and active model selection UI.

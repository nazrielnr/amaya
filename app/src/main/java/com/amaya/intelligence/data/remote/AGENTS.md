# Android Remote Data Instructions

## Scope
- This file applies to `app/src/main/java/com/amaya/intelligence/data/remote/` and its children.

## Status

Remote provider logic is **incomplete and deferred** while product direction changes. See `docs/local/audits/VISION-HOLD.md`. Keep only shared/core remote API maintenance active unless explicitly requested.

## Remote Data Rules
- Keep API clients, request/response models, and remote settings storage in this subtree.
- Treat this layer as the boundary between Android and external services.
- Keep serialization, auth/token handling, and provider-specific mapping here rather than in UI or local runtime code.
- Avoid adding file-system, shell, or other device-local behavior in this subtree.
- Before changing provider contracts or payload shapes, crosscheck the current workspace diff and recent commits for the affected remote surface.
- Reasoning effort is controlled by a single global `ThinkingEffort` (NONE/LOW/MEDIUM/HIGH) from the chat bulb. It flows into `ChatRequest.effort` + `providerId`, resolved per model via `ReasoningCatalog` (prefix match) with a per-provider fallback in `ReasoningProfileRegistry`. Never add per-model settings UI — capability is data-driven from `ReasoningCatalog`.
- Vendors that share the OpenAI-compatible wire format (GLM/Kimi/MiniMax) reuse `OpenAiProvider`; adding one is a data-only diff (catalog prefix + `RequestShape` + optional `ProviderRegistry` entry). Do not create a new `AiProvider` class per vendor.
- Response reasoning is parsed universally by `ReasoningStreamParser` (probes `reasoning_content`, `reasoning_details[]`, `reasoning`, `thinking.reasoning`, Responses reasoning items) plus stateful `InlineThinkStripper` for `<think>` tags leaked into content. New vendors that emit reasoning under a new field name extend the parser probe chain, not the provider class.

## Coordination
- Coordinate remote transport and model mapping changes with `impl/ide/antigravity/` when the runtime flow depends on Antigravity-specific behavior.
- Keep provider adapters explicit so shared app code stays agnostic.
- If a change adds or renames remote-facing files or folders, update this AGENTS file and any parent Android instructions in the same patch.

## File Tree
```text
data/remote/
├─ AGENTS.md
├─ api/
├─ auth/codex/                   # placeholder for auth extraction
├─ mcp/
├─ provider/{anthropic,gemini,openai}/ # provider split placeholders
└─ settings/                     # settings extraction placeholder
```

## File Functions
- `AGENTS.md`: rules for remote data and service integration.
- `api/`: current provider request/response models, settings, and auth-related code during staged migration.
- `auth/codex/`: reserved placeholder for Codex auth extraction.
- `mcp/`: MCP client/executor code that talks to external services.
- `provider/`: provider protocol extraction; `provider/openai/OpenAiWireModels.kt` owns OpenAI wire DTOs.
- `settings/`: reserved placeholder for settings and credential-store extraction.

## Key Source Code
- `api/AiSettings.kt`: provider connections, active model selection, credential access, and settings persistence.
- `api/ProviderRegistry.kt`: stable provider presets and adapter mapping; no model catalog or inferred metadata.
- `api/ProviderModelService.kt`: provider-owned model discovery, GitHub catalog mapping, and provider URL validation. Image input follows declared `input_modalities`; absent metadata permits a provider attempt rather than asserting lack of support. App-owned configuration failures carry Android string resource IDs for locale-aware UI rendering; remote server error details are preserved.
- `api/GeminiProvider.kt`: Gemini request/response models and streaming parsing.
- `api/OpenAiProvider.kt`: OpenAI Responses, compatible Chat Completions, and subscription transport orchestration; DTOs live under `provider/openai/`.
- `api/OpenAiRequestCodec.kt`: testable OpenAI request-shape helpers.
- `api/OpenAiStreamProtocol.kt`: terminal-state and indexed tool-call stream guards.
- `api/ResponseBodyLimits.kt`: bounded remote response-body reads.
- `api/AnthropicProvider.kt`: Anthropic request/response models and streaming parsing.
- `api/McpModels.kt`: MCP-related model definitions and payload mapping.
- `api/AiProvider.kt`: common remote provider contracts.
- `api/ReasoningContract.kt`: `ThinkingEffort`, `RequestShape`, per-model `ReasoningCatalog`, `ReasoningRequestBuilder` (effort→attachment), `ReasoningStreamParser` (universal field probe), and `InlineThinkStripper` (stateful `<think>` tag removal).
- `mcp/McpClientManager.kt`: lifecycle for remote MCP connectivity.
- `mcp/McpToolExecutor.kt`: execution bridge for MCP tools.
- `repository/AiRepository.kt`: repository orchestration for remote-backed chat flows.

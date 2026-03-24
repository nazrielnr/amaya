# Android Remote Data Instructions

## Scope
- This file applies to `app/src/main/java/com/amaya/intelligence/data/remote/` and its children.

## Remote Data Rules
- Keep API clients, request/response models, and remote settings storage in this subtree.
- Treat this layer as the boundary between Android and external services.
- Keep serialization, auth/token handling, and provider-specific mapping here rather than in UI or local runtime code.
- Avoid adding file-system, shell, or other device-local behavior in this subtree.

## Coordination
- Coordinate remote transport and model mapping changes with `impl/ide/antigravity/` when the runtime flow depends on Antigravity-specific behavior.
- Keep provider adapters explicit so shared app code stays agnostic.

## File Tree
```text
data/remote/
├─ AGENTS.md
├─ api/
├─ mcp/
└─ repository/
```

## File Functions
- `AGENTS.md`: rules for remote data and service integration.
- `api/`: provider request/response models, settings, and auth-related code.
- `mcp/`: MCP client/executor code that talks to external services.
- `repository/`: repository implementations that coordinate remote state and persistence.

## Key Source Code
- `api/AiSettings.kt`: agent profile, credential, and settings persistence.
- `api/GeminiProvider.kt`: Gemini request/response models and streaming parsing.
- `api/OpenAiProvider.kt`: OpenAI request/response models and tool-call parsing.
- `api/AnthropicProvider.kt`: Anthropic request/response models and streaming parsing.
- `api/McpModels.kt`: MCP-related model definitions and payload mapping.
- `api/AiProvider.kt`: common remote provider contracts.
- `mcp/McpClientManager.kt`: lifecycle for remote MCP connectivity.
- `mcp/McpToolExecutor.kt`: execution bridge for MCP tools.
- `repository/AiRepository.kt`: repository orchestration for remote-backed chat flows.
- `repository/PersonaRepository.kt`: AGENTS template storage and persona-related defaults.

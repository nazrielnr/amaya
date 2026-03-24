# Android Local Runtime Instructions

## Scope
- This file applies to `app/src/main/java/com/amaya/intelligence/impl/local/` and its children.

## Local Runtime Rules
- Keep device-local execution, persistence, and runtime services in this subtree.
- This is the correct place for local tool orchestration, background services, and non-remote behaviors.
- Keep remote API assumptions out of this layer.
- Prefer Android-native patterns for services, background work, and local state.

## Coordination
- Coordinate with `data/local/` for storage-backed behavior and with `tools/` and `service/` for execution/runtime behavior.
- If a change needs remote integration, move the remote-specific part to the remote instruction subtree instead of broadening this file.

## File Tree
```text
impl/local/
├─ AGENTS.md
├─ LocalIntelligenceService.kt
├─ tools/
└─ providers/
```

## File Functions
- `AGENTS.md`: rules for local runtime and services.
- `LocalIntelligenceService.kt`: local AI orchestration and persistence-backed chat flow.
- `tools/`: local tool mapping and execution helpers.
- `providers/`: local provider adapters and implementation-specific helpers.

## Key Source Code
- `LocalIntelligenceService.kt`: local conversation flow, persistence, and repository integration.
- `tools/LocalToolMapper.kt`: local tool normalization and UI metadata mapping.
- `providers/`: local provider implementations and compatibility adapters.
- `providers/LocalProviderFactory.kt` if present: provider registration and lookup for local mode.
- `services/` if added in this subtree: local background orchestration and execution helpers.

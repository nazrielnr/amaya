# Repository Instructions

## Scope
- This file applies to the whole monorepo.
- There are two major areas in this repo:
  - `amaya-remote-extension/` for the VS Code extension.
  - `app/` for the Android app.
- Read the nearest AGENTS file before making changes in a subdirectory.

## General Rules
- Keep changes minimal and local to the requested area.
- Do not modify unrelated modules.
- Prefer existing patterns over introducing new abstractions.
- If a task spans extension and Android, inspect both module-level instruction files before editing.
- When implementing code, prefer standard OOP structure and align with the existing architecture.
- Do not introduce redundancy, duplicate logic, or dead code.
- Before building a feature or adding new code paths, inspect the codebase structure first and choose the most natural placement for the change.
- If the right location is unclear, trace the nearest related modules, controllers, services, or mappers before editing.

## Area Routing
- Extension work belongs under `amaya-remote-extension/AGENTS.md`.
- Android-wide work belongs under `app/AGENTS.md`.
- Remote Android implementation details belong to the deeper Android remote instruction files.
- Local Android implementation details belong to the deeper Android local instruction files.

## File Tree
```text
amaya/
├─ AGENTS.md
├─ amaya-remote-extension/
└─ app/
```

## File Functions
- `AGENTS.md`: repo-wide coordination and routing rules.
- `amaya-remote-extension/AGENTS.md`: extension-specific rules for TypeScript, controllers, IDE abstraction, and tests.
- `app/AGENTS.md`: Android-wide rules for Compose, Gradle, Hilt, persistence, and runtime services.
- `app/src/main/java/com/amaya/intelligence/data/remote/AGENTS.md`: Android remote API, settings, and model mapping guidance.
- `app/src/main/java/com/amaya/intelligence/data/local/AGENTS.md`: Android local storage and database guidance.
- `app/src/main/java/com/amaya/intelligence/impl/ide/antigravity/AGENTS.md`: Antigravity remote runtime guidance.
- `app/src/main/java/com/amaya/intelligence/impl/local/AGENTS.md`: local Android runtime and service guidance.

## Key Source Code
- `amaya-remote-extension/src/extension.ts`: extension bootstrap and command registration.
- `amaya-remote-extension/src/controllers/`: message handling, lifecycle, quota, workspace, and stream orchestration.
- `amaya-remote-extension/src/ide/`: provider-neutral IDE contracts and Antigravity-specific implementation.
- `amaya-remote-extension/src/connectivity/`: WebSocket and transport wiring.
- `amaya-remote-extension/test/`: raw captures, debug harnesses, and reverse-engineering scripts.
- `app/src/main/java/com/amaya/intelligence/domain/`: shared models, interfaces, and app-level contracts.
- `app/src/main/java/com/amaya/intelligence/data/remote/`: remote API clients, settings, and transport-facing models.
- `app/src/main/java/com/amaya/intelligence/data/local/`: local entities, DAOs, and storage-backed state.
- `app/src/main/java/com/amaya/intelligence/impl/ide/antigravity/`: Antigravity provider, protocol, client, and event mapping.
- `app/src/main/java/com/amaya/intelligence/impl/local/`: local runtime, service, and tool execution flow.

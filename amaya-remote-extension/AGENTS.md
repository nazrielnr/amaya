# VS Code Extension Instructions

## Scope
- This file applies to `amaya-remote-extension/` and its children.
- It covers the TypeScript extension, controller flow, IDE abstraction, connectivity, tests, and reverse-engineering documentation.

## Extension Rules
- Treat the extension as the orchestration layer between Android and Antigravity.
- Keep message routing, WebSocket handling, and controller logic aligned with the existing controller split.
- Preserve provider-neutral interfaces in `src/ide/` when changing Antigravity-specific code.
- Keep test utilities and reverse-engineering scripts confined to the extension area.
- Avoid introducing Android assumptions into the extension code.

## Editing Guidance
- Prefer small changes in `src/controllers/`, `src/connectivity/`, `src/ide/`, `src/interfaces/`, `src/types/`, and `src/utils/`.
- If changing the Antigravity client or discovery logic, keep the boundaries explicit so provider-neutral code stays isolated from Antigravity-specific details.
- Follow the existing `package.json` scripts and TypeScript conventions in this module.

## File Tree
```text
amaya-remote-extension/
├─ AGENTS.md
├─ package.json
├─ MODEL_DISCOVERY.md
├─ REVERSE-ENGINEERING.md
├─ src/
└─ test/
```

## File Functions
- `AGENTS.md`: module rules for the extension area.
- `package.json`: extension metadata, activation, commands, and scripts.
- `docs/MODEL_DISCOVERY.md`: model mapping and discovery notes.
- `docs/REVERSE-ENGINEERING.md`: Antigravity API discovery and verification notes.
- `src/extension.ts`: extension entrypoint and bootstrap.
- `src/controllers/`: message flow, lifecycle, workspace, and quota logic.
- `src/connectivity/`: WebSocket and connection plumbing.
- `src/ide/`: provider-neutral IDE contracts and Antigravity/stub implementations.
- `src/interfaces/`: shared interfaces and IDE types.
- `src/types/`: extension-level state and payload types.
- `src/utils/`: helper utilities used across the extension.
- `test/`: raw captures, scripts, and endpoint/debug tooling.

## Key Source Code
- `src/controllers/MessageHandler.ts`: main message router between Android payloads and IDE calls.
- `src/controllers/MessageFlowController.ts`: turns messages into user-visible flows and streaming behavior.
- `src/controllers/ConversationStateController.ts`: conversation state, active session, and mode tracking.
- `src/controllers/ModelQuotaGuard.ts`: quota-aware model gating and selection checks.
- `src/connectivity/ConnectivityManager.ts`: extension-side connection lifecycle and transport setup.
- `src/ide/IDEBootstrap.ts`: provider-neutral bootstrap and IDE factory wiring.
- `src/ide/IDEFactory.ts`: provider selection and IDE implementation registration.
- `src/ide/antigravity/AntigravityClient.ts`: Antigravity-specific client facade and model/session APIs.
- `src/ide/antigravity/discovery/AntigravityDiscovery.ts`: local IDE discovery and authentication heuristics.
- `src/ide/antigravity/controllers/`: Antigravity stream, tool, and state controllers.
- `src/ide/antigravity/core/`: protocol constants and transport-level helpers.
- `test/*.js`: endpoint probes, raw capture scripts, and regression checks.

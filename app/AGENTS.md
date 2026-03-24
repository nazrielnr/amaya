# Android App Instructions

## Scope
- This file applies to `app/` and its children.
- It covers the Android module, Gradle configuration, Compose UI, Hilt wiring, persistence, and runtime services.

## Android Rules
- Keep Kotlin, Compose, Hilt, and Gradle changes consistent with the current code style.
- Preserve the split between remote and local responsibilities.
- Keep UI, domain, data, implementation, and service code separated by package intent.
- Do not move extension-specific logic into the Android module.

## Remote vs Local
- Remote Android work is handled by the deeper instruction files under `data/remote/` and `impl/ide/antigravity/`.
- Local Android work is handled by the deeper instruction files under `data/local/` and `impl/local/`.
- If a change touches both, update the shared Android file first, then the more specific subtree file.

## Testing and Runtime
- Keep JVM test behavior stable. If you touch local unit tests that call Android logging APIs, preserve the existing default-values setup used by the project.
- Respect existing foreground service, WorkManager, and persistence patterns.

## File Tree
```text
app/
├─ AGENTS.md
├─ build.gradle.kts
├─ schemas/
└─ src/
	└─ main/
		├─ AndroidManifest.xml
		├─ assets/
		├─ java/
		└─ res/
```

## File Functions
- `AGENTS.md`: Android-wide development rules and scope routing.
- `build.gradle.kts`: Android module build config, dependencies, and test settings.
- `schemas/`: exported Room schema snapshots.
- `src/main/AndroidManifest.xml`: app components, services, receivers, and permissions.
- `src/main/java/com/amaya/intelligence/data/remote/`: remote APIs, settings, and provider models.
- `src/main/java/com/amaya/intelligence/data/local/`: local storage and database layer.
- `src/main/java/com/amaya/intelligence/impl/ide/antigravity/`: remote IDE runtime and Antigravity integration.
- `src/main/java/com/amaya/intelligence/impl/local/`: local runtime, services, and background behavior.
- `src/main/java/com/amaya/intelligence/tools/`: built-in local tools and tool execution helpers.
- `src/main/java/com/amaya/intelligence/service/`: app services, receivers, and workers.
- `src/main/java/com/amaya/intelligence/ui/`: Compose UI screens, activities, and theme.

## Key Source Code
- `src/main/java/com/amaya/intelligence/domain/`: shared state, models, and service contracts used across remote/local flows.
- `src/main/java/com/amaya/intelligence/data/remote/api/`: provider clients such as Gemini, OpenAI, Anthropic, and settings managers.
- `src/main/java/com/amaya/intelligence/data/remote/mcp/`: MCP client and tool executor integration.
- `src/main/java/com/amaya/intelligence/data/repository/`: repository layer that orchestrates AI, personas, files, and conversations.
- `src/main/java/com/amaya/intelligence/data/local/db/`: Room database, entities, and DAOs.
- `src/main/java/com/amaya/intelligence/impl/common/`: mappers and shared implementation utilities.
- `src/main/java/com/amaya/intelligence/impl/ide/antigravity/`: remote IDE provider, protocol, event handling, and streaming client.
- `src/main/java/com/amaya/intelligence/impl/local/`: local AI service and local runtime integrations.
- `src/main/java/com/amaya/intelligence/tools/`: file, shell, memory, todo, reminder, and subagent tools.
- `src/main/java/com/amaya/intelligence/ui/`: chat, settings, and remote/local UI entry points.

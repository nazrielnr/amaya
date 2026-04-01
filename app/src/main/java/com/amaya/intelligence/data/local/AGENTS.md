# Android Local Data Instructions

## Scope
- This file applies to `app/src/main/java/com/amaya/intelligence/data/local/` and its children.

## Local Data Rules
- Keep local persistence, database entities, DAOs, and cached state in this subtree.
- Treat this layer as device-local storage only.
- Keep remote API clients, network transport, and provider logic out of this subtree.
- Prefer the existing Room and storage patterns used by the app.

## Coordination
- Coordinate with `impl/local/` for runtime behavior that consumes local storage.
- If a change needs a remote dependency, move that part to the remote instruction subtree instead of broadening local storage responsibilities.

## File Tree
```text
data/local/
├─ AGENTS.md
├─ dao/
├─ entity/
└─ db/
	├─ migrations/
	└─ AppDatabase.kt
```

## File Functions
- `AGENTS.md`: rules for local persistence and storage.
- `entity/`: Room entities for projects, files, metadata, conversations, and cron jobs.
- `dao/`: Room DAO interfaces for data access.
- `db/AppDatabase.kt`: Room database definition and wiring.
- `db/migrations/`: Database migration scripts.

## Key Source Code
- `entity/ProjectEntity.kt`: persisted project metadata.
- `entity/FileEntity.kt`: local file index entries.
- `entity/FileFtsEntity.kt`: full-text-search support for local files.
- `entity/FileMetadataEntity.kt`: detailed file information.
- `entity/ConversationEntity.kt`: stored conversation records.
- `entity/CronJobEntity.kt`: scheduled local job records.
- `dao/ProjectDao.kt`: project persistence access.
- `dao/FileDao.kt`: file index and FTS access.
- `dao/ConversationDao.kt`: conversation persistence access.
- `dao/CronJobDao.kt`: cron job persistence access.
- `db/AppDatabase.kt`: database configuration and migration wiring.

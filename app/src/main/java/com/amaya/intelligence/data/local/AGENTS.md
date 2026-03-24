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
└─ db/
	├─ dao/
	├─ entity/
	└─ AppDatabase.kt
```

## File Functions
- `AGENTS.md`: rules for local persistence and storage.
- `db/entity/`: Room entities for conversations, files, jobs, and related cached data.
- `db/dao/`: Room DAO interfaces for reading and writing local data.
- `db/AppDatabase.kt`: Room database definition and entity/DAO wiring.

## Key Source Code
- `db/entity/ConversationEntity.kt`: stored conversation records.
- `db/entity/FileEntity.kt`: local file index entries.
- `db/entity/FileFtsEntity.kt`: full-text-search support for local files.
- `db/entity/ProjectEntity.kt`: persisted project metadata.
- `db/entity/CronJobEntity.kt`: scheduled local job records.
- `db/dao/ConversationDao.kt`: conversation persistence access.
- `db/dao/FileDao.kt`: file index persistence access.
- `db/dao/CronJobDao.kt`: cron job persistence access.
- `db/AppDatabase.kt`: database configuration and migration wiring.

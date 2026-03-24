# Language Server Tests

This area is for direct Antigravity language server work.

## Subfolders
- `auth-discovery/`: credential discovery, model discovery, and API bootstrap helpers.
- `grpc/`: direct `LanguageServerService` endpoint calls and response checks.
- `capture-raw/`: raw CORTEX/cascade capture and transcript dumps.
- `analysis/`: offline parsing, extraction, and trajectory analysis.

## Output Rule
- Put JSON outputs beside the test family that generated them.
- Capture and analysis outputs stay in their own subfolder so the root stays clean.

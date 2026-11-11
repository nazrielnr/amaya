# Data Repository Instructions

## Scope
Applies to `data/repository/`.

## Rules
- Keep repository facades thin.
- Keep agent-loop, validation, compression, and title-generation collaborators package-local and concrete.
- Preserve provider error mapping, tool argument validation, context budgeting, memory ownership, and atomic persistence behavior.

## Key Files
- `AiRepository.kt`: injected facade, provider selection, tool catalog, public chat/compression/title APIs.
- `AiAgentLoop.kt`: bounded streaming agent loop and tool execution pipeline.
- `AiConversationCompression.kt`: manual compression and automatic task-ledger updates.
- `AiArgumentValidation.kt`: advertised-tool argument and JSON Schema validation.
- `AiTitleGenerator.kt`: bounded title generation with deterministic fallback.
- `ReferenceDocumentRepository.kt`: imported reference storage and manual notes; preserve Unicode display names while excluding path separators and control characters.

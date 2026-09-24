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
- `RecommendationRepository.kt`: evidence-verified implementation recommendation lifecycle (suggested → accepted → in_progress → verified → completed; JSONL store; `verify` is evidence-gated by the recommendation's verification rule). On VERIFIED the evidence line is linked back as provenance to `relatedMemoryIds` via `MemoryRepository.appendEvidence`.
- `SkillUsageLogRepository.kt`: scheme §1.4 `skill_usage_log` — per-session skill outcomes buffered in memory (never written one-by-one) and flushed as a single batch append at end-of-session housekeeping in `SelfImprovementPipeline`.

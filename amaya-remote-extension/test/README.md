# Test Layout

This folder is split into two main areas:

- `language-server/` for direct Antigravity language server calls, captures, and offline analysis.
- `extension-server/` for WebSocket-based extension server tests and mock server flows.

## Tree
```text
test/
├─ README.md
├─ language-server/
└─ extension-server/
```

## Output Rule
- Keep output JSON files inside the same category folder as the script that produced them.
- If a script produces a diagnostic dump, store it next to that test category instead of leaving it at the test root.

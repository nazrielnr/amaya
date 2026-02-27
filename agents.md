# Amaya Intelligence - AI Coding Agent

> **A powerful AI-powered coding assistant that runs natively on Android, bringing the capabilities of modern AI coding agents directly to your mobile device.**

[![Android](https://img.shields.io/badge/Android-15+-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-purple.svg)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-1.7-blue.svg)](https://developer.android.com/jetpack/compose)

---

## 📋 Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Supported AI Providers](#-supported-ai-providers)
- [Available Tools](#-available-tools)
- [Security Architecture](#-security-architecture)
- [System Prompt](#-system-prompt)
- [Tool Definitions](#-tool-definitions)
- [Agentic Loop](#-agentic-loop)
- [Project Structure](#-project-structure)
- [Database Schema](#-database-schema)
- [API Integration](#-api-integration)
- [MCP Servers](#-mcp-servers)
- [Configuration](#️-configuration)
- [Theme & Design](#-theme--design)
- [Permissions](#-permissions)
- [Building & Running](#-building--running)
- [Example Conversations](#-example-conversations)
- [Troubleshooting](#-troubleshooting)
- [Roadmap](#-roadmap)
- [Contributing](#-contributing)
- [License](#-license)

---

## Overview

Amaya Intelligence is an **autonomous AI coding agent** that can:
- Read, write, and modify source code files
- Navigate project structures
- Execute shell commands (git, build tools, etc.)
- Understand project context through file indexing
- Stream responses in real-time
- Request confirmation for dangerous operations
- Connect to external MCP servers via HTTP to extend tool capabilities

Unlike simple code completion tools, Amaya Intelligence operates as a **full agent** that can:
1. Understand your request
2. Plan the necessary steps
3. Execute tools to accomplish the task
4. Verify the results
5. Iterate if needed

---

## Features

### ✅ Core Features
- **Multi-provider AI support** - Use Claude, GPT-4, Gemini, or local models
- **Real-time streaming** - See responses as they're generated
- **Tool execution** - AI can read/write files, run commands
- **Security guardrails** - Dangerous operations require confirmation
- **Rich Markdown Rendering** - Support for bold/italic in headings, clickable links, and table cell formatting
- **Advanced Reminder System** - AI-powered reminders with background execution via WorkManager
- **Project Indexing** - Fast file search with FTS4
- **Security Confirmation** - Explicit approval for sensitive operations
- **MCP Servers** - HTTP-based Model Context Protocol support with editable JSON config and auto-refresh
- **Live TodoBar** - AI communicates plan and progress via a collapsible shimmer bar above chat input
- **Parallel Subagents** - AI can spawn up to 4 independent subagents in parallel via `invoke_subagents` tool

### 🔮 Planned Features
- Project browser for folder selection
- Syntax highlighting for code blocks
- File diff viewer
- Voice input support
- Foreground service for long operations

---

## 🤖 Supported AI Providers

### Anthropic (Claude)

| Model | Context | Best For |
|-------|---------|----------|
| `claude-sonnet-4-20250514` | 200K | Best balance of speed and intelligence |
| `claude-3-5-sonnet-20241022` | 200K | Fast, great for coding |
| `claude-3-opus-20240229` | 200K | Most capable, complex reasoning |
| `claude-3-haiku-20240307` | 200K | Fastest, simple tasks |

**API Endpoint:** `https://api.anthropic.com/v1/messages`

**Features:**
- Native tool use support
- Extended thinking (Claude 3.5+)
- Streaming with Server-Sent Events

---

### OpenAI (GPT)

| Model | Context | Best For |
|-------|---------|----------|
| `gpt-4o` | 128K | Best overall, multimodal |
| `gpt-4o-mini` | 128K | Fast and cheap |
| `gpt-4-turbo` | 128K | Powerful, JSON mode |
| `gpt-3.5-turbo` | 16K | Budget option |

**API Endpoint:** `https://api.openai.com/v1/chat/completions`

**Features:**
- Function calling
- JSON mode
- Streaming responses
- Compatible with Azure OpenAI

---

### Google Gemini

| Model | Context | Best For |
|-------|---------|----------|
| `gemini-1.5-pro` | 1M | Longest context, complex projects |
| `gemini-1.5-flash` | 1M | Fast, efficient |
| `gemini-2.0-flash` | 1M | Latest and fastest |

**API Endpoint:** `https://generativelanguage.googleapis.com/v1beta/models`

**Features:**
- Function declarations
- Massive context window
- Multimodal support
- Streaming with newline-delimited JSON

---

### Local Models (Ollama/LM Studio)

Any OpenAI-compatible API can be used:

```
Base URL: http://localhost:11434/v1  (Ollama)
Base URL: http://localhost:1234/v1   (LM Studio)
```

**Recommended Models:**
- `codellama:34b`
- `deepseek-coder:33b`
- `qwen2.5-coder:32b`
- `mistral-large`

---

## 🛠️ Available Tools

### 1. read_file

Read the content of a text file with safety limits.

```json
{
  "name": "read_file",
  "description": "Read the content of a text file",
  "parameters": {
    "path": {
      "type": "string",
      "description": "Absolute path to the file",
      "required": true
    },
    "max_size": {
      "type": "integer",
      "description": "Maximum file size in bytes (default: 1MB, max: 10MB)",
      "required": false
    },
    "start_line": {
      "type": "integer",
      "description": "Start reading from this line (1-indexed)",
      "required": false
    },
    "end_line": {
      "type": "integer",
      "description": "Stop reading at this line (inclusive)",
      "required": false
    },
    "encoding": {
      "type": "string",
      "description": "Character encoding (default: UTF-8)",
      "required": false
    }
  }
}
```

**Safety Features:**
- Size limit prevents OOM on large files
- Binary file detection
- Charset detection with fallback
- Line range support for large files

---

### 2. write_file

Write content to a file with atomic operations and automatic backup.

```json
{
  "name": "write_file",
  "description": "Write content to a file with safety features",
  "parameters": {
    "path": {
      "type": "string",
      "description": "Absolute path to the file",
      "required": true
    },
    "content": {
      "type": "string",
      "description": "Content to write",
      "required": true
    },
    "create_backup": {
      "type": "boolean",
      "description": "Create backup before write (default: true)",
      "required": false
    },
    "validate_syntax": {
      "type": "boolean",
      "description": "Validate code syntax (default: true for code files)",
      "required": false
    },
    "create_dirs": {
      "type": "boolean",
      "description": "Create parent directories if needed (default: true)",
      "required": false
    },
    "append": {
      "type": "boolean",
      "description": "Append instead of overwrite (default: false)",
      "required": false
    }
  }
}
```

**Safety Features:**
- **Atomic writes**: Write to temp file, then atomic rename
- **Automatic backup**: `filename.bak.{timestamp}`
- **Syntax validation**: Bracket matching, quote matching
- **Auto rollback**: Restore from backup on failure
- **Backup retention**: Keeps last 5 backups per file

---

### 3. list_files

List directory contents with filtering.

```json
{
  "name": "list_files",
  "description": "List files and directories",
  "parameters": {
    "path": {
      "type": "string",
      "description": "Directory path to list",
      "required": true
    },
    "recursive": {
      "type": "boolean",
      "description": "List subdirectories recursively (default: false)",
      "required": false
    },
    "max_depth": {
      "type": "integer",
      "description": "Maximum depth for recursive listing (default: 3)",
      "required": false
    },
    "pattern": {
      "type": "string",
      "description": "Filter by glob pattern (e.g., '*.kt')",
      "required": false
    },
    "include_hidden": {
      "type": "boolean",
      "description": "Include hidden files (default: false)",
      "required": false
    }
  }
}
```

---

### 4. create_directory

Create a directory with recursive creation.

```json
{
  "name": "create_directory",
  "description": "Create a directory and any necessary parent directories",
  "parameters": {
    "path": {
      "type": "string",
      "description": "Path of directory to create",
      "required": true
    }
  }
}
```

---

### 5. delete_file

Delete a file safely by moving to trash.

```json
{
  "name": "delete_file",
  "description": "Delete a file by moving to trash (recoverable)",
  "parameters": {
    "path": {
      "type": "string",
      "description": "Path of file to delete",
      "required": true
    },
    "permanent": {
      "type": "boolean",
      "description": "Permanently delete (not recoverable, default: false)",
      "required": false
    }
  }
}
```

**Safety Features:**
- Default moves to `.trash` folder (recoverable)
- Permanent deletion requires explicit flag
- Requires confirmation for protected paths

---

### 6. run_shell

Execute shell commands with security validation.

```json
{
  "name": "run_shell",
  "description": "Execute a shell command",
  "parameters": {
    "command": {
      "type": "string",
      "description": "Shell command to execute",
      "required": true
    },
    "working_directory": {
      "type": "string",
      "description": "Working directory for command execution",
      "required": false
    },
    "timeout": {
      "type": "integer",
      "description": "Timeout in seconds (default: 30, max: 300)",
      "required": false
    }
  }
}
```

**Whitelisted Commands:**
```
git, ls, cat, head, tail, grep, find, wc, diff, 
pwd, echo, mkdir, touch, cp, mv, rm,
gradle, gradlew, npm, node, python, python3,
adb, aapt, apksigner
```

**Blacklisted Patterns:**
```
rm -rf /
sudo, su
chmod 777
> /dev, > /sys, > /proc
curl | sh, wget | sh
mkfs, dd if=
:(){ :|:& };:
```

---

### 7. update_todo

Update the live task list shown above the chat input. AI uses this to communicate plan and progress.

```json
{
  "name": "update_todo",
  "description": "Update the task list shown to the user above the chat input",
  "parameters": {
    "merge": {
      "type": "boolean",
      "description": "If true, merge by id into existing list. If false, replace all items.",
      "required": true
    },
    "todos": {
      "type": "array",
      "description": "List of todo items: [{id, status, content, active_form}]",
      "required": true
    }
  }
}
```

**Todo Item Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `id` | int | 1-based unique identifier |
| `status` | string | `"pending"` / `"in_progress"` / `"completed"` |
| `content` | string | Imperative label (e.g. "Read files") |
| `active_form` | string | Present-continuous label shown collapsed (e.g. "Reading files") |

**Usage Pattern:**
```json
// At task start — replace all with full plan:
{"merge": false, "todos": [
  {"id": 1, "status": "in_progress", "content": "Read files", "active_form": "Reading files"},
  {"id": 2, "status": "pending", "content": "Write fix"},
  {"id": 3, "status": "pending", "content": "Verify build"}
]}

// As you progress — update specific items by id:
{"merge": true, "todos": [
  {"id": 1, "status": "completed"},
  {"id": 2, "status": "in_progress", "active_form": "Writing fix"}
]}
```

**UI Behavior:**
- **Position**: Sits directly below `TopAppBar` (in `topBar` Column of `Scaffold`), NOT in `bottomBar`
- **Collapsed** (default): Shows step number pill + current `active_form`/`content` with shimmer + `2/5` progress fraction
- **Expanded** (tap to open): Full list with icons — ✅ completed (green), ● in_progress (shimmer), ○ pending (dimmed)
- **Shimmer technique**: Text must have **solid `color = onSurface`** first, then `BlendMode.SrcAtop` overlay via `drawWithContent`. Requires `CompositingStrategy.Offscreen` on the modifier. Same as `Thinking..` indicator.
- **Shimmer colors**: `baseShimmer = Color(0xFF9E9E9E)` dark / `Color(0xFF757575)` light; `peakShimmer = Color.White` dark / `Color.Black` light
- **Only active items shimmer**: `in_progress` items get shimmer in both collapsed row and expanded list. `pending`/`completed` use plain muted color.
- Cleared automatically on new conversation (`todoRepository.clear()` called in `clearConversation()`)

```kotlin
// Correct shimmer pattern (identical to Thinking.. indicator):
Text(
    text = label,
    color = MaterialTheme.colorScheme.onSurface,  // MUST be solid, not alpha
    modifier = Modifier
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(brush = shimmerBrush, blendMode = BlendMode.SrcAtop)
        }
)
```

---

### 8. invoke_subagents

Spawn multiple independent AI subagents that run in parallel. Each subagent is a full AI chat call with its own tools.

```json
{
  "name": "invoke_subagents",
  "description": "Spawn multiple independent AI subagents that run IN PARALLEL",
  "parameters": {
    "subagents": {
      "type": "array",
      "description": "List of subagent tasks: [{task_name, task}]",
      "required": true
    }
  }
}
```

**Subagent Task Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `task_name` | string | Short label ≤5 words (e.g. "Audit UI Layer") |
| `task` | string | Full self-contained prompt — include ALL context (file paths, project info, what to look for) |

**When to use:**

| Use ✅ | Don't use ❌ |
|--------|-------------|
| Reading multiple folders simultaneously | Tasks that depend on each other |
| Auditing different layers of codebase | Sequential operations (A must finish before B) |
| Generating multiple independent files | Simple 1-2 file tasks |
| Scanning for patterns across the codebase | Anything requiring shared state |

**Rate Limit Strategy (Stagger + 1x Retry):**
- Agent N starts after `(N-1) × 2s` delay → spread API calls, avoid burst
- Agent 1: 0s delay, Agent 2: 2s, Agent 3: 4s, Agent 4: 6s
- On 429 rate-limit error: parse `Retry-After` header (max 30s wait), retry once
- If retry fails: that subagent returns `[RATE LIMITED]` error, others unaffected

**Architecture:**
```
Main AI calls invoke_subagents([task1, task2, task3])
        │
        ├── coroutineScope { async { SubagentRunner.run(task1) } }   ← 0s delay
        ├── coroutineScope { async { SubagentRunner.run(task2) } }   ← 2s delay
        └── coroutineScope { async { SubagentRunner.run(task3) } }   ← 4s delay
                ↓ all awaitAll() — parallel
        Combined summary returned to main AI
```

**Circular Dependency Fix:**
`SubagentRunner` uses `Provider<ToolExecutor>` (lazy Hilt injection) to break the cycle:
`ToolExecutor → InvokeSubagentsTool → SubagentRunner → ToolExecutor`

**Key Classes:**

| Class | File | Responsibility |
|-------|------|----------------|
| `InvokeSubagentsTool` | `InvokeSubagentsTool.kt` | Tool entrypoint, parses args, runs coroutines |
| `SubagentRunner` | `InvokeSubagentsTool.kt` | Full AI agentic loop for one subagent + rate limit handling |
| `SubagentTask` | `InvokeSubagentsTool.kt` | Data: index, taskName, task prompt |
| `SubagentResult` | `InvokeSubagentsTool.kt` | Data: taskName, summary string |
| `RateLimitException` | `InvokeSubagentsTool.kt` | Thrown on 429, carries `retryAfterMs` |

**Constraints:**
- Max 4 subagents per call
- Subagents do NOT see main conversation history
- Subagents cannot call `invoke_subagents` (no nesting)
- Max 8 agentic loop iterations per subagent

---

## 🔒 Security Architecture

### Multi-Layer Security Model

```
┌─────────────────────────────────────────────────────────┐
│                     USER REQUEST                         │
└─────────────────────────┬───────────────────────────────┘
                          ▼
┌─────────────────────────────────────────────────────────┐
│              LAYER 1: INPUT VALIDATION                   │
│  • Path sanitization (no ../ traversal)                  │
│  • Command parsing and tokenization                      │
│  • Argument type validation                              │
└─────────────────────────┬───────────────────────────────┘
                          ▼
┌─────────────────────────────────────────────────────────┐
│              LAYER 2: COMMAND VALIDATOR                  │
│  • Whitelist check for shell commands                    │
│  • Blacklist patterns for dangerous operations           │
│  • Path access validation                                │
│  • Protected paths enforcement                           │
└─────────────────────────┬───────────────────────────────┘
                          ▼
┌─────────────────────────────────────────────────────────┐
│              LAYER 3: CONFIRMATION GATE                  │
│  • User prompt for sensitive operations                  │
│  • Shows command/path details                            │
│  • Explicit allow/deny                                   │
└─────────────────────────┬───────────────────────────────┘
                          ▼
┌─────────────────────────────────────────────────────────┐
│              LAYER 4: SAFE EXECUTION                     │
│  • Atomic file operations                                │
│  • Automatic backups                                     │
│  • Timeout enforcement                                   │
│  • Rollback on failure                                   │
└─────────────────────────────────────────────────────────┘
```

### Protected Paths

These paths are **read-only** or **blocked**:

| Path | Access Level |
|------|--------------|
| `/system` | Blocked |
| `/data/data` (other apps) | Blocked |
| `/proc`, `/sys`, `/dev` | Blocked |
| `~/.ssh` | Confirmation required |
| `~/.gnupg` | Confirmation required |
| `/sdcard/Android/data` | Confirmation required |

### Path Normalization (Fixed)

`CommandValidator.normalizePath()` uses a **stack-based resolver** that correctly handles `..` sequences:

```kotlin
// Before (broken — did not resolve ".." at all):
return path.replace(Regex("//+"), "/").removeSuffix("/")

// After (correct — resolves all segments including ".."):
val parts = path.replace(Regex("//+"), "/").split("/")
val resolved = ArrayDeque<String>()
for (part in parts) {
    when (part) {
        "", "." -> { /* skip */ }
        ".." -> if (resolved.isNotEmpty()) resolved.removeLast()
        else -> resolved.addLast(part)
    }
}
return "/" + resolved.joinToString("/")
```

`containsPathTraversal()` also guards against **URL-encoded variants**:
```kotlin
return path.contains("..") ||
    path.contains("%2e%2e", ignoreCase = true) ||
    path.contains("%252e", ignoreCase = true)
```

### Tool Description Truncation

All tool descriptions (local + MCP) are truncated to **1024 characters max** in `AiRepository.buildToolDefinitions()` before being sent to AI providers. This prevents `400 Bad Request` errors from OpenAI-compatible APIs (e.g. OpenRouter) that enforce this limit.

```kotlin
private fun String.truncate(maxLen: Int): String =
    if (length <= maxLen) this else substring(0, maxLen - 1) + "…"
```

### Validation Results

```kotlin
sealed class ValidationResult {
    object Allowed : ValidationResult()
    
    data class RequiresConfirmation(
        val reason: String
    ) : ValidationResult()
    
    data class Denied(
        val reason: String
    ) : ValidationResult()
}
```

---

## 📝 System Prompt

The AI agent uses this system prompt (dynamically built in `AiRepository.buildSystemPrompt()`):

```
You are an expert AI coding assistant running on an Android device.
You help users write, edit, and debug code directly on their mobile device.

CAPABILITIES:
- Read and write files using native Android APIs
- List directory contents
- Run shell commands (for git, grep, build tools)
- Create and delete files safely (with backup/trash)

GUIDELINES:
1. Always explain what you're doing before using tools
2. Use native file tools instead of shell commands when possible
3. Create backups before modifying important files
4. Ask for confirmation before destructive operations
5. Keep responses concise but informative
6. When writing code, follow the project's existing style

TOOLS — MEMORY & REMINDERS:
- Use update_memory(content, target="daily") to log important events from this session
- Use update_memory(content, target="long") to persist user preferences/facts permanently
- Use create_reminder(...) when user asks to be reminded

TOOLS — TASK PROGRESS (update_todo):
- For any multi-step task, call update_todo at the START with merge=false to set your full plan.
- Each item needs: id (int, 1-based), status, content, active_form (optional).
- As you work, call update_todo with merge=true to update individual item statuses by id.
- This shows the user a live progress bar above the chat input — keep it up to date.

TOOLS — SUBAGENTS (invoke_subagents):
- Use invoke_subagents when a task has INDEPENDENT sub-tasks that can run IN PARALLEL.
- Perfect for: reading multiple folders at once, auditing different layers simultaneously.
- Each subagent gets its own task description — include ALL context (file paths, project info).
- Subagents do NOT see conversation history — be explicit and self-contained in each task.
- Max 4 subagents per call. DO NOT use for tasks that depend on each other.

PROJECT STRUCTURE:
[Dynamically injected based on indexed files]
```

---

## 🔄 Agentic Loop

The agent operates in a continuous loop:

```
┌─────────────────────────────────────────────────────────┐
│                    USER MESSAGE                          │
└─────────────────────────┬───────────────────────────────┘
                          ▼
┌─────────────────────────────────────────────────────────┐
│               BUILD REQUEST WITH CONTEXT                 │
│  • Add system prompt                                     │
│  • Add project structure                                 │
│  • Add tool definitions                                  │
│  • Add conversation history                              │
└─────────────────────────┬───────────────────────────────┘
                          ▼
┌─────────────────────────────────────────────────────────┐
│                  SEND TO AI PROVIDER                     │
│  • Stream response tokens                                │
│  • Collect tool calls                                    │
└─────────────────────────┬───────────────────────────────┘
                          ▼
                 ┌────────┴────────┐
                 │  Tool Calls?    │
                 └────────┬────────┘
              No          │          Yes
               ▼          │           ▼
    ┌──────────────┐      │    ┌──────────────────────┐
    │    DONE      │      │    │   EXECUTE TOOLS      │
    │ Show response│      │    │ • Validate security  │
    │              │      │    │ • Request confirm    │
    └──────────────┘      │    │ • Execute operation  │
                          │    │ • Collect results    │
                          │    └──────────┬───────────┘
                          │               │
                          │               ▼
                          │    ┌──────────────────────┐
                          │    │ ADD TOOL RESULTS     │
                          │    │ TO CONVERSATION      │
                          └────┴──────────────────────┘
                                         │
                          ┌──────────────┘
                          ▼
                   (Loop continues)
```

**Maximum Iterations:** 10 (prevents infinite loops)

---

## 📁 Project Structure

```
app/src/main/java/com/amaya/intelligence/
│
├── 📂 data/
│   ├── 📂 local/db/
│   │   ├── entity/
│   │   │   ├── ProjectEntity.kt       # Project tracking
│   │   │   ├── FileEntity.kt          # File index
│   │   │   ├── FileMetadataEntity.kt  # Code symbols
│   │   │   └── FileFtsEntity.kt       # Full-text search
│   │   ├── dao/
│   │   │   ├── ProjectDao.kt          # Project CRUD
│   │   │   ├── FileDao.kt             # File queries + FTS
│   │   │   └── FileMetadataDao.kt     # Symbol queries
│   │   └── AppDatabase.kt             # Room database
│   │
│   ├── 📂 remote/api/
│   │   ├── AiProvider.kt              # Unified interface
│   │   ├── AnthropicProvider.kt       # Claude implementation
│   │   ├── OpenAiProvider.kt          # GPT implementation
│   │   ├── GeminiProvider.kt          # Gemini implementation
│   │   ├── AiSettings.kt              # API key storage + MCP config storage
│   │   ├── McpModels.kt               # McpConfig, McpServerConfig data models
│   │   └── Models.kt                  # Request/Response types
│   │
│   ├── 📂 remote/mcp/
│   │   ├── McpClientManager.kt        # HTTP MCP client (listTools, callTool)
│   │   └── McpToolExecutor.kt         # Routes MCP vs local tool calls
│   │
│   └── 📂 repository/
│       ├── AiRepository.kt            # AI orchestration + MCP tool injection
│       └── FileIndexRepository.kt     # File system sync
│
├── 📂 di/
│   ├── DatabaseModule.kt              # Room dependencies
│   ├── NetworkModule.kt               # OkHttp, Moshi
│   └── AiModule.kt                    # AI providers
│
├── 📂 domain/security/
│   └── CommandValidator.kt            # Security validation
│
├── 📂 tools/
│   ├── ToolResult.kt                  # Tool interface + result types
│   ├── ToolExecutor.kt                # Routing & local tool execution
│   ├── ReadFileTool.kt                # read_file
│   ├── WriteFileTool.kt               # write_file
│   ├── ListFilesTool.kt               # list_files
│   ├── CreateDirectoryTool.kt         # create_directory
│   ├── DeleteFileTool.kt              # delete_file
│   ├── RunShellTool.kt                # run_shell
│   ├── TodoRepository.kt              # TodoItem state (StateFlow), merge/replace/clear
│   ├── UpdateTodoTool.kt              # update_todo — AI updates live task list
│   └── InvokeSubagentsTool.kt         # invoke_subagents — parallel AI sub-calls + SubagentRunner
│
├── 📂 ui/
│   ├── 📂 chat/
│   │   ├── ChatScreen.kt              # Main UI + sidebar drawer
│   │   └── ChatViewModel.kt           # State management
│   ├── 📂 settings/
│   │   ├── SettingsActivity.kt        # Settings standalone activity
│   │   ├── SettingsScreen.kt          # Settings list UI
│   │   ├── AgentsActivity.kt          # AI agents standalone activity
│   │   ├── AgentsScreen.kt            # Agent list + add/edit BottomSheet
│   │   ├── McpActivity.kt             # MCP servers standalone activity
│   │   ├── McpScreen.kt               # MCP server list + add/edit BottomSheet
│   │   ├── CronJobActivity.kt         # Reminders standalone activity
│   │   ├── CronJobScreen.kt           # Reminder list + add BottomSheet
│   │   ├── PersonaActivity.kt         # Persona standalone activity
│   │   └── PersonaScreen.kt           # Persona edit UI
│   ├── 📂 theme/
│   │   └── Theme.kt                   # Material 3 theme
│   └── MainActivity.kt                # Entry point
│
└── AmayaApplication.kt                # Hilt application
```

---

## 🗄️ Database Schema

Amaya Intelligence uses Room with multiple tables for project and persistent AI state.

### Cron Jobs Table
```sql
CREATE TABLE cron_jobs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    title TEXT NOT NULL,
    prompt TEXT NOT NULL,
    schedule_time INTEGER NOT NULL,
    recurring_type TEXT NOT NULL, -- "NONE", "DAILY", "WEEKLY"
    is_active INTEGER NOT NULL DEFAULT 1,
    fire_count INTEGER NOT NULL DEFAULT 0,
    conversation_id INTEGER, -- Optional link to chat
    created_at INTEGER NOT NULL
);
```

### Projects Table
```sql
CREATE TABLE projects (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    root_path TEXT NOT NULL UNIQUE,
    last_opened_at INTEGER NOT NULL,
    created_at INTEGER NOT NULL,
    file_count INTEGER DEFAULT 0,
    total_size_bytes INTEGER DEFAULT 0
);
```

### Files Table
```sql
CREATE TABLE files (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id INTEGER NOT NULL,
    file_name TEXT NOT NULL,
    relative_path TEXT NOT NULL,
    extension TEXT,
    size_bytes INTEGER NOT NULL,
    last_modified INTEGER NOT NULL,
    content_hash TEXT,
    is_directory INTEGER NOT NULL DEFAULT 0,
    indexed_at INTEGER NOT NULL,
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);

CREATE INDEX idx_files_project ON files(project_id);
CREATE INDEX idx_files_path ON files(project_id, relative_path);
CREATE INDEX idx_files_extension ON files(project_id, extension);
```

### Files FTS (Full-Text Search)
```sql
CREATE VIRTUAL TABLE files_fts USING fts4(
    file_name,
    relative_path,
    content=files
);
```

### File Metadata Table
```sql
CREATE TABLE file_metadata (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    file_id INTEGER NOT NULL,
    language TEXT,
    line_count INTEGER,
    has_syntax_errors INTEGER DEFAULT 0,
    symbols TEXT,  -- JSON array of symbols
    imports TEXT,  -- JSON array of imports
    analyzed_at INTEGER,
    FOREIGN KEY (file_id) REFERENCES files(id) ON DELETE CASCADE
);
```

---

## 🌐 API Integration

### Anthropic Messages API

**Request:**
```http
POST https://api.anthropic.com/v1/messages
Content-Type: application/json
x-api-key: YOUR_API_KEY
anthropic-version: 2023-06-01

{
  "model": "claude-sonnet-4-20250514",
  "max_tokens": 8192,
  "stream": true,
  "system": "You are an AI coding assistant...",
  "tools": [...],
  "messages": [
    {"role": "user", "content": "Read the MainActivity.kt file"}
  ]
}
```

**Stream Events:**
```
event: content_block_start
data: {"type":"content_block_start","content_block":{"type":"text"}}

event: content_block_delta
data: {"type":"content_block_delta","delta":{"type":"text_delta","text":"I'll read"}}

event: content_block_start
data: {"type":"content_block_start","content_block":{"type":"tool_use","name":"read_file"}}

event: message_stop
data: {"type":"message_stop"}
```

### OpenAI Chat Completions API

**Request:**
```http
POST https://api.openai.com/v1/chat/completions
Content-Type: application/json
Authorization: Bearer YOUR_API_KEY

{
  "model": "gpt-4o",
  "stream": true,
  "messages": [...],
  "tools": [
    {
      "type": "function",
      "function": {
        "name": "read_file",
        "description": "Read file content",
        "parameters": {...}
      }
    }
  ]
}
```

### Gemini Generate Content API

**Request:**
```http
POST https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-pro:streamGenerateContent?key=YOUR_API_KEY
Content-Type: application/json

{
  "contents": [...],
  "tools": [
    {
      "function_declarations": [
        {
          "name": "read_file",
          "description": "Read file content",
          "parameters": {...}
        }
      ]
    }
  ]
}
```

---

## 🔌 MCP Servers

Amaya Intelligence supports **Model Context Protocol (MCP)** over HTTP, allowing the AI agent to dynamically discover and invoke tools from external MCP-compatible servers.

### Architecture

```
┌─────────────────────────────────────────────┐
│              AI AGENT (AiRepository)         │
│  buildToolDefinitions()                      │
│  = local tools + mcpClientManager.cached()  │
└──────────────────┬──────────────────────────┘
                   │
       ┌───────────┴───────────┐
       │                       │
┌──────▼──────┐       ┌───────▼────────────┐
│ Local Tools │       │  McpClientManager   │
│ (ToolExecutor)      │  HTTP JSON-RPC 2.0  │
└─────────────┘       │  tools/list         │
                      │  tools/call         │
                      └───────┬─────────────┘
                              │ HTTP POST
                      ┌───────▼─────────────┐
                      │  External MCP Server │
                      │  (any serverUrl)     │
                      └─────────────────────┘
```

### Transport

Only **HTTP transport** is supported (Android cannot run `npx`/stdio servers). MCP servers must expose a `serverUrl` endpoint accepting JSON-RPC 2.0 POST requests with:

```
Content-Type: application/json
Accept: application/json, text/event-stream
```

Responses can be **plain JSON** or **SSE (Server-Sent Events)** — both are handled automatically.

### Configuration Format

MCP servers are configured via JSON, editable in Settings → **MCP Servers**:

```json
{
  "mcpServers": {
    "context7": {
      "serverUrl": "https://mcp.context7.com/mcp",
      "headers": {
        "CONTEXT7_API_KEY": "your-api-key-here"
      },
      "enabled": true
    },
    "my-server": {
      "serverUrl": "http://192.168.1.100:3000/mcp",
      "headers": {},
      "enabled": false
    }
  }
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `serverUrl` | string | ✅ | Full HTTP(S) URL to the MCP endpoint |
| `headers` | object | ❌ | Key-value headers (API keys, auth tokens) |
| `enabled` | boolean | ❌ | Skip this server if `false` (default: `true`) |

### Tool Naming Convention

MCP tools are registered with a namespaced prefix to avoid collisions with local tools:

```
mcp__{serverName}__{toolName}

Examples:
  mcp__context7__resolve-library-id
  mcp__context7__query-docs
  mcp__my-server__search
```

### Settings UI

- **Editor**: Raw JSON textarea in Settings → MCP Servers.
- **Import**: File picker to import a `.json` file from external storage.
- **Fixed path**: Config is also written to `/storage/emulated/0/Amaya/mcp.json` on every save/import.
- **Auto-load**: On app start, if `/storage/emulated/0/Amaya/mcp.json` is newer than the stored config, it is loaded automatically.

### Tool Refresh

MCP tools are refreshed automatically in the background whenever `mcpConfigJson` changes (detected via `settingsFlow`). No app restart required after updating the config.

### Key Classes

| Class | Location | Responsibility |
|-------|----------|----------------|
| `McpServerConfig` | `McpModels.kt` | Single server config (url, headers, enabled) |
| `McpConfig` | `McpModels.kt` | Full config with list of servers + JSON parse/emit |
| `McpClientManager` | `McpClientManager.kt` | HTTP client: `listTools()`, `callTool()`, tool cache |
| `McpToolExecutor` | `McpToolExecutor.kt` | Routes tool calls: MCP prefix → McpClientManager, else → ToolExecutor |

### Troubleshooting MCP

| Symptom | Likely Cause | Fix |
|---------|-------------|-----|
| No MCP tools in AI | Config JSON empty or `enabled: false` | Check Settings → MCP Servers |
| HTTP 406 error | Missing `Accept` header | Already fixed in `McpClientManager` |
| Tools list empty | Server URL wrong or unreachable | Check `serverUrl` and network |
| Tools appear but call fails | Wrong header/API key | Check `headers` in config |
| Config not persisting | Fixed path write failed | Grant `MANAGE_EXTERNAL_STORAGE` permission |

### Example MCP Servers

| Server | serverUrl | Notes |
|--------|-----------|-------|
| Context7 | `https://mcp.context7.com/mcp` | Library docs. Needs `CONTEXT7_API_KEY` header |
| Custom Node | `http://your-host:3000/mcp` | Any MCP-compatible server hosted externally |

---

## ⚙️ Configuration

### Environment Setup

1. **Android SDK**: Install via Android Studio or command line
2. **JDK 17+**: Required for Gradle 8.x
3. **Gradle 8.9**: Included via wrapper

### local.properties
```properties
sdk.dir=C\:\\Users\\YourName\\AppData\\Local\\Android\\sdk
```

### gradle.properties
```properties
android.useAndroidX=true
android.nonTransitiveRClass=true
org.gradle.parallel=true
org.gradle.configuration-cache=true
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
```

### API Key Configuration

API keys are stored in encrypted DataStore:

```kotlin
// In AiSettingsManager
suspend fun setAnthropicApiKey(key: String)
suspend fun setOpenAiApiKey(key: String)
suspend fun setGeminiApiKey(key: String)
```

---

## 🎨 Theme & Design (Material 3 Expensive)

The app strictly adheres to a "Material 3 Expensive" design language, prioritizing absolute premium feel over basic functional UI.

### Core Visual Language
- **Generous Padding**: Minimum `16.dp` global margins, `24.dp` spacing between major sections.
- **Expressive Shapes**: Heavy use of `RoundedCornerShape(24.dp)` instead of standard `12.dp`.
- **Tonal Elevation**: Use `surfaceContainer`, `surfaceContainerHigh`, and `surfaceContainerHighest` instead of stark shadows (`elevation = 0.dp` is preferred for modern flatness with tonal differentiation).
- **Edge-to-Edge**: Full screen rendering. Layouts MUST pad exactly to system status bars and navigation bars (e.g., `statusBarsPadding()`, `navigationBarsPadding()`). DO NOT hardcode padding that overlaps with system UI.

### Color Palette (VS Code Inspired)

| Role | Light | Dark | Usage |
|------|-------|------|-------|
| Primary | `#0066CC` | `#569CD6` | Buttons, links, keywords |
| Secondary | `#008080` | `#4EC9B0` | Types, classes |
| Tertiary | `#A31515` | `#CE9178` | Strings |
| Background | `#FFFFFF` | `#1E1E1E` | Main background |
| Surface | `#F3F3F3` | `#252526` | Cards, dialogs |
| Error | `#E51400` | `#F44747` | Errors |
| Success | `#008000` | `#4EC9B0` | Success states |

### AI Chat UI Strict Rules
The chat interface intentionally deviates from standard SMS-style apps to feel like an integrated IDE terminal/assistant:

1. **NO Chat Bubbles**: Text messages must flow naturally without being enclosed in rounded bubbles.
2. **Minimal Tool Calls**: `ToolCallCard` components must be extra minimal (no green dot header).
3. **Exit Codes**: Tool results use exit codes. `exit 0` = minimal green background. `exit 1` = minimal red background.
4. **Shimmering Thinking Indicator**: Never use static "AI is thinking" text. Use exactly "Thinking.." with an animated gradient shimmering effect (Black -> White -> Black on Light mode, inverse on Dark mode).
5. **Dynamic Input Bar**: The message input area contains a single dynamic button: Send icon when typing, Stop icon (square) during AI streaming to halt generation. The input bar's padding MUST perfectly align with the screen edges and navigation bars.

### Settings & Persona UI Rules
1. **Agent Configuration**: Provider selection must use space-saving layouts like `ExposedDropdownMenuBox` to keep forms clean. Standard providers (OpenAI, Anthropic, Gemini) handle base URLs automatically under-the-hood to simplify UX.
2. **Persona Simple Mode**: Configuration fields must be neatly separated (e.g., using `HorizontalDivider`) and include quick-select `AssistChip` rows (Suggestion Pills) for rapid styling (e.g., "Friendly", "Analytical").
3. **Persona Pro Mode**: File editors for `.md` guidelines (like `AGENTS.md`) must use a sleek `ScrollableTabRow` interface resembling a professional IDE, avoiding massive vertical scrolling text fields.

### Agent Management Rules (STRICT)

The AI Agents system follows a **simple enable/disable** model. There is NO concept of "active agent" in the settings screen:

1. **Toggle only**: Agents are activated/deactivated via a single `Switch`. No "Use This Agent" / Play button.
2. **Edit only in sheet**: Tapping an agent card opens `AgentEditSheet` for editing name, model ID, base URL, API key.
3. **Section labels**: "Enabled" (primary color) / "Disabled" (muted). NOT "Active"/"Inactive".
4. **Dropdown in chat**: Only **enabled** agents appear in the model selector dropdown in `ChatScreen` TopAppBar.
5. **Auto-fallback**: If the currently selected agent is disabled, `ChatViewModel` automatically falls back to the first enabled agent.
6. **Model priority**: `selectedModel` from `uiState` (set by dropdown) always takes priority over DataStore — prevents stale model bugs.

```kotlin
// Model resolution priority in AiRepository.chat():
val model = when {
    !selectedModel.isNullOrBlank()        -> selectedModel       // UI selection (always wins)
    !agentConfig?.modelId.isNullOrBlank() -> agentConfig!!.modelId  // agent config
    settings.activeModel.isNotBlank()     -> settings.activeModel   // DataStore fallback
    else                                  -> provider.supportedModels.firstOrNull() ?: ""
}
```

### BottomSheet Drawer Rules (STRICT)

All add/edit forms in Settings screens (MCP, Agents, Reminders, etc.) use `ModalBottomSheet` as a full-screen drawer. The following rules are **mandatory**:

1. **Square top, fills statusbar**: Use `containerColor = Color.Transparent` + `dragHandle = null`. Wrap content in a `Surface(shape = RoundedCornerShape(0.dp))` with `windowInsetsPadding(WindowInsets.statusBars)`. This ensures the sheet fills corner-to-corner, including the statusbar area.

2. **No swipe/drag gesture**: Always set `confirmValueChange = { false }` in `rememberModalBottomSheetState`. Users must use the explicit ✕ close button only.

3. **Predictive back = sheet slides down**: Install a `BackHandler` **outside** the `ModalBottomSheet` lambda (must be in `@Composable` scope). On back gesture: call `sheetState.hide()` first (animates sheet downward), then call `onDismiss()`.

4. **Keyboard-aware**: Add `.imePadding()` to the `Surface` modifier so content lifts when keyboard appears.

5. **Scrollable content**: Wrap the inner `Column` with `.verticalScroll(rememberScrollState())` for long forms.

6. **Header with close button**: Every sheet must have a title + `IconButton(Icons.Default.Close)` in a `Row` at the top.

```kotlin
// Correct pattern for all BottomSheet drawers:
val sheetState = rememberModalBottomSheetState(
    skipPartiallyExpanded = true,
    confirmValueChange = { false }  // disables swipe gesture
)
val scope = rememberCoroutineScope()

BackHandler {
    scope.launch {
        sheetState.hide()  // animate sheet down first
        onDismiss()
    }
}

ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = Color.Transparent,
    dragHandle = null
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .imePadding(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(0.dp)
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 20.dp, bottom = 32.dp)
        ) {
            // Header
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Title", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { scope.launch { sheetState.hide(); onDismiss() } }) {
                    Icon(Icons.Default.Close, "Dismiss")
                }
            }
            // ... form content ...
        }
    }
}
```

---

## 🧭 Navigation & Transitions (Pure Android 16 Stock)

To achieve the absolute **Pure Vanilla Android 16 / Pixel UI** feel with flawless **Predictive Back Gestures**, this project strictly abandons custom Jetpack Compose NavHost transitions for major screens.

### The "Standalone Activity" Rule
- **Main Chat Flow**: Handled by a single `NavHost` in `MainActivity` with **ZERO custom transitions** (`enterTransition`, `exitTransition`, etc. must not be defined).
- **Major Screens (Settings, Workspace)**: Must be deployed as **Standalone Activities** (e.g., `SettingsActivity`, `WorkspaceActivity`), entirely outside the `NavHost`.
- **Navigation Pattern**: Use standard Android `startActivity(Intent(this, WorkspaceActivity::class.java))` instead of `navController.navigate("workspace")`.

### Why Activity-Based Navigation?
By splitting major screens into separate Activities, we force the Android Operating System's `SurfaceFlinger` to handle all deep hierarchical transitions at the system level. This guarantees:
1. Flawless **Android 16 Material 3 Expressive motion physics**.
2. Zero "black screen" or "transparency" glitches during the predictive back swipe drag.
3. 100% native Z-depth scaling (the outgoing screen shrinks to 90%, the incoming screen scales up).
4. Uninterrupted 60/120fps scrubbing when the user reverses a back gesture midway.

**Any attempt to recreate these system-level predictive back animations manually using Compose `AnimatedContentTransitionScope` overrides is strictly forbidden for major context switches.**

### Sidebar Navigation Drawer Rules

The main screen sidebar (`ModalNavigationDrawer` in `ChatScreen.kt`) follows these design rules:

1. **Width**: Fixed `300.dp`, `RectangleShape` (no rounded corners on drawer).
2. **Header**: App name bold left + close `✕` button right.
3. **Action buttons**: Two equal-width `Surface` pill buttons side by side — "New chat" (`primaryContainer`) and "Project" (`surfaceVariant`). No `NavigationDrawerItem` for actions.
4. **Search**: Inline `BasicTextField` embedded in `Surface(rounded)` — not a fullscreen mode. Filters conversations live. Shows "No results" state.
5. **Conversation list**: `Surface(onClick)` items with `ChatBubbleOutline` icon — no `NavigationDrawerItem`. Empty state with centered icon + text.
6. **Footer**: Settings as a row with avatar `Surface(36dp)` + label + `ChevronRight`. Uses `navigationBarsPadding()`.
7. **Section labels**: `Text(labelMedium)` in `onSurfaceVariant` color above list (e.g. "Recent").

---

## 📱 Permissions

### Required Permissions

| Permission | Purpose | When Requested |
|------------|---------|----------------|
| `INTERNET` | AI API calls | Always granted |
| `ACCESS_NETWORK_STATE` | Check connectivity | Always granted |
| `MANAGE_EXTERNAL_STORAGE` | Access project files | On first project open |
| `FOREGROUND_SERVICE` | Long operations | When starting service |

### Runtime Permission Flow

```kotlin
// Check for file access permission
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
    if (!Environment.isExternalStorageManager()) {
        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
        intent.data = Uri.parse("package:$packageName")
        startActivity(intent)
    }
}
```

---

## 🔨 Building & Running

### Prerequisites
- Android SDK (API 35)
- JDK 17+
- Connected Android device with USB debugging

### Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug

# Run tests
./gradlew test

# Build release APK
./gradlew assembleRelease
```

### ADB Commands

```bash
# List connected devices
adb devices

# Install APK
adb install app/build/outputs/apk/debug/app-debug.apk

# Launch app
adb shell am start -n com.amaya.intelligence.debug/com.amaya.intelligence.ui.MainActivity

# View logs
adb logcat -s "Amaya:*" "AndroidRuntime:E"

# Uninstall
adb uninstall com.amaya.intelligence.debug
```

---

## 💬 Example Conversations

### Example 1: Reading a File

**User:** Read the MainActivity.kt file

**Agent:** I'll read the MainActivity.kt file for you.

```tool_use
{
  "name": "read_file",
  "arguments": {
    "path": "/storage/emulated/0/Projects/MyApp/app/src/main/java/com/example/MainActivity.kt"
  }
}
```

**Tool Result:**
```kotlin
package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
...
```

**Agent:** Here's the content of MainActivity.kt. It's a standard Compose activity that...

---

### Example 2: Modifying Code

**User:** Add a new button called "Settings" to the main screen

**Agent:** I'll add a Settings button to the main screen. Let me first read the current implementation, then make the modification.

```tool_use
{
  "name": "read_file",
  "arguments": {"path": "...MainActivity.kt"}
}
```

Now I'll add the Settings button:

```tool_use
{
  "name": "write_file",
  "arguments": {
    "path": "...MainActivity.kt",
    "content": "...updated content with Settings button..."
  }
}
```

**Tool Result:** Successfully written to: MainActivity.kt (1523 chars)
Backup created: MainActivity.kt.bak.1706745600000

**Agent:** Done! I've added a Settings button to the main screen. The button is placed in the top app bar...

---

### Example 3: Running Commands

**User:** Run the tests and show me the results

**Agent:** I'll run the tests for you.

```tool_use
{
  "name": "run_shell",
  "arguments": {
    "command": "./gradlew test",
    "working_directory": "/storage/emulated/0/Projects/MyApp",
    "timeout": 120
  }
}
```

**Tool Result:**
```
> Task :app:testDebugUnitTest

com.example.MyTest > testAddition PASSED
com.example.MyTest > testSubtraction PASSED
com.example.MyTest > testMultiplication FAILED

3 tests, 1 failure

FAILURE: Build failed with an exception.
```

**Agent:** The tests completed with 1 failure. Let me show you the failing test...

---

## 🐛 Troubleshooting

### App Crashes on Launch

**Cause:** Missing Hilt injection
**Solution:** Ensure `AmayaApplication` is annotated with `@HiltAndroidApp`

### "API Key Not Set" Error

**Cause:** No API key configured
**Solution:** Add API key in Settings (or hardcode for testing)

### File Permission Denied

**Cause:** Missing MANAGE_EXTERNAL_STORAGE permission
**Solution:** Grant permission in Settings > Apps > Amaya > Permissions

### Tool Execution Timeout

**Cause:** Command taking too long
**Solution:** Increase timeout parameter or break into smaller operations

### Build Fails with SDK Error

**Cause:** Missing compileSdk 35
**Solution:** Update Android SDK via SDK Manager

### "Invalid Tool Definition" / 400 Error from OpenAI-compat API

**Cause:** Tool description exceeds 1024 characters. Common with MCP tools from external servers.
**Solution:** Already fixed — `AiRepository.buildToolDefinitions()` truncates all descriptions to 1024 chars via `.truncate(1024)` extension function.

### Wrong Model Used Despite Dropdown Selection

**Cause:** `settingsFlow.collect` in `ChatViewModel.init` was overriding `selectedModel` from UI on every DataStore update.
**Solution:** Already fixed — `settingsFlow.collect` only sets `selectedModel` from DataStore if current agent is no longer enabled. UI selection (`setSelectedAgent()`) always takes priority.

### Subagent Rate Limit (429) from AI Provider

**Cause:** All subagents hitting API simultaneously.
**Solution:** Already fixed — subagents use staggered starts: Agent N starts after `(N-1) × 2s` delay. On 429: waits `Retry-After` (max 30s) then retries once.

### MCP Tools Cause 400 on Non-Anthropic Providers

**Cause:** MCP server tool descriptions can be very long (no size limit on their end).
**Solution:** Already fixed — MCP tools also go through `.truncate(1024)` in `buildToolDefinitions()`.

---

## 🗺️ Roadmap

### v1.0
- [x] Core agent functionality
- [x] Multi-provider support
- [x] Tool execution engine
- [x] Security validation
- [x] Basic chat UI

### v1.1 (Current)
- [x] Settings screen (Agent & Persona UI)
- [x] Rich Markdown rendering in chat
- [x] Advanced Reminder System (WorkManager + Hilt)
- [x] Multi-Activity Architecture (Predictive Back support)
- [x] Package Rename & Refactor (`com.amaya.intelligence`)
- [x] MCP HTTP client (JSON-RPC 2.0, SSE + plain JSON, tool cache, auto-refresh)
- [x] MCP Settings UI (list, add/edit BottomSheet, file picker import, fixed path sync)
- [x] AI Agents redesign — enable/disable toggle only, no Play button, no "active agent" concept
- [x] Agent dropdown — only shows enabled agents; fallback to first enabled on disable
- [x] Sidebar redesign (search inline, action buttons, modern conversation list, settings footer)
- [x] BottomSheet UX (no swipe dismiss, square top fills statusbar, predictive back slides down)
- [x] Live TodoBar — collapsible shimmer progress bar below TopAppBar (`update_todo` tool)
- [x] Parallel Subagents — `invoke_subagents` tool with stagger + rate limit retry (`SubagentRunner`)
- [x] Tool description truncation — all tool descriptions (local + MCP) capped at 1024 chars for OpenAI-compat APIs
- [x] Model selection fix — `selectedModel` from UI state always takes priority over DataStore
- [x] Security hardening — path traversal fix, encoded path detection, `create_directory` isWrite fix
- [x] Debug log cleanup — removed all sensitive data logs from OpenAiProvider & GeminiProvider
- [x] Memory leak fixes — `confirmationContinuation` cleared in `onCleared()`, `repoScope` cancellable
- [x] DAO `@Singleton` — all 5 DAO providers now singleton in `DatabaseModule`
- [x] Dead code removal — `selectProvider()`, `providers`/`selectedProvider` from `ChatUiState`
- [x] UUID tool call IDs — GeminiProvider now uses `UUID.randomUUID()` instead of timestamp
- [ ] Project browser
- [ ] Syntax highlighting for code blocks

### v1.2
- [ ] Foreground service
- [ ] Voice input
- [ ] File diff viewer
- [ ] Git integration UI

### v2.0
- [ ] Multi-file context
- [ ] Code search
- [ ] Refactoring tools
- [ ] Plugin system

---

## 🤝 Contributing

Contributions are welcome! Please:

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Run tests
5. Submit a pull request

### Code Style
- Follow Kotlin coding conventions
- Use meaningful variable names
- Add KDoc comments for public APIs
- Write unit tests for new features

---

## 📄 License

MIT License

```
Copyright (c) 2024 Amaya Intelligence

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

<p align="center">
  Built with ❤️ using Kotlin, Jetpack Compose, and AI
</p>

<p align="center">
  <strong>Amaya Intelligence</strong> - Code Anywhere, Anytime
</p>

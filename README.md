# Amaya

Amaya is an intelligent, cross-platform AI assistant that supports Anthropic and OpenAI-compatible models, bridging a portable mobile experience with your development environments. It integrates a built-in remote bridge to IDEs for seamless workspace interaction, delivering a practical and secure coding companion.

## Overview

The Amaya ecosystem is split into three primary components that work together:

1. **Local Intelligence (Mobile App)**  
   A feature-rich Android application serving as the primary interface. It includes its own on-device persistence layer, chat UI, and support for multiple AI providers.

2. **Remote Execution (IDE Integration Bridge)**  
   A built-in bridge that connects the mobile app to your development environment via IDE extensions. It enables direct interaction with your workspace, including file operations, command execution, and editor integration.

3. **IDE Extension Layer**  
   The extension runs inside the IDE and establishes a WebSocket connection to Amaya through the remote feature. It acts as the execution layer, exposing workspace capabilities (files, terminal, editor actions) and streaming results back to the mobile app in real-time.

## Directory Structure

This monorepo contains the following primary directories:

- `app/`  
  Kotlin-based Android application containing the main chat interface (`ChatScreen`), local database (`Room`), and networking layer.

- `amaya-remote-extension/`  
  TypeScript-based IDE integration (currently targeting VS Code) that provides workspace access and execution capabilities via WebSocket communication. See [Extension README](./amaya-remote-extension/README.md) for details.

## Capabilities

### Local AI & Personalization

- **Anthropic support [experimental]**: dedicated support for Anthropic models (non-tested), because i don't have money for access Anthropic API lol
- **OpenAI-compatible support**: works with OpenAI-style APIs.
- **Personalization modes**:
  - **Simple mode**: style, tone, characteristics, and user preferences.
  - **Pro mode**: structured context via `agent.md`, `soul.md`, `identity.md`, `user.md`, `memory.md`.
- **MCP support**: HTTP-based Model Context Protocol servers.
- **Reminder system**: cronjob-based scheduling.

### Local Workspace Access (Android)

- **Local storage access**: read and interact with files on the device.
- **File discovery**: search, locate, and inspect files.
- **Contextual understanding**: query and analyze local project data.
- **Lightweight generation**: create notes, summaries, and simple documentation from local content.

### Agent & Model Management

- **Multi-agent support**: manage multiple AI agents.
- **Flexible configuration**:
  - API keys
  - Base URLs
  - Model selection
- **Provider compatibility**: Anthropic and OpenAI-compatible backends.

### Remote Workspace & IDE Integration [BETA]

- **Workspace access**: directly read, analyze, and modify project files through IDE integration.
- **Unified parsing pipeline**: all AI outputs are normalized into a consistent structure:
  - tool calls
  - reasoning/thinking streams
  - final responses
- **Structured UI rendering**: outputs are transformed into clean, interactive elements (chat, tool calls, streaming).
- **Code generation via mobile**: write, edit, and iterate on code from your phone, with seamless session switching.
- **Streaming & sync**: real-time communication with consistent session state.
- **Execution flow support**: handles multi-step actions, intermediate states, and progressive updates.

## IDE Support Roadmap

- **Google Antigravity**
  - Generate code directly from mobile
  - Code diff support for reviewing (currenly read-only on tool call write_file)
  - Conversation history support
  - AI model list with quota tracking and reset time
  - Conversation modes (planning / fast)
  - Terminal command approval system (accept / decline before execution)
  - Send images from mobile to IDE
  - Full parsing support across all models:
    - tool calls
    - thinking/reasoning
    - text streaming

## Limitations
This currently only support Google Antigravity IDE. The extension will not work with other IDEs. Maybe in the future I will add support for other IDEs.

## Documentation & Contribution

For detailed contribution guidelines and domain-specific rules:

- `AGENTS.md`
- `amaya-remote-extension/AGENTS.md`
- `app/AGENTS.md`
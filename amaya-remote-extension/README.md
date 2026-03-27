# Amaya Remote Session (IDE Extension)

This extension acts as a high-performance bridge between the **Amaya Android App** and your development environment. It enables seamless remote interaction, allowing you to read, analyze, and modify code directly from your mobile device.

## Features

- **WebSocket Bridge**: Establishes a secure real-time connection using WebSocket (default port: `8765`).
- **Universal Workspace Access**: Provides the Amaya Android app with full access to project files, terminal, and editor actions across supported development environments.
- **Provider-Agnostic Core**: Designed as a flexible execution layer to support multiple IDEs and AI engines
- **Auto-Start**: Optionally start the server automatically when your development environment launches.
- **Diagnostic Tools**: Built-in commands to verify connection health and credentials.

## Limitations
This currently only support Google Antigravity IDE. The extension will not work with other IDEs. Maybe in the future I will add support for other IDEs.

## Installation 
1. From Android, Go to https://github.com/nazrielnr/amaya
2. Download and Install the APK file.
3. Open your IDE (e.g., VS Code).
4. Search for `Amaya Remote Session` in the marketplace or install the extension package manually.
5. Ensure the Amaya Android app is installed on your mobile device.

## Getting Started

1. **Start the Server**:
   - **Method A (Pillbar)**: Click on the **Amaya** status item in your IDE's Pillbar.
   - **Method B (Command Palette)**: Open the Command Palette (`Ctrl+Shift+P` or `Cmd+Shift+P`) and run `Amaya Remote: Start Server`.
   - Use the provided connection details (or scan the QR code if available) to pair with the mobile app.

2. **Connect from Mobile**:
   - Open the Amaya app on Android.
   - Go to **Sidebar > Remote Session**.
   - Choose your IDE (VS Code, Antigravity, etc.)
   - Enter your computer's local IP and the port (default: `8765`).

3. **Verify Connection**:
   - Use the `Amaya Remote: Diagnose Connection` command to ensure the bridge is active and reachable.

## Configuration

Customizable behavior via IDE settings:

- Port configuration for WebSocket communication.
- Automatic server startup options.
- Selection of preferred execution providers.

## Technical Architecture

The extension implements a structured communication layer that serializes:
- **File Operations**: Standardized read/write/diff operations.
- **Terminal Execution**: Command execution with secure approval workflows.
- **Session Synchronization**: Real-time state synchronization between mobile and desktop environments.

For more details on the Amaya ecosystem, see the [Root README](../README.md).

## License

This project is licensed under the [MIT License](./LICENSE).

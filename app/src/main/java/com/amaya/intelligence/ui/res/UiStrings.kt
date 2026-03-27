package com.amaya.intelligence.ui.res

/**
 * Centralized UI strings for localization support.
 * All user-facing strings should be defined here.
 */
object UiStrings {
    
    object App {
        const val LOCAL_NAME = "Amaya"
        const val REMOTE_NAME = "Remote"
    }
    
    object Connection {
        const val CONNECTING = "Connecting to Remote Session..."
        const val RECONNECTING = "Reconnecting to server..."
        const val DISCONNECTED = "Disconnected from server. Attempting to reconnect..."
        const val DISCONNECTED_TRYING = "Disconnected. Trying to reconnect..."
        const val CONNECTED_TO = "Connected to"
        const val DISCONNECT = "Disconnect"
        const val CONNECT = "Connect to Workspace"
        const val REMOTE_SESSION = "Remote Session"
        const val REMOTE_CONNECTION = "Remote Connection"
        const val OPEN_CHAT = "Open Chat"
        const val CONNECTION_SETUP = "Connection Setup"
        const val IP_ADDRESS = "IP Address"
        const val PORT = "Port"
    }
    
    object Session {
        const val NEW_CHAT = "New chat"
        const val PROJECT = "Project"
        const val SELECT_WORKSPACE_IDE = "Select Workspace IDE"
        const val SEARCH_CONVERSATIONS = "Search conversations"
        const val RECENT = "Recent"
        const val RECENT_ON = "Recent on"
        const val UNCATEGORIZED = "UNCATEGORIZED"
        const val OTHER_WORKSPACES = "OTHER WORKSPACES"
    }
    
    object Status {
        const val LOADING = "Loading..."
        const val SENDING = "Sending..."
        const val GENERATING = "Generating..."
        const val STOPPING = "Stopping..."
    }
    
    object Actions {
        const val SAVE = "Save"
        const val SAVE_PERSONA = "Save Persona"
        const val CANCEL = "Cancel"
        const val DELETE = "Delete"
        const val RESET = "Reset"
        const val RESET_TO_DEFAULT = "Reset to Default?"
        const val SELECT_THIS_FOLDER = "Select This Folder"
        const val SEARCH_FILES = "Search files..."
    }
    
    object Labels {
        const val NAME = "Name"
        const val TITLE = "Title"
        const val SIMPLE = "Simple"
        const val PRO = "Pro"
        const val DEFAULT = "Default"
    }
    
    object Placeholders {
        const val IP_EXAMPLE = "e.g. 192.168.1.5"
        const val PORT_EXAMPLE = "e.g. 8765"
        const val NAME_EXAMPLE = "e.g. my-server"
        const val TITLE_EXAMPLE = "e.g. Buy groceries"
        const val AGENT_NAME_EXAMPLE = "Enter agent name"
        const val MODEL_ID_EXAMPLE = "Enter model ID"
        const val API_KEY_EXAMPLE = "Enter API key"
        const val BASE_URL_EXAMPLE = "Enter base URL"
        const val MAX_TOKENS_EXAMPLE = "Enter max tokens"
        const val SERVER_URL_EXAMPLE = "Enter server URL"
        const val REMINDER_MESSAGE_EXAMPLE = "What should Amaya remind you of?"
        const val STYLE_TONE_EXAMPLE = "e.g. Friendly, concise, professional"
        const val CHARACTERISTIC_EXAMPLE = "e.g. Analytical, patient, thorough"
        const val CUSTOM_INSTRUCTION_EXAMPLE = "Any extra rules or preferences"
        const val NICKNAME_EXAMPLE = "How should Amaya call you?"
        const val ABOUT_YOU_EXAMPLE = "Context about you, preferences, timezone..."
    }
    
    object Persona {
        const val STYLE_TONE = "Style & Tone"
        const val CHARACTERISTIC = "Characteristic"
        const val CUSTOM_INSTRUCTION = "Custom Instruction"
        const val YOUR_NICKNAME = "Your Nickname"
        const val MORE_ABOUT_YOU = "More About You"
        const val PRO_MODE_WORKSPACE = "Pro Mode Workspace"
    }
    
    object Dialogs {
        const val DELETE_CONVERSATION_TITLE = "Delete Conversation?"
        const val DELETE_AGENT_TITLE = "Delete Agent?"
    }
    
    object Settings {
        const val CURRENT_WORKSPACE = "Current Workspace"
        const val NOT_SELECTED = "Not selected"
        const val MANAGE_AGENTS = "Manage Agents"
        const val MANAGE_AGENTS_SUBTITLE = "Add or edit API keys, base URLs, and models"
        const val PERSONALITY_MEMORY = "Personality & Memory"
        const val PERSONALITY_MEMORY_SUBTITLE = "Style, instructions, and AI memory"
        const val REMINDERS_JOBS = "Reminders & Jobs"
        const val REMINDERS_JOBS_SUBTITLE = "Schedule periodic tasks and reminders"
        const val MCP_SERVERS = "MCP Servers"
        const val NO_SERVERS_CONFIGURED = "No servers configured"
        const val SERVERS_NONE_ACTIVE = "{count} server{s}, none active"
        const val SERVERS_ACTIVE = "{active} of {total} active"
        const val VERSION = "Version"
        const val VERSION_NUMBER = "1.0.0-alpha"
        const val HELP_FEEDBACK = "Help & Feedback"
        const val HELP_FEEDBACK_SUBTITLE = "Report issues or suggest features"
        const val SETTINGS = "Settings"
        const val THEME = "Theme"
        const val SYSTEM = "System"
        const val LIGHT = "Light"
        const val DARK = "Dark"
    }
    
    object Mcp {
        const val SERVER_URL = "Server URL"
        const val HEADERS_OPTIONAL = "Headers (optional)"
        const val HEADERS_DESCRIPTION = "Add API keys or auth tokens as request headers."
        const val KEY = "Key"
        const val VALUE = "Value"
    }
    
    object CronJob {
        const val MESSAGE_REMINDER = "Message / Reminder"
    }
    
    object Agents {
        const val API_KEY = "API Key"
        const val BASE_URL = "Base URL"
        const val MODEL_ID = "Model ID"
        const val MAX_TOKENS = "Max Tokens"
    }
    
    object ConversationMode {
        const val PLANNING = "Planning"
        const val PLANNING_DESCRIPTION = "Agent can plan before executing tasks. Use for deep research, complex tasks, or collaborative work"
        const val FAST = "Fast"
        const val FAST_DESCRIPTION = "Agent will execute tasks directly. Use for simple tasks that can be completed faster"
    }
    
    object SessionInfo {
        const val TOKENS_USED = "Tokens used"
        const val CONTEXT_WINDOW = "Context window"
        const val ACTIVE_REMINDERS = "Active reminders"
        const val ACTIVE = "active"
        const val NONE = "None"
    }
}

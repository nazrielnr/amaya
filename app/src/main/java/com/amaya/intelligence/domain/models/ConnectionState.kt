package com.amaya.intelligence.domain.models

/**
 * Connection state for remote IDE sessions.
 * Moved to domain layer to decouple UI from implementation.
 */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

package com.amaya.intelligence.impl.ide.antigravity.services.streaming

import com.amaya.intelligence.impl.ide.antigravity.AntigravityProtocol

/**
 * Manages streaming state for Antigravity intelligence service.
 * Tracks current phase, accumulated text, and thinking content during streaming.
 */
class StreamingStateManager {
    
    enum class StreamPhase {
        NONE, THINKING, TEXT, TOOL
    }
    
    private val streamingText = StringBuilder()
    private val streamingThinking = StringBuilder()
    private var streamingMessageId: String? = null
    private var currentStreamPhase: StreamPhase = StreamPhase.NONE
    private var lastStreamingStepIndex: String? = null
    
    val currentText: String get() = streamingText.toString()
    val currentThinking: String get() = streamingThinking.toString()
    val currentPhase: StreamPhase get() = currentStreamPhase
    val currentStepIndex: String? get() = lastStreamingStepIndex
    
    fun setPhase(phase: StreamPhase) {
        if (phase == StreamPhase.TOOL && currentStreamPhase != StreamPhase.TOOL) {
            streamingText.clear()
        }
        currentStreamPhase = phase
    }
    
    fun setStepIndex(index: String?) {
        lastStreamingStepIndex = index
    }
    
    fun appendText(text: String) {
        streamingText.append(text)
    }
    
    fun setText(text: String) {
        streamingText.clear()
        streamingText.append(text)
    }
    
    fun setThinking(text: String) {
        streamingThinking.clear()
        streamingThinking.append(text)
    }
    
    fun clearThinking() {
        streamingThinking.clear()
    }
    
    fun clearText() {
        streamingText.clear()
    }
    
    fun clearAll() {
        streamingText.clear()
        streamingThinking.clear()
        streamingMessageId = null
        currentStreamPhase = StreamPhase.NONE
        lastStreamingStepIndex = null
    }
    
    fun mergeStreamingSegment(incoming: String): String {
        val current = streamingText.toString()
        if (incoming.isBlank()) return current
        if (current.isBlank()) {
            streamingText.clear()
            streamingText.append(incoming)
            return incoming
        }
        
        // If the incoming text contains the entirety of the current text (pure replacement delta mode)
        if (incoming.startsWith(current)) {
            streamingText.clear()
            streamingText.append(incoming)
            return incoming
        }
        
        // Find if incoming overlaps exactly with the end of current
        // e.g. current = "I want to", incoming = "to go" => overlap = "to", merged = "I want to go"
        // E.g. current = "abc", incoming = "d" => no overlap
        var overlapLength = 0
        val maxPossibleOverlap = minOf(current.length, incoming.length)
        for (i in maxPossibleOverlap downTo 1) {
            if (current.endsWith(incoming.substring(0, i))) {
                overlapLength = i
                break
            }
        }
        
        val merged = current + incoming.substring(overlapLength)
        streamingText.clear()
        streamingText.append(merged)
        return merged
    }
    
    companion object {
        const val THINKING_TOOL_NAME = AntigravityProtocol.ToolMarkers.THINKING_TOOL_NAME
        const val THINKING_TOOL_META_KEY = AntigravityProtocol.ToolMarkers.THINKING_TOOL_META_KEY
    }
}

package com.amaya.intelligence.ui.screens.chat.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class ChatErrorPresentationTest {
    @Test
    fun preservesProviderErrorMessage() {
        val raw = "OpenAI API error 429: The usage limit has been reached"
        val presentation = presentChatError(raw)

        assertEquals("OpenAI API error 429", presentation.title)
        assertEquals("The usage limit has been reached", presentation.message)
    }

    @Test
    fun providerErrorWithoutColonGetsFriendlyHintInsteadOfDuplicate() {
        val raw = "SocketTimeoutException"
        val presentation = presentChatError(raw)

        assertEquals("SocketTimeoutException", presentation.title)
        assertEquals("Check your connection and try again.", presentation.message)
    }
}

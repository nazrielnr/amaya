package com.amaya.intelligence.impl.ide.opencode

import com.amaya.intelligence.domain.bridge.BridgeEnvelope
import com.amaya.intelligence.domain.bridge.BridgeMessageType
import com.amaya.intelligence.impl.bridge.windows.tools.OpencodeBridgeTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Sanity-level coverage for [OpencodeClient]'s envelope decoder. The bridge
 * transport is faked so tests stay hermetic.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OpencodeClientDispatchTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `permission asked updates pending permission flow`() = runTest(dispatcher) {
        val transport = FakeTransport()
        val client = OpencodeClient(transport)
        client.attach(backgroundScope)

        transport.emit(
            BridgeEnvelope(
                type = BridgeMessageType.AGENT_EVENT,
                sessionId = "sess-1",
                deviceId = "windows_bridge",
                seq = 1,
                payload = mapOf(
                    "kind" to "permission.asked",
                    "sessionId" to "sess-1",
                    "data" to mapOf(
                        "id" to "perm-1",
                        "title" to "Run shell command",
                        "kind" to "execute"
                    )
                )
            )
        )
        dispatcher.scheduler.advanceUntilIdle()

        val pending = client.pendingPermission.value
        assertNotNull(pending, "permission should have been captured")
        assertEquals("perm-1", pending.permissionId)
        assertEquals("sess-1", pending.sessionId)
    }

    @Test
    fun `permission replied clears pending permission`() = runTest(dispatcher) {
        val transport = FakeTransport()
        val client = OpencodeClient(transport)
        client.attach(backgroundScope)

        transport.emit(
            BridgeEnvelope(
                type = BridgeMessageType.AGENT_EVENT,
                sessionId = "sess-1",
                deviceId = "windows_bridge",
                seq = 1,
                payload = mapOf(
                    "kind" to "permission.asked",
                    "sessionId" to "sess-1",
                    "data" to mapOf("id" to "perm-42", "title" to "Edit file")
                )
            )
        )
        dispatcher.scheduler.advanceUntilIdle()
        assertNotNull(client.pendingPermission.value)

        transport.emit(
            BridgeEnvelope(
                type = BridgeMessageType.AGENT_EVENT,
                sessionId = "sess-1",
                deviceId = "windows_bridge",
                seq = 2,
                payload = mapOf(
                    "kind" to "permission.replied",
                    "sessionId" to "sess-1",
                    "data" to mapOf("id" to "perm-42")
                )
            )
        )
        dispatcher.scheduler.advanceUntilIdle()
        assertNull(client.pendingPermission.value)
    }

    @Test
    fun `runtime status envelope updates runtime flow`() = runTest(dispatcher) {
        val transport = FakeTransport()
        val client = OpencodeClient(transport)
        client.attach(backgroundScope)

        transport.emit(
            BridgeEnvelope(
                type = BridgeMessageType.AGENT_RUNTIME_STATUS,
                sessionId = "sess-1",
                deviceId = "windows_bridge",
                seq = 1,
                payload = mapOf(
                    "runtimeId" to "opencode",
                    "displayName" to "Opencode",
                    "status" to "ready",
                    "baseUrl" to "http://127.0.0.1:4096",
                    "version" to "1.14.29",
                    "updatedAt" to System.currentTimeMillis()
                )
            )
        )
        dispatcher.scheduler.advanceUntilIdle()

        val snapshot = client.runtime.value
        assertEquals("ready", snapshot.status)
        assertEquals("http://127.0.0.1:4096", snapshot.baseUrl)
        assertEquals("1.14.29", snapshot.version)
    }

    @Test
    fun `send prompt forwards envelope to transport`() = runTest(dispatcher) {
        val transport = FakeTransport()
        val client = OpencodeClient(transport)
        client.attach(backgroundScope)

        client.sendPrompt(sessionId = "sess-1", text = "hello", agent = "plan")

        val last = transport.sent.last()
        assertEquals(BridgeMessageType.AGENT_SESSION_PROMPT, last.type)
        val parts = last.payload["parts"] as List<*>
        val first = parts.first() as Map<*, *>
        assertEquals("text", first["type"])
        assertEquals("hello", first["text"])
        assertEquals("plan", last.payload["agent"])
    }

    private class FakeTransport : OpencodeBridgeTransport {
        // Replay diperlukan karena test mengirim envelope segera setelah
        // attach(). Tanpa replay, subscriber yang belum aktif akan kehilangan
        // event dan test menjadi flaky (race antara launch{} dan emit()).
        private val flow = MutableSharedFlow<BridgeEnvelope>(
            replay = 8,
            extraBufferCapacity = 32
        )
        val sent = mutableListOf<BridgeEnvelope>()

        override val envelopes: SharedFlow<BridgeEnvelope> = flow.asSharedFlow()

        override fun sendEnvelope(envelope: BridgeEnvelope): Boolean {
            sent.add(envelope)
            return true
        }

        fun emit(envelope: BridgeEnvelope) {
            flow.tryEmit(envelope)
        }
    }
}

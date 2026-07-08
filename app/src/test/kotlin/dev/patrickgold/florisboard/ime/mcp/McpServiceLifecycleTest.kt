/*
 * Copyright (C) 2026 SwiftFloris Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.mcp

import android.os.IBinder
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class McpServiceLifecycleTest : FunSpec({

    val keyA = DaemonKey(packageName = "com.daemon.a", daemonClassName = "com.daemon.a.Svc")
    val keyB = DaemonKey(packageName = "com.daemon.b", daemonClassName = "com.daemon.b.Svc")
    val toolA = McpToolDescriptor(
        name = "calendar.next_event",
        description = "Next event",
        parameterSchemaJson = """{"type":"object"}""",
    )
    val toolB = McpToolDescriptor(
        name = "clipboard.read",
        description = "Read clip",
        parameterSchemaJson = """{"type":"object"}""",
    )
    val entryA = DaemonEntry(key = keyA, protocolVersion = 1, tools = listOf(toolA))
    val entryB = DaemonEntry(key = keyB, protocolVersion = 1, tools = listOf(toolB))

    afterEach {
        // Reset both registries between tests to avoid test-order dependencies.
        McpDaemonRegistry.setActive(emptyMap())
        McpClientRegistry.setActive(NoOpMcpClient)
    }

    fun lifecycle(
        bind: (DaemonKey) -> Boolean = { true },
        unbind: (DaemonKey) -> Unit = {},
        shutdown: () -> Unit = {},
        binderLookup: (DaemonKey) -> IBinder? = { null },
        bridgeEnabled: () -> Boolean = { true },
    ) = McpServiceLifecycle(
        bindCallback = bind,
        unbindCallback = unbind,
        shutdownCallback = shutdown,
        binderLookup = binderLookup,
        isBridgeEnabled = bridgeEnabled,
    )

    test("startWithDaemons publishes the daemon map into McpDaemonRegistry") {
        val l = lifecycle()
        l.startWithDaemons(mapOf(keyA to entryA, keyB to entryB))
        McpDaemonRegistry.active().keys shouldBe setOf(keyA, keyB)
        McpDaemonRegistry.size() shouldBe 2
    }

    test("startWithDaemons invokes the bind callback once per daemon") {
        val bound = mutableSetOf<DaemonKey>()
        val l = lifecycle(bind = { key -> bound.add(key); true })
        l.startWithDaemons(mapOf(keyA to entryA, keyB to entryB))
        bound shouldBe setOf(keyA, keyB)
    }

    test("startWithDaemons does not bind or publish daemons while the bridge is disabled") {
        val bound = mutableSetOf<DaemonKey>()
        val l = lifecycle(
            bind = { key -> bound.add(key); true },
            bridgeEnabled = { false },
        )

        l.startWithDaemons(mapOf(keyA to entryA, keyB to entryB))

        bound shouldBe emptySet()
        McpDaemonRegistry.size() shouldBe 0
        McpClientRegistry.active() shouldBe NoOpMcpClient
        l.isStarted shouldBe false
    }

    test("startWithDaemons installs an AndroidMcpClient into McpClientRegistry") {
        val l = lifecycle()
        l.startWithDaemons(mapOf(keyA to entryA))
        McpClientRegistry.active().shouldBeInstanceOf<AndroidMcpClient>()
    }

    test("startWithDaemons throws on second call (lifecycle is single-shot)") {
        val l = lifecycle()
        l.startWithDaemons(emptyMap())
        try {
            l.startWithDaemons(mapOf(keyA to entryA))
            error("expected IllegalStateException")
        } catch (e: IllegalStateException) {
            // expected
        }
    }

    test("stop unbinds every bound daemon and calls shutdown") {
        val unbound = mutableSetOf<DaemonKey>()
        var shutdownFired = 0
        val l = lifecycle(
            unbind = { key -> unbound.add(key) },
            shutdown = { shutdownFired++ },
        )
        l.startWithDaemons(mapOf(keyA to entryA, keyB to entryB))

        l.stop()

        unbound shouldBe setOf(keyA, keyB)
        shutdownFired shouldBe 1
    }

    test("stop clears McpDaemonRegistry and restores NoOpMcpClient") {
        val l = lifecycle()
        l.startWithDaemons(mapOf(keyA to entryA))
        l.stop()
        McpDaemonRegistry.size() shouldBe 0
        McpClientRegistry.active() shouldBe NoOpMcpClient
    }

    test("stop is idempotent — second stop is a no-op") {
        val unbound = mutableListOf<DaemonKey>()
        val l = lifecycle(unbind = { key -> unbound.add(key) })
        l.startWithDaemons(mapOf(keyA to entryA))
        l.stop()
        l.stop()
        // Each daemon unbinds at most once.
        unbound shouldBe listOf(keyA)
        l.isStarted shouldBe false
    }

    test("isStarted reflects start/stop transitions") {
        val l = lifecycle()
        l.isStarted shouldBe false
        l.startWithDaemons(mapOf(keyA to entryA))
        l.isStarted shouldBe true
        l.stop()
        l.isStarted shouldBe false
    }

    test("startWithDaemons with empty map publishes an empty registry but still installs the client") {
        val l = lifecycle()
        l.startWithDaemons(emptyMap())
        McpDaemonRegistry.size() shouldBe 0
        McpClientRegistry.active().shouldBeInstanceOf<AndroidMcpClient>()
    }
})

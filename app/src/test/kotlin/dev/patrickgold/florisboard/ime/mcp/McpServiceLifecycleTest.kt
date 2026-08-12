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
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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
        McpConnectionStateStore.reset()
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
        l.isStarted shouldBe true
    }

    test("startWithDaemons installs an AndroidMcpClient into McpClientRegistry") {
        val l = lifecycle()
        l.startWithDaemons(mapOf(keyA to entryA))
        McpClientRegistry.active().shouldBeInstanceOf<AndroidMcpClient>()
    }

    test("rescan removes registry eligibility before unbinding a rejected daemon") {
        val observedRegistryAtUnbind = mutableListOf<Set<DaemonKey>>()
        val l = lifecycle(
            unbind = {
                observedRegistryAtUnbind += McpDaemonRegistry.active().keys
            },
        )
        l.startWithDaemons(mapOf(keyA to entryA))

        l.replaceDaemons(emptyMap())

        McpDaemonRegistry.active() shouldBe emptyMap()
        observedRegistryAtUnbind shouldBe listOf(emptySet())
    }

    test("rescan unbinds removed daemons and binds only newly eligible daemons") {
        val bound = mutableListOf<DaemonKey>()
        val unbound = mutableListOf<DaemonKey>()
        val l = lifecycle(
            bind = { key -> bound += key; true },
            unbind = { key -> unbound += key },
        )
        l.startWithDaemons(mapOf(keyA to entryA))
        bound.clear()

        l.replaceDaemons(mapOf(keyB to entryB))

        bound shouldBe listOf(keyB)
        unbound shouldBe listOf(keyA)
        McpDaemonRegistry.active() shouldBe mapOf(keyB to entryB)
    }

    test("rescan leaves unchanged eligible daemons bound while refreshing metadata") {
        val bound = mutableListOf<DaemonKey>()
        val unbound = mutableListOf<DaemonKey>()
        val refreshedEntry = entryA.copy(
            tools = listOf(toolA.copy(description = "Updated description")),
        )
        val l = lifecycle(
            bind = { key -> bound += key; true },
            unbind = { key -> unbound += key },
        )
        l.startWithDaemons(mapOf(keyA to entryA))
        bound.clear()

        l.replaceDaemons(mapOf(keyA to refreshedEntry))

        bound shouldBe emptyList()
        unbound shouldBe emptyList()
        McpDaemonRegistry.get(keyA) shouldBe refreshedEntry
    }

    test("startWithDaemons throws on second call (lifecycle is single-shot)") {
        val l = lifecycle()
        l.startWithDaemons(emptyMap())
        shouldThrow<IllegalStateException> {
            l.startWithDaemons(mapOf(keyA to entryA))
        }
    }

    test("disabled start still consumes the lifecycle") {
        val l = lifecycle(bridgeEnabled = { false })

        l.startWithDaemons(emptyMap())

        l.isStarted shouldBe true
        shouldThrow<IllegalStateException> {
            l.startWithDaemons(emptyMap())
        }
    }

    test("rescan waits for teardown and cannot repopulate the registry") {
        val unbindEntered = CountDownLatch(1)
        val releaseUnbind = CountDownLatch(1)
        val rescanFinished = CountDownLatch(1)
        val l = lifecycle(
            unbind = {
                unbindEntered.countDown()
                releaseUnbind.await(5, TimeUnit.SECONDS)
            },
        )
        l.startWithDaemons(mapOf(keyA to entryA))

        val stopThread = Thread { l.stop() }
        val rescanThread = Thread {
            try {
                l.replaceDaemons(mapOf(keyB to entryB))
            } finally {
                rescanFinished.countDown()
            }
        }
        try {
            stopThread.start()
            unbindEntered.await(5, TimeUnit.SECONDS) shouldBe true

            rescanThread.start()
            rescanFinished.await(100, TimeUnit.MILLISECONDS) shouldBe false
        } finally {
            releaseUnbind.countDown()
            stopThread.join(5_000)
            rescanThread.join(5_000)
        }

        stopThread.isAlive shouldBe false
        rescanThread.isAlive shouldBe false
        l.isStarted shouldBe false
        McpDaemonRegistry.active() shouldBe emptyMap()
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

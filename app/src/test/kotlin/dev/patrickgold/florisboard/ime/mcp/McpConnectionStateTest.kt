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

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Discovery acceptance used to be presented as "bound". These cases pin the real lifecycle: a
 * daemon only reports Connected when a binder actually arrived, only Connected dispatches, and
 * every refusal, disconnect and death is observable without restarting the IME.
 */
class McpConnectionStateTest : FunSpec({

    val keyA = DaemonKey(packageName = "com.daemon.a", daemonClassName = "com.daemon.a.Svc")
    val keyB = DaemonKey(packageName = "com.daemon.b", daemonClassName = "com.daemon.b.Svc")

    fun connection(): ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {}
        override fun onServiceDisconnected(name: ComponentName) {}
    }

    fun binder(): IBinder = StateTestBinder()

    fun observedTable(sink: MutableList<Pair<DaemonKey, McpDaemonConnectionState?>>) =
        McpServiceConnectionManager.BindingTable(onStateChanged = { key, state -> sink += key to state })

    test("a pending binding never exposes a binder or reports Connected") {
        val table = McpServiceConnectionManager.BindingTable()
        table.registerPending(keyA, connection())

        table.stateFor(keyA) shouldBe McpDaemonConnectionState.Pending
        table.binderFor(keyA) shouldBe null
        McpDaemonConnectionState.Pending.acceptsCalls shouldBe false
    }

    test("only a connected daemon dispatches") {
        val table = McpServiceConnectionManager.BindingTable()
        val live = binder()
        table.registerPending(keyA, connection())
        table.onConnected(keyA, live)

        table.stateFor(keyA) shouldBe McpDaemonConnectionState.Connected
        table.binderFor(keyA) shouldBe live

        table.onDisconnected(keyA)

        table.stateFor(keyA) shouldBe McpDaemonConnectionState.Pending
        table.binderFor(keyA) shouldBe null
    }

    test("a refused bind is Failed and keeps no half-registered row") {
        val table = McpServiceConnectionManager.BindingTable()
        table.onBindRefused(keyA)

        table.stateFor(keyA) shouldBe McpDaemonConnectionState.Failed
        table.hasBinding(keyA) shouldBe false
        table.binderFor(keyA) shouldBe null
    }

    test("a null binding is a refusal, not a transient disconnect") {
        val table = McpServiceConnectionManager.BindingTable()
        table.registerPending(keyA, connection())
        table.onNullBinding(keyA)

        table.stateFor(keyA) shouldBe McpDaemonConnectionState.Failed
        table.binderFor(keyA) shouldBe null
    }

    test("an exhausted rebind budget reports Dead and survives the unbind that follows it") {
        val table = McpServiceConnectionManager.BindingTable()
        table.registerPending(keyA, connection())
        table.onDead(keyA)
        table.removeBinding(keyA)

        table.stateFor(keyA) shouldBe McpDaemonConnectionState.Dead
        McpDaemonConnectionState.Dead.isRetryable shouldBe true
    }

    test("unbinding a healthy daemon drops its row entirely") {
        val table = McpServiceConnectionManager.BindingTable()
        table.registerPending(keyA, connection())
        table.onConnected(keyA, binder())
        table.removeBinding(keyA)

        table.stateFor(keyA) shouldBe null
        table.states() shouldBe emptyMap()
    }

    test("clear resets a terminal state so an explicit retry starts from scratch") {
        val table = McpServiceConnectionManager.BindingTable()
        table.onBindRefused(keyA)
        table.clear(keyA)

        table.stateFor(keyA) shouldBe null

        table.registerPending(keyA, connection())
        table.stateFor(keyA) shouldBe McpDaemonConnectionState.Pending
    }

    test("every transition is published exactly once, per daemon") {
        val observed = mutableListOf<Pair<DaemonKey, McpDaemonConnectionState?>>()
        val table = observedTable(observed)

        table.registerPending(keyA, connection())
        table.registerPending(keyB, connection())
        table.onConnected(keyA, binder())
        table.onBindRefused(keyB)

        observed shouldBe listOf(
            keyA to McpDaemonConnectionState.Pending,
            keyB to McpDaemonConnectionState.Pending,
            keyA to McpDaemonConnectionState.Connected,
            keyB to McpDaemonConnectionState.Failed,
        )
    }

    test("the store forgets daemons discovery no longer accepts") {
        McpConnectionStateStore.reset()
        McpConnectionStateStore.update(keyA, McpDaemonConnectionState.Connected)
        McpConnectionStateStore.update(keyB, McpDaemonConnectionState.Failed)

        McpConnectionStateStore.retainOnly(setOf(keyA))

        McpConnectionStateStore.active() shouldBe mapOf(keyA to McpDaemonConnectionState.Connected)

        McpConnectionStateStore.reset()
        McpConnectionStateStore.active() shouldBe emptyMap()
    }

    test("consent and per-daemon switches win over any recorded connection") {
        val states = mapOf(keyA to McpDaemonConnectionState.Connected)

        McpDaemonStatePolicy.resolve(keyA, bridgeEnabled = false, disabledPackages = emptySet(), connectionStates = states) shouldBe
            McpDaemonConnectionState.Disabled
        McpDaemonStatePolicy.resolve(
            keyA,
            bridgeEnabled = true,
            disabledPackages = setOf(keyA.packageName),
            connectionStates = states,
        ) shouldBe McpDaemonConnectionState.Disabled
        McpDaemonStatePolicy.resolve(keyA, bridgeEnabled = true, disabledPackages = emptySet(), connectionStates = states) shouldBe
            McpDaemonConnectionState.Connected
    }

    test("a discovered daemon with no recorded transition reads as Pending, never Connected") {
        McpDaemonStatePolicy.resolve(
            keyA,
            bridgeEnabled = true,
            disabledPackages = emptySet(),
            connectionStates = emptyMap(),
        ) shouldBe McpDaemonConnectionState.Pending
    }

    test("the connected count reflects binders, not discovery acceptance") {
        val states = mapOf(
            keyA to McpDaemonConnectionState.Connected,
            keyB to McpDaemonConnectionState.Dead,
        )

        McpDaemonStatePolicy.connectedCount(
            daemonKeys = listOf(keyA, keyB),
            bridgeEnabled = true,
            disabledPackages = emptySet(),
            connectionStates = states,
        ) shouldBe 1
        McpDaemonStatePolicy.connectedCount(
            daemonKeys = listOf(keyA, keyB),
            bridgeEnabled = true,
            disabledPackages = setOf(keyA.packageName),
            connectionStates = states,
        ) shouldBe 0
        McpDaemonStatePolicy.connectedCount(
            daemonKeys = listOf(keyA, keyB),
            bridgeEnabled = false,
            disabledPackages = emptySet(),
            connectionStates = states,
        ) shouldBe 0
    }

    test("only Failed and Dead offer a retry") {
        McpDaemonConnectionState.entries.filter { it.isRetryable } shouldBe listOf(
            McpDaemonConnectionState.Failed,
            McpDaemonConnectionState.Dead,
        )
        McpDaemonConnectionState.entries.filter { it.acceptsCalls } shouldBe listOf(
            McpDaemonConnectionState.Connected,
        )
    }
})

/** Opaque binder token; the table only ever compares identity. */
private class StateTestBinder : IBinder {
    override fun getInterfaceDescriptor(): String? = null
    override fun pingBinder(): Boolean = true
    override fun isBinderAlive(): Boolean = true
    override fun queryLocalInterface(descriptor: String): android.os.IInterface? = null
    override fun dump(fd: java.io.FileDescriptor, args: Array<out String>?) {}
    override fun dumpAsync(fd: java.io.FileDescriptor, args: Array<out String>?) {}
    override fun transact(code: Int, data: android.os.Parcel, reply: android.os.Parcel?, flags: Int): Boolean = false
    override fun linkToDeath(recipient: IBinder.DeathRecipient, flags: Int) {}
    override fun unlinkToDeath(recipient: IBinder.DeathRecipient, flags: Int): Boolean = true
}

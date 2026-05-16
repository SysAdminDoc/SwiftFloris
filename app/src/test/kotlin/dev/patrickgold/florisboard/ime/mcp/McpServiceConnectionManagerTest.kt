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
 * Pure-JVM coverage of [McpServiceConnectionManager.BindingTable].
 *
 * The Android-bound `bindService` / `unbindService` glue in the
 * enclosing manager isn't pure-JVM testable, but the in-memory state
 * machine — registerPending, onConnected, onDisconnected, removeBinding
 * — drives every observable behaviour and is exercised here.
 */
class McpServiceConnectionManagerTest : FunSpec({

    val keyA = DaemonKey(packageName = "com.daemon.a", daemonClassName = "com.daemon.a.Svc")
    val keyB = DaemonKey(packageName = "com.daemon.b", daemonClassName = "com.daemon.b.Svc")

    fun fakeConnection(): ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {}
        override fun onServiceDisconnected(name: ComponentName) {}
    }

    fun fakeBinder(): IBinder = FakeBinder()

    test("binderFor returns null for keys with no registered binding") {
        val table = McpServiceConnectionManager.BindingTable()
        table.binderFor(keyA) shouldBe null
        table.hasBinding(keyA) shouldBe false
    }

    test("registerPending records the connection but leaves binder null until onConnected") {
        val table = McpServiceConnectionManager.BindingTable()
        table.registerPending(keyA, fakeConnection())
        table.hasBinding(keyA) shouldBe true
        table.binderFor(keyA) shouldBe null
    }

    test("onConnected stores the live binder under the daemon key") {
        val table = McpServiceConnectionManager.BindingTable()
        val binder = fakeBinder()
        table.registerPending(keyA, fakeConnection())
        table.onConnected(keyA, binder)
        table.binderFor(keyA) shouldBe binder
    }

    test("onConnected is a no-op when the key has no pending binding") {
        val table = McpServiceConnectionManager.BindingTable()
        table.onConnected(keyA, fakeBinder())
        table.binderFor(keyA) shouldBe null
        table.hasBinding(keyA) shouldBe false
    }

    test("onDisconnected clears the binder but keeps the pending-binding row") {
        val table = McpServiceConnectionManager.BindingTable()
        table.registerPending(keyA, fakeConnection())
        table.onConnected(keyA, fakeBinder())
        table.onDisconnected(keyA)
        table.binderFor(keyA) shouldBe null
        table.hasBinding(keyA) shouldBe true
    }

    test("removeBinding returns the original connection and drops the row entirely") {
        val table = McpServiceConnectionManager.BindingTable()
        val connection = fakeConnection()
        table.registerPending(keyA, connection)
        table.onConnected(keyA, fakeBinder())
        val removed = table.removeBinding(keyA)
        removed shouldBe connection
        table.binderFor(keyA) shouldBe null
        table.hasBinding(keyA) shouldBe false
    }

    test("removeBinding returns null when the key was never registered") {
        val table = McpServiceConnectionManager.BindingTable()
        table.removeBinding(keyA) shouldBe null
    }

    test("activeKeys returns every registered daemon, before and after onConnected") {
        val table = McpServiceConnectionManager.BindingTable()
        table.activeKeys() shouldBe emptySet()
        table.registerPending(keyA, fakeConnection())
        table.registerPending(keyB, fakeConnection())
        table.activeKeys() shouldBe setOf(keyA, keyB)
        table.onConnected(keyA, fakeBinder())
        table.activeKeys() shouldBe setOf(keyA, keyB)
        table.removeBinding(keyA)
        table.activeKeys() shouldBe setOf(keyB)
    }

    test("per-key isolation — onDisconnected on keyA does not affect keyB") {
        val table = McpServiceConnectionManager.BindingTable()
        val binderA = fakeBinder()
        val binderB = fakeBinder()
        table.registerPending(keyA, fakeConnection())
        table.registerPending(keyB, fakeConnection())
        table.onConnected(keyA, binderA)
        table.onConnected(keyB, binderB)

        table.onDisconnected(keyA)

        table.binderFor(keyA) shouldBe null
        table.binderFor(keyB) shouldBe binderB
    }
})

/** Minimum-viable IBinder used as an opaque token in the table tests. */
private class FakeBinder : IBinder {
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

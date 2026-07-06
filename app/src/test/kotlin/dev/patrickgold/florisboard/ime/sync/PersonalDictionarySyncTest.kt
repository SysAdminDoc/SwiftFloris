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

package dev.patrickgold.florisboard.ime.sync

import dev.patrickgold.florisboard.ime.sync.PersonalDictionarySync.SyncDictionaryWord
import io.kotest.core.spec.style.FunSpec
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.io.ByteArrayInputStream

class PersonalDictionarySyncTest : FunSpec({

    fun word(text: String, freq: Int = 128, locale: String = "en-US", shortcut: String? = null) =
        SyncDictionaryWord(word = text, locale = locale, frequency = freq, shortcut = shortcut)

    fun deviceKeys(deviceId: String): Pair<PairedSyncDevice, java.security.KeyPair> {
        val keyPair = SealedBoxCrypto.generateKeyPair()
        val pubHex = keyPair.public.encoded.takeLast(32).toByteArray().toLowerHex()
        val device = PairedSyncDevice(
            deviceId = deviceId,
            displayName = "Device $deviceId",
            pubkeyHex = pubHex,
            syncChannelId = "swiftfloris:manual-export",
            pairedAtMillis = 1L,
        )
        return device to keyPair
    }

    test("reconcile keeps timestamps of unchanged words and stamps new ones") {
        val first = PersonalDictionarySync.reconcileLocalState(
            previous = null,
            words = listOf(word("hello")),
            deviceId = "dev-a",
            nowMillis = 1_000L,
        )
        first.entries.single().writtenAt shouldBe 1_000L

        val second = PersonalDictionarySync.reconcileLocalState(
            previous = first,
            words = listOf(word("hello"), word("world")),
            deviceId = "dev-a",
            nowMillis = 2_000L,
        )
        second.entries.first { it.word == "hello" }.writtenAt shouldBe 1_000L
        second.entries.first { it.word == "world" }.writtenAt shouldBe 2_000L
    }

    test("reconcile tombstones locally deleted words and lets local re-add resurrect them") {
        val first = PersonalDictionarySync.reconcileLocalState(
            previous = null,
            words = listOf(word("hello"), word("world")),
            deviceId = "dev-a",
            nowMillis = 1_000L,
        )
        val afterDelete = PersonalDictionarySync.reconcileLocalState(
            previous = first,
            words = listOf(word("hello")),
            deviceId = "dev-a",
            nowMillis = 2_000L,
        )
        afterDelete.tombstones.single().word shouldBe "world"
        afterDelete.tombstones.single().removedAt shouldBe 2_000L

        val afterReAdd = PersonalDictionarySync.reconcileLocalState(
            previous = afterDelete,
            words = listOf(word("hello"), word("world")),
            deviceId = "dev-a",
            nowMillis = 3_000L,
        )
        afterReAdd.tombstones.shouldBeEmpty()
        afterReAdd.entries.first { it.word == "world" }.writtenAt shouldBe 3_000L
    }

    test("envelope round-trip delivers the snapshot only to the addressed device") {
        val (deviceB, keysB) = deviceKeys("dev-b")
        val (deviceC, keysC) = deviceKeys("dev-c")
        val state = PersonalDictionarySync.reconcileLocalState(
            previous = null,
            words = listOf(word("hello", freq = 200)),
            deviceId = "dev-a",
            nowMillis = 1_000L,
        )
        val file = PersonalDictionarySync.sealEnvelopes(
            state = state,
            clusterId = "cluster-1",
            recipients = listOf(deviceB, deviceC),
            nowMillis = 1_500L,
        )
        val raw = file.serializeToString()

        val openedB = PersonalDictionarySync.openEnvelopeFor(raw, "dev-b", "cluster-1", keysB)
        openedB.shouldNotBeNull()
        openedB.entries.single().word shouldBe "hello"
        openedB.entries.single().frequency shouldBe 200

        // dev-c can open its own envelope but not authenticate as dev-b.
        PersonalDictionarySync.openEnvelopeFor(raw, "dev-c", "cluster-1", keysC).shouldNotBeNull()
        PersonalDictionarySync.openEnvelopeFor(raw, "dev-b", "cluster-1", keysC).shouldBeNull()
    }

    test("envelope open fails closed on cluster mismatch, tamper, and unknown recipient") {
        val (deviceB, keysB) = deviceKeys("dev-b")
        val state = PersonalDictionarySync.reconcileLocalState(
            previous = null,
            words = listOf(word("hello")),
            deviceId = "dev-a",
            nowMillis = 1_000L,
        )
        val file = PersonalDictionarySync.sealEnvelopes(state, "cluster-1", listOf(deviceB), 1_500L)
        val raw = file.serializeToString()

        PersonalDictionarySync.openEnvelopeFor(raw, "dev-b", "other-cluster", keysB).shouldBeNull()
        PersonalDictionarySync.openEnvelopeFor(raw, "dev-x", "cluster-1", keysB).shouldBeNull()
        PersonalDictionarySync.openEnvelopeFor("not json", "dev-b", "cluster-1", keysB).shouldBeNull()
        PersonalDictionarySync.openEnvelopeFor("""{"kind":"something-else","envelopes":[]}""", "dev-b", "cluster-1", keysB).shouldBeNull()

        // Flip one ciphertext byte — GCM tag must reject it.
        val envelope = file.envelopes.single()
        val tamperedBytes = envelope.sealedHex.hexToBytes()
        tamperedBytes[tamperedBytes.size - 1] = (tamperedBytes.last().toInt() xor 0x01).toByte()
        val tamperedFile = SyncEnvelopeFile(
            envelopes = listOf(envelope.copy(sealedHex = tamperedBytes.toLowerHex())),
        ).serializeToString()
        PersonalDictionarySync.openEnvelopeFor(tamperedFile, "dev-b", "cluster-1", keysB).shouldBeNull()
    }

    test("two devices converge on the merged dictionary including deletes") {
        // Device A knows hello+world; device B independently knows hello (edited freq) and added kotlin.
        val stateA1 = PersonalDictionarySync.reconcileLocalState(
            previous = null,
            words = listOf(word("hello", freq = 100), word("world", freq = 100)),
            deviceId = "dev-a",
            nowMillis = 1_000L,
        )
        val stateB1 = PersonalDictionarySync.reconcileLocalState(
            previous = null,
            words = listOf(word("hello", freq = 250), word("kotlin", freq = 90)),
            deviceId = "dev-b",
            nowMillis = 2_000L,
        )

        // Import B into A.
        val planA = PersonalDictionarySync.planImport(
            localState = stateA1,
            imported = stateB1,
            currentWords = listOf(word("hello", freq = 100), word("world", freq = 100)),
        )
        planA.newState.deviceId shouldBe "dev-a"
        planA.wordsToUpsert.map { it.word to it.frequency }.shouldContainExactly(
            listOf("hello" to 250, "kotlin" to 90),
        )
        planA.wordsToDelete.shouldBeEmpty()

        // B deletes world? B never had world; instead B deletes kotlin locally later,
        // then A imports B's newer state and must delete kotlin too.
        val stateB2 = PersonalDictionarySync.reconcileLocalState(
            previous = stateB1,
            words = listOf(word("hello", freq = 250)),
            deviceId = "dev-b",
            nowMillis = 3_000L,
        )
        val currentA = listOf(word("hello", freq = 250), word("world", freq = 100), word("kotlin", freq = 90))
        val planA2 = PersonalDictionarySync.planImport(
            localState = planA.newState,
            imported = stateB2,
            currentWords = currentA,
        )
        planA2.wordsToDelete.map { it.word }.shouldContainExactly(listOf("kotlin"))
        planA2.newState.entries.map { it.word }.shouldContainExactly(listOf("hello", "world"))

        // Convergence: importing A's state into B yields the same live set.
        val planB = PersonalDictionarySync.planImport(
            localState = stateB2,
            imported = planA2.newState,
            currentWords = listOf(word("hello", freq = 250)),
        )
        planB.newState.entries.map { it.word }.shouldContainExactly(listOf("hello", "world"))
        planB.wordsToUpsert.map { it.word }.shouldContainExactly(listOf("world"))
        planB.wordsToDelete.shouldBeEmpty()
    }

    test("import plan is a no-op when both sides already agree") {
        val state = PersonalDictionarySync.reconcileLocalState(
            previous = null,
            words = listOf(word("hello")),
            deviceId = "dev-a",
            nowMillis = 1_000L,
        )
        val plan = PersonalDictionarySync.planImport(
            localState = state,
            imported = state.copy(deviceId = "dev-b"),
            currentWords = listOf(word("hello")),
        )
        plan.isNoOp shouldBe true
    }

    test("sync JSON reader accepts files within the byte budget") {
        val json = """{"kind":"swiftfloris-dictionary-sync","envelopes":[]}"""

        SyncJsonTransferPolicy.readJsonTextLimited(
            inputStream = ByteArrayInputStream(json.toByteArray()),
            maxBytes = json.toByteArray().size.toLong(),
        ) shouldBe json
    }

    test("sync JSON reader rejects oversized files before parsing") {
        val bytes = ByteArray(9) { 'x'.code.toByte() }

        shouldThrow<IllegalStateException> {
            SyncJsonTransferPolicy.readJsonTextLimited(
                inputStream = ByteArrayInputStream(bytes),
                maxBytes = 8L,
            )
        }
    }
})

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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

private fun entry(word: String, freq: Int, t: Long, device: String, locale: String = "en") =
    CrdtEntry(word, locale, freq, null, t, device)

private fun tombstone(word: String, t: Long, device: String, locale: String = "en") =
    CrdtTombstone(word, locale, t, device)

class PersonalDictionaryCrdtTest : FunSpec({
    test("merging disjoint entry sets yields the union") {
        val left = PersonalDictionaryCrdt("A", 5, entries = listOf(entry("hello", 100, 1, "A")))
        val right = PersonalDictionaryCrdt("B", 3, entries = listOf(entry("world", 80, 2, "B")))
        val merged = PersonalDictionaryCrdtMerger.merge(left, right)
        merged.entries.map { it.word } shouldContainExactly listOf("hello", "world")
        merged.clock shouldBe 5
    }

    test("higher writtenAt wins on entry conflicts") {
        val left = PersonalDictionaryCrdt("A", 2, entries = listOf(entry("hello", 100, 5, "A")))
        val right = PersonalDictionaryCrdt("B", 9, entries = listOf(entry("hello", 200, 7, "B")))
        val merged = PersonalDictionaryCrdtMerger.merge(left, right)
        merged.entries.shouldNotBeNull().single().frequency shouldBe 200
        merged.entries.single().writtenBy shouldBe "B"
    }

    test("deterministic tie-break uses larger writtenBy lex value") {
        val left = PersonalDictionaryCrdt("A", 1, entries = listOf(entry("hi", 100, 5, "A")))
        val right = PersonalDictionaryCrdt("B", 1, entries = listOf(entry("hi", 50, 5, "B")))
        val merged = PersonalDictionaryCrdtMerger.merge(left, right)
        merged.entries.single().writtenBy shouldBe "B"
    }

    test("tombstone strictly newer than entry deletes it") {
        val left = PersonalDictionaryCrdt("A", 1, entries = listOf(entry("foo", 100, 5, "A")))
        val right = PersonalDictionaryCrdt("B", 1, tombstones = listOf(tombstone("foo", 7, "B")))
        val merged = PersonalDictionaryCrdtMerger.merge(left, right)
        merged.entries.shouldNotBeNull().size shouldBe 0
        merged.tombstones.size shouldBe 1
    }

    test("entry strictly newer than tombstone resurrects the word") {
        val left = PersonalDictionaryCrdt("A", 1, tombstones = listOf(tombstone("foo", 5, "A")))
        val right = PersonalDictionaryCrdt("B", 1, entries = listOf(entry("foo", 100, 7, "B")))
        val merged = PersonalDictionaryCrdtMerger.merge(left, right)
        merged.entries.map { it.word } shouldContainExactly listOf("foo")
        // Tombstone still survives the merge so a third device joining sees the history.
        merged.tombstones.size shouldBe 1
    }

    test("tombstone wins on writtenAt vs removedAt tie") {
        val left = PersonalDictionaryCrdt("A", 1, entries = listOf(entry("foo", 100, 5, "A")))
        val right = PersonalDictionaryCrdt("B", 1, tombstones = listOf(tombstone("foo", 5, "B")))
        val merged = PersonalDictionaryCrdtMerger.merge(left, right)
        // Entry written at 5 is NOT strictly newer than tombstone at 5 → delete wins.
        merged.entries.size shouldBe 0
    }

    test("merge is commutative") {
        val a = PersonalDictionaryCrdt(
            "A", 2,
            entries = listOf(entry("alpha", 100, 5, "A"), entry("beta", 80, 7, "A")),
            tombstones = listOf(tombstone("gamma", 3, "A")),
        )
        val b = PersonalDictionaryCrdt(
            "B", 4,
            entries = listOf(entry("beta", 200, 8, "B"), entry("gamma", 50, 4, "B")),
            tombstones = listOf(tombstone("alpha", 9, "B")),
        )
        val ab = PersonalDictionaryCrdtMerger.merge(a, b)
        val ba = PersonalDictionaryCrdtMerger.merge(b, a)
        ab shouldBe ba
    }

    test("merge is idempotent") {
        val a = PersonalDictionaryCrdt("A", 2, entries = listOf(entry("alpha", 100, 5, "A")))
        val once = PersonalDictionaryCrdtMerger.merge(a, a)
        val twice = PersonalDictionaryCrdtMerger.merge(once, a)
        once.entries shouldBe twice.entries
        once.tombstones shouldBe twice.tombstones
    }

    test("serializeToString round-trips through parse") {
        val original = PersonalDictionaryCrdt(
            "A", 5,
            entries = listOf(entry("hello", 100, 1, "A")),
            tombstones = listOf(tombstone("bye", 2, "A")),
        )
        val raw = original.serializeToString()
        val restored = PersonalDictionaryCrdt.parse(raw).shouldNotBeNull()
        restored shouldBe original
    }

    test("PairingPayload validates the Curve25519 pubkey shape") {
        val payload = PairingPayload(
            schema = 1,
            clusterId = "cluster-1",
            deviceId = "dev-A",
            pubkeyHex = "a".repeat(64),
            displayName = "Matt's Pixel 9",
            syncChannelId = "swiftfloris:syncthing:home",
            senderClock = 7,
        )
        val raw = payload.serializeToString()
        val restored = PairingPayload.parse(raw).shouldNotBeNull()
        restored shouldBe payload
    }

    test("PairingPayload rejects malformed pubkey hex") {
        // 63 chars instead of 64.
        runCatching {
            PairingPayload(1, "c", "d", "a".repeat(63), "n", "ch", 0)
        }.isFailure shouldBe true
        // Mixed case (validator requires lowercase).
        runCatching {
            PairingPayload(1, "c", "d", "A".repeat(64), "n", "ch", 0)
        }.isFailure shouldBe true
    }
})

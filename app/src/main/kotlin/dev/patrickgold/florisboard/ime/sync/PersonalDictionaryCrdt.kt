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

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * ROADMAP §7 Next-5.1 — per-device personal-dictionary file shape.
 *
 * SwiftFloris' CRDT-over-Syncthing sync strategy avoids a central vendor
 * account by giving every device its own file (`dict-<deviceId>.bin`) and
 * merging deltas on read. Conceptually we want full Automerge-rs JSON
 * CRDT semantics; for the scaffold tier this Kotlin data model captures
 * the **observed-add / last-write-wins delete** semantics that are good
 * enough for personal dictionaries where:
 *
 *  - Concurrent adds always commute (set union).
 *  - Concurrent edits of the same word's frequency take the highest seen
 *    timestamp's value (no need for fine-grained string CRDTs).
 *  - Concurrent delete-vs-add resolves by timestamp: a later add wins
 *    over an earlier remove, and vice versa.
 *
 * The actual on-disk representation is JSON-line based so a third-party
 * sync transport (Syncthing, Nextcloud, Resilio, even email) can copy
 * the file without parsing it. Real Automerge integration arrives in
 * Next-5.1a alongside the JNI bring-up of automerge-rs.
 */
@Serializable
data class PersonalDictionaryCrdt(
    /** Stable per-device identifier (UUID v4 chosen at first launch). */
    val deviceId: String,
    /** Monotonic Lamport-like counter, bumped on every local write. */
    val clock: Long,
    /** Schema version. Currently 1. */
    val schema: Int = 1,
    /** Word entries currently believed live on this device. */
    val entries: List<CrdtEntry> = emptyList(),
    /** Words this device has explicitly deleted. Tombstones survive
     *  cross-device sync until both devices have seen the deletion. */
    val tombstones: List<CrdtTombstone> = emptyList(),
) {
    init {
        require(deviceId.isNotBlank()) { "deviceId must not be blank" }
        require(clock >= 0) { "clock must be non-negative" }
        require(schema >= 1) { "schema must be >= 1" }
    }

    fun serializeToString(): String = JsonConfig.encodeToString(serializer(), this)

    companion object {
        /** Highest CRDT schema version this IME understands. */
        const val SUPPORTED_SCHEMA: Int = 1

        val JsonConfig = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            encodeDefaults = true
        }

        fun parse(rawJson: String): PersonalDictionaryCrdt? {
            if (rawJson.isBlank()) return null
            return runCatching { JsonConfig.decodeFromString(serializer(), rawJson) }
                .getOrNull()
        }
    }
}

@Serializable
data class CrdtEntry(
    val word: String,
    val locale: String,
    val frequency: Int,
    val shortcut: String? = null,
    /** Lamport timestamp at which this entry was last written. */
    val writtenAt: Long,
    /** Origin device id (used as a tie-break in equal-clock conflicts). */
    val writtenBy: String,
) {
    init {
        require(word.isNotBlank()) { "word must not be blank" }
        require(locale.isNotBlank()) { "locale must not be blank" }
        require(frequency in 0..255) { "frequency must be 0..255; was $frequency" }
        require(writtenAt >= 0) { "writtenAt must be non-negative" }
        require(writtenBy.isNotBlank()) { "writtenBy must not be blank" }
    }
}

@Serializable
data class CrdtTombstone(
    val word: String,
    val locale: String,
    val removedAt: Long,
    val removedBy: String,
) {
    init {
        require(word.isNotBlank()) { "word must not be blank" }
        require(locale.isNotBlank()) { "locale must not be blank" }
        require(removedAt >= 0) { "removedAt must be non-negative" }
        require(removedBy.isNotBlank()) { "removedBy must not be blank" }
    }
}

/**
 * Stateless merge of two device snapshots. The result's `deviceId` is
 * derived deterministically as the **lexicographically larger** of the
 * two inputs' device ids so commutative tests pass — call
 * [PersonalDictionaryCrdt.copy] before persisting if the caller wants
 * the merged snapshot stored under its own device id.
 *
 * Merge rules (see class doc on [PersonalDictionaryCrdt]):
 *  1. Entry conflicts: pick the higher `writtenAt`; on tie, pick the
 *     larger `writtenBy` lex value so the result is deterministic.
 *  2. Delete-vs-add: compare `tombstone.removedAt` vs `entry.writtenAt`;
 *     a strictly later write resurrects the word, otherwise the
 *     tombstone wins.
 *  3. Tombstones survive: both inputs' tombstones carry into the output
 *     so a third device joining the cluster eventually applies the
 *     deletion.
 */
object PersonalDictionaryCrdtMerger {

    fun merge(
        left: PersonalDictionaryCrdt,
        right: PersonalDictionaryCrdt,
    ): PersonalDictionaryCrdt {
        val entriesByKey = HashMap<EntryKey, CrdtEntry>()
        val tombstonesByKey = HashMap<EntryKey, CrdtTombstone>()

        for (entry in left.entries + right.entries) {
            val key = EntryKey(entry.word, entry.locale)
            val existing = entriesByKey[key]
            if (existing == null || entry.writtenAt > existing.writtenAt ||
                (entry.writtenAt == existing.writtenAt && entry.writtenBy > existing.writtenBy)
            ) {
                entriesByKey[key] = entry
            }
        }
        for (tomb in left.tombstones + right.tombstones) {
            val key = EntryKey(tomb.word, tomb.locale)
            val existing = tombstonesByKey[key]
            if (existing == null || tomb.removedAt > existing.removedAt ||
                (tomb.removedAt == existing.removedAt && tomb.removedBy > existing.removedBy)
            ) {
                tombstonesByKey[key] = tomb
            }
        }
        // Resolve entry vs tombstone: pick whichever is *strictly* newer.
        // Tombstone wins on tie because the user's last action they
        // explicitly intended was the delete.
        val live = mutableListOf<CrdtEntry>()
        for ((key, entry) in entriesByKey) {
            val tomb = tombstonesByKey[key]
            if (tomb == null || entry.writtenAt > tomb.removedAt) {
                live += entry
            }
        }
        return PersonalDictionaryCrdt(
            deviceId = maxOf(left.deviceId, right.deviceId),
            clock = maxOf(left.clock, right.clock),
            schema = maxOf(left.schema, right.schema),
            entries = live.sortedWith(compareBy({ it.locale }, { it.word })),
            tombstones = tombstonesByKey.values.sortedWith(compareBy({ it.locale }, { it.word })),
        )
    }

    private data class EntryKey(val word: String, val locale: String)
}

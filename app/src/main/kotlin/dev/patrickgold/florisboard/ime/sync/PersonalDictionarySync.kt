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

import android.os.Build
import androidx.annotation.RequiresApi
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.KeyPair
import java.security.MessageDigest
import java.security.PrivateKey
import java.util.Arrays
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * ROADMAP P2 — end-to-end personal-dictionary sync engine over the existing
 * file channels (LocalFolder / ManualExport / Syncthing-managed folder).
 *
 * Pure logic tier: everything here is JVM-testable. The Android tier
 * (SAF reads/writes, DAO application, Settings UI) calls into this object
 * and owns all side effects.
 *
 * Data flow per export:
 *  1. [reconcileLocalState] diffs the live user-dictionary rows against the
 *     previously persisted local CRDT state — unchanged words keep their
 *     original `writtenAt` (so a peer's later tombstone still wins),
 *     new/edited words stamp `now`, and words that disappeared since the
 *     last reconcile become tombstones. This is what makes deletes sync
 *     without hooking every DAO call site.
 *  2. [sealEnvelopes] serializes the state and seals one
 *     [SealedBoxCrypto] envelope per paired device into a single
 *     [SyncEnvelopeFile] JSON document, so one file on the shared channel
 *     serves the whole cluster.
 *
 * Per import:
 *  3. [openEnvelopeFor] picks the envelope addressed to this device,
 *     validates cluster + schema, opens the sealed box, and parses the
 *     CRDT — every failure path returns null (fail closed).
 *  4. [planImport] merges the remote snapshot into the local state via
 *     [PersonalDictionaryCrdtMerger] and emits the exact word lists the
 *     Android tier must upsert/delete in the DAO, plus the new local state
 *     to persist.
 */
object PersonalDictionarySync {

    /** DAO-decoupled view of one user-dictionary row. */
    data class SyncDictionaryWord(
        val word: String,
        val locale: String,
        val frequency: Int,
        val shortcut: String?,
    )

    data class ImportPlan(
        val newState: PersonalDictionaryCrdt,
        val wordsToUpsert: List<SyncDictionaryWord>,
        val wordsToDelete: List<SyncDictionaryWord>,
    ) {
        val isNoOp: Boolean get() = wordsToUpsert.isEmpty() && wordsToDelete.isEmpty()
    }

    fun reconcileLocalState(
        previous: PersonalDictionaryCrdt?,
        words: List<SyncDictionaryWord>,
        deviceId: String,
        nowMillis: Long,
    ): PersonalDictionaryCrdt {
        val previousEntries = previous?.entries.orEmpty().associateBy { it.word to it.locale }
        val liveKeys = HashSet<Pair<String, String>>(words.size)
        val entries = words.mapNotNull { word ->
            if (word.word.isBlank() || word.locale.isBlank()) return@mapNotNull null
            val key = word.word to word.locale
            liveKeys += key
            val prior = previousEntries[key]
            if (prior != null && prior.frequency == word.frequency && prior.shortcut == word.shortcut) {
                // Unchanged since the last reconcile — keep the original
                // timestamp so a peer's later delete still wins the merge.
                prior
            } else {
                CrdtEntry(
                    word = word.word,
                    locale = word.locale,
                    frequency = word.frequency.coerceIn(0, 255),
                    shortcut = word.shortcut,
                    writtenAt = nowMillis,
                    writtenBy = deviceId,
                )
            }
        }
        // Words that were in the previous state but are gone from the live
        // dictionary were deleted locally since the last reconcile.
        val newTombstones = previous?.entries.orEmpty()
            .filter { (it.word to it.locale) !in liveKeys }
            .map { CrdtTombstone(word = it.word, locale = it.locale, removedAt = nowMillis, removedBy = deviceId) }
        // Carry prior tombstones unless the word came back to life locally.
        val carriedTombstones = previous?.tombstones.orEmpty()
            .filter { (it.word to it.locale) !in liveKeys }
        val tombstones = (carriedTombstones + newTombstones)
            .groupBy { it.word to it.locale }
            .map { (_, candidates) -> candidates.maxWith(compareBy({ it.removedAt }, { it.removedBy })) }
        return PersonalDictionaryCrdt(
            deviceId = deviceId,
            clock = maxOf(previous?.clock?.plus(1) ?: 0L, nowMillis),
            entries = entries.sortedWith(compareBy({ it.locale }, { it.word })),
            tombstones = tombstones.sortedWith(compareBy({ it.locale }, { it.word })),
        )
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun sealEnvelopes(
        state: PersonalDictionaryCrdt,
        clusterId: String,
        recipients: List<PairedSyncDevice>,
        senderKeyPair: KeyPair,
        nowMillis: Long,
    ): SyncEnvelopeFile {
        val plaintext = state.serializeToString().toByteArray(Charsets.UTF_8)
        val envelopes = try {
            recipients.map { recipient ->
                val unsignedEnvelope = SyncEnvelope(
                    schema = SyncEnvelope.SUPPORTED_SCHEMA,
                    clusterId = clusterId,
                    senderDeviceId = state.deviceId,
                    recipientDeviceId = recipient.deviceId,
                    createdAtMillis = nowMillis,
                    sealedHex = SealedBoxCrypto.seal(plaintext, recipient.pubkeyHex.hexToBytes()).toLowerHex(),
                )
                unsignedEnvelope.copy(
                    authHex = computeEnvelopeAuthHex(
                        envelope = unsignedEnvelope,
                        privateKey = senderKeyPair.private,
                        peerPublicKeyHex = recipient.pubkeyHex,
                    ),
                )
            }
        } finally {
            Arrays.fill(plaintext, 0.toByte())
        }
        return SyncEnvelopeFile(envelopes = envelopes)
    }

    /**
     * Returns the decrypted remote snapshot addressed to [myDeviceId], or
     * null when no envelope matches, the cluster differs, the schema is
     * newer than we understand, decryption fails, or the payload is not a
     * valid CRDT document. Every failure is indistinguishable by design.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun openEnvelopeFor(
        rawFileJson: String,
        myDeviceId: String,
        expectedClusterId: String,
        recipientKeyPair: KeyPair,
        trustedSenders: List<PairedSyncDevice>,
    ): PersonalDictionaryCrdt? {
        val file = SyncEnvelopeFile.parse(rawFileJson) ?: return null
        val trustedByDeviceId = trustedSenders.associateBy { it.deviceId }
        for (envelope in file.envelopes) {
            if (
                envelope.recipientDeviceId != myDeviceId ||
                envelope.clusterId != expectedClusterId ||
                envelope.schema != SyncEnvelope.SUPPORTED_SCHEMA ||
                envelope.senderDeviceId == myDeviceId
            ) {
                continue
            }
            val sender = trustedByDeviceId[envelope.senderDeviceId] ?: continue
            if (!verifyEnvelopeAuth(envelope, recipientKeyPair, sender)) continue
            val sealed = envelope.sealedHex.hexToBytesOrNull() ?: continue
            val plaintext = SealedBoxCrypto.open(sealed, recipientKeyPair) ?: continue
            val parsed = PersonalDictionaryCrdt.parse(plaintext.toString(Charsets.UTF_8)) ?: continue
            if (
                parsed.schema > PersonalDictionaryCrdt.SUPPORTED_SCHEMA ||
                parsed.deviceId != envelope.senderDeviceId
            ) {
                continue
            }
            return parsed
        }
        return null
    }

    /**
     * Merge [imported] into [localState] and compute the DAO mutations.
     * [currentWords] is the live dictionary snapshot the upsert/delete
     * lists are diffed against, so the Android tier applies only real
     * changes.
     */
    fun planImport(
        localState: PersonalDictionaryCrdt,
        imported: PersonalDictionaryCrdt,
        currentWords: List<SyncDictionaryWord>,
    ): ImportPlan {
        val merged = PersonalDictionaryCrdtMerger.merge(localState, imported)
            .copy(deviceId = localState.deviceId)
        val currentByKey = currentWords.associateBy { it.word to it.locale }
        val mergedKeys = merged.entries.map { it.word to it.locale }.toHashSet()
        val wordsToUpsert = merged.entries.mapNotNull { entry ->
            val current = currentByKey[entry.word to entry.locale]
            val desired = SyncDictionaryWord(
                word = entry.word,
                locale = entry.locale,
                frequency = entry.frequency,
                shortcut = entry.shortcut,
            )
            desired.takeIf { current != desired }
        }
        val wordsToDelete = currentWords.filter { (it.word to it.locale) !in mergedKeys }
        return ImportPlan(
            newState = merged,
            wordsToUpsert = wordsToUpsert,
            wordsToDelete = wordsToDelete,
        )
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun computeEnvelopeAuthHex(
    envelope: SyncEnvelope,
    privateKey: PrivateKey,
    peerPublicKeyHex: String,
): String {
    var peerPublicKey: ByteArray? = null
    var key: ByteArray? = null
    var auth: ByteArray? = null
    return try {
        val peerPub = peerPublicKeyHex.hexToBytes()
        peerPublicKey = peerPub
        val authKey = SealedBoxCrypto.deriveAuthenticationKey(privateKey, peerPub)
        key = authKey
        val tag = hmacSha256(authKey, envelope.authenticationData())
        auth = tag
        tag.toLowerHex()
    } finally {
        peerPublicKey?.let { Arrays.fill(it, 0.toByte()) }
        key?.let { Arrays.fill(it, 0.toByte()) }
        auth?.let { Arrays.fill(it, 0.toByte()) }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun verifyEnvelopeAuth(
    envelope: SyncEnvelope,
    recipientKeyPair: KeyPair,
    sender: PairedSyncDevice,
): Boolean {
    var expectedAuth: ByteArray? = null
    var senderPublicKey: ByteArray? = null
    var key: ByteArray? = null
    return try {
        val expected = envelope.authHex?.hexToBytesOrNull() ?: return false
        expectedAuth = expected
        if (expected.size != AUTH_TAG_BYTES) return false
        val senderPub = sender.pubkeyHex.hexToBytesOrNull() ?: return false
        senderPublicKey = senderPub
        val authKey = SealedBoxCrypto.deriveAuthenticationKey(
            privateKey = recipientKeyPair.private,
            peerPublicKeyRaw = senderPub,
        )
        key = authKey
        val actualAuth = hmacSha256(authKey, envelope.authenticationData())
        try {
            MessageDigest.isEqual(expected, actualAuth)
        } finally {
            Arrays.fill(actualAuth, 0.toByte())
        }
    } catch (_: Throwable) {
        false
    } finally {
        expectedAuth?.let { Arrays.fill(it, 0.toByte()) }
        senderPublicKey?.let { Arrays.fill(it, 0.toByte()) }
        key?.let { Arrays.fill(it, 0.toByte()) }
    }
}

private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
    val mac = Mac.getInstance(HMAC_ALGORITHM)
    mac.init(SecretKeySpec(key, HMAC_ALGORITHM))
    return mac.doFinal(data)
}

private fun SyncEnvelope.authenticationData(): ByteArray {
    val out = ByteArrayOutputStream()
    fun writeField(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        out.write(bytes.size.toString().toByteArray(Charsets.US_ASCII))
        out.write(':'.code)
        out.write(bytes)
        out.write('\n'.code)
    }
    writeField(schema.toString())
    writeField(clusterId)
    writeField(senderDeviceId)
    writeField(recipientDeviceId)
    writeField(createdAtMillis.toString())
    writeField(sealedHex)
    return out.toByteArray()
}

object SyncJsonTransferPolicy {
    const val MaxFileBytes: Long = 16L * 1024L * 1024L

    fun readJsonTextLimited(inputStream: InputStream, maxBytes: Long = MaxFileBytes): String {
        require(maxBytes > 0L) { "Argument `maxBytes` must be greater than 0" }
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = inputStream.read(buffer)
            if (read < 0) break
            total += read.toLong()
            if (total > maxBytes) {
                error("Sync JSON exceeds the ${maxBytes / (1024L * 1024L)} MiB safety limit.")
            }
            out.write(buffer, 0, read)
        }
        return out.toString(Charsets.UTF_8.name())
    }
}

/**
 * One sealed CRDT snapshot addressed to a single paired device. Several of
 * these (one per cluster member) ride in a [SyncEnvelopeFile] so a single
 * document on the shared folder serves every device.
 */
@Serializable
data class SyncEnvelope(
    val schema: Int,
    val clusterId: String,
    val senderDeviceId: String,
    val recipientDeviceId: String,
    val createdAtMillis: Long,
    val sealedHex: String,
    val authHex: String? = null,
) {
    init {
        require(schema >= 1) { "schema must be >= 1" }
        require(clusterId.isNotBlank()) { "clusterId must not be blank" }
        require(senderDeviceId.isNotBlank()) { "senderDeviceId must not be blank" }
        require(recipientDeviceId.isNotBlank()) { "recipientDeviceId must not be blank" }
        require(createdAtMillis >= 0) { "createdAtMillis must be non-negative" }
        require(sealedHex.matches(HEX_REGEX)) { "sealedHex must be lowercase hex" }
        if (authHex != null) {
            require(authHex.matches(AUTH_HEX_REGEX)) { "authHex must be a lowercase HMAC-SHA256 hex string" }
        }
    }

    companion object {
        const val SUPPORTED_SCHEMA: Int = 2
        private val HEX_REGEX = Regex("^(?:[0-9a-f]{2})+$")
        private val AUTH_HEX_REGEX = Regex("^[0-9a-f]{64}$")
    }
}

@Serializable
data class SyncEnvelopeFile(
    val kind: String = KIND,
    val envelopes: List<SyncEnvelope> = emptyList(),
) {
    fun serializeToString(): String = JsonConfig.encodeToString(serializer(), this)

    companion object {
        const val KIND = "swiftfloris-dictionary-sync"

        private val JsonConfig = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            encodeDefaults = true
        }

        fun parse(rawJson: String): SyncEnvelopeFile? {
            if (rawJson.isBlank()) return null
            return runCatching { JsonConfig.decodeFromString(serializer(), rawJson) }
                .getOrNull()
                ?.takeIf { it.kind == KIND }
        }
    }
}

internal fun ByteArray.toLowerHex(): String =
    joinToString(separator = "") { byte -> String.format(Locale.ROOT, "%02x", byte.toInt() and 0xff) }

internal fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0) { "hex string must have even length" }
    return ByteArray(length / 2) { i ->
        val hi = Character.digit(this[i * 2], 16)
        val lo = Character.digit(this[i * 2 + 1], 16)
        require(hi >= 0 && lo >= 0) { "invalid hex character at index ${i * 2}" }
        ((hi shl 4) + lo).toByte()
    }
}

internal fun String.hexToBytesOrNull(): ByteArray? =
    runCatching { hexToBytes() }.getOrNull()?.takeIf { it.isNotEmpty() }

private const val AUTH_TAG_BYTES = 32
private const val HMAC_ALGORITHM = "HmacSHA256"

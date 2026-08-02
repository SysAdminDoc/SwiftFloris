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

import com.google.crypto.tink.Aead
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets.UTF_8
import java.security.GeneralSecurityException
import java.util.Base64

/** Why a persisted sync identity could not be opened. Never carries key material. */
enum class SyncIdentityFailure {
    /** The file is unreadable, truncated, or not a sync identity at all. */
    Corrupt,

    /** The wrapping key is gone or the ciphertext was tampered with; the private key is lost. */
    KeyUnavailable,

    /** The identity belongs to a different device id than the one configured now. */
    DeviceIdMismatch,
}

/** Raised by [SyncIdentityEnvelope.decode]; [failure] classifies the recovery path. */
class SyncIdentityDecodeException(
    val failure: SyncIdentityFailure,
    cause: Throwable? = null,
) : Exception("Sync identity could not be decoded: $failure", cause)

/**
 * Wire format for the persisted sync identity.
 *
 * Schema 1 stored the PKCS#8 private key as plain Base64. Schema 2 wraps it with an
 * Android-Keystore-held AEAD and binds the ciphertext to the device id through associated data, so
 * a stolen file cannot be replayed under a different identity. The public half stays in the clear:
 * peers already pinned it from the QR payload, and rewrapping must never change it.
 *
 * The [Aead] is injected so the whole round trip — including tamper and mismatch rejection — is
 * unit-testable against a JVM Tink key, independent of the keystore binding production uses.
 */
internal object SyncIdentityEnvelope {
    const val SCHEMA_PLAINTEXT = 1
    const val SCHEMA_WRAPPED = 2

    private val PublicKeyHexRegex = Regex("^[0-9a-f]{64}$")

    private val JsonConfig = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    @Serializable
    internal data class Persisted(
        val schema: Int = SCHEMA_WRAPPED,
        val deviceId: String,
        val publicKeyHex: String,
        /** Schema 1 only — retained so an existing identity can be migrated in place. */
        val privateKeyPkcs8Base64: String? = null,
        /** Schema 2 — AEAD ciphertext over the PKCS#8 bytes. */
        val privateKeyWrappedBase64: String? = null,
    )

    /** Decoded identity material plus whether it still has to be rewritten in the wrapped schema. */
    internal data class Decoded(
        val deviceId: String,
        val publicKeyHex: String,
        val privateKeyPkcs8: ByteArray,
        val needsRewrap: Boolean,
    )

    /** Binds a ciphertext to the identity it belongs to. */
    fun associatedData(deviceId: String): ByteArray = "sync-identity:$deviceId".toByteArray(UTF_8)

    fun encode(aead: Aead, deviceId: String, publicKeyHex: String, privateKeyPkcs8: ByteArray): String {
        val wrapped = aead.encrypt(privateKeyPkcs8, associatedData(deviceId))
        return JsonConfig.encodeToString(
            Persisted.serializer(),
            Persisted(
                schema = SCHEMA_WRAPPED,
                deviceId = deviceId,
                publicKeyHex = publicKeyHex,
                privateKeyWrappedBase64 = Base64.getEncoder().encodeToString(wrapped),
            ),
        )
    }

    /**
     * Parses [json] and returns the private key material.
     *
     * @throws SyncIdentityDecodeException with the failure class the caller has to react to. A
     *  failure never causes a rewrite: the stored identity is the only copy of a key peers pinned.
     */
    fun decode(aead: Aead, json: String, expectedDeviceId: String): Decoded {
        val persisted = try {
            JsonConfig.decodeFromString(Persisted.serializer(), json)
        } catch (error: Exception) {
            throw SyncIdentityDecodeException(SyncIdentityFailure.Corrupt, error)
        }
        if (persisted.deviceId != expectedDeviceId) {
            throw SyncIdentityDecodeException(SyncIdentityFailure.DeviceIdMismatch)
        }
        if (!persisted.publicKeyHex.matches(PublicKeyHexRegex)) {
            throw SyncIdentityDecodeException(SyncIdentityFailure.Corrupt)
        }
        return when (persisted.schema) {
            SCHEMA_PLAINTEXT -> {
                val encoded = persisted.privateKeyPkcs8Base64
                    ?: throw SyncIdentityDecodeException(SyncIdentityFailure.Corrupt)
                Decoded(
                    deviceId = persisted.deviceId,
                    publicKeyHex = persisted.publicKeyHex,
                    privateKeyPkcs8 = decodeBase64(encoded),
                    needsRewrap = true,
                )
            }
            SCHEMA_WRAPPED -> {
                val wrapped = persisted.privateKeyWrappedBase64
                    ?: throw SyncIdentityDecodeException(SyncIdentityFailure.Corrupt)
                val plaintext = try {
                    aead.decrypt(decodeBase64(wrapped), associatedData(persisted.deviceId))
                } catch (error: GeneralSecurityException) {
                    throw SyncIdentityDecodeException(SyncIdentityFailure.KeyUnavailable, error)
                }
                Decoded(
                    deviceId = persisted.deviceId,
                    publicKeyHex = persisted.publicKeyHex,
                    privateKeyPkcs8 = plaintext,
                    needsRewrap = false,
                )
            }
            else -> throw SyncIdentityDecodeException(SyncIdentityFailure.Corrupt)
        }
    }

    private fun decodeBase64(value: String): ByteArray {
        return try {
            Base64.getDecoder().decode(value)
        } catch (error: IllegalArgumentException) {
            throw SyncIdentityDecodeException(SyncIdentityFailure.Corrupt, error)
        }
    }
}

/**
 * Durable single-file writer for identity-class data.
 *
 * The previous implementation deleted the target before its second rename attempt, so a failure at
 * that point destroyed the only valid identity on the device. Writes now stage a temp file, keep
 * the previous contents in a sibling backup until the replacement is in place, and roll the backup
 * forward again if the swap fails. [readPreferringBackup] completes the contract by falling back to
 * the backup when the primary no longer parses.
 */
internal object DurableIdentityFile {
    private const val TEMP_SUFFIX = ".tmp"
    private const val BACKUP_SUFFIX = ".bak"

    fun backupOf(target: File): File = File(target.parentFile, target.name + BACKUP_SUFFIX)

    fun tempOf(target: File): File = File(target.parentFile, target.name + TEMP_SUFFIX)

    /**
     * Writes [content] to [target], never leaving the caller without a readable copy.
     *
     * @throws IOException when the replacement could not be put in place. The previous contents are
     *  restored before throwing.
     */
    fun write(target: File, content: String) {
        val temp = tempOf(target)
        val backup = backupOf(target)
        temp.delete()
        FileOutputStream(temp).use { stream ->
            stream.write(content.toByteArray(UTF_8))
            stream.flush()
            stream.fd.sync()
        }
        val hadPrevious = target.isFile
        if (hadPrevious) {
            backup.delete()
            if (!target.renameTo(backup)) {
                // Rename-over can fail on some storage backends; a copy is still a valid fallback
                // because it keeps the previous bytes reachable.
                target.copyTo(backup, overwrite = true)
                target.delete()
            }
        }
        if (!temp.renameTo(target)) {
            if (hadPrevious && !backup.renameTo(target)) {
                backup.copyTo(target, overwrite = true)
            }
            temp.delete()
            throw IOException("Failed to persist ${target.name}")
        }
        temp.delete()
        backup.delete()
    }

    /** Copies of [target] to try, in order of preference. Only existing files are returned. */
    fun readableCopies(target: File): List<File> = listOf(target, backupOf(target)).filter { it.isFile }

    /** True when a primary or backup copy exists, whether or not it can still be parsed. */
    fun exists(target: File): Boolean = target.isFile || backupOf(target).isFile

    /** Removes every copy of [target]. Used only by the explicit re-pair reset. */
    fun deleteAll(target: File): Boolean {
        val primaryGone = !target.exists() || target.delete()
        val backup = backupOf(target)
        val backupGone = !backup.exists() || backup.delete()
        tempOf(target).delete()
        return primaryGone && backupGone
    }
}

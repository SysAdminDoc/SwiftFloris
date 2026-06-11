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

import android.content.Context
import android.os.Build
import android.util.Base64
import androidx.annotation.RequiresApi
import java.io.File
import java.security.KeyFactory
import java.security.KeyPair
import java.security.spec.NamedParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.XECPublicKeySpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * ROADMAP P2 — persistent local sync identity (device id + long-lived
 * X25519 keypair).
 *
 * Before this store existed, [PairingPayloadGenerator]'s default argument
 * minted a FRESH keypair per QR generation and the private half was
 * dropped on the floor — peers would seal envelopes to a public key no
 * device could ever decrypt. The identity now lives in
 * `filesDir/sync/identity.json`:
 *
 *  - app-private storage, same trust tier as the personal dictionary DB
 *    and persisted addon pins;
 *  - excluded from auto-backup and device-transfer archives (see
 *    `backup_rules.xml` / `data_extraction_rules.xml`) so the private key
 *    never rides into a user-shareable backup zip — devices pair via QR,
 *    they don't clone identities.
 *
 * Files are written atomically (tmp + rename) so a crash mid-write cannot
 * destroy an identity that peers already pinned.
 */
object SyncIdentityStore {

    data class SyncIdentity(
        val deviceId: String,
        val publicKeyHex: String,
        val keyPair: KeyPair,
    )

    @Serializable
    private data class PersistedIdentity(
        val schema: Int = 1,
        val deviceId: String,
        val publicKeyHex: String,
        val privateKeyPkcs8Base64: String,
    )

    private val JsonConfig = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    private const val SYNC_DIR_NAME = "sync"
    private const val IDENTITY_FILE_NAME = "identity.json"
    private const val STATE_FILE_NAME = "state.json"
    private const val X25519_ALGORITHM = "X25519"
    private const val PUBKEY_LENGTH = 32

    private val lock = Any()

    /**
     * Load the persisted identity, or mint and persist a new one. Returns
     * null only when the platform lacks X25519 (API < 33) or persistence
     * fails — callers surface that as "pairing unavailable".
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun getOrCreate(context: Context, deviceId: String): SyncIdentity? = synchronized(lock) {
        val file = identityFile(context)
        load(file, deviceId)?.let { return it }
        return runCatching {
            val keyPair = SealedBoxCrypto.generateKeyPair()
            val publicKeyHex = keyPair.public.encoded.takeLast(PUBKEY_LENGTH).toByteArray().toLowerHex()
            val persisted = PersistedIdentity(
                deviceId = deviceId,
                publicKeyHex = publicKeyHex,
                privateKeyPkcs8Base64 = Base64.encodeToString(keyPair.private.encoded, Base64.NO_WRAP),
            )
            writeAtomically(file, JsonConfig.encodeToString(PersistedIdentity.serializer(), persisted))
            SyncIdentity(deviceId = deviceId, publicKeyHex = publicKeyHex, keyPair = keyPair)
        }.getOrNull()
    }

    /** The locally persisted CRDT state from the last reconcile, if any. */
    fun loadLocalState(context: Context): PersonalDictionaryCrdt? = synchronized(lock) {
        val file = stateFile(context)
        if (!file.isFile) return null
        return runCatching { PersonalDictionaryCrdt.parse(file.readText(Charsets.UTF_8)) }.getOrNull()
    }

    fun saveLocalState(context: Context, state: PersonalDictionaryCrdt): Boolean = synchronized(lock) {
        return runCatching {
            writeAtomically(stateFile(context), state.serializeToString())
            true
        }.getOrDefault(false)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun load(file: File, expectedDeviceId: String): SyncIdentity? {
        if (!file.isFile) return null
        return runCatching {
            val persisted = JsonConfig.decodeFromString(PersistedIdentity.serializer(), file.readText(Charsets.UTF_8))
            // A device-id mismatch means prefs were reset/restored under a
            // different identity than the keypair was minted for; peers pin
            // (deviceId, pubkey) pairs from the QR payload, so fail closed
            // and let the caller mint a fresh identity via re-pairing.
            if (persisted.deviceId != expectedDeviceId) return null
            if (!persisted.publicKeyHex.matches(Regex("^[0-9a-f]{64}$"))) return null
            val factory = KeyFactory.getInstance(X25519_ALGORITHM)
            val privateKey = factory.generatePrivate(
                PKCS8EncodedKeySpec(Base64.decode(persisted.privateKeyPkcs8Base64, Base64.NO_WRAP)),
            )
            val publicKey = factory.generatePublic(
                XECPublicKeySpec(
                    NamedParameterSpec(X25519_ALGORITHM),
                    java.math.BigInteger(1, persisted.publicKeyHex.hexToBytes().reversedArray()),
                ),
            )
            SyncIdentity(
                deviceId = persisted.deviceId,
                publicKeyHex = persisted.publicKeyHex,
                keyPair = KeyPair(publicKey, privateKey),
            )
        }.getOrNull()
    }

    private fun identityFile(context: Context): File = syncDir(context).resolve(IDENTITY_FILE_NAME)

    private fun stateFile(context: Context): File = syncDir(context).resolve(STATE_FILE_NAME)

    private fun syncDir(context: Context): File =
        context.filesDir.resolve(SYNC_DIR_NAME).also { it.mkdirs() }

    private fun writeAtomically(target: File, content: String) {
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeText(content, Charsets.UTF_8)
        if (!tmp.renameTo(target)) {
            // Windows-style rename-over-existing failure path; fall back to
            // delete + rename so the identity is never half-written.
            target.delete()
            check(tmp.renameTo(target)) { "Failed to persist ${target.name}" }
        }
    }
}

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
import androidx.annotation.RequiresApi
import com.google.crypto.tink.Aead
import com.google.crypto.tink.integration.android.AndroidKeystore
import dev.patrickgold.florisboard.lib.devtools.flogError
import java.io.File
import java.security.KeyFactory
import java.security.KeyPair
import java.security.spec.NamedParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.XECPublicKeySpec

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
 * The PKCS#8 private half is wrapped with an AndroidKeystore-held AES-GCM key
 * (see [SyncIdentityEnvelope]) and every write keeps the previous copy until the
 * replacement is durable, so neither a crash nor a failed rename can destroy an
 * identity that peers already pinned. When the wrap key is gone — keystore
 * invalidation, a restored file, tampering — the store refuses to silently mint a
 * new identity under the same device id and reports [SyncIdentityResult.RePairRequired]
 * instead: peers pin (deviceId, publicKey), so a silent re-mint would look like a
 * working device that can never open an envelope again.
 */
object SyncIdentityStore {

    data class SyncIdentity(
        val deviceId: String,
        val publicKeyHex: String,
        val keyPair: KeyPair,
    )

    /** Outcome of opening the persisted identity. */
    sealed interface SyncIdentityResult {
        data class Ready(val identity: SyncIdentity) : SyncIdentityResult

        /** X25519 is unavailable (API < 33) or the identity could not be persisted at all. */
        data object Unsupported : SyncIdentityResult

        /**
         * An identity exists but cannot be used. The stored file is left untouched; recovery is
         * the user's explicit [resetForRePair] followed by re-pairing every peer.
         */
        data class RePairRequired(val failure: SyncIdentityFailure) : SyncIdentityResult
    }

    private const val SYNC_DIR_NAME = "sync"
    private const val IDENTITY_FILE_NAME = "identity.json"
    private const val STATE_FILE_NAME = "state.json"
    private const val X25519_ALGORITHM = "X25519"
    private const val PUBKEY_LENGTH = 32

    /** AndroidKeystore alias wrapping the sync identity's private key. */
    internal const val KEYSTORE_ALIAS = "swiftfloris_sync_identity_key"

    private val lock = Any()

    /**
     * Load the persisted identity, or mint and persist a new one. Returns
     * null when pairing is unavailable *or* when the stored identity needs an explicit
     * re-pair; call [open] to tell those apart and offer recovery.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun getOrCreate(context: Context, deviceId: String): SyncIdentity? {
        return (open(context, deviceId) as? SyncIdentityResult.Ready)?.identity
    }

    /**
     * Opens the persisted identity, migrating a legacy plaintext file to the wrapped schema
     * without changing the advertised public key, or mints a fresh identity when none exists.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun open(context: Context, deviceId: String): SyncIdentityResult = synchronized(lock) {
        val file = identityFile(context)
        if (DurableIdentityFile.exists(file)) {
            var failure: SyncIdentityFailure? = null
            for (candidate in DurableIdentityFile.readableCopies(file)) {
                when (val outcome = loadFrom(candidate, deviceId)) {
                    is LoadOutcome.Loaded -> {
                        if (candidate != file || outcome.needsRewrap) {
                            // Either the backup carried the surviving copy or the file is still in
                            // the plaintext schema; rewrite it wrapped, keeping the same key pair.
                            persist(file, outcome.identity)
                        }
                        return SyncIdentityResult.Ready(outcome.identity)
                    }
                    is LoadOutcome.Failed -> failure = failure ?: outcome.failure
                }
            }
            return SyncIdentityResult.RePairRequired(failure ?: SyncIdentityFailure.Corrupt)
        }
        return mint(file, deviceId)
    }

    /**
     * Discards an unusable identity so a fresh one can be minted. Callers must treat this as
     * destructive: every peer has to be paired again afterwards.
     */
    fun resetForRePair(context: Context): Boolean = synchronized(lock) {
        return DurableIdentityFile.deleteAll(identityFile(context))
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun mint(file: File, deviceId: String): SyncIdentityResult {
        return runCatching {
            val keyPair = SealedBoxCrypto.generateKeyPair()
            val identity = SyncIdentity(
                deviceId = deviceId,
                publicKeyHex = keyPair.public.encoded.takeLast(PUBKEY_LENGTH).toByteArray().toLowerHex(),
                keyPair = keyPair,
            )
            persist(file, identity)
            SyncIdentityResult.Ready(identity)
        }.getOrElse { error ->
            flogError { "Could not mint a sync identity: ${error::class.java.simpleName}" }
            SyncIdentityResult.Unsupported
        }
    }

    private fun persist(file: File, identity: SyncIdentity) {
        DurableIdentityFile.write(
            target = file,
            content = SyncIdentityEnvelope.encode(
                aead = keystoreAead(createIfMissing = true),
                deviceId = identity.deviceId,
                publicKeyHex = identity.publicKeyHex,
                privateKeyPkcs8 = identity.keyPair.private.encoded,
            ),
        )
    }

    private sealed interface LoadOutcome {
        data class Loaded(val identity: SyncIdentity, val needsRewrap: Boolean) : LoadOutcome
        data class Failed(val failure: SyncIdentityFailure) : LoadOutcome
    }

    private fun keystoreAead(createIfMissing: Boolean): Aead {
        if (createIfMissing && !AndroidKeystore.hasKey(KEYSTORE_ALIAS)) {
            AndroidKeystore.generateNewAes256GcmKey(KEYSTORE_ALIAS)
        }
        return AndroidKeystore.getAead(KEYSTORE_ALIAS)
    }

    /**
     * The locally persisted CRDT state from the last reconcile, if any. Falls back to the
     * write-backup copy so an interrupted save cannot cost the device its merge history.
     */
    fun loadLocalState(context: Context): PersonalDictionaryCrdt? = synchronized(lock) {
        for (candidate in DurableIdentityFile.readableCopies(stateFile(context))) {
            runCatching { PersonalDictionaryCrdt.parse(candidate.readText(Charsets.UTF_8)) }
                .getOrNull()
                ?.let { return it }
        }
        return null
    }

    fun saveLocalState(context: Context, state: PersonalDictionaryCrdt): Boolean = synchronized(lock) {
        return runCatching {
            writeAtomically(stateFile(context), state.serializeToString())
            true
        }.getOrDefault(false)
    }

    /**
     * Reads one identity copy. A device-id mismatch means prefs were reset or restored under a
     * different identity than the key pair was minted for; peers pin (deviceId, pubkey) pairs from
     * the QR payload, so it fails closed rather than pretending the identity still matches.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun loadFrom(file: File, expectedDeviceId: String): LoadOutcome {
        val json = runCatching { file.readText(Charsets.UTF_8) }.getOrElse {
            return LoadOutcome.Failed(SyncIdentityFailure.Corrupt)
        }
        val decoded = try {
            SyncIdentityEnvelope.decode(
                aead = keystoreAead(createIfMissing = false),
                json = json,
                expectedDeviceId = expectedDeviceId,
            )
        } catch (error: SyncIdentityDecodeException) {
            flogError { "Stored sync identity is unusable: ${error.failure}" }
            return LoadOutcome.Failed(error.failure)
        } catch (error: Exception) {
            // A missing keystore alias surfaces here; the wrapped key is unrecoverable either way.
            flogError { "Sync identity wrap key unavailable: ${error::class.java.simpleName}" }
            return LoadOutcome.Failed(SyncIdentityFailure.KeyUnavailable)
        }
        return runCatching {
            val factory = KeyFactory.getInstance(X25519_ALGORITHM)
            val privateKey = factory.generatePrivate(PKCS8EncodedKeySpec(decoded.privateKeyPkcs8))
            val publicKey = factory.generatePublic(
                XECPublicKeySpec(
                    NamedParameterSpec(X25519_ALGORITHM),
                    java.math.BigInteger(1, decoded.publicKeyHex.hexToBytes().reversedArray()),
                ),
            )
            LoadOutcome.Loaded(
                identity = SyncIdentity(
                    deviceId = decoded.deviceId,
                    publicKeyHex = decoded.publicKeyHex,
                    keyPair = KeyPair(publicKey, privateKey),
                ),
                needsRewrap = decoded.needsRewrap,
            )
        }.getOrElse { LoadOutcome.Failed(SyncIdentityFailure.Corrupt) }
    }

    private fun identityFile(context: Context): File = syncDir(context).resolve(IDENTITY_FILE_NAME)

    private fun stateFile(context: Context): File = syncDir(context).resolve(STATE_FILE_NAME)

    private fun syncDir(context: Context): File =
        context.filesDir.resolve(SYNC_DIR_NAME).also { it.mkdirs() }

    private fun writeAtomically(target: File, content: String) {
        DurableIdentityFile.write(target, content)
    }
}

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

package dev.patrickgold.florisboard.ime.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.crypto.tink.Aead
import com.google.crypto.tink.DeterministicAead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.daead.DeterministicAeadConfig
import com.google.crypto.tink.integration.android.AndroidKeystore
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.google.crypto.tink.subtle.Base64 as TinkBase64
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64

private const val LEGACY_KEY_KEYSET_ALIAS = "__androidx_security_crypto_encrypted_prefs_key_keyset__"
private const val LEGACY_VALUE_KEYSET_ALIAS = "__androidx_security_crypto_encrypted_prefs_value_keyset__"
private const val LEGACY_MASTER_KEY_URI = "android-keystore://_androidx_security_master_key_"
private const val LEGACY_STRING_TYPE_ID = 0
private const val LEGACY_NULL_VALUE = "__NULL__"

internal object TinkStringPreferenceCrypto {
    private val keystoreLock = Any()

    fun sharedPreferences(context: Context, prefsFileName: String): SharedPreferences {
        val appContext = context.applicationContext ?: context
        return appContext.getSharedPreferences(prefsFileName, Context.MODE_PRIVATE)
    }

    fun readBytes(
        prefs: SharedPreferences,
        prefsFileName: String,
        key: String,
        keystoreAlias: String,
    ): ByteArray? {
        val stored = prefs.getString(key, null) ?: return null
        return withAndroidKeystoreAead(keystoreAlias, createIfMissing = false) {
            decrypt(decodeBase64(stored), associatedData(prefsFileName, key))
        }
    }

    fun writeBytes(
        prefs: SharedPreferences,
        prefsFileName: String,
        key: String,
        keystoreAlias: String,
        value: ByteArray,
    ): Boolean {
        val wrapped = withAndroidKeystoreAead(keystoreAlias, createIfMissing = true) {
            encrypt(value, associatedData(prefsFileName, key))
        }
        return prefs.edit()
            .putString(key, encodeBase64(wrapped))
            .commit()
    }

    fun readString(
        prefs: SharedPreferences,
        prefsFileName: String,
        key: String,
        keystoreAlias: String,
    ): String? {
        return readBytes(prefs, prefsFileName, key, keystoreAlias)?.let { String(it, UTF_8) }
    }

    fun writeString(
        prefs: SharedPreferences,
        prefsFileName: String,
        key: String,
        keystoreAlias: String,
        value: String,
    ): Boolean {
        return writeBytes(prefs, prefsFileName, key, keystoreAlias, value.toByteArray(UTF_8))
    }

    fun readLegacyEncryptedString(
        context: Context,
        prefs: SharedPreferences,
        prefsFileName: String,
        legacyKey: String,
        logTag: String,
    ): String? {
        if (!legacyKeysetsExist(prefs)) {
            return null
        }
        return runCatching {
            DeterministicAeadConfig.register()
            AeadConfig.register()

            val keyAead = AndroidKeysetManager.Builder()
                .withKeyTemplate(KeyTemplates.get("AES256_SIV"))
                .withSharedPref(context, LEGACY_KEY_KEYSET_ALIAS, prefsFileName)
                .withMasterKeyUri(LEGACY_MASTER_KEY_URI)
                .build()
                .getKeysetHandle()
                .getPrimitive(RegistryConfiguration.get(), DeterministicAead::class.java)
            val valueAead = AndroidKeysetManager.Builder()
                .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
                .withSharedPref(context, LEGACY_VALUE_KEYSET_ALIAS, prefsFileName)
                .withMasterKeyUri(LEGACY_MASTER_KEY_URI)
                .build()
                .getKeysetHandle()
                .getPrimitive(RegistryConfiguration.get(), Aead::class.java)

            val encryptedKey = legacyEncryptedPreferenceKey(prefsFileName, legacyKey, keyAead)
            val encryptedValue = prefs.getString(encryptedKey, null) ?: return@runCatching null
            val decryptedValue = valueAead.decrypt(
                TinkBase64.decode(encryptedValue, TinkBase64.DEFAULT),
                encryptedKey.toByteArray(UTF_8),
            )
            decodeLegacyStringPreferenceValue(decryptedValue)
        }.onFailure { error ->
            Log.w(logTag, "Unable to migrate legacy encrypted preference $legacyKey: ${error.message}")
        }.getOrNull()
    }

    fun legacyKeysetsExist(prefs: SharedPreferences): Boolean {
        return prefs.contains(LEGACY_KEY_KEYSET_ALIAS) || prefs.contains(LEGACY_VALUE_KEYSET_ALIAS)
    }

    internal fun legacyEncryptedPreferenceKey(
        prefsFileName: String,
        key: String,
        deterministicAead: DeterministicAead,
    ): String {
        val encryptedKeyBytes = deterministicAead.encryptDeterministically(
            key.toByteArray(UTF_8),
            prefsFileName.toByteArray(UTF_8),
        )
        return TinkBase64.encode(encryptedKeyBytes)
    }

    internal fun decodeLegacyStringPreferenceValue(value: ByteArray): String? {
        val buffer = ByteBuffer.wrap(value)
        if (buffer.remaining() < Integer.BYTES * 2) return null
        val typeId = buffer.int
        if (typeId != LEGACY_STRING_TYPE_ID) return null
        val stringLength = buffer.int
        if (stringLength < 0 || stringLength > buffer.remaining()) return null
        val bytes = ByteArray(stringLength)
        buffer.get(bytes)
        val stringValue = String(bytes, UTF_8)
        return if (stringValue == LEGACY_NULL_VALUE) null else stringValue
    }

    private inline fun <T> withAndroidKeystoreAead(
        keystoreAlias: String,
        createIfMissing: Boolean,
        block: Aead.() -> T,
    ): T {
        return synchronized(keystoreLock) {
            if (createIfMissing && !AndroidKeystore.hasKey(keystoreAlias)) {
                AndroidKeystore.generateNewAes256GcmKey(keystoreAlias)
            }
            AndroidKeystore.getAead(keystoreAlias).block()
        }
    }

    private fun associatedData(prefsFileName: String, key: String): ByteArray {
        return "$prefsFileName:$key".toByteArray(UTF_8)
    }

    private fun encodeBase64(bytes: ByteArray): String {
        return Base64.getEncoder().encodeToString(bytes)
    }

    private fun decodeBase64(value: String): ByteArray {
        return Base64.getDecoder().decode(value)
    }
}

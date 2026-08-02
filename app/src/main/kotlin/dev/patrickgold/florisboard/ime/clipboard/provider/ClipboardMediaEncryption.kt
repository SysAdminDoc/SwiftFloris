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

package dev.patrickgold.florisboard.ime.clipboard.provider

import android.content.Context
import dev.patrickgold.florisboard.ime.security.EncryptedMediaFileCodec
import dev.patrickgold.florisboard.ime.security.TinkStringPreferenceCrypto
import java.io.File
import java.io.InputStream
import java.security.SecureRandom

/**
 * Device-local key management for clipboard media.
 *
 * The data key is wrapped in SharedPreferences by the same
 * [TinkStringPreferenceCrypto] AndroidKeystore construction used by the
 * encrypted clipboard-history database. The wrapped key is deliberately
 * separate from the SQLCipher passphrase and is excluded from Android backup.
 */
internal object ClipboardMediaEncryption {
    private const val KEY_PREFS_FILE = "clipboard_media_key"
    private const val KEY_PREF_TINK = "media_aes_key_tink_v1"
    private const val KEYSTORE_ALIAS = "swiftfloris_clipboard_media_aes_v1"
    private const val KEY_BYTES = 32

    private val keyLock = Any()

    fun isEncrypted(file: File): Boolean = EncryptedMediaFileCodec.isEncrypted(file)

    fun inspect(file: File, maxPlaintextBytes: Long): EncryptedMediaFileCodec.Info {
        return EncryptedMediaFileCodec.inspect(file, maxPlaintextBytes)
    }

    fun encrypt(
        context: Context,
        input: InputStream,
        target: File,
        maxPlaintextBytes: Long,
    ): Long {
        return withKey(context) { key ->
            EncryptedMediaFileCodec.encrypt(input, target, key, maxPlaintextBytes)
        }
    }

    fun decrypt(
        context: Context,
        source: File,
        target: File,
        maxPlaintextBytes: Long,
    ): Long {
        return withKey(context) { key ->
            EncryptedMediaFileCodec.decrypt(source, target, key, maxPlaintextBytes)
        }
    }

    fun copyPlaintext(
        input: InputStream,
        target: File,
        maxPlaintextBytes: Long,
    ): Long {
        return EncryptedMediaFileCodec.copyPlaintext(input, target, maxPlaintextBytes)
    }

    private inline fun <T> withKey(context: Context, block: (ByteArray) -> T): T {
        return synchronized(keyLock) {
            val key = getOrCreateKey(context)
            try {
                block(key)
            } finally {
                key.fill(0)
            }
        }
    }

    private fun getOrCreateKey(context: Context): ByteArray {
        val appContext = context.applicationContext ?: context
        val prefs = TinkStringPreferenceCrypto.sharedPreferences(appContext, KEY_PREFS_FILE)
        val stored = TinkStringPreferenceCrypto.readBytes(
            prefs = prefs,
            prefsFileName = KEY_PREFS_FILE,
            key = KEY_PREF_TINK,
            keystoreAlias = KEYSTORE_ALIAS,
        )
        if (stored != null) {
            require(stored.size == KEY_BYTES) { "Stored clipboard media key has an invalid size." }
            return stored
        }

        val key = ByteArray(KEY_BYTES).also(SecureRandom()::nextBytes)
        val persisted = TinkStringPreferenceCrypto.writeBytes(
            prefs = prefs,
            prefsFileName = KEY_PREFS_FILE,
            key = KEY_PREF_TINK,
            keystoreAlias = KEYSTORE_ALIAS,
            value = key,
        )
        check(persisted) { "Could not persist encrypted clipboard media key." }
        return key
    }
}

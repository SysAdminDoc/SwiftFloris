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

package dev.patrickgold.florisboard.ime.dictionary

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import dev.patrickgold.florisboard.ime.security.TinkStringPreferenceCrypto
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import java.security.SecureRandom
import java.util.Base64

private const val TAG = "FlorisUserDictCrypto"
private const val SQLCIPHER_LIBRARY = "sqlcipher"
private const val KEY_PREFS_FILE = "floris_user_dictionary_key"
private const val KEY_PREF_TINK = "sqlcipher_passphrase_tink_v1"
private const val KEYSTORE_ALIAS = "swiftfloris_user_dictionary_sqlcipher_passphrase_v1"
private const val LEGACY_KEY_PREF = "sqlcipher_passphrase_v1"
private const val PASSPHRASE_BYTES = 64
private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

internal object FlorisUserDictionaryEncryption {
    fun openHelperFactory(context: Context): SupportOpenHelperFactory? {
        return runCatching {
            System.loadLibrary(SQLCIPHER_LIBRARY)
            SupportOpenHelperFactory(getOrCreatePassphrase(context))
        }.onFailure { error ->
            Log.w(TAG, "Unable to initialize encrypted user dictionary: ${error.message}")
        }.getOrNull()
    }

    fun isPlaintextSqliteDatabase(file: File): Boolean {
        if (!file.isFile || file.length() < SQLITE_HEADER.size) return false
        return runCatching {
            file.inputStream().use { input ->
                val header = ByteArray(SQLITE_HEADER.size)
                input.read(header) == SQLITE_HEADER.size && looksLikePlaintextSqliteHeader(header)
            }
        }.getOrDefault(false)
    }

    internal fun looksLikePlaintextSqliteHeader(header: ByteArray): Boolean {
        if (header.size < SQLITE_HEADER.size) return false
        for (index in SQLITE_HEADER.indices) {
            if (header[index] != SQLITE_HEADER[index]) return false
        }
        return true
    }

    private fun getOrCreatePassphrase(context: Context): ByteArray {
        val appContext = context.applicationContext ?: context
        val prefs = TinkStringPreferenceCrypto.sharedPreferences(appContext, KEY_PREFS_FILE)

        val stored = TinkStringPreferenceCrypto.readBytes(
            prefs = prefs,
            prefsFileName = KEY_PREFS_FILE,
            key = KEY_PREF_TINK,
            keystoreAlias = KEYSTORE_ALIAS,
        )
        if (stored != null) {
            return stored
        }

        val legacyPassphrase = TinkStringPreferenceCrypto.readLegacyEncryptedString(
            context = appContext,
            prefs = prefs,
            prefsFileName = KEY_PREFS_FILE,
            legacyKey = LEGACY_KEY_PREF,
            logTag = TAG,
        )?.let { Base64.getDecoder().decode(it) }
        if (legacyPassphrase != null) {
            persistWrappedPassphrase(prefs, legacyPassphrase)
            return legacyPassphrase
        }

        if (TinkStringPreferenceCrypto.legacyKeysetsExist(prefs)) {
            error("Could not migrate legacy encrypted user dictionary passphrase")
        }

        val passphrase = ByteArray(PASSPHRASE_BYTES)
        SecureRandom().nextBytes(passphrase)
        persistWrappedPassphrase(prefs, passphrase)
        return passphrase
    }

    private fun persistWrappedPassphrase(
        prefs: SharedPreferences,
        passphrase: ByteArray,
    ) {
        val storedSuccessfully = TinkStringPreferenceCrypto.writeBytes(
            prefs = prefs,
            prefsFileName = KEY_PREFS_FILE,
            key = KEY_PREF_TINK,
            keystoreAlias = KEYSTORE_ALIAS,
            value = passphrase,
        )
        check(storedSuccessfully) {
            "Could not persist encrypted user dictionary passphrase"
        }
    }
}

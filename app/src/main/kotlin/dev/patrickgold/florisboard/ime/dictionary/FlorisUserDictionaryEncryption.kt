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
import dev.patrickgold.florisboard.ime.security.EncryptedDatabaseFiles
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
internal object FlorisUserDictionaryEncryption {
    fun openHelperFactory(context: Context): SupportOpenHelperFactory? {
        return runCatching {
            System.loadLibrary(SQLCIPHER_LIBRARY)
            SupportOpenHelperFactory(getOrCreatePassphrase(context))
        }.onFailure { error ->
            Log.w(TAG, "Unable to initialize encrypted user dictionary: ${error.message}")
        }.getOrNull()
    }

    fun isPlaintextSqliteDatabase(file: File): Boolean =
        EncryptedDatabaseFiles.isPlaintextSqliteDatabase(file)

    internal fun looksLikePlaintextSqliteHeader(header: ByteArray): Boolean =
        EncryptedDatabaseFiles.looksLikePlaintextSqliteHeader(header)

    internal fun getOrCreatePassphrase(context: Context): ByteArray {
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
            // We saw a legacy AndroidX Security Crypto keyset in the prefs
            // but `readLegacyEncryptedString` returned null, which means
            // either the legacy value pref is missing OR the legacy
            // decrypt failed (Android Keystore master key rotated, restored
            // from a different device, keyset corrupt, or a Keystore call
            // that failed this once).
            //
            // While an encrypted store is on disk, a new passphrase would
            // make SQLCipher call that store "not a database", and it would
            // be quarantined under a key we had just thrown away. So keep
            // the keysets and the store, and fail this open as retryable.
            check(mayReplaceUnreadableLegacyKey(appContext.getDatabasePath(FlorisUserDictionaryDatabase.DB_FILE_NAME))) {
                "Legacy user dictionary key couldn't be read; keeping it and the encrypted store for a retry"
            }
            // Nothing encrypted depends on the old key: clear the legacy
            // keyset entries and fall through to a fresh passphrase.
            Log.w(
                TAG,
                "Legacy encrypted-prefs keyset exists but cannot be decrypted; " +
                    "no encrypted dictionary needs it, so clearing it and " +
                    "generating a new user dictionary passphrase.",
            )
            prefs.edit()
                .remove("__androidx_security_crypto_encrypted_prefs_key_keyset__")
                .remove("__androidx_security_crypto_encrypted_prefs_value_keyset__")
                .apply()
        }

        val passphrase = ByteArray(PASSPHRASE_BYTES)
        SecureRandom().nextBytes(passphrase)
        persistWrappedPassphrase(prefs, passphrase)
        return passphrase
    }

    /**
     * True when no encrypted store could still need an unreadable legacy key: neither the store
     * nor its WAL holds any bytes (a new WAL-mode store keeps its first pages in the WAL until a
     * checkpoint), or it is a plaintext database that the encryption migration will copy across.
     */
    internal fun mayReplaceUnreadableLegacyKey(databaseFile: File): Boolean {
        // File.length() is 0 for a file that doesn't exist.
        val holdsNoData = databaseFile.length() == 0L && File("${databaseFile.path}-wal").length() == 0L
        return holdsNoData || isPlaintextSqliteDatabase(databaseFile)
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

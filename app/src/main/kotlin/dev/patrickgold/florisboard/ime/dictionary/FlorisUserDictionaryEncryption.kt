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
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import java.security.SecureRandom

private const val TAG = "FlorisUserDictCrypto"
private const val SQLCIPHER_LIBRARY = "sqlcipher"
private const val KEY_PREFS_FILE = "floris_user_dictionary_key"
private const val KEY_PREF = "sqlcipher_passphrase_v1"
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
        val prefs = encryptedPrefs(context)
        val stored = prefs.getString(KEY_PREF, null)
        if (stored != null) {
            return Base64.decode(stored, Base64.NO_WRAP)
        }
        val passphrase = ByteArray(PASSPHRASE_BYTES)
        SecureRandom().nextBytes(passphrase)
        val storedSuccessfully = prefs.edit()
            .putString(KEY_PREF, Base64.encodeToString(passphrase, Base64.NO_WRAP))
            .commit()
        check(storedSuccessfully) {
            "Could not persist encrypted user dictionary passphrase"
        }
        return passphrase
    }

    private fun encryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            KEY_PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }
}

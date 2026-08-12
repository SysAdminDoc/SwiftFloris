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
import android.content.SharedPreferences
import android.util.Log
import dev.patrickgold.florisboard.ime.security.TinkStringPreferenceCrypto
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.security.SecureRandom

private const val TAG = "ClipboardHistoryCrypto"
private const val SQLCIPHER_LIBRARY = "sqlcipher"
private const val KEY_PREFS_FILE = "clipboard_history_key"
private const val KEY_PREF_TINK = "sqlcipher_passphrase_tink_v1"
private const val KEYSTORE_ALIAS = "swiftfloris_clipboard_history_sqlcipher_passphrase_v1"
private const val PASSPHRASE_BYTES = 64

/**
 * SQLCipher passphrase management for the clipboard history database.
 *
 * Clipboard history holds whatever the user copied — one-time codes, addresses, message drafts —
 * and it used to sit in an ordinary Room database. It now uses the same construction the personal
 * dictionary does: a random 64-byte passphrase wrapped by an AndroidKeystore-held AES-GCM key
 * through Tink, kept in an app-private preferences file that backups exclude.
 *
 * Deliberately mirrors [dev.patrickgold.florisboard.ime.dictionary.FlorisUserDictionaryEncryption]
 * rather than sharing its state: the two stores must never be able to open each other's data.
 */
internal object ClipboardHistoryEncryption {
    fun openHelperFactory(context: Context): SupportOpenHelperFactory? {
        return try {
            System.loadLibrary(SQLCIPHER_LIBRARY)
            SupportOpenHelperFactory(getOrCreatePassphrase(context))
        } catch (error: UnsatisfiedLinkError) {
            Log.w(TAG, "Unable to load encrypted clipboard history library: ${error.message}")
            null
        }
    }

    /** True when a wrapped passphrase already exists, i.e. an encrypted store was created before. */
    fun hasStoredPassphrase(context: Context): Boolean {
        val appContext = context.applicationContext ?: context
        val prefs = TinkStringPreferenceCrypto.sharedPreferences(appContext, KEY_PREFS_FILE)
        return prefs.contains(KEY_PREF_TINK)
    }

    /**
     * Drops the wrapped passphrase so the next open mints a new one. Only used after the existing
     * ciphertext has been preserved: without the key that data is unreadable anyway, and keeping a
     * dead key would make every future open fail the same way.
     */
    fun clearStoredPassphrase(context: Context): Boolean {
        val appContext = context.applicationContext ?: context
        val prefs = TinkStringPreferenceCrypto.sharedPreferences(appContext, KEY_PREFS_FILE)
        return prefs.edit().remove(KEY_PREF_TINK).commit()
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

        val passphrase = ByteArray(PASSPHRASE_BYTES)
        SecureRandom().nextBytes(passphrase)
        persistWrappedPassphrase(prefs, passphrase)
        return passphrase
    }

    private fun persistWrappedPassphrase(prefs: SharedPreferences, passphrase: ByteArray) {
        val storedSuccessfully = TinkStringPreferenceCrypto.writeBytes(
            prefs = prefs,
            prefsFileName = KEY_PREFS_FILE,
            key = KEY_PREF_TINK,
            keystoreAlias = KEYSTORE_ALIAS,
            value = passphrase,
        )
        check(storedSuccessfully) { "Could not persist encrypted clipboard history passphrase" }
    }
}

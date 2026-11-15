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
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private const val KEY_PREFS_FILE = "floris_user_dictionary_key"
private const val LEGACY_KEY_KEYSET = "__androidx_security_crypto_encrypted_prefs_key_keyset__"
private const val LEGACY_VALUE_KEYSET = "__androidx_security_crypto_encrypted_prefs_value_keyset__"
private const val UNREADABLE_KEYSET = "not a keyset"

/**
 * Runs the real passphrase lookup with a legacy AndroidX keyset that can't be opened. The stored
 * value isn't a keyset at all, so Tink rejects it before any Keystore call. The lookup gets the same
 * null back from the legacy read whatever the reason, a rotated or briefly unavailable Keystore key
 * on a phone included, so this covers how it treats that key and the store.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class FlorisUserDictionaryLegacyKeyTest {
    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var database: File

    @Before
    fun plantUnreadableLegacyKey() {
        context = ApplicationProvider.getApplicationContext()
        prefs = context.getSharedPreferences(KEY_PREFS_FILE, Context.MODE_PRIVATE)
        database = context.getDatabasePath(FlorisUserDictionaryDatabase.DB_FILE_NAME)
        prefs.edit()
            .putString(LEGACY_KEY_KEYSET, UNREADABLE_KEYSET)
            .putString(LEGACY_VALUE_KEYSET, UNREADABLE_KEYSET)
            .commit()
    }

    @After
    fun cleanUp() {
        prefs.edit().clear().commit()
        database.delete()
        File("${database.path}-wal").delete()
    }

    @Test
    fun anEncryptedStoreKeepsItsUnreadableLegacyKey() {
        val storeBytes = ByteArray(4096) { 0x42 }
        check(database.parentFile!!.isDirectory || database.parentFile!!.mkdirs())
        database.writeBytes(storeBytes)

        val error = assertThrows(IllegalStateException::class.java) {
            FlorisUserDictionaryEncryption.getOrCreatePassphrase(context)
        }

        assertTrue(error.message.orEmpty().contains("keeping it and the encrypted store"))
        assertEquals(UNREADABLE_KEYSET, prefs.getString(LEGACY_KEY_KEYSET, null))
        assertEquals(UNREADABLE_KEYSET, prefs.getString(LEGACY_VALUE_KEYSET, null))
        assertArrayEquals(storeBytes, database.readBytes())
    }

    @Test
    fun noStoreLetsTheUnreadableLegacyKeyGo() {
        // Writing the new passphrase needs the Android Keystore too, so that step fails here.
        // What matters is that the old keysets were cleared on the way.
        runCatching { FlorisUserDictionaryEncryption.getOrCreatePassphrase(context) }

        assertFalse(prefs.contains(LEGACY_KEY_KEYSET))
        assertFalse(prefs.contains(LEGACY_VALUE_KEYSET))
    }
}

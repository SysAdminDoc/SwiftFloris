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

import dev.patrickgold.florisboard.ime.security.TinkStringPreferenceCrypto
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.File
import java.nio.ByteBuffer
import java.nio.file.Files

/**
 * JVM-safe regression checks for the value parsers used by the encrypted dictionary.
 *
 * The SQLCipher open-helper, Keystore key, Room database, and recovery paths are exercised by
 * [PersonalDictionaryRoomSqlCipherRuntimeTest] on Android. File move, restore, and quarantine
 * behavior is covered by [dev.patrickgold.florisboard.ime.security.EncryptedDatabaseFilesTest].
 */
class PersonalDictionaryEncryptionTest : FunSpec({
    test("plaintext SQLite header detection distinguishes migrated and encrypted files") {
        val sqliteHeader = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
        val encryptedLikeHeader = ByteArray(sqliteHeader.size) { 0x42 }

        FlorisUserDictionaryEncryption.looksLikePlaintextSqliteHeader(sqliteHeader) shouldBe true
        FlorisUserDictionaryEncryption.looksLikePlaintextSqliteHeader(encryptedLikeHeader) shouldBe false
        FlorisUserDictionaryEncryption.looksLikePlaintextSqliteHeader(byteArrayOf(1, 2, 3)) shouldBe false
    }

    test("an unreadable legacy key is only replaced when no encrypted store could need it") {
        val dir = Files.createTempDirectory("legacy-key").toFile()
        try {
            val database = File(dir, "floris_user_dictionary")
            val wal = File(dir, "floris_user_dictionary-wal")

            FlorisUserDictionaryEncryption.mayReplaceUnreadableLegacyKey(database) shouldBe true

            database.writeBytes(ByteArray(0))
            FlorisUserDictionaryEncryption.mayReplaceUnreadableLegacyKey(database) shouldBe true

            // A new WAL-mode store can hold every word in its WAL before the first checkpoint.
            wal.writeBytes(ByteArray(4096) { 0x42 })
            FlorisUserDictionaryEncryption.mayReplaceUnreadableLegacyKey(database) shouldBe false
            wal.delete()

            database.writeBytes(ByteArray(4096) { 0x42 })
            FlorisUserDictionaryEncryption.mayReplaceUnreadableLegacyKey(database) shouldBe false

            database.writeBytes("SQLite format 3\u0000".toByteArray(Charsets.US_ASCII) + ByteArray(4080))
            FlorisUserDictionaryEncryption.mayReplaceUnreadableLegacyKey(database) shouldBe true
        } finally {
            dir.deleteRecursively()
        }
    }

    test("legacy encrypted preference string payload parser rejects wrong types and malformed lengths") {
        val encoded = "legacy-passphrase".toByteArray()
        val valid = ByteBuffer.allocate(Integer.BYTES * 2 + encoded.size)
            .putInt(0)
            .putInt(encoded.size)
            .put(encoded)
            .array()
        val wrongType = ByteBuffer.allocate(Integer.BYTES * 2 + encoded.size)
            .putInt(2)
            .putInt(encoded.size)
            .put(encoded)
            .array()
        val oversized = ByteBuffer.allocate(Integer.BYTES * 2)
            .putInt(0)
            .putInt(encoded.size + 1)
            .array()

        TinkStringPreferenceCrypto.decodeLegacyStringPreferenceValue(valid) shouldBe "legacy-passphrase"
        TinkStringPreferenceCrypto.decodeLegacyStringPreferenceValue(wrongType) shouldBe null
        TinkStringPreferenceCrypto.decodeLegacyStringPreferenceValue(oversized) shouldBe null
        TinkStringPreferenceCrypto.decodeLegacyStringPreferenceValue(byteArrayOf(1, 2, 3)) shouldBe null
    }
})

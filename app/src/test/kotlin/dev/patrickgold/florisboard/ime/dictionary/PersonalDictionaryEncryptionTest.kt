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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File

/**
 * ROADMAP N7.4 — personal dictionary encryption regression guard.
 *
 * The Android Keystore, SQLCipher native library, and Room open-helper stack are
 * device/runtime services, so this JVM test protects the source-level contract:
 * the Floris personal dictionary must be opened through SQLCipher, its passphrase
 * must be protected by a MasterKey-backed encrypted preference, and backup rules
 * must not create a restored encrypted DB without the non-portable key material.
 */
class PersonalDictionaryEncryptionTest : FunSpec({
    test("Floris user dictionary Room database is opened through SQLCipher") {
        val source = locateProjectFile(
            "app/src/main/kotlin/dev/patrickgold/florisboard/ime/dictionary/DictionaryManager.kt",
            "src/main/kotlin/dev/patrickgold/florisboard/ime/dictionary/DictionaryManager.kt",
        ).readText()

        source shouldContain "FlorisUserDictionaryEncryption.openHelperFactory"
        source shouldContain ".openHelperFactory(factory)"
        source shouldContain "migratePlaintextFlorisUserDictionaryIfNecessary"
        source shouldContain "openVerifiedEncryptedFlorisUserDictionary"
    }

    test("SQLCipher passphrase is generated locally and protected by Android Keystore") {
        val source = locateProjectFile(
            "app/src/main/kotlin/dev/patrickgold/florisboard/ime/dictionary/FlorisUserDictionaryEncryption.kt",
            "src/main/kotlin/dev/patrickgold/florisboard/ime/dictionary/FlorisUserDictionaryEncryption.kt",
        ).readText()

        source shouldContain "System.loadLibrary(SQLCIPHER_LIBRARY)"
        source shouldContain "SupportOpenHelperFactory"
        source shouldContain "MasterKey.Builder"
        source shouldContain "MasterKey.KeyScheme.AES256_GCM"
        source shouldContain "EncryptedSharedPreferences.create"
        source shouldContain "SecureRandom().nextBytes"
        source shouldContain ".commit()"
    }

    test("SQLCipher and matching AndroidX SQLite dependencies are declared") {
        val source = locateProjectFile(
            "gradle/libs.versions.toml",
            "../gradle/libs.versions.toml",
        ).readText()

        source shouldContain "sqlcipher-android = \"4.16.0\""
        source shouldContain "androidx-sqlite = \"2.6.2\""
        source shouldContain "net.zetetic:sqlcipher-android"
        source shouldContain "androidx.sqlite:sqlite"
    }

    test("backup rules do not transfer encrypted personal dictionary without its key") {
        val source = locateProjectFile(
            "app/src/main/res/xml-v31/backup_rules.xml",
            "src/main/res/xml-v31/backup_rules.xml",
        ).readText()

        source shouldContain "SQLCipher database key"
        source shouldNotContain "path=\"floris_user_dictionary\""
    }

    test("plaintext SQLite header detection distinguishes migrated and encrypted files") {
        val sqliteHeader = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
        val encryptedLikeHeader = ByteArray(sqliteHeader.size) { 0x42 }

        FlorisUserDictionaryEncryption.looksLikePlaintextSqliteHeader(sqliteHeader) shouldBe true
        FlorisUserDictionaryEncryption.looksLikePlaintextSqliteHeader(encryptedLikeHeader) shouldBe false
        FlorisUserDictionaryEncryption.looksLikePlaintextSqliteHeader(byteArrayOf(1, 2, 3)) shouldBe false
    }
})

private fun locateProjectFile(vararg paths: String): File {
    return paths.asSequence()
        .map { File(it) }
        .firstOrNull { it.exists() }
        ?: error("None of these files are reachable from ${File(".").absolutePath}: ${paths.joinToString()}")
}

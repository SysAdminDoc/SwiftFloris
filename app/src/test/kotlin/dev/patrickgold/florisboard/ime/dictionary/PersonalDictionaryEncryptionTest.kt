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
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File
import java.nio.ByteBuffer

/**
 * ROADMAP N7.4 — personal dictionary encryption regression guard.
 *
 * The Android Keystore, SQLCipher native library, and Room open-helper stack are
 * device/runtime services, so this JVM test protects the source-level contract:
 * the Floris personal dictionary must be opened through SQLCipher, its
 * passphrase must be wrapped by Tink with an AndroidKeystore-held key, legacy
 * AndroidX encrypted preferences must migrate once, and backup rules must not
 * create a restored encrypted DB without the non-portable key material.
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

    test("SQLCipher passphrase is generated locally and wrapped by Tink AndroidKeystore") {
        val dictionarySource = locateProjectFile(
            "app/src/main/kotlin/dev/patrickgold/florisboard/ime/dictionary/FlorisUserDictionaryEncryption.kt",
            "src/main/kotlin/dev/patrickgold/florisboard/ime/dictionary/FlorisUserDictionaryEncryption.kt",
        ).readText()
        val cryptoSource = locateProjectFile(
            "app/src/main/kotlin/dev/patrickgold/florisboard/ime/security/TinkStringPreferenceCrypto.kt",
            "src/main/kotlin/dev/patrickgold/florisboard/ime/security/TinkStringPreferenceCrypto.kt",
        ).readText()

        dictionarySource shouldContain "System.loadLibrary(SQLCIPHER_LIBRARY)"
        dictionarySource shouldContain "SupportOpenHelperFactory"
        dictionarySource shouldContain "TinkStringPreferenceCrypto.readBytes"
        dictionarySource shouldContain "TinkStringPreferenceCrypto.writeBytes"
        dictionarySource shouldContain "SecureRandom().nextBytes"
        cryptoSource shouldContain "AndroidKeystore.generateNewAes256GcmKey"
        cryptoSource shouldContain "AndroidKeystore.getAead"
        cryptoSource shouldContain "createIfMissing = false"
        cryptoSource shouldContain "createIfMissing = true"
        cryptoSource shouldContain "associatedData(prefsFileName, key)"
        cryptoSource shouldContain ".commit()"
        dictionarySource shouldNotContain "androidx.security.crypto"
        cryptoSource shouldNotContain "androidx.security.crypto"
    }

    test("legacy AndroidX encrypted preference passphrases are one-shot migrated via Tink") {
        val dictionarySource = locateProjectFile(
            "app/src/main/kotlin/dev/patrickgold/florisboard/ime/dictionary/FlorisUserDictionaryEncryption.kt",
            "src/main/kotlin/dev/patrickgold/florisboard/ime/dictionary/FlorisUserDictionaryEncryption.kt",
        ).readText()
        val cryptoSource = locateProjectFile(
            "app/src/main/kotlin/dev/patrickgold/florisboard/ime/security/TinkStringPreferenceCrypto.kt",
            "src/main/kotlin/dev/patrickgold/florisboard/ime/security/TinkStringPreferenceCrypto.kt",
        ).readText()

        dictionarySource shouldContain "LEGACY_KEY_PREF"
        dictionarySource shouldContain "TinkStringPreferenceCrypto.readLegacyEncryptedString"
        dictionarySource shouldContain "persistWrappedPassphrase(prefs, legacyPassphrase)"
        cryptoSource shouldContain "LEGACY_KEY_KEYSET_ALIAS"
        cryptoSource shouldContain "LEGACY_VALUE_KEYSET_ALIAS"
        cryptoSource shouldContain "LEGACY_MASTER_KEY_URI"
        cryptoSource shouldContain "AndroidKeysetManager.Builder"
        cryptoSource shouldContain "DeterministicAeadConfig.register"
        cryptoSource shouldContain "AeadConfig.register"
    }

    test("clipboard history uses the Room-backed manager, not a parallel Tink store") {
        val clipboardDir = locateProjectFile(
            "app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard",
            "src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard",
        )
        val clipboardSource = File(clipboardDir, "ClipboardManager.kt").readText()

        File(clipboardDir, "ClipboardHistoryManager.kt").exists() shouldBe false
        clipboardSource shouldContain "ClipboardHistoryDatabase"
        clipboardSource shouldContain "ClipboardHistoryDao"
        clipboardSource shouldNotContain "TinkStringPreferenceCrypto"
        clipboardSource shouldNotContain "clipboard_history_tink_v1"
    }

    test("SQLCipher, AndroidX SQLite, and Tink dependencies are declared") {
        val source = locateProjectFile(
            "gradle/libs.versions.toml",
            "../gradle/libs.versions.toml",
        ).readText()
        val buildScript = locateProjectFile(
            "app/build.gradle.kts",
            "build.gradle.kts",
        ).readText()

        source shouldContain "sqlcipher-android = \"4.16.0\""
        source shouldContain "androidx-sqlite = \"2.6.2\""
        source shouldContain "tink-android = \"1.21.0\""
        source shouldContain "net.zetetic:sqlcipher-android"
        source shouldContain "androidx.sqlite:sqlite"
        source shouldContain "com.google.crypto.tink:tink-android"
        buildScript shouldNotContain "security-crypto"
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

private fun locateProjectFile(vararg paths: String): File {
    return paths.asSequence()
        .map { File(it) }
        .firstOrNull { it.exists() }
        ?: error("None of these files are reachable from ${File(".").absolutePath}: ${paths.joinToString()}")
}

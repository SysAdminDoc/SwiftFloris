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

package dev.patrickgold.florisboard.ime.tasker

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File
import java.security.SecureRandom

class TaskerAuthenticationStoreTest : FunSpec({
    test("first use creates and persists a 256-bit secret") {
        val persistence = RecordingPersistence()
        val store = TaskerAuthenticationStore(
            persistence = persistence,
            secureRandom = FillSecureRandom(0x31),
        )

        val secret = store.getOrCreateSecret()

        secret?.size shouldBe TaskerIntentContract.AUTH_SECRET_BYTES
        secret shouldBe ByteArray(TaskerIntentContract.AUTH_SECRET_BYTES) { 0x31 }
        persistence.value shouldBe secret
        persistence.writeCount shouldBe 1
    }

    test("valid persisted secret is reused and returned defensively") {
        val original = ByteArray(TaskerIntentContract.AUTH_SECRET_BYTES) { 0x42 }
        val persistence = RecordingPersistence(original)
        val store = TaskerAuthenticationStore(
            persistence = persistence,
            secureRandom = FillSecureRandom(0x11),
        )

        val first = store.getOrCreateSecret()!!
        first[0] = 0
        val second = store.getOrCreateSecret()

        second shouldBe original
        persistence.writeCount shouldBe 0
    }

    test("rotation replaces the secret while persistence failure fails closed") {
        val persistence = RecordingPersistence(
            ByteArray(TaskerIntentContract.AUTH_SECRET_BYTES) { 0x21 },
        )
        val random = FillSecureRandom(0x63)
        val store = TaskerAuthenticationStore(persistence, random)

        store.rotateSecret() shouldBe true
        persistence.value shouldBe ByteArray(TaskerIntentContract.AUTH_SECRET_BYTES) { 0x63 }

        persistence.acceptWrites = false
        random.fillByte = 0x75
        store.rotateSecret() shouldBe false
        persistence.value shouldBe ByteArray(TaskerIntentContract.AUTH_SECRET_BYTES) { 0x63 }
    }

    test("Android backup rules never include the Tasker key file") {
        listOf(
            locateProjectFile(
                "app/src/main/res/xml/backup_rules.xml",
                "src/main/res/xml/backup_rules.xml",
            ),
            locateProjectFile(
                "app/src/main/res/xml-v31/backup_rules.xml",
                "src/main/res/xml-v31/backup_rules.xml",
            ),
        ).forEach { file ->
            val source = file.readText()
            source shouldNotContain "domain=\"sharedpref\""
            source shouldNotContain TASKER_AUTH_PREFS_XML
        }
        val extractionRules = locateProjectFile(
            "app/src/main/res/xml/data_extraction_rules.xml",
            "src/main/res/xml/data_extraction_rules.xml",
        ).readText()
        extractionRules shouldContain "path=\"$TASKER_AUTH_PREFS_XML\""
    }
})

private class RecordingPersistence(initial: ByteArray? = null) : TaskerSecretPersistence {
    var value: ByteArray? = initial?.copyOf()
    var writeCount: Int = 0
    var acceptWrites: Boolean = true

    override fun read(): ByteArray? = value?.copyOf()

    override fun write(secret: ByteArray): Boolean {
        writeCount += 1
        if (!acceptWrites) return false
        value = secret.copyOf()
        return true
    }
}

private class FillSecureRandom(var fillByte: Byte) : SecureRandom() {
    constructor(fillByte: Int) : this(fillByte.toByte())

    override fun nextBytes(bytes: ByteArray) {
        bytes.fill(fillByte)
    }
}

private fun locateProjectFile(vararg paths: String): File {
    return paths.asSequence()
        .map(::File)
        .firstOrNull(File::isFile)
        ?: error("Could not locate any of: ${paths.joinToString()}")
}

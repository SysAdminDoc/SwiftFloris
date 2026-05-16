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

package dev.patrickgold.florisboard.ime.voice

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files

class VoiceModelInstallStoreTest : FunSpec({
    test("install copies the artifact into a model-specific private directory") {
        val root = Files.createTempDirectory("voice-models").toFile()
        val store = VoiceModelInstallStore(root)
        val model = VoiceModelCatalog.byId("vosk-en-us-small-0-15")!!

        val state = store.install(
            entry = model,
            displayName = "../unsafe:name.zip",
            inputStream = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)),
        )

        state.installed shouldBe true
        state.diskBytes shouldBe 4L
        state.artifactName shouldBe "unsafe_name.zip"
        root.resolve(model.id).resolve("unsafe_name.zip").exists() shouldBe true

        root.deleteRecursively()
    }

    test("delete removes installed model state") {
        val root = Files.createTempDirectory("voice-models").toFile()
        val store = VoiceModelInstallStore(root)
        val model = VoiceModelCatalog.byId("whisper-en-tiny-en")!!

        store.install(
            entry = model,
            displayName = model.artifactFileName,
            inputStream = ByteArrayInputStream(ByteArray(16) { it.toByte() }),
        )
        store.delete(model)

        store.state(model).installed shouldBe false
        store.state(model).diskBytes shouldBe 0L

        root.deleteRecursively()
    }

    test("failed replacement keeps the previously installed model intact") {
        val root = Files.createTempDirectory("voice-models").toFile()
        val store = VoiceModelInstallStore(root)
        val model = VoiceModelCatalog.byId("vosk-en-us-small-0-15")!!
        try {
            store.install(
                entry = model,
                displayName = "old.zip",
                inputStream = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)),
            )

            shouldThrow<IOException> {
                store.install(
                    entry = model,
                    displayName = "new.zip",
                    inputStream = FailingInputStream(failAfterBytes = 2),
                )
            }

            val state = store.state(model)
            state.installed shouldBe true
            state.diskBytes shouldBe 4L
            state.artifactName shouldBe "old.zip"
            root.resolve(model.id).resolve("old.zip").readBytes().toList() shouldBe
                byteArrayOf(1, 2, 3, 4).toList()
        } finally {
            root.deleteRecursively()
        }
    }

    test("model ids must not escape the install root") {
        val root = Files.createTempDirectory("voice-models").toFile()
        val store = VoiceModelInstallStore(root)
        val bad = VoiceModelCatalog.byId("vosk-en-us-small-0-15")!!.copy(id = "../escape")
        try {
            shouldThrow<IllegalArgumentException> {
                store.state(bad)
            }
        } finally {
            root.deleteRecursively()
        }
    }
})

private class FailingInputStream(private val failAfterBytes: Int) : InputStream() {
    private var emitted = 0

    override fun read(): Int {
        if (emitted >= failAfterBytes) throw IOException("simulated read failure")
        emitted++
        return emitted
    }
}

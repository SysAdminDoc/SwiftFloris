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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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

    test("oversized replacement stream keeps the previously installed model intact") {
        val root = Files.createTempDirectory("voice-models").toFile()
        val store = VoiceModelInstallStore(root)
        val model = VoiceModelCatalog.byId("vosk-en-us-small-0-15")!!.copy(
            id = "voice-test-small",
            approximateSizeMb = 0,
        )
        try {
            store.install(
                entry = model,
                displayName = "old.zip",
                inputStream = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)),
            )

            shouldThrow<IllegalStateException> {
                store.install(
                    entry = model,
                    displayName = "too-large.zip",
                    inputStream = RepeatingInputStream(VoiceModelInstallStore.maxArtifactBytes(model) + 1L),
                )
            }

            val state = store.state(model)
            state.installed shouldBe true
            state.diskBytes shouldBe 4L
            state.artifactName shouldBe "old.zip"
            root.resolve(model.id).resolve("old.zip").readBytes().toList() shouldBe
                byteArrayOf(1, 2, 3, 4).toList()
            root.listFiles()?.none { it.name.startsWith(".swiftfloris-staging-") } shouldBe true
        } finally {
            root.deleteRecursively()
        }
    }

    test("model artifact byte budget follows catalog metadata") {
        val tiny = VoiceModelCatalog.byId("whisper-en-tiny-en")!!
        val large = VoiceModelCatalog.byId("whisper-multi-large-v3-turbo-q8")!!

        VoiceModelInstallStore.maxArtifactBytes(tiny) shouldBe (75L + 32L) * 1024L * 1024L
        VoiceModelInstallStore.maxArtifactBytes(large) shouldBe (834L + 208L) * 1024L * 1024L

        VoiceModelInstallStore.requireArtifactSize(tiny, VoiceModelInstallStore.maxArtifactBytes(tiny))
        shouldThrow<IllegalStateException> {
            VoiceModelInstallStore.requireArtifactSize(tiny, VoiceModelInstallStore.maxArtifactBytes(tiny) + 1L)
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

    // Hardening: a JVM crash between staging-write and rename can leave
    // `.swiftfloris-staging-…` / `.swiftfloris-backup-…` directories on
    // disk. The next install must sweep them; otherwise they accumulate
    // forever.
    test("install sweeps stale staging and backup directories") {
        val root = Files.createTempDirectory("voice-models").toFile()
        val store = VoiceModelInstallStore(root)
        val model = VoiceModelCatalog.byId("whisper-en-tiny-en")!!
        val stale1 = java.io.File(root, ".swiftfloris-staging-whisper-en-tiny-en-old1").apply { mkdirs() }
        val stale2 = java.io.File(root, ".swiftfloris-backup-whisper-en-tiny-en-old2").apply { mkdirs() }
        try {
            store.install(
                entry = model,
                displayName = model.artifactFileName,
                inputStream = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)),
            )

            stale1.exists() shouldBe false
            stale2.exists() shouldBe false
            root.resolve(model.id).resolve(model.artifactFileName).exists() shouldBe true
        } finally {
            root.deleteRecursively()
        }
    }

    test("a second store on the same folder waits for an install that's still copying") {
        // Each settings screen makes its own store, and an install sweeps leftover staging
        // folders, so without a shared lock it would delete the first install's folder mid-copy.
        val root = Files.createTempDirectory("voice-models").toFile()
        val vosk = VoiceModelCatalog.byId("vosk-en-us-small-0-15")!!
        val whisper = VoiceModelCatalog.byId("whisper-en-tiny-en")!!
        val copyStarted = CountDownLatch(1)
        val finishCopy = CountDownLatch(1)
        val slowArtifact = object : ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)) {
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                copyStarted.countDown()
                check(finishCopy.await(5, TimeUnit.SECONDS)) { "The test never released the copy." }
                return super.read(b, off, len)
            }

            override fun read(b: ByteArray): Int = read(b, 0, b.size)
        }
        val failures = java.util.Collections.synchronizedList(mutableListOf<Throwable>())
        try {
            val first = Thread {
                runCatching { VoiceModelInstallStore(root).install(vosk, "first.zip", slowArtifact) }
                    .onFailure { failures += it }
            }
            first.start()
            copyStarted.await(5, TimeUnit.SECONDS) shouldBe true

            val second = Thread {
                runCatching {
                    VoiceModelInstallStore(root).install(whisper, whisper.artifactFileName, ByteArrayInputStream(byteArrayOf(5, 6)))
                }.onFailure { failures += it }
            }
            second.start()
            val deadline = System.nanoTime() + 5_000_000_000L
            while (second.state != Thread.State.BLOCKED) {
                check(second.isAlive) { "The second install finished without waiting for the first." }
                check(System.nanoTime() < deadline) { "The second install never reached the lock." }
                Thread.yield()
            }
            finishCopy.countDown()
            first.join(5_000L)
            second.join(5_000L)

            failures shouldBe emptyList()
            val store = VoiceModelInstallStore(root)
            store.state(vosk).diskBytes shouldBe 4L
            store.state(whisper).diskBytes shouldBe 2L
        } finally {
            finishCopy.countDown()
            root.deleteRecursively()
        }
    }

    test("reading what's installed doesn't wait for an install that's still copying") {
        // A settings screen opened during a long install would otherwise show every model as
        // missing until the copy finished.
        val root = Files.createTempDirectory("voice-models").toFile()
        val vosk = VoiceModelCatalog.byId("vosk-en-us-small-0-15")!!
        val whisper = VoiceModelCatalog.byId("whisper-en-tiny-en")!!
        val copyStarted = CountDownLatch(1)
        val finishCopy = CountDownLatch(1)
        val slowArtifact = object : ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)) {
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                copyStarted.countDown()
                check(finishCopy.await(5, TimeUnit.SECONDS)) { "The test never released the copy." }
                return super.read(b, off, len)
            }

            override fun read(b: ByteArray): Int = read(b, 0, b.size)
        }
        try {
            // One store for the install and the read, as one screen uses: a lock on the store
            // itself would hold the read here, where a second store would slip past it.
            val store = VoiceModelInstallStore(root)
            store.install(whisper, whisper.artifactFileName, ByteArrayInputStream(byteArrayOf(5, 6)))
            val installing = Thread {
                runCatching { store.install(vosk, "slow.zip", slowArtifact) }
            }.apply { isDaemon = true }
            installing.start()
            copyStarted.await(5, TimeUnit.SECONDS) shouldBe true

            var states: Map<String, VoiceModelInstallState>? = null
            val reader = Thread { states = store.states(listOf(vosk, whisper)) }
                .apply { isDaemon = true }
            reader.start()
            reader.join(2_000L)
            reader.isAlive shouldBe false
            states!!.getValue(whisper.id).installed shouldBe true
            states!!.getValue(vosk.id).installed shouldBe false
        } finally {
            finishCopy.countDown()
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

private class RepeatingInputStream(private val totalBytes: Long) : InputStream() {
    private var emitted = 0L

    override fun read(): Int {
        if (emitted >= totalBytes) return -1
        emitted++
        return 0
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (emitted >= totalBytes) return -1
        val count = minOf(length.toLong(), totalBytes - emitted).toInt()
        buffer.fill(0.toByte(), offset, offset + count)
        emitted += count
        return count
    }
}

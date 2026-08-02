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

package dev.patrickgold.florisboard.ime.sync

import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.util.Base64

/**
 * The sync identity is the one key on the device that peers pin: losing it silently, or letting it
 * sit in plaintext, both break the pairing model. Production wraps it with an AndroidKeystore AEAD
 * that Robolectric cannot emulate, so these cases drive the same seam with a JVM Tink key.
 */
class SyncIdentityPersistenceTest : FunSpec({

    fun newAead(): Aead {
        AeadConfig.register()
        return KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM"))
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    val deviceId = "device-a"
    val publicKeyHex = "a".repeat(64)
    val privateKey = ByteArray(48) { it.toByte() }

    fun legacyJson(
        device: String = deviceId,
        publicKey: String = publicKeyHex,
        secret: ByteArray = privateKey,
    ): String {
        val encoded = Base64.getEncoder().encodeToString(secret)
        return """{"schema":1,"deviceId":"$device","publicKeyHex":"$publicKey",""" +
            """"privateKeyPkcs8Base64":"$encoded"}"""
    }

    test("wrapped identities round-trip and never store the private key in the clear") {
        val aead = newAead()
        val json = SyncIdentityEnvelope.encode(aead, deviceId, publicKeyHex, privateKey)

        json shouldContain "\"schema\":2"
        json shouldNotContain Base64.getEncoder().encodeToString(privateKey)

        val decoded = SyncIdentityEnvelope.decode(aead, json, deviceId)
        decoded.deviceId shouldBe deviceId
        decoded.publicKeyHex shouldBe publicKeyHex
        decoded.privateKeyPkcs8.toList() shouldBe privateKey.toList()
        decoded.needsRewrap shouldBe false
    }

    test("a legacy plaintext identity decodes once and is flagged for rewrapping") {
        val decoded = SyncIdentityEnvelope.decode(newAead(), legacyJson(), deviceId)

        decoded.needsRewrap shouldBe true
        decoded.privateKeyPkcs8.toList() shouldBe privateKey.toList()
        decoded.publicKeyHex shouldBe publicKeyHex
    }

    test("rewrapping a legacy identity preserves the advertised public identity") {
        val aead = newAead()
        val migrated = SyncIdentityEnvelope.decode(aead, legacyJson(), deviceId).let { decoded ->
            SyncIdentityEnvelope.encode(aead, decoded.deviceId, decoded.publicKeyHex, decoded.privateKeyPkcs8)
        }

        val reopened = SyncIdentityEnvelope.decode(aead, migrated, deviceId)
        reopened.publicKeyHex shouldBe publicKeyHex
        reopened.deviceId shouldBe deviceId
        reopened.privateKeyPkcs8.toList() shouldBe privateKey.toList()
        reopened.needsRewrap shouldBe false
    }

    test("a different wrap key reports KeyUnavailable rather than corruption") {
        val json = SyncIdentityEnvelope.encode(newAead(), deviceId, publicKeyHex, privateKey)

        val error = shouldThrow<SyncIdentityDecodeException> {
            SyncIdentityEnvelope.decode(newAead(), json, deviceId)
        }
        error.failure shouldBe SyncIdentityFailure.KeyUnavailable
    }

    test("ciphertext is bound to the device id it was minted for") {
        val aead = newAead()
        val json = SyncIdentityEnvelope.encode(aead, deviceId, publicKeyHex, privateKey)
        val relabelled = json.replace("\"deviceId\":\"$deviceId\"", "\"deviceId\":\"device-b\"")

        shouldThrow<SyncIdentityDecodeException> {
            SyncIdentityEnvelope.decode(aead, relabelled, "device-b")
        }.failure shouldBe SyncIdentityFailure.KeyUnavailable
    }

    test("an identity minted for another device id is rejected before any crypto") {
        val aead = newAead()
        val json = SyncIdentityEnvelope.encode(aead, deviceId, publicKeyHex, privateKey)

        shouldThrow<SyncIdentityDecodeException> {
            SyncIdentityEnvelope.decode(aead, json, "device-b")
        }.failure shouldBe SyncIdentityFailure.DeviceIdMismatch
    }

    test("malformed payloads report corruption") {
        val aead = newAead()

        shouldThrow<SyncIdentityDecodeException> {
            SyncIdentityEnvelope.decode(aead, "not json at all", deviceId)
        }.failure shouldBe SyncIdentityFailure.Corrupt
        shouldThrow<SyncIdentityDecodeException> {
            SyncIdentityEnvelope.decode(aead, legacyJson(publicKey = "zz"), deviceId)
        }.failure shouldBe SyncIdentityFailure.Corrupt
        shouldThrow<SyncIdentityDecodeException> {
            SyncIdentityEnvelope.decode(
                aead,
                """{"schema":2,"deviceId":"$deviceId","publicKeyHex":"$publicKeyHex"}""",
                deviceId,
            )
        }.failure shouldBe SyncIdentityFailure.Corrupt
        shouldThrow<SyncIdentityDecodeException> {
            SyncIdentityEnvelope.decode(
                aead,
                """{"schema":99,"deviceId":"$deviceId","publicKeyHex":"$publicKeyHex"}""",
                deviceId,
            )
        }.failure shouldBe SyncIdentityFailure.Corrupt
    }

    test("tampered ciphertext is rejected") {
        val aead = newAead()
        val json = SyncIdentityEnvelope.encode(aead, deviceId, publicKeyHex, privateKey)
        val wrapped = Regex("\"privateKeyWrappedBase64\":\"([^\"]+)\"").find(json)!!.groupValues[1]
        val flipped = Base64.getDecoder().decode(wrapped).also { it[it.size - 1] = (it.last() + 1).toByte() }
        val tampered = json.replace(wrapped, Base64.getEncoder().encodeToString(flipped))

        shouldThrow<SyncIdentityDecodeException> {
            SyncIdentityEnvelope.decode(aead, tampered, deviceId)
        }.failure shouldBe SyncIdentityFailure.KeyUnavailable
    }

    test("a successful write leaves exactly one readable copy") {
        val dir = Files.createTempDirectory("sync-identity").toFile()
        val target = File(dir, "identity.json")

        DurableIdentityFile.write(target, "first")
        DurableIdentityFile.write(target, "second")

        target.readText(UTF_8) shouldBe "second"
        DurableIdentityFile.backupOf(target).exists() shouldBe false
        DurableIdentityFile.tempOf(target).exists() shouldBe false
        DurableIdentityFile.readableCopies(target) shouldBe listOf(target)
    }

    test("a failed replacement restores the previous contents") {
        val dir = Files.createTempDirectory("sync-identity").toFile()
        val target = File(dir, "identity.json")
        DurableIdentityFile.write(target, "the only valid identity")

        // A directory occupying the target path makes both the rename and the copy fail, which is
        // the shape the old delete-then-rename fallback turned into permanent data loss.
        val blocker = DurableIdentityFile.tempOf(target)
        blocker.delete()
        blocker.mkdirs()
        File(blocker, "occupied").writeText("x")

        shouldThrow<IOException> { DurableIdentityFile.write(target, "replacement") }

        DurableIdentityFile.readableCopies(target).isNotEmpty() shouldBe true
        val surviving = DurableIdentityFile.readableCopies(target).first()
        surviving.readText(UTF_8) shouldBe "the only valid identity"
    }

    test("a lost primary is recovered from the write backup") {
        val dir = Files.createTempDirectory("sync-identity").toFile()
        val target = File(dir, "identity.json")
        DurableIdentityFile.write(target, "identity")
        DurableIdentityFile.backupOf(target).writeText("identity", UTF_8)
        target.delete()

        DurableIdentityFile.exists(target) shouldBe true
        DurableIdentityFile.readableCopies(target) shouldBe listOf(DurableIdentityFile.backupOf(target))
    }

    test("resetting for re-pair removes every copy") {
        val dir = Files.createTempDirectory("sync-identity").toFile()
        val target = File(dir, "identity.json")
        DurableIdentityFile.write(target, "identity")
        DurableIdentityFile.backupOf(target).writeText("identity", UTF_8)

        DurableIdentityFile.deleteAll(target) shouldBe true
        DurableIdentityFile.exists(target) shouldBe false
    }

    test("failure classes stay distinct so the UI can tell recovery paths apart") {
        SyncIdentityFailure.entries.toSet().size shouldBe 3
        SyncIdentityFailure.KeyUnavailable shouldNotBe SyncIdentityFailure.Corrupt
    }
})

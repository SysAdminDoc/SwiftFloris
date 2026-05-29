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

package dev.patrickgold.florisboard.ime.security

import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.nio.charset.StandardCharsets.UTF_8
import java.security.GeneralSecurityException
import java.util.Base64

/**
 * RESEARCH_FEATURE_PLAN.md F28 / second-pass O7 — round-trip + tamper coverage
 * for the [TinkStringPreferenceCrypto] AEAD wire format.
 *
 * Production binds the AEAD to an AndroidKeystore master key, which Robolectric
 * cannot emulate for real crypto. So these tests exercise the
 * [TinkStringPreferenceCrypto.encodeEncrypted] / [TinkStringPreferenceCrypto.decodeEncrypted]
 * seam against a pure-JVM Tink AEAD — covering the load-bearing logic
 * (encrypt → Base64 encode → decode → decrypt, associated-data binding, tamper
 * rejection) on which every encrypted-at-rest claim in `docs/THREAT_MODEL.md`
 * depends. The AndroidKeystore key-derivation itself is Tink-library code and
 * is out of scope for a JVM unit test.
 */
class TinkStringPreferenceCryptoTest : FunSpec({

    fun newAead(): Aead {
        AeadConfig.register()
        return KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM"))
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    test("round-trips a string through encode + decode") {
        val aead = newAead()
        val wire = TinkStringPreferenceCrypto.encodeEncrypted(aead, "prefs", "k", "Hello, Tink!".toByteArray(UTF_8))
        String(TinkStringPreferenceCrypto.decodeEncrypted(aead, "prefs", "k", wire), UTF_8) shouldBe "Hello, Tink!"
    }

    test("round-trips arbitrary bytes") {
        val aead = newAead()
        val payload = ByteArray(256) { it.toByte() }
        val wire = TinkStringPreferenceCrypto.encodeEncrypted(aead, "prefs", "k", payload)
        TinkStringPreferenceCrypto.decodeEncrypted(aead, "prefs", "k", wire).toList() shouldBe payload.toList()
    }

    test("round-trips an empty payload") {
        val aead = newAead()
        val wire = TinkStringPreferenceCrypto.encodeEncrypted(aead, "prefs", "k", ByteArray(0))
        TinkStringPreferenceCrypto.decodeEncrypted(aead, "prefs", "k", wire).size shouldBe 0
    }

    test("two encryptions of the same plaintext produce different ciphertext (GCM nonce)") {
        val aead = newAead()
        val a = TinkStringPreferenceCrypto.encodeEncrypted(aead, "prefs", "k", "same".toByteArray(UTF_8))
        val b = TinkStringPreferenceCrypto.encodeEncrypted(aead, "prefs", "k", "same".toByteArray(UTF_8))
        a shouldNotBe b
    }

    test("tampered ciphertext fails to decrypt") {
        val aead = newAead()
        val wire = TinkStringPreferenceCrypto.encodeEncrypted(aead, "prefs", "k", "secret".toByteArray(UTF_8))
        val raw = Base64.getDecoder().decode(wire)
        raw[raw.size / 2] = (raw[raw.size / 2] + 1).toByte()
        val tampered = Base64.getEncoder().encodeToString(raw)
        shouldThrow<GeneralSecurityException> {
            TinkStringPreferenceCrypto.decodeEncrypted(aead, "prefs", "k", tampered)
        }
    }

    test("wrong prefsFileName (associated data) fails to decrypt") {
        val aead = newAead()
        val wire = TinkStringPreferenceCrypto.encodeEncrypted(aead, "prefsA", "k", "data".toByteArray(UTF_8))
        shouldThrow<GeneralSecurityException> {
            TinkStringPreferenceCrypto.decodeEncrypted(aead, "prefsB", "k", wire)
        }
    }

    test("wrong key (associated data) fails to decrypt") {
        val aead = newAead()
        val wire = TinkStringPreferenceCrypto.encodeEncrypted(aead, "prefs", "k1", "data".toByteArray(UTF_8))
        shouldThrow<GeneralSecurityException> {
            TinkStringPreferenceCrypto.decodeEncrypted(aead, "prefs", "k2", wire)
        }
    }

    test("a different key cannot decrypt another key's ciphertext") {
        val wire = TinkStringPreferenceCrypto.encodeEncrypted(newAead(), "prefs", "k", "data".toByteArray(UTF_8))
        shouldThrow<GeneralSecurityException> {
            TinkStringPreferenceCrypto.decodeEncrypted(newAead(), "prefs", "k", wire)
        }
    }
})

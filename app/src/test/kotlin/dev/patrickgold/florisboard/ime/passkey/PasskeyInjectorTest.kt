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

package dev.patrickgold.florisboard.ime.passkey

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class PasskeyInjectorTest : FunSpec({
    afterEach { PasskeyAdapterRegistry.reset() }

    test("Default adapter says no passkey is available and the request is null") {
        PasskeyAdapter.Default.hasPasskeyFor("example.com") shouldBe false
        PasskeyAdapter.Default.requestAssertion("example.com", byteArrayOf(1, 2, 3)).shouldBeNull()
    }

    test("PasskeyFieldDetector requires both rpId + challenge + password hint") {
        // Password hint present but no rpId/challenge in extras → no hint.
        PasskeyFieldDetector.detect(
            autofillHints = listOf("password"),
            extras = emptyMap(),
        ).shouldBeNull()
        // rpId + challenge present but no password hint → no hint.
        PasskeyFieldDetector.detect(
            autofillHints = listOf("username"),
            extras = mapOf(
                PasskeyFieldDetector.EXTRA_RP_ID to "example.com",
                PasskeyFieldDetector.EXTRA_CHALLENGE to byteArrayOf(1, 2),
            ),
        ).shouldBeNull()
        // Both present → detected.
        val hint = PasskeyFieldDetector.detect(
            autofillHints = listOf("password"),
            extras = mapOf(
                PasskeyFieldDetector.EXTRA_RP_ID to "example.com",
                PasskeyFieldDetector.EXTRA_CHALLENGE to byteArrayOf(1, 2, 3),
            ),
        ).shouldNotBeNull()
        hint.relyingPartyId shouldBe "example.com"
        hint.challenge.contentEquals(byteArrayOf(1, 2, 3)) shouldBe true
    }

    test("PasskeyFieldHint validates non-blank rpId + non-empty challenge") {
        shouldThrow<IllegalArgumentException> { PasskeyFieldHint("", byteArrayOf(1)) }
        shouldThrow<IllegalArgumentException> { PasskeyFieldHint("rp", byteArrayOf()) }
    }

    test("PasskeyAssertionRequest enforces non-blank base64url fields") {
        shouldThrow<IllegalArgumentException> {
            PasskeyAssertionRequest("", "x", "y", "z", null, "w")
        }
        shouldThrow<IllegalArgumentException> {
            PasskeyAssertionRequest("rp", "", "y", "z", null, "w")
        }
    }

    test("PasskeyFieldHint equality compares challenge bytes by content") {
        val a = PasskeyFieldHint("example.com", byteArrayOf(1, 2, 3))
        val b = PasskeyFieldHint("example.com", byteArrayOf(1, 2, 3))
        val c = PasskeyFieldHint("example.com", byteArrayOf(1, 2, 4))
        (a == b) shouldBe true
        (a == c) shouldBe false
        a.hashCode() shouldBe b.hashCode()
    }

    test("Registry default + replace + reset works") {
        val custom = object : PasskeyAdapter {
            override fun hasPasskeyFor(relyingPartyId: String) = true
            override suspend fun requestAssertion(relyingPartyId: String, challenge: ByteArray) =
                PasskeyAssertionRequest(relyingPartyId, "a", "b", "c", null, "d")
        }
        PasskeyAdapterRegistry.setActive(custom)
        PasskeyAdapterRegistry.active.hasPasskeyFor("rp") shouldBe true
        PasskeyAdapterRegistry.reset()
        PasskeyAdapterRegistry.active.hasPasskeyFor("rp") shouldBe false
    }
})

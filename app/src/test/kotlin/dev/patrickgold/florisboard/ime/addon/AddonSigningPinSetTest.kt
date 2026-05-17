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

package dev.patrickgold.florisboard.ime.addon

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private const val PIN_SHA_A =
    "12:34:56:78:9A:BC:DE:F0:12:34:56:78:9A:BC:DE:F0:" +
        "12:34:56:78:9A:BC:DE:F0:12:34:56:78:9A:BC:DE:F0"
private const val PIN_SHA_B =
    "FE:DC:BA:98:76:54:32:10:FE:DC:BA:98:76:54:32:10:" +
        "FE:DC:BA:98:76:54:32:10:FE:DC:BA:98:76:54:32:10"

private fun pinnedManifest(
    packageName: String,
    signingCertSha256: String = PIN_SHA_A,
): AddonManifest = AddonManifest(
    packageName = packageName,
    type = AddonType.DICTIONARY_PACK,
    version = 1L,
    displayName = packageName,
    descriptorResourceId = 1234,
    licenseSpdxId = "Apache-2.0",
    signingCertSha256 = signingCertSha256,
    bundleSizeBytes = 4096L,
)

class AddonSigningPinSetTest : FunSpec({
    test("parse decodes newline-separated package fingerprints") {
        val pins = AddonSigningPinSet.parse(
            """
            org.swiftfloris.dict.de=$PIN_SHA_B
            org.swiftfloris.dict.pl=$PIN_SHA_A
            """.trimIndent(),
        )

        pins.asMap() shouldBe mapOf(
            "org.swiftfloris.dict.de" to PIN_SHA_B,
            "org.swiftfloris.dict.pl" to PIN_SHA_A,
        )
    }

    test("parse ignores malformed lines instead of crashing") {
        val pins = AddonSigningPinSet.parse(
            """
            not-a-package=$PIN_SHA_A
            org.swiftfloris.dict.bad=not-a-fingerprint
            org.swiftfloris.dict.ok=$PIN_SHA_A
            no-separator
            """.trimIndent(),
        )

        pins.asMap() shouldBe mapOf("org.swiftfloris.dict.ok" to PIN_SHA_A)
    }

    test("encode sorts pins and filters invalid entries") {
        val encoded = AddonSigningPinSet.encode(
            mapOf(
                "org.swiftfloris.dict.pl" to PIN_SHA_A,
                "bad package" to PIN_SHA_A,
                "org.swiftfloris.dict.de" to PIN_SHA_B,
            ),
        )

        encoded shouldBe "org.swiftfloris.dict.de=$PIN_SHA_B\norg.swiftfloris.dict.pl=$PIN_SHA_A"
    }

    test("withFirstSeen preserves existing pins and adds new packages") {
        val pins = AddonSigningPinSet.parse("org.swiftfloris.dict.pl=$PIN_SHA_A")
            .withFirstSeen(pinnedManifest("org.swiftfloris.dict.pl", PIN_SHA_B))
            .withFirstSeen(pinnedManifest("org.swiftfloris.dict.de", PIN_SHA_B))

        pins.asMap() shouldBe mapOf(
            "org.swiftfloris.dict.de" to PIN_SHA_B,
            "org.swiftfloris.dict.pl" to PIN_SHA_A,
        )
    }

    test("registry can round-trip through the pin set codec") {
        val registry = AddonRegistry.fromPinnedSigningPinSet(
            AddonSigningPinSet.parse("org.swiftfloris.dict.pl=$PIN_SHA_A"),
        )

        registry.refresh(listOf(pinnedManifest("org.swiftfloris.dict.de", PIN_SHA_B)))

        val encoded = registry.pinnedSigningPinSet().encode()
        encoded shouldBe "org.swiftfloris.dict.de=$PIN_SHA_B\norg.swiftfloris.dict.pl=$PIN_SHA_A"
    }
})

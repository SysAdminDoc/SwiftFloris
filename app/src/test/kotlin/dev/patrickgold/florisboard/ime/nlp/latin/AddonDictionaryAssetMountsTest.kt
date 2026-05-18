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

package dev.patrickgold.florisboard.ime.nlp.latin

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.ByteArrayInputStream

class AddonDictionaryAssetMountsTest : FunSpec({
    test("addon asset paths round-trip package name and nested asset path") {
        val encoded = AddonDictionaryAssetMounts.addonAssetPath(
            packageName = "org.swiftfloris.dict.pl",
            assetPath = "ime/dict/pl.fldic",
        )

        AddonDictionaryAssetMounts.decodeAddonAssetPath(encoded) shouldBe
            AddonDictionaryAssetMounts.AddonAssetPath(
                packageName = "org.swiftfloris.dict.pl",
                assetPath = "ime/dict/pl.fldic",
            )
    }

    test("bundled and malformed paths do not decode as addon assets") {
        AddonDictionaryAssetMounts.decodeAddonAssetPath("ime/dict/pl.fldic") shouldBe null
        AddonDictionaryAssetMounts.decodeAddonAssetPath("addon://") shouldBe null
        AddonDictionaryAssetMounts.decodeAddonAssetPath("addon://org.swiftfloris.dict.pl") shouldBe null
    }

    test("addon asset reads are capped before materializing oversized text") {
        val accepted = with(AddonDictionaryAssetMounts) {
            ByteArrayInputStream("small".encodeToByteArray())
                .readUtf8WithLimit(maxBytes = 5L)
        }
        val rejected = with(AddonDictionaryAssetMounts) {
            ByteArrayInputStream("too-large".encodeToByteArray())
                .readUtf8WithLimit(maxBytes = 5L)
        }

        accepted shouldBe "small"
        rejected shouldBe null
    }
})

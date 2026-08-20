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

package dev.patrickgold.florisboard.ime.media.sticker

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class BundledStickerRepositoryTest : FunSpec({
    test("bundled stickers expose multiple stable local packs") {
        BundledStickerRepository.packs shouldHaveAtLeastSize 2
        BundledStickerRepository.packs.flatMap { it.stickers }.map { it.fileName }.let { fileNames ->
            fileNames.distinct() shouldHaveSize fileNames.size
        }
        BundledStickerRepository.allStickers().all { it.packId.isNotBlank() && it.id.isNotBlank() } shouldBe true
    }

    test("sticker search matches labels and keywords") {
        BundledStickerRepository.search("thank").map { it.id } shouldContain "thanks"
        BundledStickerRepository.search("okay").map { it.id } shouldContain "on_it"
    }

})

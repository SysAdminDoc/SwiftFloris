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
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class UserStickerRepositoryTest : FunSpec({
    test("builds a user sticker pack from supported local image documents") {
        val pack = UserStickerRepository.packFromDocuments(
            displayName = "My Stickers",
            documents = listOf(
                document("content://stickers/tree/ok", "thumbs-up.PNG", "application/octet-stream"),
                document("content://stickers/tree/laugh", "laugh.webp", "image/webp"),
                document("content://stickers/tree/readme", "readme.txt", "text/plain"),
            ),
        ).shouldNotBeNull()

        pack.id shouldBe UserStickerRepository.PackId
        pack.name shouldBe "My Stickers"
        pack.stickers shouldHaveSize 2
        pack.stickers.map { it.label } shouldBe listOf("laugh", "thumbs up")
        pack.stickers.map { it.mimeType } shouldBe listOf("image/webp", "image/png")
        pack.stickers.all { it.sourceUri != null && it.id.isNotBlank() } shouldBe true
    }

    test("returns null for folders without supported sticker images") {
        UserStickerRepository.packFromDocuments(
            listOf(
                document("content://stickers/tree/pdf", "manual.pdf", "application/pdf"),
                document("content://stickers/tree/raw", "raw.bin", null),
            ),
        ).shouldBeNull()
    }

    test("caps imported stickers and de-duplicates repeated document URIs") {
        val documents = buildList {
            add(document("content://stickers/tree/duplicate", "duplicate.png", "image/png"))
            add(document("content://stickers/tree/duplicate", "duplicate-again.png", "image/png"))
            repeat(UserStickerRepository.MaxStickers + 5) { index ->
                add(document("content://stickers/tree/$index", "sticker-$index.png", "image/png"))
            }
        }

        val pack = UserStickerRepository.packFromDocuments(documents).shouldNotBeNull()

        pack.stickers shouldHaveSize UserStickerRepository.MaxStickers
        pack.stickers.map { it.sourceUri }.distinct() shouldHaveSize UserStickerRepository.MaxStickers
    }
})

private fun document(
    uri: String,
    displayName: String,
    mimeType: String?,
): UserStickerDocument {
    return UserStickerDocument(
        uri = uri,
        displayName = displayName,
        mimeType = mimeType,
    )
}

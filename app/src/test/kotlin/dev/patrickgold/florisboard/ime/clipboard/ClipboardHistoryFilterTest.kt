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

package dev.patrickgold.florisboard.ime.clipboard

import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardItem
import dev.patrickgold.florisboard.ime.clipboard.provider.ItemType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private fun text(id: Long, content: String, sensitive: Boolean = false): ClipboardItem = ClipboardItem(
    id = id,
    type = ItemType.TEXT,
    text = content,
    uri = null,
    creationTimestampMs = id * 1000L,
    isPinned = false,
    mimeTypes = listOf("text/plain"),
    isSensitive = sensitive,
)

private fun image(id: Long): ClipboardItem = media(id, ItemType.IMAGE, "image/png")

private fun video(id: Long): ClipboardItem = media(id, ItemType.VIDEO, "video/mp4")

private fun media(id: Long, type: ItemType, mimeType: String): ClipboardItem = ClipboardItem(
    id = id,
    type = type,
    text = null,
    uri = null,
    creationTimestampMs = id * 1000L,
    isPinned = false,
    mimeTypes = listOf(mimeType),
)

class ClipboardHistoryFilterTest : FunSpec({

    test("blank query returns the input list unchanged (order preserved)") {
        val items = listOf(text(1, "hello"), text(2, "world"), image(3))
        ClipboardHistoryFilter.filterByQuery(items, "") shouldBe items
        ClipboardHistoryFilter.filterByQuery(items, "   ") shouldBe items
        ClipboardHistoryFilter.filterByQuery(items, "\t\n") shouldBe items
    }

    test("substring match is case-insensitive on TEXT items") {
        val items = listOf(text(1, "Hello World"), text(2, "Foo Bar"), text(3, "HELLO again"))
        ClipboardHistoryFilter.filterByQuery(items, "hello") shouldBe listOf(items[0], items[2])
        ClipboardHistoryFilter.filterByQuery(items, "WORLD") shouldBe listOf(items[0])
        ClipboardHistoryFilter.filterByQuery(items, "foo bar") shouldBe listOf(items[1])
    }

    test("non-TEXT items (IMAGE / VIDEO) never match a non-blank query") {
        val items = listOf(image(1), text(2, "hello"), image(3))
        ClipboardHistoryFilter.filterByQuery(items, "hello") shouldBe listOf(items[1])
    }

    test("query that matches nothing returns empty list") {
        val items = listOf(text(1, "hello"), text(2, "world"))
        ClipboardHistoryFilter.filterByQuery(items, "qwerty") shouldBe emptyList()
    }

    test("sensitive items remain visible when their text matches the query") {
        val items = listOf(text(1, "public note"), text(2, "OTP 123456", sensitive = true))
        ClipboardHistoryFilter.filterByQuery(items, "otp") shouldBe listOf(items[1])
    }

    test("query whitespace is trimmed before matching") {
        val items = listOf(text(1, "hello"), text(2, "world"))
        ClipboardHistoryFilter.filterByQuery(items, "  hello  ") shouldBe listOf(items[0])
    }

    test("matches predicate respects the per-item type contract") {
        ClipboardHistoryFilter.matches(text(1, "hello"), "hello") shouldBe true
        // A caller that forgets to lower-case its query used to get a silent miss. Matching is
        // case-insensitive on both sides now, so the pre-lowering is an optimisation, not a
        // precondition callers can get wrong.
        ClipboardHistoryFilter.matches(text(1, "hello"), "HELLO") shouldBe true
        ClipboardHistoryFilter.matches(image(1), "anything") shouldBe false
        ClipboardHistoryFilter.matches(text(1, "hello"), "") shouldBe true
    }

    test("input list ordering is preserved (stable filter)") {
        val items = listOf(text(3, "alpha"), text(1, "beta"), text(2, "alpha"))
        ClipboardHistoryFilter.filterByQuery(items, "alpha") shouldBe listOf(items[0], items[2])
    }

    test("empty input list with non-blank query returns empty list") {
        ClipboardHistoryFilter.filterByQuery(emptyList(), "hello") shouldBe emptyList()
    }

    test("query and type filters compose in palette order") {
        val items = listOf(
            text(1, "alpha text"),
            image(2),
            text(3, "beta text"),
            video(4),
        )

        ClipboardHistoryFilter
            .filterByQueryAndType(
                history = ClipboardHistory(items),
                query = "alpha",
                activeTypes = setOf(ItemType.TEXT, ItemType.IMAGE),
            )
            .all shouldBe listOf(items[0])
    }

    test("media type filters are preserved when the search query is blank") {
        val items = listOf(text(1, "alpha text"), image(2), video(3))

        ClipboardHistoryFilter
            .filterByQueryAndType(
                history = ClipboardHistory(items),
                query = " ",
                activeTypes = setOf(ItemType.IMAGE, ItemType.VIDEO),
            )
            .all shouldBe listOf(items[1], items[2])
    }

    test("blank type set keeps query-only search over full history") {
        val items = listOf(text(1, "alpha"), image(2), text(3, "alphabet"))

        ClipboardHistoryFilter
            .filterByQueryAndType(
                history = ClipboardHistory(items),
                query = "alpha",
                activeTypes = emptySet(),
            )
            .all shouldBe listOf(items[0], items[2])
    }
})

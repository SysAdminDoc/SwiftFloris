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

private fun image(id: Long): ClipboardItem = ClipboardItem(
    id = id,
    type = ItemType.IMAGE,
    text = null,
    uri = null,
    creationTimestampMs = id * 1000L,
    isPinned = false,
    mimeTypes = listOf("image/png"),
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
        ClipboardHistoryFilter.matches(text(1, "hello"), "HELLO") shouldBe false  // pre-lowered
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
})

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
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import java.lang.management.ManagementFactory

/**
 * A full clipboard history of near-limit clips, replayed through the paths a user drives.
 *
 * Three keyboards report multi-second stalls or crashes on large clipboard payloads, one at around
 * 120 KiB of URLs. SwiftFloris bounds retention at 64 KiB, but the bound only covers what enters
 * the store; the panel then filters over the decrypted rows on every keystroke, which is where the
 * cost lands. Nothing replayed that until now.
 *
 * These assert **allocation**, not wall-clock. A time budget loose enough not to flake on a slow
 * machine is also loose enough to pass on a desktop JVM with the defect present — measured: the
 * old `text.lowercase().contains(...)` filtered this fixture inside any tolerable time budget here,
 * while allocating megabytes per call. Allocated bytes are what actually differs, they are what
 * drives GC pressure on a phone, and they are stable across hosts.
 */
class ClipboardLargeClipTest : FunSpec({
    val nearLimitLength = ClipboardTextRetentionPolicy.MAX_RETAINED_UTF8_BYTES - 64
    val historyDepth = 200
    val historyBytes = nearLimitLength.toLong() * historyDepth

    // Mixed case on purpose. `String.lowercase()` returns the receiver unchanged when no character
    // differs, so an all-lowercase fixture would hide the copy the old filter made — real clips
    // (URLs, messages, addresses) are not all-lowercase.
    fun nearLimitClip(id: Long, needle: String = ""): ClipboardItem {
        val filler = "The Quick Brown Fox Jumps Over The Lazy Dog ".repeat(
            nearLimitLength / 44 + 1,
        ).take(nearLimitLength - needle.length)
        return ClipboardItem(
            id = id,
            type = ItemType.TEXT,
            text = filler + needle,
            uri = null,
            creationTimestampMs = id * 1000L,
            isPinned = false,
            mimeTypes = listOf("text/plain"),
            isSensitive = false,
        )
    }

    test("a history full of near-limit clips is all retainable, so the store really does hold this much") {
        val history = List(historyDepth) { nearLimitClip(it.toLong()) }

        history.all { ClipboardTextRetentionPolicy.shouldRetain(it) } shouldBe true
        history.first().text!!.length shouldBe nearLimitLength
    }

    test("a 120 KiB clip is rejected from history without encoding a copy of it") {
        val oversized = "x".repeat(120 * 1024)
        var retained = true

        val allocated = allocatedBytesDuring { retained = ClipboardTextRetentionPolicy.shouldRetain(oversized) }

        retained shouldBe false
        // Rejected from the UTF-16 length alone. `toByteArray()` would allocate 120 KiB here, and
        // a full walk would still touch every character.
        allocated shouldBeLessThan 16 * 1024L
    }

    test("searching a full history of near-limit clips does not copy the history to do it") {
        val history = ClipboardHistory(
            List(historyDepth) { index ->
                nearLimitClip(index.toLong(), needle = if (index == historyDepth - 1) "NEEDLE" else "")
            },
        )
        var filtered = ClipboardHistory(emptyList())

        // Warm up so class loading is not counted as allocation for the measured call.
        ClipboardHistoryFilter.filterByQueryAndType(history, "needle", emptySet())
        val allocated = allocatedBytesDuring {
            filtered = ClipboardHistoryFilter.filterByQueryAndType(history, "needle", emptySet())
        }

        filtered.all.size shouldBe 1
        // The fixture holds about 12 MiB of text. The previous implementation lowercased every
        // clip into a fresh string before searching it, so a single keystroke allocated a copy of
        // the whole history. A tenth of the history's size is far above what the in-place scan
        // needs and far below what a full copy costs.
        allocated shouldBeLessThan historyBytes / 10
    }

    test("a query that matches nothing costs no more than one that matches") {
        val history = ClipboardHistory(List(historyDepth) { nearLimitClip(it.toLong()) })
        var filtered = ClipboardHistory(listOf(nearLimitClip(0)))

        ClipboardHistoryFilter.filterByQueryAndType(history, "zzz-absent", emptySet())
        val allocated = allocatedBytesDuring {
            filtered = ClipboardHistoryFilter.filterByQueryAndType(history, "zzz-absent", emptySet())
        }

        filtered.all.size shouldBe 0
        allocated shouldBeLessThan historyBytes / 10
    }

    test("case-insensitive matching still works at this size") {
        val history = ClipboardHistory(
            List(historyDepth) { index ->
                nearLimitClip(index.toLong(), needle = if (index == 0) "MiXeDcAsE" else "")
            },
        )

        ClipboardHistoryFilter.filterByQueryAndType(history, "mixedcase", emptySet()).all.size shouldBe 1
        ClipboardHistoryFilter.filterByQueryAndType(history, "MIXEDCASE", emptySet()).all.size shouldBe 1
    }
})

/**
 * Bytes allocated on this thread while [block] ran.
 *
 * Uses the HotSpot thread allocation counter. It is exact enough for the order-of-magnitude
 * question these tests ask, and unlike elapsed time it does not change with host speed or load.
 */
private fun allocatedBytesDuring(block: () -> Unit): Long {
    val bean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
    check(bean.isThreadAllocatedMemorySupported) { "thread allocation counters are unavailable" }
    bean.isThreadAllocatedMemoryEnabled = true
    val threadId = Thread.currentThread().threadId()
    val before = bean.getThreadAllocatedBytes(threadId)
    block()
    return bean.getThreadAllocatedBytes(threadId) - before
}

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

import android.content.Context
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardHistoryDao
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardItem
import dev.patrickgold.florisboard.ime.clipboard.provider.ItemType
import dev.patrickgold.florisboard.lib.devtools.flogError

/**
 * What a clipboard delete or clear left behind. [retained] holds the items that are still stored,
 * whole or in part, and so are still in history for a retry. [historyReachable] is false when the
 * history store couldn't be read, so nothing could be checked.
 */
data class ClipboardDeletionResult(
    val retained: List<ClipboardItem>,
    val historyReachable: Boolean = true,
) {
    val succeeded: Boolean
        get() = historyReachable && retained.isEmpty()

    companion object {
        val Done = ClipboardDeletionResult(retained = emptyList())
    }
}

/**
 * Deletes clipboard history items with their media and reports what's left. An item's media goes
 * first and its history row only once that's confirmed, so an item whose file couldn't be deleted
 * stays in history, where the user can see it and try again. Blocking I/O: call it off the main
 * thread.
 */
internal object ClipboardHistoryDeletion {
    /**
     * Deletes [item]. With [onlyIfUnpinned] (clearing the primary clip), every unpinned row holding
     * the same content goes, found by content alone: the primary clip's id can be 0 (its row not
     * written yet), stale (a repeat copy moved it) or reused by a restore, and its sensitivity flag
     * may have been changed in history since. A pinned row is left alone and counts as done.
     */
    fun deleteOne(
        context: Context,
        dao: ClipboardHistoryDao?,
        item: ClipboardItem,
        onlyIfUnpinned: Boolean,
    ): ClipboardDeletionResult {
        if (!onlyIfUnpinned) return deleteExact(context, dao, listOf(item), onlyIfUnpinned = false)
        return deleteStored(context, dao, fallback = listOf(item)) {
            !it.isPinned && holdsSameClip(it, item, matchSensitivity = false)
        }
    }

    /**
     * Deletes the rows of [items] as they're stored now, so a pin or edit made since [items] was
     * read counts. A row that's gone already is done. With [onlyIfUnpinned], a row pinned since is
     * left alone.
     */
    fun deleteExact(
        context: Context,
        dao: ClipboardHistoryDao?,
        items: List<ClipboardItem>,
        onlyIfUnpinned: Boolean,
    ): ClipboardDeletionResult {
        if (items.isEmpty()) return ClipboardDeletionResult.Done
        val ids = items.mapTo(HashSet()) { it.id }
        return deleteStored(context, dao, fallback = items) { it.id in ids && !(onlyIfUnpinned && it.isPinned) }
    }

    fun deleteUnpinned(context: Context, dao: ClipboardHistoryDao?): ClipboardDeletionResult {
        return deleteStored(context, dao) { !it.isPinned }
    }

    fun deleteAll(context: Context, dao: ClipboardHistoryDao?): ClipboardDeletionResult {
        return deleteStored(context, dao) { true }
    }

    /** [fallback] is what's reported as kept when history can't be read. */
    private fun deleteStored(
        context: Context,
        dao: ClipboardHistoryDao?,
        fallback: List<ClipboardItem> = emptyList(),
        selected: (ClipboardItem) -> Boolean,
    ): ClipboardDeletionResult {
        dao ?: return unreachable(fallback)
        val items = try {
            dao.getAll().filter(selected)
        } catch (error: Exception) {
            flogError { "Failed to read clipboard history: ${error.javaClass.simpleName}" }
            return unreachable(fallback)
        }
        return mediaThenRows(context, dao, items)
    }

    private fun mediaThenRows(
        context: Context,
        dao: ClipboardHistoryDao,
        items: List<ClipboardItem>,
    ): ClipboardDeletionResult {
        if (items.isEmpty()) return ClipboardDeletionResult.Done
        val (mediaGone, mediaKept) = items.partition { it.deleteMedia(context) }
        if (mediaGone.isEmpty()) return ClipboardDeletionResult(mediaKept)
        return try {
            dao.delete(mediaGone)
            ClipboardDeletionResult(mediaKept)
        } catch (error: Exception) {
            flogError { "Failed to delete clipboard history rows: ${error.javaClass.simpleName}" }
            ClipboardDeletionResult(mediaKept + mediaGone)
        }
    }

    private fun unreachable(items: List<ClipboardItem>): ClipboardDeletionResult {
        return ClipboardDeletionResult(retained = items, historyReachable = false)
    }
}

/**
 * True when the history row [stored] holds [clip]: the same text (with the same sensitivity, unless
 * [matchSensitivity] is off), or media at the same URI. Media copied from other apps is cloned to a
 * new URI, so a URI match is one of our own clips.
 */
internal fun holdsSameClip(stored: ClipboardItem, clip: ClipboardItem, matchSensitivity: Boolean = true): Boolean {
    return if (stored.type == ItemType.TEXT) {
        clip.type == ItemType.TEXT && stored.text == clip.text &&
            (!matchSensitivity || stored.isSensitive == clip.isSensitive)
    } else {
        clip.type != ItemType.TEXT && stored.uri != null && stored.uri == clip.uri
    }
}

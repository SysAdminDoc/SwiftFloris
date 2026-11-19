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

package dev.patrickgold.florisboard.ime.media.emoji

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.florisboard.lib.android.bufferedReader
import java.io.IOException

/**
 * How often people use each emoji, most used first, from Unicode's emoji-freq.txt (written by
 * utils/build_emoji_data.py). Emoji suggestions that score the same are put in this order, so ":good" offers
 * 👍 before the smileys that also list "good" as a keyword.
 */
internal object EmojiUsageRank {
    private const val TAG = "EmojiUsageRank"
    const val ASSET_PATH = "ime/media/emoji_usage_rank.txt"

    // The keycap emoji #️⃣ is "#" followed by one of these, so its line isn't a comment.
    private val VariationSelector16 = Char(0xFE0F)
    private val CombiningEnclosingKeycap = Char(0x20E3)

    @Volatile
    private var loaded: Map<String, Int>? = null
    private val loadLock = Mutex()

    suspend fun get(context: Context): Map<String, Int> {
        loaded?.let { return it }
        return loadLock.withLock {
            loaded ?: withContext(Dispatchers.IO) { load(context) }.also { loaded = it }
        }
    }

    private fun load(context: Context): Map<String, Int> {
        return try {
            context.assets.bufferedReader(ASSET_PATH).useLines(::parse)
        } catch (e: IOException) {
            Log.w(TAG, "Can't read $ASSET_PATH, so emoji suggestions that tie keep the palette order", e)
            emptyMap()
        }
    }

    /** Each data line is one rank: an emoji and its skin tone and gender forms, separated by spaces. */
    internal fun parse(lines: Sequence<String>): Map<String, Int> {
        val ranks = HashMap<String, Int>()
        lines
            .filter { line ->
                line.isNotBlank() &&
                    !(line.startsWith("#") && line.getOrNull(1).let { it != VariationSelector16 && it != CombiningEnclosingKeycap })
            }
            .forEachIndexed { rank, line ->
                line.split(' ').forEach { emoji ->
                    if (emoji.isNotEmpty()) ranks.putIfAbsent(emoji, rank)
                }
            }
        return ranks
    }
}

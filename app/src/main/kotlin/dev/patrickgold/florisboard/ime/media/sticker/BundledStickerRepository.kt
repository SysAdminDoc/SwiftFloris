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

import androidx.annotation.ColorInt
import java.util.Locale

data class Sticker(
    val packId: String,
    val id: String,
    val label: String,
    val emoji: String,
    val keywords: List<String>,
    @param:ColorInt val backgroundColor: Int,
    @param:ColorInt val accentColor: Int,
    @param:ColorInt val textColor: Int = 0xFFFFFFFF.toInt(),
    val mimeType: String = "image/png",
    val sourceUri: String? = null,
    val displayName: String = "$label.png",
) {
    val fileName: String = "${packId}_$id.png"
    val commitMimeTypes: List<String> = listOf(mimeType)
}

data class StickerPack(
    val id: String,
    val name: String,
    val stickers: List<Sticker>,
)

object BundledStickerRepository {
    const val MimeType = "image/png"

    val packs: List<StickerPack> = listOf(
        StickerPack(
            id = "swift_reactions",
            name = "Swift reactions",
            stickers = listOf(
                sticker("swift_reactions", "yes", "Yes", "✅", listOf("yes", "done", "check", "approved"), 0xFF12322C, 0xFF3EE6A8),
                sticker("swift_reactions", "nope", "Nope", "✕", listOf("no", "nope", "cancel", "decline"), 0xFF35151A, 0xFFFF5C78),
                sticker("swift_reactions", "thanks", "Thanks", "🙏", listOf("thanks", "thank you", "gratitude"), 0xFF2C2143, 0xFFC9A7FF),
                sticker("swift_reactions", "lol", "LOL", "😂", listOf("lol", "laugh", "funny", "haha"), 0xFF33290F, 0xFFFFD166),
                sticker("swift_reactions", "nice", "Nice", "✨", listOf("nice", "great", "sparkle", "perfect"), 0xFF162A43, 0xFF82C8FF),
                sticker("swift_reactions", "seen", "Seen", "👀", listOf("seen", "looking", "watching", "eyes"), 0xFF1F2933, 0xFF9CA3AF),
            ),
        ),
        StickerPack(
            id = "quick_replies",
            name = "Quick replies",
            stickers = listOf(
                sticker("quick_replies", "hello", "Hello", "👋", listOf("hello", "hi", "wave", "hey"), 0xFF102B3C, 0xFF55D6FF),
                sticker("quick_replies", "on_it", "On it", "👍", listOf("on it", "ok", "okay", "got it"), 0xFF14351F, 0xFF78E08F),
                sticker("quick_replies", "love", "Love", "❤", listOf("love", "heart", "like"), 0xFF381529, 0xFFFF6BAA),
                sticker("quick_replies", "sorry", "Sorry", "💬", listOf("sorry", "apology", "my bad"), 0xFF2E2A18, 0xFFE7D37A),
                sticker("quick_replies", "soon", "Soon", "⏳", listOf("soon", "later", "wait", "timer"), 0xFF25214B, 0xFFA5B4FC),
                sticker("quick_replies", "bye", "Bye", "🙌", listOf("bye", "goodbye", "later"), 0xFF222222, 0xFFE5E7EB),
            ),
        ),
    )

    fun find(packId: String, stickerId: String): Sticker? {
        return packs.firstOrNull { it.id == packId }?.stickers?.firstOrNull { it.id == stickerId }
    }

    fun allStickers(): List<Sticker> {
        return packs.flatMap { it.stickers }
    }

    fun search(query: String): List<Sticker> {
        return StickerSearch.search(packs, query)
    }

    private fun sticker(
        packId: String,
        id: String,
        label: String,
        emoji: String,
        keywords: List<String>,
        @ColorInt backgroundColor: Long,
        @ColorInt accentColor: Long,
    ): Sticker {
        return Sticker(
            packId = packId,
            id = id,
            label = label,
            emoji = emoji,
            keywords = keywords,
            backgroundColor = backgroundColor.toInt(),
            accentColor = accentColor.toInt(),
        )
    }

}

object StickerSearch {
    fun search(packs: List<StickerPack>, query: String): List<Sticker> {
        val normalizedQuery = query.normalizedStickerQuery()
        if (normalizedQuery.isBlank()) return emptyList()
        return packs
            .asSequence()
            .flatMap { pack -> pack.stickers.asSequence().map { sticker -> pack to sticker } }
            .mapNotNull { (pack, sticker) ->
                val score = score(pack, sticker, normalizedQuery) ?: return@mapNotNull null
                ScoredSticker(sticker, score)
            }
            .sortedWith(compareBy<ScoredSticker> { it.score }.thenBy { it.sticker.label })
            .map { it.sticker }
            .toList()
    }

    private fun score(pack: StickerPack, sticker: Sticker, normalizedQuery: String): Int? {
        val candidates = buildList {
            add(sticker.label to 0)
            sticker.keywords.forEach { add(it to 0) }
            add(sticker.displayName.substringBeforeLast('.', sticker.displayName) to 1)
            add(pack.name to 3)
        }
        return candidates
            .mapNotNull { (value, offset) ->
                value.normalizedStickerQuery().matchScore(normalizedQuery)?.plus(offset)
            }
            .minOrNull()
    }

    private fun String.matchScore(normalizedQuery: String): Int? {
        return when {
            this == normalizedQuery -> 0
            startsWith(normalizedQuery) -> 1
            contains(normalizedQuery) -> 2
            else -> null
        }
    }

    private data class ScoredSticker(
        val sticker: Sticker,
        val score: Int,
    )
}

internal fun String.normalizedStickerQuery(): String {
    return lowercase(Locale.ROOT)
        .replace('_', ' ')
        .replace('-', ' ')
        .trim()
        .replace(Regex("\\s+"), " ")
}

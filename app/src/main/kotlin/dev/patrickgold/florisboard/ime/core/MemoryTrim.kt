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

package dev.patrickgold.florisboard.ime.core

import android.content.ComponentCallbacks2
import dev.patrickgold.florisboard.FlorisApplication
import dev.patrickgold.florisboard.ime.media.sticker.evictStickerBitmapCache

/**
 * What the keyboard frees when Android asks for memory back, and when.
 *
 * Android 17's per-app memory limiter gives a process a smaller budget while none of its windows
 * are showing, and since Android 14 the only trim levels delivered are
 * [ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN] and [ComponentCallbacks2.TRIM_MEMORY_BACKGROUND].
 * Every cache below refills on demand and has its own bound. This only decides when each one is
 * emptied.
 *
 * Off screen (UI_HIDDEN and up):
 * - sticker bitmaps: at most 24 MB, tiles of 512 px or less
 * - per-app accent colors: 64 entries
 * - smart compose results (512 entries) and translation results (2,048 entries), both keyed by
 *   typed context, so emptying them also drops that text from memory sooner
 *
 * In the background (BACKGROUND and up), also:
 * - per glide pointer: the suggestion cache (5), ideal gestures (512 words) and the pruners built
 *   for other subtypes (up to 6, the active one stays)
 * - word frequency lookups (5,000) and the word list of each subtype used so far
 * - the debug overlay's last 10 spelling results
 *
 * Kept on purpose: the loaded dictionaries and language models (they are the working set, and
 * some, like the advanced prediction unigrams, are filled once at load and never refill on
 * demand), the advanced prediction context cache (100 small entries), and everything durable on
 * disk: settings, clipboard history and the personal dictionary.
 *
 * Nothing here forces a manager into existence: one that was never used has nothing to free.
 */
internal object MemoryTrim {
    enum class Tier { None, OffScreen, Background }

    fun tierFor(level: Int): Tier = when {
        level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> Tier.Background
        level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> Tier.OffScreen
        else -> Tier.None
    }

    fun trim(app: FlorisApplication, level: Int) {
        val tier = tierFor(level)
        if (tier == Tier.None) return
        evictStickerBitmapCache()
        app.perAppAccentController.ifCreated { it.onLowMemory() }
        app.nlpAddonHub.ifCreated { it.trimMemory() }
        if (tier == Tier.Background) {
            app.glideTypingManager.ifCreated { it.trimMemory() }
            app.nlpManager.ifCreated { it.trimMemory() }
        }
    }

    private inline fun <T> Lazy<T>.ifCreated(block: (T) -> Unit) {
        if (isInitialized()) block(value)
    }
}

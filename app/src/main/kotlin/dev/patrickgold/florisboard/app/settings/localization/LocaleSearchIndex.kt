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

package dev.patrickgold.florisboard.app.settings.localization

import dev.patrickgold.florisboard.lib.FlorisLocale
import dev.patrickgold.florisboard.lib.util.foldForSearch

/** How long the language search waits after the last keystroke before it announces the result count. */
internal const val LocaleSearchSettleMillis = 500L

/**
 * The language list's index over [locales]: each name in the phone's language, in its own and in English, and
 * both tag forms.
 */
internal fun localeSearchIndex(locales: List<FlorisLocale>) = LocaleSearchIndex(
    items = locales,
    names = { locale ->
        listOf(locale.displayName(), locale.displayName(locale), locale.displayName(FlorisLocale.ENGLISH))
    },
    tags = { locale -> listOf(locale.languageTag(), locale.localeTag()) },
)

/**
 * The language list's search. [names] match anywhere and [tags] match from the start, both after
 * [foldForSearch], so "francais" finds "français" and "pt-br" finds pt-BR. Each name is folded once here, not
 * on every keystroke.
 */
internal class LocaleSearchIndex<T>(
    items: List<T>,
    names: (T) -> List<String>,
    tags: (T) -> List<String>,
) {
    private class Entry<T>(val item: T, val names: List<String>, val tags: List<String>)

    private val entries = items.map { item ->
        Entry(item, names(item).map(::foldForSearch), tags(item).map(::foldForSearch))
    }

    /** The items whose names or tags match [query], in the order they were given. A blank query keeps them all. */
    fun filter(query: String): List<T> {
        val term = foldForSearch(query.trim())
        if (term.isEmpty()) return entries.map { it.item }
        return entries
            .filter { entry -> entry.names.any { it.contains(term) } || entry.tags.any { it.startsWith(term) } }
            .map { it.item }
    }
}

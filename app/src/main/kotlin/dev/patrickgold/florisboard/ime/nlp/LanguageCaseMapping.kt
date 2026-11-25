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

package dev.patrickgold.florisboard.ime.nlp

import java.util.Locale

/**
 * Case mapping that follows the language's own rules.
 *
 * Kotlin's no-argument `lowercase()` and `uppercase()` apply Unicode's
 * locale-invariant mappings. Those are wrong wherever a language's casing is not
 * one-to-one in ASCII, and Turkish is the standard example: `I` lowercases to
 * `ı`, and `i` uppercases to `İ`. Using the invariant mapping silently breaks
 * dictionary lookup and suggestion casing for every word containing those
 * letters, because the normalized form never matches the stored form.
 *
 * Passing a blank or `null` language keeps the locale-invariant behaviour on
 * purpose. Word lists are decoded once without a language and must keep matching
 * whatever the query path produces, so callers that have no language at hand
 * (asset loading) pass nothing and get the old result.
 */
internal object LanguageCaseMapping {

    fun lowercase(text: String, languageCode: String?): String =
        text.lowercase(localeFor(languageCode))

    fun uppercase(text: String, languageCode: String?): String =
        text.uppercase(localeFor(languageCode))

    /**
     * Uppercase the first code point and leave the rest as it is. Operating on a
     * code point rather than a [Char] keeps a surrogate pair intact.
     */
    fun capitalizeFirst(text: String, languageCode: String?): String {
        if (text.isEmpty()) return text
        val firstCodePoint = text.codePointAt(0)
        val firstLength = Character.charCount(firstCodePoint)
        return text.substring(0, firstLength).uppercase(localeFor(languageCode)) +
            text.substring(firstLength)
    }

    /** The [Locale] to map case with, or [Locale.ROOT] when no language is known. */
    fun localeFor(languageCode: String?): Locale {
        val tag = languageCode?.trim().orEmpty()
        return if (tag.isEmpty()) Locale.ROOT else Locale.forLanguageTag(tag)
    }
}

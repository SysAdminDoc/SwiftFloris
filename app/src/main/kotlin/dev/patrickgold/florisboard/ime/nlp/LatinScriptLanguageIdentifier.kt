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

import java.text.Normalizer
import java.util.Locale

internal object LatinScriptLanguageIdentifier {
    fun score(
        currentWord: String,
        previousWords: List<String>,
        locales: List<String>,
    ): Map<String, Double> {
        val activeProfiles = locales
            .mapNotNull { language ->
                val key = language.normalizedLanguageKey()
                Profiles[key]?.let { profile -> key to profile }
            }
            .distinctBy { (key, _) -> key }
        if (activeProfiles.size <= 1) return emptyMap()

        val currentTokens = tokenize(currentWord).takeLast(1)
        val contextTokens = previousWords.flatMap { tokenize(it) }.takeLast(MaxContextTokens)
        if (currentTokens.isEmpty() && contextTokens.isEmpty()) return emptyMap()

        val rawScores = activeProfiles.associate { (language, profile) ->
            language to rawScore(profile, currentTokens, contextTokens)
        }
        val best = rawScores.values.maxOrNull() ?: return emptyMap()
        if (best < MinRawScore) return emptyMap()

        return rawScores.mapValues { (_, score) ->
            val ratio = (score / best).coerceIn(0.0, 1.0)
            val confidence = ratio * ratio * ratio
            if (confidence >= MinReturnedConfidence) confidence else 0.0
        }
    }

    private fun rawScore(
        profile: LanguageProfile,
        currentTokens: List<String>,
        contextTokens: List<String>,
    ): Double {
        var score = 0.0
        for (token in currentTokens) {
            score += scoreToken(token, profile, allowPrefixBonus = true) * CurrentTokenWeight
        }

        var contextWeight = ContextTokenWeight
        for (token in contextTokens.asReversed()) {
            score += scoreToken(token, profile, allowPrefixBonus = false) * contextWeight
            contextWeight *= ContextDecay
        }
        return score
    }

    private fun scoreToken(
        token: String,
        profile: LanguageProfile,
        allowPrefixBonus: Boolean,
    ): Double {
        if (token.length < 2) return 0.0

        var score = 0.0
        if (token in profile.commonWords) {
            score += CommonWordWeight
        }
        if (allowPrefixBonus && profile.prefixes.any { prefix -> token.startsWith(prefix) }) {
            score += PrefixWeight
        }

        val ngramHits = token.ngrams().count { ngram -> ngram in profile.charNgrams }
        score += minOf(NgramWeight * ngramHits, MaxNgramWeight)
        return score
    }

    private fun tokenize(value: String): List<String> {
        val normalized = Normalizer.normalize(
            value.lowercase(Locale.ROOT).replace('\u2019', '\''),
            Normalizer.Form.NFD,
        ).replace(CombiningMarks, "")
        return normalized
            .split(TokenSeparator)
            .asSequence()
            .map { it.trim('\'', '-') }
            .filter { token -> token.length >= 2 && token.any { it.isLetter() } }
            .toList()
    }

    private fun String.normalizedLanguageKey(): String {
        return substringBefore('-')
            .substringBefore('_')
            .lowercase(Locale.ROOT)
    }

    private fun String.ngrams(): Sequence<String> = sequence {
        for (size in 2..4) {
            if (length < size) continue
            for (index in 0..length - size) {
                yield(substring(index, index + size))
            }
        }
    }

    private data class LanguageProfile(
        val commonWords: Set<String>,
        val charNgrams: Set<String>,
        val prefixes: Set<String>,
    )

    private const val CurrentTokenWeight = 4.0
    private const val ContextTokenWeight = 1.0
    private const val ContextDecay = 0.85
    private const val CommonWordWeight = 2.6
    private const val PrefixWeight = 2.2
    private const val NgramWeight = 0.38
    private const val MaxNgramWeight = 2.4
    private const val MaxContextTokens = 4
    private const val MinRawScore = 1.35
    private const val MinReturnedConfidence = 0.05
    private val CombiningMarks = Regex("\\p{Mn}+")
    private val TokenSeparator = Regex("[^\\p{L}'-]+")

    private val Profiles = mapOf(
        "en" to LanguageProfile(
            commonWords = setOf(
                "the", "and", "you", "this", "that", "with", "for", "not", "have", "are",
                "was", "were", "from", "what", "when", "where", "why", "how", "hello", "thanks",
            ),
            charNgrams = setOf(
                "th", "he", "in", "ing", "tion", "you", "wh", "sh", "ch", "ck", "ll", "oo",
                "ee", "ea", "ou", "gh", "ght", "ould", "ment", "ness",
            ),
            prefixes = setOf("th", "wh", "sh", "ch", "you", "tha", "thi", "whe"),
        ),
        "es" to LanguageProfile(
            commonWords = setOf(
                "hola", "gracias", "que", "para", "pero", "como", "los", "las", "una", "uno",
                "estoy", "esta", "este", "con", "por", "del", "muy", "bien", "buenos", "buenas",
            ),
            charNgrams = setOf(
                "qu", "que", "gra", "rac", "aci", "cia", "ias", "cion", "los", "las", "por",
                "est", "ado", "ada", "men", "con", "una", "del", "muy", "bue",
            ),
            prefixes = setOf("ho", "hol", "gra", "grac", "que", "por", "para", "est", "bue"),
        ),
        "fr" to LanguageProfile(
            commonWords = setOf(
                "merci", "bonjour", "pour", "avec", "est", "pas", "des", "les", "vous", "nous",
                "une", "dans", "que", "mais", "tres", "bien", "comme", "plus", "tout", "sur",
            ),
            charNgrams = setOf(
                "me", "mer", "erc", "bon", "jou", "our", "pour", "avec", "que", "ent", "tion",
                "ais", "eux", "ille", "des", "les", "vous", "nous", "tre",
            ),
            prefixes = setOf("mer", "bon", "pour", "avec", "que", "vou", "nou", "comm"),
        ),
        "de" to LanguageProfile(
            commonWords = setOf(
                "und", "ich", "nicht", "der", "die", "das", "ist", "ein", "eine", "mit",
                "auf", "danke", "bitte", "wie", "was", "warum", "heute", "guten", "morgen",
            ),
            charNgrams = setOf(
                "sch", "ch", "ich", "ei", "ein", "ung", "der", "die", "das", "cht", "ge",
                "und", "mit", "auf", "dank", "bitt", "gute", "morgen",
            ),
            prefixes = setOf("ich", "sch", "dank", "bitt", "gute", "nich", "war"),
        ),
        "it" to LanguageProfile(
            commonWords = setOf(
                "che", "non", "per", "una", "uno", "con", "grazie", "ciao", "sono", "gli",
                "del", "della", "come", "bene", "molto", "oggi", "questo", "quella",
            ),
            charNgrams = setOf(
                "che", "gli", "zio", "azi", "zie", "per", "non", "del", "ell", "lla", "ment",
                "cia", "iao", "son", "mol", "ogg", "que",
            ),
            prefixes = setOf("che", "gra", "graz", "cia", "per", "non", "son", "que"),
        ),
        "pt" to LanguageProfile(
            commonWords = setOf(
                "que", "nao", "para", "com", "uma", "obrigado", "obrigada", "voce", "por",
                "muito", "estou", "bom", "boa", "hoje", "como", "tambem", "quando",
            ),
            charNgrams = setOf(
                "que", "nao", "cao", "oes", "nha", "com", "para", "ado", "ada", "men",
                "obr", "brig", "voce", "mui", "ito", "tam", "bem", "qua",
            ),
            prefixes = setOf("que", "na", "nao", "obr", "obri", "par", "vo", "voc", "mui"),
        ),
    )
}

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

package dev.patrickgold.florisboard.ime.nlp.latin

internal data class ColdStartNextWordPrior(
    val word: String,
    val confidence: Double,
)

/**
 * Tiny local phrase prior used before a user has enough personal n-gram history.
 *
 * SwiftKey feels strong on a fresh install because its language model already knows
 * sentence starts and common short continuations. This keeps that behavior local and
 * transparent while the personal bigram/trigram stores warm up.
 */
internal object ColdStartNextWordPriors {
    fun suggest(
        textBeforeCursor: String,
        languageCode: String,
        maxCandidateCount: Int,
    ): List<ColdStartNextWordPrior> {
        if (maxCandidateCount <= 0) {
            return emptyList()
        }
        val priorSet = PriorSets[LatinDictionaryStore.normalizeLanguageCode(languageCode)]
            ?: return emptyList()
        val words = when {
            isSentenceStart(textBeforeCursor) -> priorSet.sentenceStart
            else -> {
                val previousWords = previousWordsOf(textBeforeCursor, maxDepth = 3)
                if (previousWords.isEmpty()) return emptyList()
                priorSet.phraseContinuations[previousWords.takeLast(3).joinToString(" ")]
                    ?: priorSet.phraseContinuations[previousWords.takeLast(2).joinToString(" ")]
                    ?: priorSet.continuations[previousWords.last()].orEmpty()
            }
        }
        return words
            .take(maxCandidateCount)
            .mapIndexed { index, word ->
                ColdStartNextWordPrior(
                    word = word,
                    confidence = (0.44 - 0.025 * index).coerceAtLeast(0.24),
                )
            }
    }

    fun score(
        textBeforeCursor: String,
        languageCode: String,
        candidateWord: String,
        maxCandidateCount: Int = 16,
    ): Double {
        val normalizedCandidate = candidateWord
            .trim()
            .lowercase()
            .replace('\u2019', '\'')
        if (normalizedCandidate.isBlank()) return 0.0
        return suggest(
            textBeforeCursor = textBeforeCursor,
            languageCode = languageCode,
            maxCandidateCount = maxCandidateCount,
        ).firstOrNull { prior ->
            prior.word.equals(normalizedCandidate, ignoreCase = true)
        }?.confidence ?: 0.0
    }

    private fun isSentenceStart(textBeforeCursor: String): Boolean {
        val trimmed = textBeforeCursor.trimEnd()
        if (trimmed.isEmpty()) return true
        return trimmed.last() in SentenceTerminators
    }

    private fun previousWordsOf(textBeforeCursor: String, maxDepth: Int): List<String> {
        if (maxDepth <= 0) return emptyList()
        val words = mutableListOf<String>()
        val trimmed = textBeforeCursor.trimEnd()
        if (trimmed.isEmpty()) return emptyList()
        var end = trimmed.length
        while (end > 0 && words.size < maxDepth) {
            while (end > 0 && !trimmed[end - 1].isPriorWordChar()) {
                end--
            }
            if (end == 0) break
            var start = end
            while (start > 0 && trimmed[start - 1].isPriorWordChar()) {
                start--
            }
            val word = trimmed.substring(start, end)
                .lowercase()
                .replace('\u2019', '\'')
            if (word.isNotBlank()) {
                words.add(0, word)
            }
            end = start
        }
        return words
    }

    private fun Char.isPriorWordChar(): Boolean {
        return isLetter() || this == '\'' || this == '\u2019' || this == '-'
    }

    private data class PriorSet(
        val sentenceStart: List<String>,
        val continuations: Map<String, List<String>>,
        val phraseContinuations: Map<String, List<String>>,
    )

    private val SentenceTerminators = setOf('.', '!', '?', '\n')

    private val SentenceStart = listOf(
        "i",
        "the",
        "this",
        "what",
        "how",
        "you",
        "it",
        "we",
    )

    private val Continuations = mapOf(
        "i" to listOf("am", "have", "will", "think", "can", "don't"),
        "i'm" to listOf("going", "not", "so", "sorry", "sure"),
        "i'll" to listOf("be", "send", "check", "try"),
        "i've" to listOf("been", "got", "seen", "had"),
        "i'd" to listOf("like", "rather", "love", "say", "be"),
        "the" to listOf("same", "best", "first", "last", "next", "only"),
        "this" to listOf("is", "was", "will", "one", "looks"),
        "that" to listOf("is", "was", "would", "sounds", "makes"),
        "it" to listOf("is", "was", "will", "looks", "sounds"),
        "you" to listOf("can", "are", "have", "will", "should"),
        "we" to listOf("can", "are", "have", "will", "need"),
        "we're" to listOf("going", "not", "still", "ready"),
        "we'll" to listOf("be", "send", "check", "try"),
        "you're" to listOf("going", "not", "right", "welcome"),
        "they" to listOf("are", "were", "will", "have"),
        "he" to listOf("is", "was", "will", "has"),
        "she" to listOf("is", "was", "will", "has"),
        "thank" to listOf("you"),
        "thanks" to listOf("for", "again"),
        "how" to listOf("are", "is", "do", "can", "was"),
        "what" to listOf("are", "is", "do", "was", "would"),
        "where" to listOf("is", "are", "did", "do"),
        "when" to listOf("is", "are", "do", "can"),
        "why" to listOf("is", "are", "did", "would"),
        "let" to listOf("me", "us"),
        "good" to listOf("morning", "night", "luck", "idea"),
        "see" to listOf("you", "the", "if"),
        "talk" to listOf("to", "soon"),
        "on" to listOf("my", "the", "your"),
        "in" to listOf("the", "a", "my"),
        "for" to listOf("the", "you", "me", "a"),
        "of" to listOf("the", "course"),
        "to" to listOf("the", "be", "get", "make"),
        "be" to listOf("able", "there", "ready"),
    )

    private val PhraseContinuations = mapOf(
        "a lot" to listOf("of", "more"),
        "are you" to listOf("going", "available", "sure", "there"),
        "are you going" to listOf("to", "there"),
        "as soon" to listOf("as"),
        "as soon as" to listOf("possible", "i", "we"),
        "at the" to listOf("same", "end", "office"),
        "can you" to listOf("please", "send", "check", "help"),
        "do you" to listOf("want", "have", "think", "know"),
        "do you want" to listOf("to", "me", "a"),
        "for the" to listOf("first", "same", "most"),
        "going to" to listOf("be", "the", "get", "make"),
        "have a" to listOf("great", "good", "nice"),
        "how are" to listOf("you", "things", "we"),
        "i am" to listOf("going", "not", "sure", "sorry"),
        "i don't" to listOf("think", "know", "want"),
        "i have" to listOf("been", "a", "to"),
        "i'd like" to listOf("to", "a", "the"),
        "i'd rather" to listOf("not", "be", "go"),
        "i'd love" to listOf("to", "a"),
        "i need" to listOf("to", "a", "the"),
        "i will" to listOf("be", "send", "check", "try"),
        "i'll be" to listOf("there", "back", "home"),
        "i'm going" to listOf("to", "home", "there"),
        "i'm not" to listOf("sure", "going", "able"),
        "if you" to listOf("can", "want", "need"),
        "in the" to listOf("morning", "same", "first"),
        "let me" to listOf("know", "see", "check", "try"),
        "let me know" to listOf("if", "when", "what"),
        "on my" to listOf("way", "phone", "end"),
        "one of" to listOf("the", "my"),
        "see you" to listOf("soon", "tomorrow", "then"),
        "talk to" to listOf("you", "me", "them"),
        "thank you" to listOf("for", "so", "again"),
        "thank you for" to listOf("the", "your", "everything"),
        "thanks for" to listOf("the", "your", "help"),
        "to be" to listOf("able", "honest", "sure"),
        "we need" to listOf("to", "a", "the"),
        "what are" to listOf("you", "the", "we"),
        "when are" to listOf("you", "we", "they"),
        "where are" to listOf("you", "the", "we"),
        "would you" to listOf("like", "be", "mind"),
        "would you like" to listOf("to", "me", "a"),
        "you should" to listOf("be", "try", "check"),
    )

    private val GermanSentenceStart = listOf(
        "ich",
        "das",
        "die",
        "der",
        "wir",
        "du",
        "es",
        "wie",
    )

    private val GermanContinuations = mapOf(
        "ich" to listOf("bin", "habe", "werde", "kann", "denke"),
        "das" to listOf("ist", "war", "wird", "sieht"),
        "die" to listOf("ist", "sind", "haben", "werden"),
        "der" to listOf("ist", "war", "wird", "hat"),
        "wir" to listOf("sind", "haben", "werden", "können"),
        "du" to listOf("bist", "hast", "kannst", "solltest"),
        "es" to listOf("ist", "war", "wird", "gibt"),
        "wie" to listOf("geht", "ist", "war", "kann"),
        "vielen" to listOf("dank"),
        "gute" to listOf("nacht", "idee", "reise"),
    )

    private val GermanPhraseContinuations = mapOf(
        "ich bin" to listOf("nicht", "gleich", "da", "bereit"),
        "ich habe" to listOf("eine", "den", "das"),
        "ich werde" to listOf("das", "es", "morgen"),
        "vielen dank" to listOf("für", "nochmal"),
        "wie geht" to listOf("es", "das"),
    )

    private val SpanishSentenceStart = listOf(
        "yo",
        "el",
        "la",
        "qué",
        "cómo",
        "me",
        "te",
        "eso",
    )

    private val SpanishContinuations = mapOf(
        "yo" to listOf("tengo", "voy", "puedo", "creo"),
        "el" to listOf("día", "tiempo", "problema"),
        "la" to listOf("verdad", "semana", "cosa"),
        "qué" to listOf("tal", "pasa", "quieres"),
        "cómo" to listOf("estás", "va", "fue"),
        "me" to listOf("parece", "gusta", "puedes"),
        "te" to listOf("puedo", "mando", "veo"),
        "muchas" to listOf("gracias"),
        "buenos" to listOf("días"),
    )

    private val SpanishPhraseContinuations = mapOf(
        "muchas gracias" to listOf("por", "de", "otra"),
        "buenos días" to listOf("a", "cómo"),
        "yo voy" to listOf("a", "para"),
        "yo tengo" to listOf("que", "una", "un"),
        "cómo estás" to listOf("hoy", "tú"),
    )

    private val FrenchSentenceStart = listOf(
        "je",
        "le",
        "la",
        "ça",
        "tu",
        "nous",
        "vous",
        "comment",
    )

    private val FrenchContinuations = mapOf(
        "je" to listOf("suis", "vais", "peux", "pense"),
        "le" to listOf("temps", "jour", "problème"),
        "la" to listOf("semaine", "suite", "vérité"),
        "ça" to listOf("va", "marche", "serait"),
        "tu" to listOf("peux", "as", "vas"),
        "nous" to listOf("sommes", "avons", "allons"),
        "vous" to listOf("pouvez", "avez", "êtes"),
        "merci" to listOf("beaucoup", "pour"),
        "s'il" to listOf("vous", "te"),
    )

    private val FrenchPhraseContinuations = mapOf(
        "je suis" to listOf("désolé", "disponible", "en"),
        "je vais" to listOf("le", "te", "faire"),
        "merci beaucoup" to listOf("pour", "encore"),
        "s'il vous" to listOf("plaît"),
        "ça va" to listOf("être", "bien"),
    )

    private val ItalianSentenceStart = listOf(
        "io",
        "il",
        "la",
        "che",
        "come",
        "mi",
        "ti",
        "noi",
    )

    private val ItalianContinuations = mapOf(
        "io" to listOf("sono", "ho", "posso", "penso"),
        "il" to listOf("giorno", "tempo", "problema"),
        "la" to listOf("settimana", "cosa", "verità"),
        "che" to listOf("cosa", "ne", "succede"),
        "come" to listOf("stai", "va", "posso"),
        "mi" to listOf("sembra", "piace", "puoi"),
        "ti" to listOf("posso", "mando", "vedo"),
        "grazie" to listOf("per", "ancora"),
        "buon" to listOf("giorno", "lavoro", "viaggio"),
    )

    private val ItalianPhraseContinuations = mapOf(
        "io sono" to listOf("qui", "pronto", "sicuro"),
        "io ho" to listOf("un", "una", "bisogno"),
        "come stai" to listOf("oggi"),
        "grazie per" to listOf("il", "la", "tutto"),
        "buon giorno" to listOf("a", "come"),
    )

    private val PortugueseSentenceStart = listOf(
        "eu",
        "o",
        "a",
        "que",
        "como",
        "você",
        "nós",
        "isso",
    )

    private val PortugueseContinuations = mapOf(
        "eu" to listOf("tenho", "vou", "posso", "acho"),
        "o" to listOf("dia", "tempo", "problema"),
        "a" to listOf("semana", "coisa", "verdade"),
        "que" to listOf("bom", "tal", "você"),
        "como" to listOf("você", "vai", "foi"),
        "você" to listOf("pode", "tem", "vai"),
        "nós" to listOf("temos", "vamos", "podemos"),
        "muito" to listOf("obrigado", "bem"),
        "bom" to listOf("dia", "trabalho"),
    )

    private val PortuguesePhraseContinuations = mapOf(
        "eu tenho" to listOf("que", "uma", "um"),
        "eu vou" to listOf("para", "fazer", "ver"),
        "muito obrigado" to listOf("por", "mesmo"),
        "bom dia" to listOf("como", "a"),
        "como você" to listOf("está", "vai"),
    )

    private val CzechSentenceStart = listOf(
        "já",
        "to",
        "co",
        "jak",
        "můžu",
        "díky",
        "my",
        "ty",
    )

    private val CzechContinuations = mapOf(
        "já" to listOf("jsem", "mám", "budu", "můžu"),
        "to" to listOf("je", "bylo", "bude"),
        "co" to listOf("je", "máme", "bude"),
        "jak" to listOf("se", "to", "můžu"),
        "můžu" to listOf("to", "se", "vám"),
        "díky" to listOf("za", "moc"),
        "my" to listOf("jsme", "máme", "budeme"),
        "ty" to listOf("jsi", "máš", "budeš"),
    )

    private val CzechPhraseContinuations = mapOf(
        "já jsem" to listOf("to", "tam", "rád"),
        "já mám" to listOf("čas", "to", "jednu"),
        "jak se" to listOf("máš", "daří"),
        "díky za" to listOf("pomoc", "info", "všechno"),
        "to je" to listOf("dobře", "super", "pravda"),
    )

    private val PriorSets = mapOf(
        "en" to PriorSet(SentenceStart, Continuations, PhraseContinuations),
        "cs" to PriorSet(CzechSentenceStart, CzechContinuations, CzechPhraseContinuations),
        "de" to PriorSet(GermanSentenceStart, GermanContinuations, GermanPhraseContinuations),
        "es" to PriorSet(SpanishSentenceStart, SpanishContinuations, SpanishPhraseContinuations),
        "fr" to PriorSet(FrenchSentenceStart, FrenchContinuations, FrenchPhraseContinuations),
        "it" to PriorSet(ItalianSentenceStart, ItalianContinuations, ItalianPhraseContinuations),
        "pt" to PriorSet(PortugueseSentenceStart, PortugueseContinuations, PortuguesePhraseContinuations),
    )
}

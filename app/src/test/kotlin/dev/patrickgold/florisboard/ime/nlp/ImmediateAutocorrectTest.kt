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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ImmediateAutocorrectTest : FunSpec({

    // ----------------------------------------------------------------------------------
    // SAFE-tier contractions: substitute immediately (no real-word collision).
    // ----------------------------------------------------------------------------------

    test("standalone i auto-commits to I") {
        val candidate = ImmediateAutocorrect.englishContractionCandidate("i", "en-US")
        candidate?.text shouldBe "I"
        candidate?.isEligibleForAutoCommit shouldBe true
    }

    test("safe-tier negative -nt contractions auto-commit immediately") {
        mapOf(
            "dont" to "don't",
            "isnt" to "isn't",
            "wasnt" to "wasn't",
            "werent" to "weren't",
            "arent" to "aren't",
            "didnt" to "didn't",
            "doesnt" to "doesn't",
            "havent" to "haven't",
            "hasnt" to "hasn't",
            "hadnt" to "hadn't",
            "wouldnt" to "wouldn't",
            "shouldnt" to "shouldn't",
            "couldnt" to "couldn't",
            "mustnt" to "mustn't",
            "neednt" to "needn't",
            "mightnt" to "mightn't",
            "oughtnt" to "oughtn't",
            "shant" to "shan't",
            "aint" to "ain't",
        ).forEach { (typed, expected) ->
            val candidate = ImmediateAutocorrect.englishContractionCandidate(typed, "en-US")
            candidate?.text shouldBe expected
            candidate?.isEligibleForAutoCommit shouldBe true
        }
    }

    test("safe-tier modal -ve contractions auto-commit immediately") {
        mapOf(
            "wouldve" to "would've",
            "shouldve" to "should've",
            "couldve" to "could've",
            "mightve" to "might've",
            "mustve" to "must've",
        ).forEach { (typed, expected) ->
            ImmediateAutocorrect.englishContractionCandidate(typed, "en-US")?.text shouldBe expected
        }
    }

    test("safe-tier pronoun + auxiliary contractions auto-commit immediately") {
        mapOf(
            "youre" to "you're",
            "youve" to "you've",
            "youll" to "you'll",
            "youd" to "you'd",
            "theyre" to "they're",
            "theyve" to "they've",
            "theyll" to "they'll",
            "theyd" to "they'd",
            "weve" to "we've",
            "itll" to "it'll",
            "itd" to "it'd",
        ).forEach { (typed, expected) ->
            ImmediateAutocorrect.englishContractionCandidate(typed, "en-US")?.text shouldBe expected
        }
    }

    test("safe-tier wh- and demonstrative contractions auto-commit immediately") {
        mapOf(
            "whats" to "what's",
            "whatre" to "what're",
            "whatll" to "what'll",
            "whatd" to "what'd",
            "whatve" to "what've",
            "whos" to "who's",
            "whod" to "who'd",
            "wholl" to "who'll",
            "whove" to "who've",
            "wheres" to "where's",
            "whered" to "where'd",
            "wherell" to "where'll",
            "whens" to "when's",
            "whyd" to "why'd",
            "whys" to "why's",
            "hows" to "how's",
            "howd" to "how'd",
            "howll" to "how'll",
            "theres" to "there's",
            "thered" to "there'd",
            "therell" to "there'll",
            "thereve" to "there've",
            "thats" to "that's",
            "thatll" to "that'll",
            "thatd" to "that'd",
            "thatre" to "that're",
            "heres" to "here's",
        ).forEach { (typed, expected) ->
            ImmediateAutocorrect.englishContractionCandidate(typed, "en-US")?.text shouldBe expected
        }
    }

    test("safe-tier indefinite-pronoun contractions auto-commit immediately") {
        mapOf(
            "someones" to "someone's",
            "everyones" to "everyone's",
            "anyones" to "anyone's",
            "nobodys" to "nobody's",
            "everybodys" to "everybody's",
            "anybodys" to "anybody's",
            "somebodys" to "somebody's",
            "somethings" to "something's",
            "nothings" to "nothing's",
        ).forEach { (typed, expected) ->
            ImmediateAutocorrect.englishContractionCandidate(typed, "en-US")?.text shouldBe expected
        }
    }

    test("safe-tier misc fixed-form contractions auto-commit immediately") {
        mapOf(
            "oclock" to "o'clock",
            "yall" to "y'all",
            "maam" to "ma'am",
            "ima" to "I'ma",
        ).forEach { (typed, expected) ->
            ImmediateAutocorrect.englishContractionCandidate(typed, "en-US")?.text shouldBe expected
        }
    }

    test("safe-tier run-together phrase repairs auto-commit immediately") {
        mapOf(
            "alot" to "a lot",
            "alotof" to "a lot of",
            "alittle" to "a little",
            "aswell" to "as well",
            "aswellas" to "as well as",
            "atleast" to "at least",
            "atall" to "at all",
            "atthe" to "at the",
            "attheend" to "at the end",
            "bytheend" to "by the end",
            "bytheway" to "by the way",
            "eachother" to "each other",
            "everytime" to "every time",
            "forthefirst" to "for the first",
            "fornow" to "for now",
            "forthe" to "for the",
            "fromnowon" to "from now on",
            "fromnow" to "from now",
            "goodmorning" to "good morning",
            "infact" to "in fact",
            "infront" to "in front",
            "infrontof" to "in front of",
            "intheend" to "in the end",
            "inthemorning" to "in the morning",
            "inthe" to "in the",
            "kindof" to "kind of",
            "letmeknow" to "let me know",
            "letme" to "let me",
            "notatall" to "not at all",
            "notreally" to "not really",
            "notsure" to "not sure",
            "notyet" to "not yet",
            "noone" to "no one",
            "ofcourse" to "of course",
            "ofthe" to "of the",
            "onthe" to "on the",
            "ontheway" to "on the way",
            "rightaway" to "right away",
            "rightnow" to "right now",
            "seeyou" to "see you",
            "seeyousoon" to "see you soon",
            "sortof" to "sort of",
            "talkto" to "talk to",
            "talktoyou" to "talk to you",
            "thankyou" to "thank you",
            "thankyoufor" to "thank you for",
            "tothe" to "to the",
        ).forEach { (typed, expected) ->
            val candidate = ImmediateAutocorrect.englishPhraseRepairCandidate(typed, "en-US")
            candidate?.text shouldBe expected
            candidate?.isEligibleForAutoCommit shouldBe true
        }
    }

    // ----------------------------------------------------------------------------------
    // Case preservation: typed-case maps to the contraction.
    // ----------------------------------------------------------------------------------

    test("sentence-start capitalization is preserved on safe contractions") {
        ImmediateAutocorrect.englishContractionCandidate("Dont", "en-US")?.text shouldBe "Don't"
        ImmediateAutocorrect.englishContractionCandidate("Youre", "en-US")?.text shouldBe "You're"
        ImmediateAutocorrect.englishContractionCandidate("Theyll", "en-US")?.text shouldBe "They'll"
        ImmediateAutocorrect.englishContractionCandidate("Whats", "en-US")?.text shouldBe "What's"
    }

    test("first-person I forms always retain the capital I") {
        // Lowercase "im" still produces "I'm" because the canonical text carries the
        // capital I — but "im" is DICTIONARY_GATED, so the immediate path returns null.
        // The internal englishContraction function (dict-aware path) confirms the mapping.
        ImmediateAutocorrect.englishContraction("im", "en-US")?.text shouldBe "I'm"
        ImmediateAutocorrect.englishContraction("Im", "en-US")?.text shouldBe "I'm"
    }

    // ----------------------------------------------------------------------------------
    // ALL-CAPS suppression: matches SwiftKey — leave acronyms / shouting alone.
    // ----------------------------------------------------------------------------------

    test("all-caps tokens are never auto-corrected") {
        listOf("DONT", "YOURE", "WONT", "ID", "ILL", "IM", "IVE",
               "WELL", "HELL", "SHELL", "WED", "SHED", "LETS", "ITS",
               "WHATS", "WHOS", "HOWS").forEach { typed ->
            ImmediateAutocorrect.englishContractionCandidate(typed, "en-US") shouldBe null
            ImmediateAutocorrect.englishContraction(typed, "en-US") shouldBe null
        }
        listOf("THANKYOU", "ALOT", "ATLEAST").forEach { typed ->
            ImmediateAutocorrect.englishPhraseRepairCandidate(typed, "en-US") shouldBe null
            ImmediateAutocorrect.englishPhraseRepair(typed, "en-US") shouldBe null
        }
    }

    // ----------------------------------------------------------------------------------
    // DICTIONARY_GATED contractions: NEVER immediate-auto-commit.
    // The dict-aware LatinLanguageProvider path is the only thing that may promote them,
    // and only when the dictionary confirms the typed word is not itself a real word.
    // ----------------------------------------------------------------------------------

    test("dictionary-gated contractions do NOT immediately auto-commit") {
        listOf("im", "id", "ill", "ive", "well", "hell", "shell",
               "hes", "shes", "hed", "shed", "wed", "lets",
               "wont", "cant", "its",
               "Im", "Id", "Ill", "Ive", "Well", "Hell").forEach { typed ->
            ImmediateAutocorrect.englishContractionCandidate(typed, "en-US") shouldBe null
        }
    }

    test("dictionary-gated contractions are still discoverable via englishContraction") {
        // The dict-aware path consumes englishContraction() and applies its own
        // dictionary check before promoting to auto-commit.
        ImmediateAutocorrect.englishContraction("im", "en-US")?.text shouldBe "I'm"
        ImmediateAutocorrect.englishContraction("ill", "en-US")?.text shouldBe "I'll"
        ImmediateAutocorrect.englishContraction("id", "en-US")?.text shouldBe "I'd"
        ImmediateAutocorrect.englishContraction("ive", "en-US")?.text shouldBe "I've"
        ImmediateAutocorrect.englishContraction("well", "en-US")?.text shouldBe "we'll"
        ImmediateAutocorrect.englishContraction("hell", "en-US")?.text shouldBe "he'll"
        ImmediateAutocorrect.englishContraction("shell", "en-US")?.text shouldBe "she'll"
        ImmediateAutocorrect.englishContraction("wed", "en-US")?.text shouldBe "we'd"
        ImmediateAutocorrect.englishContraction("lets", "en-US")?.text shouldBe "let's"
        ImmediateAutocorrect.englishContraction("wont", "en-US")?.text shouldBe "won't"
        ImmediateAutocorrect.englishContraction("its", "en-US")?.text shouldBe "it's"
    }

    test("'were' is intentionally never auto-corrected") {
        ImmediateAutocorrect.englishContraction("were", "en-US") shouldBe null
        ImmediateAutocorrect.englishContractionCandidate("were", "en-US") shouldBe null
    }

    // ----------------------------------------------------------------------------------
    // No-op safety net.
    // ----------------------------------------------------------------------------------

    test("already-correct contractions are not re-substituted") {
        ImmediateAutocorrect.englishContractionCandidate("don't", "en-US") shouldBe null
        ImmediateAutocorrect.englishContractionCandidate("you're", "en-US") shouldBe null
        ImmediateAutocorrect.englishContractionCandidate("I'm", "en") shouldBe null
        ImmediateAutocorrect.englishContractionCandidate("I'll", "en") shouldBe null
        ImmediateAutocorrect.englishContractionCandidate("I", "en") shouldBe null
    }

    test("contractions do not apply to non-English locales") {
        ImmediateAutocorrect.englishContractionCandidate("dont", "fr") shouldBe null
        ImmediateAutocorrect.englishContractionCandidate("youre", "de") shouldBe null
        ImmediateAutocorrect.englishContractionCandidate("i", "it") shouldBe null
        ImmediateAutocorrect.englishContractionCandidate("im", "it") shouldBe null
    }

    test("phrase repairs do not apply to non-English locales") {
        ImmediateAutocorrect.englishPhraseRepairCandidate("thankyou", "fr") shouldBe null
        ImmediateAutocorrect.englishPhraseRepairCandidate("alot", "de") shouldBe null
    }
})

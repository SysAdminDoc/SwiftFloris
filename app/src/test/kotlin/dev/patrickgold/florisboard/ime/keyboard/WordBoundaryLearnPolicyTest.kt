/*
 * Copyright (C) 2026 SwiftFloris Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.ime.keyboard

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class WordBoundaryLearnPolicyTest : FunSpec({
    test("sentence and clause punctuation ends the word before it") {
        listOf(".", ",", "!", "?", ";", ":", ")", "]", "}", "\"", "”", "»", "…").forEach { text ->
            WordBoundaryLearnPolicy.endsWord(text) shouldBe true
        }
    }

    test("characters that join or sit inside words don't end them") {
        listOf("'", "-", "@", "/", "#", "_", "&", "1", "a", "", "..", ".,").forEach { text ->
            WordBoundaryLearnPolicy.endsWord(text) shouldBe false
        }
    }

    test("only sentence-ending punctuation breaks the next-word chain") {
        listOf(".", "!", "?", "…").forEach { text ->
            WordBoundaryLearnPolicy.endsSentence(text) shouldBe true
        }
        listOf(",", ";", ":", ")", "\"", "'", "-", "a").forEach { text ->
            WordBoundaryLearnPolicy.endsSentence(text) shouldBe false
        }
    }
})

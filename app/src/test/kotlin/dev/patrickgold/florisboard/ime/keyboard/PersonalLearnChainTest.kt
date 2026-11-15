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

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.Test

class PersonalLearnChainTest {
    private fun PersonalLearnChain.learn(word: String, localeTag: String = "en-US") =
        advance(snapshot(), word, localeTag)

    @Test
    fun wordsLearnedInARowFormTheChain() {
        val chain = PersonalLearnChain()
        chain.learn("see") shouldBe true
        chain.learn("you") shouldBe true
        chain.learn("soon") shouldBe true

        chain.snapshot().link shouldBe PersonalLearnChain.Link(last = "soon", prev = "you", localeTag = "en-US")
    }

    @Test
    fun anotherLanguageStartsTheChainOver() {
        val chain = PersonalLearnChain()
        chain.learn("hola", "es-ES")
        chain.learn("there", "en-US")

        chain.snapshot().link shouldBe PersonalLearnChain.Link(last = "there", prev = null, localeTag = "en-US")
    }

    @Test
    fun resetBreaksTheChain() {
        val chain = PersonalLearnChain()
        chain.learn("meeting")
        chain.reset()

        chain.snapshot().link.shouldBeNull()
        chain.learn("tomorrow") shouldBe true
        chain.snapshot().link shouldBe PersonalLearnChain.Link(last = "tomorrow", prev = null, localeTag = "en-US")
    }

    @Test
    fun aResetWhileAWordIsLearnedWins() {
        val chain = PersonalLearnChain()
        chain.learn("the")
        val learningAgainst = chain.snapshot()
        // The editor changes on the background collector between the read and the update.
        chain.reset()

        chain.advance(learningAgainst, "meeting", "en-US") shouldBe false
        chain.snapshot().link.shouldBeNull()
    }

    @Test
    fun aResetWinsEvenWhenTheChainWasAlreadyEmpty() {
        val chain = PersonalLearnChain()
        val learningAgainst = chain.snapshot()
        chain.reset()

        chain.advance(learningAgainst, "meeting", "en-US") shouldBe false
        chain.snapshot().link.shouldBeNull()
    }
}

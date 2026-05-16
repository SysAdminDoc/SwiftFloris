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

package dev.patrickgold.florisboard.ime.dictionary

import dev.patrickgold.florisboard.lib.FlorisLocale
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class UserDictionaryOverlayTest : FunSpec({

    afterEach {
        UserDictionaryOverlay.resetForTest()
    }

    val en = FlorisLocale.fromTag("en")
    val es = FlorisLocale.fromTag("es")

    test("learn() inserts a new word at INITIAL_FREQUENCY") {
        val overlay = UserDictionaryOverlay.get()
        overlay.learn("hello", en)
        overlay.frequencyFor("hello", en) shouldBe UserDictionaryOverlay.INITIAL_FREQUENCY
    }

    test("learn() called repeatedly bumps frequency by INCREMENT each time (capped at MAX)") {
        val overlay = UserDictionaryOverlay.get()
        overlay.learn("foo", en)
        overlay.learn("foo", en)
        overlay.learn("foo", en)
        val expected = (UserDictionaryOverlay.INITIAL_FREQUENCY +
            UserDictionaryOverlay.INCREMENT * 2)
            .coerceAtMost(UserDictionaryOverlay.MAX_FREQUENCY)
        overlay.frequencyFor("foo", en) shouldBe expected
    }

    test("learn() caps frequency at MAX_FREQUENCY") {
        val overlay = UserDictionaryOverlay.get()
        repeat(100) { overlay.learn("foo", en) }
        overlay.frequencyFor("foo", en) shouldBe UserDictionaryOverlay.MAX_FREQUENCY
    }

    test("learn() is case-insensitive — uppercase typed word stored as lowercase") {
        val overlay = UserDictionaryOverlay.get()
        overlay.learn("Foo", en)
        overlay.frequencyFor("foo", en) shouldBe UserDictionaryOverlay.INITIAL_FREQUENCY
        overlay.frequencyFor("FOO", en) shouldBe UserDictionaryOverlay.INITIAL_FREQUENCY
        overlay.contains("foo", en) shouldBe true
    }

    test("learn() rejects too-short or too-long words") {
        val overlay = UserDictionaryOverlay.get()
        overlay.learn("ab", en)  // length 2 < MIN_LENGTH 3
        overlay.frequencyFor("ab", en) shouldBe 0
        overlay.learn("a".repeat(40), en)  // length > MAX_LENGTH
        overlay.frequencyFor("a".repeat(40), en) shouldBe 0
    }

    test("learn() rejects words with internal non-word punctuation") {
        val overlay = UserDictionaryOverlay.get()
        // 'foo_bar' has internal '_' — rejected outright.
        overlay.learn("foo_bar", en)
        overlay.contains("foo_bar", en) shouldBe false
        // 'foo123' and 'foo!' have trailing junk that gets trimmed off, then
        // the cleaned "foo" passes — matching DictionaryManager.learnWord
        // semantics. The test asserts those are stored as the trimmed form.
        overlay.learn("foo123", en)
        overlay.contains("foo", en) shouldBe true
    }

    test("learn() accepts internal apostrophe and hyphen (real-word punctuation)") {
        val overlay = UserDictionaryOverlay.get()
        overlay.learn("don't", en)
        overlay.learn("re-roll", en)
        overlay.contains("don't", en) shouldBe true
        overlay.contains("re-roll", en) shouldBe true
    }

    test("forget() drops the entry") {
        val overlay = UserDictionaryOverlay.get()
        overlay.learn("foo", en)
        overlay.forget("foo", en)
        overlay.contains("foo", en) shouldBe false
        overlay.frequencyFor("foo", en) shouldBe 0
    }

    test("per-locale isolation — same word in different locales tracked separately") {
        val overlay = UserDictionaryOverlay.get()
        overlay.learn("kabob", en)
        overlay.learn("kabob", en)  // freq 86
        overlay.learn("kabob", es)  // freq 80
        overlay.frequencyFor("kabob", en) shouldBe
            UserDictionaryOverlay.INITIAL_FREQUENCY + UserDictionaryOverlay.INCREMENT
        overlay.frequencyFor("kabob", es) shouldBe UserDictionaryOverlay.INITIAL_FREQUENCY
    }

    test("wordsWithPrefix returns matching overlay words for the locale") {
        val overlay = UserDictionaryOverlay.get()
        overlay.learn("food", en)
        overlay.learn("football", en)
        overlay.learn("foo", en)
        overlay.learn("bar", en)
        val foos = overlay.wordsWithPrefix("foo", en).toSet()
        foos shouldBe setOf("food", "football", "foo")
        overlay.wordsWithPrefix("bar", en) shouldBe listOf("bar")
        overlay.wordsWithPrefix("baz", en) shouldBe emptyList()
    }

    test("hydrateLocale is idempotent and respects in-memory entries") {
        val overlay = UserDictionaryOverlay.get()
        overlay.learn("manual", en)  // user typed this — freq 80
        overlay.isHydrated(en) shouldBe false
        overlay.hydrateLocale(en, listOf("daoword" to 100, "manual" to 200))
        overlay.isHydrated(en) shouldBe true
        // In-memory "manual" wins over the DAO snapshot.
        overlay.frequencyFor("manual", en) shouldBe UserDictionaryOverlay.INITIAL_FREQUENCY
        overlay.frequencyFor("daoword", en) shouldBe 100
        // Second hydrate is a no-op.
        overlay.hydrateLocale(en, listOf("another" to 100))
        overlay.contains("another", en) shouldBe false
    }

    test("clearLocale wipes one locale and leaves others untouched") {
        val overlay = UserDictionaryOverlay.get()
        overlay.learn("foo", en)
        overlay.learn("bar", es)
        overlay.clearLocale(en)
        overlay.contains("foo", en) shouldBe false
        overlay.contains("bar", es) shouldBe true
    }

    test("clearAll wipes everything across every locale") {
        val overlay = UserDictionaryOverlay.get()
        overlay.learn("foo", en)
        overlay.learn("bar", es)
        overlay.clearAll()
        overlay.contains("foo", en) shouldBe false
        overlay.contains("bar", es) shouldBe false
        overlay.isHydrated(en) shouldBe false
    }

    test("clearLocale resets hydrated flag so the next hydrate fires") {
        val overlay = UserDictionaryOverlay.get()
        overlay.hydrateLocale(en, listOf("alpha" to 100))
        overlay.isHydrated(en) shouldBe true
        // First hydrate is locked in; a second hydrate call would no-op.
        overlay.hydrateLocale(en, listOf("beta" to 200))
        overlay.contains("beta", en) shouldBe false
        // After clearLocale, the next hydrateLocale runs again.
        overlay.clearLocale(en)
        overlay.isHydrated(en) shouldBe false
        overlay.hydrateLocale(en, listOf("beta" to 200))
        overlay.contains("beta", en) shouldBe true
        overlay.contains("alpha", en) shouldBe false  // old data dropped
    }
})

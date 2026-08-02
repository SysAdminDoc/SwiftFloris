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

package dev.patrickgold.florisboard.app.settings.dictionary

import dev.patrickgold.florisboard.ime.dictionary.UserDictionaryEntry
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

class LearnedEntriesPolicyTest : FunSpec({
    fun entry(word: String, locale: String?) = UserDictionaryEntry(
        id = 0,
        word = word,
        freq = 1,
        locale = locale,
        shortcut = null,
    )

    test("load returns sorted words when every store reads successfully") {
        val state = LearnedEntriesPolicy.load(
            readWords = {
                listOf(
                    entry("zebra", "en"),
                    entry("apple", "en"),
                    entry("beta", null),
                )
            },
            readBigrams = { emptyList() },
            readTrigrams = { emptyList() },
        )

        val ready = state.shouldBeInstanceOf<LearnedEntriesState.Ready>()
        ready.words.map { it.word } shouldContainExactly listOf("beta", "apple", "zebra")
    }

    test("a failing word read surfaces a retryable failure instead of loading forever") {
        var loggedErrorClass: String? = null

        val state = LearnedEntriesPolicy.load(
            readWords = { throw IOException("could not read table for word 'hunter2'") },
            readBigrams = { emptyList() },
            readTrigrams = { emptyList() },
            onError = { loggedErrorClass = it },
        )

        state shouldBe LearnedEntriesState.Failure("IOException")
        loggedErrorClass shouldBe "IOException"
    }

    test("a failing n-gram read fails the whole load") {
        val bigramFailure = LearnedEntriesPolicy.load(
            readWords = { emptyList() },
            readBigrams = { throw IllegalStateException("bigram table missing") },
            readTrigrams = { emptyList() },
        )
        val trigramFailure = LearnedEntriesPolicy.load(
            readWords = { emptyList() },
            readBigrams = { emptyList() },
            readTrigrams = { throw IllegalStateException("trigram table missing") },
        )

        bigramFailure shouldBe LearnedEntriesState.Failure("IllegalStateException")
        trigramFailure shouldBe LearnedEntriesState.Failure("IllegalStateException")
    }

    test("load never reports learned words or raw error text") {
        val state = LearnedEntriesPolicy.load(
            readWords = { throw IOException("row 'my-secret-word' is corrupt") },
            readBigrams = { emptyList() },
            readTrigrams = { emptyList() },
        )

        val failure = state.shouldBeInstanceOf<LearnedEntriesState.Failure>()
        failure.errorClass shouldBe "IOException"
        failure.toString().contains("my-secret-word") shouldBe false
    }

    test("load propagates cancellation instead of turning it into a failure card") {
        shouldThrow<CancellationException> {
            LearnedEntriesPolicy.load(
                readWords = { throw CancellationException("screen closed") },
                readBigrams = { emptyList() },
                readTrigrams = { emptyList() },
            )
        }
    }

    test("retrying a failed load recovers without any extra state") {
        var attempt = 0
        val readWords: suspend () -> List<UserDictionaryEntry> = {
            attempt++
            if (attempt == 1) throw IOException("transient") else listOf(entry("alpha", "en"))
        }

        val first = LearnedEntriesPolicy.load(readWords, { emptyList() }, { emptyList() })
        val second = LearnedEntriesPolicy.load(readWords, { emptyList() }, { emptyList() })

        first.shouldBeInstanceOf<LearnedEntriesState.Failure>()
        second.shouldBeInstanceOf<LearnedEntriesState.Ready>().words.map { it.word } shouldContainExactly listOf("alpha")
    }

    test("a successful removal reports success") {
        var invoked = false

        val outcome = LearnedEntriesPolicy.remove(operation = { invoked = true })

        invoked shouldBe true
        outcome shouldBe LearnedEntryRemoval.Success
    }

    test("a failing removal reports a privacy-safe failure so the row can stay on screen") {
        var loggedErrorClass: String? = null

        val outcome = LearnedEntriesPolicy.remove(
            operation = { throw IllegalStateException("delete of 'my-secret-word' failed") },
            onError = { loggedErrorClass = it },
        )

        outcome shouldBe LearnedEntryRemoval.Failure("IllegalStateException")
        loggedErrorClass shouldBe "IllegalStateException"
        outcome.toString().contains("my-secret-word") shouldBe false
    }

    test("removal propagates cancellation") {
        shouldThrow<CancellationException> {
            LearnedEntriesPolicy.remove(operation = { throw CancellationException("navigated away") })
        }
    }

    test("anonymous throwables still yield a non-blank error class") {
        val anonymous = object : RuntimeException("anonymous") {}

        LearnedEntriesPolicy.errorClassOf(anonymous).isNotBlank() shouldBe true
    }
})

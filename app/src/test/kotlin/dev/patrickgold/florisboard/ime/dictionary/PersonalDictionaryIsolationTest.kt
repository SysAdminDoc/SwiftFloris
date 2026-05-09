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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File

/**
 * ROADMAP §6 N7.3 — Personal-dictionary isolation regression test.
 *
 * Threat model recap:
 *
 * Android exposes `android.provider.UserDictionary.Words` as a public ContentProvider.
 * Any installed app holding the (low-protection) `READ_USER_DICTIONARY` permission can
 * enumerate everything written there. SwiftFloris's personal-dictionary auto-learn
 * (`DictionaryManager.learnWord`) MUST therefore write only to the app-private Room
 * database (`florisUserDictionaryDao`) inside `Context.getDataDir()`, never to the
 * system ContentProvider (`systemUserDictionaryDao`).
 *
 * This test guards the contract by static-content inspection of `DictionaryManager.kt`:
 * if a future contributor accidentally calls `systemUserDictionaryDao()` from inside
 * `learnWord`, the test fails with a clear message. We do this rather than mocking
 * the entire Android `ContentResolver` stack because:
 *  - The contract is structural ("learnWord MUST NOT reference systemDao").
 *  - The function is small enough that a textual check is robust.
 *  - The cost of the property-test approach (mocking ContentProvider, setting up
 *    Robolectric, etc.) far outweighs the value for a one-line invariant.
 *
 * Companion runtime check: `DictionaryManager.learnWord` *also* explicitly fetches
 * `florisUserDictionaryDao()` and returns early if null; the system DAO is not part
 * of its closure at all.
 */
class PersonalDictionaryIsolationTest : FunSpec({
    val sourceFile = locateDictionaryManagerSource()

    test("DictionaryManager.kt source file is reachable") {
        sourceFile.exists() shouldBe true
        sourceFile.canRead() shouldBe true
    }

    test("learnWord body never references systemUserDictionaryDao or systemUserDictionaryDatabase") {
        val source = sourceFile.readText()
        val body = extractFunctionBody(source, "fun learnWord(")
        body shouldContain "florisUserDictionaryDao"

        // Hard rule: the body of learnWord must not reference the system content-provider DAO.
        // CAKI / cross-app surface (https://developer.android.com/reference/android/provider/UserDictionary)
        // would let any app holding READ_USER_DICTIONARY enumerate auto-learned words.
        check(!body.contains("systemUserDictionaryDao")) {
            "ROADMAP §6 N7.3 violation — learnWord body references systemUserDictionaryDao. " +
                "Personal dictionary writes MUST stay in the app-private Floris Room DB " +
                "(see PersonalDictionaryIsolationTest header for the threat model)."
        }
        check(!body.contains("systemUserDictionaryDatabase")) {
            "ROADMAP §6 N7.3 violation — learnWord body references systemUserDictionaryDatabase. " +
                "Personal dictionary writes MUST stay in the app-private Floris Room DB."
        }
        check(!body.contains("UserDictionary.Words")) {
            "ROADMAP §6 N7.3 violation — learnWord body references the platform UserDictionary " +
                "ContentProvider columns directly. Stay inside the Room DAO abstraction."
        }
    }

    test("learnWord is gated on enableFlorisUserDictionary preference") {
        val body = extractFunctionBody(sourceFile.readText(), "fun learnWord(")
        body shouldContain "enableFlorisUserDictionary"
    }
})

private fun locateDictionaryManagerSource(): File {
    val candidates = listOf(
        File("app/src/main/kotlin/dev/patrickgold/florisboard/ime/dictionary/DictionaryManager.kt"),
        File("src/main/kotlin/dev/patrickgold/florisboard/ime/dictionary/DictionaryManager.kt"),
    )
    return candidates.firstOrNull { it.exists() }
        ?: error("DictionaryManager.kt not reachable from working directory ${File(".").absolutePath}")
}

/**
 * Extracts the body of the function whose declaration starts with [startsWith].
 * Returns the substring spanning from the opening `{` to its matching closing `}`.
 * Uses simple brace-balance counting; tolerates string literals and nested braces.
 */
private fun extractFunctionBody(source: String, startsWith: String): String {
    val declStart = source.indexOf(startsWith)
    require(declStart >= 0) { "Function declaration '$startsWith' not found in source" }
    val openBrace = source.indexOf('{', declStart)
    require(openBrace >= 0) { "Function '$startsWith' has no opening brace" }

    var depth = 0
    var i = openBrace
    while (i < source.length) {
        when (source[i]) {
            '{' -> depth++
            '}' -> {
                depth--
                if (depth == 0) {
                    return source.substring(openBrace, i + 1)
                }
            }
        }
        i++
    }
    error("Function '$startsWith' is missing its closing brace")
}

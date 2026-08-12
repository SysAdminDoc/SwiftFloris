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

import dev.patrickgold.florisboard.ime.text.key.KeyVariation
import io.kotest.matchers.string.shouldContain
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals

class NlpManagerSuggestionPrivacySnapshotTest {
    private val sourceFile = locateNlpManagerSource()

    @Test
    fun suggestionRequestSnapshotFreezesEnabledPrivacyAndSensitivityInputs() {
        assertEquals(
            SuggestionRequestPrivacySnapshot(
                emojiSuggestionEnabled = true,
                emojiMaxCandidateCount = 5,
                wordSuggestionEnabled = false,
                allowPossiblyOffensive = false,
                isPrivateSession = true,
                isEditorSensitive = true,
                isPasswordEditor = false,
            ),
            SuggestionPrivacyPolicy.snapshotSuggestionRequest(
                emojiSuggestionEnabled = true,
                emojiMaxCandidateCount = 5,
                wordSuggestionEnabled = false,
                blockPossiblyOffensive = true,
                isPrivateSession = true,
                isEditorSensitive = true,
                keyVariation = KeyVariation.NORMAL,
            ),
        )
    }

    @Test
    fun suggestionRequestSnapshotSuppressesWordAndEmojiCandidatesForPasswordFields() {
        assertEquals(
            SuggestionRequestPrivacySnapshot(
                emojiSuggestionEnabled = false,
                emojiMaxCandidateCount = 5,
                wordSuggestionEnabled = false,
                allowPossiblyOffensive = false,
                isPrivateSession = false,
                isEditorSensitive = true,
                isPasswordEditor = true,
            ),
            SuggestionPrivacyPolicy.snapshotSuggestionRequest(
                emojiSuggestionEnabled = true,
                emojiMaxCandidateCount = 5,
                wordSuggestionEnabled = true,
                blockPossiblyOffensive = true,
                isPrivateSession = false,
                isEditorSensitive = true,
                keyVariation = KeyVariation.PASSWORD,
            ),
        )
    }

    @Test
    fun suggestSnapshotsPrivacyInputsBeforeLaunchingProviderWork() {
        val suggestBody = extractFunctionBody(sourceFile.readText(), "fun suggest(subtype: Subtype, content: EditorContent)")
        val beforeLaunch = suggestBody.substringBefore("scope.launch")

        beforeLaunch shouldContain "SuggestionPrivacyPolicy.snapshotSuggestionRequest"
        beforeLaunch shouldContain "prefs.emoji.suggestionEnabled.get()"
        beforeLaunch shouldContain "prefs.emoji.suggestionCandidateMaxCount.get()"
        beforeLaunch shouldContain "prefs.suggestion.enabled.get()"
        beforeLaunch shouldContain "prefs.suggestion.blockPossiblyOffensive.get()"
        beforeLaunch shouldContain "keyboardManager.activeState.isIncognitoMode"
        beforeLaunch shouldContain "SensitiveFieldGuard.isSensitive"
        beforeLaunch shouldContain "editorInstance.activeInfo"
    }

    @Test
    fun asyncSuggestionWorkConsumesRequestSnapshotInsteadOfLivePrivacyState() {
        val source = sourceFile.readText()
        val suggestBody = extractFunctionBody(source, "fun suggest(subtype: Subtype, content: EditorContent)")
        val launchBody = extractFunctionBody(suggestBody, "scope.launch")

        launchBody shouldContain "requestPrivacy.emojiSuggestionEnabled"
        launchBody shouldContain "requestPrivacy.emojiMaxCandidateCount"
        launchBody shouldContain "requestPrivacy.wordSuggestionEnabled"
        launchBody shouldContain "requestPrivacy.allowPossiblyOffensive"
        launchBody shouldContain "requestPrivacy.isPrivateSession"
        launchBody shouldContain "requestPrivacy.isEditorSensitive"
        check(!launchBody.contains("keyboardManager.activeState.isIncognitoMode")) {
            "NlpManager.suggest async body must not re-read live incognito state after request snapshot."
        }
        check(!launchBody.contains("editorInstance.activeInfo")) {
            "NlpManager.suggest async body must not re-read live editor info after request snapshot."
        }
        check(!launchBody.contains("prefs.suggestion.enabled.get()")) {
            "NlpManager.suggest async body must not re-read suggestion enabled pref after request snapshot."
        }
        check(!launchBody.contains("prefs.suggestion.blockPossiblyOffensive.get()")) {
            "NlpManager.suggest async body must not re-read offensive-content pref after request snapshot."
        }
        check(!launchBody.contains("prefs.emoji.suggestionEnabled.get()")) {
            "NlpManager.suggest async body must not re-read emoji enabled pref after request snapshot."
        }
        check(!launchBody.contains("prefs.emoji.suggestionCandidateMaxCount.get()")) {
            "NlpManager.suggest async body must not re-read emoji max-count pref after request snapshot."
        }
    }

    @Test
    fun ghostTextGatingAcceptsRequestScopedEditorSensitivity() {
        val source = sourceFile.readText()
        val body = extractFunctionBody(source, "private suspend fun buildGhostTextCandidate(")

        source shouldContain "isPrivateSession: Boolean"
        source shouldContain "isEditorSensitive: Boolean"
        body shouldContain "SuggestionPrivacyPolicy.allowsGhostText"
        body shouldContain "addonHub.predictAsync"
        check(!body.contains("editorInstance.activeInfo")) {
            "Ghost-text gating must use the request-scoped editor sensitivity snapshot."
        }
        check(!body.contains("SensitiveFieldGuard.isSensitive")) {
            "Ghost-text gating must not recompute sensitivity after async launch."
        }
    }
}

private fun locateNlpManagerSource(): File {
    val candidates = listOf(
        File("app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/NlpManager.kt"),
        File("src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/NlpManager.kt"),
    )
    return candidates.firstOrNull { it.exists() && it.canRead() }
        ?: error("NlpManager.kt not reachable from working directory ${File(".").absolutePath}")
}

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

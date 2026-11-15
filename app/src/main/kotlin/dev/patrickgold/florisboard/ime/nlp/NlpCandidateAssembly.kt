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

import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.editor.EditorInstance
import dev.patrickgold.florisboard.ime.keyboard.KeyboardManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

internal class NlpCandidateAssembler(
    private val clipboardSuggestionProvider: SuggestionProvider,
    private val editorInstance: EditorInstance,
    private val keyboardManager: KeyboardManager,
) {
    private val prefs by FlorisPreferenceStore

    suspend fun assemble(
        isSuggestionOn: Boolean,
        internalSuggestions: List<SuggestionCandidate>,
        autoCommitUndoCandidate: AutoCommitUndoSuggestionCandidate?,
        glideAlternativeCandidates: List<SuggestionCandidate> = emptyList(),
    ): List<SuggestionCandidate> {
        if (!isSuggestionOn) {
            return emptyList()
        }
        return buildList {
            if (autoCommitUndoCandidate != null) {
                add(autoCommitUndoCandidate)
            }
            addAll(glideAlternativeCandidates)
            addAll(
                clipboardSuggestionProvider.suggest(
                    subtype = Subtype.DEFAULT,
                    content = editorInstance.activeContent,
                    maxCandidateCount = 8,
                    allowPossiblyOffensive = !prefs.suggestion.blockPossiblyOffensive.get(),
                    isPrivateSession = keyboardManager.activeState.isIncognitoMode,
                )
            )
            addAll(internalSuggestions)
        }
    }
}

internal class SmartbarAutoExpandController(
    private val editorInstance: EditorInstance,
    private val scope: CoroutineScope,
) {
    private val prefs by FlorisPreferenceStore
    private val expansionGeneration = AtomicLong(0L)
    private val expansionWrites = Mutex()

    fun onCandidateStateChanged(
        candidateSuggestions: List<*>?,
        inlineSuggestions: List<*>?,
    ) {
        if (!prefs.smartbar.enabled.get()) {
            return
        }
        val isSelection = editorInstance.activeContent.selection.isSelectionMode
        val isExpanded = candidateSuggestions.isNullOrEmpty() && inlineSuggestions.isNullOrEmpty() || isSelection
        val mine = expansionGeneration.incrementAndGet()
        scope.launch {
            // Launches on a multi-threaded scope can start out of order; the newest state writes
            // last because an older one that gets the lock late skips its write.
            expansionWrites.withLock {
                if (mine != expansionGeneration.get()) return@withLock
                prefs.smartbar.sharedActionsExpandWithAnimation.set(false)
                prefs.smartbar.sharedActionsExpanded.set(isExpanded)
            }
        }
    }
}

internal object SuggestionCandidateMerger {
    fun mergePreferred(
        preferred: List<SuggestionCandidate>,
        fallback: List<SuggestionCandidate>,
        maxCandidateCount: Int,
    ): List<SuggestionCandidate> {
        if (maxCandidateCount <= 0) {
            return emptyList()
        }
        val seen = mutableSetOf<String>()
        return buildList {
            for (candidate in preferred + fallback) {
                val key = candidate.text.toString().lowercase()
                if (key.isNotBlank() && seen.add(key)) {
                    add(candidate)
                    if (size >= maxCandidateCount) {
                        break
                    }
                }
            }
        }
    }
}

/**
 * Orders one request's own candidates. Emoji go after the word guesses, except
 * when the composing text is an emoji shortcode such as `:pensive`: then the
 * user is asking for an emoji, and the CLASSIC row only shows three slots, so
 * emoji appended after up to eight words never show up at all (#24).
 */
internal object InternalSuggestionOrder {
    fun merge(
        wordSuggestions: List<SuggestionCandidate>,
        emojiSuggestions: List<SuggestionCandidate>,
        ghostTextCandidate: SuggestionCandidate?,
        composingText: CharSequence,
        emojiTriggerPrefix: String,
    ): List<SuggestionCandidate> {
        val isShortcodeQuery = emojiTriggerPrefix.isNotEmpty() &&
            composingText.startsWith(emojiTriggerPrefix) &&
            emojiSuggestions.isNotEmpty()
        return buildList {
            if (isShortcodeQuery) {
                addAll(emojiSuggestions)
                addAll(wordSuggestions)
            } else {
                addAll(wordSuggestions)
                addAll(emojiSuggestions)
            }
            ghostTextCandidate?.let { add(it) }
        }
    }
}

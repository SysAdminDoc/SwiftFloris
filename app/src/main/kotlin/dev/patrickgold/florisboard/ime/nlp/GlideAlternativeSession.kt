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

import dev.patrickgold.florisboard.ime.editor.EditorContent
import dev.patrickgold.florisboard.ime.editor.EditorRange

internal data class GlideAlternativeCommit(
    val committed: String,
    val alternatives: List<String>,
    val range: EditorRange,
    val createdAtMs: Long,
)

/**
 * Keeps a small, process-local history of glide classifications so alternatives can be restored when the user
 * moves the cursor back into an unchanged committed word.
 */
internal class GlideAlternativeSession(
    private val maxCommits: Int = MaxCommits,
    private val maxAlternativesPerCommit: Int = MaxAlternativesPerCommit,
    private val timeoutMs: Long = DefaultTimeoutMs,
) {
    private val commits = ArrayDeque<GlideAlternativeCommit>()
    private var activeCommit: GlideAlternativeCommit? = null

    fun remember(
        committedText: String,
        alternatives: List<String>,
        range: EditorRange,
        now: Long = System.currentTimeMillis(),
    ) {
        val retainedAlternatives = alternatives
            .asSequence()
            .filter { it.isNotBlank() && it != committedText }
            .distinct()
            .take(maxAlternativesPerCommit.coerceAtLeast(0))
            .toList()
        if (
            committedText.isBlank() ||
            retainedAlternatives.isEmpty() ||
            !range.isValid ||
            range.start > range.end ||
            range.length != committedText.length
        ) {
            return
        }

        val commit = GlideAlternativeCommit(
            committed = committedText,
            alternatives = retainedAlternatives,
            range = range,
            createdAtMs = now,
        )
        commits.removeAll { it.range == range }
        commits.addFirst(commit)
        while (commits.size > maxCommits.coerceAtLeast(0)) {
            commits.removeLast()
        }
        activeCommit = commit.takeIf { commits.contains(it) }
    }

    fun onContentChanged(
        content: EditorContent,
        now: Long = System.currentTimeMillis(),
        allowRetention: Boolean = true,
    ) {
        if (!allowRetention || content.offset < 0 || content.selection.isNotValid) {
            clear()
            return
        }
        pruneExpired(now)
        commits.removeAll { commit ->
            content.textAt(commit.range) != commit.committed
        }
        activeCommit = commits.firstOrNull { commit ->
            commit.selectionTouches(content.selection)
        }
    }

    fun activeCandidates(now: Long = System.currentTimeMillis()): List<SuggestionCandidate> {
        pruneExpired(now)
        val commit = activeCommit ?: return emptyList()
        return commit.alternatives.mapIndexed { index, alternative ->
            GlideAlternativeSuggestionCandidate(
                alternative = alternative,
                committed = commit.committed,
                range = commit.range,
                rank = index,
                confidence = 1.0 - index.toDouble() / commit.alternatives.size,
            )
        }
    }

    fun consume(candidate: GlideAlternativeSuggestionCandidate): Boolean {
        val commit = commits.firstOrNull {
            it.committed == candidate.committed &&
                it.range == candidate.range &&
                candidate.alternative in it.alternatives
        } ?: return false
        commits.remove(commit)
        if (activeCommit == commit) {
            activeCommit = null
        }
        return true
    }

    fun clear() {
        commits.clear()
        activeCommit = null
    }

    private fun pruneExpired(now: Long) {
        commits.removeAll { commit ->
            now >= commit.createdAtMs && now - commit.createdAtMs >= timeoutMs
        }
        if (activeCommit !in commits) {
            activeCommit = null
        }
    }

    private fun GlideAlternativeCommit.selectionTouches(selection: EditorRange): Boolean {
        return if (selection.isCursorMode) {
            selection.start in range.start..(range.end + 1)
        } else {
            selection.start < range.end && selection.end > range.start
        }
    }

    private fun EditorContent.textAt(range: EditorRange): String? {
        val localStart = range.start - offset
        val localEnd = range.end - offset
        if (localStart < 0 || localEnd > text.length || localStart >= localEnd) {
            return null
        }
        return text.substring(localStart, localEnd)
    }

    private companion object {
        const val MaxCommits = 5
        const val MaxAlternativesPerCommit = 7
        const val DefaultTimeoutMs = 6_000L
    }
}

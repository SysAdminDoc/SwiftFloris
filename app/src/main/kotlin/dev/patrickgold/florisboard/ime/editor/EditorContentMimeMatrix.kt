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

package dev.patrickgold.florisboard.ime.editor

/**
 * ROADMAP matrix #35 — local image clipboard / sticker history.
 *
 * The image / sticker commit infrastructure has been in place since v1.7.x:
 *  - `ClipboardItem` supports `ItemType.IMAGE` / `ItemType.VIDEO` with a `Uri`.
 *  - `EditorInstance.commitClipboardItem` already routes image / video items through
 *    `InputConnectionCompat.commitContent` with `INPUT_CONTENT_GRANT_READ_URI_PERMISSION`.
 *  - `EditorInstance.commitRichContent` is the sticker-palette / file-share entry point.
 *  - `StickerPaletteView` + `BundledStickerRepository` already render the palette.
 *
 * What was missing was a *pure* mime-matching matrix that the UI can consult before showing an image
 * "insert" affordance. Without it, the keyboard either tries the commit and silently fails (`commitContent`
 * returns false on an editor that does not accept the type), or proactively hides the affordance based on
 * inconsistent ad-hoc checks. This helper centralises the "does the editor accept this kind of media?" logic
 * so smartbar buttons, sticker tiles, and clipboard image tiles all stay in lockstep.
 *
 * Kept pure (no Android types) so the matching contract is unit-testable without Robolectric.
 *
 * ## Globbing
 *
 * The matching follows Android's `ClipDescription.compareMimeTypes` semantics: `image/*` matches every
 * `image/png`, `image/jpeg`, `image/webp`, etc. The pure helper does its own lightweight glob match so the
 * full Android type-resolution chain (which depends on `MimeTypeMap` and `ContentResolver` lookups) is not
 * needed at this layer.
 */
object EditorContentMimeMatrix {

    /** Common image MIME types in order of preference (PNG → WebP → JPEG → GIF → fallback `image/*`). */
    val PREFERRED_IMAGE_MIMES: List<String> = listOf(
        "image/png",
        "image/webp",
        "image/jpeg",
        "image/gif",
    )

    /** Static-sticker preference order (PNG dominates; WebP is the modern alternative). */
    val PREFERRED_STATIC_STICKER_MIMES: List<String> = listOf(
        "image/png",
        "image/webp",
    )

    /** Animated-sticker preference order. */
    val PREFERRED_ANIMATED_STICKER_MIMES: List<String> = listOf(
        "image/webp",
        "image/gif",
        "video/mp4",
    )

    /**
     * @return true if [editorMimeTypes] declares acceptance for any of [PREFERRED_IMAGE_MIMES] or
     *  for the broader `image/*` glob.
     */
    fun acceptsImages(editorMimeTypes: List<String>): Boolean {
        return PREFERRED_IMAGE_MIMES.any { editorMimeType ->
            isAccepted(editorMimeType, editorMimeTypes)
        }
    }

    /**
     * @return true if [editorMimeTypes] declares acceptance for any of the candidate types.
     */
    fun acceptsAny(candidates: List<String>, editorMimeTypes: List<String>): Boolean {
        return candidates.any { isAccepted(it, editorMimeTypes) }
    }

    /**
     * Pick the best mime type from [available] that [editorMimeTypes] also accepts. Returns null when there
     * is no overlap.
     */
    fun bestMatchFor(available: List<String>, editorMimeTypes: List<String>): String? {
        return available.firstOrNull { isAccepted(it, editorMimeTypes) }
    }

    /**
     * @return true if [candidate] matches any entry in [editorMimeTypes] under glob semantics.
     *  - Exact equality matches.
     *  - `image/*` on the editor side matches any `image/X` candidate.
     *  - `* /*` on the editor side matches any candidate.
     *  - The reverse direction (candidate `image/*` against an editor `image/png`) does NOT match here —
     *    we conservatively interpret the editor side as authoritative.
     */
    fun isAccepted(candidate: String, editorMimeTypes: List<String>): Boolean {
        val normalisedCandidate = candidate.trim().lowercase()
        if (normalisedCandidate.isEmpty()) return false
        for (editorRaw in editorMimeTypes) {
            val editorType = editorRaw.trim().lowercase()
            if (editorType.isEmpty()) continue
            if (editorType == normalisedCandidate) return true
            if (editorType == "*/*") return true
            val slashIdx = editorType.indexOf('/')
            if (slashIdx <= 0 || slashIdx >= editorType.length - 1) continue
            val editorMajor = editorType.substring(0, slashIdx)
            val editorSubtype = editorType.substring(slashIdx + 1)
            val candidateSlash = normalisedCandidate.indexOf('/')
            if (candidateSlash <= 0) continue
            val candidateMajor = normalisedCandidate.substring(0, candidateSlash)
            if (editorMajor != candidateMajor) continue
            if (editorSubtype == "*") return true
        }
        return false
    }
}

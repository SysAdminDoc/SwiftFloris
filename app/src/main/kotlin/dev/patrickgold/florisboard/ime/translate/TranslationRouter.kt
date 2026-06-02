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

package dev.patrickgold.florisboard.ime.translate

import dev.patrickgold.florisboard.ime.smartcompose.SensitiveFieldGuard

/**
 * ROADMAP §10.5 L2.1f — translation end-to-end router.
 *
 * Sibling of `SmartComposeRouter` (v1.8.21) but for the inline-
 * translation surface. Layers every v1.8.x translation building
 * block in the right order:
 *
 *   1. [SensitiveFieldGuard] — short-circuit on password / PIN /
 *      no-learn fields.
 *   2. [LanguageDetector] — auto-detect source locale when caller
 *      didn't supply one ([Request.sourceLocale] = null).
 *   3. [TranslationLanguagePackManager] — pick a default
 *      `LanguagePairDescriptor` for the resolved source locale +
 *      user preferred target.
 *   4. [SentenceTokenizer] — split paragraph-length input into
 *      sentences when applicable (Bergamot prefers per-sentence
 *      inference).
 *   5. [TranslationCache] — internal LRU cache check per sentence.
 *   6. Underlying [InlineTranslator] — usually
 *      `InlineTranslatorRegistry.active`.
 *   7. Stitch per-sentence translations back into the final
 *      output preserving inter-sentence whitespace.
 *
 * The router is intentionally a pure-Kotlin facade. Production code
 * wires `InlineTranslatorRegistry.active` + the manager singleton;
 * tests inject fakes.
 */
class TranslationRouter(
    private val translator: InlineTranslator,
    private val packManager: PackManagerView,
    private val bypassCache: Boolean = false,
    cacheCapacity: Int = TranslationCache.DEFAULT_CAPACITY,
    private val isConsentGranted: () -> Boolean = { true },
) {

    private val cache: TranslationCache? = if (bypassCache) null else TranslationCache(
        delegate = translator,
        capacity = cacheCapacity,
    )

    /**
     * Translate [request].sourceText, returning a [Response] with
     * either the final string + the resolved source/target locales,
     * or a structured failure reason.
     */
    fun translate(request: Request): Response {
        // Matrix #37 — consent gate. NEEDS_PROMPT / DENIED short-circuits with the "consent required" reason
        // so the IME's UI layer can drive the consent-dialog flow.
        if (!isConsentGranted()) {
            return Response.Suppressed(reason = "consent required")
        }
        if (SensitiveFieldGuard.isSensitive(request.inputType, request.imeOptions)) {
            return Response.Suppressed(reason = "sensitive field")
        }
        if (request.sourceText.isBlank()) {
            return Response.Suppressed(reason = "blank input")
        }
        val resolvedSource = request.sourceLocale ?: detectSourceLocale(request.sourceText)
            ?: return Response.Suppressed(reason = "source-locale detection failed")
        val target = request.targetLocale ?: packManager.preferredTargetLocale()
            ?: return Response.Suppressed(reason = "no target locale resolved")
        if (resolvedSource == target) return Response.Suppressed(reason = "source == target")
        val pair = packManager.installedPairs()
            .firstOrNull { it.sourceLocale == resolvedSource && it.targetLocale == target }
            ?: return Response.Suppressed(reason = "no installed pair for $resolvedSource→$target")

        // Sentence-split + translate piece-by-piece for paragraphs.
        val pieces = if (SentenceTokenizer.hasMultipleSentences(request.sourceText)) {
            SentenceTokenizer.split(request.sourceText)
        } else {
            listOf(request.sourceText)
        }
        val out = StringBuilder(request.sourceText.length)
        var anyTranslated = false
        for (piece in pieces) {
            // SentenceTokenizer folds inter-sentence whitespace into the *trailing*
            // end of each piece. A neural translator (Bergamot) normalises its
            // output and does NOT echo that trailing whitespace back, so stitching
            // raw `translatedText` would collapse "Hola. ¡Mundo!" into
            // "Hola.¡Mundo!". Split the separator off, translate only the core,
            // and re-append the original separator verbatim.
            val coreEnd = piece.indexOfLast { !it.isWhitespace() } + 1
            val core = if (coreEnd > 0) piece.substring(0, coreEnd) else piece
            val trailing = if (coreEnd > 0) piece.substring(coreEnd) else ""
            if (core.isEmpty()) {
                // Whitespace-only piece (shouldn't happen post-split, but be safe).
                out.append(piece)
                continue
            }
            val pieceResult = if (cache != null) {
                cache.translate(core, resolvedSource, target)
            } else {
                translator.translate(core, resolvedSource, target)
            }
            when (pieceResult) {
                is TranslationResult.Translated -> {
                    out.append(pieceResult.translatedText)
                    out.append(trailing)
                    anyTranslated = true
                }
                is TranslationResult.Unavailable -> {
                    // Fall back to source for the piece so the user
                    // still sees something coherent.
                    out.append(core)
                    out.append(trailing)
                }
            }
        }
        if (!anyTranslated) return Response.Suppressed(reason = "translator returned Unavailable")
        return Response.Translated(
            translatedText = out.toString(),
            resolvedSourceLocale = resolvedSource,
            resolvedTargetLocale = target,
            pair = pair,
        )
    }

    /** Drop the LRU cache (e.g. on language-pack swap). */
    fun clearCache() {
        cache?.clear()
    }

    /** Map [LanguageDetector.DetectedScript] → ISO 639-1 best-guess. */
    private fun detectSourceLocale(text: String): String? {
        val det = LanguageDetector.detect(text)
        if (det.confidence < 0.5f) return null
        return when (det.script) {
            LanguageDetector.DetectedScript.LATIN -> "en"
            LanguageDetector.DetectedScript.CYRILLIC -> "ru"
            LanguageDetector.DetectedScript.GREEK -> "el"
            LanguageDetector.DetectedScript.HEBREW -> "he"
            LanguageDetector.DetectedScript.ARABIC -> "ar"
            LanguageDetector.DetectedScript.DEVANAGARI -> "hi"
            LanguageDetector.DetectedScript.BENGALI -> "bn"
            LanguageDetector.DetectedScript.CJK -> "zh"
            LanguageDetector.DetectedScript.THAI -> "th"
            LanguageDetector.DetectedScript.UNKNOWN -> null
        }
    }

    /**
     * Caller-facing input. `sourceLocale` / `targetLocale` are
     * optional so the router can auto-detect / use preferred
     * defaults.
     */
    data class Request(
        val sourceText: String,
        val sourceLocale: String? = null,
        val targetLocale: String? = null,
        val inputType: Int = 0x01,
        val imeOptions: Int = 0,
    )

    /** Caller-facing output. */
    sealed class Response {
        data class Translated(
            val translatedText: String,
            val resolvedSourceLocale: String,
            val resolvedTargetLocale: String,
            val pair: LanguagePairDescriptor,
        ) : Response()

        data class Suppressed(val reason: String) : Response()
    }

    /**
     * View interface over [TranslationLanguagePackManager] so tests
     * inject a fake without driving the singleton's atomic state.
     * Production calls [from] to produce a view backed by the real
     * manager.
     */
    interface PackManagerView {
        fun installedPairs(): List<LanguagePairDescriptor>
        fun preferredTargetLocale(): String?
        companion object {
            fun from(): PackManagerView = object : PackManagerView {
                override fun installedPairs() =
                    TranslationLanguagePackManager.installedPairs()
                override fun preferredTargetLocale() =
                    TranslationLanguagePackManager.preferredTargetLocale()
            }
        }
    }
}

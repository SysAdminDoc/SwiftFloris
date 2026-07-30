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

import kotlinx.serialization.Serializable

/**
 * ROADMAP §7 L2 — on-device inline-translation facade (Bergamot WASM
 * neural machine translation, Firefox's local-only translator).
 *
 * SwiftKey's "translation toolbar" sends every keystroke to Microsoft's
 * cloud for translation; that surface is the explicit anti-pattern this
 * facade exists to counter. Bergamot's WASM NMT runtime runs the
 * compressed Mozilla/Bergamot encoder+decoder models entirely
 * on-device (Firefox embeds the same `marian-decoder` shape). Like the
 * LiteRT-LM smart-compose and handwriting model bindings, a future
 * Bergamot integration belongs outside the base APK. No translator
 * runtime addon currently ships and no production path calls
 * [InlineTranslatorRegistry.setActive], so [InlineTranslator.Default]
 * remains active and translation is unavailable. Any future runtime
 * must use the explicit addon trust/enrolment boundary before this
 * registry is activated.
 *
 * The facade exposes the minimum the IME's smartbar quick-action +
 * preview-row UI needs:
 *  - [translate] translates a fragment from one locale to another.
 *  - [isLanguagePairReady] tests whether a model is loaded for
 *    `(source, target)`.
 *  - [installedPairs] lists the language pairs currently bound.
 */
interface InlineTranslator {
    fun translate(
        sourceText: String,
        sourceLocale: String,
        targetLocale: String,
    ): TranslationResult

    fun isLanguagePairReady(sourceLocale: String, targetLocale: String): Boolean
    val installedPairs: Set<LanguagePairDescriptor>

    object Default : InlineTranslator {
        override fun translate(
            sourceText: String,
            sourceLocale: String,
            targetLocale: String,
        ) = TranslationResult.Unavailable
        override fun isLanguagePairReady(sourceLocale: String, targetLocale: String) = false
        override val installedPairs: Set<LanguagePairDescriptor> = emptySet()
    }
}

/**
 * Descriptor contract for one potential local translation model. A
 * future runtime can expose pair metadata without making the base APK
 * parse or bundle model binaries.
 */
@Serializable
data class LanguagePairDescriptor(
    /** ISO 639-1 lowercase, e.g. `"en"`. */
    val sourceLocale: String,
    val targetLocale: String,
    /** Asset path inside the addon APK to the Bergamot model bundle. */
    val bundleAssetPath: String,
    /** Bundle size in bytes (UI surfaces it for the language download page). */
    val bundleSizeBytes: Long,
    /** Model quality tier — Bergamot ships "tiny" (~17 MB) + "base". */
    val qualityTier: String,
) {
    init {
        require(sourceLocale.isNotBlank() && sourceLocale == sourceLocale.lowercase()) {
            "sourceLocale must be a non-blank lowercase ISO 639-1 code"
        }
        require(targetLocale.isNotBlank() && targetLocale == targetLocale.lowercase()) {
            "targetLocale must be a non-blank lowercase ISO 639-1 code"
        }
        require(sourceLocale != targetLocale) { "source and target locales must differ" }
        require(bundleAssetPath.isNotBlank()) { "bundleAssetPath must not be blank" }
        require(bundleSizeBytes >= 0) { "bundleSizeBytes must be non-negative" }
        require(qualityTier in setOf("tiny", "base", "high")) {
            "qualityTier must be tiny / base / high; was $qualityTier"
        }
    }

    val pairKey: String get() = "$sourceLocale-$targetLocale"
}

/**
 * Translation result envelope. [Translated] carries the translated text +
 * a confidence; [Unavailable] is the "no model loaded" / "language pair
 * not installed" answer the IME uses to gate the preview row off.
 */
sealed class TranslationResult {
    object Unavailable : TranslationResult()
    data class Translated(
        val translatedText: String,
        val confidence: Float,
    ) : TranslationResult() {
        init {
            require(translatedText.isNotEmpty()) { "translatedText must not be empty" }
            require(confidence in 0f..1f) { "confidence must be in [0, 1]; was $confidence" }
        }
    }
}

object InlineTranslatorRegistry {
    @Volatile
    private var current: InlineTranslator = InlineTranslator.Default
    val active: InlineTranslator get() = current
    fun setActive(provider: InlineTranslator) { current = provider }
    fun reset() { current = InlineTranslator.Default }
}

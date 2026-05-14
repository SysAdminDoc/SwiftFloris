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

package dev.patrickgold.florisboard.ime.text.gestures

data class GlideTypingLanguageProfile(
    val languageCode: String,
    val engine: GlideTypingEngine,
    val quality: GlideTypingQuality,
)

enum class GlideTypingEngine {
    STATISTICAL,
    NEURAL_COMING_SOON,
}

enum class GlideTypingQuality {
    EXPANDED_STATISTICAL,
    IMPORTED_STATISTICAL,
}

object GlideTypingLanguageSupport {
    private val profiles = listOf(
        GlideTypingLanguageProfile(
            languageCode = "en",
            engine = GlideTypingEngine.STATISTICAL,
            quality = GlideTypingQuality.EXPANDED_STATISTICAL,
        ),
        GlideTypingLanguageProfile(
            languageCode = "de",
            engine = GlideTypingEngine.STATISTICAL,
            quality = GlideTypingQuality.IMPORTED_STATISTICAL,
        ),
        GlideTypingLanguageProfile(
            languageCode = "es",
            engine = GlideTypingEngine.STATISTICAL,
            quality = GlideTypingQuality.IMPORTED_STATISTICAL,
        ),
        GlideTypingLanguageProfile(
            languageCode = "fr",
            engine = GlideTypingEngine.STATISTICAL,
            quality = GlideTypingQuality.IMPORTED_STATISTICAL,
        ),
        GlideTypingLanguageProfile(
            languageCode = "it",
            engine = GlideTypingEngine.STATISTICAL,
            quality = GlideTypingQuality.IMPORTED_STATISTICAL,
        ),
        GlideTypingLanguageProfile(
            languageCode = "pt",
            engine = GlideTypingEngine.STATISTICAL,
            quality = GlideTypingQuality.IMPORTED_STATISTICAL,
        ),
    ).associateBy { profile -> profile.languageCode }

    fun profileFor(languageCode: String): GlideTypingLanguageProfile? {
        return profiles[languageCode.substringBefore('-').substringBefore('_').lowercase()]
    }

    fun supportedLanguageCodes(): Set<String> {
        return profiles.keys
    }
}

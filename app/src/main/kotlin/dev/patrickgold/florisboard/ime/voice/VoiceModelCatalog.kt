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

package dev.patrickgold.florisboard.ime.voice

enum class VoiceModelEngine {
    WHISPER_CPP,
    VOSK_STREAMING,
}

data class VoiceModelCatalogEntry(
    val id: String,
    val localeTag: String,
    val languageName: String,
    val engine: VoiceModelEngine,
    val displayName: String,
    val artifactFileName: String,
    val approximateSizeMb: Int,
    val license: String,
    val sourceUrl: String,
)

object VoiceModelCatalog {
    val entries: List<VoiceModelCatalogEntry> = listOf(
        VoiceModelCatalogEntry(
            id = "whisper-en-tiny-en",
            localeTag = "en",
            languageName = "English",
            engine = VoiceModelEngine.WHISPER_CPP,
            displayName = "Whisper tiny.en",
            artifactFileName = "ggml-tiny.en.bin",
            approximateSizeMb = 75,
            license = "MIT",
            sourceUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en.bin",
        ),
        VoiceModelCatalogEntry(
            id = "whisper-en-base-en",
            localeTag = "en",
            languageName = "English",
            engine = VoiceModelEngine.WHISPER_CPP,
            displayName = "Whisper base.en",
            artifactFileName = "ggml-base.en.bin",
            approximateSizeMb = 142,
            license = "MIT",
            sourceUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.en.bin",
        ),
        VoiceModelCatalogEntry(
            id = "whisper-multi-large-v3-turbo-q8",
            localeTag = "multi",
            languageName = "Multilingual",
            engine = VoiceModelEngine.WHISPER_CPP,
            displayName = "Whisper Large-v3-Turbo Q8",
            artifactFileName = "ggml-large-v3-turbo-q8_0.bin",
            approximateSizeMb = 834,
            license = "MIT",
            sourceUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-large-v3-turbo-q8_0.bin",
        ),
        vosk(
            id = "vosk-en-us-small-0-15",
            localeTag = "en-US",
            languageName = "English",
            displayName = "Vosk small en-US 0.15",
            artifactFileName = "vosk-model-small-en-us-0.15.zip",
            approximateSizeMb = 40,
        ),
        vosk(
            id = "vosk-es-small-0-42",
            localeTag = "es",
            languageName = "Spanish",
            displayName = "Vosk small es 0.42",
            artifactFileName = "vosk-model-small-es-0.42.zip",
            approximateSizeMb = 39,
        ),
        vosk(
            id = "vosk-fr-small-0-22",
            localeTag = "fr",
            languageName = "French",
            displayName = "Vosk small fr 0.22",
            artifactFileName = "vosk-model-small-fr-0.22.zip",
            approximateSizeMb = 41,
        ),
        vosk(
            id = "vosk-de-small-0-15",
            localeTag = "de",
            languageName = "German",
            displayName = "Vosk small de 0.15",
            artifactFileName = "vosk-model-small-de-0.15.zip",
            approximateSizeMb = 45,
        ),
        vosk(
            id = "vosk-it-small-0-22",
            localeTag = "it",
            languageName = "Italian",
            displayName = "Vosk small it 0.22",
            artifactFileName = "vosk-model-small-it-0.22.zip",
            approximateSizeMb = 48,
        ),
        vosk(
            id = "vosk-pt-small-0-3",
            localeTag = "pt",
            languageName = "Portuguese",
            displayName = "Vosk small pt 0.3",
            artifactFileName = "vosk-model-small-pt-0.3.zip",
            approximateSizeMb = 31,
        ),
    )

    fun byId(id: String?): VoiceModelCatalogEntry? {
        return entries.firstOrNull { it.id == id }
    }

    fun embeddedWhisperModelFor(tier: VoiceModelTier): VoiceModelCatalogEntry {
        val id = when (tier) {
            VoiceModelTier.TINY_EN -> "whisper-en-tiny-en"
            VoiceModelTier.BASE_EN -> "whisper-en-base-en"
            VoiceModelTier.LARGE_V3_TURBO_INT8 -> "whisper-multi-large-v3-turbo-q8"
        }
        return byId(id) ?: error("Missing embedded Whisper catalog entry for $tier")
    }

    private fun vosk(
        id: String,
        localeTag: String,
        languageName: String,
        displayName: String,
        artifactFileName: String,
        approximateSizeMb: Int,
    ): VoiceModelCatalogEntry {
        return VoiceModelCatalogEntry(
            id = id,
            localeTag = localeTag,
            languageName = languageName,
            engine = VoiceModelEngine.VOSK_STREAMING,
            displayName = displayName,
            artifactFileName = artifactFileName,
            approximateSizeMb = approximateSizeMb,
            license = "Apache-2.0",
            sourceUrl = "https://alphacephei.com/vosk/models/$artifactFileName",
        )
    }
}

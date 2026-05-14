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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

class VoiceModelCatalogTest : FunSpec({
    test("catalog exposes Whisper and Vosk model families") {
        val engines = VoiceModelCatalog.entries.map { it.engine }.toSet()

        engines shouldContain VoiceModelEngine.WHISPER_CPP
        engines shouldContain VoiceModelEngine.VOSK_STREAMING
    }

    test("embedded Whisper model tiers resolve to concrete catalog artifacts") {
        VoiceModelCatalog.embeddedWhisperModelFor(VoiceModelTier.TINY_EN).artifactFileName shouldBe
            "ggml-tiny.en.bin"
        VoiceModelCatalog.embeddedWhisperModelFor(VoiceModelTier.BASE_EN).artifactFileName shouldBe
            "ggml-base.en.bin"
        VoiceModelCatalog.embeddedWhisperModelFor(VoiceModelTier.LARGE_V3_TURBO_INT8).artifactFileName shouldBe
            "ggml-large-v3-turbo-q8_0.bin"
    }

    test("streaming Vosk catalog uses Apache-compatible small mobile models") {
        val voskModels = VoiceModelCatalog.entries.filter { it.engine == VoiceModelEngine.VOSK_STREAMING }

        voskModels.map { it.localeTag } shouldContain "en-US"
        voskModels.map { it.localeTag } shouldContain "es"
        voskModels.all { it.license == "Apache-2.0" } shouldBe true
        voskModels.all { it.approximateSizeMb <= 50 } shouldBe true
    }
})

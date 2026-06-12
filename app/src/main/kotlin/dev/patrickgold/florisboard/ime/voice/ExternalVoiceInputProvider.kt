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

data class ExternalVoiceInputProvider(
    val packageName: String,
    val label: String,
    val installUrl: String,
)

object ExternalVoiceInputProviders {
    val Futo = ExternalVoiceInputProvider(
        packageName = "org.futo.voiceinput",
        label = "FUTO Voice Input",
        installUrl = "https://f-droid.org/packages/org.futo.voiceinput/",
    )

    val WhisperInput = ExternalVoiceInputProvider(
        packageName = "com.alexvt.whisperinput",
        label = "WhisperInput",
        installUrl = "https://github.com/alex-vt/WhisperInput",
    )

    val Whisper = ExternalVoiceInputProvider(
        packageName = "org.woheller69.whisper",
        label = "Whisper",
        installUrl = "https://f-droid.org/packages/org.woheller69.whisper/",
    )

    val SupportedOfflineImeProviders = listOf(Futo, WhisperInput, Whisper)

    fun byPackageName(packageName: String): ExternalVoiceInputProvider? {
        return SupportedOfflineImeProviders.firstOrNull { it.packageName == packageName }
    }
}

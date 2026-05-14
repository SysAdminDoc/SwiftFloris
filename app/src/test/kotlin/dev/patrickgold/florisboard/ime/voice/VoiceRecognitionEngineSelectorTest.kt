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
import io.kotest.matchers.shouldBe

class VoiceRecognitionEngineSelectorTest : FunSpec({
    val highRam = VoiceDeviceRamProfile(totalRamMb = 12_288, isLowRamDevice = false)
    val lowRam = VoiceDeviceRamProfile(totalRamMb = 3_072, isLowRamDevice = false)

    test("auto selects Vosk for command mode when streaming model is available") {
        val selection = select(
            profile = highRam,
            commandModeRequested = true,
            availability = availability(
                hasEmbeddedWhisperModel = true,
                hasVoskStreamingModel = true,
            ),
        )

        selection.route shouldBe VoiceRecognitionEngineRoute.VOSK_STREAMING
        selection.reason shouldBe VoiceRecognitionEngineRouteReason.AUTO_COMMAND_MODE_VOSK
    }

    test("auto selects Vosk for low-ram streaming fallback") {
        val selection = select(
            profile = lowRam,
            availability = availability(
                hasEmbeddedWhisperModel = true,
                hasVoskStreamingModel = true,
            ),
        )

        selection.route shouldBe VoiceRecognitionEngineRoute.VOSK_STREAMING
        selection.reason shouldBe VoiceRecognitionEngineRouteReason.AUTO_LOW_RAM_VOSK
    }

    test("auto selects embedded Whisper on capable devices outside command mode") {
        val selection = select(
            profile = highRam,
            availability = availability(
                hasEmbeddedWhisperModel = true,
                hasVoskStreamingModel = true,
            ),
        )

        selection.route shouldBe VoiceRecognitionEngineRoute.EMBEDDED_WHISPER
        selection.reason shouldBe VoiceRecognitionEngineRouteReason.AUTO_EMBEDDED_WHISPER
        selection.embeddedModelTier shouldBe VoiceModelTier.LARGE_V3_TURBO_INT8
    }

    test("auto falls back to external voice input while local models are missing") {
        val selection = select(
            profile = highRam,
            availability = availability(
                hasEmbeddedWhisperModel = false,
                hasVoskStreamingModel = false,
                externalVoiceInputReady = true,
            ),
        )

        selection.route shouldBe VoiceRecognitionEngineRoute.EXTERNAL_IME
        selection.reason shouldBe VoiceRecognitionEngineRouteReason.FALLBACK_EXTERNAL_WHILE_LOCAL_UNAVAILABLE
    }

    test("explicit Vosk requires both model and SwiftFloris microphone permission") {
        val missingModel = select(
            profile = highRam,
            preference = VoiceRecognitionEnginePreference.VOSK_STREAMING,
            availability = availability(
                hasEmbeddedWhisperModel = true,
                hasVoskStreamingModel = false,
            ),
        )
        val missingPermission = select(
            profile = highRam,
            preference = VoiceRecognitionEnginePreference.VOSK_STREAMING,
            availability = availability(
                hasEmbeddedWhisperModel = true,
                hasVoskStreamingModel = true,
                hasSwiftFlorisMicrophonePermission = false,
            ),
        )

        missingModel.route shouldBe VoiceRecognitionEngineRoute.UNAVAILABLE
        missingModel.reason shouldBe VoiceRecognitionEngineRouteReason.VOSK_MODEL_MISSING
        missingPermission.route shouldBe VoiceRecognitionEngineRoute.UNAVAILABLE
        missingPermission.reason shouldBe VoiceRecognitionEngineRouteReason.LOCAL_MIC_PERMISSION_MISSING
    }

    test("explicit external voice input reports readiness without local prerequisites") {
        val selection = select(
            profile = lowRam,
            preference = VoiceRecognitionEnginePreference.EXTERNAL_IME,
            availability = availability(
                hasEmbeddedWhisperModel = false,
                hasVoskStreamingModel = false,
                hasSwiftFlorisMicrophonePermission = false,
                externalVoiceInputReady = true,
            ),
        )

        selection.route shouldBe VoiceRecognitionEngineRoute.EXTERNAL_IME
        selection.reason shouldBe VoiceRecognitionEngineRouteReason.EXPLICIT_EXTERNAL_IME
    }
})

private fun select(
    profile: VoiceDeviceRamProfile,
    preference: VoiceRecognitionEnginePreference = VoiceRecognitionEnginePreference.AUTO,
    commandModeRequested: Boolean = false,
    availability: VoiceRecognitionEngineAvailability,
): VoiceRecognitionEngineSelection {
    return VoiceRecognitionEngineSelector.select(
        request = VoiceRecognitionEngineRequest(
            enginePreference = preference,
            modelPreference = VoiceModelPreference.AUTO,
            deviceRamProfile = profile,
            commandModeRequested = commandModeRequested,
        ),
        availability = availability,
    )
}

private fun availability(
    hasEmbeddedWhisperModel: Boolean,
    hasVoskStreamingModel: Boolean,
    hasSwiftFlorisMicrophonePermission: Boolean = true,
    externalVoiceInputReady: Boolean = false,
): VoiceRecognitionEngineAvailability {
    return VoiceRecognitionEngineAvailability(
        hasEmbeddedWhisperModel = hasEmbeddedWhisperModel,
        hasVoskStreamingModel = hasVoskStreamingModel,
        hasSwiftFlorisMicrophonePermission = hasSwiftFlorisMicrophonePermission,
        externalVoiceInputReady = externalVoiceInputReady,
    )
}

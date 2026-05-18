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

enum class VoiceRecognitionEnginePreference {
    AUTO,
    EMBEDDED_WHISPER,
    VOSK_STREAMING,
    EXTERNAL_IME,
}

enum class VoiceRecognitionEngineRoute {
    EMBEDDED_WHISPER,
    VOSK_STREAMING,
    EXTERNAL_IME,
    UNAVAILABLE,
}

enum class VoiceRecognitionEngineRouteReason {
    AUTO_COMMAND_MODE_VOSK,
    AUTO_LOW_RAM_VOSK,
    AUTO_EMBEDDED_WHISPER,
    AUTO_VOSK_WHISPER_MISSING,
    EXPLICIT_EMBEDDED_WHISPER,
    EXPLICIT_VOSK_STREAMING,
    EXPLICIT_EXTERNAL_IME,
    FALLBACK_EXTERNAL_WHILE_LOCAL_UNAVAILABLE,
    LOCAL_RECOGNIZER_RUNTIME_UNAVAILABLE,
    LOCAL_MIC_PERMISSION_MISSING,
    EMBEDDED_WHISPER_MODEL_MISSING,
    VOSK_MODEL_MISSING,
    EXTERNAL_IME_NOT_READY,
    NO_VOICE_ENGINE_AVAILABLE,
}

object VoiceLocalRecognizerRuntime {
    const val AVAILABLE = false
}

data class VoiceRecognitionEngineAvailability(
    val hasEmbeddedWhisperModel: Boolean,
    val hasVoskStreamingModel: Boolean,
    val hasSwiftFlorisMicrophonePermission: Boolean,
    val externalVoiceInputReady: Boolean,
    val localRecognizerRuntimeAvailable: Boolean = VoiceLocalRecognizerRuntime.AVAILABLE,
)

data class VoiceRecognitionEngineRequest(
    val enginePreference: VoiceRecognitionEnginePreference,
    val modelPreference: VoiceModelPreference,
    val deviceRamProfile: VoiceDeviceRamProfile,
    val commandModeRequested: Boolean = false,
)

data class VoiceRecognitionEngineSelection(
    val route: VoiceRecognitionEngineRoute,
    val reason: VoiceRecognitionEngineRouteReason,
    val embeddedModelTier: VoiceModelTier,
) {
    val isLocal: Boolean
        get() = route == VoiceRecognitionEngineRoute.EMBEDDED_WHISPER ||
            route == VoiceRecognitionEngineRoute.VOSK_STREAMING
}

object VoiceRecognitionEngineSelector {
    fun select(
        request: VoiceRecognitionEngineRequest,
        availability: VoiceRecognitionEngineAvailability,
    ): VoiceRecognitionEngineSelection {
        val embeddedModelTier = request.modelPreference.resolve(request.deviceRamProfile)
        return when (request.enginePreference) {
            VoiceRecognitionEnginePreference.AUTO -> selectAuto(
                request = request,
                availability = availability,
                embeddedModelTier = embeddedModelTier,
            )
            VoiceRecognitionEnginePreference.EMBEDDED_WHISPER -> selectEmbeddedWhisper(
                availability = availability,
                embeddedModelTier = embeddedModelTier,
            )
            VoiceRecognitionEnginePreference.VOSK_STREAMING -> selectVoskStreaming(
                availability = availability,
                embeddedModelTier = embeddedModelTier,
            )
            VoiceRecognitionEnginePreference.EXTERNAL_IME -> selectExternalIme(
                availability = availability,
                embeddedModelTier = embeddedModelTier,
            )
        }
    }

    private fun selectAuto(
        request: VoiceRecognitionEngineRequest,
        availability: VoiceRecognitionEngineAvailability,
        embeddedModelTier: VoiceModelTier,
    ): VoiceRecognitionEngineSelection {
        if (request.commandModeRequested && availability.canUseVoskStreaming()) {
            return VoiceRecognitionEngineSelection(
                route = VoiceRecognitionEngineRoute.VOSK_STREAMING,
                reason = VoiceRecognitionEngineRouteReason.AUTO_COMMAND_MODE_VOSK,
                embeddedModelTier = embeddedModelTier,
            )
        }
        if (request.deviceRamProfile.prefersStreamingFallback() && availability.canUseVoskStreaming()) {
            return VoiceRecognitionEngineSelection(
                route = VoiceRecognitionEngineRoute.VOSK_STREAMING,
                reason = VoiceRecognitionEngineRouteReason.AUTO_LOW_RAM_VOSK,
                embeddedModelTier = embeddedModelTier,
            )
        }
        if (availability.canUseEmbeddedWhisper()) {
            return VoiceRecognitionEngineSelection(
                route = VoiceRecognitionEngineRoute.EMBEDDED_WHISPER,
                reason = VoiceRecognitionEngineRouteReason.AUTO_EMBEDDED_WHISPER,
                embeddedModelTier = embeddedModelTier,
            )
        }
        if (availability.canUseVoskStreaming()) {
            return VoiceRecognitionEngineSelection(
                route = VoiceRecognitionEngineRoute.VOSK_STREAMING,
                reason = VoiceRecognitionEngineRouteReason.AUTO_VOSK_WHISPER_MISSING,
                embeddedModelTier = embeddedModelTier,
            )
        }
        if (availability.externalVoiceInputReady) {
            return VoiceRecognitionEngineSelection(
                route = VoiceRecognitionEngineRoute.EXTERNAL_IME,
                reason = VoiceRecognitionEngineRouteReason.FALLBACK_EXTERNAL_WHILE_LOCAL_UNAVAILABLE,
                embeddedModelTier = embeddedModelTier,
            )
        }
        val reason = when {
            availability.hasInstalledLocalModel() && !availability.localRecognizerRuntimeAvailable ->
                VoiceRecognitionEngineRouteReason.LOCAL_RECOGNIZER_RUNTIME_UNAVAILABLE
            availability.hasEmbeddedWhisperModel || availability.hasVoskStreamingModel ->
                VoiceRecognitionEngineRouteReason.LOCAL_MIC_PERMISSION_MISSING
            request.commandModeRequested || request.deviceRamProfile.prefersStreamingFallback() ->
                VoiceRecognitionEngineRouteReason.VOSK_MODEL_MISSING
            else -> VoiceRecognitionEngineRouteReason.NO_VOICE_ENGINE_AVAILABLE
        }
        return VoiceRecognitionEngineSelection(
            route = VoiceRecognitionEngineRoute.UNAVAILABLE,
            reason = reason,
            embeddedModelTier = embeddedModelTier,
        )
    }

    private fun selectEmbeddedWhisper(
        availability: VoiceRecognitionEngineAvailability,
        embeddedModelTier: VoiceModelTier,
    ): VoiceRecognitionEngineSelection {
        val reason = when {
            !availability.hasEmbeddedWhisperModel ->
                VoiceRecognitionEngineRouteReason.EMBEDDED_WHISPER_MODEL_MISSING
            !availability.localRecognizerRuntimeAvailable ->
                VoiceRecognitionEngineRouteReason.LOCAL_RECOGNIZER_RUNTIME_UNAVAILABLE
            !availability.hasSwiftFlorisMicrophonePermission ->
                VoiceRecognitionEngineRouteReason.LOCAL_MIC_PERMISSION_MISSING
            else -> VoiceRecognitionEngineRouteReason.EXPLICIT_EMBEDDED_WHISPER
        }
        return VoiceRecognitionEngineSelection(
            route = if (reason == VoiceRecognitionEngineRouteReason.EXPLICIT_EMBEDDED_WHISPER) {
                VoiceRecognitionEngineRoute.EMBEDDED_WHISPER
            } else {
                VoiceRecognitionEngineRoute.UNAVAILABLE
            },
            reason = reason,
            embeddedModelTier = embeddedModelTier,
        )
    }

    private fun selectVoskStreaming(
        availability: VoiceRecognitionEngineAvailability,
        embeddedModelTier: VoiceModelTier,
    ): VoiceRecognitionEngineSelection {
        val reason = when {
            !availability.hasVoskStreamingModel -> VoiceRecognitionEngineRouteReason.VOSK_MODEL_MISSING
            !availability.localRecognizerRuntimeAvailable ->
                VoiceRecognitionEngineRouteReason.LOCAL_RECOGNIZER_RUNTIME_UNAVAILABLE
            !availability.hasSwiftFlorisMicrophonePermission ->
                VoiceRecognitionEngineRouteReason.LOCAL_MIC_PERMISSION_MISSING
            else -> VoiceRecognitionEngineRouteReason.EXPLICIT_VOSK_STREAMING
        }
        return VoiceRecognitionEngineSelection(
            route = if (reason == VoiceRecognitionEngineRouteReason.EXPLICIT_VOSK_STREAMING) {
                VoiceRecognitionEngineRoute.VOSK_STREAMING
            } else {
                VoiceRecognitionEngineRoute.UNAVAILABLE
            },
            reason = reason,
            embeddedModelTier = embeddedModelTier,
        )
    }

    private fun selectExternalIme(
        availability: VoiceRecognitionEngineAvailability,
        embeddedModelTier: VoiceModelTier,
    ): VoiceRecognitionEngineSelection {
        return if (availability.externalVoiceInputReady) {
            VoiceRecognitionEngineSelection(
                route = VoiceRecognitionEngineRoute.EXTERNAL_IME,
                reason = VoiceRecognitionEngineRouteReason.EXPLICIT_EXTERNAL_IME,
                embeddedModelTier = embeddedModelTier,
            )
        } else {
            VoiceRecognitionEngineSelection(
                route = VoiceRecognitionEngineRoute.UNAVAILABLE,
                reason = VoiceRecognitionEngineRouteReason.EXTERNAL_IME_NOT_READY,
                embeddedModelTier = embeddedModelTier,
            )
        }
    }

    private fun VoiceRecognitionEngineAvailability.canUseEmbeddedWhisper(): Boolean {
        return localRecognizerRuntimeAvailable &&
            hasEmbeddedWhisperModel &&
            hasSwiftFlorisMicrophonePermission
    }

    private fun VoiceRecognitionEngineAvailability.canUseVoskStreaming(): Boolean {
        return localRecognizerRuntimeAvailable &&
            hasVoskStreamingModel &&
            hasSwiftFlorisMicrophonePermission
    }

    private fun VoiceRecognitionEngineAvailability.hasInstalledLocalModel(): Boolean {
        return hasEmbeddedWhisperModel || hasVoskStreamingModel
    }

    private fun VoiceDeviceRamProfile.prefersStreamingFallback(): Boolean {
        return isLowRamDevice ||
            totalRamMb?.let { it < VoiceModelTier.BASE_EN.minimumRecommendedRamMb } == true
    }
}

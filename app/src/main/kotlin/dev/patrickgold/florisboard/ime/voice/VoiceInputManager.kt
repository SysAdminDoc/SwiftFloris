/*
 * Copyright (C) 2025 The FlorisBoard Contributors
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

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.view.inputmethod.InputMethodManager
import dev.patrickgold.florisboard.BuildConfig
import dev.patrickgold.florisboard.FlorisImeService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.florisboard.lib.android.systemServiceOrNull

/**
 * Delegates dictation to an external voice IME such as FUTO Voice Input.
 */
class VoiceInputManager(private val context: Context) {
    companion object {
        const val FUTO_PACKAGE_NAME = "org.futo.voiceinput"
        const val FUTO_FDROID_URL = "https://f-droid.org/packages/org.futo.voiceinput/"
        const val FUTO_RELEASES_URL = "https://github.com/FUTO-org/android-voice-input/releases"
    }

    private val _transcriptionState = MutableStateFlow<TranscriptionState>(TranscriptionState.Idle)
    val transcriptionState: StateFlow<TranscriptionState> = _transcriptionState

    private val _recognizedText = MutableStateFlow("")
    val recognizedText: StateFlow<String> = _recognizedText

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening

    private val _error = MutableStateFlow<VoiceError?>(null)
    val error: StateFlow<VoiceError?> = _error

    fun initialize() {
        refreshAvailability()
    }

    fun refreshAvailability(): Boolean {
        val available = isExternalVoiceInputMethodEnabled()
        _transcriptionState.value = if (available) TranscriptionState.Ready else TranscriptionState.Unavailable
        _error.value = if (available) null else VoiceError.NotAvailable
        return available
    }

    fun startListening(): Boolean {
        _recognizedText.value = ""
        _error.value = null
        _isListening.value = true
        _transcriptionState.value = TranscriptionState.Listening

        val switched = FlorisImeService.switchToVoiceInputMethod(showFailureToast = false)
        if (switched) {
            _isListening.value = false
            _transcriptionState.value = TranscriptionState.Ready
            return true
        }

        _isListening.value = false
        _transcriptionState.value = TranscriptionState.Unavailable
        _error.value = if (isFutoVoiceInputInstalled()) {
            VoiceError.NotEnabled
        } else {
            VoiceError.NotAvailable
        }
        return false
    }

    fun stopListening() {
        _isListening.value = false
        refreshAvailability()
    }

    fun cancel() {
        _isListening.value = false
        _recognizedText.value = ""
        refreshAvailability()
    }

    fun destroy() {
        _isListening.value = false
        _transcriptionState.value = TranscriptionState.Idle
    }

    fun isFutoVoiceInputInstalled(): Boolean {
        val packageManager = context.packageManager
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    FUTO_PACKAGE_NAME,
                    PackageManager.PackageInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(FUTO_PACKAGE_NAME, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun isExternalVoiceInputMethodEnabled(): Boolean {
        val imm = context.systemServiceOrNull(InputMethodManager::class) ?: return false
        return imm.enabledInputMethodList.any { inputMethod ->
            inputMethod.packageName != BuildConfig.APPLICATION_ID &&
                (0 until inputMethod.subtypeCount).any { index ->
                    inputMethod.getSubtypeAt(index).mode == "voice"
                }
        }
    }
}

/**
 * State of the external voice input handoff.
 */
enum class TranscriptionState {
    Idle,
    Ready,
    Listening,
    Processing,
    Error,
    Unavailable,
}

/**
 * Voice input handoff errors.
 */
sealed class VoiceError {
    object NotAvailable : VoiceError()
    object NotEnabled : VoiceError()
    object AudioError : VoiceError()
    object ClientError : VoiceError()
    object PermissionDenied : VoiceError()
    object NetworkError : VoiceError()
    object NetworkTimeout : VoiceError()
    object NoMatch : VoiceError()
    object RecognizerBusy : VoiceError()
    object ServerError : VoiceError()
    object SpeechTimeout : VoiceError()
    object NoResults : VoiceError()
    data class StartFailed(val message: String) : VoiceError()
    data class StopFailed(val message: String) : VoiceError()
    data class CancelFailed(val message: String) : VoiceError()
    data class UnknownError(val code: Int) : VoiceError()
}

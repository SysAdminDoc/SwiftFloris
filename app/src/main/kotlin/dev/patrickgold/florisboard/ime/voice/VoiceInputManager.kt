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

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.core.net.toUri
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

    private val commandParser = VoiceCommandParser()

    fun initialize() {
        refreshAvailability()
    }

    fun refreshAvailability(): Boolean {
        val available = isVoiceInputReadyForHandoff()
        _transcriptionState.value = if (available) TranscriptionState.Ready else TranscriptionState.Unavailable
        _error.value = if (available) null else resolveAvailabilityError()
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
        _error.value = resolveAvailabilityError()
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

    fun detectCommand(
        spokenText: String,
        minimumConfidence: Double = VoiceCommandParser.DEFAULT_MINIMUM_CONFIDENCE,
    ): VoiceCommandMatch? {
        return commandParser.parse(spokenText, minimumConfidence)
    }

    fun detectAndExecuteCommand(
        spokenText: String,
        actions: VoiceCommandActions,
        minimumConfidence: Double = VoiceCommandParser.DEFAULT_MINIMUM_CONFIDENCE,
    ): VoiceCommandExecutionResult? {
        val match = detectCommand(spokenText, minimumConfidence) ?: return null
        return VoiceCommandExecutor(actions).execute(match)
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
        return enabledVoiceInputMethodPackages().any { it != BuildConfig.APPLICATION_ID }
    }

    fun isVoiceInputReadyForHandoff(): Boolean {
        val enabledExternalPackages = enabledVoiceInputMethodPackages().filter { it != BuildConfig.APPLICATION_ID }
        return enabledExternalPackages.any { it != FUTO_PACKAGE_NAME } ||
            (FUTO_PACKAGE_NAME in enabledExternalPackages && isFutoMicrophonePermissionGranted())
    }

    fun isFutoVoiceInputEnabled(): Boolean {
        return FUTO_PACKAGE_NAME in enabledVoiceInputMethodPackages()
    }

    fun isFutoMicrophonePermissionGranted(): Boolean {
        if (!isFutoVoiceInputInstalled()) {
            return false
        }
        return try {
            context.packageManager.checkPermission(
                android.Manifest.permission.RECORD_AUDIO,
                FUTO_PACKAGE_NAME,
            ) == PackageManager.PERMISSION_GRANTED
        } catch (_: RuntimeException) {
            false
        }
    }

    fun launchFutoVoiceInputApp(): Boolean {
        val intent = context.packageManager
            .getLaunchIntentForPackage(FUTO_PACKAGE_NAME)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: return false
        return launchActivity(intent)
    }

    fun launchFutoAppInfoSettings(): Boolean {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData("package:$FUTO_PACKAGE_NAME".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return launchActivity(intent)
    }

    private fun launchActivity(intent: Intent): Boolean {
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    fun resolveSetupReason(): VoiceInputSetupReason {
        return when {
            isVoiceInputReadyForHandoff() -> VoiceInputSetupReason.READY
            !isFutoVoiceInputInstalled() -> VoiceInputSetupReason.FUTO_NOT_INSTALLED
            isFutoVoiceInputEnabled() && !isFutoMicrophonePermissionGranted() ->
                VoiceInputSetupReason.FUTO_MIC_PERMISSION_DENIED
            else -> VoiceInputSetupReason.FUTO_NOT_ENABLED
        }
    }

    fun showSetupDialog(reason: VoiceInputSetupReason = resolveSetupReason()): Boolean {
        return VoiceInputSetupActivity.launch(context, reason)
    }

    private fun resolveAvailabilityError(): VoiceError {
        return when (resolveSetupReason()) {
            VoiceInputSetupReason.READY -> VoiceError.NotAvailable
            VoiceInputSetupReason.FUTO_NOT_ENABLED -> VoiceError.NotEnabled
            VoiceInputSetupReason.FUTO_MIC_PERMISSION_DENIED -> VoiceError.PermissionDenied
            VoiceInputSetupReason.FUTO_NOT_INSTALLED,
            VoiceInputSetupReason.NO_ENABLED_PROVIDER,
            -> VoiceError.NotAvailable
        }
    }

    private fun enabledVoiceInputMethodPackages(): Set<String> {
        val imm = context.systemServiceOrNull(InputMethodManager::class) ?: return emptySet()
        return imm.enabledInputMethodList.mapNotNullTo(mutableSetOf()) { inputMethod ->
            inputMethod.packageName.takeIf {
                (0 until inputMethod.subtypeCount).any { index ->
                    inputMethod.getSubtypeAt(index).mode == "voice"
                }
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

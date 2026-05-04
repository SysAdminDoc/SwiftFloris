package dev.patrickgold.florisboard.ime.voice

import android.content.Context
import android.content.Intent
import android.content.ActivityNotFoundException
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Delegates voice input to FUTO Voice Input app.
 * FUTO Voice Input must be installed separately.
 * Integrates via IME voice subtype mode.
 */
class VoiceInputManager(private val context: Context) {

    private val _transcriptionState = MutableStateFlow<TranscriptionState>(TranscriptionState.Idle)
    val transcriptionState: StateFlow<TranscriptionState> = _transcriptionState

    private val _recognizedText = MutableStateFlow<String>("")
    val recognizedText: StateFlow<String> = _recognizedText

    private val _isListening = MutableStateFlow<Boolean>(false)
    val isListening: StateFlow<Boolean> = _isListening

    private val _error = MutableStateFlow<VoiceError?>(null)
    val error: StateFlow<VoiceError?> = _error

    fun initialize() {
        // Check if FUTO Voice Input is available
        if (isFutoVoiceInputAvailable()) {
            _transcriptionState.value = TranscriptionState.Ready
        } else {
            _transcriptionState.value = TranscriptionState.Unavailable
            _error.value = VoiceError.NotAvailable
            Log.w("VoiceInputManager", "FUTO Voice Input not installed. Please install it from Play Store or F-Droid.")
        }
    }

    fun startListening() {
        if (_transcriptionState.value == TranscriptionState.Unavailable) {
            _error.value = VoiceError.NotAvailable
            return
        }

        try {
            // Check once more before launching to handle edge case where FUTO was uninstalled
            if (!isFutoVoiceInputAvailable()) {
                _transcriptionState.value = TranscriptionState.Unavailable
                _error.value = VoiceError.NotAvailable
                Log.w("VoiceInputManager", "FUTO Voice Input became unavailable (was uninstalled?)")
                return
            }

            _isListening.value = true
            _transcriptionState.value = TranscriptionState.Listening
            _error.value = null  // Clear any previous errors
            
            // Launch FUTO Voice Input via IME subtype mode
            // This opens voice input in the bottom half of the keyboard
            val intent = Intent("android.intent.action.VIEW").apply {
                setPackage("org.futo.voiceinput")
                // No additional extras needed for IME mode
            }
            
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            _transcriptionState.value = TranscriptionState.Unavailable
            _isListening.value = false
            _error.value = VoiceError.NotAvailable
            Log.e("VoiceInputManager", "FUTO Voice Input activity not found", e)
        } catch (e: SecurityException) {
            _transcriptionState.value = TranscriptionState.Error
            _isListening.value = false
            _error.value = VoiceError.StartFailed("Permission denied to start voice input")
            Log.e("VoiceInputManager", "Permission denied to start voice input", e)
        } catch (e: Exception) {
            _transcriptionState.value = TranscriptionState.Error
            _isListening.value = false
            val errorMsg = when {
                e.message?.contains("Context", ignoreCase = true) == true -> "Context error: voice input unavailable"
                !e.message.isNullOrEmpty() -> e.message!!
                else -> "Failed to launch voice input"
            }
            _error.value = VoiceError.StartFailed(errorMsg)
            Log.e("VoiceInputManager", "Failed to start listening: $errorMsg", e)
        }
    }

    fun stopListening() {
        _isListening.value = false
        _transcriptionState.value = TranscriptionState.Ready
    }

    fun cancel() {
        _isListening.value = false
        _transcriptionState.value = TranscriptionState.Ready
        _recognizedText.value = ""
    }

    fun destroy() {
        _isListening.value = false
        _transcriptionState.value = TranscriptionState.Idle
    }

    private fun isFutoVoiceInputAvailable(): Boolean {
        return try {
            val intent = Intent("android.intent.action.VIEW").apply {
                setPackage("org.futo.voiceinput")
            }
            val activities = context.packageManager.queryIntentActivities(intent, 0)
            activities.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * State of the voice transcription process
 */
enum class TranscriptionState {
    Idle,           // Not initialized
    Ready,          // Ready to accept input
    Listening,      // Currently recording audio
    Processing,     // Processing recorded audio
    Error,          // Error occurred
    Unavailable     // Voice input not available
}

/**
 * Voice input errors
 */
sealed class VoiceError {
    object NotAvailable : VoiceError()
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


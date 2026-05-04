package dev.patrickgold.florisboard.ime.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/**
 * Manages voice input for SwiftFloris using Android's built-in Speech Recognizer.
 *
 * Features:
 * - No API key required (uses system recognizer)
 * - Works offline on most Android 12+ devices
 * - Supports all configured languages
 * - Real-time transcription with confidence scores
 * - Performance profiling for latency optimization (v1.4.1+)
 */
class VoiceInputManager(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null
    
    private val _transcriptionState = MutableStateFlow<TranscriptionState>(TranscriptionState.Idle)
    val transcriptionState: StateFlow<TranscriptionState> = _transcriptionState

    private val _recognizedText = MutableStateFlow<String>("")
    val recognizedText: StateFlow<String> = _recognizedText

    private val _confidence = MutableStateFlow<Float>(0f)
    val confidence: StateFlow<Float> = _confidence

    private val _isListening = MutableStateFlow<Boolean>(false)
    val isListening: StateFlow<Boolean> = _isListening

    private val _error = MutableStateFlow<VoiceError?>(null)
    val error: StateFlow<VoiceError?> = _error

    private var currentLocale: Locale = Locale.US
    
    // Profiling fields (for latency measurement in v1.4.1+)
    private var recognitionStartTime: Long = 0
    private var partialResultTime: Long = 0
    private var resultReceiveTime: Long = 0
    
    fun initialize() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(RecognitionListenerImpl())
            _transcriptionState.value = TranscriptionState.Ready
        } else {
            _transcriptionState.value = TranscriptionState.Unavailable
            _error.value = VoiceError.NotAvailable
        }
    }

    fun setLocale(locale: Locale) {
        currentLocale = locale
    }

    fun startListening() {
        if (_transcriptionState.value == TranscriptionState.Unavailable) {
            _error.value = VoiceError.NotAvailable
            return
        }

        if (speechRecognizer == null) {
            initialize()
        }

        try {
            recognitionStartTime = System.currentTimeMillis()
            Log.d("VoiceProfiler", "Recognition started at $recognitionStartTime")
            
            _transcriptionState.value = TranscriptionState.Listening
            _isListening.value = true
            _recognizedText.value = ""
            _confidence.value = 0f
            _error.value = null

            val intent = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, currentLocale.toLanguageTag())
                putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                // Increase partial results for real-time feedback
                putExtra(android.speech.RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000)
                // 3 second silence timeout
            }

            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            _transcriptionState.value = TranscriptionState.Error
            _isListening.value = false
            _error.value = VoiceError.StartFailed(e.message ?: "Unknown error")
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            _isListening.value = false
        } catch (e: Exception) {
            _error.value = VoiceError.StopFailed(e.message ?: "Failed to stop listening")
        }
    }

    fun cancel() {
        try {
            speechRecognizer?.cancel()
            _transcriptionState.value = TranscriptionState.Ready
            _isListening.value = false
            _recognizedText.value = ""
        } catch (e: Exception) {
            _error.value = VoiceError.CancelFailed(e.message ?: "Failed to cancel")
        }
    }

    fun destroy() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
            _transcriptionState.value = TranscriptionState.Idle
        } catch (e: Exception) {
            // Suppress errors during cleanup
        }
    }

    /**
     * Internal recognition listener implementation
     */
    private inner class RecognitionListenerImpl : RecognitionListener {

        override fun onReadyForSpeech(params: android.os.Bundle?) {
            Log.d("VoiceProfiler", "onReadyForSpeech called at ${System.currentTimeMillis() - recognitionStartTime}ms")
            _transcriptionState.value = TranscriptionState.Listening
        }

        override fun onBeginningOfSpeech() {
            Log.d("VoiceProfiler", "User started speaking at ${System.currentTimeMillis() - recognitionStartTime}ms")
            _transcriptionState.value = TranscriptionState.Processing
        }

        override fun onRmsChanged(rmsdB: Float) {
            // Could update a visualizer here
        }

        override fun onBufferReceived(buffer: ByteArray?) {
            // Buffer received from speech recognizer
        }

        override fun onEndOfSpeech() {
            Log.d("VoiceProfiler", "User stopped speaking at ${System.currentTimeMillis() - recognitionStartTime}ms")
            _transcriptionState.value = TranscriptionState.Processing
        }

        override fun onError(error: Int) {
            _isListening.value = false
            _transcriptionState.value = TranscriptionState.Error
            _error.value = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> VoiceError.AudioError
                SpeechRecognizer.ERROR_CLIENT -> VoiceError.ClientError
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> VoiceError.PermissionDenied
                SpeechRecognizer.ERROR_NETWORK -> VoiceError.NetworkError
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> VoiceError.NetworkTimeout
                SpeechRecognizer.ERROR_NO_MATCH -> VoiceError.NoMatch
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> VoiceError.RecognizerBusy
                SpeechRecognizer.ERROR_SERVER -> VoiceError.ServerError
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> VoiceError.SpeechTimeout
                else -> VoiceError.UnknownError(error)
            }
        }

        override fun onResults(results: android.os.Bundle?) {
            resultReceiveTime = System.currentTimeMillis()
            val latency = resultReceiveTime - recognitionStartTime
            Log.d("VoiceProfiler", "Results received. Total latency: ${latency}ms, Start: $recognitionStartTime, End: $resultReceiveTime")
            
            _isListening.value = false
            _transcriptionState.value = TranscriptionState.Ready

            if (results == null) {
                _error.value = VoiceError.NoResults
                return
            }

            val matches = results.getStringArray(SpeechRecognizer.RESULTS_RECOGNITION) ?: emptyArray()
            val confidences = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES) ?: floatArrayOf()

            if (matches.isNotEmpty()) {
                _recognizedText.value = matches[0]
                _confidence.value = if (confidences.isNotEmpty()) confidences[0] else 0f
                _error.value = null
                Log.d("VoiceProfiler", "Result text: '${matches[0]}' (confidence: ${_confidence.value})")
            } else {
                _error.value = VoiceError.NoResults
            }
        }

        override fun onPartialResults(partialResults: android.os.Bundle?) {
            // Handle partial results for real-time feedback
            if (partialResults == null) return

            partialResultTime = System.currentTimeMillis()
            val partialLatency = partialResultTime - recognitionStartTime
            Log.d("VoiceProfiler", "Partial result received at ${partialLatency}ms")
            
            val partialMatches = partialResults.getStringArray(SpeechRecognizer.RESULTS_RECOGNITION)
            if (partialMatches?.isNotEmpty() == true) {
                _recognizedText.value = partialMatches[0]
                Log.d("VoiceProfiler", "Partial text: '${partialMatches[0]}'")
            }
        }

        override fun onEvent(eventType: Int, params: android.os.Bundle?) {
            // Handle events (only in EXTRA_CALLING_PACKAGE mode)
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
    Unavailable     // Speech recognition not available on device
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

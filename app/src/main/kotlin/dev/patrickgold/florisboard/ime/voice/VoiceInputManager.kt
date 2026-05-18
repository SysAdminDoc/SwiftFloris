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
    private val fallbackHandler = VoiceCommandFallbackHandler(commandParser)

    /**
     * ROADMAP §7 Next-2.4 — streaming-transcript voice-command harness. Each
     * partial / final transcript chunk emitted by the embedded recognizer
     * (Whisper, Vosk, or external) flows through this buffer; when the buffer
     * detects a confident command match, the IME executes it immediately via
     * [VoiceCommandExecutor]. This is the SwiftKey "Smart Edit" voice-edit
     * surface ("change dog to cat") expressed as a stream consumer, so the
     * command fires the moment the user finishes saying it rather than
     * waiting for the recognizer's full-utterance final transcript.
     *
     * Streaming buffer state is per-session: callers re-create one via
     * [resetStreamingBuffer] when a new dictation session starts.
     */
    private var streamingBuffer = StreamingVoiceTranscriptBuffer(commandParser)

    fun resetStreamingBuffer() {
        streamingBuffer = StreamingVoiceTranscriptBuffer(commandParser)
    }

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
            // Previously: this branch immediately flipped `_isListening` back
            // to false and the transcription state back to Ready in the same
            // synchronous frame as the Listening assignment above. The Listening
            // event was sub-millisecond and no UI consumer ever observed it
            // (mic-meters, "connecting to voice IME…" spinners, recording-
            // indicator notifications all stayed at Ready). Concretely: any
            // composable doing `collectAsState()` on `transcriptionState`
            // skipped straight from Ready → Ready with no rendered Listening
            // frame.
            //
            // SwiftFloris's UI is hidden once the IME swap takes effect (FUTO
            // is now the foreground IME), but the state stays load-bearing for
            // (a) the brief pre-swap render window where SwiftFloris's UI is
            // still on-screen and consumers want to render the handoff
            // affordance, (b) the SwiftFloris UI that re-renders the moment
            // the user returns from FUTO. We keep Listening here; the return-
            // path is handled by `stopListening()` / `cancel()` / the next
            // `refreshAvailability()` call when SwiftFloris becomes active
            // again, all of which already exist.
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
        return commandParser.parse(spokenText = spokenText, minimumConfidence = minimumConfidence)
    }

    fun detectCommand(
        spokenText: String,
        customCommands: VoiceCommandCustomCommands,
        minimumConfidence: Double = VoiceCommandParser.DEFAULT_MINIMUM_CONFIDENCE,
    ): VoiceCommandMatch? {
        return commandParser.parse(
            spokenText = spokenText,
            customCommands = customCommands,
            minimumConfidence = minimumConfidence,
        )
    }

    fun detectAndExecuteCommand(
        spokenText: String,
        actions: VoiceCommandActions,
        minimumConfidence: Double = VoiceCommandParser.DEFAULT_MINIMUM_CONFIDENCE,
    ): VoiceCommandExecutionResult? {
        val match = detectCommand(spokenText, minimumConfidence) ?: return null
        return VoiceCommandExecutor(actions).execute(match)
    }

    fun detectAndExecuteCommand(
        spokenText: String,
        actions: VoiceCommandActions,
        customCommands: VoiceCommandCustomCommands,
        minimumConfidence: Double = VoiceCommandParser.DEFAULT_MINIMUM_CONFIDENCE,
    ): VoiceCommandExecutionResult? {
        val match = detectCommand(spokenText, customCommands, minimumConfidence) ?: return null
        return VoiceCommandExecutor(actions).execute(match)
    }

    /**
     * ROADMAP §7 Next-2.4 — feed a streaming-transcript chunk through the
     * per-session [StreamingVoiceTranscriptBuffer] and, when the buffer
     * surfaces a confident command match, execute it via
     * [VoiceCommandExecutor]. Returns the buffer's update so callers can
     * render the partial / committed transcript exactly as the user sees
     * it in the dictation overlay, plus any executed-command result so the
     * UI can show feedback ("Executed: undo word").
     *
     * Designed to be called per-chunk from the embedded recognizer (Whisper
     * / Vosk) or from the external-IME bridge's streaming handoff path,
     * not per-keystroke. Cheap (~one regex pass per chunk).
     */
    fun consumeStreamingChunk(
        chunk: VoiceTranscriptChunk,
        actions: VoiceCommandActions,
        customCommands: VoiceCommandCustomCommands = VoiceCommandCustomCommands.Empty,
        commandMode: Boolean = false,
        commandMinimumConfidence: Double = VoiceCommandParser.DEFAULT_MINIMUM_CONFIDENCE,
        suggestionMinimumConfidence: Double = VoiceCommandParser.DEFAULT_SUGGESTION_MINIMUM_CONFIDENCE,
    ): VoiceStreamingCommandUpdate {
        val bufferUpdate = streamingBuffer.accept(
            chunk = chunk,
            customCommands = customCommands,
            commandMode = commandMode,
            commandMinimumConfidence = commandMinimumConfidence,
            suggestionMinimumConfidence = suggestionMinimumConfidence,
        )
        // Auto-execute only on FINAL chunks so a partial transcript that
        // *might* be a command ("undo wo…") doesn't fire `undoWord` before
        // the user finished the utterance. Partials still surface as a
        // suggestion in the update payload so the UI can preview the
        // command before commit.
        val executed = if (chunk.isFinal && bufferUpdate.commandMatch != null) {
            VoiceCommandExecutor(actions).execute(bufferUpdate.commandMatch)
        } else {
            null
        }
        return VoiceStreamingCommandUpdate(
            transcript = bufferUpdate,
            executed = executed,
        )
    }

    fun handleTranscript(
        spokenText: String,
        actions: VoiceCommandActions,
        customCommands: VoiceCommandCustomCommands = VoiceCommandCustomCommands.Empty,
    ): VoiceCommandTranscriptResult {
        return fallbackHandler.handleTranscript(
            spokenText = spokenText,
            actions = actions,
            customCommands = customCommands,
        )
    }

    fun acceptSuggestedCommand(
        suggestion: VoiceCommandSuggestion,
        actions: VoiceCommandActions,
    ): VoiceCommandExecutionResult {
        return fallbackHandler.acceptSuggestion(suggestion, actions)
    }

    fun rejectSuggestedCommand(
        suggestion: VoiceCommandSuggestion,
        actions: VoiceCommandActions,
    ): VoiceCommandTranscriptResult.InsertedText {
        return fallbackHandler.rejectSuggestion(suggestion, actions)
    }

    fun handleError(error: VoiceError): VoiceCommandErrorHandlingResult {
        val result = fallbackHandler.handleError(error)
        _isListening.value = false
        _error.value = error
        _transcriptionState.value = result.transcriptionState
        if (result.recovery == VoiceCommandErrorRecovery.NO_TRANSCRIPT) {
            _recognizedText.value = ""
        }
        return result
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
        return enabledExternalVoiceInputMethodPackages().isNotEmpty()
    }

    fun isVoiceInputReadyForHandoff(): Boolean {
        return ExternalVoiceInputHandoffPolicy.isReadyForHandoff(
            enabledVoiceInputMethodPackages = enabledVoiceInputMethodPackages(),
            selfPackageName = BuildConfig.APPLICATION_ID,
            hasMicrophonePermission = ::isMicrophonePermissionGranted,
        )
    }

    fun isFutoVoiceInputEnabled(): Boolean {
        return FUTO_PACKAGE_NAME in enabledVoiceInputMethodPackages()
    }

    fun isFutoMicrophonePermissionGranted(): Boolean {
        if (!isFutoVoiceInputInstalled()) {
            return false
        }
        return isMicrophonePermissionGranted(FUTO_PACKAGE_NAME)
    }

    private fun isMicrophonePermissionGranted(packageName: String): Boolean {
        return try {
            context.packageManager.checkPermission(
                android.Manifest.permission.RECORD_AUDIO,
                packageName,
            ) == PackageManager.PERMISSION_GRANTED
        } catch (_: RuntimeException) {
            false
        }
    }

    fun isSwiftFlorisMicrophonePermissionGranted(): Boolean {
        return try {
            context.packageManager.checkPermission(
                android.Manifest.permission.RECORD_AUDIO,
                BuildConfig.APPLICATION_ID,
            ) == PackageManager.PERMISSION_GRANTED
        } catch (_: RuntimeException) {
            false
        }
    }

    fun resolveRecognitionEngineSelection(
        enginePreference: VoiceRecognitionEnginePreference,
        modelPreference: VoiceModelPreference,
        ramProfile: VoiceDeviceRamProfile = VoiceModelSelector.detectDeviceRamProfile(context),
        commandModeRequested: Boolean = false,
        hasEmbeddedWhisperModel: Boolean = false,
        hasVoskStreamingModel: Boolean = false,
        localRecognizerRuntimeAvailable: Boolean = VoiceLocalRecognizerRuntime.AVAILABLE,
    ): VoiceRecognitionEngineSelection {
        return VoiceRecognitionEngineSelector.select(
            request = VoiceRecognitionEngineRequest(
                enginePreference = enginePreference,
                modelPreference = modelPreference,
                deviceRamProfile = ramProfile,
                commandModeRequested = commandModeRequested,
            ),
            availability = VoiceRecognitionEngineAvailability(
                hasEmbeddedWhisperModel = hasEmbeddedWhisperModel,
                hasVoskStreamingModel = hasVoskStreamingModel,
                hasSwiftFlorisMicrophonePermission = isSwiftFlorisMicrophonePermissionGranted(),
                externalVoiceInputReady = isVoiceInputReadyForHandoff(),
                localRecognizerRuntimeAvailable = localRecognizerRuntimeAvailable,
            ),
        )
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
        val enabledExternalPackages = enabledExternalVoiceInputMethodPackages()
        return when {
            isVoiceInputReadyForHandoff() -> VoiceInputSetupReason.READY
            FUTO_PACKAGE_NAME in enabledExternalPackages && !isFutoMicrophonePermissionGranted() ->
                VoiceInputSetupReason.FUTO_MIC_PERMISSION_DENIED
            enabledExternalPackages.isNotEmpty() -> VoiceInputSetupReason.NO_ENABLED_PROVIDER
            !isFutoVoiceInputInstalled() -> VoiceInputSetupReason.FUTO_NOT_INSTALLED
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

    private fun enabledExternalVoiceInputMethodPackages(): Set<String> {
        return enabledVoiceInputMethodPackages().filterTo(mutableSetOf()) { it != BuildConfig.APPLICATION_ID }
    }
}

internal object ExternalVoiceInputHandoffPolicy {
    fun isReadyForHandoff(
        enabledVoiceInputMethodPackages: Set<String>,
        selfPackageName: String,
        hasMicrophonePermission: (String) -> Boolean,
    ): Boolean {
        return enabledVoiceInputMethodPackages
            .asSequence()
            .filter { it != selfPackageName }
            .any { hasMicrophonePermission(it) }
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

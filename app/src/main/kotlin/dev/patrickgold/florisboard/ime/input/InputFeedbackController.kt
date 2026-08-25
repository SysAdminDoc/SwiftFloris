/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.ime.input

import android.inputmethodservice.InputMethodService
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.runtime.staticCompositionLocalOf
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.keyboard.KeyData
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import org.florisboard.lib.android.AndroidVersion
import org.florisboard.lib.android.systemServiceOrNull
import org.florisboard.lib.android.systemVibratorOrNull
import org.florisboard.lib.android.vibrate
import dev.patrickgold.florisboard.lib.devtools.flogDebug
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val LocalInputFeedbackController = staticCompositionLocalOf<InputFeedbackController> { error("not init") }

/**
 * Input feedback controller is responsible to process and perform audio and haptic
 * feedback for user interactions based on the system and floris preferences.
 */
class InputFeedbackController private constructor(private val ims: InputMethodService) {
    companion object {
        fun new(ims: InputMethodService) = InputFeedbackController(ims)
    }

    private val prefs by FlorisPreferenceStore

    private val audioManager = ims.systemServiceOrNull(AudioManager::class)
    private val vibrator = ims.systemVibratorOrNull()
    private val contentResolver = ims.contentResolver
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val soundPool = SoundPool.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .setMaxStreams(KeypressSoundClass.entries.size)
        .build()
    // Loaded lazily and reloaded when the store says its files changed. This
    // was a val snapshotted at construction, and the controller lives as long as
    // the IME service, so a sound imported in Settings stayed inaudible and a
    // deleted one carried on playing from the handle already in the SoundPool.
    //
    // Each entry carries the file it was loaded from, so a change to one class
    // does not throw away the other three, and a readiness flag, because
    // SoundPool.load is asynchronous: playing an id that has not finished
    // decoding is silent. Until a sample reports ready the system effect is
    // used, which is what the user heard before they imported anything.
    private data class LoadedSound(
        val soundId: Int,
        val stamp: String,
        @Volatile var isReady: Boolean = false,
    )

    // Volatile because performAudioFeedback reads this from the coroutine it
    // launches, on a different thread from the synchronized reload.
    @Volatile
    private var customSounds: Map<KeypressSoundClass, LoadedSound> = emptyMap()
    private var loadedSoundRevision: Long = -1L

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            val loaded = customSounds.values.firstOrNull { it.soundId == sampleId } ?: return@setOnLoadCompleteListener
            loaded.isReady = status == 0
        }
    }

    /** Identity of the file behind a class, so an unchanged one is left alone. */
    private fun soundStamp(soundClass: KeypressSoundClass): String? {
        val file = KeypressSoundStore.file(ims, soundClass)
        if (!file.isFile) return null
        return "${file.lastModified()}:${file.length()}"
    }

    @Synchronized
    private fun syncCustomSounds() {
        val revision = KeypressSoundStore.revision.get()
        if (revision == loadedSoundRevision) return
        val previous = customSounds
        val next = HashMap<KeypressSoundClass, LoadedSound>(previous.size)
        for (soundClass in KeypressSoundClass.entries) {
            val stamp = soundStamp(soundClass)
            val existing = previous[soundClass]
            if (stamp != null && existing != null && existing.stamp == stamp) {
                // Same bytes as last time; keep the handle and its readiness.
                next[soundClass] = existing
                continue
            }
            if (existing != null) runCatching { soundPool.unload(existing.soundId) }
            if (stamp == null) continue
            val soundId = runCatching {
                soundPool.load(KeypressSoundStore.file(ims, soundClass).path, 1)
            }.getOrNull()
            if (soundId != null && soundId != 0) {
                next[soundClass] = LoadedSound(soundId = soundId, stamp = stamp)
            }
        }
        customSounds = next
        loadedSoundRevision = revision
    }

    private var systemAudioEnabled: Boolean = false
    private var systemHapticEnabled: Boolean = false

    fun updateSystemPrefsState() {
        systemAudioEnabled = systemPref(Settings.System.SOUND_EFFECTS_ENABLED)
        systemHapticEnabled = systemPref(Settings.System.HAPTIC_FEEDBACK_ENABLED)
    }

    fun keyPress(data: KeyData = TextKeyData.UNSPECIFIED) {
        if (prefs.inputFeedback.audioFeatKeyPress.get()) performAudioFeedback(data, 1.0)
        if (prefs.inputFeedback.hapticFeatKeyPress.get()) performHapticFeedback(data, 1.0)
    }

    fun keyLongPress(data: KeyData = TextKeyData.UNSPECIFIED) {
        if (prefs.inputFeedback.audioFeatKeyLongPress.get()) performAudioFeedback(data, 0.7)
        if (prefs.inputFeedback.hapticFeatKeyLongPress.get()) performHapticFeedback(data, 0.4)
    }

    fun keyRepeatedAction(data: KeyData = TextKeyData.UNSPECIFIED) {
        if (prefs.inputFeedback.audioFeatKeyRepeatedAction.get()) performAudioFeedback(data, 0.4)
        if (prefs.inputFeedback.hapticFeatKeyRepeatedAction.get()) performHapticFeedback(data, 0.05)
    }

    fun gestureSwipe(data: KeyData = TextKeyData.UNSPECIFIED) {
        if (prefs.inputFeedback.audioFeatGestureSwipe.get()) performAudioFeedback(data, 0.7)
        if (prefs.inputFeedback.hapticFeatGestureSwipe.get()) performHapticFeedback(data, 0.4)
    }

    fun gestureMovingSwipe(data: KeyData = TextKeyData.UNSPECIFIED) {
        if (prefs.inputFeedback.audioFeatGestureMovingSwipe.get()) performAudioFeedback(data, 0.4)
        if (prefs.inputFeedback.hapticFeatGestureMovingSwipe.get()) performHapticFeedback(data, 0.05)
    }

    /**
     * Cancels the internal feedback scope so any in-flight audio/haptic coroutines
     * stop holding references to the InputMethodService (and transitively its
     * decorView). Must be invoked from FlorisImeService.onDestroy — without this
     * the SupervisorJob outlives the service and leaks the window decorView for
     * the duration of any queued playSoundEffect / vibrate calls.
     */
    fun dispose() {
        scope.cancel()
        soundPool.release()
    }

    private fun systemPref(id: String): Boolean {
        if (contentResolver == null) return false
        return Settings.System.getInt(contentResolver, id, 0) != 0
    }

    private fun performAudioFeedback(data: KeyData, factor: Double) {
        // A volatile long read on the keypress path, and a reload only when it
        // moved. Cheap enough to sit here, which is what lets an import made
        // while the keyboard is on screen be audible on the next key.
        syncCustomSounds()
        if (audioManager == null && customSounds.isEmpty()) return
        if (!prefs.inputFeedback.audioEnabled.get()) return
        if (prefs.inputFeedback.audioActivationMode.get() ==
            InputFeedbackActivationMode.RESPECT_SYSTEM_SETTINGS && !systemAudioEnabled) return

        scope.launch {
            val volume = (prefs.inputFeedback.audioVolume.get() * factor) / 100.0
            if (volume in 0.01..1.00) {
                val soundClass = KeypressSoundClass.fromKeyCode(data.code)
                // Only once the sample has finished decoding. Playing an id
                // that has not is silent, and silence is worse than the system
                // effect the user had before.
                val customSoundId = customSounds[soundClass]?.takeIf { it.isReady }?.soundId
                if (customSoundId != null) {
                    flogDebug { "Perform custom audio with volume=$volume and class=$soundClass" }
                    soundPool.play(
                        customSoundId,
                        volume.toFloat(),
                        volume.toFloat(),
                        1,
                        0,
                        1.0f,
                    )
                } else {
                    val effect = when (soundClass) {
                        KeypressSoundClass.DELETE -> AudioManager.FX_KEYPRESS_DELETE
                        KeypressSoundClass.RETURN -> AudioManager.FX_KEYPRESS_RETURN
                        KeypressSoundClass.SPACEBAR -> AudioManager.FX_KEYPRESS_SPACEBAR
                        KeypressSoundClass.STANDARD -> AudioManager.FX_KEYPRESS_STANDARD
                    }
                    flogDebug { "Perform audio with volume=$volume and effect=$effect" }
                    audioManager?.playSoundEffect(effect, volume.toFloat())
                }
            }
        }
    }

    private fun performHapticFeedback(data: KeyData, factor: Double) {
        if (vibrator == null) return
        if (!prefs.inputFeedback.hapticEnabled.get()) return
        if (prefs.inputFeedback.hapticActivationMode.get() ==
            InputFeedbackActivationMode.RESPECT_SYSTEM_SETTINGS && !systemHapticEnabled) return

        scope.launch {
            if (prefs.inputFeedback.hapticVibrationMode.get() == HapticVibrationMode.USE_HAPTIC_FEEDBACK_INTERFACE) {
                val didPerform = withContext(Dispatchers.Main) {
                    val view = ims.window?.window?.decorView ?: return@withContext false
                    val hfc = if (factor < 1.0 && AndroidVersion.ATLEAST_API27_O_MR1) {
                        HapticFeedbackConstants.TEXT_HANDLE_MOVE
                    } else {
                        HapticFeedbackConstants.KEYBOARD_TAP
                    }
                    view.performHapticFeedback(hfc,
                        HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING or
                            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                    )
                }
                if (didPerform) return@launch
            }

            vibrator.vibrate(
                duration = prefs.inputFeedback.hapticVibrationDuration.get(),
                strength = prefs.inputFeedback.hapticVibrationStrength.get(),
                factor = factor,
            )
        }
    }
}

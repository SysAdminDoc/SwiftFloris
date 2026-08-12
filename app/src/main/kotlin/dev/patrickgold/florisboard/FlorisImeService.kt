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

package dev.patrickgold.florisboard

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.hardware.input.InputManager
import android.inputmethodservice.ExtractEditText
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import android.os.SystemClock
import android.os.Trace
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InlineSuggestionsRequest
import android.view.inputmethod.InlineSuggestionsResponse
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import android.widget.inline.InlinePresentationSpec
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import dev.patrickgold.florisboard.app.FlorisAppActivity
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.settings.about.SigningFingerprint
import dev.patrickgold.florisboard.app.settings.advanced.HardwareKeyboardDeviceOption
import dev.patrickgold.florisboard.app.settings.advanced.PhysicalKeyboardPolicy
import dev.patrickgold.florisboard.ime.addon.AddonEnumerator
import dev.patrickgold.florisboard.ime.addon.AddonRegistryStartup
import dev.patrickgold.florisboard.ime.addon.AddonRegistryStore
import dev.patrickgold.florisboard.ime.editor.EditorRange
import dev.patrickgold.florisboard.ime.editor.FlorisEditorInfo
import dev.patrickgold.florisboard.ime.editor.InputAttributes
import dev.patrickgold.florisboard.ime.input.InputFeedbackController
import dev.patrickgold.florisboard.ime.keyboard.isFullscreenInputRequired
import dev.patrickgold.florisboard.ime.landscapeinput.ExtractedInputRootView
import dev.patrickgold.florisboard.ime.landscapeinput.LandscapeInputUiMode
import dev.patrickgold.florisboard.ime.lifecycle.LifecycleInputMethodService
import dev.patrickgold.florisboard.ime.nlp.InlineSuggestionSizePolicy
import dev.patrickgold.florisboard.ime.nlp.NlpInlineAutofill
import dev.patrickgold.florisboard.ime.security.FlagSecurePolicy
import dev.patrickgold.florisboard.ime.text.gestures.GlideTypingCapability
import dev.patrickgold.florisboard.ime.text.key.KeyVariation
import dev.patrickgold.florisboard.ime.theme.WallpaperChangeReceiver
import dev.patrickgold.florisboard.ime.voice.VoiceInputManager
import dev.patrickgold.florisboard.ime.voice.VoiceInputSetupReason
import dev.patrickgold.florisboard.ime.window.ImeRootView
import dev.patrickgold.florisboard.ime.window.ImeVisibilityConfigurationPolicy
import dev.patrickgold.florisboard.ime.window.ImeWindowController
import dev.patrickgold.florisboard.lib.devtools.LogTopic
import dev.patrickgold.florisboard.lib.devtools.flogError
import dev.patrickgold.florisboard.lib.devtools.flogInfo
import dev.patrickgold.florisboard.lib.devtools.flogWarning
import dev.patrickgold.florisboard.lib.util.InputMethodUtils
import dev.patrickgold.florisboard.lib.util.debugSummarize
import dev.patrickgold.florisboard.lib.util.debugSummarizeTextForLog
import dev.patrickgold.florisboard.lib.util.launchActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.florisboard.lib.android.AndroidInternalR
import org.florisboard.lib.android.AndroidVersion
import org.florisboard.lib.android.postShortToast
import org.florisboard.lib.android.systemServiceOrNull
import org.florisboard.lib.kotlin.collectIn
import org.florisboard.lib.kotlin.collectLatestIn
import java.lang.ref.WeakReference

/**
 * Global weak reference for the [FlorisImeService] class. This is needed as certain actions (request hide, switch to
 * another input method, getting the editor instance / input connection, etc.) can only be performed by an IME
 * service class and no context-bound managers. This reference is exclusively used by the companion helper methods
 * of [FlorisImeService], which provide a safe and memory-leak-free way of performing certain actions on the Floris
 * input method service instance.
 */
private var FlorisImeServiceReference = WeakReference<FlorisImeService?>(null)

/**
 * Core class responsible for linking together all managers and UI composables to provide an IME service. Sets
 * up the window and context to be lifecycle-aware, so LiveData and Jetpack Compose can be used without issues.
 */
class FlorisImeService : LifecycleInputMethodService() {
    companion object {
        fun currentInputConnection(): InputConnection? {
            return FlorisImeServiceReference.get()?.currentInputConnection
        }

        fun inputFeedbackController(): InputFeedbackController? {
            return FlorisImeServiceReference.get()?.inputFeedbackController
        }

        /**
         * Hides the IME and launches [FlorisAppActivity].
         */
        fun launchSettings() {
            val ims = FlorisImeServiceReference.get() ?: return
            ims.requestHideSelf(0)
            ims.launchActivity(FlorisAppActivity::class) {
                it.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }

        fun showUi() {
            val ims = FlorisImeServiceReference.get() ?: return
            ims.showUi()
        }

        fun hideUi() {
            val ims = FlorisImeServiceReference.get() ?: return
            ims.hideUi()
        }

        fun switchToPrevInputMethod(): Boolean {
            val ims = FlorisImeServiceReference.get() ?: return false
            return ims.switchToPrevInputMethod()
        }

        fun switchToNextInputMethod(): Boolean {
            val ims = FlorisImeServiceReference.get() ?: return false
            return ims.switchToNextInputMethod()
        }

        fun switchToVoiceInputMethod(showFailureToast: Boolean = true): Boolean {
            val ims = FlorisImeServiceReference.get() ?: return false
            return ims.switchToVoiceInputMethod(showFailureToast)
        }

        fun showImePicker(): Boolean {
            val ims = FlorisImeServiceReference.get() ?: return false
            return InputMethodUtils.showImePicker(ims)
        }

        fun windowControllerOrNull(): ImeWindowController? {
            val ims = FlorisImeServiceReference.get() ?: return null
            return ims.windowController
        }

        fun voiceInputManagerOrNull(): VoiceInputManager? {
            val ims = FlorisImeServiceReference.get() ?: return null
            return ims.voiceInputManager
        }
    }

    fun hideUi() {
        requestHideSelf(0)
    }

    /**
     * Show the Ime UI
     *
     * Note: This function can be replaced with a `requestShowSelf(0)`
     * call once we've set the minApiLevel to 28 (Android 9)
     */
    fun showUi() {
        if (AndroidVersion.ATLEAST_API28_P) {
            requestShowSelf(0)
        } else {
            @Suppress("DEPRECATION")
            systemServiceOrNull(InputMethodManager::class)
                ?.showSoftInputFromInputMethod(currentInputBinding.connectionToken, 0)
        }
    }


    /**
     * Switch to previous input method
     *
     * Note: This function can be replaced with a `switchToPreviousInputMethod()`
     * call once we've set the minApiLevel to 28 (Android 9)
     *
     * @return true if the switch was successful
     */
    fun switchToPrevInputMethod(): Boolean {
        val imm = systemServiceOrNull(InputMethodManager::class)
        try {
            if (AndroidVersion.ATLEAST_API28_P) {
                return switchToPreviousInputMethod()
            } else {
                window.window?.let { window ->
                    @Suppress("DEPRECATION")
                    return imm?.switchToLastInputMethod(window.attributes.token) == true
                }
            }
        } catch (e: Exception) {
            flogError { "Unable to switch to the previous IME" }
            imm?.showInputMethodPicker()
        }
        return false
    }

    /**
     * Switch to next input method
     *
     * Note: This function can be replaced with a `switchToNextInputMethod(false)`
     * call once we've set the minApiLevel to 28 (Android 9)
     *
     * @return true if the switch was successful
     */
    fun switchToNextInputMethod(): Boolean {
        val imm = systemServiceOrNull(InputMethodManager::class)
        try {
            if (AndroidVersion.ATLEAST_API28_P) {
                return switchToNextInputMethod(false)
            } else {
                window.window?.let { window ->
                    @Suppress("DEPRECATION")
                    return imm?.switchToNextInputMethod(window.attributes.token, false) == true
                }
            }
        } catch (e: Exception) {
            flogError { "Unable to switch to the next IME" }
            imm?.showInputMethodPicker()
        }
        return false
    }

    /**
     * Switch to an enabled external voice input method.
     *
     * Note: The inner part of this function can be replaced with a
     *
     * `switchInputMethod(el.id, el.getSubtypeAt(i))` call once we've set the minApiLevel to 28 (Android 9)
     *
     * @return true if the switch was successful
     */
    fun switchToVoiceInputMethod(showFailureToast: Boolean = true): Boolean {
        // ROADMAP §6 N7.2 (extension) — refuse to hand off to an external
        // voice IME when the focused field is sensitive. Mirrors the
        // existing dictionary-learn, clipboard-cut/copy (v1.8.86 +
        // v1.8.105), and smart-compose gates. Without this, a user
        // tapping the voice key while in a password / numeric-PIN / web-
        // password field would route their spoken credential through an
        // external recogniser process that the IME's no-`INTERNET`
        // contract does NOT extend to — voice IMEs typically have full
        // network permission. The host app's sensitive-field declaration
        // is the load-bearing privacy signal here; honour it.
        val state = keyboardManager.activeState
        if (state.keyVariation == KeyVariation.PASSWORD ||
            state.isIncognitoMode
        ) {
            if (showFailureToast) {
                postShortToast(R.string.voice_input__suppressed_on_sensitive_field)
            }
            return false
        }
        val imm = systemServiceOrNull(InputMethodManager::class) ?: return false
        val candidates = mutableListOf<Pair<InputMethodInfo, InputMethodSubtype>>()
        for (el in imm.enabledInputMethodList) {
            if (el.packageName == BuildConfig.APPLICATION_ID) continue
            for (i in 0 until el.subtypeCount) {
                // Check if the subtype is a voice input method.
                // We need to hardcode 'voice' here because the SUBTYPE_MODE_VOICE constant is private.
                // https://cs.android.com/android/platform/superproject/+/android-latest-release:frameworks/base/core/java/android/view/inputmethod/InputMethodManager.java;drc=2b278ab3ac73bb5596327aac1298df85cd94e454;l=309
                if (el.getSubtypeAt(i).mode != "voice") continue
                candidates.add(el to el.getSubtypeAt(i))
            }
        }

        val futoCandidate = candidates.firstOrNull { (method, _) ->
            method.packageName == VoiceInputManager.FUTO_PACKAGE_NAME
        }
        val fallbackCandidate = candidates.firstOrNull { (method, _) ->
            method.packageName != VoiceInputManager.FUTO_PACKAGE_NAME
        }
        val (method, subtype) = when {
            futoCandidate != null && voiceInputManager.isFutoMicrophonePermissionGranted() -> futoCandidate
            fallbackCandidate != null -> fallbackCandidate
            futoCandidate != null -> {
                if (showFailureToast) {
                    val shown = voiceInputManager.showSetupDialog(VoiceInputSetupReason.FUTO_MIC_PERMISSION_DENIED)
                    if (!shown) {
                        postShortToast(R.string.voice_input_setup__open_failed)
                    }
                }
                return false
            }
            else -> {
                if (showFailureToast) {
                    val shown = voiceInputManager.showSetupDialog()
                    if (!shown) {
                        postShortToast(R.string.voice_input_setup__open_failed)
                    }
                }
                return false
            }
        }

        if (AndroidVersion.ATLEAST_API28_P) {
            switchInputMethod(method.id, subtype)
            return true
        } else {
            // Pre-API28: a null window token previously fell through to `return false`
            // silently — a candidate voice IME was selected but the user got no
            // feedback and nothing happened. Surface the IME picker (the same fallback
            // switchToPrev/NextInputMethod use) instead of a silent no-op.
            val token = window.window?.attributes?.token
            if (token != null) {
                @Suppress("DEPRECATION")
                imm.setInputMethod(token, method.id)
                return true
            }
            if (showFailureToast) {
                imm.showInputMethodPicker()
            }
            return false
        }
    }

    private val prefs by FlorisPreferenceStore
    val editorInstance by editorInstance()
    private val keyboardManager by keyboardManager()
    private val nlpManager by nlpManager()
    private val subtypeManager by subtypeManager()
    private val themeManager by themeManager()
    val perAppAccentController by perAppAccentController()
    val voiceInputManager by lazy { VoiceInputManager(this) }

    val windowController = ImeWindowController(prefs, lifecycleScope)

    private val activeState get() = keyboardManager.activeState
    val inputFeedbackController by lazy { InputFeedbackController.new(this) }
    private val systemLocalesFlow = MutableStateFlow(LocaleList())
    var resourcesContext by mutableStateOf(this as Context)
        private set

    private val wallpaperChangeReceiver = WallpaperChangeReceiver()
    private var wallpaperReceiverRegistered = false
    private var flagSecureEditorInfo: FlorisEditorInfo? = null
    private var lastPhysicalKeyboardVisibilitySummary: String? = null
    private val flagSecureIncognitoModeChangedListener: (Boolean) -> Unit = {
        reapplyFlagSecureForCurrentField()
    }

    /** ROADMAP §10.5 L7.5b — MCP daemon bridge lifecycle, owned by the IME service. */
    private var mcpLifecycle: dev.patrickgold.florisboard.ime.mcp.McpServiceLifecycle? = null

    init {
        setTheme(R.style.FlorisImeTheme)
    }

    override fun onCreate() {
        super.onCreate()
        FlorisImeServiceReference = WeakReference(this)
        systemLocalesFlow.value = resources.configuration.locales
        keyboardManager.setIncognitoModeChangedListener(flagSecureIncognitoModeChangedListener)
        // A previous session may have disabled glide typing after an allocation failure. Each new
        // IME session gets a clean attempt; the device's low-RAM verdict is unaffected.
        GlideTypingCapability.resetForNewSession()

        // Initialize voice input manager
        voiceInputManager.initialize()

        window.window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }
        windowController.onConfigurationChanged(resources.configuration)
        windowController.activeWindowConfig.collectLatestIn(lifecycleScope) {
            keyboardManager.updateActiveEvaluators() // TODO: wacky solution, but works for now
        }

        combine(
            systemLocalesFlow,
            subtypeManager.activeSubtypeFlow,
            prefs.localization.displayKeyboardLabelsInSubtypeLanguage.asFlow(),
        ) { systemLocales, subtype, shouldUseSubtypeLanguage ->
            systemLocales to (if (shouldUseSubtypeLanguage) subtype.primaryLocale else null)
        }.distinctUntilChanged().collectIn(lifecycleScope) { (systemLocales, subtypeLocale) ->
            val config = Configuration().apply {
                setToDefaults()
                if (subtypeLocale != null) {
                    setLocale(subtypeLocale.base)
                } else {
                    setLocales(systemLocales)
                }
            }
            resourcesContext = createConfigurationContext(config)
        }

        prefs.physicalKeyboard.showOnScreenKeyboard.asFlow().collectIn(lifecycleScope) {
            updateInputViewShown()
        }
        prefs.physicalKeyboard.showSmartbarOnly.asFlow().collectIn(lifecycleScope) {
            updateInputViewShown()
        }

        @Suppress("DEPRECATION") // We do not retrieve the wallpaper but only listen to changes
        try {
            registerReceiver(wallpaperChangeReceiver, IntentFilter(Intent.ACTION_WALLPAPER_CHANGED))
            wallpaperReceiverRegistered = true
        } catch (e: Exception) {
            flogWarning(LogTopic.IMS_EVENTS) { "Failed to register wallpaper change receiver: $e" }
        }

        // MCP dispatch has no live keyboard caller yet. Keep the process-wide
        // registries empty so merely starting the keyboard cannot discover or
        // bind a daemon that no audited production action can reach. The
        // dispatch router remains available behind NlpAddonHub for the future
        // action that will make this lifecycle safe to re-enable.
        mcpLifecycle = null
        dev.patrickgold.florisboard.ime.mcp.McpDaemonRegistry.setActive(emptyMap())
        dev.patrickgold.florisboard.ime.mcp.McpClientRegistry.setActive(
            dev.patrickgold.florisboard.ime.mcp.NoOpMcpClient,
        )

        startAddonRegistry()
    }

    private fun startAddonRegistry() {
        lifecycleScope.launch(Dispatchers.Default) {
            try {
                val enumeration = AddonEnumerator(applicationContext).scan()
                val result = AddonRegistryStartup.reconcile(
                    discovered = enumeration.accepted,
                    persistedSigningPinsRaw = prefs.addon.signingCertPins.get(),
                    trustedRootSigningCertSha256 = SigningFingerprint.sha256(applicationContext),
                    packageRejections = enumeration.rejected,
                )
                AddonRegistryStore.setActive(result.registry, result.snapshot)
                if (result.signingPinsChanged) {
                    prefs.addon.signingCertPins.set(result.encodedSigningPins)
                }
                flogInfo(LogTopic.IMS_EVENTS) {
                    "Addon registry startup: accepted=${result.snapshot.accepted.size}, " +
                        "rejected=${result.snapshot.rejected.size}, pinsChanged=${result.signingPinsChanged}"
                }
            } catch (e: Exception) {
                AddonRegistryStore.reset()
                flogWarning(LogTopic.IMS_EVENTS) { "Addon registry startup failed: $e" }
            }
        }
    }

    override fun onCreateInputView(): View? {
        val firstRenderStartedAt = SystemClock.elapsedRealtimeNanos()
        Trace.beginSection("swiftfloris.ime.firstRender")
        try {
            super.installViewTreeOwners()
            val content = window.window!!.findViewById<ViewGroup>(android.R.id.content)
            content.addView(ImeRootView(this))
            // Disable the default input view placement
            return null
        } finally {
            if (BuildConfig.BUILD_TYPE == "benchmark") {
                val durationMs = (SystemClock.elapsedRealtimeNanos() - firstRenderStartedAt) / 1_000_000.0
                Log.i("SwiftFlorisPerf", "swiftfloris.ime.firstRenderMs=$durationMs")
            }
            Trace.endSection()
        }
    }

    override fun onCreateCandidatesView(): View? {
        // Disable the default candidates view
        return null
    }

    override fun onCreateExtractTextView(): View {
        super.installViewTreeOwners()
        // Consider adding a fallback to the default extract edit layout if user reports come
        // that this causes a crash, especially if the device manufacturer of the user device
        // is a known one to break AOSP standards...
        val defaultExtractView = super.onCreateExtractTextView()
        if (defaultExtractView == null || defaultExtractView !is ViewGroup) {
            return ExtractedInputRootView(this, null)
        }
        val extractEditText = defaultExtractView.findViewById<ExtractEditText>(android.R.id.inputExtractEditText)
        (extractEditText?.parent as? ViewGroup)?.removeView(extractEditText)
        defaultExtractView.let {
            it.removeAllViews()
            it.addView(ExtractedInputRootView(this, extractEditText))
        }
        return defaultExtractView
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val shouldRestoreIme = ImeVisibilityConfigurationPolicy.shouldRestoreImeAfterConfigurationChange(
            wasWindowShown = windowController.isWindowShown.value,
            runtimeSdk = Build.VERSION.SDK_INT,
            targetSdk = applicationInfo.targetSdkVersion,
        )
        systemLocalesFlow.value = newConfig.locales
        windowController.onConfigurationChanged(newConfig)
        themeManager.configurationChangeCounter.update { it + 1 }
        if (shouldRestoreIme) {
            window?.window?.decorView?.post {
                if (windowController.isWindowShown.value) {
                    showUi()
                }
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            dev.patrickgold.florisboard.ime.media.sticker.evictStickerBitmapCache()
        }
    }

    override fun onDestroy() {
        // Run our cleanup BEFORE super.onDestroy() — the lifecycle scope is
        // cancelled by super and any callbacks scheduled on it would be
        // dropped, so we tear down owned resources first while everything
        // is still wired up. Guard each step independently so a single
        // failure (e.g. unregistering a receiver that was never registered
        // because onCreate threw before reaching it) doesn't abort the rest
        // of cleanup and leak references.
        try {
            mcpLifecycle?.stop()
            mcpLifecycle = null
            dev.patrickgold.florisboard.ime.mcp.McpDaemonRegistry.setActive(emptyMap())
            dev.patrickgold.florisboard.ime.mcp.McpClientRegistry.setActive(
                dev.patrickgold.florisboard.ime.mcp.NoOpMcpClient,
            )
        } catch (e: Exception) {
            flogWarning(LogTopic.IMS_EVENTS) { "mcpLifecycle.stop() failed: $e" }
        }
        try { voiceInputManager.destroy() } catch (e: Exception) {
            flogWarning(LogTopic.IMS_EVENTS) { "voiceInputManager.destroy() failed: $e" }
        }
        // Cancel the input-feedback scope so in-flight playSoundEffect / vibrate
        // coroutines don't outlive this service and keep the decorView alive.
        try { inputFeedbackController.dispose() } catch (e: Exception) {
            flogWarning(LogTopic.IMS_EVENTS) { "inputFeedbackController.dispose() failed: $e" }
        }
        if (wallpaperReceiverRegistered) {
            try {
                unregisterReceiver(wallpaperChangeReceiver)
            } catch (e: IllegalArgumentException) {
                flogWarning(LogTopic.IMS_EVENTS) { "unregisterReceiver(wallpaper) skipped: $e" }
            }
            wallpaperReceiverRegistered = false
        }
        // Clear inline-autofill suggestions: NlpInlineAutofill is a process-lifetime
        // singleton whose StateFlow holds InlineContentViews inflated against THIS
        // service Context. onFinishInput() clears them, but Android does not guarantee
        // it runs before onDestroy(), so a destroy/rebind with suggestions still
        // resident would keep this dead service (and its window) reachable.
        try { NlpInlineAutofill.clearInlineSuggestions() } catch (e: Exception) {
            flogWarning(LogTopic.IMS_EVENTS) { "NlpInlineAutofill.clearInlineSuggestions() failed: $e" }
        }
        keyboardManager.clearIncognitoModeChangedListener(flagSecureIncognitoModeChangedListener)
        FlorisImeServiceReference = WeakReference(null)
        super.onDestroy()
    }

    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        flogInfo { "restarting=$restarting info=${info?.debugSummarize()}" }
        super.onStartInput(info, restarting)
        if (info == null) return
        val editorInfo = FlorisEditorInfo.wrap(info)
        editorInstance.handleStartInput(editorInfo)
        // After v1.8.110, `voiceInputManager.startListening` deliberately
        // leaves the listening/transcription state in Listening when the
        // IME hands off to an external voice IME, so consumers can observe
        // the handoff window. Reset that state here — when SwiftFloris is
        // re-bound as the active IME (the user returned from FUTO / system
        // picker) — so the next interaction starts from a clean Ready
        // baseline. Cheap idempotent operation; `refreshAvailability()`
        // already short-circuits when the recogniser surface hasn't
        // changed.
        voiceInputManager.refreshAvailability()
    }

    private val pendingStrokes = mutableListOf<dev.patrickgold.florisboard.ime.handwriting.Stroke>()
    private val pendingPoints = mutableListOf<dev.patrickgold.florisboard.ime.handwriting.StrokePoint>()
    private var handwritingSessionActive = false

    @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onPrepareStylusHandwriting(): Unit {
        if (!prefs.keyboard.stylusHandwritingEnabled.get()) return
        pendingStrokes.clear()
        pendingPoints.clear()
    }

    @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onStartStylusHandwriting(): Boolean {
        if (!prefs.keyboard.stylusHandwritingEnabled.get()) return false
        val locale = subtypeManager.activeSubtype.primaryLocale.localeTag()
        val recognizer = dev.patrickgold.florisboard.ime.handwriting.StrokeRecognizerRegistry.active
        if (!recognizer.isReady(locale)) {
            flogInfo { "Stylus handwriting: no recognizer addon installed for $locale" }
            postShortToast(R.string.handwriting__no_recognizer)
            return false
        }
        handwritingSessionActive = true
        pendingStrokes.clear()
        pendingPoints.clear()
        flogInfo { "Stylus handwriting session started for locale=$locale" }
        return true
    }

    @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onStylusHandwritingMotionEvent(event: MotionEvent) {
        if (!handwritingSessionActive) return
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pendingPoints.clear()
                pendingPoints.add(dev.patrickgold.florisboard.ime.handwriting.StrokePoint(
                    event.x, event.y, event.eventTime,
                ))
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.historySize) {
                    pendingPoints.add(dev.patrickgold.florisboard.ime.handwriting.StrokePoint(
                        event.getHistoricalX(i), event.getHistoricalY(i), event.getHistoricalEventTime(i),
                    ))
                }
                pendingPoints.add(dev.patrickgold.florisboard.ime.handwriting.StrokePoint(
                    event.x, event.y, event.eventTime,
                ))
            }
            MotionEvent.ACTION_UP -> {
                pendingPoints.add(dev.patrickgold.florisboard.ime.handwriting.StrokePoint(
                    event.x, event.y, event.eventTime,
                ))
                if (pendingPoints.size >= 2) {
                    pendingStrokes.add(dev.patrickgold.florisboard.ime.handwriting.Stroke(
                        pendingPoints.toList(),
                    ))
                }
                pendingPoints.clear()
            }
        }
    }

    @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onFinishStylusHandwriting() {
        if (!handwritingSessionActive) return
        handwritingSessionActive = false
        if (pendingStrokes.isEmpty()) return
        val locale = subtypeManager.activeSubtype.primaryLocale.localeTag()
        val recognizer = dev.patrickgold.florisboard.ime.handwriting.StrokeRecognizerRegistry.active
        val result = recognizer.recognize(pendingStrokes.toList(), locale)
        pendingStrokes.clear()
        when (result) {
            is dev.patrickgold.florisboard.ime.handwriting.StrokeRecognitionResult.Candidates -> {
                val best = result.candidates.firstOrNull()
                if (best != null) {
                    currentInputConnection?.commitText(best.text, 1)
                    flogInfo {
                        "Stylus handwriting committed: text=${best.text.debugSummarizeTextForLog()} " +
                            "(confidence=${best.confidence})"
                    }
                }
            }
            is dev.patrickgold.florisboard.ime.handwriting.StrokeRecognitionResult.NoRecognition -> {
                flogInfo { "Stylus handwriting: recognizer returned NoRecognition" }
            }
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        flogInfo { "restarting=$restarting info=${info?.debugSummarize()}" }
        super.onStartInputView(info, restarting)
        if (info == null) return
        val editorInfo = FlorisEditorInfo.wrap(info)
        flagSecureEditorInfo = editorInfo
        subtypeManager.onEditorPackageFocus(editorInfo.packageName)
        activeState.batchEdit {
            activeState.isSelectionMode = editorInfo.initialSelection.isSelectionMode
            editorInstance.handleStartInputView(editorInfo, isRestart = restarting)
        }
        applyFlagSecureForCurrentField(editorInfo)
        // ROADMAP §7 Next-11.3a — per-app adaptive accent. Publish the active
        // editor's package name to the controller so any Compose surface
        // subscribed to `LocalPerAppAccent` can retint. Cheap; no-op when
        // the user toggle is off.
        perAppAccentController.setActiveEditorPackage(editorInfo.packageName)
    }

    private fun reapplyFlagSecureForCurrentField() {
        flagSecureEditorInfo?.let(::applyFlagSecureForCurrentField)
    }

    /**
     * ROADMAP §6 N7.2 / R7-1 — set [WindowManager.LayoutParams.FLAG_SECURE] on the
     * IME window whenever the active editor is a password / visible-password /
     * web-password / app-private field or incognito mode is active. Prevents
     * screenshots, screen recordings, and external display mirroring from capturing
     * the long-press preview popup or the suggestion strip.
     *
     * The flag is cleared when the user moves to a plain non-incognito field, so the
     * user can still screenshot the keyboard for support / bug-report purposes outside
     * of private-entry contexts.
     */
    private fun applyFlagSecureForCurrentField(editorInfo: FlorisEditorInfo) {
        val w = window?.window ?: return
        val isPasswordField = when (editorInfo.inputAttributes.variation) {
            InputAttributes.Variation.PASSWORD,
            InputAttributes.Variation.VISIBLE_PASSWORD,
            InputAttributes.Variation.WEB_PASSWORD,
            -> true
            else -> false
        }
        val isAppPrivateField = editorInfo.imeOptions.flagNoPersonalizedLearning
        // Also honour incognito: every other sensitive-data gate (voice handoff,
        // dictionary learning, clipboard history) treats incognito as equal to a
        // password field, but FLAG_SECURE only checked the field variation, leaving
        // the IME window screenshot-/record-able while the user types privately into
        // an ordinary field in incognito mode. The keyboard-manager toggle callback
        // re-applies this policy for the active field before the next keypress.
        val shouldSecureImeWindow = FlagSecurePolicy.shouldSecureImeWindow(
            isPasswordField = isPasswordField,
            isAppPrivateField = isAppPrivateField,
            isIncognitoMode = activeState.isIncognitoMode,
        )
        if (shouldSecureImeWindow) {
            w.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            w.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    override fun onEvaluateInputViewShown(): Boolean {
        val config = resources.configuration
        val diagnostics = PhysicalKeyboardPolicy.inputViewVisibilityDiagnostics(
            formFactorType = windowController.activeRootInsets.value.formFactor.typeGuess,
            configurationKeyboard = config.keyboard,
            hardKeyboardHidden = config.hardKeyboardHidden,
            frameworkWouldShowInputView = super.onEvaluateInputViewShown(),
            showOnScreenKeyboardPref = prefs.physicalKeyboard.showOnScreenKeyboard.get(),
            showSmartbarOnlyPref = prefs.physicalKeyboard.showSmartbarOnly.get(),
            detectedHardwareKeyboards = detectedHardwareKeyboardOptions(),
        )
        logPhysicalKeyboardVisibilityDecision(diagnostics.summary())
        keyboardManager.activeState.isSmartbarOnlyMode = diagnostics.decision.smartbarOnly
        return diagnostics.decision.shouldShow
    }

    private fun logPhysicalKeyboardVisibilityDecision(summary: String) {
        if (summary == lastPhysicalKeyboardVisibilitySummary) return
        lastPhysicalKeyboardVisibilitySummary = summary
        flogInfo(LogTopic.IMS_EVENTS) { "Physical keyboard input-view visibility: $summary" }
    }

    private fun detectedHardwareKeyboardOptions(): List<HardwareKeyboardDeviceOption> {
        val inputManager = systemServiceOrNull(InputManager::class) ?: return emptyList()
        return inputManager.inputDeviceIds
            .asSequence()
            .mapNotNull { deviceId -> InputDevice.getDevice(deviceId) }
            .filter { device ->
                device.id >= 0 &&
                    !device.isVirtual &&
                    device.keyboardType != InputDevice.KEYBOARD_TYPE_NONE &&
                    device.sources and InputDevice.SOURCE_KEYBOARD == InputDevice.SOURCE_KEYBOARD
            }
            .map { device ->
                HardwareKeyboardDeviceOption(
                    id = device.id,
                    displayName = device.name.ifBlank {
                        "Hardware keyboard ${device.id}"
                    },
                )
            }
            .toList()
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
    ) {
        flogInfo { "old={start=$oldSelStart,end=$oldSelEnd} new={start=$newSelStart,end=$newSelEnd} composing={start=$candidatesStart,end=$candidatesEnd}" }
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        activeState.batchEdit {
            activeState.isSelectionMode = (newSelEnd - newSelStart) != 0
            editorInstance.handleSelectionUpdate(
                oldSelection = EditorRange.normalized(oldSelStart, oldSelEnd),
                newSelection = EditorRange.normalized(newSelStart, newSelEnd),
                composing = EditorRange.normalized(candidatesStart, candidatesEnd),
            )
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        flogInfo { "finishing=$finishingInput" }
        super.onFinishInputView(finishingInput)
        flagSecureEditorInfo = null
        editorInstance.handleFinishInputView()
    }

    override fun onFinishInput() {
        flogInfo { "(no args)" }
        super.onFinishInput()
        editorInstance.handleFinishInput()
        NlpInlineAutofill.clearInlineSuggestions()
    }

    override fun onWindowShown() {
        super.onWindowShown()
        if (windowController.onWindowShown()) {
            flogInfo(LogTopic.IMS_EVENTS)
            inputFeedbackController.updateSystemPrefsState()
        } else {
            flogWarning(LogTopic.IMS_EVENTS) { "Ignoring (is already shown)" }
        }
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        if (windowController.onWindowHidden()) {
            flogInfo(LogTopic.IMS_EVENTS)
            activeState.batchEdit {
                keyboardManager.resetUiModeAndHistory()
                activeState.isActionsOverflowVisible = false
                activeState.isActionsEditorVisible = false
            }
        } else {
            flogWarning(LogTopic.IMS_EVENTS) { "Ignoring (is already hidden)" }
        }
    }

    override fun onEvaluateFullscreenMode(): Boolean {
        val config = resources.configuration
        if (config.orientation != Configuration.ORIENTATION_LANDSCAPE) {
            return false
        }
        return when (prefs.keyboard.landscapeInputUiMode.get()) {
            LandscapeInputUiMode.DYNAMICALLY_SHOW -> super.onEvaluateFullscreenMode()
            LandscapeInputUiMode.NEVER_SHOW -> false
            LandscapeInputUiMode.ALWAYS_SHOW -> true
        }
    }

    override fun onUpdateExtractingVisibility(info: EditorInfo?) {
        if (info != null) {
            val editorInfo = FlorisEditorInfo.wrap(info)
            // The framework re-invokes this on every fullscreen re-evaluation,
            // in addition to onStartInputView. Replaying the start-input
            // pipeline for the same editor resets phantom/auto-space state,
            // bounces the keyboard mode back to the field default, and
            // launches a duplicate content-generation pass mid-session — so
            // only resync when the editor actually changed.
            if (editorInfo != editorInstance.activeInfo) {
                editorInstance.handleStartInputView(editorInfo, isRestart = true)
            }
        }
        when (prefs.keyboard.landscapeInputUiMode.get()) {
            LandscapeInputUiMode.DYNAMICALLY_SHOW -> super.onUpdateExtractingVisibility(info)
            LandscapeInputUiMode.NEVER_SHOW -> isExtractViewShown = false
            LandscapeInputUiMode.ALWAYS_SHOW -> isExtractViewShown = true
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreateInlineSuggestionsRequest(uiExtras: Bundle): InlineSuggestionsRequest? {
        if (!prefs.smartbar.enabled.get() || !prefs.suggestion.api30InlineSuggestionsEnabled.get()) {
            flogInfo(LogTopic.IMS_EVENTS) {
                "Ignoring inline suggestions request because Smartbar and/or inline suggestions are disabled."
            }
            return null
        }

        flogInfo(LogTopic.IMS_EVENTS) { "Creating inline suggestions request" }
        val stylesBundle = themeManager.createInlineSuggestionUiStyleBundle(this)
        if (stylesBundle == null) {
            flogWarning(LogTopic.IMS_EVENTS) { "Failed to retrieve inline suggestions style bundle" }
            return null
        }
        val maxSize = InlineSuggestionSizePolicy.presentationMaxSize(
            displayWidthPx = resources.displayMetrics.widthPixels,
            chipHeightPx = NlpInlineAutofill.suggestionsChipHeightPx,
        )
        val spec = InlinePresentationSpec.Builder(
            InlineSuggestionSizePolicy.presentationMinSize,
            maxSize,
        ).run {
            setStyle(stylesBundle)
            build()
        }

        return InlineSuggestionsRequest.Builder(listOf(spec)).run {
            setMaxSuggestionCount(InlineSuggestionsRequest.SUGGESTION_COUNT_UNLIMITED)
            build()
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onInlineSuggestionsResponse(response: InlineSuggestionsResponse): Boolean {
        val inlineSuggestions = response.inlineSuggestions
        flogInfo(LogTopic.IMS_EVENTS) {
            "Received inline suggestions response with ${inlineSuggestions.size} suggestion(s) provided."
        }
        return NlpInlineAutofill.showInlineSuggestions(this, inlineSuggestions)
    }

    override fun onComputeInsets(outInsets: Insets?) {
        if (outInsets == null) return
        val state = keyboardManager.activeState.snapshot()
        windowController.onComputeInsets(outInsets, state.isFullscreenInputRequired())
    }

    override fun getTextForImeAction(imeOptions: Int): String? {
        return try {
            when (imeOptions and EditorInfo.IME_MASK_ACTION) {
                EditorInfo.IME_ACTION_NONE -> null
                EditorInfo.IME_ACTION_GO -> resourcesContext.getString(AndroidInternalR.string.ime_action_go)
                EditorInfo.IME_ACTION_SEARCH -> resourcesContext.getString(AndroidInternalR.string.ime_action_search)
                EditorInfo.IME_ACTION_SEND -> resourcesContext.getString(AndroidInternalR.string.ime_action_send)
                EditorInfo.IME_ACTION_NEXT -> resourcesContext.getString(AndroidInternalR.string.ime_action_next)
                EditorInfo.IME_ACTION_DONE -> resourcesContext.getString(AndroidInternalR.string.ime_action_done)
                EditorInfo.IME_ACTION_PREVIOUS -> resourcesContext.getString(AndroidInternalR.string.ime_action_previous)
                else -> resourcesContext.getString(AndroidInternalR.string.ime_action_default)
            }
        } catch (t: Throwable) {
            // resourcesContext.getString against AndroidInternalR can throw on
            // devices where the framework strings are stripped / renamed (OEM
            // builds, very old preview tracks). Fall back to the platform
            // getTextForImeAction and surface a dev-build log so a regression
            // here doesn't hide silently.
            flogWarning(LogTopic.IMS_EVENTS) {
                "getTextForImeAction: AndroidInternalR lookup failed (imeOptions=$imeOptions): ${t.javaClass.simpleName}"
            }
            super.getTextForImeAction(imeOptions)?.toString()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return keyboardManager.onHardwareKeyDown(keyCode, event) || super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        return keyboardManager.onHardwareKeyUp(keyCode, event) || super.onKeyUp(keyCode, event)
    }
}

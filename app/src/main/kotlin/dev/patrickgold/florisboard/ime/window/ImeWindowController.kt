/*
 * Copyright (C) 2025-2026 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.ime.window

import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.width
import dev.patrickgold.florisboard.app.FlorisPreferenceModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.florisboard.lib.kotlin.collectIn

/**
 * The window controller is responsible for managing everything related to window config, spec, insets,
 * actions, editor move, and editor resize.
 *
 * This class is designed so it does not contain any references to Android framework classes, allowing it
 * and its inner classes to be unit tested on JVM desktop.
 *
 * @property prefs The preference data store from which the window config is read from / where the window
 *  config is written to.
 * @property scope The coroutine scope in which suspending operations and flow collection should run in.
 */
class ImeWindowController(
    private val prefs: FlorisPreferenceModel,
    val scope: CoroutineScope,
) {
    /**
     * Access to window-related actions.
     */
    val actions = Actions()

    /**
     * Access to window config editor.
     */
    val editor = Editor()

    /**
     * The active root insets describe the size and position of the root window.
     *
     * Typically, the root insets corresponds to the screen bounds, however this is not guaranteed. For
     * consistency reasons, all sub sizes and positions should be derived from the root insets.
     *
     * @see ImeRootWindow
     */
    val activeRootInsets: StateFlow<ImeInsets.Root>
        field = MutableStateFlow(ImeInsets.Root.Zero)

    /**
     * The active window insets describe the size and position of the window within the root window.
     *
     * This value is used for responding to [onComputeInsets] by the accompanying IME service class.
     *
     * May be null. If null, this indicates the window insets are unknown, and no insets will be reported
     * back to the IME service class in this case.
     *
     * @see ImeWindow
     */
    val activeWindowInsets: StateFlow<ImeInsets.Window?>
        field = MutableStateFlow(null)

    /**
     * Holds the active window config for the current type guess based on the root insets.
     */
    val activeWindowConfig: StateFlow<ImeWindowConfig>
        field = MutableStateFlow(ImeWindowConfig.Default)

    /**
     * Holds the active window spec, which is computed based on the root insets and window config.
     */
    val activeWindowSpec: StateFlow<ImeWindowSpec>
        field = MutableStateFlow<ImeWindowSpec>(ImeWindowSpec.Fallback)

    /**
     * Holds the state if the window is currently shown to the user, as reported by the IME service class.
     */
    val isWindowShown: StateFlow<Boolean>
        field = MutableStateFlow(false)

    /**
     * One-shot onboarding visibility for the floating-window drag and resize affordances.
     */
    val floatingOnboardingVisible: StateFlow<Boolean>
        field = MutableStateFlow(false)

    private val updateConfigMutex = Mutex()

    init {
        combine(
            activeRootInsets,
            prefs.keyboard.windowConfig.asFlow(),
        ) { rootInsets, windowConfigByType ->
            val typeGuess = rootInsets.formFactor.typeGuess
            windowConfigByType[typeGuess] ?: ImeWindowConfig.Default
        }.collectIn(scope) { windowConfig ->
            activeWindowConfig.value = windowConfig
        }

        val userPreferredOptions = combine(
            listOf(
                activeRootInsets,
                prefs.keyboard.keySpacingHorizontal.asFlow(),
                prefs.keyboard.keySpacingVertical.asFlow(),
                prefs.keyboard.fontSizeMultiplierPortrait.asFlow(),
                prefs.keyboard.fontSizeMultiplierLandscape.asFlow(),
                prefs.keyboard.keyboardHeightMultiplierPortrait.asFlow(),
                prefs.keyboard.keyboardHeightMultiplierLandscape.asFlow(),
            )
        ) { values ->
            userPreferredOptionsOf(
                rootInsets = values[0] as ImeInsets.Root,
                keySpacingFactorH = values[1] as Int,
                keySpacingFactorV = values[2] as Int,
                multiplierP = values[3] as Int,
                multiplierL = values[4] as Int,
                heightP = values[5] as Int,
                heightL = values[6] as Int,
            )
        }

        combine(
            activeRootInsets,
            activeWindowConfig,
            userPreferredOptions,
            editor.version,
        ) { rootInsets, windowConfig, userConfig, _ ->
            doComputeWindowSpec(rootInsets, windowConfig, userConfig)
        }.collectIn(scope) { windowSpec ->
            activeWindowSpec.value = windowSpec
        }
    }

    /**
     * The window spec the keyboard settles on once its root window is laid out with [rootInsets],
     * computed straight from the stored window config and preferences. Android asks for the inline
     * autofill request before a freshly started keyboard has composed, while [activeWindowSpec] is
     * still [ImeWindowSpec.Fallback], and the chip width in that request holds for the whole form.
     */
    fun estimateWindowSpec(rootInsets: ImeInsets.Root): ImeWindowSpec {
        val windowConfig = prefs.keyboard.windowConfig.get()[rootInsets.formFactor.typeGuess]
            ?: ImeWindowConfig.Default
        val userPreferredOptions = userPreferredOptionsOf(
            rootInsets = rootInsets,
            keySpacingFactorH = prefs.keyboard.keySpacingHorizontal.get(),
            keySpacingFactorV = prefs.keyboard.keySpacingVertical.get(),
            multiplierP = prefs.keyboard.fontSizeMultiplierPortrait.get(),
            multiplierL = prefs.keyboard.fontSizeMultiplierLandscape.get(),
            heightP = prefs.keyboard.keyboardHeightMultiplierPortrait.get(),
            heightL = prefs.keyboard.keyboardHeightMultiplierLandscape.get(),
        )
        return doComputeWindowSpec(rootInsets, windowConfig, userPreferredOptions)
    }

    /**
     * Updates the active root window insets. Should be called exclusively by [ImeRootWindow].
     */
    fun updateRootInsets(newInsets: ImeInsets.Root) {
        activeRootInsets.value = newInsets
    }

    /**
     * Updates the active window insets. Should be called exclusively by [ImeWindow].
     */
    fun updateWindowInsets(newInsets: ImeInsets.Window) {
        activeWindowInsets.value = newInsets
    }

    /**
     * Updates the window config in the preferences. It will only update the window config for the type guess of
     * the active root insets, and will keep other window configs intact.
     *
     * This function is thread-safe under the assumption that:
     * a) The window config pref is only written to by this update function. On other concurrent write accesses
     *    to the underlying pref the behavior is undefined.
     * b) The active root insets do not change while the update in ongoing. A snapshot of the active root insets
     *    will be taken once before any update attempt, and any inset change afterward will not be reflected.
     */
    fun updateWindowConfig(
        afterUpdate: (ImeWindowConfig) -> Unit = { },
        function: (ImeWindowConfig) -> ImeWindowConfig,
    ) {
        val rootInsets = activeRootInsets.value
        val typeGuess = rootInsets.formFactor.typeGuess
        scope.launch {
            // not bullet-proof sync, but good enough considering this is only triggered by tap actions
            updateConfigMutex.withLock {
                val newWindowConfig = activeWindowConfig.updateAndGet(function)
                val byType = prefs.keyboard.windowConfig.get()
                prefs.keyboard.windowConfig.set(byType.plus(typeGuess to newWindowConfig))
                afterUpdate(newWindowConfig)
            }
        }
    }

    /**
     * Called by the accompanying IME service class to request the current window insets.
     *
     * The window controller will honor the request for computation only if it knows where the window is
     * located within the root window. If unknown, no response will be given.
     *
     * The response's touchable insets mode will always be [InputMethodService.Insets.TOUCHABLE_INSETS_REGION],
     * even if the touchable area needs to be fullscreen, or matches the visible/content top. This is due to a
     * bug that affects other modes, where no touch input is propagated to the root window in some cases.
     *
     * If the current window spec is of floating mode, the reported visible/content bounds will be empty. This
     * causes the underlying application to not resize at all when the floating window is shown.
     *
     * @param outInsets The out insets to write the response into.
     * @param isFullscreenInputRequired Flag indicating if, despite the current window config not requiring
     *  it, a fullscreen touch area should be reported back. This can be used e.g. for bottom sheets that
     *  cover a good portion of the screen, or other overlays requiring touch interaction.
     */
    fun onComputeInsets(
        outInsets: InputMethodService.Insets,
        isFullscreenInputRequired: Boolean,
    ) {
        val reported = reportedInsets() ?: return
        val touchableBounds = if (isFullscreenInputRequired) {
            activeRootInsets.value.boundsPx
        } else {
            reported.touchableBoundsPx
        }

        outInsets.contentTopInsets = reported.insetsTop
        outInsets.visibleTopInsets = reported.insetsTop
        outInsets.touchableRegion.set(
            touchableBounds.left,
            touchableBounds.top,
            touchableBounds.right,
            touchableBounds.bottom,
        )
        outInsets.touchableInsets = InputMethodService.Insets.TOUCHABLE_INSETS_REGION
    }

    /**
     * What [onComputeInsets] reports from this controller's state alone: the content and visible top line
     * ([reportedInsetsTop]) and the touchable bounds, which are the whole root while the editor is enabled
     * and the keyboard window otherwise. Null while the window insets are unknown.
     */
    internal fun reportedInsets(): ReportedImeInsets? {
        val windowBounds = activeWindowInsets.value?.boundsPx ?: return null
        val insetsTop = reportedInsetsTop() ?: return null
        val touchableBounds = if (editor.state.value.isEnabled) activeRootInsets.value.boundsPx else windowBounds
        return ReportedImeInsets(insetsTop, touchableBounds)
    }

    /**
     * Emits [reportedInsets] each time it changes. Android only asks for the insets at the start of a view
     * traversal, and the keyboard is laid out later in that traversal, so the service requests another one
     * on every emission to get the new insets to the app behind the keyboard.
     */
    fun reportedInsetsChanges(): Flow<ReportedImeInsets?> {
        return combine(activeRootInsets, activeWindowInsets, activeWindowSpec, editor.state) { _, _, _, _ ->
            reportedInsets()
        }.distinctUntilChanged()
    }

    /**
     * The content and visible top insets [onComputeInsets] reports, in px, or null while the window insets
     * are unknown. The app behind the keyboard lays itself out above this line.
     *
     * While an editor move or resize gesture is ongoing this holds the value from when the gesture began.
     * Following the window instead makes the app behind re-layout on every frame of the drag, which is what
     * made resizing janky (issue #23). The value catches up once the gesture ends or is cancelled.
     */
    internal fun reportedInsetsTop(): Int? {
        if (editor.state.value.isAnyGesture) {
            editor.gestureInsetsTop?.let { return it }
        }
        val windowBounds = activeWindowInsets.value?.boundsPx ?: return null
        return when (activeWindowSpec.value) {
            is ImeWindowSpec.Fixed -> windowBounds.top
            is ImeWindowSpec.Floating -> activeRootInsets.value.boundsPx.bottom
        }
    }

    /**
     * The font scale the keyboard theme sizes its text with, for [spec] while the editor is in [editorState].
     *
     * During a resize gesture this holds the scale from when the gesture began. Every style query in the
     * keyboard depends on the font scale, so following the window would restyle every key on every frame of
     * the drag (issue #23). The text catches up to the new size once the gesture ends or is cancelled.
     */
    internal fun themeFontScale(spec: ImeWindowSpec, editorState: EditorState): Float {
        if (editorState.isResizeGesture) {
            editor.gestureFontScale?.let { return it }
        }
        return spec.fontScale
    }

    fun onConfigurationChanged(@Suppress("UNUSED_PARAMETER") newConfig: Configuration) {
        // As of writing newConfig is unused, but kept for forward-compatibility.
        editor.disable()
    }

    fun onWindowShown(): Boolean {
        val didShow = isWindowShown.compareAndSet(expect = false, update = true)
        if (didShow) {
            scope.launch {
                val currentConfig = activeWindowConfig.value
                val currentMode = currentConfig.mode
                val startInFloatingMode = prefs.keyboard.startInFloatingMode.get()
                // ROADMAP §0 P3 — split-keyboard preference wire-up. When
                // the user has enabled `prefs.keyboard.splitKeyboardEnabled`
                // AND we're in fixed mode AND the current form-factor is
                // wide enough to host the split (`Fixed.Split.isViable`),
                // promote the fixed sub-mode to SPLIT so the renderer
                // emits the split key-rect arrangement (Next-7.2-renderer).
                val splitEnabled = prefs.keyboard.splitKeyboardEnabled.get()
                val splitViable = isSplitModeViable(activeRootInsets.value)
                if (startInFloatingMode && currentMode != ImeWindowMode.FLOATING) {
                    updateWindowConfig(
                        afterUpdate = { queueFloatingOnboardingIfNeeded(ImeWindowMode.FLOATING) },
                    ) { config ->
                        config.copy(mode = ImeWindowMode.FLOATING)
                    }
                } else if (splitEnabled && currentMode == ImeWindowMode.FIXED &&
                    splitViable &&
                    currentConfig.fixedMode != ImeWindowMode.Fixed.SPLIT
                ) {
                    updateWindowConfig { config ->
                        config.copy(fixedMode = ImeWindowMode.Fixed.SPLIT)
                    }
                } else {
                    queueFloatingOnboardingIfNeeded(currentMode)
                }
            }
        }
        return didShow
    }

    fun onWindowHidden(): Boolean {
        editor.disable()
        dismissFloatingOnboarding()
        return isWindowShown.compareAndSet(expect = true, update = false)
    }

    fun dismissFloatingOnboarding() {
        floatingOnboardingVisible.value = false
    }

    private fun queueFloatingOnboardingIfNeeded(windowMode: ImeWindowMode) {
        scope.launch {
            val alreadyShown = prefs.keyboard.floatingOnboardingShown.get()
            if (FloatingModeOnboarding.shouldShow(windowMode, alreadyShown)) {
                prefs.keyboard.floatingOnboardingShown.set(true)
                floatingOnboardingVisible.value = true
            }
        }
    }

    private fun userPreferredOptionsOf(
        rootInsets: ImeInsets.Root,
        keySpacingFactorH: Int,
        keySpacingFactorV: Int,
        multiplierP: Int,
        multiplierL: Int,
        heightP: Int,
        heightL: Int,
    ): ImeWindowSpec.UserPreferredOptions {
        val rootBounds = rootInsets.boundsDp
        val isPortrait = rootBounds.width <= rootBounds.height
        // ROADMAP §6 N5.3 — Scalable keyboard height. Form-factor default ×
        // user multiplier; ImeWindowController.doComputeWindowSpec re-constrains
        // to [minKeyboardHeight, maxKeyboardHeight].
        val heightScale = if (isPortrait) heightP / 100f else heightL / 100f
        return ImeWindowSpec.UserPreferredOptions(
            keySpacingFactorH = keySpacingFactorH / 100f,
            keySpacingFactorV = keySpacingFactorV / 100f,
            fontScale = if (isPortrait) multiplierP / 100f else multiplierL / 100f,
            keyboardHeightScale = heightScale.coerceIn(0.5f, 1.5f),
        )
    }

    private fun doComputeWindowSpec(
        rootInsets: ImeInsets.Root,
        windowConfig: ImeWindowConfig,
        userPreferredOptions: ImeWindowSpec.UserPreferredOptions,
    ): ImeWindowSpec {
        return when (windowConfig.mode) {
            ImeWindowMode.FIXED -> {
                val fixedMode = if (
                    windowConfig.fixedMode == ImeWindowMode.Fixed.SPLIT &&
                    !isSplitModeViable(rootInsets)
                ) {
                    ImeWindowMode.Fixed.NORMAL
                } else {
                    windowConfig.fixedMode
                }
                val constraints = ImeWindowConstraints.of(rootInsets, fixedMode)
                val rawProps = windowConfig.fixedProps[fixedMode] ?: constraints.defaultProps
                // ROADMAP §6 N5.3 — apply user height scale before constrained() so the
                // [min, max] keyboard-height clamp catches absurd slider values.
                val scaledProps = rawProps.copy(
                    keyboardHeight = rawProps.keyboardHeight * userPreferredOptions.keyboardHeightScale,
                )
                val props = scaledProps.constrained(constraints)
                ImeWindowSpec.Fixed(
                    fixedMode = fixedMode,
                    props = props,
                    userPreferredOptions = userPreferredOptions,
                    constraints = constraints,
                )
            }
            ImeWindowMode.FLOATING -> {
                val constraints = ImeWindowConstraints.of(rootInsets, windowConfig.floatingMode)
                val props = (windowConfig.floatingProps[windowConfig.floatingMode] ?: constraints.defaultProps)
                    .constrained(constraints)
                // Floating window has its own resize affordance, so the slider is
                // intentionally ignored for floating mode.
                ImeWindowSpec.Floating(
                    floatingMode = windowConfig.floatingMode,
                    props = props,
                    userPreferredOptions = userPreferredOptions,
                    constraints = constraints,
                )
            }
        }
    }

    private fun isSplitModeViable(rootInsets: ImeInsets.Root): Boolean {
        val constraints = ImeWindowConstraints.of(rootInsets, ImeWindowMode.Fixed.SPLIT)
        return constraints is ImeWindowConstraints.Fixed.Split && constraints.isViable
    }

    /**
     * Wrapper class for window-related actions.
     */
    inner class Actions {
        fun toggleFloatingWindow() {
            updateWindowConfig(
                afterUpdate = { config -> queueFloatingOnboardingIfNeeded(config.mode) },
            ) { config ->
                val newMode = when (config.mode) {
                    ImeWindowMode.FIXED -> ImeWindowMode.FLOATING
                    ImeWindowMode.FLOATING -> ImeWindowMode.FIXED
                }
                config.copy(mode = newMode)
            }
        }

        fun toggleCompactLayout() {
            updateWindowConfig { config ->
                when (config.mode) {
                    ImeWindowMode.FIXED -> {
                        val newFixedMode = when (config.fixedMode) {
                            ImeWindowMode.Fixed.COMPACT -> ImeWindowMode.Fixed.NORMAL
                            else -> ImeWindowMode.Fixed.COMPACT
                        }
                        config.copy(fixedMode = newFixedMode)
                    }
                    ImeWindowMode.FLOATING -> {
                        config.copy(
                            mode = ImeWindowMode.FIXED,
                            fixedMode = ImeWindowMode.Fixed.COMPACT,
                        )
                    }
                }
            }
        }

        private inline fun doCompactLayout(
            crossinline updateProps: (ImeWindowProps.Fixed) -> ImeWindowProps.Fixed,
        ) {
            val rootInsets = activeRootInsets.value
            val constraints = ImeWindowConstraints.of(rootInsets, ImeWindowMode.Fixed.COMPACT)
            updateWindowConfig { config ->
                val props = config.fixedProps[ImeWindowMode.Fixed.COMPACT] ?: constraints.defaultProps
                val newProps = updateProps(props)
                config.copy(
                    mode = ImeWindowMode.FIXED,
                    fixedMode = ImeWindowMode.Fixed.COMPACT,
                    fixedProps = config.fixedProps.plus(ImeWindowMode.Fixed.COMPACT to newProps)
                )
            }
        }

        fun compactLayoutToLeft() {
            doCompactLayout { props ->
                props.copy(
                    paddingLeft = min(props.paddingLeft, props.paddingRight),
                    paddingRight = max(props.paddingLeft, props.paddingRight),
                )
            }
        }

        fun compactLayoutToRight() {
            doCompactLayout { props ->
                props.copy(
                    paddingLeft = max(props.paddingLeft, props.paddingRight),
                    paddingRight = min(props.paddingLeft, props.paddingRight),
                )
            }
        }

        fun compactLayoutFlipSide() {
            doCompactLayout { props ->
                props.copy(
                    paddingLeft = props.paddingRight,
                    paddingRight = props.paddingLeft,
                )
            }
        }

        fun resetFixedSize() {
            updateWindowConfig { config ->
                config.copy(
                    fixedProps = config.fixedProps.minus(config.fixedMode),
                )
            }
        }

        fun resetFloatingSize() {
            val rootInsets = activeRootInsets.value
            updateWindowConfig { config ->
                when (config.mode) {
                    ImeWindowMode.FLOATING -> {
                        val constraints = ImeWindowConstraints.of(rootInsets, config.floatingMode)
                        val defaultProps = constraints.defaultProps
                        val newProps = config.floatingProps[config.floatingMode]?.copy(
                            keyboardHeight = defaultProps.keyboardHeight,
                            keyboardWidth = defaultProps.keyboardWidth,
                        ) ?: defaultProps
                        config.copy(
                            floatingProps = config.floatingProps.plus(
                                config.floatingMode to newProps.constrained(constraints)
                            ),
                        )
                    }
                    ImeWindowMode.FIXED -> {
                        config
                    }
                }
            }
        }
    }

    /**
     * The window config editor is the in-window UI for moving and/or resizing the window without having to
     * go to the settings. It both manages the state of the editor, and contains some utility functions.
     */
    inner class Editor {
        /**
         * The state of the editor.
         *
         * @see EditorState
         */
        val state: StateFlow<EditorState>
            field = MutableStateFlow(EditorState.INACTIVE)

        /**
         * The version of the editor. Is used to force spec re-computation.
         */
        val version: StateFlow<Int>
            field = MutableStateFlow(0)

        /**
         * The [reportedInsetsTop] from when the current gesture began, held until it ends.
         */
        internal var gestureInsetsTop: Int? = null
            private set

        /**
         * The [ImeWindowSpec.fontScale] from when the current resize gesture began, held until it ends.
         */
        internal var gestureFontScale: Float? = null
            private set

        /**
         * The [activeWindowSpec] from when the current resize gesture began, held until it ends. The keys keep
         * the layout they had at this size for the whole drag, and are stretched to the dragged height instead
         * of being laid out again on every frame (issue #23).
         */
        internal var gestureStartSpec: ImeWindowSpec? = null
            private set

        private fun syncFromPrefs() {
            version.update { it + 1 }
        }

        fun enable() {
            state.value = EditorState.ACTIVE
            syncFromPrefs()
        }

        fun disable() {
            state.value = EditorState.INACTIVE
            syncFromPrefs()
        }

        fun disableIfNoGestureInProgress() {
            val newState = state.updateAndGet { state ->
                if (state.isAnyGesture) state else EditorState.INACTIVE
            }
            if (!newState.isEnabled) {
                syncFromPrefs()
            }
        }

        fun toggleEnabled() {
            state.update { state ->
                editorStateOf(!state.isEnabled)
            }
            syncFromPrefs()
        }

        fun beginMoveGesture(): ImeWindowSpec {
            gestureInsetsTop = reportedInsetsTop()
            state.value = EditorState.ACTIVE_MOVE_GESTURE
            return activeWindowSpec.value
        }

        fun endMoveGesture(spec: ImeWindowSpec) {
            var keepEnabled = true
            // The state write must happen in afterUpdate (inside the async config
            // update), not synchronously after this call: keepEnabled is assigned
            // inside the update lambda, so reading it here would always see the
            // initial value and a floating window docked past dockToFixedHeight
            // would keep the editor active — swallowing all touches over the host
            // app via the fullscreen touchable insets region.
            updateWindowConfig(afterUpdate = { state.value = editorStateOf(keepEnabled) }) { config ->
                when (spec) {
                     is ImeWindowSpec.Fixed -> {
                         keepEnabled = true
                         config.copy(fixedProps = config.fixedProps.plus(spec.fixedMode to spec.storedProps()))
                     }
                    is ImeWindowSpec.Floating -> {
                        if (spec.props.offsetBottom <= spec.constraints.dockToFixedHeight) {
                            keepEnabled = false
                            config.copy(mode = ImeWindowMode.FIXED)
                        } else {
                            keepEnabled = true
                            config.copy(floatingProps = config.floatingProps.plus(spec.floatingMode to spec.props))
                        }
                    }
                }
            }
        }

        fun beginResizeGesture(): ImeWindowSpec {
            gestureInsetsTop = reportedInsetsTop()
            gestureFontScale = activeWindowSpec.value.fontScale
            gestureStartSpec = activeWindowSpec.value
            state.value = EditorState.ACTIVE_RESIZE_GESTURE
            return activeWindowSpec.value
        }

        fun endResizeGesture(spec: ImeWindowSpec) {
            var keepEnabled = true
            updateWindowConfig { config ->
                when (spec) {
                    is ImeWindowSpec.Fixed -> {
                        keepEnabled = true
                        config.copy(fixedProps = config.fixedProps.plus(spec.fixedMode to spec.storedProps()))
                    }
                    is ImeWindowSpec.Floating -> {
                        keepEnabled = true
                        config.copy(floatingProps = config.floatingProps.plus(spec.floatingMode to spec.props))
                    }
                }
            }
            state.value = editorStateOf(keepEnabled)
        }

        fun onSpecUpdated(spec: ImeWindowSpec) {
            if (state.value.isEnabled) {
                activeWindowSpec.value = spec
            }
        }

        fun cancelGesture() {
            state.value = EditorState.INACTIVE
            syncFromPrefs()
        }

        @Suppress("NOTHING_TO_INLINE")
        private inline fun editorStateOf(isEnabled: Boolean): EditorState {
            return if (isEnabled) {
                EditorState.ACTIVE
            } else {
                EditorState.INACTIVE
            }
        }
    }

    /**
     * The window config editor state.
     *
     * @property isEnabled If the editor is currently enabled.
     * @property isMoveGesture If an editor move gesture is ongoing. If true, implies that the editor is enabled.
     * @property isResizeGesture If an editor resize gesture is ongoing. If true, implies that the editor is enabled.
     *
     * @see Editor
     */
    enum class EditorState(
        val isEnabled: Boolean = false,
        val isMoveGesture: Boolean = false,
        val isResizeGesture: Boolean = false,
    ) {
        /**
         * The editor is disabled.
         */
        INACTIVE,

        /**
         * The editor is enabled without any ongoing gesture.
         */
        ACTIVE(isEnabled = true),

        /**
         * The editor is enabled and a move gesture is ongoing.
         */
        ACTIVE_MOVE_GESTURE(isEnabled = true, isMoveGesture = true),

        /**
         * The editor is enabled and a resize gesture is ongoing.
         */
        ACTIVE_RESIZE_GESTURE(isEnabled = true, isResizeGesture = true);

        /**
         * Indicates if any editor gesture is ongoing. If true, implies that the editor is enabled.
         */
        val isAnyGesture: Boolean
            get() = isMoveGesture || isResizeGesture
    }
}

/**
 * The insets [ImeWindowController.onComputeInsets] reports, see [ImeWindowController.reportedInsets].
 *
 * @property insetsTop The content and visible top inset in px.
 * @property touchableBoundsPx The touchable bounds in px.
 */
data class ReportedImeInsets(val insetsTop: Int, val touchableBoundsPx: IntRect)

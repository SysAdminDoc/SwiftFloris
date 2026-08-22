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

package dev.patrickgold.florisboard.ime.text.keyboard

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.animation.AccelerateInterpolator
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.toSize
import dev.patrickgold.florisboard.FlorisImeService
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.editorInstance
import dev.patrickgold.florisboard.glideTypingManager
import dev.patrickgold.florisboard.ime.bidi.NastaliqFontProvider
import dev.patrickgold.florisboard.ime.editor.OperationScope
import dev.patrickgold.florisboard.ime.editor.OperationUnit
import dev.patrickgold.florisboard.ime.input.InputEventDispatcher
import dev.patrickgold.florisboard.ime.keyboard.ComputingEvaluator
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.keyboard.KeyData
import dev.patrickgold.florisboard.ime.keyboard.KeyboardMode
import dev.patrickgold.florisboard.ime.keyboard.SpaceBarMode
import dev.patrickgold.florisboard.ime.nlp.SuggestionPrivacyPolicy
import dev.patrickgold.florisboard.ime.nlp.TouchDecoderCandidate
import dev.patrickgold.florisboard.ime.popup.ExceptionsForKeyCodes
import dev.patrickgold.florisboard.ime.popup.PopupUiController
import dev.patrickgold.florisboard.ime.popup.rememberPopupUiController
import dev.patrickgold.florisboard.ime.text.gestures.GlideTrailTheme
import dev.patrickgold.florisboard.ime.text.gestures.GlideTypingGesture
import dev.patrickgold.florisboard.ime.text.gestures.SpaceTouchpadPolicy
import dev.patrickgold.florisboard.ime.text.gestures.SwipeAction
import dev.patrickgold.florisboard.ime.text.gestures.SwipeGesture
import dev.patrickgold.florisboard.ime.text.gestures.SwipeSensitivityPolicy
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import dev.patrickgold.florisboard.ime.text.key.KeyType
import dev.patrickgold.florisboard.ime.text.key.KeyVariation
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.ime.window.ImeWindowConstraints
import dev.patrickgold.florisboard.ime.window.ImeWindowSpec
import dev.patrickgold.florisboard.ime.window.LocalImeVerticalHingeBounds
import dev.patrickgold.florisboard.ime.window.LocalWindowController
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.lib.FlorisRect
import dev.patrickgold.florisboard.lib.Pointer
import dev.patrickgold.florisboard.lib.PointerMap
import dev.patrickgold.florisboard.lib.devtools.LogTopic
import dev.patrickgold.florisboard.lib.devtools.flogDebug
import dev.patrickgold.florisboard.lib.toIntOffset
import dev.patrickgold.florisboard.nlpManager
import dev.patrickgold.florisboard.subtypeManager
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.isActive
import org.florisboard.lib.android.isOrientationLandscape
import org.florisboard.lib.compose.DisposableLifecycleEffect
import org.florisboard.lib.snygg.SnyggSelector
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.SnyggIcon
import org.florisboard.lib.snygg.ui.SnyggText
import org.florisboard.lib.snygg.ui.rememberSnyggThemeQuery
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TextKeyboardLayout(
    modifier: Modifier = Modifier,
    evaluator: ComputingEvaluator,
): Unit = with(LocalDensity.current) {
    val prefs by FlorisPreferenceStore
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val glideTypingManager by context.glideTypingManager()

    val keyboard = evaluator.keyboard as TextKeyboard
    val glideEnabledInternal by prefs.glide.enabled.collectAsState()
    val glideEnglishEnabled by prefs.glide.enabledEnglish.collectAsState()
    val glideGermanEnabled by prefs.glide.enabledGerman.collectAsState()
    val glideSpanishEnabled by prefs.glide.enabledSpanish.collectAsState()
    val glideFrenchEnabled by prefs.glide.enabledFrench.collectAsState()
    val glideItalianEnabled by prefs.glide.enabledItalian.collectAsState()
    val glidePortugueseEnabled by prefs.glide.enabledPortuguese.collectAsState()
    val glideLanguageEnabled = when (evaluator.subtype.primaryLocale.language) {
        "en" -> glideEnglishEnabled
        "de" -> glideGermanEnabled
        "es" -> glideSpanishEnabled
        "fr" -> glideFrenchEnabled
        "it" -> glideItalianEnabled
        "pt" -> glidePortugueseEnabled
        else -> false
    }
    val glideEnabled = glideEnabledInternal && glideLanguageEnabled && evaluator.editorInfo.isRichInputEditor &&
        evaluator.state.keyVariation != KeyVariation.PASSWORD
    val glideShowTrailPref by prefs.glide.showTrail.collectAsState()
    // ROADMAP §6 N8.4 — Respect Android's reduced-motion (Developer Options →
    // Animator duration scale = 0). Read once on enter; cheap (single ContentProvider
    // query) and stays correct because configuration changes recompose this layout.
    val animatorDurationScale = remember(configuration) {
        runCatching {
            android.provider.Settings.Global.getFloat(
                context.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        }.getOrDefault(1f)
    }
    val reducedMotion = animatorDurationScale == 0f
    val glideShowTrail = glideShowTrailPref && !reducedMotion
    val glideTrailTheme by prefs.glide.trailTheme.collectAsState()
    val glideTrailStyle = rememberSnyggThemeQuery(FlorisImeUi.GlideTrail.elementName)
    val glideTrailAccent = glideTrailStyle.foreground(default = Color.Green).let { c ->
        // Guard against transparent theme resolution — ensure the accent is visible.
        if (c.alpha < 0.1f) Color.Green else c
    }

    val controller = remember { TextKeyboardLayoutController(context) }.also {
        it.keyboard = keyboard
        if (glideEnabled && keyboard.mode == KeyboardMode.CHARACTERS) {
            val keys = keyboard.keys().asSequence().toList()
            glideTypingManager.setLayout(keys)
        }
    }
    val touchEventChannel = remember { Channel<MotionEvent>(64) }

    fun resetAllKeys() {
        try {
            val event = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
            controller.onTouchEventInternal(event)
            controller.popupUiController.hide()
            event.recycle()
        } catch (_: Throwable) {
            // Ignore
        }
    }

    DisposableEffect(Unit) {
        controller.glideTypingDetector.registerListener(controller)
        controller.glideTypingDetector.registerListener(glideTypingManager)
        onDispose {
            controller.glideTypingDetector.unregisterListener(controller)
            controller.glideTypingDetector.unregisterListener(glideTypingManager)
            resetAllKeys()
            // The consuming LaunchedEffect is cancelled on disposal, so any
            // events still queued mid-gesture (text -> media mode switch,
            // theme reload) would otherwise never be recycled and deplete
            // the MotionEvent pool over repeated mode switches.
            touchEventChannel.close()
            while (true) {
                val event = touchEventChannel.tryReceive().getOrNull() ?: break
                event.recycle()
            }
        }
    }

    DisposableLifecycleEffect(
        onResume = { /* Do nothing */ },
        onPause = { resetAllKeys() },
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(FlorisImeSizing.keyboardUiHeight())
            .onGloballyPositioned { coords ->
                controller.size = coords.size.toSize()
            }
            .pointerInteropFilter { event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN,
                    MotionEvent.ACTION_POINTER_DOWN,
                    MotionEvent.ACTION_MOVE,
                    MotionEvent.ACTION_POINTER_UP,
                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL,
                        -> {
                        val clonedEvent = MotionEvent.obtain(event)
                        touchEventChannel
                            .trySend(clonedEvent)
                            .onFailure {
                                // Make sure to prevent MotionEvent memory leakage
                                // in case the input channel is full
                                clonedEvent.recycle()
                            }
                        return@pointerInteropFilter true
                    }
                }
                return@pointerInteropFilter false
            }
            .drawWithContent {
                drawContent()
                val radius = 20.0f
                val timeMs = System.currentTimeMillis()
                if (controller.fadingGlideRadius > 0) {
                    controller.drawGlideTrail(
                        this,
                        controller.fadingGlide,
                        radius,
                        glideTrailTheme,
                        glideTrailAccent,
                        timeMs,
                        fadeProgress = controller.fadingGlideRadius / 20.0f,
                    )
                }
                if (glideShowTrail && controller.isGliding && controller.glideDataForDrawing.isNotEmpty()) {
                    controller.glideActiveKeys.values.forEach { key ->
                        val bounds = key.visibleBounds
                        val highlightColor = glideTrailTheme.colorAt(1f, timeMs, glideTrailAccent)
                        drawRoundRect(
                            color = highlightColor.copy(alpha = 0.12f),
                            topLeft = Offset(bounds.left, bounds.top),
                            size = Size(bounds.width, bounds.height),
                            cornerRadius = CornerRadius(8f, 8f),
                        )
                    }
                    controller.glideDataForDrawing.values.forEach { gestureData ->
                        controller.drawGlideTrail(
                            this,
                            gestureData,
                            radius,
                            glideTrailTheme,
                            glideTrailAccent,
                            timeMs,
                        )
                    }
                }
            },
    ) {
        val keyboardWidth = constraints.maxWidth.toFloat()
        val keyboardHeight = constraints.maxHeight.toFloat()
        val keyboardRowBaseHeight = FlorisImeSizing.keyboardRowBaseHeight

        val windowController = LocalWindowController.current
        val windowSpec by windowController.activeWindowSpec.collectAsState()
        val activeWindowInsets by windowController.activeWindowInsets.collectAsState()
        val keyMarginH by remember { derivedStateOf { windowSpec.keyMarginH.toPx() } }
        val keyMarginV by remember { derivedStateOf { windowSpec.keyMarginV.toPx() } }

        val splitConstraints = (windowSpec as? ImeWindowSpec.Fixed)
            ?.constraints as? ImeWindowConstraints.Fixed.Split
        val splitGutterPx = TextKeyboardSplitLayout.gutterPx(
            keyboardMode = keyboard.mode,
            windowSpec = windowSpec,
            defaultGutterPx = splitConstraints?.defaultGutter?.toPx() ?: 0f,
            keyboardWidthPx = keyboardWidth,
        )
        val localHingeBounds = LocalImeVerticalHingeBounds.current?.let { hingeBounds ->
            activeWindowInsets?.boundsPx?.let { windowBounds ->
                hingeBounds.translatedBy(
                    dx = -windowBounds.left.toFloat(),
                    dy = -windowBounds.top.toFloat(),
                )
            }
        }
        val hingePlacement = TextKeyboardSplitLayout.hingePlacement(
            keyboardMode = keyboard.mode,
            windowSpec = windowSpec,
            defaultGutterPx = splitConstraints?.defaultGutter?.toPx() ?: 0f,
            keyboardWidthPx = keyboardWidth,
            keyboardHeightPx = keyboardHeight,
            hingeBounds = localHingeBounds,
        )
        val effectiveSplitGutterPx = hingePlacement?.gutterPx ?: splitGutterPx
        val layoutKeyboardWidth = TextKeyboardSplitLayout.layoutWidthPx(keyboardWidth, effectiveSplitGutterPx)

        val desiredKey = remember(
            keyboard, keyboardWidth, layoutKeyboardWidth, keyboardHeight, keyMarginH, keyMarginV,
            keyboardRowBaseHeight, evaluator, effectiveSplitGutterPx, hingePlacement
        ) {
            TextKey(data = TextKeyData.UNSPECIFIED).also { desiredKey ->
                desiredKey.touchBounds.apply {
                    width = layoutKeyboardWidth / 10f
                    height = TextKeyboardLayoutPolicy.desiredTouchHeightPx(
                        mode = keyboard.mode,
                        rowCount = keyboard.rowCount,
                        keyboardHeightPx = keyboardHeight,
                        rowBaseHeightPx = keyboardRowBaseHeight.toPx(),
                    )
                }
                desiredKey.visibleBounds.applyFrom(desiredKey.touchBounds).deflateBy(keyMarginH, keyMarginV)
                keyboard.layout(layoutKeyboardWidth, keyboardHeight, desiredKey, true)
                if (effectiveSplitGutterPx > 0f) {
                    SplitGutterPostPass.apply(
                        keyboard = keyboard,
                        gutterPx = effectiveSplitGutterPx,
                        placement = hingePlacement,
                    )
                }
            }
        }

        val latestEvaluator = rememberUpdatedState(evaluator)
        val popupUiController = rememberPopupUiController(
            key1 = keyboard,
            key2 = desiredKey,
            boundsProvider = { key ->
                TextKeyboardLayoutPolicy.popupBounds(
                    keyVisibleBounds = key.visibleBounds,
                    desiredVisibleBounds = desiredKey.visibleBounds,
                    isLandscape = configuration.isOrientationLandscape(),
                )
            },
            isSuitableForBasicPopup = { key ->
                if (PasswordFieldPopupGate.shouldSuppressPopups(latestEvaluator.value.state.keyVariation)) {
                    false
                } else if (key is TextKey) {
                    val keyCode = key.computedData.code
                    val keyType = key.computedData.type
                    val numeric = keyboard.mode == KeyboardMode.NUMERIC ||
                        keyboard.mode == KeyboardMode.PHONE || keyboard.mode == KeyboardMode.PHONE2 ||
                        keyboard.mode == KeyboardMode.NUMERIC_ADVANCED && keyType == KeyType.NUMERIC
                    keyCode > KeyCode.SPACE && keyCode != KeyCode.CJK_SPACE && !numeric
                } else {
                    true
                }
            },
            isSuitableForExtendedPopup = { key ->
                if (PasswordFieldPopupGate.shouldSuppressPopups(latestEvaluator.value.state.keyVariation)) {
                    false
                } else if (key is TextKey) {
                    val keyCode = key.computedData.code
                    keyCode > KeyCode.SPACE && keyCode != KeyCode.CJK_SPACE || ExceptionsForKeyCodes.contains(keyCode)
                } else {
                    true
                }
            },
        )
        popupUiController.evaluator = evaluator
        popupUiController.keyHintConfiguration = prefs.keyboard.keyHintConfiguration()
        controller.popupUiController = popupUiController
        val debugShowTouchBoundaries by prefs.devtools.showKeyTouchBoundaries.collectAsState()
        for (textKey in keyboard.keys()) {
            TextKeyButton(
                textKey, evaluator, desiredKey,
                debugShowTouchBoundaries,
                controller = controller,
                reducedMotion = reducedMotion,
                honeycombShape = keyboard.layoutStyle == TextKeyboardLayoutStyle.Honeycomb,
            )
        }

        popupUiController.RenderPopups()
    }

    LaunchedEffect(Unit) {
        for (event in touchEventChannel) {
            if (!isActive) break
            controller.onTouchEventInternal(event)
            event.recycle()
        }
    }
}

private val SwipeAction.isSubtypeSwitchAction: Boolean
    get() = this == SwipeAction.SWITCH_TO_PREV_SUBTYPE || this == SwipeAction.SWITCH_TO_NEXT_SUBTYPE

private val SwipeGesture.Direction.isCardinal: Boolean
    get() = when (this) {
        SwipeGesture.Direction.UP,
        SwipeGesture.Direction.DOWN,
        SwipeGesture.Direction.LEFT,
        SwipeGesture.Direction.RIGHT -> true
        else -> false
    }

@Composable
private fun TextKeyButton(
    key: TextKey,
    evaluator: ComputingEvaluator,
    desiredKey: TextKey,
    debugShowTouchBoundaries: Boolean,
    controller: TextKeyboardLayoutController,
    reducedMotion: Boolean,
    honeycombShape: Boolean = false,
) = with(LocalDensity.current) {
    // ROADMAP §6 N8.3a — context needed for the localized
    // `keyContentDescription` string-resource lookups.
    val context = LocalContext.current
    val attributes = mapOf(
        FlorisImeUi.Attr.Code to key.computedData.code,
        FlorisImeUi.Attr.Mode to evaluator.keyboard.mode.toString(),
        FlorisImeUi.Attr.ShiftState to evaluator.state.inputShiftState.toString(),
    )
    val subtypeLanguage = evaluator.subtype.primaryLocale.language
    val nastaliqFontFamily = remember(context, subtypeLanguage) {
        if (NastaliqFontProvider.isUrduLanguage(subtypeLanguage)) {
            NastaliqFontProvider.bundledFontFamily(context)
        } else {
            null
        }
    }
    val selector = when {
        !key.isEnabled -> SnyggSelector.DISABLED
        key.isPressed -> SnyggSelector.PRESSED
        else -> SnyggSelector.NONE
    }
    val size = remember(key, desiredKey) {
        key.visibleBounds.size.toDpSize()
    }
    // ROADMAP §6 N8.3 — TalkBack content description per key. Uses the visible
    // label when present (covers letters, numbers, punctuation) and falls back to
    // a stable code-derived label for special keys (Shift, Backspace, Enter, Space,
    // arrow keys, etc.) so screen-reader users hear the key's purpose, not "button".
    // §6 N8.3a — localized via string resources (Crowdin pipeline) instead of
    // the hard-coded English fallback when [context] is present.
    val keyDescription = remember(key.computedData.code, key.label, key.hintedLabel, context) {
        keyContentDescription(context, key.computedData.code, key.label, key.hintedLabel)
    }
    // ROADMAP §6 N3.4 — pressed-key 1.03× scale-up over 60ms gives the keypress
    // visible "depress" feedback SwiftKey/Gboard ship without changing the
    // touch-target geometry (graphicsLayer scales the visual only). The animation
    // recovers to 1.0× over 80ms on release for a snappy spring-back, then
    // converges on the static glyph weight from the PRESSED Snygg selector.
    // §6 N8.4 — reduced-motion users (Developer Options → Animator duration scale
    // = 0) get a static 1.0× — the PRESSED Snygg selector still flips colors so
    // the press still reads visually, just without animation.
    val pressTarget = if (key.isPressed && !reducedMotion) 1.03f else 1.0f
    val pressScale by animateFloatAsState(
        targetValue = pressTarget,
        animationSpec = if (reducedMotion) {
            tween(durationMillis = 0)
        } else {
            tween(durationMillis = if (key.isPressed) 60 else 80)
        },
        label = "TextKeyButton.pressScale",
    )
    val visualBounds = key.visibleBounds
    val semanticsBounds = keyAccessibilityBounds(key)
    Box(
        modifier = Modifier
            .requiredSize(semanticsBounds.size.toDpSize())
            .absoluteOffset { semanticsBounds.topLeft.toIntOffset() }
            .semantics {
                contentDescription = keyDescription
                role = Role.Button
                if (key.isEnabled && key.isVisible) {
                    onClick(label = keyDescription) {
                        controller.performAccessibilityClick(key)
                    }
                } else {
                    disabled()
                }
            },
    )
    val keyModifier = Modifier
        .requiredSize(size)
        .absoluteOffset { visualBounds.topLeft.toIntOffset() }
        .graphicsLayer {
            scaleX = pressScale
            scaleY = pressScale
            transformOrigin = TransformOrigin.Center
        }
        .let { base -> if (honeycombShape) base.clip(HoneycombHexShape) else base }
        .clearAndSetSemantics { }

    SnyggBox(
        FlorisImeUi.Key.elementName,
        attributes = attributes,
        selector = selector,
        modifier = keyModifier,
    ) {
        val isTelPadKey = key.computedData.type == KeyType.NUMERIC && evaluator.keyboard.mode == KeyboardMode.PHONE
        val isVoiceCommaKey = evaluator.keyboard.mode == KeyboardMode.CHARACTERS && key.computedData.code == 44
        val isPunctuationClusterKey = evaluator.keyboard.mode == KeyboardMode.CHARACTERS && key.computedData.code == 46
        key.label?.let { label ->
            var customLabel = label
            if (key.computedData.code == KeyCode.SPACE) {
                val prefs by FlorisPreferenceStore
                val spaceBarMode by prefs.keyboard.spaceBarMode.collectAsState()
                when (spaceBarMode) {
                    SpaceBarMode.NOTHING -> return@let
                    SpaceBarMode.CURRENT_LANGUAGE -> return@let
                    SpaceBarMode.SPACE_BAR_KEY -> customLabel = "␣"
                }
            }
            SnyggText(
                modifier = Modifier
                    .wrapContentSize()
                    .align(
                        when {
                            isTelPadKey -> BiasAlignment(-0.5f, 0f)
                            isVoiceCommaKey || isPunctuationClusterKey -> BiasAlignment(0f, 0.58f)
                            else -> Alignment.Center
                        }
                    ),
                text = customLabel,
                fontFamilyOverride = nastaliqFontFamily.takeIf {
                    NastaliqFontProvider.shouldRouteText(subtypeLanguage, customLabel)
                },
            )
        }
        if (isVoiceCommaKey) {
            SnyggIcon(
                modifier = Modifier
                    .align(BiasAlignment(0f, -0.55f))
                    .alpha(0.56f),
                imageVector = Icons.Default.KeyboardVoice,
                contentDescription = null,
            )
        }
        if (isPunctuationClusterKey) {
            SnyggText(
                elementName = FlorisImeUi.KeyHint.elementName,
                attributes = attributes,
                selector = selector,
                modifier = Modifier
                    .wrapContentSize()
                    .align(BiasAlignment(0f, -0.62f)),
                text = "!?",
            )
        }
        key.hintedLabel?.let { hintedLabel ->
            SnyggText(
                elementName = FlorisImeUi.KeyHint.elementName,
                attributes = attributes,
                selector = selector,
                modifier = Modifier
                    .wrapContentSize()
                    .align(if (isTelPadKey) BiasAlignment(0.5f, 0f) else Alignment.TopEnd),
                text = hintedLabel,
                fontFamilyOverride = nastaliqFontFamily.takeIf {
                    NastaliqFontProvider.shouldRouteText(subtypeLanguage, hintedLabel)
                },
            )
        }
        key.foregroundImageVector?.let { imageVector ->
            SnyggIcon(
                modifier = Modifier.align(Alignment.Center),
                imageVector = imageVector,
                contentDescription = null,
            )
        }
    }
    if (debugShowTouchBoundaries) {
        Box(
            modifier = Modifier
                .requiredSize(key.touchBounds.size.toDpSize())
                .absoluteOffset { key.touchBounds.topLeft.toIntOffset() }
                .border(Dp.Hairline, Color.Red),
        )
    }
}

internal fun keyAccessibilityBounds(key: TextKey): FlorisRect {
    return if (key.touchBounds.isNotEmpty()) key.touchBounds else key.visibleBounds
}

/**
 * ROADMAP §6 N8.3 — TalkBack content description for a single keyboard key.
 *
 * Strategy:
 *  - For printable keys ([label] is non-blank and not a Snygg layout artifact),
 *    use the label directly (a, b, ?, …) — this is what TalkBack already says
 *    for letter keys via the underlying SnyggText, but providing it on the
 *    button container ensures the press target itself announces correctly.
 *  - For control / system keys, return a short localized resource string so
 *    the semantic key target announces intent instead of falling back to
 *    "button" or a layout-internal token.
 */
internal fun keyContentDescription(
    context: android.content.Context,
    code: Int,
    label: String?,
    hintedLabel: String? = null,
): String {
    val res = context.resources
    return keyContentDescription(
        code = code,
        label = label,
        hintedLabel = hintedLabel,
        getString = res::getString,
        getFormattedString = { resId, arg -> res.getString(resId, arg) },
    )
}

internal fun keyContentDescription(
    code: Int,
    label: String?,
    hintedLabel: String? = null,
    getString: (Int) -> String,
    getFormattedString: (Int, String) -> String,
): String {
    // §6 N8.3 + N8.3a — Localized TalkBack description. Each special-key
    // label resolves to a Crowdin-routed string resource so non-English
    // users get localised announcements instead of the hard-coded English
    // fallback. Letter / number / punctuation keys use the visible label
    // directly (already locale-correct since it's the typed glyph).
    val hintSuffix = if (!hintedLabel.isNullOrBlank() && hintedLabel.length <= 4) {
        getFormattedString(dev.patrickgold.florisboard.R.string.a11y__key__alternative_suffix, hintedLabel)
    } else {
        ""
    }
    if (!label.isNullOrBlank() && label.length <= 4 && label.all { !it.isISOControl() }) {
        return label + hintSuffix
    }
    val resId = when (code) {
        KeyCode.SHIFT -> dev.patrickgold.florisboard.R.string.a11y__key__shift
        KeyCode.DELETE -> dev.patrickgold.florisboard.R.string.a11y__key__delete
        KeyCode.DELETE_WORD -> dev.patrickgold.florisboard.R.string.a11y__key__delete_word
        KeyCode.FORWARD_DELETE -> dev.patrickgold.florisboard.R.string.a11y__key__forward_delete
        KeyCode.FORWARD_DELETE_WORD -> dev.patrickgold.florisboard.R.string.a11y__key__forward_delete_word
        KeyCode.ENTER -> dev.patrickgold.florisboard.R.string.a11y__key__enter
        KeyCode.SPACE, KeyCode.CJK_SPACE -> dev.patrickgold.florisboard.R.string.a11y__key__space
        KeyCode.TAB -> dev.patrickgold.florisboard.R.string.a11y__key__tab
        KeyCode.ESCAPE -> dev.patrickgold.florisboard.R.string.a11y__key__escape
        KeyCode.ARROW_LEFT -> dev.patrickgold.florisboard.R.string.a11y__key__arrow_left
        KeyCode.ARROW_RIGHT -> dev.patrickgold.florisboard.R.string.a11y__key__arrow_right
        KeyCode.ARROW_UP -> dev.patrickgold.florisboard.R.string.a11y__key__arrow_up
        KeyCode.ARROW_DOWN -> dev.patrickgold.florisboard.R.string.a11y__key__arrow_down
        KeyCode.MOVE_START_OF_LINE -> dev.patrickgold.florisboard.R.string.a11y__key__move_start_of_line
        KeyCode.MOVE_END_OF_LINE -> dev.patrickgold.florisboard.R.string.a11y__key__move_end_of_line
        KeyCode.MOVE_START_OF_PAGE -> dev.patrickgold.florisboard.R.string.a11y__key__move_start_of_page
        KeyCode.MOVE_END_OF_PAGE -> dev.patrickgold.florisboard.R.string.a11y__key__move_end_of_page
        KeyCode.PAGE_UP -> dev.patrickgold.florisboard.R.string.a11y__key__page_up
        KeyCode.PAGE_DOWN -> dev.patrickgold.florisboard.R.string.a11y__key__page_down
        KeyCode.LANGUAGE_SWITCH -> dev.patrickgold.florisboard.R.string.a11y__key__language_switch
        KeyCode.SHOW_SUBTYPE_PICKER, KeyCode.IME_SUBTYPE_PICKER -> dev.patrickgold.florisboard.R.string.a11y__key__subtype_picker
        KeyCode.IME_NEXT_SUBTYPE -> dev.patrickgold.florisboard.R.string.a11y__key__next_subtype
        KeyCode.IME_PREV_SUBTYPE -> dev.patrickgold.florisboard.R.string.a11y__key__prev_subtype
        KeyCode.SYSTEM_INPUT_METHOD_PICKER -> dev.patrickgold.florisboard.R.string.a11y__key__input_method_picker
        KeyCode.SYSTEM_NEXT_INPUT_METHOD -> dev.patrickgold.florisboard.R.string.a11y__key__next_input_method
        KeyCode.SYSTEM_PREV_INPUT_METHOD -> dev.patrickgold.florisboard.R.string.a11y__key__prev_input_method
        KeyCode.CLIPBOARD_COPY -> dev.patrickgold.florisboard.R.string.a11y__key__clipboard_copy
        KeyCode.CLIPBOARD_CUT -> dev.patrickgold.florisboard.R.string.a11y__key__clipboard_cut
        KeyCode.CLIPBOARD_PASTE -> dev.patrickgold.florisboard.R.string.a11y__key__clipboard_paste
        KeyCode.CLIPBOARD_SELECT -> dev.patrickgold.florisboard.R.string.a11y__key__clipboard_select
        KeyCode.CLIPBOARD_SELECT_ALL -> dev.patrickgold.florisboard.R.string.a11y__key__clipboard_select_all
        KeyCode.CLIPBOARD_CLEAR_HISTORY -> dev.patrickgold.florisboard.R.string.a11y__key__clipboard_clear_history
        KeyCode.CLIPBOARD_CLEAR_FULL_HISTORY -> dev.patrickgold.florisboard.R.string.a11y__key__clipboard_clear_full_history
        KeyCode.CLIPBOARD_CLEAR_PRIMARY_CLIP -> dev.patrickgold.florisboard.R.string.a11y__key__clipboard_clear_primary_clip
        KeyCode.TOGGLE_FLOATING_WINDOW -> dev.patrickgold.florisboard.R.string.a11y__key__toggle_floating_window
        KeyCode.TOGGLE_COMPACT_LAYOUT -> dev.patrickgold.florisboard.R.string.a11y__key__toggle_compact_layout
        KeyCode.COMPACT_LAYOUT_TO_LEFT -> dev.patrickgold.florisboard.R.string.a11y__key__compact_layout_left
        KeyCode.COMPACT_LAYOUT_TO_RIGHT -> dev.patrickgold.florisboard.R.string.a11y__key__compact_layout_right
        KeyCode.SPLIT_LAYOUT -> dev.patrickgold.florisboard.R.string.a11y__key__split_layout
        KeyCode.MERGE_LAYOUT -> dev.patrickgold.florisboard.R.string.a11y__key__merge_layout
        KeyCode.TOGGLE_RESIZE_MODE -> dev.patrickgold.florisboard.R.string.a11y__key__toggle_resize_mode
        KeyCode.VOICE_INPUT -> dev.patrickgold.florisboard.R.string.a11y__key__voice_input
        KeyCode.IME_SHOW_UI -> dev.patrickgold.florisboard.R.string.a11y__key__show_ui
        KeyCode.IME_HIDE_UI -> dev.patrickgold.florisboard.R.string.a11y__key__hide_ui
        KeyCode.IME_UI_MODE_TEXT -> dev.patrickgold.florisboard.R.string.a11y__key__ime_text
        KeyCode.IME_UI_MODE_MEDIA -> dev.patrickgold.florisboard.R.string.a11y__key__ime_media
        KeyCode.IME_UI_MODE_CLIPBOARD -> dev.patrickgold.florisboard.R.string.a11y__key__ime_clipboard
        KeyCode.TOGGLE_SMARTBAR_VISIBILITY -> dev.patrickgold.florisboard.R.string.a11y__key__toggle_smartbar_visibility
        KeyCode.TOGGLE_ACTIONS_OVERFLOW -> dev.patrickgold.florisboard.R.string.a11y__key__toggle_actions_overflow
        KeyCode.TOGGLE_ACTIONS_EDITOR -> dev.patrickgold.florisboard.R.string.a11y__key__toggle_actions_editor
        KeyCode.TOGGLE_INCOGNITO_MODE -> dev.patrickgold.florisboard.R.string.a11y__key__toggle_incognito
        KeyCode.TOGGLE_AUTOCORRECT -> dev.patrickgold.florisboard.R.string.a11y__key__toggle_autocorrect
        KeyCode.UNDO -> dev.patrickgold.florisboard.R.string.a11y__key__undo
        KeyCode.REDO -> dev.patrickgold.florisboard.R.string.a11y__key__redo
        KeyCode.VIEW_CHARACTERS -> dev.patrickgold.florisboard.R.string.a11y__key__view_characters
        KeyCode.VIEW_SYMBOLS -> dev.patrickgold.florisboard.R.string.a11y__key__view_symbols
        KeyCode.VIEW_SYMBOLS2 -> dev.patrickgold.florisboard.R.string.a11y__key__view_symbols2
        KeyCode.VIEW_NUMERIC -> dev.patrickgold.florisboard.R.string.a11y__key__view_numeric
        KeyCode.VIEW_NUMERIC_ADVANCED -> dev.patrickgold.florisboard.R.string.a11y__key__view_numeric_advanced
        KeyCode.VIEW_PHONE -> dev.patrickgold.florisboard.R.string.a11y__key__view_phone
        KeyCode.VIEW_PHONE2 -> dev.patrickgold.florisboard.R.string.a11y__key__view_phone2
        KeyCode.SETTINGS -> dev.patrickgold.florisboard.R.string.a11y__key__settings
        else -> return (label?.takeIf { it.isNotBlank() }
            ?: getString(dev.patrickgold.florisboard.R.string.a11y__key__generic)) + hintSuffix
    }
    return getString(resId) + hintSuffix
}

@Suppress("unused_parameter")
private class TextKeyboardLayoutController(
    context: Context,
) : SwipeGesture.Listener, GlideTypingGesture.Listener {
    private val prefs by FlorisPreferenceStore
    private val editorInstance by context.editorInstance()
    private val keyboardManager by context.keyboardManager()
    private val nlpManager by context.nlpManager()
    private val subtypeManager by context.subtypeManager()

    private val inputEventDispatcher get() = keyboardManager.inputEventDispatcher
    private val inputFeedbackController get() = FlorisImeService.inputFeedbackController()
    private val keyHintConfiguration = prefs.keyboard.keyHintConfiguration()
    private val pointerMap: PointerMap<TouchPointer> = PointerMap { TouchPointer() }
    lateinit var popupUiController: PopupUiController

    fun performAccessibilityClick(key: TextKey): Boolean {
        if (!key.isEnabled || !key.isVisible || key.computedData.code == KeyCode.UNSPECIFIED) {
            return false
        }
        inputEventDispatcher.sendDownUp(key.computedData)
        return true
    }

    private var initSelectionStart: Int = 0
    private var initSelectionEnd: Int = 0
    var isGliding by mutableStateOf(false)

    val glideTypingDetector = GlideTypingGesture.Detector(context)
    val glideDataForDrawing = mutableStateMapOf<Int, List<Pair<GlideTypingGesture.Detector.Position, Long>>>()
    val fadingGlide = mutableStateListOf<Pair<GlideTypingGesture.Detector.Position, Long>>()
    var fadingGlideRadius by mutableFloatStateOf(0.0f)
    val glideActiveKeys = mutableStateMapOf<Int, TextKey>()
    private val swipeGestureDetector = SwipeGesture.Detector(this)

    lateinit var keyboard: TextKeyboard
    var size = Size.Zero

    val isGlideEnabled: Boolean get() = prefs.glide.enabled.get() &&
        prefs.glide.isEnabledForSubtype(subtypeManager.activeSubtype) &&
        editorInstance.activeInfo.isRichInputEditor &&
        keyboardManager.activeState.keyVariation != KeyVariation.PASSWORD

    private fun swipeSensitivityFor(pointer: TouchPointer): Int {
        val initialCode = pointer.initialKey?.computedData?.code
        val activeCode = pointer.activeKey?.computedData?.code
        return when {
            initialCode == KeyCode.DELETE -> prefs.gestures.deleteKeySwipeSensitivity.get()
            initialCode == KeyCode.SPACE || initialCode == KeyCode.CJK_SPACE -> {
                prefs.gestures.spaceBarSwipeSensitivity.get()
            }
            initialCode == KeyCode.SHIFT && (activeCode == KeyCode.SPACE || activeCode == KeyCode.CJK_SPACE) -> {
                prefs.gestures.spaceBarSwipeSensitivity.get()
            }
            !isGlideEnabled && usesLanguageSwitchSwipeAction() -> {
                prefs.gestures.languageSwitchSwipeSensitivity.get()
            }
            else -> SwipeSensitivityPolicy.DEFAULT_SENSITIVITY
        }
    }

    private fun usesLanguageSwitchSwipeAction(): Boolean {
        return prefs.gestures.swipeUp.get().isSubtypeSwitchAction ||
            prefs.gestures.swipeDown.get().isSubtypeSwitchAction ||
            prefs.gestures.swipeLeft.get().isSubtypeSwitchAction ||
            prefs.gestures.swipeRight.get().isSubtypeSwitchAction
    }

    fun onTouchEventInternal(event: MotionEvent) {
        flogDebug { "event=$event" }
        swipeGestureDetector.onTouchEvent(event)
        if (isGlideEnabled && keyboard.mode == KeyboardMode.CHARACTERS) {
            // The traced pointer is not necessarily the first one down, so resolving the glide's
            // origin key against a hardcoded id 0 left `initialKey` null for any other pointer —
            // which is what suppresses glides that begin on delete, shift or space. Fall back to
            // the pointer this event is about while the detector has not adopted one yet.
            val eventPointerId = event.getPointerId(event.actionIndex)
            val glidePointerId = when (event.actionMasked) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_POINTER_DOWN,
                -> eventPointerId
                else -> glideTypingDetector.tracedPointerId.takeIf { it != -1 } ?: eventPointerId
            }
            val glidePointer = pointerMap.findById(glidePointerId)
            val isNotBlocked = glidePointer?.hasTriggeredLongPress != true
            val initialKey = glidePointer?.initialKey ?: when (event.actionMasked) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_POINTER_DOWN,
                -> keyboard.getKeyForPos(event.getX(event.actionIndex), event.getY(event.actionIndex))
                else -> null
            }
            if (isNotBlocked && glideTypingDetector.onTouchEvent(event, initialKey)) {
                for (pointer in pointerMap) {
                    if (pointer.activeKey != null) {
                        onTouchCancelInternal(event, pointer)
                    }
                }
                if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                    pointerMap.clear()
                }
                isGliding = true
                return
            }
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)
                val pointer = pointerMap.add(pointerId, pointerIndex)
                if (pointer != null) {
                    swipeGestureDetector.onTouchDown(event, pointer)
                    onTouchDownInternal(event, pointer)
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)
                val oldPointer = pointerMap.findById(pointerId)
                if (oldPointer != null) {
                    swipeGestureDetector.onTouchCancel(event, oldPointer)
                    onTouchCancelInternal(event, oldPointer)
                    pointerMap.removeById(oldPointer.id)
                }
                // Search for active character keys and cancel them
                for (pointer in pointerMap) {
                    val activeKey = pointer.activeKey
                    if (activeKey != null && popupUiController.isSuitableForPopups(activeKey)) {
                        swipeGestureDetector.onTouchCancel(event, pointer)
                        onTouchUpInternal(event, pointer)
                    }
                }
                val pointer = pointerMap.add(pointerId, pointerIndex)
                if (pointer != null) {
                    swipeGestureDetector.onTouchDown(event, pointer)
                    onTouchDownInternal(event, pointer)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (pointerIndex in 0 until event.pointerCount) {
                    val pointerId = event.getPointerId(pointerIndex)
                    val pointer = pointerMap.findById(pointerId)
                    if (pointer != null) {
                        pointer.index = pointerIndex
                        val alwaysTriggerOnMove = (pointer.hasTriggeredGestureMove
                            && (pointer.initialKey?.computedData?.code == KeyCode.DELETE
                            && prefs.gestures.deleteKeySwipeLeft.get().let {
                                it == SwipeAction.DELETE_CHARACTERS_PRECISELY || it == SwipeAction.SELECT_CHARACTERS_PRECISELY
                            }
                            || pointer.initialKey?.computedData?.code == KeyCode.SPACE
                            || pointer.initialKey?.computedData?.code == KeyCode.CJK_SPACE))
                        if (
                            swipeGestureDetector.onTouchMove(
                                event,
                                pointer,
                                alwaysTriggerOnMove,
                                swipeSensitivityFor(pointer),
                            ) || pointer.hasTriggeredGestureMove
                        ) {
                            pointer.hasTriggeredGestureMove = true
                            pointer.activeKey?.let { activeKey ->
                                inputEventDispatcher.sendCancel(activeKey.computedDataOnDown)
                            }
                        } else {
                            onTouchMoveInternal(event, pointer)
                        }
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)
                val pointer = pointerMap.findById(pointerId)
                if (pointer != null) {
                    pointer.index = pointerIndex
                    if (
                        swipeGestureDetector.onTouchUp(
                            event,
                            pointer,
                            swipeSensitivityFor(pointer),
                        ) || pointer.hasTriggeredGestureMove
                    ) {
                        if (pointer.hasTriggeredGestureMove && pointer.initialKey?.computedData?.code == KeyCode.DELETE) {
                            val selection = editorInstance.activeContent.selection
                            if (selection.isSelectionMode) {
                                editorInstance.deleteBackwards(OperationUnit.CHARACTERS)
                            }
                        }
                        onTouchCancelInternal(event, pointer)
                    } else {
                        onTouchUpInternal(event, pointer)
                    }
                    pointerMap.removeById(pointer.id)
                }
            }
            MotionEvent.ACTION_UP -> {
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)
                for (pointer in pointerMap) {
                    if (pointer.id == pointerId) {
                        pointer.index = pointerIndex
                        if (
                            swipeGestureDetector.onTouchUp(
                                event,
                                pointer,
                                swipeSensitivityFor(pointer),
                            ) || pointer.hasTriggeredGestureMove
                        ) {
                            if (pointer.hasTriggeredGestureMove &&
                                pointer.initialKey?.computedData?.code == KeyCode.DELETE &&
                                prefs.gestures.deleteKeySwipeLeft.get() != SwipeAction.SELECT_CHARACTERS_PRECISELY &&
                                prefs.gestures.deleteKeySwipeLeft.get() != SwipeAction.SELECT_WORDS_PRECISELY) {
                                val selection = editorInstance.activeContent.selection
                                if (selection.isSelectionMode) {
                                    editorInstance.deleteBackwards(OperationUnit.CHARACTERS)
                                }
                            }
                            onTouchCancelInternal(event, pointer)
                        } else {
                            onTouchUpInternal(event, pointer)
                        }
                    } else {
                        swipeGestureDetector.onTouchCancel(event, pointer)
                        onTouchCancelInternal(event, pointer)
                    }
                }
                pointerMap.clear()
            }
            MotionEvent.ACTION_CANCEL -> {
                for (pointer in pointerMap) {
                    swipeGestureDetector.onTouchCancel(event, pointer)
                    onTouchCancelInternal(event, pointer)
                }
                pointerMap.clear()
            }
        }
    }

    private fun onTouchDownInternal(event: MotionEvent, pointer: TouchPointer) {
        flogDebug(LogTopic.TEXT_KEYBOARD_VIEW) { "pointer=$pointer" }

        val touchX = event.getX(pointer.index)
        val touchY = event.getY(pointer.index)
        val canCaptureTextTap = keyboard.mode == KeyboardMode.CHARACTERS &&
            keyboardManager.activeState.keyVariation != KeyVariation.PASSWORD
        val adaptiveTouchEnabled = SuggestionPrivacyPolicy.shouldRecordAdaptiveTouchSample(
            isAdaptiveTouchEnabled = prefs.correction.adaptiveTouchModel.get(),
            isIncognitoMode = keyboardManager.activeState.isIncognitoMode,
            keyVariation = keyboardManager.activeState.keyVariation,
        ) && keyboard.mode == KeyboardMode.CHARACTERS
        val calibration = prefs.correction.touchCalibrationProfile.get()
        val touchDecoderEnabled = prefs.correction.autoCorrect.get() &&
            canCaptureTextTap &&
            !keyboardManager.activeState.isIncognitoMode
        val initialKey = keyboard.getKeyForPos(touchX, touchY) ?: if (adaptiveTouchEnabled) {
            keyboard.getNearestKeyForPos(touchX, touchY, calibration.gapRescueDistanceFactor)
        } else {
            null
        }
        val key = if (initialKey != null && adaptiveTouchEnabled) {
            AdaptiveTouchModel.refine(
                keyboard = keyboard,
                primary = initialKey,
                touchX = touchX,
                touchY = touchY,
                minSamples = calibration.minSamplesPerKey,
                horizontalTolerance = calibration.neighbourHorizontalTolerance,
                verticalTolerance = calibration.neighbourVerticalTolerance,
            )
        } else {
            initialKey
        }
        if (key != null && key.isEnabled) {
            key.computedDataOnDown = key.computedData
            if (adaptiveTouchEnabled || touchDecoderEnabled) {
                pointer.adaptiveTouchKey = key
                pointer.adaptiveTouchX = touchX
                pointer.adaptiveTouchY = touchY
            }
            pointer.pressedKeyInfo = inputEventDispatcher.sendDown(
                data = key.computedData,
                onLongPress = onLongPress@ {
                    pointer.hasTriggeredLongPress = true
                    when (key.computedData.code) {
                        KeyCode.SPACE, KeyCode.CJK_SPACE -> {
                            when (prefs.gestures.spaceBarLongPress.get()) {
                                SwipeAction.NO_ACTION,
                                SwipeAction.INSERT_SPACE -> {
                                }
                                else -> {
                                    keyboardManager.executeSwipeAction(prefs.gestures.spaceBarLongPress.get())
                                }
                            }
                            true
                        }
                        KeyCode.SHIFT -> {
                            if (inputEventDispatcher.isUninterruptedEventSequence(key.computedData)) {
                                inputEventDispatcher.sendDownUp(TextKeyData.CAPS_LOCK)
                                inputFeedbackController?.keyLongPress(key.computedData)
                            }
                            // We always return false here to prevent blockade for the up touch event
                            false
                        }
                        KeyCode.LANGUAGE_SWITCH -> {
                            inputEventDispatcher.sendDownUp(TextKeyData.SYSTEM_INPUT_METHOD_PICKER)
                            true
                        }
                        else -> {
                            if (popupUiController.isSuitableForPopups(key) && key.computedPopups.getPopupKeys(
                                    keyHintConfiguration
                                ).isNotEmpty()
                            ) {
                                popupUiController.extend(key, size)
                                inputFeedbackController?.keyLongPress(key.computedData)
                                true
                            } else {
                                false
                            }
                        }
                    }
                },
            )
            if (prefs.keyboard.popupEnabled.get() && popupUiController.isSuitableForPopups(key)) {
                popupUiController.show(key)
            }
            inputFeedbackController?.keyPress(key.computedData)
            key.isPressed = true
            if (pointer.initialKey == null) {
                pointer.initialKey = key
            }
            pointer.activeKey = key
            initSelectionStart = editorInstance.activeContent.selection.start
            initSelectionEnd = editorInstance.activeContent.selection.end
        } else {
            pointer.activeKey = null
        }
    }

    private fun onTouchMoveInternal(event: MotionEvent, pointer: TouchPointer) {
        flogDebug(LogTopic.TEXT_KEYBOARD_VIEW) { "pointer=$pointer" }

        val initialKey = pointer.initialKey
        val activeKey = pointer.activeKey
        if (initialKey != null && activeKey != null) {
            if (popupUiController.isShowingExtendedPopup) {
                val x = event.getX(pointer.index)
                val y = event.getY(pointer.index)
                if (!popupUiController.propagateMotionEvent(activeKey, x, y)) {
                    onTouchCancelInternal(event, pointer)
                    onTouchDownInternal(event, pointer)
                }
            } else {
                if ((event.getX(pointer.index) < activeKey.visibleBounds.left - 0.1f * activeKey.visibleBounds.width)
                    || (event.getX(pointer.index) > activeKey.visibleBounds.right + 0.1f * activeKey.visibleBounds.width)
                    || (event.getY(pointer.index) < activeKey.visibleBounds.top - 0.35f * activeKey.visibleBounds.height)
                    || (event.getY(pointer.index) > activeKey.visibleBounds.bottom + 0.35f * activeKey.visibleBounds.height)
                ) {
                    onTouchCancelInternal(event, pointer)
                    onTouchDownInternal(event, pointer)
                }
            }
        }
    }

    private fun onTouchUpInternal(event: MotionEvent, pointer: TouchPointer) {
        flogDebug(LogTopic.TEXT_KEYBOARD_VIEW) { "pointer=$pointer" }
        pointer.pressedKeyInfo?.cancelJobs()
        pointer.pressedKeyInfo = null

        if (pointer.hasTriggeredMassSelection) {
            pointer.hasTriggeredMassSelection = false
            editorInstance.massSelection.end()
        }

        val initialKey = pointer.initialKey
        val activeKey = pointer.activeKey
        if (initialKey != null && activeKey != null) {
            activeKey.isPressed = false
            if (popupUiController.isSuitableForPopups(activeKey)) {
                val retData = popupUiController.getActiveKeyData(activeKey)
                if (retData != null && !pointer.hasTriggeredGestureMove) {
                    if (retData == activeKey.computedData) {
                        recordSuccessfulTapIfEligible(pointer, activeKey, retData)
                        if (activeKey.computedData != activeKey.computedDataOnDown) {
                            inputEventDispatcher.sendCancel(activeKey.computedDataOnDown)
                            inputEventDispatcher.sendDownUp(activeKey.computedData)
                        } else {
                            inputEventDispatcher.sendUp(activeKey.computedDataOnDown)
                        }
                    } else {
                        inputEventDispatcher.sendCancel(activeKey.computedDataOnDown)
                        inputEventDispatcher.sendDownUp(retData)
                    }
                } else {
                    inputEventDispatcher.sendCancel(activeKey.computedDataOnDown)
                }
                popupUiController.hide()
            } else {
                if (pointer.hasTriggeredGestureMove) {
                    inputEventDispatcher.sendCancel(activeKey.computedDataOnDown)
                } else {
                    recordSuccessfulTapIfEligible(pointer, activeKey, activeKey.computedData)
                    if (activeKey.computedData != activeKey.computedDataOnDown) {
                        inputEventDispatcher.sendCancel(activeKey.computedDataOnDown)
                        inputEventDispatcher.sendDownUp(activeKey.computedData)
                    } else {
                        inputEventDispatcher.sendUp(activeKey.computedDataOnDown)
                    }
                }
            }
            pointer.activeKey = null
        }
        pointer.hasTriggeredGestureMove = false
    }

    private fun recordSuccessfulTapIfEligible(pointer: TouchPointer, activeKey: TextKey, committedData: KeyData) {
        if (keyboard.mode != KeyboardMode.CHARACTERS) return
        if (pointer.hasTriggeredGestureMove || pointer.hasTriggeredLongPress) return
        if (pointer.adaptiveTouchKey !== activeKey) return
        if (committedData.code != activeKey.computedData.code) return
        if (SuggestionPrivacyPolicy.shouldRecordAdaptiveTouchSample(
                isAdaptiveTouchEnabled = prefs.correction.adaptiveTouchModel.get(),
                isIncognitoMode = keyboardManager.activeState.isIncognitoMode,
                keyVariation = keyboardManager.activeState.keyVariation,
            )) {
            AdaptiveTouchModel.recordTap(activeKey, pointer.adaptiveTouchX, pointer.adaptiveTouchY)
        }
        recordTouchDecoderEvidence(activeKey, committedData, pointer.adaptiveTouchX, pointer.adaptiveTouchY)
    }

    private fun recordTouchDecoderEvidence(
        activeKey: TextKey,
        committedData: KeyData,
        touchX: Float,
        touchY: Float,
    ) {
        val primaryText = committedData.asString(isForDisplay = false)
        if (primaryText.length != 1 || primaryText.none { it.isLetter() }) return
        val alternatives = keyboard.getNearbyKeysForPos(touchX, touchY)
            .asSequence()
            .filter { nearby -> nearby.key !== activeKey }
            .mapNotNull { nearby ->
                val alternativeText = nearby.key.computedData.asString(isForDisplay = false)
                if (alternativeText.length == 1 && alternativeText.any { it.isLetter() }) {
                    TouchDecoderCandidate(
                        text = alternativeText,
                        confidence = nearby.confidence,
                    )
                } else {
                    null
                }
            }
            .toList()
        nlpManager.recordTouchDecoderSample(primaryText, alternatives)
    }

    private fun onTouchCancelInternal(event: MotionEvent, pointer: TouchPointer) {
        flogDebug(LogTopic.TEXT_KEYBOARD_VIEW) { "pointer=$pointer" }
        pointer.pressedKeyInfo?.cancelJobs()
        pointer.pressedKeyInfo = null

        if (pointer.hasTriggeredMassSelection) {
            pointer.hasTriggeredMassSelection = false
            editorInstance.massSelection.end()
        }

        val activeKey = pointer.activeKey
        if (activeKey != null) {
            activeKey.isPressed = false
            inputEventDispatcher.sendCancel(activeKey.computedDataOnDown)
            if (popupUiController.isSuitableForPopups(activeKey)) {
                popupUiController.hide()
            }
            pointer.activeKey = null
        }
        pointer.hasTriggeredGestureMove = false
    }

    override fun onSwipe(event: SwipeGesture.Event): Boolean {
        val pointer = pointerMap.findById(event.pointerId) ?: return false
        val initialKey = pointer.initialKey ?: return false
        val activeKey = pointer.activeKey
        flogDebug(LogTopic.TEXT_KEYBOARD_VIEW)

        return when (initialKey.computedData.code) {
            KeyCode.DELETE -> handleDeleteSwipe(event)
            KeyCode.SPACE, KeyCode.CJK_SPACE -> handleSpaceSwipe(event)
            else -> when {
                (initialKey.computedData.code == KeyCode.SHIFT && activeKey?.computedData?.code == KeyCode.SPACE ||
                    initialKey.computedData.code == KeyCode.SHIFT && activeKey?.computedData?.code == KeyCode.CJK_SPACE) &&
                    event.type == SwipeGesture.Type.TOUCH_MOVE -> handleSpaceSwipe(event)
                initialKey.computedData.code == KeyCode.SHIFT && activeKey?.computedData?.code != KeyCode.SHIFT &&
                    event.type == SwipeGesture.Type.TOUCH_UP -> {
                    activeKey?.let {
                        inputEventDispatcher.sendUp(popupUiController.getActiveKeyData(it) ?: it.computedDataOnDown)
                    }
                    inputEventDispatcher.sendCancel(TextKeyData.SHIFT)
                    true
                }
                initialKey.computedData.code > KeyCode.SPACE && !popupUiController.isShowingExtendedPopup -> when {
                    shouldHandleSymbolFlick(event, pointer, initialKey) -> {
                        handleSymbolFlick(initialKey)
                    }
                    !isGlideEnabled && !pointer.hasTriggeredGestureMove -> when (event.type) {
                        SwipeGesture.Type.TOUCH_UP -> {
                            val swipeAction = when (event.direction) {
                                SwipeGesture.Direction.UP -> prefs.gestures.swipeUp.get()
                                SwipeGesture.Direction.DOWN -> prefs.gestures.swipeDown.get()
                                SwipeGesture.Direction.LEFT -> prefs.gestures.swipeLeft.get()
                                SwipeGesture.Direction.RIGHT -> prefs.gestures.swipeRight.get()
                                else -> SwipeAction.NO_ACTION
                            }
                            if (swipeAction != SwipeAction.NO_ACTION) {
                                keyboardManager.executeSwipeAction(swipeAction)
                                true
                            } else {
                                false
                            }
                        }
                        else -> false
                    }
                    else -> false
                }
                else -> false
            }
        }
    }

    private fun shouldHandleSymbolFlick(
        event: SwipeGesture.Event,
        pointer: TouchPointer,
        initialKey: TextKey,
    ): Boolean {
        return event.type == SwipeGesture.Type.TOUCH_UP &&
            event.direction.isCardinal &&
            !isGlideEnabled &&
            !pointer.hasTriggeredGestureMove &&
            keyboard.mode == KeyboardMode.CHARACTERS &&
            prefs.gestures.symbolFlickEnabled.get() &&
            prefs.keyboard.hintedSymbolsEnabled.get() &&
            initialKey.computedPopups.symbolHint != null
    }

    private fun handleSymbolFlick(initialKey: TextKey): Boolean {
        val symbolHint = initialKey.computedPopups.symbolHint ?: return false
        inputFeedbackController?.gestureSwipe(symbolHint)
        inputEventDispatcher.sendDownUp(symbolHint)
        return true
    }

    private fun handleDeleteSwipe(event: SwipeGesture.Event): Boolean {
        if (editorInstance.activeInfo.isRawInputEditor) return false

        return when (event.type) {
            SwipeGesture.Type.TOUCH_MOVE -> when (prefs.gestures.deleteKeySwipeLeft.get()) {
                SwipeAction.DELETE_CHARACTERS_PRECISELY, SwipeAction.SELECT_CHARACTERS_PRECISELY -> {
                    if (abs(event.relUnitCountX) > 0) {
                        inputFeedbackController?.gestureMovingSwipe(TextKeyData.DELETE)
                    }
                    val activeSelection = editorInstance.activeContent.selection
                    if (activeSelection.isValid) {
                        if (!inputEventDispatcher.isPressed(KeyCode.SHIFT)) {
                            // Backward select
                            editorInstance.setSelectionSurrounding(
                                n = -event.absUnitCountX - 1,
                                unit = OperationUnit.CHARACTERS,
                                scope = OperationScope.BEFORE_CURSOR,
                            )
                        } else {
                            // Forward select
                            editorInstance.setSelectionSurrounding(
                                n = -event.absUnitCountX - 1,
                                unit = OperationUnit.CHARACTERS,
                                scope = OperationScope.AFTER_CURSOR,
                            )
                        }
                    }
                    true
                }
                SwipeAction.DELETE_WORDS_PRECISELY, SwipeAction.SELECT_WORDS_PRECISELY -> {
                    if (abs(event.relUnitCountX) > 0) {
                        inputFeedbackController?.gestureMovingSwipe(TextKeyData.DELETE)
                    }
                    val activeSelection = editorInstance.activeContent.selection
                    if (activeSelection.isValid) {
                        if (!inputEventDispatcher.isPressed(KeyCode.SHIFT)) {
                            // Backward select
                            editorInstance.setSelectionSurrounding(
                                n = -event.absUnitCountX / 2 - 1,
                                unit = OperationUnit.WORDS,
                                scope = OperationScope.BEFORE_CURSOR,
                            )
                        } else {
                            // Forward select
                            editorInstance.setSelectionSurrounding(
                                n = -event.absUnitCountX / 2 - 1,
                                unit = OperationUnit.WORDS,
                                scope = OperationScope.AFTER_CURSOR,
                            )
                        }
                    }
                    true
                }
                else -> false
            }
            SwipeGesture.Type.TOUCH_UP -> {
                if (event.direction == SwipeGesture.Direction.LEFT &&
                    prefs.gestures.deleteKeySwipeLeft.get() == SwipeAction.DELETE_WORD
                ) {
                    keyboardManager.executeSwipeAction(prefs.gestures.deleteKeySwipeLeft.get())
                    true
                } else {
                    false
                }
            }
        }
    }

    private fun handleSpaceSwipe(event: SwipeGesture.Event): Boolean {
        val pointer = pointerMap.findById(event.pointerId) ?: return false

        if (prefs.gestures.spaceBarTouchpadMode.get()) {
            return handleSpaceTouchpad(event, pointer)
        }

        return when (event.type) {
            SwipeGesture.Type.TOUCH_MOVE -> when (event.direction) {
                SwipeGesture.Direction.LEFT -> {
                    val action = prefs.gestures.spaceBarSwipeLeft.get()
                    if (action == SwipeAction.MOVE_CURSOR_LEFT) {
                        abs(event.relUnitCountX).let {
                            val count = if (!pointer.hasTriggeredGestureMove) it - 1 else it
                            if (count > 0) {
                                inputFeedbackController?.gestureMovingSwipe(TextKeyData.SPACE)
                                if (!pointer.hasTriggeredMassSelection) {
                                    pointer.hasTriggeredMassSelection = true
                                    editorInstance.massSelection.begin()
                                }
                                keyboardManager.handleArrow(KeyCode.ARROW_LEFT, count)
                            }
                        }
                        true
                    } else {
                        action != SwipeAction.NO_ACTION
                    }
                }
                SwipeGesture.Direction.RIGHT -> {
                    val action = prefs.gestures.spaceBarSwipeRight.get()
                    if (action == SwipeAction.MOVE_CURSOR_RIGHT) {
                        abs(event.relUnitCountX).let {
                            val count = if (!pointer.hasTriggeredGestureMove) it - 1 else it
                            if (count > 0) {
                                inputFeedbackController?.gestureMovingSwipe(TextKeyData.SPACE)
                                if (!pointer.hasTriggeredMassSelection) {
                                    pointer.hasTriggeredMassSelection = true
                                    editorInstance.massSelection.begin()
                                }
                                keyboardManager.handleArrow(KeyCode.ARROW_RIGHT, count)
                            }
                        }
                        true
                    } else {
                        action != SwipeAction.NO_ACTION
                    }
                }
                // Matrix #14 — free-movement spacebar trackpad. When the user has bound the up or down
                // space-bar swipe to MOVE_CURSOR_UP / MOVE_CURSOR_DOWN, the TOUCH_MOVE path dispatches
                // continuous arrow-up / arrow-down events as the finger drags vertically — mirroring the
                // existing horizontal cursor-drag path. For any other binding the action fires once on
                // TOUCH_UP via the matrix #15 dispatch below.
                SwipeGesture.Direction.UP -> {
                    val action = prefs.gestures.spaceBarSwipeUp.get()
                    if (action == SwipeAction.MOVE_CURSOR_UP) {
                        abs(event.relUnitCountY).let {
                            val count = if (!pointer.hasTriggeredGestureMove) it - 1 else it
                            if (count > 0) {
                                inputFeedbackController?.gestureMovingSwipe(TextKeyData.SPACE)
                                if (!pointer.hasTriggeredMassSelection) {
                                    pointer.hasTriggeredMassSelection = true
                                    editorInstance.massSelection.begin()
                                }
                                keyboardManager.handleArrow(KeyCode.ARROW_UP, count)
                            }
                        }
                        true
                    } else {
                        action != SwipeAction.NO_ACTION
                    }
                }
                SwipeGesture.Direction.DOWN -> {
                    val action = prefs.gestures.spaceBarSwipeDown.get()
                    if (action == SwipeAction.MOVE_CURSOR_DOWN) {
                        abs(event.relUnitCountY).let {
                            val count = if (!pointer.hasTriggeredGestureMove) it - 1 else it
                            if (count > 0) {
                                inputFeedbackController?.gestureMovingSwipe(TextKeyData.SPACE)
                                if (!pointer.hasTriggeredMassSelection) {
                                    pointer.hasTriggeredMassSelection = true
                                    editorInstance.massSelection.begin()
                                }
                                keyboardManager.handleArrow(KeyCode.ARROW_DOWN, count)
                            }
                        }
                        true
                    } else {
                        action != SwipeAction.NO_ACTION
                    }
                }
                else -> false
            }
            SwipeGesture.Type.TOUCH_UP -> when (event.direction) {
                SwipeGesture.Direction.LEFT -> {
                    prefs.gestures.spaceBarSwipeLeft.get().let {
                        when {
                            it == SwipeAction.NO_ACTION -> {
                                false
                            }
                            it != SwipeAction.MOVE_CURSOR_LEFT -> {
                                keyboardManager.executeSwipeAction(it)
                                true
                            }
                            else -> {
                                false
                            }
                        }
                    }
                }
                SwipeGesture.Direction.RIGHT -> {
                    prefs.gestures.spaceBarSwipeRight.get().let {
                        when {
                            it == SwipeAction.NO_ACTION -> {
                                false
                            }
                            it != SwipeAction.MOVE_CURSOR_RIGHT -> {
                                keyboardManager.executeSwipeAction(it)
                                true
                            }
                            else -> {
                                false
                            }
                        }
                    }
                }
                // Matrix #15 — vertical cursor actions / trackpad navigation keys on space-bar swipe.
                // Discrete one-shot dispatch for any bound action (including the new spaceBarSwipeDown
                // pref) that isn't already being handled continuously by the TOUCH_MOVE arms above.
                SwipeGesture.Direction.UP -> {
                    val action = prefs.gestures.spaceBarSwipeUp.get()
                    when {
                        action == SwipeAction.NO_ACTION -> false
                        action != SwipeAction.MOVE_CURSOR_UP -> {
                            keyboardManager.executeSwipeAction(action)
                            true
                        }
                        else -> false
                    }
                }
                SwipeGesture.Direction.DOWN -> {
                    val action = prefs.gestures.spaceBarSwipeDown.get()
                    when {
                        action == SwipeAction.NO_ACTION -> false
                        action != SwipeAction.MOVE_CURSOR_DOWN -> {
                            keyboardManager.executeSwipeAction(action)
                            true
                        }
                        else -> false
                    }
                }
                else -> {
                    if (event.absUnitCountY < -6) {
                        keyboardManager.executeSwipeAction(prefs.gestures.spaceBarSwipeUp.get())
                        true
                    } else {
                        false
                    }
                }
            }
        }
    }

    private fun handleSpaceTouchpad(event: SwipeGesture.Event, pointer: TouchPointer): Boolean {
        if (event.type != SwipeGesture.Type.TOUCH_MOVE) return false
        val ratio = prefs.gestures.spaceBarTouchpadRatio.get()
        val xMovement = SpaceTouchpadPolicy.scaleAxis(
            relativeUnitCount = event.relUnitCountX,
            ratioPercent = ratio,
            remainder = pointer.spaceBarTouchpadRemainderX,
        )
        val yMovement = SpaceTouchpadPolicy.scaleAxis(
            relativeUnitCount = event.relUnitCountY,
            ratioPercent = ratio,
            remainder = pointer.spaceBarTouchpadRemainderY,
        )
        pointer.spaceBarTouchpadRemainderX = xMovement.remainder
        pointer.spaceBarTouchpadRemainderY = yMovement.remainder

        val content = editorInstance.activeContent
        val selection = content.selection
        val safeBounds = content.safeEditorBounds
        if (!selection.isValid || !safeBounds.isValid) return true

        val horizontalPosition = when {
            selection.isSelectionMode && xMovement.units < 0 -> selection.start
            selection.isSelectionMode && xMovement.units > 0 -> selection.end
            else -> selection.start
        }
        val safeHorizontalDelta = SpaceTouchpadPolicy.safeDelta(
            position = horizontalPosition,
            requestedDelta = xMovement.units,
            bounds = safeBounds,
        )
        val canMoveVertically = selection.start in safeBounds.start..safeBounds.end
        if (safeHorizontalDelta == 0 && (yMovement.units == 0 || !canMoveVertically)) return true

        inputFeedbackController?.gestureMovingSwipe(TextKeyData.SPACE)
        if (!pointer.hasTriggeredMassSelection) {
            pointer.hasTriggeredMassSelection = true
            editorInstance.massSelection.begin()
        }

        if (safeHorizontalDelta != 0) {
            val code = if (safeHorizontalDelta < 0) KeyCode.ARROW_LEFT else KeyCode.ARROW_RIGHT
            keyboardManager.handleArrow(code, abs(safeHorizontalDelta))
        }
        if (yMovement.units != 0 && canMoveVertically) {
            keyboardManager.activeState.isManualSelectionMode = true
            val code = if (yMovement.units < 0) KeyCode.ARROW_UP else KeyCode.ARROW_DOWN
            keyboardManager.handleArrow(code, abs(yMovement.units))
        }
        return true
    }

    private val glidePointersLeftSpaceBar = mutableSetOf<Int>()

    override fun onGlideAddPoint(point: GlideTypingGesture.Detector.Position) {
        onGlideAddPoint(pointerId = 0, point = point)
    }

    override fun onGlideAddPoint(pointerId: Int, point: GlideTypingGesture.Detector.Position) {
        if (isGlideEnabled) {
            glideDataForDrawing[pointerId] = glideDataForDrawing[pointerId].orEmpty() +
                (point to System.currentTimeMillis())
            val pointed = keyboard.getKeyForPos(point.x, point.y)
            if (pointed != null) glideActiveKeys[pointerId] = pointed
            // Flow Through Space: split the gesture into separate words when the trace
            // crosses the top edge of the space bar after first leaving it.
            if (prefs.glide.flowThroughSpace.get() && keyboard.mode == KeyboardMode.CHARACTERS) {
                val isOnSpace = pointed?.computedData?.code == KeyCode.SPACE ||
                    pointed?.computedData?.code == KeyCode.CJK_SPACE
                if (!isOnSpace) {
                    glidePointersLeftSpaceBar.add(pointerId)
                } else if (pointerId in glidePointersLeftSpaceBar) {
                    glidePointersLeftSpaceBar.remove(pointerId)
                    glideTypingDetector.signalWordBoundary(pointerId)
                }
            }
        }
    }

    override fun onGlideWordBoundary(data: GlideTypingGesture.Detector.PointerData) {
        onGlideWordBoundary(data.pointerId, data)
    }

    override fun onGlideWordBoundary(pointerId: Int, data: GlideTypingGesture.Detector.PointerData) {
        // Match onGlideComplete's trail-fade visual so each finished word feels like a
        // separate finger-up to the user, even though the finger never actually lifted.
        finishGlidePointer(pointerId)
    }

    override fun onGlideComplete(data: GlideTypingGesture.Detector.PointerData) {
        onGlideComplete(data.pointerId, data)
    }

    override fun onGlideComplete(pointerId: Int, data: GlideTypingGesture.Detector.PointerData) {
        finishGlidePointer(pointerId)
    }

    override fun onGlideCancelled() {
        glideDataForDrawing.keys.toList().forEach(::finishGlidePointer)
        glideActiveKeys.clear()
        glidePointersLeftSpaceBar.clear()
        isGliding = false
    }

    override fun onGlideCancelled(pointerId: Int) {
        finishGlidePointer(pointerId)
    }

    private fun finishGlidePointer(pointerId: Int) {
        val finished = glideDataForDrawing.remove(pointerId)
        glideActiveKeys.remove(pointerId)
        glidePointersLeftSpaceBar.remove(pointerId)
        if (finished.isNullOrEmpty()) {
            isGliding = glideDataForDrawing.isNotEmpty()
            return
        }
        // Only the fade-out animation is gated on the trail pref. The buffer
        // clear + state reset below MUST run unconditionally: onGlideAddPoint
        // populates `glideDataForDrawing` whenever glide is enabled (it does
        // not consult `showTrail`), so leaving them inside the pref branch
        // leaked the point buffer for the whole session and latched
        // `isGliding` true permanently when the trail was disabled.
        if (prefs.glide.showTrail.get()) {
            fadingGlide.addAll(finished)

            val animator = ValueAnimator.ofFloat(20.0f, 0.0f)
            animator.interpolator = AccelerateInterpolator()
            animator.duration = prefs.glide.trailDuration.get().toLong()
            animator.addUpdateListener {
                fadingGlideRadius = it.animatedValue as Float
            }
            animator.start()
        }
        isGliding = glideDataForDrawing.isNotEmpty()
    }

    fun drawGlideTrail(
        drawScope: ContentDrawScope,
        gestureData: List<Pair<GlideTypingGesture.Detector.Position, Long>>,
        initialRadius: Float,
        theme: GlideTrailTheme,
        accentColor: Color,
        timeMillis: Long,
        fadeProgress: Float = 1.0f,
    ) {
        if (gestureData.size < 2 || fadeProgress <= 0f) return
        val trailDurationMs = prefs.glide.trailDuration.get()

        // Find first point still within the visible trail window.
        var firstVisible = -1
        for (i in gestureData.indices) {
            if (timeMillis - gestureData[i].second <= trailDurationMs) {
                firstVisible = i
                break
            }
        }
        if (firstVisible < 0) return
        val visibleCount = gestureData.size - firstVisible
        if (visibleCount < 2) return

        // 8 segments balances color-gradient fidelity vs draw-call cost.
        val segCount = min(8, max(2, visibleCount / 4))
        val dataEnd = firstVisible + visibleCount

        for (seg in 0 until segCount) {
            val segStart = firstVisible + (seg.toLong() * visibleCount / segCount).toInt()
            val nextSegStart = firstVisible + ((seg + 1).toLong() * visibleCount / segCount).toInt()
            val segEnd = if (seg < segCount - 1) min(nextSegStart + 1, dataEnd) else min(nextSegStart, dataEnd)
            if (segEnd - segStart < 2) continue

            val progress = (seg + 1f) / segCount
            val sqrtProgress = sqrt(progress)
            val easedAlpha = (0.15f + 0.75f * sqrtProgress) * fadeProgress
            // Single slightly-wider stroke instead of separate glow + core layers.
            val width = initialRadius * (0.6f + 0.5f * sqrtProgress) * fadeProgress
            val segColor = theme.colorAt(progress, timeMillis, accentColor)

            val path = Path().apply {
                moveTo(gestureData[segStart].first.x, gestureData[segStart].first.y)
                for (i in segStart + 1 until segEnd) {
                    lineTo(gestureData[i].first.x, gestureData[i].first.y)
                }
            }

            drawScope.drawPath(
                path,
                segColor.copy(alpha = easedAlpha),
                style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

        // Bright head dot at the finger position.
        if (fadeProgress > 0.3f) {
            val head = gestureData.last().first
            val headCenter = Offset(head.x, head.y)
            val dotAlpha = fadeProgress
            val headColor = theme.colorAt(1f, timeMillis, accentColor)
            // Wide glow ring
            drawScope.drawCircle(headColor.copy(alpha = dotAlpha * 0.20f), initialRadius * 2f, headCenter)
            // Core dot
            drawScope.drawCircle(headColor.copy(alpha = dotAlpha * 0.95f), initialRadius * 0.6f, headCenter)
            // Hot center highlight
            drawScope.drawCircle(Color.White.copy(alpha = dotAlpha * 0.7f), initialRadius * 0.25f, headCenter)
        }
    }

    private class TouchPointer : Pointer() {
        var initialKey: TextKey? = null
        var activeKey: TextKey? = null
        var hasTriggeredGestureMove: Boolean = false
        var hasTriggeredLongPress: Boolean = false
        var hasTriggeredMassSelection: Boolean = false
        var spaceBarTouchpadRemainderX: Double = 0.0
        var spaceBarTouchpadRemainderY: Double = 0.0
        var pressedKeyInfo: InputEventDispatcher.PressedKeyInfo? = null
        var adaptiveTouchKey: TextKey? = null
        var adaptiveTouchX: Float = 0.0f
        var adaptiveTouchY: Float = 0.0f

        override fun reset() {
            super.reset()
            initialKey = null
            activeKey = null
            hasTriggeredGestureMove = false
            hasTriggeredLongPress = false
            hasTriggeredMassSelection = false
            spaceBarTouchpadRemainderX = 0.0
            spaceBarTouchpadRemainderY = 0.0
            pressedKeyInfo = null
            adaptiveTouchKey = null
            adaptiveTouchX = 0.0f
            adaptiveTouchY = 0.0f
        }

        override fun toString(): String {
            return "${TouchPointer::class.simpleName} { id=$id, index=$index, initialKey=$initialKey, activeKey=$activeKey }"
        }
    }
}

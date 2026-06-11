/*
 * Copyright (C) 2024-2025 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.ime.smartbar

import android.os.SystemClock
import android.os.Trace
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.FlorisImeService
import dev.patrickgold.florisboard.BuildConfig
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.nlp.AutoCommitUndoSuggestionCandidate
import dev.patrickgold.florisboard.ime.nlp.ClipboardSuggestionCandidate
import dev.patrickgold.florisboard.ime.nlp.SuggestionCandidate
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.nlpManager
import dev.patrickgold.florisboard.subtypeManager
import dev.patrickgold.jetpref.datastore.model.collectAsState
import org.florisboard.lib.compose.conditional
import org.florisboard.lib.compose.florisHorizontalScroll
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.snygg.SnyggSelector
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.SnyggColumn
import org.florisboard.lib.snygg.ui.SnyggIcon
import org.florisboard.lib.snygg.ui.SnyggRow
import org.florisboard.lib.snygg.ui.SnyggSpacer
import org.florisboard.lib.snygg.ui.SnyggText
import org.florisboard.lib.snygg.ui.rememberSnyggThemeQuery

val CandidatesRowScrollbarHeight = 2.dp

@Composable
fun CandidatesRow(modifier: Modifier = Modifier) {
    // ROADMAP §7 Next-12.1 — Macrobenchmark trace section. Compose forbids
    // try/finally around composable invocations, so we use sequential
    // Trace.beginSection / Trace.endSection calls flanking the body.
    // `android.os.Trace` tolerates a missing endSection if recomposition
    // throws — Perfetto just reports an unclosed section.
    val shouldLogBenchmark = BuildConfig.BUILD_TYPE == "benchmark"
    val recomposeStartedAt = if (shouldLogBenchmark) {
        SystemClock.elapsedRealtimeNanos()
    } else {
        0L
    }
    Trace.beginSection("swiftfloris.smartbar.candidates.recompose")
    val prefs by FlorisPreferenceStore
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val nlpManager by context.nlpManager()
    val subtypeManager by context.subtypeManager()

    val displayMode by prefs.suggestion.displayMode.collectAsState()
    val candidates by nlpManager.activeCandidatesFlow.collectAsState()

    // ROADMAP §7 Next-3.4 — long-press a suggestion to surface an in-strip
    // "Remove '<word>' from predictions?" prompt (SwiftKey/Gboard parity, closes
    // FlorisBoard #737, AnySoftKeyboard #1399, FlorisBoard #1888, COMM-A FR-22).
    // The actual removal is deferred until the user taps "Remove"; tapping
    // anywhere else (or pressing Cancel) dismisses without changes.
    var pendingRemoval by remember { mutableStateOf<SuggestionCandidate?>(null) }
    // If the underlying candidate list rotates out the pending word, clear the
    // overlay so we don't show a confirm prompt for a candidate the user can no
    // longer see.
    if (pendingRemoval != null && candidates.none { it === pendingRemoval }) {
        pendingRemoval = null
    }

    Box(modifier = modifier.fillMaxSize()) {
        SnyggRow(
            elementName = FlorisImeUi.SmartbarCandidatesRow.elementName,
            modifier = Modifier
                .fillMaxSize()
                .conditional(displayMode == CandidatesDisplayMode.DYNAMIC_SCROLLABLE && candidates.size > 1) {
                    florisHorizontalScroll(scrollbarHeight = CandidatesRowScrollbarHeight)
                },
            horizontalArrangement = if (candidates.size > 1) {
                Arrangement.Start
            } else {
                Arrangement.Center
            },
        ) {
            if (candidates.isNotEmpty()) {
                val candidateModifier = if (candidates.size == 1) {
                    Modifier
                        .fillMaxHeight()
                        .weight(1f, fill = false)
                } else {
                    Modifier
                        .fillMaxHeight()
                        .conditional(displayMode == CandidatesDisplayMode.CLASSIC) {
                            weight(1f)
                        }
                        .conditional(displayMode != CandidatesDisplayMode.CLASSIC) {
                            wrapContentWidth().widthIn(max = 160.dp)
                        }
                }
                val list = when (displayMode) {
                    CandidatesDisplayMode.CLASSIC -> candidates.subList(0, 3.coerceAtMost(candidates.size))
                    else -> candidates
                }
                for ((n, candidate) in list.withIndex()) {
                    if (n > 0) {
                        SnyggSpacer(
                            elementName = FlorisImeUi.SmartbarCandidateSpacer.elementName,
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight(0.6f)
                                .align(Alignment.CenterVertically),
                        )
                    }
                    CandidateItem(
                        modifier = candidateModifier,
                        candidate = candidate,
                        index = n,
                        count = list.size,
                        displayMode = displayMode,
                        onClick = {
                            // Can't use candidate directly. The live list can
                            // also shrink between the rendered frame and the
                            // tap (suggestions reroll on every content change),
                            // so a stale slot index must be a no-op, not an
                            // IndexOutOfBoundsException inside a click handler.
                            candidates.getOrNull(n)?.let { live ->
                                keyboardManager.commitCandidate(live)
                            }
                        },
                        onLongPress = {
                            // Can't use candidate directly — capture the live
                            // candidate at gesture time so the confirm prompt
                            // operates on what the user actually saw, even if
                            // the strip rerolls before they confirm. Guard the
                            // index: the list may have shrunk since render.
                            val candidateItem = candidates.getOrNull(n)
                            if (candidateItem != null && candidateItem.isEligibleForUserRemoval) {
                                pendingRemoval = candidateItem
                                true
                            } else {
                                false
                            }
                        },
                        longPressDelay = prefs.keyboard.longPressDelay.get().toLong(),
                    )
                }
            }
        }
        // ROADMAP §7 Next-11.2 — springy entry/exit for the confirm overlay.
        // Critically-damped spring (Spring.DampingRatioMediumBouncy) gives a
        // tiny rebound on appear so the prompt reads as a deliberate action
        // rather than a flash. Exit is a faster scale-out so the user gets
        // immediate feedback when they tap Cancel or the backdrop.
        AnimatedVisibility(
            visible = pendingRemoval != null,
            enter = scaleIn(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
                initialScale = 0.85f,
            ) + fadeIn(
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
            ),
            exit = scaleOut(
                animationSpec = spring(stiffness = Spring.StiffnessHigh),
                targetScale = 0.9f,
            ) + fadeOut(
                animationSpec = spring(stiffness = Spring.StiffnessHigh),
            ),
        ) {
            pendingRemoval?.let { candidateItem ->
                CandidateRemoveConfirmation(
                    candidate = candidateItem,
                    onConfirm = {
                        nlpManager.removeSuggestion(subtypeManager.activeSubtype, candidateItem)
                        pendingRemoval = null
                    },
                    onDismiss = { pendingRemoval = null },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
    if (shouldLogBenchmark) {
        val durationMs = (SystemClock.elapsedRealtimeNanos() - recomposeStartedAt) / 1_000_000.0
        Log.i(
            "SwiftFlorisPerf",
            "swiftfloris.smartbar.candidates.recomposeMs=$durationMs " +
                "candidateCount=${candidates.size} displayMode=$displayMode",
        )
    }
    Trace.endSection()
}

/**
 * ROADMAP §7 Next-3.4 — confirmation overlay for "Remove '<word>' from
 * predictions". Renders flush over the candidate strip. Square 8 dp corners
 * (per global UI rules); cancels on tap-outside; confirms via the right-hand
 * Remove button.
 */
@Composable
private fun CandidateRemoveConfirmation(
    candidate: SuggestionCandidate,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Backdrop comes from the keyboard theme, not the (always-light) Material
    // baseline scheme the IME window is wrapped in \u2014 a light surface under
    // Snygg-cascaded light foreground text was illegible on dark themes.
    // Window background underneath guarantees the confirmation occludes the
    // candidate strip even when the theme's row background is transparent.
    val rowStyle = rememberSnyggThemeQuery(FlorisImeUi.SmartbarCandidatesRow.elementName)
    val windowStyle = rememberSnyggThemeQuery(FlorisImeUi.Window.elementName)
    val baseBackground = windowStyle.background(default = Color(0xFF171923))
    val rowBackground = rowStyle.background(default = baseBackground)
    Box(
        modifier = modifier.clickable(
            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
            indication = null,
            onClick = onDismiss,
        ),
        contentAlignment = Alignment.Center,
    ) {
        SnyggRow(
            elementName = FlorisImeUi.SmartbarCandidatesRow.elementName,
            modifier = Modifier
                .background(baseBackground, RoundedCornerShape(8.dp))
                .background(rowBackground, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
                SnyggText(
                    elementName = FlorisImeUi.SmartbarCandidateWordText.elementName,
                    text = stringRes(R.string.smartbar__candidate_remove_confirm__message, "word" to candidate.text),
                )
                SnyggSpacer(
                    elementName = FlorisImeUi.SmartbarCandidateSpacer.elementName,
                    modifier = Modifier.width(12.dp),
                )
                SnyggText(
                    elementName = FlorisImeUi.SmartbarCandidateWordText.elementName,
                    modifier = Modifier
                        .clickable(role = Role.Button, onClick = onDismiss)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    text = stringRes(R.string.action__cancel),
                )
                SnyggText(
                    elementName = FlorisImeUi.SmartbarCandidateWordText.elementName,
                    modifier = Modifier
                        .clickable(role = Role.Button, onClick = onConfirm)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    text = stringRes(R.string.action__remove),
                )
        }
    }
}

@Composable
private fun CandidateItem(
    candidate: SuggestionCandidate,
    index: Int,
    count: Int,
    displayMode: CandidatesDisplayMode,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = { },
    onLongPress: () -> Boolean = { false },
    longPressDelay: Long,
) = with(LocalDensity.current) {
    var isPressed by remember { mutableStateOf(false) }

    val elementName = if (candidate is ClipboardSuggestionCandidate) {
        FlorisImeUi.SmartbarCandidateClip
    } else {
        FlorisImeUi.SmartbarCandidateWord
    }.elementName
    val attributes = mapOf("auto-commit" to if (candidate.isEligibleForAutoCommit) 1 else 0)
    val selector = if (isPressed) SnyggSelector.PRESSED else SnyggSelector.NONE

    // ROADMAP §6 N8.3 — TalkBack semantic for each suggestion-strip slot. The
    // candidate text is the primary announce; eligible-for-user-removal candidates
    // also expose a "Remove from predictions" custom action so screen-reader users
    // can do what long-press does for sighted users.
    val candidateText = candidate.text.toString()
    val candidateSemanticLabel = SmartbarAccessibilityLabels.candidateLabel(
        template = stringRes(
            when {
                candidate is AutoCommitUndoSuggestionCandidate -> R.string.a11y__candidate__autocorrect_undo
                candidate is ClipboardSuggestionCandidate -> R.string.a11y__candidate__clipboard
                candidate.isEligibleForAutoCommit -> R.string.a11y__candidate__autocorrect
                else -> R.string.a11y__candidate__suggestion
            }
        ),
        text = candidateText,
        index = index,
        count = count,
    )
    val removeActionLabel = stringRes(R.string.a11y__candidate__remove_action)
    SnyggRow(
        elementName = elementName,
        attributes = attributes,
        selector = selector,
        modifier = modifier
            .semantics {
                contentDescription = candidateSemanticLabel
                role = Role.Button
                if (candidate.isEligibleForUserRemoval) {
                    customActions = listOf(
                        CustomAccessibilityAction(
                            label = removeActionLabel,
                            action = { onLongPress() },
                        ),
                    )
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    isPressed = true
                    FlorisImeService.inputFeedbackController()?.keyPress()
                    if (down.pressed != down.previousPressed) down.consume()
                    var upOrCancel: PointerInputChange? = null
                    try {
                        upOrCancel = withTimeout(longPressDelay) {
                            waitForUpOrCancellation()
                        }
                        upOrCancel?.let { if (it.pressed != it.previousPressed) it.consume() }
                    } catch (_: PointerEventTimeoutCancellationException) {
                        if (onLongPress()) {
                            FlorisImeService.inputFeedbackController()?.keyLongPress()
                            upOrCancel = null
                            isPressed = false
                        }
                        waitForUpOrCancellation()?.let { if (it.pressed != it.previousPressed) it.consume() }
                    }
                    if (upOrCancel != null) {
                        onClick()
                    }
                    isPressed = false
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (candidate.icon != null) {
            SnyggBox(
                elementName = "$elementName-icon",
                attributes = attributes,
                selector = selector,
            ) {
                SnyggIcon(imageVector = candidate.icon!!)
            }
        }
        SnyggColumn(
            modifier = if (displayMode == CandidatesDisplayMode.CLASSIC) Modifier.weight(1f) else Modifier,
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SnyggText(
                elementName = "$elementName-text",
                attributes = attributes,
                selector = selector,
                text = candidate.text.toString(),
            )
            if (candidate.secondaryText != null) {
                SnyggText(
                    elementName = "$elementName-secondary-text",
                    attributes = attributes,
                    selector = selector,
                    text = candidate.secondaryText!!.toString(),
                )
            }
        }
    }
}

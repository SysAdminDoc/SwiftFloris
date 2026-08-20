/*
 * Copyright (C) 2026 SwiftFloris Contributors
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

@file:OptIn(dev.patrickgold.jetpref.datastore.ui.ExperimentalJetPrefDatastoreUi::class)

package dev.patrickgold.florisboard.app.settings.search

import androidx.compose.foundation.background
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import dev.patrickgold.jetpref.datastore.model.PreferenceData
import dev.patrickgold.jetpref.datastore.model.PreferenceDataEvaluatorScope
import dev.patrickgold.jetpref.datastore.ui.DialogPrefStrings
import dev.patrickgold.jetpref.datastore.ui.ListPreferenceEntry
import dev.patrickgold.jetpref.datastore.ui.LocalDefaultDialogPrefStrings
import dev.patrickgold.jetpref.datastore.ui.LocalIconSpaceReserved
import dev.patrickgold.jetpref.datastore.ui.Preference as JetPrefPreference
import dev.patrickgold.jetpref.datastore.ui.SwitchPreference as JetPrefSwitchPreference
import dev.patrickgold.jetpref.datastore.ui.ListPreference as JetPrefListPreference
import dev.patrickgold.jetpref.datastore.ui.DialogSliderPreference as JetPrefDialogSliderPreference
import dev.patrickgold.jetpref.datastore.ui.ColorPickerPreference as JetPrefColorPickerPreference

internal val LocalSettingsSearchScreenTitle = compositionLocalOf { "" }
internal val LocalSettingsSearchScrollState = compositionLocalOf<androidx.compose.foundation.ScrollState?> { null }

private const val SearchRowHighlightDurationMillis = 2_400L

internal fun Modifier.settingsSearchRow(title: String): Modifier = composed {
    val requester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val screenTitle = LocalSettingsSearchScreenTitle.current
    val density = LocalDensity.current
    val scrollState = LocalSettingsSearchScrollState.current
    val pendingTarget = SettingsSearchHighlightStore.activeTarget
    var handledTargetId by remember { mutableStateOf<String?>(null) }
    var highlighted by remember { mutableStateOf(false) }

    val targetTrackingModifier = if (pendingTarget == null) {
        Modifier
    } else {
        Modifier.onGloballyPositioned { coordinates ->
            val target = SettingsSearchHighlightStore.activeTarget ?: return@onGloballyPositioned
            if (
                target.entryId != handledTargetId &&
                target.screenTitle == screenTitle &&
                target.title == title &&
                SettingsSearchHighlightStore.claimTargetForRow(screenTitle, title)
            ) {
                handledTargetId = target.entryId
                highlighted = true
                scope.launch {
                    if (scrollState == null) {
                        requester.bringIntoView()
                    } else {
                        kotlinx.coroutines.yield()
                        val desiredTop = with(density) { 24.dp.toPx() }
                        val desiredScroll = (
                            scrollState.value + coordinates.positionInRoot().y - desiredTop
                        ).toInt().coerceIn(0, scrollState.maxValue)
                        scrollState.animateScrollTo(desiredScroll)
                    }
                    launch {
                        delay(SearchRowHighlightDurationMillis)
                        highlighted = false
                    }
                }
            }
        }
    }

    this
        .then(targetTrackingModifier)
        .bringIntoViewRequester(requester)
        .then(
            if (highlighted) {
                Modifier.background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    shape = MaterialTheme.shapes.medium,
                )
            } else {
                Modifier
            },
        )
}

@Composable
fun Preference(
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconSpaceReserved: Boolean = LocalIconSpaceReserved.current,
    title: String,
    summary: String? = null,
    trailing: @Composable () -> Unit = {},
    enabledIf: @Composable PreferenceDataEvaluatorScope.() -> Boolean = { true },
    visibleIf: @Composable PreferenceDataEvaluatorScope.() -> Boolean = { true },
    onClick: (() -> Unit)? = null,
    eventModifier: (@Composable () -> Modifier)? = null,
) {
    JetPrefPreference(
        modifier.settingsSearchRow(title),
        icon,
        iconSpaceReserved,
        title,
        summary,
        trailing,
        enabledIf,
        visibleIf,
        onClick,
        eventModifier,
    )
}

@Composable
fun SwitchPreference(
    pref: PreferenceData<Boolean>,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconSpaceReserved: Boolean = LocalIconSpaceReserved.current,
    title: String,
    summary: String? = null,
    summaryOn: String? = null,
    summaryOff: String? = null,
    enabledIf: @Composable PreferenceDataEvaluatorScope.() -> Boolean = { true },
    visibleIf: @Composable PreferenceDataEvaluatorScope.() -> Boolean = { true },
) {
    JetPrefSwitchPreference(
        pref,
        modifier.settingsSearchRow(title),
        icon,
        iconSpaceReserved,
        title,
        summary,
        summaryOn,
        summaryOff,
        enabledIf,
        visibleIf,
    )
}

@Composable
fun <V : Any> ListPreference(
    listPref: PreferenceData<V>,
    switchPref: PreferenceData<Boolean>? = null,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconSpaceReserved: Boolean = LocalIconSpaceReserved.current,
    title: String,
    summarySwitchDisabled: String? = null,
    dialogStrings: DialogPrefStrings = LocalDefaultDialogPrefStrings.current,
    enabledIf: @Composable PreferenceDataEvaluatorScope.() -> Boolean = { true },
    visibleIf: @Composable PreferenceDataEvaluatorScope.() -> Boolean = { true },
    entries: List<ListPreferenceEntry<V>>,
) {
    JetPrefListPreference(
        listPref,
        switchPref,
        modifier.settingsSearchRow(title),
        icon,
        iconSpaceReserved,
        title,
        summarySwitchDisabled,
        dialogStrings,
        enabledIf,
        visibleIf,
        entries,
    )
}

@Composable
fun DialogSliderPreference(
    pref: PreferenceData<Int>,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconSpaceReserved: Boolean = LocalIconSpaceReserved.current,
    title: String,
    valueLabel: @Composable (Int) -> String = { it.toString() },
    summary: @Composable (Int) -> String = valueLabel,
    min: Int,
    max: Int,
    stepIncrement: Int,
    onPreviewSelectedValue: (Int) -> Unit = {},
    dialogStrings: DialogPrefStrings = LocalDefaultDialogPrefStrings.current,
    enabledIf: @Composable PreferenceDataEvaluatorScope.() -> Boolean = { true },
    visibleIf: @Composable PreferenceDataEvaluatorScope.() -> Boolean = { true },
) {
    JetPrefDialogSliderPreference(
        pref,
        modifier.settingsSearchRow(title),
        icon,
        iconSpaceReserved,
        title,
        valueLabel,
        summary,
        min,
        max,
        stepIncrement,
        onPreviewSelectedValue,
        dialogStrings,
        enabledIf,
        visibleIf,
    )
}

@Composable
fun DialogSliderPreference(
    pref: PreferenceData<Long>,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconSpaceReserved: Boolean = LocalIconSpaceReserved.current,
    title: String,
    valueLabel: @Composable (Long) -> String = { it.toString() },
    summary: @Composable (Long) -> String = valueLabel,
    min: Long,
    max: Long,
    stepIncrement: Long,
    onPreviewSelectedValue: (Long) -> Unit = {},
    dialogStrings: DialogPrefStrings = LocalDefaultDialogPrefStrings.current,
    enabledIf: @Composable PreferenceDataEvaluatorScope.() -> Boolean = { true },
    visibleIf: @Composable PreferenceDataEvaluatorScope.() -> Boolean = { true },
) {
    JetPrefDialogSliderPreference(
        pref,
        modifier.settingsSearchRow(title),
        icon,
        iconSpaceReserved,
        title,
        valueLabel,
        summary,
        min,
        max,
        stepIncrement,
        onPreviewSelectedValue,
        dialogStrings,
        enabledIf,
        visibleIf,
    )
}

@Composable
fun DialogSliderPreference(
    pref: PreferenceData<Float>,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconSpaceReserved: Boolean = LocalIconSpaceReserved.current,
    title: String,
    valueLabel: @Composable (Float) -> String = { it.toString() },
    summary: @Composable (Float) -> String = valueLabel,
    min: Float,
    max: Float,
    stepIncrement: Float,
    onPreviewSelectedValue: (Float) -> Unit = {},
    dialogStrings: DialogPrefStrings = LocalDefaultDialogPrefStrings.current,
    enabledIf: @Composable PreferenceDataEvaluatorScope.() -> Boolean = { true },
    visibleIf: @Composable PreferenceDataEvaluatorScope.() -> Boolean = { true },
) {
    JetPrefDialogSliderPreference(
        pref,
        modifier.settingsSearchRow(title),
        icon,
        iconSpaceReserved,
        title,
        valueLabel,
        summary,
        min,
        max,
        stepIncrement,
        onPreviewSelectedValue,
        dialogStrings,
        enabledIf,
        visibleIf,
    )
}

@Composable
fun DialogSliderPreference(
    primaryPref: PreferenceData<Int>,
    secondaryPref: PreferenceData<Int>,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconSpaceReserved: Boolean = LocalIconSpaceReserved.current,
    title: String,
    primaryLabel: String,
    secondaryLabel: String,
    valueLabel: @Composable (Int) -> String = { it.toString() },
    summary: @Composable (Int, Int) -> String = { _, _ -> "" },
    min: Int,
    max: Int,
    stepIncrement: Int,
    onPreviewSelectedPrimaryValue: (Int) -> Unit = {},
    onPreviewSelectedSecondaryValue: (Int) -> Unit = {},
    dialogStrings: DialogPrefStrings = LocalDefaultDialogPrefStrings.current,
    enabledIf: @Composable PreferenceDataEvaluatorScope.() -> Boolean = { true },
    visibleIf: @Composable PreferenceDataEvaluatorScope.() -> Boolean = { true },
) {
    JetPrefDialogSliderPreference(
        primaryPref,
        secondaryPref,
        modifier.settingsSearchRow(title),
        icon,
        iconSpaceReserved,
        title,
        primaryLabel,
        secondaryLabel,
        valueLabel,
        summary,
        min,
        max,
        stepIncrement,
        onPreviewSelectedPrimaryValue,
        onPreviewSelectedSecondaryValue,
        dialogStrings,
        enabledIf,
        visibleIf,
    )
}

@Composable
fun ColorPickerPreference(
    pref: PreferenceData<Color>,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconSpaceReserved: Boolean = LocalIconSpaceReserved.current,
    title: String,
    summary: String? = null,
    defaultValueLabel: String? = null,
    showAlphaSlider: Boolean = true,
    enableAdvancedLayout: Boolean = false,
    defaultColors: Array<Color> = emptyArray(),
    colorOverride: (Color) -> Color = { it },
    dialogStrings: DialogPrefStrings = LocalDefaultDialogPrefStrings.current,
    enabledIf: @Composable PreferenceDataEvaluatorScope.() -> Boolean = { true },
    visibleIf: @Composable PreferenceDataEvaluatorScope.() -> Boolean = { true },
) {
    JetPrefColorPickerPreference(
        pref,
        modifier.settingsSearchRow(title),
        icon,
        iconSpaceReserved,
        title,
        summary,
        defaultValueLabel,
        showAlphaSlider,
        enableAdvancedLayout,
        defaultColors,
        colorOverride,
        dialogStrings,
        enabledIf,
        visibleIf,
    )
}

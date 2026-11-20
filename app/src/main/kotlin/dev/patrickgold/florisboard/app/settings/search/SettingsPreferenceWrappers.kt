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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import dev.patrickgold.jetpref.datastore.model.PreferenceData
import dev.patrickgold.jetpref.datastore.model.PreferenceDataEvaluatorScope
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.jetpref.datastore.ui.DialogPrefStrings
import dev.patrickgold.jetpref.datastore.ui.ListPreferenceEntry
import dev.patrickgold.jetpref.datastore.ui.LocalDefaultDialogPrefStrings
import dev.patrickgold.jetpref.datastore.ui.LocalIconSpaceReserved
import dev.patrickgold.jetpref.datastore.ui.LocalIsPrefEnabled
import dev.patrickgold.jetpref.datastore.ui.LocalIsPrefVisible
import dev.patrickgold.jetpref.material.ui.JetPrefAlertDialog
import dev.patrickgold.jetpref.material.ui.JetPrefAlertDialogDefaults
import dev.patrickgold.jetpref.material.ui.copy

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
    // Null leaves the row without a trailing slot, which Material 3 pads by 16 dp even when
    // it's empty. JetPref drew its slider and time rows that way.
    trailing: (@Composable () -> Unit)? = {},
    enabledIf: @Composable PreferenceDataEvaluatorScope.() -> Boolean = { true },
    visibleIf: @Composable PreferenceDataEvaluatorScope.() -> Boolean = { true },
    onClick: (() -> Unit)? = null,
    eventModifier: (@Composable () -> Modifier)? = null,
) {
    // JetPref's Preference, drawn with SettingsListItem so its text wraps at large font scales.
    require(!(onClick != null && eventModifier != null)) {
        "You cannot provide both an onClick lambda and an eventModifier."
    }
    if (LocalIsPrefVisible.current && visibleIf(PreferenceDataEvaluatorScope)) {
        val isEnabled = LocalIsPrefEnabled.current && enabledIf(PreferenceDataEvaluatorScope)
        CompositionLocalProvider(
            LocalIconSpaceReserved provides iconSpaceReserved,
            LocalIsPrefEnabled provides isEnabled,
            LocalIsPrefVisible provides true,
        ) {
            val rowModifier = modifier.settingsSearchRow(title)
            SettingsListItem(
                modifier = if (onClick != null) {
                    rowModifier.clickable(enabled = isEnabled, role = Role.Button, onClick = onClick)
                } else {
                    rowModifier.then(eventModifier?.invoke() ?: Modifier)
                },
                icon = settingsRowIcon(icon, iconSpaceReserved),
                text = title,
                secondaryText = summary,
                trailing = trailing,
                enabled = isEnabled,
            )
        }
    }
}

private fun settingsRowIcon(icon: ImageVector?, iconSpaceReserved: Boolean): (@Composable () -> Unit)? {
    return when {
        icon != null -> ({ Icon(imageVector = icon, contentDescription = null) })
        iconSpaceReserved -> ({ Spacer(modifier = Modifier.width(24.dp)) })
        else -> null
    }
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
    // JetPref's SwitchPreference on the Preference above.
    val scope = rememberCoroutineScope()
    val prefValue by pref.collectAsState()
    Preference(
        modifier = modifier,
        eventModifier = {
            Modifier.toggleable(
                value = prefValue,
                enabled = LocalIsPrefEnabled.current,
                role = Role.Switch,
                onValueChange = { scope.launch { pref.set(it) } },
            )
        },
        icon = icon,
        iconSpaceReserved = iconSpaceReserved,
        title = title,
        summary = when {
            prefValue && summaryOn != null -> summaryOn
            !prefValue && summaryOff != null -> summaryOff
            else -> summary
        },
        trailing = {
            Switch(
                modifier = Modifier.size(LocalViewConfiguration.current.minimumTouchTargetSize),
                checked = prefValue,
                onCheckedChange = null,
                enabled = LocalIsPrefEnabled.current,
            )
        },
        enabledIf = enabledIf,
        visibleIf = visibleIf,
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
    // JetPref's ListPreference on the Preference above, with the same dialog.
    val scope = rememberCoroutineScope()
    val listPrefValue by listPref.collectAsState()
    val switchPrefValue = switchPref?.collectAsState()
    val (tmpListPrefValue, setTmpListPrefValue) = remember { mutableStateOf(listPref.get()) }
    val (tmpSwitchPrefValue, setTmpSwitchPrefValue) = remember { mutableStateOf(false) }
    var isDialogOpen by remember { mutableStateOf(false) }

    Preference(
        modifier = modifier,
        icon = icon,
        iconSpaceReserved = iconSpaceReserved,
        title = title,
        summary = if (switchPrefValue == null || switchPrefValue.value) {
            entries.find { it.key == listPrefValue }?.label ?: "!! invalid !!"
        } else {
            summarySwitchDisabled
        },
        trailing = {
            if (switchPrefValue != null) {
                val dividerColor = MaterialTheme.colorScheme.outlineVariant
                Box(
                    modifier = Modifier
                        .size(LocalViewConfiguration.current.minimumTouchTargetSize + DpSize(8.dp, 0.dp))
                        .toggleable(
                            value = switchPrefValue.value,
                            enabled = LocalIsPrefEnabled.current,
                            role = Role.Switch,
                            onValueChange = { scope.launch { switchPref.set(it) } },
                        )
                        .drawBehind {
                            drawLine(
                                color = dividerColor,
                                start = Offset(0f, size.height * 0.1f),
                                end = Offset(0f, size.height * 0.9f),
                                strokeWidth = 2f,
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Switch(
                        modifier = Modifier.padding(start = 8.dp),
                        checked = switchPrefValue.value,
                        onCheckedChange = null,
                        enabled = LocalIsPrefEnabled.current,
                    )
                }
            }
        },
        enabledIf = enabledIf,
        visibleIf = visibleIf,
        onClick = {
            setTmpListPrefValue(listPrefValue)
            if (switchPrefValue != null) {
                setTmpSwitchPrefValue(switchPrefValue.value)
            }
            isDialogOpen = true
        },
    )

    if (isDialogOpen) {
        JetPrefAlertDialog(
            title = title,
            confirmLabel = dialogStrings.confirmLabel,
            onConfirm = {
                scope.launch {
                    listPref.set(tmpListPrefValue)
                    switchPref?.set(tmpSwitchPrefValue)
                }
                isDialogOpen = false
            },
            dismissLabel = dialogStrings.dismissLabel,
            onDismiss = { isDialogOpen = false },
            neutralLabel = dialogStrings.neutralLabel,
            onNeutral = {
                scope.launch {
                    listPref.reset()
                    switchPref?.reset()
                }
                isDialogOpen = false
            },
            trailingIconTitle = {
                if (switchPrefValue != null) {
                    Switch(
                        modifier = Modifier.padding(start = 16.dp),
                        checked = tmpSwitchPrefValue,
                        onCheckedChange = { setTmpSwitchPrefValue(it) },
                        enabled = true,
                    )
                }
            },
            titlePadding = remember(switchPrefValue) {
                if (switchPrefValue != null) {
                    JetPrefAlertDialogDefaults.TitlePadding.copy(top = 16.dp)
                } else {
                    JetPrefAlertDialogDefaults.TitlePadding
                }
            },
            contentPadding = PaddingValues(horizontal = 8.dp),
        ) {
            Column {
                val alpha = if (switchPrefValue == null || tmpSwitchPrefValue) 1f else 0.38f
                for (entry in entries) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = entry.key == tmpListPrefValue,
                                enabled = switchPrefValue == null || tmpSwitchPrefValue,
                                onClick = { setTmpListPrefValue(entry.key) },
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .alpha(alpha),
                    ) {
                        RadioButton(
                            selected = entry.key == tmpListPrefValue,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.padding(end = 12.dp),
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.CenterVertically),
                        ) {
                            entry.labelComposer(entry.label)
                            if (!entry.showDescriptionOnlyIfSelected || entry.key == tmpListPrefValue) {
                                entry.descriptionComposer(entry.description)
                            }
                        }
                    }
                }
            }
        }
    }
}

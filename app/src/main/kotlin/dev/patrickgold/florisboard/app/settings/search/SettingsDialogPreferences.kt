/*
 * Copyright 2021 Patrick Goldinger
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

package dev.patrickgold.florisboard.app.settings.search

import android.text.format.DateFormat.is24HourFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.patrickgold.jetpref.datastore.model.LocalTime
import dev.patrickgold.jetpref.datastore.model.PreferenceData
import dev.patrickgold.jetpref.datastore.model.PreferenceDataEvaluatorScope
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.jetpref.datastore.ui.ColorBox
import dev.patrickgold.jetpref.datastore.ui.DialogPrefStrings
import dev.patrickgold.jetpref.datastore.ui.LocalDefaultDialogPrefStrings
import dev.patrickgold.jetpref.datastore.ui.LocalIconSpaceReserved
import dev.patrickgold.jetpref.datastore.ui.safeValue
import dev.patrickgold.jetpref.datastore.ui.toLocalTime
import dev.patrickgold.jetpref.material.ui.AlphaBar
import dev.patrickgold.jetpref.material.ui.ExperimentalJetPrefMaterial3Ui
import dev.patrickgold.jetpref.material.ui.JetPrefAlertDialog
import dev.patrickgold.jetpref.material.ui.JetPrefColorPicker
import dev.patrickgold.jetpref.material.ui.checkeredBackground
import dev.patrickgold.jetpref.material.ui.rememberJetPrefColorPickerState
import kotlinx.coroutines.launch
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.roundToLong

// JetPref 0.3.0's slider, color and time preferences draw their row through JetPref's own Preference,
// whose JetPrefListItem cuts the title to one line at any font scale. These are the same preferences with
// the row drawn by this package's Preference (SettingsListItem, which wraps above a 1.3 font scale, plus
// the settings search highlight) and JetPref's dialogs unchanged. Behavior follows JetPref 0.3.0, including
// the single slider rounding its value to whole numbers while it moves.

private typealias SettingsPrefEvaluator = @Composable PreferenceDataEvaluatorScope.() -> Boolean

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
    enabledIf: SettingsPrefEvaluator = { true },
    visibleIf: SettingsPrefEvaluator = { true },
) {
    SingleSliderPreference(
        pref, modifier, icon, iconSpaceReserved, title, valueLabel, summary, min, max,
        stepIncrement, onPreviewSelectedValue, dialogStrings, enabledIf, visibleIf,
    ) {
        try {
            it.roundToInt()
        } catch (_: IllegalArgumentException) {
            it.toInt()
        }
    }
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
    enabledIf: SettingsPrefEvaluator = { true },
    visibleIf: SettingsPrefEvaluator = { true },
) {
    SingleSliderPreference(
        pref, modifier, icon, iconSpaceReserved, title, valueLabel, summary, min, max,
        stepIncrement, onPreviewSelectedValue, dialogStrings, enabledIf, visibleIf,
    ) {
        try {
            it.roundToLong()
        } catch (_: IllegalArgumentException) {
            it.toLong()
        }
    }
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
    enabledIf: SettingsPrefEvaluator = { true },
    visibleIf: SettingsPrefEvaluator = { true },
) {
    SingleSliderPreference(
        pref, modifier, icon, iconSpaceReserved, title, valueLabel, summary, min, max,
        stepIncrement, onPreviewSelectedValue, dialogStrings, enabledIf, visibleIf,
    ) { it }
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
    summary: @Composable (Int, Int) -> String = { p, s -> "${valueLabel(p)} / ${valueLabel(s)}" },
    min: Int,
    max: Int,
    stepIncrement: Int,
    onPreviewSelectedPrimaryValue: (Int) -> Unit = {},
    onPreviewSelectedSecondaryValue: (Int) -> Unit = {},
    dialogStrings: DialogPrefStrings = LocalDefaultDialogPrefStrings.current,
    enabledIf: SettingsPrefEvaluator = { true },
    visibleIf: SettingsPrefEvaluator = { true },
) {
    require(stepIncrement > 0) { "Step increment must be greater than 0!" }
    require(max > min) { "Maximum value ($max) must be greater than minimum value ($min)!" }
    val scope = rememberCoroutineScope()
    val primaryPrefValue by primaryPref.collectAsState()
    val secondaryPrefValue by secondaryPref.collectAsState()
    var primarySliderValue by remember { mutableIntStateOf(0) }
    var secondarySliderValue by remember { mutableIntStateOf(0) }
    var isDialogOpen by remember { mutableStateOf(false) }
    val convertToV: (Float) -> Int = {
        try {
            it.roundToInt()
        } catch (_: IllegalArgumentException) {
            it.toInt()
        }
    }

    Preference(
        modifier = modifier,
        icon = icon,
        iconSpaceReserved = iconSpaceReserved,
        title = title,
        summary = summary(primaryPrefValue, secondaryPrefValue),
        trailing = null,
        enabledIf = enabledIf,
        visibleIf = visibleIf,
        onClick = {
            primarySliderValue = primaryPrefValue
            secondarySliderValue = secondaryPrefValue
            isDialogOpen = true
        },
    )

    if (isDialogOpen) {
        val steps = ((max.toFloat() - min.toFloat()) / stepIncrement.toFloat()).toInt() - 1
        JetPrefAlertDialog(
            title = title,
            confirmLabel = dialogStrings.confirmLabel,
            onConfirm = {
                scope.launch {
                    primaryPref.set(primarySliderValue)
                    secondaryPref.set(secondarySliderValue)
                }
                isDialogOpen = false
            },
            dismissLabel = dialogStrings.dismissLabel,
            onDismiss = {
                isDialogOpen = false
            },
            neutralLabel = dialogStrings.neutralLabel,
            onNeutral = {
                scope.launch {
                    primaryPref.reset()
                    secondaryPref.reset()
                }
                isDialogOpen = false
            },
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(primaryLabel)
                    Text(valueLabel(primarySliderValue))
                }
                Slider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    value = primarySliderValue.toFloat(),
                    valueRange = min.toFloat()..max.toFloat(),
                    steps = steps,
                    onValueChange = { primarySliderValue = convertToV(it) },
                    onValueChangeFinished = { onPreviewSelectedPrimaryValue(primarySliderValue) },
                    colors = sliderDialogColors(),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(secondaryLabel)
                    Text(valueLabel(secondarySliderValue))
                }
                Slider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    value = secondarySliderValue.toFloat(),
                    valueRange = min.toFloat()..max.toFloat(),
                    steps = steps,
                    onValueChange = { secondarySliderValue = convertToV(it) },
                    onValueChangeFinished = { onPreviewSelectedSecondaryValue(secondarySliderValue) },
                    colors = sliderDialogColors(),
                )
            }
        }
    }
}

@Composable
private fun <V> SingleSliderPreference(
    pref: PreferenceData<V>,
    modifier: Modifier,
    icon: ImageVector?,
    iconSpaceReserved: Boolean,
    title: String,
    valueLabel: @Composable (V) -> String,
    summary: @Composable (V) -> String,
    min: V,
    max: V,
    stepIncrement: V,
    onPreviewSelectedValue: (V) -> Unit,
    dialogStrings: DialogPrefStrings,
    enabledIf: SettingsPrefEvaluator,
    visibleIf: SettingsPrefEvaluator,
    convertToV: (Float) -> V,
) where V : Number, V : Comparable<V> {
    require(stepIncrement > convertToV(0f)) { "Step increment must be greater than 0!" }
    require(max > min) { "Maximum value ($max) must be greater than minimum value ($min)!" }

    val scope = rememberCoroutineScope()
    val prefValue by pref.collectAsState()
    var sliderValue by remember { mutableFloatStateOf(0.0f) }
    var isDialogOpen by remember { mutableStateOf(false) }

    Preference(
        modifier = modifier,
        icon = icon,
        iconSpaceReserved = iconSpaceReserved,
        title = title,
        summary = summary(prefValue),
        trailing = null,
        enabledIf = enabledIf,
        visibleIf = visibleIf,
        onClick = {
            sliderValue = prefValue.toFloat()
            isDialogOpen = true
        },
    )

    if (isDialogOpen) {
        JetPrefAlertDialog(
            title = title,
            confirmLabel = dialogStrings.confirmLabel,
            onConfirm = {
                scope.launch {
                    pref.set(convertToV(sliderValue))
                }
                isDialogOpen = false
            },
            dismissLabel = dialogStrings.dismissLabel,
            onDismiss = {
                isDialogOpen = false
            },
            neutralLabel = dialogStrings.neutralLabel,
            onNeutral = {
                scope.launch {
                    pref.reset()
                }
                isDialogOpen = false
            },
        ) {
            Column {
                Text(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 8.dp),
                    text = valueLabel(convertToV(sliderValue)),
                )
                Slider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    value = sliderValue,
                    valueRange = min.toFloat()..max.toFloat(),
                    steps = ((max.toFloat() - min.toFloat()) / stepIncrement.toFloat()).roundToInt() - 1,
                    onValueChange = { sliderValue = round(it) },
                    onValueChangeFinished = { onPreviewSelectedValue(convertToV(sliderValue)) },
                    colors = sliderDialogColors(),
                )
            }
        }
    }
}

@Composable
private fun sliderDialogColors(): SliderColors {
    return SliderDefaults.colors(
        thumbColor = MaterialTheme.colorScheme.primary,
        activeTrackColor = MaterialTheme.colorScheme.primary,
        activeTickColor = Color.Transparent,
        inactiveTrackColor = MaterialTheme.colorScheme.primaryContainer,
        inactiveTickColor = Color.Transparent,
    )
}

@OptIn(ExperimentalJetPrefMaterial3Ui::class)
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
    enabledIf: SettingsPrefEvaluator = { true },
    visibleIf: SettingsPrefEvaluator = { true },
) {
    val scope = rememberCoroutineScope()
    var showPicker by remember { mutableStateOf(false) }
    var dialogValue by remember { mutableIntStateOf(0) }
    val prefValue by pref.collectAsState()
    val safeValue = prefValue.safeValue()

    Preference(
        modifier = modifier,
        icon = icon,
        iconSpaceReserved = iconSpaceReserved,
        trailing = {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .checkeredBackground(gridSize = 2.dp)
                    .background(Color(safeValue))
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            )
        },
        title = title,
        summary = summary,
        enabledIf = enabledIf,
        visibleIf = visibleIf,
        onClick = {
            dialogValue = safeValue
            showPicker = true
        },
    )

    if (showPicker) {
        val defaultColor = Color(pref.default.safeValue())
        val color = Color(dialogValue)
        val presetColors = remember {
            defaultColors.apply {
                if (defaultValueLabel == null) {
                    plus(defaultColor)
                }
            }
        }
        var selectedPreset by remember {
            mutableIntStateOf(presetColors.indexOf(color.copy(alpha = 1f)))
        }
        var advanced by remember {
            mutableStateOf(
                !presetColors.contains(color.copy(alpha = 1f)) &&
                    color.copy(1f) != defaultColor &&
                    enableAdvancedLayout,
            )
        }
        val neutralLabel by remember(advanced, enableAdvancedLayout) {
            mutableStateOf(
                if (!enableAdvancedLayout) {
                    null
                } else {
                    if (advanced) "Presets" else "Custom"
                },
            )
        }
        val colorPickerState = rememberJetPrefColorPickerState(color)

        key(advanced) {
            JetPrefAlertDialog(
                title = title,
                scrollModifier = Modifier,
                content = {
                    if (advanced) {
                        selectedPreset = -1
                        JetPrefColorPicker(
                            state = colorPickerState,
                            onColorChange = {
                                dialogValue = it.toArgb()
                            },
                            withAlpha = showAlphaSlider,
                            saturationValueAspectRatio = 1f,
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(48.dp),
                                userScrollEnabled = false,
                                content = {
                                    items(presetColors.size) { index ->
                                        ColorBox(
                                            color = presetColors[index].copy(alpha = color.alpha),
                                            selected = selectedPreset == index,
                                            icon = icon,
                                            onSelect = {
                                                selectedPreset = index
                                                dialogValue = it.toArgb()
                                                colorPickerState.setColor(it)
                                            },
                                        )
                                    }
                                },
                            )

                            defaultValueLabel?.let {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(50))
                                        .clickable {
                                            selectedPreset = -1
                                            dialogValue = defaultColor.toArgb()
                                            colorPickerState.setColor(defaultColor)
                                        },
                                ) {
                                    ColorBox(
                                        color = defaultColor,
                                        selected = color.copy(1f) == defaultColor,
                                        icon = icon,
                                        onSelect = {
                                            selectedPreset = -1
                                            dialogValue = defaultColor.toArgb()
                                            colorPickerState.setColor(defaultColor)
                                        },
                                    )
                                    Text(
                                        modifier = Modifier.padding(horizontal = 4.dp),
                                        style = MaterialTheme.typography.bodyLarge,
                                        text = defaultValueLabel,
                                    )
                                }
                            }

                            if (showAlphaSlider) {
                                AlphaBar(
                                    onColorChange = {
                                        dialogValue = it.toArgb()
                                    },
                                    state = colorPickerState,
                                    strokeColor = Color.White,
                                )
                            }
                        }
                    }
                },
                neutralLabel = neutralLabel,
                onNeutral = {
                    advanced = !advanced
                },
                neutralEnabled = enableAdvancedLayout,
                confirmLabel = dialogStrings.confirmLabel,
                onConfirm = {
                    scope.launch {
                        pref.set(colorOverride(Color(dialogValue)))
                    }
                    showPicker = false
                },
                dismissLabel = dialogStrings.dismissLabel,
                onDismiss = { showPicker = false },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalTimePickerPreference(
    pref: PreferenceData<LocalTime>,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconSpaceReserved: Boolean = LocalIconSpaceReserved.current,
    title: String,
    dialogStrings: DialogPrefStrings = LocalDefaultDialogPrefStrings.current,
    enabledIf: SettingsPrefEvaluator = { true },
    visibleIf: SettingsPrefEvaluator = { true },
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefValue by pref.collectAsState()
    var isDialogOpen by remember { mutableStateOf(false) }
    var displayMode by remember { mutableStateOf(DisplayMode.Picker) }
    var is24hour by remember { mutableStateOf(is24HourFormat(context)) }

    LifecycleResumeEffect(Unit) {
        is24hour = is24HourFormat(context)
        onPauseOrDispose { }
    }

    Preference(
        modifier = modifier,
        icon = icon,
        iconSpaceReserved = iconSpaceReserved,
        title = title,
        summary = prefValue.stringRepresentation(is24hour),
        trailing = null,
        enabledIf = enabledIf,
        visibleIf = visibleIf,
        onClick = {
            isDialogOpen = true
        },
    )

    if (isDialogOpen) {
        val timePickerState = rememberTimePickerState(
            initialHour = prefValue.hour,
            initialMinute = prefValue.minute,
            is24Hour = is24hour,
        )

        LifecycleResumeEffect(Unit) {
            timePickerState.is24hour = is24HourFormat(context)
            onPauseOrDispose { }
        }

        JetPrefAlertDialog(
            title = title,
            modifier = Modifier
                .width(IntrinsicSize.Min)
                .height(IntrinsicSize.Min),
            confirmLabel = dialogStrings.confirmLabel,
            onConfirm = {
                scope.launch {
                    pref.set(timePickerState.toLocalTime())
                }
                isDialogOpen = false
            },
            dismissLabel = dialogStrings.dismissLabel,
            onDismiss = {
                isDialogOpen = false
            },
            neutralLabel = dialogStrings.neutralLabel,
            onNeutral = {
                scope.launch {
                    pref.reset()
                }
                isDialogOpen = false
            },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Column {
                val contentModifier = Modifier.padding(horizontal = 24.dp)
                when (displayMode) {
                    DisplayMode.Picker -> TimePicker(modifier = contentModifier, state = timePickerState)
                    DisplayMode.Input -> TimeInput(modifier = contentModifier, state = timePickerState)
                }
                TimeDisplayModeToggleButton(displayMode, onDisplayModeChange = { displayMode = it })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDisplayModeToggleButton(
    displayMode: DisplayMode,
    onDisplayModeChange: (DisplayMode) -> Unit,
) {
    when (displayMode) {
        DisplayMode.Picker -> IconButton(onClick = { onDisplayModeChange(DisplayMode.Input) }) {
            Icon(
                imageVector = Icons.Default.Keyboard,
                contentDescription = "Switch time input to keyboard input",
            )
        }
        DisplayMode.Input -> IconButton(onClick = { onDisplayModeChange(DisplayMode.Picker) }) {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = "Switch time input to time picker",
            )
        }
    }
}

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

package org.florisboard.lib.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val StepHeaderPaddingVertical = 6.dp
private val StepHeaderNumberBoxSize = 34.dp
private val StepHeaderNumberBoxPaddingEnd = 14.dp
private val StepHeaderTextInnerPaddingHorizontal = 14.dp

data class FlorisStep(
    val id: Int,
    val title: String,
    val content: @Composable FlorisStepLayoutScope.() -> Unit,
)

class FlorisStepLayoutScope(
    columnScope: ColumnScope,
    private val primaryColor: Color,
) : ColumnScope by columnScope {

    @Composable
    fun StepText(
        text: String,
        modifier: Modifier = Modifier,
        fontStyle: FontStyle = FontStyle.Normal,
    ) {
        Text(
            modifier = modifier,
            text = text,
            textAlign = TextAlign.Start,
            fontStyle = fontStyle,
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    @Composable
    fun StepButton(
        label: String,
        modifier: Modifier = Modifier,
        onClick: () -> Unit,
    ) {
        FlorisButton(
            modifier = modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = primaryColor,
            ),
            text = label,
            onClick = onClick,
        )
    }
}

@Suppress("unused")
class FlorisStepState private constructor(
    private val currentAuto: MutableState<Int>,
    private val currentManual: MutableState<Int> = mutableIntStateOf(-1),
) {
    companion object {
        fun new(init: Int) = FlorisStepState(mutableIntStateOf(init))

        val Saver = Saver<FlorisStepState, ArrayList<Int>>(
            save = {
                arrayListOf(it.currentAuto.value, it.currentManual.value)
            },
            restore = {
                FlorisStepState(mutableIntStateOf(it[0]), mutableIntStateOf(it[1]))
            },
        )
    }

    fun getCurrent(): State<Int> {
        return if (currentManual.value >= 0 && currentAuto.value >= currentManual.value) {
            currentManual
        } else {
            currentAuto
        }
    }

    fun getCurrentAuto(): State<Int> = currentAuto

    fun getCurrentManual(): State<Int> = currentManual

    fun setCurrentAuto(value: Int) {
        currentAuto.value = value
    }

    fun setCurrentManual(value: Int) {
        if (currentAuto.value == value) {
            currentManual.value = -1
        } else {
            currentManual.value = value
        }
    }
}

@Composable
fun FlorisStepLayout(
    stepState: FlorisStepState,
    steps: List<FlorisStep>,
    modifier: Modifier = Modifier,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    header: @Composable FlorisStepLayoutScope.() -> Unit = { },
    footer: @Composable FlorisStepLayoutScope.() -> Unit = { },
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .florisVerticalScroll()
    ) {
        val scope = FlorisStepLayoutScope(this, primaryColor)
        header(scope)
        for ((index, step) in steps.withIndex()) {
            key(step.id) {
                Step(
                    ownStepId = step.id,
                    index = index + 1, // Start numbering with 1
                    stepState = stepState,
                    title = step.title,
                    primaryColor = primaryColor,
                ) {
                    step.content(FlorisStepLayoutScope(this, primaryColor))
                }
            }
        }
        footer(scope)
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun ColumnScope.Step(
    ownStepId: Int,
    index: Int,
    stepState: FlorisStepState,
    title: String,
    primaryColor: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    val currentStepId by stepState.getCurrent()
    val autoStepId by stepState.getCurrentAuto()
    val contentVisible = ownStepId == currentStepId
    val isAvailable = ownStepId <= autoStepId
    val isCompleted = ownStepId < autoStepId && !contentVisible
    val reducedMotion = rememberReducedMotion()
    StepHeader(
        modifier = when {
            ownStepId <= autoStepId -> Modifier
                .clickable(
                    enabled = !contentVisible,
                    role = Role.Button,
                ) { stepState.setCurrentManual(ownStepId) }
            else -> Modifier.alpha(0.64f)
        },
        primaryColor = primaryColor,
        isCurrent = contentVisible,
        isAvailable = isAvailable,
        isCompleted = isCompleted,
        step = index,
        title = title,
    )
    AnimatedVisibility(
        modifier = Modifier
            .fillMaxWidth(),
        visible = contentVisible,
        enter = if (reducedMotion) {
            fadeIn(animationSpec = tween(durationMillis = 0))
        } else {
            fadeIn(animationSpec = tween(durationMillis = 120)) +
                expandVertically(animationSpec = tween(durationMillis = 180), expandFrom = Alignment.Top)
        },
        exit = if (reducedMotion) {
            fadeOut(animationSpec = tween(durationMillis = 0))
        } else {
            fadeOut(animationSpec = tween(durationMillis = 90)) +
                shrinkVertically(animationSpec = tween(durationMillis = 140), shrinkTowards = Alignment.Top)
        },
    ) {
        val colorScheme = MaterialTheme.colorScheme
        val shape = MaterialTheme.shapes.small
        val onBackground = colorScheme.onSurfaceVariant
        Box(
            modifier = Modifier
                .padding(start = 56.dp, bottom = 12.dp)
                .drawBehind {
                    val strokeWidth = 2.dp
                    val x = -(StepHeaderNumberBoxPaddingEnd + (StepHeaderNumberBoxSize / 2 - strokeWidth / 2))
                    drawLine(
                        color = onBackground,
                        start = Offset(x.toPx(), 0f),
                        end = Offset(x.toPx(), size.height),
                        strokeWidth = strokeWidth.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 10.dp.toPx())),
                        alpha = 0.12f,
                    )
                }
                .clip(shape)
                .background(colorScheme.surfaceContainer)
                .border(
                    width = 1.dp,
                    color = colorScheme.outlineVariant.copy(alpha = FlorisSurfaceTokens.HairlineAlpha),
                    shape = shape,
                )
                .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun StepHeader(
    modifier: Modifier = Modifier,
    primaryColor: Color,
    isCurrent: Boolean,
    isAvailable: Boolean,
    isCompleted: Boolean,
    step: Int,
    title: String,
) {
    val colorScheme = MaterialTheme.colorScheme
    val backgroundColor = when {
        isCurrent -> colorScheme.surfaceContainerHigh
        isAvailable -> colorScheme.surfaceContainerHigh
        else -> colorScheme.surfaceContainerLow
    }
    val contentColor = when {
        isCurrent -> colorScheme.onSurface
        isAvailable -> colorScheme.onSurface
        else -> colorScheme.onSurfaceVariant
    }
    val numberBackgroundColor = when {
        isCurrent -> primaryColor
        isAvailable -> colorScheme.primaryContainer
        else -> colorScheme.surfaceContainerHighest
    }
    val numberContentColor = when {
        isCurrent -> colorScheme.onPrimary
        isAvailable -> colorScheme.onPrimaryContainer
        else -> colorScheme.onSurfaceVariant
    }
    Row(
        modifier = modifier
            .semantics {
                selected = isCurrent
                stateDescription = when {
                    isCurrent -> "Current step"
                    isCompleted -> "Completed step"
                    isAvailable -> "Available step"
                    else -> "Locked step"
                }
            }
            .padding(vertical = StepHeaderPaddingVertical)
            .clip(MaterialTheme.shapes.small)
            .background(backgroundColor)
            .border(
                width = 1.dp,
                color = if (isCurrent) {
                    primaryColor.copy(alpha = 0.36f)
                } else {
                    colorScheme.outlineVariant.copy(alpha = FlorisSurfaceTokens.HairlineAlpha)
                },
                shape = MaterialTheme.shapes.small,
            )
            .defaultMinSize(minHeight = 58.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .padding(end = StepHeaderNumberBoxPaddingEnd)
                .size(StepHeaderNumberBoxSize)
                .clip(MaterialTheme.shapes.small)
                .background(numberBackgroundColor),
        ) {
            if (isCompleted) {
                Icon(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(18.dp),
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = numberContentColor,
                )
            } else {
                Text(
                    modifier = Modifier.align(Alignment.Center),
                    text = step.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = numberContentColor,
                )
            }
        }

        Row(
            modifier = Modifier
                .weight(1.0f)
                .padding(horizontal = StepHeaderTextInnerPaddingHorizontal),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                overflow = TextOverflow.Ellipsis,
                maxLines = 2,
                color = contentColor,
            )
        }
    }
}

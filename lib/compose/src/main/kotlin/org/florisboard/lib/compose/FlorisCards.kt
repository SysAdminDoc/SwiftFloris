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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


object FlorisCardDefaults {
    val IconSpacing = 16.dp
    const val SecondaryContentAlpha = 0.88f
    const val StatusBorderAlpha = 0.34f
    const val StatusIconBackgroundAlpha = 0.11f

    val ContentPadding = PaddingValues(start = 0.dp, end = 18.dp, top = 16.dp, bottom = 16.dp)
}

/**
 * Severity of a shared status card. Drives which accessibility live region — if any — the card
 * opts into, so asynchronous state changes are announced instead of only being visible.
 */
enum class FlorisStatusSeverity {
    /** Long-running work in progress; announced without interrupting the current utterance. */
    Progress,

    /** Work finished successfully. */
    Success,

    /** Degraded but non-blocking state. */
    Warning,

    /** Failure the user has to act on; interrupts to avoid a silently failed operation. */
    Error,

    /** Static explanatory copy that does not change asynchronously. */
    Info,

    /** Static unavailable/disabled copy that does not change asynchronously. */
    Neutral,
}

object FlorisStatusSemantics {
    /**
     * Maps [severity] to a live-region mode.
     *
     * Only severities that represent a *transition* announce themselves. Static copy returns
     * `null`, as does every non-status surface, so per-keystroke keyboard UI (candidates,
     * key popups) is never turned into a live region by accident.
     */
    fun liveRegionFor(severity: FlorisStatusSeverity): LiveRegionMode? {
        return when (severity) {
            FlorisStatusSeverity.Progress -> LiveRegionMode.Polite
            FlorisStatusSeverity.Success -> LiveRegionMode.Polite
            FlorisStatusSeverity.Warning -> LiveRegionMode.Polite
            FlorisStatusSeverity.Error -> LiveRegionMode.Assertive
            FlorisStatusSeverity.Info -> null
            FlorisStatusSeverity.Neutral -> null
        }
    }
}

object BoxDefaults {
    val OutlinedBoxShape = RoundedCornerShape(8.dp)

    val ContentPadding = PaddingValues(all = 0.dp)
}

@Composable
fun FlorisSimpleCard(
    modifier: Modifier = Modifier,
    text: String,
    secondaryText: String? = null,
    actionLabel: String? = null,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f),
    contentPadding: PaddingValues = FlorisCardDefaults.ContentPadding,
    liveRegion: LiveRegionMode? = null,
    icon: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val cardModifier = modifier
        .fillMaxWidth()
        .semantics(mergeDescendants = true) {
            if (liveRegion != null) {
                this.liveRegion = liveRegion
            }
        }
    val cardColors = CardDefaults.cardColors(
        contentColor = contentColor,
        containerColor = backgroundColor,
        disabledContainerColor = backgroundColor,
        disabledContentColor = contentColor,
    )
    val cardElevation = CardDefaults.cardElevation(
        defaultElevation = 0.dp,
        pressedElevation = 2.dp,
        disabledElevation = 0.dp,
    )
    val cardContent: @Composable ColumnScope.() -> Unit = {
        Row(
            modifier = Modifier
                .defaultMinSize(minHeight = 72.dp)
                .padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                icon()
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (icon == null) 16.dp else 0.dp),
            ) {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = text,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                )
                if (secondaryText != null) {
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = secondaryText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = FlorisCardDefaults.SecondaryContentAlpha),
                    )
                }
                if (actionLabel != null && onClick != null) {
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = actionLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Icon(
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .size(18.dp),
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = contentColor.copy(alpha = 0.84f),
                        )
                    }
                }
            }
            if (onClick != null && actionLabel == null) {
                Icon(
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .size(20.dp),
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = contentColor.copy(alpha = 0.72f),
                )
            }
        }
    }
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = cardModifier,
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(width = 1.dp, color = borderColor),
            colors = cardColors,
            elevation = cardElevation,
            content = cardContent,
        )
    } else {
        Card(
            modifier = cardModifier,
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(width = 1.dp, color = borderColor),
            colors = cardColors,
            elevation = cardElevation,
            content = cardContent,
        )
    }
}

@Composable
fun FlorisErrorCard(
    text: String,
    modifier: Modifier = Modifier,
    secondaryText: String? = null,
    actionLabel: String? = null,
    showIcon: Boolean = true,
    contentPadding: PaddingValues = FlorisCardDefaults.ContentPadding,
    liveRegion: LiveRegionMode? = FlorisStatusSemantics.liveRegionFor(FlorisStatusSeverity.Error),
    onClick: (() -> Unit)? = null,
) {
    FlorisSimpleCard(
        modifier = modifier,
        backgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        borderColor = MaterialTheme.colorScheme.error.copy(alpha = FlorisCardDefaults.StatusBorderAlpha),
        liveRegion = liveRegion,
        onClick = onClick,
        icon = if (showIcon) ({
            FlorisStatusIcon(
                imageVector = Icons.Default.ErrorOutline,
                tint = MaterialTheme.colorScheme.error,
            )
        }) else null,
        text = text,
        secondaryText = secondaryText,
        actionLabel = actionLabel,
        contentPadding = contentPadding,
    )
}

@Composable
fun FlorisWarningCard(
    text: String,
    modifier: Modifier = Modifier,
    secondaryText: String? = null,
    actionLabel: String? = null,
    showIcon: Boolean = true,
    contentPadding: PaddingValues = FlorisCardDefaults.ContentPadding,
    liveRegion: LiveRegionMode? = FlorisStatusSemantics.liveRegionFor(FlorisStatusSeverity.Warning),
    onClick: (() -> Unit)? = null,
) {
    FlorisSimpleCard(
        modifier = modifier,
        backgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        borderColor = MaterialTheme.colorScheme.tertiary.copy(alpha = FlorisCardDefaults.StatusBorderAlpha),
        liveRegion = liveRegion,
        onClick = onClick,
        icon = if (showIcon) ({
            FlorisStatusIcon(
                imageVector = Icons.Outlined.Warning,
                tint = MaterialTheme.colorScheme.tertiary,
            )
        }) else null,
        text = text,
        secondaryText = secondaryText,
        actionLabel = actionLabel,
        contentPadding = contentPadding,
    )
}

@Composable
fun FlorisSuccessCard(
    text: String,
    modifier: Modifier = Modifier,
    secondaryText: String? = null,
    actionLabel: String? = null,
    showIcon: Boolean = true,
    contentPadding: PaddingValues = FlorisCardDefaults.ContentPadding,
    liveRegion: LiveRegionMode? = FlorisStatusSemantics.liveRegionFor(FlorisStatusSeverity.Success),
    onClick: (() -> Unit)? = null,
) {
    FlorisSimpleCard(
        modifier = modifier,
        backgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = FlorisCardDefaults.StatusBorderAlpha),
        liveRegion = liveRegion,
        onClick = onClick,
        icon = if (showIcon) ({
            FlorisStatusIcon(
                imageVector = Icons.Default.CheckCircle,
                tint = MaterialTheme.colorScheme.primary,
            )
        }) else null,
        text = text,
        secondaryText = secondaryText,
        actionLabel = actionLabel,
        contentPadding = contentPadding,
    )
}

@Composable
fun FlorisProgressCard(
    text: String,
    modifier: Modifier = Modifier,
    secondaryText: String? = null,
    actionLabel: String? = null,
    showIcon: Boolean = true,
    contentPadding: PaddingValues = FlorisCardDefaults.ContentPadding,
    liveRegion: LiveRegionMode? = FlorisStatusSemantics.liveRegionFor(FlorisStatusSeverity.Progress),
    onClick: (() -> Unit)? = null,
) {
    FlorisSimpleCard(
        modifier = modifier,
        backgroundColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.32f),
        liveRegion = liveRegion,
        onClick = onClick,
        icon = if (showIcon) ({
            FlorisStatusIcon(
                imageVector = Icons.Default.Sync,
                tint = MaterialTheme.colorScheme.primary,
            )
        }) else null,
        text = text,
        secondaryText = secondaryText,
        actionLabel = actionLabel,
        contentPadding = contentPadding,
    )
}

@Composable
fun FlorisNeutralCard(
    text: String,
    modifier: Modifier = Modifier,
    secondaryText: String? = null,
    actionLabel: String? = null,
    showIcon: Boolean = true,
    contentPadding: PaddingValues = FlorisCardDefaults.ContentPadding,
    liveRegion: LiveRegionMode? = FlorisStatusSemantics.liveRegionFor(FlorisStatusSeverity.Neutral),
    onClick: (() -> Unit)? = null,
) {
    FlorisSimpleCard(
        modifier = modifier,
        backgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.56f),
        liveRegion = liveRegion,
        onClick = onClick,
        icon = if (showIcon) ({
            FlorisStatusIcon(
                imageVector = Icons.Default.Block,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }) else null,
        text = text,
        secondaryText = secondaryText,
        actionLabel = actionLabel,
        contentPadding = contentPadding,
    )
}

@Composable
fun FlorisInfoCard(
    text: String,
    modifier: Modifier = Modifier,
    secondaryText: String? = null,
    actionLabel: String? = null,
    showIcon: Boolean = true,
    contentPadding: PaddingValues = FlorisCardDefaults.ContentPadding,
    liveRegion: LiveRegionMode? = FlorisStatusSemantics.liveRegionFor(FlorisStatusSeverity.Info),
    onClick: (() -> Unit)? = null,
) {
    FlorisSimpleCard(
        modifier = modifier,
        backgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.56f),
        liveRegion = liveRegion,
        onClick = onClick,
        icon = if (showIcon) ({
            FlorisStatusIcon(
                imageVector = Icons.Default.Info,
                tint = MaterialTheme.colorScheme.primary,
            )
        }) else null,
        text = text,
        secondaryText = secondaryText,
        actionLabel = actionLabel,
        contentPadding = contentPadding,
    )
}

@Composable
private fun FlorisStatusIcon(
    imageVector: ImageVector,
    tint: Color,
) {
    Box(
        modifier = Modifier
            .padding(horizontal = FlorisCardDefaults.IconSpacing)
            .size(42.dp)
            .clip(MaterialTheme.shapes.small)
            .background(tint.copy(alpha = FlorisCardDefaults.StatusIconBackgroundAlpha)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            modifier = Modifier.size(22.dp),
            imageVector = imageVector,
            contentDescription = null,
            tint = tint,
        )
    }
}

@Composable
fun FlorisEmptyState(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    message: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { },
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.56f),
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.88f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    modifier = Modifier.size(24.dp),
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Text(
                modifier = Modifier.semantics { heading() },
                text = title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            if (!message.isNullOrBlank()) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            if (actionLabel != null && onAction != null) {
                FlorisTextButton(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .defaultMinSize(minHeight = FlorisTouchTarget.MinSize),
                    onClick = onAction,
                    text = actionLabel,
                )
            }
        }
    }
}

@Composable
fun FlorisOutlinedBox(
    modifier: Modifier = Modifier,
    title: String,
    onTitleClick: (() -> Unit)? = null,
    subtitle: String? = null,
    onSubtitleClick: (() -> Unit)? = null,
    borderWidth: Dp = 1.dp,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    shape: Shape = BoxDefaults.OutlinedBoxShape,
    contentPadding: PaddingValues = BoxDefaults.ContentPadding,
    content: @Composable ColumnScope.() -> Unit,
) {
    FlorisOutlinedBox(
        modifier = modifier,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        onTitleClick = onTitleClick,
        subtitle = if (subtitle != null) {
            {
                Text(
                    modifier = Modifier
                        .padding(start = 6.dp, end = 6.dp, bottom = 4.dp),
                    text = subtitle,
                    color = LocalContentColor.current.copy(alpha = 0.56f),
                    fontWeight = FontWeight.Normal,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                )
            }
        } else {
            null
        },
        onSubtitleClick = onSubtitleClick,
        borderWidth = borderWidth,
        borderColor = borderColor,
        shape = shape,
        contentPadding = contentPadding,
        content = content,
    )
}

// TODO: Rework internal implementation (with same API and visual appearance) of FlorisOutlinedBox
//  to avoid too much nesting and improve performance
@Composable
fun FlorisOutlinedBox(
    modifier: Modifier = Modifier,
    title: (@Composable () -> Unit)? = null,
    onTitleClick: (() -> Unit)? = null,
    subtitle: (@Composable () -> Unit)? = null,
    onSubtitleClick: (() -> Unit)? = null,
    borderWidth: Dp = 1.dp,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    shape: Shape = BoxDefaults.OutlinedBoxShape,
    contentPadding: PaddingValues = BoxDefaults.ContentPadding,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .padding(top = if (title != null) 11.dp else 0.dp),
    ) {
        Column(
            modifier = Modifier
                .border(borderWidth, borderColor, shape)
                .clip(shape)
                .padding(top = if (title != null) 11.dp else 0.dp),
        ) {
            if (title != null && subtitle != null) {
                Box(
                    modifier = Modifier
                        .padding(start = 10.dp, bottom = 4.dp)
                        .rippleClickable(enabled = onSubtitleClick != null) {
                            onSubtitleClick!!()
                        },
                ) {
                    subtitle()
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(contentPadding),
                content = content,
            )
        }
        if (title != null) {
            Box(
                modifier = Modifier
                    .height(23.dp)
                    .offset(x = 10.dp, y = (-12).dp)
                    .background(MaterialTheme.colorScheme.background)
                    .rippleClickable(enabled = onTitleClick != null) {
                        onTitleClick!!()
                    }
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                title()
            }
        }
    }
}

fun Modifier.defaultFlorisOutlinedBox(): Modifier {
    return this
        .fillMaxWidth()
        .padding(vertical = 8.dp, horizontal = 16.dp)
}

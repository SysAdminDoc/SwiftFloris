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

package dev.patrickgold.florisboard.ime.media.emoji

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.ime.theme.snyggErrorForegroundFor
import dev.patrickgold.florisboard.lib.compose.DynamicFontScale
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.rememberSnyggThemeQuery

/**
 * In-keyboard "Pin to group..." surface for ROADMAP §7 Next-9.4a.
 *
 * The logic lives in [PinToGroupSheetState]; this composable only renders
 * existing-group rows, the new-group field, and the create action. [version]
 * is a caller-owned invalidation counter because the state object is
 * intentionally Compose-agnostic for JVM unit tests.
 */
@Composable
fun PinToGroupSheet(
    state: PinToGroupSheetState,
    version: Int,
    onStateChanged: () -> Unit,
    onPinned: (groupName: String) -> Unit,
) {
    if (!state.isVisible()) return

    val windowStyle = rememberSnyggThemeQuery(FlorisImeUi.Window.elementName)
    val panelStyle = rememberSnyggThemeQuery(FlorisImeUi.MediaEmojiKeyPopupBox.elementName)
    val tabStyle = rememberSnyggThemeQuery(FlorisImeUi.MediaEmojiTab.elementName)
    val errorStyle = rememberSnyggThemeQuery(FlorisImeUi.MediaEmojiPinSheetError.elementName)
    val foreground = tabStyle.foreground(default = windowStyle.foreground(default = Color.White))
    val windowBackground = windowStyle.background(default = Color(0xFF171923))
    val background = panelStyle.background(default = windowBackground)
    val errorForeground = errorStyle.foreground(
        default = snyggErrorForegroundFor(background, behind = windowBackground),
    )
    val fieldBackground = foreground.copy(alpha = 0.10f)
    val actionBackground = foreground.copy(alpha = 0.16f)

    fun dismiss() {
        state.dismiss()
        onStateChanged()
    }

    key(version) {
        Popup(
            alignment = Alignment.BottomCenter,
            onDismissRequest = ::dismiss,
        ) {
            SnyggBox(
                elementName = FlorisImeUi.MediaEmojiKeyPopupBox.elementName,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(background)
                    .padding(12.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringRes(R.string.emoji__pin_group__title, "emoji" to state.emoji()),
                        color = foreground,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )

                    val existingGroups = state.existingGroups()
                    if (existingGroups.isEmpty()) {
                        Text(
                            text = stringRes(R.string.emoji__pin_group__existing_empty),
                            color = foreground.copy(alpha = 0.72f),
                            fontSize = 12.sp,
                        )
                    } else {
                        for (groupName in existingGroups) {
                            val groupActionLabel = stringRes(
                                R.string.emoji__pin_group__existing_group_a11y,
                                "group" to groupName,
                            )
                            ExistingGroupRow(
                                groupName = groupName,
                                preview = state.emojisForExistingGroup(groupName)
                                    .take(PinnedGroupChip.PREVIEW_LIMIT)
                                    .joinToString(separator = ""),
                                actionLabel = groupActionLabel,
                                foreground = foreground,
                                background = actionBackground,
                                onClick = {
                                    val pinned = state.pinToExisting(groupName)
                                    onStateChanged()
                                    if (pinned) onPinned(groupName)
                                },
                            )
                        }
                    }

                    val fieldContentDescription = stringRes(R.string.emoji__pin_group__field_content_description)
                    BasicTextField(
                        value = state.newGroupNameInput(),
                        onValueChange = { text ->
                            state.updateNewGroupName(text)
                            onStateChanged()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                contentDescription = fieldContentDescription
                            }
                            .clip(RoundedCornerShape(8.dp))
                            .background(fieldBackground)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        singleLine = true,
                        textStyle = TextStyle(
                            color = foreground,
                            fontSize = 14.sp,
                        ),
                        decorationBox = { innerTextField ->
                            Box(modifier = Modifier.fillMaxWidth()) {
                                if (state.newGroupNameInput().isBlank()) {
                                    Text(
                                        text = stringRes(R.string.emoji__pin_group__new_group_placeholder),
                                        color = foreground.copy(alpha = 0.56f),
                                        fontSize = 14.sp,
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )

                    state.error()?.let { error ->
                        val errorText = pinErrorText(error)
                        Text(
                            modifier = Modifier.semantics {
                                contentDescription = errorText
                                liveRegion = LiveRegionMode.Assertive
                            },
                            text = errorText,
                            color = errorForeground,
                            fontSize = 12.sp,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SheetAction(
                            label = stringRes(R.string.action__cancel),
                            foreground = foreground,
                            background = Color.Transparent,
                            onClick = ::dismiss,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        SheetAction(
                            label = stringRes(R.string.emoji__pin_group__create),
                            foreground = foreground,
                            background = actionBackground,
                            onClick = {
                                val pinned = state.createGroupAndPin()
                                val groupName = state.lastPinnedGroupName()
                                onStateChanged()
                                if (pinned && groupName != null) onPinned(groupName)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExistingGroupRow(
    groupName: String,
    preview: String,
    actionLabel: String,
    foreground: Color,
    background: Color,
    onClick: () -> Unit,
) {
    val groupNameMaxLines = DynamicFontScale.maxLines(
        compact = 1,
        expanded = 2,
        fontScale = LocalDensity.current.fontScale,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = actionLabel
                onClick(label = actionLabel) {
                    onClick()
                    true
                }
            }
            .pointerInput(groupName) {
                detectTapGestures { onClick() }
            }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = groupName,
            color = foreground,
            fontSize = 13.sp,
            maxLines = groupNameMaxLines,
            overflow = TextOverflow.Ellipsis,
        )
        if (preview.isNotBlank()) {
            Text(
                text = preview,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun SheetAction(
    label: String,
    foreground: Color,
    background: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                onClick(label = label) {
                    onClick()
                    true
                }
            }
            .pointerInput(label) {
                detectTapGestures { onClick() }
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = foreground,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun pinErrorText(error: PinToGroupSheetState.PinError): String = when (error) {
    PinToGroupSheetState.PinError.NoEmojiSelected ->
        stringRes(R.string.emoji__pin_group__error_no_emoji)
    PinToGroupSheetState.PinError.GroupNameBlank ->
        stringRes(R.string.emoji__pin_group__error_blank)
    PinToGroupSheetState.PinError.TooManyGroups ->
        stringRes(R.string.emoji__pin_group__error_too_many_groups)
    PinToGroupSheetState.PinError.GroupFull ->
        stringRes(R.string.emoji__pin_group__error_group_full)
    PinToGroupSheetState.PinError.AlreadyPinned ->
        stringRes(R.string.emoji__pin_group__error_already_pinned)
}

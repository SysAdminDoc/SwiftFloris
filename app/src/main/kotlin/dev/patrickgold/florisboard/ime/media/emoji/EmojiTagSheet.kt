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
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.rememberSnyggThemeQuery

/** In-keyboard sheet for adding and removing user-defined emoji tags. */
@Composable
fun EmojiTagSheet(
    state: EmojiTagSheetState,
    version: Int,
    onStateChanged: () -> Unit,
    onTagged: (String) -> Unit,
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
                        text = stringRes(R.string.emoji__custom_tag__title, "emoji" to state.emoji()),
                        color = foreground,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )

                    val existingTags = state.existingTags()
                    if (existingTags.isEmpty()) {
                        Text(
                            text = stringRes(R.string.emoji__custom_tag__empty),
                            color = foreground.copy(alpha = 0.72f),
                            fontSize = 12.sp,
                        )
                    } else {
                        for (tag in existingTags) {
                            TagRow(
                                tag = tag,
                                actionLabel = stringRes(
                                    R.string.emoji__custom_tag__remove,
                                    "tag" to tag,
                                    "emoji" to state.emoji(),
                                ),
                                foreground = foreground,
                                background = actionBackground,
                                onClick = {
                                    if (state.removeTag(tag)) onStateChanged()
                                },
                            )
                        }
                    }

                    val fieldContentDescription = stringRes(R.string.emoji__custom_tag__field_a11y)
                    BasicTextField(
                        value = state.tagInput(),
                        onValueChange = { text ->
                            state.updateTagInput(text)
                            onStateChanged()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = fieldContentDescription }
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
                                if (state.tagInput().isBlank()) {
                                    Text(
                                        text = stringRes(R.string.emoji__custom_tag__field_placeholder),
                                        color = foreground.copy(alpha = 0.56f),
                                        fontSize = 14.sp,
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )

                    state.error()?.let { error ->
                        val errorText = tagErrorText(error)
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
                        TagSheetAction(
                            label = stringRes(R.string.action__cancel),
                            foreground = foreground,
                            background = Color.Transparent,
                            onClick = ::dismiss,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TagSheetAction(
                            label = stringRes(R.string.emoji__custom_tag__add),
                            foreground = foreground,
                            background = actionBackground,
                            onClick = {
                                if (state.addTag()) {
                                    val added = state.lastAddedTag()
                                    onStateChanged()
                                    if (added != null) onTagged(added)
                                } else {
                                    onStateChanged()
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TagRow(
    tag: String,
    actionLabel: String,
    foreground: Color,
    background: Color,
    onClick: () -> Unit,
) {
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
            .pointerInput(tag) { detectTapGestures { onClick() } }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = tag,
            color = foreground,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringRes(R.string.emoji__custom_tag__remove_short),
            color = foreground.copy(alpha = 0.72f),
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun TagSheetAction(
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
            .pointerInput(label) { detectTapGestures { onClick() } }
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
private fun tagErrorText(error: EmojiTagSheetState.TagError): String = when (error) {
    EmojiTagSheetState.TagError.NoEmojiSelected ->
        stringRes(R.string.emoji__custom_tag__error_no_emoji)
    EmojiTagSheetState.TagError.TagBlank ->
        stringRes(R.string.emoji__custom_tag__error_blank)
    EmojiTagSheetState.TagError.Duplicate ->
        stringRes(R.string.emoji__custom_tag__error_duplicate)
    EmojiTagSheetState.TagError.TooManyTags ->
        stringRes(R.string.emoji__custom_tag__error_too_many_tags)
    EmojiTagSheetState.TagError.TooManyEmoji ->
        stringRes(R.string.emoji__custom_tag__error_too_many_emoji)
}

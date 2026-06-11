/*
 * Copyright (C) 2022-2025 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.ime.media

import android.annotation.SuppressLint
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.ime.input.InputEventDispatcher
import dev.patrickgold.florisboard.ime.input.LocalInputFeedbackController
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.keyboard.KeyData
import dev.patrickgold.florisboard.ime.media.emoji.EmojiData
import dev.patrickgold.florisboard.ime.media.emoji.EmojiPaletteView
import dev.patrickgold.florisboard.ime.media.sticker.StickerPaletteView
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.keyboardManager
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.snygg.SnyggSelector
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.SnyggColumn
import org.florisboard.lib.snygg.ui.SnyggRow

private enum class MediaPanelMode {
    EMOJI,
    STICKERS,
}

@SuppressLint("MutableCollectionMutableState")
@Composable
fun MediaInputLayout(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()

    var emojiLayoutDataMap by remember { mutableStateOf(EmojiData.Fallback) }
    var activeMode by remember { mutableStateOf(MediaPanelMode.EMOJI) }
    LaunchedEffect(Unit) {
        emojiLayoutDataMap = EmojiData.get(context, "ime/media/emoji/root.txt")
    }

    SnyggColumn(
        elementName = FlorisImeUi.Media.elementName,
        modifier = modifier
            .fillMaxWidth()
            .height(FlorisImeSizing.imeUiHeight()),
    ) {
        when (activeMode) {
            MediaPanelMode.EMOJI -> {
                EmojiPaletteView(
                    modifier = Modifier.weight(1f),
                    fullEmojiMappings = emojiLayoutDataMap,
                )
            }
            MediaPanelMode.STICKERS -> {
                StickerPaletteView(
                    modifier = Modifier.weight(1f),
                )
            }
        }
        SnyggRow(
            elementName = FlorisImeUi.MediaBottomRow.elementName,
            modifier = Modifier
                .fillMaxWidth()
                .height(FlorisImeSizing.keyboardRowBaseHeight * 0.8f),
        ) {
            KeyboardLikeButton(
                elementName = FlorisImeUi.MediaBottomRowButton.elementName,
                inputEventDispatcher = keyboardManager.inputEventDispatcher,
                keyData = TextKeyData.IME_UI_MODE_TEXT,
                modifier = Modifier.fillMaxHeight(),
                contentDescription = stringRes(R.string.clip__back_to_text_input),
            ) {
                Text(
                    text = "ABC",
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            MediaModeButton(
                active = activeMode == MediaPanelMode.EMOJI,
                description = stringRes(R.string.media__tab__emojis),
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f),
                onClick = { activeMode = MediaPanelMode.EMOJI },
            ) {
                Icon(imageVector = Icons.Default.EmojiEmotions, contentDescription = null)
            }
            MediaModeButton(
                active = activeMode == MediaPanelMode.STICKERS,
                description = stringRes(R.string.media__tab__stickers),
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f),
                onClick = { activeMode = MediaPanelMode.STICKERS },
            ) {
                Icon(imageVector = Icons.Default.Image, contentDescription = null)
            }
            Spacer(modifier = Modifier.weight(1f))
            KeyboardLikeButton(
                elementName = FlorisImeUi.MediaBottomRowButton.elementName,
                inputEventDispatcher = keyboardManager.inputEventDispatcher,
                keyData = TextKeyData.DELETE,
                modifier = Modifier.fillMaxHeight(),
                contentDescription = stringRes(R.string.key__backspace),
            ) {
                Icon(imageVector = Icons.AutoMirrored.Outlined.Backspace, contentDescription = null)
            }
        }
    }
}

@Composable
private fun MediaModeButton(
    active: Boolean,
    description: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val inputFeedbackController = LocalInputFeedbackController.current
    val selector = if (active) {
        SnyggSelector.FOCUS
    } else {
        SnyggSelector.NONE
    }
    SnyggBox(
        elementName = FlorisImeUi.MediaBottomRowButton.elementName,
        selector = selector,
        clickAndSemanticsModifier = modifier
            .semantics(mergeDescendants = true) {
                role = Role.Tab
                selected = active
                contentDescription = description
                onClick(label = null) {
                    inputFeedbackController.keyPress(TextKeyData.UNSPECIFIED)
                    onClick()
                    true
                }
            }
            .pointerInput(Unit) {
                detectTapGestures {
                    inputFeedbackController.keyPress(TextKeyData.UNSPECIFIED)
                    onClick()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
internal fun KeyboardLikeButton(
    modifier: Modifier = Modifier,
    inputEventDispatcher: InputEventDispatcher,
    keyData: KeyData,
    elementName: String = FlorisImeUi.MediaEmojiKey.elementName,
    contentDescription: String? = null,
    content: @Composable () -> Unit,
) {
    val inputFeedbackController = LocalInputFeedbackController.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val selector = if (isPressed) {
        SnyggSelector.PRESSED
    } else {
        SnyggSelector.NONE
    }
    val description = contentDescription

    SnyggBox(
        elementName = elementName,
        attributes = mapOf(FlorisImeUi.Attr.Code to keyData.code),
        selector = selector,
        clickAndSemanticsModifier = modifier
            .semantics(mergeDescendants = true) {
                role = Role.Button
                if (description != null) {
                    this.contentDescription = description
                }
                onClick(label = null) {
                    inputFeedbackController.keyPress(keyData)
                    inputEventDispatcher.sendDownUp(keyData)
                    true
                }
            }
            .indication(interactionSource, ripple())
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false).also {
                        if (it.pressed != it.previousPressed) it.consume()
                    }
                    val press = PressInteraction.Press(down.position)
                    interactionSource.tryEmit(press)
                    inputEventDispatcher.sendDown(keyData)
                    inputFeedbackController.keyPress(keyData)
                    val up = waitForUpOrCancellation()
                    if (up != null) {
                        interactionSource.tryEmit(PressInteraction.Release(press))
                        inputEventDispatcher.sendUp(keyData)
                    } else {
                        interactionSource.tryEmit(PressInteraction.Cancel(press))
                        inputEventDispatcher.sendCancel(keyData)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

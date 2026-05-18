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

package dev.patrickgold.florisboard.lib.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.lib.util.InputMethodUtils
import kotlinx.coroutines.launch
import org.florisboard.lib.android.showShortToast
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.compose.verticalTween

private const val AnimationDuration = 200

private val PreviewEnterTransition = EnterTransition.verticalTween(AnimationDuration)
private val PreviewExitTransition = ExitTransition.verticalTween(AnimationDuration)

val LocalPreviewFieldController = staticCompositionLocalOf<PreviewFieldController?> { null }

@Composable
fun rememberPreviewFieldController(): PreviewFieldController {
    return remember { PreviewFieldController() }
}

class PreviewFieldController {
    val focusRequester = FocusRequester()
    var isVisible by mutableStateOf(false)
    var isFocused by mutableStateOf(false)
    var text by mutableStateOf(TextFieldValue(""))
}

@Composable
fun PreviewKeyboardField(
    controller: PreviewFieldController,
    modifier: Modifier = Modifier,
    hint: String = stringRes(R.string.settings__preview_keyboard),
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme
    val stateLabel = stringRes(
        if (controller.isFocused) {
            R.string.settings__preview_keyboard__state_active
        } else {
            R.string.settings__preview_keyboard__state_ready
        },
    )
    val switchKeyboardLabel = stringRes(R.string.settings__preview_keyboard__switch_keyboard)

    AnimatedVisibility(
        visible = controller.isVisible,
        enter = PreviewEnterTransition,
        exit = PreviewExitTransition,
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .semantics {
                    traversalIndex = FlorisScreenFocusOrder.BottomBar
                },
            color = colorScheme.surfaceContainer,
            contentColor = colorScheme.onSurface,
            tonalElevation = 3.dp,
            shadowElevation = 1.dp,
        ) {
            Column {
                HorizontalDivider(color = colorScheme.outlineVariant)
                SelectionContainer {
                    TextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .onFocusChanged { controller.isFocused = it.isFocused }
                            .onPreviewKeyEvent { event ->
                                if (event.key == Key.Back) {
                                    focusManager.clearFocus()
                                }
                                false
                            }
                            .focusRequester(controller.focusRequester),
                        value = controller.text,
                        onValueChange = { controller.text = it },
                        textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.ContentOrLtr),
                        label = {
                            Text(
                                text = stringRes(R.string.settings__preview_keyboard__label),
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1,
                            )
                        },
                        placeholder = {
                            Text(
                                text = hint,
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1,
                            )
                        },
                        supportingText = {
                            Text(
                                text = stateLabel,
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1,
                            )
                        },
                        trailingIcon = {
                            Row {
                                IconButton(onClick = {
                                    if (!InputMethodUtils.showImePicker(context)) {
                                        scope.launch {
                                            context.showShortToast(
                                                R.string.settings__preview_keyboard__ime_picker_unavailable,
                                            )
                                        }
                                    }
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Keyboard,
                                        contentDescription = switchKeyboardLabel,
                                    )
                                }
                            }
                        },
                        keyboardActions = KeyboardActions(
                            onDone = { focusManager.clearFocus() },
                        ),
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = true),
                        singleLine = true,
                        shape = RectangleShape,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = colorScheme.surfaceContainerHigh,
                            disabledContainerColor = colorScheme.surfaceContainerHigh,
                            focusedTrailingIconColor = colorScheme.primary,
                            unfocusedTrailingIconColor = colorScheme.onSurfaceVariant,
                            cursorColor = colorScheme.primary,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                        ),
                    )
                }
            }
        }
    }
}

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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import dev.patrickgold.jetpref.material.ui.JetPrefDropdownMenuDefaults
import dev.patrickgold.jetpref.material.ui.JetPrefTextField
import dev.patrickgold.jetpref.material.ui.JetPrefTextFieldAppearance


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlorisDropdownLikeButton(
    item: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    errorMessage: String? = null,
    onClick: () -> Unit = { },
    appearance: JetPrefTextFieldAppearance = JetPrefDropdownMenuDefaults.filled(),
) {
    Box(
        modifier = modifier.wrapContentSize(Alignment.TopStart)
    ) {
        // The field only shows the value. A clear layer on top takes the click, so a quick tap, TalkBack's
        // double-tap and Switch Access (clicks with no press) open it too, and screen readers hear one
        // dropdown with the value instead of an editable field. The field never takes focus either, so Tab and
        // the D-pad stop once, on the layer, where Enter opens it.
        val interactionSource = remember { MutableInteractionSource() }

        JetPrefTextField(
            modifier = Modifier
                .fillMaxWidth()
                .focusProperties { canFocus = false }
                .clearAndSetSemantics { },
            value = item,
            onValueChange = {},
            enabled = true,
            readOnly = true,
            isError = isError,
            singleLine = true,
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(
                    expanded = true,
                    modifier = Modifier.rotate(90f), //Arrow to the right
                )
            },
            appearance = appearance,
            interactionSource = interactionSource,
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                // The field's own semantics are cleared, so its error has to be repeated here for screen readers.
                .semantics {
                    text = AnnotatedString(item)
                    if (isError) error(errorMessage.orEmpty())
                }
                // Shares the field's interaction source, so the field still shows its own focus and press look.
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.DropdownList,
                    onClick = onClick,
                ),
        )
    }
}

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

package dev.patrickgold.florisboard.ime.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.lib.compose.DynamicFontScale
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.snygg.ui.SnyggIcon
import org.florisboard.lib.snygg.ui.SnyggIconButton
import org.florisboard.lib.snygg.ui.SnyggRow
import org.florisboard.lib.snygg.ui.rememberSnyggThemeQuery

/**
 * Shown in place of the Smartbar while the letter keys type into a field the
 * keyboard draws itself. Back keeps the text the field had, Done applies it.
 */
@Composable
fun InKeyboardTextEntryBar(
    session: InKeyboardTextEntry.Session,
    onCancel: () -> Unit,
    onClear: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val windowStyle = rememberSnyggThemeQuery(FlorisImeUi.Window.elementName)
    val smartbarStyle = rememberSnyggThemeQuery(FlorisImeUi.Smartbar.elementName)
    val foreground = smartbarStyle.foreground(default = windowStyle.foreground(default = Color.White))
    val fontSize = DynamicFontScale.fixedGeometrySp(16f, LocalDensity.current.fontScale)
    val hint = session.request.hint
    val typingInto = stringRes(R.string.text_entry__typing_into, "field" to hint)
    val buttonModifier = Modifier
        .sizeIn(maxHeight = FlorisImeSizing.smartbarHeight)
        .aspectRatio(1f)

    SnyggRow(
        elementName = FlorisImeUi.Smartbar.elementName,
        modifier = modifier
            .fillMaxWidth()
            .height(FlorisImeSizing.smartbarHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SnyggIconButton(
            elementName = FlorisImeUi.SmartbarActionKey.elementName,
            modifier = buttonModifier,
            onClick = onCancel,
        ) {
            SnyggIcon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringRes(R.string.action__back),
            )
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = typingInto
                    stateDescription = session.text
                    liveRegion = LiveRegionMode.Polite
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (session.text.isEmpty()) {
                TextEntryCaret(color = foreground)
                Text(
                    modifier = Modifier.padding(start = 2.dp),
                    text = hint,
                    color = foreground.copy(alpha = 0.58f),
                    fontSize = fontSize,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                // Long text keeps its end in view, where the caret is.
                Text(
                    modifier = Modifier.weight(1f, fill = false),
                    text = session.text,
                    color = foreground,
                    fontSize = fontSize,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.StartEllipsis,
                )
                TextEntryCaret(color = foreground)
            }
        }
        if (session.text.isNotEmpty()) {
            SnyggIconButton(
                elementName = FlorisImeUi.SmartbarActionKey.elementName,
                modifier = buttonModifier,
                onClick = onClear,
            ) {
                SnyggIcon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringRes(R.string.text_entry__clear),
                )
            }
        }
        SnyggIconButton(
            elementName = FlorisImeUi.SmartbarActionKey.elementName,
            modifier = buttonModifier,
            onClick = onDone,
        ) {
            SnyggIcon(
                imageVector = Icons.Default.Check,
                contentDescription = stringRes(R.string.action__done),
            )
        }
    }
}

@Composable
private fun TextEntryCaret(color: Color) {
    Box(
        modifier = Modifier
            .width(2.dp)
            .height(20.dp)
            .background(color),
    )
}

/**
 * Stands in for a text field inside the keyboard. Tapping it hands the
 * letter keys to the field through [KeyboardManager.beginTextEntry].
 */
@Composable
fun InKeyboardTextField(
    text: String,
    placeholder: String,
    contentDescription: String,
    color: Color,
    fontSize: TextUnit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholderColor: Color = color.copy(alpha = 0.58f),
) {
    val typeLabel = stringRes(R.string.text_entry__type_action)
    Box(
        modifier = modifier
            .clickable(
                interactionSource = null,
                indication = null,
                onClickLabel = typeLabel,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics {
                this.contentDescription = contentDescription
                if (text.isNotEmpty()) stateDescription = text
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text.ifEmpty { placeholder },
            color = if (text.isEmpty()) placeholderColor else color,
            fontSize = fontSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

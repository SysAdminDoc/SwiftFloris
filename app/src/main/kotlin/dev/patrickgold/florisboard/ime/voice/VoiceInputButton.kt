/*
 * Copyright (C) 2025 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.ime.voice

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import dev.patrickgold.florisboard.FlorisImeService
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.snygg.ui.rememberSnyggThemeQuery

@Composable
fun VoiceInputButton(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val voiceInputManager = FlorisImeService.voiceInputManagerOrNull() ?: return

    var showSetupDialog by remember { mutableStateOf(false) }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val themeStyle = rememberSnyggThemeQuery(FlorisImeUi.VoiceInputButton.elementName)
    val buttonBackground = themeStyle.background(default = Color(0xFF6200EE))
    val buttonForeground = themeStyle.foreground(default = Color.White)

    Box(
        modifier = modifier.size(64.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(buttonBackground)
                .border(
                    width = if (isPressed) 2.dp else 1.dp,
                    color = buttonForeground.copy(alpha = 0.5f),
                    shape = CircleShape,
                )
                .scale(if (isPressed) 0.95f else 1f),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(
                onClick = {
                    if (!voiceInputManager.startListening()) {
                        showSetupDialog = true
                    }
                },
                interactionSource = interactionSource,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = stringRes(R.string.quick_action__voice_input__tooltip),
                    tint = buttonForeground,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }

    if (showSetupDialog) {
        VoiceInputSetupDialog(
            futoInstalled = voiceInputManager.isFutoVoiceInputInstalled(),
            onDismiss = { showSetupDialog = false },
            onOpenKeyboardSettings = {
                openKeyboardSettings(context)
                showSetupDialog = false
            },
            onInstallFuto = {
                openFutoInstallPage(context)
                showSetupDialog = false
            },
        )
    }
}

@Composable
private fun VoiceInputSetupDialog(
    futoInstalled: Boolean,
    onDismiss: () -> Unit,
    onOpenKeyboardSettings: () -> Unit,
    onInstallFuto: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (futoInstalled) {
                    stringRes(R.string.voice_input__enable_provider__title)
                } else {
                    stringRes(R.string.voice_input__install_provider__title)
                },
            )
        },
        text = {
            Column {
                Text(
                    if (futoInstalled) {
                        stringRes(R.string.voice_input__enable_provider__message)
                    } else {
                        stringRes(R.string.voice_input__install_provider__message)
                    },
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringRes(R.string.voice_input__setup_hint))
            }
        },
        confirmButton = {
            TextButton(
                onClick = if (futoInstalled) onOpenKeyboardSettings else onInstallFuto,
            ) {
                Text(
                    if (futoInstalled) {
                        stringRes(R.string.action__open_settings)
                    } else {
                        stringRes(R.string.action__install)
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringRes(R.string.action__cancel))
            }
        },
    )
}

private fun openKeyboardSettings(context: Context) {
    val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).apply {
        addCategory(Intent.CATEGORY_DEFAULT)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

private fun openFutoInstallPage(context: Context) {
    val fdroidIntent = Intent(Intent.ACTION_VIEW, VoiceInputManager.FUTO_FDROID_URL.toUri()).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (context.tryStartActivity(fdroidIntent)) return

    val releasesIntent = Intent(Intent.ACTION_VIEW, VoiceInputManager.FUTO_RELEASES_URL.toUri()).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.tryStartActivity(releasesIntent)
}

private fun Context.tryStartActivity(intent: Intent): Boolean {
    return runCatching {
        startActivity(intent)
        true
    }.getOrDefault(false)
}

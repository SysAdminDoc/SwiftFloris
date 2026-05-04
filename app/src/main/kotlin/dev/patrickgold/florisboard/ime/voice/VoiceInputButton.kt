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

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.FlorisImeService
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import org.florisboard.lib.snygg.ui.rememberSnyggThemeQuery

/**
 * A circular voice input button that launches FUTO Voice Input.
 * FUTO Voice Input must be installed separately from Play Store or F-Droid.
 * 
 * When clicked, opens the FUTO Voice Input app for on-device, offline speech-to-text.
 * No internet connection required, all processing happens locally.
 */
@Composable
fun VoiceInputButton(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val voiceInputManager = FlorisImeService.voiceInputManagerOrNull() ?: return

    val transcriptionState by voiceInputManager.transcriptionState.collectAsState()
    
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val themeStyle = rememberSnyggThemeQuery(FlorisImeUi.VoiceInputButton.elementName)
    val buttonBackground = themeStyle.background(default = Color(0xFF6200EE))
    val buttonForeground = themeStyle.foreground(default = Color.White)

    Box(
        modifier = modifier.size(64.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = transcriptionState != TranscriptionState.Unavailable,
            enter = scaleIn(),
            exit = scaleOut(),
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
                        // Launch FUTO Voice Input app
                        try {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setPackage("org.futo.voiceinput")
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // FUTO Voice Input not installed
                        }
                    },
                    interactionSource = interactionSource,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Open FUTO Voice Input",
                        tint = buttonForeground,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
    }
}


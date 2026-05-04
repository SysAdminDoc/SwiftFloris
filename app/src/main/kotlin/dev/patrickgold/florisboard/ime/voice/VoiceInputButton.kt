package dev.patrickgold.florisboard.ime.voice

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.runtime.collectAsState
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
import dev.patrickgold.florisboard.FlorisImeService
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import org.florisboard.lib.snygg.ui.rememberSnyggThemeQuery

/**
 * A circular voice input button that launches FUTO Voice Input.
 * FUTO Voice Input must be installed separately from Play Store or F-Droid.
 * 
 * When clicked, opens the FUTO Voice Input app for on-device, offline speech-to-text.
 * No internet connection required, all processing happens locally.
 * 
 * If FUTO is not installed, shows a friendly dialog with installation options.
 */
@Composable
fun VoiceInputButton(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val voiceInputManager = FlorisImeService.voiceInputManagerOrNull() ?: return

    val transcriptionState by voiceInputManager.transcriptionState.collectAsState()
    var showNotInstalledDialog by remember { mutableStateOf(false) }
    
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
                        // Launch FUTO Voice Input app with graceful degradation
                        try {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setPackage("org.futo.voiceinput")
                            }
                            
                            // Check if FUTO is available before launching
                            val resolvedActivities = context.packageManager.queryIntentActivities(intent, 0)
                            if (resolvedActivities.isNotEmpty()) {
                                context.startActivity(intent)
                            } else {
                                // FUTO not installed - show friendly dialog
                                showNotInstalledDialog = true
                            }
                        } catch (e: Exception) {
                            // Fallback: show not installed dialog
                            showNotInstalledDialog = true
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

    // Dialog for FUTO not installed
    if (showNotInstalledDialog) {
        AlertDialog(
            onDismissRequest = { showNotInstalledDialog = false },
            title = { Text("FUTO Voice Input Not Installed") },
            text = {
                Column {
                    Text("Voice input requires FUTO Voice Input, a free and open-source offline speech-to-text app.")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Would you like to install it?")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // Open F-Droid listing for FUTO Voice Input
                        val futoFdroidUrl = "https://f-droid.org/packages/org.futo.voiceinput/"
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(futoFdroidUrl))
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // Fallback to FUTO GitHub releases
                            val futoGithubUrl = "https://github.com/FUTO-org/android-voice-input/releases"
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(futoGithubUrl)))
                        }
                        showNotInstalledDialog = false
                    }
                ) {
                    Text("Install")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showNotInstalledDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}


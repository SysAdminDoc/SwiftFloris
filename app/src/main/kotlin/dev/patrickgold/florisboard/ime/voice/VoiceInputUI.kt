package dev.patrickgold.florisboard.ime.voice

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.sin

/**
 * Voice input UI component for FlorisBoard keyboard
 *
 * Displays:
 * - Animated mic button
 * - Real-time transcription
 * - Confidence score
 * - Error messages
 */
@Composable
fun VoiceInputButton(
    voiceManager: VoiceInputManager,
    modifier: Modifier = Modifier,
    onTextInsert: (String) -> Unit = {}
) {
    val transcriptionState by voiceManager.transcriptionState.collectAsState()
    val recognizedText by voiceManager.recognizedText.collectAsState()
    val confidence by voiceManager.confidence.collectAsState()
    val isListening by voiceManager.isListening.collectAsState()
    val error by voiceManager.error.collectAsState()

    val isRecording = transcriptionState == TranscriptionState.Listening || isListening

    Column(
        modifier = modifier
            .padding(8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp)
    ) {
        // Mic Button
        Box(
            modifier = Modifier
                .size(64.dp)
                .align(Alignment.CenterHorizontally),
            contentAlignment = Alignment.Center
        ) {
            if (isRecording) {
                // Animated pulse during recording
                repeat(3) { index ->
                    val scale = 1f + (0.5f * ((index + 1) / 3f))
                    val animatedAlpha = remember {
                        androidx.compose.animation.core.Animatable(0.5f)
                    }

                    LaunchedEffect(Unit) {
                        animatedAlpha.animateTo(
                            targetValue = 0f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1500, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(64.dp * scale)
                            .scale(1f / scale)
                            .clip(CircleShape)
                            .background(
                                MaterialTheme.colorScheme.primary
                                    .copy(alpha = animatedAlpha.value * 0.3f)
                            )
                    )
                }
            }

            FloatingActionButton(
                onClick = {
                    if (isRecording) {
                        voiceManager.stopListening()
                    } else {
                        voiceManager.startListening()
                    }
                },
                modifier = Modifier.size(56.dp),
                containerColor = if (isRecording) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = if (isRecording) Icons.Default.Mic else Icons.Default.MicOff,
                    contentDescription = if (isRecording) "Stop recording" else "Start recording",
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        // Transcription Display
        if (recognizedText.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Recognized:",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = recognizedText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .background(
                        MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(8.dp)
            )

            // Confidence Indicator
            if (confidence > 0f) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Confidence:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    LinearProgressIndicator(
                        progress = { confidence },
                        modifier = Modifier
                            .height(4.dp)
                            .weight(1f)
                            .clip(RoundedCornerShape(2.dp)),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )

                    Text(
                        text = "${(confidence * 100).toInt()}%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = when {
                            confidence > 0.7f -> Color(0xFF4CAF50)  // Green
                            confidence > 0.4f -> Color(0xFFFFC107)  // Amber
                            else -> Color(0xFFF44336)               // Red
                        }
                    )
                }
            }

            // Insert Button
            Button(
                onClick = {
                    onTextInsert(recognizedText)
                    voiceManager.cancel()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary
                )
            ) {
                Text("Insert Text", fontWeight = FontWeight.SemiBold)
            }
        }

        // Status Messages
        when (transcriptionState) {
            TranscriptionState.Listening -> {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Listening...",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }

            TranscriptionState.Processing -> {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Processing", fontSize = 12.sp)
                    repeat(3) {
                        Box(
                            modifier = Modifier
                                .size(3.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }

            TranscriptionState.Unavailable -> {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Voice input not available",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }

            else -> {}
        }

        // Error Display
        error?.let { voiceError ->
            Spacer(modifier = Modifier.height(8.dp))

            val errorMessage = when (voiceError) {
                VoiceError.NotAvailable -> "Speech recognition not available"
                VoiceError.PermissionDenied -> "Microphone permission denied"
                VoiceError.NoMatch -> "No speech detected"
                VoiceError.NetworkError -> "Network error"
                VoiceError.NetworkTimeout -> "Network timeout"
                VoiceError.SpeechTimeout -> "No speech after timeout"
                VoiceError.AudioError -> "Microphone error"
                VoiceError.ServerError -> "Server error"
                is VoiceError.StartFailed -> "Failed to start: ${voiceError.message}"
                is VoiceError.StopFailed -> "Failed to stop: ${voiceError.message}"
                else -> "Voice input error"
            }

            Text(
                text = errorMessage,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.error,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Retry Button
            if (transcriptionState == TranscriptionState.Error) {
                Button(
                    onClick = { voiceManager.startListening() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    colors = ButtonDefaults.outlinedButtonColors()
                ) {
                    Text("Retry")
                }
            }
        }
    }
}

/**
 * Minimal voice input FAB for toolbar
 */
@Composable
fun VoiceInputFAB(
    voiceManager: VoiceInputManager,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val isListening by voiceManager.isListening.collectAsState()
    val error by voiceManager.error.collectAsState()

    FloatingActionButton(
        onClick = {
            if (isListening) {
                voiceManager.stopListening()
            } else {
                voiceManager.startListening()
            }
            onClick()
        },
        modifier = modifier,
        containerColor = if (isListening) {
            MaterialTheme.colorScheme.error
        } else if (error != null) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.primary
        }
    ) {
        Icon(
            imageVector = if (isListening) Icons.Default.Mic else Icons.Default.MicOff,
            contentDescription = "Voice input",
            modifier = Modifier.size(24.dp)
        )
    }
}

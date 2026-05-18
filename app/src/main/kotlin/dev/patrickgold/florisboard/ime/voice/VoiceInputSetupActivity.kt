/*
 * Copyright (C) 2026 The FlorisBoard Contributors
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

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.apptheme.FlorisAppTheme
import dev.patrickgold.jetpref.datastore.model.collectAsState
import org.florisboard.lib.compose.ProvideLocalizedResources
import org.florisboard.lib.compose.stringRes

enum class VoiceInputSetupReason {
    READY,
    FUTO_NOT_INSTALLED,
    FUTO_NOT_ENABLED,
    FUTO_MIC_PERMISSION_DENIED,
    NO_ENABLED_PROVIDER,
}

class VoiceInputSetupActivity : ComponentActivity() {
    companion object {
        fun launch(context: Context, reason: VoiceInputSetupReason): Boolean {
            val intent = VoiceInputSetupIntentContract.createIntent(context, reason)
            return try {
                context.startActivity(intent)
                true
            } catch (_: ActivityNotFoundException) {
                false
            } catch (_: SecurityException) {
                false
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val reason = VoiceInputSetupIntentContract.reasonFrom(intent)
        if (reason == null) {
            finish()
            return
        }
        setFinishOnTouchOutside(true)
        setContent {
            Content(reason)
        }
    }

    @Composable
    private fun Content(reason: VoiceInputSetupReason) {
        val prefs by FlorisPreferenceStore

        ProvideLocalizedResources(
            resourcesContext = this,
            appName = R.string.app_name,
            forceLayoutDirection = LayoutDirection.Ltr,
        ) {
            val theme by prefs.other.settingsTheme.collectAsState()
            FlorisAppTheme(theme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent,
                ) {
                    VoiceSetupDialog(
                        reason = reason,
                        onOpenKeyboardSettings = ::openKeyboardSettings,
                        onOpenFutoAppSettings = ::openFutoAppSettings,
                        onOpenFdroid = { openUrl(VoiceInputManager.FUTO_FDROID_URL) },
                        onOpenReleases = { openUrl(VoiceInputManager.FUTO_RELEASES_URL) },
                        onDismiss = ::finish,
                    )
                }
            }
        }
    }

    @Composable
    private fun VoiceSetupDialog(
        reason: VoiceInputSetupReason,
        onOpenKeyboardSettings: () -> Unit,
        onOpenFutoAppSettings: () -> Unit,
        onOpenFdroid: () -> Unit,
        onOpenReleases: () -> Unit,
        onDismiss: () -> Unit,
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            shape = MaterialTheme.shapes.large,
            title = { Text(text = stringRes(R.string.voice_input_setup__title)) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = stringRes(reason.messageRes),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledTonalButton(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.small,
                            onClick = if (reason == VoiceInputSetupReason.FUTO_MIC_PERMISSION_DENIED) {
                                onOpenFutoAppSettings
                            } else {
                                onOpenKeyboardSettings
                            },
                        ) {
                            Text(
                                text = stringRes(
                                    if (reason == VoiceInputSetupReason.FUTO_MIC_PERMISSION_DENIED) {
                                        R.string.voice_input_setup__open_futo_permissions
                                    } else {
                                        R.string.voice_input_setup__open_keyboard_settings
                                    },
                                ),
                            )
                        }
                        if (reason == VoiceInputSetupReason.FUTO_MIC_PERMISSION_DENIED) {
                            OutlinedButton(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.small,
                                onClick = onOpenKeyboardSettings,
                            ) {
                                Text(text = stringRes(R.string.voice_input_setup__open_keyboard_settings))
                            }
                        } else if (reason != VoiceInputSetupReason.FUTO_NOT_ENABLED) {
                            OutlinedButton(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.small,
                                onClick = onOpenFdroid,
                            ) {
                                Text(text = stringRes(R.string.voice_input_setup__install_fdroid))
                            }
                            OutlinedButton(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.small,
                                onClick = onOpenReleases,
                            ) {
                                Text(text = stringRes(R.string.voice_input_setup__github_releases))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    modifier = Modifier.padding(end = 8.dp),
                    shape = MaterialTheme.shapes.small,
                    onClick = onDismiss,
                ) {
                    Text(text = stringRes(R.string.voice_input_setup__dismiss))
                }
            },
        )
    }

    private fun openKeyboardSettings() {
        openIntent(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
    }

    private fun openFutoAppSettings() {
        openIntent(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                "package:${VoiceInputManager.FUTO_PACKAGE_NAME}".toUri(),
            ),
        )
    }

    private fun openUrl(url: String) {
        openIntent(Intent(Intent.ACTION_VIEW, url.toUri()))
    }

    private fun openIntent(intent: Intent) {
        try {
            startActivity(intent)
            finish()
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.voice_input_setup__open_failed, Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(this, R.string.voice_input_setup__open_failed, Toast.LENGTH_SHORT).show()
        }
    }
}

private val VoiceInputSetupReason.messageRes: Int
    get() = when (this) {
        VoiceInputSetupReason.READY -> R.string.voice_input_setup__ready_message
        VoiceInputSetupReason.FUTO_NOT_INSTALLED -> R.string.voice_input_setup__not_installed_message
        VoiceInputSetupReason.FUTO_NOT_ENABLED -> R.string.voice_input_setup__not_enabled_message
        VoiceInputSetupReason.FUTO_MIC_PERMISSION_DENIED -> R.string.voice_input_setup__mic_permission_message
        VoiceInputSetupReason.NO_ENABLED_PROVIDER -> R.string.voice_input_setup__no_provider_message
    }

internal object VoiceInputSetupIntentContract {
    private const val ExtraReason = "reason"
    private val AllowedExtraKeys = setOf(ExtraReason)

    fun createIntent(context: Context, reason: VoiceInputSetupReason): Intent {
        return Intent(context, VoiceInputSetupActivity::class.java)
            .putExtra(ExtraReason, reason.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun reasonFrom(intent: Intent): VoiceInputSetupReason? {
        val extras = intent.extras ?: return null
        return reasonFromExtras(
            keys = extras.keySet(),
            reasonName = extras.getString(ExtraReason),
        )
    }

    fun reasonFromExtras(keys: Set<String>, reasonName: String?): VoiceInputSetupReason? {
        if (keys != AllowedExtraKeys) return null
        return VoiceInputSetupReason.entries.firstOrNull { it.name == reasonName }
    }
}

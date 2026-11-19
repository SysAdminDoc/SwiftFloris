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

package dev.patrickgold.florisboard.app.settings.advanced

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import org.florisboard.lib.compose.stringRes

/**
 * Non-saveable passphrase entry used by portable backup export and restore and by encrypted
 * dictionary export and import. The hosting window is secure while the dialog is visible, and
 * callers receive the secret only as a short-lived [CharArray].
 *
 * [errorMessage] is shown under the field after a wrong passphrase, so the caller can ask again
 * with the file still at hand. It's announced, and focus goes back to the field.
 */
@Composable
internal fun BackupPassphraseDialog(
    title: String,
    message: String,
    confirmLabel: String,
    requireConfirmation: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (CharArray) -> Unit,
    errorMessage: String? = null,
) {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        window?.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    // Deliberately not rememberSaveable: secrets must not enter saved state.
    var passphrase by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val mismatch = requireConfirmation &&
        confirmation.isNotEmpty() &&
        passphrase != confirmation
    val canConfirm = passphrase.isNotBlank() &&
        (!requireConfirmation || passphrase == confirmation)
    val passphraseFocus = remember { FocusRequester() }
    if (errorMessage != null) {
        LaunchedEffect(errorMessage) {
            passphraseFocus.requestFocus()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(message)
                OutlinedTextField(
                    // The field carries the specific error, so TalkBack reads it when focus lands
                    // here, not just Material's generic "Invalid input".
                    modifier = Modifier
                        .focusRequester(passphraseFocus)
                        .semantics { if (errorMessage != null) error(errorMessage) },
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = {
                        Text(stringRes(R.string.settings__udm__encrypted_dictionary__passphrase))
                    },
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { error ->
                        {
                            Text(
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                                text = error,
                            )
                        }
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = if (requireConfirmation) ImeAction.Next else ImeAction.Done,
                    ),
                )
                if (requireConfirmation) {
                    OutlinedTextField(
                        value = confirmation,
                        onValueChange = { confirmation = it },
                        label = {
                            Text(
                                stringRes(
                                    R.string.settings__udm__encrypted_dictionary__confirm_passphrase,
                                ),
                            )
                        },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                    )
                    if (mismatch) {
                        Text(
                            text = stringRes(
                                R.string.settings__udm__encrypted_dictionary__passphrase_mismatch,
                            ),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(passphrase.toCharArray()) },
                enabled = canConfirm,
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringRes(R.string.action__cancel))
            }
        },
    )
}

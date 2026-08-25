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

package dev.patrickgold.florisboard.app.settings.dictionary

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import android.app.Activity
import android.view.WindowManager
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.settings.theme.DialogProperty
import dev.patrickgold.florisboard.ime.dictionary.DictionaryImportFormat
import dev.patrickgold.florisboard.ime.dictionary.DictionaryImportException
import dev.patrickgold.florisboard.ime.dictionary.DictionaryImporter
import dev.patrickgold.florisboard.ime.dictionary.DictionaryManager
import dev.patrickgold.florisboard.ime.dictionary.EncryptedDictionaryException
import dev.patrickgold.florisboard.ime.dictionary.EncryptedDictionaryExport
import dev.patrickgold.florisboard.ime.dictionary.FREQUENCY_MAX
import dev.patrickgold.florisboard.ime.dictionary.FREQUENCY_MIN
import dev.patrickgold.florisboard.ime.dictionary.PersonalDictionaryImportBatch
import dev.patrickgold.florisboard.ime.dictionary.PersonalDictionaryImportResult
import dev.patrickgold.florisboard.ime.dictionary.PersonalDictionaryEntry
import dev.patrickgold.florisboard.ime.dictionary.UserDictionaryDatabase
import dev.patrickgold.florisboard.ime.dictionary.UserDictionaryDao
import dev.patrickgold.florisboard.ime.dictionary.UserDictionaryEntry
import dev.patrickgold.florisboard.ime.dictionary.UserDictionaryReadDao
import dev.patrickgold.florisboard.ime.dictionary.UserDictionaryValidation
import dev.patrickgold.florisboard.lib.FlorisLocale
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.compose.Validation
import dev.patrickgold.florisboard.lib.rememberValidationResult
import dev.patrickgold.florisboard.lib.util.launchActivity
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.jetpref.material.ui.JetPrefAlertDialog
import dev.patrickgold.jetpref.material.ui.JetPrefListItem
import dev.patrickgold.jetpref.material.ui.JetPrefTextField
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.florisboard.lib.android.showLongToast
import org.florisboard.lib.android.stringRes
import org.florisboard.lib.compose.FlorisEmptyState
import org.florisboard.lib.compose.FlorisErrorCard
import org.florisboard.lib.compose.FlorisIconButton
import org.florisboard.lib.compose.FlorisInfoCard
import org.florisboard.lib.compose.FlorisProgressCard
import org.florisboard.lib.compose.FlorisSuccessCard
import org.florisboard.lib.compose.defaultFlorisOutlinedBox
import org.florisboard.lib.compose.rippleClickable
import org.florisboard.lib.compose.stringRes
import dev.patrickgold.florisboard.lib.util.summarizeForUser

private val AllLanguagesLocale = FlorisLocale.from(language = "zz")
private val UserDictionaryEntryToAdd = UserDictionaryEntry(id = 0, "", 255, null, null)
private const val UserDictionaryMediaType = "text/plain"
private const val EncryptedUserDictionaryMediaType = "application/octet-stream"
private const val EncryptedUserDictionaryDefaultFileName = "my-personal-dictionary.sfexp"
private const val SystemUserDictionaryUiIntentAction = "android.settings.USER_DICTIONARY_SETTINGS"

private data class ParsedDictionaryImport(
    val entries: List<PersonalDictionaryEntry>,
    val format: DictionaryImportFormat?,
)

private sealed interface DictionaryImportFlowResult {
    data class Preview(val import: ParsedDictionaryImport) : DictionaryImportFlowResult
    data class Applied(val result: PersonalDictionaryImportResult?) : DictionaryImportFlowResult
}

enum class UserDictionaryType(val id: String) {
    FLORIS("floris"),
    SYSTEM("system");
}

enum class UserDictionaryScreenAction(val id: String) {
    IMPORT("import"),
    EXPORT_ENCRYPTED("export-encrypted");
}

@Composable
fun UserDictionaryScreen(
    type: UserDictionaryType,
    action: UserDictionaryScreenAction? = null,
) = FlorisScreen {
    title = stringRes(when (type) {
        UserDictionaryType.FLORIS -> R.string.settings__udm__title_floris
        UserDictionaryType.SYSTEM -> R.string.settings__udm__title_system
    })
    previewFieldVisible = false
    scrollable = false

    val navController = LocalNavController.current
    val context = LocalContext.current
    val dictionaryManager = DictionaryManager.default()
    val prefs by FlorisPreferenceStore
    val scope = rememberCoroutineScope()
    val previewPersonalDictionaryImports by prefs.dictionary.previewPersonalDictionaryImports.collectAsState()

    var currentLocale by remember { mutableStateOf<FlorisLocale?>(null) }
    var languageList by remember { mutableStateOf(emptyList<FlorisLocale>()) }
    var wordList by remember { mutableStateOf(emptyList<UserDictionaryEntry>()) }
    var userDictionaryEntryForDialog by remember { mutableStateOf<UserDictionaryEntry?>(null) }
    // docs/archive/SWIFTKEY_PARITY_ROADMAP_2026-05-17 §A2 — post-import summary
    // confirmation surface. Holds the result of the most recent
    // successful modular DictionaryImporter run so the user can
    // see what landed and optionally roll the inserts back.
    var importSummary by remember { mutableStateOf<PersonalDictionaryImportResult?>(null) }
    var pendingImportPreview by remember { mutableStateOf<PersonalDictionaryImportPreview?>(null) }
    var encryptedExportDialogVisible by rememberSaveable { mutableStateOf(false) }
    var encryptedImportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingEncryptedExportPassphrase by remember { mutableStateOf<CharArray?>(null) }
    var activeEntryOperation by rememberSaveable { mutableStateOf<UserDictionaryEntryOperation?>(null) }
    var activeDictionaryTransfer by rememberSaveable { mutableStateOf<UserDictionaryTransferOperation?>(null) }
    var lastEntryNotice by rememberSaveable { mutableStateOf<UserDictionaryEntryNotice?>(null) }
    var lastEntryNoticeDetail by rememberSaveable { mutableStateOf<String?>(null) }
    val isEntryOperationInProgress = activeEntryOperation != null
    val isDictionaryTransferInProgress = activeDictionaryTransfer != null
    val entryActionsEnabled = UserDictionaryEntryPolicy.canMutateDictionary(type) &&
        UserDictionaryEntryPolicy.canMutateEntry(
            isOperationInProgress = isEntryOperationInProgress,
            isTransferInProgress = isDictionaryTransferInProgress,
        )
    val canLeaveDictionaryScreen = UserDictionaryEntryPolicy.canLeave(
        isOperationInProgress = isEntryOperationInProgress,
        isTransferInProgress = isDictionaryTransferInProgress,
    )
    val dictionaryStoreUnavailableMessage = stringRes(R.string.settings__udm__dictionary_store_unavailable)
    val unknownEntryErrorMessage = stringRes(R.string.settings__udm__entry_error_details_unavailable)

    fun startEntryOperation(operation: UserDictionaryEntryOperation) {
        activeEntryOperation = operation
        lastEntryNotice = null
        lastEntryNoticeDetail = null
    }

    fun finishEntryOperation(notice: UserDictionaryEntryNotice, detail: String? = null) {
        activeEntryOperation = null
        lastEntryNotice = notice
        lastEntryNoticeDetail = detail
    }

    fun canStartDictionaryTransfer(): Boolean {
        return UserDictionaryEntryPolicy.canStartTransfer(
            isOperationInProgress = isEntryOperationInProgress,
            isTransferInProgress = isDictionaryTransferInProgress,
        )
    }

    fun canStartDictionaryMutation(): Boolean {
        return UserDictionaryEntryPolicy.canMutateDictionary(type) && canStartDictionaryTransfer()
    }

    fun startDictionaryTransfer(operation: UserDictionaryTransferOperation) {
        activeDictionaryTransfer = operation
        lastEntryNotice = null
        lastEntryNoticeDetail = null
    }

    fun finishDictionaryTransfer() {
        activeDictionaryTransfer = null
    }

    fun showBlockedBackFeedback() {
        val messageId = when (UserDictionaryEntryPolicy.resolveBlockedBackNotice(
            activeEntryOperation = activeEntryOperation,
            activeTransferOperation = activeDictionaryTransfer,
        )) {
            UserDictionaryBlockedBackNotice.None -> return
            UserDictionaryBlockedBackNotice.Saving -> R.string.settings__udm__blocked_back_saving
            UserDictionaryBlockedBackNotice.Deleting -> R.string.settings__udm__blocked_back_deleting
            UserDictionaryBlockedBackNotice.Importing -> R.string.settings__udm__blocked_back_importing
            UserDictionaryBlockedBackNotice.Exporting -> R.string.settings__udm__blocked_back_exporting
        }
        scope.launch {
            context.showLongToast(messageId)
        }
    }

    fun userDictionaryDao(): UserDictionaryReadDao? {
        return when (type) {
            UserDictionaryType.FLORIS -> dictionaryManager.florisUserDictionaryDao()
            UserDictionaryType.SYSTEM -> dictionaryManager.systemUserDictionaryDao()
        }
    }

    fun mutableUserDictionaryDao(): UserDictionaryDao? {
        return when (type) {
            UserDictionaryType.FLORIS -> dictionaryManager.florisUserDictionaryDao()
            UserDictionaryType.SYSTEM -> null
        }
    }

    fun getDisplayNameForLocale(locale: FlorisLocale): String {
        return if (locale == AllLanguagesLocale) {
            context.stringRes(R.string.settings__udm__all_languages)
        } else {
            locale.displayName()
        }
    }

    data class UserDictionaryUiSnapshot(
        val currentLocale: FlorisLocale?,
        val languageList: List<FlorisLocale>,
        val wordList: List<UserDictionaryEntry>,
    )

    fun loadUiSnapshot(selectedLocale: FlorisLocale?): UserDictionaryUiSnapshot {
        val dao = userDictionaryDao()
        if (selectedLocale != null) {
            // Stay on the selected locale even when it holds no words, so the
            // per-locale empty state can render with its add action. Falling
            // through to the language list here bounced the user out of the
            // language they were editing the moment they deleted its last word,
            // with no explanation, and left that empty state unreachable.
            val locale = if (selectedLocale == AllLanguagesLocale) null else selectedLocale
            return UserDictionaryUiSnapshot(
                currentLocale = selectedLocale,
                languageList = emptyList(),
                wordList = dao?.queryAll(locale).orEmpty(),
            )
        }
        return UserDictionaryUiSnapshot(
            currentLocale = null,
            languageList = dao
                ?.queryLanguageList()
                ?.sortedBy { it?.displayLanguage() }
                ?.map { it ?: AllLanguagesLocale }
                ?: emptyList(),
            wordList = emptyList(),
        )
    }

    fun buildUi() {
        val selectedLocale = currentLocale
        scope.launch {
            val snapshot = withContext(Dispatchers.IO) {
                loadUiSnapshot(selectedLocale)
            }
            currentLocale = snapshot.currentLocale
            languageList = snapshot.languageList
            wordList = snapshot.wordList
        }
    }

    fun userDictionaryDatabase(): UserDictionaryDatabase? {
        return when (type) {
            UserDictionaryType.FLORIS -> dictionaryManager.florisUserDictionaryDatabase()
            UserDictionaryType.SYSTEM -> dictionaryManager.systemUserDictionaryDatabase()
        }
    }

    fun mutableUserDictionaryDatabase(): UserDictionaryDatabase? {
        return if (UserDictionaryEntryPolicy.canMutateDictionary(type)) {
            dictionaryManager.florisUserDictionaryDatabase()
        } else {
            null
        }
    }

    fun encryptedDictionaryErrorMessage(error: Throwable): String {
        return when ((error as? EncryptedDictionaryException)?.reason) {
            EncryptedDictionaryExport.FailureReason.BAD_PASSPHRASE ->
                context.stringRes(R.string.settings__udm__encrypted_dictionary__bad_passphrase)
            EncryptedDictionaryExport.FailureReason.UNSUPPORTED_VERSION ->
                context.stringRes(R.string.settings__udm__encrypted_dictionary__unsupported_version)
            EncryptedDictionaryExport.FailureReason.OVERSIZED ->
                context.stringRes(R.string.settings__udm__encrypted_dictionary__oversized)
            EncryptedDictionaryExport.FailureReason.CORRUPT_HEADER,
            EncryptedDictionaryExport.FailureReason.NOT_AN_ENVELOPE,
            EncryptedDictionaryExport.FailureReason.TRUNCATED ->
                context.stringRes(R.string.settings__udm__encrypted_dictionary__corrupt)
            null -> error.summarizeForUser(unknownEntryErrorMessage)
        }
    }

    fun handleImportSuccess(result: PersonalDictionaryImportResult?) {
        buildUi()
        if (result != null) {
            importSummary = result
        } else {
            scope.launch {
                context.showLongToast(R.string.settings__udm__dictionary_import_success)
            }
        }
    }

    fun handleImportFailure(error: Throwable) {
        scope.launch {
            context.showLongToast(
                R.string.settings__udm__dictionary_import_failure,
                "error_message" to encryptedDictionaryErrorMessage(error),
            )
        }
    }

    fun detectEncryptedEnvelope(uri: Uri): Boolean {
        return context.contentResolver.openInputStream(uri)?.use { stream ->
            val sniffBuffer = ByteArray(EncryptedDictionaryExport.MAGIC.size)
            val sniffed = stream.read(sniffBuffer).coerceAtLeast(0)
            EncryptedDictionaryExport.isEncryptedEnvelope(sniffBuffer.copyOf(sniffed))
        } ?: false
    }

    fun readEncryptedEnvelopeBytes(uri: Uri): ByteArray {
        val limit = EncryptedDictionaryExport.HEADER_SIZE +
            EncryptedDictionaryExport.MAX_PAYLOAD_BYTES +
            (EncryptedDictionaryExport.GCM_TAG_BITS / 8)
        val input = context.contentResolver.openInputStream(uri)
            ?: throw DictionaryImportException("Could not read selected file.")
        input.use { stream ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                total += read
                if (total > limit) {
                    throw EncryptedDictionaryException(EncryptedDictionaryExport.FailureReason.OVERSIZED)
                }
                out.write(buffer, 0, read)
            }
            return out.toByteArray()
        }
    }

    fun shouldPreviewImport(entries: List<PersonalDictionaryEntry>): Boolean {
        return previewPersonalDictionaryImports && entries.isNotEmpty()
    }

    fun parsePlainDictionaryImport(uri: Uri): ParsedDictionaryImport {
        val importer = DictionaryImporter()
        val parsed = context.contentResolver.openInputStream(uri)?.use { stream ->
            importer.import(stream)
        } ?: throw DictionaryImportException("Could not read selected file.")
        val format = context.contentResolver.openInputStream(uri)?.use { sniffStream ->
            val sniffBuffer = ByteArray(1024)
            val sniffed = sniffStream.read(sniffBuffer).coerceAtLeast(0)
            importer.detectFormat(sniffBuffer.copyOf(sniffed))
        }
        return ParsedDictionaryImport(parsed, format)
    }

    fun applyParsedDictionaryImport(
        parsed: ParsedDictionaryImport,
        excludedEntryIndexes: Set<Int> = emptySet(),
    ) {
        if (!canStartDictionaryMutation()) {
            return
        }
        val dao = mutableUserDictionaryDao()
        if (dao == null) {
            scope.launch {
                context.showLongToast(R.string.settings__udm__dictionary_store_unavailable)
            }
            return
        }
        scope.launch {
            startDictionaryTransfer(UserDictionaryTransferOperation.Importing)
            try {
                runCatching {
                    withContext(Dispatchers.IO) {
                        PersonalDictionaryImportBatch.import(
                            parsedEntries = parsed.entries,
                            dao = dao,
                            format = parsed.format,
                            excludedEntryIndexes = excludedEntryIndexes,
                        )
                    }
                }.onSuccess { result ->
                    handleImportSuccess(result)
                }.onFailure { error ->
                    handleImportFailure(error)
                }
            } finally {
                finishDictionaryTransfer()
            }
        }
    }

    fun importPlainDictionary(uri: Uri) {
        if (!canStartDictionaryMutation()) {
            return
        }
        val db = mutableUserDictionaryDatabase()
        if (db == null) {
            scope.launch {
                context.showLongToast(R.string.settings__udm__dictionary_store_unavailable)
            }
            return
        }
        val dao = mutableUserDictionaryDao()
        if (dao == null) {
            scope.launch {
                context.showLongToast(R.string.settings__udm__dictionary_store_unavailable)
            }
            return
        }
        scope.launch {
            startDictionaryTransfer(UserDictionaryTransferOperation.Importing)
            try {
                // docs/archive/SWIFTKEY_PARITY_ROADMAP_2026-05-17 §A2/A3 — try the modular
                // importer first (SwiftKey JSON / Gboard XML / CSV / zip /
                // SwiftFloris combined-list detection by byte sniff). On any
                // DictionaryImportException, fall through to the legacy URI-based
                // combined-list path so older FlorisBoard backups keep importing.
                runCatching {
                    withContext(Dispatchers.IO) {
                        runCatching {
                            val parsed = parsePlainDictionaryImport(uri)
                            if (shouldPreviewImport(parsed.entries)) {
                                DictionaryImportFlowResult.Preview(parsed)
                            } else {
                                DictionaryImportFlowResult.Applied(
                                    PersonalDictionaryImportBatch.import(
                                        parsedEntries = parsed.entries,
                                        dao = dao,
                                        format = parsed.format,
                                    )
                                )
                            }
                        }.recoverCatching { modularError ->
                            if (modularError !is DictionaryImportException) throw modularError
                            if (modularError.isSafetyLimit) throw modularError
                            db.importCombinedList(context, uri)
                            DictionaryImportFlowResult.Applied(null)
                        }.getOrThrow()
                    }
                }.onSuccess { result ->
                    when (result) {
                        is DictionaryImportFlowResult.Preview -> {
                            pendingImportPreview = PersonalDictionaryImportPreview(
                                entries = result.import.entries,
                                format = result.import.format,
                            )
                        }
                        is DictionaryImportFlowResult.Applied -> handleImportSuccess(result.result)
                    }
                }.onFailure { error ->
                    handleImportFailure(error)
                }
            } finally {
                finishDictionaryTransfer()
            }
        }
    }

    fun importEncryptedDictionary(uri: Uri, passphrase: CharArray) {
        if (!canStartDictionaryMutation()) {
            passphrase.fill('\u0000')
            return
        }
        val dao = mutableUserDictionaryDao()
        if (dao == null) {
            passphrase.fill('\u0000')
            scope.launch {
                context.showLongToast(R.string.settings__udm__dictionary_store_unavailable)
            }
            return
        }
        scope.launch {
            startDictionaryTransfer(UserDictionaryTransferOperation.Importing)
            try {
                runCatching {
                    withContext(Dispatchers.IO) {
                        val envelope = readEncryptedEnvelopeBytes(uri)
                        val plaintext = EncryptedDictionaryExport.decrypt(envelope, passphrase)
                        // Hoisted so it can be scrubbed in finally: this is a 1 KiB copy
                        // of the decrypted personal-dictionary plaintext; leaving it
                        // un-wiped on the heap defeats the deliberate plaintext.fill(0)
                        // below and leaks a slice of the user's vocabulary.
                        var sniff: ByteArray? = null
                        try {
                            val importer = DictionaryImporter()
                            val parsed = importer.import(ByteArrayInputStream(plaintext))
                            sniff = plaintext.copyOf(minOf(plaintext.size, 1024))
                            val format = importer.detectFormat(sniff)
                            if (shouldPreviewImport(parsed)) {
                                DictionaryImportFlowResult.Preview(ParsedDictionaryImport(parsed, format))
                            } else {
                                DictionaryImportFlowResult.Applied(
                                    PersonalDictionaryImportBatch.import(
                                        parsedEntries = parsed,
                                        dao = dao,
                                        format = format,
                                    )
                                )
                            }
                        } finally {
                            plaintext.fill(0)
                            sniff?.fill(0)
                        }
                    }
                }.onSuccess { result ->
                    when (result) {
                        is DictionaryImportFlowResult.Preview -> {
                            pendingImportPreview = PersonalDictionaryImportPreview(
                                entries = result.import.entries,
                                format = result.import.format,
                            )
                        }
                        is DictionaryImportFlowResult.Applied -> handleImportSuccess(result.result)
                    }
                }.onFailure { error ->
                    handleImportFailure(error)
                }
            } finally {
                finishDictionaryTransfer()
                passphrase.fill('\u0000')
            }
        }
    }

    val importDictionary = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            // If uri is null it indicates that the selection activity was cancelled (mostly
            // by pressing the back button), so we don't display an error message here.
            if (uri == null) return@rememberLauncherForActivityResult
            if (!canStartDictionaryMutation()) return@rememberLauncherForActivityResult
            val isEncrypted = runCatching {
                detectEncryptedEnvelope(uri)
            }.getOrElse { error ->
                handleImportFailure(error)
                return@rememberLauncherForActivityResult
            }
            if (isEncrypted) {
                encryptedImportUri = uri
            } else {
                importPlainDictionary(uri)
            }
        },
    )

    var importActionConsumed by rememberSaveable(action?.id) { mutableStateOf(false) }
    LaunchedEffect(action, entryActionsEnabled) {
        if (!entryActionsEnabled || importActionConsumed) return@LaunchedEffect
        when (action) {
            UserDictionaryScreenAction.IMPORT -> {
                importActionConsumed = true
                importDictionary.launch("*/*")
            }
            UserDictionaryScreenAction.EXPORT_ENCRYPTED -> {
                importActionConsumed = true
                encryptedExportDialogVisible = true
            }
            null -> Unit
        }
    }

    val exportDictionary = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(UserDictionaryMediaType),
        onResult = { uri ->
            // If uri is null it indicates that the selection activity was cancelled (mostly
            // by pressing the back button), so we don't display an error message here.
            if (uri == null) return@rememberLauncherForActivityResult
            if (!canStartDictionaryTransfer()) return@rememberLauncherForActivityResult
            val db = when (type) {
                UserDictionaryType.FLORIS -> dictionaryManager.florisUserDictionaryDatabase()
                UserDictionaryType.SYSTEM -> dictionaryManager.systemUserDictionaryDatabase()
            }
            if (db == null) {
                scope.launch {
                    context.showLongToast(R.string.settings__udm__dictionary_store_unavailable)
                }
                return@rememberLauncherForActivityResult
            }
            scope.launch {
                startDictionaryTransfer(UserDictionaryTransferOperation.Exporting)
                try {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            db.exportCombinedList(context, uri)
                        }
                    }.onSuccess {
                        context.showLongToast(R.string.settings__udm__dictionary_export_success)
                    }.onFailure { error ->
                        val errorMessage = error.summarizeForUser(unknownEntryErrorMessage)
                        context.showLongToast(
                            R.string.settings__udm__dictionary_export_failure,
                            "error_message" to errorMessage,
                        )
                    }
                } finally {
                    finishDictionaryTransfer()
                }
            }
        },
    )

    val exportEncryptedDictionary = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(EncryptedUserDictionaryMediaType),
        onResult = { uri ->
            val passphrase = pendingEncryptedExportPassphrase
            pendingEncryptedExportPassphrase = null
            if (uri == null) {
                passphrase?.fill('\u0000')
                return@rememberLauncherForActivityResult
            }
            if (passphrase == null) {
                encryptedExportDialogVisible = true
                scope.launch {
                    context.showLongToast(R.string.settings__udm__encrypted_dictionary_export_passphrase_lost)
                }
                return@rememberLauncherForActivityResult
            }
            if (!canStartDictionaryTransfer()) {
                passphrase.fill('\u0000')
                return@rememberLauncherForActivityResult
            }
            val db = userDictionaryDatabase()
            if (db == null) {
                passphrase.fill('\u0000')
                scope.launch {
                    context.showLongToast(R.string.settings__udm__dictionary_store_unavailable)
                }
                return@rememberLauncherForActivityResult
            }
            scope.launch {
                startDictionaryTransfer(UserDictionaryTransferOperation.Exporting)
                try {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            db.exportEncryptedCombinedList(context, uri, passphrase)
                        }
                    }.onSuccess {
                        context.showLongToast(R.string.settings__udm__encrypted_dictionary_export_success)
                    }.onFailure { error ->
                        context.showLongToast(
                            R.string.settings__udm__dictionary_export_failure,
                            "error_message" to encryptedDictionaryErrorMessage(error),
                        )
                    }
                } finally {
                    finishDictionaryTransfer()
                    passphrase.fill('\u0000')
                }
            }
        },
    )

    navigationIcon {
        FlorisIconButton(
            onClick = {
                if (!canLeaveDictionaryScreen) {
                    return@FlorisIconButton
                } else if (currentLocale != null) {
                    currentLocale = null
                    buildUi()
                } else {
                    navController.popBackStack()
                }
            },
            icon = if (currentLocale != null) {
                Icons.Default.Close
            } else {
                Icons.AutoMirrored.Filled.ArrowBack
            },
            contentDescription = stringRes(
                if (currentLocale != null) {
                    R.string.action__close
                } else {
                    R.string.action__back
                },
            ),
            enabled = canLeaveDictionaryScreen,
        )
    }

    actions {
        var expanded by remember { mutableStateOf(false) }
        FlorisIconButton(
            onClick = { expanded = !expanded },
            icon = Icons.Default.MoreVert,
            contentDescription = stringRes(R.string.action__more_options),
            enabled = canStartDictionaryTransfer(),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                onClick = {
                    importDictionary.launch("*/*")
                    expanded = false
                },
                text = { Text(text = stringRes(R.string.action__import)) },
                enabled = entryActionsEnabled,
            )
            DropdownMenuItem(
                onClick = {
                    exportDictionary.launch("my-personal-dictionary.clb")
                    expanded = false
                },
                text = { Text(text = stringRes(R.string.action__export)) },
                enabled = canStartDictionaryTransfer(),
            )
            DropdownMenuItem(
                onClick = {
                    encryptedExportDialogVisible = true
                    expanded = false
                },
                text = { Text(text = stringRes(R.string.settings__udm__encrypted_export)) },
                enabled = canStartDictionaryTransfer(),
            )
            if (type == UserDictionaryType.SYSTEM) {
                DropdownMenuItem(
                    onClick = {
                        context.launchActivity { it.action = SystemUserDictionaryUiIntentAction }
                        expanded = false
                    },
                    text = { Text(text = stringRes(R.string.settings__udm__open_system_manager_ui)) },
                    enabled = canStartDictionaryTransfer(),
                )
            }
        }
    }

    floatingActionButton {
        if (entryActionsEnabled) {
            ExtendedFloatingActionButton(
                onClick = { userDictionaryEntryForDialog = UserDictionaryEntryToAdd },
                shape = MaterialTheme.shapes.medium,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringRes(R.string.settings__udm__dialog__title_add),
                    )
                },
                text = { Text(text = stringRes(R.string.settings__udm__dialog__title_add)) },
            )
        }
    }

    content {
        BackHandler(currentLocale != null || isEntryOperationInProgress || isDictionaryTransferInProgress) {
            if (canLeaveDictionaryScreen && currentLocale != null) {
                currentLocale = null
                buildUi()
            } else if (!canLeaveDictionaryScreen) {
                showBlockedBackFeedback()
            }
        }

        LaunchedEffect(Unit) {
            dictionaryManager.loadUserDictionariesIfNecessary()
            buildUi()
        }

        when (UserDictionaryEntryPolicy.resolveTransferNotice(activeDictionaryTransfer)) {
            UserDictionaryTransferNotice.None -> Unit
            UserDictionaryTransferNotice.Importing -> FlorisProgressCard(
                modifier = Modifier.defaultFlorisOutlinedBox(),
                text = stringRes(R.string.settings__udm__dictionary_import_in_progress),
                secondaryText = stringRes(R.string.settings__udm__dictionary_import_in_progress_summary),
            )
            UserDictionaryTransferNotice.Exporting -> FlorisProgressCard(
                modifier = Modifier.defaultFlorisOutlinedBox(),
                text = stringRes(R.string.settings__udm__dictionary_export_in_progress),
                secondaryText = stringRes(R.string.settings__udm__dictionary_export_in_progress_summary),
            )
        }

        if (type == UserDictionaryType.SYSTEM) {
            FlorisInfoCard(
                modifier = Modifier.padding(8.dp),
                text = stringRes(R.string.settings__udm__system_read_only_title),
                secondaryText = stringRes(R.string.settings__udm__system_read_only_summary),
            )
        }

        when (UserDictionaryEntryPolicy.resolveNotice(activeEntryOperation, lastEntryNotice)) {
            UserDictionaryEntryNotice.None -> Unit
            UserDictionaryEntryNotice.Saving -> FlorisProgressCard(
                modifier = Modifier.defaultFlorisOutlinedBox(),
                text = stringRes(R.string.settings__udm__entry_save_in_progress),
                secondaryText = stringRes(R.string.settings__udm__entry_save_in_progress_summary),
            )
            UserDictionaryEntryNotice.Deleting -> FlorisProgressCard(
                modifier = Modifier.defaultFlorisOutlinedBox(),
                text = stringRes(R.string.settings__udm__entry_delete_in_progress),
                secondaryText = stringRes(R.string.settings__udm__entry_delete_in_progress_summary),
            )
            UserDictionaryEntryNotice.SaveSuccess -> FlorisSuccessCard(
                modifier = Modifier.defaultFlorisOutlinedBox(),
                text = stringRes(R.string.settings__udm__entry_save_success),
                secondaryText = stringRes(R.string.settings__udm__entry_save_success_summary),
            )
            UserDictionaryEntryNotice.DeleteSuccess -> FlorisSuccessCard(
                modifier = Modifier.defaultFlorisOutlinedBox(),
                text = stringRes(R.string.settings__udm__entry_delete_success),
                secondaryText = stringRes(R.string.settings__udm__entry_delete_success_summary),
            )
            UserDictionaryEntryNotice.SaveFailure -> FlorisErrorCard(
                modifier = Modifier.defaultFlorisOutlinedBox(),
                text = stringRes(R.string.settings__udm__entry_save_failure),
                secondaryText = stringRes(
                    R.string.settings__udm__entry_save_failure_summary,
                    "error_message" to (lastEntryNoticeDetail ?: unknownEntryErrorMessage),
                ),
            )
            UserDictionaryEntryNotice.DeleteFailure -> FlorisErrorCard(
                modifier = Modifier.defaultFlorisOutlinedBox(),
                text = stringRes(R.string.settings__udm__entry_delete_failure),
                secondaryText = stringRes(
                    R.string.settings__udm__entry_delete_failure_summary,
                    "error_message" to (lastEntryNoticeDetail ?: unknownEntryErrorMessage),
                ),
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            if (languageList.isEmpty()) {
                item {
                    // A locale is selected once currentLocale is non-null, so
                    // the empty state can name the language the user is in
                    // rather than implying the whole dictionary is empty.
                    val emptyLocale = currentLocale?.takeIf { type != UserDictionaryType.SYSTEM }
                    FlorisEmptyState(
                        modifier = Modifier.padding(16.dp),
                        icon = Icons.AutoMirrored.Filled.LibraryBooks,
                        title = stringRes(
                            if (emptyLocale != null) {
                                R.string.settings__udm__empty_locale_title
                            } else {
                                R.string.settings__udm__empty_title
                            },
                        ),
                        message = when {
                            emptyLocale != null -> stringRes(
                                R.string.settings__udm__empty_locale_message,
                                "language" to getDisplayNameForLocale(emptyLocale),
                            )
                            type == UserDictionaryType.SYSTEM ->
                                stringRes(R.string.settings__udm__system_no_words)
                            else -> stringRes(R.string.settings__udm__no_words_in_dictionary)
                        },
                        actionLabel = if (entryActionsEnabled) {
                            stringRes(R.string.settings__udm__dialog__title_add)
                        } else {
                            null
                        },
                        onAction = if (entryActionsEnabled) {
                            { userDictionaryEntryForDialog = UserDictionaryEntryToAdd }
                        } else {
                            null
                        },
                    )
                }
            }
            if (currentLocale == null) {
                items(languageList) { language ->
                    JetPrefListItem(
                        modifier = Modifier.rippleClickable {
                            if (canLeaveDictionaryScreen) {
                                scope.launch {
                                    // Delay makes UI ripple visible and experience better
                                    delay(150)
                                    currentLocale = language
                                    buildUi()
                                }
                            }
                        },
                        text = getDisplayNameForLocale(language),
                    )
                }
            } else {
                if (wordList.isEmpty()) {
                    item {
                        FlorisEmptyState(
                            modifier = Modifier.padding(16.dp),
                            icon = Icons.AutoMirrored.Filled.LibraryBooks,
                            title = stringRes(R.string.settings__udm__empty_locale_title),
                            message = stringRes(
                                if (type == UserDictionaryType.SYSTEM) {
                                    R.string.settings__udm__system_no_words_locale
                                } else {
                                    R.string.settings__udm__empty_locale_message
                                },
                                "language" to getDisplayNameForLocale(currentLocale!!),
                            ),
                            actionLabel = if (entryActionsEnabled) {
                                stringRes(R.string.settings__udm__dialog__title_add)
                            } else {
                                null
                            },
                            onAction = if (entryActionsEnabled) {
                                { userDictionaryEntryForDialog = UserDictionaryEntryToAdd }
                            } else {
                                null
                            },
                        )
                    }
                }
                items(wordList) { wordEntry ->
                    JetPrefListItem(
                        modifier = Modifier.rippleClickable {
                            if (entryActionsEnabled) {
                                userDictionaryEntryForDialog = wordEntry
                            }
                        },
                        text = wordEntry.word,
                        secondaryText = stringRes(
                            if (wordEntry.shortcut != null) {
                                R.string.settings__udm__word_summary_freq_shortcut
                            } else {
                                R.string.settings__udm__word_summary_freq
                            },
                            "freq" to wordEntry.freq,
                            "shortcut" to wordEntry.shortcut,
                        ),
                    )
                }
            }
        }

        val wordEntry = userDictionaryEntryForDialog
        if (wordEntry != null) {
            var showValidationErrors by rememberSaveable { mutableStateOf(false) }
            val isAddWord = wordEntry === UserDictionaryEntryToAdd
            var word by rememberSaveable { mutableStateOf(wordEntry.word) }
            val wordValidation = rememberValidationResult(UserDictionaryValidation.Word, word)
            var freq by rememberSaveable { mutableStateOf(wordEntry.freq.toString()) }
            val freqValidation = rememberValidationResult(UserDictionaryValidation.Freq, freq)
            var shortcut by rememberSaveable { mutableStateOf(wordEntry.shortcut ?: "") }
            val shortcutValidation = rememberValidationResult(UserDictionaryValidation.Shortcut, shortcut)
            var locale by rememberSaveable { mutableStateOf(wordEntry.locale ?: "") }
            val localeValidation = rememberValidationResult(UserDictionaryValidation.Locale, locale)

            JetPrefAlertDialog(
                title = stringRes(if (isAddWord) {
                    R.string.settings__udm__dialog__title_add
                } else {
                    R.string.settings__udm__dialog__title_edit
                }),
                confirmLabel = stringRes(if (isAddWord) {
                    R.string.action__add
                } else {
                    R.string.action__apply
                }),
                onConfirm = {
                    if (!entryActionsEnabled) {
                        return@JetPrefAlertDialog
                    }
                    val isInvalid = wordValidation.isInvalid() ||
                        freqValidation.isInvalid() ||
                        shortcutValidation.isInvalid() ||
                        localeValidation.isInvalid()
                    if (isInvalid) {
                        showValidationErrors = true
                    } else {
                        val entry = UserDictionaryEntry(
                            id = wordEntry.id,
                            word = word.trim(),
                            freq = freq.toInt(10),
                            shortcut = shortcut.trim().takeIf { it.isNotBlank() },
                            locale = locale.trim().takeIf { it.isNotBlank() }?.let {
                                // Normalize tag
                                FlorisLocale.fromTag(it).localeTag()
                            },
                        )
                        val localeTagsToRebuild = setOf(wordEntry.locale, entry.locale).filterNotNull()
                        userDictionaryEntryForDialog = null
                        startEntryOperation(UserDictionaryEntryOperation.Saving)
                        scope.launch {
                            val saved = runCatching {
                                withContext(Dispatchers.IO) {
                                    val dao = mutableUserDictionaryDao() ?: error(dictionaryStoreUnavailableMessage)
                                    if (isAddWord) {
                                        dao.insert(entry)
                                    } else {
                                        dao.update(entry)
                                    }
                                }
                                // ROADMAP §7 Next-3 — keep the in-memory
                                // overlay in sync with manual DAO edits so the
                                // IME's suggest() path picks up the change on
                                // the next keystroke.
                                localeTagsToRebuild.forEach { tag ->
                                    dictionaryManager.rebuildOverlay(FlorisLocale.fromTag(tag))
                                }
                            }
                            saved.onSuccess {
                                finishEntryOperation(UserDictionaryEntryPolicy.saveResult(saved = true))
                                buildUi()
                            }.onFailure { error ->
                                finishEntryOperation(
                                    UserDictionaryEntryPolicy.saveResult(saved = false),
                                    error.summarizeForUser(unknownEntryErrorMessage),
                                )
                                buildUi()
                            }
                        }
                    }
                },
                dismissLabel = stringRes(R.string.action__cancel),
                onDismiss = {
                    userDictionaryEntryForDialog = null
                },
                neutralLabel = if (isAddWord) {
                    null
                } else {
                    stringRes(R.string.action__delete)
                },
                onNeutral = {
                    if (!entryActionsEnabled) {
                        return@JetPrefAlertDialog
                    }
                    val localeTagsToRebuild = setOf(wordEntry.locale).filterNotNull()
                    userDictionaryEntryForDialog = null
                    startEntryOperation(UserDictionaryEntryOperation.Deleting)
                    scope.launch {
                        val deleted = runCatching {
                            withContext(Dispatchers.IO) {
                                val dao = mutableUserDictionaryDao() ?: error(dictionaryStoreUnavailableMessage)
                                dao.delete(wordEntry)
                            }
                            localeTagsToRebuild.forEach { tag ->
                                dictionaryManager.rebuildOverlay(FlorisLocale.fromTag(tag))
                            }
                        }
                        deleted.onSuccess {
                            finishEntryOperation(UserDictionaryEntryPolicy.deleteResult(deleted = true))
                            buildUi()
                        }.onFailure { error ->
                            finishEntryOperation(
                                UserDictionaryEntryPolicy.deleteResult(deleted = false),
                                error.summarizeForUser(unknownEntryErrorMessage),
                            )
                            buildUi()
                        }
                    }
                },
            ) {
                Column {
                    DialogProperty(text = stringRes(R.string.settings__udm__dialog__word_label)) {
                        JetPrefTextField(
                            value = word,
                            onValueChange = { word = it },
                        )
                        Validation(showValidationErrors, wordValidation)
                    }
                    DialogProperty(text = stringRes(
                        R.string.settings__udm__dialog__freq_label,
                        "f_min" to FREQUENCY_MIN, "f_max" to FREQUENCY_MAX,
                    )) {
                        JetPrefTextField(
                            value = freq,
                            onValueChange = { freq = it },
                        )
                        Validation(showValidationErrors, freqValidation)
                    }
                    DialogProperty(text = stringRes(R.string.settings__udm__dialog__shortcut_label)) {
                        JetPrefTextField(
                            value = shortcut,
                            onValueChange = { shortcut = it },
                        )
                        Validation(showValidationErrors, shortcutValidation)
                    }
                    DialogProperty(text = stringRes(R.string.settings__udm__dialog__locale_label)) {
                        JetPrefTextField(
                            value = locale,
                            onValueChange = { locale = it },
                        )
                        Validation(showValidationErrors, localeValidation)
                    }
                }
            }
        }

        val importPreview = pendingImportPreview
        if (importPreview != null) {
            PersonalDictionaryImportPreviewDialog(
                preview = importPreview,
                onImport = { excludedEntryIndexes, skipFuturePreview ->
                    pendingImportPreview = null
                    if (skipFuturePreview) {
                        scope.launch {
                            prefs.dictionary.previewPersonalDictionaryImports.set(false)
                        }
                    }
                    applyParsedDictionaryImport(
                        parsed = ParsedDictionaryImport(
                            entries = importPreview.entries,
                            format = importPreview.format,
                        ),
                        excludedEntryIndexes = excludedEntryIndexes,
                    )
                },
                onDismiss = { pendingImportPreview = null },
            )
        }

        // docs/archive/SWIFTKEY_PARITY_ROADMAP_2026-05-17 §A2/A3 — post-import summary.
        // Renders when the modular DictionaryImporter path returned a
        // result, including decrypted SwiftFloris combined-list exports.
        val summary = importSummary
        if (summary != null) {
            PersonalDictionaryImportSummaryDialog(
                result = summary,
                onKeep = { importSummary = null },
                onRollback = {
                    val deleted = PersonalDictionaryImportBatch.rollback(
                        result = summary,
                        dao = mutableUserDictionaryDao() ?: return@PersonalDictionaryImportSummaryDialog,
                    )
                    importSummary = null
                    buildUi()
                    scope.launch {
                        context.showLongToast(
                            R.string.settings__udm__import_summary__rollback_done,
                            "count" to deleted,
                        )
                    }
                },
            )
        }
    }

    if (encryptedExportDialogVisible) {
        DictionaryPassphraseDialog(
            title = stringRes(R.string.settings__udm__encrypted_export__dialog_title),
            message = stringRes(R.string.settings__udm__encrypted_export__dialog_message),
            confirmLabel = stringRes(R.string.action__export),
            requireConfirmation = true,
            onDismiss = {
                encryptedExportDialogVisible = false
            },
            onConfirm = { passphrase ->
                encryptedExportDialogVisible = false
                pendingEncryptedExportPassphrase?.fill('\u0000')
                pendingEncryptedExportPassphrase = passphrase.toCharArray()
                exportEncryptedDictionary.launch(EncryptedUserDictionaryDefaultFileName)
            },
        )
    }

    encryptedImportUri?.let { uri ->
        DictionaryPassphraseDialog(
            title = stringRes(R.string.settings__udm__encrypted_import__dialog_title),
            message = stringRes(R.string.settings__udm__encrypted_import__dialog_message),
            confirmLabel = stringRes(R.string.action__import),
            requireConfirmation = false,
            onDismiss = {
                encryptedImportUri = null
            },
            onConfirm = { passphrase ->
                encryptedImportUri = null
                importEncryptedDictionary(uri, passphrase.toCharArray())
            },
        )
    }
}

@Composable
private fun DictionaryPassphraseDialog(
    title: String,
    message: String,
    confirmLabel: String,
    requireConfirmation: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    // While this dialog is on-screen, mark the host activity window as
    // FLAG_SECURE so screen recordings / screenshots / external-display
    // mirroring cannot capture the passphrase the user is typing. The
    // PasswordVisualTransformation only masks the rendered dot/bullet —
    // without FLAG_SECURE the typed characters are still in the surface
    // layer that a screen recorder captures. Cleared on dispose.
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
    // Use plain `remember` (not `rememberSaveable`) so the passphrase does
    // NOT survive process death / configuration change via the savedInstance
    // state bundle. A passphrase in a savedInstanceState bundle is
    // recoverable via `am dumpstate` and the like.
    var passphrase by remember { mutableStateOf("") }
    var passphraseConfirmation by remember { mutableStateOf("") }
    val mismatch = requireConfirmation &&
        passphraseConfirmation.isNotEmpty() &&
        passphrase != passphraseConfirmation
    val canConfirm = passphrase.isNotBlank() &&
        (!requireConfirmation || passphrase == passphraseConfirmation)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = message)
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text(text = stringRes(R.string.settings__udm__encrypted_dictionary__passphrase)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = if (requireConfirmation) ImeAction.Next else ImeAction.Done,
                    ),
                )
                if (requireConfirmation) {
                    OutlinedTextField(
                        value = passphraseConfirmation,
                        onValueChange = { passphraseConfirmation = it },
                        label = {
                            Text(text = stringRes(R.string.settings__udm__encrypted_dictionary__confirm_passphrase))
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
                            text = stringRes(R.string.settings__udm__encrypted_dictionary__passphrase_mismatch),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(passphrase) },
                enabled = canConfirm,
            ) {
                Text(text = confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringRes(R.string.action__cancel))
            }
        },
    )
}

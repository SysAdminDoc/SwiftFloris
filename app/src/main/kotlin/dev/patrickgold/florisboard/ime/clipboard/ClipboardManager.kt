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

package dev.patrickgold.florisboard.ime.clipboard

import android.content.ClipData
import android.content.ClipDescription.EXTRA_IS_SENSITIVE
import android.content.Context
import android.os.PersistableBundle
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.appContext
import dev.patrickgold.florisboard.editorInstance
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardHistoryDao
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardHistoryDatabase
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardItem
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardMediaProvider
import dev.patrickgold.florisboard.ime.clipboard.provider.ItemType
import dev.patrickgold.florisboard.lib.devtools.flogError
import java.io.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.florisboard.lib.android.AndroidClipboardManager
import org.florisboard.lib.android.AndroidClipboardManager_OnPrimaryClipChangedListener
import org.florisboard.lib.android.AndroidVersion
import org.florisboard.lib.android.clearPrimaryClipAnyApi
import org.florisboard.lib.android.postShortToast
import org.florisboard.lib.android.setOrClearPrimaryClip
import org.florisboard.lib.android.systemService
import org.florisboard.lib.kotlin.tryOrNull

/**
 * [ClipboardManager] manages the clipboard and clipboard history.
 *
 * Also just going to document how all the classes here work.
 *
 * [ClipboardManager] handles storage and retrieval of clipboard items. All manipulation of the
 * clipboard goes through here.
 */
class ClipboardManager(
    context: Context,
) : AndroidClipboardManager_OnPrimaryClipChangedListener, Closeable {
    companion object {
        // 1 minute
        private const val INTERVAL = 60 * 1000L

        /**
         * Taken from ClipboardDescription.java from the AOSP
         *
         * Helper to compare two MIME types, where one may be a pattern.
         * @param concreteType A fully-specified MIME type.
         * @param desiredType A desired MIME type that may be a pattern such as * / *.
         * @return Returns true if the two MIME types match.
         */
        fun compareMimeTypes(concreteType: String, desiredType: String): Boolean {
            val typeLength = desiredType.length
            if (typeLength == 3 && desiredType == "*/*") {
                return true
            }
            val slashpos = desiredType.indexOf('/')
            if (slashpos > 0) {
                if (typeLength == slashpos + 2 && desiredType[slashpos + 1] == '*') {
                    if (desiredType.regionMatches(0, concreteType, 0, slashpos + 1)) {
                        return true
                    }
                } else if (desiredType == concreteType) {
                    return true
                }
            }
            return false
        }
    }

    private val prefs by FlorisPreferenceStore
    private val appContext by context.appContext()
    private val editorInstance by context.editorInstance()
    private val systemClipboardManager = context.systemService(AndroidClipboardManager::class)

    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val cleanUpJob: Job
    private val historyMaintenanceMutex = Mutex(locked = false)
    private var clipHistoryDb: ClipboardHistoryDatabase? = null
    private val clipHistoryDao: ClipboardHistoryDao? get() = clipHistoryDb?.clipboardItemDao()

    val historyFlow: StateFlow<ClipboardHistory>
        field = MutableStateFlow(ClipboardHistory.EMPTY)
    val currentHistory: ClipboardHistory
        get() = historyFlow.value

    private val primaryClipLastFromCallbackGuard = Mutex(locked = false)
    private var primaryClipLastFromCallback: ClipData? = null
    @Volatile
    private var directPastePrimaryText: CharSequence? = null
    val primaryClipFlow: StateFlow<ClipboardItem?>
        field = MutableStateFlow(null)
    val primaryClipAvailableFlow: StateFlow<Boolean>
        field = MutableStateFlow(false)
    inline var primaryClip
        get() = primaryClipFlow.value
        private set(v) {
            primaryClipFlow.value = v
        }

    init {
        systemClipboardManager.addPrimaryClipChangedListener(this)
        cleanUpJob = ioScope.launch {
            while (isActive) {
                delay(INTERVAL)
                enforceExpiryDate()
            }
        }
    }

    fun initializeForContext(context: Context) {
        ioScope.launch {
            if (clipHistoryDb == null) {
                clipHistoryDb = ClipboardHistoryDatabase.new(context.applicationContext)
                clipHistoryDao?.let { dao ->
                    ClipboardStorageReconciliation.reconcile(context.applicationContext, dao)
                }
                clipHistoryDao?.getAllAsFlow()?.collect { items ->
                    updateHistory(items)
                }
            }
        }
    }

    private suspend fun updateHistory(items: List<ClipboardItem>) {
        historyMaintenanceMutex.withLock {
            val (retainedItems, rejectedItems) = withContext(Dispatchers.Default) {
                items.partition(ClipboardTextRetentionPolicy::shouldRetain)
            }
            val clipHistory = withContext(Dispatchers.Default) {
                ClipboardHistoryMaintenance.sortedHistory(retainedItems)
            }
            val overflowItems = overflowHistoryItems(clipHistory)
            evictClipboardHistoryItemsNow((rejectedItems + overflowItems).distinctBy { it.id })
            historyFlow.value = ClipboardHistoryMaintenance.withoutEvictedItems(clipHistory, overflowItems)
        }
    }

    private fun replacePrimaryState(item: ClipboardItem?, directPasteText: CharSequence?) {
        directPastePrimaryText = directPasteText
        primaryClip = item
        primaryClipAvailableFlow.value = item != null || directPasteText != null
    }

    /**
     * Sets the current primary clip without updating the internal clipboard history.
     */
    fun updatePrimaryClip(item: ClipboardItem?) {
        if (item != null && !ClipboardTextRetentionPolicy.shouldRetain(item)) {
            val text = item.text ?: return
            setPlaintextWithoutHistory(text, item.isSensitive)
            return
        }
        replacePrimaryState(item, directPasteText = null)
        if (prefs.clipboard.useInternalClipboard.get()) {
            val syncBehavior = prefs.clipboard.syncToSystem.get()
            val clipData = item?.toClipData(appContext)
            if (clipData != null && syncBehavior.shouldSyncSet) {
                systemClipboardManager.setPrimaryClip(clipData)
            } else if (clipData == null && syncBehavior.shouldSyncClear) {
                systemClipboardManager.clearPrimaryClipAnyApi()
            }
        } else {
            systemClipboardManager.setOrClearPrimaryClip(item?.toClipData(appContext))
        }
    }

    /**
     * Called by system clipboard when the system primary clip has changed.
     */
    override fun onPrimaryClipChanged() {
        val syncBehavior = prefs.clipboard.syncToFloris.get()
        if (!prefs.clipboard.useInternalClipboard.get() || syncBehavior != ClipboardSyncBehavior.NO_EVENTS) {
            val systemPrimaryClip = systemClipboardManager.primaryClip
            ioScope.launch {
                if (systemPrimaryClip == null) {
                    primaryClipLastFromCallbackGuard.withLock {
                        primaryClipLastFromCallback = null
                    }
                    if (syncBehavior.shouldSyncClear) {
                        replaceSystemPrimaryClip(null)
                    }
                    return@launch
                }

                if (systemPrimaryClip.getItemAt(0).let { it.text == null && it.uri == null }) {
                    primaryClipLastFromCallbackGuard.withLock {
                        primaryClipLastFromCallback = null
                    }
                    if (syncBehavior.shouldSyncClear) {
                        replaceSystemPrimaryClip(null)
                    }
                    return@launch
                }

                if (!syncBehavior.shouldSyncSet) {
                    return@launch
                }

                val pasteOnlyText = ClipboardTextRetentionPolicy.pasteOnlyTextOrNull(systemPrimaryClip)
                if (pasteOnlyText != null) {
                    primaryClipLastFromCallbackGuard.withLock {
                        // Oversized text is not compared or retained in the
                        // callback-deduplication snapshot.
                        primaryClipLastFromCallback = null
                    }
                    replaceSystemPrimaryClipWithDirectPasteText(pasteOnlyText)
                    return@launch
                }

                val isDuplicate: Boolean
                primaryClipLastFromCallbackGuard.withLock {
                    val a = primaryClipLastFromCallback?.getItemAt(0)
                    val b = systemPrimaryClip.getItemAt(0)
                    isDuplicate = when {
                        a === b -> true
                        a == null -> false
                        else -> a.text == b.text && a.uri == b.uri
                    }
                    primaryClipLastFromCallback = systemPrimaryClip
                }
                if (isDuplicate) return@launch

                val internalPrimaryClip = primaryClip
                val isEqual = internalPrimaryClip?.isEqualTo(systemPrimaryClip) == true
                if (!isEqual) {
                    // Decide sensitivity BEFORE cloning. fromClipData(cloneUri = true)
                    // unconditionally writes IMAGE/VIDEO bytes to on-disk clipboard
                    // storage; the `if (!item.isSensitive)` history gate below only
                    // prevents the *history row*, so a sensitive media clip (e.g. a
                    // copied secret image from a password manager) would still leave a
                    // plaintext file + DAO row on disk that nothing deletes until the
                    // next process-start reconcile. Skipping the clone for sensitive
                    // clips keeps the bytes off disk entirely; isSensitive is parsed
                    // from the description independently of cloneUri, mirrored here.
                    val isSensitiveClip = if (AndroidVersion.ATLEAST_API33_T) {
                        systemPrimaryClip.description?.extras?.getBoolean(EXTRA_IS_SENSITIVE) ?: false
                    } else {
                        false
                    }
                    val item = try {
                        ClipboardItem.fromClipData(appContext, systemPrimaryClip, cloneUri = !isSensitiveClip)
                    } catch (e: Exception) {
                        flogError { "Failed to import system clipboard item: ${e.message.orEmpty()}" }
                        return@launch
                    }
                    replaceSystemPrimaryClip(item)
                    // Skip IME-local history when the source app marked the
                    // clip as sensitive via
                    // `ClipDescription.EXTRA_IS_SENSITIVE` (API 33+).
                    // Password managers (Bitwarden, 1Password, KeePassXC,
                    // Proton Pass) and TOTP apps set this flag on every
                    // copied credential. The system clipboard still
                    // receives it (the OS is the source of truth for
                    // primary-clip behaviour), but our IME-local history
                    // must not retain a copy that would resurface on the
                    // next clipboard-palette open. The flag was already
                    // parsed into `ClipboardItem.isSensitive` by
                    // `fromClipData`; this is the missing gate that
                    // *uses* the flag.
                    if (!item.isSensitive) {
                        insertOrMoveBeginning(item)
                    }
                }
            }
        }
    }

    private fun replaceSystemPrimaryClip(item: ClipboardItem?) {
        val previous = primaryClip
        replacePrimaryState(item, directPasteText = null)
        if (ClipboardPrimaryClipCleanupPolicy.shouldCloseReplacedPrimaryClip(
                replacedItem = previous,
                historyEnabled = prefs.clipboard.historyEnabled.get(),
            )
        ) {
            previous?.close(appContext)
        }
    }

    private fun replaceSystemPrimaryClipWithDirectPasteText(text: CharSequence) {
        val previous = primaryClip
        replacePrimaryState(item = null, directPasteText = text)
        if (ClipboardPrimaryClipCleanupPolicy.shouldCloseReplacedPrimaryClip(
                replacedItem = previous,
                historyEnabled = prefs.clipboard.historyEnabled.get(),
            )
        ) {
            previous?.close(appContext)
        }
    }

    /**
     * Change the current text on clipboard, update history (if enabled).
     */
    private fun addNewClip(item: ClipboardItem) {
        insertOrMoveBeginning(item)
        updatePrimaryClip(item)
    }

    /**
     * Wraps some plaintext in a ClipData and calls [addNewClip]
     */
    fun addNewPlaintext(newText: String) {
        if (!ClipboardTextRetentionPolicy.shouldRetain(newText)) {
            setPlaintextWithoutHistory(newText, isSensitive = false)
            return
        }
        val newData = ClipboardItem.text(newText)
        addNewClip(newData)
    }

    /**
     * Publishes text for direct paste without inserting it into history.
     *
     * Oversized text is held only as the current in-memory paste target and is
     * never converted to a Room-backed [ClipboardItem].
     */
    fun setPlaintextWithoutHistory(newText: CharSequence, isSensitive: Boolean) {
        if (ClipboardTextRetentionPolicy.shouldRetain(newText)) {
            updatePrimaryClip(
                ClipboardItem.text(newText.toString()).copy(isSensitive = isSensitive),
            )
            return
        }

        replacePrimaryState(item = null, directPasteText = newText)
        val clipData = ClipData.newPlainText(ClipboardItem.FLORIS_CLIP_LABEL, newText)
        if (isSensitive && AndroidVersion.ATLEAST_API33_T) {
            clipData.description.extras = PersistableBundle().apply {
                putBoolean(EXTRA_IS_SENSITIVE, true)
            }
        }
        if (prefs.clipboard.useInternalClipboard.get()) {
            if (prefs.clipboard.syncToSystem.get().shouldSyncSet) {
                systemClipboardManager.setPrimaryClip(clipData)
            }
        } else {
            systemClipboardManager.setPrimaryClip(clipData)
        }
    }

    /**
     * Adds a new item to the clipboard history (if enabled).
     */
    private fun insertOrMoveBeginning(newItem: ClipboardItem) {
        if (!ClipboardTextRetentionPolicy.shouldRetain(newItem)) return
        if (prefs.clipboard.historyEnabled.get()) {
            val historyElement = currentHistory.all.firstOrNull { item ->
                item.type == ItemType.TEXT && item.text == newItem.text && item.isSensitive == newItem.isSensitive
            }
            if (historyElement != null) {
                moveToTheBeginning(
                    oldItem = historyElement,
                    newItem = if (historyElement.isPinned) {
                        newItem.copy(isPinned = true)
                    } else {
                        newItem
                    }
                )
            } else {
                insertClip(newItem)
            }
        }
    }

    private fun overflowHistoryItems(clipHistory: ClipboardHistory): List<ClipboardItem> {
        return if (prefs.clipboard.historySizeLimitEnabled.get()) {
            ClipboardHistoryEviction.overflowItems(
                history = clipHistory,
                historySizeLimit = prefs.clipboard.historySizeLimit.get(),
            )
        } else {
            emptyList()
        }
    }

    private suspend fun enforceExpiryDate() {
        historyMaintenanceMutex.withLock {
            val clipHistory = currentHistory
            val expiredItems = ClipboardHistoryEviction.expiredItems(
                history = clipHistory,
                nowMs = System.currentTimeMillis(),
                oldEnabled = prefs.clipboard.historyAutoCleanOldEnabled.get(),
                oldAfterMinutes = prefs.clipboard.historyAutoCleanOldAfter.get(),
                sensitiveEnabled = prefs.clipboard.historyAutoCleanSensitiveEnabled.get(),
                sensitiveAfterSeconds = prefs.clipboard.historyAutoCleanSensitiveAfter.get(),
            )
            evictClipboardHistoryItemsNow(expiredItems)
            historyFlow.value = ClipboardHistoryMaintenance.withoutEvictedItems(clipHistory, expiredItems)
        }
    }

    private suspend fun evictClipboardHistoryItemsNow(items: List<ClipboardItem>) {
        if (items.isEmpty()) return
        ClipboardHistoryEviction.closeThenDelete(
            items = items,
            closeItem = { it.close(appContext) },
            deleteItems = { clipHistoryDao?.delete(it) },
        )
    }

    private fun moveToTheBeginning(oldItem: ClipboardItem, newItem: ClipboardItem) {
        ioScope.launch {
            clipHistoryDao?.deleteAndInsert(oldItem.id, newItem)
        }
    }

    fun insertClip(item: ClipboardItem) {
        if (!ClipboardTextRetentionPolicy.shouldRetain(item)) return
        ioScope.launch {
            val id = clipHistoryDao?.insert(item)
            item.id = id ?: 0
        }
    }

    fun clearExactHistory(items: List<ClipboardItem>) {
        ioScope.launch {
            for (item in items) {
                item.close(appContext)
            }
            clipHistoryDao?.delete(items)
        }
    }

    /**
     * Clears all unpinned items from the clipboard history
     */
    fun clearHistory() {
        ioScope.launch {
            // Only close (and thereby delete backing media of) unpinned items:
            // the DB keeps pinned rows, so closing them too would strand pinned
            // image/video clips with their content provider files gone.
            val snapshot = currentHistory.unpinned.toList()
            for (item in snapshot) {
                item.close(appContext)
            }
            clipHistoryDao?.deleteAllUnpinned()
        }
    }

    /**
     * Clears the full clipboard history
     */
    fun clearFullHistory() {
        ioScope.launch {
            val snapshot = currentHistory.all.toList()
            for (item in snapshot) {
                item.close(appContext)
            }
            clipHistoryDao?.deleteAll()
        }
    }


    /**
     * Restore the clipboard history from a [List]
     *
     * @param items the [ClipboardItem] list with the new items
     */
    fun restoreHistory(items: List<ClipboardItem>) {
        ioScope.launch {
            val currentHistory = currentHistory.all
                .mapTo(mutableSetOf()) { it.copy(id = 0) }
            for (item in items) {
                if (!ClipboardTextRetentionPolicy.shouldRetain(item)) continue
                val restoredItem = item.copy(id = 0)
                if (currentHistory.add(restoredItem)) {
                    insertClip(restoredItem)
                }
            }
        }
    }

    fun deleteClip(item: ClipboardItem, onlyIfUnpinned: Boolean) {
        ioScope.launch {
            val deletedRows = if (onlyIfUnpinned) {
                clipHistoryDao?.deleteIfUnpinned(item.id)
            } else {
                clipHistoryDao?.delete(item.id)
            } ?: 0
            if (onlyIfUnpinned && deletedRows <= 0) return@launch
            tryOrNull {
                val uri = item.uri
                if (uri != null) {
                    appContext.contentResolver.delete(uri, null, null)
                }
            }
        }
    }

    fun pinClip(item: ClipboardItem) {
        ioScope.launch {
            clipHistoryDao?.update(item.copy(isPinned = true))
        }
    }

    fun unpinClip(item: ClipboardItem) {
        ioScope.launch {
            clipHistoryDao?.update(item.copy(isPinned = false))
        }
    }

    fun pasteItem(item: ClipboardItem) {
        val editorInstance by appContext.editorInstance()
        editorInstance.commitClipboardItem(item).also { result ->
            if (!result) {
                appContext.postShortToast(R.string.clipboard__paste_failed)
            }
        }
    }

    /**
     * Returns true if the editor can accept the clip item, else false.
     */
    fun canBePasted(clipItem: ClipboardItem?): Boolean {
        if (clipItem == null) return false

        return clipItem.mimeTypes.contains("text/plain") || editorInstance.activeInfo.contentMimeTypes.any { editorType ->
            clipItem.mimeTypes.any { clipType ->
                compareMimeTypes(clipType, editorType)
            }
        }
    }

    fun canPastePrimaryClip(): Boolean {
        return directPastePrimaryText != null || canBePasted(primaryClip)
    }

    /**
     * The live, non-retained oversized text accepted only by the explicit
     * direct-paste path. Callers must not search, classify, or persist it.
     */
    fun primaryTextForDirectPaste(): CharSequence? = directPastePrimaryText

    /**
     * Cleans up.
     *
     * Unregisters the system clipboard listener, cancels clipboard clean ups.
     */
    override fun close() {
        systemClipboardManager.removePrimaryClipChangedListener(this)
        // Cancel the whole IO scope, not just cleanUpJob: initializeForContext()
        // launches a long-lived clipboard-history flow collector in the same scope,
        // and cancelling only cleanUpJob would leak it (and any in-flight launches).
        ioScope.cancel()
    }
}

internal object ClipboardPrimaryClipCleanupPolicy {
    private val ProviderBackedUriPrefix = "content://${ClipboardMediaProvider.AUTHORITY}/"

    fun shouldCloseReplacedPrimaryClip(
        replacedItem: ClipboardItem?,
        historyEnabled: Boolean,
    ): Boolean {
        return shouldCloseProviderBackedPrimaryClipUri(
            uriString = replacedItem?.uri?.toString(),
            historyEnabled = historyEnabled,
        )
    }

    fun shouldCloseProviderBackedPrimaryClipUri(
        uriString: String?,
        historyEnabled: Boolean,
    ): Boolean {
        if (historyEnabled) return false
        val uri = uriString ?: return false
        return uri.startsWith(ProviderBackedUriPrefix, ignoreCase = true)
    }
}

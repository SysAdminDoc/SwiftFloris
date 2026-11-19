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
import android.content.Intent
import android.os.PersistableBundle
import androidx.annotation.VisibleForTesting
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.appContext
import dev.patrickgold.florisboard.editorInstance
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardHistoryDao
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardHistoryDatabase
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardHistoryStore
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardItem
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardMediaProvider
import dev.patrickgold.florisboard.ime.clipboard.provider.ItemType
import dev.patrickgold.florisboard.ime.security.AdvancedProtectionPolicy
import dev.patrickgold.florisboard.lib.devtools.flogError
import java.io.Closeable
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
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
    private val reportScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val cleanUpJob: Job
    private val historyMaintenanceMutex = Mutex(locked = false)
    // Local history writes start here one at a time, so they reach the maintenance lock in the
    // order they were asked for: a delete of a clip copied just before waits for its row.
    private val historyWrites = Dispatchers.IO.limitedParallelism(1)
    private var clipHistoryDb: ClipboardHistoryDatabase? = null
    private val clipHistoryDao: ClipboardHistoryDao? get() = clipHistoryDb?.clipboardItemDao()
    private val clipHistoryDaoReady = CompletableDeferred<ClipboardHistoryDao>()

    val historyFlow: StateFlow<ClipboardHistory>
        field = MutableStateFlow(ClipboardHistory.EMPTY)
    val currentHistory: ClipboardHistory
        get() = historyFlow.value

    private val primaryClipLastFromCallbackGuard = Mutex(locked = false)
    private var primaryClipLastFromCallback: ClipData? = null
    // The callback handling the remembered clip. Repeats leave it with the callback they defer
    // to, so only that one may forget the clip.
    private var primaryClipLastFromCallbackOwner: CallbackRevision? = null
    // Newest revision that reached the remembered clip check, so an older callback that gets
    // there late leaves the clip alone.
    private var primaryClipLastFromCallbackNewest = 0L
    private val clipRevisions = ClipboardRevisionGate()
    private val callbacksInFlight = AtomicInteger(0)

    /** System clipboard callbacks that haven't finished yet, so tests can wait without sleeping. */
    @VisibleForTesting
    internal val clipboardCallbacksInFlight: Int
        get() = callbacksInFlight.get()

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
            try {
                if (clipHistoryDb == null) {
                    clipHistoryDb = ClipboardHistoryStore.open(context.applicationContext)
                    val dao = checkNotNull(clipHistoryDao)
                    ClipboardStorageReconciliation.reconcile(context.applicationContext, dao)
                    clipHistoryDaoReady.complete(dao)
                    dao.getAllAsFlow().collect { items ->
                        updateHistory(items)
                    }
                } else {
                    clipHistoryDao?.let(clipHistoryDaoReady::complete)
                }
            } catch (error: Throwable) {
                clipHistoryDaoReady.completeExceptionally(error)
                throw error
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
            val evicted = evictClipboardHistoryItemsNow((rejectedItems + overflowItems).distinctBy { it.id })
            historyFlow.value = ClipboardHistoryMaintenance.withoutEvictedItems(clipHistory, evicted)
        }
    }

    private fun replacePrimaryState(item: ClipboardItem?, directPasteText: CharSequence?) {
        directPastePrimaryText = directPasteText
        primaryClip = item
        primaryClipAvailableFlow.value = item != null || directPasteText != null
    }

    /**
     * Makes [item] or [directPasteText] what the keyboard pastes, then hands it to the system clipboard with
     * [toSystem]. When the system clipboard throws, the keyboard's previous clip goes back before the failure
     * reaches the caller, so the keyboard never offers to paste what the caller reports as not copied.
     */
    private inline fun publishLocally(item: ClipboardItem?, directPasteText: CharSequence?, toSystem: () -> Unit) {
        val previousItem = primaryClip
        val previousText = directPastePrimaryText
        clipRevisions.local { replacePrimaryState(item, directPasteText) }
        try {
            toSystem()
        } catch (e: Exception) {
            clipRevisions.local { replacePrimaryState(previousItem, previousText) }
            throw e
        }
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
        publishLocally(item, directPasteText = null) {
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
    }

    /**
     * Called by system clipboard when the system primary clip has changed.
     */
    override fun onPrimaryClipChanged() {
        val syncBehavior = prefs.clipboard.syncToFloris.get()
        if (!prefs.clipboard.useInternalClipboard.get() || syncBehavior != ClipboardSyncBehavior.NO_EVENTS) {
            val systemPrimaryClip = systemClipboardManager.primaryClip
            // Taken here, on the main thread, so revisions follow the order the changes happened in.
            val revision = clipRevisions.arrive()
            val callback = CallbackRevision(revision)
            callbacksInFlight.incrementAndGet()
            ioScope.launch {
                // A clear the sync setting ignores changes nothing, so it also leaves the
                // remembered clip, and any revision a repeat handed on, alone.
                if (systemPrimaryClip == null) {
                    if (syncBehavior.shouldSyncClear) {
                        primaryClipLastFromCallbackGuard.withLock { forgetPrimaryClipFromCallback(revision) }
                        publishFromCallback(revision, item = null, directPasteText = null)
                    }
                    return@launch
                }

                if (systemPrimaryClip.getItemAt(0).let { it.text == null && it.uri == null }) {
                    if (syncBehavior.shouldSyncClear) {
                        primaryClipLastFromCallbackGuard.withLock { forgetPrimaryClipFromCallback(revision) }
                        publishFromCallback(revision, item = null, directPasteText = null)
                    }
                    return@launch
                }

                if (!syncBehavior.shouldSyncSet) {
                    return@launch
                }

                val pasteOnlyText = ClipboardTextRetentionPolicy.pasteOnlyTextOrNull(systemPrimaryClip)
                if (pasteOnlyText != null) {
                    // Oversized text is not compared or retained in the
                    // callback-deduplication snapshot.
                    primaryClipLastFromCallbackGuard.withLock { forgetPrimaryClipFromCallback(revision) }
                    publishFromCallback(revision, item = null, directPasteText = pasteOnlyText)
                    return@launch
                }

                // The repeat check, the hand-off and the claim share one lock section, so no repeat
                // can hand its revision to this callback after it has settled.
                val claimedAsPrimary: Boolean
                primaryClipLastFromCallbackGuard.withLock {
                    val owner = primaryClipLastFromCallbackOwner
                    val a = primaryClipLastFromCallback?.getItemAt(0)
                    val b = systemPrimaryClip.getItemAt(0)
                    val isDuplicate = when {
                        a === b -> true
                        a == null -> false
                        else -> a.text == b.text && a.uri == b.uri
                    }
                    // Callbacks run in parallel, so an older one can get here after a newer one.
                    // It leaves the newer one's clip in place.
                    val isNewest = revision > primaryClipLastFromCallbackNewest
                    if (isNewest) primaryClipLastFromCallbackNewest = revision
                    if (isDuplicate && owner != null && !owner.finished && revision > owner.arrival) {
                        // Some phones report one copy twice. A repeat of a copy that's still
                        // cloning is part of it, and that copy publishes or fails as the newer of
                        // the two. It keeps that even if a newer copy takes the remembered clip
                        // before it's done. An older copy that gets here late isn't a repeat, so
                        // it isn't lost if the newer one fails.
                        owner.publishAs = maxOf(owner.publishAs, revision)
                        return@launch
                    }
                    if (isNewest) {
                        primaryClipLastFromCallback = systemPrimaryClip
                        primaryClipLastFromCallbackOwner = callback
                    }
                    // The primary clip already shows this copy: it's the clip the remembered copy
                    // published, or an equal one (a media clone never equals its source, so an
                    // image only matches the first way). Claiming the revision makes an older copy
                    // that's cloning lose. If an older copy published first, it may have closed
                    // the clip it replaced, so this copy is imported again instead.
                    val shown = primaryClip
                    claimedAsPrimary = shown != null &&
                        ((isDuplicate && shown === owner?.shows) || shown.isEqualTo(systemPrimaryClip)) &&
                        clipRevisions.claimIfNewest(revision) { primaryClip === shown }
                    if (claimedAsPrimary) {
                        callback.shows = shown
                        callback.finished = true
                    }
                }
                if (!claimedAsPrimary) {
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
                        // A copy that couldn't be read changes nothing, as if it never happened, so
                        // an older copy or clear still applies. Copying it again isn't a repeat,
                        // so that gets another try. Only while this callback still owns the
                        // remembered clip: a repeat that came in meanwhile left it here, a newer
                        // copy took it.
                        primaryClipLastFromCallbackGuard.withLock {
                            if (primaryClipLastFromCallbackOwner === callback) primaryClipLastFromCallback = null
                            callback.finished = true
                        }
                        return@launch
                    }
                    // Under the guard, so no repeat can hand its revision on between reading it
                    // and publishing.
                    suspend fun publish(clip: ClipboardItem) = primaryClipLastFromCallbackGuard.withLock {
                        publishFromCallback(callback.publishAs, clip, directPasteText = null).also { won ->
                            if (won) {
                                callback.shows = clip
                            } else if (primaryClipLastFromCallbackOwner === callback) {
                                primaryClipLastFromCallback = null
                            }
                            callback.finished = true
                        }
                    }
                    // Skip IME-local history when the source app marked the clip as sensitive via
                    // `ClipDescription.EXTRA_IS_SENSITIVE` (API 33+). Password managers (Bitwarden,
                    // 1Password, KeePassXC, Proton Pass) and TOTP apps set this flag on every
                    // copied credential. The system clipboard still receives it (the OS is the
                    // source of truth for primary-clip behaviour), but our IME-local history must
                    // not retain a copy that would resurface on the next clipboard-palette open.
                    // The flag was already parsed into `ClipboardItem.isSensitive` by
                    // `fromClipData`. A clip history won't keep doesn't wait for the history lock,
                    // so a long clear can't hold it up.
                    val published = if (item.isSensitive || !historyKeeps(item)) {
                        publish(item)
                    } else {
                        // Publishing and adding the history row share the history lock, so a
                        // delete of the primary clip waits for its row, and rows go in in the
                        // order their clips were published. The time is taken here for the same
                        // reason: a clip that finished cloning first but published second is
                        // still the newer one.
                        historyMaintenanceMutex.withLock {
                            val copied = item.copy(creationTimestampMs = System.currentTimeMillis())
                            publish(copied).also { won -> if (won) insertOrMoveBeginningNow(copied) }
                        }
                    }
                    if (!published) {
                        // A newer copy, a clear or a local change published while this one was
                        // cloning, so it's dropped whole. Only a file this callback cloned is
                        // deleted: our own clip echoed back keeps the history item's URI.
                        if (item.uri != null && item.uri != systemPrimaryClip.getItemAt(0).uri) {
                            item.close(appContext)
                        }
                        return@launch
                    }
                }
            }.invokeOnCompletion { callbacksInFlight.decrementAndGet() }
        }
    }

    /**
     * A clear the sync setting applies, or text too long to remember, newer than everything that
     * reached the remembered clip check replaces that clip with nothing. Call with
     * [primaryClipLastFromCallbackGuard] held.
     */
    private fun forgetPrimaryClipFromCallback(revision: Long) {
        if (revision > primaryClipLastFromCallbackNewest) {
            primaryClipLastFromCallback = null
            primaryClipLastFromCallbackOwner = null
            primaryClipLastFromCallbackNewest = revision
        }
    }

    /**
     * A system clipboard callback's revision. A repeat of its copy that arrived after it, made
     * while it still clones, hands it a newer revision in [publishAs], and it publishes or fails as
     * that one. Once [finished], a repeat no longer joins it, and [shows] is the clip it published
     * or claimed, if any. Guarded by [primaryClipLastFromCallbackGuard], except [arrival].
     */
    private class CallbackRevision(revision: Long) {
        val arrival = revision
        var publishAs = revision
        var finished = false
        var shows: ClipboardItem? = null
    }

    /**
     * Publishes a system clipboard callback's result unless a newer change published while it ran.
     * Returns false, having changed nothing, when it lost that race.
     */
    private fun publishFromCallback(
        revision: Long,
        item: ClipboardItem?,
        directPasteText: CharSequence?,
    ): Boolean {
        var previous: ClipboardItem? = null
        val change: () -> Unit = {
            previous = primaryClip
            replacePrimaryState(item, directPasteText)
        }
        val published = clipRevisions.publishIfNewest(revision, change)
        // A clip that points at the same file as the one it replaces, like our own clip echoed
        // back, keeps that file. So does one the system clipboard holds again: another app
        // copied it after this callback's clip, and that newer copy still has to read it.
        if (published && previous?.uri != item?.uri && ClipboardPrimaryClipCleanupPolicy.shouldCloseReplacedPrimaryClip(
                replacedItem = previous,
                historyEnabled = prefs.clipboard.historyEnabled.get(),
            ) && systemClipboardManager.primaryClip?.getItemAt(0)?.uri != previous?.uri
        ) {
            previous?.close(appContext)
        }
        return published
    }

    /**
     * Change the current text on clipboard, update history (if enabled). History is written only once the
     * clipboard took the clip, so a copy the system refuses leaves no row to paste it from.
     */
    private fun addNewClip(item: ClipboardItem) {
        updatePrimaryClip(item)
        insertOrMoveBeginning(item)
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

        val clipData = ClipData.newPlainText(ClipboardItem.FLORIS_CLIP_LABEL, newText)
        if (isSensitive && AndroidVersion.ATLEAST_API33_T) {
            clipData.description.extras = PersistableBundle().apply {
                putBoolean(EXTRA_IS_SENSITIVE, true)
            }
        }
        publishLocally(item = null, directPasteText = newText) {
            if (prefs.clipboard.useInternalClipboard.get()) {
                if (prefs.clipboard.syncToSystem.get().shouldSyncSet) {
                    systemClipboardManager.setPrimaryClip(clipData)
                }
            } else {
                systemClipboardManager.setPrimaryClip(clipData)
            }
        }
    }

    /**
     * Adds a new item to the clipboard history (if enabled), after every history write asked for
     * before it.
     */
    private fun insertOrMoveBeginning(newItem: ClipboardItem) {
        ioScope.launch(historyWrites) {
            historyMaintenanceMutex.withLock { insertOrMoveBeginningNow(newItem) }
        }
    }

    /** Whether history takes a new copy of [item] right now. */
    private fun historyKeeps(item: ClipboardItem): Boolean {
        if (!ClipboardTextRetentionPolicy.shouldRetain(item)) return false
        // Under Android Advanced Protection Mode nothing the user copies is
        // written to history, whatever the saved preference says. The clip is
        // still pasteable right now — this withholds the on-disk record, not
        // the clipboard itself.
        if (AdvancedProtectionPolicy.decide(appContext).suspendsClipboardHistoryPersistence) return false
        return prefs.clipboard.historyEnabled.get()
    }

    /**
     * Adds [newItem] to history (if enabled), or moves the row already holding it to the top, and
     * gives [newItem] that row's id. Call with [historyMaintenanceMutex] held, off the main thread.
     */
    private fun insertOrMoveBeginningNow(newItem: ClipboardItem) {
        if (!historyKeeps(newItem)) return
        val dao = clipHistoryDao ?: return
        // The stored rows, not currentHistory, which lags behind them: a clip copied again right
        // after the first time would get a second row. A media URI match is one of our own clips
        // copied back. A second row would share its file and lose it when either row is deleted.
        val stored = dao.getAll().firstOrNull { holdsSameClip(it, newItem) }
        newItem.id = if (stored != null) {
            dao.deleteAndInsert(stored.id, if (stored.isPinned) newItem.copy(isPinned = true) else newItem)
        } else {
            dao.insert(newItem)
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
            val evicted = evictClipboardHistoryItemsNow(expiredItems)
            historyFlow.value = ClipboardHistoryMaintenance.withoutEvictedItems(clipHistory, evicted)
        }
    }

    /** Returns the items that are gone. One whose media stayed keeps its row and stays visible. */
    private fun evictClipboardHistoryItemsNow(items: List<ClipboardItem>): List<ClipboardItem> {
        return ClipboardHistoryEviction.closeThenDelete(
            items = items,
            closeItem = { it.deleteMedia(appContext) },
            deleteItems = { clipHistoryDao?.delete(it) },
        )
    }

    fun insertClip(item: ClipboardItem) {
        if (!ClipboardTextRetentionPolicy.shouldRetain(item)) return
        ioScope.launch {
            val id = clipHistoryDao?.insert(item)
            item.id = id ?: 0
        }
    }

    /**
     * Deletes [items] and their media from the clipboard history and returns what's still stored.
     * With [onlyIfUnpinned], an item pinned since [items] was read stays. Like the other deletes
     * here, it returns only once every store answered.
     */
    suspend fun clearExactHistory(items: List<ClipboardItem>, onlyIfUnpinned: Boolean): ClipboardDeletionResult {
        return maintainHistory {
            ClipboardHistoryDeletion.deleteExact(appContext, clipHistoryDao, items, onlyIfUnpinned)
        }
    }

    /**
     * Clears all unpinned items from the clipboard history. Pinned ones keep their rows and media.
     */
    suspend fun clearHistory(): ClipboardDeletionResult {
        return maintainHistory { ClipboardHistoryDeletion.deleteUnpinned(appContext, clipHistoryDao) }
    }

    /**
     * Clears the full clipboard history
     */
    suspend fun clearFullHistory(): ClipboardDeletionResult {
        return maintainHistory { ClipboardHistoryDeletion.deleteAll(appContext, clipHistoryDao) }
    }

    /**
     * Runs a history delete in the manager's own scope, so a caller that goes away (the clipboard
     * screen closing) doesn't cut it short, and under the maintenance lock, so a pin can't land
     * between reading which items to delete and deleting them. It queues behind the history writes
     * asked for before it, so a clip copied just before has its row by then.
     */
    private suspend fun maintainHistory(delete: () -> ClipboardDeletionResult): ClipboardDeletionResult {
        return ioScope.async(historyWrites) { historyMaintenanceMutex.withLock { delete() } }.await()
    }

    /**
     * Runs [delete] and hands its result to [report] on the main thread, in the manager's scope:
     * the clipboard screen can close at any point and the delete still runs and gets reported.
     */
    fun deleteThenReport(
        delete: suspend ClipboardManager.() -> ClipboardDeletionResult,
        report: (ClipboardDeletionResult) -> Unit,
    ) {
        reportScope.launch { report(delete()) }
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

    /**
     * Transactional restore primitives. Unlike the legacy fire-and-forget
     * methods above, these return only after Room has committed so callers can
     * coordinate backup restore and rollback with clipboard media storage.
     */
    suspend fun snapshotHistoryForRestore(): List<ClipboardItem> = withContext(Dispatchers.IO) {
        clipHistoryDaoReady.await().getAll()
    }

    suspend fun clearFullHistoryForRestore() = withContext(Dispatchers.IO) {
        historyMaintenanceMutex.withLock {
            val dao = clipHistoryDaoReady.await()
            dao.getAll().forEach { item ->
                val uri = item.uri
                if (uri?.authority == ClipboardMediaProvider.AUTHORITY) {
                    runCatching {
                        appContext.revokeUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    }
                }
            }
            dao.deleteAll()
        }
    }

    suspend fun restoreHistoryAndAwait(items: List<ClipboardItem>) = withContext(Dispatchers.IO) {
        historyMaintenanceMutex.withLock {
            val dao = clipHistoryDaoReady.await()
            val existing = dao.getAll()
                .mapTo(mutableSetOf()) { it.copy(id = 0) }
            for (item in items) {
                if (!ClipboardTextRetentionPolicy.shouldRetain(item)) continue
                val restoredItem = item.copy(id = 0)
                if (existing.add(restoredItem)) {
                    dao.insert(restoredItem)
                }
            }
        }
    }

    suspend fun replaceHistoryFromRollback(items: List<ClipboardItem>) = withContext(Dispatchers.IO) {
        historyMaintenanceMutex.withLock {
            val retainedItems = items.filter(ClipboardTextRetentionPolicy::shouldRetain)
            clipHistoryDaoReady.await().replaceAllForRestore(retainedItems)
        }
    }

    /**
     * Deletes [item] and its media. With [onlyIfUnpinned], a pinned item, or one no longer in
     * history, is left alone and counts as done.
     */
    suspend fun deleteClip(item: ClipboardItem, onlyIfUnpinned: Boolean): ClipboardDeletionResult {
        return maintainHistory { ClipboardHistoryDeletion.deleteOne(appContext, clipHistoryDao, item, onlyIfUnpinned) }
    }

    fun pinClip(item: ClipboardItem) {
        ioScope.launch {
            historyMaintenanceMutex.withLock { clipHistoryDao?.update(item.copy(isPinned = true)) }
        }
    }

    fun unpinClip(item: ClipboardItem) {
        ioScope.launch {
            historyMaintenanceMutex.withLock { clipHistoryDao?.update(item.copy(isPinned = false)) }
        }
    }

    /** Persists the user's per-entry sensitive-text classification without changing its content. */
    fun setClipSensitive(item: ClipboardItem, isSensitive: Boolean) {
        if (item.type != ItemType.TEXT || item.isSensitive == isSensitive) return
        ioScope.launch {
            clipHistoryDao?.update(item.copy(isSensitive = isSensitive))
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
        reportScope.cancel()
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

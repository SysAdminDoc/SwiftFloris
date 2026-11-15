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

package dev.patrickgold.florisboard.ime.core

import android.content.Context
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.keyboard.CurrencySet
import dev.patrickgold.florisboard.ime.keyboard.LayoutType
import dev.patrickgold.florisboard.ime.keyboard.extCoreLayout
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.lib.FlorisLocale
import dev.patrickgold.florisboard.lib.devtools.flogDebug
import dev.patrickgold.florisboard.lib.devtools.flogError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.florisboard.lib.kotlin.collectLatestIn
import java.util.Locale

val SubtypeJsonConfig = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    isLenient = false
}

@Serializable
data class PerAppSubtypeMemoryEntry(
    val packageName: String,
    val subtypeId: Long,
)

data class PerAppSubtypeMemoryDecision(
    val subtypeId: Long?,
    val prunedRawJson: String,
)

object PerAppSubtypeMemory {
    const val EmptyJson = "{}"

    private val JsonConfig = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    fun isRecordablePackageName(packageName: String?): Boolean {
        val trimmed = packageName?.trim() ?: return false
        return trimmed.isNotEmpty() && trimmed.length <= 255 && '/' !in trimmed
    }

    fun parse(rawJson: String): Map<String, Long> {
        if (rawJson.isBlank()) return emptyMap()
        return runCatching {
            JsonConfig.decodeFromString<Map<String, Long>>(rawJson)
        }.recoverCatching {
            JsonConfig.decodeFromString<List<PerAppSubtypeMemoryEntry>>(rawJson)
                .associate { it.packageName to it.subtypeId }
        }.getOrElse {
            emptyMap()
        }.filterKeys(::isRecordablePackageName)
    }

    fun serialize(memory: Map<String, Long>): String {
        if (memory.isEmpty()) return EmptyJson
        val sorted = memory.toSortedMap().entries.associate { (key, value) -> key to value }
        return JsonConfig.encodeToString<Map<String, Long>>(sorted)
    }

    fun remember(
        rawJson: String,
        packageName: String?,
        subtypeId: Long,
        availableSubtypeIds: Set<Long>,
    ): String {
        if (!isRecordablePackageName(packageName) || subtypeId !in availableSubtypeIds) {
            return prune(rawJson, availableSubtypeIds)
        }
        val next = parse(rawJson)
            .filterValues { it in availableSubtypeIds }
            .plus(packageName!!.trim() to subtypeId)
        return serialize(next)
    }

    fun resolve(
        rawJson: String,
        packageName: String?,
        availableSubtypeIds: Set<Long>,
    ): PerAppSubtypeMemoryDecision {
        if (!isRecordablePackageName(packageName)) {
            val prunedRawJson = prune(rawJson, availableSubtypeIds)
            return PerAppSubtypeMemoryDecision(subtypeId = null, prunedRawJson = prunedRawJson)
        }
        val pruned = parse(rawJson).filterValues { it in availableSubtypeIds }
        return PerAppSubtypeMemoryDecision(
            subtypeId = pruned[packageName!!.trim()],
            prunedRawJson = serialize(pruned),
        )
    }

    fun prune(rawJson: String, availableSubtypeIds: Set<Long>): String {
        return serialize(parse(rawJson).filterValues { it in availableSubtypeIds })
    }

    fun count(rawJson: String, availableSubtypeIds: Set<Long>? = null): Int {
        val parsed = parse(rawJson)
        return if (availableSubtypeIds == null) {
            parsed.size
        } else {
            parsed.count { it.value in availableSubtypeIds }
        }
    }
}

/**
 * Class which acts as a high level helper for the raw implementation of subtypes in the prefs. Additionally provides
 * helper methods for the in-keyboard language switch process.
 */
class SubtypeManager(
    context: Context,
    // SupervisorJob so one failed persist or switch cannot cancel the parent and
    // leave subtype switching dead for the rest of the process lifetime.
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
) {
    private val prefs by FlorisPreferenceStore
    private val keyboardManager by context.keyboardManager()

    /** Starts saves in the order they were asked for. See [launchSave]. */
    private val saveDispatcher = Dispatchers.Default.limitedParallelism(1)

    /** Held for the whole of one save, so the next can't start while the one before is still writing. */
    private val saveLock = Mutex()

    val subtypesFlow: StateFlow<List<Subtype>>
        field = MutableStateFlow(listOf())
    inline var subtypes
        get() = subtypesFlow.value
        private set(v) { subtypesFlow.value = v }

    val activeSubtypeFlow: StateFlow<Subtype>
        field = MutableStateFlow(Subtype.DEFAULT)
    inline var activeSubtype
        get() = activeSubtypeFlow.value
        private set(v) { activeSubtypeFlow.value = v }

    @Volatile
    private var activeEditorPackageName: String? = null

    /** The app whose field has focus, as given, so a hand-picked language holds until the app changes. */
    @Volatile
    private var focusedPackageName: String? = null

    /**
     * The language the user last switched to by hand since [focusedPackageName] got focus. It only
     * counts while it's the one showing, so removing it (or anything else moving off it) lets hints
     * work again.
     */
    @Volatile
    private var pickedByHandInFocusedApp: Long? = null

    /** The language showing came from a field's locale hint and wasn't saved as the user's own. */
    @Volatile
    private var activeFromHint = false

    /** A focus that came before the saved keyboards loaded, decided once they have. */
    private var pendingFocus: PendingFocus? = null

    /** The id last sent to be saved as the user's own, until every such save lands. See [userSubtypeId]. */
    private var savingUserSubtypeId: Long? = null

    /** Saves of the user's own id that haven't landed yet. */
    private var userSubtypeSavesInFlight = 0

    /**
     * Held while deciding and switching the active subtype, since focus changes come on the main
     * thread and list changes and manual switches on [scope].
     */
    private val switchLock = Any()

    init {
        prefs.localization.subtypes.asFlow().collectLatestIn(scope) { listRaw ->
            flogDebug { listRaw }
            val list = if (listRaw.isNotBlank()) {
                runCatching {
                    SubtypeJsonConfig.decodeFromString<List<Subtype>>(listRaw)
                }.getOrElse { cause ->
                    flogError { "SubtypeManager: failed to decode persisted subtypes: ${cause.message}" }
                    emptyList()
                }
            } else {
                emptyList()
            }
            subtypes = list
            evaluateActiveSubtype(list)
        }
    }

    private fun persistNewSubtypeList(list: List<Subtype>) = scope.launch {
        val listRaw = SubtypeJsonConfig.encodeToString(list)
        prefs.localization.subtypes.set(listRaw)
    }

    /**
     * Gets the active subtype and returns it. If the activeSubtypeId points to a non-existent
     * subtype, this method tries to determine a new active subtype.
     *
     * @return The active subtype or null, if the subtype list is empty or no new active subtype
     *  could be determined.
     */
    private fun evaluateActiveSubtype(list: List<Subtype>) = scope.launch {
        synchronized(switchLock) {
            // A hinted language isn't the saved one, and stays through list changes while it's still set up.
            val hinted = if (activeFromHint) list.find { it.id == activeSubtype.id } else null
            val activeSubtypeId = userSubtypeId()
            if (hinted != null) {
                activateSubtype(hinted, source = SubtypeSwitchSource.Hint, persist = false)
                // The user's own was removed meanwhile: the first one left becomes theirs, as it
                // would without the hint, so a field without one has something to go back to.
                if (list.none { it.id == activeSubtypeId }) saveUserSubtypeId(list.first().id)
            } else {
                val subtype = list.find { it.id == activeSubtypeId } ?: list.firstOrNull() ?: Subtype.DEFAULT
                activateSubtype(subtype, source = SubtypeSwitchSource.System, persist = subtype.id != activeSubtypeId)
            }
            val pending = pendingFocus
            if (pending != null && list.isNotEmpty()) {
                onEditorPackageFocus(pending.packageName, pending.hintLocales, pending.appLocales)
            }
        }
    }

    /**
     * Called when a field in [packageName] gets focus. Switches to the language remembered for the
     * app, or else to the one the field's [hintLocales] (then the app's own [appLocales]) ask for,
     * unless the user picked one by hand in this app. See [LocaleHints.resolve].
     */
    fun onEditorPackageFocus(
        packageName: String?,
        hintLocales: List<Locale> = emptyList(),
        appLocales: () -> List<Locale> = { emptyList() },
    ): Unit = synchronized(switchLock) {
        val focused = packageName?.trim()
        if (focused != focusedPackageName) {
            focusedPackageName = focused
            pickedByHandInFocusedApp = null
        }
        activeEditorPackageName = focused?.takeIf(PerAppSubtypeMemory::isRecordablePackageName)
        val subtypeList = subtypes
        if (subtypeList.isEmpty()) {
            // Right after the keyboard starts, before the saved keyboards load. Deciding now would
            // miss the field's language, and would drop every app's remembered one as gone.
            pendingFocus = PendingFocus(packageName, hintLocales, appLocales)
            return
        }
        pendingFocus = null
        var rememberedSubtypeId: Long? = null
        if (prefs.localization.rememberSubtypePerAppEnabled.get()) {
            val rawMemory = prefs.localization.perAppSubtypeMemory.get()
            val decision = PerAppSubtypeMemory.resolve(
                rawJson = rawMemory,
                packageName = activeEditorPackageName,
                availableSubtypeIds = subtypeList.map { it.id }.toSet(),
            )
            if (decision.prunedRawJson != rawMemory) launchSave { prunePerAppSubtypeMemory() }
            rememberedSubtypeId = decision.subtypeId
        }
        val resolution = LocaleHints.resolve(
            subtypes = subtypeList,
            activeSubtypeId = activeSubtype.id,
            userSubtypeId = userSubtypeId(),
            activeFromHint = activeFromHint,
            rememberedSubtypeId = rememberedSubtypeId,
            pickedByHand = pickedByHandInFocusedApp == activeSubtype.id,
            hintsEnabled = prefs.localization.followAppLanguageHints.get(),
            editorLocales = hintLocales,
            appLocales = appLocales,
        ) ?: return
        // A hinted language lasts while fields ask for it and never replaces the user's own.
        val persist = resolution.source != SubtypeSwitchSource.Hint &&
            resolution.subtype.id != userSubtypeId()
        activateSubtype(resolution.subtype, resolution.source, persist = persist)
    }

    private fun activateSubtype(
        subtype: Subtype,
        source: SubtypeSwitchSource,
        persist: Boolean = true,
    ): Unit = synchronized(switchLock) {
        activeSubtype = subtype
        activeFromHint = source == SubtypeSwitchSource.Hint
        if (source == SubtypeSwitchSource.Manual) pickedByHandInFocusedApp = subtype.id
        if (persist) saveUserSubtypeId(subtype.id)
        if (source == SubtypeSwitchSource.Manual) {
            // The app is taken now, since another may have focus by the time this save runs.
            val packageName = activeEditorPackageName
            launchSave { rememberManualSubtypeSwitch(subtype.id, packageName) }
        }
    }

    /**
     * The user's own language: the saved one, or the one being saved. Saving happens in the
     * background, and a decision made before it lands would otherwise see the old id.
     */
    private fun userSubtypeId(): Long = synchronized(switchLock) {
        savingUserSubtypeId ?: prefs.localization.activeSubtypeId.get()
    }

    private fun saveUserSubtypeId(id: Long): Unit = synchronized(switchLock) {
        savingUserSubtypeId = id
        userSubtypeSavesInFlight++
        // Counted rather than compared, so the first of three quick saves going X, Y, X can't clear it
        // while the later ones still wait. This also runs for a save stopped before it starts.
        launchSave { prefs.localization.activeSubtypeId.set(id) }.invokeOnCompletion {
            synchronized(switchLock) { if (--userSubtypeSavesInFlight == 0) savingUserSubtypeId = null }
        }
    }

    /**
     * Runs [save] once every save asked for before it has finished, so an older one can't land last
     * and two can't read and write the per-app memory over each other.
     */
    private fun launchSave(save: suspend () -> Unit) = scope.launch(saveDispatcher) {
        saveLock.withLock { save() }
    }

    /** Drops remembered languages that are no longer set up, from the memory as it is by then. */
    private suspend fun prunePerAppSubtypeMemory() {
        val subtypeIds = subtypes.map { it.id }.toSet()
        if (subtypeIds.isEmpty()) return
        val rawMemory = prefs.localization.perAppSubtypeMemory.get()
        val pruned = PerAppSubtypeMemory.prune(rawMemory, subtypeIds)
        if (pruned != rawMemory) prefs.localization.perAppSubtypeMemory.set(pruned)
    }

    /** Forgets the language remembered for every app, after any save of it already asked for. */
    fun clearPerAppSubtypeMemory() = launchSave {
        prefs.localization.perAppSubtypeMemory.set(PerAppSubtypeMemory.EmptyJson)
    }

    private suspend fun rememberManualSubtypeSwitch(subtypeId: Long, packageName: String?) {
        if (!prefs.localization.rememberSubtypePerAppEnabled.get()) return
        if (packageName == null) return
        val subtypeIds = subtypes.map { it.id }.toSet()
        val rawMemory = prefs.localization.perAppSubtypeMemory.get()
        val nextMemory = PerAppSubtypeMemory.remember(
            rawJson = rawMemory,
            packageName = packageName,
            subtypeId = subtypeId,
            availableSubtypeIds = subtypeIds,
        )
        if (nextMemory != rawMemory) {
            prefs.localization.perAppSubtypeMemory.set(nextMemory)
        }
    }

    /**
     * Adds a given [subtype] to the subtype list, if it does not exist.
     *
     * @param subtype The subtype which should be added.
     * @return True if the subtype was added, false otherwise. A return value of false indicates
     *  that the subtype already exists.
     */
    fun addSubtype(subtype: Subtype): Boolean {
        val subtypeToAdd = subtype.copy(id = System.currentTimeMillis())
        val subtypeList = subtypes
        if (subtypeList.find { it.equalsExcludingId(subtype) } != null) {
            return false
        }
        val newSubtypeList = subtypeList + subtypeToAdd
        persistNewSubtypeList(newSubtypeList)
        return true
    }

    /**
     * Gets the currency set from the given subtype and returns it. Falls back to a default one if the subtype does not
     * exist.
     *
     * @return The currency set or a fallback.
     */
    fun getCurrencySet(subtypeToSearch: Subtype): CurrencySet {
        return keyboardManager.resources.currencySets.value[subtypeToSearch.currencySet] ?: CurrencySet.Fallback
    }

    /**
     * Gets a subtype by the given [id].
     *
     * @param id The id of the subtype you want to get.
     * @return The subtype or null, if no matching subtype could be found.
     */
    fun getSubtypeById(id: Long): Subtype? {
        val subtypeList = subtypes
        return subtypeList.find { it.id == id }
    }

    /**
     * Gets the default system subtype for a given [locale].
     *
     * @param locale The locale of the default system subtype to get.
     * @return The default system locale or null, if no matching default system subtype could be
     *  found.
     */
    fun getSubtypePresetForLocale(locale: FlorisLocale): SubtypePreset? {
        val presets = keyboardManager.resources.subtypePresets.value
        return presets.find { it.locale == locale } ?: presets.find { it.locale.language == locale.language }
    }

    /**
     * Modifies an existing subtype with the newly provided details. In order to determine which
     * subtype should be updated, the id must be the same.
     *
     * @param subtypeToModify The subtype with the new details but same id.
     */
    fun modifySubtypeWithSameId(subtypeToModify: Subtype) {
        val subtypeList = subtypes
        val index = subtypeList.indexOfFirst { subtypeToModify.id == it.id }
        if (index >= 0 && index < subtypeList.size) {
            val newSubtypeList = subtypeList.mapIndexed { n, subtype ->
                if (n == index) {
                    subtypeToModify
                } else {
                    subtype
                }
            }
            persistNewSubtypeList(newSubtypeList)
        }
    }

    /**
     * Switches only the active subtype's character layout while preserving locale, NLP,
     * punctuation, and all non-character layout mappings.
     */
    fun switchActiveSubtypeCharactersLayout(layoutId: String): Boolean = synchronized(switchLock) {
        val subtypeList = subtypes
        val cachedActiveSubtype = activeSubtype
        val index = subtypeList.indexOfFirst { cachedActiveSubtype.id == it.id }
        if (index !in subtypeList.indices) {
            return false
        }

        val updatedLayoutMap = cachedActiveSubtype.layoutMap.copy(
            layoutType = LayoutType.CHARACTERS,
            componentName = extCoreLayout(layoutId),
        ) ?: return false
        val updatedSubtype = cachedActiveSubtype.copy(layoutMap = updatedLayoutMap)
        val newSubtypeList = subtypeList.mapIndexed { n, subtype ->
            if (n == index) {
                updatedSubtype
            } else {
                subtype
            }
        }

        subtypes = newSubtypeList
        activeSubtype = updatedSubtype
        persistNewSubtypeList(newSubtypeList)
        return true
    }

    /**
     * Removes a given [subtypeToRemove]. Nothing happens if the given [subtypeToRemove] does not
     * exist.
     *
     * @param subtypeToRemove The subtype which should be removed.
     */
    fun removeSubtype(subtypeToRemove: Subtype) {
        val subtypeList = subtypes
        val indexToRemove = subtypeList.indexOf(subtypeToRemove)
        if (indexToRemove in subtypeList.indices) {
            val newSubtypeList = subtypeList.mapIndexedNotNull { n, subtype ->
                if (n != indexToRemove) {
                    subtype
                } else {
                    null
                }
            }
            persistNewSubtypeList(newSubtypeList)
            evaluateActiveSubtype(newSubtypeList)
        }
    }

    /**
     * Switch to the previous subtype in the subtype list if possible.
     */
    fun switchToPrevSubtype() = scope.launch {
        synchronized(switchLock) {
            val subtypeList = subtypes
            if (subtypeList.isEmpty()) return@launch
            val currentIndex = subtypeList.indexOf(activeSubtype)
            // If the active subtype is no longer in the list (e.g. it was just removed),
            // fall back to the last real subtype rather than the sentinel Subtype.DEFAULT,
            // which is not a selectable subtype.
            val newActiveSubtype = if (currentIndex < 0) {
                subtypeList.last()
            } else {
                subtypeList[(currentIndex - 1 + subtypeList.size) % subtypeList.size]
            }
            activateSubtype(newActiveSubtype, source = SubtypeSwitchSource.Manual)
        }
    }

    /**
     * Switch to the next subtype in the subtype list if possible.
     */
    fun switchToNextSubtype() = scope.launch {
        synchronized(switchLock) {
            val subtypeList = subtypes
            if (subtypeList.isEmpty()) return@launch
            val currentIndex = subtypeList.indexOf(activeSubtype)
            // See [switchToPrevSubtype]: a missing active subtype falls back to the first
            // real subtype instead of the sentinel Subtype.DEFAULT.
            val newActiveSubtype = if (currentIndex < 0) {
                subtypeList.first()
            } else {
                subtypeList[(currentIndex + 1) % subtypeList.size]
            }
            activateSubtype(newActiveSubtype, source = SubtypeSwitchSource.Manual)
        }
    }

    fun switchToSubtypeById(id: Long) = scope.launch {
        val subtype = getSubtypeById(id) ?: return@launch
        activateSubtype(subtype, source = SubtypeSwitchSource.Manual)
    }

    internal enum class SubtypeSwitchSource {
        Manual,
        Restore,
        System,
        /** A field's or app's locale hint; not saved as the user's own language. */
        Hint,
    }

    private class PendingFocus(
        val packageName: String?,
        val hintLocales: List<Locale>,
        val appLocales: () -> List<Locale>,
    )
}

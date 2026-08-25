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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.florisboard.lib.kotlin.collectLatestIn

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
class SubtypeManager(context: Context) {
    private val prefs by FlorisPreferenceStore
    private val keyboardManager by context.keyboardManager()
    // SupervisorJob so one failed persist or switch cannot cancel the parent and
    // leave subtype switching dead for the rest of the process lifetime.
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

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
        val activeSubtypeId = prefs.localization.activeSubtypeId.get()
        val subtype = list.find { it.id == activeSubtypeId } ?: list.firstOrNull() ?: Subtype.DEFAULT
        activateSubtype(subtype, source = SubtypeSwitchSource.System, persist = subtype.id != activeSubtypeId)
    }

    fun onEditorPackageFocus(packageName: String?) {
        activeEditorPackageName = packageName?.trim()?.takeIf(PerAppSubtypeMemory::isRecordablePackageName)
        if (!prefs.localization.rememberSubtypePerAppEnabled.get()) return
        val subtypeIds = subtypes.map { it.id }.toSet()
        val rawMemory = prefs.localization.perAppSubtypeMemory.get()
        val decision = PerAppSubtypeMemory.resolve(
            rawJson = rawMemory,
            packageName = activeEditorPackageName,
            availableSubtypeIds = subtypeIds,
        )
        if (decision.prunedRawJson != rawMemory) {
            scope.launch { prefs.localization.perAppSubtypeMemory.set(decision.prunedRawJson) }
        }
        val rememberedSubtype = decision.subtypeId?.let(::getSubtypeById) ?: return
        if (rememberedSubtype.id != activeSubtype.id) {
            activateSubtype(rememberedSubtype, source = SubtypeSwitchSource.Restore)
        }
    }

    private fun activateSubtype(
        subtype: Subtype,
        source: SubtypeSwitchSource,
        persist: Boolean = true,
    ) {
        activeSubtype = subtype
        scope.launch {
            if (persist) {
                prefs.localization.activeSubtypeId.set(subtype.id)
            }
            if (source == SubtypeSwitchSource.Manual) {
                rememberManualSubtypeSwitch(subtype.id)
            }
        }
    }

    private suspend fun rememberManualSubtypeSwitch(subtypeId: Long) {
        if (!prefs.localization.rememberSubtypePerAppEnabled.get()) return
        val packageName = activeEditorPackageName ?: return
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
    fun switchActiveSubtypeCharactersLayout(layoutId: String): Boolean {
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

    /**
     * Switch to the next subtype in the subtype list if possible.
     */
    fun switchToNextSubtype() = scope.launch {
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

    fun switchToSubtypeById(id: Long) = scope.launch {
        val subtype = getSubtypeById(id) ?: return@launch
        activateSubtype(subtype, source = SubtypeSwitchSource.Manual)
    }

    private enum class SubtypeSwitchSource {
        Manual,
        Restore,
        System,
    }
}

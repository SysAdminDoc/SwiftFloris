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

package dev.patrickgold.florisboard.ime.profile

import dev.patrickgold.florisboard.ime.keyboard.IncognitoMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class PerAppThemeOverride {
    FOLLOW_GLOBAL,
    ADAPTIVE_ACCENT,
    GLOBAL_ACCENT,
}

enum class PerAppBooleanOverride {
    FOLLOW_GLOBAL,
    FORCE_OFF,
    FORCE_ON,
}

enum class PerAppSuggestionAggressiveness {
    FOLLOW_GLOBAL,
    OFF,
    CONSERVATIVE,
    BALANCED,
    AGGRESSIVE,
}

enum class PerAppGestureSet {
    FOLLOW_GLOBAL,
    DEFAULT,
    CHAT,
    CODE,
    READING,
}

@Serializable
data class PerAppKeyboardProfile(
    val packageName: String,
    val label: String = packageName,
    val theme: PerAppThemeOverride = PerAppThemeOverride.FOLLOW_GLOBAL,
    val incognito: PerAppBooleanOverride = PerAppBooleanOverride.FOLLOW_GLOBAL,
    val clipboardHistory: PerAppBooleanOverride = PerAppBooleanOverride.FOLLOW_GLOBAL,
    val suggestions: PerAppSuggestionAggressiveness = PerAppSuggestionAggressiveness.FOLLOW_GLOBAL,
    val gestureSet: PerAppGestureSet = PerAppGestureSet.FOLLOW_GLOBAL,
)

data class ResolvedPerAppKeyboardProfile(
    val packageName: String,
    val label: String,
    val theme: PerAppThemeOverride,
    val incognito: PerAppBooleanOverride,
    val clipboardHistory: PerAppBooleanOverride,
    val suggestions: PerAppSuggestionAggressiveness,
    val gestureSet: PerAppGestureSet,
)

object PerAppKeyboardProfiles {
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

    fun parse(rawJson: String): Map<String, PerAppKeyboardProfile> {
        if (rawJson.isBlank()) return emptyMap()
        return runCatching {
            JsonConfig.decodeFromString<Map<String, PerAppKeyboardProfile>>(rawJson)
        }.recoverCatching {
            JsonConfig.decodeFromString<List<PerAppKeyboardProfile>>(rawJson)
                .associateBy { it.packageName }
        }.getOrElse {
            emptyMap()
        }.mapNotNull { (key, profile) ->
            val normalizedPackage = profile.packageName.trim().ifBlank { key.trim() }
            if (isRecordablePackageName(normalizedPackage)) {
                normalizedPackage to profile.copy(
                    packageName = normalizedPackage,
                    label = profile.label.ifBlank { normalizedPackage },
                )
            } else {
                null
            }
        }.toMap()
    }

    fun serialize(profiles: Map<String, PerAppKeyboardProfile>): String {
        val normalized = profiles
            .mapNotNull { (key, profile) ->
                val normalizedPackage = profile.packageName.trim().ifBlank { key.trim() }
                if (isRecordablePackageName(normalizedPackage)) {
                    normalizedPackage to profile.copy(
                        packageName = normalizedPackage,
                        label = profile.label.ifBlank { normalizedPackage },
                    )
                } else {
                    null
                }
            }
            .toMap()
        if (normalized.isEmpty()) return EmptyJson
        return JsonConfig.encodeToString<Map<String, PerAppKeyboardProfile>>(
            normalized.toSortedMap().entries.associate { it.key to it.value },
        )
    }

    fun upsert(rawJson: String, profile: PerAppKeyboardProfile): String {
        val packageName = profile.packageName.trim()
        if (!isRecordablePackageName(packageName)) return serialize(parse(rawJson))
        return serialize(parse(rawJson) + (packageName to profile.copy(packageName = packageName)))
    }

    fun remove(rawJson: String, packageName: String?): String {
        if (!isRecordablePackageName(packageName)) return serialize(parse(rawJson))
        return serialize(parse(rawJson) - packageName!!.trim())
    }

    fun resolve(rawJson: String, packageName: String?): ResolvedPerAppKeyboardProfile? {
        if (!isRecordablePackageName(packageName)) return null
        val profile = parse(rawJson)[packageName!!.trim()] ?: return null
        return ResolvedPerAppKeyboardProfile(
            packageName = profile.packageName,
            label = profile.label,
            theme = profile.theme,
            incognito = profile.incognito,
            clipboardHistory = profile.clipboardHistory,
            suggestions = profile.suggestions,
            gestureSet = profile.gestureSet,
        )
    }

    fun count(rawJson: String): Int = parse(rawJson).size
}

object PerAppKeyboardProfilePolicy {
    fun resolveIncognitoMode(
        appDeclaredNoPersonalizedLearning: Boolean,
        globalPreference: IncognitoMode,
        isDynamicIncognitoForced: Boolean,
        override: PerAppBooleanOverride,
    ): Boolean {
        if (appDeclaredNoPersonalizedLearning) return true
        return when (override) {
            PerAppBooleanOverride.FORCE_OFF -> false
            PerAppBooleanOverride.FORCE_ON -> true
            PerAppBooleanOverride.FOLLOW_GLOBAL -> when (globalPreference) {
                IncognitoMode.FORCE_OFF -> false
                IncognitoMode.FORCE_ON -> true
                IncognitoMode.DYNAMIC_ON_OFF -> isDynamicIncognitoForced
            }
        }
    }

    fun shouldEnableComposing(
        baseEnabled: Boolean,
        suggestions: PerAppSuggestionAggressiveness,
    ): Boolean {
        return baseEnabled && suggestions != PerAppSuggestionAggressiveness.OFF
    }

    fun shouldSuppressClipboardHistory(override: PerAppBooleanOverride): Boolean {
        return override == PerAppBooleanOverride.FORCE_OFF
    }

    fun hasExplicitOverrides(profile: ResolvedPerAppKeyboardProfile): Boolean {
        return profile.theme != PerAppThemeOverride.FOLLOW_GLOBAL ||
            profile.incognito != PerAppBooleanOverride.FOLLOW_GLOBAL ||
            profile.clipboardHistory != PerAppBooleanOverride.FOLLOW_GLOBAL ||
            profile.suggestions != PerAppSuggestionAggressiveness.FOLLOW_GLOBAL ||
            profile.gestureSet != PerAppGestureSet.FOLLOW_GLOBAL
    }
}

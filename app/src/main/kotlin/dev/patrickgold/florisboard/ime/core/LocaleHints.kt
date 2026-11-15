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

package dev.patrickgold.florisboard.ime.core

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import dev.patrickgold.florisboard.ime.core.SubtypeManager.SubtypeSwitchSource
import dev.patrickgold.florisboard.lib.devtools.flogDebug
import java.util.Locale

/**
 * Picks the keyboard language a focused field asks for, from the keyboards already set up: the
 * field's hint locales first, then the app's own language on Android 14 and newer. Nothing is
 * downloaded or added, and a hint that fits none, or more than one, of them counts as no hint.
 */
internal object LocaleHints {
    /**
     * What a newly focused field should switch to, or null to stay on [activeSubtypeId].
     *
     * A language the user picked by hand in this app since it came up ([pickedByHand]) comes first,
     * then a language remembered for the app ([rememberedSubtypeId]). Only then, and only when
     * [hintsEnabled], do the field's [editorLocales] count, and [appLocales] only when none of
     * those fits any keyboard. A field with no usable hint goes back to the user's own language
     * ([userSubtypeId]) if the one showing came from an earlier field's hint ([activeFromHint]).
     */
    fun resolve(
        subtypes: List<Subtype>,
        activeSubtypeId: Long,
        userSubtypeId: Long,
        activeFromHint: Boolean,
        rememberedSubtypeId: Long?,
        pickedByHand: Boolean,
        hintsEnabled: Boolean,
        editorLocales: List<Locale>,
        appLocales: () -> List<Locale>,
    ): Resolution? {
        fun switchTo(subtype: Subtype?, source: SubtypeSwitchSource): Resolution? {
            if (subtype == null) return null
            // The one showing already only needs a switch when a hint brought it and it now counts as chosen.
            if (subtype.id == activeSubtypeId && !(activeFromHint && source != SubtypeSwitchSource.Hint)) return null
            return Resolution(subtype, source)
        }

        // A pick by hand is never older than the app's remembered language, which is saved after it
        // and may not have caught up yet.
        if (pickedByHand) return null
        if (rememberedSubtypeId != null) {
            return switchTo(subtypes.find { it.id == rememberedSubtypeId }, SubtypeSwitchSource.Restore)
        }
        if (hintsEnabled) {
            val match = candidates(subtypes, editorLocales) ?: candidates(subtypes, appLocales())
            if (match != null) {
                // Already on a keyboard that fits: leave it alone.
                if (match.fitting.any { it.id == activeSubtypeId }) return null
                val own = match.pickable.find { it.id == userSubtypeId }
                if (own != null) return switchTo(own, SubtypeSwitchSource.System)
                // More than one fits and none is the user's own: don't guess, as if there were no hint.
                match.pickable.singleOrNull()?.let { return switchTo(it, SubtypeSwitchSource.Hint) }
            }
        }
        if (!activeFromHint) return null
        return switchTo(subtypes.find { it.id == userSubtypeId }, SubtypeSwitchSource.System)
    }

    data class Resolution(val subtype: Subtype, val source: SubtypeSwitchSource)

    /** The keyboards that fit what was asked for, and the ones of those a hint may switch to. */
    data class Match(val fitting: List<Subtype>, val pickable: List<Subtype>)

    /**
     * The keyboards the first fitting locale in [locales] matches, or null when none fits any.
     * A locale naming a country prefers keyboards for that country, and falls back to every
     * keyboard in its language when there's none for it. A locale naming a script can't be told
     * apart, since a keyboard's locale never says which script it types: every keyboard in its
     * language fits, so one already showing stays, but none may be picked, and nothing further
     * down wins either.
     */
    fun candidates(subtypes: List<Subtype>, locales: List<Locale>): Match? {
        for (locale in locales) {
            val language = normalizedLanguage(locale.language)
            if (language.isEmpty() || language == "und") continue
            val sameLanguage = subtypes.filter { normalizedLanguage(it.primaryLocale.language) == language }
            if (sameLanguage.isEmpty()) continue
            if (locale.script.isNotEmpty()) return Match(fitting = sameLanguage, pickable = emptyList())
            if (locale.country.isEmpty()) return Match(sameLanguage, sameLanguage)
            val country = normalizedCountry(locale.country)
            val sameCountry = sameLanguage.filter { normalizedCountry(it.primaryLocale.country) == country }
            val best = sameCountry.ifEmpty { sameLanguage }
            return Match(best, best)
        }
        return null
    }

    /** The bundled British English keyboard is tagged en-UK, while apps say en-GB. */
    private fun normalizedCountry(country: String): String = when (val upper = country.uppercase(Locale.ROOT)) {
        "UK" -> "GB"
        else -> upper
    }

    /** Java still hands out the old codes for Hebrew, Indonesian and Yiddish on some versions. */
    internal fun normalizedLanguage(language: String): String = when (val lower = language.lowercase(Locale.ROOT)) {
        "iw" -> "he"
        "in" -> "id"
        "ji" -> "yi"
        else -> lower
    }

    fun LocaleList?.toLocales(): List<Locale> {
        if (this == null) return emptyList()
        return List(size()) { get(it) }
    }

    /**
     * The UI languages the app with [packageName] picked for itself, which the current input
     * method may read on Android 14 and newer (Android 13 wants a permission third-party apps can't
     * hold, so it isn't asked). Empty when it didn't pick any, or when reading
     * them isn't allowed.
     */
    fun applicationLocales(context: Context, packageName: String?): List<Locale> {
        if (packageName == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return emptyList()
        return try {
            context.getSystemService(LocaleManager::class.java)?.getApplicationLocales(packageName).toLocales()
        } catch (e: SecurityException) {
            // Only the current input method may ask, and the keyboard may have just stopped being it.
            flogDebug { "LocaleHints: app locales for $packageName not readable: ${e.message}" }
            emptyList()
        } catch (e: IllegalArgumentException) {
            flogDebug { "LocaleHints: app locales for $packageName not found: ${e.message}" }
            emptyList()
        }
    }
}

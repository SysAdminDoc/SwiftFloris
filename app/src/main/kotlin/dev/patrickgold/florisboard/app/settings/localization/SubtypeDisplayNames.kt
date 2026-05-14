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

package dev.patrickgold.florisboard.app.settings.localization

import dev.patrickgold.florisboard.ime.core.DisplayLanguageNamesIn
import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.core.SubtypePreset
import dev.patrickgold.florisboard.lib.FlorisLocale

internal fun FlorisLocale.displayName(displayLanguageNamesIn: DisplayLanguageNamesIn): String {
    return when (displayLanguageNamesIn) {
        DisplayLanguageNamesIn.SYSTEM_LOCALE -> displayName()
        DisplayLanguageNamesIn.NATIVE_LOCALE -> displayName(this)
    }
}

internal fun Subtype.displayName(displayLanguageNamesIn: DisplayLanguageNamesIn): String {
    return locales().joinToString(separator = " + ") { locale ->
        locale.displayName(displayLanguageNamesIn)
    }
}

internal fun SubtypePreset.displayName(displayLanguageNamesIn: DisplayLanguageNamesIn): String {
    return (listOf(locale) + secondaryLocales).joinToString(separator = " + ") { locale ->
        locale.displayName(displayLanguageNamesIn)
    }
}

internal fun List<FlorisLocale>.displayName(displayLanguageNamesIn: DisplayLanguageNamesIn): String {
    return joinToString(separator = " + ") { locale ->
        locale.displayName(displayLanguageNamesIn)
    }
}


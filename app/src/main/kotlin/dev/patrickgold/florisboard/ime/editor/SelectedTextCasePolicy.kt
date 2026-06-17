/*
 * Copyright (C) 2026 SwiftFloris Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.ime.editor

import java.util.Locale

internal object SelectedTextCasePolicy {

    enum class CaseMode { LOWER, TITLE, UPPER }

    fun detectCase(text: String, locale: Locale): CaseMode {
        if (text.isEmpty()) return CaseMode.LOWER
        val lower = text.lowercase(locale)
        val upper = text.uppercase(locale)
        return when (text) {
            upper -> CaseMode.UPPER
            titleCase(lower, locale) -> CaseMode.TITLE
            else -> CaseMode.LOWER
        }
    }

    fun nextCase(current: CaseMode): CaseMode = when (current) {
        CaseMode.LOWER -> CaseMode.TITLE
        CaseMode.TITLE -> CaseMode.UPPER
        CaseMode.UPPER -> CaseMode.LOWER
    }

    fun applyCase(text: String, mode: CaseMode, locale: Locale): String = when (mode) {
        CaseMode.LOWER -> text.lowercase(locale)
        CaseMode.UPPER -> text.uppercase(locale)
        CaseMode.TITLE -> titleCase(text, locale)
    }

    private fun titleCase(text: String, locale: Locale): String {
        if (text.isEmpty()) return text
        val lower = text.lowercase(locale)
        val sb = StringBuilder(lower.length)
        var capitalizeNext = true
        for (ch in lower) {
            if (Character.isWhitespace(ch) || ch == '-' || ch == '\'') {
                sb.append(ch)
                capitalizeNext = true
            } else if (capitalizeNext) {
                sb.append(ch.uppercaseChar())
                capitalizeNext = false
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }
}

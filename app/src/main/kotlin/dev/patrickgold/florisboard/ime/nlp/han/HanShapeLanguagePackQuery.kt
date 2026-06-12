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

package dev.patrickgold.florisboard.ime.nlp.han

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import dev.patrickgold.florisboard.ime.nlp.SuggestionProvider
import dev.patrickgold.florisboard.ime.nlp.WordSuggestionCandidate

internal data class HanShapeTableRow(
    val code: String,
    val text: String,
    val weight: Double,
)

internal object HanShapeLanguagePackQuery {
    fun suggestions(
        database: SQLiteDatabase,
        table: String,
        composingText: String,
        maxCandidateCount: Int,
        sourceProvider: SuggestionProvider?,
    ): List<WordSuggestionCandidate> {
        if (maxCandidateCount <= 0 || composingText.isBlank()) return emptyList()

        val rows = rowsByCodePrefix(
            database = database,
            table = table,
            codePrefix = composingText,
            limit = maxCandidateCount,
        )
        val maxWeight = rows.maxOfOrNull { it.weight }?.takeIf { it > 0.0 } ?: 1.0
        return rows.mapIndexed { index, row ->
            WordSuggestionCandidate(
                text = row.text,
                secondaryText = row.code,
                confidence = (row.weight / maxWeight).coerceIn(0.0, 1.0),
                isEligibleForAutoCommit = index == 0,
                sourceProvider = sourceProvider,
            )
        }
    }

    fun words(database: SQLiteDatabase, table: String): List<String> {
        if (!isSafeTableName(table)) return emptyList()
        return runCatching {
            database.query(
                table,
                arrayOf(TextColumn),
                null,
                null,
                null,
                null,
                "$WeightColumn DESC, $CodeColumn ASC",
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(cursor.getString(0))
                    }
                }.distinct()
            }
        }.getOrElse { error ->
            if (error is SQLiteException || error is IllegalArgumentException || error is IllegalStateException) {
                emptyList()
            } else {
                throw error
            }
        }
    }

    fun frequencyForWord(database: SQLiteDatabase, table: String, word: String): Double {
        if (!isSafeTableName(table) || word.isBlank()) return 0.0
        return runCatching {
            database.query(
                table,
                arrayOf(WeightColumn),
                "$TextColumn = ?",
                arrayOf(word),
                null,
                null,
                "$WeightColumn DESC",
                "1",
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.getDouble(0).coerceAtLeast(0.0) else 0.0
            }
        }.getOrElse { error ->
            if (error is SQLiteException || error is IllegalArgumentException || error is IllegalStateException) {
                0.0
            } else {
                throw error
            }
        }
    }

    fun containsWord(database: SQLiteDatabase, table: String, word: String): Boolean {
        return frequencyForWord(database, table, word) > 0.0
    }

    private fun rowsByCodePrefix(
        database: SQLiteDatabase,
        table: String,
        codePrefix: String,
        limit: Int,
    ): List<HanShapeTableRow> {
        if (!isSafeTableName(table)) return emptyList()
        return runCatching {
            database.query(
                table,
                arrayOf(CodeColumn, TextColumn, WeightColumn),
                "$CodeColumn LIKE ? || '%'",
                arrayOf(codePrefix),
                null,
                null,
                "$CodeColumn ASC, $WeightColumn DESC",
                limit.toString(),
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            HanShapeTableRow(
                                code = cursor.getString(0),
                                text = cursor.getString(1),
                                weight = cursor.getDouble(2).coerceAtLeast(0.0),
                            )
                        )
                    }
                }
            }
        }.getOrElse { error ->
            if (error is SQLiteException || error is IllegalArgumentException || error is IllegalStateException) {
                emptyList()
            } else {
                throw error
            }
        }
    }

    private fun isSafeTableName(table: String): Boolean {
        return table.isNotBlank() && table.all { it.isLetterOrDigit() || it == '_' }
    }

    private const val CodeColumn = "code"
    private const val TextColumn = "text"
    private const val WeightColumn = "weight"
}

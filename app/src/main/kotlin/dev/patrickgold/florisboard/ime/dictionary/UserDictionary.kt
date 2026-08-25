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

package dev.patrickgold.florisboard.ime.dictionary

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.UserDictionary
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.lib.FlorisLocale
import dev.patrickgold.florisboard.lib.ValidationRule
import org.florisboard.lib.android.copyToLimited
import org.florisboard.lib.android.writeText
import org.florisboard.lib.kotlin.tryOrNull
import java.io.ByteArrayOutputStream
import java.lang.ref.WeakReference

private const val WORDS_TABLE = "words"

const val FREQUENCY_MIN = 1
const val FREQUENCY_MAX = 255
const val FREQUENCY_DEFAULT = 128

private const val SORT_BY_WORD_ASC = "${UserDictionary.Words.WORD} ASC"
private const val SORT_BY_WORD_DESC = "${UserDictionary.Words.WORD} DESC"
private const val SORT_BY_FREQ_ASC = "${UserDictionary.Words.FREQUENCY} ASC"
private const val SORT_BY_FREQ_DESC = "${UserDictionary.Words.FREQUENCY} DESC"

private val PROJECTIONS: Array<String> = arrayOf(
    UserDictionary.Words._ID,
    UserDictionary.Words.WORD,
    UserDictionary.Words.FREQUENCY,
    UserDictionary.Words.LOCALE,
    UserDictionary.Words.SHORTCUT,
)

private val PROJECTIONS_LANGUAGE: Array<String> = arrayOf(
    UserDictionary.Words.LOCALE,
)

@Entity(tableName = WORDS_TABLE)
data class UserDictionaryEntry(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = UserDictionary.Words._ID, index = true)
    val id: Long,
    @ColumnInfo(name = UserDictionary.Words.WORD)
    val word: String,
    @ColumnInfo(name = UserDictionary.Words.FREQUENCY)
    val freq: Int,
    @ColumnInfo(name = UserDictionary.Words.LOCALE)
    val locale: String?,
    @ColumnInfo(name = UserDictionary.Words.SHORTCUT)
    val shortcut: String?,
)

interface UserDictionaryReadDao {
    companion object {
        private const val SELECT_ALL_FROM_WORDS =
            "SELECT * FROM $WORDS_TABLE"
        private const val LOCALE_MATCHES =
            "(${UserDictionary.Words.LOCALE} = :locale OR ${UserDictionary.Words.LOCALE} IS NULL)"
    }

    @Query("$SELECT_ALL_FROM_WORDS WHERE ${UserDictionary.Words.WORD} LIKE '%' || :word || '%'")
    fun query(word: String): List<UserDictionaryEntry>

    @Query("$SELECT_ALL_FROM_WORDS WHERE ${UserDictionary.Words.WORD} LIKE '%' || :word || '%' AND $LOCALE_MATCHES")
    fun query(word: String, locale: FlorisLocale?): List<UserDictionaryEntry>

    @Query("$SELECT_ALL_FROM_WORDS WHERE ${UserDictionary.Words.SHORTCUT} = :shortcut")
    fun queryShortcut(shortcut: String): List<UserDictionaryEntry>

    @Query("$SELECT_ALL_FROM_WORDS WHERE ${UserDictionary.Words.SHORTCUT} = :shortcut AND $LOCALE_MATCHES")
    fun queryShortcut(shortcut: String, locale: FlorisLocale?): List<UserDictionaryEntry>

    @Query(SELECT_ALL_FROM_WORDS)
    fun queryAll(): List<UserDictionaryEntry>

    @Transaction
    @Query(SELECT_ALL_FROM_WORDS)
    fun queryAllReadOnlyTransaction(): List<UserDictionaryEntry>

    @Query("$SELECT_ALL_FROM_WORDS WHERE (${UserDictionary.Words.LOCALE} = :locale AND :locale IS NOT NULL) OR (${UserDictionary.Words.LOCALE} IS NULL AND :locale IS NULL)")
    fun queryAll(locale: FlorisLocale?): List<UserDictionaryEntry>

    @Query("$SELECT_ALL_FROM_WORDS WHERE ${UserDictionary.Words.WORD} = :word")
    fun queryExact(word: String): List<UserDictionaryEntry>

    @Query("$SELECT_ALL_FROM_WORDS WHERE ${UserDictionary.Words.WORD} = :word AND (${UserDictionary.Words.LOCALE} = :locale OR (${UserDictionary.Words.LOCALE} IS NULL AND :locale IS NULL))")
    fun queryExact(word: String, locale: FlorisLocale?): List<UserDictionaryEntry>

    @Query("$SELECT_ALL_FROM_WORDS WHERE ${UserDictionary.Words.WORD} = :word AND $LOCALE_MATCHES")
    fun queryExactFuzzyLocale(word: String, locale: FlorisLocale?): List<UserDictionaryEntry>

    @Query("SELECT DISTINCT COALESCE(${UserDictionary.Words.LOCALE}, '') FROM $WORDS_TABLE")
    fun queryLanguageTagList(): List<String>

    fun queryLanguageList(): List<FlorisLocale?> {
        return queryLanguageTagList().map { languageTag ->
            languageTag.takeIf { it.isNotEmpty() }?.let { FlorisLocale.fromTag(it) }
        }
    }
}

@Dao
interface UserDictionaryDao : UserDictionaryReadDao {
    @Insert
    fun insert(entry: UserDictionaryEntry)

    @Update
    fun update(entry: UserDictionaryEntry)

    @Delete
    fun delete(entry: UserDictionaryEntry)

    @Query("DELETE FROM $WORDS_TABLE")
    fun deleteAll()
}

interface UserDictionaryDatabase {
    fun userDictionaryDao(): UserDictionaryReadDao

    fun mutableUserDictionaryDao(): UserDictionaryDao? {
        return userDictionaryDao() as? UserDictionaryDao
    }

    fun reset()

    fun importCombinedList(context: Context, uri: Uri) {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: error("Could not open import source for reading.")
        val text = inputStream.use { input ->
            val out = ByteArrayOutputStream()
            try {
                input.copyToLimited(out, DictionaryImporter.MAX_IMPORT_FILE_BYTES)
            } catch (error: IllegalStateException) {
                if (error.message?.contains("maximum size") == true) {
                    throw DictionaryImportException(
                        "Legacy combined-list import exceeds the " +
                            "${DictionaryImporter.MAX_IMPORT_FILE_BYTES / (1024 * 1024)} MiB safety limit.",
                        isSafetyLimit = true,
                    )
                }
                throw error
            }
            String(out.toByteArray(), Charsets.UTF_8)
        }
        importCombinedListEntries(UserDictionaryCombinedListCodec.decode(text))
    }

    fun exportCombinedList(context: Context, uri: Uri) {
        context.contentResolver.writeText(uri) { dst ->
            dst.write(exportCombinedListText(context, uri))
        }
    }

    fun importCombinedListText(text: String) {
        importCombinedListEntries(UserDictionaryCombinedListCodec.decode(text))
    }

    fun exportCombinedListText(
        context: Context,
        uri: Uri,
        timestampMillis: Long = System.currentTimeMillis(),
    ): String {
        return UserDictionaryCombinedListCodec.encode(
            entries = userDictionaryDao().queryAll(),
            dictionaryName = uri.lastPathSegment,
            generatedBy = context.packageName,
            timestampMillis = timestampMillis,
        )
    }

    fun exportEncryptedCombinedList(
        context: Context,
        uri: Uri,
        passphrase: CharArray,
    ) {
        val plaintext = exportCombinedListText(context, uri).toByteArray(Charsets.UTF_8)
        val envelope = try {
            EncryptedDictionaryExport.encrypt(plaintext, passphrase)
        } finally {
            plaintext.fill(0)
        }
        val outputStream = context.contentResolver.openOutputStream(uri)
            ?: error("Could not open export destination for writing.")
        outputStream.use { stream ->
            stream.write(envelope)
        }
    }

    private fun importCombinedListEntries(entries: List<PersonalDictionaryEntry>) {
        val dao = mutableUserDictionaryDao()
            ?: error("This user dictionary is read-only.")
        for (entry in entries) {
            val parsedLocale = entry.locale?.let { runCatching { FlorisLocale.fromTag(it) }.getOrNull() }
            // Persist the spelling the lookup binds. `locale` is a raw string
            // column while queryExact takes a FlorisLocale that Room converts
            // through localeTag(), which joins with '_'. Storing an imported
            // BCP-47 tag verbatim meant "en-GB" was written and "en_GB" was
            // searched for, so the row was never found and a re-import stacked
            // up duplicates.
            val storedLocale = parsedLocale?.localeTag() ?: entry.locale
            val alreadyExistingEntries = dao.queryExact(entry.word, parsedLocale)
            val row = UserDictionaryEntry(
                id = alreadyExistingEntries.firstOrNull()?.id ?: 0,
                word = entry.word,
                freq = entry.frequency,
                locale = storedLocale,
                shortcut = entry.shortcut,
            )
            if (alreadyExistingEntries.isNotEmpty()) {
                dao.update(row)
            } else {
                dao.insert(row)
            }
        }
    }
}

object UserDictionaryCombinedListCodec {
    fun encode(
        entries: List<UserDictionaryEntry>,
        dictionaryName: String?,
        generatedBy: String,
        timestampMillis: Long,
    ): String {
        return buildString {
            append("dictionary=")
            append(dictionaryName)
            append(";date=")
            append(timestampMillis)
            append(";generated-by=")
            append(generatedBy)
            append(";version=1")
            appendLine()
            for (entry in entries) {
                append(" w=")
                append(entry.word)
                append(";f=")
                append(entry.freq)
                append(";l=")
                append(entry.locale) // always append locale even if null
                if (entry.shortcut != null) {
                    append(";s=")
                    append(entry.shortcut)
                }
                appendLine()
            }
        }
    }

    fun decode(text: String): List<PersonalDictionaryEntry> {
        return decodeLines(text.lineSequence())
    }

    fun decodeLines(lines: Sequence<String>): List<PersonalDictionaryEntry> {
        val entries = mutableListOf<PersonalDictionaryEntry>()
        var isFirstLine = true
        lines.forEach { line ->
            if (line.isBlank()) {
                return@forEach
            }
            if (isFirstLine) {
                isFirstLine = false
                if (line.trimStart().startsWith("dictionary=", ignoreCase = true)) {
                    return@forEach
                }
            }
            var word: String? = null
            var freq: Int? = null
            var locale: String? = null
            var shortcut: String? = null
            var malformed = false
            for (property in line.split(';')) {
                val keyValuePair = property.split('=', limit = 2)
                if (keyValuePair.size != 2) {
                    malformed = true
                    break
                }
                val key = keyValuePair[0].trim().lowercase()
                val value = keyValuePair[1].trim()
                when (key) {
                    "w", "word" -> word = value.ifBlank { null }
                    "f", "freq" -> {
                        val number = value.toIntOrNull(10)
                        if (number == null || number !in FREQUENCY_MIN..FREQUENCY_MAX) {
                            malformed = true
                            break
                        }
                        freq = number
                    }
                    "l", "locale" -> locale = when (value) {
                        "all", "null", "" -> null
                        else -> value.ifBlank { null }
                    }
                    "s", "shortcut" -> shortcut = value.ifBlank { null }
                }
            }
            if (malformed || word == null || freq == null) {
                return@forEach
            }
            entries += PersonalDictionaryEntry(
                word = word,
                frequency = freq,
                locale = locale,
                shortcut = shortcut,
            )
            if (entries.size > DictionaryImporter.MAX_IMPORTED_ENTRIES) {
                throw DictionaryImportException(
                    "Dictionary import contains more than ${DictionaryImporter.MAX_IMPORTED_ENTRIES} entries; " +
                        "split the file and retry.",
                    isSafetyLimit = true,
                )
            }
        }
        return entries
    }
}

@Database(entities = [UserDictionaryEntry::class], version = 1)
@TypeConverters(FlorisUserDictionaryDatabase.Converters::class)
abstract class FlorisUserDictionaryDatabase : RoomDatabase(), UserDictionaryDatabase {
    companion object {
        const val DB_FILE_NAME = "floris_user_dictionary"
    }

    abstract override fun userDictionaryDao(): UserDictionaryDao

    override fun mutableUserDictionaryDao(): UserDictionaryDao {
        return userDictionaryDao()
    }

    override fun reset() {
        userDictionaryDao().deleteAll()
    }

    class Converters {
        @TypeConverter
        fun localeToString(locale: FlorisLocale?): String? {
            return when (locale) {
                null -> null
                else -> locale.localeTag()
            }
        }

        @TypeConverter
        fun stringToLocale(string: String?): FlorisLocale? {
            return when (string) {
                null, "all", "null", "" -> null
                else -> FlorisLocale.fromTag(string)
            }
        }
    }
}

class SystemUserDictionaryDatabase(context: Context) : UserDictionaryDatabase {
    private val applicationContext: WeakReference<Context> = WeakReference(context.applicationContext ?: context)

    private val dao = object : UserDictionaryReadDao {
        override fun query(word: String): List<UserDictionaryEntry> {
            return queryResolver(
                selection = "${UserDictionary.Words.WORD} LIKE ?",
                selectionArgs = arrayOf("%$word%"),
                sortOrder = SORT_BY_FREQ_DESC,
            )
        }

        override fun query(word: String, locale: FlorisLocale?): List<UserDictionaryEntry> {
            return if (locale == null) {
                queryResolver(
                    selection = "${UserDictionary.Words.WORD} LIKE ? AND ${UserDictionary.Words.LOCALE} IS NULL",
                    selectionArgs = arrayOf("%$word%"),
                    sortOrder = SORT_BY_FREQ_DESC,
                )
            } else {
                queryResolver(
                    selection = "${UserDictionary.Words.WORD} LIKE ? AND (${UserDictionary.Words.LOCALE} = ? OR ${UserDictionary.Words.LOCALE} = ? OR ${UserDictionary.Words.LOCALE} IS NULL)",
                    selectionArgs = arrayOf("%$word%", locale.localeTag(), locale.language),
                    sortOrder = SORT_BY_FREQ_DESC,
                )
            }
        }

        override fun queryShortcut(shortcut: String): List<UserDictionaryEntry> {
            return queryResolver(
                selection = "${UserDictionary.Words.SHORTCUT} = ?",
                selectionArgs = arrayOf(shortcut),
                sortOrder = SORT_BY_FREQ_DESC,
            )
        }

        override fun queryShortcut(shortcut: String, locale: FlorisLocale?): List<UserDictionaryEntry> {
            return if (locale == null) {
                queryResolver(
                    selection = "${UserDictionary.Words.SHORTCUT} = ? AND ${UserDictionary.Words.LOCALE} IS NULL",
                    selectionArgs = arrayOf(shortcut),
                    sortOrder = SORT_BY_FREQ_DESC,
                )
            } else {
                queryResolver(
                    selection = "${UserDictionary.Words.SHORTCUT} = ? AND (${UserDictionary.Words.LOCALE} = ? OR ${UserDictionary.Words.LOCALE} = ? OR ${UserDictionary.Words.LOCALE} IS NULL)",
                    selectionArgs = arrayOf(shortcut, locale.localeTag(), locale.language),
                    sortOrder = SORT_BY_FREQ_DESC,
                )
            }
        }

        override fun queryAll(): List<UserDictionaryEntry> {
            return queryResolver(
                selection = null,
                selectionArgs = null,
                sortOrder = SORT_BY_FREQ_DESC,
            )
        }

        override fun queryAllReadOnlyTransaction(): List<UserDictionaryEntry> {
            return queryAll()
        }

        override fun queryAll(locale: FlorisLocale?): List<UserDictionaryEntry> {
            return if (locale == null) {
                queryResolver(
                    selection = "${UserDictionary.Words.LOCALE} IS NULL",
                    selectionArgs = null,
                    sortOrder = SORT_BY_FREQ_DESC,
                )
            } else {
                queryResolver(
                    selection = "${UserDictionary.Words.LOCALE} = ?",
                    selectionArgs = arrayOf(locale.localeTag()),
                    sortOrder = SORT_BY_FREQ_DESC,
                )
            }
        }

        override fun queryExact(word: String): List<UserDictionaryEntry> {
            return queryResolver(
                selection = "${UserDictionary.Words.WORD} = ?",
                selectionArgs = arrayOf(word),
                sortOrder = null,
            )
        }

        override fun queryExact(word: String, locale: FlorisLocale?): List<UserDictionaryEntry> {
            return if (locale == null) {
                queryResolver(
                    selection = "${UserDictionary.Words.WORD} = ? AND ${UserDictionary.Words.LOCALE} IS NULL",
                    selectionArgs = arrayOf(word),
                    sortOrder = SORT_BY_FREQ_DESC,
                )
            } else {
                queryResolver(
                    selection = "${UserDictionary.Words.WORD} = ? AND ${UserDictionary.Words.LOCALE} = ?",
                    selectionArgs = arrayOf(word, locale.localeTag()),
                    sortOrder = SORT_BY_FREQ_DESC,
                )
            }
        }

        override fun queryExactFuzzyLocale(word: String, locale: FlorisLocale?): List<UserDictionaryEntry> {
            return if (locale == null) {
                queryResolver(
                    selection = "${UserDictionary.Words.WORD} = ? AND ${UserDictionary.Words.LOCALE} IS NULL",
                    selectionArgs = arrayOf(word),
                    sortOrder = SORT_BY_FREQ_DESC,
                )
            } else {
                queryResolver(
                    selection = "${UserDictionary.Words.WORD} = ? AND (${UserDictionary.Words.LOCALE} = ? OR ${UserDictionary.Words.LOCALE} IS NULL)",
                    selectionArgs = arrayOf(word, locale.localeTag()),
                    sortOrder = SORT_BY_FREQ_DESC,
                )
            }
        }

        override fun queryLanguageTagList(): List<String> {
            val resolver = applicationContext.get()?.contentResolver ?: return listOf()
            val cursor = resolver.query(
                UserDictionary.Words.CONTENT_URI,
                PROJECTIONS_LANGUAGE,
                null,
                null,
                null
            ) ?: return listOf()
            cursor.use { c ->
                if (c.count <= 0) {
                    return listOf()
                }
                val localeIndex = c.getColumnIndex(UserDictionary.Words.LOCALE)
                val retList = mutableSetOf<String>()
                while (c.moveToNext()) {
                    val localeStr = c.getString(localeIndex)
                    if (localeStr == null) {
                        retList.add("")
                    } else {
                        retList.add(localeStr)
                    }
                }
                return retList.toList()
            }
        }

        private fun queryResolver(selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): List<UserDictionaryEntry> {
            val resolver = applicationContext.get()?.contentResolver ?: return listOf()
            val cursor = resolver.query(
                UserDictionary.Words.CONTENT_URI,
                PROJECTIONS,
                selection,
                selectionArgs,
                sortOrder
            ) ?: return listOf()
            // `cursor.use {}` closes the cursor even if `parseEntries` throws.
            // The previous `.also { cursor.close() }` chain leaked the cursor
            // on any RuntimeException from parseEntries (Robolectric / OEM
            // content-provider implementations sometimes throw on
            // `getString` for malformed rows).
            return cursor.use { parseEntries(it) }
        }

        private fun parseEntries(cursor: Cursor): List<UserDictionaryEntry> {
            if (cursor.count <= 0) {
                return listOf()
            }
            val idIndex = cursor.getColumnIndex(UserDictionary.Words._ID)
            val wordIndex = cursor.getColumnIndex(UserDictionary.Words.WORD)
            val freqIndex = cursor.getColumnIndex(UserDictionary.Words.FREQUENCY)
            val localeIndex = cursor.getColumnIndex(UserDictionary.Words.LOCALE)
            val shortcutIndex = cursor.getColumnIndex(UserDictionary.Words.SHORTCUT)
            val retList = mutableListOf<UserDictionaryEntry>()
            while (cursor.moveToNext()) {
                retList.add(
                    UserDictionaryEntry(
                        id = cursor.getLong(idIndex),
                        word = cursor.getString(wordIndex),
                        freq = cursor.getInt(freqIndex),
                        locale = cursor.getString(localeIndex),
                        shortcut = cursor.getString(shortcutIndex)
                    )
                )
            }
            return retList
        }

    }

    override fun userDictionaryDao(): UserDictionaryReadDao {
        return dao
    }

    override fun reset() {
        // The platform user dictionary provider does not expose a safe app-scoped reset operation.
    }
}

object UserDictionaryValidation {
    private val WordRegex = """^[^\s;,]+${'$'}""".toRegex()

    val Word = ValidationRule<String> {
        forKlass = UserDictionaryEntry::class
        forProperty = "word"
        validator { input ->
            val str = input.trim()
            when {
                input.isBlank() -> resultInvalid(error = R.string.settings__udm__dialog__word_error_empty)
                !str.matches(WordRegex) -> resultInvalid(error = R.string.settings__udm__dialog__word_error_invalid, "regex" to WordRegex)
                else -> resultValid()
            }
        }
    }

    val Freq = ValidationRule<String> {
        forKlass = UserDictionaryEntry::class
        forProperty = "freq"
        validator { input ->
            val freq = input.trim().toIntOrNull(10)
            when {
                input.isBlank() -> resultInvalid(error = R.string.settings__udm__dialog__freq_error_empty)
                freq == null -> resultInvalid(error = R.string.settings__udm__dialog__freq_error_empty)
                freq < FREQUENCY_MIN || freq > FREQUENCY_MAX -> resultInvalid(error = R.string.settings__udm__dialog__freq_error_invalid)
                else -> resultValid()
            }
        }
    }

    val Shortcut = ValidationRule<String> {
        forKlass = UserDictionaryEntry::class
        forProperty = "shortcut"
        validator { input ->
            val str = input.trim()
            when {
                input.isBlank() -> resultValid() // Is optional
                !str.matches(WordRegex) -> resultInvalid(error = R.string.settings__udm__dialog__shortcut_error_invalid, "regex" to WordRegex)
                else -> resultValid()
            }
        }
    }

    val Locale = ValidationRule<String> {
        forKlass = UserDictionaryEntry::class
        forProperty = "locale"
        validator { input ->
            val str = input.trim()
            when {
                input.isBlank() -> resultValid() // Is optional
                tryOrNull { FlorisLocale.fromTag(str) } == null -> resultInvalid(error = R.string.settings__udm__dialog__locale_error_invalid)
                else -> resultValid()
            }
        }
    }
}

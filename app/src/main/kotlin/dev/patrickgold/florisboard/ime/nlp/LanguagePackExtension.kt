/*
 * Copyright (C) 2025 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.ime.nlp

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import dev.patrickgold.florisboard.lib.FlorisLocale
import dev.patrickgold.florisboard.lib.devtools.flogError
import dev.patrickgold.florisboard.lib.ext.Extension
import dev.patrickgold.florisboard.lib.ext.ExtensionComponent
import dev.patrickgold.florisboard.lib.ext.ExtensionEditor
import dev.patrickgold.florisboard.lib.ext.ExtensionMeta
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.florisboard.lib.kotlin.io.FsDir
import org.florisboard.lib.kotlin.io.subFile

@Serializable
enum class LanguagePackKind {
    @SerialName("han-shape-based")
    HAN_SHAPE_BASED,

    @SerialName("generic")
    GENERIC,
}

@Serializable
class LanguagePackComponent(
    override val id: String,
    override val label: String,
    override val authors: List<String>,
    val locale: FlorisLocale = FlorisLocale.fromTag(id),
    val hanShapeBasedKeyCode: String = "abcdefghijklmnopqrstuvwxyz",
) : ExtensionComponent {
    @Transient var parent: LanguagePackExtension? = null

    @SerialName("hanShapeBasedTable")
    private val _hanShapeBasedTable: String? = null  // Allows overriding the sqlite3 table to query in the json
    val hanShapeBasedTable
        get() = _hanShapeBasedTable ?: locale.variant
}

@SerialName(LanguagePackExtension.SERIAL_TYPE)
@Serializable
class LanguagePackExtension(
    override val meta: ExtensionMeta,
    override val dependencies: List<String>? = null,
    val items: List<LanguagePackComponent> = listOf(),
    val kind: LanguagePackKind = LanguagePackKind.HAN_SHAPE_BASED,
    val hanShapeBasedSQLite: String = "han.sqlite3",
) : Extension() {

    override fun components(): List<ExtensionComponent> = items

    override fun edit() = LanguagePackExtensionEditor(
        meta = meta,
        dependencies = dependencies?.toMutableList() ?: mutableListOf(),
        items = items.toMutableList(),
        kind = kind,
        hanShapeBasedSQLite = hanShapeBasedSQLite,
    )

    companion object {
        const val SERIAL_TYPE = "ime.extension.languagepack"
    }

    override fun serialType() = SERIAL_TYPE

    @Transient private val hanShapeBasedSQLiteDatabaseLock = Any()
    @Transient private var _hanShapeBasedSQLiteDatabase: SQLiteDatabase? = null

    fun supportsHanShapeBased(): Boolean = kind == LanguagePackKind.HAN_SHAPE_BASED

    fun hanShapeBasedComponents(): List<LanguagePackComponent> {
        return if (supportsHanShapeBased()) items else emptyList()
    }

    fun hasOpenHanShapeBasedSQLiteDatabase(): Boolean {
        return synchronized(hanShapeBasedSQLiteDatabaseLock) {
            _hanShapeBasedSQLiteDatabase?.isOpen == true
        }
    }

    fun <T> withHanShapeBasedSQLiteDatabase(block: (SQLiteDatabase) -> T): T? {
        synchronized(hanShapeBasedSQLiteDatabaseLock) {
            val database = _hanShapeBasedSQLiteDatabase?.takeIf { it.isOpen } ?: return null
            return block(database)
        }
    }

    override fun onAfterLoad(context: Context, cacheDir: FsDir) {
        super.onAfterLoad(context, cacheDir)

        if (!supportsHanShapeBased()) {
            return
        }

        val databasePath = workingDir?.subFile(hanShapeBasedSQLite)?.path
        if (databasePath == null) {
            flogError { "Han shape-based language pack not found or loaded" }
            closeHanShapeBasedSQLiteDatabase()
        } else try {
            openHanShapeBasedSQLiteDatabase(databasePath)
        } catch (e: SQLiteException) {
            closeHanShapeBasedSQLiteDatabase()
            flogError { "SQLiteException in openDatabase: path=$databasePath, error='${e}'" }
        }
    }

    override fun onBeforeUnload(context: Context, cacheDir: FsDir) {
        super.onBeforeUnload(context, cacheDir)
        closeHanShapeBasedSQLiteDatabase()
    }

    private fun openHanShapeBasedSQLiteDatabase(databasePath: String) {
        synchronized(hanShapeBasedSQLiteDatabaseLock) {
            val replacement = SQLiteDatabase.openDatabase(databasePath, null, SQLiteDatabase.OPEN_READONLY)
            val previous = _hanShapeBasedSQLiteDatabase
            _hanShapeBasedSQLiteDatabase = replacement
            if (previous !== replacement) {
                previous?.takeIf { it.isOpen }?.close()
            }
        }
    }

    private fun closeHanShapeBasedSQLiteDatabase() {
        synchronized(hanShapeBasedSQLiteDatabaseLock) {
            _hanShapeBasedSQLiteDatabase?.takeIf { it.isOpen }?.close()
            _hanShapeBasedSQLiteDatabase = null
        }
    }
}

class LanguagePackExtensionEditor(
    override var meta: ExtensionMeta,
    override val dependencies: MutableList<String>,
    val items: MutableList<LanguagePackComponent>,
    val kind: LanguagePackKind,
    val hanShapeBasedSQLite: String,
) : ExtensionEditor {

    override fun build() = LanguagePackExtension(
        meta = meta,
        dependencies = dependencies.takeUnless { it.isEmpty() }?.toList(),
        items = items.toList(),
        kind = kind,
        hanShapeBasedSQLite = hanShapeBasedSQLite,
    )
}

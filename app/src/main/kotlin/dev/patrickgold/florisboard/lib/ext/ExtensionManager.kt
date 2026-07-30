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

package dev.patrickgold.florisboard.lib.ext

import android.content.Context
import android.net.Uri
import android.os.FileObserver
import dev.patrickgold.florisboard.appContext
import dev.patrickgold.florisboard.ime.keyboard.KeyboardExtension
import dev.patrickgold.florisboard.ime.nlp.LanguagePackExtension
import dev.patrickgold.florisboard.ime.text.composing.Appender
import dev.patrickgold.florisboard.ime.text.composing.Composer
import dev.patrickgold.florisboard.ime.text.composing.HangulUnicode
import dev.patrickgold.florisboard.ime.text.composing.KanaUnicode
import dev.patrickgold.florisboard.ime.text.composing.WithRules
import dev.patrickgold.florisboard.ime.theme.ThemeExtension
import dev.patrickgold.florisboard.lib.devtools.LogTopic
import dev.patrickgold.florisboard.lib.devtools.flogDebug
import dev.patrickgold.florisboard.lib.devtools.flogError
import dev.patrickgold.florisboard.lib.io.FlorisRef
import dev.patrickgold.florisboard.lib.io.ZipUtils
import dev.patrickgold.florisboard.lib.io.ArchiveEntryTooLargeException
import dev.patrickgold.florisboard.lib.io.delete
import dev.patrickgold.florisboard.lib.io.listDirs
import dev.patrickgold.florisboard.lib.io.listFiles
import dev.patrickgold.florisboard.lib.io.loadJsonAsset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.florisboard.lib.android.FileObserver
import org.florisboard.lib.kotlin.io.FsFile
import org.florisboard.lib.kotlin.io.writeJson
import org.florisboard.lib.kotlin.throwOnFailure

@OptIn(ExperimentalSerializationApi::class)
val ExtensionJsonConfig = Json {
    classDiscriminator = "$"
    encodeDefaults = false
    ignoreUnknownKeys = true
    isLenient = true
    prettyPrint = true
    prettyPrintIndent = "  "
    serializersModule = SerializersModule {
        polymorphic(Extension::class) {
            subclass(KeyboardExtension::class, KeyboardExtension.serializer())
            subclass(ThemeExtension::class, ThemeExtension.serializer())
            subclass(LanguagePackExtension::class, LanguagePackExtension.serializer())
        }
        polymorphic(Composer::class) {
            subclass(Appender::class, Appender.serializer())
            subclass(HangulUnicode::class, HangulUnicode.serializer())
            subclass(KanaUnicode::class, KanaUnicode.serializer())
            subclass(WithRules::class, WithRules.serializer())
            defaultDeserializer { Appender.serializer() }
        }
    }
}

class ExtensionManager(context: Context) {
    companion object {
        const val IME_KEYBOARD_PATH = "ime/keyboard"
        const val IME_KEYBOARD3_PATH = "ime/keyboard3"
        const val IME_THEME_PATH = "ime/theme"
        const val IME_LANGUAGEPACK_PATH = "ime/languagepack"

        private const val FILE_OBSERVER_MASK =
            FileObserver.CLOSE_WRITE or FileObserver.DELETE or FileObserver.MOVED_FROM or FileObserver.MOVED_TO
    }

    private val appContext by context.appContext()
    private val defaultScope = CoroutineScope(Dispatchers.Default)
    private val ioScope = CoroutineScope(Dispatchers.IO)

    val keyboardExtensions = ExtensionIndex(KeyboardExtension.serializer(), IME_KEYBOARD_PATH)
    val themes = ExtensionIndex(ThemeExtension.serializer(), IME_THEME_PATH)
    val languagePacks = ExtensionIndex(LanguagePackExtension.serializer(), IME_LANGUAGEPACK_PATH)

    val extensions = combine(
        keyboardExtensions,
        themes,
        languagePacks,
    ) { lists -> lists.flatMap { it } }.stateIn(defaultScope, SharingStarted.Eagerly, emptyList())

    fun init() {
        ioScope.launch {
            keyboardExtensions.init()
            themes.init()
            languagePacks.init()
        }
    }

    fun import(ext: Extension) {
        val workingDir = requireNotNull(ext.workingDir) { "No working dir specified" }
        ExtensionPackagePolicy.validateExtracted(ext, workingDir)
        val extFileName = ExtensionDefaults.createFlexName(ext.meta.id)
        val relGroupPath = when (ext) {
            is KeyboardExtension -> IME_KEYBOARD_PATH
            is ThemeExtension -> IME_THEME_PATH
            is LanguagePackExtension -> IME_LANGUAGEPACK_PATH
            else -> error("Unknown extension type")
        }
        ext.sourceRef = FlorisRef.internal(relGroupPath).subRef(extFileName)
        FsFile(workingDir, ExtensionDefaults.MANIFEST_FILE_NAME).writeJson(ext, ExtensionJsonConfig)
        writeExtension(ext).throwOnFailure()
        ext.unload(appContext)
        ext.workingDir = null
    }

    fun export(ext: Extension, uri: Uri) {
        ext.load(appContext).throwOnFailure()
        val workingDir = requireNotNull(ext.workingDir) { "No working dir specified" }
        ZipUtils.zip(appContext, workingDir, uri).throwOnFailure()
        ext.unload(appContext)
    }

    private fun writeExtension(ext: Extension) = runCatching {
        val workingDir = requireNotNull(ext.workingDir) { "No working dir specified" }
        val sourceRef = requireNotNull(ext.sourceRef) { "No source ref specified" }
        ZipUtils.zip(appContext, workingDir, sourceRef).throwOnFailure()
    }

    fun getExtensionById(id: String): Extension? {
        return extensions.value.find { it.meta.id == id }
    }

    fun canDelete(ext: Extension): Boolean {
        return ext.sourceRef?.isInternal == true
    }

    fun delete(ext: Extension) {
        check(canDelete(ext)) { "Cannot delete extension!" }
        ext.unload(appContext)
        ext.sourceRef!!.delete(appContext)
    }

    @OptIn(ExperimentalForInheritanceCoroutinesApi::class)
    inner class ExtensionIndex<T : Extension>(
        private val serializer: KSerializer<T>,
        modulePath: String,
        private val flow: MutableStateFlow<List<T>> = MutableStateFlow(emptyList()),
    ) : StateFlow<List<T>> by flow {
        private val assetsModuleRef = FlorisRef.assets(modulePath)
        private val internalModuleRef = FlorisRef.internal(modulePath)
        var internalModuleDir = internalModuleRef.absoluteFile(appContext)

        private var staticExtensions = listOf<T>()
        private val quarantineFlow = MutableStateFlow<List<QuarantinedExtension>>(emptyList())
        internal val quarantined: StateFlow<List<QuarantinedExtension>> = quarantineFlow
        private var fileObserver: FileObserver? = null
        private val initGuard = Mutex()
        private val refreshGuard = Mutex()

        suspend fun init() {
            initGuard.withLock {
                // Update internal module dir to actual path and make directory if not exists
                internalModuleDir = internalModuleRef.absoluteFile(appContext)
                internalModuleDir.mkdirs()

                // Refresh index to new state
                refreshGuard.withLock {
                    staticExtensions = indexAssetsModule()
                    refresh()
                }

                // Stop watching on old file observer if one exists and start new observer on new path
                fileObserver?.stopWatching()
                fileObserver = FileObserver(internalModuleDir, FILE_OBSERVER_MASK) { event, path ->
                    flogDebug(LogTopic.EXT_INDEXING) { "FileObserver.onEvent { event=$event path=$path }" }
                    if (path == null) return@FileObserver
                    ioScope.launch {
                        refreshGuard.withLock {
                            refresh()
                        }
                    }
                }.also { it.startWatching() }
            }
        }

        private fun refresh() {
            val (dynamicExtensions, quarantined) = indexInternalModule(
                reservedIds = staticExtensions.mapTo(mutableSetOf()) { it.meta.id },
            )
            quarantineFlow.value = quarantined
            flow.value = staticExtensions + dynamicExtensions
        }

        private fun indexAssetsModule(): List<T> {
            val list = mutableListOf<T>()
            assetsModuleRef.listDirs(appContext).fold(
                onSuccess = { extRefs ->
                    for (extRef in extRefs) {
                        runCatching {
                            val manifest = ZipUtils.readFileFromArchive(
                                context = appContext,
                                zipRef = extRef,
                                relPath = ExtensionDefaults.MANIFEST_FILE_NAME,
                                maxBytes = ExtensionPackagePolicy.MAX_MANIFEST_BYTES,
                            ).getOrThrow()
                            val ext = loadJsonAsset(
                                manifest,
                                serializer,
                                ExtensionJsonConfig,
                            ).getOrThrow()
                            ExtensionPackagePolicy.inspect(ext)
                            ext.sourceRef = extRef
                            list.add(ext)
                        }.onFailure { error ->
                            flogError { "Bundled extension rejected: ${error.safeExtensionReason()}" }
                        }
                    }
                },
                onFailure = { error ->
                    flogError { error.toString() }
                },
            )
            return list.toList()
        }

        private fun indexInternalModule(
            reservedIds: MutableSet<String>,
        ): Pair<List<T>, List<QuarantinedExtension>> {
            val list = mutableListOf<T>()
            val quarantined = mutableListOf<QuarantinedExtension>()
            internalModuleRef.listFiles(appContext).fold(
                onSuccess = { extRefs ->
                    for (extRef in extRefs.sortedBy { it.absoluteFile(appContext).name }) {
                        val fileRef = extRef.absoluteFile(appContext)
                        if (!fileRef.name.endsWith(".${ExtensionDefaults.FILE_EXTENSION}")) {
                            continue
                        }
                        runCatching {
                            val manifest = ZipUtils.readFileFromArchive(
                                context = appContext,
                                zipRef = extRef,
                                relPath = ExtensionDefaults.MANIFEST_FILE_NAME,
                                maxBytes = ExtensionPackagePolicy.MAX_MANIFEST_BYTES,
                            ).getOrElse { error ->
                                if (error is ArchiveEntryTooLargeException) {
                                    throw ExtensionPackageException(
                                        ExtensionQuarantineReason.MANIFEST_TOO_LARGE,
                                    )
                                }
                                throw error
                            }
                            val ext = loadJsonAsset(
                                manifest,
                                serializer,
                                ExtensionJsonConfig,
                            ).getOrElse {
                                throw ExtensionPackageException(
                                    ExtensionQuarantineReason.MANIFEST_MALFORMED,
                                )
                            }
                            val inspection = ExtensionPackagePolicy.inspect(ext)
                            inspection.componentJsonPaths.forEach { componentPath ->
                                ZipUtils.validateFileInArchive(
                                    context = appContext,
                                    zipRef = extRef,
                                    relPath = componentPath,
                                    maxBytes = ExtensionPackagePolicy.MAX_COMPONENT_JSON_BYTES,
                                ).getOrElse { error ->
                                    if (error is ArchiveEntryTooLargeException) {
                                        throw ExtensionPackageException(
                                            ExtensionQuarantineReason.COMPONENT_TOO_LARGE,
                                        )
                                    }
                                    throw ExtensionPackageException(
                                        ExtensionQuarantineReason.MISSING_COMPONENT_FILE,
                                    )
                                }
                            }
                            inspection.requiredBinaryPaths.forEach { componentPath ->
                                ZipUtils.validateFileInArchive(
                                    context = appContext,
                                    zipRef = extRef,
                                    relPath = componentPath,
                                    maxBytes = Long.MAX_VALUE,
                                ).getOrElse {
                                    throw ExtensionPackageException(
                                        ExtensionQuarantineReason.MISSING_COMPONENT_FILE,
                                    )
                                }
                            }
                            if (!reservedIds.add(ext.meta.id)) {
                                throw ExtensionPackageException(
                                    ExtensionQuarantineReason.DUPLICATE_COMPONENT_ID,
                                )
                            }
                            ext.sourceRef = extRef
                            list.add(ext)
                        }.onFailure { error ->
                            val reason = error.toQuarantineReason()
                            quarantined += QuarantinedExtension(
                                fileName = fileRef.name,
                                reason = reason,
                            )
                            flogError {
                                "Installed extension quarantined: file=${fileRef.name}, reason=$reason"
                            }
                        }
                    }
                },
                onFailure = { error ->
                    flogError { error.toString() }
                },
            )
            return list.toList() to quarantined.toList()
        }
    }
}

internal data class QuarantinedExtension(
    val fileName: String,
    val reason: ExtensionQuarantineReason,
)

private fun Throwable.toQuarantineReason(): ExtensionQuarantineReason {
    return when (this) {
        is ExtensionPackageException -> reason
        is ArchiveEntryTooLargeException -> ExtensionQuarantineReason.MANIFEST_TOO_LARGE
        else -> ExtensionQuarantineReason.UNREADABLE_ARCHIVE
    }
}

private fun Throwable.safeExtensionReason(): ExtensionQuarantineReason {
    return toQuarantineReason()
}

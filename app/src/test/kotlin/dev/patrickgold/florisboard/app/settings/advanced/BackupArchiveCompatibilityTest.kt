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

package dev.patrickgold.florisboard.app.settings.advanced

import dev.patrickgold.florisboard.lib.io.ZipUtils
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import org.florisboard.lib.kotlin.io.readJson
import org.florisboard.lib.kotlin.io.subDir
import org.florisboard.lib.kotlin.io.subFile
import org.florisboard.lib.kotlin.io.writeJson

class BackupArchiveCompatibilityTest : FunSpec({
    test("a v1 archive without the new stores still restores") {
        val root = Files.createTempDirectory("backup-v1-fixture").toFile()
        try {
            val legacySource = root.subDir("legacy-source")
            legacySource.mkdirs()
            legacySource.subFile(Backup.METADATA_JSON_NAME).writeJson(
                Backup.Metadata(
                    packageName = Restore.ACCEPTED_PACKAGE_PREFIXES.first(),
                    versionCode = Restore.MIN_VERSION_CODE,
                    versionName = "legacy",
                    timestamp = 42L,
                ),
            )
            val legacyKeyboard = legacySource
                .subDir("files")
                .subDir("ime")
                .subDir("keyboard")
            legacyKeyboard.mkdirs()
            legacyKeyboard.subFile("legacy-layout.json").writeText("legacy layout")

            val archive = root.subFile("legacy.zip")
            ZipUtils.zip(legacySource, archive)

            val extracted = root.subDir("extracted")
            extracted.mkdirs()
            ZipUtils.unzip(archive, extracted)
            val metadata: Backup.Metadata = extracted
                .subFile(Backup.METADATA_JSON_NAME)
                .readJson()

            metadata.archiveVersion shouldBe Backup.LEGACY_ARCHIVE_FORMAT_VERSION
            BackupRestorePolicy.validateRestoreArchive(
                metadata = metadata,
                currentVersionCode = Restore.MIN_VERSION_CODE,
                minimumVersionCode = Restore.MIN_VERSION_CODE,
                expectedPackagePrefixes = Restore.ACCEPTED_PACKAGE_PREFIXES,
                hasRestorableContent = extracted
                    .subDir("files")
                    .subDir("ime")
                    .subDir("keyboard")
                    .exists(),
            ).errorId shouldBe null

            val restored = root.subDir("restored")
            restored.mkdirs()
            BackupArchiveStores.copyDirectory(
                extracted.subDir("files").subDir("ime").subDir("keyboard"),
                restored.subDir("files").subDir("ime").subDir("keyboard"),
            )
            restored
                .subDir("files")
                .subDir("ime")
                .subDir("keyboard")
                .subFile("legacy-layout.json")
                .readText() shouldBe "legacy layout"
        } finally {
            root.deleteRecursively()
        }
    }
})

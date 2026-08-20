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

import org.florisboard.lib.kotlin.io.FsDir
import org.florisboard.lib.kotlin.io.FsFile

/**
 * Names and filesystem operations for the small user-owned stores carried by
 * the portable archive.
 *
 * Keeping these paths in one object prevents the writer, restore path, and
 * rollback snapshot from silently drifting apart.
 */
internal object BackupArchiveStores {
    const val SnippetsDirName = "snippets"
    const val KeypressSoundsDirName = "keypress_sounds"
    const val HardwareKeyboardLayoutFileName = "hardware_keyboard_layouts.json"
    const val CustomEmojiTagsFileName = "custom_emoji_tags.json"
    const val EmojiPinGroupsFileName = "emoji_pin_groups.json"

    fun copyDirectory(source: FsDir, target: FsDir) {
        source.copyRecursively(target, overwrite = true)
    }

    fun copyFile(source: FsFile, target: FsFile) {
        source.copyTo(target, overwrite = true)
    }
}

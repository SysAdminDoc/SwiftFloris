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

import org.florisboard.lib.kotlin.curlyFormat

object ExtensionDefaults {
    private const val ID_LOCAL_TEMPLATE = "local.{groupName}.{extensionName}"
    private val ExtensionIdRegex = """^[a-z][a-z0-9_]*(\.[a-z0-9][a-z0-9_]*)*${'$'}""".toRegex()

    const val FILE_EXTENSION = "flex"
    const val MANIFEST_FILE_NAME = "extension.json"

    fun createLocalId(
        groupName: String,
        extensionName: String = System.currentTimeMillis().toString(),
    ) = ID_LOCAL_TEMPLATE.curlyFormat("groupName" to groupName, "extensionName" to extensionName)

    fun isValidId(id: String): Boolean = ExtensionIdRegex.matches(id)

    fun createFlexName(id: String): String {
        require(isValidId(id)) { "Invalid extension id: $id" }
        return "$id.$FILE_EXTENSION"
    }
}

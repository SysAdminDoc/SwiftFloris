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

package dev.patrickgold.florisboard

/**
 * Base package namespace used by public cross-package contracts.
 *
 * This is intentionally the release/base ID, not [BuildConfig.APPLICATION_ID]:
 * debug, beta, and benchmark variants append suffixes but still interoperate
 * with addon and MCP fixtures that target the stable external contract.
 */
object AppPackageContract {
    const val BASE_APPLICATION_ID: String = "dev.patrickgold.florisboard"

    const val ACTION_PREFIX: String = "$BASE_APPLICATION_ID.action."
    const val PERMISSION_PREFIX: String = "$BASE_APPLICATION_ID.permission."
    const val ADDON_METADATA_PREFIX: String = "$BASE_APPLICATION_ID.addon."
    const val MCP_METADATA_PREFIX: String = "$BASE_APPLICATION_ID.mcp."
}

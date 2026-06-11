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

package dev.patrickgold.florisboard.ime.mcp

/**
 * Process-local snapshot of the last MCP discovery pass, including rejected
 * daemon packages that Settings can offer for explicit certificate pinning.
 */
object McpDaemonDiscoveryStore {
    private val lock = Any()
    private var current: McpDiscoverySnapshot = McpDiscoverySnapshot.Empty
    private var generation: Long = 0L

    fun active(): McpDiscoverySnapshot = synchronized(lock) { current }

    fun generation(): Long = synchronized(lock) { generation }

    fun setActive(snapshot: McpDiscoverySnapshot) = synchronized(lock) {
        current = snapshot
        generation += 1
    }

    fun reset() = setActive(McpDiscoverySnapshot.Empty)
}

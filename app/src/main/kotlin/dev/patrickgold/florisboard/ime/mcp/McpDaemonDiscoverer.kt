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

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * ROADMAP §10.5 L7.2 — Pure-Kotlin MCP daemon discovery pipeline.
 *
 * Takes a list of [DiscoveryCandidate]s — produced in production by
 * the `IntentResolver` wrapper that calls
 * `PackageManager.queryIntentServices(...)` for
 * [McpBridgeContract.ACTION_BIND_MCP_DAEMON] — and produces the
 * `Map<DaemonKey, DaemonEntry>` that feeds [McpDaemonRegistry.setActive].
 *
 * Validation rules (silently drop the candidate when violated; not
 * fatal — a single malformed daemon shouldn't break the registry):
 *
 *  1. Candidate package + class must be non-blank.
 *  2. Candidate must declare the
 *     [McpBridgeContract.PERMISSION_BIND_MCP] permission. This is
 *     enforced by PackageManager before reaching the discoverer; the
 *     discoverer re-checks defensively in case the caller hand-fed
 *     a fixture list.
 *  3. Protocol version metadata must be in `1..SUPPORTED_PROTOCOL_VERSION`.
 *  4. Tool catalog JSON must parse + contain at least one entry with
 *     a non-blank `name`. Tools missing `description` or
 *     `parameterSchema` default to safe placeholders so a partial
 *     catalog still lights up something.
 *  5. Catalog payload must not exceed
 *     [McpBridgeContract.MAX_PAYLOAD_BYTES] — runaway-tool guard.
 *
 * The full Android-side wrapper that converts `ResolveInfo` →
 * [DiscoveryCandidate] is intentionally a thin shim and lives in the
 * adjacent `McpAndroidDiscoverer` (TBD); this object is the
 * platform-independent core that's directly testable.
 */
object McpDaemonDiscoverer {

    /**
     * Drive a discovery pass.  Returns the active registry map the
     * caller should hand to [McpDaemonRegistry.setActive].
     * [candidates] doesn't need to be sorted; the output preserves
     * insertion order.
     */
    fun discover(candidates: List<DiscoveryCandidate>): Map<DaemonKey, DaemonEntry> {
        val out = LinkedHashMap<DaemonKey, DaemonEntry>(candidates.size)
        for (cand in candidates) {
            val entry = buildEntry(cand) ?: continue
            out[entry.key] = entry
        }
        return out
    }

    private fun buildEntry(cand: DiscoveryCandidate): DaemonEntry? {
        if (cand.packageName.isBlank() || cand.daemonClassName.isBlank()) return null
        if (!cand.hasBindPermission) return null
        val protocolVersion = cand.protocolVersion
        if (protocolVersion < 1) return null
        if (protocolVersion > McpBridgeContract.SUPPORTED_PROTOCOL_VERSION) return null
        if (cand.toolCatalogJson.isBlank()) return null
        if (cand.toolCatalogJson.length.toLong() > McpBridgeContract.MAX_PAYLOAD_BYTES) return null
        val tools = parseToolCatalog(cand.toolCatalogJson) ?: return null
        if (tools.isEmpty()) return null
        val key = DaemonKey(cand.packageName, cand.daemonClassName)
        return runCatching {
            DaemonEntry(key = key, protocolVersion = protocolVersion, tools = tools)
        }.getOrNull()
    }

    private fun parseToolCatalog(json: String): List<McpToolDescriptor>? {
        return runCatching {
            val root = JSON.parseToJsonElement(json).jsonObject
            val toolsArr: JsonArray = root["tools"]?.jsonArray ?: return null
            toolsArr.mapNotNull { entry ->
                val obj = (entry as? JsonObject) ?: return@mapNotNull null
                val name = obj["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                if (name.isEmpty()) return@mapNotNull null
                val description = obj["description"]?.jsonPrimitive?.contentOrNull
                    ?.takeIf { it.isNotBlank() } ?: "(no description provided)"
                val schema = obj["parameterSchema"]?.jsonPrimitive?.contentOrNull
                    ?.takeIf { it.isNotBlank() } ?: """{"type":"object"}"""
                McpToolDescriptor(
                    name = name,
                    description = description,
                    parameterSchemaJson = schema,
                )
            }
        }.getOrNull()
    }

    private val JSON = Json { ignoreUnknownKeys = true; isLenient = true }
}

/**
 * One candidate daemon as surfaced by the PackageManager scan.
 * Production callers build this from `ResolveInfo + ServiceInfo +
 * PackageManager.getServiceInfo` — see `McpAndroidDiscoverer`. Tests
 * build it directly.
 *
 *  - [protocolVersion] — value read from the
 *    [McpBridgeContract.METADATA_PROTOCOL_VERSION] meta-data attribute.
 *  - [hasBindPermission] — true when the service declares
 *    [McpBridgeContract.PERMISSION_BIND_MCP]; PackageManager normally
 *    enforces this for us, but the discoverer re-checks defensively.
 *  - [toolCatalogJson] — raw JSON pulled from the resource pointed to
 *    by [McpBridgeContract.METADATA_TOOL_CATALOG]. Expected shape:
 *    `{"tools": [{"name": "...", "description": "...", "parameterSchema": "..."}, ...]}`.
 */
data class DiscoveryCandidate(
    val packageName: String,
    val daemonClassName: String,
    val protocolVersion: Int,
    val hasBindPermission: Boolean,
    val toolCatalogJson: String,
)

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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Pure-JVM coverage of [McpAndroidDiscoverer.shapeCandidate].
 *
 * The Bundle / ResolveInfo / ServiceInfo lifting happens in
 * [McpAndroidDiscoverer.serviceAttrsFrom]; this test feeds the
 * already-lifted [McpAndroidDiscoverer.ServiceAttrs] shape directly,
 * matching the way `runDiscovery` decomposes the pipeline.
 */
class McpAndroidDiscovererTest : FunSpec({

    val catalogJson = """{"tools":[{"name":"calendar.next_event","description":"d","parameterSchema":"{}"}]}"""

    fun attrs(
        packageName: String = "com.daemon.a",
        className: String = "com.daemon.a.Svc",
        permission: String? = McpBridgeContract.PERMISSION_BIND_MCP,
        protocolVersion: Int = 1,
        catalogResourceId: Int = 42,
    ) = McpAndroidDiscoverer.ServiceAttrs(
        packageName = packageName,
        className = className,
        permission = permission,
        protocolVersion = protocolVersion,
        catalogResourceId = catalogResourceId,
    )

    test("returns a DiscoveryCandidate for a well-formed ServiceAttrs with non-blank catalog") {
        val cand = McpAndroidDiscoverer.shapeCandidate(attrs()) { pkg, resId ->
            pkg shouldBe "com.daemon.a"
            resId shouldBe 42
            catalogJson
        }
        cand shouldBe DiscoveryCandidate(
            packageName = "com.daemon.a",
            daemonClassName = "com.daemon.a.Svc",
            protocolVersion = 1,
            hasBindPermission = true,
            toolCatalogJson = catalogJson,
        )
    }

    test("hasBindPermission is false when the service's permission attr doesn't match") {
        val cand = McpAndroidDiscoverer.shapeCandidate(
            attrs(permission = "com.other.permission"),
        ) { _, _ -> catalogJson }
        cand?.hasBindPermission shouldBe false
    }

    test("hasBindPermission is false when the service's permission attr is null") {
        val cand = McpAndroidDiscoverer.shapeCandidate(attrs(permission = null)) { _, _ -> catalogJson }
        cand?.hasBindPermission shouldBe false
    }

    test("returns null when packageName is blank") {
        McpAndroidDiscoverer.shapeCandidate(attrs(packageName = "")) { _, _ -> catalogJson } shouldBe null
    }

    test("returns null when className is blank") {
        McpAndroidDiscoverer.shapeCandidate(attrs(className = "")) { _, _ -> catalogJson } shouldBe null
    }

    test("returns null when protocol-version is < 1 (default sentinel)") {
        McpAndroidDiscoverer.shapeCandidate(attrs(protocolVersion = -1)) { _, _ -> catalogJson } shouldBe null
        McpAndroidDiscoverer.shapeCandidate(attrs(protocolVersion = 0)) { _, _ -> catalogJson } shouldBe null
    }

    test("returns null when tool-catalog resource id is 0") {
        McpAndroidDiscoverer.shapeCandidate(attrs(catalogResourceId = 0)) { _, _ -> catalogJson } shouldBe null
    }

    test("returns null when catalogLookup returns null (resource not found)") {
        McpAndroidDiscoverer.shapeCandidate(attrs()) { _, _ -> null } shouldBe null
    }

    test("returns null when catalogLookup returns a blank string") {
        McpAndroidDiscoverer.shapeCandidate(attrs()) { _, _ -> "   " } shouldBe null
    }

    test("forwards both daemon-package + resource id to the catalogLookup callback") {
        var seenPkg: String? = null
        var seenId: Int? = null
        McpAndroidDiscoverer.shapeCandidate(
            attrs(packageName = "com.example.x", catalogResourceId = 99),
        ) { pkg, resId ->
            seenPkg = pkg
            seenId = resId
            catalogJson
        }
        seenPkg shouldBe "com.example.x"
        seenId shouldBe 99
    }
})

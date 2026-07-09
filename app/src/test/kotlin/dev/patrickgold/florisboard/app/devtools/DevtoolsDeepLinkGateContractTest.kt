package dev.patrickgold.florisboard.app.devtools

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain
import java.io.File

class DevtoolsDeepLinkGateContractTest : FunSpec({
    test("sensitive devtools child routes are gated at the route boundary") {
        val routesSource = locateProjectFile(
            "app/src/main/kotlin/dev/patrickgold/florisboard/app/Routes.kt",
        ).readText()

        routesSource shouldContain "private fun DevtoolsRouteGate"
        routesSource shouldContain "val devtoolsEnabled by prefs.devtools.enabled.collectAsState()"
        routesSource shouldContain "if (devtoolsEnabled)"
        routesSource shouldContain "DevtoolsScreen()"

        for (route in listOf("AndroidLocales", "AndroidSettings", "ExportDebugLog")) {
            routeBlock(routesSource, route) shouldContain "DevtoolsRouteGate"
        }
    }

    test("debug log generation remains behind the gated route") {
        val routesSource = locateProjectFile(
            "app/src/main/kotlin/dev/patrickgold/florisboard/app/Routes.kt",
        ).readText()
        val exportSource = locateProjectFile(
            "app/src/main/kotlin/dev/patrickgold/florisboard/app/devtools/ExportDebugLogScreen.kt",
        ).readText()

        routesSource shouldContain "DevtoolsRouteGate { ExportDebugLogScreen() }"
        exportSource shouldContain "Devtools.generateDebugLog(context, prefs, includeLogcat = true)"
    }
})

private fun locateProjectFile(path: String): File {
    return listOf(File(path), File("..", path))
        .firstOrNull { it.isFile }
        ?: error("$path not reachable from working directory ${File(".").absolutePath}")
}

private fun routeBlock(source: String, route: String): String {
    val start = source.indexOf("composableWithDeepLink(Devtools.$route::class)")
    require(start >= 0) { "Missing devtools route $route" }
    val nextRoute = source.indexOf("composableWithDeepLink(", start + 1)
    return source.substring(start, if (nextRoute >= 0) nextRoute else source.length)
}

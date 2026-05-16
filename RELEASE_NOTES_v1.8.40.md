# SwiftFloris v1.8.40 — 2026-05-15

L7.6b — per-daemon enable / disable for the MCP daemon bridge.
**998 unit tests** at HEAD, 0 failures.

## What changed (user-visible)

Each bound daemon row in **Settings → MCP daemon bridge** now has
a switch on the right. Flipping it off:

- writes the daemon's package name into the
  `mcp__disabled_daemon_packages` preference (newline-separated
  list),
- updates the daemon's row icon (from "play" to "block"),
- keeps the binding live (the daemon stays in the registry, the
  IBinder stays available) but stops the dispatch router from
  forwarding any `callTool` traffic to it.

The Status row count now reads "M/N" where M is the count of
enabled daemons and N is the total bound count, so the user sees
the disabled subset at a glance.

## What changed (internal)

### L7.6b — `prefs.mcp.disabledDaemonPackages`

New JetPref-backed preference in `AppPrefs.Mcp`:

```kotlin
val disabledDaemonPackages = string(
    key = "mcp__disabled_daemon_packages",
    default = "",
)
```

Stored as a newline-separated string — JetPref doesn't ship a
`Set<String>` type in this version, so the `Set<String>` view is
provided by the [DisabledDaemonSet] codec.

### L7.6b — `DisabledDaemonSet`

New `ime/mcp/DisabledDaemonSet`:

- `parse(serialized): Set<String>` — split on `\n`, trim, drop
  blanks.
- `encode(packages): String` — join sorted (stable diff),
  deduplicated, blanks dropped.
- `add(serialized, pkg)`, `remove(serialized, pkg)`,
  `contains(serialized, pkg)` — convenience wrappers around
  parse + mutate + encode.

### L7.6b — `McpDispatchRouter.isDaemonDisabled`

New constructor parameter:

```kotlin
class McpDispatchRouter(
    private val client: McpClient,
    private val registryView: RegistryView = RegistryView.from(),
    private val isDaemonDisabled: (DaemonKey) -> Boolean = { false },
)
```

The lambda is consulted **after** the tool resolves to a daemon
but **before** the `client.callTool` invocation. Disabled daemons
yield a `Response.Suppressed` with the reason
`"daemon <pkg> disabled by user"`.

### Tests

- 13 new `DisabledDaemonSetTest` cases covering parse / encode /
  add / remove / contains.
- Two new `McpDispatchRouterTest` cases: disabled-daemon
  short-circuit + lazy lambda evaluation (the check only fires
  after tool resolution).

## Versioning

- `gradle.properties`: `projectVersionCode=1840`,
  `projectVersionName=1.8.40`.
- README badge bumped to `v1.8.40`.

## What's next

- **L7.6c** — manual re-scan button in the screen (forces a fresh
  `McpAndroidDiscoverer.runDiscovery` pass without IME restart).
- **L7.7** — wire `McpDispatchRouter` into `NlpManager.smart-compose`
  so the disabled-set actually gates real traffic. The router's
  `isDaemonDisabled` parameter at the call-site reads
  `prefs.mcp.disabledDaemonPackages` via the `DisabledDaemonSet`
  codec.

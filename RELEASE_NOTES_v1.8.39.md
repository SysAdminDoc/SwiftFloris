# SwiftFloris v1.8.39 — 2026-05-15

L7.6 — Settings → MCP daemon bridge screen. Read-only listing of
every bound MCP daemon with its protocol version + advertised
tools. Per-daemon enable/disable + a runtime re-scan ride as the
L7.6b sub-slice. **982 unit tests** at HEAD, 0 failures.

## What changed (user-visible)

A new **MCP daemon bridge** entry appears in the Settings home
list, between **Sync** and **Backup**. Tapping it opens the new
screen:

- **Status group** — shows either "No MCP daemons installed"
  (with a one-line summary explaining the local-only contract)
  or "MCP bridge active: bound to N daemon(s)".
- **Bound daemons group** — one row per daemon, showing the
  package id, the protocol version, the tool count, and a
  comma-separated list of tool names. Read-only for v1.8.39.

The summary text explicitly tells the user that the MCP bridge
is local-only and never opens a network connection — keeping
the §1 no-network promise visible in the spot where the bridge
shows up.

## What changed (internal)

### L7.6 — `McpSettingsScreen`

New `app/settings/mcp/McpSettingsScreen.kt`:

- Reads `McpDaemonRegistry.active()` once on entry — the registry
  is rebuilt at IME service startup, so the snapshot is stable
  for the life of the screen.
- Renders one Preference row per daemon: package id as title,
  protocol version + tool count + tool names as summary.

### Routes + HomeScreen entry

- `Routes.Settings.Mcp` added as a `@Deeplink("settings/mcp")`
  serializable object, wired into the nav graph via
  `composableWithDeepLink(Settings.Mcp::class) { McpSettingsScreen() }`.
- `Routes.Settings.Mcp` exposed from the Settings home list with
  the `Icons.Default.Extension` icon, sitting right after the
  Sync entry.

### Strings

Added eight new strings under `settings__mcp__*` covering the
title, two group titles, and four status / daemon-row variants.
The two formatted strings (`status_bound_summary` and the daemon
protocol/tool count fragments) use a `{count}` / `{version}`
placeholder substituted at render time via `String.replace(...)` —
no `%d` format-arg overhead, keeping the strings translatable
through the existing Crowdin pipeline.

## Versioning

- `gradle.properties`: `projectVersionCode=1839`,
  `projectVersionName=1.8.39`.
- README badge bumped to `v1.8.39`.

## What's next

- **L7.6b** — per-daemon enable / disable. Adds a JetPref-backed
  `prefs.mcp.disabledDaemonPackages: Set<String>` plus a toggle
  on each daemon row. `McpDispatchRouter` consults the prefs
  before forwarding a `callTool` request.
- **L7.6c** — manual re-scan button (forces a fresh
  `McpAndroidDiscoverer.runDiscovery` pass without IME restart).
- **L7.7** — `NlpManager.smart-compose` consults
  `McpDaemonRegistry.findTool(...)` for tools relevant to the
  current input field.

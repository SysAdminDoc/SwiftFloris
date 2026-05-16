# SwiftFloris v1.8.37 — 2026-05-15

L7.5 — `McpAndroidDiscoverer`: the Android wrapper that converts
`PackageManager.queryIntentServices` results into the
`DiscoveryCandidate` shape `McpDaemonDiscoverer` already consumes.
**973 unit tests** at HEAD, 0 failures.

## What changed (user-visible)

Nothing. The actual IME-side wire-up that calls
`McpAndroidDiscoverer.runDiscovery(context)` at startup and feeds
the result into `McpDaemonRegistry.setActive(...)` rides as L7.5b
in the next slice. This release adds the platform translation layer
so when the IME-side wiring lands it can call one method.

## What changed (internal)

### L7.5 — `McpAndroidDiscoverer`

New `ime/mcp/McpAndroidDiscoverer`:

- `runDiscovery(context: Context): Map<DaemonKey, DaemonEntry>` —
  queries every installed Service matching
  `ACTION_BIND_MCP_DAEMON` with `GET_META_DATA`, shapes each
  `ResolveInfo` into a `DiscoveryCandidate`, and hands the list
  off to `McpDaemonDiscoverer.discover(...)`.
- **Catalog read**: opens the daemon's `R.raw.<catalog>` resource
  through `Context.createPackageContext(daemonPackage)` —
  no content-URI / FileProvider handshake needed.
- **Permission check**: marks `hasBindPermission = true` only
  when the daemon's `<service>` declared
  `android:permission="dev.patrickgold.florisboard.permission.BIND_MCP"`.
- **Failure modes**: missing serviceInfo / missing meta-data /
  protocol version < 1 / catalog resource id == 0 / catalog
  lookup failure all return null for that candidate (it gets
  filtered out of the result map without aborting the rest).

### Decomposition for testability

The Android-bound part of the pipeline is split into two
helpers so the candidate-shaping logic is pure-JVM testable:

- `serviceAttrsFrom(ResolveInfo): ServiceAttrs?` — lifts the
  platform-specific bits (`Bundle.getInt`, `ServiceInfo.permission`,
  …) into a flat `ServiceAttrs` record. Not pure-JVM testable
  on its own (the `Bundle.getInt` return is the well-known
  "not-mocked-returns-0" trap under `returnDefaultValues=true`).
- `shapeCandidate(ServiceAttrs, catalogLookup): DiscoveryCandidate?` —
  pure-Kotlin candidate validation and shaping. Pure-JVM testable.

### Tests — `McpAndroidDiscovererTest`

Ten new Kotest tests pinning `shapeCandidate`:

1. Returns a `DiscoveryCandidate` for well-formed input.
2. `hasBindPermission = false` when permission attr doesn't match.
3. `hasBindPermission = false` when permission attr is null.
4. Returns null on blank package name.
5. Returns null on blank class name.
6. Returns null on protocol version < 1 (covers both -1 sentinel
   and 0 default).
7. Returns null on catalog resource id == 0.
8. Returns null when `catalogLookup` returns null.
9. Returns null when `catalogLookup` returns a blank string.
10. Forwards daemon-package + resource id to `catalogLookup`.

## Versioning

- `gradle.properties`: `projectVersionCode=1837`,
  `projectVersionName=1.8.37`.
- README badge bumped to `v1.8.37`.

## What's next

- **L7.5b** — `FlorisImeService.onCreate` calls
  `McpAndroidDiscoverer.runDiscovery(context)` →
  `McpDaemonRegistry.setActive(...)`; constructs a
  `McpServiceConnectionManager`, binds every daemon, and
  installs `AndroidMcpClient(manager::binderFor)` via
  `McpClientRegistry.setActive(...)`. `onDestroy` calls
  `manager.shutdown()`.
- **L7.6 Settings UI** — Settings → Privacy → MCP screen listing
  every bound daemon + its advertised tools, with per-daemon
  enable/disable.

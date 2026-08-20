# Changelog

## Unreleased

- Give every security-relevant dependency a freshness floor. The gate checked a
  single dependency and printed `OK (1 checked dependency floor(s))`, which read
  like a pass while Tink, Room, androidx-sqlite, Kotlin, KSP and AGP had no floor
  at all; it now fails when a security-relevant catalog pin has no reviewed
  entry. The override matcher was inverted and could let one coordinate's
  override suppress another's floor, so overrides now match on both fields and
  are rejected at load time when either is missing.
- Record the Tink CVE-2026-15432 triage: the reported timing side channel is in
  `ChunkedMacVerification`, which this app never calls, so the pin stays at
  1.23.0 with a floor that surfaces the patched release when it ships.
- Repair two dead ends in the migration flow: the "SwiftFloris encrypted backup
  (.sfexp)" tile now opens the personal dictionary import picker, which detects
  the encrypted envelope and prompts for the passphrase, instead of the archive
  Restore screen that cannot read it; and an archive storing its dictionary as a
  SQLite snapshot now explains how to re-export it rather than pointing at an
  import path that does not exist.
- Correct four public docs that described protections the code does not have:
  the personal dictionary passphrase is Keystore-wrapped rather than
  Keystore-held, the Tasker receiver is gated by a default-off preference and a
  per-install HMAC signature rather than a signature permission (its `adb`
  examples were rejected at runtime and are replaced with the real setup flow),
  the reduced-motion guard reads `ANIMATOR_DURATION_SCALE` through
  `rememberReducedMotion()` rather than a Compose API that does not exist, and
  the release checklist now names the merged-manifest gate that actually
  enforces the permission guarantee.
- Raise the shared Settings widgets to the 48 dp WCAG 2.5.5 touch-target floor
  through a single named constant, including the custom layout editor's key
  buttons, and cover the floor with a test that measures the rendered target
  rather than matching source text.
- Fix a context-chain walk that could not terminate: configuring the IME system
  bars from a wrapped context (a Compose dialog or themed wrapper) spun on the
  main thread instead of resolving, and a missing window now skips the system
  bar setup rather than crashing.
- Derive theme contrast coverage from every foreground-bearing selector in each
  bundled stylesheet instead of a ten-selector list, report all violations at
  once, and name each WCAG exemption (inactive components, decorative
  separators, and the 3:1 non-text floor for glyph elements) in the gate.
- Raise contrast in the bundled themes the widened gate exposed: the extracted
  landscape action and the borderless focused key popup no longer draw light
  text on a light surface, and the glide trail, focused emoji tab, and floating
  resize handle use accent-variant tints that clear the non-text floor in the
  Floris Day, SwiftKey Pure, and M3E Nord themes.
- Warn inline in the theme editor when an edited foreground/background pair
  falls below the 4.5:1 WCAG AA text floor.

- Make the OSV release gate classify numeric CVSS scores and supported vectors
  correctly, fail closed on unknown severity, and run a regression self-test
  beside the release gate.
- Keep smart-compose ghost text off during incognito sessions, including in
  ordinary non-sensitive editors, and retain request-scoped privacy gating.
- Make release evidence exhaustive: discover every gate and Python self-test,
  record the two connected-device gates as explicit operator-run checks, and
  repair the repo-hygiene allowlist drift.
- Keep Android system dictionary access read-only in SwiftFloris: system entries
  remain browsable and exportable, while add, edit, delete, and import actions
  stay in the internal dictionary and Android system dictionary settings.
- Unify addon/MCP enrollment, merged-manifest, and addon-APK permission gates
  around the same fail-closed allowlist, including a SEND_SMS fixture regression
  check and the shared trust-capabilities registry.
- Expand live-document integrity to scan planning and untracked Markdown, reject
  dead roadmap/workflow references, and validate the blocked-roadmap structure.
- Require the F-Droid recipe's `commit:` ref to resolve on both local and origin
  tag sets, with a fixture proving an unresolvable ref fails the release gate.
- Route live smart-compose and translation calls through the audited addon hub,
  require explicit MCP consent/disable gates, remove the dead direct dispatcher,
  and pause MCP daemon binding until a real audited keyboard action exists.
- Preserve unreadable sticker, correction-prior, and personal n-gram stores
  instead of treating them as empty, and only quarantine clipboard history on
  explicit SQLite corruption evidence while keeping transient read failures
  retryable.
- Make MCP lifecycle startup single-shot even when consent is disabled and
  serialize start, rescan, retry, and teardown so a Settings rescan cannot
  repopulate registries during IME shutdown.
- Remove the maintainer device serial from benchmark baselines and emit a
  stable SHA-256 device key derived from manufacturer, model, and SDK, with
  repository hygiene rejecting serial fields in tracked documentation.
- Reconcile stale device-tier blockers with the attached API-36 emulator:
  preserve the real app/password-manager/API-37 gaps, close the already-shipped
  instrumented smoke coverage, and keep the missing MCP addon sample blocked.
- Declare API-33 stylus-handwriting and TalkBack touch-exploration inline-
  autofill capabilities in the IME manifest, with a contract test and updated
  accessibility/autofill verification guidance.
- Keep theme, extension, and addon settings loading states separate from their
  empty states, automatically start the first addon scan, and add a Roborazzi
  baseline for the theme loading surface.

## v1.9.59 — 2026-08-11

- Screen addon and MCP daemon enrolment against a permission allowlist so
  transports that need no `INTERNET` permission — SMS, Bluetooth,
  nearby-devices, shared storage — are rejected alongside the network ones.
- Clear leftover cache off the main thread at startup, so a cold keyboard
  start no longer blocks on a recursive delete.
- Replace a typed word by selecting it and committing over the selection
  instead of marking a composing region, so rich-text and web editors that
  ignore composing regions no longer duplicate the word.
- Move the build to Android Gradle Plugin 9.3.0.
- React to Android Advanced Protection Mode (Android 16+): while it is on,
  learning from typed text, clipboard history persistence and new add-on
  enrolment are all held off, and the privacy posture screen says so.
- Reject unrenderable key code points during layout import instead of
  producing a blank, unlabelled key.
- Move the build to Kotlin 2.4.10.
- Offer line-start, line-end, text-start, text-end and Page Up/Down as
  quick actions, so cursor jumps no longer require knowing a swipe binding.
- Add an opt-in emoji suggestion mode that keeps the typed word and puts the
  emoji after it instead of replacing the word.
- Route all 42 shipped locales through the translation pipeline; eight,
  including Simplified Chinese and Urdu, previously had no mapping and could
  never round-trip. A gate now fails when a locale has no route.
- Declare the bundled emoji data as Unicode Emoji 17.0, which is what CLDR 48
  generated; the assets already carried every Emoji 17.0 character.

## v1.9.58 — 2026-08-02

- Keep trust-capability evidence aligned with SQLCipher clipboard fallback and
  manifest permission-removal directives, and make the release-front-door
  locale gate portable across `python` and `python3` environments.
- Retain bounded, privacy-gated glide alternatives for unchanged committed words and restore them when the cursor returns to the word.
- Search emoji across the active and enrolled subtype locales with ordered fallback matching and bounded deduplication.
- Expand headless settings screenshot coverage across compact, wide-landscape, RTL, and 200% font-scale states, with shared loading/error/empty semantics and production color contrast checks.
- Refresh Tink to 1.23.0, Roborazzi to 1.70.0, and Kotest to 6.2.3 with the existing verification gates retained.
- Localize privacy-audit record labels, plural summaries, and timestamps while keeping the JSON export schema locale-independent.
- Centralize editor input-class/variation/flag compatibility, clear stale candidates for host-owned completion fields, and add headless restart and hardware-key contract coverage.
- Add a deterministic trust-critical locale coverage gate with translated-resource ratchets, explicit reviewed UI locale policy, typing-language separation, `en-XA`/`ar-XB` pseudolocale contracts, and hard-coded critical-copy detection.
- Centralize bounded keyboard-mode/context transitions so clipboard and media panels restore the prior symbols/numeric mode and clear stale history across editor, privacy, and window boundaries.
- Wire custom emoji tags through long-press palette actions, palette search, emoji suggestions, persisted settings management, locale-root normalization, and atomic file replacement.
- Preserve existing emoji pin-group files when an atomic replacement fails, including on Windows hosts that reject rename-over-existing.
- Surface snippet load and delete failures, move snippet file work to Dispatchers.IO, and expose per-file trigger counts from state.
- Route emoji and sticker empty-state copy through the keyboard theme text pipeline, including expanded-font-scale Roborazzi coverage.
- Resolve custom-layout labels and generated extension metadata through localized resources while preserving stable on-disk slugs.
- Add programmable Page Up/Page Down key codes, Android page-key dispatch, custom-layout editor support, localized labels, and Terminal/Navigation preset coverage.
- Import Unicode Keyboard3 XML as hardened local keyboard extensions with bounded XML parsing, versioned bundled-CLDR allowlists, deterministic compilation, source provenance, and security/conformance diagnostics.

## Roadmap archive — 2026-08-10 — ROADMAP.md

<details>
<summary>Original roadmap snapshot</summary>

```markdown
# SwiftFloris Roadmap

This file contains only actionable, unblocked work. Completed items are
deleted (they live in git history and the fastlane changelogs). Items
gated on external deliverables or hardware testing live in
`Roadmap_Blocked.md`.

---

## Research-Driven Additions

### P3
## Research-Driven Additions (2026-06-29)

### P1

### P3

## Research-Driven Additions

### P1

### P2

## Research-Driven Additions (2026-06-29 refresh)

### P1

### P2

### P3

## Research-Driven Additions

### P1

### P2

### P3

## Research-Driven Additions

### P1

### P2

### P3

## Research-Driven Additions

### P0

### P1

### P2

### P3

## Research-Driven Additions

### P2

### P3

## Audit Findings — 2026-08-02

Baseline for this pass: `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug`
→ **BUILD SUCCESSFUL in 4m 34s**, no failing tests, no lint failures. Nothing
below is a pre-existing baseline failure; every item is a defect found by
reading and tracing the code against that green baseline.

### P2

### P3
```

</details>

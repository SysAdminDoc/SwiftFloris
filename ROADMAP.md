# SwiftFloris Roadmap

This file contains only actionable, unblocked work. Completed items are
deleted (they live in git history and the fastlane changelogs). Items
gated on external deliverables or hardware testing live in
[`Roadmap_Blocked.md`](Roadmap_Blocked.md).

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

- [ ] P0 — Make public release/version drift a hard release-evidence failure
  Why: Public docs and metadata claim `v1.9.54`, but no local/remote `v1.9.54` tag or GitHub Release exists and the latest public release is `v1.9.53`.
  Evidence: `README.md`, `gradle.properties`, `fastlane/metadata/android/en-US/changelogs/2103.txt`, `fdroid/io.github.sysadmindoc.swiftfloris.yml`, `scripts/check-release-front-door.sh`, `scripts/release-evidence.ps1`, `gh release list --repo SysAdminDoc/SwiftFloris`.
  Touches: `scripts/check-release-front-door.sh`, `scripts/release-evidence.ps1`, release-front-door tests or script fixtures.
  Acceptance: Normal release evidence fails when the claimed version lacks a matching local tag, remote tag, or GitHub Release; an explicit pre-publication mode exists only for unpublished local version prep and cannot pass when README/F-Droid surfaces already claim the release.
  Complexity: M

- [ ] P0 — Fix and gate Obtainium manifests after the SwiftFloris package migration
  Why: The README's Obtainium link subscribes to `io.github.sysadmindoc.swiftfloris` in `SysAdminDoc/SwiftFloris`, but both checked-in Obtainium JSON files still point to upstream FlorisBoard package IDs and repository URLs.
  Evidence: `README.md`, `fastlane/obtainium/stable.json`, `fastlane/obtainium/preview.json`, Obtainium source support documentation.
  Touches: `fastlane/obtainium/stable.json`, `fastlane/obtainium/preview.json`, `scripts/check-public-doc-version-pins.py`, `scripts/check-fork-identity.sh`.
  Acceptance: Stable Obtainium JSON targets `io.github.sysadmindoc.swiftfloris`, `https://github.com/SysAdminDoc/SwiftFloris`, author `SysAdminDoc`, and name `SwiftFloris`; preview JSON uses the intended SwiftFloris preview ID/channel if retained; local gates fail on upstream app IDs, upstream repo URLs, upstream author names, or stale APK filters.
  Complexity: S

### P1

- [ ] P1 — Remove inherited upstream funding metadata and guard fork identity surfaces
  Why: `.github/FUNDING.yml` still sends sponsorship traffic to upstream `patrickgold` GitHub/LiberaPay/PayPal handles, which is user-visible trust drift for a renamed fork.
  Evidence: `.github/FUNDING.yml`, `scripts/check-fork-identity.sh`, repository fork-identity policy.
  Touches: `.github/FUNDING.yml`, `scripts/check-fork-identity.sh`, fork-identity test fixtures if present.
  Acceptance: Funding metadata is removed or intentionally points only to SwiftFloris-owned channels; `scripts/check-fork-identity.sh` fails on `patrickgold`, `paypal.me/devpatrickgold`, `florisboard/florisboard`, or upstream app IDs in public identity surfaces that are not explicitly whitelisted.
  Complexity: S

- [ ] P1 — Add redacted `EditorInfo.extras` diagnostics
  Why: `DebugSummarizeUtils.kt` currently lists only extras keys because value access was avoided, leaving maintainers without host-context diagnostics during crash/debug triage.
  Evidence: `app/src/main/kotlin/dev/patrickgold/florisboard/lib/util/DebugSummarizeUtils.kt`, SwiftKey/Gboard privacy-field behavior, existing crash-report redaction work.
  Touches: `app/src/main/kotlin/dev/patrickgold/florisboard/lib/util/DebugSummarizeUtils.kt`, related diagnostics/crash tests.
  Acceptance: Diagnostics include safe primitive extras, array sizes, nested bundle shapes, and type names while redacting raw strings, typed content, tokens, URIs, and unknown Parcelables; tests cover strings, numbers, arrays, nested bundles, unsupported objects, and password/incognito editor inputs.
  Complexity: M

- [ ] P1 — Serialize Han shape-based SQLite database lifecycle
  Why: `LanguagePackExtension.kt` opens, swaps, and closes the Han SQLite database without a lock, which can race lookup code once CJK language packs are loaded or reloaded.
  Evidence: `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/LanguagePackExtension.kt`, Fcitx5/Trime CJK package architecture.
  Touches: `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/LanguagePackExtension.kt`, Han provider/query call sites, `LanguagePackExtensionTest`.
  Acceptance: Database open/reload/unload/query paths use a synchronized or atomic holder so readers never observe a closed handle; tests simulate reload during lookup and verify repeated unload/load cycles do not throw or leak open handles.
  Complexity: M

### P2

- [ ] P2 — Harden Snygg `flex:/` URI validation at the value layer
  Why: Theme font/image values are a local asset trust boundary, but `SnyggUriValue.kt` still uses a TODO regex and accepts whatever `URI.create` normalizes after a broad `flex:/[^` ]+` match.
  Evidence: `lib/snygg/src/main/kotlin/org/florisboard/lib/snygg/value/SnyggUriValue.kt`, `lib/snygg/src/test/kotlin/org/florisboard/lib/snygg/value/SnyggUriValueTest.kt`.
  Touches: `lib/snygg/src/main/kotlin/org/florisboard/lib/snygg/value/SnyggUriValue.kt`, `lib/snygg/src/test/kotlin/org/florisboard/lib/snygg/value/SnyggUriValueTest.kt`, downstream asset resolver tests if needed.
  Acceptance: Valid `flex:/asset/name.ext` values still parse and serialize; empty paths, traversal segments, encoded traversal, control characters, backslashes, embedded whitespace, alternate schemes, host components, fragments, and query strings are rejected with deterministic tests.
  Complexity: S

- [ ] P2 — Add blocked-roadmap freshness checks for closed issues and already-published release gates
  Why: `Roadmap_Blocked.md` still lists issue #9 release follow-through as blocked even though the GitHub issue is closed and public releases have advanced beyond the affected version.
  Evidence: `Roadmap_Blocked.md`, `https://github.com/SysAdminDoc/SwiftFloris/issues/9`, `gh issue view 9 --repo SysAdminDoc/SwiftFloris`, `gh release list --repo SysAdminDoc/SwiftFloris`.
  Touches: `scripts/check-live-doc-integrity.py` or a release-evidence helper, `Roadmap_Blocked.md` cleanup in the implementation commit.
  Acceptance: Local doc/release checks warn or fail when blocked items reference closed GitHub issues, unavailable blockers that have become available, or release-follow-through items already satisfied by a newer public release; issue #9 is removed or updated from `Roadmap_Blocked.md`.
  Complexity: M

- [ ] P2 — Extend gesture sensitivity controls beyond glide typing
  Why: Competitor issue traffic repeatedly flags spacebar cursor, swipe-delete, language-switch, and popup/gesture conflict sensitivity, while SwiftFloris currently exposes a glide sensitivity slider but not equivalent tuning for those daily editing gestures.
  Evidence: `app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/gestures/GesturesScreen.kt`, `app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/gestures/GlideSensitivityPolicy.kt`, FUTO Keyboard issues #2105/#2102/#2119, Fcitx5 Android issues #879/#653/#906.
  Touches: gesture preferences/model, `GesturesScreen`, spacebar touchpad/delete/language-switch gesture handlers, settings search index, replay/unit tests.
  Acceptance: Users can select at least three sensitivity presets or sliders for spacebar cursor and swipe-delete/language-switch gestures; default behavior is unchanged; tests prove thresholds change commit/delete/switch decisions without regressing glide sensitivity.
  Complexity: M

### P3

- [ ] P3 — Expand source-stub hygiene gates with an allowlist for intentional TODOs and preview-only stubs
  Why: Source still contains TODOs and `error("not implemented")` preview stubs; some are harmless, but production IME code should not gain new runtime stubs without a reviewed allowlist.
  Evidence: `app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/theme/EditRuleDialog.kt`, `lib/snygg/src/main/kotlin/org/florisboard/lib/snygg/value/SnyggUriValue.kt`, `app/src/main/kotlin/dev/patrickgold/florisboard/lib/util/DebugSummarizeUtils.kt`, `scripts/check-repo-hygiene.sh`.
  Touches: `scripts/check-repo-hygiene.sh`, a small stub/TODO allowlist file if needed, tests or fixture commands.
  Acceptance: The hygiene gate fails on new `TODO()`, `NotImplementedError`, `error("not implemented")`, or high-risk TODO comments in production source unless the line is allowlisted with a rationale; existing intentional stubs are documented and covered by preview/unit tests.
  Complexity: S

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

- [ ] P3 — Add an Emoji 17 parser dry-run fixture without changing shipped CLDR assets
  Why: Unicode Emoji 17.0 keyboard test data is available, but full CLDR 49 assets are not; parser readiness can be tested now without changing production emoji ordering.
  Evidence: Unicode Emoji 17.0 `emoji-test.txt`; `EmojiData.kt`; `EmojiDataVersionTest.kt`; blocked CLDR 49 emoji refresh item.
  Touches: Emoji asset-generation/parsing tests, `EmojiDataVersionTest`, minimal test fixtures, `Roadmap_Blocked.md` note when implemented.
  Acceptance: A small fixture containing at least one E17.0 sequence parses through the existing emoji data path, records `emoji=17.0` metadata in tests, keeps shipped assets unchanged, and leaves the full CLDR 49 refresh blocked until CLDR artifacts are available.
  Complexity: S

## Research-Driven Additions

### P1

### P2

### P3

- [ ] P3 — Replace stale `RESEARCH_FEATURE_PLAN.md` source references
  Why: Source comments still point at a retired root research-plan filename even though current research lives in `RESEARCH.md` and historical plans live under `docs/research-feature-plan-*`.
  Evidence: `FlorisApplication.kt`; `PrivacyAuditScreen.kt`; `TypingStatsScreen.kt`; `AboutScreen.kt`; `ShiftStateMachine.kt`; `HeuristicSmartComposeProvider.kt`; `FlorisEmojiCompatReflectionGuardTest.kt`; `scripts/check-live-doc-integrity.py`.
  Touches: Source/test comments only, `scripts/check-live-doc-integrity.py` if adding a stale-reference guard.
  Acceptance: `rg "RESEARCH_FEATURE_PLAN\\.md" app lib scripts docs README.md ROADMAP.md` returns no stale source references, comments either name the current feature contract or are removed, behavior and tests are unchanged, and no new markdown files are created.
  Complexity: S

## Research-Driven Additions

### P1

### P2

### P3

- [ ] P3 — Fix PR template debug package ID drift
  Why: Contributor docs say debug APKs install as `dev.patrickgold.florisboard.debug`, but the current app ID is `io.github.sysadmindoc.swiftfloris.debug`.
  Evidence: `.github/PULL_REQUEST_TEMPLATE.md`; `app/build.gradle.kts`; `README.md`.
  Touches: `.github/PULL_REQUEST_TEMPLATE.md`, optional doc-integrity guard if package-ID checks are expanded.
  Acceptance: The PR template names the actual debug package ID, keeps the upstream namespace explanation separate from install identity, and any existing package-ID doc check continues to pass.
  Complexity: S

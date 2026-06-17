# Research — SwiftFloris

## Executive Summary
SwiftFloris is a privacy-first Android keyboard fork with an unusually strong trust posture for this category: no `INTERNET` permission, release-time reproducibility/SBOM/SLSA gates, local-only dictionary and clipboard flows, addon enrollment controls, and broad keyboard features beyond most FOSS alternatives. The highest-value direction is not more novelty by default; it is making the trust promise operationally hard to drift, closing the live crash/release gap, and turning existing addon/layout infrastructure into reviewer- and contributor-ready workflows. Top opportunities: P0 release-channel freshness gate; P0 issue #9 SymSpell OOM release follow-through; P1 make public trust docs trackable before refreshing them; P1 replace destructive clipboard Room migrations; P1 parse data-extraction rules by section/domain; P1 expose addon provenance export in Settings; P1 provide addon authoring docs/fixtures for `scripts/verify-addon-apk.sh`; P2 add a layout JSON validation gate before the visual editor; P2 expand IME action/sensitive-field replay tests from competitor regressions; P2 add one-tap privacy proof export from Privacy posture.

## Product Map
- Core workflows: enable IME, type with tap/glide/hardware/voice handoff, manage dictionaries and learned entries, customize themes/layouts/profiles, audit and back up local data.
- User personas: privacy-conscious SwiftKey/Gboard refugees, multilingual offline typists, power users needing terminal/hardware-keyboard features, accessibility users relying on TalkBack/magnification, addon authors.
- Platforms and distribution: Android 8.0+ (`minSdk 26`, `targetSdk 36`), GitHub Releases/Obtainium today, F-Droid metadata prepared, no Google Play by design.
- Key integrations and data flows: FUTO Voice Input handoff, Tasker intents, MCP daemon bridge, addon APK discovery, local personal-dictionary sync envelopes, SwiftKey/Gboard dictionary imports, Espanso snippet expansion.

## Competitive Landscape
- FUTO Keyboard: Offline modern keyboard with swipe, autocorrect, prediction, and voice input; it sets the visible feature bar for privacy keyboards. Learn from its product clarity around "offline but modern." Avoid source/licensing ambiguity and heavy model paths in the base app.
- HeliBoard: Strongest FOSS direct alternative, high adoption, F-Droid channel, active 4.0 alpha work on floating keyboard, image clipboard, and gesture data gathering. Learn from its fast user-request loop. Avoid closed swipe-library dependencies and unclear addon trust boundaries.
- FlorisBoard: Upstream Compose/settings/theming foundation and the strongest custom-layout demand signal. Learn from upstream extension compatibility and layout editor requests. Avoid repeating long-lived "coming soon" NLP promises.
- AnySoftKeyboard: Mature no-network keyboard with a long-standing language-pack ecosystem. Learn from external language pack distribution. Avoid hiding pack authoring behind undocumented conventions.
- fcitx5-android and Trime: Best analogues for CJK/plugin architecture and table/Rime-style engines. Learn from explicit engine/plugin boundaries. Avoid importing their complexity into the base app while CJK data and native runtime choices remain blocked.
- Keyman and Espanso: Adjacent authoring ecosystems with package validation and user-authored text automation. Learn from package validation, schema compatibility, and author-facing docs. Avoid executing rich third-party logic in the IME process without a narrow addon contract.
- Gboard, SwiftKey, Samsung Keyboard, Grammarly: Commercial products normalize voice typing, cloud sync, grammar/rewrite, clipboard sync, and AI writing assist. Learn from their clear user-facing workflows. Avoid cloud/account/network dependence; Citizen Lab keyboard research makes no-network proof a strategic differentiator, not a constraint.

## Security, Privacy, and Reliability
- [Verified] GitHub Releases latest is `v1.9.48`, while `gradle.properties`, README, and fastlane metadata declare `v1.9.52` / versionCode `2101`; `scripts/check-release-front-door.sh` currently treats the GitHub release mismatch as advisory. This breaks Obtainium trust, public release claims, and issue triage.
- [Verified] GitHub issue #9 reports OOM while typing on released `v1.9.48`; current source contains heap-scaled SymSpell budgets and OOM fallback in `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/latin/SymSpellIndex.kt`, plus regression tests, but the fix has not reached the latest public release.
- [Verified] `docs/THREAT_MODEL.md` still says last updated `v1.8.231` and references F-Droid reproducible verification as a Now item; `docs/PRIVACY_AND_AI.md` links deleted `PROJECT_CONTEXT.md`; `docs/outreach/2026-05-17-swiftkey-migration/alternativeto-entry.md` also links that deleted file.
- [Verified] `.gitignore` ignores all `*.md` except README and two docs; `git check-ignore` reports `docs/THREAT_MODEL.md`, `docs/PRIVACY_AND_AI.md`, the outreach AlternativeTo draft, `CLAUDE.md`, `AGENTS.md`, and `Roadmap_Blocked.md` as ignored. Refreshing those trust docs without fixing the allowlist can leave them local-only and invisible to normal contributor flow.
- [Verified] `ClipboardHistoryDatabase.new()` and `ClipboardFilesDatabase.new()` call `fallbackToDestructiveMigration()` in `ClipboardDatabase.kt`. The history DB has auto migrations to v4, but no migration-test helper coverage was found; the files DB is v2 with no explicit migration path. Room's migration guidance treats destructive fallback as data-dropping behavior, which is too risky for pinned clipboard text, sensitivity flags, and provider media metadata.
- [Verified] `verifyDataExtractionRules` in `app/build.gradle.kts` only substring-checks the whole XML file even though its failure copy says each identifier must appear inside both `<cloud-backup>` and `<device-transfer>`. It also omits the learned n-gram stores, SwiftKey trace files, sync identity, and diagnostics that `data_extraction_rules.xml` explicitly excludes.
- [Verified] `scripts/verify-addon-apk.sh` points maintainers to `docs/addons/apk-validation.md`, but no `docs/addons/` file exists. The addon contract is present in `AddonContract.kt`, `AddonEnumerator.kt`, and `DictionaryPackDescriptor.kt`; author-facing proof is missing.
- [Verified] `AddonProvenanceReport.kt` can render stable plain text and JSON provenance, but `AddonsSettingsScreen.kt` only surfaces status, descriptor rows, trust controls, and fingerprints. Users cannot copy the full report before trusting or auditing an addon.
- [Verified] `PrivacyPostureScreen.kt` checks the installed manifest for `INTERNET` and links source/release verification pages, but it has no one-tap export of the current no-network/signature/version/addon-proof bundle.
- [Verified] Current guardrails are strong: merged-manifest no-network check, data-extraction rules, encrypted dictionary exports, staged crash reports, OSV high/critical release gate, release Roborazzi, 16 KB native alignment guard, SLSA attestation, SPDX SBOM, and reproducible APK check.

## Architecture Assessment
- Release/publication boundary: `.github/workflows/release.yml`, `scripts/check-release-front-door.sh`, README, fastlane changelogs, and GitHub Releases need one stricter source-of-truth rule. Advisory mismatch is acceptable before creating a release; it is not acceptable on normal CI after README claims the release exists.
- Documentation boundary: the Markdown ignore policy must match the public trust surface. Any doc linked by README, CI, release scripts, or research should either be tracked explicitly or treated as local-only and removed from public evidence.
- Data-safety boundary: clipboard history and provider metadata need row-preserving Room migrations and migration tests; destructive fallback should not be present in production builders for user data stores.
- Backup/privacy boundary: data-extraction verification should parse XML sections and domain/path pairs so cloud and D2D exclusions cannot drift independently.
- Addon boundary: the runtime enrollment path is mature, but missing docs/fixtures and missing UI export create a trust gap for third-party addon authors and reviewers.
- Layout boundary: the app already imports KLC/macOS Keylayout/Keyman LDML and consumes JSON keyboard assets; the active visual editor roadmap item should land on top of a machine validator for duplicate IDs, invalid refs, unreachable keys, row-width drift, and preview regressions.
- Editor reliability: competitor issue trackers show current pain around password suggestion leaks, Bluetooth PIN enter keys, Flutter undo/redo shortcuts, Teams/no-keyboard fields, and autocapitalization mistakes. SwiftFloris has many focused tests, but it should add an EditorInfo replay suite around sensitive fields and IME action handling before these become live reports.
- Test and documentation gaps: no strict public-release freshness gate; public trust docs are ignored by default; no link-drift gate for trust docs; no clipboard Room migration tests; data-extraction verification is substring-based; no addon APK fixture project; no layout JSON lint command; no exported privacy proof bundle test.
- Category coverage: security, privacy, distribution, docs, plugin ecosystem, testing, mobile, offline/resilience, migration/upgrade strategy, and i18n are represented in the prioritized work above. Multi-user collaboration is not a fit for a system IME; observability is already strong through crash reports, local audit logs, benchmark reports, and release OSV/SBOM evidence, so the only new observability item is user-exportable proof.

## Rejected Ideas
- Cloud sync or GIF search: rejected because the project philosophy and README require no `INTERNET` permission; source: Citizen Lab keyboard research and README.
- Bundling closed-source swipe libraries: rejected because HeliBoard's closed-library problem would undermine SwiftFloris's trust promise; source: HeliBoard/FOSS community threads.
- FUTO Swipe or NLnet gesture library integration now: rejected for active roadmap because licensing/release status remains externally gated and is already represented in blocked planning.
- Full CJK/Rime/fcitx engine in the base app: rejected because runtime size, native complexity, and data licensing conflict with the current lightweight base; source: fcitx5-android and Trime architecture.
- Cloud AI grammar/rewrite assistant: rejected because Samsung/Grammarly-style value is real but the network/account model conflicts with SwiftFloris; keep AI work addon-only and opt-in.
- In-app self-updater: rejected because Obtainium/F-Droid/GitHub Releases already cover updates and self-update increases supply-chain risk.
- New sync transport implementation: rejected for active roadmap because transport selection is already blocked separately; sealed envelopes and SAF import/export exist.
- Immediate AGP/Room major migrations: rejected because current AGP 9.2.1 and Room 2.8.4 are already current enough; major future migrations should stay dependency-gated, not feature-led.
- Switch Access row-column scanning: rejected for active roadmap because device validation is already hardware-gated in blocked planning; static accessibility coverage remains useful but should not pretend to replace real Switch Access QA.
- Active glide-cap tuning: rejected for this additive pass because the public glide benchmark harness and FUTO dataset evaluation are already represented in `Roadmap_Blocked.md`; the hard-coded `MAX_SIZE = 500` TODO is not enough to split a second active roadmap item without the external trace baseline.
- Han language-pack database locking: rejected for active roadmap because `HanShapeBasedLanguageProvider.loadLanguagePacksFor()` already serializes language-pack loads with `loadLock`; keep watching crash reports before adding more locking work.
- Backup-rule rewrite: rejected because `backup_rules.xml` and `data_extraction_rules.xml` are already allowlist/exclude based. The needed work is verifier precision, not a new backup policy.

## Sources
### OSS Competitors
- https://github.com/florisboard/florisboard
- https://github.com/HeliBorg/HeliBoard
- https://github.com/futo-org/android-keyboard
- https://keyboard.futo.org/
- https://github.com/AnySoftKeyboard/AnySoftKeyboard
- https://anysoftkeyboard.github.io/languages/
- https://github.com/fcitx5-android/fcitx5-android
- https://github.com/osfans/trime
- https://github.com/Julow/Unexpected-Keyboard
- https://github.com/dessalines/thumb-key
- https://github.com/keymanapp/keyman
- https://github.com/espanso/espanso

### Commercial and Community
- https://support.google.com/gboard/answer/11197787?hl=en
- https://support.microsoft.com/en-us/swiftkey
- https://support.microsoft.com/en-us/topic/account-a3c38581-903f-4d22-a388-cc13c7debf0e
- https://www.samsung.com/us/support/answer/ANS10000943/
- https://www.grammarly.com/mobile
- https://citizenlab.ca/research/vulnerabilities-across-keyboard-apps-reveal-keystrokes-to-network-eavesdroppers/
- https://discuss.privacyguides.net/t/recommend-open-source-android-keyboards/17808
- https://discuss.techlore.tech/t/i-tried-11-mobile-keyboards/10627

### Platform, Standards, and Dependencies
- https://developer.android.com/developer-verification
- https://f-droid.org/en/2026/02/24/open-letter-opposing-developer-verification.html
- https://f-droid.org/en/docs/Reproducible_Builds/
- https://f-droid.org/en/2025/03/04/even-my-keyboard-is-built-reproducibly.html
- https://developer.android.com/identity/autofill/ime-autofill
- https://developer.android.com/guide/practices/page-sizes
- https://developer.android.com/build/releases/agp-9-2-0-release-notes
- https://developer.android.com/jetpack/androidx/releases/room
- https://developer.android.com/training/data-storage/room/migrating-db-versions
- https://developer.android.com/privacy-and-security/risks/backup-best-practices

## Open Questions
- Was the `v1.9.49`-`v1.9.52` public release lag intentional, or did the release workflow fail after README/fastlane/version bumps?
- Should the release-channel freshness gate fail only on `master`/scheduled CI, or also on every PR after the release workflow creates a tag?
- Should `docs/THREAT_MODEL.md` and `docs/PRIVACY_AND_AI.md` become tracked public docs, or should README/CI stop treating them as public trust surfaces?
- Which addon type should get the first complete fixture project: dictionary pack, theme pack, layout pack, or smart-compose addon?

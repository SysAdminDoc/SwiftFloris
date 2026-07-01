# Research — SwiftFloris

## Executive Summary

SwiftFloris is a privacy-first Android keyboard fork that tries to preserve the familiar Gboard/SwiftKey typing surface while keeping the base APK offline, account-free, telemetry-free, and locally auditable. Its strongest current shape is not a single feature but the release-trust system: no `INTERNET`, encrypted local stores, addon validation, reproducible-build preparation, privacy/AI disclosure, Roborazzi and replay tests, and a broad settings surface. Highest-value opportunities, in order: gate the checked-in F-Droid YAML against Gradle metadata; enforce the documented addon bundle-size cap at runtime; add a Kotlin build-cache CVE guard until a stable Kotlin/KSP upgrade lands; keep the existing ignored root crash-log, candidate-spacing, runBlocking-budget, privacy-audit export, targetSdk 37, crash-template, and Emoji 17 roadmap work; correct the in-app crash dialog identity/redaction flow; and fix contributor docs that still name the pre-migration debug package ID.

## Product Map

- Core workflows: first-run IME enable/select/import; settings for languages, typing, themes, privacy, backup/restore, addons, MCP/Tasker, voice handoff, clipboard, media, and diagnostics; daily typing with candidates/autocorrect/glide/CJK/emoji/stickers; local evidence export and release verification.
- User personas: privacy-focused Android users, SwiftKey/Gboard migrators, multilingual and CJK users, accessibility/physical-keyboard/tablet users, addon authors, F-Droid/Obtainium reviewers, and maintainers triaging device-only IME failures.
- Platforms and distribution: Android 8+ APK, minSdk 26, targetSdk 36, compileSdk 37, GitHub Releases/Obtainium canonical channel, prepared F-Droid metadata, no Google Play dependency by design.
- Key integrations and data flows: Room/SQLCipher local stores, Tink-protected secrets, SAF import/export, local audit JSON, signature-permission addons, external FUTO/voice IME handoff, no-network manifest gates, local release-evidence bundle, no cloud transport in the base app.

## Competitive Landscape

- HeliBoard/OpenBoard: strong offline trust and customization; SwiftFloris should learn from their simple privacy positioning and broad layout/dictionary expectations, while avoiding closed glide dependencies in the base app.
- FUTO Keyboard/FUTO Swipe: closest modern typing-quality comparator for offline prediction, gesture typing, and voice adjacency; SwiftFloris should keep local replay/benchmark evidence and licensing review before importing any model or engine.
- AnySoftKeyboard: mature language/addon ecosystem and physical-keyboard history; SwiftFloris should copy automated addon release checks and avoid relying on README-only package rules.
- Fcitx5 Android, Trime/Rime, and Keyman: best CJK/schema/keyboard-package analogues; SwiftFloris should keep complex engines behind provider/addon contracts and make candidate commit semantics provider-owned before adding more data.
- Unexpected Keyboard, Thumb-Key, Fossify Keyboard, and FOSS app lists: prove privacy keyboards can win through focused ergonomics, F-Droid availability, and clear constraints; SwiftFloris should keep its richer surface but make trust claims mechanically verifiable.
- Gboard, Microsoft SwiftKey, Samsung Keyboard, and Grammarly: set user expectations for glide, multilingual UX, clipboard/media, voice, writing assistance, and migration; SwiftFloris should copy only local, inspectable interactions and reject cloud/account/telemetry parity.
- F-Droid and Obtainium: distribution trust is a product feature for a keyboard; SwiftFloris should treat source metadata, signing expectations, and reproducible-build notes as release gates, not side docs.

## Security, Privacy, and Reliability

- Verified: `fdroid/io.github.sysadmindoc.swiftfloris.yml` is still pinned to `1.9.52` / `2101` / `v1.9.52`, while `gradle.properties` and `docs/REPRODUCIBLE_BUILDS.md` are `1.9.53` / `2102`; `scripts/check-public-doc-version-pins.py` checks the docs stanza but not the checked-in F-Droid YAML.
- Verified: F-Droid build metadata uses build `commit`, `versionName`, `versionCode`, `CurrentVersion`, and `CurrentVersionCode`, so stale YAML can mislead fdroiddata review even when the README/docs are current.
- Verified: `docs/addons/apk-validation.md`, `scripts/verify-addon-apk.sh`, and `AddonContract.ADDON_MAX_BUNDLE_BYTES` promise a 64 MiB addon cap, but `AddonEnumerator.evaluate()` reports `bundleSizeBytes = 0L`, so installed third-party addons can bypass the runtime cap if they do not run the release script.
- Verified: Kotlin `2.4.0` is in `gradle/libs.versions.toml`; NVD `CVE-2026-53914` affects Kotlin before `2.4.20` via build-cache metadata deserialization. The repo mitigates release/repro builds with `--no-build-cache --rerun-tasks -Dorg.gradle.caching=false`, but no local gate fails if someone re-enables Gradle caching before the stable Kotlin/KSP path exists.
- Verified: existing active roadmap items remain valid and should not be duplicated: ignored root crash/replay logs; targetSdk 37 shadow preflight; privacy-audit save/share; Emoji 17 parser dry-run; provider-owned trailing-space policy; `runBlocking` hot-path budgets; crash-template environment/redaction fields; stale `RESEARCH_FEATURE_PLAN.md` references.
- Verified: `CrashDialogActivity.kt` still generates `- FlorisBoard ...` in the copied crash report even though public docs and templates now say SwiftFloris; this is user-visible observability drift separate from the existing GitHub crash-template item.
- Verified: `.github/PULL_REQUEST_TEMPLATE.md` tells contributors debug APKs install as `dev.patrickgold.florisboard.debug`, while `app/build.gradle.kts` sets release `applicationId = "io.github.sysadmindoc.swiftfloris"` plus `.debug`; the contributor instruction is stale.

## Architecture Assessment

- Release evidence should remain the central trust aggregator; add F-Droid YAML and Kotlin build-cache checks there rather than creating a parallel verification path.
- Addon safety should be enforced in the runtime enumerator as well as the release script, because third-party addon authors may not run SwiftFloris validation before installation.
- `SuggestionProvider`/`SuggestionCandidate` should own candidate side effects such as trailing spaces; `KeyboardManager` should not infer Latin autocorrect behavior for CJK, emoji, media, snippet, or addon candidates.
- The existing `runBlocking` allowlist is a useful ratchet but needs risk classification and latency budgets for IME hot paths, where per-keystroke stalls are user-visible.
- Crash reporting is both observability and privacy UI; align app-generated crash text, issue-template fields, and redaction guidance so maintainers get actionable logs without typed content.
- Dependency posture is stable except Kotlin build-cache risk: AGP, Gradle, Room, SQLCipher, Tink, Compose BOM, and Roborazzi are already on current/recent lines found in the repo and dependency docs.
- Category coverage: security, accessibility, i18n/l10n, observability, testing, docs, distribution/packaging, plugin/addon ecosystem, mobile/large-screen, offline/resilience, migration, and upgrade strategy were reviewed. Multi-user/cloud collaboration remains intentionally excluded because it contradicts the local single-user keyboard trust model.

## Rejected Ideas

- Cloud AI, online GIF/search, account sync, or telemetry-backed prediction: rejected from Gboard/SwiftKey/Samsung/Grammarly research because it violates SwiftFloris's no-network base-app invariant.
- Immediate Kotlin `2.4.20-Beta1` upgrade: rejected because the security fix target is clear but the available newer compiler/KSP path is not yet stable enough for a production keyboard release.
- Embed Fcitx5/Rime/Keyman runtimes in `:app`: rejected because heavy schemas, native/runtime size, and licensing complexity belong behind audited addon/provider contracts.
- Add GitHub Actions or remote SLSA release automation: rejected because the project policy and current release design rely on local builds, local evidence, and direct artifact publication.
- Full Emoji 17/CLDR asset refresh now: rejected as a duplicate of existing Emoji 17 parser dry-run work until the project verifies which CLDR release contains the exact production search/order data it wants to ship.
- Broad dependency-upgrade sweep: rejected because current evidence points to one actionable Kotlin build-cache guard, not a general stale-dependency problem.
- Weblate/localization program: rejected for this roadmap pass because competitors use translation infrastructure, but no concrete SwiftFloris localization pipeline failure was found.

## Sources

OSS keyboards and adjacent projects:
- https://github.com/florisboard/florisboard
- https://github.com/HeliBorg/HeliBoard
- https://github.com/openboard-team/openboard
- https://github.com/futo-org/android-keyboard
- https://swipe.futo.tech/
- https://github.com/AnySoftKeyboard/AnySoftKeyboard
- https://github.com/fcitx5-android/fcitx5-android
- https://github.com/osfans/trime
- https://github.com/keymanapp/keyman
- https://github.com/Julow/Unexpected-Keyboard
- https://github.com/dessalines/thumb-key
- https://albertomosconi.github.io/foss-apps/categories/keyboards.html

Commercial and community:
- https://support.google.com/gboard/answer/9058584
- https://security.googleblog.com/2020/10/privacy-preserving-smart-input-with.html
- https://support.microsoft.com/en-us/swiftkey-keyboard/account
- https://support.microsoft.com/en-us/swiftkey-keyboard/swiftkey-backup-and-sync-with-onedrive
- https://www.samsung.com/us/support/answer/ANS10000943/
- https://support.grammarly.com/hc/en-us/articles/15606282682637-Grammarly-for-Android-user-guide
- https://discuss.privacyguides.net/t/recommend-open-source-android-keyboards/17808

Platform, standards, security, dependencies, and distribution:
- https://developer.android.com/reference/android/view/inputmethod/TextAttribute.Builder#setTextSuggestionSelected(boolean)
- https://developer.android.com/about/versions/17/behavior-changes-17
- https://android-developers.googleblog.com/2026/02/prepare-your-app-for-resizability-and.html
- https://developer.android.com/guide/practices/page-sizes
- https://developer.android.com/identity/data/autobackup
- https://www.unicode.org/Public/17.0.0/emoji/emoji-test.txt
- https://www.unicode.org/reports/tr35/tr35-keyboards.html
- https://nvd.nist.gov/vuln/detail/CVE-2026-53914
- https://kotlinlang.org/docs/whatsnew-eap.html
- https://f-droid.org/en/docs/Build_Metadata_Reference/
- https://f-droid.org/en/docs/Submitting_to_F-Droid_Quick_Start_Guide/

## Open Questions

- No public-research question blocks prioritization. Stable Kotlin/KSP compatibility, final F-Droid reviewer preferences, release-key disclosure policy, hardware-only validation, FUTO engine licensing/model decisions, and production CJK data sourcing are implementation-time constraints rather than research gaps.

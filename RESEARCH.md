# Research — SwiftFloris

## Executive Summary

SwiftFloris is a Kotlin/Compose Android keyboard fork that is strongest when it treats privacy, distribution trust, and local verifiability as product features: Android 8+ support, no `INTERNET` permission, local-only dictionaries/snippets/clipboard history, addon trust gates, release evidence, Roborazzi baselines, and explicit F-Droid/Obtainium positioning. The highest-value direction is not chasing cloud keyboard parity; it is closing the remaining public trust gaps and hardening local extension/diagnostics seams before adding more typing surface. Priority opportunities: make public version claims fail when no tag/GitHub Release exists; fix and gate the stale Obtainium manifests; remove inherited upstream funding metadata from fork identity surfaces; make `EditorInfo` diagnostics useful without leaking private content; serialize Han SQLite lifecycle swaps; harden Snygg `flex:/` URI parsing; add a freshness checker for blocked-roadmap entries tied to now-closed issues/releases; and extend gesture sensitivity controls beyond glide where competitor users report repeated friction.

## Product Map

- Core workflows: first-run IME enable/select/import; daily typing with autocorrect, suggestions, glide, CJK skeleton, emoji, stickers, snippets, clipboard, voice handoff, Tasker/MCP, and addon dictionaries; settings for privacy, languages, gestures, themes, backup/restore, diagnostics, and distribution trust.
- User personas: privacy-focused Android users leaving Gboard/SwiftKey; multilingual/CJK typists; users who need offline snippets, clipboard, or local migration; accessibility and hardware-keyboard users; addon authors; F-Droid/Obtainium reviewers; maintainers diagnosing device-only IME bugs.
- Platforms and distribution: Android APK, minSdk 26, targetSdk 36, compileSdk 37, GitHub Releases as canonical channel, Obtainium auto-update subscription, prepared F-Droid metadata, no Google Play dependency by design.
- Key integrations and data flows: Room/SQLCipher local stores, Tink-protected secrets, SAF import/export, local release evidence, signature-permission addons, external voice IME handoff, no-network manifest gates, local privacy audit JSON, no cloud transport in the base app.

## Competitive Landscape

- FlorisBoard: upstream source and feature baseline; SwiftFloris should keep cherry-pick ergonomics but mechanically guard every public fork-identity surface that users or reviewers see.
- HeliBoard/OpenBoard: strong offline privacy positioning and F-Droid credibility; SwiftFloris should learn from simple trust copy and dictionary/layout expectations, while avoiding closed glide dependencies in the base APK.
- FUTO Keyboard/FUTO Swipe: closest modern offline typing-quality comparator; SwiftFloris should keep benchmark/replay evidence and license review before any engine reuse, and should copy the user-facing sensitivity polish that does not require cloud or account services.
- AnySoftKeyboard: mature language/addon ecosystem and long-standing keyboard customization; SwiftFloris should copy addon/package verification rigor and avoid README-only package rules.
- Fcitx5 Android, Trime/Rime, and Keyman: best analogues for CJK/schema engines and pluggable keyboard packages; SwiftFloris should keep complex engines behind provider/addon contracts and fix Han provider lifecycle safety before loading production data.
- Gboard, Microsoft SwiftKey, Samsung Keyboard, and Grammarly: set user expectations for glide, voice, clipboard, style/grammar assistance, and writing UX; SwiftFloris should copy local, inspectable interaction patterns and reject account/cloud/telemetry parity.
- Obtainium and F-Droid: distribution trust is part of the keyboard product; SwiftFloris should treat app IDs, release tags, signing paths, metadata recipes, and update subscriptions as release gates, not side docs.

## Security, Privacy, and Reliability

- Verified: `README.md`, `gradle.properties`, Fastlane changelog `2103.txt`, and F-Droid metadata claim `v1.9.54`, but `gh release list --repo SysAdminDoc/SwiftFloris` shows latest public GitHub Release `v1.9.53`, `gh release view v1.9.54` returns "release not found", and no local or remote `v1.9.54` tag exists. `scripts/check-release-front-door.sh` treats this as advisory unless `--strict`, and `scripts/release-evidence.ps1` does not use `-StrictRelease` by default.
- Verified: `fastlane/obtainium/stable.json` and `fastlane/obtainium/preview.json` still point to `dev.patrickgold.florisboard`, `https://github.com/florisboard/florisboard`, author `florisboard`, and names `FlorisBoard Stable/Preview`, while `README.md` documents `io.github.sysadmindoc.swiftfloris` and the `SysAdminDoc/SwiftFloris` GitHub channel. Obtainium tracks release sources directly, so stale manifests can subscribe users to the wrong app/repo.
- Verified: `.github/FUNDING.yml` still routes sponsorship to upstream `patrickgold` GitHub/LiberaPay/PayPal handles. This contradicts the fork identity and donation-removal rules, and `scripts/check-fork-identity.sh` currently does not inspect funding or Obtainium surfaces.
- Verified: `app/src/main/kotlin/dev/patrickgold/florisboard/lib/util/DebugSummarizeUtils.kt` lists only `EditorInfo.extras` keys because of a TODO around unsafe `Bundle` value access. Crash/debug reports lose useful host-context data even though the repo has redaction posture and issue-template work for diagnostics.
- Verified: `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/LanguagePackExtension.kt` opens/closes `hanShapeBasedSQLiteDatabase` with a TODO for locking. A CJK language-pack reload can race readers or close the handle during lookup once production Han data is installed.
- Verified: `lib/snygg/src/main/kotlin/org/florisboard/lib/snygg/value/SnyggUriValue.kt` still uses a TODO regex for `flex:/` URI values and normalizes via `URI.create`. Theme font/image import is a local asset trust boundary and should reject traversal/control/malformed paths explicitly.
- Verified: `Roadmap_Blocked.md` still says issue #9 requires release/human closeout, but GitHub issue #9 is closed as of 2026-06-25 and public releases have advanced beyond `v1.9.48`. The blocked-roadmap surface needs freshness automation; this research pass did not edit it because only `RESEARCH.md` and `ROADMAP.md` may be changed.
- Likely: gesture sensitivity beyond glide is still uneven. The repo has `glide.sensitivity` and touch calibration, but competitor/user signals repeatedly complain about spacebar cursor, delete, language-switch, and swipe conflict sensitivity in FUTO and Fcitx5 issues.

## Architecture Assessment

- Release evidence should remain the central trust aggregator. Add tag/GitHub Release strictness, Obtainium manifest checks, and fork funding checks there instead of creating a parallel release checklist.
- Fork identity is broader than package ID: app ID, README install links, Fastlane metadata, Obtainium subscription JSON, funding metadata, and GitHub Releases must all agree or users can install, fund, or trust the wrong project.
- Diagnostics should use typed/redacted summaries rather than raw `Bundle.toString()` or key-only output; `DebugSummarizeUtils.kt` is the right boundary because it already owns `EditorInfo` crash/support rendering.
- Han language packs should expose a safe database-handle lifecycle before production CJK data moves out of `Roadmap_Blocked.md`; a small synchronized/atomic holder plus reload/unload tests is lower risk than debugging intermittent addon crashes later.
- Snygg URI validation belongs in the value encoder/tests, not only in downstream asset resolvers, because invalid theme values should be rejected before UI resolution.
- Room 3.0, Kotlin 2.4.20, FUTO Swipe, voice runtime, Emoji 17 production assets, developer verification, and CJK production data remain tracked or blocked elsewhere; do not duplicate them in the active roadmap.
- Category coverage: security, privacy, accessibility, i18n/l10n, observability, testing, docs, distribution/packaging, plugin/addon ecosystem, mobile/large-screen, offline/resilience, migration, and upgrade strategy were reviewed. Multi-user/cloud collaboration remains intentionally excluded because it contradicts the local single-user keyboard trust model.

## Rejected Ideas

- Cloud AI, account sync, cloud clipboard, online GIF/search, telemetry-backed prediction, and server grammar rewriting: rejected from Gboard/SwiftKey/Samsung/Grammarly research because the base app has no `INTERNET` permission and no accounts by design.
- Embed FUTO Swipe immediately: rejected because the dataset/license review and benchmark run are already in `Roadmap_Blocked.md`; only local sensitivity/control polish is currently unblocked.
- Ship local Whisper/Vosk/Bergamot runtimes in `:app`: rejected because runtime size, licenses, and model packaging belong in signed addons and are already blocked on external packaging decisions.
- Embed Fcitx5/Rime/Keyman runtimes directly in the base APK: rejected because heavy schemas/native engines conflict with the small, auditable base-app model; provider/addon contracts are the right path.
- Room 3.0 migration now: rejected as active work because `Roadmap_Blocked.md` already tracks it and the migration requires a deliberate SupportSQLite/SQLCipher compatibility plan.
- Emoji 17 production refresh now: rejected as a duplicate of blocked Emoji 17 asset work; parser dry-run exists, but production data refresh waits on the chosen CLDR/asset generation source.
- Google Play publication: rejected because `README.md` explicitly positions GitHub Releases/Obtainium/F-Droid as the trust path and avoids Play target-SDK/Integrity tradeoffs.
- GitHub Actions/Dependabot release automation: rejected because repo policy requires local builds, manual dependency updates, and direct artifact publication.

## Sources

OSS keyboards and distribution:
- https://github.com/florisboard/florisboard
- https://github.com/Helium314/HeliBoard
- https://github.com/futo-org/android-keyboard
- https://github.com/AnySoftKeyboard/AnySoftKeyboard
- https://github.com/fcitx5-android/fcitx5-android
- https://github.com/osfans/trime
- https://github.com/keymanapp/keyman
- https://github.com/ImranR98/Obtainium
- https://f-droid.org/en/docs/Build_Metadata_Reference/
- https://f-droid.org/en/docs/Submitting_to_F-Droid_Quick_Start_Guide/

Commercial and community:
- https://play.google.com/store/apps/details?id=com.google.android.inputmethod.latin
- https://play.google.com/store/apps/details?id=com.touchtype.swiftkey
- https://support.microsoft.com/en-us/topic/microsoft-swiftkey-keyboard-privacy-questions-and-your-data-07e13677-6b38-4ad0-bad0-d41207cab6de
- https://support.microsoft.com/en-us/swiftkey-keyboard/how-to-use-microsoft-swiftkey-keyboard-to-copy-and-paste-text-between-swiftkey-and-windows
- https://www.samsung.com/latin_en/support/mobile-devices/an-overview-of-the-enhancement-writing-assist-when-sending-or-receiving-messages-on-the-galaxy-s24/
- https://www.grammarly.com/mobile/android
- https://discuss.privacyguides.net/t/heliboard-offline-keyboard-for-android/28093
- https://forum.f-droid.org/t/best-foss-keyboard/24671
- https://www.reddit.com/r/degoogle/comments/1ph4lqa/any_open_source_keyboards_recommendation_i_dont/
- https://www.reddit.com/r/degoogle/comments/1nogp7b/recommendation_for_privacy_feature_based_keyboard/

Platform, standards, dependencies, and security:
- https://developer.android.com/about/versions/17/behavior-changes-17
- https://developer.android.com/guide/practices/page-sizes
- https://developer.android.com/developer-verification
- https://support.google.com/android-developer-console/answer/16650243
- https://developer.android.com/develop/ui/views/touch-and-input/creating-input-method
- https://developer.android.com/jetpack/androidx/releases/room3
- https://developer.android.com/build/releases/agp-9-2-0-release-notes
- https://nvd.nist.gov/vuln/detail/CVE-2026-53914
- https://www.zetetic.net/blog/2026/05/12/sqlcipher-4.16.0-release/
- https://unicode.org/emoji/charts-17.0/

## Open Questions

- No public-research question blocks prioritization. Remaining implementation constraints are known gates: release signing/publishing authority, final Kotlin/KSP availability, F-Droid reviewer feedback, hardware-only accessibility validation, FUTO dataset/license decisions, and production CJK data licensing.

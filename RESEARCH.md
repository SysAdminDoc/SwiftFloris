# Research: SwiftFloris

Date: 2026-09-25. This report replaces the 2026-09-04 research pass after a fresh review of the repository, build, tracker, upstream project, Android platform changes, dependencies, security advisories, competitors, community discussions, adjacent tools, and current research literature.

## Executive Decision

SwiftFloris should keep competing as the trustworthy, offline keyboard that people can inspect, back up, and control. The evidence does not support adding a cloud account, a bundled language model, or another prediction engine. It supports a shorter and less glamorous sequence: clear stale blockers, finish the known resize and backup work, prove Android 17 behavior under memory pressure, get the existing release path into F-Droid, and track the parent project's Keyboard3 rewrite without merging it blindly.

The repository is already unusually capable. It has local tap and glide typing, multilingual Latin correction, encrypted clipboard and dictionary storage, explicit backup and restore, per-app profiles, themes, settings search, offline addon contracts, handwriting integration, and extensive policy tests. The remaining product risk is not feature count. It is whether the keyboard stays predictable across process restarts, unusual editors, low-memory devices, direct boot, stylus input, and platform upgrades (`app/src/main`, `scripts`, `docs`, and `ROADMAP.md`).

Highest-value decisions as of 2026-09-25:

1. Promote Kotlin 2.4.20 and KSP 2.3.12 from blocked research into active maintenance. Kotlin 2.4.10 is affected by CVE-2026-53914, while the final 2.4.20 release is available and returned no matching OSV record in the same package query. The temporary cache-disable guard reduces exposure but does not make the vulnerable compiler plugin current ([OSV advisory](https://osv.dev/vulnerability/GHSA-r937-wjx7-w2jp), [Kotlin releases](https://kotlinlang.org/docs/releases.html), `scripts/check-kotlin-build-cache-cve-guard.py`).
2. Finish the existing P1 reliability work before adding visible features. Issue #23 still describes resize overshoot, and the roadmap already has the traced fix. The manual backup publication check and the reliability-suspicion triage are also already active ([issue #23](https://github.com/SysAdminDoc/SwiftFloris/issues/23), `ROADMAP.md`).
3. Add one Android 17 memory-limiter work item. Android 17 can terminate every app under new per-app memory limits, and `FlorisImeService.onTrimMemory()` currently evicts only the sticker bitmap cache at the background threshold ([Android 17 all-app changes](https://developer.android.com/about/versions/17/behavior-changes-all), `app/src/main/kotlin/dev/patrickgold/florisboard/FlorisImeService.kt`).
4. Make blocked decisions expire. Several local blocker claims are now false or incomplete: Kotlin 2.4.20 is final, Room 3.0.3 is stable, Unicode 18 is final, the release signing certificate exists, and the initial developer-verification store list does not include F-Droid. The current live-doc checker cannot detect most of these transitions (`Roadmap_Blocked.md`, `scripts/check-live-doc-integrity.py`).
5. Keep a small compatibility ledger for the parent Keyboard3 rewrite. Upstream PR #3341 changes 147 files and core keyboard, extension, storage, hint, and input-action APIs. SwiftFloris has enough fork-specific behavior that a later bulk merge would be needlessly risky ([upstream PR #3341](https://github.com/florisboard/florisboard/pull/3341)).

## Method and Confidence

This pass used five evidence layers:

- Repository reconnaissance: source, tests, manifests, Gradle catalogs, policy scripts, documentation, release metadata, recent history, and current working-tree state.
- Tracker review: every SwiftFloris issue and discussion, relevant upstream FlorisBoard issues and pull requests, and current release assets.
- External change review: Android 17 behavior, developer verification, Unicode and CLDR, F-Droid 2.0, dependency releases, and vulnerability records.
- Market review: active open-source keyboards, commercial keyboard capabilities, community discussions, and adjacent offline writing tools.
- Research review: recent papers on swipe decoding, touch models, suggestion simulation, morphologically rich languages, and sustained on-device inference.

The build baseline was exercised on 2026-09-25 with `gradlew testDebugUnitTest` under JDK 21. It completed successfully in 11 minutes 47 seconds with 144 tasks, including unit, policy, resize and window, accessibility, and Roborazzi regression suites. That proves the checked-out baseline is internally consistent. It does not prove device-only behavior, F-Droid reproducibility, Android 17 runtime behavior, or interaction with third-party editors.

The idea harvest produced 126 raw candidates. Filtering was exclusive: 54 were already shipped or duplicates, 31 were already represented in `ROADMAP.md` or `Roadmap_Blocked.md`, 18 lacked enough evidence, 20 had a worse cost-to-value ratio than existing work, and 3 remained as new actions. Those three are the only additions appended to the roadmap.

Confidence labels used below:

- Verified: confirmed in current code, a local build, an official release record, or primary documentation.
- Strong: supported by multiple independent sources and consistent with current code.
- Watch: plausible and worth monitoring, but not ready to schedule.

## Current Product Snapshot

### Repository and architecture

- Verified: the project is a Kotlin and Jetpack Compose Android input method with minimum API 26, target API 36, compile API 37, version name 1.9.66, and version code 2115 (`gradle.properties`).
- Verified: the repository contains the app, benchmark, dictionary-pack sample, and Android, color, Compose, Kotlin, and Snygg libraries (`settings.gradle.kts`). The reviewed app and library tree contains roughly 1,466 source, test, and resource files, including more than 1,000 Kotlin files.
- Verified: the base app declares no `INTERNET` permission. It explicitly removes WorkManager's network-state permission and declares only the capabilities required for vibration, notifications, calendar-backed features, advanced-protection status, and signature-protected addon or MCP integration (`app/src/main/AndroidManifest.xml`).
- Verified: the input method is direct-boot aware, advertises stylus handwriting, and supports inline suggestions with touch exploration (`app/src/main/AndroidManifest.xml`, `FlorisImeService.kt`).
- Verified: encrypted clipboard and dictionary paths use SQLCipher and Tink, while backup, restore, migration, and addon enrollment have dedicated policy and test coverage (`app/src/main`, `scripts`, `docs/THREAT_MODEL.md`).
- Verified: production source has no broad unfinished-feature cluster. Remaining TODO markers are mostly allowlisted refactors or explicit design notes. The largest complexity centers are `TextKeyboardLayout.kt`, `KeyboardManager.kt`, `LatinLanguageProvider.kt`, and `UserDictionaryScreen.kt`.

### Distribution and adoption

- Verified: v1.9.66 was published on 2026-08-30 with a signed APK and checksum asset. The APK had 268 GitHub downloads when checked on 2026-09-25 ([release v1.9.66](https://github.com/SysAdminDoc/SwiftFloris/releases/tag/v1.9.66)).
- Verified: the release certificate SHA-256 is `dba1aa88e37b90155fca3135ca3b781de92c225107e47c9806e75bf88055fdd8`, and the APK digest is `d8ac31149f4b3007d8771d8c2d65dc7510de67e201f6c5cb1cf330ad9f9b644b` (release asset inspection and `fdroid/io.github.sysadmindoc.swiftfloris.yml`).
- Verified: SwiftFloris is not listed in the official F-Droid repository, and no fdroiddata submission was found. The repository has preparation material, but public installation still depends on GitHub ([F-Droid package index](https://f-droid.org/en/packages/), `fdroid/`).
- Verified: Fastlane store text exists for en-US, es-AR, es-ES, and pl-PL, while the app itself ships many more localized resources (`fastlane/metadata/android`, `app/src/main/res/values-*`).

### User signals

- Verified: the SwiftFloris tracker has one open defect with a concrete device report, issue #23. The preference-reset defect in issue #22 was fixed and closed on 2026-09-05 ([issue #22](https://github.com/SysAdminDoc/SwiftFloris/issues/22), [issue #23](https://github.com/SysAdminDoc/SwiftFloris/issues/23)).
- Strong: community demand remains centered on offline privacy, reliable swipe typing, multilingual prediction, and a usable escape from Gboard or SwiftKey. Community recommendations often mention FUTO Keyboard and HeliBoard, but reports still call out lag, haptics, swipe accuracy, and prediction quality ([privacy keyboard discussion](https://www.reddit.com/r/androidapps/comments/1uihuvb/whats_the_safest_alternative_to_gboard_or_the/), [SwiftKey replacement discussion](https://www.reddit.com/r/androidapps/comments/1ut34cf/looking_for_a_swiftkey_replacement/), [FOSS keyboard discussion](https://www.reddit.com/r/fossdroid/comments/1w0rdaj/best_foss_keyboard_in_2026/)).
- Verified: the project discussion asking how to install another language shows that capability discovery still matters even when language data exists ([discussion #21](https://github.com/SysAdminDoc/SwiftFloris/discussions/21)).

## What Changed Since 2026-09-04

### Dependencies and security

- Verified: Kotlin 2.4.20 became final on 2026-09-07. KSP 2.3.12 followed on 2026-09-09. The repository still pins Kotlin 2.4.10 and KSP 2.3.11 ([Kotlin 2.4.20 notes](https://kotlinlang.org/docs/whatsnew2420.html), [KSP releases](https://github.com/google/ksp/releases), `gradle/libs.versions.toml`).
- Verified: OSV reports CVE-2026-53914 for `org.jetbrains.kotlin:kotlin-gradle-plugin` 2.4.10. The issue is unsafe build-cache deserialization with code-execution impact. A same-package query for 2.4.20 returned no advisory on 2026-09-25 ([OSV](https://osv.dev/vulnerability/GHSA-r937-wjx7-w2jp), [NVD](https://nvd.nist.gov/vuln/detail/CVE-2026-53914)).
- Verified: Room 3.0.3 became stable on 2026-09-09. This clears the old “wait for stable” condition, but it does not make a Room 3 migration routine. The API and generated-code change still needs a deliberate branch and full encrypted-database migration exercise ([Room 3 releases](https://developer.android.com/jetpack/androidx/releases/room3)).
- Verified: Android Gradle Plugin 9.4.1 is stable, KSP is at 2.3.12, Navigation is at 2.10.2, Compose BOM is at 2026.09.00, UiAutomator is at 2.4.0, and SQLite is at 2.7.1. The repository's dependency-freshness policy does not yet reflect all of those releases ([AGP metadata](https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/maven-metadata.xml), [Navigation releases](https://developer.android.com/jetpack/androidx/releases/navigation), [Compose BOM](https://developer.android.com/develop/ui/compose/bom), [UiAutomator releases](https://developer.android.com/jetpack/androidx/releases/test-uiautomator), [AndroidX versions](https://developer.android.com/jetpack/androidx/versions)).
- Strong: the local Tink policy text is stale. Tink's tracked timing issue says releases through 1.21.0 were affected and records the fix on 2026-06-08. SwiftFloris uses 1.23.0, and an OSV query for that exact Android artifact returned no record. The policy should state the evidence and remove the claim that no patched release exists ([Tink issue #75](https://github.com/tink-crypto/tink-java/issues/75), [CVE record](https://www.tenable.com/cve/CVE-2026-15432), `.github/security-dependency-freshness.json`).

### Android platform

- Verified: Android 17 applies per-app memory limits to all apps. When the limiter kills a process, `ApplicationExitInfo` uses `REASON_OTHER` and a description containing `MemoryLimiter:AnonSwap`. Android documents `am memory-limiter manual`, `status`, and `ignore` for testing ([Android 17 all-app changes](https://developer.android.com/about/versions/17/behavior-changes-all)).
- Verified: Android 17 no longer restores IME visibility automatically after an unhandled configuration change. SwiftFloris already owns window and configuration behavior, but this must stay in the API 37 runtime matrix ([Android 17 all-app changes](https://developer.android.com/about/versions/17/behavior-changes-all), `FlorisImeService.kt`).
- Verified: Android 17 splits physical-keyboard password display from touch password display. The existing roadmap correctly keeps this as a device check because reading the documentation cannot prove where the platform masks characters in the full input path ([Android 17 target changes](https://developer.android.com/about/versions/17/behavior-changes-17), `ROADMAP.md`).
- Verified: Android 17's safer native dynamic-code-loading rule specifically addresses writable files loaded through `System.load()`. SQLCipher is loaded through packaged native libraries and `System.loadLibrary()`, so the published rule does not establish a SwiftFloris defect. Runtime database tests on API 37 remain prudent ([Android 17 target changes](https://developer.android.com/about/versions/17/behavior-changes-17), SQLCipher loader code under `app/src/main`).
- Verified: Android 17 requires `FLAG_SECURE` when an app wants to block content capture. Sensitive SwiftFloris windows and dialogs already use that flag, so no new roadmap item is needed ([Android 17 target changes](https://developer.android.com/about/versions/17/behavior-changes-17), `app/src/main/kotlin/dev/patrickgold/florisboard/ime/window`).

### Standards and distribution

- Verified: Unicode 18.0 was published on 2026-09-16 with 13,007 new characters and 9 new emoji. The Unicode side of the old blocker has cleared ([Unicode announcement](https://blog.unicode.org/2026/09/announcing-unicode-standard-version-180.html), [Unicode 18 specification](https://www.unicode.org/versions/Unicode18.0.0/)).
- Verified: CLDR 49 was still alpha on 2026-09-25. Locale and emoji asset regeneration should wait for the final CLDR data rather than mixing a final Unicode release with provisional locale data ([CLDR home](https://cldr.unicode.org/), [CLDR translation status](https://cldr.unicode.org/translation)).
- Verified: F-Droid 2.0 was announced on 2026-09-24. It improves search, translated-content discovery, update behavior, and installation flow. That makes translated listing metadata more valuable once SwiftFloris enters the repository ([F-Droid 2.0 announcement](https://f-droid.org/en/2026/09/24/f-droid-2.0-a-new-chapter-for-android-freedom.html)).
- Verified: Android developer verification begins on 2026-09-30 for participating stores in Brazil, Indonesia, Singapore, and Thailand. Google's named initial stores include Google Play, HONOR, OPPO, Galaxy Store, Palm Store, V-Appstore, and GetApps. F-Droid is not on that list. The repository's current “no pre-registration planned” posture is consistent with the published initial rollout, while the older local blocker saying to register before September is stale ([developer verification](https://developer.android.com/developer-verification), [rollout announcement](https://android-developers.googleblog.com/2026/03/android-developer-verification-rolling-out-to-all-developers.html)).

### Parent project

- Verified: FlorisBoard PR #3341 is a work-in-progress Keyboard3 integration touching 147 files, with about 3,140 additions and 2,764 deletions when reviewed on 2026-09-25. It changes keyboard storage, extension types, hints, input actions, and function-key behavior. Its build check was failing at that snapshot, so it is evidence to track, not code to merge ([PR #3341](https://github.com/florisboard/florisboard/pull/3341)).
- Strong: current upstream reports cluster around input reliability rather than visual novelty. Recent examples include stylus taps not registering, keys stopping after the first character, keyboard freeze during close, Vietnamese Telex placement, personal-dictionary priority, physical-keyboard compatibility, and fast-typing crashes ([#3350](https://github.com/florisboard/florisboard/issues/3350), [#3348](https://github.com/florisboard/florisboard/issues/3348), [#3342](https://github.com/florisboard/florisboard/issues/3342), [#3334](https://github.com/florisboard/florisboard/issues/3334), [#3332](https://github.com/florisboard/florisboard/issues/3332), [#3313](https://github.com/florisboard/florisboard/issues/3313), [#3311](https://github.com/florisboard/florisboard/issues/3311)).

## Ecosystem Findings

### Active open-source keyboards

- HeliBoard remains the largest direct offline competitor in this set, with release 4.1 published on 2026-08-30. Its strength is practical keyboard behavior and broad user familiarity, not a richer trust model ([HeliBoard](https://github.com/HeliBorg/HeliBoard), [v4.1](https://github.com/HeliBorg/HeliBoard/releases/tag/v4.1)).
- FUTO Keyboard remains the clearest benchmark for local voice and swipe quality. Release 0.1.30 added theme work, KASROZ, swipe switching, and a foldable fix. Its public swipe dataset is valuable for evaluation, while its model-weight license is not suitable for an F-Droid-distributed SwiftFloris build ([FUTO Keyboard](https://github.com/futo-org/android-keyboard), [v0.1.30](https://github.com/futo-org/android-keyboard/releases/tag/v0.1.30), [layout-agnostic decoder paper](https://arxiv.org/abs/2606.25247)).
- CleverKeys continues to move quickly on keyboard-aware typo scoring, dictionary scale, automatic spacing, swipe fixes, clean-link actions, import previews, and settings search. SwiftFloris already has equivalents for several of those ideas, so copying the feature list would create duplicate work ([CleverKeys](https://github.com/tribixbite/CleverKeys), [v1.5.0](https://github.com/tribixbite/CleverKeys/releases/tag/v1.5.0)).
- AnySoftKeyboard, Fossify Keyboard, Unexpected Keyboard, Fcitx5 for Android, Trime, and Thumb-Key all had recent repository activity when checked. Their diversity reinforces that keyboard maintenance is mostly compatibility work across layouts, editors, input sources, and lifecycle edges rather than one dominant architecture ([AnySoftKeyboard](https://github.com/AnySoftKeyboard/AnySoftKeyboard), [Fossify Keyboard](https://github.com/FossifyOrg/Keyboard), [Unexpected Keyboard](https://github.com/Julow/Unexpected-Keyboard), [Fcitx5 for Android](https://github.com/fcitx5-android/fcitx5-android), [Trime](https://github.com/osfans/trime), [Thumb-Key](https://github.com/dessalines/thumb-key)).
- Keyman remains the strongest reference for language-system breadth and durable keyboard packages. Its 2026 roadmap emphasizes package and platform work rather than a single universal prediction engine ([Keyman](https://github.com/keymanapp/keyman), [Keyman roadmap](https://blog.keyman.com/2026/03/keyman-roadmap-march-2026/)).

### Commercial keyboards

- Gboard's writing tools and handwriting support show where platform-backed keyboards are moving, but those features depend on device support and platform services. They are not evidence that SwiftFloris should bundle a general model ([Gboard writing tools](https://support.google.com/gboard/answer/16515540), [Gboard handwriting](https://support.google.com/gboard/answer/9108773)).
- Samsung Keyboard and Microsoft SwiftKey continue to package rewriting, tone, and assistance into account or platform ecosystems. SwiftFloris has a clearer distinction when it keeps the base keyboard offline and explicit about external handoffs ([Samsung Keyboard settings](https://www.samsung.com/us/support/answer/ANS10001592/), [Samsung writing assist](https://www.samsung.com/us/support/answer/ANS10000943/), [SwiftKey tone](https://support.microsoft.com/en-us/swiftkey-keyboard/how-to-use-tone-in-microsoft-swiftkey-keyboard)).

### Adjacent tools and research

- Expanda proves there is demand for an offline Android text expander with Espanso-compatible YAML. SwiftFloris already imports Espanso data inside the IME, so a separate text-expansion feature would duplicate shipped behavior ([Expanda](https://github.com/diegomarzaa/expanda), SwiftFloris importer code under `app/src/main`).
- Harper and LanguageTool show two possible grammar paths. Harper is local and open source; LanguageTool supports broad editor integration. The existing roadmap already requires an honest production path or removal of rewrite surfaces, so another grammar proposal would duplicate that decision ([Harper](https://github.com/Automattic/harper), [LanguageTool support](https://help.languagetool.org/hc/en-us/articles/39254502620183-Which-websites-and-applications-are-supported-by-LanguageTool), `ROADMAP.md`).
- Recent mobile-input research favors measurement and constrained models. KeySense reports better intentional-tap isolation, WSTypist simulates suggestion use, and work on compounds shows language-specific decoding matters. These findings support improving the existing scorecard and language behavior, not replacing the stack with a general model ([KeySense](https://arxiv.org/abs/2602.12432), [WSTypist](https://arxiv.org/abs/2602.06489), [compound handling](https://arxiv.org/abs/2201.06469)).
- Current on-device inference studies still report framework overhead, energy waste, memory pressure, and thermal limits. A keyboard is latency-sensitive and persistent, which makes a base-app language model a poor trade while deterministic local paths remain unfinished ([NPU bottleneck study](https://arxiv.org/abs/2607.05475), [sustained mobile load study](https://arxiv.org/abs/2603.23640), [ExecuTorch study](https://arxiv.org/abs/2605.08195), [Micro Language Models](https://arxiv.org/abs/2604.19642)).

## Gap Analysis

| Rank | Gap | Evidence | User impact | Confidence | Effort | Disposition |
|---:|---|---|---|---|---|---|
| 1 | Blocked decisions can remain stale after their evidence changes | Kotlin, Room, Unicode, signing, and verification entries aged past reality | High, because security and release work can remain needlessly parked | Verified | M | New P1 roadmap item |
| 2 | Resize behavior in issue #23 remains open | Local trace and public device report | High for users who customize keyboard size | Verified | M | Existing P1 item, do not duplicate |
| 3 | Android 17 can kill the IME under the new memory limiter, but recovery is not measured | Official platform behavior and current trim handling | High on memory-constrained or sustained-use devices | Verified | M | New P1 roadmap item |
| 4 | F-Droid distribution is prepared but not public | No official package, signed GitHub release exists | High for the privacy-focused audience | Verified | M | Existing blocked work, promote when recipe is ready |
| 5 | Backup publication still lacks the scheduled manual proof | Current roadmap and release posture | High because recovery claims need external proof | Verified | S | Existing P1 item |
| 6 | Parent Keyboard3 is changing contracts across 147 files | Upstream PR #3341 | Medium now, high when the parent work lands | Verified | M | New P2 roadmap item |
| 7 | Language installation and imported-pack identity can still be confusing | Discussion #21 and existing labels item | Medium | Verified | S | Existing P1 and P2 items |
| 8 | Physical keyboard, stylus, rich-editor, and API 37 behavior need real devices | Upstream reports and current synthetic replay coverage | Medium to high | Strong | M | Fold into existing controller and Android 17 checks |
| 9 | Store metadata covers four locales | Fastlane tree and F-Droid 2.0 discovery changes | Medium after F-Droid acceptance | Verified | S | Prepare after submission is accepted |
| 10 | Dependency policy trails current stable releases | Catalog versus official release feeds | Medium | Verified | S to L by dependency | Handle through maintenance and the new expiry gate |

## New Recommendations

### P1: Make blocked decisions expire

The local blocked file records useful reasoning, but its evidence can become false without changing any repository input that the current checker understands. A tracked evidence manifest should give each blocker a stable ID, last-reviewed date, recheck date, and primary evidence. Version-backed blockers should carry a machine-readable probe. The live-doc check should fail when evidence expires or a probe proves the blocking condition cleared.

This is distinct from the existing public-toolchain drift item. That item checks facts presented to users. This item checks internal scheduling decisions so work does not remain parked after the reason disappears.

Acceptance should cover a missing review date, an expired recheck date, a newly stable dependency, a closed tracker issue, and a release tag that satisfies a blocker. The current stale entries should be reconciled as part of the same change.

### P1: Prove Android 17 memory-limiter resilience

Create a measured baseline for first keyboard render, dictionary load, glide decoding, emoji search, clipboard history, and sticker decoding. Exercise API 37 with `am memory-limiter manual`, inspect `ApplicationExitInfo`, and verify that recoverable caches are dropped before durable state is at risk. The test should distinguish a limiter kill from an application crash and record enough context for support logs without collecting typed text.

The goal is not to keep an IME process alive at any cost. Android may kill it. The goal is deterministic reconstruction, no data loss, bounded caches, and diagnostics that name the platform event.

### P2: Maintain a parent Keyboard3 compatibility ledger

Track the parent commit or pull-request revision, the changed contracts SwiftFloris depends on, and one decision per contract: adopt, reimplement, or reject. Include storage, extension types, keyboard hints, input actions, function keys, and layout semantics. Update the ledger when the parent PR changes materially or lands.

A small import or mapping fixture should prove that parent keyboard metadata cannot silently change meaning when consumed by SwiftFloris. This turns future upstream work into reviewed increments instead of a single high-risk merge.

## Existing Work to Execute Before More Features

1. Fix resize self-amplification and close issue #23 with the synthetic regression already specified in `ROADMAP.md`.
2. Complete the manual backup publication verification. Recovery is a trust claim, not only a unit-test result.
3. Finish the reliability-suspicion triage and remove claims that have already been refuted.
4. Complete installed-language source hints and built-in versus imported pack labels. Discussion #21 shows why the distinction matters.
5. Finish the direct-boot behavior contract and testable keyboard-layout controller. Both improve several hardware and editor paths without adding another product surface.
6. Run the existing glide scorecard and FUTO trace harness before changing decoder behavior. The repository already has the measurement infrastructure that the prior report requested (`scripts/typing-quality-scorecard.py`, tap and glide fixtures, benchmark code).

## Cleared or Changed Blockers

- Kotlin: 2.4.20 final is available. Upgrade with KSP 2.3.12, run the full suite, and remove the temporary build-cache suppression only after the vulnerable version is gone.
- Room: 3.0.3 stable is available. Move from “wait for stable” to a migration design and encrypted-database proof. Do not combine it with unrelated dependency bumps.
- Unicode: 18.0 is final. Keep only the CLDR 49 final-data condition.
- Signing: v1.9.66 provides a real release certificate and artifact digest. Any “no release keystore” or “no published APK” blocker is obsolete.
- Developer verification: the 2026-09-30 initial participating-store list does not name F-Droid. Keep monitoring the global 2027 expansion, but remove the expired instruction to register before September.
- Tink: current 1.23.0 is newer than the release range named in the tracked timing issue. Correct the policy record and retain an exact-version advisory query.

## Ideas Rejected or Deferred

- Bundled general language model: rejected for the base app. Current mobile research still shows memory, thermal, energy, and framework costs, while the product has unresolved deterministic reliability work. Optional platform handoff can be reconsidered without weakening the no-network base.
- Cloud sync or account system: rejected. It would erase the clearest product distinction and create custody, authentication, retention, breach, and support obligations. Existing explicit backup and dormant CRDT contracts preserve future options without a service.
- Wholesale merge of Keyboard3 now: deferred. The upstream work is incomplete, broad, and failing its own build check at the reviewed revision. The compatibility ledger is the lower-risk action.
- Ship FUTO model weights: rejected for distribution compatibility. Use the MIT trace dataset for evaluation, not the restricted model weights.
- Another swipe-quality roadmap item: rejected as duplicate. The repository already has a scorecard, production decoder fixtures, and a FUTO corpus benchmark path.
- Another hostile-editor matrix: rejected as duplicate. `HostileEditorCandidateReplayTest` already covers rich editors, game-style editors, Flutter, and desktop physical-keyboard patterns synthetically. Real-device runs belong in the existing controller and API checks.
- Silent clean-link rewriting: rejected. The existing explicit preview and undo roadmap item is safer for signed, expiring, authenticated, and order-sensitive URLs.
- Separate text-expander feature: rejected as duplicate. Espanso import is already present.
- Broad visual redesign: deferred behind reliability work. The current roadmap already contains targeted spacing, Snygg fallback, honeycomb, and candidate-row cleanup with screenshot acceptance.
- Immediate CLDR 49 asset regeneration: deferred until final data. Mixing Unicode 18 final data with CLDR alpha would create churn and uncertain locale output.
- F-Droid metadata expansion before submission: deferred until the package path is accepted or has a live review. Once that happens, translated store text becomes a small, evidence-backed follow-up because F-Droid 2.0 searches translated content.

## Open Questions for the Next Evidence Pass

1. What peak proportional-set size and Java heap usage do first render, a large local dictionary, glide decoding, emoji search, clipboard history, and sticker decoding reach on 256 MB and 512 MB heap classes?
2. Does an API 37 emulator reproduce `MemoryLimiter:AnonSwap` during a realistic keyboard session, and does the service rebuild all ephemeral state after process recreation?
3. Can the current F-Droid recipe reproduce the signed v1.9.66 artifact byte for byte on the official builder image, or which input first diverges?
4. Which contracts from Keyboard3 survive review and merge, and which fork-specific behavior would be lost by direct adoption?
5. Does Android 17 mask physical-keyboard password input fully below the IME with both password settings toggled?
6. Which third-party editor produces the first mismatch against the existing synthetic hostile-editor replay suite?

## Source Register

Sources were checked on 2026-09-25 unless a publication date is stated above. Repository paths refer to commit `414c03b44` plus the pre-existing local working tree, which was not modified during reconnaissance.

### SwiftFloris and parent project

1. SwiftFloris repository: https://github.com/SysAdminDoc/SwiftFloris
2. SwiftFloris v1.9.66: https://github.com/SysAdminDoc/SwiftFloris/releases/tag/v1.9.66
3. SwiftFloris issue #22: https://github.com/SysAdminDoc/SwiftFloris/issues/22
4. SwiftFloris issue #23: https://github.com/SysAdminDoc/SwiftFloris/issues/23
5. SwiftFloris discussion #21: https://github.com/SysAdminDoc/SwiftFloris/discussions/21
6. FlorisBoard Keyboard3 PR #3341: https://github.com/florisboard/florisboard/pull/3341
7. FlorisBoard stylus issue #3350: https://github.com/florisboard/florisboard/issues/3350
8. FlorisBoard key-response issue #3348: https://github.com/florisboard/florisboard/issues/3348
9. FlorisBoard close-freeze issue #3342: https://github.com/florisboard/florisboard/issues/3342
10. FlorisBoard Telex issue #3334: https://github.com/florisboard/florisboard/issues/3334
11. FlorisBoard personal-dictionary issue #3332: https://github.com/florisboard/florisboard/issues/3332
12. FlorisBoard subtype-cycle issue #3328: https://github.com/florisboard/florisboard/issues/3328
13. FlorisBoard physical-keyboard issue #3313: https://github.com/florisboard/florisboard/issues/3313
14. FlorisBoard fast-typing issue #3311: https://github.com/florisboard/florisboard/issues/3311

### Android, standards, and distribution

15. Android 17 overview: https://developer.android.com/about/versions/17
16. Android 17 all-app behavior changes: https://developer.android.com/about/versions/17/behavior-changes-all
17. Android 17 target behavior changes: https://developer.android.com/about/versions/17/behavior-changes-17
18. Android 17 migration guide: https://developer.android.com/about/versions/17/migration
19. Android 17 release notes: https://developer.android.com/about/versions/17/release-notes
20. Android developer verification: https://developer.android.com/developer-verification
21. Developer verification rollout: https://android-developers.googleblog.com/2026/03/android-developer-verification-rolling-out-to-all-developers.html
22. Inline autofill for IMEs: https://developer.android.com/identity/autofill/ime-autofill
23. InputMethodService reference: https://developer.android.com/reference/android/inputmethodservice/InputMethodService
24. InputMethodInfo reference: https://developer.android.com/reference/android/view/inputmethod/InputMethodInfo
25. Stylus input in custom text editors: https://developer.android.com/develop/ui/views/touch-and-input/stylus-input/custom-text-editors
26. Unicode 18 announcement: https://blog.unicode.org/2026/09/announcing-unicode-standard-version-180.html
27. Unicode 18 specification: https://www.unicode.org/versions/Unicode18.0.0/
28. CLDR home and release status: https://cldr.unicode.org/
29. CLDR translation status: https://cldr.unicode.org/translation
30. F-Droid 2.0 announcement: https://f-droid.org/en/2026/09/24/f-droid-2.0-a-new-chapter-for-android-freedom.html
31. F-Droid package index: https://f-droid.org/en/packages/

### Dependencies and security

32. Android Gradle Plugin Maven metadata: https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/maven-metadata.xml
33. Android Gradle Plugin 9.4 notes: https://developer.android.com/build/releases/agp-9-4-0-release-notes
34. Kotlin releases: https://kotlinlang.org/docs/releases.html
35. Kotlin 2.4.20: https://kotlinlang.org/docs/whatsnew2420.html
36. KSP releases: https://github.com/google/ksp/releases
37. Room 3 releases: https://developer.android.com/jetpack/androidx/releases/room3
38. Navigation releases: https://developer.android.com/jetpack/androidx/releases/navigation
39. Compose BOM mapping: https://developer.android.com/develop/ui/compose/bom
40. Compose releases: https://developer.android.com/jetpack/androidx/releases/compose
41. UiAutomator releases: https://developer.android.com/jetpack/androidx/releases/test-uiautomator
42. AndroidX versions: https://developer.android.com/jetpack/androidx/versions
43. Gradle releases: https://gradle.org/releases/
44. OSV database: https://osv.dev/
45. Kotlin advisory GHSA-r937-wjx7-w2jp: https://osv.dev/vulnerability/GHSA-r937-wjx7-w2jp
46. CVE-2026-53914: https://nvd.nist.gov/vuln/detail/CVE-2026-53914
47. Tink timing issue #75: https://github.com/tink-crypto/tink-java/issues/75
48. CVE-2026-15432 record: https://www.tenable.com/cve/CVE-2026-15432

### Open-source and commercial keyboards

49. HeliBoard: https://github.com/HeliBorg/HeliBoard
50. HeliBoard v4.1: https://github.com/HeliBorg/HeliBoard/releases/tag/v4.1
51. FUTO Keyboard: https://github.com/futo-org/android-keyboard
52. FUTO Keyboard v0.1.30: https://github.com/futo-org/android-keyboard/releases/tag/v0.1.30
53. CleverKeys: https://github.com/tribixbite/CleverKeys
54. CleverKeys v1.5.0: https://github.com/tribixbite/CleverKeys/releases/tag/v1.5.0
55. AnySoftKeyboard: https://github.com/AnySoftKeyboard/AnySoftKeyboard
56. Fossify Keyboard: https://github.com/FossifyOrg/Keyboard
57. Unexpected Keyboard: https://github.com/Julow/Unexpected-Keyboard
58. Fcitx5 for Android: https://github.com/fcitx5-android/fcitx5-android
59. Trime: https://github.com/osfans/trime
60. Thumb-Key: https://github.com/dessalines/thumb-key
61. Keyman: https://github.com/keymanapp/keyman
62. Keyman 2026 roadmap: https://blog.keyman.com/2026/03/keyman-roadmap-march-2026/
63. Gboard writing tools: https://support.google.com/gboard/answer/16515540
64. Gboard handwriting: https://support.google.com/gboard/answer/9108773
65. Samsung Keyboard settings: https://www.samsung.com/us/support/answer/ANS10001592/
66. Samsung writing assist: https://www.samsung.com/us/support/answer/ANS10000943/
67. Microsoft SwiftKey tone: https://support.microsoft.com/en-us/swiftkey-keyboard/how-to-use-tone-in-microsoft-swiftkey-keyboard

### Community, adjacent tools, and research

68. Android privacy keyboard discussion: https://www.reddit.com/r/androidapps/comments/1uihuvb/whats_the_safest_alternative_to_gboard_or_the/
69. SwiftKey replacement discussion: https://www.reddit.com/r/androidapps/comments/1ut34cf/looking_for_a_swiftkey_replacement/
70. Android keyboard discussion: https://www.reddit.com/r/androidapps/comments/1v39j0m/whats_a_good_keyboard_app/
71. Free keyboard alternative discussion: https://www.reddit.com/r/androidapps/comments/1u9zvkr/free_alternative_to_gboard/
72. Privacy keyboard comparison: https://www.reddit.com/r/androidapps/comments/1sbo5f9/better_keyboard_for_privacy/
73. FOSS keyboard discussion: https://www.reddit.com/r/fossdroid/comments/1w0rdaj/best_foss_keyboard_in_2026/
74. Hacker News OpenBoard discussion: https://news.ycombinator.com/item?id=25669538
75. Compose custom IME question: https://stackoverflow.com/questions/79277838/using-custom-input-method-in-compose-1-7-0-and-above
76. Expanda: https://github.com/diegomarzaa/expanda
77. Harper: https://github.com/Automattic/harper
78. LanguageTool app support: https://help.languagetool.org/hc/en-us/articles/39254502620183-Which-websites-and-applications-are-supported-by-LanguageTool
79. Layout-agnostic swipe decoder: https://arxiv.org/abs/2606.25247
80. KeySense: https://arxiv.org/abs/2602.12432
81. WSTypist: https://arxiv.org/abs/2602.06489
82. Compound-aware prediction: https://arxiv.org/abs/2201.06469
83. On-device NPU bottlenecks: https://arxiv.org/abs/2607.05475
84. Sustained mobile inference limits: https://arxiv.org/abs/2603.23640
85. ExecuTorch evaluation: https://arxiv.org/abs/2605.08195
86. Micro Language Models: https://arxiv.org/abs/2604.19642
87. MobileLLM-Flash: https://aclanthology.org/2026.acl-industry.51/
88. Multi-LoRA deployment: https://aclanthology.org/2026.findings-acl.2106/
89. Memory-efficient mobile fine-tuning: https://aclanthology.org/2026.acl-industry.62/
90. Small-footprint word prediction: https://aclanthology.org/C18-2028/
91. Invisible mobile keyboard: https://arxiv.org/abs/2108.09030
92. TapType: https://arxiv.org/abs/2410.06001
93. lm-Meter: https://arxiv.org/abs/2510.06126

## Final Assessment

SwiftFloris does not need a new thesis. It needs to make its existing thesis harder to break. The project already offers a credible offline keyboard with unusually transparent data handling. The next release gains more from closing resize and recovery risks, clearing obsolete blockers, proving Android 17 reconstruction, and widening trustworthy distribution than from adding another visible feature.

The three new roadmap items are deliberately narrow. They prevent stale evidence from freezing work, test the only newly documented platform behavior with direct process-lifecycle consequences, and reduce the future cost of the parent project's largest architectural change. Everything else belongs to an existing item, a cleared blocker, or the watch list.

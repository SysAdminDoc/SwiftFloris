# Research — SwiftFloris

Date: 2026-08-10 — replaces all prior research (previous pass: 2026-07-29).

## Executive Summary

SwiftFloris v1.9.58 (versionCode 2107) is a mature, privacy-first Android IME: 898 Kotlin files / ~165k LOC in `:app`, five shared `lib/*` modules, 355 JVM test files, 37 Roborazzi goldens, 40 local verification scripts, no `INTERNET` permission, no accounts, no CI. Every one of the ten opportunities from the 2026-07-29 pass shipped in the 38 commits between then and v1.9.58, and `ROADMAP.md` is drained. The highest-value direction now is narrower than "more trust work": the trust *mechanisms* are in place but several of them are scoped by hand-written lists, stale premises, or orphaned wiring, so they certify more than they check. Unless marked otherwise, findings are **Verified** against the working tree at commit `5dd534eaa`.

Top opportunities, in priority order:

1. The addon/MCP enrolment gate screens only five network permissions; `SEND_SMS`, Bluetooth, nearby-devices, storage and Android 17's `ACCESS_LOCAL_NETWORK` are unscreened exfil channels for the selected text the bridge hands over.
2. Four real correctness lint checks (`Recycle`, `CommitPrefEdits`, `ApplySharedPref`, `CommitTransaction`) are disabled across all of `:lib:android` as collateral from an unrelated build workaround.
3. The Emoji 17 blocker is based on a false premise — CLDR 48 *is* the Unicode 17 update and the shipped assets already contain Emoji 17.0 characters while declaring `EMOJI-VERSION: 16.0`.
4. `FlorisApplication.onCreate` does a synchronous recursive cache wipe on the main thread — the same defect class as upstream FlorisBoard #3300.
5. `crowdin.yml` is orphaned (its workflow no longer exists) and omits 8 shipped locales including `zh-rCN`, the largest translation; README still advertises a "Crowdin pipeline".
6. No Advanced Protection Mode awareness — a privacy keyboard is the natural consumer of `AdvancedProtectionManager`, and no competitor keyboard does this.
7. Text duplication / cursor jump in rich-text and web hosts is reported independently against FlorisBoard, HeliBoard and AnySoftKeyboard; SwiftFloris's generic reconciliation covers it in principle but nothing replays those sequences.
8. AGP 9.3.0 (July 2026) and Kotlin 2.4.10 (2026-07-14) are available; neither is the blocked Kotlin 2.4.20 CVE fix.
9. The NDK 29 / CMake / Rust build pins have no native sources behind them.
10. Local-only agent docs point at files that no longer exist, which is how a wrong blocker premise survived (see #3).

## Product Map

- Core workflows: enable the IME and type/correct/glide; emoji, stickers, snippets, clipboard history with search and pinning; multilingual subtypes, hardware layouts, CJK and transliteration; themes, per-app profiles, privacy posture, addons and the MCP bridge; backup/restore (manual + scheduled, encrypted), dictionary migration, diagnostics.
- User personas: privacy-conscious Gboard/SwiftKey migrants; multilingual and minority-script typists; offline users; hardware-keyboard, accessibility, Tasker and addon users; F-Droid/Obtainium reviewers.
- Platforms and distribution: Android 8.0+ (`minSdk 26`), `targetSdk 36`, `compileSdk 37`; local builds plus GitHub Releases and Obtainium, F-Droid recipe prepared; Apache-2.0.
- Key integrations and data flows: Room/SQLCipher stores, Tink/Android Keystore, SAF and share-sheet import/export, signed addon APKs, external voice IMEs, exported Tasker plugin, local Binder/AIDL MCP daemons. No network path in the base APK, enforced by `:app:verifyNoInternetPermission` on both source and merged manifests.

## Competitive Landscape

- **FlorisBoard** (upstream) — does layout/extension architecture well. Learn from #3323 (mask sensitive clipboard entries), #3300 (main-thread I/O in `Application.onCreate`, which SwiftFloris still has), #3310 (text spam/deletion failure in a web editor). Avoid its Crowdin `maxLength` friction (#3299) — SwiftFloris uses zero `maxLength` attributes and should keep it that way.
- **HeliBoard** — does restrained offline typing and pragmatic state fixes well. Learn from its August 2026 floating-window cluster (#2725 unrecoverable resize, #2709 unreachable controls, #2708 Z Fold 6 drag lock): a movable/resizable IME window needs clamping invariants, not just a reset action. Avoid copying GPL code into the Apache base.
- **AnySoftKeyboard** — durable language-pack ecosystem. Learn from #4812 (text duplication) and #4856 (cursor jumps when editing existing text), which corroborate the same host-desync pattern as FlorisBoard #3310 and HeliBoard #2702. Avoid loosely validated third-party payloads.
- **FUTO Keyboard** — v0.1.30 (2026-08-04) added a desktop web theme editor with 44 colors, the KASROZ swipe-optimized layout and better spacebar language switching; v0.1.29 (2026-06-01) shipped Unicode 17 emoji with a 1 MB compatibility font. Learn that Unicode 17 emoji data has been shippable since mid-2026 — this is direct evidence against SwiftFloris's Emoji 17 blocker. Avoid its GPL runtime and separate model-license surface.
- **Gboard / SwiftKey** — set expectations for correction, migration and learned-data transparency. Learn recovery transparency; avoid accounts, cloud clipboard and server learning (already the repo's stated position).
- **fcitx5-android / Trime / Keyman** — modular engines and data packs behind package boundaries. Learn the schema/package separation SwiftFloris already mirrors in its addon contract; avoid embedding native engines or update channels in the base APK.

## Security, Privacy, and Reliability

- **Verified — the no-network enrolment gate is a five-entry denylist.** `app/src/main/kotlin/dev/patrickgold/florisboard/ime/security/NoNetworkPermissionPolicy.kt:28-34` denies only `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `CHANGE_NETWORK_STATE`, `CHANGE_WIFI_STATE`. It gates addon enrolment (`ime/addon/AddonEnumerator.kt:189-192`) and both MCP discoverers (`ime/mcp/McpAndroidDiscoverer.kt:222`, `ime/mcp/McpDaemonDiscoverer.kt:108`). `docs/PRIVACY_AND_AI.md:216-219` states the bridge hands the daemon "your selected text plus any context fields". Permissions that move data off-device without `INTERNET` are unscreened: `SEND_SMS`, `BLUETOOTH_CONNECT`/`BLUETOOTH_ADVERTISE`, `NEARBY_WIFI_DEVICES` (Wi-Fi Direct/Aware), `NFC`, `WRITE_EXTERNAL_STORAGE`/`MANAGE_EXTERNAL_STORAGE`, `CHANGE_WIFI_MULTICAST_STATE`, and Android 17's new `ACCESS_LOCAL_NETWORK`. The tests iterate `NoNetworkPermissionPolicy.DeniedPermissions` itself (`app/src/test/.../McpAndroidDiscovererTest.kt:112`, `McpDaemonDiscovererTest.kt:75`), so they can never detect an omission. This is a scope gap, not a bypass of the existing rule — but the enrolment verdict reads as a general safety guarantee in README and `PRIVACY_AND_AI.md`.
- **Verified — four correctness lint checks disabled module-wide.** `lib/android/build.gradle.kts:68-79` carries the repo's only `FIXME` ("This is a workaround! Otherwise `:lib:snygg:generateJsonSchema` breakes") and disables `UElementAsPsi`, `ApplySharedPref`, `CommitTransaction`, `Recycle`, `CommitPrefEdits`. Only `UElementAsPsi` plausibly relates to the lint-API conflict; `Recycle` and `CommitPrefEdits` guard resource leaks and data loss in the Android compat layer (which includes `AndroidClipboardManager.kt`). No `lint-baseline.xml` exists anywhere, so this is the repo's only meaningful lint suppression.
- **Verified — no open advisories for the current pins.** An OSV batch query on 2026-08-10 returned empty for `com.google.crypto.tink:tink-android 1.23.0`, `androidx.room:room-runtime 2.8.4`, `org.jetbrains.kotlin:kotlin-stdlib 2.4.0`, `com.google.zxing:core 3.5.4`, `io.coil-kt.coil3:coil-compose 3.4.0` and `org.jetbrains.kotlinx:kotlinx-serialization-json 1.11.0`. CVE-2026-53914 (Kotlin build-cache metadata) remains unfixed in any stable release: 2.4.20 is still at Beta2 and is scheduled for September 2026, so `scripts/check-kotlin-build-cache-cve-guard.py` (which requires ≥ 2.4.20 stable or disabled caches) stays correct as written.
- **Verified — main-thread startup I/O.** `FlorisApplication.kt:139` and `:153` call `cacheDir?.deleteContentsRecursively()` synchronously inside `onCreate`/`init()`. `ExtensionManager.init()` (`lib/ext/ExtensionManager.kt:113-119`) already dispatches to `ioScope`; the cache wipe does not. Upstream FlorisBoard #3300 reports the same defect class.
- **Verified — Android 17 platform work is already done, with one exception.** `TextAttribute.Builder.setTextSuggestionSelected()` is wired at `ime/editor/EditorInputConnectionBatch.kt:144-167` behind an API-37 writer, CJK candidates set `isTextSuggestionSelected` (`ime/cjk/CjkInputProvider.kt:127`), and `scripts/verify-targetsdk37-shadow.py` gates the whole set (`EditorInputConnectionBatchTextAttributeTest`, `ImeVisibilityConfigurationPolicyTest`, `AndroidAdaptiveImeWindowTest`, `AndroidAdaptiveManifestContractTest`). Not adopted: `android.security.advancedprotection.AdvancedProtectionManager` — zero references in the tree.
- **Assumption — fail-visible key construction.** `ime/text/keyboard/TextKeyData.kt:82-88` wraps `appendCodePoint(code)` in an empty `catch (_: Throwable) {}` inside `buildString`, so a key with an invalid code point renders with a blank label rather than being rejected. Impact is cosmetic in the shipped layouts but silently degrades user-imported Keyboard3/KLC layouts.
- **Checked and clean (do not re-investigate):** `AddonEnumerator.readSigningFingerprint` fails closed (`AddonEnumerator.kt:205` → `Rejected("cannot read signing certificate")`); `NlpInlineAutofill.showInlineSuggestions` already catches the `RuntimeException` behind FlorisBoard #3294/#3311 (`ime/nlp/NlpInlineAutofill.kt:89-93`); scheduled-backup failures already notify and surface a last-failure card (`ScheduledBackupNotifications.kt:45`, `ScheduledBackupPanel.kt:194-198`); `SealedBoxCrypto.kt:197` returning `null` on any decrypt failure is correct AEAD practice, not an oracle; `CrashDialogActivity.kt:43-47` is a deliberate safe-preference wrapper; the clipboard 64 KiB ingress bound, clipboard/media/backup encryption, MCP binder-state reporting and glide low-RAM degradation all shipped in v1.9.57–v1.9.58.

## Architecture Assessment

- **Enrolment boundary.** Invert `NoNetworkPermissionPolicy` from "deny these five" to "permit only these" for enrolled addons and MCP daemons, and add a test that fails when a permission outside the allowlist appears in a fixture manifest — the current tests derive their inputs from the policy they are testing.
- **Emoji data provenance.** `app/src/main/assets/ime/media/emoji/*.txt` are generated from CLDR v48 and already contain Emoji 17.0 characters — `en.txt` matches `distorted face`, `fight cloud` and `hairy creature`, all confirmed V17.0 on the Unicode emoji-versions chart, plus `orca`, `trombone`, `treasure chest` and `landslide` — yet line 4 declares `# EMOJI-VERSION: 16.0`. CLDR 48 shipped 2025-10-29 and its release note states it is "Updated for Unicode 17, including new names and search terms for new emoji, new sort-order". `EmojiDataVersion` (`ime/media/emoji/EmojiData.kt:30-33`) is parsed but has no production consumer — only `EmojiDataVersionTest` — so the "version gate infrastructure is in place" claim in `Roadmap_Blocked.md:226-233` overstates what exists. Glyph availability is separately handled by `Paint.hasGlyph` fallback (`FlorisEmojiCompat.kt` KDoc), so no compatibility font is needed.
- **Localization pipeline.** `crowdin.yml` names `FSEC_CROWDIN_PROJECT_ID`/`FSEC_CROWDIN_PERSONAL_TOKEN` but nothing consumes it: `.github/` contains only issue templates, `osv-overrides.json`, `security-dependency-freshness.json`, the PR template and an icon — no `workflows/` directory. The only references to `crowdin-upload.yml` are in `docs/archive/`. Its `languages_mapping` covers 34 locales while `app/src/main/res/` ships 42, omitting `et-rEE`, `ko-rKR`, `lv-rLV`, `nds-rDE`, `pt-rBR`, `sq-rAL`, `ur-rPK` and `zh-rCN` — including the largest translation (zh-rCN, 999 strings) and the locale with dedicated Nastaliq font support. `README.md:187` still lists "Crowdin pipeline for translations" under Architecture & Stack. Either wire a local `crowdin` CLI script (consistent with the repo's no-CI, local-gates model) or retire the config and document a PR-based translation path in `CONTRIBUTING.md`.
- **Editor compatibility.** Three independent keyboards report the same host-desync family — FlorisBoard #3310 (Google Keep web), HeliBoard #2702 (text duplicated on delete), AnySoftKeyboard #4812/#4856 (duplication, cursor jumps). The generic defence is already present: deletes run `finishComposingText()` inside the batch before `deleteSurroundingTextInCodePoints` (`ime/editor/AbstractEditorInstance.kt:595-600`, `:614-619`), `setComposingRegion` is range-guarded (`:362-368`), and `handleSelectionUpdate` falls back to re-reading the surrounding text when the host's reported state does not match the predicted one (`:185-211`). What is missing is proof: no fixture replays the reported host sequences, and `commitCompletion`'s anti-append guard is scoped to password fields only (`ime/editor/EditorInstance.kt:294-300`). This is a test-coverage gap on a defect class three projects hit, not an absent mechanism.
- **Vietnamese input.** Telex is already a data-driven rule table — ~200 entries including reverse/undo rules in `app/src/main/assets/ime/keyboard/org.florisboard.composers/extension.json:14-17`, executed by the longest-key-first matcher in `ime/text/composing/Composer.kt:63-89` — so the refactor FlorisBoard is asking for in #3316 does not apply here. VNI, the other standard Vietnamese method, has no composer at all: `telex` is the only `with-rules` entry in the extension and no `vni` id exists in the tree. Adding it is a second rule table in the same file, not new engine work.
- **Cursor and text-editing controls.** `KeyboardMode.EDITING` was deliberately removed as a long-dead mode (`ime/keyboard/KeyboardMode.kt:31-33`), and its replacement is the quick-actions overflow panel, whose default arrangement carries undo/redo, four arrows, copy/cut/paste/select-all and forward-delete (`ime/smartbar/quickaction/QuickActionArrangement.kt:74-100`). `MOVE_START_OF_LINE`, `MOVE_END_OF_LINE` and `MOVE_START_OF_PAGE` exist as key codes (`ime/keyboard/KeyCode.kt:57-60`) but are reachable only through swipe bindings, and the Page Up/Down keys added in v1.9.58 are not in the arrangement either. Exposing them is a data change to the default arrangement, which is the cheapest available answer to HeliBoard #2706 and AnySoftKeyboard #4805.
- **IME window resilience.** `resetFixedSize()`/`resetFloatingSize()` exist and are reachable in-keyboard (`ime/window/ImeWindowEditorHandles.kt:240`, `ime/window/ImeSystemUi.kt:222`), so the recovery action is present — but HeliBoard's cluster is about reaching the control after a bad drag, not the control's absence. `ime/window/ImeWindowEditorHandles.kt:433` carries an unresolved TODO about drag rounding error and `ImeWindowControllerActionsTest.kt:227` documents an untested offset case. Property-test the clamp so no drag/resize sequence can place the handles outside the visible window.
- **Build surface.** `app/build.gradle.kts:75` sets `ndkVersion` from `gradle/tools.versions.toml` (NDK 29.0.14206865, plus CMake 4.1.2 and Rust 1.93.0 pins) while the tree contains no `CMakeLists.txt`, no `Cargo.toml`, no `.so`, no `externalNativeBuild` and no `jniLibs`. Only `utils/repr_build/run.sh` reads the cmake/ndk/rust pins. If they are load-bearing for reproducibility, say so in a comment; otherwise a clean build should not require a ~2 GB NDK install.
- **Dependency currency.** AGP 9.3.0 shipped July 2026 (new optimization DSL, `src/<variant>/keepRules/` source sets); the repo is on 9.2.1. Kotlin 2.4.10 (2026-07-14) is the current stable patch against the pinned 2.4.0. Compose BOM 2026.06.01 exists but maps to identical library versions as the pinned 2026.06.00 — no reason to bump. SQLCipher 4.17.0, Tink 1.23.0, Room 2.8.4, Roborazzi 1.70.0 and Kotest 6.2.3 are current.
- **Agent-doc integrity.** `CLAUDE.md:11-18` records that `PROJECT_CONTEXT.md` and `AGENTS.md` no longer exist, then `CLAUDE.md:38-39` still lists both as steps 1 and 2 of the reading order. `Roadmap_Blocked.md:174` cites `.github/workflows/emulator-smoke.yml`, which does not exist. Stale pointers in these files are how the Emoji 17 blocker premise survived nine months.
- **Category coverage.** Security, i18n/l10n, testing, docs, distribution/packaging, plugin ecosystem, mobile/large-screen, migration and upgrade strategy produced concrete work. Observability produced none: every silently-swallowing `catch` surveyed turned out to be correct fail-closed behaviour or defensible by design, and scheduled-backup, addon-rejection and MCP-binding failures all already reach the UI. Accessibility produced none either — `TalkBack key echo` and `Switch Access` are confirmed absent (no `TextToSpeech`, no accessibility-service declaration; TalkBack support is live-region only, `ime/keyboard/KeyboardManager.kt:115,602-607`) but both are already tracked in `Roadmap_Blocked.md` and remain device-gated, as do the foreground editor matrix and the benchmark refresh. Multi-user remains intentionally excluded.

## Rejected Ideas

- Bumping Compose BOM to 2026.06.01 — the Android BOM mapping table shows it resolves to the same material3 1.4.0 / compose 1.11.4 as the pinned 2026.06.00; pure churn.
- Crowdin `maxLength` remediation (FlorisBoard #3299) — `app/src/main/res/values/strings.xml` contains zero `maxLength` attributes; the problem does not exist here.
- A compatibility emoji font (FUTO v0.1.29 ships 1 MB) — `FlorisEmojiCompat` already degrades to `Paint.hasGlyph` on GMS-less devices, so unrenderable glyphs are already filtered.
- Per-app incognito override (HeliBoard #2719) — already shipped: `ime/profile/PerAppKeyboardProfile.kt:56`.
- Rewriting Telex as an algorithm (FlorisBoard #3316) — SwiftFloris's Telex is already the table-driven form upstream is asking for (`org.florisboard.composers/extension.json:14-17`, `ime/text/composing/Composer.kt:63-89`).
- Key-popup vs inline-autofill z-order (HeliBoard #2721) — already handled: `ime/smartbar/InlineSuggestionsUi.kt:72-73,97` derives `isZOrderedOnTop` from the popup counter maintained by `ime/popup/PopupUi.kt:44,53-58`.
- Shift lost during glide (HeliBoard #2720) — cannot occur: `ime/text/gestures/GlideTypingGesture.kt:51,110` excludes `KeyCode.SHIFT` from gesture-initiating keys and `KeyboardManager.fixCase` (`:633-643`) re-cases at commit.
- Emoji search state persisting across sessions (HeliBoard #2698) — cannot occur: the query is a plain `remember` (`ime/media/emoji/EmojiPaletteView.kt:243`) inside a panel that `KeyboardModeTransitionController.prepareForEditor()` disposes on every new editor.
- Spacebar-swipe cursor crash (HeliBoard #2707) — not reachable: the swipe dispatches DPAD key events rather than a computed index (`ime/keyboard/KeyboardManager.kt:648-663`), and every computed selection index is clamped to `safeEditorBounds` (`ime/editor/EditorInstance.kt:533,544`).
- Masking sensitive clipboard entries (FlorisBoard #3323) — the masking half already ships (`ClipboardDatabase.kt:233-238` renders `************`) and sensitive clips are excluded from history entirely (`ClipboardManager.kt:299`); only the reveal affordance and manual marking remain, which is what the roadmap item covers.
- Spacebar swipe-to-switch-language (FUTO v0.1.30) — already shipped as four configurable spacebar swipe gestures (`app/FlorisPreferenceModelImpl.kt:89-94`).
- Split/one-handed keyboard for foldables (AnySoftKeyboard #4788) — already shipped (`ime/window/SplitKeyboardLayoutCalculator.kt`, `ime/text/keyboard/SplitGutterPostPass.kt`).
- Enabling R8/resource shrinking (AnySoftKeyboard #4838) — already on for both release variants (`app/build.gradle.kts:173-183`).
- Guarding against cross-app KeyEvent injection (CAKI) — an IME receives whatever the framework dispatches; there is no reliable in-IME discriminator, and the existing `deviceId`-scoped layout binding (`ime/hardware/HardwareKeyboardRuntimeMapper.kt:79-90`) is the correct scope. Platform-level problem, not a roadmap item.
- A `catch`-block cleanup sweep — of the sites surveyed, `readSigningFingerprint`, `SealedBoxCrypto.decrypt`, `CrashDialogActivity`'s preference wrapper, `ScheduledBackupSaf` verification and `PersonalDictionarySync`'s HMAC compare are all correct fail-closed or defensible-by-design; only `TextKeyData.kt:85` is worth changing.
- Registering for Android developer verification, F-Droid MR submission, Emoji-17 asset regeneration tooling, FUTO Swipe evaluation, Kotlin 2.4.20, AGP 10, Room 3, device-tier accessibility and benchmark refresh — all already tracked in `Roadmap_Blocked.md`; only the Emoji 17 blocker's premise changed (see above). Note for that file: Google's enforcement date is 2026-09-30 in Brazil, Indonesia, Singapore and Thailand.
- Cloud AI, account sync, cloud clipboard, telemetry, online addon store, multi-user profiles, bundled GPL keyboard code — unchanged rejections from the 2026-07-29 pass; the reasoning still holds and is not repeated here.

## Sources

Upstream and OSS keyboards:

- https://github.com/florisboard/florisboard/issues/3323
- https://github.com/florisboard/florisboard/issues/3316
- https://github.com/florisboard/florisboard/issues/3312
- https://github.com/florisboard/florisboard/issues/3310
- https://github.com/florisboard/florisboard/issues/3300
- https://github.com/florisboard/florisboard/issues/3299
- https://github.com/florisboard/florisboard/issues/3294
- https://github.com/florisboard/florisboard/pull/3320
- https://github.com/HeliBorg/HeliBoard/issues/2725
- https://github.com/HeliBorg/HeliBoard/issues/2721
- https://github.com/HeliBorg/HeliBoard/issues/2709
- https://github.com/HeliBorg/HeliBoard/issues/2708
- https://github.com/HeliBorg/HeliBoard/issues/2706
- https://github.com/HeliBorg/HeliBoard/issues/2702
- https://github.com/HeliBorg/HeliBoard/issues/2698
- https://github.com/HeliBorg/HeliBoard/issues/2704
- https://github.com/HeliBorg/HeliBoard/issues/2720
- https://github.com/HeliBorg/HeliBoard/issues/2719
- https://github.com/AnySoftKeyboard/AnySoftKeyboard/issues/4856
- https://github.com/AnySoftKeyboard/AnySoftKeyboard/issues/4812
- https://github.com/AnySoftKeyboard/AnySoftKeyboard/issues/4805
- https://github.com/AnySoftKeyboard/AnySoftKeyboard/issues/4788
- https://github.com/futo-org/android-keyboard/releases

Platform, standards and specifications:

- https://developer.android.com/about/versions/17/behavior-changes-17
- https://developer.android.com/about/versions/17/behavior-changes-all
- https://developer.android.com/privacy-and-security/advanced-protection-mode
- https://developer.android.com/reference/android/security/advancedprotection/AdvancedProtectionManager
- https://developer.android.com/reference/android/view/inputmethod/TextAttribute
- https://developer.android.com/developer-verification
- https://cldr.unicode.org/downloads/cldr-48
- https://unicode.org/emoji/charts/full-emoji-list.html
- https://www.unicode.org/emoji/charts/emoji-versions.html
- https://www.unicode.org/reports/tr35/tr35-keyboards.html

Build tooling and dependencies:

- https://developer.android.com/build/releases/agp-9-3-0-release-notes
- https://developer.android.com/develop/ui/compose/bom/bom-mapping
- https://kotlinlang.org/docs/releases.html
- https://kotlinlang.org/docs/whatsnew-eap.html
- https://developer.android.com/jetpack/androidx/releases/room
- https://api.osv.dev/v1/query
- https://nvd.nist.gov/vuln/detail/CVE-2026-53914

Community and distribution signal:

- https://f-droid.org/2026/02/24/open-letter-opposing-developer-verification.html
- https://thehackernews.com/2026/06/google-sets-sept-30-deadline-for.html
- https://news.ycombinator.com/item?id=46221226
- https://zeltser.com/third-party-keyboards-security

## Open Questions

- Are the NDK 29 / CMake 4.1.2 / Rust 1.93.0 pins load-bearing for `utils/repr_build/run.sh` reproducibility, or vestigial? The answer decides between documenting them and deleting them, and nothing in the tree records the intent.
- Is the Crowdin project still live and owned by this fork? If the account is gone, the correct fix is to delete `crowdin.yml` and document PR-based translation rather than rebuild the pipeline.

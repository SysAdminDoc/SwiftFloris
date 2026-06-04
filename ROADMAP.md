# SwiftFloris Roadmap

> Single source of truth for all planned work. Items above the --- are existing plans; items below are research conducted 2026-06-03.

**Current release:** v1.8.226 (versionCode 2026). **Local verification:** fastlane metadata green; full Gradle gate stopped on maintainer request before a final success summary after the rerun passed the prior Kotlin compile failure and reached `:app:assembleDebug`.

Hard rules still apply (see `AGENTS.md`): no `INTERNET` permission in `:app`; Apache-2.0 ceiling on `:app`; no closed-source blobs; one logical change per commit; every shipped release bumps `gradle.properties` version, writes a `CHANGELOG.md` section, and adds a `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (draft <=480 chars for headroom).

Item IDs trace to their origin research: `F#`/`EI#` from the archived 2026-05-25 research feature plan; `R#`/`O#` from the 2026-05-25 second-pass findings; `WS#` from the archived improvement-plan workstreams; `N#`/`Next-#`/`L#` from the archived roadmap tiers. Shipped items and reframed/rejected items live in `COMPLETED.md`; full release detail in `CHANGELOG.md`. Historical strategy (tiered NOW/NEXT/LATER, sourced appendix) is preserved at `docs/archive/ROADMAP_v5.67_2026-05-18.md`.

> Last researched: Cycle 4 - 2026-06-04.

## ▶ Implementer Instructions (for the build machine)

This roadmap is fed continuously by an automated research machine. On every
pass, the implementing machine should:

1. `git pull --rebase` to get the latest researched items before starting.
2. Work the open 🤖 items top-down by priority (P0 -> P3). Build them properly:
   multi-file structure, real error handling, no runtime auto-install hacks,
   version strings synced, docs/CHANGELOG updated in the same commit.
3. In addition to building items, run a full UX audit each pass. Walk every
   screen / page / dialog / form / table / empty-loading-error-disabled state
   across light/dark/high-contrast themes. Check onboarding, navigation clarity,
   spacing/contrast/alignment, clipping/overflow, hierarchy, microcopy,
   destructive-action guards, keyboard + screen-reader accessibility, and trust
   signals. Fix what you find, or file it back as a new 🤖 roadmap item if it
   is larger than a pass.
4. Check off ✅ each item you complete (leave it in place with the checkmark),
   commit per logical change with a "why" message, and push.
5. Never edit this Implementer Instructions block or the 🔬 Researcher Queue
   headings. Never force-push.

Keep the `:app` invariant strict: no internet/network permissions, Apache-2.0
ceiling, no closed-source blobs, and network or incompatible features only in
isolated addon APKs. Shipped work belongs in `CHANGELOG.md`; completed roadmap
items belong in `COMPLETED.md`.

## Existing Planned Work

### Keyboard surface & visual polish (device-gated)

- [ ] P1 — Keyboard surface polish + manual-override verification (WS11)
  - Why: Candidate-row, smartbar, and software-key states plus the full layout matrix need real-field verification; cannot fully close without a device.
  - Touches: candidate-row selection/pressed/disabled/correction states; smartbar ordering + overflow + long-label resilience; software-key pressed/held/disabled/gesture states; one-handed/floating/split/compact/landscape/tablet layouts.
  - Acceptance: each state and layout verified in real input fields on a device.
  - Source: docs/archive/TODO_2026-06-03.md B / improvement-plan WS11.
- [ ] P1 — Glide-trail theme baselines + low-end perf evidence (F9)
  - Why: Glide-trail themes lack Roborazzi baselines and low-end (<=4 GB) performance evidence.
  - Touches: Roborazzi baselines (device/emulator); trace `swiftfloris.glide.trailDrawMs` on Pixel 4a / Galaxy A12-class.
  - Acceptance: baselines recorded; trail-draw timing captured on low-end hardware.
  - Source: docs/archive/TODO_2026-06-03.md B / research feature plan F9.
- [ ] P2 — F40 Roborazzi capture phase (F40 capture)
  - Why: Screen-level Roborazzi test classes ship baseline-pending (v1.8.201); the baseline PNGs still need on-device capture.
  - Touches: `:app:recordRoborazziDebug` for the A1 test classes, then remove the class-level `@Ignore` from the pending F40 screenshot classes.
  - Acceptance: baseline PNGs captured; `@Ignore` removed; gate green.
  - Source: docs/archive/TODO_2026-06-03.md A1/B / research feature plan F40.
- [ ] P2 — Glide-trail reduced-animation + tooltip verification (EI4 residual)
  - Why: Confirm Rainbow/Aurora/Neon glide trails honour `ANIMATOR_DURATION_SCALE == 0f` on-device; the doc disclosure already shipped (v1.8.182).
  - Touches: GesturesScreen "i" tooltip + on-device animation-scale check.
  - Acceptance: trails respect zero animation scale; tooltip present.
  - Source: docs/archive/TODO_2026-06-03.md B / second-pass EI4.

### Data safety, backup/restore & import (device-gated portions)

- [ ] P1 — Backup/restore + import path-safety device confirmation (WS13 device portions)
  - Why: Unit tests for these paths are Tier A and done; the on-device confirmation is still required.
  - Touches: backup/restore overwrite-vs-merge; clipboard media missing-file/path-safety; extension-import path-traversal; `StickerMediaProvider.openFile` SAF-grant allow-list validation for imported stickers.
  - Acceptance: overwrite/merge, missing-media, traversal, and imported-sticker open-file behaviors are confirmed on-device; forged encoded sticker URIs are rejected without breaking legitimate user-picked sticker folders.
  - Source: docs/archive/TODO_2026-06-03.md B / improvement-plan WS13; `docs/AUDIT_2026-06-02.md:159-164`.

### CI, build & release hardening

- [ ] P3 — API 37 / Kotlin 2.4 dependency compatibility follow-up
  - Why: The v1.8.216 freshness pass verified Kotlin `2.4.0` and AndroidX Core `1.19.0` as current, but Kotlin has no matching KSP `2.4.0` plugin artifact yet and AndroidX Core `1.19.0` requires `compileSdk 37`.
  - Touches: `gradle/libs.versions.toml`, `gradle/tools.versions.toml`, API 37 behavior-gate docs.
  - Acceptance: bump Kotlin only after a compatible KSP plugin is published; bump AndroidX Core only with the compileSdk 37 behavior-gate plan and full Gradle/Roborazzi verification.
  - Source: v1.8.216 dependency freshness pass.

### Docs & hygiene

- [ ] P2 — Localization content-quality pass (WS12)
  - Why: Turkish repeated-word lint, vague/abrupt English source labels, and inconsistent failure/destructive copy need cleanup.
  - Touches: native-safe Turkish repeated-word review; tighten English source labels; standardize backup/restore/import/export failure + destructive-confirmation copy; document translation-safe cleanup rules.
  - Acceptance: lint warnings reviewed; copy standardized; rules documented.
  - Source: docs/archive/TODO_2026-06-03.md A5 / improvement-plan WS12.
- [ ] P2 — Visual-QA + manual-QA + release-evidence checklists (WS10 / WS15)
  - Why: No standing checklists for the portrait/landscape/compact/floating/dark/high-font-scale matrix, manual QA, or release evidence.
  - Touches: docs for visual-QA matrix, manual-QA flow, and release-evidence capture.
  - Acceptance: three checklists exist and are referenced from the verification docs.
  - Source: docs/archive/TODO_2026-06-03.md A5 / improvement-plan WS10/WS15.
- [ ] P3 — Fastlane changelog drafting guide (R5)
  - Why: No documented guidance on drafting the <=480-char fastlane changelog.
  - Touches: add the guide to `docs/LOCAL_VERIFICATION.md` / `docs/REPO_HYGIENE.md`.
  - Acceptance: guide present with the character-budget rule.
  - Source: docs/archive/TODO_2026-06-03.md A5 / second-pass R5.
- [ ] P3 — Document module build-cache survival (O1)
  - Why: `lib/<module>/build/` cache survives `git rm --cached`; this surprises contributors.
  - Touches: note in `docs/REPO_HYGIENE.md`.
  - Acceptance: behavior documented.
  - Source: docs/archive/TODO_2026-06-03.md A5 / second-pass O1.

### External-action-blocked / sibling-repo / XL (maintainer decision required)

These are genuine blockers — each needs an account, key, sibling repo, ML infra, or a product decision the code cannot make.

- [ ] P0 — Crowdin sync of v1.8.179 + v1.8.186 string drops (R1)
  - Why: 44 stale translated entries across 22 locales; Crowdin web console is source of truth (lint `UnusedResources` until done).
  - Touches: server-side Crowdin sync/pull.
  - Acceptance: translations synced; stale-entry lint clears.
  - Source: docs/archive/TODO_2026-06-03.md C / second-pass R1.
- [ ] P1 — FlorisBoard `0.6.0-alpha02` cherry-picks (F22)
  - Why: Upstream CLDR 48, Emoji 17, number-field fix, and floating-window foundation are worth picking up; conflict resolution needs iterative on-device builds and risks regressing shipped features.
  - Touches: cherry-pick + conflict resolution across input/emoji/layout.
  - Acceptance: picks merged without regressing shipped features; on-device verified.
  - Source: docs/archive/TODO_2026-06-03.md C / research feature plan F22.
- [ ] P1 — Apache-2.0 glide model trained on the MIT FUTO swipe dataset (F21)
  - Why: A licensed in-tree glide model needs off-device ML training infra (XL, out-of-tree). FUTO Keyboard v0.1.29 now publishes FUTO Swipe, a public 1M-swipe QWERTY English dataset, top-1/top-4 benchmark framing, and an open-source swipe system, sharpening this from "train a model" into "evaluate against a public test set before integrating."
  - Touches: external training pipeline + model integration; candidate-row top-4 display policy if the model exposes alternatives.
  - Acceptance: Apache-2.0-clean model trained and integrated; before merge, report top-1 and top-4 error on the public FUTO filtered test-set framing and document whether SwiftFloris should expose accepted word + 3 alternatives after glide completion.
  - Source: docs/archive/TODO_2026-06-03.md C / research feature plan F21; https://github.com/futo-org/android-keyboard/releases/tag/0.1.29.
- [ ] P2 — Bundled Vosk small-en-us recognizer addon (F8)
  - Why: Needs a sibling addon repo + JNI; `RECORD_AUDIO` only in the addon, never `:app`.
  - Touches: sibling addon repo, JNI binding.
  - Acceptance: recognizer ships as a signed addon; `:app` stays permission-clean.
  - Source: docs/archive/TODO_2026-06-03.md C / research feature plan F8.
- [ ] P2 — CycloneDX SBOM + SLSA provenance on release (F10)
  - Why: Needs GitHub Attestations onboarding + release-tag dispatch.
  - Touches: release workflow attestation step.
  - Acceptance: SBOM + provenance attached to releases.
  - Source: docs/archive/TODO_2026-06-03.md C / research feature plan F10.
- [ ] P2 — GPG-signed release tags (F11)
  - Why: Needs a maintainer GPG key.
  - Touches: release-tag signing.
  - Acceptance: tags are GPG-signed and verifiable.
  - Source: docs/archive/TODO_2026-06-03.md C / research feature plan F11.
- [ ] P2 — F-Droid `fdroiddata` submission (F12)
  - Why: `dev.patrickgold.florisboard(.beta)` package-id collides with upstream; needs a rename/coexistence decision plus a multi-month review queue.
  - Touches: fdroiddata metadata + package-id decision.
  - Acceptance: submission accepted into the F-Droid queue.
  - Source: docs/archive/TODO_2026-06-03.md C / research feature plan F12.
- [ ] P2 — FunctionGemma 270M MCP-bridge addon (F30)
  - Why: Needs a sibling addon repo.
  - Touches: sibling addon repo + MCP bridge.
  - Acceptance: addon bridges FunctionGemma over MCP without linking into `:app`.
  - Source: docs/archive/TODO_2026-06-03.md C / research feature plan F30.
- [ ] P3 — Cross-platform desktop dictionary-export CLI (F13)
  - Why: Needs a sibling repo.
  - Touches: standalone CLI project.
  - Acceptance: CLI exports the dictionary format cross-platform.
  - Source: docs/archive/TODO_2026-06-03.md C / research feature plan F13.

#### Open questions blocking the external-action items (maintainer decisions)

1. F-Droid package-id: coexist with upstream FlorisBoard `.beta` or rename? (blocks F12)
2. Vosk 40 MB addon in 2026, or voice stays FUTO-handoff-only? (affects F8 + EI7 copy)
3. Maintainer GPG key (Yubikey-backed?) for signed tags? (affects F11)
4. F-Droid submission timing — during the migration spike or a quiet week? (affects F12)

---

## Research-Driven Additions

### Researcher Queue (Cycle 4 - 2026-06-04)

- [x] 🔬 `locale-a11y-mime-native-audit-2026-06-04` - synced
  `master`, confirmed the Cycle 3 docs push is now at `dc72e32`, refreshed the
  post-v1.8.225 release-ledger evidence to the rewritten pushed hashes, checked
  current audit carry-forwards for duplicates, and widened into language-tag,
  Compose semantics, MIME-filter, and ByteBuffer platform contracts. Existing
  R3-1/R3-4, RA-4/RA-9, and device-gated work remain valid; this cycle adds
  four small, implementation-ready correctness/a11y/contract items and sharpens
  WS13 with the deferred sticker-provider SAF validation.

#### Locale correctness

- [ ] 🤖 P1 — Correct Japanese locale capability gates and pin them with tests (R4-1)
  - Why: `FlorisLocale.supportsAutoSpace` disables auto-space for `"jp"`, but
    Android/BCP-47 Japanese locales use language subtag `"ja"`; `"JP"` is only a
    region subtag. Japanese therefore falls through as an auto-space language,
    and the adjacent capitalization table has no regression coverage for the
    same class of language-tag mistakes.
  - Evidence: `FlorisLocale.kt:219-231` hard-codes no-capitalization and
    no-auto-space language lists, including `"jp"` but not `"ja"`;
    `EditorInstance.kt:701` and `KeyboardManager.kt:678,728` consume
    `primaryLocale.supportsAutoSpace`; `LayoutScriptClassifier.kt:139` already
    classifies `"ja"` as Japanese; IANA Language Subtag Registry lists `Subtag:
    ja` / `Description: Japanese` and region `Subtag: JP` / `Description:
    Japan` (`https://www.iana.org/assignments/language-subtag-registry/language-subtag-registry`);
    Android `Locale` docs recommend BCP 47 `forLanguageTag` / `toLanguageTag`
    for conforming locale strings (`https://developer.android.com/reference/java/util/Locale`).
  - Touches: `FlorisLocale.kt`, new `FlorisLocaleTest` or equivalent JVM test,
    `docs/AUTOCORRECT_LIFECYCLE.md` if the locale capability contract is
    documented there.
  - Acceptance: `FlorisLocale.from("ja").supportsAutoSpace == false` and
    `FlorisLocale.from("ja").supportsCapitalization == false`; existing `zh`,
    `ko`, `th`, `bn`, `hi`, and a Latin control locale are pinned; no
    regression to `languageTag()` / `localeTag()` serialization.
  - Verify: `./gradlew.bat :app:testDebugUnitTest --tests
    "dev.patrickgold.florisboard.lib.FlorisLocaleTest"` plus the existing
    editor spacing policy tests.

#### Clipboard media accessibility

- [ ] 🤖 P3 — Add TalkBack descriptions for clipboard image/video history tiles (R4-2)
  - Why: Text clips can surface URL/email/phone descriptions, but image and
    video history tiles expose only visual thumbnails. A screen-reader user can
    focus and activate the tile without hearing whether it is an image, video,
    pinned/recent item, or the copied timestamp. The prior audit deferred this
    only to avoid Crowdin churn, not because the gap was invalid.
  - Evidence: `ClipboardInputLayout.kt:282-357` renders image/video `Image`
    thumbnails and the video overlay icon with `contentDescription = null`;
    `docs/AUDIT_2026-05-29.md:163-164` records the missing clipboard
    image/video `contentDescription`; Compose semantics docs say semantic
    properties give accessibility services additional context and that
    `contentDescription` conveys an icon/image's meaning
    (`https://developer.android.com/develop/ui/compose/accessibility/semantics`).
  - Touches: `ClipboardInputLayout.kt`, `strings.xml` / translations,
    `docs/ACCESSIBILITY.md`, optional semantics or screenshot test if the helper
    can be extracted without brittle IME rendering.
  - Acceptance: clipboard image and video tiles expose localized, non-sensitive
    labels such as "Clipboard image" / "Clipboard video" plus pinned/recent and
    copied-time context where available; decorative overlay icons remain hidden;
    sensitive text-description protections remain unchanged.
  - Verify: `./gradlew.bat :app:testDebugUnitTest` plus manual TalkBack pass over
    text/image/video clipboard history, long-press popup, and paste/delete
    actions.

#### MIME helper contract

- [ ] 🤖 P3 — Pin `MimeTypeFilter` aggregate semantics and remove constructor stdout (R4-3)
  - Why: The shared MIME helper is used by extension-file import and
    copy-to-clipboard image routing, but its aggregate helpers still carry
    "document and test" TODOs and the constructor prints compiled regex filters
    to stdout. It also deliberately permits wildcard fragments like
    `application/font-*`, which differs from AndroidX `MimeTypeFilter`; that
    divergence should be explicit and tested before more provider/import code
    depends on it.
  - Evidence: `MimeTypeFilter.kt:31-127` documents wildcard-at-any-position
    behavior, has `println(filters)` in the constructor, and leaves
    `matchesAll`, `matchesAny`, and `matchesOne` undocumented/test TODOs;
    `MimeTypeFilterTest.kt:23-124` covers only single-MIME `matches`; AndroidX
    `MimeTypeFilter` allows wildcards only as the whole type/subtype and notes
    Android framework MIME matching is case-sensitive
    (`https://developer.android.com/reference/androidx/core/content/MimeTypeFilter`);
    Android `ClipDescription.compareMimeTypes` documents the platform pattern
    comparison used elsewhere in the IME
    (`https://developer.android.com/reference/android/content/ClipDescription#compareMimeTypes(java.lang.String,java.lang.String)`).
  - Touches: `lib/kotlin/src/main/kotlin/org/florisboard/lib/kotlin/MimeTypeFilter.kt`,
    `lib/kotlin/src/test/kotlin/org/florisboard/lib/kotlin/MimeTypeFilterTest.kt`,
    call-site comments if any behavior is intentionally broader than AndroidX.
  - Acceptance: no constructor stdout; aggregate helpers have KDoc and tests for
    null/empty lists, exactly-one vs many matches, case-sensitive behavior, and
    the intentional fragment-wildcard cases used by font/image import filters.
  - Verify: `./gradlew.bat :lib:kotlin:testDebugUnitTest --tests
    "org.florisboard.lib.kotlin.MimeTypeFilterTest"`.

#### Native bridge hardening

- [ ] 🤖 P3 — Make `NativeStr.toJavaString()` honor ByteBuffer position/limit/arrayOffset (R4-4)
  - Why: The native string bridge currently returns the whole backing array
    whenever `hasArray()` is true, ignoring `position()`, `limit()`, and
    `arrayOffset()`. The current caller surface is small, but CJK/native addon
    work will make this bridge harder to reason about if sliced heap buffers
    decode stale prefix/suffix bytes.
  - Evidence: `Native.kt:39-46` uses `array()` directly on heap-backed buffers
    but copies only `remaining()` bytes for direct buffers; `docs/AUDIT_2026-05-29.md:165-166`
    records the latent offset/position bug; Android `ByteBuffer` docs state
    `hasArray()` permits `array()`/`arrayOffset()`, and buffer content-sensitive
    operations depend on remaining elements from `position()` to `limit() - 1`
    (`https://developer.android.com/reference/java/nio/ByteBuffer`).
  - Touches: `Native.kt`, new focused JVM test for heap, sliced heap, direct,
    read-only/direct-equivalent, and non-zero-position buffers.
  - Acceptance: `toJavaString()` decodes exactly the remaining bytes without
    mutating the caller-visible position, or documents and tests the mutation if
    preserving position is not feasible; direct and heap-backed buffers behave
    the same.
  - Verify: `./gradlew.bat :app:testDebugUnitTest --tests
    "dev.patrickgold.florisboard.lib.NativeStrTest"`.

### Researcher Queue (Cycle 3 - 2026-06-04)

- [x] 🔬 `post-1.8.225-sync-and-futo-swipe-refresh-2026-06-04` - synced
  `master`, reconciled the post-v1.8.225 local fixes against the current
  roadmap, later confirmed the pushed docs state at `dc72e32`, and checked
  current competitor/standards sources. Existing RA-4/RA-9, device-gated visual
  work, and maintainer-gated release items remain valid; this cycle adds only
  net-new release-ledger, clipboard-search, and sync-crypto-contract work, plus
  sharper evidence on F21 from the FUTO Keyboard v0.1.29 swipe release.

#### Release/source-of-truth hygiene

- [x] 🤖 P0 — Reconcile post-v1.8.225 local fixes into a versioned release ledger (R3-1)
  - Why: The branch is `v1.8.223-6-gdc72e32`, `HEAD` is untagged, and three
    local code-fix commits after the v1.8.225 docs marker change privacy,
    crypto, i18n, and theme-engine behavior without a matching new version,
    fastlane changelog, or top-of-README release entry. That breaks the repo's
    own "one shipped release = version + changelog + fastlane metadata + tag"
    contract and makes it hard for Obtainium/F-Droid/reproducible-build readers
    to know which APK contains the fixes.
  - Evidence: `git describe --tags --dirty --always` -> `v1.8.223-6-gdc72e32`;
    `git tag --points-at HEAD` is empty; `CHANGELOG.md:5-78` documents
    v1.8.225 but not commits `4fda240`, `86c9885`, or `76a74c2`;
    `gradle.properties:18-19` still reports versionCode 2025 / versionName
    1.8.225 after those commits.
  - Touches: `CHANGELOG.md`, `README.md`, `PROJECT_CONTEXT.md`,
    `gradle.properties`, `fastlane/metadata/android/en-US/changelogs/2026.txt`,
    `COMPLETED.md` if any roadmap/audit rows are closed, and the release tag.
  - Acceptance: a new release marker (or explicitly documented untagged-dev
    marker) covers the n-gram/thread-safety/crypto/privacy, shared-secret
    scrubbing/Arabic-shaping, and Snygg selector/contentScale fixes; fastlane
    metadata exists for the new versionCode; `git describe` resolves to the new
    tag once released.
  - Verify: `git describe --tags --dirty --always`; `bash
    scripts/check-fastlane-metadata.sh`; full release gate after the build
    machine performs the version bump.
  - Shipped: v1.8.226 (2026-06-04) with versionCode 2026, fastlane changelog
    `2026.txt`, release tag `v1.8.226`, and a local verification caveat in
    `CHANGELOG.md#v1.8.226`.
  - Complexity: S-M

#### Clipboard UX

- [ ] 🤖 P1 — Wire clipboard-history text search into the in-keyboard clipboard palette (R3-2)
  - Why: SwiftFloris already has a tested pure `ClipboardHistoryFilter` and a
    `historySearchEnabled` preference, but the live `ClipboardInputLayout`
    exposes only type filters. FUTO Keyboard v0.1.29 added clipboard-history
    search in its latest release, reinforcing this as table-stakes for long
    local clipboard histories.
  - Evidence: `ClipboardHistoryFilter.kt:22-68` defines the query contract and
    says the search wire-up is missing; `ClipboardPrefs.kt:152-162` defines the
    default-on UI-density toggle; `ClipboardInputLayout.kt:151-163` filters only
    by active `ItemType`; FUTO Keyboard v0.1.29 release notes list clipboard
    history search: https://github.com/futo-org/android-keyboard/releases/tag/0.1.29.
  - Touches: `ClipboardInputLayout.kt`, clipboard strings, `ClipboardScreen.kt`
    if the existing toggle needs a visible settings row, and focused Compose or
    policy tests around query + type-filter composition.
  - Acceptance: when history search is enabled, the clipboard palette offers a
    compact search affordance, filters text clips through
    `ClipboardHistoryFilter.filterByQuery`, composes correctly with image/video
    type filters, shows a clear/no-results state, preserves sensitive-field and
    lock-screen redaction behavior, and resets scroll to the first match on
    query/type changes.
  - Verify: `:app:testDebugUnitTest --tests
    "dev.patrickgold.florisboard.ime.clipboard.*"`; manual keyboard clipboard
    palette smoke with text, sensitive text, image, video, no-results, and
    device-locked states.
  - Complexity: M

#### Sync crypto contract

- [ ] 🤖 P1 — Freeze the sealed-box envelope/KDF contract with vectors before sync transport ships (R3-3)
  - Why: The local sealed-box scaffold now uses X25519 + AES-GCM with an
    RFC-5869-style HMAC KDF and scrubs the derived shared secret, but tests only
    cover generated-key round-trips. Before CRDT sync starts persisting or
    exchanging envelopes, the byte format and derivation constants need
    deterministic vectors so a future KDF tweak does not silently strand paired
    devices.
  - Evidence: `SealedBoxCrypto.kt:96-143` emits `ephemeralPub || nonce ||
    ciphertext+tag` and opens the same shape; `SealedBoxCrypto.kt:166-170`
    derives key material from X25519 output; `SealedBoxCryptoTest.kt:24-68`
    lacks deterministic vectors or schema/version assertions; libsodium's
    sealed-box docs define the same ephemeral-public-key-prefixed shape and
    erase the ephemeral secret after encryption
    (https://doc.libsodium.org/public-key_cryptography/sealed_boxes); RFC 5869
    defines HKDF's extract-then-expand construction and publishes SHA-256 test
    vectors (https://datatracker.ietf.org/doc/html/rfc5869).
  - Touches: `SealedBoxCrypto.kt`, `SealedBoxCryptoTest.kt`,
    `docs/THREAT_MODEL.md` or `docs/SECURITY.md` for the sync-envelope contract.
  - Acceptance: tests pin at least one deterministic vector for nonce/key or a
    fixed test-key envelope; docs state the envelope schema/version and
    compatibility policy; raw X25519 output and temporary nonce/KDF buffers are
    scrubbed where practical; malformed-envelope diagnostics remain non-leaky.
  - Verify: `:app:testDebugUnitTest --tests
    "dev.patrickgold.florisboard.ime.sync.SealedBoxCryptoTest"`; manual review
    that no network permission or native dependency was introduced.
  - Complexity: M

#### Regression coverage

- [ ] 🤖 P2 — Backfill focused regression tests for the untested post-v1.8.225 hotfix surfaces (R3-4)
  - Why: The newest local fixes cover fragile crash/privacy/i18n paths, but the
    changed behavior is not fully pinned by tests. Without focused tests, the
    same regressions can reappear while the release ledger says the fixes are
    shipped.
  - Evidence: `ArabicShaperTest.kt:24-58` lacks a combining-mark case even
    though `86c9885` changed mark-skipping join context; `SnyggRuleTest.kt`
    covers valid/invalid selectors but not unknown-selector fallback after
    `76a74c2`; `rg "contentScale|SnyggContentScaleValue" lib/snygg/src/test`
    finds no serializer-id test; `SwiftKeyTypingTraceRecorder.kt` gained
    private-session gates in `4fda240` without a focused recorder test.
  - Touches: `ArabicShaperTest.kt`, `SnyggRuleTest.kt` / Snygg value tests,
    `SwiftKeyTypingTraceRecorder` tests, and n-gram per-locale flush tests if
    the stores already expose a tractable test seam.
  - Acceptance: combining-mark Arabic shaping, unknown Snygg selector import,
    `contentScale` serialization, private-session trace suppression, and
    per-locale n-gram flush behavior each have focused regression coverage or a
    documented reason they require an extraction seam first.
  - Verify: focused test packages plus full Gradle gate.
  - Complexity: M

### Researcher Queue (Cycle 2 - 2026-06-04)

- [x] 🔬 `startup-diagnostics-and-docs-refresh-2026-06-04` - re-read the
  current v1.8.218 repo state, the committed audit docs, the last 15 shipped
  releases, and current upstream/standards sources. Existing settings-search,
  dependency, upstream FlorisBoard, CLDR/Emoji, F-Droid, device-gated, and
  maintainer-gated rows remain correctly represented below; this cycle adds
  only the net-new startup diagnostics and source-of-truth documentation gaps.

#### Reliability & diagnostics

- [x] 🤖 P1 — Persist or surface staged startup exceptions before Settings opens (R2-1)
  - Shipped v1.8.218: `CrashUtility.consumeStagedException(...)` persists
    staged init exceptions without invoking the process-killing uncaught
    handler, and `FlorisAppActivity` opens `CrashDialogActivity` before the
    splash keep condition can hang on `preferenceStoreLoaded`.
  - Why: A synchronous `FlorisApplication.onCreate()` failure is staged and the
    application returns, but no production call drains the staged exception into
    the existing crash-file / notification path. On a privacy keyboard, a silent
    startup failure is a trust problem even if the failure is rare.
  - Evidence: `FlorisApplication.kt:100-156` installs Flog/CrashUtility, then
    catches `Exception` with `CrashUtility.stageException(e); return`;
    `CrashUtility.kt:159-170` stores and drains staged exceptions; `rg
    "handleStagedButUnhandledExceptions" app/src/main/kotlin app/src/test/kotlin`
    finds no production/test call site; `FlorisAppActivity.kt:100-170` opens the
    Settings activity without reading `CrashUtility`; `docs/AUDIT_2026-05-28.md:16-17`
    independently verified the same path.
  - Touches: `FlorisApplication.kt`, `CrashUtility.kt`, `FlorisAppActivity.kt`,
    and a focused JVM/Robolectric test around the chosen staging/drain policy.
  - Acceptance: an injected synchronous app-init failure creates a persisted
    stacktrace or visible crash/recovery surface; the splash screen does not
    hang silently; the implementation documents whether it intentionally calls
    the existing uncaught handler (process-killing) or writes a recoverable
    staged-init stacktrace without killing the Settings activity.
  - Verify: `:app:testDebugUnitTest`; manual debug build with a temporary
    injected pre-`init()` failure before removing the injection.
  - Complexity: M
- [x] 🤖 P2 — Replace remaining restore/crash diagnostic `printStackTrace()` paths with project logging plus user-safe fallback copy (R2-2)
  - Shipped v1.8.219: restore archive-load, per-section restore, restore
    launcher, and top-level restore failures now route diagnostics through
    `flogError`, restore cards/toasts use `BackupRestorePolicy.restoreErrorMessage(...)`
    to avoid null/blank user copy, and crash stacktrace write failures use the
    `CRASH_UTILITY` logging topic instead of raw `printStackTrace()`.
  - Why: The restore flow and crash-file write helper still fall back to raw
    `printStackTrace()` on exceptional diagnostic paths, while adjacent code
    already uses `flogError`. The fix should improve consistency and user-facing
    failure text without overstating release-build file-log coverage, because
    `Flog` is debug-gated and `fileLog()` is still a stub.
  - Evidence: `RestoreScreen.kt` failure paths are called out in
    `docs/AUDIT_2026-05-28.md:19-22`; sibling `BackupScreen.kt:205` and
    `BackupScreen.kt:338` use `flogError`; `CrashUtility.kt:366-370` still
    catches crash-file write failures with `e.printStackTrace()`;
    `Flog.kt:326` tracks the file-logging TODO.
  - Touches: `RestoreScreen.kt`, `CrashUtility.kt`, possibly `Flog.kt` only if
    a minimal release-safe sink is added; add focused tests for non-null restore
    failure messages where practical.
  - Acceptance: restore failure cards/toasts use stable fallback text when
    `localizedMessage` is null; diagnostic exceptions route through the project
    logging idiom; docs/changelog do not claim persisted release logs unless a
    real persisted sink is implemented.
  - Verify: `:app:testDebugUnitTest`; manual restore-failure smoke with a bad
    archive on the Android SDK host.
  - Complexity: S-M

#### Docs & source-of-truth

- [x] 🤖 P2 — Refresh root onboarding docs to the v1.8.220 source of truth (R2-3)
  - Shipped v1.8.220: root onboarding docs now route open work to
    `ROADMAP.md`, shipped state to `COMPLETED.md`, release notes to
    `CHANGELOG.md` plus fastlane metadata, and archived parity/improvement
    plans are clearly historical context.
  - Why: The live roadmap is current, but the fast onboarding docs still mixed
    older stack facts, archived-plan routes, and retired release-note
    instructions. Future build passes use these docs first, so stale routing
    increases the chance of wrong release or planning edits.
  - Evidence: the pre-fix stale scan found outdated stack/version facts and
    root release-note/planning routes in `PROJECT_CONTEXT.md`,
    `ARCHITECTURE.md`, `CONTRIBUTING.md`, `README.md`, `docs/REPO_HYGIENE.md`,
    and `AGENTS.md`.
  - Touches: `PROJECT_CONTEXT.md`, `ARCHITECTURE.md`, `CONTRIBUTING.md`,
    `README.md`, `docs/REPO_HYGIENE.md`, `AGENTS.md`, and release ledgers.
  - Acceptance: root docs agree that `ROADMAP.md` is the open-work source,
    `COMPLETED.md` is shipped-state summary, `CHANGELOG.md` is the only release
    note stream, and current stack/release facts match v1.8.220.
  - Verify: stale reference scan; `:app:verifyNoInternetPermission`;
    `:app:testDebugUnitTest`; `:app:lintDebug`; `:app:assembleDebug`;
    fastlane metadata check; repo hygiene check.
  - Complexity: M

### Researcher Queue (Cycle 1 - 2026-06-04)

- [x] 🔬 `voice-copy-dependency-refresh-2026-06-04` - rechecked the v1.8.207
  voice-copy slice and public dependency metadata without running Gradle on this
  VM. FUTO Voice Input remains the correct privacy-preserving handoff for voice
  copy, Android 17/API 37 remains future behavior-gate work, and dependency
  drift is low-risk maintenance rather than a security item. The new P3
  dependency freshness row is the build-lane handoff.

*Research conducted 2026-06-03. Items below are new — not duplicates of Existing Planned Work.*

This pass focused on the v1.8.204 **settings search** drop (the newest feature, shipped this release) and a few cross-cutting gaps the three deep audits (`docs/AUDIT_2026-05-28/29` + `2026-06-02`) and the existing roadmap do not already cover. The search subsystem is a hand-maintained static catalog that mirrors the navigation graph; v1.8.221 adds a drift guard, v1.8.222 adds a no-results escape hatch, v1.8.223 adds high-traffic synonym coverage, and v1.8.224 resets result scrolling when the query changes, while the remaining work is accessibility and highlight-lifecycle polish.

### Quick Wins

All current quick wins shipped through v1.8.215. Remaining settings-search work is listed under Larger Bets.

### Larger Bets

- [x] P1 — Drift guard test: every `SettingsSearchDestination` is navigable + every entry resId resolves (RA-1)
  - Shipped v1.8.221: `SettingsSearchIndexIntegrityTest` now fails on
    duplicate entry IDs, missing/blank real string resources, fake resolver
    fallback text, and destination-route mapping drift. The screen navigation
    path uses the same `SettingsSearchDestination.toSearchRoute()` helper the
    test pins.
  - Why: The search catalog is a 33-value enum + ~100 hand-curated entries that mirror the navigation graph and reference real string resIds. Nothing fails the build when a Settings screen is added without a search entry, an entry points at a deleted/renamed pref label, or a `destination` loses its `Routes.*` arm. The only existing test (`SettingsSearchIndexTest.kt`) uses a fake `resolve` map and asserts ranking, not integrity. This is the same registry-drift failure mode the project already hit elsewhere (see the partitioned-prefs golden test).
  - Evidence: pre-fix `SettingsSearchIndex.kt` held enum/catalog rows directly and `SettingsSearchScreen.kt` kept destination routing inside a private navigation function; the existing `SettingsSearchIndexTest` resolved strings through a fake map.
  - Touches: `SettingsSearchScreen.kt`; `SettingsSearchIndexIntegrityTest.kt`.
  - Acceptance: deleting a referenced string res or adding an unmapped destination fails the test; passes today.
  - Verify: `:app:testDebugUnitTest --tests "dev.patrickgold.florisboard.app.settings.search.*"`; full Gradle gate.
  - Complexity: M
- [x] P2 — No-results fallback action in settings search (RA-2)
  - Shipped v1.8.222: zero-result searches now show a centered
    `Browse all settings` text button that navigates to `Routes.Settings.Home`.
  - Why: An empty result set renders only gray "no results for X" text — a dead-end. There's no escape hatch (browse-all / jump to Settings home) and, notably, no link into the Android **system** keyboard settings, which is where a missing pref often actually lives (the search index is app-internal only).
  - Evidence: pre-fix `SettingsSearchScreen.kt` rendered only a no-results
    `Text`; the shipped branch now renders the message plus action.
  - Touches: `SettingsSearchScreen`; default `settings__search__browse_all`
    string.
  - Acceptance: from a zero-result query the user can reach Settings home in one tap; copy is translation-safe.
  - Verify: focused search tests plus `:app:assembleDebug`; full Gradle gate.
  - Complexity: S
- [x] P2 — Reset settings-search result scroll when the query changes (RA-10)
  - Shipped v1.8.224: `SettingsSearchScreen` now scrolls populated
    non-blank result sets back to item 0 whenever the query changes, while
    blank and no-result states stay untouched. `SettingsSearchScreenStateTest`
    pins the reset guard.
  - Why: settings search ranks results per query, but the list keeps one
    `LazyListState` across every edit. A user who scrolls down one query and
    then types a different query can land mid-list for the new result set,
    hiding the highest-ranked destination until they manually scroll back up.
  - Evidence: pre-fix `SettingsSearchScreen` recomputed `results` from
    `searchQuery` but created one unkeyed `rememberLazyListState()` for the
    lifetime of the screen; only the initial-focus `LaunchedEffect` existed.
  - Touches: `SettingsSearchScreen`; `SettingsSearchScreenStateTest`.
  - Acceptance: after changing a non-blank query, the result list starts at the
    first/highest-ranked result; clearing or entering a no-results query does not
    leave the next populated query scrolled into the middle.
  - Verify: focused search package tests plus full Gradle gate.
  - Complexity: S
- [x] P2 — Keyword/synonym coverage audit for high-traffic settings terms (RA-3)
  - Shipped v1.8.223: `SettingsSearchIndex` now adds targeted keyword
    synonyms for theme mode, haptic feedback, trace/shape-writing gestures,
    punctuation spacing, and privacy audit rows. `SettingsSearchIndexTest`
    pins the requested "dark theme", "haptic", "trace", "punctuation", and
    "privacy" queries to the expected destinations, with exact target-row
    checks for dark mode, punctuation, and privacy.
  - Why: Search matches title/summary/screen-title/keywords substrings, but many discoverable prefs have sparse `keywords` (e.g. "haptic" only on input-feedback, "dark"/"light" not on theme.mode, "swipe" present on gestures but "shape writing"/"trace" partial). Users search by capability words, not the exact shipped label.
  - Evidence: pre-fix `SettingsSearchIndex` rows had no targeted synonyms for
    theme mode, glide trace/shape-writing, punctuation spacing, or privacy
    audit capability queries; the shipped rows now carry those keywords.
  - Touches: `SettingsSearchIndex.entries` keyword strings only (no code path change); `SettingsSearchIndexTest`.
  - Acceptance: a documented set of capability synonyms each resolve to the right destination; test pins them.
  - Verify: focused search package tests plus full Gradle gate.
  - Complexity: M
- [ ] P2 — Accessibility/TalkBack pass over the search screen + result list (RA-4)
  - Why: `ACCESSIBILITY.md` does not yet cover the new search surface. The result `JetPrefListItem`s are `clickable` with no `role`/merged-semantics announcement of "result N of M", the leading icon is correctly `contentDescription = null` (decorative) but the field itself has no labelled state, and the empty/no-results text isn't a live region — a TalkBack user won't hear result-count changes as they type.
  - Evidence: `SettingsSearchScreen.kt:82-143` — no `Modifier.semantics{}`/`liveRegion`/`role` on the field, results, or the count-changing branches; `docs/ACCESSIBILITY.md` "Manual QA checklist" has no search entry.
  - Touches: `SettingsSearchScreen` semantics (field label, results `role = Role.Button`/merged, `liveRegion = Polite` on the result-count container); add a search row to the `docs/ACCESSIBILITY.md` manual-QA checklist.
  - Acceptance: TalkBack announces a labelled search field, reads each result's screen + title, and reports result-count changes; checklist documents the flow.
  - Verify: manual TalkBack on-device; `:app:assembleDebug`.
  - Complexity: M
- [ ] P2 — Consume or dismiss Settings search highlight state after the target screen is reached (RA-9)
  - Why: the search-result highlight card is stored in a process-wide Compose
    singleton and rendered by the shared settings scaffold. Because production
    code never clears it, the same "Search result" card can reappear whenever
    the user later visits the matching settings screen, pushing content down
    after the original search context is gone.
  - Evidence: `SettingsSearchScreen.kt:158-164` calls
    `SettingsSearchHighlightStore.mark(...)`; `FlorisScreen.kt:234-247`
    renders a `FlorisInfoCard` whenever `activeTarget.screenTitle == title`;
    `SettingsSearchIndex.kt:84-99` stores already-resolved display strings and
    exposes `clear()`, but `rg "SettingsSearchHighlightStore.clear"` finds only
    the JVM test caller.
  - Touches: `SettingsSearchHighlightStore` plus the `FlorisScreen` search-card
    rendering path. Prefer a one-shot `consumeTargetFor(screenTitle)` API or a
    local displayed-target copy with a dismiss action, so the card survives the
    first target-screen composition but does not persist across later visits.
    If practical, match by a stable destination/screen key instead of localized
    title text.
  - Acceptance: selecting a search result still shows the destination card once;
    leaving and returning to that screen without a new search does not show the
    stale card; users can dismiss the card explicitly if it remains visible.
  - Verify: focused JVM test for the consume/clear contract; `:app:assembleDebug`;
    optional manual Settings search -> destination -> back -> destination smoke.
  - Complexity: M
- [x] P3 — Surface settings search from Settings home (entry-point discoverability) (RA-8)
  - Confirmed 2026-06-04: Settings Home already exposes the search route as a
    top app-bar action with `settings__search__title` content description, so
    search is reachable from the first Settings screen without scrolling.
  - Why: Search is a registered route but reaching it depends on the home-screen wiring; a top-of-home search affordance (or app-bar icon) is the conventional discovery point and matches how Gboard/SwiftKey expose their settings search.
  - Evidence: `HomeScreen.kt:68-75` defines `actions { FlorisIconButton(...) }`,
    `onClick = { navController.navigate(Routes.Settings.Search) }`, icon
    `Icons.Default.Search`, and content description
    `R.string.settings__search__title`.
  - Touches: none; source already satisfies the row.
  - Acceptance: search is reachable from the first screen of Settings without scrolling.
  - Verify: source inspection; optional manual on-device smoke.
  - Complexity: S

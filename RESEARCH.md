# Research — SwiftFloris

Date: 2026-08-20 — replaces all prior research (previous pass: 2026-08-11).

## Executive Summary

SwiftFloris v1.9.59 (versionCode 2108) is tagged, released, and reconciled: all 14 commits that followed the 2026-08-11 research pass map 1:1 to that pass's findings, and every mapped finding is genuinely fixed in source — the OSV gate now computes real CVSS base scores and blocks UNKNOWN (`scripts/osv-release-gate.py:62-136`), incognito gates ghost text (`SuggestionPrivacyPolicy.kt:89-92`), the system user dictionary is read-only (`ime/dictionary/UserDictionary.kt:221`), the no-network gate is a merged-manifest allowlist driven by `app/src/main/config/trust-capabilities.json` (`app/build.gradle.kts:363,416`), the hardware serial is scrubbed and gated against recurrence, release evidence auto-discovers every self-test with a contract test enforcing the roster, and both previously-red gates run green as of 2026-08-20. Two things changed shape at HEAD: **8 commits of fixed work sit past the v1.9.59 tag with no version bump**, and **the top P1 roadmap item (contrast gate) is half-implemented, uncommitted, in the working tree with CRLF line-ending damage**. The highest-value direction is: ship what's sitting there (finish the contrast WIP, cut v1.9.60), fix the one inherited crash-class bug this pass verified (`findWindow` non-termination), and then work the standing UX/verification roadmap — the ecosystem's news this cycle mostly sharpens positioning rather than demanding new subsystems.

Top opportunities, in priority order:

1. **`Context.findWindow()` cannot terminate on wrapped contexts.** `ime/window/ImeSystemUi.kt:287-292` recurses on the same receiver (`val context = this; … context.findWindow()`) instead of unwrapping `baseContext`, so any `ContextWrapper` that is not itself an `Activity`/`InputMethodService` — a Compose `Dialog`, any `ContextThemeWrapper` — loops forever; `:96` then force-unwraps the result. Upstream hit exactly this (florisboard #3326, opened 2026-08). Verified in this tree by inspection.
2. **Cut v1.9.60.** Eight fixed findings (incognito ghost text, system-dictionary read-only, merged-manifest allowlist, MCP no-bind, sticker/n-gram data preservation, empty-state loading, method.xml capabilities, MCP lifecycle) are unreleased and unversioned; the repo's own front-door gate will trip the moment the next release is attempted without a bump.
3. **Finish the uncommitted contrast-gate WIP** — 25 modified files plus `ThemeContrastPolicy.kt` and its test, with CRLF endings on 21 stylesheets that must be normalized (repo is LF, `core.autocrlf=false`) before commit.
4. **Tink CVE-2026-15432** (ChunkedMac timing side channel, HIGH, published 2026-07-21, patched version unknown): grep verified 2026-08-20 that no `ChunkedMac*` API is used anywhere in the tree, so runtime exposure is nil — record the triage, floor Tink in the freshness gate, bump when a patched release ships.
5. **Developer verification is now dated and shaped:** enforcement 2026-09-30 in the four pilot countries, ADB installs explicitly exempt, a rolling-out "advanced flow" lets users allow unverified developers after a 24-hour wait, and a free 20-device limited tier exists. The README section predates all of this and the front-door gate requires quarterly review of it.
6. **Dependency refresh wave** — ten pins are behind, headlined by SQLCipher 4.18.0 (2026-08-18, adds Room 3 support, requires compileSdk 37 — already satisfied) and Compose BOM 2026.08.00. Kotlin stays at 2.4.10: 2.4.20 is still RC (2026-08-12).
7. **Glide strategy is now legally mapped:** FUTO's swipe dataset is MIT (1M+ swipes, HuggingFace), but its model weights ship under FUTO Model Weights License 1.0 (visible-attribution + patent-retaliation clauses — not Apache-clean), and HeliBoard's NLnet engine has shipped no decoder code. The clean path is training an own model on the MIT corpus using the published layout-agnostic method (arXiv 2606.25247), or waiting.
8. **Voice: add Transcribro** to the three-entry external voice-IME list (`ExternalVoiceInputProvider.kt:39-58`) — whisper.cpp + Silero VAD, on-device, actively developed; cheapest voice-story improvement available.
9. **Unicode 18 is now schedulable, not speculative:** draft `emoji-test.txt` v18.0 (dated 2026-04-30) is published; final data lands 2026-09-16; CLDR 49 (~Oct 2026) carries the localized names — CLDR 48 never will.
10. **Positioning: lead with what is already built.** Gboard's on-device AI escaped Pixel exclusivity and Google markets "never uploaded to the cloud"; a new F-Droid competitor (Urik) markets SQLCipher-encrypted learning as its headline. SwiftFloris has both properties plus a verifiable no-INTERNET manifest and reproducible builds, and presents them as release hygiene. The sharp claim is "no network permission, ever, verifiable" — Google cannot match it.

## Product Map

- **Core workflows:** on-device typing with SymSpell autocorrect + bigram/trigram prediction; bilingual (EN+ES/FR/DE) per-token language ID; glide typing over bounded 6-language dictionaries; encrypted clipboard history with search/filters; emoji/sticker media palette; Settings app with search, per-app profiles, theme engine (Snygg), backup/restore, migration importers (SwiftKey JSON, Gboard, FlorisBoard, Keyman, KLC).
- **Personas:** SwiftKey refugees (account retirement 2026-05-31, Copilot/Compose stripped mid-2026 — two displacement waves); privacy-first users (no-network, F-Droid); tinkerers (custom layouts, honeycomb, Tasker, addon contracts).
- **Distribution:** GitHub Releases (canonical, signed, SHA256SUMS) + Obtainium; F-Droid recipe prepared and now resolvable (v1.9.57-59 tags exist on origin); not on Play by design.
- **Data flows:** everything local; explicit-action exports only; SQLCipher + Tink-wrapped keys for dictionary and clipboard; addon/MCP trust contracts exist but MCP binding is deliberately pinned off (`FlorisImeService.kt:424-434`).

## Competitive Landscape

- **FlorisBoard (upstream)** — still stalled: last commit 2026-07-01, nothing in August. Its tracker keeps producing free inventory: #3326 (`findWindow` infinite loop — SwiftFloris inherits the exact defective function), #3323 (hide-sensitive-clipboard toggle — SwiftFloris's existing P3 reveal/marking item covers it). Learn: nothing new; mine the tracker. Avoid: its layout-contribution pipeline (unchanged).
- **HeliBoard** — repo moved orgs to `HeliBorg/HeliBoard` (update stored links). Post-4.0 main already carries 4.1 material: full cursor D-pad (#2600, merged 2026-07-18), long-press arrow key-repeat (#2672), paste-hint redesign, and a fake-CTRL+V paste fallback for apps that ignore `KEYCODE_PASTE` — SwiftFloris pastes via `commitText`, so that workaround is not needed here (checked 2026-08-20). Its NLnet gesture engine (#2226): background data gathering shipped in 4.0, **no decoder code public as of 2026-08-20**, no timeline; designed as a drop-in usable by FlorisBoard derivatives. Learn: D-pad completeness (SwiftFloris shipped line/document jumps in the same window — near parity); watch #2226 quarterly. Enhancement backlog themes worth tracking: modular backup (#2576), long-press-backspace word deletion (#2595), granular autocorrect kill-switches (#2727).
- **FUTO Keyboard** — v0.1.30 stable 2026-08-04. The durable facts: swipe dataset **MIT** (huggingface.co/datasets/futo-org/swipe.futo.org), model weights **FUTO Model Weights License 1.0** (visible end-user attribution + patent-retaliation termination — do not vendor into an Apache-2.0 APK), method published (arXiv 2606.25247: layout-agnostic decoding, layout supplied at inference, generalizes to unseen layouts). Its source-available license drew sustained HN criticism — SwiftFloris's Apache-2.0 is a citable advantage. Learn: the web theme editor pattern (browser-side authoring exporting local theme files needs no app network permission); the ContextLM candidate re-ranking design.
- **fcitx5-android** — 0.1.3 (2026-07-26) remains the CJK candidate-UI bar; physical-keyboard candidate selection. No new movement.
- **Urik Keyboard** (new, F-Droid 2026-06-14, GPLv3) — first competitor whose headline is *SQLCipher-encrypted learned words + Keystore*; PrivacyTools already lists it. SwiftFloris has had this since v1.9.x but does not lead with it. Learn: the marketing, not the code.
- **Yaps (yaps.ai)** — commercial voice-first "fully on-device AI keyboard"; confirms offline voice is the monetizable differentiator. SwiftFloris's answer is the external-voice-IME handoff plus the parked local-recognizer addon; adding Transcribro to the provider list is the cheap move.
- **Gboard** — on-device AI Writing Tools now on Snapdragon 8 Elite / Dimensity 9400 / Exynos 2500 flagships (no longer Pixel-only); teardowns show screenshot/chat-context drafting incoming. SwiftKey — Microsoft removed Copilot and Compose entirely (own FAQ); managed decline continues. Samsung One UI 8.5 buried toolbar resize/text-edit shortcuts, annoying users. Avoid: all of it. The positioning lever: "on-device" is now Google's claim too; "no network permission, verifiable, reproducible" is not.
- **Paywall scan** (what commercial keyboards charge for in 2026): theme packs, AI rewrite/tone, text-expansion snippets, cross-device sync. SwiftFloris ships snippets (Espanso import) and 21 themes free; the sync scaffold exists. Nothing here demands new work; it validates the existing feature set.

## Security, Privacy, and Reliability

- **Verified — `findWindow` non-termination (top finding, see Executive Summary).** `ime/window/ImeSystemUi.kt:287-292` + the `!!` at `:96`. Today's call sites pass raw `InputMethodService`/`Activity` contexts, which is why it has not fired; any future composition inside a `Dialog` or themed wrapper hangs the IME process. Fix is one line (`context.baseContext.findWindow()`) plus a null-safe call site and a regression test.
- **Verified — Tink CVE-2026-15432** (GHSA-xxmf-j3rw-f8p2, published 2026-07-21, CVSS4 8.2): non-constant-time tag comparison in `ChunkedMacVerification`. Zero `ChunkedMac` usage in this tree (grep 2026-08-20); Tink 1.23.0 is used for AEAD keyset wrapping only. Exposure nil; action is triage documentation + a Tink floor in the freshness gate + bump on the patched release.
- **Verified — release/version discipline gap at HEAD.** `gradle.properties` still says 1.9.59/2108 while 8 commits sit past the `v1.9.59` tag (`b6f368f8a..89bc87d6a`). The 2026-08-11 pass's "untagged releases" finding was fixed (v1.9.57-59 tags exist locally and on origin; front-door gate now checks live tag + GitHub Release and passes) — but the same drift pattern is re-accumulating from the other direction: shipped fixes with no version.
- **Verified — uncommitted WIP with CRLF damage.** The contrast-gate implementation (21 modified stylesheets, `ThemeContrastTest.kt` +192/-98, `ThemeContrastPolicy.kt` + test untracked) carries CRLF endings against an LF repo. Finish and normalize, or shelve deliberately; do not let it rot in the working tree.
- **Verified — the privacy audit log is in-memory only.** `AddonInvocationAudit` is an `object` whose KDoc states "nothing is persisted" (`AddonInvocationAudit.kt:43-49`). The 2026-08-11 concern — an empty audit screen reads as "no AI invocation occurred" — is now half-recreated after every process death. Either persist a bounded log or caption the screen "since keyboard start".
- **Verified — MCP is now UI-without-engine.** `FlorisImeService.kt:424-434` pins `mcpLifecycle = null` and empties the registries (correctly closing the old bind-without-dispatch hazard), but `McpSettingsScreen` still offers trust/consent/per-daemon toggles governing a no-op. Honest UI needs a parked-state banner or a gate hiding the screen until a live action exists.
- **Verified — `check-release-front-door.sh` now requires network** (origin tag + GitHub Release checks). Correct for purpose; note that an offline evidence run fails for a release-unrelated reason. Minor.
- **Checked and clean (do not re-investigate):** no `ChunkedMac`; no `KEYCODE_PASTE` dispatch (paste is `commitText`-based, HeliBoard's fake-CTRL+V fallback not needed); the only reflection is a constructor `isAccessible` in `FlorisEmojiCompat.kt:238` — no static-final field writes, so Android 17's reflection ban costs nothing; `SwipeAction` already includes word deletion, so SwiftKey's swipe-to-delete muscle memory is assignable today; emoji keyword search shipped (v1.9.58); line/document cursor jumps shipped (quick actions); no new TODO/FIXME entered the tree in the 14-commit range (the grep count of 21 includes 3 in the gitignored `lib/kotlin/bin/` build-output copy — local clutter worth deleting, nothing more); all new gates added in the range verifiably fail closed (`verify-addon-apk.sh:96` exits on empty policy, OSV gate blocks UNKNOWN, hygiene gate rejects serial fields).
- **No new advisories for the rest of the stack:** Room/androidx.sqlite/SQLCipher-android clean in 2026; Gradle CVE-2026-25063 is the bash-completion script, not core; Kotlin CVE-2026-53914 unchanged (fix still unreleased — 2.4.20-RC 2026-08-12); no 2026 keyboard supply-chain incident found beyond the known CleverType fork, which is now also SEO-squatting "best keyboard" queries with self-promotional listicles.

## Architecture Assessment

- **The addon hub is finally load-bearing.** `NlpAddonHub.production()` is constructed at `NlpManager.kt:69`, the shipping predict path routes through `addonHub.predictAsync` (`:397`) with `AddonInvocationAudit.record(...)` on every outcome, and `QuickAction.kt:112` routes TranslateSelection the same way. Two residues: `QuickAction` constructs a fresh hub per invocation (fine today, silently wrong the day the hub gains state — share the `NlpManager` instance), and the audit store is in-memory (above).
- **Verification posture is structurally fixed.** `release-evidence.ps1` auto-discovers all 14 `scripts/test-*.py` self-tests (`:230-234`), invokes the previously-orphaned gates (`check-fork-identity.sh:277`, `check-layout-json.py:278`, `verify-targetsdk37-shadow.py:288`), and declares the two genuinely device-bound gates in `release-evidence-manual-gates.tsv` with existence checks; `ReleaseEvidenceContractTest.kt:42,73` enforces the roster. Security-path source contracts are now behavior tests, the settings-search index is complete, and the remaining "certifies whatever it omits" instances are the data-extraction Gradle copy and the one-entry freshness config.
- **Device situation regressed.** The physical SM-S938B is gone; only an API-36 x86 emulator with TalkBack 16.0 and no password manager / Word / Collabora is attached. Every device-gated blocker in `Roadmap_Blocked.md` re-verified honestly on 2026-08-12, but that evidence trail lives in a gitignored file — worth remembering that the blocked-roadmap's provenance is unversioned by design and dies with the working tree.
- **Dependency currency (verified against registries 2026-08-20):** behind — Gradle 9.6.1→9.7.1, AGP 9.3.0→9.3.1 (no AGP 10 exists even as alpha; the "late 2026" blocked item stays blocked), androidx-core 1.18.0→1.19.0, androidx-sqlite 2.6.2→2.7.0, Compose BOM 2026.06.00→2026.08.00, Coil 3.4.0→3.5.0, Roborazzi 1.70.0→1.72.0, Kotest 6.2.3→6.2.4, KSP 2.3.9→2.3.11, SQLCipher 4.17.0→4.18.0 (Room 3 support; requires compileSdk 37 — satisfied), buildTools 36.0.0→37.0.0. Current — Kotlin 2.4.10 (hold; 2.4.20 still RC), Room 2.8.4 (Room 3 is alpha under new `androidx.room3` coordinates, Kotlin-only codegen, KSP mandatory; SQLCipher support just removed the main migration blocker — planning item stays blocked but the calculus improved), Tink 1.23.0 (watch for the ChunkedMac fix), Robolectric 4.16.1 (stable; 4.17-beta-2 first with SDK 37 shadows), coroutines/serialization 1.11.0.
- **Android 17 (API 37) deltas relevant here** (behavior-changes page updated 2026-08-14): `TextAttribute` suggestion-selected signaling (compat backport in core 1.19.0 — the bump unlocks pre-37 coverage of a path already written); `show_passwords_physical`/`show_passwords_touch` (platform-side; existing blocked verify item stands); all-apps change — **IME visibility is not restored after an unhandled config change** (rotation reshow needs a device-level regression check; added to the blocked evidence); static-final reflection ban (verified no exposure).
- **Category coverage.** Security (Tink CVE, findWindow), reliability (version discipline, WIP hygiene), distribution (developer verification, v1.9.60), observability (audit persistence), voice (Transcribro), plugin ecosystem (MCP surface honesty), i18n/accessibility/testing/docs/migration (carried on the open 2026-08-11 roadmap items, all re-verified still valid at HEAD). Multi-user remains intentionally excluded (IMEs are per-user on Android; work-profile behavior is a device-testable question, tracked nowhere because no user has raised it). Upgrade strategy is the dependency item plus the blocked Room 3 / AGP 10 planning items.
- **Remaining test and doc gaps:** shared widgets at 44 dp; `crowdin.yml` still names upstream's `FSEC_*` env vars with no consumer (the ownership Open Question is untouched and still gates the i18n item); `docs/PRIVACY_AND_AI.md:236-237` still misstates the dictionary key location. Security-path behavior tests now run against real stores where needed, and Roborazzi capture classes are explicitly named in the plain unit-test report.

## Rejected Ideas

- **Vendoring FUTO Swipe model weights** — FUTO Model Weights License 1.0 requires visible end-user attribution and carries patent-retaliation termination; incompatible friction inside an Apache-2.0 APK. Train on the MIT dataset instead or wait for the NLnet engine. (huggingface.co/futo-org/futo-swipe LICENSE.md)
- **Fake-CTRL+V paste fallback** (HeliBoard) — SwiftFloris pastes via `commitText`, not `KEYCODE_PASTE`; the bug class doesn't apply. Grep verified 2026-08-20.
- **Static-final reflection audit beyond what exists** — only reflection is a constructor accessibility toggle in `FlorisEmojiCompat.kt:238`; API 37's ban doesn't reach it.
- **Compose BOM 2026.06.01** — superseded; go straight to 2026.08.00. (Prior rejection of .01-as-churn stands.)
- **Kotlin 2.4.20-RC for CVE-2026-53914** — still pre-release; build-machine-local threat model unchanged. Hold at 2.4.10.
- **Federated/DP learning architecture** — 2026 output (FLiPD etc.) is infrastructure research; for a no-network keyboard, "no telemetry beats DP telemetry" is the citable contrast, not an adoption path. (arXiv 2305.18465 as the thing deliberately not built.)
- **Copying Gboard's screenshot/chat-context drafting** — reads the user's gallery and on-screen conversations; the exact class of feature this project exists to refuse. Cite as contrast in privacy docs if it ships.
- **Desktop web theme editor, per-app incognito, emoji search, D-pad basics, dictionary importers, glide low-RAM guard, custom layout editor** — all previously rejected-as-shipped or shipped; unchanged.
- **Chasing the "Android desktop mode" theme (FUTO #2137)** — no attached hardware, no user demand signal in this project's channels; re-evaluate when desktop-mode devices materialize.

## Sources

Upstream and OSS keyboards:

- https://github.com/florisboard/florisboard/issues/3326
- https://github.com/florisboard/florisboard/issues/3323
- https://github.com/HeliBorg/HeliBoard/releases
- https://github.com/HeliBorg/HeliBoard/issues/2226
- https://github.com/HeliBorg/HeliBoard/issues/2576
- https://github.com/HeliBorg/HeliBoard/issues/2595
- https://github.com/futo-org/android-keyboard/releases
- https://huggingface.co/datasets/futo-org/swipe.futo.org
- https://huggingface.co/futo-org/futo-swipe/blob/main/LICENSE.md
- https://arxiv.org/abs/2606.25247
- https://github.com/fcitx5-android/fcitx5-android/releases
- https://f-droid.org/packages/com.urik.keyboard/
- https://github.com/soupslurpr/Transcribro
- https://github.com/futo-org/voice-input/releases
- https://nlnet.nl/project/RTranslator/

Platform, standards and specifications:

- https://developer.android.com/about/versions/17/behavior-changes-17
- https://developer.android.com/about/versions/17/behavior-changes-all
- https://www.unicode.org/Public/draft/emoji/emoji-test.txt
- https://emojipedia.org/unicode-18.0
- https://github.com/unicode-org/cldr/releases

Build tooling, dependencies and security:

- https://github.com/advisories/GHSA-xxmf-j3rw-f8p2
- https://github.com/advisories/GHSA-r937-wjx7-w2jp
- https://github.com/JetBrains/kotlin/releases
- https://github.com/sqlcipher/sqlcipher-android/releases
- https://android-developers.googleblog.com/2026/03/room-30-modernizing-room.html
- https://services.gradle.org/versions/current
- https://github.com/takahirom/roborazzi/releases
- https://github.com/google/ksp/releases

Community, distribution and market:

- https://news.ycombinator.com/item?id=48648619
- https://support.microsoft.com/en-us/topic/faqs-for-copilot-changes-in-swiftkey-c02289e6-c5b3-401c-af8d-f6c88409a2d2
- https://www.androidauthority.com/gboard-writing-tools-other-android-phones-3593589/
- https://www.sammobile.com/news/you-can-still-resize-samsung-keyboard-one-ui-8-5-just-not-as-quickly/
- https://9to5google.com/2026/08/18/ (developer-verification advanced flow)
- https://android-developers.googleblog.com/2026/03/android-developer-verification.html
- https://f-droid.org/2026/02/24/open-letter-opposing-developer-verification.html
- https://www.makeuseof.com/best-open-source-gboard-alternatives-tested/
- https://theleaker.com/keyboard-apps-and-themes-for-android/

## Open Questions

- Is the Crowdin project still live and owned by this fork? Unchanged from 2026-08-10/11 — `crowdin.yml` still names FlorisBoard's `FSEC_*` env vars, nothing consumes it, and the i18n roadmap item is gated on this answer.
- Should the blocked-roadmap evidence trail stay unversioned? `Roadmap_Blocked.md` is gitignored by design (root `*.md` rule), but the 2026-08-12 device re-verification evidence exists only on this machine's disk. If the working tree is lost, the blocker provenance is lost with it. A maintainer call between the "local-only planning docs" convention and evidence durability — nothing in the tree records why the convention should win.

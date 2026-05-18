# Sixth-Pass Findings — 2026-05-17

**Scope:** same-date sixth re-run of the autonomous research prompt. The
fifth pass closed dependency drift; this pass folds in the **eight
audit-driven hardening releases v1.8.85 – v1.8.92** that shipped between
the fifth pass and now, plus a focused 2-week external delta on the
landscape since the fifth pass artifacts were written.

This pass produces:

1. This file — the per-pass record.
2. Updates to
   [`../../../ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md`](../../../ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md)
   reflecting the eight new releases and any external commitment / correction.
3. A new `## 0. Research Refresh v5.4` section appended to
   [`../../../ROADMAP.md`](../../../ROADMAP.md), preserving v5.0 – v5.3 in place
   per the established append-mostly convention.

**Local state at start:** clean worktree, `master…origin/master [ahead 88]`,
HEAD `993d181` (`Release v1.8.92 — LDML parser honours shift= over longPress=`).
Latest local tag visible in working tree: `v1.8.84`. `java` is not on PATH on
this VM, so Gradle Definition-of-Done verification still belongs on the
maintainer's build host.

---

## 1. v1.8.85 cross-subsystem hardening pass — what shipped and why

v1.8.85 is the first deliberate AGENTS.md §6 per-PR-scope deviation in the
project's history. The maintainer authorised an extreme cross-subsystem
audit + hardening pass spanning the just-shipped v1.8.75 – v1.8.84 slices
and the foundational privacy / build / CI infrastructure. The audit ran
five parallel read-only research agents across:

- the recently-shipped addon registry (v1.8.81 – v1.8.84),
- the recently-shipped hardware-keyboard layout import (v1.8.75 – v1.8.78),
- the recently-shipped sticker import (v1.8.77),
- the backup / restore + crypto path (Tink migration, SQLCipher),
- the scripts / CI workflows (`verifyNoInternetPermission`, `pull_request_target`,
  reproducible-build self-check, fastlane).

Eleven fixes landed in v1.8.85 itself; seven follow-ups landed as
single-feature commits v1.8.86 – v1.8.92 over the same day. The full
per-fix breakdown lives in `RELEASE_NOTES_v1.8.85.md` through
`RELEASE_NOTES_v1.8.92.md`; the summary below is the roadmap-relevant
projection.

### 1.1 The flagship `verifyNoInternetPermission` gate had a hole

[ROADMAP §6 N7.1](../../../ROADMAP.md) (no-`INTERNET` build gate) is the
load-bearing privacy invariant cited in `PROJECT_CONTEXT.md §2`. The gate
task in `app/build.gradle.kts` only scanned source manifests under
`app/src/**` — a library AAR (current or future) adding `INTERNET` via
manifest merging would slip past undetected. The fix wires a
per-variant `verifyNoInternetPermissionMerged<Variant>` against AGP's
`SingleArtifact.MERGED_MANIFEST`, hooked into `processManifest` (finalize)
and `assemble` (depends-on). The check now also honours legitimate
`tools:node="remove"` / `tools:node="removeAll"` directives, eliminating
the latent false-positive on intentional permission stripping.

**Roadmap effect:** the existing N7.1 description in ROADMAP §6 should
record the merged-manifest expansion and the `tools:node` exemption as
part of the gate's contract.

### 1.2 Personal-dictionary DB was carried by Android 12+ D2D transfer

`android:dataExtractionRules="@xml/backup_rules"` pointed at a
`<full-backup-content>` schema file. The Android 12+ `dataExtractionRules`
attribute requires a `<data-extraction-rules>` schema with separate
`<cloud-backup>` and `<device-transfer>` sections. On Android 12+ the
system silently fell back to the default "include everything" for D2D,
which carried:

- the SQLCipher personal-dictionary DB (`floris_user_dictionary*`),
- the Tink-wrapped passphrase prefs (`floris_user_dictionary_key.xml`),
- the clipboard history.

To a new device, alongside a wrap key that was bound to the *original*
device's Android Keystore and cannot transfer. Net effect: PII ciphertext
leaks via D2D, and the dictionary is bricked on the new device.

v1.8.85 ships a new [`app/src/main/res/xml/data_extraction_rules.xml`](../../../app/src/main/res/xml/data_extraction_rules.xml)
with explicit `<exclude>` entries for every personal-dictionary DB sidecar
(`.db`, `.db-journal`, `.db-wal`, `.db-shm`), the wrap-key SharedPrefs, and
the clipboard-history directory, in both `<cloud-backup>` and
`<device-transfer>` rule sets.

**Roadmap effect:** the existing N7.4 personal-dictionary encryption entry
in ROADMAP §6 should record `data_extraction_rules.xml` as a load-bearing
sidecar file — the SQLCipher work is incomplete without the correct
transfer-exclusion rules.

### 1.3 ZipUtils.unzip atomic-restore semantics

`ZipUtils.unzip` previously warned-and-continued on every guard. A
malicious archive with one well-formed entry plus one zip-slip entry left
the well-formed entry on disk and the caller's `runCatching` saw
`Result.Success`. v1.8.85 added a pre-canonical entry-name guard
(`isUnsafeEntryName`: rejects `..` segments, leading `/`/`\`, Windows
drive prefixes, spaces in names) plus a 10_000-entry cap. v1.8.89 split
guards by intent:

- **Abort (throw `SecurityException`):** unsafe entry name, zip-slip
  via canonical-path resolution, entry-count cap exceeded.
- **Continue with warning:** name > 255 chars, destination path > 1023
  chars, per-entry byte cap (legitimate archives can hit these).

`RestoreScreen.prepareRestoreWorkspace` already wraps `unzip` in a
`try { … } catch (Throwable) { workspace.close(); throw error }`, so abort
propagates to the launcher's `onFailure` toast — user gets a clear
"refusing to extract" message instead of a half-applied restore.

**Roadmap effect:** ROADMAP §6 N7.x can claim atomic-restore semantics; a
future Roborazzi test slice should pin the failure-mode toast string so
i18n drift doesn't silently break the user-facing message.

### 1.4 Hardware-keyboard runtime mapper hardening

`HardwareKeyboardRuntimeMapper` (v1.8.76) used a plain `LinkedHashMap`
touched from both the IME input thread (`KeyboardManager.onHardwareKeyDown`,
`InputManager` device-detach callback) and the settings UI thread. v1.8.85
added a monitor lock around every mutation / read; concurrent `put` +
`remove` no longer corrupt the bucket array.

Same file: `map()` rejected every `isCtrlPressed` event, but Android
delivers AltGr as Ctrl+Alt. Every AltGr-mapped key on `.klc`-imported
PC keyboards (€ on EU layouts, AltGr layers everywhere) was silently
dropped. The gate now rejects Ctrl only when Alt is *not* also pressed.

**Roadmap effect:** ROADMAP §7 Next-6.4b should note the AltGr fix as a
prerequisite to the eventual addon-shipped AltGr custom-layer UX.

### 1.5 Sticker palette decoder OOM

The v1.8.77 user-imported sticker folder fed arbitrary SAF file URIs into
`BitmapFactory.decodeStream(stream)` with no `BitmapFactory.Options`. A
corrupted or hostile 100k × 100k PNG would allocate ~40 GB of bitmap heap
and crash the IME process. v1.8.85 added the canonical two-pass decode:
`inJustDecodeBounds=true` reads dimensions, hard ceiling of 8192 px
rejects anything obviously hostile, then `inSampleSize` is computed so
the final bitmap never exceeds ~512 px on its longest edge.

Same file (v1.8.85, v1.8.90): MIME-type spoof closed — SAF's declared
MIME is now the source of truth, the filename-extension fallback only
runs when SAF gives `null`. SAF lost-grant state is surfaced in the
Settings preference summary with "Folder access lost. Tap to select
again." Tap re-enters the folder picker.

**Roadmap effect:** ROADMAP §7 Next-9.5 should reference the bounded-decode
contract; an in-keyboard recovery banner is logged as a future feature
slice.

### 1.6 CI workflow tightening

`pull_request_target` on `validate-strings-no-translations.yml` interpolated
PR-author filenames directly into `run:` shell blocks. A filename
containing shell metacharacters could escape the `echo` command and
execute attacker-controlled shell in the base-repo context with the
workflow's own (write-capable) `GITHUB_TOKEN`. v1.8.85:

- Passes every `${{ github.event.* }}` and step-output value via `env:`
  and references them as `"$VAR"`.
- Replaces hand-rolled `curl + jq` paginator with `gh api --paginate --jq`.
- Adds `set -euo pipefail` to every `run:` block.
- Adds file-scope `permissions: { contents: read }` to `android.yml`,
  `crowdin-upload.yml`, `reproducible-build.yml` (the other workflows
  already had explicit blocks).

**Roadmap effect:** ROADMAP §6 N6.x CI gates should record the
`pull_request_target` hardening pattern as the canonical reference for
any future workflow that needs to comment on PRs.

### 1.7 Tink migration recovery path

`FlorisUserDictionaryEncryption.getOrCreatePassphrase` previously called
`error(...)` — a hard crash — when the Tink-wrapped passphrase pref was
missing, a legacy AndroidX Security Crypto keyset existed in the prefs,
and the legacy decrypt returned null (master key rotated, partial pref
restore, etc.). v1.8.88 replaced the hard error with a recovery path:
log warning, clear the unreadable legacy keyset prefs, fall through to
fresh-passphrase generation. The user loses access to words encrypted
under the dead key (already unrecoverable), but the IME starts cleanly
with an empty dictionary.

**Roadmap effect:** the Tink migration story (N7.6) should reference this
as the official "broken legacy state" recovery path; a small Roborazzi
slice could pin the regeneration-toast UI when the regeneration UI ships.

### 1.8 Focused per-feature releases v1.8.86 – v1.8.92

Returning to the standard one-logical-change-per-release pattern after
v1.8.85's documented exception:

| Release | Subsystem | One-line summary |
|---|---|---|
| **v1.8.86** | NLP / privacy | `keyVariation` honours `TYPE_NUMBER_VARIATION_PASSWORD` so numeric-PIN copy/cut no longer lands in the IME-local clipboard history |
| **v1.8.87** | Privacy / passphrase | `FLAG_SECURE` + non-saveable passphrase on `DictionaryPassphraseDialog` (encrypted-export / encrypted-import) — closes the screen-recording capture surface and the `savedInstanceState` recoverable-passphrase pattern |
| **v1.8.88** | Crypto / reliability | Recover, don't crash, on undecryptable legacy AndroidX Security Crypto passphrase state |
| **v1.8.89** | I/O / reliability | `ZipUtils.unzip` aborts atomically on security violations; benign anomalies still continue-with-warning |
| **v1.8.90** | UX / SAF | Surface lost SAF persistable-grant for the imported sticker folder in Settings |
| **v1.8.91** | Addon spec | KDoc mandate of a REGISTER receiver — matches the existing CI / docs / Android 11+ visibility mechanism |
| **v1.8.92** | Hardware / LDML | `KeymanLdmlParser` honours `shift=` over `longPress=` (LDML spec correctness); three new tests cover the case matrix |

---

## 2. External-delta research (2-week window: 2026-05-03 → 2026-05-17)

Three parallel research agents covered (a) competitor / dependency
landscape, (b) community signal across r/Swiftkey, r/HeliBoard, HN,
r/LocalLLaMA, r/Android, (c) Android 16/17 platform-API changes since
the fifth pass.

### 2.1 Competitor / dependency landscape

**LiteRT-LM v0.11.0 shipped 2026-05-07** (in window). Headline: Gemma 4
multi-token-prediction inference path delivers >2× decode on mobile
GPU at zero quality loss; CLI now native on Windows with CPU+GPU.
This is the concrete stable target for any future SwiftFloris L1.1a
on-device-LLM addon migrating off the deprecated MediaPipe LLM
Inference (deprecated 2026-03-31, ROADMAP §10 rejected entry).
Source: <https://github.com/google-ai-edge/LiteRT-LM/releases>.

**Gemma 4 model family released 2026-04-02** (just outside the
2-week window but informs LiteRT-LM v0.11): four checkpoints (E2B, E4B,
26B MoE, 31B dense), **Apache-2.0 license**, 256K context, 140
languages. Apache 2.0 aligns cleanly with the SwiftFloris `:app`
license ceiling, so the E2B model is a concrete in-addon target for
the future L1.1a slice. Source:
<https://blog.google/innovation-and-ai/technology/developers-tools/gemma-4/>.

**FUTO Keyboard v0.1.28 stable shipped 2026-05-04** with **RIME
engine integration for Chinese Pinyin** (plus Vietnamese Telex/VNI,
IPA, Toki Pona, clipboard images). This is the first proven-in-prod
open-source pattern for bundling librime under a permissive base
keyboard's CJK path. If SwiftFloris's L3 librime-JNI-addon slice
moves forward, FUTO is the reference implementation worth diff'ing.
Source: <https://github.com/futo-org/android-keyboard/releases>.

**HeliBoard / NLnet open-glide-library:** no in-window milestone. The
NLnet tracker confirms the gesture-data window ends 2026-11-30 with
the grant deadline 2026-06-01, so SwiftFloris's L1.3 glide-engine
swap remains correctly gated. No-op for this pass. Sources:
<https://nlnet.nl/project/GestureTyping/>,
<https://github.com/Helium314/HeliBoard/issues/1226>.

**Bergamot:** no new release in window; last tagged build still
2024-09-20. L2.1a addon blocker unchanged. Source:
<https://github.com/browsermt/bergamot-translator/releases>.

**FlorisBoard upstream:** no release in window. Latest stable still
v0.5.2 (2024-11-28); alpha line stalled at v0.6.0-alpha02
(2025-01-23). Nothing upstream to cherry-pick. Source:
<https://github.com/florisboard/florisboard/releases>.

**Security advisories (SQLCipher 4.16.0, Tink 1.21.0, Room 2.8.4,
Compose BOM 2026.05.00, Kotlin 2.3.21):** Android Security Bulletin
2026-05-01 lists only CVE-2026-0073 (adbd RCE) — nothing affecting
the pinned deps. Security posture green. Source:
<https://source.android.com/docs/security/bulletin/2026/2026-05-01>.

**Google I/O 2026-05-19/20** falls **two days after the SwiftKey
cutoff**. A single-day post-I/O re-scan pass should land any new
on-device Edge AI APIs into a sixth-and-a-half pass. Tracked as a
research-debt item, not a roadmap commitment.

### 2.2 Community signal

**SwiftKey-cutoff press wave:** Neowin's "PSA: this month, here is
what you need to do" landed in window
(<https://www.neowin.net/news/psa-microsoft-is-deleting-swiftkey-accounts-this-month-here-is-what-you-need-to-do/>),
and a second Xeno PSA on X reposted the deadline to the general-tech
feed. **AlternativeTo and BGR / Android Authority round-ups now name
HeliBoard, FUTO Keyboard, FlorisBoard, and AnySoftKeyboard as the
open-source escape route — SwiftFloris is not yet on any of these
lists.** This is the highest-leverage outreach gap of the migration
window. Sources:
<https://alternativeto.net/software/swiftkey/>,
<https://www.androidauthority.com/heliboard-gboard-alternative-3505462/>,
<https://www.bgr.com/2003971/android-keyboards-replace-google-gboard-swiftkey-heliboard/>.

**Trinity College Dublin study** independently rates SwiftKey
telemetry "the most intrusive… no opt-out available" — recirculating
in the migration-window context. This validates the SwiftFloris
no-telemetry invariant as a load-bearing differentiator for the
2026-05-30 pinned release. Source:
<https://www.scss.tcd.ie/Doug.Leith/pubs/gboard_kamil.pdf>.

**Glide-typing community signal:** HeliBoard's chronic #1 complaint
remains the closed-source Google "swypelibs" sideload dependency
(<https://github.com/Helium314/HeliBoard/issues/1226>,
<https://news.ycombinator.com/item?id=45234744>). FUTO issue #332
("Swipe to Type Gets Worse Over Time") tracks a user-degradation
problem in their built-in engine (<https://github.com/futo-org/android-keyboard/issues/332>).
FlorisBoard 0.4.6 still ships no word suggestions
(<https://github.com/florisboard/florisboard/releases/tag/v0.4.6>).
CleverKeys gets the third-party endorsement "the only open-source
keyboard with reliable swipe typing"
(<https://github.com/tribixbite/CleverKeys>,
<https://www.howtogeek.com/open-source-android-keyboards-that-rival-gboard/>).
**The CleverKeys engine is GPL-3.0**, so it cannot link into `:app`
(load-bearing invariant §1) — but it remains an architectural
reference for the SwiftFloris statistical-glide engine that shipped
in v1.8.72 (N1.3 production default). The statistical engine already
in production gives SwiftFloris a *positional* advantage over
HeliBoard and FlorisBoard on glide accuracy + license cleanliness.

**On-device LLM keyboard demand — negative signal.** HN threads on
on-device LLMs on Android in May 2026 (Off Grid, EdgeDox, Ensu, MLC
Chat ~40 tok/s on Snapdragon NPUs) **do not** request keyboard
integration, smart-compose, tone-rewrite, or translate; commenters
ask for markdown, TTS, RAM pre-checks. Gboard's Gemini-Nano "Rambler"
dictation already occupies the on-device-AI-keyboard niche on Pixel
9/10. **Recommendation: keep L1.1a (LiteRT-LM addon) as Later /
Under Consideration, not Now.** Sources:
<https://news.ycombinator.com/item?id=47019133>,
<https://news.ycombinator.com/item?id=47016559>.

**F-Droid Reproducible-Builds Verified Tier — new opportunity.**
F-Droid published "Making reproducible builds visible" in window
with a per-app Reproducibility Status link and ✔️/💔 verification
badges; F-Droid Basic 2.0-alpha9 ships the surface and the work is
NLnet-funded. No keyboard verified as a flagship example yet.
SwiftFloris's v1.8.67 reproducible-build self-check (N12.5) plus the
v1.8.85 entry-manifest pass criterion mean the project is *one
submission step* from being the visibly-reproducible exemplar
keyboard. Combined with the Sept-2026 Google developer-verification
mandate (Brazil/Indonesia/Singapore/Thailand first), reproducible
distribution is now a load-bearing distribution story, not nice-to-have.
Sources:
<https://f-droid.org/en/2025/05/21/making-reproducible-builds-visible.html>,
<https://nlnet.nl/project/Reproducible-F-Droid/>,
<https://www.bleepingcomputer.com/news/security/f-droid-project-threatened-by-googles-new-dev-registration-rules/>.

### 2.3 Platform / Android 16/17 APIs

**Google I/O 2026 (2026-05-13 to 2026-05-15) — zero new IME
framework APIs.** The Android Show I/O Edition 2026's only IME-
adjacent announcement was Gboard's Gemini-Nano "Rambler" dictation,
which is an app feature, not a third-party-consumable platform
surface. Source: <https://blog.google/products-and-platforms/platforms/android/android-show-io-edition-2026/>.

**Android 17 Beta 4 (2026-04-16)** — final beta before stable in
June 2026. No IME-specific behavior changes in Beta 4 itself
(deltas: memory limits, ML-DSA post-quantum crypto, background
audio, certificate transparency, local-network restrictions,
dynamic code loading, large-screen resizability). The two
IME-relevant Android 17 behaviors carried forward from earlier
betas remain:

1. **IME visibility is no longer auto-restored across unhandled
   config changes.** Apps must use `windowSoftInputMode=stateAlwaysVisible`
   or re-request via `onConfigurationChanged()`. Targets host apps,
   not the IME service — SwiftFloris should confirm this is benign.
2. **`TextAttribute.Builder.setTextSuggestionSelected()` +
   `TextAttribute.isTextSuggestionSelected()`** for CJKV candidate
   selection a11y, plus `AccessibilityEvent.setTextChangeTypes()`.
   Wire behind the existing API 37 gate when CJK candidate UI lands
   (currently L3 librime addon territory).

Sources: <https://developer.android.com/about/versions/17/release-notes>,
<https://developer.android.com/about/versions/17/behavior-changes-17>.

**No compileSdk bump required before Android 17 stable** (June
2026). `compileSdk 36` remains correct.

**ML Kit GenAI Speech Recognition** (Gemini Nano via AICore) is in
alpha — not yet a stable IME-consumable API. Future addon path for
on-device dictation; not actionable today. Source:
<https://picovoice.ai/blog/android-speech-recognition/>.

**MediaPipe LLM Inference → LiteRT-LM:** no formal migration guide
on `ai.google.dev/edge/litert-lm` as of 2026-05-17 — research debt
to flag for the next pass. The general "production-ready" guidance
exists; the IME-specific migration recipe does not.

**Predictive Back / Inline Autofill / `UserDictionary` / Personal
Dictionary content provider:** no deltas in the 4-week window. All
existing roadmap entries remain valid as written.

**Play / F-Droid policy for IMEs:** no IME-specific clauses in the
April 15 2026 Play policy bundle. F-Droid published no new IME-
specific inclusion rules. 16 KB ELF page alignment requirement
applies generally — already gated in CI by `android.yml`'s zipalign
step.

---

---

## 3. Open follow-up roster (post-v1.8.92, pre-cutoff)

Surfaced by the v1.8.85 audit but deliberately deferred from the v1.8.85 – v1.8.92
slice so each can land as its own per-PR commit on the standard cadence.
Priorities below are derived from impact / blast-radius / dependency-chain
analysis, not from a fresh re-prioritisation pass.

| # | Item | Impact | Cost | Urg. | Score | Rationale |
|---|---|---:|---:|---:|---:|---|
| F1 | `generate_icon.py` hard-coded Windows path → `pathlib.Path(__file__).parent` | 1 | 1 | 1 | **3.0** | One-line script polish; only matters if anyone re-runs the icon generator on a non-maintainer host |
| F2 | `release.yml` keystore-decode hygiene (`printf %s` not `echo`, `chmod 600`, `umask 077`) | 3 | 1 | 2 | **8.0** | Real but low-frequency forensic-leak risk on the release-runner FS |
| F3 | `fastlane/update-readme.sh` + `fastlane/generate-screenshots.sh` quoting + `set -euo pipefail` + absolute paths | 2 | 1 | 1 | **5.0** | `rm -r out` after implicit `cd ..` is a footgun |
| F4 | `verify-addon-apk.sh` upgrade to `set -eo pipefail` + distinguish "no output" from "no match" | 3 | 1 | 1 | **7.0** | Currently a broken `aapt2` could silently PASS the gate |
| F5 | Sticker palette `LruCache<String, ImageBitmap>` + folder-enumeration cap inside the cursor loop | 3 | 2 | 1 | **3.5** | Real on a 50k-file Downloads tree; v1.8.85 fixed the OOM but not the per-scroll re-decode |
| F6 | Hardware-keyboard `HardwareKeyboardLayout.equals` excludes `scancodeMap` from generated equality | 2 | 2 | 1 | **2.5** | Performance-cliff polish; not load-bearing |
| F7 | In-keyboard banner for SAF lost-grant on the Imported sticker tab (mirror of v1.8.90 Settings surface) | 3 | 2 | 1 | **3.5** | New Snygg element + palette state plumbing; bigger scope than fit in v1.8.90 |
| F8 | `HardwareKeyEntry.longPressAlternates: List<Int>` + long-press popup routing for hardware-keyboard imports | 3 | 4 | 1 | **1.75** | Crosses the parser, data class, popup controller, and Snygg surface; worth its own slice |
| F9 | Crowdin upload action SHA-pin (currently `crowdin/github-action@v2` floating) | 3 | 1 | 1 | **7.0** | `gh api repos/crowdin/github-action/git/refs/tags/v2` returns the canonical SHA |
| F10 | `peter-evans/create-or-update-comment@v4` SHA-pin (TODO comment placed in v1.8.85) | 3 | 1 | 1 | **7.0** | Same shape as F9; SHA lookup is one shell command on the build host |
| F11 | Roborazzi visual baselines for the new `swiftkey_high_contrast` + `aurora_animated` themes + the new Addons settings surface | 3 | 2 | 1 | **3.5** | Once recorded, removes `continue-on-error: true` from `android.yml`'s Roborazzi step |
| F12 | Test for `data_extraction_rules.xml` schema (Android Lint already validates the XML; add a `:app:lintDebug` smoke that the file resolves) | 2 | 1 | 1 | **5.0** | Cheap; pins the v1.8.85 behaviour against accidental rewrite |

Items F2, F4, F9, F10, F12 are the highest-leverage items in this roster
(score ≥ 5.0). They can ship as single-feature releases v1.8.93 – v1.8.97
in a tight back-to-back window before the 2026-05-31 cutoff if maintainer
bandwidth allows.

---

## 4. Recommended ROADMAP v5.4 reframings

These are the corrections / promotions / demotions the next ROADMAP
refresh should fold in:

1. **§6 N7.1 (no-INTERNET build gate)** — promote from "regex check on
   source manifests" to "regex check on source manifests AND per-variant
   `SingleArtifact.MERGED_MANIFEST` check, with `tools:node="remove"`
   exemption". Cite v1.8.85.
2. **§6 N7.4 (personal-dictionary encryption)** — add
   `data_extraction_rules.xml` as a load-bearing sidecar. Cite v1.8.85.
3. **§6 N7.x (atomic restore)** — new sub-item: `ZipUtils.unzip` aborts
   on three classes of security violation, continues-with-warning on
   benign anomalies. Cite v1.8.85, v1.8.89.
4. **§6 N7.x (clipboard-history privacy)** — close the
   `TYPE_NUMBER_VARIATION_PASSWORD` gap. Cite v1.8.86.
5. **§7 Next-6.4b (hardware-keyboard runtime mapper)** — add the
   thread-safety + AltGr fix to the shipped record. Cite v1.8.85.
6. **§7 Next-9.5 (user-imported sticker folder)** — add the bounded-decode
   contract, MIME-spoof fix, and the in-Settings lost-grant surface to
   the shipped record. Add the in-keyboard banner (F7) as Next-9.5a
   open. Cite v1.8.85, v1.8.90.
7. **§7 Next-10.x (addon scaffold)** — record the addon-bundle-size cap
   misuse fix from v1.8.85 (APK-size vs. bundle-size category error).
   Cite v1.8.85.
8. **§7 Next-10.1 (addon spec)** — record the receiver-mandatory KDoc
   alignment with `verify-addon-apk.sh` and `docs/addons/apk-validation.md`.
   Cite v1.8.91.
9. **§6 N12.5 (reproducible-build self-check)** — record the entry-
   manifest pass criterion that replaces the bytes-equal `cmp -s` on
   signed APKs. This now matches F-Droid rebuilder methodology.
   Cite v1.8.85.
10. **§7 Next-3.x / Next-6.4b — LDML import correctness** — record the
    `shift= > longPress=` parser correction. Cite v1.8.92.
11. **§7 Next-7.x — CI supply-chain** — record the file-scope
    `permissions: { contents: read }` rollout and the
    `pull_request_target` env-var hardening pattern. Cite v1.8.85.
12. **§14 Risk Register** — close one row: "INTERNET-via-library-AAR"
    is no longer latent now that v1.8.85 caught it.

These reframings are append-only in the ROADMAP body. The v5.4 header
records the cutover.

---

## 5. New rejections (none)

No new "explicitly rejected" items surface in this pass. The v1.8.85 –
v1.8.92 work tightens existing surfaces rather than opening new feature
slices, so the rejected-set in ROADMAP §10 is unchanged.

---

## 6. External-delta research output

_(Folded in once the three external-research agents return — they were
dispatched in parallel at sixth-pass start. Their findings land in §2
above and are reflected in §4 as new ROADMAP reframings if any are
genuinely incremental over the fifth pass.)_

---

## 7. Sources cited by this pass

- `RELEASE_NOTES_v1.8.85.md` (cross-subsystem hardening — 16 fixes;
  intentional AGENTS.md §6 deviation flagged in the release notes).
- `RELEASE_NOTES_v1.8.86.md` (numeric-PIN keyVariation).
- `RELEASE_NOTES_v1.8.87.md` (FLAG_SECURE + non-saveable passphrase).
- `RELEASE_NOTES_v1.8.88.md` (legacy-passphrase recovery).
- `RELEASE_NOTES_v1.8.89.md` (ZipUtils abort vs. continue).
- `RELEASE_NOTES_v1.8.90.md` (SAF lost-grant in Settings).
- `RELEASE_NOTES_v1.8.91.md` (addon spec docs alignment).
- `RELEASE_NOTES_v1.8.92.md` (LDML shift= > longPress=).
- The fifth-pass research artifacts in `.ai/research/2026-05-17/` — used as
  the delta basis.
- External sources from the §6 agents — see the new entries appended to
  [`SOURCE_REGISTER.md`](SOURCE_REGISTER.md) by the sixth pass.

---

*End of sixth-pass findings. Open items folded into the next ROADMAP
refresh (`v5.4`) and into `ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md`
as commitments. The v5.4 ROADMAP append is the canonical user-facing
record; this file is the audit trail.*

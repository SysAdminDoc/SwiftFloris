# ROADMAP Research Addendum — 2026-05-17

This file is the **actionable output** of the autonomous research run at
[`.ai/research/2026-05-17/`](.ai/research/2026-05-17/). It does **not**
rewrite [ROADMAP.md](ROADMAP.md); it adds new commitments, corrections,
and reframings keyed to existing roadmap sections.

The convention is: every item here either (a) inserts a new line in an
existing `ROADMAP.md` table, (b) corrects an existing claim that the
research run verified to be stale, or (c) promotes/demotes/reframes an
existing item. When the next ROADMAP refresh (`v5.3`) lands, the items
here either flow into the relevant section or are explicitly retired with
reasoning.

**HEAD at latest reconciliation:** v1.8.121 — dead clipboard history store removal (seventh-pass follow-up G9 closure; the full seventh-pass shipped layer is v1.8.104 – v1.8.121, documented in §0.c below).

**Previous reconciliation marker:** v1.8.92 — LDML parser shift= > longPress=.
(The research run started at v1.8.55; v1.8.56-84 shipped concurrently in the
same release window, implementing Phase B4 + Phase C2 + Phase D2 + Phase D3 + Phase B1 seed + Phase B2 + Phase C1 + Phase C3 + Phase D1 + Phase A3 Settings wiring + N8.7 Article 50 transparency + N12.5 reproducible-build self-check + N7.6 Tink migration + Bump-batches A/B/C + README Samsung / Grammarly callouts + glide strategy correction + root crash-log guard + hardware-keyboard import/runtime follow-ups + user sticker folders + Keyman `.kmp` package intake + honeycomb hex layout production wire-up + SQLCipher provider migration plan + Next-10.3a addon catalog foundation + Next-10.3b signing-pin persistence + Next-10.3c startup reconciliation + Next-10.3d Settings status/rescan UI. The sixth-pass v1.8.85-92 cross-subsystem hardening + per-feature follow-ups landed afterwards; see §0.b below.)

---

## 0.c Reconciliation with v1.8.104-121 seventh-pass audit releases

The user re-invoked the extreme-audit prompt after the sixth-pass
roster closed. The seventh research pass dispatched three parallel
agents on the un-audited subsystems (NLP, voice, clipboard) plus a
personal pass on `FlorisImeService` / `EditorInstance`. Two agents
returned with findings; the NLP agent rate-limited (research debt for
the eighth pass).

| Seventh-pass finding | Shipped as |
|---|---|
| Personal pass 1.1: `EditorInstance.handleStartInputView` honoured `IME_FLAG_NO_PERSONALIZED_LEARNING` only when `prefs.suggestion.incognitoMode == DYNAMIC_ON_OFF`; under `FORCE_OFF` the user silently overrode every app-declared cross-app privacy flag (Signal / ProtonMail / banking / E2E chat / password vaults) | ✅ **v1.8.104** — app-declared flag now forces `isIncognitoMode = true` regardless of user pref; user pref governs only user-requested incognito (smartbar toggle, FORCE_ON power-user setting) |
| Personal pass 1.2 + clipboard agent #1: clipboard cut/copy gated only on `isPasswordField()` and `ClipboardItem.fromClipData` read `EXTRA_IS_SENSITIVE` into `item.isSensitive` but `onPrimaryClipChanged` never used the flag as an insertion gate; password-manager credentials and incognito-field text landed in IME-local history | ✅ **v1.8.105** — new `shouldSuppressClipboardHistory()` helper unifies password + incognito signals; both cut and copy now read it. `onPrimaryClipChanged` wraps `insertOrMoveBeginning` in `if (!item.isSensitive)`. System clipboard unchanged |
| Voice agent #7: `switchToVoiceInputMethod` routed voice input to external IMEs (FUTO Voice Input — typically full-network-permission) in password / numeric-PIN / web-password / incognito fields | ✅ **v1.8.106** — early-return + toast `voice_input__suppressed_on_sensitive_field`. Voice IME's privacy boundary doesn't inherit SwiftFloris's no-`INTERNET` contract |
| Voice agent #16: `RemoveItemPattern(canonicalPhrase = "scratch", prefix = "scratch")` was a bare-prefix entry; any utterance starting with "scratch" (including natural prose "let me scratch that idea") silently fired `REMOVE_ITEM_FROM_LIST` and excised text from the committed buffer | ✅ **v1.8.107** — replaced with four explicit suffix-anchored patterns (`scratch X {from\|off} {the }list`). Regression-guard test pins three previously-vulnerable inputs |
| Voice agent #14: `removeItemFromList` collapsed non-empty editor selections and overwrote selected text plus the suffix above the cursor on execution — silent multi-region data loss | ✅ **v1.8.108** — early-return `ACTION_REJECTED` when `content.selectedText.isNotEmpty()` BEFORE the streaming buffer is mutated, so dictation state stays in sync after retry |
| Clipboard agent #11 + #19: backup zip serialised every history row including sensitive ones in plaintext (backup is not passphrase-encrypted); `ClipboardItem.close(context)` only deleted the content-provider URI for `IMAGE`, so video clear-all leaked on-disk files AND kept per-receiver `grantUriPermission` calls live | ✅ **v1.8.109** — `filterNot { it.isSensitive }` at the top of the clipboard backup path; `close()` extended from IMAGE to IMAGE OR VIDEO |
| Voice agent #11: `_isListening` / `_transcriptionState` assigned-and-overwritten in the same synchronous frame as the successful `switchToVoiceInputMethod` call; observers never saw the Listening transition (mic-meter UIs read `isListening` as permanently false; "Connecting to voice IME…" spinners read `transcriptionState` as Ready → Ready) | ✅ **v1.8.110** — state held Listening when the handoff succeeds; `FlorisImeService.onStartInput` calls `voiceInputManager.refreshAvailability()` so state resets to Ready when SwiftFloris is re-bound as the active IME (user returned from FUTO) |
| Clipboard follow-up G2 + G12: provider-backed image/video clipboard clones had no byte cap, and the modern API 28+ preview decode path did not reject oversized dimensions before allocation | ✅ **v1.8.111** — `ClipboardFileStorage.cloneUri` applies 32 MiB image / 128 MiB video copy caps and removes partial failed clones; `ClipboardPreviewImagePolicy` guards both `ImageDecoder` and `BitmapFactory` preview bounds before decode |
| Clipboard follow-up G6: size-limit rotation and timed expiry deleted clipboard-history rows directly, bypassing `ClipboardItem.close(context)` and leaving provider-backed image/video cleanup to receiver-process lifetime | ✅ **v1.8.112** — automatic eviction routes through `ClipboardHistoryEviction.closeThenDelete(...)`, closing media items before Room row deletion |
| Voice follow-up G7: `VoiceInputSetupActivity` needed its non-exported manifest state pinned and accepted arbitrary extras by falling back to `NO_ENABLED_PROVIDER` | ✅ **v1.8.113** — Robolectric manifest test pins `android:exported="false"` and `VoiceInputSetupIntentContract` rejects malformed setup extras |
| Voice follow-up G8: `isVoiceInputReadyForHandoff()` treated every non-FUTO voice IME as ready without checking that package's microphone grant | ✅ **v1.8.114** — `ExternalVoiceInputHandoffPolicy` requires `RECORD_AUDIO` permission for each enabled external voice IME package |
| Clipboard follow-up G10: pin-popup description detection ran URL/email/phone classification over `stringRepresentation()` even for `item.isSensitive`, leaking structural info via badges | ✅ **v1.8.115** — `clipboardItemDescriptionKind` returns no badge for sensitive clips before reading raw text |
| Clipboard follow-up G3: no startup reconciliation between clipboard-history rows, `ClipboardFileInfo` rows, and on-disk provider files; destructive history migration orphaned files forever | ✅ **v1.8.116** — `ClipboardStorageReconciliation` deletes missing-file history rows and unreferenced provider files / metadata rows before history collection |
| Clipboard follow-up G4: backup restore copied provider files but did not recreate `ClipboardFileInfo` rows, so restored media URIs pointed at IDs missing from the provider metadata DB | ✅ **v1.8.117** — restore recreates metadata rows and provider cache misses lazy-load metadata from Room |
| Clipboard agent #2: foreign `content://` image/video URI clone failures were caught inside `ClipboardMediaProvider.insert(...)` and converted into a synthetic `/0` URI, so IME-local history could contain phantom media rows with no backing file | ✅ **v1.8.118** — clone failures now propagate, invalid provider insert URIs are rejected before `ClipboardItem` creation, and `ClipboardManager` logs/skips failed imports |
| Clipboard follow-up G5 + clipboard agent #7: `updateHistory` sorted / rebuilt history on Main and history-limit eviction re-entered through its own Room emission; timed expiry read `currentHistory` without sharing a maintenance lock | ✅ **v1.8.119** — history collection stays on IO, derivation sorts on `Dispatchers.Default`, and size-limit / timed-expiry eviction share one `Mutex`-serialized maintenance path |
| Voice structural follow-up G1: local Whisper/Vosk routes and the model catalog implied a working in-app recognizer runtime despite no `AudioRecord` / Vosk JNI / whisper.cpp glue | ✅ **v1.8.120** — local routes now require `VoiceLocalRecognizerRuntime.AVAILABLE`, Auto falls back to the external voice keyboard while it is false, and Settings marks the model catalog preview-only with download/import disabled |
| Clipboard follow-up G9: `ClipboardHistoryManager` Tink store was a dead parallel clipboard-history backend beside the Room-backed `ClipboardManager` path | ✅ **v1.8.121** — removed the unused manager and unused panel; source regression now pins clipboard history to the Room-backed manager instead of the parallel Tink store |

The v1.8.104 – v1.8.121 release notes are the per-release audit trail
for this seventh-pass shipped layer.

### 0.c.1 Seventh-pass structural finding carried forward

The voice agent surfaced one major structural finding. The product-honesty
branch shipped in v1.8.120; the actual recognizer runtime remains future work:

**Voice no-local-recogniser story.** The voice catalog UI advertises
Whisper tiny/base/large + seven Vosk packages and lets users
download ~3 GB cumulative, but `RECORD_AUDIO` is not declared in the
manifest, no `AudioRecord` / Vosk JNI / whisper.cpp glue code exists,
and the auto-route never reaches the local-engine branches. The only
working voice path is the external-IME handoff (FUTO Voice Input).
v1.8.120 flags the catalog UI as preview-only and gates local routes
behind an explicit runtime-available flag until the recognizer lands.
The recognizer integration itself remains a dedicated future release
(mirrors the L1 / L2 / L3 facade-only pattern documented in
[`PROJECT_CONTEXT.md` §8](PROJECT_CONTEXT.md)).

### 0.c.2 Seventh-pass open follow-up roster

Full priority-scored roster in
[`.ai/research/2026-05-17/SEVENTH_PASS_FINDINGS.md §5`](.ai/research/2026-05-17/SEVENTH_PASS_FINDINGS.md);
closed follow-ups from this roster:

- ✅ **Clipboard #2** — failed foreign `content://` media clones no
  longer create phantom history rows. Shipped in **v1.8.118**.
- ✅ **G5** — `enforceHistoryLimit` `Mutex` + off-Main collection,
  bundled with clipboard agent #7 timed-expiry synchronization.
  Shipped in **v1.8.119**.
- ✅ **G1** — Local voice model catalog / route selector must not imply
  a working in-app Whisper/Vosk runtime. Shipped in **v1.8.120** as a
  preview-only catalog and runtime-availability gate.
- ✅ **G9** — `ClipboardHistoryManager` Tink store was a dead parallel
  backend beside the Room path. Removed in **v1.8.121**.
- ✅ **G2** — `ClipboardFileStorage.cloneUri` max-size cap (image /
  video). Shipped in **v1.8.111**.
- ✅ **G6** — `revokeUriPermission` on clipboard history rotation /
  expiry path (previously only on explicit delete). Shipped in
  **v1.8.112**.
- ✅ **G7** — `VoiceInputSetupActivity` `android:exported="false"` +
  validate Intent extras. Shipped in **v1.8.113**.
- ✅ **G8** — `isVoiceInputReadyForHandoff()` checks per-external-IME
  `RECORD_AUDIO` grant. Shipped in **v1.8.114**.
- ✅ **G10** — Pin-popup `NetworkUtils.isUrl` skipped when
  `item.isSensitive`. Shipped in **v1.8.115**.
- ✅ **G12** — `uriToPreviewBitmap` modern (API 28+) branch max-size
  guard. Shipped in **v1.8.111**.

### 0.c.3 Seventh-pass research debt

- **NLP / autocorrect / suggestion-strip / KenLM-header / phantom-space
  audit.** Seventh-pass agent rate-limited; no findings returned.
  Eighth-pass agent should pace at 1-2 parallel agents instead of
  three. The subsystem remains un-audited in depth.

---

## 0.b Reconciliation with v1.8.85-92 sixth-pass hardening releases

The sixth research pass ([`.ai/research/2026-05-17/SIXTH_PASS_FINDINGS.md`](.ai/research/2026-05-17/SIXTH_PASS_FINDINGS.md))
ran five parallel read-only research agents over the v1.8.75-84 slices
plus the foundational privacy / build / CI infrastructure. Eight releases
landed that implemented the findings:

| Sixth-pass finding | Shipped as |
|---|---|
| `verifyNoInternetPermission` only scanned `app/src/**` source manifests — library AARs could re-add INTERNET via manifest merging and slip past the gate. Also did not honour `tools:node="remove"`. | ✅ **v1.8.85** — per-variant `verifyNoInternetPermissionMerged<Variant>` wired against AGP's `SingleArtifact.MERGED_MANIFEST`; `tools:node="remove" \| "removeAll"` exemption added. Hooked into `processManifest` (finalizedBy) and `assemble` (dependsOn). |
| `android:dataExtractionRules` pointed at a `<full-backup-content>` schema file — wrong schema for Android 12+, system fell back to default-include-all for D2D transfer, carrying the SQLCipher personal-dictionary DB + Tink-wrapped passphrase prefs to a new device where they cannot decrypt. | ✅ **v1.8.85** — new `app/src/main/res/xml/data_extraction_rules.xml` with explicit `<exclude>` entries for every dictionary DB sidecar (.db, .db-journal, .db-wal, .db-shm), the wrap-key SharedPrefs, and the clipboard-history directory, in both `<cloud-backup>` and `<device-transfer>` rule sets. |
| `ZipUtils.unzip` warned-and-continued on every guard — a malicious archive with one good entry + one zip-slip entry left the good entry on disk and the caller saw `Result.Success`. Also no entry-count cap. | ✅ **v1.8.85** (pre-canonical entry-name guard + 10_000-entry cap) and ✅ **v1.8.89** (split: abort-class throws `SecurityException` for unsafe-name / zip-slip / entry-count; benign anomalies still continue-with-warning). |
| `HardwareKeyboardRuntimeMapper`'s `layoutsByDeviceId` was a plain `LinkedHashMap` touched from both the IME input thread and the settings UI thread — concurrent `put`+`remove` could corrupt the bucket array and throw `ConcurrentModificationException`. | ✅ **v1.8.85** — monitor lock around every mutation / read. |
| `HardwareKeyboardRuntimeMapper.map()` rejected every `isCtrlPressed` event, but Android delivers PC-style AltGr as Ctrl+Alt, so AltGr-mapped characters on `.klc` imports were silently dropped. | ✅ **v1.8.85** — gate now rejects Ctrl only when Alt is *not* also pressed. |
| `StickerPaletteView` called `BitmapFactory.decodeStream(stream)` with no `BitmapFactory.Options` — a 100k×100k PNG would allocate ~40 GB and crash the IME. | ✅ **v1.8.85** — two-pass decode (inJustDecodeBounds → reject > 8192 px → inSampleSize to ~512 px target edge). |
| Sticker MIME-type spoof: SAF-declared `application/octet-stream` on `evil.png` fell through to extension-based MIME, so the recipient `commitContent` receiver saw `image/png` for arbitrary bytes. | ✅ **v1.8.85** — SAF MIME is now source of truth; extension fallback only when SAF returns null. |
| Addon enumerator's bundle-size gate read APK file size (`File(sourceDir).length()`) and rejected legitimate 64MB+ theme/dictionary packs through `AddonManifest.init { require(...) }`. Category error — APK is never loaded into RAM by the IME. | ✅ **v1.8.85** — bundle-size field clamped to 0L at enrolment; real enforcement moves to asset-mount time (future Next-10.4). |
| `scripts/verify-reproducible-apk.sh` top-level pass criterion was `cmp -s` on signed APKs, which can never be byte-identical (v2/v3 signing block has randomised padding) — every signed-build run failed. | ✅ **v1.8.85** — payload-entry-manifest pass criterion replaces the bytes-equal test, matching F-Droid rebuilder methodology. |
| `validate-strings-no-translations.yml` (under `pull_request_target` with base-repo write token) interpolated PR-author filenames directly into `run:` shell blocks. | ✅ **v1.8.85** — every `${{ github.event.* }}` and step-output value passed via `env:`, referenced as `"$VAR"`; `gh api --paginate --jq` replaces hand-rolled `curl + jq` paginator; `set -euo pipefail` everywhere. |
| CI workflows shipped with the default repo `GITHUB_TOKEN` (typically read-write). | ✅ **v1.8.85** — file-scope `permissions: { contents: read }` on `android.yml`, `crowdin-upload.yml`, `reproducible-build.yml`. |
| `EditorInstance.handleStartInputView` hard-coded `keyVariation = NORMAL` for every `Type.NUMBER` field, bypassing every `keyVariation == PASSWORD` privacy gate for `TYPE_NUMBER_VARIATION_PASSWORD` (numeric-PIN / OTP entry — the IME-local clipboard history wrote PINs). | ✅ **v1.8.86** — numeric-PIN fields propagate `keyVariation = PASSWORD` while preserving `KeyboardMode.NUMERIC` for the layout. |
| `DictionaryPassphraseDialog` was screen-recordable (no `FLAG_SECURE` on the host window while up) and the passphrase round-tripped through `savedInstanceState` via `rememberSaveable`. | ✅ **v1.8.87** — `DisposableEffect`-scoped `FLAG_SECURE` set on entry / cleared on dispose; passphrase storage changed to plain `remember`. |
| `FlorisUserDictionaryEncryption.getOrCreatePassphrase` had a hard `error(...)` for the legacy-AndroidX-keyset-but-decrypt-failed edge case (Android Keystore master key rotated, partial pref restore) — IME crash on startup. | ✅ **v1.8.88** — recovery: log warning, clear unreadable legacy keyset prefs, fall through to fresh-passphrase generation. |
| Sticker palette silently emptied the Imported tab when Android revoked the persistable SAF URI grant — no user signal. | ✅ **v1.8.90** — `UserStickerRepository.hasPersistableReadPermission` helper; Settings preference summary shows "Folder access lost. Tap to select again." Tap re-enters the folder picker. |
| `AddonContract.kt` KDoc said the REGISTER broadcast receiver was *optional* — contradicting the Android 11+ `<queries>`-based visibility mechanism that the project's `AndroidManifest.xml`, `verify-addon-apk.sh`, and `docs/addons/apk-validation.md` all already enforce. | ✅ **v1.8.91** — KDoc now mandates the receiver and explains the intent-filter-as-visibility-mechanism. |
| `KeymanLdmlParser` read `longPress=` first as the shift slot, falling back to `shift=` — but LDML defines `shift=` as the shift-modifier mapping and `longPress=` as a space-separated alternates list. Wrong slot was picked whenever both were declared; multi-alternate `longPress` poisoned the shift slot. | ✅ **v1.8.92** — new ordering: `shift=` wins; single-value `longPress=` falls through as legacy shift fallback (preserves Amharic-SERA-style backward-compat); multi-alternate `longPress=` leaves shift = null (await `longPressAlternates` field + long-press popup). Three new tests cover the case matrix. |

The eight follow-up releases are tagged locally; push is blocked by the
same VM 403 documented in [`AGENTS.md`](AGENTS.md).

### 0.b.1 Sixth-pass follow-up roster — **all closed v1.8.93 – v1.8.102**

Surfaced by the v1.8.85 audit and deferred from the v1.8.85 – v1.8.92
slice; landed as ten single-feature releases over 2026-05-17:

| Item | Shipped as |
|---|---|
| **F2** — `release.yml` keystore-decode hygiene (`printf %s` instead of `echo`, `chmod 600`, `umask 077`, JKS / PKCS#12 magic-byte gate, `gh release create` env-var hardening) | ✅ **v1.8.93** |
| **F4** — `verify-addon-apk.sh` upgrade to `set -eo pipefail`, distinguish "tool failed to invoke" from "tool produced no output" from "tool succeeded but no match" — broken `aapt2` can no longer silently PASS | ✅ **v1.8.94** |
| **F12** — Build gate `verifyDataExtractionRules` pinning the load-bearing excludes in `data_extraction_rules.xml` against accidental rewrite | ✅ **v1.8.95** |
| **F9** — Pin `crowdin/github-action@v2` to `8868a33591d21088edfc398968173a3b98d51706` | ✅ **v1.8.96** |
| **F10** — Pin `peter-evans/create-or-update-comment@v4` to `71345be0265236311c031f5c7866368bd1eff043` | ✅ **v1.8.96** |
| **F3** — `fastlane/update-readme.sh` Python block-substitution replacement for the `sed -i` interpolation footgun; `fastlane/generate-screenshots.sh` absolute-path cleanup instead of `cd ..; rm -r out` | ✅ **v1.8.97** |
| **F1** — `generate_icon.py` portability (`Path(__file__).resolve().parent` instead of hard-coded Windows absolute) | ✅ **v1.8.98** |
| **F6** — `HardwareKeyboardLayout.equals` fast-path skips the structural map walk in the common cases | ✅ **v1.8.99** |
| **F5** — Sticker palette `LruCache<String, ImageBitmap>` (64-entry) + cursor-time enumeration cap in `UserStickerRepository` | ✅ **v1.8.100** |
| **F7** — In-keyboard banner for SAF lost-grant — mirror of the v1.8.90 Settings-side surface | ✅ **v1.8.101** |
| **F8** — `HardwareKeyEntry.longPressAlternates` + LDML parser tokenisation; popup-UI routing intentionally deferred to its own future slice | ✅ **v1.8.102** |

The follow-up roster is fully closed. The remaining open items below are
all multi-week feature slices, external-clock-dependent, or maintainer
outreach tasks; they did not land in the autonomous-loop window.

### 0.b.2 Sixth-pass external delta — new commitments

The 2-week external-research pass surfaced four genuinely new commitments
not already in this addendum:

1. **SwiftKey-refugee discovery (Tier-1, urgency 5 / 14 days).**
   AlternativeTo, BGR, and Android Authority round-ups now name
   HeliBoard / FUTO / FlorisBoard / AnySoftKeyboard as the
   SwiftKey-migration escape route — **SwiftFloris is on none of them**.
   This is the single highest-leverage outreach gap of the migration
   window.
   **Status — 2026-05-17:** drafts shipped at
   [`docs/outreach/2026-05-17-swiftkey-migration/`](docs/outreach/2026-05-17-swiftkey-migration/)
   covering all four surfaces (AlternativeTo entry, BGR comment,
   Android Authority comment, r/Swiftkey post). Maintainer to review
   and post from their own accounts during the 2026-05-28 to 2026-05-30
   window; the drafts directory's `README.md` documents the
   recommended order and engagement guidance. Citations:
   <https://alternativeto.net/software/swiftkey/>,
   <https://www.androidauthority.com/heliboard-gboard-alternative-3505462/>,
   <https://www.bgr.com/2003971/android-keyboards-replace-google-gboard-swiftkey-heliboard/>.

2. **F-Droid Reproducible-Builds Verified Tier (Tier-1, urgency 3).**
   F-Droid Basic 2.0-alpha9 ships visible per-app reproducibility
   badges; SwiftFloris's v1.8.67 reproducible-build self-check
   (N12.5) plus the v1.8.85 entry-manifest pass criterion mean the
   project is *one submission step* from being the visibly-reproducible
   exemplar keyboard. Action: when F-Droid Basic 2.0 stable ships,
   submit SwiftFloris's reproducible-build metadata to the F-Droid
   data repository and request a rebuilder-anchored verification badge.
   Citations: <https://f-droid.org/en/2025/05/21/making-reproducible-builds-visible.html>,
   <https://nlnet.nl/project/Reproducible-F-Droid/>.

3. **LiteRT-LM v0.11.0 as the concrete L1.1a target (Tier-2).**
   LiteRT-LM v0.11.0 stable shipped 2026-05-07 with Gemma 4 multi-token
   prediction (>2× decode on mobile GPU, zero quality loss). Gemma 4
   shipped 2026-04-02 under **Apache 2.0**, aligning cleanly with the
   `:app` license ceiling. Action: when ROADMAP §8 L1.1a moves out of
   the gate, target LiteRT-LM v0.11+ and the Gemma 4 E2B checkpoint as
   the canonical addon runtime. Citations:
   <https://github.com/google-ai-edge/LiteRT-LM/releases>,
   <https://blog.google/innovation-and-ai/technology/developers-tools/gemma-4/>.

4. **Android 17 IME compliance slice (Tier-2, urgency 1 — June 2026
   stable).** Android 17 stable lands in June 2026. The two IME-relevant
   behaviors are (a) IME visibility no longer auto-restored across
   unhandled config changes — host-app problem, but SwiftFloris should
   confirm it's benign for the IME service; (b) new
   `TextAttribute.setTextSuggestionSelected()` for CJKV candidate
   selection a11y, wire behind the existing API 37 gate. **No
   compileSdk bump required before stable.**
   **Status — 2026-05-17 (post-v1.8.103 audit):** the gates are already
   shipped where they need to be. **N13.2** (IME-visibility-restore
   across config change) is at
   [`FlorisAppActivity.kt:113-127`](app/src/main/kotlin/dev/patrickgold/florisboard/app/FlorisAppActivity.kt);
   **N13.3** (long-press-popup suppression for password fields under the
   new `show_passwords_physical` separation) is at
   [`PasswordFieldPopupGate.kt`](app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/keyboard/PasswordFieldPopupGate.kt)
   plus [`TextKeyboardLayout.kt:307-316`](app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/keyboard/TextKeyboardLayout.kt).
   The IME service surface is benign (the agent's note was explicit:
   "targets host apps, not the IME service"). `TextAttribute.setTextSuggestionSelected()`
   remains the only outstanding behavior, but it should land WITH the
   CJK candidate UI (currently L3 librime addon territory) rather than
   ahead of it — speculative wiring without a corresponding consumer
   is the wrong shape. No further pre-stable code action required;
   pick up the CJKV `TextAttribute` work as part of L3.
   Citations:
   <https://developer.android.com/about/versions/17/release-notes>,
   <https://developer.android.com/about/versions/17/behavior-changes-17>.

### 0.b.3 Sixth-pass external delta — confirmed negative signal

The sixth-pass external research confirmed three "no-op" findings:

- **HeliBoard NLnet open-glide library:** no in-window milestone. L1.3
  glide-engine swap remains correctly gated; do not pull forward.
  Gesture-data window ends 2026-11-30, grant deadline 2026-06-01.
  Source: <https://nlnet.nl/project/GestureTyping/>.
- **Bergamot translator:** no new release in window; last tagged build
  2024-09-20. L2.1a addon blocker unchanged.
- **FlorisBoard upstream:** no release in window. Latest stable still
  v0.5.2 (2024-11-28); alpha line stalled at v0.6.0-alpha02. Nothing
  to cherry-pick.
- **On-device LLM keyboard demand:** HN threads on on-device LLMs in
  May 2026 do not request keyboard integration. Gboard's Gemini Nano
  "Rambler" occupies the on-device-AI-keyboard mindshare on Pixel.
  Keep L1.1a as Later / Under Consideration, **not Now**. Sources:
  <https://news.ycombinator.com/item?id=47019133>,
  <https://news.ycombinator.com/item?id=47016559>.

---

## 0. Reconciliation with concurrent v1.8.56-84 releases

While this research run was in flight, sixteen releases landed that
implemented several recommendations:

| Recommendation in this addendum | Shipped as |
|---|---|
| Phase B4 same-sentence language-switch hardening (PRIORITIZATION_MATRIX Tier-1 #22) | ✅ **v1.8.56** — geometric-decay (`decay = 0.7`) weighted blend in new `TrailingContextLanguageBlend`; 8 new tests |
| §B.3 — Dedicated arrow-keys row preset (P24) | ✅ **v1.8.57 — Phase C2** — labeled `BottomRowPreset.Navigation` (with ARROW_LEFT / ARROW_UP / SPACE / ARROW_DOWN / ARROW_RIGHT / ENTER); equivalent to my N4.4 proposal modulo naming |
| §C.2 — Tasks quick-insert (P10) (PRIORITIZATION_MATRIX Tier-2 #19) | ✅ **v1.8.58 — Phase D2** — `QuickAction.InsertTask` via `Intent.ACTION_SEND` chooser; `SensitiveFieldGuard` gate; works with Tasks.org / OpenTasks / Google Tasks / Joplin / Notion / Markor |
| §C.4 — Personalization stats delta (P26) | ✅ **v1.8.59 — Phase D3** — `CorrectionOutcomePriors.accuracyDelta()` and Settings → Typing stats row for current-week accepted corrections versus last week |
| §B.1 — Multilingual sentence-position priors seed (P13 partial) | ✅ **v1.8.60 — Phase B1** — EN/CS/DE/ES/FR/IT/PT cold-start priors plus top-1,000 wordfreq Zipf seed overlays for CS/DE/ES/FR/IT/PT |
| Phase B2 quick-prediction-insert tuning (P18) | ✅ **v1.8.61 — Phase B2** — configurable weighted-confidence floor plus same-path plain-space suppression for blank-current-word prediction insertion |
| Phase C1 split-keyboard renderer (P3) | ✅ **v1.8.62 — Phase C1** — viable split gutter rendering in `TextKeyboardLayout` plus gutter-aware touch behavior |
| Phase C3 bundled themes (P14/P15) | ✅ **v1.8.63 — Phase C3** — `swiftkey_high_contrast` AAA-tested stylesheet + `aurora_animated` GenericShape background renderer |
| Phase D1 calendar quick-insert (P9) | ✅ **v1.8.64 — Phase D1** — `QuickAction.InsertCalendarEvent`, `CalendarContract.Instances` reader, explicit `READ_CALENDAR` request, and IME-local agenda picker |
| Phase A3 encrypted dictionary export/import wiring (P12) | ✅ **v1.8.65 — Phase A3** — Settings **Export encrypted** passphrase flow, `.sfexp` create-document write, `SFEXP1` import sniffing, decrypt, and `PersonalDictionaryImportBatch` summary/rollback routing |
| §B.2 EU AI Act transparency surface (N8.7) | ✅ **v1.8.66 — N8.7** — first-run setup disclosure, Settings → About → **AI features in this keyboard**, docs links, and catalog test coverage |
| §B.4 Reproducible-build self-verification CI (N12.5) | ✅ **v1.8.67 — N12.5** — build-twice clean-worktree release APK workflow, byte compare, and drift manifests |
| §A.2 / §G.2 — AndroidX Security Crypto deprecated API surface (N7.6) | ✅ **v1.8.68 — N7.6** — removed AndroidX Security Crypto, added Tink Android 1.21.0, and migrated SQLCipher passphrase + legacy clipboard-history encrypted-preference payloads |
| §A.4 / Tier-1 #4 — Bump-batch A | ✅ **v1.8.69 — N14.5** — coroutines 1.11.0, KSP 2.3.8, ZXing 3.5.4, AboutLibraries stable 14.2.0 |
| Tier-1 #2 / #5 / #10 — README Samsung / Grammarly migration-window callouts + release-front-door hygiene | ✅ **v1.8.70 — N16.4** — Galaxy AI Writing Assist framed as optional Samsung layer on One UI 7+; Grammarly Keyboard replacement framed as an overlay above SwiftFloris |
| §A.4 / Tier-1 #12 — Bump-batch B | ✅ **v1.8.71 — N14.6** — Roborazzi 1.60.0 + Robolectric 4.16.1 |
| §G.1 / Tier-1 #9 — HeliBoard slip-risk promote | ✅ **v1.8.72 — N1.1** — statistical glide is production default; HeliBoard open-glide remains additive |
| Tier-1 #14/#15 — root crash/replay log cleanup + CI guard | ✅ **v1.8.73 — N18.1** — logs moved out of root; committed root logs fail CI |
| Tier-2 #16 — Bump-batch C | ✅ **v1.8.74 — N14.7** — AGP 9.2.1 + Compose BOM 2026.05.00 |
| Tier-2 #27 — macOS `.keylayout` parser | ✅ **v1.8.75 — Next-6.4a** — XXE-hardened parser normalizes macOS key maps and modifier maps into `HardwareKeyboardLayout` |
| Tier-2 #28 — Hardware-keyboard runtime mapper | ✅ **v1.8.76 — Next-6.4b** — device-id layout binding + KLC/macOS/source-name runtime fallbacks |
| Tier-3 #29 — User-imported sticker folder | ✅ **v1.8.77 — Next-9.5** — SAF folder picker, local image enumeration, imported sticker pack previews, provider proxy commits |
| Tier-3 #34 — Keyman `.kmp` package import foundation | ✅ **partial v1.8.78 — L8.3 / Tier-3 #34** — safe ZIP/package metadata intake, LDML-in-package extraction, lexical-model/mixed/compiled-required classification; compiled `.kmx` / `.js` runtime remains future addon work |
| Tier-3 #35 — Honeycomb hex layout wire-up | ✅ **v1.8.79 — L9.2 / Tier-3 #35** — selectable bundled honeycomb layout, production `TextKeyboardLayout` style, clipped hex key surfaces, and hex-aware hit testing |
| Tier-3 #36 — SQLCipher OpenSSL/BoringSSL provider migration plan | ✅ **v1.8.80** — corrected the LibTomCrypt-removal premise against Zetetic 4.14/4.16 provider matrices, kept the stock AAR, and documented OpenSSL proof-of-concept triggers / gates |
| Next-10.3a — Addon catalog foundation | ✅ **v1.8.81** — `AddonRegistry` live state + signing-pin reconciliation and `DictionaryPackCatalog` descriptor/provenance validation for dictionary packs |
| Next-10.3b — Addon signing-pin persistence | ✅ **v1.8.82** — `AddonSigningPinSet` codec + `prefs.addon.signingCertPins` durable trust key |
| Next-10.3c — Addon registry startup wiring | ✅ **v1.8.83** — IME startup scans installed addon manifests, reconciles through persisted signing pins, publishes `AddonRegistryStore`, and cleans malformed stored pin lines |
| Next-10.3d — Settings → Addons status surface | ✅ **v1.8.84** — route + Home entry + read-only accepted/rejected rows + manual rescan through `AddonRegistryStartup` |

These shipped items are removed from this addendum's open commitments. Historical
sections below are preserved in place; rows with a **Status: shipped** marker
are no longer open.

---

## A. Material corrections to existing ROADMAP claims

### A.1 KenLM is LGPL — incompatible with `:app`

**Where:** ROADMAP §7 Next-3.1 (lines 511 ff. — KenLM binary header reader scaffold).

**Issue:** KenLM is licensed **LGPL-2.1+**. Per ROADMAP §1 the `:app`
ceiling is Apache-2.0; LGPL cannot link into `:app`.

**Resolution:** the in-`:app` `KenLmBinaryReader.readHeader` parser is
**fine** — it parses a public binary format and is original work. The
**JNI bring-up against the KenLM library itself** must move to a
separate addon APK. Add explicit text to Next-3.1:

> Implementation note: the KenLM **runtime** is LGPL-2.1+ and therefore
> cannot link into `:app` (see §1 Apache-2.0 ceiling). The in-`:app`
> header parser is original code parsing a public format and is unaffected.
> Runtime scoring ships in `addons/kenlm-jni/` (a sibling-repo addon APK)
> at the same trust boundary as the librime / Bergamot / handwriting-mlkit
> addons.

If KenLM-in-addon proves heavy, the Apache-2.0 alternative is
**SentencePiece** (`google/sentencepiece`), which the project could adopt
in-`:app`. Cited in [.ai/research/2026-05-17/SECURITY_AND_DEPENDENCY_REVIEW.md §4](.ai/research/2026-05-17/SECURITY_AND_DEPENDENCY_REVIEW.md#4-license-compatibility-verification).

### A.2 `androidx-security-crypto:1.1.0-alpha06` is a deprecated-API artifact

**Where:** [app/build.gradle.kts](app/build.gradle.kts#L299) inline pin.

**Issue:** fifth-pass verification corrected the earlier wording:
`androidx.security:security-crypto:1.1.0` did ship, but the AndroidX
release notes deprecate the APIs in favor of platform APIs and direct
Android Keystore use. SwiftFloris was still pinned to older
`1.1.0-alpha06`; `EncryptedSharedPreferences` remained the wrong long-term
primitive for SQLCipher-passphrase wrapping.

**Status:** ✅ shipped 2026-05-17 in v1.8.68. The implementation went one
step broader than the original SQLCipher-only wording because the now-retired
`ClipboardHistoryManager` also depended on AndroidX Security Crypto:

- `androidx.security:security-crypto:1.1.0-alpha06` removed from
  `app/build.gradle.kts`.
- `com.google.crypto.tink:tink-android:1.21.0` added.
- New `TinkStringPreferenceCrypto` wraps local preference bytes / strings
  with Tink `Aead`, AndroidKeystore-held AES-256-GCM keys, and
  `prefsFile:key` associated data.
- SQLCipher passphrase storage migrates from `sqlcipher_passphrase_v1` to
  `sqlcipher_passphrase_tink_v1`.
- Legacy clipboard history migrated from `clipboard_history` to
  `clipboard_history_tink_v1`; that parallel store was later removed in
  v1.8.121 once the Room-backed `ClipboardManager` path was confirmed
  canonical.

Original ROADMAP item:

> **N7.6 (NEW)** Replace `androidx-security-crypto` with Google Tink
> (`com.google.crypto.tink:tink-android`, Apache-2.0). Wrap the SQLCipher
> passphrase via Tink `Aead`; protect the wrapping key through
> `AndroidKeystoreV1` KMS. Migrate existing on-disk passphrase shape via
> one-shot detection (same pattern as the v1.7.4 plaintext-DB →
> encrypted-DB migration). Update `PersonalDictionaryEncryptionTest` to
> pin the new contract. Effort: ~1 day. Reference:
> https://github.com/tink-crypto/tink-java

This is captured as Tier-1 #3 in
[.ai/research/2026-05-17/PRIORITIZATION_MATRIX.md](.ai/research/2026-05-17/PRIORITIZATION_MATRIX.md).

### A.3 `androidx-activity 1.13.0` is stable — downgrade retired

**Where:** [gradle/libs.versions.toml](gradle/libs.versions.toml#L4)
`androidx-activity = "1.13.0"`.

**Correction:** fifth-pass verification against AndroidX release notes
and Google Maven metadata shows `1.13.0` is the stable release. The
first-pass recommendation to downgrade to `1.12.4` is wrong.

**Resolution:** keep `androidx-activity = "1.13.0"` unless tests reveal a
SwiftFloris-specific regression. Bump-batch A no longer includes Activity.

### A.4 Multiple deps materially behind

**Where:** [gradle/libs.versions.toml](gradle/libs.versions.toml).

**Issue:** the following pins are materially behind 2026-05-17 latest:

| Pin | Current | Latest | Action |
|---|---|---|---|
| AGP | 9.2.1 | 9.2.1 stable (`9.3.0-alpha05` preview skipped) | ✅ shipped v1.8.74 |
| Compose BOM | 2026.05.00 | 2026.05.00 | ✅ shipped v1.8.74 |
| kotlinx-coroutines | 1.11.0 | 1.11.0 | ✅ shipped v1.8.69 |
| KSP | 2.3.8 | 2.3.8 | ✅ shipped v1.8.69 |
| Roborazzi | 1.60.0 | 1.60.0 | ✅ shipped v1.8.71 |
| Robolectric | 4.16.1 | 4.16.1 | ✅ shipped v1.8.71 |
| aboutlibraries | 14.2.0 | 14.2.0 stable (`15.0.0-b01` beta exists) | ✅ shipped v1.8.69 |
| zxing-core | 3.5.4 | 3.5.4 | ✅ shipped v1.8.69 |

**Resolution:** Tier-1 #4 (Bump-batch A: low risk) shipped in v1.8.69;
Tier-1 #12 (Bump-batch B: visual-regression infrastructure) shipped in
v1.8.71; Tier-2 #16 (Bump-batch C: build toolchain) shipped in v1.8.74.
Maintainer-host Gradle verification is still required because this VM has no
Java on PATH.

### A.5 FlorisBoard upstream is frozen on v0.6.0-alpha02

**Where:** ROADMAP §4 Strategic Thesis — "Upstream drift. FlorisBoard
v0.6-alpha targets glide typing, predictions, floating mode, and Snygg
v2 themes."

**Issue:** v0.6.0-alpha02 shipped **2025-01-23**. **No alpha03 in 16+
months.** v0.6 milestone was re-scoped — glide + predictions were pushed
to v0.7 (the public-beta milestone). Last upstream stable is v0.5.2
(2025-11-28).

**Resolution:** SwiftFloris is **lapping a stalled upstream**, not
drifting from a moving target. Update §4 framing:

> 1. **Upstream drift, now upstream-lap.** FlorisBoard v0.6 alpha milestone
>    has been re-scoped — the v0.6 alpha02 (2025-01-23) is the latest
>    upstream alpha and ships Snygg v2 + floating + S-Pen-text-input
>    fixes; glide typing and word suggestions were pushed to v0.7's
>    public-beta milestone, which has no published date as of
>    2026-05-17. SwiftFloris is ahead of upstream on autocorrect,
>    dictionary, multilingual ranking, SwiftKey-parity surface polish,
>    voice handoff, MCP bridge, and encrypted personal dict; the
>    remaining upstream-only items SwiftFloris should consider absorbing
>    are Snygg v2 engine refresh and the CLDR 48 / Emoji 17 bumps.

### A.6 MediaPipe LLM Inference on Android is deprecated

**Where:** ROADMAP §8 L1 + Appendix `[STD-LITERT-LM]`.

**Issue:** Google officially deprecated the MediaPipe LLM Inference API
on Android (docs touched 2026-03-31). LiteRT-LM is the named successor.
ROADMAP already targets LiteRT-LM, but **does not explicitly REJECT**
MediaPipe — so a future contributor could re-propose it.

**Resolution:** add to ROADMAP §10 (Explicitly Rejected):

> | MediaPipe LLM Inference API on Android | Officially deprecated by Google as of 2026-03-31. LiteRT-LM is the named successor and the project's L1 target. Listed here so MediaPipe doesn't get re-proposed |

### A.7 FunctionGemma 270M shipped Jan 2026 — should be named in L1

**Where:** ROADMAP §8 L1 + §C.3.

**Issue:** ROADMAP §8 L1 names "Gemma 3 270M Q4 INT4 (~135 MB)" as the
target. In **January 2026** Google released **FunctionGemma**, a 270M
function-calling variant of Gemma 3 fine-tuned on Mobile Actions
(`litert-community/functiongemma-270m-ft-mobile-actions` on HuggingFace).
Action-call accuracy 58 % → 85 %. This is the more relevant model for
**any agentic / tool-use Smart Compose** that talks to the MCP daemon
bridge.

**Resolution:** add to §8 L1:

> **L1.1b (NEW)** FunctionGemma 270M as the named model target for the
> agentic / tool-use Smart Compose path. Same `.litertlm` packaging as
> Gemma 3 270M Q4; structured `tools/call` + natural-language
> explanation in one prompt. Pairs with the MCP daemon bridge
> (Settings → MCP) so user-installed daemons (calendar, weather, SMS
> tools) become first-class call targets.

---

## B. New ROADMAP items (NOW tier)

### B.1 N6.6 — Migration-window outreach checklist (full)

**Where:** ROADMAP §6 N6 (CI + release engineering hardening) or §6
new N16-extension.

**Why now:** SwiftKey cutoff is 14 days from HEAD; ROADMAP §14 Risk
Register already names this as the one-shot opportunity but has no
checklist for the marketing slice.

**Historical proposed body:**

> - 2026-05-25 — soft pre-launch comms: README banner already up;
>   add a "see you on 5/30" pin to the GitHub repo description.
> - 2026-05-28 — short Reddit posts to r/SwiftKey, r/PrivacyGuides,
>   r/HeliBoard, r/fossandroid (link `docs/MIGRATE_FROM_SWIFTKEY.md`
>   + the Obtainium one-tap URL above the fold). Cap one post per
>   subreddit per week; no spam.
> - 2026-05-30 — pinned GitHub release on the day before the cutoff.
>   Release body opens with the migration story, the Obtainium URL,
>   and the `swiftkey-cloud.json` instructions. SHA-256 + signing
>   fingerprint published per N6.2 / N7.5.
> - 2026-05-31 — repo description updated to "SwiftKey migration
>   importer now in v1.8.<x>" if Phase A4 (still-open marketing
>   surface) is ready.
> - 2026-06-07 — one-week-after retrospective in a new
>   `docs/MIGRATION_RETRO_2026-06-07.md` recording how many
>   `swiftkey-cloud.json` imports succeeded vs failed in the
>   `DictionaryImporter` audit log, and how the parser was tuned
>   if anything broke.

### B.2 N8.7 — EU AI Act Article 50 transparency surface (2 Aug 2026)

**Status:** ✅ shipped 2026-05-17 in v1.8.66. `SetupScreen` now starts with
`Steps.AiFeatures` before IME enablement, `prefs.internal.aiFeaturesExplainerSeen`
stores the one-time acknowledgement, `Routes.Settings.AiFeatures` exposes a
reopenable Settings → About screen, and `AiFeatureDisclosureCatalogTest` pins
the first-run surface inventory.

**Where:** ROADMAP §6 N8 (Accessibility scoped pass — accessibility is
the closest existing surface, though this is privacy/regulatory not a11y;
could also land in §11 Privacy Hardening).

**Why now:** EU AI Act Article 50 transparency duties apply from
**2 August 2026**. Any AI-assisted feature interacting directly with users
must inform the user at first interaction.

**Body:**

> - **N8.7.1** First-run AI-features explainer screen lives next to
>   `app/setup/`. Lists the AI/ML surfaces in the IME
>   (next-word, glide, voice, translate, smart-compose), states
>   plainly "all processing on this device, no data leaves the device,
>   no vendor accounts," and links to `docs/THREAT_MODEL.md` +
>   `PROJECT_CONTEXT.md`. Cost: S.
> - **N8.7.2** Re-openable Settings → About → "AI features in this
>   keyboard" screen with the same content. Cost: XS.
> - **N8.7.3** New `docs/PRIVACY_AND_AI.md` writes up the privacy
>   posture diff vs SwiftKey / Gboard / Grammarly side-by-side. Cost: S.
> - Acceptance: `verifyNoInternetPermission` still passes; first-run
>   surface is keyboard-disabled-safe; explainer reopenable.

### B.3 N4.4 — Dedicated arrow-keys row preset (P24)

**Where:** ROADMAP §6 N4 (Customizable bottom row + smartbar).

**Status:** ✅ shipped 2026-05-17 in v1.8.57. The implementation is named
`BottomRowPreset.Navigation` rather than `ArrowsRow`, and it surfaces
ARROW_LEFT / ARROW_UP / SPACE / ARROW_DOWN / ARROW_RIGHT / ENTER from
Settings → Keyboard → Bottom-row preset.

**Why it mattered:** SwiftKey-parity P24 was a small, high-confidence UX gap.
`BottomRowPreset.Programmer` already provided the scaffolding for a separate
navigation-focused preset.

**Body:**

> **N4.4 (NEW)** New `BottomRowPreset.ArrowsRow` selectable in Settings
> → Keyboard → Bottom-row preset → "Arrows row." Surfaces ← → ↑ ↓ +
> Home / End cluster in the main letter view. Closes SwiftKey-parity
> P24. Cost: S; reuses Next-8.1a `BottomRowKey` shape.

### B.4 N12.5 — Reproducible-build self-verification CI

**Status:** ✅ shipped 2026-05-17 in v1.8.67. New
`.github/workflows/reproducible-build.yml` invokes
`scripts/verify-reproducible-apk.sh`, which builds release APKs from two
detached clean worktrees at the same commit, compares bytes, and writes
ZIP-entry SHA-256 manifests on drift.

**Where:** ROADMAP §6 N12 (Performance instrumentation + Roborazzi).

**Why now:** F-Droid verified-tier badge launched 2025-05; ~21 % of main
repo apps reproducible; SwiftFloris's pin matrix is in place but
`fdroiddata` submission has not happened.

**Body:**

> **N12.5 (NEW)** Local "build twice, compare APK checksums" CI job:
> assembleRelease in a clean checkout twice, diff the APK signing
> blocks, fail if non-determinism beyond the expected
> embedded-commit-hash region. Catches reproducibility regressions
> before F-Droid's rebuilder does. Cost: S.

### B.5 N16.3 — Tag every shipped release (catch-up)

**Where:** ROADMAP §6 N16 (the existing migration-related cluster — or
§12 Operating Cadence).

**Status:** ✅ shipped locally 2026-05-17 as release metadata. This item was
renumbered from the draft `N16.2` label because `ROADMAP.md` already uses
`N16.2` for the SwiftKey `swiftkey-cloud.json` importer.

**Why now:** latest tag was `v1.8.40`; HEAD was `v1.8.69`. **29 missing
tags** since v1.8.40. Obtainium auto-update keys off GitHub Releases, but
release.yml triggers on `workflow_dispatch` not on tag-push, so the release
stream is decoupled from tags. Tags are still the canonical shipped-commit
anchor for forks / audit.

**Body:**

> **N16.3 (SHIPPED LOCALLY)** Tag every shipped release v1.8.41 through
> v1.8.69 from its corresponding `gradle.properties`-bumping commit. Tags
> push only on the user's main host (push to `SysAdminDoc/SwiftFloris` is
> blocked from the dev VM per the established workflow). Establish a
> per-release "tag concurrently with the release notes commit" rule going
> forward.

## C. New ROADMAP items (NEXT tier)

### C.1 Next-9.5 — User-imported sticker folder

**Where:** ROADMAP §7 Next-9 (Inline `commitContent()` for sticker / GIF
/ image insertion).

**Why now:** No surveyed keyboard offers user-imported sticker libraries.
The `StickerMediaProvider` already in tree handles the URI + permission
grant. Inserting a SAF document tree as the stickers source closes the
last open piece.

**Body:**

> ✅ **Next-9.5 shipped in v1.8.77.** Settings → Emoji & stickers opens a
> SAF tree picker; selected URI is persisted via `prefs.sticker.userFolderUri`;
> `UserStickerRepository` walks the folder for `.png` / `.webp` / `.jpg` /
> `.jpeg` / `.gif` documents and surfaces them as an Imported sticker pack in
> the media panel. Reuses the existing `commitContent(InputContentInfoCompat)`
> rich-content path through `StickerMediaProvider`. Long-press deletion from
> the chosen folder remains a later explicit SAF write-flow polish item.

### C.2 Next-10.4 — HeliBoard-style dictionary downloader UI

**Where:** ROADMAP §7 Next-10 (Plugin / addon APK loading).

**Why now:** HeliBoard's killer ecosystem feature is the in-app
dictionary catalog + download UI. SwiftFloris's Next-10.3 now has both
dictionary-pack addon schemas and the v1.8.81 process-local catalog
foundation; the Settings list + install-hint UI and APK asset mounting are the
missing pieces.

**Body:**

> **Next-10.4 (NEW)** Settings → Addons → Dictionary packs screen lists
> installed addon APKs that declare
> `dev.patrickgold.florisboard.action.REGISTER_DICTIONARY_PACK`,
> and (when the FlorisBoard Addons marketplace exposes a no-network
> directory file or a curated bundled JSON in `assets/`) lists
> available-not-yet-installed packs with a "Get from F-Droid /
> IzzyOnDroid" link. **No in-app download** (would require INTERNET);
> just discoverability + install-hint. Cost: M.

### C.3 Next-12.6 — Roborazzi baseline for every bundled theme

**Where:** ROADMAP §7 Next-12 (Performance instrumentation + Roborazzi).

**Body:**

> **Next-12.6 (NEW)** Capture Roborazzi baseline PNGs for each of the
> 21 bundled themes against (a) the QWERTY letter keyboard,
> (b) the suggestion strip with 3 candidates, (c) the smartbar with
> the CODE profile active, and (d) the emoji palette. Removes the
> `continue-on-error: true` from `:app:verifyRoborazziDebug` in
> CI. Cost: M.

## D. New ROADMAP items (LATER tier)

### D.1 L13 — CleverKeys-architecture Apache-2.0 reimplementation

**Where:** ROADMAP §8 (LATER) — new tier-13 item.

**Why now:** CleverKeys (GPL-3.0) is shipping a working 13 MB transformer
encoder/decoder glide model with sub-200 ms latency on Pixel 7 via
XNNPACK in F-Droid as of v1.4.0 (2026-04-26). SwiftFloris cannot link it
but could train an Apache-2.0 model against the same architecture once
the HeliBoard NLnet swipe dataset (or another permissive set) lands.

**Body:**

> **L13. CleverKeys-architecture Apache-2.0 glide reranker**
>
> - L13.1 — train an encoder/decoder transformer (~5 MB encoder + ~8 MB
>   decoder ONNX, XNNPACK / ONNX Runtime Mobile) against a permissive
>   swipe-trace corpus. Architecture reference: `tribixbite/CleverKeys-ML`.
> - L13.2 — plug into the existing `NeuralCandidateReranker` boundary as
>   a glide-specific path; heuristic ranker stays the default.
> - L13.3 — share the dataset upstream under a permissive license so the
>   FOSS keyboard field benefits.
>
> **Gated on:** HeliBoard NLnet open-glide dataset release (or any
> permissively-licensed corpus). Slip-base-case framing in §6 N1.1 applies.

## E. Items in §9 (Under Consideration) to promote

### E.1 Promote: Per-app "tone profile" (KenLM weight swap by package)

**Why now:** Per-app profile detection plumbing already shipped
(Next-4.3). The tone-profile angle is one line of preference + one new
Snygg "active package" hook. Promote from Under Consideration → §7 Next.

**Body suggestion:** Promote to **Next-13 (NEW)** with a "depends on
addon-side KenLM bring-up via the §A.1 path" gate.

## F. ROADMAP §10 (Explicitly Rejected) — additions

The following should be added with reasoning to prevent re-litigation:

| New rejected item | Why |
|---|---|
| **MediaPipe LLM Inference API on Android** | Officially deprecated 2026-03-31; LiteRT-LM is the successor (already targeted in §8 L1) |
| **NLLB-200 (offline NMT)** | CC-BY-NC-4.0 license — non-commercial conflicts with §1 audit-friendly distribution. Bergamot (MPL-2.0) is the right path |
| **CleverKeys glide model (as-shipped)** | GPL-3.0; cannot link. Architecture is the reference (see new §8 L13). Restated for completeness |
| **KenLM library linked into `:app`** | LGPL-2.1+; cannot link. Header parser in-`:app` is fine; runtime ships in addon (see §A.1). Restated for completeness |

## G. ROADMAP §14 (Risk Register) — updated rows

### G.1 HeliBoard NLnet slip risk — promote to "High" (shipped v1.8.72)

**Current row:**

> | HeliBoard's NLnet glide drop ships first and becomes the de facto OSS swipe lib | High | Low (good for users; we adopt it; positive outcome) | Plan N1.1 as the default path |

**Updated:**

> | HeliBoard NLnet open-glide library slips past 2026-06-01 deadline | **High (now base case)** | Medium (delays N1.1; keeps SwiftFloris on the bounded statistical classifier) | Reframe N1.3 statistical as the *production* default, not the placeholder. Plan N1.1 integration as additive once the library lands. Gesture-data accrual via HeliBoard's data-gathering feed already started; whether the dataset gets a permissive release is the second-order risk |

**v1.8.72 implementation note:** re-checked HeliBoard `#2226`, HeliBoard
releases, the NLnet project page, and the gesture-data contribution wiki on
2026-05-17. The latest HeliBoard release remains `v3.9` from 2026-03-29,
`#2226` remains open, and the public workflow is still collecting gesture
data with the existing proprietary library. `ROADMAP.md` now treats
`swiftfloris-statistical` as production default and keeps `heliboard-open` as
a future `prefs.glide.engine` option only after a permissive library + dataset
land and pass the N1.4 replay benchmark.

### G.2 New row — `androidx-security-crypto` deprecated API surface

| New risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| `androidx-security-crypto:1.1.0-alpha06` kept SQLCipher-passphrase wrapping on deprecated AndroidX Security APIs even though 1.1.0 stable exists | ✅ resolved in v1.8.68 | Medium before fix (no crash, but security hygiene + key-rotation issues + F-Droid review smell) | Migrated to Google Tink + direct AndroidKeystore wrapping; also covered legacy clipboard-history encrypted preferences |

### G.3 New row — EU AI Act Article 50 cutoff

| New risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| EU AI Act Article 50 transparency duties (2 Aug 2026) apply to next-word / glide / voice / translate / smart-compose | Certainty | Low if surface added on time; Medium if not (regulatory review smell from EU users / F-Droid editors) | Ship first-run explainer + Settings → About → "AI features" screen + `docs/PRIVACY_AND_AI.md` (see new N8.7 item) |

## H. ROADMAP §13 (Out-of-Scope Adjacent Wins) — note

The Samsung One UI 7 decoupling of Galaxy AI Writing Assist from Samsung
Keyboard is a **good-news adjacency**, not an out-of-scope item. It
removes the vendor-keyboard lock-in for users on Samsung S25/S26. Worth
calling out in README (Tier-1 #2 in
[PRIORITIZATION_MATRIX.md](.ai/research/2026-05-17/PRIORITIZATION_MATRIX.md))
but does not require a roadmap change.

---

## Status legend

🟢 = new commitment ready to ship in next slice
🟡 = new commitment, gated on listed dependency
🔄 = correction / reframing to existing item
🔴 = new risk-register entry, action required
✅ = shipped in this release window

| Item | Status |
|---|---|
| §A.1 KenLM license boundary | 🔄 |
| §A.2 Tink migration (new N7.6) | ✅ v1.8.68 |
| §A.3 activity 1.13.0 downgrade retired | 🔄 |
| §A.4 Bump-batches A/B/C | ✅ v1.8.69 (A); ✅ v1.8.71 (B); ✅ v1.8.74 (C) |
| §A.5 Upstream-lap framing | 🔄 |
| §A.6 MediaPipe rejection | 🔄 |
| §A.7 FunctionGemma named target | 🔄 |
| §B.1 Migration outreach checklist | 🟢 |
| §B.2 EU AI Act surface (N8.7) | ✅ v1.8.66 |
| §B.3 Arrows-row preset (N4.4) | ✅ v1.8.57 |
| §B.4 Reproducible-build CI (N12.5) | ✅ v1.8.67 |
| §B.5 Tag catch-up (N16.3) | ✅ local tags v1.8.41-v1.8.84; push pending from maintainer host |
| Tier-1 README Samsung / Grammarly callouts (N16.4) | ✅ v1.8.70 |
| Tier-1 root crash/replay log cleanup + CI guard | ✅ v1.8.73 |
| §C.1 User-imported sticker folder (Next-9.5) | ✅ v1.8.77 |
| Tier-3 architecture / contributing docs | ✅ docs-only 2026-05-17 |
| Tier-3 root multilingual / voice docs consolidation | ✅ docs-only 2026-05-17 |
| Tier-3 #34 Keyman `.kmp` package import foundation | ✅ partial v1.8.78; compiled runtime/addon remains |
| Tier-3 #35 Honeycomb hex layout wire-up | ✅ v1.8.79 |
| Tier-3 #36 SQLCipher provider migration plan | ✅ v1.8.80 |
| Next-10.3a Addon catalog foundation | ✅ v1.8.81 |
| Next-10.3b Addon signing-pin persistence | ✅ v1.8.82 |
| Next-10.3c Addon registry startup wiring | ✅ v1.8.83 |
| Next-10.3d Settings → Addons status surface | ✅ v1.8.84 |
| Seventh-pass G2 clipboard media clone caps | ✅ v1.8.111 |
| Seventh-pass G6 clipboard rotation / expiry cleanup | ✅ v1.8.112 |
| Seventh-pass G7 voice setup intent hardening | ✅ v1.8.113 |
| Seventh-pass G8 external voice IME microphone gate | ✅ v1.8.114 |
| Seventh-pass G10 sensitive clipboard description guard | ✅ v1.8.115 |
| Seventh-pass G3 clipboard startup storage reconciliation | ✅ v1.8.116 |
| Seventh-pass G4 clipboard restore media metadata | ✅ v1.8.117 |
| Seventh-pass clipboard foreign-URI clone failure guard | ✅ v1.8.118 |
| Seventh-pass G5 clipboard history maintenance serialization | ✅ v1.8.119 |
| Seventh-pass G1 local voice catalog preview gate | ✅ v1.8.120 |
| Seventh-pass G9 dead clipboard history store removal | ✅ v1.8.121 |
| Seventh-pass G12 clipboard preview decode bounds | ✅ v1.8.111 |
| §C.2 Dictionary downloader UI (Next-10.4) | 🟡 on signing-pin revoke/reset UX + asset mounting |
| §C.3 Roborazzi per-theme baseline (Next-12.6) | 🟡 on Bump-batch B |
| §D.1 L13 CleverKeys-arch Apache-2.0 | 🟡 on dataset |
| §E.1 Per-app tone profile promotion | 🟡 on addon-side KenLM |
| §F new §10 rejections | 🔄 |
| §G.1 HeliBoard slip-risk promote | ✅ v1.8.72 |
| §G.2 Tink-migration risk | ✅ v1.8.68 |
| §G.3 EU AI Act risk | ✅ v1.8.66 |

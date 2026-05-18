# Seventh-Pass Findings — 2026-05-17

**Scope:** second extreme-audit pass on the same date, post v1.8.103 +
post-sixth-pass-roster-closure (v1.8.93-102 + outreach drafts +
Android-17 audit). The user re-invoked the audit prompt; this pass
covers the subsystems the original five sixth-pass agents did NOT
deeply audit: **NLP / autocorrect**, **voice input**, **clipboard
manager**, **FlorisImeService lifecycle**. Original sixth pass had
covered: addon registry, hardware keyboard, sticker import, backup /
restore + crypto, scripts / CI.

Three external research agents were dispatched in parallel plus a
personal pass on `FlorisImeService` / `EditorInstance`. Two agents
returned with substantial findings; the NLP agent hit an upstream
rate limit and returned no output — flagged as **research debt for
the eighth pass**.

**Local state at start:** clean worktree, `master…origin/master
[ahead 105]`, HEAD `3dd879b` (`docs: Android 17 (API 37) gate audit`).
HEAD release: v1.8.103. `java` is not on PATH on this VM; gradle
Definition-of-Done verification remains on the maintainer host.

This pass produces:

1. This file — the per-pass record.
2. Updates to
   [`../../../ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md`](../../../ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md)
   reflecting the seven new releases (v1.8.104-110) and the
   structural findings the per-PR slice could not absorb.
3. A new `## 0. Research Refresh v5.7` section appended to
   [`../../../ROADMAP.md`](../../../ROADMAP.md) ahead of the preserved
   v5.5 / v5.4 / v5.3 blocks.

---

## 1. Personal pass — FlorisImeService / EditorInstance privacy gates

Two related findings surfaced from following the
`activeState.isIncognitoMode` propagation chain and the
`isPasswordField()` callers. Both are real privacy regressions that
the existing test surface did not cover.

### 1.1 `IME_FLAG_NO_PERSONALIZED_LEARNING` ignored when IncognitoMode is FORCE_OFF

[`EditorInstance.handleStartInputView`](../../../app/src/main/kotlin/dev/patrickgold/florisboard/ime/editor/EditorInstance.kt)
previously honoured the app-declared
`IME_FLAG_NO_PERSONALIZED_LEARNING` only when the user's
`prefs.suggestion.incognitoMode` was `DYNAMIC_ON_OFF` (the default).
A user who set it to `FORCE_OFF` silently overrode every cross-app
sensitive-field declaration (Signal, ProtonMail, banking, E2E chat,
password vaults).

**Status:** ✅ shipped **v1.8.104**. App-declared flag now always
forces `isIncognitoMode = true` regardless of user preference. User's
IncognitoMode preference continues to govern user-requested incognito
(smartbar toggle, FORCE_ON power-user setting).

### 1.2 Clipboard cut/copy did not gate on `isIncognitoMode`

`EditorInstance.performClipboardCut/Copy` gated only on
`isPasswordField()`. A user typing in Signal (incognito due to
NO_PERSONALIZED_LEARNING — now forcibly honoured per 1.1) who hit
Cut would leak the selected text into the IME-local clipboard
history, where it could be re-pasted into any app via the clipboard
palette — bypassing the host-app's privacy declaration.

**Status:** ✅ shipped **v1.8.105**. New
`shouldSuppressClipboardHistory()` helper that returns true on
either signal (password OR incognito). Both cut and copy now read
the helper. Bundled with the clipboard agent's finding #1
(`EXTRA_IS_SENSITIVE` gate on the primary-clip sync path).

---

## 2. Voice subsystem agent — 19 findings

Voice was not audited by the sixth-pass agents. The seventh-pass
agent produced 19 findings spanning model-integrity, missing
sensitive-field guards, race conditions, and parser footguns. Five
shipped in this pass; four were structural / out-of-scope.

### Shipped

| Finding | Severity | Shipped as |
|---|---|---|
| **#7** — no sensitive-field guard in `switchToVoiceInputMethod`; voice routed to external IME (which typically has full network permission) in password / numeric-PIN / web-password / incognito fields | High | ✅ **v1.8.106** — early-return + toast `voice_input__suppressed_on_sensitive_field` |
| **#16** — `RemoveItemPattern(canonicalPhrase = "scratch", prefix = "scratch")` was bare-prefix; any utterance starting with the word "scratch" silently fired `REMOVE_ITEM_FROM_LIST` against committed text | Medium | ✅ **v1.8.107** — replaced with four explicit suffix-anchored variants (`scratch X {from \| off} {the }list`) + regression-guard test |
| **#14** — `removeItemFromList` collapsed any non-empty editor selection and overwrote selected text plus the suffix above the cursor on execution — silent multi-region data loss | Medium | ✅ **v1.8.108** — early-return `ACTION_REJECTED` when `content.selectedText.isNotEmpty()` BEFORE buffer mutation |
| **#11** — `_isListening` and `_transcriptionState` set to Listening then overwritten back to Ready in the same synchronous frame; observers never saw the Listening transition | Medium | ✅ **v1.8.110** — keep state Listening when handoff succeeds; reset via `voiceInputManager.refreshAvailability()` on `onStartInput` rebind when SwiftFloris regains focus |
| **#12** — `VoiceInputSetupActivity` exported by default (no manifest verify on `android:exported`); accepts arbitrary `Intent` extras | Low | ✅ **v1.8.113** — manifest test pins `exported=false`; setup intent contract rejects missing / unknown / unexpected extras. |
| **#17** — `isVoiceInputReadyForHandoff()` returns true for any non-FUTO voice IME without checking that IME's mic permission | Low | ✅ **v1.8.114** — all enabled external voice IME packages must pass `RECORD_AUDIO` permission before handoff readiness. |

### Open (structural — multi-file / out-of-scope for per-PR)

| Finding | Severity | Disposition |
|---|---|---|
| **#1, #2, #5, #6** — `VoiceModelInstallStore` has no model-integrity validation (any SAF input stream is "installed" once bytes > 0); `RECORD_AUDIO` not declared in manifest so the auto-route never reaches local engines; no actual `AudioRecord` / Vosk JNI / whisper.cpp integration exists anywhere in the codebase | High (advertising-vs-reality) | **STRUCTURAL.** The voice catalog UI advertises Whisper tiny/base/large + seven Vosk packages and lets users download ~3 GB cumulative, but the local-recogniser glue code does not exist — the only working voice path is the external-IME handoff (FUTO Voice Input). Either ship the recognizer integration as part of a dedicated future release (multi-week feature slice; mirrors the L1 / L2 / L3 facade-only pattern documented in PROJECT_CONTEXT.md §8) OR flag the catalog UI as preview-only / hide it behind a developer-options toggle until the recogniser lands. Flagged for the eighth pass and the next ROADMAP refresh. |
| **#3, #4** — concurrent model-install races against itself; `sweepStaleStagingDirs()` deletes other in-flight staging dirs | Medium | Bundle with the L1.1a / Vosk integration slice when it lands. |
| **#8** — engine-selector mis-labels failure reason when no engine is available | Medium | UX polish on a code path that is dead until #6 lands. Defer. |
| **#10** — `acceptFinal` may re-commit stale text on a cumulative-final echo from non-incremental recognisers | Medium | Affects only the in-tree recogniser path (#6); defer with that slice. |
| **#13** — `parser.parse` runs twice per utterance (once for command threshold, once for suggestion threshold) | Low | Perf-only polish on a non-hot path. Defer. |
| **#15** — parameterised matches bypass the enabled-set gate that the UI filters on | Medium | Partially addressed by v1.8.107's tightening of the patterns themselves; the broader "enabled-action set" gate is a larger refactor. Flag for future slice. |
| **#18, #19** — `delete` returns true for missing dir (false-positive UI message); no `FEATURE_MICROPHONE` check on tablets without a mic | Low | UX polish. Defer. |

---

## 3. Clipboard subsystem agent — 20 findings

### Shipped

| Finding | Severity | Shipped as |
|---|---|---|
| **#1** — `onPrimaryClipChanged` parsed `ClipDescription.EXTRA_IS_SENSITIVE` into `ClipboardItem.isSensitive` but never used the flag as an insertion gate; password managers' sensitive clips landed in IME-local history regardless | High | ✅ **v1.8.105** — `if (!item.isSensitive)` gate wrapping `insertOrMoveBeginning(item)`. Bundled with personal finding 1.2 (incognito gate on cut/copy). |
| **#11** — clipboard backup zip serialised every history row including `isSensitive` ones in plaintext (backup is not passphrase-encrypted) | Medium | ✅ **v1.8.109** — `filterNot { it.isSensitive }` at the top of the backup path. |
| **#19** — `ClipboardItem.close(context)` only deleted the content-provider URI for `ItemType.IMAGE`; videos leaked both the on-disk file and per-receiver `grantUriPermission` calls | Medium | ✅ **v1.8.109** — extended to `IMAGE OR VIDEO`. |
| **#3** — `ClipboardFileStorage.cloneUri` had no max-size cap for provider-backed image/video media clones | Medium | ✅ **v1.8.111** — 32 MiB image cap, 128 MiB video cap, and partial private-file cleanup on failed clones. |
| **#16** — `uriToPreviewBitmap` modern (API 28+) branch had no max-size guard before bitmap allocation | Low | ✅ **v1.8.111** — shared `ClipboardPreviewImagePolicy` rejects unknown or >8192 px bounds before preview decode. |
| **#13** — `revokeUriPermission` only on explicit delete, not on history rotation / expiry | Medium | ✅ **v1.8.112** — size-limit rotation and timed expiry now close provider-backed media items before deleting Room rows. |
| **#9** — pin-popup `stringRepresentation()` ran URL/email/phone detection on unredacted text even when `isSensitive` is true, leaking structural info via icon | Low | ✅ **v1.8.115** — `clipboardItemDescriptionKind` skips classification for sensitive clips before reading raw text. |
| **#4** — no startup reconciliation between DB rows and on-disk files; `fallbackToDestructiveMigration` orphaned provider media files forever | Medium | ✅ **v1.8.116** — startup reconciliation deletes missing-file history rows and unreferenced provider files / metadata rows before history collection. |
| **#10** — backup-restore dropped the `ClipboardFileInfo` row, so restored items had URIs pointing at IDs not in `clipboard_files` table | Medium | ✅ **v1.8.117** — restore recreates metadata rows and provider cache misses lazy-load metadata from Room. |
| **#2** — no `SecurityException` catch when reading a foreign content URI; phantom history entries pointing at non-existent files | Medium | ✅ **v1.8.118** — clone failures now propagate instead of returning a synthetic `/0` URI; invalid provider insert URIs are rejected before item creation; manager logs and skips failed imports. |
| **#5, #6** — `enforceHistoryLimit` recurses on its own emission; `updateHistory` runs on Main and sort + filter at every emission | Medium | ✅ **v1.8.119** — Room collection stays on IO, derivation sorts on `Dispatchers.Default`, and eviction is serialized behind one history-maintenance `Mutex`. |
| **#7** — `enforceExpiryDate` reads `currentHistory` on a background timer with no sync | Low | ✅ **v1.8.119** — timed expiry now shares the same maintenance `Mutex` and visible-history prune path as size-limit eviction. |

### Open (mostly perf / UX polish or structural)

| Finding | Severity | Disposition |
|---|---|---|
| **#8** — no pin-cap; pinned items grow unbounded (combined with #4, pinned media files leak forever) | Low | UX polish + a pref; defer. |
| **#12** — `openFile` doesn't pre-check `file.exists()` (FileNotFoundException to receiver) | Low | Defer. |
| **#14** — `primaryClipLastFromCallback` duplicate check too narrow on text-clear-text-cycle | Low | Defer. |
| **#15** — `ClipboardMediaProvider.init` loads `cachedFileInfos` async; receiver calls during cold-paste race the cache | Medium | One-PR fix; flag. |
| **#17** — `ClipboardHistoryManager` (the v1.8.68 Tink store) appears to be dead code on the IME path — the real store is `ClipboardManager`'s Room DB; the two parallel stores can drift | Low | Confirm intent; either delete or wire as the storage backend. |
| **#18, #20** — minor cross-user / UX polish | Very low | Defer. |

---

## 4. NLP / autocorrect / suggestion agent — rate-limited, no output

The agent crashed with `API Error: Server is temporarily limiting
requests · Rate limited` after a long run. No findings returned.
This subsystem (NlpManager, suggestion ranking, latin/han subpackages,
DictionaryManager, smartbar suggestion strip, text/composing) remains
**un-audited in depth** as of the seventh pass.

The personal-pass finding 1.1 + 1.2 touched NLP-adjacent surfaces
(the incognito gate is consumed by `learnIfAllowed` in
`KeyboardManager`, which feeds `DictionaryManager.learnWord`), but a
proper deep audit of the suggestion-strip race conditions, distance-
algorithm boundaries, surrogate-pair handling, KenLM header parser,
phantom-space lifecycle, etc., did not complete.

**Disposition:** carry forward to the eighth pass with the same
agent prompt. Avoid scheduling parallel agents during peak rate-
limit windows.

---

## 5. Open follow-up roster (post-v1.8.119)

Items the seventh-pass audit surfaced that remain open after the
v1.8.119 clipboard history maintenance serialization slice. Priority-scored.

| # | Item | Source | Impact | Cost | Urg. | Score |
|---|---|---|---|---:|---:|---:|
| G1 | Voice no-local-recogniser: hide / preview-only-flag the local engine catalog UI OR start the integration | Voice #6 | 4 | 4 | 1 | **2.25** |
| G9 | `ClipboardHistoryManager` (Tink store) — confirm intent; delete or wire as backend | Clipboard #17 | 2 | 2 | 1 | **2.5** |
| G11 | NLP / autocorrect / suggestion full re-audit (rate-limited agent) | — | 4 | 4 | 2 | **2.5** |

No high-leverage items (score ≥ 5.0) remain. The remaining items are
structural, external-clock-dependent, or lower-score clipboard follow-ups.

---

## 6. Sources

- Voice subsystem agent output (in-session, agentId `a62da4a58412340e6`).
- Clipboard subsystem agent output (in-session, agentId `ae17df2497d54c6aa`).
- NLP subsystem agent (rate-limited, no output; agentId `a64f84cebff6af200`).
- Personal pass against `FlorisImeService.kt`, `EditorInstance.kt`,
  `KeyboardManager.kt`, `DictionaryManager.kt` —
  see [`SEVENTH_PASS_FINDINGS.md §1`](SEVENTH_PASS_FINDINGS.md) and
  the inline file:line links in each of the v1.8.104-110
  `RELEASE_NOTES_v*.md` files.

---

*End of seventh-pass findings. Open items folded into the next
ROADMAP refresh (`v5.14` at current HEAD) and into
[`ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md` §0.c](../../../ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md).
The ROADMAP append is the canonical user-facing record; this
file is the audit trail.*

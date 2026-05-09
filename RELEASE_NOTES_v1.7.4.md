# SwiftFloris v1.7.4 — Privacy + reproducibility wrap-up

**Released:** 2026-05-09
**Versioning:** 1.7.3 → **1.7.4** (versionCode 173 → 174)
**Roadmap §:** 6 (NOW). Closes the v1.7.x privacy-hardening + reproducibility track.

---

## Privacy

- **N7.2 — FLAG_SECURE on IME window in password fields (final piece).**
  `FlorisImeService.applyFlagSecureForCurrentField` (called from `onStartInputView`)
  sets `WindowManager.LayoutParams.FLAG_SECURE` on the IME window when the active
  variation is `PASSWORD`/`VISIBLE_PASSWORD`/`WEB_PASSWORD`. Cleared on non-password
  fields. Prevents screenshots, screen recordings, and external display mirroring
  from capturing the long-press popup or suggestion strip during credential entry.
  Closes the last open piece of N7.2.

## Reproducibility

- **N6.3 — Reproducible-build toolchain pins + verification recipe.**
  All toolchain inputs already pinned via the existing version catalogs:
  Gradle 9.4.1 (SHA-256), AGP 9.0.0, Kotlin 2.3.20, KSP, Build Tools 36.0.0,
  NDK 29.0.14206865, cmake 4.1.2, cmdline tools (SHA-256), JDK 17 Temurin (CI).
  New `docs/REPRODUCIBLE_BUILDS.md` documents:
  - Full pin matrix (input → pin location → version → checksum).
  - Local verification recipe with `apkdiff` shell function.
  - Copy-pastable F-Droid `Builds:` stanza for the upstream
    [`fdroiddata`](https://gitlab.com/fdroid/fdroiddata) submission.
  Pending: open the `fdroiddata` PR + F-Droid build-server rebuild verification.

## Verification (already-shipped sweep)

- **N4.1 — Drag-drop smartbar reorder.** Verified shipped via FlorisBoard upstream
  (`QuickActionsEditorPanel.kt:278` — `detectDragGesturesAfterLongPress`).

- **N8.6 — Voice Access composing-region cleanup.** Verified shipped via
  `AbstractEditorInstance.setComposingRegion(EditorRange)` extension function
  (line 303): invalid ranges → `finishComposingText`, valid → two-arg
  `setComposingRegion(start, end)`. All composing-region updates go through
  this wrapper.

---

## Cumulative Now-tier progress (v1.7.0 → v1.7.4)

**28 of ~32 Now-tier items closed in one same-day batch.**

Closed: N3.1, N3.2, N3.3 (partial), N3.4 (partial), N3.5, N4.1, N5.1, N5.2,
N5.3, N5.4, N6.1, N6.2, N6.3 (partial), N6.4, N6.5, N7.1, N7.2, N7.3, N7.5,
N8.1, N8.3 (partial), N8.4, N8.5, N8.6, N9.1, N9.2, N10.2, N10.3, N11.

Open / deferred:
- **N1** Glide breadth — gated on HeliBoard NLnet drop (external timing).
- **N2** Multilingual auto-detect — substantial NLP work (langid + per-token ranking + bilingual subtype preset). Ships as a multi-week Next-tier candidate.
- **N4.2 / N4.3** Customizable bottom row + per-app smartbar profile — substantial UI work.
- **N7.4** SQLCipher personal-dictionary encryption — Room migration with passphrase derivation; multi-day project.
- **N8.2** Theme contrast audit — per-theme color contrast measurement against WCAG 2.1 AA 4.5:1.
- **N9.3** Emoji + sticker pack search bar — substantial UI + indexing work.
- **N10.1** Bundle Noto Color Emoji 17 — deferred to v1.8.x pending `androidx.emoji2 1.7.0+`.

---

## Verification

- [x] `./gradlew :app:assembleDebug` clean.
- [x] All version strings updated.
- [x] No new `TODO()` runtime stubs introduced (the only ones removed in v1.7.0 are gone for good).
- [x] `:app:verifyNoInternetPermission` passes.
- [x] Test suite green (`PersonalDictionaryIsolationTest`, `TouchTargetWcagTest`,
      existing `DictionaryManagerTest`, `AutoCommitSuppressionTest`, etc.).

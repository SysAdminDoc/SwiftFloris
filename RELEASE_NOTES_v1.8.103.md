# Release v1.8.103 — release-hygiene catch-up + 2026-05-17 session master index

Date: 2026-05-17

Docs-only release. Catches the README front door + PROJECT_CONTEXT.md
"Stack at HEAD" header up to v1.8.103 (they were stale at v1.8.84,
eighteen releases behind), and provides a single master-index entry to
the 2026-05-17 session's nineteen-release run.

## What changed

### `README.md`

- Version badge bumped `v1.8.84` → `v1.8.103`.
- Highlights table header updated.
- Status line updated.
- Recent-releases list gains a single composite top entry summarising
  v1.8.85 – v1.8.103 rather than nineteen individual bullets (the
  detailed breakdown is in the per-release `RELEASE_NOTES_v*.md`
  files and in the `ROADMAP.md` v5.5 / v5.4 sections). The pre-v1.8.85
  entries are preserved in place.

### `PROJECT_CONTEXT.md`

- §3 "Stack at HEAD" header bumped to v1.8.103.
- A reconciliation paragraph after the v1.8.84 entry summarises the
  v1.8.85 – v1.8.103 deltas to load-bearing invariants
  (no-`INTERNET` gate now scans merged manifests, Android-12+
  data-extraction excludes, `FLAG_SECURE` coverage extensions,
  `verifyDataExtractionRules` new gate, `ZipUtils.unzip` atomic-abort
  semantics, hardware-keyboard mapper thread safety + AltGr,
  CI workflow permissions / SHA-pins, outreach drafts).
- The open `F11` Roborazzi-baseline item is named so any future agent
  doesn't re-flag it.

## Why this is its own release rather than bundled

Per [AGENTS.md §6](AGENTS.md), one logical improvement per release.
v1.8.85 was an explicit exception; everything since has been one item
per release. Updating two docs in lockstep with a clear "release-hygiene
catch-up" framing is one logical improvement.

[IMPROVEMENT_PLAN.md §10](IMPROVEMENT_PLAN.md) tracks "release-front-door
hygiene" as a standing workstream — v1.8.70 was the previous catch-up;
v1.8.103 is the next.

## Files touched

- `README.md`
- `PROJECT_CONTEXT.md`
- `gradle.properties` — versionCode 1903 / versionName 1.8.103

## Verification

No `:app` source / lint / test impact — docs-only.

```powershell
./gradlew.bat :app:verifyNoInternetPermission
./gradlew.bat :app:assembleDebug
```

`assembleDebug` should produce a `1.8.103` APK; the rest of the
Definition-of-Done verification is no-op for docs changes.

## 2026-05-17 session master index

For anyone arriving at this commit cold, the 2026-05-17 session shipped
nineteen releases. In chronological order:

1. **v1.8.85** — cross-subsystem hardening pass (eleven fixes, intentional
   AGENTS.md §6 one-time deviation): `verifyNoInternetPermission` merged
   manifest scan + `tools:node="remove"` exemption,
   `data_extraction_rules.xml` for Android 12+, `ZipUtils.unzip`
   pre-canonical entry-name guard + entry-count cap,
   `HardwareKeyboardRuntimeMapper` thread safety + AltGr fix,
   `BitmapFactory` bounded decode in the sticker palette, sticker MIME
   spoof close, addon enumerator APK-size vs bundle-size category-error
   fix, `verify-reproducible-apk.sh` payload-entry-manifest pass criterion,
   CI file-scope `permissions: { contents: read }`,
   `validate-strings-no-translations.yml` `env:`-passing of untrusted PR
   data.
2. **v1.8.86** — `keyVariation` honours `TYPE_NUMBER_VARIATION_PASSWORD`.
3. **v1.8.87** — `FLAG_SECURE` + non-saveable passphrase on
   `DictionaryPassphraseDialog`.
4. **v1.8.88** — recover-not-crash on undecryptable legacy AndroidX
   Security Crypto passphrase state.
5. **v1.8.89** — `ZipUtils.unzip` atomic-abort policy split.
6. **v1.8.90** — SAF lost-grant surface in Settings.
7. **v1.8.91** — Addon spec KDoc mandate of the REGISTER receiver.
8. **v1.8.92** — `KeymanLdmlParser` honours `shift=` over `longPress=`.
9. **v1.8.93** — `release.yml` keystore-decode hygiene + `gh release
   create` env-var hardening.
10. **v1.8.94** — `verify-addon-apk.sh` strict mode + tri-state failure
    reporting.
11. **v1.8.95** — `verifyDataExtractionRules` build gate.
12. **v1.8.96** — `crowdin/github-action@v2` +
    `peter-evans/create-or-update-comment@v4` SHA-pinned.
13. **v1.8.97** — `fastlane/update-readme.sh` Python block substitution
    + `generate-screenshots.sh` absolute-path cleanup.
14. **v1.8.98** — `generate_icon.py` portability.
15. **v1.8.99** — `HardwareKeyboardLayout.equals` fast-path.
16. **v1.8.100** — Sticker palette `LruCache<String, ImageBitmap>` +
    cursor-time enumeration cap.
17. **v1.8.101** — In-keyboard banner for SAF lost-grant.
18. **v1.8.102** — `HardwareKeyEntry.longPressAlternates` parser side.
19. **v1.8.103** — this release: docs catch-up + master index.

Plus docs-only commits:

- `docs(outreach): SwiftKey-refugee discovery drafts for 2026-05-30 window`
- `docs: research run 2026-05-17 — sixth-pass roster closure`
- `docs: research run 2026-05-17 — sixth pass (ROADMAP v5.4)`
- `docs: mark SwiftKey-refugee outreach status — drafts shipped`

Sixth-pass F-roster status: 11/12 closed. **F11 (Roborazzi visual
baselines)** remains open and requires Android SDK + on-device record on
the maintainer build host.

# SwiftFloris Cycle 3 Findings - 2026-06-04

## Scope

Cycle 3 began after `git pull --rebase` reported `master` up to date with the
remote, while the local branch remained five commits ahead of `origin/master`.
The worktree was clean. This pass did not edit feature code; it reconciled local
post-v1.8.225 commits, checked current Android-keyboard competitor releases, and
looked for net-new roadmap work not already covered by RA-4/RA-9, F21/F22, or
the device/maintainer-gated queues.

## Local Evidence

- `git describe --tags --dirty --always` returned `v1.8.223-5-g8142536`, and
  `git tag --points-at HEAD` returned nothing.
- `CHANGELOG.md` has a top `v1.8.225` section, while the newest local commits
  after the docs marker are:
  - `1917583` - n-gram per-locale counters, thread-safety, crypto KDF, trace
    privacy gates.
  - `5df1cfa` - sealed-box shared-secret scrubbing and Arabic combining-mark
    join-context handling.
  - `8142536` - Snygg selector fallback and `contentScale` serialization id.
- `ClipboardHistoryFilter.kt` and `ClipboardPrefs.historySearchEnabled` already
  implement/test the privacy-neutral query contract, but `ClipboardInputLayout`
  currently wires only item-type filters.
- `SealedBoxCryptoTest` covers generated round-trips, wrong-key failure,
  truncation, freshness, and public-key length, but not deterministic vectors or
  envelope schema compatibility.

## External Evidence

- FUTO Keyboard v0.1.29 (published 2026-06-01) introduced FUTO Swipe, cites a
  1M-swipe public QWERTY English dataset, reports top-1/top-4 error framing,
  exposes the accepted word plus three glide alternatives, and adds clipboard
  history search: https://github.com/futo-org/android-keyboard/releases/tag/0.1.29
- AnySoftKeyboard 1.13-r1 continues to emphasize gesture-typing accuracy,
  Android 15 16 KB pages, emoji updates, edge-to-edge, and stability:
  https://github.com/AnySoftKeyboard/AnySoftKeyboard/releases/tag/1.13-r1
- HeliBoard 3.9 remains current in the OpenBoard-derived space and mentions
  incognito-state and backup-restore gesture-data fixes:
  https://github.com/HeliBorg/HeliBoard/releases/tag/v3.9
- FlorisBoard v0.6.0-alpha02 remains the current upstream alpha, with existing
  F22 relevance for number fields, clipboard sensitivity, CLDR/emoji, and
  layout/font scaling:
  https://github.com/florisboard/florisboard/releases/tag/v0.6.0-alpha02
- Libsodium sealed-box documentation confirms the ephemeral-public-key-prefixed
  shape and erasure of the ephemeral secret after encryption:
  https://doc.libsodium.org/public-key_cryptography/sealed_boxes
- RFC 5869 remains the primary HKDF reference and publishes SHA-256 test
  vectors:
  https://datatracker.ietf.org/doc/html/rfc5869

## Roadmap Changes Fed

1. R3-1, P0: reconcile post-v1.8.225 local fixes into a versioned release
   ledger.
2. R3-2, P1: wire existing clipboard-history text search into the in-keyboard
   clipboard palette.
3. R3-3, P1: freeze the sealed-box envelope/KDF contract with deterministic
   vectors before sync transport ships.
4. R3-4, P2: backfill focused tests for the newest local hotfix surfaces.
5. F21 evidence sharpened with the FUTO Swipe v0.1.29 release and top-1/top-4
   benchmark framing.

## Non-Adds

- No new upstream-FlorisBoard row was added; F22 already captures the alpha
  cherry-pick work.
- No new Android 16 KB / edge-to-edge row was added; AnySoftKeyboard and
  existing SwiftFloris release/CI work already cover it.
- No cloud GIF/search or vendor-account feature was added; those remain
  explicit non-goals.

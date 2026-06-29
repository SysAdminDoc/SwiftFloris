# SwiftFloris Roadmap

This file contains only actionable, unblocked work. Completed items are
deleted (they live in git history and the fastlane changelogs). Items
gated on external deliverables or hardware testing live in
[`Roadmap_Blocked.md`](Roadmap_Blocked.md).

---

## Research-Driven Additions

### P3

- [ ] P3 - Add offline sticker-pack import and share-to-sticker creation
  Why: SwiftFloris already supports SAF sticker folders, but users coming from proprietary keyboards expect quick sticker creation and portable local sticker packs without GIF/network dependencies.
  Evidence: HeliBoard issue #2587; `ime/media/sticker/` user-imported folder support; README sticker posture.
  Touches: `ime/media/sticker/`, media settings, SAF import/export helpers, MIME validation, backup/restore inclusion, sticker tests and docs.
  Acceptance: users can share a local image into SwiftFloris as a sticker and import/export a local sticker-pack archive with a small manifest; unsupported/oversized files fail with diagnostics; no network permission or remote catalog is introduced.
  Complexity: M

## Research-Driven Additions (2026-06-29)

### P1

### P3

## Research-Driven Additions

### P1

### P2

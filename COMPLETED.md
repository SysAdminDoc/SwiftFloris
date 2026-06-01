# SwiftFloris Completed Work

This file summarizes shipped state. Release-level detail remains in `CHANGELOG.md`.

## Product Baseline

- Privacy-first Android keyboard forked from FlorisBoard.
- Base app has no `INTERNET` permission, no telemetry, no account requirement, and an Apache-2.0 ceiling.
- Optional networked/native capabilities are designed as signed addon APKs rather than linked into `:app`.
- Current release stream is v1.8.x with consolidated release notes in `CHANGELOG.md`.

## Shipped Feature Areas

- SwiftKey-style migration paths: SwiftKey JSON importer, Gboard XML import, FlorisBoard CSV import, encrypted SwiftFloris dictionary export/import, and migration documentation.
- Autocorrect and prediction: SCOWL dictionary, SymSpell, bigram/trigram scoring, phrase/candidate policies, multilingual ranking, and focused JVM policy coverage.
- Gesture typing: statistical glide classifier, adaptive touch evidence, multilingual dictionaries, and configurable glide trail themes.
- Clipboard: Room-backed history with sensitive gates, media/provider metadata, backup/restore handling, reconciliation, and bounded clone/preview paths.
- Addons: manifest/enumerator contracts, signing-pin trust store, Settings status/rescan/trust controls, dictionary-pack catalog details, and APK asset mounting.
- Voice: FUTO Voice Input handoff plus preview-only local Whisper/Vosk catalog until a recognizer runtime ships.
- Local-only productivity surfaces: calendar quick insert, task quick insert, MCP daemon bridge, Tasker integration, local sticker packs, and hardware-keyboard layout import foundations.
- Quality gates: no-network manifest verification, Roborazzi visual gate, OSV/dependency scanning, reproducible-build tooling, fastlane metadata checks, benchmark baselines, and repo hygiene scripts.

## Documentation Consolidation

- Live open work remains in `TODO.md`.
- Historical strategy remains in `ROADMAP.md`.
- Completed release history remains in `CHANGELOG.md`.
- Root planning summaries are consolidated into `COMPLETED.md` and `RESEARCH_REPORT.md`.
- The 2026-05-25 research plan is archived at `docs/archive/research/RESEARCH_FEATURE_PLAN_2026-05-25.md`.

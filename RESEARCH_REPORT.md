# SwiftFloris Research Report

This report summarizes current research conclusions. The full 2026-05-25 research plan is archived at `docs/archive/research/RESEARCH_FEATURE_PLAN_2026-05-25.md`.

## Product Direction

SwiftFloris is a privacy-first Android IME aimed at SwiftKey-class multilingual typing without vendor cloud dependency. The durable wedge is local capability with audit-friendly boundaries:

- No `INTERNET` permission in `:app`.
- No account requirement.
- No telemetry or ads.
- Main app remains Apache-2.0 compatible.
- GPL/AGPL/LGPL/FUTO Source-First/native runtimes move to isolated optional addons when needed.
- Reproducibility and release evidence matter as much as feature breadth.

## Research-Backed Priorities

- Keep `TODO.md` as the live open-work queue because older planning docs are large and historical.
- Keep the SwiftKey migration story explicit even after the 2026-05-31 cloud-export cutoff; post-cutoff users still need local/on-device recovery paths.
- Expand visual-regression coverage beyond the initial Roborazzi baselines to match the number of shipped themes and keyboard surfaces.
- Continue keyboard-surface polish, settings search/discovery, and privacy/safety/data-integrity workstreams.
- Treat F-Droid/reproducible-build metadata, fastlane changelog discipline, SBOM/provenance, and signed tags as release-readiness work rather than optional polish.
- Keep voice local recognizer runtime, FunctionGemma/MCP addon, and Apache-2.0 glide-model training as out-of-tree or maintainer-decision work.

## Ongoing Risk Areas

- F-Droid package-id collision with upstream FlorisBoard.
- Native voice/glide/model runtimes that could violate the main app's license or no-network posture.
- Translation freshness after large English string drops.
- Settings discoverability as the feature surface grows.
- Device-only visual/performance validation for glide trails, keyboard surfaces, and high-density layouts.

## Archived Evidence

- `docs/archive/research/RESEARCH_FEATURE_PLAN_2026-05-25.md`
- `.ai/research/2026-05-17/`
- `.ai/research/2026-05-25/`

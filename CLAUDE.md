# CLAUDE.md — SwiftFloris (Claude-specific)

This file carries **only** Claude-specific instructions. Everything else
— invariants, hard rules, file routing, rejected features, local
environment notes — lives in [`AGENTS.md`](AGENTS.md). Read both.

---

## Canonical project context

For consolidated project memory, current architecture, known gaps, and
roadmap context, see [`PROJECT_CONTEXT.md`](PROJECT_CONTEXT.md) and
[`AGENTS.md`](AGENTS.md). This file remains the tool-specific
instruction file for Claude (Claude Code CLI, Claude Agent SDK, and
ad-hoc Claude API sessions).

---

## Reading order for a fresh Claude session

1. [`PROJECT_CONTEXT.md`](PROJECT_CONTEXT.md) — one page; fastest read.
2. [`AGENTS.md`](AGENTS.md) — hard rules + file-routing.
3. [`SWIFTKEY_PARITY_ROADMAP_2026-05-17.md`](SWIFTKEY_PARITY_ROADMAP_2026-05-17.md)
   — current sprint plan.
4. [`ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md`](ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md)
   — latest research run's additions / corrections.
5. The most recent few `RELEASE_NOTES_v*.md` files — what shipped recently.
6. [`ROADMAP.md`](ROADMAP.md) only if the prior files leave a question
   unanswered. It's ~340 KB; sample by section heading.

---

## Tools Claude should and should not use here

- **Use** `Glob` for file discovery, `Grep` for content search, `Read`
  with line ranges for large files, `Edit` for in-place changes,
  `Write` for new files, `Bash` (POSIX) and `PowerShell` for the
  Windows-host work.
- **Do not** try `git push` from this VM (returns 403 by design — see
  AGENTS §"Local environment notes"). Commit locally; the user pushes
  from a separate host.
- **Do not** run gradle from this VM unless explicitly asked — JDK +
  Android SDK are not on this VM's path.

---

## Multi-step work conventions

- Use `TodoWrite` for any task with ≥ 3 steps.
- Mark each task `completed` as soon as it's done; don't batch
  completions.
- Use the `Agent` tool with `general-purpose` subagent_type to parallelise
  independent research / verification work. Three parallel agents at a
  time has worked well; more than four overwhelms the context window
  for synthesis. Prefer `run_in_background: true` for research agents
  so the main session can keep working.
- When research artifacts get large, write them directly to disk via
  `Write` rather than collecting them in conversation context.

---

## Memory rules (Claude Code auto-memory)

The maintainer's `~/.claude/projects/.../memory/` already pins:

- `Z:\repos` is the master directory holding all projects on this VM.
- SwiftFloris-specific facts: Android keyboard fork; per-release file
  pattern; build gates.
- `git push` to `SysAdminDoc/SwiftFloris` fails 403 from this VM.

Do not re-save these as memory entries.

---

## Writing style for this repo

Conventions observed across `RELEASE_NOTES_v*.md`, `ROADMAP.md`, and
`SWIFTKEY_PARITY_ROADMAP_2026-05-17.md`:

- **Tight prose.** Sentence-level precision; minimal hand-waving.
- **Specific file references** with line numbers when relevant.
- **Source citations.** Every meaningful external claim has a URL.
  Internal claims point to a file path or commit hash.
- **Status verbs.** `shipped vN.M.O (YYYY-MM-DD)` for done items;
  `partial`, `pending`, `gated on <blocker>`, `verified shipped
  (inherited FlorisBoard upstream)` for in-flight items; the ✅ / ⏳ /
  ⚠️ / ❌ glyphs are used consistently across the roadmap.
- **No filler.** No "I hope this helps" / "Let me know if you have
  questions" boilerplate in committed docs.

When writing code or docs that land in this repo, match this voice.

---

## When the user pastes the autonomous-research prompt

The prompt that produced [`.ai/research/2026-05-17/`](.ai/research/2026-05-17/)
should be re-runnable. On a re-run:

1. Check git log to see what's shipped since the last research run.
2. Check whether the prior date's research-run directory already exists.
   If yes and you're on the same date, **don't overwrite** — produce a
   `SECOND_PASS_FINDINGS.md` in the same directory and update only the
   files where new evidence has surfaced.
3. Always commit locally with a `docs:` prefix; never `git push`.
4. The recommended-not-applied items in
   `ROADMAP_RESEARCH_ADDENDUM_*.md` are the right place to look for
   "did this land yet?" reconciliation.

---

## Slash-command suggestions (Claude Code)

These are useful in this repo:

- `/init` — only if `CLAUDE.md` is missing (this file). It is present;
  don't re-run.
- `/review` — useful before any non-trivial code change touching
  `ime/keyboard/`, `ime/nlp/`, `ime/editor/`, or the build gates.
- `/security-review` — useful before any change to `:app` permissions,
  `AndroidManifest.xml`, signing, addon enrolment, or the SQLCipher /
  Tink crypto path.
- `/ultrareview` — multi-agent cloud review on a branch; the user can
  trigger this themselves before pushing a large change.

Do not invoke `/ultrareview` from inside Claude; it's user-billed.

---

## Editing this file

If the model family changes, capabilities change, or the maintainer
moves to a different Claude Code release line, refresh this file. Keep
the **Canonical project context** section verbatim so other tools can
find their way to `PROJECT_CONTEXT.md` and `AGENTS.md`.

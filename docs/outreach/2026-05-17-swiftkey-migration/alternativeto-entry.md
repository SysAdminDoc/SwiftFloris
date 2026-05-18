# AlternativeTo entry — SwiftFloris

**Target:** <https://alternativeto.net/software/swiftkey/> → "Add an alternative".
The form is at <https://alternativeto.net/about/submit/> and asks for the
fields below in roughly this shape.

---

## Submission fields

**Name:** SwiftFloris

**Tagline (short, < 100 chars):**
Privacy-first Android keyboard. No INTERNET permission, no account, no cloud.
SwiftKey-class typing offline.

**Long description (≤ 1000 chars):**

SwiftFloris is a privacy-first Android keyboard forked from FlorisBoard
and pushed toward SwiftKey-class multilingual typing without the cloud.

It ships under Apache-2.0, holds no `INTERNET` permission (enforced by a
build-time gate against the merged manifest), binds zero accounts, and
sends zero telemetry. Personal vocabulary is stored locally in a
SQLCipher-encrypted dictionary with the wrap key bound to the device's
Android Keystore.

Replaces:

- Multilingual typing with up to four simultaneously-active languages
- Glide typing (production statistical engine; no closed-source blob)
- Voice typing (local Vosk / Whisper, optional FUTO Voice Input handoff)
- Clipboard manager + sticker pack support
- Customizable Snygg themes including AAA-contrast and animated
- SwiftKey personal-dictionary import (read `swiftkey-cloud.json`)
- Encrypted personal-dictionary export / re-import via `.sfexp`

Distributed via GitHub Releases. Recommended installer: Obtainium.

**Categories / tags:** Android, Keyboard, IME, Privacy, FOSS,
Open Source, On-Device AI, Apache 2.0, No Telemetry, No Account.

**License:** Apache-2.0

**Platforms:** Android (8.0+)

**Pricing model:** Free, Open Source

**Official website:** <https://github.com/SysAdminDoc/SwiftFloris>

**Source code:** <https://github.com/SysAdminDoc/SwiftFloris>

**Download / install link:**
[GitHub Releases](https://github.com/SysAdminDoc/SwiftFloris/releases)
(recommended via [Obtainium](https://github.com/ImranR98/Obtainium) for
auto-updates — one-tap link in the README)

**SwiftKey migration page (deep link for the migration window):**
<https://github.com/SysAdminDoc/SwiftFloris/blob/master/docs/MIGRATE_FROM_SWIFTKEY.md>

## "Why is this an alternative?" (the differentiation paragraph)

> SwiftKey users hit the 2026-05-31 Microsoft-account cutoff need an
> exit, and the offered Microsoft-account migration is not an exit —
> it's the opposite. SwiftFloris is built around the inverse contract:
> the keyboard ships without any network permission at all, so the
> data-cloud question never arises. Personal dictionary lives on
> device, encrypted at rest. SwiftKey's data-export window
> (`data.swiftkey.com`) closes 2026-05-31; SwiftFloris's
> migration walk-through documents the JSON-import path before that
> deadline, and a "just retrain" fallback after it.

## Screenshots to upload

From the repo's `metadata/android/en-US/images/phoneScreenshots/`
directory:

1. `1.png` — app icon + Settings landing
2. `2.png` — Settings UI (themed)
3. `3.png` — keyboard layout (themed)
4. `4.png` — clipboard / smartbar
5. `5.png` — emoji palette
6. `6.png` — keyboard with first-letter prediction strip
7. `7.png` — themes / customization

Existing AlternativeTo entries for HeliBoard / FUTO / FlorisBoard use
3-6 screenshots; mirror that count rather than uploading all seven.

## Tags that should NOT be applied

- "F-Droid" — SwiftFloris is not yet on F-Droid (verified-tier
  submission is queued for after F-Droid Basic 2.0 stable).
- "Google Play" — not distributed on Play by design.
- "Cloud sync" / "Sync" — there is no cloud sync.
- "AI Chatbot" / "Generative AI" — the on-device LLM addon (L1.1a) is
  not shipped; the keyboard's existing AI surfaces are facade-only
  until L1 lands.

## After submission

Capture the live URL in the maintainer notes and update
[`PROJECT_CONTEXT.md`](../../../PROJECT_CONTEXT.md) §5 Distribution so
the seventh research pass doesn't re-flag the AlternativeTo gap.

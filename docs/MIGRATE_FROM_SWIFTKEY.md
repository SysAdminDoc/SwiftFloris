# Migrating from Microsoft SwiftKey to SwiftFloris

**TL;DR:** If you can still access [`data.swiftkey.com`](https://data.swiftkey.com)
before Microsoft's **2026-05-31** shutdown, export `swiftkey-cloud.json` and
import it in SwiftFloris via **Settings → Personal dictionary → Import**. If you
miss that window, Microsoft does not provide a usable local export of SwiftKey's
personalized learning data. The honest fallback options are (a) re-train
SwiftFloris's personal dictionary by typing for ~2 weeks on the same device,
(b) restore from a `Microsoft account → OneDrive` backup *if* you previously
enabled SwiftKey Cloud, or (c) on a rooted device only, pull the raw databases
and convert visible entries via the recipe below.

This document exists because v1.7.0's privacy posture (no `INTERNET`
permission, no account requirement, no cloud sync) makes a polished
"connect your Microsoft account" migration physically impossible — and
that's the right tradeoff. The point of SwiftFloris is to *not* connect
to a vendor cloud. If you can't fully migrate, the loss is two weeks of
adaptive retraining, which is small and time-bounded.

## Why this is hard

| Microsoft SwiftKey behavior | Effect on migration |
|---|---|
| Personal learning lives in `/data/data/com.touchtype.swiftkey/databases/` on the device, in encrypted SQLite databases. | Not accessible to other apps without root. |
| The user-facing **Backup & Sync** option uploads the personalization payload to a Microsoft account / OneDrive only. No local-file export exists. | The data is reachable, but only through the Microsoft cloud, which requires keeping a Microsoft account and signing in from a network-connected device. SwiftFloris explicitly does not implement an inbound importer for that pipeline. |
| The encrypted payload format is undocumented and changes with each SwiftKey release. | A user-facing one-click "import SwiftKey" path would require reverse-engineering a moving target Microsoft does not publish a spec for. |
| Microsoft is forcing SwiftKey users onto a Microsoft account by 2026-05-31 [C2]. | The *only* official exit path Microsoft will provide is "stay signed in" — which is not an exit path. |

## The supported paths

### Path 0 — Export `swiftkey-cloud.json` before 2026-05-31

Before the cutoff, use Microsoft's data export endpoint to download
`swiftkey-cloud.json`, then choose it from SwiftFloris **Settings → Personal
dictionary → Import**. SwiftFloris parses the tolerant SwiftKey JSON shapes,
shows the import summary, and lets you undo newly inserted rows.

After your words are in SwiftFloris, **Settings → Personal dictionary → Export
encrypted** creates a passphrase-protected `.sfexp` file you can move with
Syncthing, USB, or another user-chosen channel. Importing the same file on
another SwiftFloris device asks for the passphrase and keeps the same
summary/rollback flow.

### Path A — Just retrain SwiftFloris (recommended for almost everyone)

This is what most users should do. It takes one to two weeks of normal
typing and the result is a personal dictionary built on the same words
and phrases you actually use *today*, not whatever SwiftKey was learning
from you two years ago.

What SwiftFloris will pick up automatically as you type:

- **Personal words.** Every word you type that's not in the base
  117 k-entry English dictionary, you've typed more than the
  `learnWord` threshold times. Stored locally in
  `DictionaryManager.florisUserDictionary` (SQLCipher-encrypted as of
  v1.7.4, N7.4). This is the largest chunk of SwiftKey's "what's
  personal about you."
- **Bigrams + trigrams** for next-word prediction (N12.2 + N12.5):
  every pair / triple of consecutive words you commit gets weighted, so
  after a few days the suggestion strip surfaces *your* common
  continuations.
- **Adaptive touch model** (N12.1): per-key offset learning from your
  thumbs. Reaches steady state in ~500 keystrokes per key, which on a
  normal day is well under one hour of typing.
- **Personal autocorrect shortcuts.** Anything you set up in Settings →
  Typing → Personal dictionary (N5.4) — that's the manual SwiftKey
  "Shortcut" feature.

### Path B — Restore from your Microsoft account, then immediately leave

If you previously enabled SwiftKey Backup & Sync (account-bound), you
can re-install Microsoft SwiftKey, sign in to the Microsoft account, let
SwiftKey re-download its personalization payload, type for a few hours
on SwiftKey while reading important phrases / contacts so they get
re-promoted into its visible word list, then export the visible word
list with a screenshot or screen-recording trick. SwiftFloris does *not*
support automated import of this.

This is unsatisfying. It is also the only thing Microsoft makes
possible without root. If your personal dictionary represents serious
ongoing value (years of professional shorthand, irregular contact names,
in-jokes), this is the path that recovers most of it. Note: doing this
requires re-accepting Microsoft's account mandate, which is exactly the
thing many SwiftFloris users came here to escape.

### Path C — Root-only: pull SwiftKey's databases

On a rooted device:

```
adb shell su -c 'tar -czf /sdcard/swiftkey-backup.tar.gz -C /data/data/com.touchtype.swiftkey databases/'
adb pull /sdcard/swiftkey-backup.tar.gz
```

Then on a desktop:

```
tar xzf swiftkey-backup.tar.gz
sqlite3 databases/dynamic.lm '.dump'   # or whatever the current SwiftKey filename is
```

The schema changes across SwiftKey releases. As of SwiftKey 9.10.x the
personalized learning model is in a database called `dynamic.lm` (a custom
binary format, not raw SQLite), and the visible-words / shortcuts surface
in a much smaller SQLite database. Word lists are extractable; the
neural personalization weights are not.

Once you have a list of (word, frequency) pairs in plain text, convert
to a CSV in the same shape as SwiftFloris's personal-dictionary import
format:

```csv
word,frequency,shortcut,locale
omw,128,,en
brb,128,,en
gracias,200,,es
... etc.
```

Settings → Typing → Personal dictionary → **Import CSV** consumes that.

If you do not have root, do not try to mount or copy the
`/data/data/com.touchtype.swiftkey/` tree some other way. Microsoft also
ships a side-loaded "secure storage" pass on Android 12+ that breaks
non-root extraction; you will get an unreadable file. Save yourself the
hour.

## What SwiftFloris will never offer

- A bundled SwiftKey-cloud OAuth flow. Violates §1 (no `INTERNET`,
  zero account requirement). Even an "optional" toggle is rejected
  because the existence of the toggle creates a credibility problem the
  rest of the privacy posture has to defend against.
- A binary SwiftKey-database parser shipped inside the app. Same reason:
  reverse-engineering a closed format inside a privacy keyboard is a
  supply-chain surface we have no need to take on, and the format
  shifts every Microsoft release anyway.
- A "we'll talk to a small server we run for you" migration helper. No
  server, ever, for any reason that touches user input.

## What SwiftFloris *does* offer for migration

| Source | Status | Tracking |
|---|---|---|
| Microsoft SwiftKey | `swiftkey-cloud.json` import before the 2026-05-31 endpoint shutdown; retrain/root fallback after cutoff. | v1.8.46 parser + v1.8.53 Settings import wiring. |
| SwiftFloris device-to-device | Passphrase-encrypted `.sfexp` export/import for the personal dictionary. | v1.8.54 codec + v1.8.65 Settings wiring. |
| Google Gboard | XML / CSV import from `PersonalDictionary.zip` export (Google Takeout). | v1.8.x importer path. |
| HeliBoard / FlorisBoard upstream | Backup file (`.flbackup` zip archive) imports supported personal-dictionary payloads through the modular importer. | v1.8.x importer path. |
| Hardware-keyboard layouts (Windows `.klc` / macOS `.keylayout`) | Build-time conversion via KLFC. | Next-6.4 — pending. |

So if you are coming from Gboard, HeliBoard, or FlorisBoard upstream you
get a real migration. If you are coming from SwiftKey, retrain. It is
faster than you think.

## Why we're not apologizing for this

Microsoft built a closed ecosystem around SwiftKey and gated the exit
behind an account requirement. SwiftFloris does not have an account, does
not phone home, and cannot be shut down by a vendor policy change. The
two-week retraining cost is the price of buying back that property, paid
once.

## References

- [Microsoft SwiftKey Backup & Sync support article](https://support.microsoft.com/en-us/topic/how-to-use-backup-sync-in-microsoft-swiftkey-keyboard-3604cb8a-47e9-4045-82de-fd301904e59a)
- [Microsoft SwiftKey requires an MS account by 2026-05-31 — Windows Central](https://www.windowscentral.com/software-apps/swiftkey-will-soon-require-a-microsoft-account-data-to-be-moved-to-onedrive)
- [Microsoft SwiftKey Copilot removal — Support FAQ](https://support.microsoft.com/en-us/topic/faqs-for-copilot-changes-in-swiftkey-c02289e6-c5b3-401c-af8d-f6c88409a2d2)
- SwiftFloris `docs/THREAT_MODEL.md` (privacy posture rationale)
- ROADMAP §1 — load-bearing philosophy that explains why this is the
  shape of the path, not a temporary gap.

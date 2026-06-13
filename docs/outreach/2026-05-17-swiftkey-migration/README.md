# SwiftKey-migration outreach drafts — 2026-05-17

**Purpose:** unblock the Tier-1 / urgency-5 "SwiftKey-refugee discovery
gap" commitment from
[`docs/archive/ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md` §0.b.2](../../../docs/archive/ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md)
and
[`ROADMAP.md` v5.4 §0.4.3](../../../ROADMAP.md) /
v5.5 §0.5.2. The sixth research pass surfaced that AlternativeTo, BGR,
and Android Authority round-ups all name HeliBoard / FUTO / FlorisBoard /
AnySoftKeyboard as the SwiftKey-migration escape route and **SwiftFloris
is not yet on any of them**. The 2026-05-31 SwiftKey account-deletion
cutoff is 14 days away.

This directory holds four drafts the maintainer can review and post
manually from their own accounts. Each is standalone and copy-pasteable.
Drafts only — the maintainer is the one who owns the SwiftFloris
persona / accounts / posting cadence.

## Drafts

| File | Target surface | When to post |
|---|---|---|
| [`alternativeto-entry.md`](alternativeto-entry.md) | AlternativeTo's [SwiftKey alternatives page](https://alternativeto.net/software/swiftkey/) — "Add an alternative" form | ASAP; submission has an approval lag |
| [`bgr-comment.md`](bgr-comment.md) | Comment on [BGR's "Android keyboards to replace Google Gboard and SwiftKey"](https://www.bgr.com/2003971/android-keyboards-replace-google-gboard-swiftkey-heliboard/) | After AlternativeTo submission is approved, so the comment can link to the live AlternativeTo page |
| [`android-authority-comment.md`](android-authority-comment.md) | Comment on [Android Authority's "HeliBoard: A Gboard alternative"](https://www.androidauthority.com/heliboard-gboard-alternative-3505462/) | Same window as the BGR comment |
| [`r-swiftkey-post.md`](r-swiftkey-post.md) | New post on [r/Swiftkey](https://www.reddit.com/r/Swiftkey/) | 2026-05-28 to 2026-05-30 — peak refugee-traffic window |

## What's NOT in this directory

- **An r/HeliBoard / r/FlorisBoard cross-post.** Those communities already
  know about FOSS keyboards; posting "yet another fork" into them is
  noise. SwiftKey refugees are the right audience.
- **An r/Android post.** Subreddit rules on self-promotion are strict
  and unevenly enforced; the r/Swiftkey post is the safe channel.
- **A Mastodon / Bluesky thread.** Add separately if the maintainer
  already has a SwiftFloris presence on those platforms; the drafts
  here are written for the four cited surfaces only.
- **The maintainer's actual posting credentials or any automated
  posting.** Drafts only.

## Style notes (so the maintainer doesn't have to fight the AI voice)

The drafts deliberately avoid:

- **Marketing superlatives** ("revolutionary", "next-generation",
  "game-changer"). The repo's own writing voice is tight and specific;
  the drafts match that.
- **Unverified feature claims.** Every claim maps to a documented
  release-notes file or roadmap section. Counts (1000+ tests,
  117 k base dictionary, 7-language priors) come straight from
  `README.md`.
- **Comparisons that say "X is bad".** Reddit / BGR comment moderators
  routinely remove negative-comparison posts. The drafts position
  SwiftFloris on its specific load-bearing invariants
  (no `INTERNET` permission, Apache-2.0 only, no account requirement)
  and let the reader draw the conclusion.
- **F-Droid claims.** SwiftFloris is not yet on F-Droid (verified-tier
  submission is the v1.8.85 §0.4.3 Tier-1 follow-up); GitHub Releases
  + Obtainium is the documented distribution path.

## After-posting maintenance

Once posted, add the live URLs to the Distribution section of
[`README.md`](../../../README.md) so future research runs don't re-flag
the discovery gap. The seventh
research pass should re-check the four surfaces for SwiftFloris's
presence as part of its source-register update.

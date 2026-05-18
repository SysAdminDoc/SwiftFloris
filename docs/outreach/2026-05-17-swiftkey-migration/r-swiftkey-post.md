# r/Swiftkey post — pre-cutoff migration option

**Target:** <https://www.reddit.com/r/Swiftkey/submit>

Sub rules (per the sidebar as of 2026-05-17): on-topic content; flair
posts; no excessive self-promotion. A single post that frames itself as
"here's an option I've been working on for the cutoff" reads as helpful
to the community in pain right now and not as a campaign.

**Recommended posting window:** 2026-05-28 to 2026-05-30 (peak
refugee-traffic days). Avoid the cutoff day itself — the subreddit will
be flooded with "data gone" complaints and the post will get buried.

## Recommended flair

`Tip` (if the sub uses that flair) or `Help` (most r/Swiftkey
keyboard-migration posts go under `Help`).

## Title

**Pre-cutoff migration option: a FlorisBoard fork that imports your `data.swiftkey.com` export directly**

## Body (copy-paste)

> 14 days to the SwiftKey account-deletion cutoff. If you're still
> deciding where to land and you don't want another vendor-account
> keyboard, one option worth knowing about before 2026-05-31:
>
> **SwiftFloris** is a FlorisBoard fork I've been working on with the
> explicit constraint of no `INTERNET` permission, no vendor account,
> no cloud sync. It's not yet on F-Droid (verified-tier submission is
> queued), so distribution is GitHub Releases / Obtainium:
>
> Repo: <https://github.com/SysAdminDoc/SwiftFloris>
> Migration walk-through:
> <https://github.com/SysAdminDoc/SwiftFloris/blob/master/docs/MIGRATE_FROM_SWIFTKEY.md>
>
> The migration walk-through is the load-bearing piece. Short version:
>
> 1. Before 2026-05-31, log in to <https://data.swiftkey.com> and
>    download `swiftkey-cloud.json`.
> 2. Install SwiftFloris via the Obtainium one-tap link in the README
>    (or sideload the APK from Releases).
> 3. Settings → Personal dictionary → Import → pick your
>    `swiftkey-cloud.json`. SwiftFloris parses the SwiftKey JSON
>    shape directly. There's a summary screen and a rollback if it
>    grabs more than you wanted.
>
> If you miss the cutoff: the official Microsoft path is "stay signed
> in to a Microsoft account, your data lives in OneDrive." If that's
> not acceptable to you, the next-best option is a two-week retrain
> on whichever offline keyboard you pick — your typing patterns from
> two years ago aren't actually what you type today, so it's less
> painful than it sounds.
>
> Not pitching this as the only option — HeliBoard, FUTO Keyboard,
> AnySoftKeyboard, and stock FlorisBoard are all legitimate exits if
> their tradeoffs fit you better. Posting this specifically because
> the `swiftkey-cloud.json` direct-import path isn't documented on
> the other forks, and 14 days isn't a lot of time.
>
> Happy to answer questions about the migration path or the project's
> "no cloud" gates (e.g. the build-time check that fails if any
> manifest declares `INTERNET`, the SQLCipher dictionary at rest,
> etc.) in replies.
>
> Edit: not affiliated with FlorisBoard upstream — SwiftFloris is a
> separate fork. Source code is under Apache 2.0.

## Notes for the maintainer

- ~370 words; r/Swiftkey accepts long-form posts. The "Happy to answer
  questions" close is doing work — it commits you to staying engaged
  in the thread for at least a day, which the sub culturally expects.
- Don't pre-emptively cross-post to r/Android or r/AndroidQuestions.
  Both subs have stricter self-promotion rules and a cross-post that
  reads as "I made this" will get removed where the r/Swiftkey post
  will stand.
- If asked about Google Play distribution, the honest answer is "not
  by design — Play forces target-SDK churn and Integrity-API tradeoffs
  that conflict with the no-telemetry posture." Don't be defensive
  about it.
- If a moderator asks for `OP is the developer` disclosure, add the
  flair / disclaimer they request — Reddit's self-promotion rules vary
  per moderator.
- The post does NOT include the AlternativeTo link because that page
  doesn't exist yet (submission pending). Once the AlternativeTo entry
  is approved, you can comment-edit the post to add it as a fifth
  link.

## When NOT to post this

- If Microsoft announces an extension to the 2026-05-31 cutoff
  between now and posting day, retitle: "Pre-extension-cutoff
  migration option…" and re-time accordingly. The current text
  assumes the cutoff is firm (which sixth-pass external research
  confirmed it is, as of 2026-05-17).
- If F-Droid Basic 2.0 stable ships before the posting window,
  amend the parenthetical to "available on F-Droid as of {date}" —
  that's a stronger distribution signal than "GitHub Releases /
  Obtainium" on this audience.

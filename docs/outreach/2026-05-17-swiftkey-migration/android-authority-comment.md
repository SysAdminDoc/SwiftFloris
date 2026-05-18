# Android Authority comment — "HeliBoard: A Gboard alternative"

**Target:** <https://www.androidauthority.com/heliboard-gboard-alternative-3505462/>

Android Authority uses Disqus. Comments stay live but get heavily
moderated when they read like ad copy. Goal: position SwiftFloris as a
genuinely different point on the privacy-keyboard tradeoff curve —
specifically the "no closed-source glide blob" line that HeliBoard is
honest about needing.

## Draft (copy-paste)

> If the HeliBoard glide story is the wedge for you, you might also
> look at **SwiftFloris** —
> <https://github.com/SysAdminDoc/SwiftFloris>. It's a FlorisBoard fork
> that ships a statistical glide engine in-tree under Apache 2.0
> instead of asking the user to sideload Google's closed
> `libjni_latinimegoogle.so`. The tradeoff is the engine isn't quite
> as accurate as Google's on aggressive flick gestures, but it doesn't
> need a binary blob and you can read the code.
>
> Same no-`INTERNET`-permission contract as HeliBoard (pinned by a
> Gradle build gate, including the merged manifest so a library AAR
> can't re-add the permission silently), but the project is also
> pursuing F-Droid's reproducible-verified-builds tier — F-Droid Basic
> 2.0-alpha9 just shipped the per-app verification badge surface, so
> getting on it is realistic this cycle.
>
> For SwiftKey refugees specifically, there's a direct
> `swiftkey-cloud.json` importer if you can export from
> `data.swiftkey.com` before 2026-05-31; the walk-through is at
> <https://github.com/SysAdminDoc/SwiftFloris/blob/master/docs/MIGRATE_FROM_SWIFTKEY.md>.

## Notes for the maintainer

- ~165 words; safe under Disqus collapse thresholds.
- Opens with a frame the article itself sets up (HeliBoard's glide
  blob), so the comment doesn't read as off-topic boosterism.
- Tone is "and here's another option with this specific tradeoff",
  not "this is better than HeliBoard". Don't change that.
- If asked "isn't statistical glide just worse than the Google blob?"
  — the honest answer is "yes, on some inputs, especially aggressive
  flicks; we accepted that tradeoff to keep the engine open and
  auditable." Don't oversell.

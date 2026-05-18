# BGR comment — "Android keyboards to replace Google Gboard and SwiftKey"

**Target:** <https://www.bgr.com/2003971/android-keyboards-replace-google-gboard-swiftkey-heliboard/>

BGR comments accept short markdown / plaintext. Goal: surface SwiftFloris
as a fifth option alongside the four BGR already names, without writing
ad copy or sniping at the article's existing picks.

## Draft (copy-paste)

> Worth adding to this list: **SwiftFloris** —
> <https://github.com/SysAdminDoc/SwiftFloris>. It's a FlorisBoard fork
> pushed toward SwiftKey-class typing under the same offline-only
> contract HeliBoard targets, but with a couple of guarantees pinned
> in code:
>
> - The Gradle build fails if any merged manifest declares
>   `INTERNET` / `ACCESS_NETWORK_STATE` (verifyNoInternetPermission is
>   a preBuild dep on every variant, including the post-merge manifest
>   so a library AAR can't sneak the permission back in).
> - Personal dictionary lives in a SQLCipher database with the wrap
>   key bound to Android Keystore; the Android-12+ data-extraction
>   rules explicitly exclude both the DB and the wrap key from cloud
>   backup and D2D transfer.
> - Apache 2.0 only — GPL-licensed glide engines etc. can't link into
>   the main APK, so the in-tree statistical glide engine is the
>   default rather than a closed-source SwiftKey-from-AOSP blob.
> - Direct SwiftKey-data importer: read the `swiftkey-cloud.json` you
>   pull from `data.swiftkey.com` before 2026-05-31 — Settings →
>   Personal dictionary → Import handles the JSON shape.
>   Walk-through:
>   <https://github.com/SysAdminDoc/SwiftFloris/blob/master/docs/MIGRATE_FROM_SWIFTKEY.md>.
>
> Distribution is GitHub Releases + Obtainium (one-tap link in the
> README) rather than Play, by design.

## Notes for the maintainer

- BGR's comment system is Disqus-style; long comments stay readable
  but get aggressively collapsed past ~250 words. The draft above is
  ~180 words.
- Don't lead with "you missed X" — moderators dislike posts that
  reframe the article. The draft opens neutral.
- If BGR pushes back on the link count, drop the
  `MIGRATE_FROM_SWIFTKEY.md` link first (the GitHub link is the
  load-bearing one).
- If the article gets a "Why isn't FlorisBoard upstream on this
  list?" follow-up, do not engage — let the comment stand on its own.

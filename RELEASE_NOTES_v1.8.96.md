# Release v1.8.96 — SHA-pin third-party action floating tags

Date: 2026-05-17

Follow-up F9 + F10 from the [v1.8.85 audit roster](RELEASE_NOTES_v1.8.85.md#follow-up-work-next-per-feature-releases).

## What changed

Two CI workflows referenced third-party actions by floating major-version
tag rather than by SHA. A floating tag means an attacker who compromises
the action's repository can re-point the tag at a malicious commit and
every workflow consuming the floating tag picks up the malicious code at
its next run. Each of these workflows passes either the workflow's
`GITHUB_TOKEN` or a real third-party credential into the action, so the
blast radius matters.

- [`.github/workflows/crowdin-upload.yml`](.github/workflows/crowdin-upload.yml)
  — `crowdin/github-action@v2` pinned to
  `8868a33591d21088edfc398968173a3b98d51706`. This action receives the
  Crowdin personal token (`FSEC_CROWDIN_PERSONAL_TOKEN`) plus the
  workflow's read-only `GITHUB_TOKEN`.
- [`.github/workflows/validate-strings-no-translations.yml`](.github/workflows/validate-strings-no-translations.yml)
  — `peter-evans/create-or-update-comment@v4` pinned to
  `71345be0265236311c031f5c7866368bd1eff043`. This action runs on
  `pull_request_target` (base-repo context) with `pull-requests: write`,
  so a malicious v4 retag could exfiltrate the workflow's token.

Both SHAs were verified at edit time against the GitHub API:
`GET /repos/<owner>/<repo>/git/refs/tags/<tag>` returned
`object.type = commit` and `object.sha` matching the values above.

Each pin carries an inline comment explaining the SHA's provenance and
the re-pin procedure, so a future maintainer bumping the action knows
to re-run the API call rather than reintroduce a floating tag.

## What this release does NOT change

The first-party GitHub actions (`actions/checkout`, `actions/setup-java`,
`actions/upload-artifact`, `gradle/actions/*`, `lukka/get-cmake@v4.0.2`)
are still on floating tags. Those are arguably acceptable because:

- `actions/*` are maintained by GitHub itself.
- `gradle/actions/*` are maintained by Gradle Inc.
- `lukka/get-cmake@v4.0.2` is pinned to a patch version (not a moving
  major).

Sweeping every floating tag is a larger consistency exercise tracked
separately; this release closes only the two third-party / write-token
exposures the v1.8.85 audit flagged.

## Files touched

- `.github/workflows/crowdin-upload.yml`
- `.github/workflows/validate-strings-no-translations.yml`
- `gradle.properties` — versionCode 1896 / versionName 1.8.96

## Verification

No `:app` source / lint / test impact — workflow-only.

The next push to `main` triggering `validate-strings-no-translations.yml`
or `crowdin-upload.yml` exercises the pinned SHA; the workflow run logs
will show the action resolving to a commit hash rather than a tag name.

If the maintainer wants to re-verify the SHA values:

```bash
gh api repos/crowdin/github-action/git/refs/tags/v2 --jq '.object'
gh api repos/peter-evans/create-or-update-comment/git/refs/tags/v4 --jq '.object'
```

Both should return `{ "sha": "<the pinned value>", "type": "commit", … }`.

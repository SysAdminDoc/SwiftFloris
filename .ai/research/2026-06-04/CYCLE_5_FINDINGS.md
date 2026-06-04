# SwiftFloris Cycle 5 Findings - 2026-06-04

## Scope

Cycle 5 ran from a clean detached worktree because the primary worktree had
separate implementation edits in progress. The detached worktree was based on
`origin/master` at `8cbd0d4` (`v1.8.226`), and `git pull --rebase origin master`
reported it was already up to date. This pass did not edit feature code, tests,
build files, or assets.

## Anti-Duplication Check

- R3-1 is closed in v1.8.226 and was not re-added.
- R4-1 through R4-4 remain the current Cycle 4 rows and were not duplicated.
- The addon signing-history / multi-signer pin bypass is already fixed in
  `SigningFingerprint.readSignatures` and recorded in `docs/AUDIT_2026-06-02.md`.
- Tasker intent extras now have per-action validation and size bounds in
  `TaskerIntentContract.validate`, so the older broad "extras bounds" audit row
  was not re-added.

## Local Evidence

- `AddonContract.kt:108-110` says addons must be co-signed by the same key as
  the IME or explicitly trusted in Settings.
- `AndroidManifest.xml:15-19` describes the same co-signed-or-user-whitelisted
  addon enrollment contract for `REGISTER_ADDON`.
- `AddonEnumerator.kt:118-183` accepts any installed package with addon metadata,
  no banned network permissions, descriptor/version/license metadata, and a
  readable signing fingerprint.
- `AddonRegistry.kt:55-60` pins the first-seen signing certificate and accepts
  the manifest whenever no existing pin exists for that package.
- `AddonRegistryTest.kt:48-72` and `AddonRegistryStartupTest.kt:46-56` currently
  assert first-seen auto-enrollment from an empty pin set.
- `AddonsSettingsScreen.kt:104-145` exposes rescan/reset flows, while
  `AddonsSettingsScreen.kt:252-292` can trust a changed certificate only after
  an existing pin has rejected it. There is no pending first-run trust state.
- `docs/AUDIT_2026-05-28.md:84-86` records the same auto-pin vs documented
  trust-contract mismatch.

## External Evidence

- Android custom permission docs define `signature` protection as granted only
  when the requesting app is signed with the same certificate as the declaring
  app: https://developer.android.com/guide/topics/manifest/permission-element
- Android package visibility docs say Android 11+ filters installed-package
  visibility by default, and `<queries>` can selectively expand visibility:
  https://developer.android.com/training/package-visibility
- Android `<queries>` docs say an app can list intent-filter signatures to
  discover packages matching those intent filters:
  https://developer.android.com/training/package-visibility/declaring
- Android `SigningInfo` docs confirm current signer, signing-history, and
  multi-signer distinctions; this supports the non-add decision that the
  signing-history bypass is already separately handled:
  https://developer.android.com/reference/android/content/pm/SigningInfo

## Roadmap Changes Fed

1. R5-1, P1: require explicit first-run trust for non-co-signed addon APK
   enrollment.

## Non-Adds

- No signing-history row was added because `SigningFingerprint.readSignatures`
  already refuses multi-signer packages and uses current signers instead of
  `signingCertificateHistory.first()`.
- No Tasker extras-bounds row was added because `TaskerIntentContract.validate`
  already rejects unknown actions, malformed extras, oversize inserted text, and
  invalid layout/mode values.
- No release-ledger row was added because v1.8.226 closes R3-1.
- No source-code fix was attempted in this cycle; all changes are roadmap and
  research documentation only.

# Cycle 20 Findings - 2026-06-06

## Cycle

`post-retirement-migration-doc-drift-2026-06-06`

## Scope

Rechecked post-retirement SwiftKey migration copy after the 2026-05-31 account
retirement date had passed. This pass looked for source-of-truth drift between
the README, the dedicated migration guide, shipped hardware-keyboard import
notes, and Microsoft's current SwiftKey account support page.

## Files and sources reviewed

- `README.md`
- `docs/MIGRATE_FROM_SWIFTKEY.md`
- `CHANGELOG.md`
- Microsoft SwiftKey account support:
  https://support.microsoft.com/en-us/topic/account-a3c38581-903f-4d22-a388-cc13c7debf0e

## Findings

- `README.md:11-14` is already post-cutoff: it says Microsoft retired
  standalone SwiftKey accounts and shut down the `data.swiftkey.com` export
  endpoint on 2026-05-31.
- `docs/MIGRATE_FROM_SWIFTKEY.md:4-5` and
  `docs/MIGRATE_FROM_SWIFTKEY.md:31-36` still foreground pre-cutoff export
  instructions. That is now historical advice and should not be the first path
  a user sees on 2026-06-06.
- `docs/MIGRATE_FROM_SWIFTKEY.md:146-150` still describes SwiftKey JSON import
  in pre-cutoff wording and says Windows `.klc` / macOS `.keylayout`
  hardware-keyboard layouts are "Next-6.4 - pending".
- `README.md:54` and `README.md:379-380` record Settings-based Keyman `.kmp`,
  Windows KLC, and macOS hardware-keyboard import/runtime support as shipped
  through v1.8.75 and v1.8.76.
- Microsoft's support page now frames SwiftKey Accounts as retired on 31 May
  2026 and points users toward Microsoft-account backup/sync. SwiftFloris should
  continue documenting that as an external vendor path it does not automate.

## Roadmap effect

Added R20-1 to `ROADMAP.md`: refresh migration guide and import docs for
post-retirement reality. This complements R18-2's in-app migration assistant
instead of duplicating it.

## Acceptance shape

- The migration guide opens with what users can still do today.
- Already-saved `swiftkey-cloud.json` files remain documented as importable.
- Current supported local import sources are listed before historical
  pre-cutoff advice.
- Hardware-keyboard import status matches shipped v1.8.75/v1.8.76 behavior.
- Microsoft-account/OneDrive recovery is clearly external and not automated by
  SwiftFloris.

## Duplicate avoidance

- Did not duplicate R18-2, which targets an in-app local-only migration recovery
  assistant.
- Did not propose cloud import, Microsoft OAuth, or network flows.
- Did not edit migration docs in this cycle; only added implementation-ready
  roadmap/source notes.


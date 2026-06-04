# SwiftFloris Accessibility Policy

This document is the SwiftFloris accessibility (a11y) conventions doc for both the IME surface and the Settings
app. It is intentionally short — the point is to document the load-bearing patterns once so future contributors
can apply them without re-deriving the policy from upstream Android changelogs.

## Android 16 (API 36) pane-aware migration (ROADMAP matrix #11)

Android 16 deprecates disruptive announcement events (`AccessibilityEvent.TYPE_ANNOUNCEMENT`,
`View.announceForAccessibility(...)`). The replacement is a pane-aware contract:

- Every distinct screen / pane sets a stable `paneTitle` via Compose `Modifier.semantics { paneTitle = "..." }`.
  TalkBack reads the new pane title on navigation, replacing the announcement event with something users can
  actually interrogate via swipe.
- Surfaces that change without user navigation (a smartbar candidate row, a status banner, a Snackbar) use
  `Modifier.semantics { liveRegion = LiveRegionMode.Polite }` instead. Polite live regions defer to whatever
  TalkBack is currently reading and merge updates if many fire close together — exactly the right shape for
  IME-side text suggestions where the user is typing and we don't want to interrupt them.

SwiftFloris currently does **not** call `announceForAccessibility(...)` or post `TYPE_ANNOUNCEMENT` events
anywhere in production code, so the migration is forward-only: every Compose screen that ships should carry a
`paneTitle`, and every live-status surface that lands should opt into `liveRegion = Polite` (or `Assertive` when
the message is genuinely critical, e.g. a "model failed to load" error in Settings → Voice).

The base IME `FlorisScreen` Scaffold already wires `paneTitle = title` from the screen's title state, so every
Settings screen inherits the pane-title contract for free. New full-screen surfaces should build on top of
`FlorisScreen` rather than constructing their own Scaffold.

## Android 16 edge-to-edge (ROADMAP matrix #12)

`targetSdk 36` makes edge-to-edge the default for the Settings activity. `FlorisAppActivity.onCreate` opts in
explicitly via the modern `androidx.activity.enableEdgeToEdge()` API rather than the older
`WindowCompat.setDecorFitsSystemWindows(window, false)` call — `enableEdgeToEdge()` carries the same behavior
across pre-API-36 builds and lets the call-site name the intended `SystemBarStyle` deliberately.

The inset-aware Scaffold path inside `FlorisScreen` already consumes the `innerPadding` provided by Material3's
Scaffold, so every Settings screen built on top of `FlorisScreen` handles status / navigation / IME insets
without per-screen edits. The `previewKeyboardField` floating-IME preview path inside Settings → Theme is the
one surface that needs explicit attention because it overlays the bottom edge; that is handled by the existing
`PreviewKeyboardField` composable's own inset math.

Screenshot baselines (matrix #6) should cover at least one settings screen in both portrait and landscape to
pin the edge-to-edge rendering — that test lands when the Roborazzi baseline capture lands.

## Glide trail themes and photosensitivity

v1.8.172 added seven selectable glide-trail themes
(`GlideTrailTheme.{ACCENT, RAINBOW, FIRE, ICE, AURORA, GALAXY, NEON}`,
defined in
[`app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/gestures/GlideTrailTheme.kt`](../app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/gestures/GlideTrailTheme.kt)).
Five of them (`RAINBOW`, `FIRE`, `AURORA`, `GALAXY`, `NEON`) are time-driven —
their per-segment colour is computed against the current `timeMillis`, so the
trail animates as the finger draws it.

Approximate animation rates by theme (rad/ms or hue°/ms):

| Theme | Time-driven effect | Rate |
|---|---|---|
| ACCENT | none (constant Snygg primary) | 0 |
| ICE | none (static gradient by position) | 0 |
| RAINBOW | 360° hue sweep over time | 0.2 hue°/ms (~3 Hz at typical gesture lengths) |
| AURORA | 180° hue cycle | 0.06 hue°/ms (slow shimmer) |
| FIRE | per-segment sin shimmer at the hot tail | 0.003 rad/ms (~0.5 Hz visible flicker) |
| GALAXY | sub-hue shift on the mid segments | 0.0004 rad/ms (sub-Hz, slow drift) |
| NEON | sinusoidal brightness modulation across the whole trail | 0.015 rad/ms (~2.4 Hz pulse) |

The `RAINBOW`, `NEON`, and `FIRE` themes can rise above the seizure-trigger
threshold for users with photosensitive epilepsy (the WCAG 2.3 / 2.3.2 floor
sits at three flashes per second of red-saturated content over more than 25% of
the central visual field). The trail itself is a thin stroke at most ~110% of a
key radius, well below 25% of the visual field, and the brightness is bounded
by the active Snygg accent — but the disclosure is worth making explicit
because the maintainer cannot audit every user's hardware and ambient
conditions.

**Reduced-motion guarantee.** The whole trail rendering path is gated off at
[`TextKeyboardLayout.kt:177-178`](../app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/keyboard/TextKeyboardLayout.kt#L177)
when `Settings.Global.ANIMATOR_DURATION_SCALE == 0f` (Developer Options →
"Animator duration scale" = Animations off):
`val glideShowTrail = glideShowTrailPref && !reducedMotion`. With reduced
motion on, the trail does not draw at all — animated or otherwise — regardless
of which theme is selected. So a user with photosensitivity concerns has a
single system-level switch that fully removes the trail surface; they do not
need to navigate into Settings → Gestures to pick a less-animated theme.

The Snygg engine respects `ANIMATOR_DURATION_SCALE` per-element as well, so
the `KeyPopupElement` accent ring (v1.7.9 N3.4) and the smartbar transitions
already collapse to static frames when reduced motion is on. The glide trail
inherits the same gate.

**Recommended user guidance for photosensitive users:** turn on
Developer Options → "Animator duration scale" = Animations off (or set
the per-app accessibility motion preference where available on Android 14+).
SwiftFloris's trail will not render at all in that state.

**Recommended Settings affordance follow-up.** Settings → Gestures →
"Trail theme" picker could grow a small "ⓘ" tooltip beside `RAINBOW`,
`AURORA`, `NEON`, `FIRE`, and `GALAXY` noting the time-based animation and
the existing reduced-motion gate. Tracked as a future Workstream 10 polish
slice; the disclosure copy here is the canonical reference until it lands.

## Other a11y contracts SwiftFloris pins

- **48 dp touch targets (WCAG 2.5.5).** The IME enforces a minimum 48 dp touch-target size via the existing
  `TouchTargetWcagTest` regression test (`app/src/test/kotlin/.../TouchTargetWcagTest.kt`). Any keyboard layout
  change that would shrink a key below 48 dp must come with a matching test update + visible UI affordance.
- **Per-key TalkBack labels.** Every `KeyData` rendered into the layout grid carries a `contentDescription`
  derived from its display label, with fallbacks for symbol keys. Keys without a sensible display label (e.g.
  modifier keys with only an icon) carry a string-resource-backed description.
- **Reduced-motion guard.** Spring animations on key-press feedback, smartbar transitions, and Settings
  navigation respect `Settings.Global.TRANSITION_ANIMATION_SCALE = 0` and `ANIMATOR_DURATION_SCALE = 0` —
  Compose `LocalConfiguration.current.reduceMotion` is consulted before any non-trivial animation kicks in.
- **Switch Access verified.** The keyboard renders correctly under Switch Access (each key receives focus
  visit-order matching its layout row/column).
- **Long-press popup suppression on password fields (v1.8.44 + matrix #5 audit).** Password / web-password /
  numeric-PIN variations collapse to `KeyVariation.PASSWORD`, which suppresses both long-press popups and
  EmojiCompat-driven font replacement. The same guard underlies the clipboard-suggestion lock-screen gate
  (matrix #34) so the smartbar surface and the panel surface stay in lockstep on sensitive contexts.

## When to use live regions

Use `LiveRegionMode.Polite`:

- Smartbar candidate row when a new suggestion list arrives (debounce so we don't fire on every keystroke).
- Toast-like inline status: "model installed", "translation pair downloaded".
- Settings status text below a switch that the user just toggled.

Use `LiveRegionMode.Assertive` very sparingly. Reserve for failures the user must hear immediately even if
they're in the middle of reading something else: "voice model unavailable", "addon installation rejected".

Never use either for content that changes on every keystroke. Live-region spam is worse than no announcement.

## When to use a pane title

- Every top-level screen in Settings (already inherited from `FlorisScreen`).
- Every modal dialog that takes focus from the underlying screen.
- The themes browser / preview drawer when a different theme is selected.

Pane titles should be the user-facing screen name, the same string shown in the AppBar.

## Touch-target floor

- 48 dp minimum on every interactive element (WCAG 2.5.5).
- Smartbar candidate chips: 48 dp tall, width grows with content.
- Key popups, key extension previews, popup mappings: the visible footprint may be smaller, but the touch
  target stretched into the surrounding tile must still meet 48 dp.

## Color contrast floor

- 4.5:1 between key text and key background (WCAG AA, normal text).
- 3:1 for large text (24 dp+, key labels for symbols).
- The Snygg theme engine ships built-in palettes that meet these floors. Custom user themes are not enforced
  but should be flagged when contrast drops below floor (Snygg has a per-element contrast warning in the theme
  editor).

## Reading order

The keyboard reads top-to-bottom, left-to-right by default. RTL subtypes (Arabic, Hebrew, Persian, Urdu)
mirror the row order via `Modifier.autoMirrorForRtl()`. Smartbar candidates always render in
"highest-confidence first" order regardless of layout direction, because the user expects the most likely
candidate to be the first one TalkBack reaches.

## Testing

- `TouchTargetWcagTest` — regression on touch-target floor.
- Roborazzi screenshot tests (`:app:verifyRoborazziDebug`) capture rendered key labels including
  contentDescription strings, so a regression in label rendering will diff out in CI once baselines land
  (matrix #6).

## Manual QA checklist

- **Settings traversal:** with TalkBack enabled, navigate Settings home → one
  nested settings screen → back. Each screen should announce one pane title,
  then traverse app bar controls, scrollable content, bottom actions, and
  floating actions in that order.
- **Keyboard labels:** focus common key types in a normal text field: printable
  keys, Shift, Backspace, Enter, Space, clipboard, voice, keyboard mode, layout,
  input-method, and smartbar controls. None should announce as only generic
  "button" or "key" text.
- **Candidate row:** type enough text to show predictions. TalkBack should read
  each candidate's type, position, and text, and eligible candidates should
  expose the remove-from-predictions custom action.
- **Clipboard media history:** copy one text clip, one image clip, and one
  video clip, then open the keyboard clipboard panel with TalkBack enabled.
  Text clips should keep their URL/email/phone descriptions when applicable;
  image and video tiles should announce their media type, group
  (Pinned/Recent/Other when shown), and copied time, while thumbnail and video
  overlay icons remain decorative.
- **Settings search:** open Settings -> Search, verify the field is announced as
  a search field with changing result-count state, type a query with results,
  swipe the result list, and confirm each row reads result position, setting
  title, and destination screen. Type a no-results query and confirm the empty
  state is announced without trapping focus away from Browse all settings.
- **Font scale:** at high system font scale, inspect Settings metadata rows,
  hyperlinks, extension component headings, dialogs, and theme key previews for
  clipping or unreadable truncation.
- **State indicators:** review backup/restore, extension import, language-pack
  delete, dictionary import/export, home readiness, and voice readiness states.
  Readiness, progress, warning, error, cancellation, and success must be clear
  from icon shape and copy, not color alone.
- **Blocked navigation feedback:** during active user-dictionary save, delete,
  import, or export work, system back should keep the user on the screen and
  provide visible/spoken keep-screen-open feedback instead of a silent no-op.
- **Theme/layout cross-check:** repeat the affected flow in dark theme,
  SwiftKey High Contrast, phone portrait, landscape, compact, floating, and
  split/tablet layouts when the change touches IME or settings layout.

# SwiftFloris v1.8.45 — 2026-05-16

N13.2 — IME visibility now round-trips through configuration changes,
preparing for the Android 17 (API 37) behavior change that no longer
auto-restores it.

## What changed (user-visible)

If the user opens a text field inside SwiftFloris Settings (e.g. the
dictionary editor's word input, the search bar, etc.), opens the IME by
tapping it, then rotates the device, the IME now stays visible after
the rotation. On Android 14-16 this already worked because the
platform auto-restored IME visibility; on Android 17 the platform
stopped doing that for apps that don't opt in, and SwiftFloris is
now explicit about the opt-in.

## What changed (internal)

### N13.2 — `FlorisAppActivity` save/restore wire-up

The audit confirmed there is exactly **one** activity in the manifest
(`FlorisAppActivity`); the "eight Compose-route activities" the
roadmap mentioned are eight Compose `NavHost` destinations inside
that one activity, so the IME-visibility-restore wire-up lands once.

```kotlin
const val SAVED_KEY_IME_VISIBLE = "swiftfloris.app.ime_visible"

class FlorisAppActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        ...
        if (savedInstanceState?.getBoolean(SAVED_KEY_IME_VISIBLE, false) == true) {
            window?.decorView?.post {
                WindowInsetsControllerCompat(window, window.decorView)
                    .show(WindowInsetsCompat.Type.ime())
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val imeVisible = window?.decorView?.let { decor ->
            ViewCompat.getRootWindowInsets(decor)
                ?.isVisible(WindowInsetsCompat.Type.ime())
        } ?: false
        outState.putBoolean(SAVED_KEY_IME_VISIBLE, imeVisible)
    }
    ...
}
```

`WindowInsetsCompat.Type.ime()` collapses the API-26..API-30
platform variants so the call is safe at the project's `minSdk = 26`
floor.

Notable design choices:

- **Why not add `android:windowSoftInputMode="stateAlwaysVisible"`
  to the manifest?** Because that flag forces the IME open every
  single time the activity comes to the foreground, including when
  the user had deliberately dismissed it. The Android 17 behavior
  change is asking for *previous-state* restoration, not
  always-visible. Save+restore through the bundle is the
  minimum-surprise path.
- **Why `window.decorView.post { ... }`?** Because requesting
  `show(Type.ime())` immediately inside `onCreate` can race the
  view-tree attachment; posting it onto the decor view's message
  queue lets the request fire after the tree is attached, which
  is when `WindowInsetsControllerCompat` can actually reach the
  WindowInsetsAnimationController.
- **Pre-Android-17 builds.** The `show(Type.ime())` call is
  idempotent — if the IME is already visible (because the
  platform's auto-restore did its job), the call is a no-op. So
  the behavior is forward-compatible with the API-37 targetSdk
  bump on a future slice *without* changing pre-API-37 behavior.

## Versioning

- `gradle.properties`: `projectVersionCode=1845`,
  `projectVersionName=1.8.45`.

## What's next

- **Roborazzi baseline capture** — maintainer-side
  `:app:recordRoborazziDebug` run.
- **N15.1** — Free-movement Cursor mode (Gboard 16.8 virtual
  trackpad on long-press space).
- **N16.2** — SwiftKey `swiftkey-cloud.json` parser, time-sensitive
  before the 2026-05-31 retirement cutoff.

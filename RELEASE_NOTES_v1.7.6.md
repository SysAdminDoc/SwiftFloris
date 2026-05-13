# SwiftFloris v1.7.6 — Hardening pass

**Released:** 2026-05-13
**Type:** Maintenance / hardening — no new features, no UI changes.

Three rounds of targeted bug-hunting and privacy hardening, all addressing real defects with confirmed impact on correctness, data integrity, leak surface, or the project's "100% offline, zero cloud" privacy posture. Every fix ships with the existing public API unchanged.

---

## Round 1 — correctness, leaks, races (commit `920da85`)

### Clipboard history data corruption — `ClipboardHistoryManager`
The hand-rolled JSON parser in the encrypted clipboard layer silently corrupted control characters on save→load roundtrip. A clipboard entry containing a newline, tab, or carriage return came back with those characters replaced by literal `n` / `t` / `r` letters. Replaced with `kotlinx.serialization` (`@Serializable` data class) so all control characters, quotes, backslashes, and non-ASCII text survive a roundtrip cleanly. Added a coarse-grained lock so two concurrent producers can no longer drop each other's entries via a race in read-modify-write. Added a graceful fallback to an in-memory store when the Android Keystore is unavailable, so a corrupted keystore can no longer prevent the IME from instantiating.

### Theme disk leak — `ThemeManager`
Every theme reload (including each keystroke in the theme editor) created `cacheDir/loaded/<UUID>/` and never deleted it. Long-running keyboards accumulated megabytes of stale extracted theme assets indefinitely. Now sweeps stale dirs on init, deletes the on-disk dir for each evicted cached `ThemeInfo`, and spares the dir backing the currently-active theme (composition still reads from it).

### Han index race — `HanShapeBasedLanguageProvider`
`connectedLanguagePacks` / `languagePackItems` / `keyCode` were mutated from one coroutine (`create()`, `preload()`) and read concurrently from another (`suggest()`, `determineLocalComposing()`) without synchronization. Marked `@Volatile` (snapshots are already immutable) and added a `synchronized(loadLock)` around `LanguagePack.load(context)` so two coroutines for different subtypes can't race on the SQLite handle.

### IME teardown crash — `FlorisImeService.onDestroy`
`unregisterReceiver(wallpaperChangeReceiver)` was unconditional. If `onCreate` threw before registration completed, `onDestroy` raised `IllegalArgumentException` — masking the real init error and aborting the rest of teardown, leaking the static `WeakReference`. Now tracks `wallpaperReceiverRegistered`, guards each teardown step independently.

---

## Round 2 — privacy & performance (commit `346aa89`)

### Backup leak — `backup_rules.xml`, `xml-v31/backup_rules.xml`
The user dictionary table (`floris_user_dictionary`) records every word the personal-learning pipeline has promoted from the user's typing — names, addresses, codenames, vocabulary. The project's stated posture is "Zero cloud processing. Zero telemetry. All features work offline." Auto Backup was silently uploading the dictionary to the user's Google Drive on both pre-API-31 (`<full-backup-content>`) and API 31+ (`<cloud-backup>`). Removed it from both cloud paths. Kept it in `<device-transfer>` (API 31+) so explicit phone-to-phone migration still preserves the adaptive vocabulary — that flow is a deliberate user action, not a silent upload.

### Profileable shipped to release — `AndroidManifest.xml`
`<profileable android:shell="true"/>` was in the main manifest and shipped in release/beta APKs. On a privacy keyboard, a shell-attached profiler (simpleperf, perfetto) can heap-dump the IME process and expose freshly-typed text including passwords. Moved to `app/src/debug/AndroidManifest.xml` and `app/src/benchmark/AndroidManifest.xml` variant overlays so release and beta builds no longer advertise as profileable. Benchmark and debug variants still get full profiling.

### Unbounded frequency cache — `NlpManager.frequencyCache`
Was a raw `ConcurrentHashMap<String,Double>` keyed by `${subtype}-$word`. `StatisticalGlideTypingClassifier:235` calls it inside the per-candidate inner loop of every gesture, so a heavy gesture-typing session would accumulate every unique word forever in a long-lived IME process. Switched to `LruCache(5000)` — bounded warm-vocabulary cache for a single session.

### Double Room query per stats refresh — `TypingStatsScreen`
Was calling `DictionaryManager.queryAll()` twice (size, then sorted-top-10) per refresh — two full Room table scans on what can be a 10k+ row table. Cached once. Also wrapped `AdaptiveTouchModel.totalSampleCount()` in `remember(refreshTick)` so it isn't walked under a `@Synchronized` block on every recomposition.

### `.gitignore` narrowed
The Rust `debug/` pattern shadowed `app/src/debug/`. Narrowed to `/target/` and `**/target/{debug,release}/` so the Android variant tree is no longer hidden.

---

## Round 3 — leaks & OOM (commit `5543baf`)

### Feedback-scope leak — `InputFeedbackController`
Held a `CoroutineScope(SupervisorJob)` that was never cancelled on IME teardown. Each keypress launched a coroutine on that scope that captured `ims.window?.window?.decorView`, so when the InputMethodService was destroyed mid-typing the in-flight haptic and audio coroutines kept the entire window's view tree reachable past service death. Added `dispose()` and call it from `FlorisImeService.onDestroy` alongside the existing teardown steps.

### Copy-to-clipboard OOM — `FlorisCopyToClipboardActivity`
The legacy bounds-decode fallback called `MediaStore.Images.Media.getBitmap()`, which decodes at full resolution. An attacker-supplied `content://` URI to a multi-megapixel image could OOM the IME process. Replaced with sampled `BitmapFactory.decodeStream(inSampleSize=2)` that bounds peak memory. Also fixed a redundant `bitmap!!.asImageBitmap()` non-null assertion to use the let parameter `bmp`.

---

## Verification

- `:app:compileDebugKotlin` ✓
- `:app:testDebugUnitTest` — all tests pass ✓
- `:app:verifyNoInternetPermission` — privacy gate green ✓
- `:app:processDebugManifest` / `:app:processReleaseManifest` — variant overlays confirmed (`<profileable>` in debug-only) ✓

## Known follow-ups

- README clipboard claim ("AES-256 GCM, military-grade protection") references the `ClipboardHistoryManager` AES path, but the wired clipboard is still the Room-backed `ClipboardManager` (Android FBE at rest, not AES-256-GCM at the app layer). Either wire `ClipboardHistoryManager` into the active flow or soften the README. Out of scope for this hardening pass.

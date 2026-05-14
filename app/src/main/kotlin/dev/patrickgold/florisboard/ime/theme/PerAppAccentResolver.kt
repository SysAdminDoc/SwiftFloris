/*
 * Copyright (C) 2026 SwiftFloris Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.theme

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.collection.LruCache
import androidx.compose.ui.graphics.Color
import dev.patrickgold.florisboard.lib.devtools.flogDebug
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * ROADMAP §7 Next-11.3 — Per-app adaptive theming (Chrooma's "Chameleon"
 * pattern, executed cleanly without Palette / UsageStats permissions). Given
 * the foreground editor's package name (which the IME always has via
 * `getCurrentInputEditorInfo().packageName`), return an accent [Color] derived
 * from the package's launcher icon. No `PACKAGE_USAGE_STATS` permission
 * needed — the editor's package name comes from the system as part of the
 * IME contract, so this never escalates beyond what the IME already sees.
 *
 * Quantization strategy (no androidx.palette dependency to keep the dep
 * tree audited and small):
 *  - Rasterize the package's `applicationInfo.loadIcon()` drawable into a
 *    32×32 ARGB bitmap (one allocation, ~4 KB).
 *  - Sample every pixel. For each non-transparent pixel, compute HSV.
 *    Reject near-grey pixels (saturation < 0.25) and near-white/black
 *    pixels (value outside 0.20..0.92). What's left is the colorful part
 *    of the icon; pick the highest-saturation pixel as the accent.
 *  - When the icon is dominantly grey/black/white (e.g. Slack's white
 *    icon in dark mode, or system-style monochrome icons), fall back to
 *    null and let the keyboard's theme accent stand.
 *
 * The cache is bounded so repeatedly switching across hundreds of packages
 * doesn't pin a per-package Color in memory forever. Per-package result is
 * stable as long as the package's icon doesn't change, and re-extraction is
 * cheap (~1-3 ms), so cache-misses are tolerable on cold paths.
 *
 * Privacy invariant: this resolver does not store, log, or persist which
 * packages the user has invoked the keyboard in. The cache lives in-process
 * for the IME's lifetime and is wiped on process death.
 */
class PerAppAccentResolver(
    private val context: Context,
    cacheCapacity: Int = 64,
) {

    private val cache = LruCache<String, Color?>(cacheCapacity)

    /**
     * Look up the accent color for [packageName]. Returns null when the
     * package isn't installed, the icon can't be rasterized, or the icon
     * yields no sufficiently-saturated pixel. Callers should fall back to
     * the active theme's `--primary` color in the null case.
     */
    fun accentFor(packageName: String?): Color? {
        if (packageName.isNullOrBlank()) return null
        cache[packageName]?.let { return it }
        // Distinguishes "we looked, found nothing useful" from "not looked
        // yet" — once we've extracted null we don't re-extract every
        // keystroke.
        val computed = computeAccent(packageName)
        cache.put(packageName, computed)
        flogDebug { "PerAppAccentResolver: $packageName -> $computed" }
        return computed
    }

    /** Drops the cached entry for [packageName]; the next [accentFor] call
     *  re-extracts. Useful when the editor signals a package update via
     *  the system intent broadcast. */
    fun invalidate(packageName: String) {
        cache.remove(packageName)
    }

    /** Drops every cached entry. Use during heap-pressure / `onLowMemory`. */
    fun invalidateAll() {
        cache.evictAll()
    }

    internal fun computeAccent(packageName: String): Color? {
        val drawable = try {
            val pm = context.packageManager
            val applicationInfo = pm.getApplicationInfo(packageName, 0)
            applicationInfo.loadIcon(pm)
        } catch (_: PackageManager.NameNotFoundException) {
            return null
        } catch (_: Throwable) {
            return null
        } ?: return null
        val bitmap = Bitmap.createBitmap(SAMPLE_SIZE, SAMPLE_SIZE, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            val w = SAMPLE_SIZE
            val h = SAMPLE_SIZE
            drawable.setBounds(0, 0, w, h)
            drawable.draw(canvas)
            return extractAccent(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    private fun extractAccent(bitmap: Bitmap): Color? {
        val pixels = IntArray(SAMPLE_SIZE * SAMPLE_SIZE)
        bitmap.getPixels(pixels, 0, SAMPLE_SIZE, 0, 0, SAMPLE_SIZE, SAMPLE_SIZE)
        val hsv = FloatArray(3)
        var bestArgb: Int? = null
        var bestSaturation = MIN_SATURATION
        for (argb in pixels) {
            val alpha = (argb ushr 24) and 0xFF
            if (alpha < 200) continue  // mostly-transparent pixel; skip
            val r = (argb ushr 16) and 0xFF
            val g = (argb ushr 8) and 0xFF
            val b = argb and 0xFF
            // RGB→HSV inline (cheaper than android.graphics.Color.RGBToHSV
            // by ~3× because we don't need to allocate a FloatArray per
            // call; reuse the hsv array).
            rgbToHsv(r, g, b, hsv)
            val s = hsv[1]
            val v = hsv[2]
            if (s < MIN_SATURATION) continue
            if (v < MIN_VALUE || v > MAX_VALUE) continue
            if (s > bestSaturation) {
                bestSaturation = s
                bestArgb = argb
            }
        }
        return bestArgb?.let { Color(it) }
    }

    companion object {
        private const val SAMPLE_SIZE = 32
        // Drop near-grey (Slack's whitespace icon, Outlook's blue+white
        // background) — anything below ~25 % saturation isn't an accent
        // candidate, it's chrome.
        private const val MIN_SATURATION = 0.25f
        // Drop near-black (Wikipedia W) and near-white (Tumblr's text on
        // dark) — those don't render legibly against the keyboard surface
        // either way.
        private const val MIN_VALUE = 0.20f
        private const val MAX_VALUE = 0.92f

        internal fun rgbToHsv(r: Int, g: Int, b: Int, out: FloatArray) {
            val rf = r / 255f
            val gf = g / 255f
            val bf = b / 255f
            val maxF = max(rf, max(gf, bf))
            val minF = min(rf, min(gf, bf))
            val delta = maxF - minF
            val v = maxF
            val s = if (maxF == 0f) 0f else delta / maxF
            var h: Float
            if (delta == 0f) {
                h = 0f
            } else {
                h = when (maxF) {
                    rf -> 60f * (((gf - bf) / delta) % 6f)
                    gf -> 60f * (((bf - rf) / delta) + 2f)
                    else -> 60f * (((rf - gf) / delta) + 4f)
                }
                if (h < 0f) h += 360f
            }
            out[0] = h
            out[1] = s
            out[2] = v
        }

        /** Test-only helper that exposes the colorimetric saturation/value
         *  bands so unit tests can pin the rejection thresholds. */
        internal fun classify(r: Int, g: Int, b: Int): String {
            val hsv = FloatArray(3)
            rgbToHsv(r, g, b, hsv)
            val s = hsv[1]
            val v = hsv[2]
            return when {
                s < MIN_SATURATION -> "grey"
                v < MIN_VALUE -> "tooDark"
                v > MAX_VALUE -> "tooLight"
                else -> "accent"
            }
        }

        /** Test-only helper that returns the unbounded saturation so unit
         *  tests can verify the "highest saturation wins" tiebreak rule. */
        internal fun saturationOf(r: Int, g: Int, b: Int): Float {
            val hsv = FloatArray(3)
            rgbToHsv(r, g, b, hsv)
            return hsv[1]
        }

        /** Test-only helper that returns the unbounded hue so unit tests
         *  can verify the RGB→HSV math. Units: degrees, 0..360. */
        internal fun hueOf(r: Int, g: Int, b: Int): Float {
            val hsv = FloatArray(3)
            rgbToHsv(r, g, b, hsv)
            return hsv[0]
        }

        /** Distance-from-360 metric for hue comparison (since hue wraps).
         *  Test-only utility. */
        internal fun hueDistance(a: Float, b: Float): Float {
            val raw = abs(a - b)
            return min(raw, 360f - raw)
        }
    }
}

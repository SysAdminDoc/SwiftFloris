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

package dev.patrickgold.florisboard.lib.io

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

/**
 * The APK's asset folder listings, read once per process. Bundled assets can't change while the app runs, yet
 * every extension index lists each bundled extension's folder again just to tell folders from files, and each
 * index of each kind did it all over. Callers asking for a path that's being read wait for that one read.
 * A read that throws or comes back null isn't kept, so the next caller tries again.
 */
internal class AssetListingCache {
    private val listings = ConcurrentHashMap<String, Lazy<List<String>?>>()

    fun list(path: String, read: (String) -> Array<String>?): List<String>? {
        val entry = listings.computeIfAbsent(path) { lazy(LazyThreadSafetyMode.SYNCHRONIZED) { read(path)?.toList() } }
        val listing = try {
            entry.value
        } catch (e: Exception) {
            listings.remove(path, entry)
            throw e
        }
        if (listing == null) listings.remove(path, entry)
        return listing
    }

    companion object {
        private val shared = AssetListingCache()

        fun list(context: Context, path: String): List<String>? = shared.list(path) { context.assets.list(it) }
    }
}

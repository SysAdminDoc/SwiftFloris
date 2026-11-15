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

package dev.patrickgold.florisboard.ime.clipboard

/**
 * Keeps clipboard changes that finish out of order from publishing out of order.
 *
 * Each system clipboard callback takes a revision when it arrives, on the main thread and so in
 * arrival order, then does its slow work (cloning media) on I/O. Its result may replace the primary
 * clip only if nothing that arrived after it has published yet: a newer callback that published,
 * or a change the keyboard made itself. A loser's caller discards its result, including any media
 * file it cloned. The primary clip then ends up as if the changes had run one at a time in the
 * order they happened.
 *
 * A callback whose clip the primary clip already shows changes nothing, but in that order it still
 * comes after everything older, so it claims its revision with [claimIfNewest] and older ones
 * lose. One that changes nothing for any other reason (a copy that failed to import, a clear or
 * copy the sync setting ignores) claims nothing, so an older change still applies. The check and
 * the publication share one lock with local changes, so nothing can slip in between.
 */
internal class ClipboardRevisionGate {
    private var latest = 0L
    // Newest revision that published or claimed. Older callbacks lose to it.
    private var published = 0L

    /** Called when a system clipboard callback arrives; returns its revision. */
    @Synchronized
    fun arrive(): Long = ++latest

    /** Applies a change made by the keyboard itself, which outranks every callback still running. */
    @Synchronized
    fun <T> local(change: () -> T): T {
        published = ++latest
        return change()
    }

    /**
     * Runs [publish] and returns true only if nothing that arrived after [revision] has published.
     */
    @Synchronized
    fun publishIfNewest(revision: Long, publish: () -> Unit): Boolean {
        if (revision <= published) return false
        publish()
        published = revision
        return true
    }

    /**
     * For a callback whose clip the primary clip already shows: claims [revision] without
     * publishing anything, and returns true, only if nothing newer published and [stillCurrent]
     * still holds. Otherwise the revision stays free to publish.
     */
    @Synchronized
    fun claimIfNewest(revision: Long, stillCurrent: () -> Boolean): Boolean {
        if (revision <= published || !stillCurrent()) return false
        published = revision
        return true
    }
}

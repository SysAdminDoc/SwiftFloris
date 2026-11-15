/*
 * Copyright (C) 2026 SwiftFloris Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.ime.keyboard

import java.util.concurrent.atomic.AtomicReference

/**
 * The last two words learned in a row, which personal bigrams and trigrams are built from.
 *
 * Words are learned on the main thread, while editor changes reset the chain from a background
 * collector. The chain is one immutable snapshot swapped atomically, so a reset can't be half
 * applied, and a reset that lands while a word is being learned wins: that word doesn't start a
 * new chain the next word, typed after the change, would pair with.
 */
internal class PersonalLearnChain {
    data class Link(val last: String, val prev: String?, val localeTag: String)

    /**
     * One read of the chain, compared by identity. Every reset makes a new one, so [advance]
     * notices a reset even when the chain was already empty.
     */
    class Snapshot(val link: Link?)

    private val current = AtomicReference(Snapshot(null))

    fun reset() {
        current.set(Snapshot(null))
    }

    /** The chain as it stands, to learn the next word against and then pass to [advance]. */
    fun snapshot(): Snapshot = current.get()

    /**
     * Appends [word] to the chain read by [snapshot]. Words in another language start over.
     * Returns false, leaving the chain as it is, when it changed since [from] was read.
     */
    fun advance(from: Snapshot, word: String, localeTag: String): Boolean {
        val next = Link(
            last = word,
            prev = from.link?.takeIf { it.localeTag == localeTag }?.last,
            localeTag = localeTag,
        )
        return current.compareAndSet(from, Snapshot(next))
    }
}

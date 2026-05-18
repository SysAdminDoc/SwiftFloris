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

package dev.patrickgold.florisboard.ime.nlp.kenlm

import java.io.RandomAccessFile
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Path

/**
 * ROADMAP §7 Next-3.1a — KenLM mmap trie reader.
 *
 * Wraps a memory-mapped KenLM binary file so subsequent layers can
 * navigate the unigram / bigram / trigram / 4-gram / 5-gram blocks.
 * The header is parsed eagerly via [KenLmBinaryReader.readHeader];
 * the trie *body* (vocabulary string arena + Bhiksha-encoded
 * next-pointer arrays + probability + backoff quantisation tables)
 * stays mmap'd so 100s-of-MB models don't blow up the IME heap.
 *
 * This scaffold pins the lifecycle + the I/O boundary so the
 * subsequent slices (Bhiksha-decoded pointer reads + quantised
 * probability lookups) can land independently from the JNI path.
 * Real per-n-gram lookup arrives in Next-3.1b alongside the
 * upstream KenLM JNI bring-up.
 *
 *  ```
 *  KenLmTrieReader.openMapped(path).use { reader ->
 *      reader.header // structured header parsed eagerly
 *      reader.vocabularyByteRange // sub-buffer for unigram strings
 *  }
 *  ```
 */
class KenLmTrieReader private constructor(
    val header: KenLmBinaryHeader,
    private val buffer: MappedByteBuffer,
    private val channel: FileChannel,
    private val randomAccessFile: RandomAccessFile,
) : AutoCloseable {

    /**
     * File offset where the trie *body* begins (just past the header
     * block).  For a TRIE / QUANT_TRIE model the body lays out the
     * unigram → bigram → trigram → ... → highest-order blocks
     * sequentially; this offset is where the first block starts.
     */
    val bodyStartOffset: Long by lazy {
        // Header layout: 64-byte magic + uint32 model type + uint32 order
        // + 4 × uint8 FixedWidthParameters + order × uint64 counts.
        64L + 4L + 4L + 4L + (header.order * 8L)
    }

    /**
     * Byte length of the body block. Mapped buffer length minus the
     * computed body-start offset.
     */
    val bodyByteLength: Long
        get() = (channel.size() - bodyStartOffset).coerceAtLeast(0)

    /**
     * Read [length] bytes from absolute file offset [offset] into a
     * new byte array. Returns null when the requested span runs past
     * end-of-file. Used by the next-slice trie navigator.
     */
    fun readBytesAt(offset: Long, length: Int): ByteArray? {
        if (offset < 0 || length < 0) return null
        if (offset < bodyStartOffset) return null
        if (length > 0 && offset > Long.MAX_VALUE - length) return null
        if (offset + length > channel.size()) return null
        val out = ByteArray(length)
        // Mapped buffer is little-endian per KenLM; use absolute reads
        // so concurrent readers don't fight over the buffer position.
        synchronized(buffer) {
            val relativeOffset = offset - bodyStartOffset
            if (relativeOffset > Int.MAX_VALUE) return null
            val pos = relativeOffset.toInt()
            if (length > buffer.capacity() - pos) return null
            buffer.position(pos)
            buffer.get(out, 0, length)
        }
        return out
    }

    override fun close() {
        runCatching { channel.close() }
        runCatching { randomAccessFile.close() }
    }

    companion object {
        /**
         * Memory-map [path] and parse the KenLM header. The caller is
         * responsible for closing the returned reader. Throws
         * [KenLmFormatException] when [path] is not a recognisable
         * KenLM file.
         */
        fun openMapped(path: Path): KenLmTrieReader {
            val raf = RandomAccessFile(path.toFile(), "r")
            try {
                val channel = raf.channel
                // Read the first 256 bytes as a sanity buffer for the header.
                val headerBytes = ByteArray(minOf(256L, channel.size()).toInt())
                val headerBb = java.nio.ByteBuffer.wrap(headerBytes)
                var readOffset = 0L
                while (headerBb.hasRemaining()) {
                    val read = channel.read(headerBb, readOffset)
                    if (read == -1) break
                    readOffset += read
                }
                channel.position(0)
                val header = KenLmBinaryReader.readHeader(
                    java.io.ByteArrayInputStream(headerBytes),
                )
                val bodyStart = 64L + 4L + 4L + 4L + (header.order * 8L)
                val bodyLen = (channel.size() - bodyStart).coerceAtLeast(0)
                if (bodyLen > Int.MAX_VALUE.toLong()) {
                    throw KenLmFormatException("KenLM body too large for single mmap: $bodyLen bytes")
                }
                val mapped: MappedByteBuffer = channel.map(
                    FileChannel.MapMode.READ_ONLY,
                    bodyStart,
                    bodyLen,
                )
                mapped.order(ByteOrder.LITTLE_ENDIAN)
                return KenLmTrieReader(header, mapped, channel, raf)
            } catch (t: Throwable) {
                runCatching { raf.close() }
                throw t
            }
        }
    }
}

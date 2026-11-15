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

package org.florisboard.lib.android

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.InterruptedIOException
import java.io.OutputStream

class CopyToLimitedTest : FunSpec({

    test("stops at the next buffer once the copying thread is interrupted") {
        var reads = 0
        val endless = object : InputStream() {
            override fun read(): Int = 0

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                reads++
                if (reads == 2) Thread.currentThread().interrupt()
                return len
            }
        }
        try {
            shouldThrow<InterruptedIOException> {
                endless.copyToLimited(OutputStream.nullOutputStream(), 1L shl 30)
            }
        } finally {
            Thread.interrupted()
        }
        reads shouldBe 2
    }

    test("copies the whole stream when nothing interrupts it") {
        val data = ByteArray(3 * DEFAULT_BUFFER_SIZE + 5) { it.toByte() }
        val out = ByteArrayOutputStream()

        ByteArrayInputStream(data).copyToLimited(out, data.size.toLong()) shouldBe data.size.toLong()

        out.toByteArray().contentEquals(data) shouldBe true
    }
})

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

package dev.patrickgold.florisboard.lib

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.ByteBuffer

class NativeStrTest : FunSpec({
    test("heap buffers decode only remaining bytes without moving position") {
        val buffer = ByteBuffer.wrap("xxheap-valueyy".toByteArray()).apply {
            position(2)
            limit(12)
        }

        buffer.toJavaString() shouldBe "heap-value"
        buffer.position() shouldBe 2
        buffer.limit() shouldBe 12
    }

    test("sliced heap buffers honor array offset position and limit") {
        val source = ByteBuffer.wrap("prefix-sliced-value-suffix".toByteArray()).apply {
            position(7)
            limit(19)
        }
        val slice = source.slice().apply {
            position(1)
            limit(12)
        }

        slice.toJavaString() shouldBe "liced-value"
        slice.position() shouldBe 1
        slice.limit() shouldBe 12
    }

    test("direct buffers decode remaining bytes without moving position") {
        val bytes = "00direct-value99".toByteArray()
        val buffer = ByteBuffer.allocateDirect(bytes.size).apply {
            put(bytes)
            flip()
            position(2)
            limit(14)
        }

        buffer.toJavaString() shouldBe "direct-value"
        buffer.position() shouldBe 2
        buffer.limit() shouldBe 14
    }

    test("read-only buffers decode remaining bytes without array access") {
        val buffer = ByteBuffer.wrap("##readonly##".toByteArray()).asReadOnlyBuffer().apply {
            position(2)
            limit(10)
        }

        buffer.toJavaString() shouldBe "readonly"
        buffer.position() shouldBe 2
        buffer.limit() shouldBe 10
    }

    test("toNativeStr round-trips without consuming the native buffer") {
        val buffer = "emoji cafe".toNativeStr()

        buffer.toJavaString() shouldBe "emoji cafe"
        buffer.position() shouldBe 0
        buffer.toJavaString() shouldBe "emoji cafe"
    }
})

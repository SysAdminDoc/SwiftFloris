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

package dev.patrickgold.florisboard.ime.sync

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

data class SyncQrCodeMatrix(
    val size: Int,
    val cells: List<Boolean>,
) {
    init {
        require(size > 0) { "size must be positive" }
        require(cells.size == size * size) { "cells must match size * size" }
    }

    operator fun get(x: Int, y: Int): Boolean {
        return cells[y * size + x]
    }
}

object SyncQrCode {
    fun encode(raw: String, size: Int = DEFAULT_SIZE): SyncQrCodeMatrix {
        require(raw.isNotBlank()) { "raw must not be blank" }
        require(size >= MIN_SIZE) { "size must be >= $MIN_SIZE" }
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1,
        )
        val bitMatrix = QRCodeWriter().encode(raw, BarcodeFormat.QR_CODE, size, size, hints)
        val cells = List(bitMatrix.width * bitMatrix.height) { index ->
            val x = index % bitMatrix.width
            val y = index / bitMatrix.width
            bitMatrix.get(x, y)
        }
        return SyncQrCodeMatrix(bitMatrix.width, cells)
    }

    private const val MIN_SIZE = 21
    private const val DEFAULT_SIZE = 37
}

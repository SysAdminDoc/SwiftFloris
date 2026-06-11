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

package dev.patrickgold.florisboard.ime.clipboard.provider

internal object ClipboardSensitiveTextClassifier {
    private val bareOneTimeCode = Regex("""^\d(?:[\s-]?\d){3,7}$""")
    private val labelledOneTimeCode = Regex(
        pattern = """(?i)\b(?:2fa|otp|totp|mfa|code|pin|passcode|verification|auth(?:entication)?|login)\b.{0,24}\b\d(?:[\s-]?\d){3,7}\b""",
    )

    fun isSensitive(text: String?): Boolean {
        val normalized = text?.trim().orEmpty()
        if (normalized.isEmpty()) return false
        return bareOneTimeCode.matches(normalized) || labelledOneTimeCode.containsMatchIn(normalized)
    }
}

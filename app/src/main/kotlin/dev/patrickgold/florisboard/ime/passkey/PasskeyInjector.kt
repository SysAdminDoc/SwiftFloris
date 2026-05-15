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

package dev.patrickgold.florisboard.ime.passkey

/**
 * ROADMAP §7 L10 — WebAuthn passkey injection from IME.
 *
 * Today an IME observes when the user has focused a password field
 * (`editorInfo.inputType` carries `TYPE_TEXT_VARIATION_PASSWORD` or
 * the field's `autofillHints` array contains
 * `AUTOFILL_HINT_PASSWORD`), but the IME can't drive the underlying
 * WebAuthn ceremony — the credential picker is owned by the OS
 * (Android 14+) or the autofill provider. SwiftFloris' L10 makes the
 * IME a **passkey conduit**: when the focused editor signals it can
 * accept a passkey (relying-party id + challenge surfaced via
 * `EditorInfo.extras` or via a credential-manager adapter installed
 * as an addon), the IME pops a "Use passkey" chip in the smartbar
 * that the user taps to drive the rest of the ceremony.
 *
 * This scaffold ships the **detection layer** + the
 * **PasskeyAdapter interface** that the credential-manager addon
 * implements. The actual WebAuthn ceremony cannot live in `:app`
 * because the Android Credential Manager API entrypoints are
 * Activity-bound (`CredentialManager.getCredential(activity, ...)`)
 * and would tangle the IME's no-Activity lifecycle. The adapter
 * pattern matches Next-4.2 (ML Kit) and L1 (LiteRT-LM): the heavy
 * runtime stays out of the base APK, behind a registry the IME reads.
 */
interface PasskeyAdapter {
    /**
     * Best-effort sync probe: are passkeys available for [relyingPartyId]?
     * Should never trigger UI; the smartbar uses this purely to decide
     * whether to render the "Use passkey" chip.
     */
    fun hasPasskeyFor(relyingPartyId: String): Boolean

    /**
     * Kick off the WebAuthn `get()` ceremony for the focused editor.
     * Returns a [PasskeyAssertionRequest] the IME forwards to the
     * focused editor by calling `commitContent(InputContentInfo)` — or
     * `null` if the ceremony was cancelled / failed.
     *
     * The actual call goes through the Android Credential Manager
     * from inside the *adapter*'s Activity; the IME never sees the
     * raw credential material.
     */
    suspend fun requestAssertion(
        relyingPartyId: String,
        challenge: ByteArray,
    ): PasskeyAssertionRequest?

    object Default : PasskeyAdapter {
        override fun hasPasskeyFor(relyingPartyId: String): Boolean = false
        override suspend fun requestAssertion(
            relyingPartyId: String,
            challenge: ByteArray,
        ): PasskeyAssertionRequest? = null
    }
}

/**
 * Cross-process passkey assertion envelope. The IME hands this to the
 * focused editor; the editor / web view sends it on to the
 * relying-party server, which decodes the WebAuthn assertion
 * normally. SwiftFloris never inspects the bytes — it's a pass-
 * through transport.
 */
data class PasskeyAssertionRequest(
    val relyingPartyId: String,
    val clientDataJsonBase64Url: String,
    val authenticatorDataBase64Url: String,
    val signatureBase64Url: String,
    val userHandleBase64Url: String?,
    val credentialIdBase64Url: String,
) {
    init {
        require(relyingPartyId.isNotBlank()) { "relyingPartyId must not be blank" }
        require(clientDataJsonBase64Url.isNotBlank()) { "clientDataJson must not be blank" }
        require(authenticatorDataBase64Url.isNotBlank()) {
            "authenticatorData must not be blank"
        }
        require(signatureBase64Url.isNotBlank()) { "signature must not be blank" }
        require(credentialIdBase64Url.isNotBlank()) { "credentialId must not be blank" }
    }
}

/**
 * Field-introspection helpers — pure functions over the inputs an
 * IME already has via `EditorInfo`. The detector is intentionally
 * conservative: it only fires when the field gives an unambiguous
 * passkey-friendly signal (relying-party + challenge declared via
 * the [`EditorInfo.extras`] bundle the focused web-view advertises).
 */
object PasskeyFieldDetector {

    /**
     * Detect a passkey-receivable field from the EditorInfo-style
     * bundle. Returns null when the field is a password field with no
     * passkey signal (the IME should fall through to ordinary text
     * suggestions in that case).
     *
     * [autofillHints] mirrors `EditorInfo.hintText` / autofillHints
     * array; [extras] mirrors `EditorInfo.extras` and is where the
     * focused webview or Credential-Manager-aware app advertises a
     * relying-party id + challenge.
     */
    fun detect(
        autofillHints: List<String>?,
        extras: Map<String, Any?>?,
    ): PasskeyFieldHint? {
        val rp = extras?.get(EXTRA_RP_ID) as? String ?: return null
        val challenge = extras[EXTRA_CHALLENGE] as? ByteArray ?: return null
        if (rp.isBlank() || challenge.isEmpty()) return null
        val acceptsPassword = autofillHints.orEmpty().any {
            it == "AUTOFILL_HINT_PASSWORD" ||
                it == "android.text.autofill.password" ||
                it == "password"
        }
        if (!acceptsPassword) return null
        return PasskeyFieldHint(relyingPartyId = rp, challenge = challenge.copyOf())
    }

    /** Key in `EditorInfo.extras` carrying the WebAuthn `rp.id`. */
    const val EXTRA_RP_ID: String = "dev.patrickgold.florisboard.passkey.rpId"

    /** Key in `EditorInfo.extras` carrying the WebAuthn challenge bytes. */
    const val EXTRA_CHALLENGE: String = "dev.patrickgold.florisboard.passkey.challenge"
}

data class PasskeyFieldHint(
    val relyingPartyId: String,
    val challenge: ByteArray,
) {
    init {
        require(relyingPartyId.isNotBlank()) { "relyingPartyId must not be blank" }
        require(challenge.isNotEmpty()) { "challenge must not be empty" }
    }

    // ByteArray-aware equals/hashCode so the data class is comparable.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PasskeyFieldHint) return false
        return relyingPartyId == other.relyingPartyId &&
            challenge.contentEquals(other.challenge)
    }

    override fun hashCode(): Int =
        relyingPartyId.hashCode() * 31 + challenge.contentHashCode()
}

object PasskeyAdapterRegistry {
    @Volatile
    private var current: PasskeyAdapter = PasskeyAdapter.Default
    val active: PasskeyAdapter get() = current
    fun setActive(adapter: PasskeyAdapter) { current = adapter }
    fun reset() { current = PasskeyAdapter.Default }
}

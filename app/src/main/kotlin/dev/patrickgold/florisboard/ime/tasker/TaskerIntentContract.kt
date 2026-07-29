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

package dev.patrickgold.florisboard.ime.tasker

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * The private SwiftFloris action schema carried by the public Locale/Tasker
 * plug-in protocol.
 *
 * Hosts never send these action names directly. The configuration activity
 * returns one compact JSON string under [Plugin.EXTRA_STRING_JSON], signed with
 * an app-private per-install HMAC key. Tasker stores that configuration and
 * later returns it to the single [Plugin.ACTION_FIRE_SETTING] receiver.
 */
object TaskerIntentContract {
    object Plugin {
        const val ACTION_EDIT_SETTING: String =
            "com.twofortyfouram.locale.intent.action.EDIT_SETTING"
        const val ACTION_FIRE_SETTING: String =
            "com.twofortyfouram.locale.intent.action.FIRE_SETTING"
        const val EXTRA_BUNDLE: String =
            "com.twofortyfouram.locale.intent.extra.BUNDLE"
        const val EXTRA_STRING_BLURB: String =
            "com.twofortyfouram.locale.intent.extra.BLURB"
        const val EXTRA_STRING_JSON: String =
            "com.twofortyfouram.locale.intent.extra.STRING_JSON"

        const val SCHEMA_VERSION: Int = 1
        const val MAX_JSON_LENGTH: Int = 16_384
    }

    /** Insert literal text at the cursor of the focused editor. */
    object InsertText {
        const val ACTION: String = "swiftfloris.action.INSERT_TEXT"
        const val EXTRA_TEXT: String = "text"
        const val EXTRA_APPEND_SPACE: String = "appendSpace"
    }

    /** Insert the current clipboard primary item at the cursor. */
    object InsertClipboard {
        const val ACTION: String = "swiftfloris.action.INSERT_CLIP"
    }

    /** Switch the active subtype layout. */
    object SwitchLayout {
        const val ACTION: String = "swiftfloris.action.SWITCH_LAYOUT"
        const val EXTRA_LAYOUT_ID: String = "layoutId"
    }

    /** Trigger the voice-input session. */
    object TriggerVoice {
        const val ACTION: String = "swiftfloris.action.TRIGGER_VOICE"
        const val EXTRA_MODE: String = "mode"
    }

    fun validate(action: String, extras: Map<String, Any?>): ValidationResult {
        return when (action) {
            InsertText.ACTION -> {
                val unexpected = rejectUnexpectedExtras(
                    extras,
                    allowed = setOf(InsertText.EXTRA_TEXT, InsertText.EXTRA_APPEND_SPACE),
                )
                if (unexpected != null) return unexpected
                val text = extras[InsertText.EXTRA_TEXT] as? String
                val appendSpace = extras[InsertText.EXTRA_APPEND_SPACE]
                when {
                    text == null -> ValidationResult.Reject("missing required EXTRA_TEXT")
                    text.isEmpty() -> ValidationResult.Reject("EXTRA_TEXT must not be empty")
                    text.length > MAX_INSERT_LENGTH -> ValidationResult.Reject(
                        "EXTRA_TEXT exceeds $MAX_INSERT_LENGTH chars",
                    )
                    appendSpace != null && appendSpace !is Boolean -> ValidationResult.Reject(
                        "EXTRA_APPEND_SPACE must be boolean when present",
                    )
                    else -> ValidationResult.Accept
                }
            }
            InsertClipboard.ACTION -> {
                rejectUnexpectedExtras(extras, allowed = emptySet()) ?: ValidationResult.Accept
            }
            SwitchLayout.ACTION -> {
                val unexpected = rejectUnexpectedExtras(
                    extras,
                    allowed = setOf(SwitchLayout.EXTRA_LAYOUT_ID),
                )
                if (unexpected != null) return unexpected
                val layoutId = extras[SwitchLayout.EXTRA_LAYOUT_ID] as? String
                when {
                    layoutId == null -> ValidationResult.Reject("missing required EXTRA_LAYOUT_ID")
                    layoutId.isBlank() -> ValidationResult.Reject("EXTRA_LAYOUT_ID must not be blank")
                    !layoutId.matches(LAYOUT_ID_REGEX) -> ValidationResult.Reject(
                        "EXTRA_LAYOUT_ID must match $LAYOUT_ID_REGEX",
                    )
                    else -> ValidationResult.Accept
                }
            }
            TriggerVoice.ACTION -> {
                val unexpected = rejectUnexpectedExtras(
                    extras,
                    allowed = setOf(TriggerVoice.EXTRA_MODE),
                )
                if (unexpected != null) return unexpected
                val rawMode = extras[TriggerVoice.EXTRA_MODE]
                when {
                    rawMode == null -> ValidationResult.Accept
                    rawMode !is String -> ValidationResult.Reject("EXTRA_MODE must be a string")
                    rawMode !in VOICE_MODES -> ValidationResult.Reject(
                        "EXTRA_MODE must be 'dictation' or 'command'",
                    )
                    else -> ValidationResult.Accept
                }
            }
            else -> ValidationResult.Reject("unknown SwiftFloris Tasker action")
        }
    }

    /**
     * Builds the single-string Locale bundle payload. The returned JSON
     * contains an HMAC tag but never contains [secret].
     */
    internal fun createAuthenticatedJson(
        secret: ByteArray,
        action: String,
        extras: Map<String, Any?>,
    ): String {
        require(secret.size == AUTH_SECRET_BYTES) { "Tasker authentication secret has invalid length" }
        require(validate(action, extras) == ValidationResult.Accept) {
            "Cannot sign an invalid Tasker action"
        }

        val values = linkedMapOf<String, kotlinx.serialization.json.JsonElement>(
            FIELD_SCHEMA_VERSION to JsonPrimitive(Plugin.SCHEMA_VERSION),
            FIELD_ACTION to JsonPrimitive(action),
        )
        extras.toSortedMap().forEach { (key, value) ->
            values[key] = when (value) {
                is String -> JsonPrimitive(value)
                is Boolean -> JsonPrimitive(value)
                else -> error("Unsupported Tasker extra type")
            }
        }
        values[FIELD_AUTH_TAG] = JsonPrimitive(
            Base64.getUrlEncoder().withoutPadding().encodeToString(
                hmac(secret, canonicalPayload(action, extras)),
            ),
        )
        return Json.encodeToString(JsonObject.serializer(), JsonObject(values))
    }

    /**
     * Strictly parses and authenticates a plug-in payload. Authentication uses
     * [MessageDigest.isEqual] so a mismatched tag does not leak a useful
     * byte-prefix timing signal.
     */
    internal fun authenticateJson(secret: ByteArray, rawJson: String): PluginAuthenticationResult {
        if (secret.size != AUTH_SECRET_BYTES) {
            return PluginAuthenticationResult.Reject("authentication secret unavailable")
        }
        val parsed = parseJson(rawJson)
        if (parsed is ParsedPluginPayload.Reject) {
            return PluginAuthenticationResult.Reject(parsed.reason)
        }
        parsed as ParsedPluginPayload.Accept
        val expected = hmac(secret, canonicalPayload(parsed.action.action, parsed.action.extras))
        if (!MessageDigest.isEqual(expected, parsed.authTag)) {
            return PluginAuthenticationResult.Reject("authentication tag mismatch")
        }
        return PluginAuthenticationResult.Accept(parsed.action)
    }

    /**
     * Reads an existing configuration for the edit activity without treating
     * it as authorized to execute. Saving always replaces its tag using the
     * current per-install secret, so configurations invalidated by rotation can
     * be deliberately re-authorized by the user.
     */
    internal fun decodeForEditing(rawJson: String): TaskerPluginAction? {
        return (parseJson(rawJson) as? ParsedPluginPayload.Accept)?.action
    }

    internal fun blurb(action: String, extras: Map<String, Any?>): String {
        return when (action) {
            InsertText.ACTION -> "Insert configured text"
            InsertClipboard.ACTION -> "Paste current clipboard"
            SwitchLayout.ACTION -> "Switch layout: ${extras[SwitchLayout.EXTRA_LAYOUT_ID]}"
            TriggerVoice.ACTION -> "Start voice input: ${extras[TriggerVoice.EXTRA_MODE] ?: "dictation"}"
            else -> "SwiftFloris action"
        }
    }

    /** Hard cap on inserted text length to prevent flooding the editor. */
    const val MAX_INSERT_LENGTH: Int = 4096
    internal const val AUTH_SECRET_BYTES: Int = 32
    internal const val AUTH_TAG_BYTES: Int = 32

    private const val FIELD_SCHEMA_VERSION = "schemaVersion"
    private const val FIELD_ACTION = "action"
    private const val FIELD_AUTH_TAG = "authTag"
    private const val HMAC_ALGORITHM = "HmacSHA256"
    private val LAYOUT_ID_REGEX = Regex("^[a-z0-9_]{1,32}$")
    private val VOICE_MODES = setOf("dictation", "command")
    private val JSON = Json {
        isLenient = false
        ignoreUnknownKeys = false
    }

    private fun parseJson(rawJson: String): ParsedPluginPayload {
        if (rawJson.isEmpty() || rawJson.length > Plugin.MAX_JSON_LENGTH) {
            return ParsedPluginPayload.Reject("plug-in JSON length is invalid")
        }
        val obj = runCatching {
            JSON.parseToJsonElement(rawJson) as? JsonObject
        }.getOrNull() ?: return ParsedPluginPayload.Reject("plug-in JSON is malformed")

        val versionElement = obj[FIELD_SCHEMA_VERSION] as? JsonPrimitive
            ?: return ParsedPluginPayload.Reject("schema version missing")
        val version = versionElement.takeUnless { it.isString }?.intOrNull
            ?: return ParsedPluginPayload.Reject("schema version must be an integer")
        if (version != Plugin.SCHEMA_VERSION) {
            return ParsedPluginPayload.Reject("unsupported schema version")
        }

        val action = obj.string(FIELD_ACTION)
            ?: return ParsedPluginPayload.Reject("action missing or not a string")
        val allowedPayloadKeys = when (action) {
            InsertText.ACTION -> setOf(InsertText.EXTRA_TEXT, InsertText.EXTRA_APPEND_SPACE)
            InsertClipboard.ACTION -> emptySet()
            SwitchLayout.ACTION -> setOf(SwitchLayout.EXTRA_LAYOUT_ID)
            TriggerVoice.ACTION -> setOf(TriggerVoice.EXTRA_MODE)
            else -> return ParsedPluginPayload.Reject("unknown SwiftFloris Tasker action")
        }
        val allowedKeys = allowedPayloadKeys + setOf(
            FIELD_SCHEMA_VERSION,
            FIELD_ACTION,
            FIELD_AUTH_TAG,
        )
        if (obj.keys != allowedKeys && !allowedKeys.containsAll(obj.keys)) {
            return ParsedPluginPayload.Reject("unexpected plug-in JSON fields")
        }

        val extras = linkedMapOf<String, Any?>()
        when (action) {
            InsertText.ACTION -> {
                val text = obj.string(InsertText.EXTRA_TEXT)
                    ?: return ParsedPluginPayload.Reject("text missing or not a string")
                extras[InsertText.EXTRA_TEXT] = text
                if (obj.containsKey(InsertText.EXTRA_APPEND_SPACE)) {
                    val appendSpace = obj.strictBoolean(InsertText.EXTRA_APPEND_SPACE)
                        ?: return ParsedPluginPayload.Reject("appendSpace must be boolean")
                    extras[InsertText.EXTRA_APPEND_SPACE] = appendSpace
                }
            }
            InsertClipboard.ACTION -> Unit
            SwitchLayout.ACTION -> {
                val layoutId = obj.string(SwitchLayout.EXTRA_LAYOUT_ID)
                    ?: return ParsedPluginPayload.Reject("layoutId missing or not a string")
                extras[SwitchLayout.EXTRA_LAYOUT_ID] = layoutId
            }
            TriggerVoice.ACTION -> {
                if (obj.containsKey(TriggerVoice.EXTRA_MODE)) {
                    val mode = obj.string(TriggerVoice.EXTRA_MODE)
                        ?: return ParsedPluginPayload.Reject("mode must be a string")
                    extras[TriggerVoice.EXTRA_MODE] = mode
                }
            }
        }

        when (val validation = validate(action, extras)) {
            ValidationResult.Accept -> Unit
            is ValidationResult.Reject -> return ParsedPluginPayload.Reject(validation.reason)
        }
        val encodedTag = obj.string(FIELD_AUTH_TAG)
            ?: return ParsedPluginPayload.Reject("authentication tag missing or not a string")
        val authTag = runCatching {
            Base64.getUrlDecoder().decode(encodedTag)
        }.getOrNull()?.takeIf { it.size == AUTH_TAG_BYTES }
            ?: return ParsedPluginPayload.Reject("authentication tag encoding is invalid")
        return ParsedPluginPayload.Accept(
            action = TaskerPluginAction(action, extras),
            authTag = authTag,
        )
    }

    private fun JsonObject.string(key: String): String? {
        val primitive = this[key] as? JsonPrimitive ?: return null
        return primitive.takeIf { it.isString }?.content
    }

    private fun JsonObject.strictBoolean(key: String): Boolean? {
        val primitive = this[key] as? JsonPrimitive ?: return null
        return primitive.takeUnless { it.isString }?.booleanOrNull
    }

    private fun canonicalPayload(action: String, extras: Map<String, Any?>): ByteArray {
        return buildString {
            append("schema:")
            append(Plugin.SCHEMA_VERSION)
            appendField("action", action)
            extras.toSortedMap().forEach { (key, value) ->
                appendField(
                    key,
                    when (value) {
                        is String -> "s:$value"
                        is Boolean -> "b:${if (value) 1 else 0}"
                        else -> error("Unsupported Tasker extra type")
                    },
                )
            }
        }.toByteArray(UTF_8)
    }

    private fun StringBuilder.appendField(key: String, value: String) {
        append('|')
        append(key.length)
        append(':')
        append(key)
        append('=')
        append(value.length)
        append(':')
        append(value)
    }

    private fun hmac(secret: ByteArray, payload: ByteArray): ByteArray {
        return Mac.getInstance(HMAC_ALGORITHM).run {
            init(SecretKeySpec(secret, HMAC_ALGORITHM))
            doFinal(payload)
        }
    }

    private fun rejectUnexpectedExtras(
        extras: Map<String, Any?>,
        allowed: Set<String>,
    ): ValidationResult.Reject? {
        val unexpected = extras.keys - allowed
        if (unexpected.isEmpty()) return null
        return ValidationResult.Reject("unexpected Tasker extras")
    }

    private sealed class ParsedPluginPayload {
        data class Accept(
            val action: TaskerPluginAction,
            val authTag: ByteArray,
        ) : ParsedPluginPayload()

        data class Reject(val reason: String) : ParsedPluginPayload()
    }
}

internal data class TaskerPluginAction(
    val action: String,
    val extras: Map<String, Any?>,
)

internal sealed class PluginAuthenticationResult {
    data class Accept(val action: TaskerPluginAction) : PluginAuthenticationResult()
    data class Reject(val reason: String) : PluginAuthenticationResult()
}

sealed class ValidationResult {
    object Accept : ValidationResult()
    data class Reject(val reason: String) : ValidationResult()
}

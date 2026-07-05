/*
 * Copyright (C) 2026 SwiftFloris Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.lib.util

import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.text.InputType
import android.view.inputmethod.EditorInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.Serializable

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class DebugSummarizeUtilsTest {

    @Test
    fun editorInfoExtrasSummarizeSafePrimitiveValuesAndRedactRawContent() {
        val extras = Bundle().apply {
            putBoolean("boolean", true)
            putInt("count", 7)
            putLong("epoch", 123_456L)
            putFloat("score", 0.75f)
            putString("rawString", "hunter2")
            putCharSequence("typedContent", "draft body")
            putStringArray("labels", arrayOf("private label", "second label"))
            putCharSequenceArray("typedArray", arrayOf<CharSequence>("typed one", "typed two"))
            putIntArray("numbers", intArrayOf(1, 2, 3))
            putParcelable("uri", Uri.parse("content://example.local/token-123"))
            putParcelable("parcelable", SecretParcelable("parcel payload"))
            putParcelableArray("parcelables", arrayOf(SecretParcelable("array payload")))
            putSerializable("serializable", SecretSerializable("serial payload"))
            putStringArrayList("list", arrayListOf("list secret", "list second"))
            putBinder("binder", Binder())
            putBundle(
                "nested",
                Bundle().apply {
                    putInt("depth", 2)
                    putString("rawNested", "nested typed words")
                },
            )
        }

        val summary = editorInfoWithExtras(extras).debugSummarize()

        summary shouldContain "boolean=Boolean(true)"
        summary shouldContain "count=Int(7)"
        summary shouldContain "epoch=Long(123456)"
        summary shouldContain "score=Float(0.75)"
        summary shouldContain "rawString=String(<redacted>, length=7)"
        summary shouldContain "typedContent=String(<redacted>, length=10)"
        summary shouldContain "labels=Array<String>(size=2)"
        summary shouldContain "typedArray=Array<CharSequence>(size=2)"
        summary shouldContain "numbers=IntArray(size=3)"
        summary shouldContain "uri=Uri(<redacted>)"
        summary shouldContain "parcelable=SecretParcelable(Parcelable)"
        summary shouldContain "parcelables=Array<SecretParcelable>(size=1)"
        summary shouldContain "serializable=SecretSerializable(Serializable)"
        summary shouldContain "list=ArrayList(size=2, elements=String)"
        summary shouldContain "binder="
        summary shouldContain "(<unsupported>)"
        summary shouldContain "nested=[depth=Int(2), rawNested=String(<redacted>, length=18)]"
        summary shouldNotContain "hunter2"
        summary shouldNotContain "draft body"
        summary shouldNotContain "private label"
        summary shouldNotContain "typed one"
        summary shouldNotContain "token-123"
        summary shouldNotContain "parcel payload"
        summary shouldNotContain "array payload"
        summary shouldNotContain "serial payload"
        summary shouldNotContain "list secret"
        summary shouldNotContain "nested typed words"
    }

    @Test
    fun passwordAndIncognitoEditorFieldsRedactAppSuppliedStrings() {
        val summary = EditorInfo().apply {
            packageName = "com.example.login"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            privateImeOptions = "auth_token=hunter2"
            actionLabel = "Send secret"
            hintText = "Password for bank"
            extras = Bundle().apply {
                putString("typedContent", "typed password")
                putParcelable("callbackUri", Uri.parse("content://example.local/callback-token"))
            }
        }.debugSummarize()

        summary shouldContain "TYPE_TEXT_VARIATION_PASSWORD"
        summary shouldContain "IME_FLAG_NO_PERSONALIZED_LEARNING"
        summary shouldContain "privateImeOptions: String(<redacted>, length=18)"
        summary shouldContain "actionLabel: String(<redacted>, length=11)"
        summary shouldContain "hintText: String(<redacted>, length=17)"
        summary shouldContain "typedContent=String(<redacted>, length=14)"
        summary shouldContain "callbackUri=Uri(<redacted>)"
        summary shouldNotContain "auth_token"
        summary shouldNotContain "hunter2"
        summary shouldNotContain "Send secret"
        summary shouldNotContain "Password for bank"
        summary shouldNotContain "typed password"
        summary shouldNotContain "callback-token"
    }
}

private fun editorInfoWithExtras(extras: Bundle): EditorInfo {
    return EditorInfo().apply {
        packageName = "com.example.editor"
        this.extras = extras
    }
}

private data class SecretSerializable(
    val payload: String,
) : Serializable

private class SecretParcelable(
    private val payload: String,
) : Parcelable {
    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(payload)
    }

    companion object CREATOR : Parcelable.Creator<SecretParcelable> {
        override fun createFromParcel(source: Parcel): SecretParcelable {
            return SecretParcelable(source.readString().orEmpty())
        }

        override fun newArray(size: Int): Array<SecretParcelable?> {
            return arrayOfNulls(size)
        }
    }
}

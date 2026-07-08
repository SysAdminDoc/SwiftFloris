/*
 * Copyright (C) 2026 SwiftFloris Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.ime.clipboard

import android.content.ClipDescription.EXTRA_IS_SENSITIVE
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardItem
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ClipboardItemClipDataTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun sensitiveTextClipDataCarriesAndroidSensitiveFlag() {
        val clipData = ClipboardItem.text("123456").toClipData(context)

        clipData.description.extras?.getBoolean(EXTRA_IS_SENSITIVE) shouldBe true
    }

    @Test
    fun nonSensitiveTextClipDataDoesNotSetSensitiveFlag() {
        val clipData = ClipboardItem.text("ordinary note").toClipData(context)

        clipData.description.extras?.getBoolean(EXTRA_IS_SENSITIVE) shouldBe null
    }
}

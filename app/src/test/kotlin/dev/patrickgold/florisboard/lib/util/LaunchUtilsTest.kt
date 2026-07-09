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

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.matchers.string.shouldContain
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class LaunchUtilsTest {

    private val appContext: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun launchUrlShowsToastWhenAndroidRejectsIntent() {
        val context = ThrowingStartActivityContext(appContext, SecurityException("blocked"))

        context.launchUrl("https://example.com")

        ShadowToast.getTextOfLatestToast().toString() shouldContain
            "No browser app found for handling URL https://example.com"
    }

    @Test
    fun launchActivityShowsToastWhenAndroidRejectsIntent() {
        val context = ThrowingStartActivityContext(appContext, SecurityException("blocked"))

        context.launchActivity { intent ->
            intent.action = Intent.ACTION_VIEW
        }

        ShadowToast.getTextOfLatestToast().toString() shouldContain
            "Could not open the requested screen."
    }
}

private class ThrowingStartActivityContext(
    base: Context,
    private val throwable: RuntimeException,
) : ContextWrapper(base) {
    override fun startActivity(intent: Intent?) {
        throw throwable
    }

    override fun startActivity(intent: Intent?, options: Bundle?) {
        throw throwable
    }
}

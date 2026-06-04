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

package dev.patrickgold.florisboard.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.patrickgold.florisboard.lib.crashutility.CrashDialogActivity
import dev.patrickgold.florisboard.lib.crashutility.CrashUtility
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class StartupCrashRecoveryTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clearCrashState()
    }

    @After
    fun tearDown() {
        clearCrashState()
    }

    @Test
    fun consumeStagedExceptionPersistsStacktraceAndClearsStage() {
        CrashUtility.stageException(IllegalStateException("startup init failed"))

        CrashUtility.consumeStagedException(context) shouldBe true
        CrashUtility.hasUnhandledStacktraceFiles(context) shouldBe true

        val stacktraces = CrashUtility.getUnhandledStacktraces(context)
        stacktraces shouldHaveSize 1
        stacktraces.single().details shouldContain "startup init failed"

        CrashUtility.consumeStagedException(context) shouldBe false
    }

    @Test
    fun stagedStartupCrashIntentTargetsCrashDialogAndPersistsStacktrace() {
        CrashUtility.stageException(IllegalArgumentException("activity init failed"))

        val intent = stagedStartupCrashIntent(context).shouldNotBeNull()

        intent.component?.className shouldBe CrashDialogActivity::class.java.name
        CrashUtility.hasUnhandledStacktraceFiles(context) shouldBe true
    }

    @Test
    fun stagedStartupCrashIntentIsNullWithoutStagedException() {
        stagedStartupCrashIntent(context) shouldBe null
        CrashUtility.hasUnhandledStacktraceFiles(context) shouldBe false
    }

    private fun clearCrashState() {
        CrashUtility.consumeStagedException(context)
        CrashUtility.getUnhandledStacktraces(context)
    }
}

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

package dev.patrickgold.florisboard.app.settings

import android.content.ClipboardManager
import android.content.pm.Signature
import android.content.pm.SigningInfo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.patrickgold.florisboard.BuildConfig
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.AppTheme
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.apptheme.FlorisAppTheme
import dev.patrickgold.florisboard.app.settings.about.AboutScreen
import dev.patrickgold.florisboard.app.settings.privacy.PrivacyAuditScreen
import dev.patrickgold.florisboard.app.settings.sync.SyncQrPayloadCard
import dev.patrickgold.florisboard.ime.clipboard.SwitchableClipboardShadow
import dev.patrickgold.florisboard.ime.smartcompose.AddonAuditExport
import dev.patrickgold.florisboard.ime.smartcompose.AddonInvocationAudit
import dev.patrickgold.florisboard.lib.crashutility.CrashReportEnvironment
import dev.patrickgold.florisboard.lib.crashutility.CrashReportFormatter
import dev.patrickgold.florisboard.screenshot.RoborazziHostActivity
import dev.patrickgold.jetpref.datastore.ui.ProvideDefaultDialogPrefStrings
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import java.security.MessageDigest
import org.florisboard.lib.android.stringRes
import org.florisboard.lib.compose.ProvideLocalizedResources
import org.florisboard.lib.compose.stringRes
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowSigningInfo
import org.robolectric.shadows.ShadowToast

/**
 * The privacy audit's copy said it worked even when the clipboard threw, the signing fingerprint's copy said
 * nothing at all, and the pairing payload's copy had no guard. Each copy button here runs against a clipboard
 * that throws, then one that works: the first click gets the failure toast and only that, the second copies the
 * exact text and confirms once.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xxhdpi", sdk = [35], shadows = [SwitchableClipboardShadow::class])
class TrustCopyFeedbackTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<RoborazziHostActivity>()

    private val context get() = composeRule.activity

    @Before
    fun setUp() {
        SwitchableClipboardShadow.failure = null
    }

    @After
    fun tearDown() {
        SwitchableClipboardShadow.failure = null
        AddonInvocationAudit.clear()
    }

    @Test
    fun theAuditLogCopyOnlyClaimsSuccessWhenItCopied() {
        AddonInvocationAudit.clear()
        AddonInvocationAudit.record(
            surface = AddonInvocationAudit.Surface.MCP,
            outcome = AddonInvocationAudit.Outcome.ACCEPTED,
            subject = "dev.swiftfloris.mcp.calendar::calendar.next_event",
            timestampMillis = 1_720_000_000_000L,
        )
        val expected = AddonAuditExport.toJsonString(records = AddonInvocationAudit.snapshot())
        show { PrivacyAuditScreen() }

        checkCopy(
            button = context.getString(R.string.settings__privacy_audit__export),
            payload = expected,
            copied = context.getString(R.string.settings__privacy_audit__export_toast),
            failed = context.getString(R.string.settings__privacy_audit__export_failed),
            // The export stamps the moment it was made, so that field is the one part allowed to differ.
            normalize = { it.replace(ExportedAt, "") },
        )
    }

    @Test
    fun theSigningFingerprintCopySaysWhetherItCopied() {
        val certificate = byteArrayOf(0x30, 0x82.toByte(), 0x01, 0x0a, 0x02, 0x01, 0x07)
        // Robolectric keeps a SigningInfo's signers in its shadow, so they go in there.
        val signingInfo = SigningInfo()
        Shadow.extract<ShadowSigningInfo>(signingInfo).setSignatures(arrayOf(Signature(certificate)))
        shadowOf(context.packageManager).getInternalMutablePackageInfo(context.packageName).signingInfo = signingInfo
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(certificate)
            .joinToString(":") { "%02X".format(it.toInt() and 0xff) }
        show { AboutScreen() }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasText(fingerprint), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        checkCopy(
            button = context.getString(R.string.about__signing_fingerprint__title),
            payload = fingerprint,
            copied = context.getString(R.string.about__signing_fingerprint__copied),
            failed = context.getString(R.string.about__signing_fingerprint__copy_failed),
        )
    }

    @Test
    fun theProblemReportCopySaysWhetherItCopied() {
        val report = CrashReportFormatter.formatProblemReport(
            CrashReportEnvironment.current(
                context = context,
                versionNameMarkdown = context.stringRes(
                    R.string.florisboard__changelog_url,
                    "version" to BuildConfig.VERSION_NAME,
                ).let { url -> "[${BuildConfig.VERSION_NAME}]($url)" },
            ),
        )
        show { AboutScreen() }

        checkCopy(
            button = context.getString(R.string.about__report_problem__title),
            payload = report,
            copied = context.getString(R.string.about__report_problem__copied),
            failed = context.getString(R.string.about__report_problem__copy_failed),
        )
    }

    @Test
    fun thePairingPayloadCopySaysWhetherItCopied() {
        val payload = """{"schema":1,"clusterId":"c","deviceId":"d","pubkeyHex":"${"ab".repeat(32)}"}"""
        show { SyncQrPayloadCard(payload) }

        checkCopy(
            button = context.getString(R.string.settings__sync__copy_pairing_payload),
            payload = payload,
            copied = context.getString(R.string.settings__sync__pairing_payload_copied),
            failed = context.getString(R.string.settings__sync__pairing_payload_copy_failed),
        )
    }

    @Test
    fun theVersionCopyKeepsItsErrorDetails() {
        show { AboutScreen() }
        val button = context.getString(R.string.about__version__title)

        SwitchableClipboardShadow.failure = IllegalStateException("clipboard service is gone")
        ShadowToast.reset()
        composeRule.onNodeWithText(button).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        ShadowToast.shownToastCount() shouldBe 1
        ShadowToast.getTextOfLatestToast().toString() shouldStartWith
            context.getString(R.string.about__version_copied__error).substringBefore("{")

        SwitchableClipboardShadow.failure = null
        ShadowToast.reset()
        composeRule.onNodeWithText(button).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        ShadowToast.shownToastCount() shouldBe 1
        ShadowToast.getTextOfLatestToast().toString() shouldBe context.getString(R.string.about__version_copied__title)
    }

    private fun checkCopy(
        button: String,
        payload: String,
        copied: String,
        failed: String,
        normalize: (String) -> String = { it },
    ) {
        withClue("with a clipboard that throws") {
            SwitchableClipboardShadow.failure = IllegalStateException("clipboard service is gone")
            ShadowToast.reset()
            composeRule.onNodeWithText(button).performSemanticsAction(SemanticsActions.OnClick)
            composeRule.waitForIdle()
            ShadowToast.shownToastCount() shouldBe 1
            ShadowToast.getTextOfLatestToast().toString() shouldBe failed
        }
        withClue("with a working clipboard") {
            SwitchableClipboardShadow.failure = null
            ShadowToast.reset()
            composeRule.onNodeWithText(button).performSemanticsAction(SemanticsActions.OnClick)
            composeRule.waitForIdle()
            ShadowToast.shownToastCount() shouldBe 1
            ShadowToast.getTextOfLatestToast().toString() shouldBe copied
            val clip = context.getSystemService(ClipboardManager::class.java).primaryClip
            clip?.getItemAt(0)?.text?.toString()?.let(normalize) shouldBe normalize(payload)
        }
    }

    private fun show(content: @Composable () -> Unit) {
        composeRule.setContent {
            ProvideLocalizedResources(resourcesContext = LocalContext.current, appName = R.string.app_name) {
                FlorisAppTheme(theme = AppTheme.LIGHT) {
                    CompositionLocalProvider(LocalNavController provides rememberNavController()) {
                        ProvideDefaultDialogPrefStrings(
                            confirmLabel = stringRes(R.string.action__ok),
                            dismissLabel = stringRes(R.string.action__cancel),
                            neutralLabel = stringRes(R.string.action__default),
                        ) {
                            Surface(color = MaterialTheme.colorScheme.background) {
                                Box(modifier = Modifier.size(width = 360.dp, height = 640.dp)) {
                                    content()
                                }
                            }
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private companion object {
        val ExportedAt = Regex(""""exportedAtIso":"[^"]*"""")
    }
}

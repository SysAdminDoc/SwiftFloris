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

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Parcel
import androidx.core.content.IntentCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Plays the activity's side: a fresh ledger per activity instance, restored from the bundle the
 * previous instance saved. A rotation hands the new instance the same intent object. A process
 * death sends the saved bundle through a Parcel and hands the new instance a copy of the launch
 * intent the system kept, without the token.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ExternalIntentLedgerTest {

    private fun view() = Intent(Intent.ACTION_VIEW, Uri.parse("content://files/theme.flex"))

    private fun send(name: String = "pack.zip") = Intent(Intent.ACTION_SEND)
        .putExtra(Intent.EXTRA_STREAM, Uri.parse("content://files/$name"))

    private fun language() = Intent().setComponent(
        ComponentName("dev.patrickgold.florisboard", FlorisAppActivity.ImeLanguageSettingsAliasName),
    )

    private fun launcher() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

    /** What the system keeps: the launch intent as sent, without the token the app added. */
    private fun systemCopy(sent: Intent) = Intent(sent).apply { removeExtra(TOKEN) }

    private fun rotate(previous: ExternalIntentLedger): ExternalIntentLedger {
        val saved = Bundle().also { previous.save(it) }
        return ExternalIntentLedger().also { it.restore(saved) }
    }

    private fun afterProcessDeath(previous: ExternalIntentLedger): ExternalIntentLedger {
        val parcel = Parcel.obtain()
        try {
            parcel.writeBundle(Bundle().also { previous.save(it) })
            parcel.setDataPosition(0)
            val saved = parcel.readBundle(javaClass.classLoader)
            return ExternalIntentLedger().also { it.restore(saved) }
        } finally {
            parcel.recycle()
        }
    }

    private fun ExternalIntentLedger.startWith(intent: Intent) = offer(intent, isLaunch = true, isRequest = intent.isRequest())

    private fun ExternalIntentLedger.deliver(intent: Intent) = offer(intent, isLaunch = false, isRequest = intent.isRequest())

    private fun Intent.isRequest() = action != Intent.ACTION_MAIN

    private val Intent.token: String?
        get() = getStringExtra(TOKEN)

    private fun eachKind(check: (sent: () -> Intent) -> Unit) {
        check(::view)
        check { send() }
        check(::language)
    }

    @Test
    fun aRequestHandledBeforeARotationIsNotHandledAgain() = eachKind { sent ->
        val launch = sent()
        val first = ExternalIntentLedger()
        first.startWith(launch)
        first.pending shouldBe launch
        first.markHandled(launch)
        first.pending.shouldBeNull()

        val second = rotate(first)
        second.startWith(launch)
        second.pending.shouldBeNull()
        rotate(second).apply { startWith(launch) }.pending.shouldBeNull()
    }

    @Test
    fun aRequestWaitingWhenTheActivityRotatesIsHandledOnceAfter() = eachKind { sent ->
        val launch = sent()
        val first = ExternalIntentLedger()
        first.startWith(launch)

        val second = rotate(first)
        second.startWith(launch)
        val waiting = second.pending.shouldNotBeNull()
        waiting.token shouldBe launch.token
        second.markHandled(waiting)
        second.pending.shouldBeNull()
        rotate(second).apply { startWith(launch) }.pending.shouldBeNull()
    }

    @Test
    fun aHandledLaunchRequestStaysHandledAfterProcessDeath() = eachKind { sent ->
        val original = sent()
        val launch = Intent(original)
        val first = ExternalIntentLedger()
        first.startWith(launch)
        first.markHandled(launch)

        val restored = systemCopy(original)
        restored.hasExtra(TOKEN) shouldBe false
        val second = afterProcessDeath(first)
        second.startWith(restored)
        second.pending.shouldBeNull()
        afterProcessDeath(second).apply { startWith(systemCopy(original)) }.pending.shouldBeNull()
    }

    @Test
    fun aLaunchRequestWaitingAtProcessDeathIsHandledOnceAfter() = eachKind { sent ->
        val original = sent()
        val first = ExternalIntentLedger()
        first.startWith(Intent(original))

        val second = afterProcessDeath(first)
        second.startWith(systemCopy(original))
        val waiting = second.pending.shouldNotBeNull()
        waiting.filterEquals(original) shouldBe true
        second.markHandled(waiting)
        second.pending.shouldBeNull()
        afterProcessDeath(second).apply { startWith(systemCopy(original)) }.pending.shouldBeNull()
    }

    @Test
    fun aDeliveredRequestWaitingAtProcessDeathIsHandledAfter() {
        val launch = view()
        val first = ExternalIntentLedger()
        first.startWith(launch)
        first.markHandled(launch)
        val next = send("later.zip")
        first.deliver(next)

        // The system only replays the launch intent, so the delivered one must come from the bundle.
        val second = afterProcessDeath(first)
        second.startWith(systemCopy(launch))
        val waiting = second.pending.shouldNotBeNull()
        waiting.token shouldBe next.token
        IntentCompat.getParcelableExtra(waiting, Intent.EXTRA_STREAM, Uri::class.java) shouldBe
            Uri.parse("content://files/later.zip")
        second.markHandled(waiting)
        afterProcessDeath(second).apply { startWith(systemCopy(launch)) }.pending.shouldBeNull()
    }

    @Test
    fun aLauncherTapLeavesTheWaitingRequestAlone() {
        val launch = send()
        val ledger = ExternalIntentLedger()
        ledger.startWith(launch)
        ledger.deliver(launcher())
        ledger.pending shouldBe launch

        val restored = afterProcessDeath(ledger)
        restored.startWith(systemCopy(launch))
        restored.pending.shouldNotBeNull().token shouldBe launch.token
    }

    @Test
    fun aLauncherStartHasNothingToHandle() {
        val start = launcher()
        val ledger = ExternalIntentLedger()
        ledger.startWith(start)
        ledger.pending.shouldBeNull()
        afterProcessDeath(ledger).apply { startWith(systemCopy(start)) }.pending.shouldBeNull()
    }

    @Test
    fun aNewerRequestReplacesTheWaitingOne() = eachKind { sent ->
        val original = sent()
        val first = ExternalIntentLedger()
        first.startWith(Intent(original))
        val next = sent()
        first.deliver(next)
        first.pending shouldBe next

        // The replaced launch request doesn't come back after process death, the newer one does.
        val second = afterProcessDeath(first)
        second.startWith(systemCopy(original))
        second.pending.shouldNotBeNull().token shouldBe next.token
    }

    @Test
    fun theSameRequestSentAgainIsNewEachTime() = eachKind { sent ->
        val ledger = ExternalIntentLedger()
        val launch = sent()
        ledger.startWith(launch)
        ledger.markHandled(launch)

        repeat(3) {
            val again = sent()
            ledger.deliver(again)
            ledger.pending shouldBe again
            ledger.markHandled(again)
        }
    }

    @Test
    fun aSenderCannotMarkANewRequestAsAlreadyHandled() {
        val ledger = ExternalIntentLedger()
        val launch = view()
        ledger.startWith(launch)
        ledger.markHandled(launch)
        val stolen = launch.token

        val forged = send().putExtra(TOKEN, stolen)
        ledger.deliver(forged)
        ledger.pending shouldBe forged
        (forged.token == stolen) shouldBe false
        // After onNewIntent, getIntent() is the forged intent, so a rotation offers it as the launch.
        rotate(ledger).apply { startWith(forged) }.pending?.token shouldBe forged.token
    }

    @Test
    fun aDeliveredRequestHandledBeforeARotationIsNotHandledAgain() = eachKind { sent ->
        val ledger = ExternalIntentLedger()
        ledger.startWith(launcher())
        val delivered = sent()
        ledger.deliver(delivered)
        ledger.markHandled(delivered)

        // onNewIntent called setIntent, so the recreated activity starts with the delivered intent.
        rotate(ledger).apply { startWith(delivered) }.pending.shouldBeNull()
    }

    @Test
    fun aDeliveredRequestWaitingAtARotationIsHandledOnce() = eachKind { sent ->
        val ledger = ExternalIntentLedger()
        ledger.startWith(launcher())
        val delivered = sent()
        ledger.deliver(delivered)

        val second = rotate(ledger)
        second.startWith(delivered)
        second.pending?.token shouldBe delivered.token
        second.markHandled(second.pending!!)
        second.pending.shouldBeNull()
        rotate(second).apply { startWith(delivered) }.pending.shouldBeNull()
    }

    @Test
    fun theSavedStateKeepsOnlyTokensThatCanComeBack() {
        val ledger = ExternalIntentLedger()
        val launch = view()
        ledger.startWith(launch)
        ledger.markHandled(launch)
        repeat(20) {
            val next = send()
            ledger.deliver(next)
            ledger.markHandled(next)
        }

        val saved = Bundle().also { ledger.save(it) }
        saved.getStringArrayList("swiftfloris.app.handled_intent_tokens")!!.size shouldBe 2
    }
}

/** The extra the ledger tags intents with. */
private const val TOKEN = "swiftfloris.app.external_intent_token"

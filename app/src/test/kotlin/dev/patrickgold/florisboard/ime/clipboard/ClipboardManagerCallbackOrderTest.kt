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

package dev.patrickgold.florisboard.ime.clipboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.patrickgold.florisboard.FlorisApplication
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.clipboardManager
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardItem
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardMediaProvider
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

/**
 * Drives the app's own ClipboardManager through the system clipboard, with the media provider
 * swapped for one that can hold a clone mid-copy. History stays off (the default) unless a case
 * turns it on, so most cases check the primary clip and the media files, not history rows.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [SwitchableClipboardShadow::class])
class ClipboardManagerCallbackOrderTest {
    private val prefs by FlorisPreferenceStore
    private lateinit var media: HoldingMediaProvider
    private lateinit var system: android.content.ClipboardManager
    private lateinit var manager: ClipboardManager
    private lateinit var context: Context
    private var changedPrefs = false

    @Before
    fun setUp() {
        media = Robolectric.setupContentProvider(HoldingMediaProvider::class.java, ClipboardMediaProvider.AUTHORITY)
        context = ApplicationProvider.getApplicationContext()
        system = context.getSystemService(android.content.ClipboardManager::class.java)
        manager = context.clipboardManager().value
    }

    @After
    fun tearDown() {
        SwitchableClipboardShadow.failure = null
        media.releaseEverything()
        if (changedPrefs) {
            // The preference model outlives this test's app, so the defaults go back. The internal
            // clipboard changes the most for later tests, so it goes first.
            try {
                setUseInternalClipboard(false)
            } finally {
                try {
                    setSyncToFloris(ClipboardSyncBehavior.ALL_EVENTS)
                } finally {
                    setHistoryEnabled(false)
                }
            }
        }
    }

    @Test
    fun aSlowImageCopyFinishingAfterANewerTextCopyIsDroppedWithItsFile() {
        media.holdNextInsert()
        system.setPrimaryClip(imageClip())
        media.awaitHeldInsert()

        system.setPrimaryClip(ClipData.newPlainText("", "newer text"))
        waitUntil { manager.primaryClip?.text == "newer text" }
        media.releaseHeldInsert()
        waitUntil { media.deleted.isNotEmpty() }

        manager.primaryClip?.text shouldBe "newer text"
        media.deleted shouldContainExactly media.inserted
    }

    @Test
    fun theSameClipArrivingTwiceKeepsTheCopyStillCloning() {
        media.holdNextInsert()
        system.setPrimaryClip(imageClip())
        media.awaitHeldInsert()

        // Some phones report one copy twice. The repeat publishes nothing. It finishes before the
        // image does, so a repeat that claimed its revision would make it lose.
        manager.onPrimaryClipChanged()
        waitUntil { manager.clipboardCallbacksInFlight == 1 }
        media.releaseHeldInsert()
        waitUntil { manager.clipboardCallbacksInFlight == 0 }

        media.deleted.shouldBeEmpty()
        manager.primaryClip?.uri shouldBe media.inserted.single()
    }

    @Test
    fun aClearTheSyncSettingIgnoresKeepsTheCopyStillCloning() {
        setSyncToFloris(ClipboardSyncBehavior.ONLY_SET_EVENTS)
        media.holdNextInsert()
        system.setPrimaryClip(imageClip())
        media.awaitHeldInsert()

        system.setPrimaryClip(ClipData(ClipDescription("", arrayOf("text/plain")), ClipData.Item(null as CharSequence?)))
        // The ignored clear finishes before the image does, so claiming would make it lose.
        waitUntil { manager.clipboardCallbacksInFlight == 1 }
        media.releaseHeldInsert()
        waitUntil { manager.clipboardCallbacksInFlight == 0 }

        media.deleted.shouldBeEmpty()
        manager.primaryClip?.uri shouldBe media.inserted.single()
    }

    @Test
    fun aCopyThatFailedToImportIsImportedWhenCopiedAgain() {
        media.refuseNextInsert()
        system.setPrimaryClip(imageClip())
        waitUntil { manager.clipboardCallbacksInFlight == 0 }
        media.refusedInserts.get() shouldBe 1

        system.setPrimaryClip(imageClip())
        waitUntil { manager.clipboardCallbacksInFlight == 0 }

        manager.primaryClip?.uri shouldBe media.inserted.single()
        media.deleted.shouldBeEmpty()
    }

    @Test
    fun aCopyRepeatedWhileItFailsToImportIsImportedWhenCopiedAgain() {
        media.holdNextInsert()
        media.refuseNextInsert()
        system.setPrimaryClip(imageClip())
        media.awaitHeldInsert()

        // An equal copy arrives while the first is still reading, so it's a repeat and becomes
        // the remembered clip. The first then fails, and that mustn't make the next copy a repeat.
        system.setPrimaryClip(imageClip())
        waitUntil { manager.clipboardCallbacksInFlight == 1 }
        media.releaseHeldInsert()
        waitUntil { manager.clipboardCallbacksInFlight == 0 }
        media.refusedInserts.get() shouldBe 1

        system.setPrimaryClip(imageClip())
        waitUntil { manager.clipboardCallbacksInFlight == 0 }

        manager.primaryClip?.uri shouldBe media.inserted.single()
        media.deleted.shouldBeEmpty()
    }

    @Test
    fun aCopyThatLostToAKeyboardChangeIsImportedWhenCopiedAgain() {
        // With the internal clipboard on and sync to system off (the default), a clip the
        // keyboard sets stays in the keyboard and never comes back as a callback.
        setUseInternalClipboard(true)
        media.holdNextInsert()
        system.setPrimaryClip(imageClip())
        media.awaitHeldInsert()
        manager.updatePrimaryClip(ClipboardItem.text("from the keyboard"))
        system.primaryClip?.getItemAt(0)?.uri shouldBe imageClip().getItemAt(0).uri
        media.releaseHeldInsert()
        waitUntil { manager.clipboardCallbacksInFlight == 0 }
        manager.primaryClip?.text shouldBe "from the keyboard"
        media.deleted shouldContainExactly media.inserted

        system.setPrimaryClip(imageClip())
        waitUntil { manager.clipboardCallbacksInFlight == 0 }

        manager.primaryClip?.uri shouldBe media.inserted.last()
        media.inserted.size shouldBe 2
    }

    @Test
    fun aRepeatWhileTheCopyIsStillCloningOutranksAKeyboardChangeBeforeIt() {
        setUseInternalClipboard(true)
        media.holdNextInsert()
        system.setPrimaryClip(imageClip())
        media.awaitHeldInsert()
        manager.updatePrimaryClip(ClipboardItem.text("from the keyboard"))
        system.primaryClip?.getItemAt(0)?.uri shouldBe imageClip().getItemAt(0).uri

        // The user copies the image again after the keyboard's change. That copy hands its
        // revision to the one still cloning, which then publishes as the newest.
        system.setPrimaryClip(imageClip())
        waitUntil { manager.clipboardCallbacksInFlight == 1 }
        media.releaseHeldInsert()
        waitUntil { manager.clipboardCallbacksInFlight == 0 }

        manager.primaryClip?.uri shouldBe media.inserted.single()
        media.deleted.shouldBeEmpty()
    }

    @Test
    fun anOlderCopyReachingTheRepeatCheckAfterANewerClearLeavesItAlone() {
        val stalled = StalledClipData(imageClip())
        media.holdNextInsert()
        system.setPrimaryClip(stalled)
        stalled.awaitStalled()
        system.setPrimaryClip(emptyClip())
        waitUntil { manager.clipboardCallbacksInFlight == 1 }

        // The image copy gets to the repeat check only after the clear, then clones. Copying the
        // image again is a new copy, since the clear came in between, so it's imported and wins.
        stalled.resume()
        media.awaitHeldInsert()
        system.setPrimaryClip(imageClip())
        waitUntil { manager.clipboardCallbacksInFlight == 1 }
        media.releaseHeldInsert()
        waitUntil { manager.clipboardCallbacksInFlight == 0 }

        media.inserted.size shouldBe 2
        manager.primaryClip?.uri shouldBe media.inserted.first()
        media.deleted shouldContainExactly listOf(media.inserted.last())
    }

    @Test
    fun aClearTheSyncSettingIgnoresLeavesARepeatsHandOffInPlace() {
        setUseInternalClipboard(true)
        setSyncToFloris(ClipboardSyncBehavior.ONLY_SET_EVENTS)
        media.holdNextInsert()
        system.setPrimaryClip(imageClip())
        media.awaitHeldInsert()
        manager.updatePrimaryClip(ClipboardItem.text("from the keyboard"))
        system.primaryClip?.getItemAt(0)?.uri shouldBe imageClip().getItemAt(0).uri
        system.setPrimaryClip(imageClip())
        waitUntil { manager.clipboardCallbacksInFlight == 1 }

        // Another app clears the clipboard, which this sync setting ignores, so the repeat's
        // revision still goes to the copy that's cloning.
        system.setPrimaryClip(emptyClip())
        waitUntil { manager.clipboardCallbacksInFlight == 1 }
        media.releaseHeldInsert()
        waitUntil { manager.clipboardCallbacksInFlight == 0 }

        manager.primaryClip?.uri shouldBe media.inserted.single()
        media.deleted.shouldBeEmpty()
    }

    @Test
    fun aClearThatRunsLateStillAppliesAfterARepeatedCopyFails() {
        system.setPrimaryClip(ClipData.newPlainText("", "secret"))
        waitUntil { manager.primaryClip?.text == "secret" }
        media.holdNextInsert()
        media.refuseNextInsert()
        system.setPrimaryClip(imageClip())
        media.awaitHeldInsert()
        val clear = StalledClipData(emptyClip())
        system.setPrimaryClip(clear)
        clear.awaitStalled()

        // The image copied again after the clear defers to the copy still cloning, which then
        // fails. The clear runs last but came first, so it still empties the keyboard's clip.
        system.setPrimaryClip(imageClip())
        waitUntil { manager.clipboardCallbacksInFlight == 2 }
        media.releaseHeldInsert()
        waitUntil { manager.clipboardCallbacksInFlight == 1 }
        clear.resume()
        waitUntil { manager.clipboardCallbacksInFlight == 0 }

        media.refusedInserts.get() shouldBe 1
        manager.primaryClip shouldBe null
    }

    @Test
    fun aClearThatRunsLateStillAppliesAfterANewerCopyFails() {
        system.setPrimaryClip(ClipData.newPlainText("", "secret"))
        waitUntil { manager.primaryClip?.text == "secret" }
        val clear = StalledClipData(emptyClip())
        system.setPrimaryClip(clear)
        clear.awaitStalled()

        // The image copied after the clear can't be read. The clear still has to empty the
        // keyboard's clip when it gets there.
        media.refuseNextInsert()
        system.setPrimaryClip(imageClip())
        waitUntil { manager.clipboardCallbacksInFlight == 1 }
        media.refusedInserts.get() shouldBe 1
        manager.primaryClip?.text shouldBe "secret"
        clear.resume()
        waitUntil { manager.clipboardCallbacksInFlight == 0 }

        manager.primaryClip shouldBe null
    }

    @Test
    fun aClearLosesToTheSameClipCopiedAgainAfterItWhenANewerCopyFails() {
        system.setPrimaryClip(ClipData.newPlainText("", "p"))
        waitUntil { manager.primaryClip?.text == "p" }
        val clear = StalledClipData(emptyClip())
        system.setPrimaryClip(clear)
        clear.awaitStalled()
        val again = StalledClipData(ClipData.newPlainText("", "p"))
        system.setPrimaryClip(again)
        again.awaitStalled()

        // The user copies p again after the clear, then an image that can't be read. The copy of
        // p gets to its claim only after that failure, and the clear runs last.
        media.refuseNextInsert()
        system.setPrimaryClip(imageClip())
        waitUntil { manager.clipboardCallbacksInFlight == 2 }
        media.refusedInserts.get() shouldBe 1
        again.resume()
        waitUntil { manager.clipboardCallbacksInFlight == 1 }
        clear.resume()
        waitUntil { manager.clipboardCallbacksInFlight == 0 }

        manager.primaryClip?.text shouldBe "p"
    }

    @Test
    fun anOlderCopyStillLandsWhenARepeatedCopyAndANewerOneBothFail() {
        system.setPrimaryClip(ClipData.newPlainText("", "a"))
        waitUntil { manager.primaryClip?.text == "a" }
        val first = media.holdInsertOf("photo.png", refuse = true)
        system.setPrimaryClip(imageClip())
        first.awaitReached()
        val older = StalledClipData(imageClip("other.png"))
        system.setPrimaryClip(older)
        older.awaitStalled()
        system.setPrimaryClip(imageClip())
        waitUntil { manager.clipboardCallbacksInFlight == 2 }

        // A newer copy takes the repeat check while the first image is still cloning. The first
        // image then fails as its repeat, and the newest copy fails too. Failed copies change
        // nothing, so the other image, copied before both, still lands.
        val newest = media.holdInsertOf("third.png", refuse = true)
        system.setPrimaryClip(imageClip("third.png"))
        newest.awaitReached()
        first.release()
        waitUntil { manager.clipboardCallbacksInFlight == 2 }
        older.resume()
        waitUntil { manager.clipboardCallbacksInFlight == 1 }
        newest.release()
        waitUntil { manager.clipboardCallbacksInFlight == 0 }

        media.refusedInserts.get() shouldBe 2
        manager.primaryClip?.uri shouldBe media.inserted.single()
        media.deleted.shouldBeEmpty()
    }

    @Test
    fun aCopyRepeatedBeforeANewerCopyTookTheRepeatCheckStillPublishesAsTheRepeat() {
        system.setPrimaryClip(ClipData.newPlainText("", "a"))
        waitUntil { manager.primaryClip?.text == "a" }
        val first = media.holdInsertOf("photo.png", refuse = false)
        system.setPrimaryClip(imageClip())
        first.awaitReached()
        val older = StalledClipData(imageClip("other.png"))
        system.setPrimaryClip(older)
        older.awaitStalled()
        system.setPrimaryClip(imageClip())
        waitUntil { manager.clipboardCallbacksInFlight == 2 }

        // Same order, but the first image imports. It publishes as its repeat, so the other image
        // loses to it, and the newest copy then fails without taking its place.
        val newest = media.holdInsertOf("third.png", refuse = true)
        system.setPrimaryClip(imageClip("third.png"))
        newest.awaitReached()
        first.release()
        waitUntil { manager.clipboardCallbacksInFlight == 2 }
        older.resume()
        waitUntil { manager.clipboardCallbacksInFlight == 1 }
        newest.release()
        waitUntil { manager.clipboardCallbacksInFlight == 0 }

        media.refusedInserts.get() shouldBe 1
        media.inserted.size shouldBe 2
        manager.primaryClip?.uri shouldBe media.inserted.first()
        media.deleted shouldContainExactly listOf(media.inserted.last())
    }

    @Test
    fun anOlderCopyStillLandsWhenANewerRepeatedCopyFails() {
        system.setPrimaryClip(ClipData.newPlainText("", "a"))
        waitUntil { manager.primaryClip?.text == "a" }
        media.holdNextInsert()
        media.refuseNextInsert()
        system.setPrimaryClip(imageClip())
        media.awaitHeldInsert()
        val older = StalledClipData(imageClip("other.png"))
        system.setPrimaryClip(older)
        older.awaitStalled()

        // The first image is copied again, so the copy still cloning stands for that newer copy
        // too. It then fails, which changes nothing, so the other image copied before the repeat
        // still lands, as it would if the copies had run one at a time.
        system.setPrimaryClip(imageClip())
        waitUntil { manager.clipboardCallbacksInFlight == 2 }
        media.releaseHeldInsert()
        waitUntil { manager.clipboardCallbacksInFlight == 1 }
        media.refusedInserts.get() shouldBe 1
        older.resume()
        waitUntil { manager.clipboardCallbacksInFlight == 0 }

        manager.primaryClip?.uri shouldBe media.inserted.single()
        media.deleted.shouldBeEmpty()
    }

    @Test
    fun anOlderCopyOfTheSameImageStillLandsWhenANewerCopyStillRunningFails() {
        system.setPrimaryClip(ClipData.newPlainText("", "a"))
        waitUntil { manager.primaryClip?.text == "a" }
        val older = StalledClipData(imageClip())
        system.setPrimaryClip(older)
        older.awaitStalled()

        // The same image copied again reaches the repeat check first, and its clone will fail.
        val newer = media.holdInsertOf("photo.png", refuse = true)
        system.setPrimaryClip(imageClip())
        newer.awaitReached()
        // The older copy arrived first, so it isn't a repeat of the newer one.
        older.resume()
        waitUntil { manager.clipboardCallbacksInFlight == 1 }
        newer.release()
        waitUntil { manager.clipboardCallbacksInFlight == 0 }

        media.refusedInserts.get() shouldBe 1
        manager.primaryClip?.uri shouldBe media.inserted.single()
        media.deleted.shouldBeEmpty()
    }

    @Test
    fun anOlderSameImageCopyThatLandsFirstIsReplacedAndItsFileDeleted() {
        system.setPrimaryClip(ClipData.newPlainText("", "a"))
        waitUntil { manager.primaryClip?.text == "a" }
        val older = StalledClipData(imageClip())
        system.setPrimaryClip(older)
        older.awaitStalled()

        val newer = media.holdInsertOf("photo.png", refuse = false)
        system.setPrimaryClip(imageClip())
        newer.awaitReached()
        // The older copy isn't a repeat of the newer one, so it clones and publishes first.
        older.resume()
        waitUntil { manager.clipboardCallbacksInFlight == 1 }
        manager.primaryClip?.uri shouldBe media.inserted.single()
        newer.release()
        waitUntil { manager.clipboardCallbacksInFlight == 0 }

        media.inserted.size shouldBe 2
        manager.primaryClip?.uri shouldBe media.inserted.last()
        media.deleted shouldContainExactly listOf(media.inserted.first())
    }

    @Test
    fun copyingTheShownImageAgainBeatsAClearThatRunsLate() {
        system.setPrimaryClip(imageClip())
        waitUntil { manager.clipboardCallbacksInFlight == 0 }
        val shown = manager.primaryClip
        val clear = StalledClipData(emptyClip())
        system.setPrimaryClip(clear)
        clear.awaitStalled()

        // The image copied again after the clear is the user's last change. Its import already
        // finished, so the repeat stands on its own and the late clear loses to it.
        system.setPrimaryClip(imageClip())
        waitUntil { manager.clipboardCallbacksInFlight == 1 }
        clear.resume()
        waitUntil { manager.clipboardCallbacksInFlight == 0 }

        manager.primaryClip shouldBeSameInstanceAs shown
        media.inserted.size shouldBe 1
        media.deleted.shouldBeEmpty()
    }

    @Test
    fun copyingTheShownImageAgainMakesAnOlderCopyStillRunningLose() {
        system.setPrimaryClip(imageClip())
        waitUntil { manager.clipboardCallbacksInFlight == 0 }
        val shown = manager.primaryClip
        val older = StalledClipData(imageClip("other.png"))
        system.setPrimaryClip(older)
        older.awaitStalled()

        system.setPrimaryClip(imageClip())
        waitUntil { manager.clipboardCallbacksInFlight == 1 }
        older.resume()
        waitUntil { manager.clipboardCallbacksInFlight == 0 }

        manager.primaryClip shouldBeSameInstanceAs shown
        media.inserted.size shouldBe 2
        media.deleted shouldContainExactly listOf(media.inserted.last())
    }

    @Test
    fun copyingTheSameTextAgainAfterTheKeyboardClearedItBringsItBack() {
        // With sync to system off (the default), the keyboard's clear leaves the system clipboard
        // holding p, so the next copy of p looks like a repeat of the copy that brought it in.
        setUseInternalClipboard(true)
        system.setPrimaryClip(ClipData.newPlainText("", "p"))
        waitUntil { manager.primaryClip?.text == "p" }
        manager.updatePrimaryClip(null)
        manager.primaryClip shouldBe null

        system.setPrimaryClip(ClipData.newPlainText("", "p"))
        waitUntil { manager.clipboardCallbacksInFlight == 0 }

        manager.primaryClip?.text shouldBe "p"
    }

    @Test
    fun copyingAnImageAgainAfterTheKeyboardSetItsOwnClipBringsItBack() {
        // The first copy finished and won before the keyboard set its own clip. The system
        // clipboard still holds the image, so the next copy of it looks like a repeat.
        setUseInternalClipboard(true)
        system.setPrimaryClip(imageClip())
        waitUntil { manager.clipboardCallbacksInFlight == 0 }
        manager.primaryClip?.uri shouldBe media.inserted.single()
        manager.updatePrimaryClip(ClipboardItem.text("from the keyboard"))
        system.primaryClip?.getItemAt(0)?.uri shouldBe imageClip().getItemAt(0).uri

        system.setPrimaryClip(imageClip())
        waitUntil { manager.clipboardCallbacksInFlight == 0 }

        manager.primaryClip?.uri shouldBe media.inserted.last()
        media.inserted.size shouldBe 2
    }

    @Test
    fun aCopyMadeAfterAClearStillLandsWhenANewerCopyFailsFirst() {
        system.setPrimaryClip(ClipData.newPlainText("", "q"))
        waitUntil { manager.primaryClip?.text == "q" }
        system.setPrimaryClip(emptyClip())
        waitUntil { manager.primaryClip == null }
        val again = StalledClipData(ClipData.newPlainText("", "q"))
        system.setPrimaryClip(again)
        again.awaitStalled()

        // An image that can't be read comes after the second copy of q and fails first.
        media.refuseNextInsert()
        system.setPrimaryClip(imageClip())
        waitUntil { manager.clipboardCallbacksInFlight == 1 }
        media.refusedInserts.get() shouldBe 1
        again.resume()
        waitUntil { manager.clipboardCallbacksInFlight == 0 }

        manager.primaryClip?.text shouldBe "q"
    }

    @Test
    fun theFinalClipIsTheLastChangeThatTookEffectWhateverOrderCallbacksFinishIn() {
        // One at a time in arrival order, every change that takes effect replaces the clip with
        // its own content, a repeat included, and a copy that can't be read changes nothing. So
        // however the callbacks interleave, the clip ends as the last change that took effect.
        // Sync to system stays off (the default), so the keyboard's own changes stay local.
        setUseInternalClipboard(true)
        media.holdEveryInsert = true
        media.unreadableSources += UNREADABLE
        var systemHolds = Effect(null)
        repeat(60) { round ->
            val random = Random(round)
            val steps = mutableListOf<String>()
            val stalls = mutableListOf<StalledClipData>()
            manager.updatePrimaryClip(ClipboardItem.text("start $round"))
            var expected: String? = "start $round"

            fun copy(clip: ClipData, effect: Effect?, name: String) {
                val stalled = random.nextBoolean()
                steps += if (stalled) "$name (stalled)" else name
                val sent = if (stalled) StalledClipData(clip).also { stalls += it } else clip
                system.setPrimaryClip(sent)
                if (sent is StalledClipData) sent.awaitStalled()
                systemHolds = effect ?: Effect(UNREADABLE_EFFECT)
                if (effect != null) expected = effect.content
            }

            // Lets the stalled callbacks and the held clones go one at a time in random order.
            // Mid-round, this makes the next repeat meet a copy that already finished.
            fun settle() {
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
                while (manager.clipboardCallbacksInFlight > 0) {
                    check(System.nanoTime() < deadline) { "Round $round never settled: $steps" }
                    val next: List<() -> Unit> = stalls.map { stall -> { stalls.remove(stall); stall.resume() } } +
                        media.heldClones.map { clone -> { media.heldClones.remove(clone); clone.countDown() } }
                    if (next.isEmpty()) {
                        Thread.sleep(1)
                        continue
                    }
                    next[random.nextInt(next.size)]()
                    if (random.nextBoolean()) Thread.sleep(random.nextLong(0, 3))
                }
            }

            repeat(6) {
                when (random.nextInt(10)) {
                    0 -> copy(ClipData.newPlainText("", "p"), Effect("p"), "copy p")
                    1 -> copy(ClipData.newPlainText("", "q"), Effect("q"), "copy q")
                    2 -> copy(imageClip(), Effect("photo.png"), "copy photo")
                    3 -> copy(imageClip("other.png"), Effect("other.png"), "copy other")
                    4 -> copy(imageClip(UNREADABLE), null, "copy unreadable")
                    5 -> copy(emptyClip(), Effect(null), "clear")
                    6 -> {
                        steps += "keyboard x"
                        manager.updatePrimaryClip(ClipboardItem.text("x"))
                        expected = "x"
                    }
                    7 -> {
                        steps += "keyboard clear"
                        manager.updatePrimaryClip(null)
                        expected = null
                    }
                    8 -> {
                        steps += "repeat"
                        manager.onPrimaryClipChanged()
                        if (systemHolds.content != UNREADABLE_EFFECT) expected = systemHolds.content
                    }
                    else -> {
                        steps += "settle"
                        settle()
                    }
                }
                if (random.nextBoolean()) Thread.sleep(random.nextLong(0, 3))
            }
            settle()

            withClue("round $round: $steps") {
                val clip = manager.primaryClip
                (clip?.text ?: clip?.uri?.let { media.sourceOf[it] ?: "unknown $it" }) shouldBe expected
                clip?.uri?.let { media.deleted shouldNotContain it }
            }
        }
    }

    @Test
    fun aRepeatOfANewerEqualCopyStaysARepeatAfterTheSlowCopyLoses() {
        media.holdNextInsert()
        system.setPrimaryClip(imageClip())
        media.awaitHeldInsert()
        system.setPrimaryClip(ClipData.newPlainText("", "newer text"))
        waitUntil { manager.primaryClip?.text == "newer text" }

        // The same image copied after the text is a new copy, not a repeat: it clones and wins.
        system.setPrimaryClip(imageClip())
        waitUntil { manager.clipboardCallbacksInFlight == 1 }
        val newest = media.inserted.single()
        media.releaseHeldInsert()
        waitUntil { manager.clipboardCallbacksInFlight == 0 }

        // The first copy lost. That mustn't turn a repeat of the newest copy into another import.
        system.setPrimaryClip(imageClip())
        waitUntil { manager.clipboardCallbacksInFlight == 0 }

        manager.primaryClip?.uri shouldBe newest
        media.inserted.size shouldBe 2
        media.deleted shouldContainExactly listOf(media.inserted.last())
    }

    @Test
    fun copyingThePrimaryClipAgainMakesTheCopyStillCloningLose() {
        system.setPrimaryClip(ClipData.newPlainText("", "kept text"))
        waitUntil { manager.primaryClip?.text == "kept text" }
        val kept = manager.primaryClip
        media.holdNextInsert()
        system.setPrimaryClip(imageClip())
        media.awaitHeldInsert()

        // The newest copy equals the primary clip, so it claims its revision and keeps that clip
        // as it is. It finishes before the image does, which then loses.
        system.setPrimaryClip(ClipData.newPlainText("", "kept text"))
        waitUntil { manager.clipboardCallbacksInFlight == 1 }
        media.releaseHeldInsert()
        waitUntil { manager.clipboardCallbacksInFlight == 0 }

        manager.primaryClip shouldBeSameInstanceAs kept
        media.deleted shouldContainExactly media.inserted
    }

    @Test
    fun aReplacedClipTheSystemClipboardHoldsAgainKeepsItsFile() {
        system.setPrimaryClip(imageClip())
        waitUntil { manager.clipboardCallbacksInFlight == 0 }
        val ours = media.inserted.single()
        media.holdNextInsert()
        system.setPrimaryClip(imageClip("other.png"))
        media.awaitHeldInsert()

        // Another app copies our clip's URI after the slow image. With sync ignoring copies, that
        // callback changes nothing, so the image still publishes and replaces our clip. Its file
        // has to stay, since the system clipboard points at it.
        setSyncToFloris(ClipboardSyncBehavior.ONLY_CLEAR_EVENTS)
        system.setPrimaryClip(ClipData(ClipDescription("photo", arrayOf("image/png")), ClipData.Item(ours)))
        waitUntil { manager.clipboardCallbacksInFlight == 1 }
        media.releaseHeldInsert()
        waitUntil { manager.clipboardCallbacksInFlight == 0 }

        manager.primaryClip?.uri shouldBe media.inserted.last()
        media.deleted.shouldBeEmpty()
    }

    @Test
    fun aClearAfterANewerCopyStillDropsTheSlowCopy() {
        media.holdNextInsert()
        system.setPrimaryClip(imageClip())
        media.awaitHeldInsert()

        system.setPrimaryClip(ClipData.newPlainText("", "newer text"))
        waitUntil { manager.primaryClip?.text == "newer text" }
        system.setPrimaryClip(emptyClip())
        waitUntil { manager.primaryClip == null }
        media.releaseHeldInsert()
        waitUntil { media.deleted.isNotEmpty() }

        manager.primaryClip shouldBe null
        media.deleted shouldContainExactly media.inserted
    }

    @Test
    fun deletingTheClipJustCopiedRemovesItsHistoryRowAndFile() {
        setHistoryEnabled(true)
        historyRows()
        val result = AtomicReference<ClipboardDeletionResult?>()
        // Deletes the primary clip the moment it's published, before its callback goes on, the
        // way the clear key can. A delete that has to wait for the row lets the callback go on.
        val watcher = CoroutineScope(Dispatchers.Unconfined).launch {
            val shown = manager.primaryClipFlow.first { it?.uri != null }!!
            thread { result.set(runBlocking { manager.deleteClip(shown, onlyIfUnpinned = true) }) }
                .join(1_500)
        }
        system.setPrimaryClip(imageClip())
        waitUntil { result.get() != null }
        watcher.cancel()

        result.get()!!.succeeded shouldBe true
        media.deleted shouldContainExactly media.inserted
        historyRows().shouldBeEmpty()
    }

    @Test
    fun clearingTextTheKeyboardJustCopiedRemovesItsHistoryRow() {
        setHistoryEnabled(true)
        historyRows()
        manager.addNewPlaintext("local")
        // What the clear key does: drop the primary clip, then its row.
        val shown = manager.primaryClip!!
        manager.updatePrimaryClip(null)
        val result = runBlocking { manager.deleteClip(shown, onlyIfUnpinned = true) }
        waitUntil { manager.clipboardCallbacksInFlight == 0 }
        runBlocking { manager.clearExactHistory(emptyList(), onlyIfUnpinned = false) }

        result.succeeded shouldBe true
        historyRows().shouldBeEmpty()
    }

    @Test
    fun clipsCopiedBackToBackStayInTheOrderTheyArrived() {
        setHistoryEnabled(true)
        historyRows()
        // Ten rounds stay inside the default history limit of 20, so nothing is evicted.
        repeat(10) { round ->
            system.setPrimaryClip(ClipData.newPlainText("", "first $round"))
            system.setPrimaryClip(ClipData.newPlainText("", "second $round"))
            waitUntil { manager.clipboardCallbacksInFlight == 0 }
        }

        // A first copy that the second beat to publishing is dropped, so it may have no row.
        val history = ClipboardHistoryMaintenance.sortedHistory(historyRows()).all.map { it.text }
        val arrivedNewestFirst = (9 downTo 0).flatMap { listOf("second $it", "first $it") }
        history shouldContainExactly arrivedNewestFirst.filter { it in history }
        history.filter { it!!.startsWith("second") }.size shouldBe 10
    }

    @Test
    fun copyingTheSameTextTwiceInARowKeepsOneRow() {
        setHistoryEnabled(true)
        historyRows()
        manager.addNewPlaintext("again")
        manager.addNewPlaintext("again")
        waitUntil { historyRows().isNotEmpty() }
        // History deletes queue behind the writes asked for before them, so this one runs after both.
        runBlocking { manager.clearExactHistory(emptyList(), onlyIfUnpinned = false) }
        waitUntil { manager.clipboardCallbacksInFlight == 0 }

        historyRows().map { it.text } shouldContainExactly listOf("again")
    }

    @Test
    fun aCopyTheSystemRefusesLeavesThePasteTargetAndHistoryAlone() {
        setHistoryEnabled(true)
        historyRows()
        manager.addNewPlaintext("kept")
        waitUntil { manager.clipboardCallbacksInFlight == 0 && historyRows().isNotEmpty() }

        SwitchableClipboardShadow.failure = IllegalStateException("clipboard service is gone")
        val refused = try {
            manager.addNewPlaintext("refused")
            null
        } catch (e: IllegalStateException) {
            e
        } finally {
            SwitchableClipboardShadow.failure = null
        }
        // History deletes queue behind the writes asked for before them, so anything the refused copy queued
        // has landed once this returns.
        runBlocking { manager.clearExactHistory(emptyList(), onlyIfUnpinned = false) }

        withClue("the copy screens still hear that it failed") { (refused != null) shouldBe true }
        withClue("the keyboard still pastes the clip from before") { manager.primaryClip?.text shouldBe "kept" }
        withClue("the refused text never reaches history") {
            historyRows().map { it.text } shouldContainExactly listOf("kept")
        }
    }

    @Test
    fun anOversizedCopyTheSystemRefusesLeavesThePasteTargetAlone() {
        setHistoryEnabled(true)
        historyRows()
        manager.addNewPlaintext("kept")
        waitUntil { manager.clipboardCallbacksInFlight == 0 && historyRows().isNotEmpty() }
        // Past the retention limit, so it can only become the direct-paste text, never a clip.
        val oversized = "x".repeat(ClipboardTextRetentionPolicy.MAX_RETAINED_UTF8_BYTES + 1)

        SwitchableClipboardShadow.failure = IllegalStateException("clipboard service is gone")
        val refused = try {
            manager.setPlaintextWithoutHistory(oversized, isSensitive = false)
            null
        } catch (e: IllegalStateException) {
            e
        } finally {
            SwitchableClipboardShadow.failure = null
        }

        withClue("the caller still hears that it failed") { (refused != null) shouldBe true }
        withClue("the keyboard still pastes the clip from before") { manager.primaryClip?.text shouldBe "kept" }
        withClue("the refused text isn't held for a direct paste") { manager.primaryTextForDirectPaste() shouldBe null }
    }

    /** The history rows as stored. The first call also waits for the history database to open. */
    private fun historyRows(): List<ClipboardItem> = runBlocking { manager.snapshotHistoryForRestore() }

    private fun setHistoryEnabled(enabled: Boolean) {
        savePref { prefs.clipboard.historyEnabled.set(enabled) }
    }

    private fun setSyncToFloris(behavior: ClipboardSyncBehavior) {
        savePref { prefs.clipboard.syncToFloris.set(behavior) }
    }

    private fun setUseInternalClipboard(enabled: Boolean) {
        savePref { prefs.clipboard.useInternalClipboard.set(enabled) }
    }

    private fun savePref(write: suspend () -> Result<*>) {
        changedPrefs = true
        // The app loads the store in the background and swaps it in when done, which would drop a
        // value set before then.
        waitUntil { (context as FlorisApplication).preferenceStoreLoaded.value }
        // JetPref saves by renaming a temp file over the old one, which a Windows JVM refuses once
        // the file exists, so the saved copy goes first.
        val saved = File(context.dataDir, "jetpref_datastore/florisboard-app-prefs.jetpref")
        check(saved.delete() || !saved.exists()) { "Couldn't delete the saved preferences" }
        // A save that never answers fails this test instead of hanging the whole run.
        runBlocking { withTimeout(10_000) { write().getOrThrow() } }
    }

    /** What another app's clear looks like: one item with neither text nor a URI. */
    private fun emptyClip(): ClipData {
        return ClipData(ClipDescription("", arrayOf("text/plain")), ClipData.Item(null as CharSequence?))
    }

    private fun imageClip(name: String = "photo.png"): ClipData {
        return ClipData(
            ClipDescription("photo", arrayOf("image/png")),
            ClipData.Item(Uri.parse("content://dev.patrickgold.florisboard.test.source/$name")),
        )
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!condition()) {
            check(System.nanoTime() < deadline) { "Timed out waiting for the clipboard callback" }
            Thread.sleep(10)
        }
    }

    /** What a change leaves the clip showing: text, an image's source name, or null for empty. */
    private class Effect(val content: String?)

    private companion object {
        const val UNREADABLE = "unreadable.png"
        // What the system clipboard holds after a copy that can't be read, so repeating it is a no-op.
        const val UNREADABLE_EFFECT = "no effect"
    }
}

/**
 * A clipboard change whose callback stalls before it touches any shared state: the first read of
 * its items off the test thread waits until [resume].
 */
class StalledClipData(clip: ClipData) : ClipData(clip) {
    private val testThread = Thread.currentThread()
    private val stalled = AtomicBoolean(false)
    private val reached = CountDownLatch(1)
    private val resumed = CountDownLatch(1)

    override fun getItemAt(index: Int): ClipData.Item {
        if (Thread.currentThread() !== testThread && stalled.compareAndSet(false, true)) {
            reached.countDown()
            resumed.await(30, TimeUnit.SECONDS)
        }
        return super.getItemAt(index)
    }

    fun awaitStalled() {
        check(reached.await(5, TimeUnit.SECONDS)) { "The change's callback never read its items" }
    }

    fun resume() {
        resumed.countDown()
    }
}

/** Stands in for ClipboardMediaProvider: records clones and deletes, and can hold or refuse one insert. */
class HoldingMediaProvider : ContentProvider() {
    private val nextId = AtomicLong(0)
    val inserted: MutableList<Uri> = CopyOnWriteArrayList()
    val deleted: MutableList<Uri> = CopyOnWriteArrayList()
    val refusedInserts = AtomicInteger(0)

    @Volatile
    private var refuseNext = false

    @Volatile
    private var hold: CountDownLatch? = null
    private val reached = CountDownLatch(1)
    private val release = CountDownLatch(1)

    fun holdNextInsert() {
        hold = release
    }

    fun awaitHeldInsert() {
        check(reached.await(5, TimeUnit.SECONDS)) { "The clone never reached the media provider" }
    }

    fun releaseHeldInsert() {
        release.countDown()
    }

    /** One source's clone, held until [release] and then failed if [refuse]. */
    class SourceHold(val refuse: Boolean) {
        val reached = CountDownLatch(1)
        private val released = CountDownLatch(1)

        fun awaitReached() {
            check(reached.await(5, TimeUnit.SECONDS)) { "That clone never reached the media provider" }
        }

        fun release() {
            released.countDown()
        }

        fun await() {
            released.await(30, TimeUnit.SECONDS)
        }
    }

    private val sourceHolds = ConcurrentHashMap<String, SourceHold>()
    private val allSourceHolds: MutableList<SourceHold> = CopyOnWriteArrayList()

    /** Holds the next clone of the source named [name], so two clones can be held at once. */
    fun holdInsertOf(name: String, refuse: Boolean): SourceHold {
        return SourceHold(refuse).also {
            sourceHolds[name] = it
            allSourceHolds += it
        }
    }

    /** While on, every clone waits in [heldClones] until the test counts its latch down. */
    @Volatile
    var holdEveryInsert = false
    val heldClones: MutableList<CountDownLatch> = CopyOnWriteArrayList()

    /** Sources every clone of fails, like an app that took its URI grant back. */
    val unreadableSources: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** The source each clone was made from. */
    val sourceOf = ConcurrentHashMap<Uri, String>()

    fun releaseEverything() {
        releaseHeldInsert()
        allSourceHolds.forEach { it.release() }
        holdEveryInsert = false
        heldClones.forEach { it.countDown() }
    }

    /** The next clone fails the way an unreadable source does. */
    fun refuseNextInsert() {
        refuseNext = true
    }

    override fun onCreate(): Boolean = true

    override fun insert(uri: Uri, values: ContentValues?): Uri {
        val source = values?.getAsString(ClipboardMediaProvider.Columns.MediaUri)?.substringAfterLast('/')
        source?.let { sourceHolds.remove(it) }?.let { held ->
            held.reached.countDown()
            held.await()
            if (held.refuse) {
                refusedInserts.incrementAndGet()
                throw IllegalStateException("injected")
            }
        }
        hold?.let { gate ->
            hold = null
            reached.countDown()
            gate.await(30, TimeUnit.SECONDS)
        }
        if (holdEveryInsert) {
            val clone = CountDownLatch(1)
            heldClones += clone
            clone.await(30, TimeUnit.SECONDS)
        }
        if (refuseNext || (source != null && source in unreadableSources)) {
            refuseNext = false
            refusedInserts.incrementAndGet()
            throw IllegalStateException("injected")
        }
        val inserted = ContentUris.withAppendedId(uri, nextId.incrementAndGet())
        this.inserted += inserted
        source?.let { sourceOf[inserted] = it }
        return inserted
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        deleted += uri
        return 1
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}

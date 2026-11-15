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

package dev.patrickgold.florisboard.app.settings.advanced

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The remembered archives a scheduled run reads at its start and saves at its end. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ScheduledBackupStoreTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun aRunWithNoChangesKeepsWhatItChecked() {
        val generation = ScheduledBackupStore.checkedArchivesGeneration(context)
        ScheduledBackupStore.saveCheckedArchives(context, generation, setOf("a/10")) shouldBe true

        val nextRun = ScheduledBackupStore.checkedArchivesGeneration(context)
        ScheduledBackupStore.checkedArchives(context, nextRun) shouldBe setOf("a/10")
    }

    @Test
    fun aFolderChangeDuringARunDropsWhatThatRunChecked() {
        // The run read its folder before the change, so what it checked belongs to the old one.
        val earlier = ScheduledBackupStore.checkedArchivesGeneration(context)
        ScheduledBackupStore.saveCheckedArchives(context, earlier, setOf("a/10")) shouldBe true
        val runStart = ScheduledBackupStore.checkedArchivesGeneration(context)

        ScheduledBackupStore.setTreeUri(context, "content://another-folder") shouldBe true

        ScheduledBackupStore.checkedArchives(context, runStart) shouldBe emptySet()
        ScheduledBackupStore.saveCheckedArchives(context, runStart, setOf("a/10", "b/20")) shouldBe false
        val nextRun = ScheduledBackupStore.checkedArchivesGeneration(context)
        ScheduledBackupStore.checkedArchives(context, nextRun) shouldBe emptySet()
    }

    @Test
    fun clearingThePassphraseDuringARunDropsWhatThatRunChecked() {
        val runStart = ScheduledBackupStore.checkedArchivesGeneration(context)

        ScheduledBackupStore.clearPassphrase(context) shouldBe true

        ScheduledBackupStore.saveCheckedArchives(context, runStart, setOf("a/10")) shouldBe false
        val nextRun = ScheduledBackupStore.checkedArchivesGeneration(context)
        ScheduledBackupStore.checkedArchives(context, nextRun) shouldBe emptySet()
    }
}

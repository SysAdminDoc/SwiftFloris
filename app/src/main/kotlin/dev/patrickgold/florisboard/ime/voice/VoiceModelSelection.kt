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

package dev.patrickgold.florisboard.ime.voice

import android.app.ActivityManager
import android.content.Context

enum class VoiceModelTier(
    val approximateSizeMb: Int,
    val minimumRecommendedRamMb: Int,
) {
    TINY_EN(
        approximateSizeMb = 75,
        minimumRecommendedRamMb = 2_048,
    ),
    BASE_EN(
        approximateSizeMb = 140,
        minimumRecommendedRamMb = 4_096,
    ),
    LARGE_V3_TURBO_INT8(
        approximateSizeMb = 800,
        minimumRecommendedRamMb = 8_192,
    ),
}

enum class VoiceModelPreference {
    AUTO,
    TINY_EN,
    BASE_EN,
    LARGE_V3_TURBO_INT8,
    ;

    fun resolve(profile: VoiceDeviceRamProfile): VoiceModelTier {
        return when (this) {
            AUTO -> VoiceModelSelector.recommend(profile)
            TINY_EN -> VoiceModelTier.TINY_EN
            BASE_EN -> VoiceModelTier.BASE_EN
            LARGE_V3_TURBO_INT8 -> VoiceModelTier.LARGE_V3_TURBO_INT8
        }
    }
}

data class VoiceDeviceRamProfile(
    val totalRamMb: Int?,
    val isLowRamDevice: Boolean,
)

object VoiceModelSelector {
    fun detectDeviceRamProfile(context: Context): VoiceDeviceRamProfile {
        val activityManager = context.getSystemService(ActivityManager::class.java)
            ?: return VoiceDeviceRamProfile(totalRamMb = null, isLowRamDevice = false)
        val memoryInfo = ActivityManager.MemoryInfo()
        val totalRamMb = runCatching {
            activityManager.getMemoryInfo(memoryInfo)
            memoryInfo.totalMem
                .takeIf { it > 0L }
                ?.let { bytes -> (bytes / BYTES_PER_MEBIBYTE).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() }
        }.getOrNull()
        val isLowRamDevice = runCatching { activityManager.isLowRamDevice }.getOrDefault(false)
        return VoiceDeviceRamProfile(
            totalRamMb = totalRamMb,
            isLowRamDevice = isLowRamDevice,
        )
    }

    fun recommend(profile: VoiceDeviceRamProfile): VoiceModelTier {
        val totalRamMb = profile.totalRamMb
        return when {
            profile.isLowRamDevice -> VoiceModelTier.TINY_EN
            totalRamMb == null -> VoiceModelTier.BASE_EN
            totalRamMb < VoiceModelTier.BASE_EN.minimumRecommendedRamMb -> VoiceModelTier.TINY_EN
            totalRamMb < VoiceModelTier.LARGE_V3_TURBO_INT8.minimumRecommendedRamMb -> VoiceModelTier.BASE_EN
            else -> VoiceModelTier.LARGE_V3_TURBO_INT8
        }
    }

    private const val BYTES_PER_MEBIBYTE = 1_024L * 1_024L
}

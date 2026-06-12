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

package dev.patrickgold.florisboard.app.settings.typing

internal enum class TypingTraceAction {
    ShareRawTrace,
    ShareReplayFixtures,
    ClearTrace,
}

internal data class TypingTraceActionState(
    val action: TypingTraceAction,
    val enabled: Boolean,
) {
    val sharesExternally: Boolean
        get() = action != TypingTraceAction.ClearTrace

    val containsSensitiveRawTraceFields: Boolean
        get() = action == TypingTraceAction.ShareRawTrace

    val requiresSensitiveContentConfirmation: Boolean
        get() = containsSensitiveRawTraceFields

    val isRecommendedDebugExport: Boolean
        get() = action == TypingTraceAction.ShareReplayFixtures
}

internal object TypingTraceExportPolicy {
    val recommendedDebugExportAction: TypingTraceAction = TypingTraceAction.ShareReplayFixtures

    fun stateFor(action: TypingTraceAction, traceFileBytes: Long): TypingTraceActionState {
        return TypingTraceActionState(
            action = action,
            enabled = traceFileBytes > 0L,
        )
    }
}

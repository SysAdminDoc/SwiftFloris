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

package dev.patrickgold.florisboard.app.settings.dictionary

internal enum class UserDictionaryEntryOperation {
    Saving,
    Deleting,
}

internal enum class UserDictionaryTransferOperation {
    Importing,
    Exporting,
}

internal enum class UserDictionaryEntryNotice {
    None,
    Saving,
    Deleting,
    SaveSuccess,
    SaveFailure,
    DeleteSuccess,
    DeleteFailure,
}

internal enum class UserDictionaryTransferNotice {
    None,
    Importing,
    Exporting,
}

internal enum class UserDictionaryBlockedBackNotice {
    None,
    Saving,
    Deleting,
    Importing,
    Exporting,
}

internal object UserDictionaryEntryPolicy {
    fun canLeave(
        isOperationInProgress: Boolean,
        isTransferInProgress: Boolean = false,
    ): Boolean {
        return !isOperationInProgress && !isTransferInProgress
    }

    fun canMutateEntry(
        isOperationInProgress: Boolean,
        isTransferInProgress: Boolean = false,
    ): Boolean {
        return !isOperationInProgress && !isTransferInProgress
    }

    fun canStartTransfer(
        isOperationInProgress: Boolean,
        isTransferInProgress: Boolean,
    ): Boolean {
        return !isOperationInProgress && !isTransferInProgress
    }

    fun resolveNotice(
        activeOperation: UserDictionaryEntryOperation?,
        lastTerminalNotice: UserDictionaryEntryNotice?,
    ): UserDictionaryEntryNotice {
        return when (activeOperation) {
            UserDictionaryEntryOperation.Saving -> UserDictionaryEntryNotice.Saving
            UserDictionaryEntryOperation.Deleting -> UserDictionaryEntryNotice.Deleting
            null -> lastTerminalNotice ?: UserDictionaryEntryNotice.None
        }
    }

    fun resolveTransferNotice(
        activeOperation: UserDictionaryTransferOperation?,
    ): UserDictionaryTransferNotice {
        return when (activeOperation) {
            UserDictionaryTransferOperation.Importing -> UserDictionaryTransferNotice.Importing
            UserDictionaryTransferOperation.Exporting -> UserDictionaryTransferNotice.Exporting
            null -> UserDictionaryTransferNotice.None
        }
    }

    fun resolveBlockedBackNotice(
        activeEntryOperation: UserDictionaryEntryOperation?,
        activeTransferOperation: UserDictionaryTransferOperation?,
    ): UserDictionaryBlockedBackNotice {
        return when {
            activeEntryOperation == UserDictionaryEntryOperation.Saving -> UserDictionaryBlockedBackNotice.Saving
            activeEntryOperation == UserDictionaryEntryOperation.Deleting -> UserDictionaryBlockedBackNotice.Deleting
            activeTransferOperation == UserDictionaryTransferOperation.Importing -> UserDictionaryBlockedBackNotice.Importing
            activeTransferOperation == UserDictionaryTransferOperation.Exporting -> UserDictionaryBlockedBackNotice.Exporting
            else -> UserDictionaryBlockedBackNotice.None
        }
    }

    fun saveResult(saved: Boolean): UserDictionaryEntryNotice {
        return if (saved) {
            UserDictionaryEntryNotice.SaveSuccess
        } else {
            UserDictionaryEntryNotice.SaveFailure
        }
    }

    fun deleteResult(deleted: Boolean): UserDictionaryEntryNotice {
        return if (deleted) {
            UserDictionaryEntryNotice.DeleteSuccess
        } else {
            UserDictionaryEntryNotice.DeleteFailure
        }
    }
}

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

package dev.patrickgold.florisboard.ime.keyboard

import dev.patrickgold.florisboard.ime.editor.ImeOptions

internal object EnterKeyPolicy {
    private val EditorActions = setOf(
        ImeOptions.Action.DONE,
        ImeOptions.Action.GO,
        ImeOptions.Action.NEXT,
        ImeOptions.Action.PREVIOUS,
        ImeOptions.Action.SEARCH,
        ImeOptions.Action.SEND,
    )

    /**
     * The action Enter runs in a field, or null when it types a newline there. Shift+Enter types a
     * newline in a multi-line field even when the field has an action.
     */
    fun editorAction(
        action: ImeOptions.Action,
        flagNoEnterAction: Boolean,
        isMultiLine: Boolean,
        isShiftPressed: Boolean,
    ): ImeOptions.Action? {
        if (flagNoEnterAction || isMultiLine && isShiftPressed) return null
        return action.takeIf { it in EditorActions }
    }
}

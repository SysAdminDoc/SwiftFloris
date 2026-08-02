/*
 * Copyright (C) 2026 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.ime.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.SnyggButton
import org.florisboard.lib.snygg.ui.SnyggText
import java.time.ZoneId
import java.util.Locale

@Composable
fun CalendarAgendaPickerPanel(
    state: CalendarAgendaPickerState,
    manager: CalendarQuickInsertManager,
    modifier: Modifier = Modifier,
) {
    val zoneId = remember { ZoneId.systemDefault() }
    val locale = remember { Locale.getDefault() }
    SnyggBox(
        elementName = FlorisImeUi.SmartbarActionsOverflow.elementName,
        modifier = modifier
            .fillMaxWidth()
            .height(FlorisImeSizing.keyboardUiHeight()),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SnyggText(
                    modifier = Modifier.weight(1f),
                    text = stringRes(R.string.calendar__panel__title),
                )
                SnyggButton(
                    elementName = FlorisImeUi.SmartbarActionsOverflowCustomizeButton.elementName,
                    onClick = manager::dismiss,
                ) {
                    SnyggText(text = stringRes(R.string.action__close))
                }
            }

            when (state) {
                CalendarAgendaPickerState.Hidden -> Unit
                CalendarAgendaPickerState.Loading -> {
                    SnyggText(text = stringRes(R.string.calendar__panel__loading))
                }
                CalendarAgendaPickerState.Empty -> {
                    SnyggText(text = stringRes(R.string.calendar__panel__empty))
                }
                is CalendarAgendaPickerState.Error -> {
                    SnyggText(text = stringRes(state.messageResId))
                }
                is CalendarAgendaPickerState.Showing -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(
                            items = state.events,
                            key = { event -> "${event.eventId}:${event.startMillis}:${event.title}" },
                        ) { event ->
                            CalendarAgendaPickerItem(
                                event = event,
                                zoneId = zoneId,
                                locale = locale,
                                onClick = { manager.insert(event) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarAgendaPickerItem(
    event: CalendarAgendaEvent,
    zoneId: ZoneId,
    locale: Locale,
    onClick: () -> Unit,
) {
    val title = remember(event) { CalendarAgendaFormatter.run { event.titleForDisplay() } }
    val timeRange = remember(event, zoneId, locale) {
        CalendarAgendaFormatter.formatTimeRange(event, zoneId, locale)
    }
    SnyggButton(
        elementName = FlorisImeUi.SmartbarActionsOverflowCustomizeButton.elementName,
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            SnyggText(text = title)
            SnyggText(text = timeRange)
            if (!event.calendarName.isNullOrBlank()) {
                SnyggText(text = event.calendarName)
            }
        }
    }
}

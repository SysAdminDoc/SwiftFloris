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

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import dev.patrickgold.florisboard.editorInstance
import dev.patrickgold.florisboard.keyboardManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.chrono.Chronology
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private const val DefaultDaysAhead = 7L
private const val DefaultMaxEvents = 24

data class CalendarAgendaEvent(
    val eventId: Long,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val calendarName: String? = null,
    val location: String? = null,
    val timeZoneId: String? = null,
)

data class CalendarAgendaWindow(
    val startMillis: Long,
    val endMillis: Long,
) {
    companion object {
        fun todayThroughNextDays(
            nowMillis: Long,
            daysAhead: Long = DefaultDaysAhead,
            zoneId: ZoneId = ZoneId.systemDefault(),
        ): CalendarAgendaWindow {
            require(daysAhead >= 0L) { "daysAhead must be non-negative" }
            val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
            val start = today.atStartOfDay(zoneId).toInstant().toEpochMilli()
            val end = today.plusDays(daysAhead + 1L).atStartOfDay(zoneId).toInstant().toEpochMilli()
            return CalendarAgendaWindow(startMillis = start, endMillis = end)
        }
    }
}

object CalendarAgendaFormatter {
    private const val UntitledEvent = "Untitled event"

    private fun dateFormatter(locale: Locale): DateTimeFormatter {
        val chronology = Chronology.ofLocale(locale)
        return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(locale)
            .withChronology(chronology)
    }

    private fun timeFormatter(locale: Locale): DateTimeFormatter {
        return DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
            .withLocale(locale)
    }

    // CLDR 42+ uses U+202F (narrow no-break space) before AM/PM in many
    // locales.  Committed keyboard text should use plain ASCII spaces so
    // every text field renders it consistently.
    private fun String.normalizeSpaces(): String =
        replace('\u202F', ' ').replace('\u00A0', ' ')

    fun formatForInsert(
        event: CalendarAgendaEvent,
        zoneId: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): String {
        return "${event.titleForDisplay()} - ${formatTimeRange(event, zoneId, locale)}"
    }

    fun formatTimeRange(
        event: CalendarAgendaEvent,
        zoneId: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): String {
        val dateFmt = dateFormatter(locale)
        if (event.allDay) {
            val startDate = event.startMillis.toUtcLocalDate()
            val endDate = event.endMillis
                .takeIf { it > event.startMillis }
                ?.let { (it - 1L).toUtcLocalDate() }
                ?: startDate
            return if (startDate == endDate) {
                dateFmt.format(startDate)
            } else {
                "${dateFmt.format(startDate)} - ${dateFmt.format(endDate)}"
            }
        }

        val zone = event.resolveZone(zoneId)
        val start = Instant.ofEpochMilli(event.startMillis).atZone(zone)
        val end = event.endMillis
            .takeIf { it > event.startMillis }
            ?.let { Instant.ofEpochMilli(it).atZone(zone) }
        val timeFmt = timeFormatter(locale)
        val startTime = timeFmt.format(start).normalizeSpaces()
        val endTime = end?.let { timeFmt.format(it).normalizeSpaces() }
        val startText = "${dateFmt.format(start)} $startTime"
        return when {
            end == null -> startText
            start.toLocalDate() == end.toLocalDate() -> {
                "${dateFmt.format(start)}, $startTime-$endTime"
            }
            else -> {
                "$startText - ${dateFmt.format(end)} $endTime"
            }
        }
    }

    fun CalendarAgendaEvent.titleForDisplay(): String {
        return title.trim().ifBlank { UntitledEvent }
    }

    private fun CalendarAgendaEvent.resolveZone(fallback: ZoneId): ZoneId {
        return timeZoneId?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: fallback
    }

    private fun Long.toUtcLocalDate(): LocalDate {
        return Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
    }
}

sealed interface CalendarAgendaPickerState {
    data object Hidden : CalendarAgendaPickerState
    data object Loading : CalendarAgendaPickerState
    data object Empty : CalendarAgendaPickerState
    data class Showing(val events: List<CalendarAgendaEvent>) : CalendarAgendaPickerState
    data class Error(val message: String) : CalendarAgendaPickerState
}

class CalendarQuickInsertManager(private val context: Context) {
    private val appContext = context.applicationContext
    private val reader = CalendarAgendaReader(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _pickerState = MutableStateFlow<CalendarAgendaPickerState>(CalendarAgendaPickerState.Hidden)

    val pickerState: StateFlow<CalendarAgendaPickerState> = _pickerState

    fun hasReadCalendarPermission(): Boolean {
        return reader.hasReadCalendarPermission()
    }

    fun openPicker(nowMillis: Long = System.currentTimeMillis()) {
        hideCompetingPanels()
        _pickerState.value = CalendarAgendaPickerState.Loading
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    reader.queryUpcoming(
                        nowMillis = nowMillis,
                        window = CalendarAgendaWindow.todayThroughNextDays(nowMillis),
                        limit = DefaultMaxEvents,
                    )
                }
            }
            _pickerState.value = result.fold(
                onSuccess = { events ->
                    if (events.isEmpty()) {
                        CalendarAgendaPickerState.Empty
                    } else {
                        CalendarAgendaPickerState.Showing(events)
                    }
                },
                onFailure = {
                    CalendarAgendaPickerState.Error("Calendar events could not be loaded.")
                },
            )
        }
    }

    fun insert(event: CalendarAgendaEvent) {
        val editorInstance by appContext.editorInstance()
        editorInstance.commitText(CalendarAgendaFormatter.formatForInsert(event))
        dismiss()
    }

    fun dismiss() {
        _pickerState.value = CalendarAgendaPickerState.Hidden
    }

    private fun hideCompetingPanels() {
        val keyboardManager by appContext.keyboardManager()
        keyboardManager.activeState.batchEdit {
            it.isActionsOverflowVisible = false
            it.isActionsEditorVisible = false
        }
    }
}

class CalendarAgendaReader(private val context: Context) {
    fun hasReadCalendarPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun queryUpcoming(
        nowMillis: Long = System.currentTimeMillis(),
        window: CalendarAgendaWindow = CalendarAgendaWindow.todayThroughNextDays(nowMillis),
        limit: Int = DefaultMaxEvents,
    ): List<CalendarAgendaEvent> {
        if (!hasReadCalendarPermission()) return emptyList()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also { builder ->
            ContentUris.appendId(builder, window.startMillis)
            ContentUris.appendId(builder, window.endMillis)
        }.build()

        val events = mutableListOf<CalendarAgendaEvent>()
        val cursor = context.contentResolver.query(
            uri,
            Projection,
            null,
            null,
            "${CalendarContract.Instances.BEGIN} ASC",
        ) ?: return emptyList()
        cursor.use {
            while (it.moveToNext() && events.size < limit) {
                val begin = it.getLong(ColumnBegin)
                val end = it.getLong(ColumnEnd).takeIf { value -> value > 0L } ?: begin
                if (end < nowMillis) continue
                val title = it.stringOrNull(ColumnTitle)?.trim().orEmpty()
                if (title.isBlank()) continue
                events += CalendarAgendaEvent(
                    eventId = it.getLong(ColumnEventId),
                    title = title,
                    startMillis = begin,
                    endMillis = end,
                    allDay = it.getInt(ColumnAllDay) != 0,
                    calendarName = it.stringOrNull(ColumnCalendarName),
                    location = it.stringOrNull(ColumnLocation),
                    timeZoneId = it.stringOrNull(ColumnTimeZone),
                )
            }
        }
        return events
    }

    private fun Cursor.stringOrNull(columnIndex: Int): String? {
        return if (isNull(columnIndex)) null else getString(columnIndex)
    }

    private companion object {
        private const val ColumnEventId = 0
        private const val ColumnTitle = 1
        private const val ColumnBegin = 2
        private const val ColumnEnd = 3
        private const val ColumnAllDay = 4
        private const val ColumnCalendarName = 5
        private const val ColumnLocation = 6
        private const val ColumnTimeZone = 7

        private val Projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.EVENT_TIMEZONE,
        )
    }
}

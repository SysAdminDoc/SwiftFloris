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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class CalendarAgendaFormatterTest : FunSpec({
    val zone = ZoneId.of("America/New_York")
    val locale = Locale.US

    test("calendar query window starts today and includes the next seven days") {
        val now = Instant.parse("2026-05-17T15:30:00Z").toEpochMilli()
        val window = CalendarAgendaWindow.todayThroughNextDays(
            nowMillis = now,
            zoneId = zone,
        )

        window.startMillis shouldBe Instant.parse("2026-05-17T04:00:00Z").toEpochMilli()
        window.endMillis shouldBe Instant.parse("2026-05-25T04:00:00Z").toEpochMilli()
    }

    test("formats same-day timed event for insertion") {
        val event = CalendarAgendaEvent(
            eventId = 42L,
            title = "Room install call",
            startMillis = Instant.parse("2026-05-17T13:00:00Z").toEpochMilli(),
            endMillis = Instant.parse("2026-05-17T14:30:00Z").toEpochMilli(),
            allDay = false,
            timeZoneId = "America/New_York",
        )

        CalendarAgendaFormatter.formatForInsert(event, zone, locale) shouldBe
            "Room install call - May 17, 2026, 9:00 AM-10:30 AM"
    }

    test("formats cross-day timed event with both endpoints") {
        val event = CalendarAgendaEvent(
            eventId = 43L,
            title = "Overnight maintenance",
            startMillis = Instant.parse("2026-05-18T03:30:00Z").toEpochMilli(),
            endMillis = Instant.parse("2026-05-18T06:00:00Z").toEpochMilli(),
            allDay = false,
            timeZoneId = "America/New_York",
        )

        CalendarAgendaFormatter.formatTimeRange(event, zone, locale) shouldBe
            "May 17, 2026 11:30 PM - May 18, 2026 2:00 AM"
    }

    test("formats all-day events using UTC dates to avoid local timezone drift") {
        val event = CalendarAgendaEvent(
            eventId = 44L,
            title = "Expo day",
            startMillis = Instant.parse("2026-05-17T00:00:00Z").toEpochMilli(),
            endMillis = Instant.parse("2026-05-18T00:00:00Z").toEpochMilli(),
            allDay = true,
        )

        CalendarAgendaFormatter.formatForInsert(event, zone, locale) shouldBe
            "Expo day - May 17, 2026"
    }

    test("formats multi-day all-day events with inclusive end date") {
        val event = CalendarAgendaEvent(
            eventId = 45L,
            title = "Conference",
            startMillis = Instant.parse("2026-05-17T00:00:00Z").toEpochMilli(),
            endMillis = Instant.parse("2026-05-20T00:00:00Z").toEpochMilli(),
            allDay = true,
        )

        CalendarAgendaFormatter.formatTimeRange(event, zone, locale) shouldBe
            "May 17, 2026 - May 19, 2026"
    }

    test("blank titles fall back to a deterministic insert label") {
        val event = CalendarAgendaEvent(
            eventId = 46L,
            title = " ",
            startMillis = Instant.parse("2026-05-17T13:00:00Z").toEpochMilli(),
            endMillis = Instant.parse("2026-05-17T14:00:00Z").toEpochMilli(),
            allDay = false,
            timeZoneId = "America/New_York",
        )

        CalendarAgendaFormatter.formatForInsert(event, zone, locale) shouldBe
            "Untitled event - May 17, 2026, 9:00 AM-10:00 AM"
    }

    test("German locale formats date in locale-appropriate order") {
        val deLocale = Locale.GERMAN
        val event = CalendarAgendaEvent(
            eventId = 50L,
            title = "Besprechung",
            startMillis = Instant.parse("2026-05-17T00:00:00Z").toEpochMilli(),
            endMillis = Instant.parse("2026-05-18T00:00:00Z").toEpochMilli(),
            allDay = true,
        )
        val result = CalendarAgendaFormatter.formatForInsert(event, zone, deLocale)
        result shouldBe "Besprechung - 17.05.2026"
    }

    test("Japanese locale formats date with locale-appropriate characters") {
        val jaLocale = Locale.JAPANESE
        val event = CalendarAgendaEvent(
            eventId = 51L,
            title = "Meeting",
            startMillis = Instant.parse("2026-05-17T00:00:00Z").toEpochMilli(),
            endMillis = Instant.parse("2026-05-18T00:00:00Z").toEpochMilli(),
            allDay = true,
        )
        val result = CalendarAgendaFormatter.formatForInsert(event, zone, jaLocale)
        result shouldBe "Meeting - 2026/05/17"
    }

    test("narrow no-break spaces in AM/PM are normalized to ASCII space") {
        val event = CalendarAgendaEvent(
            eventId = 52L,
            title = "Test",
            startMillis = Instant.parse("2026-05-17T13:00:00Z").toEpochMilli(),
            endMillis = Instant.parse("2026-05-17T14:00:00Z").toEpochMilli(),
            allDay = false,
            timeZoneId = "America/New_York",
        )
        val result = CalendarAgendaFormatter.formatTimeRange(event, zone, locale)
        result.any { it == '\u202F' || it == '\u00A0' } shouldBe false
    }
})

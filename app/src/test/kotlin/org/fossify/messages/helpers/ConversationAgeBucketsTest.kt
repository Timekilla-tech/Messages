package org.fossify.messages.helpers

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class ConversationAgeBucketsTest {
    private val mondayFirstDayOfWeek = Calendar.MONDAY

    @Test
    fun getConversationAgeBoundaries_usesProvidedWeekStartAndNow() {
        val nowMillis = calendarMillis(2024, Calendar.JUNE, 19, 12, 0, 0)

        val boundaries = getConversationAgeBoundaries(nowMillis, mondayFirstDayOfWeek)

        assertEquals(calendarMillis(2024, Calendar.JUNE, 19, 0, 0, 0), boundaries.startOfToday)
        assertEquals(calendarMillis(2024, Calendar.JUNE, 18, 0, 0, 0), boundaries.startOfYesterday)
        assertEquals(calendarMillis(2024, Calendar.JUNE, 17, 0, 0, 0), boundaries.startOfWeek)
        assertEquals(calendarMillis(2024, Calendar.JUNE, 1, 0, 0, 0), boundaries.startOfMonth)
    }

    @Test
    fun getConversationAgeBucket_returnsExpectedBucketForEachBoundaryRange() {
        val nowMillis = calendarMillis(2024, Calendar.JUNE, 19, 12, 0, 0)
        val boundaries = getConversationAgeBoundaries(nowMillis, mondayFirstDayOfWeek)

        assertEquals(ConversationAgeBucket.TODAY, getConversationAgeBucket((boundaries.startOfToday / 1000L).toInt(), nowMillis, mondayFirstDayOfWeek))
        assertEquals(ConversationAgeBucket.YESTERDAY, getConversationAgeBucket((boundaries.startOfYesterday / 1000L).toInt(), nowMillis, mondayFirstDayOfWeek))
        assertEquals(ConversationAgeBucket.THIS_WEEK, getConversationAgeBucket((boundaries.startOfWeek / 1000L).toInt(), nowMillis, mondayFirstDayOfWeek))
        assertEquals(ConversationAgeBucket.THIS_MONTH, getConversationAgeBucket((boundaries.startOfMonth / 1000L).toInt(), nowMillis, mondayFirstDayOfWeek))
        assertEquals(ConversationAgeBucket.OLDER, getConversationAgeBucket(((boundaries.startOfMonth - 1000L) / 1000L).toInt(), nowMillis, mondayFirstDayOfWeek))
    }

    private fun calendarMillis(
        year: Int,
        month: Int,
        dayOfMonth: Int,
        hourOfDay: Int,
        minute: Int,
        second: Int,
    ): Long {
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, dayOfMonth)
            set(Calendar.HOUR_OF_DAY, hourOfDay)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, second)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}


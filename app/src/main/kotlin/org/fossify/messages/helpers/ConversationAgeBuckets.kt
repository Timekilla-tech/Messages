@file:Suppress("unused")

package org.fossify.messages.helpers

import androidx.annotation.StringRes
import org.fossify.messages.R
import java.util.Calendar

internal enum class ConversationAgeBucket(@param:StringRes val titleRes: Int) {
    TODAY(R.string.inbox_group_today),
    YESTERDAY(R.string.inbox_group_yesterday),
    THIS_WEEK(R.string.inbox_group_this_week),
    THIS_MONTH(R.string.inbox_group_this_month),
    OLDER(R.string.inbox_group_older),
}

internal data class ConversationAgeBoundaries(
    val startOfToday: Long,
    val startOfYesterday: Long,
    val startOfWeek: Long,
    val startOfMonth: Long,
)

internal fun getConversationAgeBoundaries(
    nowMillis: Long = System.currentTimeMillis(),
    firstDayOfWeek: Int = Calendar.getInstance().firstDayOfWeek,
): ConversationAgeBoundaries {
    val now = Calendar.getInstance().apply {
        timeInMillis = nowMillis
    }

    val startOfToday = (now.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    val startOfYesterday = (startOfToday.clone() as Calendar).apply {
        add(Calendar.DAY_OF_YEAR, -1)
    }

    val startOfWeek = (startOfToday.clone() as Calendar).apply {
        val dayOffset = (7 + get(Calendar.DAY_OF_WEEK) - firstDayOfWeek) % 7
        add(Calendar.DAY_OF_YEAR, -dayOffset)
    }

    val startOfMonth = (startOfToday.clone() as Calendar).apply {
        set(Calendar.DAY_OF_MONTH, 1)
    }

    return ConversationAgeBoundaries(
        startOfToday = startOfToday.timeInMillis,
        startOfYesterday = startOfYesterday.timeInMillis,
        startOfWeek = startOfWeek.timeInMillis,
        startOfMonth = startOfMonth.timeInMillis,
    )
}

internal fun getConversationAgeBucket(
    timestampSeconds: Int,
    nowMillis: Long = System.currentTimeMillis(),
    firstDayOfWeek: Int = Calendar.getInstance().firstDayOfWeek,
): ConversationAgeBucket {
    val timestampMillis = timestampSeconds * 1000L
    val boundaries = getConversationAgeBoundaries(nowMillis, firstDayOfWeek)

    return when {
        timestampMillis >= boundaries.startOfToday -> ConversationAgeBucket.TODAY
        timestampMillis >= boundaries.startOfYesterday -> ConversationAgeBucket.YESTERDAY
        timestampMillis >= boundaries.startOfWeek -> ConversationAgeBucket.THIS_WEEK
        timestampMillis >= boundaries.startOfMonth -> ConversationAgeBucket.THIS_MONTH
        else -> ConversationAgeBucket.OLDER
    }
}


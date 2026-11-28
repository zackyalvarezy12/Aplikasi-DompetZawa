package com.example.domain

import com.example.data.local.RecurringRuleEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class BillReminderAlert(
    val rule: RecurringRuleEntity,
    val daysUntilDue: Int, // 3 (H-3) or 1 (H-1)
    val dedupKey: String,
    val dueDateLabel: String
)

object BillReminderEvaluator {

    fun findPendingReminders(
        rules: List<RecurringRuleEntity>,
        alreadyNotifiedKeys: Set<String>,
        nowMillis: Long = System.currentTimeMillis()
    ): List<BillReminderAlert> {
        val todayStart = startOfLocalDay(nowMillis)
        val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        return rules.mapNotNull { rule ->
            if (!rule.isActive || !rule.reminderEnabled) return@mapNotNull null
            val dueStart = startOfLocalDay(rule.nextDueDateMillis)
            val diffDays = ((dueStart - todayStart) / 86_400_000L).toInt()
            if (diffDays != 3 && diffDays != 1) return@mapNotNull null

            val dueStr = dayFmt.format(Date(dueStart))
            val dedupKey = "${rule.id}|$dueStr|H-$diffDays"
            if (dedupKey in alreadyNotifiedKeys) return@mapNotNull null

            BillReminderAlert(
                rule = rule,
                daysUntilDue = diffDays,
                dedupKey = dedupKey,
                dueDateLabel = CurrencyFormatter.formatDateShort(rule.nextDueDateMillis)
            )
        }
    }

    fun calendarDaysUntilDue(
        dueDateMillis: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): Int {
        val todayStart = startOfLocalDay(nowMillis)
        val dueStart = startOfLocalDay(dueDateMillis)
        return ((dueStart - todayStart) / 86_400_000L).toInt()
    }

    private fun startOfLocalDay(millis: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}

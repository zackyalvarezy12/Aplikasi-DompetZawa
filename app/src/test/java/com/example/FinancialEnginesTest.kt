package com.example

import com.example.data.local.*
import com.example.domain.BillReminderEvaluator
import com.example.domain.BudgetStatusLevel
import com.example.domain.CurrencyFormatter
import com.example.domain.MonthlyBudgetReportCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class FinancialEnginesTest {

    @Test
    fun `bill reminder evaluator triggers exactly on H-3 and H-1 and deduplicates`() {
        val baseCal = Calendar.getInstance().apply {
            set(2026, Calendar.MAY, 10, 9, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val now = baseCal.timeInMillis

        fun dayOffset(days: Int): Long = (baseCal.clone() as Calendar).apply {
            add(Calendar.DAY_OF_MONTH, days)
        }.timeInMillis

        val rules = listOf(
            RecurringRuleEntity(
                id = "r_h3", workspaceId = "ws_1", title = "Listrik PLN", amount = 250_000L,
                type = TxTypes.EXPENSE, accountId = "acc_1", categoryId = "cat_1",
                frequency = "MONTHLY", nextDueDateMillis = dayOffset(3), isActive = true, reminderEnabled = true
            ),
            RecurringRuleEntity(
                id = "r_h1", workspaceId = "ws_1", title = "Internet WiFi", amount = 350_000L,
                type = TxTypes.EXPENSE, accountId = "acc_1", categoryId = "cat_1",
                frequency = "MONTHLY", nextDueDateMillis = dayOffset(1), isActive = true, reminderEnabled = true
            ),
            RecurringRuleEntity(
                id = "r_h2", workspaceId = "ws_1", title = "Streaming", amount = 65_000L,
                type = TxTypes.EXPENSE, accountId = "acc_1", categoryId = "cat_1",
                frequency = "MONTHLY", nextDueDateMillis = dayOffset(2), isActive = true, reminderEnabled = true
            ),
            RecurringRuleEntity(
                id = "r_disabled", workspaceId = "ws_1", title = "Air PDAM", amount = 90_000L,
                type = TxTypes.EXPENSE, accountId = "acc_1", categoryId = "cat_1",
                frequency = "MONTHLY", nextDueDateMillis = dayOffset(3), isActive = true, reminderEnabled = false
            )
        )

        val firstPass = BillReminderEvaluator.findPendingReminders(rules, emptySet(), now)
        assertEquals(2, firstPass.size)
        assertEquals(listOf("r_h3", "r_h1"), firstPass.map { it.rule.id })
        assertEquals(listOf(3, 1), firstPass.map { it.daysUntilDue })

        val notifiedKeys = firstPass.map { it.dedupKey }.toSet()
        val secondPass = BillReminderEvaluator.findPendingReminders(rules, notifiedKeys, now)
        assertTrue(secondPass.isEmpty())
    }

    @Test
    fun `monthly budget report calculator aggregates allocation rollover and status`() {
        val monthKey = "2026-05"
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.MAY, 15, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val mayMillis = cal.timeInMillis

        val categories = listOf(
            CategoryEntity(id = "cat_food", workspaceId = "ws_1", name = "Makan", type = TxTypes.EXPENSE, iconKey = "restaurant", colorHex = 0xFF059669L),
            CategoryEntity(id = "cat_bill", workspaceId = "ws_1", name = "Tagihan", type = TxTypes.EXPENSE, iconKey = "receipt_long", colorHex = 0xFF2563EBL),
            CategoryEntity(id = "cat_sal", workspaceId = "ws_1", name = "Gaji", type = TxTypes.INCOME, iconKey = "payments", colorHex = 0xFF10B981L)
        )
        val budgets = listOf(
            BudgetEntity(id = "b1", workspaceId = "ws_1", categoryId = "cat_food", monthYear = monthKey, limitAmount = 1_000_000L, rolloverAmount = 200_000L, rolloverEnabled = true),
            BudgetEntity(id = "b2", workspaceId = "ws_1", categoryId = "cat_bill", monthYear = monthKey, limitAmount = 500_000L, rolloverAmount = 0L, rolloverEnabled = false)
        )
        val txs = listOf(
            TransactionEntity(id = "t1", workspaceId = "ws_1", type = TxTypes.INCOME, amount = 5_000_000L, accountId = "acc_1", categoryId = "cat_sal", title = "Gaji", dateMillis = mayMillis, createdByMemberId = "m1", createdByName = "User"),
            TransactionEntity(id = "t2", workspaceId = "ws_1", type = TxTypes.EXPENSE, amount = 600_000L, accountId = "acc_1", categoryId = "cat_food", title = "Makan", dateMillis = mayMillis, createdByMemberId = "m1", createdByName = "User"),
            TransactionEntity(id = "t3", workspaceId = "ws_1", type = TxTypes.EXPENSE, amount = 550_000L, accountId = "acc_1", categoryId = "cat_bill", title = "Listrik", dateMillis = mayMillis, createdByMemberId = "m1", createdByName = "User")
        )

        val report = MonthlyBudgetReportCalculator.calculate(monthKey, txs, budgets, categories)
        assertEquals(5_000_000L, report.totalIncome)
        assertEquals(1_150_000L, report.totalExpense)
        assertEquals(3_850_000L, report.netCashFlow)
        assertEquals(1_700_000L, report.totalAllocatedBudget)
        assertEquals(1_150_000L, report.totalBudgetedSpent)
        assertEquals(550_000L, report.totalRemainingBudget)

        val foodRow = report.rows.first { it.categoryId == "cat_food" }
        assertEquals(BudgetStatusLevel.AMAN, foodRow.status)
        assertEquals(600_000L, foodRow.remaining)

        val billRow = report.rows.first { it.categoryId == "cat_bill" }
        assertEquals(BudgetStatusLevel.OVERBUDGET, billRow.status)
        assertEquals(-50_000L, billRow.remaining)

        val months = MonthlyBudgetReportCalculator.availableMonthKeys(txs, budgets)
        assertTrue(monthKey in months)
        assertTrue(CurrencyFormatter.currentMonthKey() in months)
    }
}

package com.example.domain

import com.example.data.local.BudgetEntity
import com.example.data.local.CategoryEntity
import com.example.data.local.TransactionEntity
import com.example.data.local.TxTypes

enum class BudgetStatusLevel {
    AMAN,
    WASPADA,
    OVERBUDGET,
    UNBUDGETED
}

data class MonthlyCategoryBudgetSummary(
    val categoryId: String,
    val categoryName: String,
    val allocated: Long,
    val spent: Long,
    val remaining: Long,
    val usagePercent: Double,
    val status: BudgetStatusLevel
)

data class MonthlyBudgetSummaryReport(
    val monthKey: String,
    val monthLabel: String,
    val totalIncome: Long,
    val totalExpense: Long,
    val netCashFlow: Long,
    val totalAllocatedBudget: Long,
    val totalBudgetedSpent: Long,
    val totalRemainingBudget: Long,
    val overallUsagePercent: Double,
    val rows: List<MonthlyCategoryBudgetSummary>
)

object MonthlyBudgetReportCalculator {

    fun calculate(
        monthKey: String,
        transactions: List<TransactionEntity>,
        budgets: List<BudgetEntity>,
        categories: List<CategoryEntity>
    ): MonthlyBudgetSummaryReport {
        val catMap = categories.associateBy { it.id }
        val monthTxs = transactions.filter { CurrencyFormatter.currentMonthKey(it.dateMillis) == monthKey }

        val totalIncome = monthTxs
            .filter { it.type == TxTypes.INCOME || it.type == TxTypes.REFUND }
            .sumOf { it.amount }
        val totalExpense = monthTxs
            .filter { it.type == TxTypes.EXPENSE || it.type == TxTypes.SPLIT }
            .sumOf { it.amount }

        val spentByCat = HashMap<String, Long>()
        monthTxs
            .filter { it.type == TxTypes.EXPENSE || it.type == TxTypes.SPLIT }
            .forEach { tx ->
                spentByCat[tx.categoryId] = (spentByCat[tx.categoryId] ?: 0L) + tx.amount
            }

        val monthBudgets = budgets.filter { it.monthYear == monthKey }
        val budgetByCat = monthBudgets.groupBy { it.categoryId }

        val allCatIds = (budgetByCat.keys + spentByCat.keys).distinct()
        val rows = allCatIds.map { catId ->
            val catBudgets = budgetByCat[catId].orEmpty()
            val allocated = catBudgets.sumOf { it.limitAmount + if (it.rolloverEnabled) it.rolloverAmount else 0L }
            val spent = spentByCat[catId] ?: 0L
            val remaining = if (allocated > 0L) allocated - spent else 0L
            val usagePct = if (allocated > 0L) (spent.toDouble() / allocated) * 100.0 else 0.0
            val status = when {
                allocated <= 0L -> BudgetStatusLevel.UNBUDGETED
                usagePct >= 100.0 -> BudgetStatusLevel.OVERBUDGET
                usagePct >= 75.0 -> BudgetStatusLevel.WASPADA
                else -> BudgetStatusLevel.AMAN
            }
            MonthlyCategoryBudgetSummary(
                categoryId = catId,
                categoryName = catMap[catId]?.name ?: "Lainnya",
                allocated = allocated,
                spent = spent,
                remaining = remaining,
                usagePercent = usagePct,
                status = status
            )
        }.sortedWith(
            compareByDescending<MonthlyCategoryBudgetSummary> { it.allocated > 0L }
                .thenByDescending { it.spent }
        )

        val totalAllocated = rows.sumOf { it.allocated }
        val totalBudgetedSpent = rows.filter { it.allocated > 0L }.sumOf { it.spent }
        val totalRemaining = totalAllocated - totalBudgetedSpent
        val overallPct = if (totalAllocated > 0L) (totalBudgetedSpent.toDouble() / totalAllocated) * 100.0 else 0.0

        return MonthlyBudgetSummaryReport(
            monthKey = monthKey,
            monthLabel = CurrencyFormatter.monthLabel(monthKey),
            totalIncome = totalIncome,
            totalExpense = totalExpense,
            netCashFlow = totalIncome - totalExpense,
            totalAllocatedBudget = totalAllocated,
            totalBudgetedSpent = totalBudgetedSpent,
            totalRemainingBudget = totalRemaining,
            overallUsagePercent = overallPct,
            rows = rows
        )
    }

    fun availableMonthKeys(
        transactions: List<TransactionEntity>,
        budgets: List<BudgetEntity>
    ): List<String> {
        val current = CurrencyFormatter.currentMonthKey()
        val txMonths = transactions.map { CurrencyFormatter.currentMonthKey(it.dateMillis) }
        val budgetMonths = budgets.map { it.monthYear }
        return (listOf(current) + txMonths + budgetMonths)
            .distinct()
            .sortedDescending()
    }
}

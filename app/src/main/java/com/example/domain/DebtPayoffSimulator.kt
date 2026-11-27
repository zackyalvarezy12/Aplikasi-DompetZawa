package com.example.domain

import com.example.data.local.DebtLoanEntity
import kotlin.math.roundToLong

enum class PayoffStrategy {
    SNOWBALL,  // Smallest remaining balance first
    AVALANCHE  // Highest interest rate first
}

data class PayoffScheduleResult(
    val strategy: PayoffStrategy,
    val monthsToDebtFree: Int,
    val totalInterestPaid: Long,
    val totalPaid: Long,
    val payoffOrderTitles: List<String>,
    val balanceCurve: List<Long>
)

data class PayoffComparison(
    val snowball: PayoffScheduleResult,
    val avalanche: PayoffScheduleResult,
    val interestSavedByAvalanche: Long,
    val monthsSavedByAvalanche: Int
)

object DebtPayoffSimulator {

    fun compare(debts: List<DebtLoanEntity>, extraMonthlyBudget: Long): PayoffComparison {
        val activeDebts = debts.filter { it.kind == "DEBT" && !it.isPaidOff && it.remainingAmount > 0L }
        val snowball = simulate(activeDebts, extraMonthlyBudget, PayoffStrategy.SNOWBALL)
        val avalanche = simulate(activeDebts, extraMonthlyBudget, PayoffStrategy.AVALANCHE)
        return PayoffComparison(
            snowball = snowball,
            avalanche = avalanche,
            interestSavedByAvalanche = (snowball.totalInterestPaid - avalanche.totalInterestPaid).coerceAtLeast(0L),
            monthsSavedByAvalanche = (snowball.monthsToDebtFree - avalanche.monthsToDebtFree).coerceAtLeast(0)
        )
    }

    private fun simulate(
        debts: List<DebtLoanEntity>,
        extraMonthlyBudget: Long,
        strategy: PayoffStrategy
    ): PayoffScheduleResult {
        if (debts.isEmpty()) {
            return PayoffScheduleResult(strategy, 0, 0L, 0L, emptyList(), listOf(0L))
        }

        data class MutableDebt(
            val title: String,
            var balance: Double,
            val monthlyRate: Double,
            val minPayment: Double
        )

        val ordered = when (strategy) {
            PayoffStrategy.SNOWBALL -> debts.sortedBy { it.remainingAmount }
            PayoffStrategy.AVALANCHE -> debts.sortedByDescending { it.interestRateAnnual }
        }.map {
            MutableDebt(
                title = it.title,
                balance = it.remainingAmount.toDouble(),
                monthlyRate = (it.interestRateAnnual / 100.0) / 12.0,
                minPayment = it.minimumMonthlyPayment.coerceAtLeast(50_000L).toDouble()
            )
        }

        val totalMinPayment = ordered.sumOf { it.minPayment }
        val monthlyTotalBudget = totalMinPayment + extraMonthlyBudget.coerceAtLeast(0L)
        var months = 0
        var totalInterest = 0.0
        var totalPaid = 0.0
        val curve = mutableListOf(ordered.sumOf { it.balance }.roundToLong())
        val paidOffOrder = mutableListOf<String>()

        while (ordered.any { it.balance > 1.0 } && months < 360) {
            months++
            // 1. Accrue monthly interest
            for (d in ordered) {
                if (d.balance > 0.0) {
                    val interest = d.balance * d.monthlyRate
                    d.balance += interest
                    totalInterest += interest
                }
            }

            var remainingBudget = monthlyTotalBudget

            // 2. Pay minimums first
            for (d in ordered) {
                if (d.balance > 0.0) {
                    val pay = minOf(d.balance, d.minPayment, remainingBudget)
                    d.balance -= pay
                    remainingBudget -= pay
                    totalPaid += pay
                    if (d.balance <= 1.0) {
                        d.balance = 0.0
                        if (d.title !in paidOffOrder) paidOffOrder.add(d.title)
                    }
                }
            }

            // 3. Apply freed minimum + extra budget to target debt in priority order
            for (d in ordered) {
                if (remainingBudget <= 1.0) break
                if (d.balance > 0.0) {
                    val pay = minOf(d.balance, remainingBudget)
                    d.balance -= pay
                    remainingBudget -= pay
                    totalPaid += pay
                    if (d.balance <= 1.0) {
                        d.balance = 0.0
                        if (d.title !in paidOffOrder) paidOffOrder.add(d.title)
                    }
                }
            }

            curve.add(ordered.sumOf { it.balance }.roundToLong())
        }

        return PayoffScheduleResult(
            strategy = strategy,
            monthsToDebtFree = months,
            totalInterestPaid = totalInterest.roundToLong(),
            totalPaid = totalPaid.roundToLong(),
            payoffOrderTitles = ordered.map { it.title },
            balanceCurve = curve
        )
    }
}

package com.example.domain

import kotlin.math.pow
import kotlin.math.roundToLong

data class CompoundPoint(
    val year: Int,
    val totalPrincipal: Long,
    val totalValue: Long
)

data class CompoundResult(
    val finalValue: Long,
    val totalPrincipal: Long,
    val totalInterest: Long,
    val yearlyPoints: List<CompoundPoint>
)

data class KprComparisonResult(
    val flatMonthlyInstallment: Long,
    val flatTotalInterest: Long,
    val flatTotalPayment: Long,
    val effectiveMonthlyInstallment: Long,
    val effectiveTotalInterest: Long,
    val effectiveTotalPayment: Long
)

data class ZakatResult(
    val zakatMaalDue: Long,
    val isMaalEligible: Boolean,
    val nisabMaalThreshold: Long,
    val zakatPenghasilanMonthly: Long,
    val isPenghasilanEligible: Boolean,
    val nisabPenghasilanMonthly: Long
)

object FinancialCalculators {

    fun calculateEmergencyFund(
        monthlyExpenses: Long,
        dependentsCount: Int,
        isFreelancer: Boolean
    ): Pair<Int, Long> {
        val baseMonths = when {
            dependentsCount >= 3 -> 12
            dependentsCount == 2 -> 9
            dependentsCount == 1 -> 6
            else -> 3
        }
        val recommendedMonths = baseMonths + if (isFreelancer) 3 else 0
        return recommendedMonths to (monthlyExpenses * recommendedMonths)
    }

    fun calculateCompoundInterest(
        initialDeposit: Long,
        monthlyContribution: Long,
        annualRatePercent: Double,
        years: Int
    ): CompoundResult {
        val rMonthly = (annualRatePercent / 100.0) / 12.0
        var balance = initialDeposit.toDouble()
        var principal = initialDeposit.toDouble()
        val points = mutableListOf(CompoundPoint(0, initialDeposit, initialDeposit))

        for (y in 1..years.coerceIn(1, 50)) {
            repeat(12) {
                balance = balance * (1.0 + rMonthly) + monthlyContribution
                principal += monthlyContribution
            }
            points.add(CompoundPoint(y, principal.roundToLong(), balance.roundToLong()))
        }

        val finalVal = balance.roundToLong()
        val totalPrin = principal.roundToLong()
        return CompoundResult(
            finalValue = finalVal,
            totalPrincipal = totalPrin,
            totalInterest = (finalVal - totalPrin).coerceAtLeast(0L),
            yearlyPoints = points
        )
    }

    fun calculateKprFlatVsEffective(
        principal: Long,
        annualRatePercent: Double,
        tenorYears: Int
    ): KprComparisonResult {
        val months = (tenorYears * 12).coerceAtLeast(1)
        val rAnnual = annualRatePercent / 100.0
        val rMonthly = rAnnual / 12.0

        // Flat
        val flatTotalInterest = (principal * rAnnual * tenorYears).roundToLong()
        val flatTotalPayment = principal + flatTotalInterest
        val flatMonthly = flatTotalPayment / months

        // Effective (Annuity)
        val effMonthly = if (rMonthly > 0.0) {
            val factor = (1.0 + rMonthly).pow(months)
            (principal * rMonthly * factor / (factor - 1.0)).roundToLong()
        } else {
            principal / months
        }
        val effTotalPayment = effMonthly * months
        val effTotalInterest = (effTotalPayment - principal).coerceAtLeast(0L)

        return KprComparisonResult(
            flatMonthlyInstallment = flatMonthly,
            flatTotalInterest = flatTotalInterest,
            flatTotalPayment = flatTotalPayment,
            effectiveMonthlyInstallment = effMonthly,
            effectiveTotalInterest = effTotalInterest,
            effectiveTotalPayment = effTotalPayment
        )
    }

    fun calculateZakat(
        totalNetWealth: Long,
        monthlyIncome: Long,
        goldPricePerGram: Long = 1_520_000L
    ): ZakatResult {
        val nisabMaal = 85L * goldPricePerGram
        val maalEligible = totalNetWealth >= nisabMaal
        val zakatMaal = if (maalEligible) (totalNetWealth * 0.025).roundToLong() else 0L

        val nisabPenghasilanMonthly = nisabMaal / 12L
        val penghasilanEligible = monthlyIncome >= nisabPenghasilanMonthly
        val zakatPenghasilan = if (penghasilanEligible) (monthlyIncome * 0.025).roundToLong() else 0L

        return ZakatResult(
            zakatMaalDue = zakatMaal,
            isMaalEligible = maalEligible,
            nisabMaalThreshold = nisabMaal,
            zakatPenghasilanMonthly = zakatPenghasilan,
            isPenghasilanEligible = penghasilanEligible,
            nisabPenghasilanMonthly = nisabPenghasilanMonthly
        )
    }
}

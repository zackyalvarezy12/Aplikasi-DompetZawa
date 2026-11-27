package com.example.domain

import java.util.Calendar
import kotlin.math.roundToLong

data class PayrollCalculationInput(
    val baseSalary: Long,
    val workingDaysInMonth: Int,
    val offDays: Int = 0,
    val deductOffDays: Boolean = true,
    val sickDays: Int = 0,
    val sickDeductionPercent: Int = 100, // 100, 50, or 0
    val otherLeaveDays: Int = 0,
    val extraDeduction: Long = 0L,
    val bonusAmount: Long = 0L
)

data class PayrollCalculationResult(
    val baseSalary: Long,
    val workingDaysInMonth: Int,
    val dailyRate: Long,
    val offDays: Int,
    val sickDays: Int,
    val otherLeaveDays: Int,
    val totalLeaveDays: Int,
    val effectiveWorkingDays: Int,
    val offDeduction: Long,
    val sickDeduction: Long,
    val otherLeaveDeduction: Long,
    val totalLeaveDeduction: Long,
    val extraDeduction: Long,
    val totalDeduction: Long,
    val bonusAmount: Long,
    val netSalary: Long
)

object PayrollCalculator {

    fun calculate(input: PayrollCalculationInput): PayrollCalculationResult {
        val safeBase = input.baseSalary.coerceAtLeast(0L)
        val safeWorkingDays = input.workingDaysInMonth.coerceIn(1, 31)
        val safeOff = input.offDays.coerceAtLeast(0)
        val safeSick = input.sickDays.coerceAtLeast(0)
        val safeOther = input.otherLeaveDays.coerceAtLeast(0)
        val safeExtra = input.extraDeduction.coerceAtLeast(0L)
        val safeBonus = input.bonusAmount.coerceAtLeast(0L)
        val safeSickPct = input.sickDeductionPercent.coerceIn(0, 100)

        val dailyRate = (safeBase.toDouble() / safeWorkingDays.toDouble()).roundToLong()

        val offDeduction = if (input.deductOffDays) safeOff * dailyRate else 0L
        val sickDeduction = ((safeSick * dailyRate) * (safeSickPct / 100.0)).roundToLong()
        val otherLeaveDeduction = safeOther * dailyRate

        val totalLeaveDeduction = offDeduction + sickDeduction + otherLeaveDeduction
        val totalDeduction = totalLeaveDeduction + safeExtra
        val netSalary = (safeBase - totalDeduction + safeBonus).coerceAtLeast(0L)

        val totalLeaveDays = safeOff + safeSick + safeOther
        val effectiveWorkingDays = (safeWorkingDays - totalLeaveDays).coerceAtLeast(0)

        return PayrollCalculationResult(
            baseSalary = safeBase,
            workingDaysInMonth = safeWorkingDays,
            dailyRate = dailyRate,
            offDays = safeOff,
            sickDays = safeSick,
            otherLeaveDays = safeOther,
            totalLeaveDays = totalLeaveDays,
            effectiveWorkingDays = effectiveWorkingDays,
            offDeduction = offDeduction,
            sickDeduction = sickDeduction,
            otherLeaveDeduction = otherLeaveDeduction,
            totalLeaveDeduction = totalLeaveDeduction,
            extraDeduction = safeExtra,
            totalDeduction = totalDeduction,
            bonusAmount = safeBonus,
            netSalary = netSalary
        )
    }

    fun effectivePaydayForMonth(paydayDay: Int, calendar: Calendar = Calendar.getInstance()): Int {
        val maxDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        return paydayDay.coerceIn(1, maxDay)
    }

    fun isPaydayDueThisMonth(
        isEnabled: Boolean,
        monthlyBaseSalary: Long,
        paydayDay: Int,
        lastHandledCycleKey: String,
        calendar: Calendar = Calendar.getInstance()
    ): Boolean {
        if (!isEnabled || monthlyBaseSalary <= 0L) return false
        val currentCycle = CurrencyFormatter.currentMonthKey(calendar.timeInMillis)
        if (lastHandledCycleKey == currentCycle) return false
        val todayDay = calendar.get(Calendar.DAY_OF_MONTH)
        val effectiveDay = effectivePaydayForMonth(paydayDay, calendar)
        return todayDay >= effectiveDay
    }
}

package com.example

import com.example.data.local.*
import com.example.domain.*
import org.junit.Assert.*
import org.junit.Test

class FinancialEnginesTest {

    @Test
    fun quickInputParser_parsesCoffeeExampleAccurately() {
        val accounts = listOf(
            AccountEntity("acc_bca", "ws", "Bank BCA", AccountTypes.BANK, 10_000_000L, institutionName = "BCA"),
            AccountEntity("acc_gopay", "ws", "GoPay", AccountTypes.EWALLET, 500_000L, institutionName = "GoPay")
        )
        val categories = DatabaseSeeder.defaultCategories("ws", "p")
        val parsed = SmartQuickInputParser.parse("Beli kopi 28rb pakai GoPay #nongkrong", accounts, categories)

        assertTrue(parsed.isValid)
        assertEquals(TxTypes.EXPENSE, parsed.type)
        assertEquals(28_000L, parsed.amount)
        assertEquals("acc_gopay", parsed.accountId)
        assertEquals("cat_p_makan", parsed.categoryId)
        assertEquals("#nongkrong", parsed.tags)
    }

    @Test
    fun debtSimulator_avalancheSavesInterestOverSnowball() {
        val debts = listOf(
            DebtLoanEntity("d1", "ws", "DEBT", "PayLater", "SPay", 2_000_000L, 2_000_000L, 28.0, 250_000L, 0L),
            DebtLoanEntity("d2", "ws", "DEBT", "Motor", "Adira", 6_000_000L, 5_000_000L, 12.0, 500_000L, 0L),
            DebtLoanEntity("d3", "ws", "DEBT", "KTA", "Bank", 10_000_000L, 1_500_000L, 6.0, 300_000L, 0L)
        )
        val cmp = DebtPayoffSimulator.compare(debts, extraMonthlyBudget = 500_000L)
        assertTrue(cmp.avalanche.monthsToDebtFree > 0)
        assertTrue(cmp.snowball.monthsToDebtFree > 0)
        assertTrue(cmp.avalanche.totalInterestPaid <= cmp.snowball.totalInterestPaid)
    }

    @Test
    fun splitBillSimplifier_minimizesTransfers() {
        val members = listOf(
            WorkspaceMemberEntity("m1", "ws", "u1", "Zacky", "z@x.id", "ZK", 0xFF000000, MemberRoles.OWNER),
            WorkspaceMemberEntity("m2", "ws", "u2", "Nadia", "n@x.id", "ND", 0xFF000000, MemberRoles.EDITOR),
            WorkspaceMemberEntity("m3", "ws", "u3", "Budi", "b@x.id", "BD", 0xFF000000, MemberRoles.VIEWER)
        )
        val bills = listOf(
            SplitBillEntity("b1", "ws", "Dinner", 900_000L, "m1", "EQUAL", "m1:300000:true|m2:300000:false|m3:300000:false", 0L, false),
            SplitBillEntity("b2", "ws", "Villa", 300_000L, "m2", "EQUAL", "m1:100000:false|m2:100000:true|m3:100000:false", 0L, false)
        )
        val settlements = SplitBillSimplifier.calculateSettlements(bills, members)
        // Net: m1 owed 600k - 100k = +500k; m2 owes 300k - 200k = -100k; m3 owes 300k + 100k = -400k
        assertEquals(2, settlements.size)
        assertEquals(500_000L, settlements.sumOf { it.amount })
    }

    @Test
    fun calculators_computeCorrectZakatAndCompound() {
        val zakat = FinancialCalculators.calculateZakat(totalNetWealth = 200_000_000L, monthlyIncome = 15_000_000L, goldPricePerGram = 1_500_000L)
        assertTrue(zakat.isMaalEligible)
        assertEquals(5_000_000L, zakat.zakatMaalDue)
        assertTrue(zakat.isPenghasilanEligible)
        assertEquals(375_000L, zakat.zakatPenghasilanMonthly)

        val compound = FinancialCalculators.calculateCompoundInterest(10_000_000L, 1_000_000L, 12.0, 5)
        assertTrue(compound.finalValue > compound.totalPrincipal)
        assertEquals(6, compound.yearlyPoints.size)
    }

    @Test
    fun timeOfDayPastelTheme_mapsSunrisePinkAndMoonlightLavender() {
        assertEquals(
            com.example.ui.theme.TimeOfDayPastelPhase.SUNRISE_PINK,
            com.example.ui.theme.TimeOfDayPastelPhase.fromHour(7)
        )
        assertEquals(
            com.example.ui.theme.TimeOfDayPastelPhase.FRESH_MINT,
            com.example.ui.theme.TimeOfDayPastelPhase.fromHour(13)
        )
        assertEquals(
            com.example.ui.theme.TimeOfDayPastelPhase.TWILIGHT_ROSE,
            com.example.ui.theme.TimeOfDayPastelPhase.fromHour(17)
        )
        assertEquals(
            com.example.ui.theme.TimeOfDayPastelPhase.MOONLIGHT_LAVENDER,
            com.example.ui.theme.TimeOfDayPastelPhase.fromHour(22)
        )
        assertEquals(
            com.example.ui.theme.TimeOfDayPastelPhase.MOONLIGHT_LAVENDER,
            com.example.ui.theme.TimeOfDayPastelPhase.fromHour(2)
        )
    }

    @Test
    fun payrollCalculator_dividesMonthlySalaryAndComputesLeaveDeductions() {
        val res = PayrollCalculator.calculate(
            PayrollCalculationInput(
                baseSalary = 5_000_000L,
                workingDaysInMonth = 25,
                offDays = 2,
                deductOffDays = true,
                sickDays = 1,
                sickDeductionPercent = 50,
                otherLeaveDays = 1,
                extraDeduction = 100_000L,
                bonusAmount = 250_000L
            )
        )
        assertEquals(200_000L, res.dailyRate)
        assertEquals(400_000L, res.offDeduction)
        assertEquals(100_000L, res.sickDeduction)
        assertEquals(200_000L, res.otherLeaveDeduction)
        assertEquals(700_000L, res.totalLeaveDeduction)
        assertEquals(800_000L, res.totalDeduction)
        assertEquals(21, res.effectiveWorkingDays)
        assertEquals(4_450_000L, res.netSalary)
    }

    @Test
    fun payrollCalculator_triggersPaydayPopupOnDay17AndSkipsWhenHandled() {
        val calBefore = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.NOVEMBER, 16, 10, 0)
        }
        val calOnPayday = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.NOVEMBER, 17, 9, 0)
        }
        assertFalse(
            PayrollCalculator.isPaydayDueThisMonth(
                isEnabled = true,
                monthlyBaseSalary = 6_000_000L,
                paydayDay = 17,
                lastHandledCycleKey = "",
                calendar = calBefore
            )
        )
        assertTrue(
            PayrollCalculator.isPaydayDueThisMonth(
                isEnabled = true,
                monthlyBaseSalary = 6_000_000L,
                paydayDay = 17,
                lastHandledCycleKey = "",
                calendar = calOnPayday
            )
        )
        assertFalse(
            PayrollCalculator.isPaydayDueThisMonth(
                isEnabled = true,
                monthlyBaseSalary = 6_000_000L,
                paydayDay = 17,
                lastHandledCycleKey = "2026-11",
                calendar = calOnPayday
            )
        )
    }
}

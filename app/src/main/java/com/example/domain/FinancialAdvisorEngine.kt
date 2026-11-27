package com.example.domain

import androidx.compose.runtime.Immutable
import com.example.data.local.*
import kotlin.math.roundToInt

@Immutable
data class AdvisorInsight(
    val title: String,
    val description: String,
    val severity: String, // GOOD | WARNING | CRITICAL | TIP
    val potentialMonthlySaving: Long = 0L
)

@Immutable
data class FinancialHealthReport(
    val score: Int, // 0..100
    val gradeLabel: String,
    val savingRatePercent: Double,
    val emergencyFundMonths: Double,
    val debtToAssetRatio: Double,
    val creditUtilizationPercent: Double,
    val latteFactorMonthly: Long,
    val latteFactorCount: Int,
    val insights: List<AdvisorInsight>
)

object FinancialAdvisorEngine {

    fun analyze(
        accounts: List<AccountEntity>,
        assets: List<AssetInvestmentEntity>,
        debtsLoans: List<DebtLoanEntity>,
        transactions: List<TransactionEntity>,
        categories: List<CategoryEntity>,
        budgets: List<BudgetEntity>
    ): FinancialHealthReport {
        val now = System.currentTimeMillis()
        val thirtyDaysAgo = now - 30L * 24 * 60 * 60 * 1000L
        val sevenDaysAgo = now - 7L * 24 * 60 * 60 * 1000L
        val monthKey = CurrencyFormatter.currentMonthKey(now)

        val liquidCash = accounts.filter {
            it.type in listOf(AccountTypes.CASH, AccountTypes.BANK, AccountTypes.EWALLET, AccountTypes.DEPOSIT)
        }.sumOf { it.balance }
        val investmentAssets = assets.sumOf { it.currentValue }
        val totalAssets = liquidCash + investmentAssets

        val creditCards = accounts.filter { it.type == AccountTypes.CREDIT_CARD }
        val ccDebt = creditCards.sumOf { it.balance }
        val ccLimit = creditCards.sumOf { it.creditLimit }
        val creditUtil = if (ccLimit > 0L) (ccDebt.toDouble() / ccLimit) * 100.0 else 0.0

        val activeDebts = debtsLoans.filter { it.kind == "DEBT" && !it.isPaidOff }.sumOf { it.remainingAmount } + ccDebt
        val debtToAsset = if (totalAssets > 0L) (activeDebts.toDouble() / totalAssets) * 100.0 else 0.0

        val recent30Tx = transactions.filter { it.dateMillis >= thirtyDaysAgo }
        val income30 = recent30Tx.filter { it.type == TxTypes.INCOME }.sumOf { it.amount }
        val expense30 = recent30Tx.filter { it.type == TxTypes.EXPENSE || it.type == TxTypes.SPLIT }.sumOf { it.amount }
        val savingRate = if (income30 > 0L) ((income30 - expense30).toDouble() / income30) * 100.0 else 0.0

        val monthlyExpenseBaseline = expense30.coerceAtLeast(1_500_000L)
        val emergencyMonths = liquidCash.toDouble() / monthlyExpenseBaseline

        // Latte Factor: frequent small discretionary expenses <= 60,000 IDR
        val smallFrequentTx = recent30Tx.filter {
            (it.type == TxTypes.EXPENSE) && it.amount in 1_000L..60_000L
        }
        val latteTotal = smallFrequentTx.sumOf { it.amount }
        val latteCount = smallFrequentTx.size

        // Compute Health Score (0-100)
        var score = 50
        score += when {
            savingRate >= 30 -> 20
            savingRate >= 15 -> 14
            savingRate > 0 -> 6
            else -> -12
        }
        score += when {
            emergencyMonths >= 6.0 -> 18
            emergencyMonths >= 3.0 -> 10
            emergencyMonths >= 1.0 -> 4
            else -> -10
        }
        score += when {
            debtToAsset <= 20.0 -> 10
            debtToAsset <= 45.0 -> 3
            else -> -10
        }
        score += when {
            creditUtil <= 30.0 -> 5
            creditUtil <= 60.0 -> -4
            else -> -10
        }
        val finalScore = score.coerceIn(0, 100)

        val gradeLabel = when {
            finalScore >= 80 -> "Sangat Sehat (Prima)"
            finalScore >= 65 -> "Sehat & Terkendali"
            finalScore >= 45 -> "Perlu Optimasi"
            else -> "Zona Waspada"
        }

        val insights = mutableListOf<AdvisorInsight>()

        // 1. Saving rate insight
        if (income30 > 0L) {
            if (savingRate >= 20.0) {
                insights.add(
                    AdvisorInsight(
                        title = "Rasio Tabungan Sehat (${savingRate.roundToInt()}%)",
                        description = "Arus kas bulan ini menyisakan ${CurrencyFormatter.formatIdr(income30 - expense30)} untuk tabungan dan investasi.",
                        severity = "GOOD"
                    )
                )
            } else {
                insights.add(
                    AdvisorInsight(
                        title = "Rasio Tabungan Di Bawah Target 20%",
                        description = "Saat ini saving rate Anda ${savingRate.roundToInt()}%. Alokasikan minimal 20% pemasukan di awal bulan (Pay Yourself First).",
                        severity = "WARNING",
                        potentialMonthlySaving = (income30 * 0.20 - (income30 - expense30)).toLong().coerceAtLeast(0L)
                    )
                )
            }
        }

        // 2. Anomaly Detection: Compare last 7 days per category vs weekly average of last 30 days
        val last7Expenses = transactions.filter {
            it.dateMillis >= sevenDaysAgo && (it.type == TxTypes.EXPENSE || it.type == TxTypes.SPLIT)
        }
        val catById = categories.associateBy { it.id }
        val grouped7 = last7Expenses.groupBy { it.categoryId }
        val grouped30 = recent30Tx.filter { it.type == TxTypes.EXPENSE || it.type == TxTypes.SPLIT }.groupBy { it.categoryId }
        for ((catId, tx7) in grouped7) {
            val sum7 = tx7.sumOf { it.amount }
            val sum30 = grouped30[catId]?.sumOf { it.amount } ?: sum7
            val weeklyAvg = (sum30 / 4.2).coerceAtLeast(100_000.0)
            if (sum7 > weeklyAvg * 1.35 && sum7 >= 150_000L) {
                val pctIncrease = (((sum7 - weeklyAvg) / weeklyAvg) * 100).roundToInt()
                val catName = catById[catId]?.name ?: "Kategori"
                insights.add(
                    AdvisorInsight(
                        title = "Anomali Pengeluaran $catName (+$pctIncrease%)",
                        description = "Pengeluaran $catName 7 hari terakhir (${CurrencyFormatter.formatIdr(sum7)}) melonjak $pctIncrease% di atas rata-rata mingguan.",
                        severity = "WARNING",
                        potentialMonthlySaving = (sum7 - weeklyAvg.toLong()).coerceAtLeast(0L)
                    )
                )
            }
        }

        // 3. Latte Factor insight
        if (latteCount >= 3 && latteTotal >= 100_000L) {
            val yearlyImpact = latteTotal * 12
            insights.add(
                AdvisorInsight(
                    title = "Deteksi Latte Factor ($latteCount transaksi kecil)",
                    description = "Pengeluaran mikro <= Rp 60rb berjumlah ${CurrencyFormatter.formatIdr(latteTotal)} bulan ini (setara ${CurrencyFormatter.formatCompactIdr(yearlyImpact)}/tahun). Mengurangi separuhnya dapat menambah dana investasi.",
                    severity = "TIP",
                    potentialMonthlySaving = latteTotal / 2
                )
            )
        }

        // 4. Credit Card Utilization > 30%
        if (ccLimit > 0L && creditUtil > 30.0) {
            insights.add(
                AdvisorInsight(
                    title = "Rasio Pemakaian Kartu Kredit ${creditUtil.roundToInt()}% (>30%)",
                    description = "Pemakaian kartu kredit melebihi batas aman 30%. Segera cicil agar skor kredit dan beban bunga tetap rendah.",
                    severity = "CRITICAL"
                )
            )
        }

        // 5. Overbudget check
        val monthBudgets = budgets.filter { it.monthYear == monthKey }
        val monthExpenses = transactions.filter {
            CurrencyFormatter.currentMonthKey(it.dateMillis) == monthKey &&
                (it.type == TxTypes.EXPENSE || it.type == TxTypes.SPLIT)
        }
        monthBudgets.forEach { b ->
            val spent = monthExpenses.filter { it.categoryId == b.categoryId }.sumOf { it.amount }
            val effectiveLimit = b.limitAmount + if (b.rolloverEnabled) b.rolloverAmount else 0L
            if (effectiveLimit > 0L && spent >= effectiveLimit) {
                val catName = catById[b.categoryId]?.name ?: "Kategori"
                insights.add(
                    AdvisorInsight(
                        title = "Anggaran $catName Overbudget",
                        description = "Terpakai ${CurrencyFormatter.formatIdr(spent)} dari batas ${CurrencyFormatter.formatIdr(effectiveLimit)}.",
                        severity = "CRITICAL"
                    )
                )
            }
        }

        // 6. Emergency fund check
        if (emergencyMonths < 3.0) {
            insights.add(
                AdvisorInsight(
                    title = "Dana Darurat Baru ${String.format("%.1f", emergencyMonths)} Bulan",
                    description = "Idealnya dana likuid minimal 3-6 bulan pengeluaran rutin (${CurrencyFormatter.formatIdr(monthlyExpenseBaseline * 3)}).",
                    severity = "WARNING"
                )
            )
        }

        return FinancialHealthReport(
            score = finalScore,
            gradeLabel = gradeLabel,
            savingRatePercent = savingRate,
            emergencyFundMonths = emergencyMonths,
            debtToAssetRatio = debtToAsset,
            creditUtilizationPercent = creditUtil,
            latteFactorMonthly = latteTotal,
            latteFactorCount = latteCount,
            insights = insights
        )
    }
}

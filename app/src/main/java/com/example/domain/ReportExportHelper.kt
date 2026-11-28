package com.example.domain

import android.content.Context
import android.content.Intent
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.FileProvider
import com.example.data.local.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

object ReportExportHelper {

    fun exportTransactionsCsv(
        context: Context,
        workspaceName: String,
        transactions: List<TransactionEntity>,
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>,
        budgets: List<BudgetEntity> = emptyList(),
        monthKey: String = CurrencyFormatter.currentMonthKey()
    ): Intent? {
        return try {
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
            val safeWs = workspaceName.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "DompetZawa" }
            val file = File(exportDir, "DompetZawa_${safeWs}_$timestamp.csv")
            val accMap = accounts.associateBy { it.id }
            val catMap = categories.associateBy { it.id }
            val budgetReport = MonthlyBudgetReportCalculator.calculate(monthKey, transactions, budgets, categories)

            val sb = StringBuilder()
            sb.append("\uFEFF") // UTF-8 BOM for clean Excel Indonesian support

            // Section 1: Monthly Budget & Finance Summary
            sb.appendLine("RINGKASAN ANGGARAN BULANAN (${budgetReport.monthLabel})")
            sb.appendLine("Pemasukan Bulan Ini,${budgetReport.totalIncome}")
            sb.appendLine("Pengeluaran Bulan Ini,${budgetReport.totalExpense}")
            sb.appendLine("Sisa Uang (Untung / Kurang),${budgetReport.netCashFlow}")
            sb.appendLine("Total Batas Anggaran,${budgetReport.totalAllocatedBudget}")
            sb.appendLine("Total Terpakai Anggaran,${budgetReport.totalBudgetedSpent}")
            sb.appendLine("Sisa Anggaran,${budgetReport.totalRemainingBudget}")
            sb.appendLine()
            sb.appendLine("Kategori,Batas Anggaran (Rp),Sudah Terpakai (Rp),Sisa Anggaran (Rp),Pemakaian (%),Status")
            budgetReport.rows.forEach { row ->
                val statusLabel = when (row.status) {
                    BudgetStatusLevel.AMAN -> "Aman"
                    BudgetStatusLevel.WASPADA -> "Hampir Habis"
                    BudgetStatusLevel.OVERBUDGET -> "Melebihi Batas"
                    BudgetStatusLevel.UNBUDGETED -> "Tanpa Batas"
                }
                sb.appendLine(
                    listOf(
                        csvEscape(row.categoryName),
                        row.allocated.toString(),
                        row.spent.toString(),
                        row.remaining.toString(),
                        "${row.usagePercent.roundToInt()}%",
                        csvEscape(statusLabel)
                    ).joinToString(",")
                )
            }

            sb.appendLine()
            sb.appendLine("DAFTAR CATATAN TRANSAKSI")
            sb.appendLine("Tanggal,Jenis,Nama Transaksi,Kategori,Rekening / Dompet,Rekening Tujuan,Nominal (Rp),Biaya Admin,Keterangan Tambahan,Dicatat Oleh,Catatan,Status Simpan Online")
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            transactions.forEach { tx ->
                val date = sdf.format(Date(tx.dateMillis))
                val catName = catMap[tx.categoryId]?.name ?: "-"
                val accName = accMap[tx.accountId]?.name ?: "-"
                val toAccName = tx.toAccountId?.let { accMap[it]?.name } ?: "-"
                sb.appendLine(
                    listOf(
                        csvEscape(date),
                        csvEscape(CurrencyFormatter.txTypeLabel(tx.type)),
                        csvEscape(tx.title),
                        csvEscape(catName),
                        csvEscape(accName),
                        csvEscape(toAccName),
                        tx.amount.toString(),
                        tx.adminFee.toString(),
                        csvEscape(tx.tags),
                        csvEscape(tx.createdByName),
                        csvEscape(tx.note),
                        csvEscape(CurrencyFormatter.syncStatusLabel(tx.syncStatus))
                    ).joinToString(",")
                )
            }
            file.writeText(sb.toString(), Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Laporan Keuangan & Anggaran Dompet Zawa - $workspaceName")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun printFinancialStatementsPdf(
        context: Context,
        workspaceName: String,
        accounts: List<AccountEntity>,
        assets: List<AssetInvestmentEntity>,
        debtsLoans: List<DebtLoanEntity>,
        transactions: List<TransactionEntity>,
        categories: List<CategoryEntity>,
        budgets: List<BudgetEntity> = emptyList(),
        monthKey: String = CurrencyFormatter.currentMonthKey()
    ) {
        val liquidAssets = accounts.filter { it.type != AccountTypes.CREDIT_CARD }.sumOf { it.balance }
        val investments = assets.sumOf { it.currentValue }
        val receivables = debtsLoans.filter { it.kind == "LOAN" && !it.isPaidOff }.sumOf { it.remainingAmount }
        val totalAssets = liquidAssets + investments + receivables

        val ccDebt = accounts.filter { it.type == AccountTypes.CREDIT_CARD }.sumOf { it.balance }
        val loanDebt = debtsLoans.filter { it.kind == "DEBT" && !it.isPaidOff }.sumOf { it.remainingAmount }
        val totalLiabilities = ccDebt + loanDebt
        val netWorth = totalAssets - totalLiabilities

        val budgetReport = MonthlyBudgetReportCalculator.calculate(monthKey, transactions, budgets, categories)
        val totalIncome = budgetReport.totalIncome
        val totalExpense = budgetReport.totalExpense
        val netCashFlow = budgetReport.netCashFlow

        val catMap = categories.associateBy { it.id }
        val monthTx = transactions.filter { CurrencyFormatter.currentMonthKey(it.dateMillis) == monthKey }
        val expenseByCategory = monthTx
            .filter { it.type == TxTypes.EXPENSE || it.type == TxTypes.SPLIT }
            .groupBy { catMap[it.categoryId]?.name ?: "Lainnya" }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
            .entries
            .sortedByDescending { it.value }

        val html = buildString {
            append("""
                <!DOCTYPE html>
                <html>
                <head>
                <meta charset="utf-8" />
                <style>
                    body { font-family: sans-serif; color: #0F172A; padding: 24px; line-height: 1.5; }
                    h1 { color: #064E3B; margin-bottom: 4px; }
                    h2 { color: #065F46; border-bottom: 2px solid #D1FAE5; padding-bottom: 4px; margin-top: 24px; }
                    .meta { color: #64748B; font-size: 13px; margin-bottom: 20px; }
                    table { width: 100%; border-collapse: collapse; margin-top: 8px; }
                    th, td { border-bottom: 1px solid #E2E8F0; padding: 8px 6px; text-align: left; font-size: 13px; }
                    th { background: #ECFDF5; color: #064E3B; }
                    .num { text-align: right; font-family: monospace; font-weight: bold; }
                    .total-row { background: #F8FAFC; font-weight: bold; }
                    .status-ok { color: #059669; font-weight: bold; }
                    .status-warn { color: #D97706; font-weight: bold; }
                    .status-bad { color: #DC2626; font-weight: bold; }
                </style>
                </head>
                <body>
                <h1>Laporan Keuangan &amp; Anggaran Dompet Zawa</h1>
                <div class="meta">Ruang Keuangan: <strong>${htmlEscape(workspaceName)}</strong> &bull; Bulan: <strong>${htmlEscape(budgetReport.monthLabel)}</strong> &bull; Dicetak: ${CurrencyFormatter.formatDateShort(System.currentTimeMillis())}</div>

                <h2>1. Daftar Harta &amp; Utang</h2>
                <table>
                    <tr><th>Jenis Harta / Utang</th><th class="num">Jumlah (Rp)</th></tr>
                    <tr><td>Uang Tunai, Bank, Dompet Digital &amp; Deposito</td><td class="num">${CurrencyFormatter.formatIdr(liquidAssets)}</td></tr>
                    <tr><td>Harta Berharga (Emas, Saham, Properti, dll)</td><td class="num">${CurrencyFormatter.formatIdr(investments)}</td></tr>
                    <tr><td>Uang yang Dipinjam Orang (Piutang)</td><td class="num">${CurrencyFormatter.formatIdr(receivables)}</td></tr>
                    <tr class="total-row"><td>Total Harta</td><td class="num">${CurrencyFormatter.formatIdr(totalAssets)}</td></tr>
                    <tr><td>Tagihan Kartu Kredit</td><td class="num">${CurrencyFormatter.formatIdr(ccDebt)}</td></tr>
                    <tr><td>Pinjaman &amp; Cicilan Berjalan</td><td class="num">${CurrencyFormatter.formatIdr(loanDebt)}</td></tr>
                    <tr class="total-row"><td>Total Utang &amp; Cicilan</td><td class="num">${CurrencyFormatter.formatIdr(totalLiabilities)}</td></tr>
                    <tr class="total-row"><td>Total Harta Bersih (Setelah Dikurangi Utang)</td><td class="num">${CurrencyFormatter.formatIdr(netWorth)}</td></tr>
                </table>

                <h2>2. Pemasukan &amp; Pengeluaran (${htmlEscape(budgetReport.monthLabel)})</h2>
                <table>
                    <tr><th>Keterangan</th><th class="num">Jumlah (Rp)</th></tr>
                    <tr><td>Total Pemasukan</td><td class="num">${CurrencyFormatter.formatIdr(totalIncome)}</td></tr>
                    <tr><td>Total Pengeluaran</td><td class="num">${CurrencyFormatter.formatIdr(totalExpense)}</td></tr>
                    <tr class="total-row"><td>Sisa Uang (Untung / Kurang)</td><td class="num">${CurrencyFormatter.formatIdr(netCashFlow, showSign = true)}</td></tr>
                </table>

                <h2>3. Ringkasan Batas Belanja Bulanan (${htmlEscape(budgetReport.monthLabel)})</h2>
                <table>
                    <tr>
                        <th>Kategori</th>
                        <th class="num">Batas (Rp)</th>
                        <th class="num">Terpakai (Rp)</th>
                        <th class="num">Sisa (Rp)</th>
                        <th class="num">%</th>
                        <th>Status</th>
                    </tr>
            """.trimIndent())
            if (budgetReport.rows.isEmpty()) {
                append("<tr><td colspan=\"6\">Belum ada batas belanja atau pengeluaran pada bulan ini.</td></tr>")
            } else {
                budgetReport.rows.forEach { row ->
                    val (statusText, cssClass) = when (row.status) {
                        BudgetStatusLevel.AMAN -> "Aman" to "status-ok"
                        BudgetStatusLevel.WASPADA -> "Hampir Habis" to "status-warn"
                        BudgetStatusLevel.OVERBUDGET -> "Melebihi Batas" to "status-bad"
                        BudgetStatusLevel.UNBUDGETED -> "Tanpa Batas" to ""
                    }
                    val allocStr = if (row.allocated > 0L) CurrencyFormatter.formatIdr(row.allocated) else "-"
                    val remStr = if (row.allocated > 0L) CurrencyFormatter.formatIdr(row.remaining, showSign = true) else "-"
                    val pctStr = if (row.allocated > 0L) "${row.usagePercent.roundToInt()}%" else "-"
                    append(
                        "<tr><td>${htmlEscape(row.categoryName)}</td>" +
                            "<td class=\"num\">$allocStr</td>" +
                            "<td class=\"num\">${CurrencyFormatter.formatIdr(row.spent)}</td>" +
                            "<td class=\"num\">$remStr</td>" +
                            "<td class=\"num\">$pctStr</td>" +
                            "<td class=\"$cssClass\">$statusText</td></tr>"
                    )
                }
                append(
                    "<tr class=\"total-row\"><td>Total Batas Belanja Terpantau</td>" +
                        "<td class=\"num\">${CurrencyFormatter.formatIdr(budgetReport.totalAllocatedBudget)}</td>" +
                        "<td class=\"num\">${CurrencyFormatter.formatIdr(budgetReport.totalBudgetedSpent)}</td>" +
                        "<td class=\"num\">${CurrencyFormatter.formatIdr(budgetReport.totalRemainingBudget, showSign = true)}</td>" +
                        "<td class=\"num\">${budgetReport.overallUsagePercent.roundToInt()}%</td>" +
                        "<td>-</td></tr>"
                )
            }
            append("""
                </table>

                <h2>4. Rincian Pengeluaran per Kategori</h2>
                <table>
                    <tr><th>Kategori</th><th class="num">Total (IDR)</th></tr>
            """.trimIndent())
            if (expenseByCategory.isEmpty()) {
                append("<tr><td colspan=\"2\">Belum ada pengeluaran pada bulan ini.</td></tr>")
            } else {
                expenseByCategory.forEach { (cat, amt) ->
                    append("<tr><td>${htmlEscape(cat)}</td><td class=\"num\">${CurrencyFormatter.formatIdr(amt)}</td></tr>")
                }
            }
            append("""
                </table>
                </body>
                </html>
            """.trimIndent())
        }

        val webView = WebView(context)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
                val jobName = "DompetZawa_${workspaceName}_Laporan_$monthKey"
                val adapter = webView.createPrintDocumentAdapter(jobName)
                printManager.print(jobName, adapter, PrintAttributes.Builder().build())
            }
        }
        webView.loadDataWithBaseURL(null, html, "text/HTML", "UTF-8", null)
    }

    private fun csvEscape(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    private fun htmlEscape(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}

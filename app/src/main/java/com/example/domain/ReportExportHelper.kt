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

object ReportExportHelper {

    fun exportTransactionsCsv(
        context: Context,
        workspaceName: String,
        transactions: List<TransactionEntity>,
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>
    ): Intent? {
        return try {
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
            val file = File(exportDir, "DompetZawa_${workspaceName.replace(" ", "_")}_$timestamp.csv")
            val accMap = accounts.associateBy { it.id }
            val catMap = categories.associateBy { it.id }

            val sb = StringBuilder()
            sb.append("\uFEFF") // UTF-8 BOM for clean Excel Indonesian support
            sb.appendLine("Tanggal,Tipe,Judul,Kategori,Akun,Akun Tujuan,Nominal (IDR),Biaya Admin,Tag,Pencatat,Catatan,Status Sync")
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            transactions.forEach { tx ->
                val date = sdf.format(Date(tx.dateMillis))
                val catName = catMap[tx.categoryId]?.name ?: "-"
                val accName = accMap[tx.accountId]?.name ?: "-"
                val toAccName = tx.toAccountId?.let { accMap[it]?.name } ?: "-"
                sb.appendLine(
                    listOf(
                        csvEscape(date),
                        csvEscape(tx.type),
                        csvEscape(tx.title),
                        csvEscape(catName),
                        csvEscape(accName),
                        csvEscape(toAccName),
                        tx.amount.toString(),
                        tx.adminFee.toString(),
                        csvEscape(tx.tags),
                        csvEscape(tx.createdByName),
                        csvEscape(tx.note),
                        csvEscape(tx.syncStatus)
                    ).joinToString(",")
                )
            }
            file.writeText(sb.toString(), Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Laporan Keuangan Dompet Zawa - $workspaceName")
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
        categories: List<CategoryEntity>
    ) {
        val liquidAssets = accounts.filter { it.type != AccountTypes.CREDIT_CARD }.sumOf { it.balance }
        val investments = assets.sumOf { it.currentValue }
        val receivables = debtsLoans.filter { it.kind == "LOAN" && !it.isPaidOff }.sumOf { it.remainingAmount }
        val totalAssets = liquidAssets + investments + receivables

        val ccDebt = accounts.filter { it.type == AccountTypes.CREDIT_CARD }.sumOf { it.balance }
        val loanDebt = debtsLoans.filter { it.kind == "DEBT" && !it.isPaidOff }.sumOf { it.remainingAmount }
        val totalLiabilities = ccDebt + loanDebt
        val netWorth = totalAssets - totalLiabilities

        val monthKey = CurrencyFormatter.currentMonthKey()
        val monthTx = transactions.filter { CurrencyFormatter.currentMonthKey(it.dateMillis) == monthKey }
        val totalIncome = monthTx.filter { it.type == TxTypes.INCOME }.sumOf { it.amount }
        val totalExpense = monthTx.filter { it.type == TxTypes.EXPENSE || it.type == TxTypes.SPLIT }.sumOf { it.amount }
        val netCashFlow = totalIncome - totalExpense

        val catMap = categories.associateBy { it.id }
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
                </style>
                </head>
                <body>
                <h1>Laporan Keuangan Dompet Zawa</h1>
                <div class="meta">Workspace: <strong>${htmlEscape(workspaceName)}</strong> &bull; Dicetak: ${CurrencyFormatter.formatDateShort(System.currentTimeMillis())}</div>

                <h2>1. Neraca Keuangan (Balance Sheet)</h2>
                <table>
                    <tr><th>Pos Akun</th><th class="num">Nilai (IDR)</th></tr>
                    <tr><td>Kas, Bank, E-Wallet & Deposito</td><td class="num">${CurrencyFormatter.formatIdr(liquidAssets)}</td></tr>
                    <tr><td>Aset Fisik & Portofolio Investasi</td><td class="num">${CurrencyFormatter.formatIdr(investments)}</td></tr>
                    <tr><td>Piutang Lancar</td><td class="num">${CurrencyFormatter.formatIdr(receivables)}</td></tr>
                    <tr class="total-row"><td>Total Aset</td><td class="num">${CurrencyFormatter.formatIdr(totalAssets)}</td></tr>
                    <tr><td>Tagihan Kartu Kredit</td><td class="num">${CurrencyFormatter.formatIdr(ccDebt)}</td></tr>
                    <tr><td>Pinjaman & Cicilan Berjalan</td><td class="num">${CurrencyFormatter.formatIdr(loanDebt)}</td></tr>
                    <tr class="total-row"><td>Total Kewajiban</td><td class="num">${CurrencyFormatter.formatIdr(totalLiabilities)}</td></tr>
                    <tr class="total-row"><td>Kekayaan Bersih (Ekuitas / Net Worth)</td><td class="num">${CurrencyFormatter.formatIdr(netWorth)}</td></tr>
                </table>

                <h2>2. Laba / Rugi &amp; Arus Kas (${CurrencyFormatter.monthLabel(monthKey)})</h2>
                <table>
                    <tr><th>Komponen</th><th class="num">Nominal (IDR)</th></tr>
                    <tr><td>Total Pemasukan</td><td class="num">${CurrencyFormatter.formatIdr(totalIncome)}</td></tr>
                    <tr><td>Total Pengeluaran</td><td class="num">${CurrencyFormatter.formatIdr(totalExpense)}</td></tr>
                    <tr class="total-row"><td>Surplus / Defisit Arus Kas</td><td class="num">${CurrencyFormatter.formatIdr(netCashFlow, showSign = true)}</td></tr>
                </table>

                <h2>3. Rincian Beban per Kategori</h2>
                <table>
                    <tr><th>Kategori</th><th class="num">Total (IDR)</th></tr>
            """.trimIndent())
            expenseByCategory.forEach { (cat, amt) ->
                append("<tr><td>${htmlEscape(cat)}</td><td class=\"num\">${CurrencyFormatter.formatIdr(amt)}</td></tr>")
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
                val jobName = "DompetZawa_${workspaceName}_Laporan"
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

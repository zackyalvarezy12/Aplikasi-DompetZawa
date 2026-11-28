package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.AccountTypes
import com.example.domain.AiAssistantUiState
import com.example.domain.BudgetStatusLevel
import com.example.domain.CurrencyFormatter
import com.example.domain.FinancialCalculators
import com.example.domain.MonthlyBudgetReportCalculator
import com.example.domain.ReportExportHelper
import com.example.ui.MainUiState
import com.example.ui.components.SvgLogo
import com.example.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun AdvisorReportsScreen(
    state: MainUiState,
    onOpenSyncSecurity: () -> Unit,
    onNotify: (String) -> Unit,
    aiAnalysisState: AiAssistantUiState = AiAssistantUiState.Idle,
    aiStreamingText: String = "",
    onRunDeepAiAnalysis: () -> Unit = {},
    onOpenZawaAiChat: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val stealth = state.settings.isStealthMode
    var subTab by rememberSaveable { mutableStateOf(0) } // 0: Saran & Analisa, 1: Hitungan, 2: Laporan
    val subTabLabels = remember { listOf("Saran & Analisa", "Hitungan", "Laporan") }

    Column(modifier = modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            subTabLabels.forEachIndexed { idx, label ->
                SegmentedButton(
                    selected = subTab == idx,
                    onClick = { subTab = idx },
                    shape = SegmentedButtonDefaults.itemShape(idx, 3)
                ) {
                    Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        when (subTab) {
            // SUBTAB 0: OFFLINE FINANCIAL ADVISOR (RULE-BASED AI ENGINE)
            0 -> {
                val listState = rememberLazyListState()
                val report = state.advisorReport
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item(key = "zawa_ai_live_card", contentType = "zawa_ai_live") {
                        val isRunning = aiAnalysisState is AiAssistantUiState.Loading || aiAnalysisState is AiAssistantUiState.Streaming
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    SvgLogo(
                                        resId = R.drawable.ic_logo_advisor_ai,
                                        contentDescription = "Ikon Zawa AI",
                                        size = 42.dp
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Asisten Zawa • Cek Kondisi Keuangan",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Bantu cek kondisi keuangan, saran hemat, pengingat tagihan & catat otomatis.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.86f)
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = onOpenZawaAiChat,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("advisor_open_ai_chat_button")
                                    ) {
                                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Tanya & Atur Otomatis", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    FilledTonalButton(
                                        onClick = onRunDeepAiAnalysis,
                                        enabled = !isRunning,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("advisor_stream_analysis_button")
                                    ) {
                                        if (isRunning) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        } else {
                                            Icon(Icons.Filled.Insights, contentDescription = null, modifier = Modifier.size(18.dp))
                                        }
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = if (isRunning) "Sedang Memeriksa..." else "Periksa Lengkap",
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                if (aiAnalysisState is AiAssistantUiState.Error) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Text(
                                                text = aiAnalysisState.message,
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                } else if (aiStreamingText.isNotBlank()) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                        contentColor = MaterialTheme.colorScheme.onSurface,
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            text = aiStreamingText,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (report != null) {
                        item(key = "advisor_score_card", contentType = "score") {
                            val scoreColor = when {
                                report.score >= 80 -> IncomeGreen
                                report.score >= 60 -> TransferBlue
                                report.score >= 45 -> WarningAmber
                                else -> ExpenseRed
                            }
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            SvgLogo(resId = R.drawable.ic_logo_advisor_ai, contentDescription = null, size = 44.dp)
                                            Spacer(Modifier.width(10.dp))
                                            Column {
                                                Text("Nilai Kesehatan Keuangan", style = MaterialTheme.typography.titleSmall)
                                                Text(report.gradeLabel, style = MaterialTheme.typography.titleMedium, color = scoreColor, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        Surface(color = scoreColor.copy(alpha = 0.14f), shape = RoundedCornerShape(14.dp)) {
                                            Text(
                                                "${report.score}/100",
                                                style = MaterialTheme.typography.headlineSmall,
                                                color = scoreColor,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                            )
                                        }
                                    }
                                    LinearProgressIndicator(
                                        progress = { report.score / 100f },
                                        color = scoreColor,
                                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                                    )
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Uang Ditabung: ${report.savingRatePercent.roundToInt()}%", style = MaterialTheme.typography.bodySmall)
                                        Text("Utang vs Harta: ${report.debtToAssetRatio.roundToInt()}%", style = MaterialTheme.typography.bodySmall)
                                        Text("Dana Darurat: ${String.format("%.1f", report.emergencyFundMonths)} bln", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }

                        item(key = "advisor_insights_header", contentType = "section_header") {
                            Text("Belanja Melonjak, Jajan Kecil & Saran Hemat", style = MaterialTheme.typography.titleMedium)
                        }
                        items(
                            items = report.insights,
                            key = { "${it.severity}_${it.title}" },
                            contentType = { "advisor_insight" }
                        ) { insight ->
                            val (bg, onBg, icon) = when (insight.severity) {
                                "GOOD" -> Triple(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, Icons.Filled.CheckCircle)
                                "CRITICAL" -> Triple(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, Icons.Filled.Warning)
                                "WARNING" -> Triple(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer, Icons.Filled.Info)
                                else -> Triple(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, Icons.Outlined.AutoAwesome)
                            }
                            Card(colors = CardDefaults.cardColors(containerColor = bg)) {
                                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                                    Icon(icon, contentDescription = null, tint = onBg)
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(insight.title, style = MaterialTheme.typography.titleSmall, color = onBg)
                                        Spacer(Modifier.height(2.dp))
                                        Text(insight.description, style = MaterialTheme.typography.bodySmall, color = onBg.copy(alpha = 0.85f))
                                        if (insight.potentialMonthlySaving > 0L) {
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                "Potensi hemat: ${CurrencyFormatter.formatIdr(insight.potentialMonthlySaving, stealth)}/bln",
                                                style = MoneySmallStyle,
                                                color = onBg,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item(key = "sync_sec_btn", contentType = "action") {
                        OutlinedButton(
                            onClick = onOpenSyncSecurity,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Atur Profil, Kunci Layar & Cadangan Google")
                        }
                    }
                }
            }

            // SUBTAB 1: 4 HITUNGAN KEUANGAN
            1 -> {
                val listState = rememberLazyListState()
                var calcMode by rememberSaveable { mutableStateOf(0) } // 0: Darurat, 1: Tabungan, 2: Cicilan Rumah, 3: Zakat
                val calcLabels = remember { listOf("Darurat", "Tabungan", "Cicilan", "Zakat") }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item(key = "calc_selector", contentType = "selector") {
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            calcLabels.forEachIndexed { idx, lbl ->
                                SegmentedButton(
                                    selected = calcMode == idx,
                                    onClick = { calcMode = idx },
                                    shape = SegmentedButtonDefaults.itemShape(idx, 4)
                                ) {
                                    Text(lbl, maxLines = 1, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }

                    item(key = "calc_body_$calcMode", contentType = "calculator") {
                        when (calcMode) {
                            0 -> EmergencyCalculatorCard(state.monthExpense.coerceAtLeast(3_500_000L), stealth)
                            1 -> CompoundCalculatorCard(stealth)
                            2 -> KprCalculatorCard(stealth)
                            else -> ZakatCalculatorCard(
                                initialWealth = (state.totalLiquidCash + state.totalInvestments - state.totalDebts).coerceAtLeast(0L),
                                initialIncome = state.monthIncome.coerceAtLeast(10_000_000L),
                                stealth = stealth
                            )
                        }
                    }
                }
            }

            // SUBTAB 2: LAPORAN KEUANGAN & RINGKASAN ANGGARAN BULANAN + PDF & CSV
            else -> {
                val listState = rememberLazyListState()
                val availableMonths = remember(state.transactions, state.budgets) {
                    MonthlyBudgetReportCalculator.availableMonthKeys(state.transactions, state.budgets)
                }
                var selectedMonthKey by rememberSaveable { mutableStateOf(CurrencyFormatter.currentMonthKey()) }
                if (selectedMonthKey !in availableMonths && availableMonths.isNotEmpty()) {
                    selectedMonthKey = availableMonths.first()
                }
                val budgetReport = remember(selectedMonthKey, state.transactions, state.budgets, state.categories) {
                    MonthlyBudgetReportCalculator.calculate(
                        monthKey = selectedMonthKey,
                        transactions = state.transactions,
                        budgets = state.budgets,
                        categories = state.categories
                    )
                }

                val balanceSheet = remember(state.accounts, state.assets, state.debtsLoans) {
                    val liq = state.accounts.filter { it.type != AccountTypes.CREDIT_CARD }.sumOf { it.balance }
                    val inv = state.assets.sumOf { it.currentValue }
                    val rec = state.debtsLoans.filter { it.kind == "LOAN" && !it.isPaidOff }.sumOf { it.remainingAmount }
                    val cc = state.accounts.filter { it.type == AccountTypes.CREDIT_CARD }.sumOf { it.balance }
                    val loan = state.debtsLoans.filter { it.kind == "DEBT" && !it.isPaidOff }.sumOf { it.remainingAmount }
                    BalanceSheetTotals(liq, inv, rec, liq + inv + rec, cc, loan, cc + loan)
                }
                val liquidAssets = balanceSheet.liquidAssets
                val investments = balanceSheet.investments
                val receivables = balanceSheet.receivables
                val totalAssets = balanceSheet.totalAssets
                val ccDebt = balanceSheet.ccDebt
                val loanDebt = balanceSheet.loanDebt
                val totalLiabilities = balanceSheet.totalLiabilities

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item(key = "month_filter_row", contentType = "filter") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(availableMonths, key = { it }) { mKey ->
                                FilterChip(
                                    selected = selectedMonthKey == mKey,
                                    onClick = { selectedMonthKey = mKey },
                                    label = { Text(CurrencyFormatter.monthLabel(mKey)) }
                                )
                            }
                        }
                    }

                    item(key = "export_actions", contentType = "actions") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = {
                                    ReportExportHelper.printFinancialStatementsPdf(
                                        context = context,
                                        workspaceName = state.activeWorkspace?.name ?: "Dompet Zawa",
                                        accounts = state.accounts,
                                        assets = state.assets,
                                        debtsLoans = state.debtsLoans,
                                        transactions = state.transactions,
                                        categories = state.categories,
                                        budgets = state.budgets,
                                        monthKey = selectedMonthKey
                                    )
                                },
                                modifier = Modifier.weight(1f).testTag("print_pdf_button")
                            ) {
                                Icon(Icons.Outlined.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Cetak / Simpan PDF")
                            }
                            FilledTonalButton(
                                onClick = {
                                    val intent = ReportExportHelper.exportTransactionsCsv(
                                        context = context,
                                        workspaceName = state.activeWorkspace?.name ?: "Dompet Zawa",
                                        transactions = state.transactions,
                                        accounts = state.accounts,
                                        categories = state.categories,
                                        budgets = state.budgets,
                                        monthKey = selectedMonthKey
                                    )
                                    if (intent != null) {
                                        context.startActivity(android.content.Intent.createChooser(intent, "Simpan Tabel Laporan Dompet Zawa"))
                                    } else {
                                        onNotify("Gagal menyimpan file tabel (CSV).")
                                    }
                                },
                                modifier = Modifier.weight(1f).testTag("export_csv_button")
                            ) {
                                Icon(Icons.Outlined.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Simpan Tabel (CSV)")
                            }
                        }
                    }

                    // 0. Ringkasan Anggaran Bulanan (Interactive Budget Report)
                    item(key = "monthly_budget_summary", contentType = "statement") {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.testTag("monthly_budget_summary_card")
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    SvgLogo(resId = R.drawable.ic_logo_cat_bills, contentDescription = null, size = 32.dp)
                                    Column {
                                        Text("Ringkasan Batas Belanja Bulanan", style = MaterialTheme.typography.titleMedium)
                                        Text(budgetReport.monthLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                StatementRow("Total Batas Belanja", budgetReport.totalAllocatedBudget, stealth)
                                StatementRow("Sudah Terpakai", budgetReport.totalBudgetedSpent, stealth, valueColor = ExpenseRed)
                                StatementRow(
                                    "Sisa Batas Belanja",
                                    budgetReport.totalRemainingBudget,
                                    stealth,
                                    bold = true,
                                    valueColor = if (budgetReport.totalRemainingBudget >= 0L) IncomeGreen else ExpenseRed
                                )
                                if (budgetReport.totalAllocatedBudget > 0L) {
                                    LinearProgressIndicator(
                                        progress = { (budgetReport.overallUsagePercent / 100.0).toFloat().coerceIn(0f, 1f) },
                                        color = when {
                                            budgetReport.overallUsagePercent >= 100.0 -> ExpenseRed
                                            budgetReport.overallUsagePercent >= 75.0 -> WarningAmber
                                            else -> IncomeGreen
                                        },
                                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                                    )
                                }
                                if (budgetReport.rows.isEmpty()) {
                                    Text(
                                        "Belum ada batas belanja atau pengeluaran tercatat pada bulan ${budgetReport.monthLabel}.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    HorizontalDivider()
                                    budgetReport.rows.forEach { row ->
                                        val (statusText, statusColor) = when (row.status) {
                                            BudgetStatusLevel.AMAN -> "Aman" to IncomeGreen
                                            BudgetStatusLevel.WASPADA -> "Hampir Habis" to WarningAmber
                                            BudgetStatusLevel.OVERBUDGET -> "Melebihi Batas" to ExpenseRed
                                            BudgetStatusLevel.UNBUDGETED -> "Tanpa Batas" to MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                Text(row.categoryName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                                Surface(color = statusColor.copy(alpha = 0.14f), shape = RoundedCornerShape(6.dp)) {
                                                    Text(
                                                        text = if (row.allocated > 0L) "$statusText (${row.usagePercent.roundToInt()}%)" else statusText,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = statusColor,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("Terpakai: ${CurrencyFormatter.formatIdr(row.spent, stealth)}", style = MoneySmallStyle)
                                                Text(
                                                    if (row.allocated > 0L) "Batas: ${CurrencyFormatter.formatIdr(row.allocated, stealth)}" else "Belum diatur batas",
                                                    style = MoneySmallStyle,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 1. Neraca (Balance Sheet)
                    item(key = "balance_sheet", contentType = "statement") {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    SvgLogo(resId = R.drawable.ic_logo_report_ledger, contentDescription = null, size = 32.dp)
                                    Text("Daftar Harta & Utang", style = MaterialTheme.typography.titleMedium)
                                }
                                StatementRow("Uang Tunai, Bank, Dompet Digital & Deposito", liquidAssets, stealth)
                                StatementRow("Harta Berharga (Emas, Saham, Properti)", investments, stealth)
                                StatementRow("Uang Dipinjam Orang (Piutang)", receivables, stealth)
                                HorizontalDivider()
                                StatementRow("Total Harta", totalAssets, stealth, bold = true, valueColor = IncomeGreen)
                                Spacer(Modifier.height(4.dp))
                                StatementRow("Tagihan Kartu Kredit", ccDebt, stealth)
                                StatementRow("Pinjaman & Cicilan", loanDebt, stealth)
                                HorizontalDivider()
                                StatementRow("Total Utang & Cicilan", totalLiabilities, stealth, bold = true, valueColor = ExpenseRed)
                                StatementRow("Total Harta Bersih", totalAssets - totalLiabilities, stealth, bold = true, valueColor = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    // 2. Pemasukan & Pengeluaran
                    item(key = "income_statement", contentType = "statement") {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    SvgLogo(resId = R.drawable.ic_logo_asset_stocks, contentDescription = null, size = 30.dp)
                                    Text("Pemasukan & Pengeluaran (${budgetReport.monthLabel})", style = MaterialTheme.typography.titleMedium)
                                }
                                StatementRow("Total Pemasukan", budgetReport.totalIncome, stealth, valueColor = IncomeGreen)
                                StatementRow("Total Pengeluaran", budgetReport.totalExpense, stealth, valueColor = ExpenseRed)
                                HorizontalDivider()
                                StatementRow("Sisa Uang (Untung / Kurang)", budgetReport.netCashFlow, stealth, bold = true, valueColor = if (budgetReport.netCashFlow >= 0) IncomeGreen else ExpenseRed)
                            }
                        }
                    }

                    // 3. Perbandingan Bulanan
                    item(key = "mom_comparison", contentType = "statement") {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Perbandingan Masuk & Keluar 6 Bulan", style = MaterialTheme.typography.titleMedium)
                                state.monthlyBars.reversed().forEach { bar ->
                                    val net = bar.income - bar.expense
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(bar.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "+${CurrencyFormatter.formatCompactIdr(bar.income, stealth)} / -${CurrencyFormatter.formatCompactIdr(bar.expense, stealth)} (${CurrencyFormatter.formatCompactIdr(net, stealth)})",
                                            style = MoneySmallStyle,
                                            color = if (net >= 0) IncomeGreen else ExpenseRed
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatementRow(
    label: String,
    amount: Long,
    stealth: Boolean,
    bold: Boolean = false,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text(
            CurrencyFormatter.formatIdr(amount, stealth),
            style = MoneySmallStyle,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
            color = valueColor
        )
    }
}

@Composable
private fun EmergencyCalculatorCard(defaultExpense: Long, stealth: Boolean) {
    var expenseStr by remember { mutableStateOf(defaultExpense.toString()) }
    var dependents by remember { mutableStateOf(1) }
    var isFreelance by remember { mutableStateOf(false) }
    val (months, target) = remember(expenseStr, dependents, isFreelance) {
        FinancialCalculators.calculateEmergencyFund(
            expenseStr.toLongOrNull() ?: 0L, dependents, isFreelance
        )
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SvgLogo(resId = R.drawable.ic_logo_debt_shield, contentDescription = null, size = 32.dp)
                Text("Hitungan Dana Darurat Ideal", style = MaterialTheme.typography.titleMedium)
            }
            OutlinedTextField(
                value = expenseStr,
                onValueChange = { expenseStr = it.filter(Char::isDigit) },
                label = { Text("Pengeluaran Wajib / Bulan (Rp)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text("Jumlah Tanggungan: $dependents orang", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (0..3).forEach { d ->
                    FilterChip(selected = dependents == d, onClick = { dependents = d }, label = { Text("$d") })
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Penghasilan Tidak Tetap / Harian")
                Switch(checked = isFreelance, onCheckedChange = { isFreelance = it })
            }
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Text("Saran Tabungan Darurat: $months Bulan Pengeluaran", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(CurrencyFormatter.formatIdr(target, stealth), style = MoneyDisplayStyle, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
    }
}

@Composable
private fun CompoundCalculatorCard(stealth: Boolean) {
    var initStr by remember { mutableStateOf("10000000") }
    var monthlyStr by remember { mutableStateOf("2000000") }
    var rateStr by remember { mutableStateOf("10") }
    var yearsStr by remember { mutableStateOf("10") }
    val res = remember(initStr, monthlyStr, rateStr, yearsStr) {
        FinancialCalculators.calculateCompoundInterest(
            initStr.toLongOrNull() ?: 0L,
            monthlyStr.toLongOrNull() ?: 0L,
            rateStr.toDoubleOrNull() ?: 0.0,
            yearsStr.toIntOrNull() ?: 5
        )
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SvgLogo(resId = R.drawable.ic_logo_asset_stocks, contentDescription = null, size = 32.dp)
                Text("Hitungan Tabungan Berkembang", style = MaterialTheme.typography.titleMedium)
            }
            OutlinedTextField(value = initStr, onValueChange = { initStr = it.filter(Char::isDigit) }, label = { Text("Uang Awal (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = monthlyStr, onValueChange = { monthlyStr = it.filter(Char::isDigit) }, label = { Text("Tabung Rutin / Bulan (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = rateStr, onValueChange = { rateStr = it }, label = { Text("Perkiraan Untung %/thn") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(value = yearsStr, onValueChange = { yearsStr = it.filter(Char::isDigit) }, label = { Text("Lama (Tahun)") }, modifier = Modifier.weight(1f), singleLine = true)
            }
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Perkiraan Hasil Akhir", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(CurrencyFormatter.formatIdr(res.finalValue, stealth), style = MoneyDisplayStyle, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("Total Uang Disetor: ${CurrencyFormatter.formatIdr(res.totalPrincipal, stealth)}", style = MoneySmallStyle, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f))
                    Text("Tambahan Keuntungan: ${CurrencyFormatter.formatIdr(res.totalInterest, stealth)}", style = MoneySmallStyle, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
    }
}

@Composable
private fun KprCalculatorCard(stealth: Boolean) {
    var principalStr by remember { mutableStateOf("500000000") }
    var rateStr by remember { mutableStateOf("8.5") }
    var tenorStr by remember { mutableStateOf("15") }
    val res = remember(principalStr, rateStr, tenorStr) {
        FinancialCalculators.calculateKprFlatVsEffective(
            principalStr.toLongOrNull() ?: 0L,
            rateStr.toDoubleOrNull() ?: 0.0,
            tenorStr.toIntOrNull() ?: 10
        )
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SvgLogo(resId = R.drawable.ic_logo_asset_property, contentDescription = null, size = 32.dp)
                Text("Hitungan Cicilan Rumah (KPR): Tetap vs Menurun", style = MaterialTheme.typography.titleMedium)
            }
            OutlinedTextField(value = principalStr, onValueChange = { principalStr = it.filter(Char::isDigit) }, label = { Text("Jumlah Pinjaman (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = rateStr, onValueChange = { rateStr = it }, label = { Text("Bunga %/thn") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(value = tenorStr, onValueChange = { tenorStr = it.filter(Char::isDigit) }, label = { Text("Lama Cicilan (Thn)") }, modifier = Modifier.weight(1f), singleLine = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                    Column(Modifier.padding(10.dp)) {
                        Text("Cicilan Tetap", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        Text("${CurrencyFormatter.formatIdr(res.flatMonthlyInstallment, stealth)}/bln", style = MoneySmallStyle, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        Text("Total Bunga: ${CurrencyFormatter.formatCompactIdr(res.flatTotalInterest, stealth)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f))
                    }
                }
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                    Column(Modifier.padding(10.dp)) {
                        Text("Cicilan Menurun", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("${CurrencyFormatter.formatIdr(res.effectiveMonthlyInstallment, stealth)}/bln", style = MoneySmallStyle, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("Total Bunga: ${CurrencyFormatter.formatCompactIdr(res.effectiveTotalInterest, stealth)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ZakatCalculatorCard(initialWealth: Long, initialIncome: Long, stealth: Boolean) {
    var wealthStr by remember { mutableStateOf(initialWealth.toString()) }
    var incomeStr by remember { mutableStateOf(initialIncome.toString()) }
    var goldStr by remember { mutableStateOf("1520000") }
    val res = remember(wealthStr, incomeStr, goldStr) {
        FinancialCalculators.calculateZakat(
            wealthStr.toLongOrNull() ?: 0L,
            incomeStr.toLongOrNull() ?: 0L,
            goldStr.toLongOrNull() ?: 1_520_000L
        )
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SvgLogo(resId = R.drawable.ic_logo_asset_gold, contentDescription = null, size = 32.dp)
                Text("Hitungan Zakat Harta & Penghasilan", style = MaterialTheme.typography.titleMedium)
            }
            OutlinedTextField(value = wealthStr, onValueChange = { wealthStr = it.filter(Char::isDigit) }, label = { Text("Total Harta Bersih (Terisi Otomatis)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = incomeStr, onValueChange = { incomeStr = it.filter(Char::isDigit) }, label = { Text("Penghasilan Bulanan (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = goldStr, onValueChange = { goldStr = it.filter(Char::isDigit) }, label = { Text("Harga Emas / Gram Saat Ini (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())

            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Zakat Maal (2.5% • Nisab ${CurrencyFormatter.formatCompactIdr(res.nisabMaalThreshold, stealth)})", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(
                        if (res.isMaalEligible) CurrencyFormatter.formatIdr(res.zakatMaalDue, stealth) else "Belum Mencapai Nisab",
                        style = MoneyMediumStyle,
                        color = if (res.isMaalEligible) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.65f)
                    )
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Text("Zakat Penghasilan/bln (2.5% • Nisab ${CurrencyFormatter.formatCompactIdr(res.nisabPenghasilanMonthly, stealth)})", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(
                        if (res.isPenghasilanEligible) CurrencyFormatter.formatIdr(res.zakatPenghasilanMonthly, stealth) else "Belum Mencapai Nisab",
                        style = MoneyMediumStyle,
                        color = if (res.isPenghasilanEligible) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.65f)
                    )
                }
            }
        }
    }
}

private data class BalanceSheetTotals(
    val liquidAssets: Long,
    val investments: Long,
    val receivables: Long,
    val totalAssets: Long,
    val ccDebt: Long,
    val loanDebt: Long,
    val totalLiabilities: Long
)

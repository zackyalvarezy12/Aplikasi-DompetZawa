package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.rounded.SwipeLeft
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.*
import com.example.domain.CurrencyFormatter
import com.example.ui.CategorySpendSlice
import com.example.ui.MainUiState
import com.example.ui.MonthlyBarPoint
import com.example.ui.components.*
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.roundToInt

private val QUICK_INPUT_EXAMPLES = listOf(
    "Kopi 25rb GoPay",
    "Makan siang 40rb tunai",
    "Gaji 5jt BCA"
)

private val DAILY_CHEER_TIPS = listOf(
    "Sisihkan 10% di awal gajian sebelum belanja ya!",
    "Catat jajan kecil supaya saldo nggak bocor halus~",
    "Cek target tabunganmu minggu ini biar makin dekat!",
    "Keuangan sehat = pikiran tenang & tidur nyenyak 😴✨"
)

@Composable
fun DashboardScreen(
    state: MainUiState,
    onQuickInputSubmit: (String) -> Boolean,
    onOpenNewTransaction: () -> Unit,
    onOpenThemePicker: () -> Unit,
    onDismissGuide: () -> Unit,
    onNavigateToLedger: () -> Unit,
    onNavigateToWealth: () -> Unit,
    onNavigateToGroup: () -> Unit,
    onPayRecurring: (RecurringRuleEntity) -> Unit,
    onDeleteTransaction: (TransactionEntity) -> Unit,
    onOpenAutoSalarySetup: () -> Unit,
    onOpenPayrollPopup: () -> Unit,
    onNavigateToAdvisor: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stealth = state.settings.isStealthMode
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val showScrollTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 1 }
    }
    var heroCelebrationCount by remember { mutableIntStateOf(0) }
    var cheerTipIndex by remember { mutableIntStateOf(0) }
    var historyFilter by rememberSaveable { mutableStateOf("ALL") }
    val greetingTitle = remember(state.activeMember?.name, state.currentUser?.name) {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val name = state.activeMember?.name ?: state.currentUser?.name ?: "Sahabat"
        when (hour) {
            in 4..10 -> "Selamat Pagi Ceria, $name! ☀️"
            in 11..14 -> "Semangat Siang, $name! 🌤️"
            in 15..18 -> "Sore Santai, $name! 🌅"
            else -> "Malam Tenang, $name! 🌙"
        }
    }

    val dueSoon = remember(state.recurringRules) {
        val threshold = System.currentTimeMillis() + 5L * 24 * 60 * 60 * 1000L
        state.recurringRules.filter { it.isActive && it.nextDueDateMillis <= threshold }
    }

    val criticalBudgets = remember(state.budgets, state.transactions) {
        val monthKey = CurrencyFormatter.currentMonthKey()
        val spentByCat = HashMap<String, Long>()
        state.transactions.forEach { tx ->
            if ((tx.type == TxTypes.EXPENSE || tx.type == TxTypes.SPLIT) &&
                CurrencyFormatter.currentMonthKey(tx.dateMillis) == monthKey
            ) {
                spentByCat[tx.categoryId] = (spentByCat[tx.categoryId] ?: 0L) + tx.amount
            }
        }
        state.budgets.filter { it.monthYear == monthKey }.mapNotNull { b ->
            val spent = spentByCat[b.categoryId] ?: 0L
            val limit = b.limitAmount + if (b.rolloverEnabled) b.rolloverAmount else 0L
            val ratio = if (limit > 0L) spent.toFloat() / limit else 0f
            if (ratio >= 0.75f) Triple(b, spent, limit) else null
        }
    }

    val historyTransactions = remember(state.pagedTransactions, state.transactions, historyFilter) {
        val base = if (state.pagedTransactions.isNotEmpty()) state.pagedTransactions else state.transactions
        val filtered = when (historyFilter) {
            TxTypes.EXPENSE -> base.filter { it.type == TxTypes.EXPENSE || it.type == TxTypes.SPLIT }
            TxTypes.INCOME -> base.filter { it.type == TxTypes.INCOME || it.type == TxTypes.REFUND }
            TxTypes.TRANSFER -> base.filter { it.type == TxTypes.TRANSFER }
            else -> base
        }
        filtered.take(15)
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .testTag("dashboard_lazy_column"),
            contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Financial Summary Card (Net Worth + Sisa Anggaran + Interactive Lottie Mascot)
            item(key = "financial_summary_card", contentType = "hero") {
                FinancialSummaryLottieCard(
                    netWorth = state.netWorth,
                    totalLiquidAndInvestments = state.totalLiquidCash + state.totalInvestments,
                    totalDebts = state.totalDebts,
                    savingRatePercent = state.savingRatePercent,
                    totalBudgetLimit = state.totalBudgetLimit,
                    totalBudgetSpent = state.totalBudgetSpent,
                    remainingBudget = state.remainingBudget,
                    budgetRemainingFraction = state.budgetRemainingFraction,
                    healthScore = state.advisorReport?.score ?: 0,
                    stealthMode = stealth,
                    celebrationTrigger = heroCelebrationCount,
                    onCelebrate = { heroCelebrationCount++ },
                    onOpenAdvisor = onNavigateToAdvisor,
                    onOpenBudgetDetail = onNavigateToWealth
                )
            }

        // 1b. Cheerful Interactive Lottie Sparkle Mood Strip
        item(key = "cheer_mood_strip", contentType = "cheer_strip") {
            val activeTimePhase = LocalActiveTimePastelPhase.current
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.78f),
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable {
                        cheerTipIndex++
                        heroCelebrationCount++
                    }
                    .testTag("dashboard_cheer_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    InteractiveLottieSparkleBadge(
                        size = 48.dp,
                        onTap = {
                            cheerTipIndex++
                            heroCelebrationCount++
                        }
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = greetingTitle,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (activeTimePhase != null) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer
                                ) {
                                    Text(
                                        text = "${activeTimePhase.emoji} ${activeTimePhase.displayName}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        AnimatedContent(
                            targetState = DAILY_CHEER_TIPS[cheerTipIndex % DAILY_CHEER_TIPS.size],
                            label = "daily_cheer_tip"
                        ) { tip ->
                            Text(
                                text = tip,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.88f)
                            )
                        }
                    }
                }
            }
        }

        // 2. Friendly 4-Shortcut Action Bar with SVG Logos
        item(key = "shortcut_bar", contentType = "shortcuts") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickShortcutTile(
                    logoResId = R.drawable.ic_logo_cat_bills,
                    label = "Catat",
                    onClick = onOpenNewTransaction,
                    modifier = Modifier.weight(1f)
                )
                QuickShortcutTile(
                    logoResId = R.drawable.ic_logo_acc_bank,
                    label = "Dompet",
                    onClick = onNavigateToWealth,
                    modifier = Modifier.weight(1f)
                )
                QuickShortcutTile(
                    logoResId = R.drawable.ic_logo_workspace_shared,
                    label = "Patungan",
                    onClick = onNavigateToGroup,
                    modifier = Modifier.weight(1f)
                )
                QuickShortcutTile(
                    logoResId = R.drawable.ic_logo_nusakas,
                    label = "Ganti Tema",
                    onClick = onOpenThemePicker,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 2b. Auto-Salary & Monthly Payday Pop-up Card
        item(key = "auto_salary_card", contentType = "auto_salary") {
            val salarySettings = state.settings
            val isConfigured = salarySettings.isAutoSalaryEnabled && salarySettings.monthlyBaseSalary > 0L
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().testTag("dashboard_auto_salary_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SvgLogo(resId = R.drawable.ic_logo_cat_salary, contentDescription = null, size = 38.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isConfigured) {
                                "Gaji Otomatis • Tiap Tgl ${salarySettings.salaryPaydayDay}"
                            } else {
                                "Atur Gaji Otomatis & Potongan Izin"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (isConfigured) {
                                val lastInfo = if (salarySettings.lastSalaryNet > 0L) {
                                    "Terakhir: ${CurrencyFormatter.formatCompactIdr(salarySettings.lastSalaryNet, stealth)}"
                                } else {
                                    "Pop-up hitung libur/sakit siap"
                                }
                                "${CurrencyFormatter.formatCompactIdr(salarySettings.monthlyBaseSalary, stealth)} / ${salarySettings.salaryWorkingDays} hr • $lastInfo"
                            } else {
                                "Set gaji & tanggal terima (mis. tgl 17) agar pop-up hitung libur, sakit & izin muncul otomatis."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (isConfigured) {
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            FilledTonalButton(
                                onClick = onOpenPayrollPopup,
                                enabled = state.canEdit,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("open_payroll_popup_button")
                            ) {
                                Text("Isi Slip", style = MaterialTheme.typography.labelMedium)
                            }
                            TextButton(
                                onClick = onOpenAutoSalarySetup,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.testTag("edit_auto_salary_button")
                            ) {
                                Text("Ubah", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    } else {
                        FilledTonalButton(
                            onClick = onOpenAutoSalarySetup,
                            enabled = state.canEdit,
                            modifier = Modifier.testTag("setup_auto_salary_button")
                        ) {
                            Text("Atur")
                        }
                    }
                }
            }
        }

        // 3. Dismissible Beginner-Friendly Guide
        if (!state.settings.hideBeginnerGuide) {
            item(key = "beginner_guide", contentType = "guide") {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f),
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Panduan Singkat (Mudah Dipakai)",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            TextButton(
                                onClick = onDismissGuide,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                            ) {
                                Text("Tutup")
                            }
                        }
                        Text(
                            "1. Ketik jajan atau gaji di kotak 'Catat Kilat' di bawah, otomatis tersimpan walau tanpa internet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.92f)
                        )
                        Text(
                            "2. Buka 'Dompet' untuk cek saldo rekening, kartu kredit & batas belanja bulanan.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.92f)
                        )
                        Text(
                            "3. Ketuk tombol 'Ganti Tema' kapan saja untuk pilih tema Lucu Pastel, Cerah, Gelap, atau Warna-Warni.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.92f)
                        )
                    }
                }
            }
        }

        // 4. Isolated Quick-Input Card (typing only recomposes this scope)
        item(key = "quick_input_card", contentType = "quick_input") {
            DashboardQuickInputCard(
                canEdit = state.canEdit,
                onQuickInputSubmit = { text ->
                    val ok = onQuickInputSubmit(text)
                    if (ok) {
                        heroCelebrationCount++
                    }
                    ok
                },
                onOpenNewTransaction = onOpenNewTransaction
            )
        }

        // 5. Executive KPI Strip in Everyday Indonesian with SVG Logos
        item(key = "kpi_strip", contentType = "kpi_strip") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item(key = "kpi_liquid", contentType = "kpi") {
                    KpiMiniCard(
                        title = "Uang Siap Pakai",
                        value = CurrencyFormatter.formatIdr(state.totalLiquidCash, stealth),
                        subtitle = "Tunai, Bank & E-Wallet",
                        accent = MaterialTheme.colorScheme.secondary,
                        logoResId = R.drawable.ic_logo_acc_cash
                    )
                }
                item(key = "kpi_cashflow", contentType = "kpi") {
                    KpiMiniCard(
                        title = "Selisih Bulan Ini",
                        value = CurrencyFormatter.formatIdr(state.monthNetCashflow, stealth, showSign = true),
                        subtitle = "+${CurrencyFormatter.formatCompactIdr(state.monthIncome, stealth)} / -${CurrencyFormatter.formatCompactIdr(state.monthExpense, stealth)}",
                        accent = if (state.monthNetCashflow >= 0) IncomeGreen else ExpenseRed,
                        logoResId = R.drawable.ic_logo_asset_stocks
                    )
                }
                item(key = "kpi_emergency", contentType = "kpi") {
                    KpiMiniCard(
                        title = "Ketahanan Darurat",
                        value = "${String.format("%.1f", state.emergencyFundMonths)} Bulan",
                        subtitle = if (state.emergencyFundMonths >= 6.0) "Sudah aman (>= 6 bln)" else "Ideal: cukup 6 bulan",
                        accent = if (state.emergencyFundMonths >= 3.0) TransferBlue else WarningAmber,
                        logoResId = R.drawable.ic_logo_debt_shield
                    )
                }
            }
        }

        // 6. Transaction History List right under Summary Cards (Swipe-to-Delete + Categorical Expense Icons)
        item(key = "recent_tx_header", contentType = "section_header") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SvgLogo(resId = R.drawable.ic_logo_report_ledger, contentDescription = null, size = 26.dp)
                        Column {
                            Text("Riwayat Transaksi", style = MaterialTheme.typography.titleMedium)
                            if (state.canEdit) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.SwipeLeft,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        "Geser kiri pada item untuk menghapus",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    TextButton(
                        onClick = onNavigateToLedger,
                        modifier = Modifier.testTag("dashboard_see_all_tx_button")
                    ) {
                        Text("Lihat Semua")
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item(key = "hist_all") {
                        FilterChip(
                            selected = historyFilter == "ALL",
                            onClick = { historyFilter = "ALL" },
                            label = { Text("Semua") }
                        )
                    }
                    item(key = "hist_expense") {
                        FilterChip(
                            selected = historyFilter == TxTypes.EXPENSE,
                            onClick = { historyFilter = TxTypes.EXPENSE },
                            label = { Text("Pengeluaran") }
                        )
                    }
                    item(key = "hist_income") {
                        FilterChip(
                            selected = historyFilter == TxTypes.INCOME,
                            onClick = { historyFilter = TxTypes.INCOME },
                            label = { Text("Pemasukan") }
                        )
                    }
                    item(key = "hist_transfer") {
                        FilterChip(
                            selected = historyFilter == TxTypes.TRANSFER,
                            onClick = { historyFilter = TxTypes.TRANSFER },
                            label = { Text("Transfer") }
                        )
                    }
                }
            }
        }
        if (historyTransactions.isEmpty()) {
            item(key = "recent_tx_empty", contentType = "empty") {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SvgLogo(resId = R.drawable.ic_logo_empty_state, contentDescription = null, size = 44.dp)
                        Text(
                            "Belum ada transaksi di filter ini. Catat transaksi pertamamu di atas!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(
                items = historyTransactions,
                key = { it.id },
                contentType = { "tx_history" }
            ) { tx ->
                val cat = state.categoryMap[tx.categoryId]
                val acc = state.accountMap[tx.accountId]
                val toAcc = tx.toAccountId?.let { state.accountMap[it] }
                SwipeableTransactionHistoryItem(
                    tx = tx,
                    category = cat,
                    accountName = acc?.name ?: "-",
                    toAccountName = toAcc?.name,
                    stealthMode = stealth,
                    canEdit = state.canEdit,
                    onDelete = onDeleteTransaction,
                    modifier = Modifier.animateItem()
                )
            }
        }

        // 7. Due Bills (1-Click Pay)
        if (dueSoon.isNotEmpty()) {
            item(key = "due_soon", contentType = "due_soon") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SvgLogo(resId = R.drawable.ic_logo_cat_bills, contentDescription = null, size = 28.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Tagihan Segera Jatuh Tempo",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        dueSoon.forEach { rule ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        rule.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                    Text(
                                        "${CurrencyFormatter.formatIdr(rule.amount, stealth)} • ${CurrencyFormatter.formatDateShort(rule.nextDueDateMillis)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.82f)
                                    )
                                }
                                Button(
                                    onClick = { onPayRecurring(rule) },
                                    enabled = state.canEdit
                                ) {
                                    Text("Bayar")
                                }
                            }
                        }
                    }
                }
            }
        }

        // 8. Critical Budget Status (uses O(1) categoryMap)
        if (criticalBudgets.isNotEmpty()) {
            item(key = "critical_budgets", contentType = "budgets") {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Batas Belanja Hampir Penuh", style = MaterialTheme.typography.titleSmall)
                        criticalBudgets.forEach { (b, spent, limit) ->
                            val ratio = (spent.toFloat() / limit.coerceAtLeast(1L)).coerceIn(0f, 1.2f)
                            val color = if (ratio >= 1f) ExpenseRed else WarningAmber
                            Column {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(state.categoryMap[b.categoryId]?.name ?: "Kategori", style = MaterialTheme.typography.bodyMedium)
                                    Text("${(ratio * 100).roundToInt()}%", style = MoneySmallStyle, color = color, fontWeight = FontWeight.Bold)
                                }
                                LinearProgressIndicator(
                                    progress = { ratio.coerceIn(0f, 1f) },
                                    color = color,
                                    modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp))
                                )
                            }
                        }
                    }
                }
            }
        }

        // 9. Isolated Charts Card (switching chart tab only recomposes this scope)
        item(key = "charts_card", contentType = "charts") {
            DashboardChartsCard(
                netWorthTrend12M = state.netWorthTrend12M,
                monthlyBars = state.monthlyBars,
                categorySlices = state.categorySlices,
                dailyHeatmapSpending = state.dailyHeatmapSpending,
                stealthMode = stealth
            )
        }

        // 10. Recent Member Activity
        if (state.isSharedWorkspace && state.auditLogs.isNotEmpty()) {
            item(key = "recent_audit", contentType = "audit") {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Aktivitas Anggota Terbaru", style = MaterialTheme.typography.titleSmall)
                        state.auditLogs.take(4).forEach { log ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "${log.actorName}: ${log.action} (${log.detail})",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    CurrencyFormatter.formatRelativeDateTime(log.createdAt),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
        }

        AnimatedVisibility(
            visible = showScrollTop,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 18.dp)
        ) {
            SmallFloatingActionButton(
                onClick = { scope.launch { listState.animateScrollToItem(0) } },
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.testTag("dashboard_scroll_top_button")
            ) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Kembali ke atas")
            }
        }
    }
}

@Composable
private fun DashboardQuickInputCard(
    canEdit: Boolean,
    onQuickInputSubmit: (String) -> Boolean,
    onOpenNewTransaction: () -> Unit
) {
    var quickInput by remember { mutableStateOf("") }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SvgLogo(resId = R.drawable.ic_logo_cat_salary, contentDescription = null, size = 26.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Catat Kilat (Ketik Biasa Aja)", style = MaterialTheme.typography.titleSmall)
                }
                FilledTonalButton(
                    onClick = onOpenNewTransaction,
                    enabled = canEdit,
                    modifier = Modifier.testTag("dashboard_add_tx_button")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Form")
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = quickInput,
                    onValueChange = { quickInput = it },
                    placeholder = { Text("Contoh: Beli kopi 25rb pakai GoPay", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("dashboard_quick_input")
                )
                Button(
                    onClick = {
                        if (onQuickInputSubmit(quickInput)) {
                            quickInput = ""
                        }
                    },
                    enabled = quickInput.isNotBlank() && canEdit,
                    modifier = Modifier.height(52.dp).testTag("dashboard_quick_submit")
                ) {
                    Text("Simpan")
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(
                    items = QUICK_INPUT_EXAMPLES,
                    key = { it },
                    contentType = { "quick_sample" }
                ) { sample ->
                    SuggestionChip(
                        onClick = { quickInput = sample },
                        label = { Text("Coba: $sample", style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardChartsCard(
    netWorthTrend12M: List<Pair<String, Long>>,
    monthlyBars: List<MonthlyBarPoint>,
    categorySlices: List<CategorySpendSlice>,
    dailyHeatmapSpending: Map<Int, Long>,
    stealthMode: Boolean
) {
    var selectedChartTab by rememberSaveable { mutableStateOf(0) }
    val chartLabels = remember { listOf("Tren 12B", "Masuk/Keluar", "Kategori", "Kalender") }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("Grafik Keuanganmu", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                chartLabels.forEachIndexed { idx, label ->
                    SegmentedButton(
                        selected = selectedChartTab == idx,
                        onClick = { selectedChartTab = idx },
                        shape = SegmentedButtonDefaults.itemShape(idx, 4)
                    ) {
                        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            when (selectedChartTab) {
                0 -> NetWorthTrendChart(netWorthTrend12M, stealthMode)
                1 -> IncomeExpenseBarChart(monthlyBars, stealthMode)
                2 -> CategoryDonutChart(categorySlices, stealthMode)
                else -> DailySpendingHeatmap(dailyHeatmapSpending, stealthMode)
            }
        }
    }
}

@Composable
private fun QuickShortcutTile(
    logoResId: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.small,
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SvgLogo(
                resId = logoResId,
                contentDescription = label,
                size = 38.dp
            )
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun KpiMiniCard(
    title: String,
    value: String,
    subtitle: String,
    accent: Color,
    logoResId: Int
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.width(188.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(accent)
                )
                SvgLogo(resId = logoResId, contentDescription = null, size = 26.dp)
            }
            Spacer(Modifier.height(6.dp))
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MoneyMediumStyle, color = accent, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}


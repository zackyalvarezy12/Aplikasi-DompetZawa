package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.*
import com.example.domain.BillReminderEvaluator
import com.example.domain.CurrencyFormatter
import com.example.ui.MainUiState
import com.example.ui.components.SvgLogo
import com.example.ui.components.accountLogoRes
import com.example.ui.components.assetLogoRes
import com.example.ui.components.categoryLogoRes
import com.example.ui.theme.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WealthBudgetScreen(
    state: MainUiState,
    requestedSubTab: Int? = null,
    requestedSubTabNonce: Int = 0,
    onSaveAccount: (existingId: String?, name: String, type: String, balance: Long, creditLimit: Long, billingDay: Int, dueDay: Int, institution: String) -> Unit,
    onReconcileAccount: (AccountEntity, Long) -> Unit,
    onDeleteAccount: (AccountEntity) -> Unit,
    onSaveAsset: (existingId: String?, name: String, assetClass: String, qty: Double, unitLabel: String, unitPrice: Long, purchaseValue: Long) -> Unit,
    onDeleteAsset: (AssetInvestmentEntity) -> Unit,
    onSaveBudget: (existingId: String?, catId: String, limit: Long, rollover: Long, rolloverEnabled: Boolean) -> Unit,
    onDeleteBudget: (BudgetEntity) -> Unit,
    onSaveRecurring: (existingId: String?, title: String, amount: Long, accountId: String, categoryId: String, freq: String, nextDueMillis: Long, reminderEnabled: Boolean) -> Unit,
    onDeleteRecurring: (RecurringRuleEntity) -> Unit,
    onPayRecurringNow: (RecurringRuleEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val stealth = state.settings.isStealthMode
    var selectedSubTab by rememberSaveable { mutableStateOf(0) } // 0: Rekening & Tunai, 1: Harta & Emas, 2: Anggaran, 3: Tagihan
    val subTabLabels = remember { listOf("Rekening & Tunai", "Harta & Emas", "Anggaran", "Tagihan") }

    LaunchedEffect(requestedSubTabNonce) {
        if (requestedSubTab != null && requestedSubTab in 0..3) {
            selectedSubTab = requestedSubTab
        }
    }

    var accountEditorTarget by remember { mutableStateOf<Pair<Boolean, AccountEntity?>?>(null) }
    var deleteAccountConfirm by remember { mutableStateOf<AccountEntity?>(null) }
    var reconcileTarget by remember { mutableStateOf<AccountEntity?>(null) }

    var assetEditorTarget by remember { mutableStateOf<Pair<Boolean, AssetInvestmentEntity?>?>(null) }
    var deleteAssetConfirm by remember { mutableStateOf<AssetInvestmentEntity?>(null) }

    var budgetEditorTarget by remember { mutableStateOf<Pair<Boolean, BudgetEntity?>?>(null) }
    var deleteBudgetConfirm by remember { mutableStateOf<BudgetEntity?>(null) }

    var recurringEditorTarget by remember { mutableStateOf<Pair<Boolean, RecurringRuleEntity?>?>(null) }
    var deleteRecurringConfirm by remember { mutableStateOf<RecurringRuleEntity?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            subTabLabels.forEachIndexed { idx, label ->
                SegmentedButton(
                    selected = selectedSubTab == idx,
                    onClick = { selectedSubTab = idx },
                    shape = SegmentedButtonDefaults.itemShape(idx, 4)
                ) {
                    Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        when (selectedSubTab) {
            // SUBTAB 0: ACCOUNTS & CREDIT CARDS
            0 -> {
                val listState = rememberLazyListState()
                val normalAccounts = remember(state.accounts) {
                    state.accounts.filter { it.type != AccountTypes.CREDIT_CARD }
                }
                val creditCards = remember(state.accounts) {
                    state.accounts.filter { it.type == AccountTypes.CREDIT_CARD }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item(key = "acc_header", contentType = "section_header") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Uang Tunai, Bank & Dompet Digital", style = MaterialTheme.typography.titleMedium)
                            FilledTonalButton(onClick = { accountEditorTarget = true to null }, enabled = state.canEdit) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Tambah")
                            }
                        }
                    }
                    if (normalAccounts.isEmpty()) {
                        item(key = "acc_empty", contentType = "empty") {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    SvgLogo(resId = R.drawable.ic_logo_empty_state, contentDescription = null, size = 40.dp)
                                    Text("Belum ada dompet tunai atau rekening bank. Ketuk '+ Tambah' untuk menambah.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    items(normalAccounts, key = { it.id }, contentType = { "account" }) { acc ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        SvgLogo(
                                            resId = accountLogoRes(acc.type),
                                            contentDescription = acc.type,
                                            size = 42.dp
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(acc.name, style = MaterialTheme.typography.titleSmall)
                                            Text("${CurrencyFormatter.accountTypeLabel(acc.type)} • ${acc.institutionName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    Text(CurrencyFormatter.formatIdr(acc.balance, stealth), style = MoneyMediumStyle, color = MaterialTheme.colorScheme.primary)
                                }
                                if (state.canEdit) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(onClick = { reconcileTarget = acc }) {
                                            Text("Cocokkan Saldo", style = MaterialTheme.typography.labelMedium)
                                        }
                                        IconButton(onClick = { accountEditorTarget = true to acc }, modifier = Modifier.size(48.dp)) {
                                            Icon(Icons.Filled.Edit, contentDescription = "Edit rekening", tint = MaterialTheme.colorScheme.primary)
                                        }
                                        if (state.accounts.size > 1) {
                                            IconButton(onClick = { deleteAccountConfirm = acc }, modifier = Modifier.size(48.dp)) {
                                                Icon(Icons.Filled.DeleteOutline, contentDescription = "Hapus rekening", tint = ExpenseRed)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item(key = "cc_header", contentType = "section_header") {
                        Spacer(Modifier.height(6.dp))
                        Text("Kartu Kredit & Batas Pemakaian", style = MaterialTheme.typography.titleMedium)
                    }
                    if (creditCards.isEmpty()) {
                        item(key = "cc_empty", contentType = "empty") {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    SvgLogo(resId = R.drawable.ic_logo_acc_card, contentDescription = null, size = 40.dp)
                                    Text("Belum ada kartu kredit terdaftar.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    } else {
                        items(creditCards, key = { it.id }, contentType = { "credit_card" }) { cc ->
                            val utilRatio = if (cc.creditLimit > 0L) (cc.balance.toFloat() / cc.creditLimit) else 0f
                            val utilPct = (utilRatio * 100).roundToInt()
                            val isHighRisk = utilRatio > 0.30f
                            val remainingLimit = (cc.creditLimit - cc.balance).coerceAtLeast(0L)
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isHighRisk) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface,
                                    contentColor = if (isHighRisk) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            SvgLogo(resId = R.drawable.ic_logo_acc_card, contentDescription = cc.name, size = 38.dp)
                                            Spacer(Modifier.width(10.dp))
                                            Column {
                                                Text(cc.name, style = MaterialTheme.typography.titleSmall)
                                                Text(
                                                    "Cetak tgl ${cc.billingDay} • Jatuh tempo tgl ${cc.dueDay}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = if (isHighRisk) MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        if (isHighRisk) {
                                            Surface(color = ExpenseRed, shape = RoundedCornerShape(8.dp)) {
                                                Text("Terpakai $utilPct% (tinggi)", color = Color.White, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                            }
                                        }
                                    }
                                    LinearProgressIndicator(
                                        progress = { utilRatio.coerceIn(0f, 1f) },
                                        color = if (isHighRisk) ExpenseRed else IncomeGreen,
                                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                                    )
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(
                                            "Terpakai: ${CurrencyFormatter.formatIdr(cc.balance, stealth)}",
                                            style = MoneySmallStyle,
                                            color = if (isHighRisk) MaterialTheme.colorScheme.onErrorContainer else ExpenseRed
                                        )
                                        Text(
                                            "Sisa Batas: ${CurrencyFormatter.formatIdr(remainingLimit, stealth)}",
                                            style = MoneySmallStyle,
                                            color = if (isHighRisk) MaterialTheme.colorScheme.onErrorContainer else IncomeGreen
                                        )
                                    }
                                    if (state.canEdit) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                            IconButton(onClick = { accountEditorTarget = true to cc }, modifier = Modifier.size(48.dp)) {
                                                Icon(Icons.Filled.Edit, contentDescription = "Edit kartu kredit")
                                            }
                                            IconButton(onClick = { deleteAccountConfirm = cc }, modifier = Modifier.size(48.dp)) {
                                                Icon(Icons.Filled.DeleteOutline, contentDescription = "Hapus kartu kredit", tint = ExpenseRed)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // SUBTAB 1: ASSETS & INVESTMENTS
            1 -> {
                val listState = rememberLazyListState()
                val (totalCurrent, _, gain) = remember(state.assets) {
                    val cur = state.assets.sumOf { it.currentValue }
                    val cost = state.assets.sumOf { it.purchaseValue }
                    Triple(cur, cost, cur - cost)
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item(key = "asset_summary", contentType = "summary") {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    SvgLogo(resId = R.drawable.ic_logo_asset_stocks, contentDescription = null, size = 44.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text("Total Harta & Simpanan Berharga", style = MaterialTheme.typography.labelMedium)
                                        Text(CurrencyFormatter.formatIdr(totalCurrent, stealth), style = MoneyDisplayStyle)
                                        Text(
                                            "Selisih Nilai: ${CurrencyFormatter.formatIdr(gain, stealth, showSign = true)}",
                                            style = MoneySmallStyle,
                                            color = if (gain >= 0) IncomeGreen else ExpenseRed
                                        )
                                    }
                                }
                                FilledTonalButton(onClick = { assetEditorTarget = true to null }, enabled = state.canEdit) {
                                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Text("Harta")
                                }
                            }
                        }
                    }
                    if (state.assets.isEmpty()) {
                        item(key = "asset_empty", contentType = "empty") {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    SvgLogo(resId = R.drawable.ic_logo_empty_state, contentDescription = null, size = 40.dp)
                                    Text("Belum ada harta atau simpanan berharga tercatat.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    items(state.assets, key = { it.id }, contentType = { "asset" }) { asset ->
                        val pnl = asset.currentValue - asset.purchaseValue
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        SvgLogo(
                                            resId = assetLogoRes(asset.assetClass),
                                            contentDescription = asset.assetClass,
                                            size = 40.dp
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(asset.name, style = MaterialTheme.typography.titleSmall)
                                            Text(
                                                "${CurrencyFormatter.assetClassLabel(asset.assetClass)} • ${asset.quantity} ${asset.unitLabel} @ ${CurrencyFormatter.formatCompactIdr(asset.unitPrice, stealth)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(CurrencyFormatter.formatIdr(asset.currentValue, stealth), style = MoneyMediumStyle)
                                        Text(
                                            CurrencyFormatter.formatIdr(pnl, stealth, showSign = true),
                                            style = MoneySmallStyle,
                                            color = if (pnl >= 0) IncomeGreen else ExpenseRed
                                        )
                                    }
                                }
                                if (state.canEdit) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                        IconButton(onClick = { assetEditorTarget = true to asset }, modifier = Modifier.size(48.dp)) {
                                            Icon(Icons.Filled.Edit, contentDescription = "Edit aset", tint = MaterialTheme.colorScheme.primary)
                                        }
                                        IconButton(onClick = { deleteAssetConfirm = asset }, modifier = Modifier.size(48.dp)) {
                                            Icon(Icons.Filled.DeleteOutline, contentDescription = "Hapus aset", tint = ExpenseRed)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // SUBTAB 2: ZERO-BASED BUDGETING & ROLLOVER
            2 -> {
                val listState = rememberLazyListState()
                val monthKey = remember { CurrencyFormatter.currentMonthKey() }
                val (monthBudgets, spentByCategory, totalAllocated, totalSpent) = remember(state.budgets, state.transactions, monthKey) {
                    val mBudgets = state.budgets.filter { it.monthYear == monthKey }
                    val spentMap = HashMap<String, Long>()
                    state.transactions.forEach { tx ->
                        if ((tx.type == TxTypes.EXPENSE || tx.type == TxTypes.SPLIT) &&
                            CurrencyFormatter.currentMonthKey(tx.dateMillis) == monthKey
                        ) {
                            spentMap[tx.categoryId] = (spentMap[tx.categoryId] ?: 0L) + tx.amount
                        }
                    }
                    val alloc = mBudgets.sumOf { it.limitAmount + if (it.rolloverEnabled) it.rolloverAmount else 0L }
                    val spent = mBudgets.sumOf { b -> spentMap[b.categoryId] ?: 0L }
                    BudgetSubTabData(mBudgets, spentMap, alloc, spent)
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item(key = "budget_header", contentType = "summary") {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    SvgLogo(resId = R.drawable.ic_logo_report_ledger, contentDescription = null, size = 34.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text("Batas Belanja Bulanan (${CurrencyFormatter.monthLabel(monthKey)})", style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            "Terpakai ${CurrencyFormatter.formatIdr(totalSpent, stealth)} / ${CurrencyFormatter.formatIdr(totalAllocated, stealth)}",
                                            style = MoneySmallStyle,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                FilledTonalButton(onClick = { budgetEditorTarget = true to null }, enabled = state.canEdit) {
                                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Text("Atur")
                                }
                            }
                        }
                    }
                    if (monthBudgets.isEmpty()) {
                        item(key = "budget_empty", contentType = "empty") {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    SvgLogo(resId = R.drawable.ic_logo_empty_state, contentDescription = null, size = 40.dp)
                                    Text("Belum ada batas belanja bulan ini. Ketuk '+ Atur' untuk menambah.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    items(monthBudgets, key = { it.id }, contentType = { "budget" }) { b ->
                        val cat = state.categoryMap[b.categoryId]
                        val spent = spentByCategory[b.categoryId] ?: 0L
                        val effectiveLimit = b.limitAmount + if (b.rolloverEnabled) b.rolloverAmount else 0L
                        val ratio = if (effectiveLimit > 0L) spent.toFloat() / effectiveLimit else 0f
                        val statusColor = when {
                            ratio >= 1f -> ExpenseRed
                            ratio >= 0.75f -> WarningAmber
                            else -> IncomeGreen
                        }
                        val statusText = when {
                            ratio >= 1f -> "Melebihi Batas"
                            ratio >= 0.75f -> "Hampir Habis"
                            else -> "Aman"
                        }
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        SvgLogo(
                                            resId = categoryLogoRes(cat?.iconKey, TxTypes.EXPENSE),
                                            contentDescription = cat?.name ?: "Kategori",
                                            size = 34.dp
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(cat?.name ?: "Kategori", style = MaterialTheme.typography.titleSmall)
                                    }
                                    Surface(color = statusColor.copy(alpha = 0.14f), shape = RoundedCornerShape(8.dp)) {
                                        Text("$statusText • ${(ratio * 100).roundToInt()}%", color = statusColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                                    }
                                }
                                LinearProgressIndicator(
                                    progress = { ratio.coerceIn(0f, 1f) },
                                    color = statusColor,
                                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                                )
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column {
                                        Text("Pakai: ${CurrencyFormatter.formatIdr(spent, stealth)}", style = MoneySmallStyle)
                                        Text(
                                            "Batas: ${CurrencyFormatter.formatIdr(effectiveLimit, stealth)}" +
                                                if (b.rolloverEnabled && b.rolloverAmount > 0L) " (+Sisa bln lalu)" else "",
                                            style = MoneySmallStyle,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (state.canEdit) {
                                        Row {
                                            IconButton(onClick = { budgetEditorTarget = true to b }, modifier = Modifier.size(48.dp)) {
                                                Icon(Icons.Filled.Edit, contentDescription = "Edit anggaran", tint = MaterialTheme.colorScheme.primary)
                                            }
                                            IconButton(onClick = { deleteBudgetConfirm = b }, modifier = Modifier.size(48.dp)) {
                                                Icon(Icons.Filled.DeleteOutline, contentDescription = "Hapus anggaran", tint = ExpenseRed)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // SUBTAB 3: RECURRING BILLS & AUTO REMINDERS (H-3 & H-1)
            else -> {
                val listState = rememberLazyListState()
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item(key = "recurring_header", contentType = "section_header") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Tagihan Rutin & Langganan", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Notifikasi otomatis muncul H-3 dan H-1 sebelum jatuh tempo",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            FilledTonalButton(
                                onClick = { recurringEditorTarget = true to null },
                                enabled = state.canEdit,
                                modifier = Modifier.testTag("add_recurring_bill_button")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("Jadwal")
                            }
                        }
                    }
                    if (state.recurringRules.isEmpty()) {
                        item(key = "recurring_empty", contentType = "empty") {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    SvgLogo(resId = R.drawable.ic_logo_cat_bills, contentDescription = null, size = 40.dp)
                                    Text("Belum ada tagihan bulanan (listrik, internet, langganan). Ketuk '+ Jadwal' untuk aktifkan pengingat otomatis H-3 & H-1.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    items(state.recurringRules, key = { it.id }, contentType = { "recurring" }) { rule ->
                        val daysLeft = BillReminderEvaluator.calendarDaysUntilDue(rule.nextDueDateMillis)
                        val isDueSoon = daysLeft <= 3
                        val badgeLabel = when {
                            daysLeft < 0 -> "Telat ${-daysLeft} hr"
                            daysLeft == 0 -> "Jatuh Tempo Hari Ini"
                            daysLeft <= 3 -> "H-$daysLeft"
                            else -> CurrencyFormatter.formatDateShort(rule.nextDueDateMillis)
                        }
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isDueSoon) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surface,
                                contentColor = if (isDueSoon) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        SvgLogo(
                                            resId = categoryLogoRes(state.categoryMap[rule.categoryId]?.iconKey, rule.type),
                                            contentDescription = rule.title,
                                            size = 38.dp
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text(rule.title, style = MaterialTheme.typography.titleSmall)
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = if (isDueSoon) WarningAmberSoft else MaterialTheme.colorScheme.secondaryContainer
                                                ) {
                                                    Text(
                                                        badgeLabel,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isDueSoon) PastelPlumText else MaterialTheme.colorScheme.onSecondaryContainer,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                "${CurrencyFormatter.frequencyLabel(rule.frequency)} • Jatuh tempo ${CurrencyFormatter.formatDateShort(rule.nextDueDateMillis)} • ${if (rule.reminderEnabled) "Pengingat H-3/H-1" else "Pengingat Mati"}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (isDueSoon) MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                CurrencyFormatter.formatIdr(rule.amount, stealth),
                                                style = MoneyMediumStyle,
                                                color = if (isDueSoon) MaterialTheme.colorScheme.onTertiaryContainer else ExpenseRed
                                            )
                                        }
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(
                                            imageVector = if (rule.reminderEnabled) Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationsOff,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = if (rule.reminderEnabled) IncomeGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            if (rule.reminderEnabled) "Pengingat Otomatis H-3 & H-1" else "Pengingat Dimatikan",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (state.canEdit) {
                                            IconButton(onClick = { recurringEditorTarget = true to rule }, modifier = Modifier.size(48.dp)) {
                                                Icon(Icons.Filled.Edit, contentDescription = "Edit tagihan")
                                            }
                                            IconButton(onClick = { deleteRecurringConfirm = rule }, modifier = Modifier.size(48.dp)) {
                                                Icon(Icons.Filled.DeleteOutline, contentDescription = "Hapus tagihan", tint = ExpenseRed)
                                            }
                                        }
                                        Button(
                                            onClick = { onPayRecurringNow(rule) },
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
            }
        }
    }

    // --- DIALOGS ---
    accountEditorTarget?.let { (_, existing) ->
        var name by remember(existing?.id) { mutableStateOf(existing?.name ?: "") }
        var type by remember(existing?.id) { mutableStateOf(existing?.type ?: AccountTypes.BANK) }
        var bal by remember(existing?.id) { mutableStateOf(existing?.balance?.toString() ?: "") }
        var limit by remember(existing?.id) { mutableStateOf(existing?.creditLimit?.takeIf { it > 0L }?.toString() ?: "") }
        var billingDayStr by remember(existing?.id) { mutableStateOf((existing?.billingDay ?: 5).toString()) }
        var dueDayStr by remember(existing?.id) { mutableStateOf((existing?.dueDay ?: 20).toString()) }
        var institution by remember(existing?.id) { mutableStateOf(existing?.institutionName ?: "") }

        Dialog(
            onDismissRequest = { accountEditorTarget = null },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .widthIn(max = 520.dp)
                    .imePadding(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        if (existing == null) "Tambah Dompet / Rekening" else "Edit Rekening: ${existing.name}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nama Dompet / Rekening") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_account_name")
                    )
                    OutlinedTextField(
                        value = institution,
                        onValueChange = { institution = it },
                        label = { Text("Nama Bank / Penyedia (mis. BRI, GoPay)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_account_institution")
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(
                            listOf(
                                AccountTypes.CASH to "Tunai",
                                AccountTypes.BANK to "Bank",
                                AccountTypes.EWALLET to "Dompet Digital",
                                AccountTypes.CREDIT_CARD to "Kartu Kredit"
                            )
                        ) { (t, lbl) ->
                            FilterChip(selected = type == t, onClick = { type = t }, label = { Text(lbl, style = MaterialTheme.typography.labelMedium) })
                        }
                    }
                    OutlinedTextField(
                        value = bal,
                        onValueChange = { bal = it.filter(Char::isDigit) },
                        label = { Text(if (type == AccountTypes.CREDIT_CARD) "Pemakaian Berjalan (Rp)" else "Saldo (Rp)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_account_balance")
                    )
                    if (type == AccountTypes.CREDIT_CARD) {
                        OutlinedTextField(
                            value = limit,
                            onValueChange = { limit = it.filter(Char::isDigit) },
                            label = { Text("Batas Kartu Kredit (Rp)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = billingDayStr,
                                onValueChange = { billingDayStr = it.filter(Char::isDigit).take(2) },
                                label = { Text("Tgl Cetak") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = dueDayStr,
                                onValueChange = { dueDayStr = it.filter(Char::isDigit).take(2) },
                                label = { Text("Tgl Tempo") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { accountEditorTarget = null }) { Text("Batal") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            if (name.isNotBlank()) {
                                onSaveAccount(
                                    existing?.id,
                                    name,
                                    type,
                                    bal.toLongOrNull() ?: 0L,
                                    limit.toLongOrNull() ?: 0L,
                                    (billingDayStr.toIntOrNull() ?: 5).coerceIn(1, 31),
                                    (dueDayStr.toIntOrNull() ?: 20).coerceIn(1, 31),
                                    institution.ifBlank { name }
                                )
                                accountEditorTarget = null
                            }
                        }) { Text("Simpan") }
                    }
                }
            }
        }
    }

    deleteAccountConfirm?.let { acc ->
        AlertDialog(
            onDismissRequest = { deleteAccountConfirm = null },
            title = { Text("Hapus Rekening?") },
            text = { Text("Rekening '${acc.name}' akan dihapus dari daftar rekeningmu.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteAccount(acc)
                        deleteAccountConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed)
                ) { Text("Hapus") }
            },
            dismissButton = { TextButton(onClick = { deleteAccountConfirm = null }) { Text("Batal") } }
        )
    }

    reconcileTarget?.let { acc ->
        var actualStr by remember { mutableStateOf(acc.balance.toString()) }
        Dialog(
            onDismissRequest = { reconcileTarget = null },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .widthIn(max = 500.dp)
                    .imePadding(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Cocokkan Saldo: ${acc.name}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Saldo tercatat: ${CurrencyFormatter.formatIdr(acc.balance, stealth)}")
                    OutlinedTextField(
                        value = actualStr,
                        onValueChange = { actualStr = it.filter(Char::isDigit) },
                        label = { Text("Saldo Asli Saat Ini (Rp)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_reconcile_balance")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { reconcileTarget = null }) { Text("Batal") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            onReconcileAccount(acc, actualStr.toLongOrNull() ?: acc.balance)
                            reconcileTarget = null
                        }) { Text("Sesuaikan") }
                    }
                }
            }
        }
    }

    assetEditorTarget?.let { (_, existing) ->
        var name by remember(existing?.id) { mutableStateOf(existing?.name ?: "") }
        var assetClass by remember(existing?.id) { mutableStateOf(existing?.assetClass ?: "GOLD") }
        var qtyStr by remember(existing?.id) { mutableStateOf(existing?.quantity?.toString() ?: "1") }
        var unitLabel by remember(existing?.id) { mutableStateOf(existing?.unitLabel ?: "gram") }
        var unitPriceStr by remember(existing?.id) { mutableStateOf(existing?.unitPrice?.toString() ?: "") }
        var costStr by remember(existing?.id) { mutableStateOf(existing?.purchaseValue?.toString() ?: "") }
        Dialog(
            onDismissRequest = { assetEditorTarget = null },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .widthIn(max = 520.dp)
                    .imePadding(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        if (existing == null) "Tambah Harta / Simpanan" else "Edit Harta: ${existing.name}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nama Harta (mis. Emas Antam)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_asset_name")
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(listOf("GOLD" to "Emas", "STOCKS" to "Saham", "MUTUAL_FUND" to "Reksa Dana", "PROPERTY" to "Properti")) { (k, lbl) ->
                            FilterChip(selected = assetClass == k, onClick = {
                                assetClass = k
                                unitLabel = when (k) {
                                    "GOLD" -> "gram"
                                    "STOCKS" -> "lot"
                                    else -> "unit"
                                }
                            }, label = { Text(lbl, style = MaterialTheme.typography.labelMedium) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = qtyStr,
                            onValueChange = { qtyStr = it },
                            label = { Text("Jumlah ($unitLabel)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = unitPriceStr,
                            onValueChange = { unitPriceStr = it.filter(Char::isDigit) },
                            label = { Text("Harga/@") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    }
                    OutlinedTextField(
                        value = costStr,
                        onValueChange = { costStr = it.filter(Char::isDigit) },
                        label = { Text("Modal Beli Total (Rp)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_asset_cost")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { assetEditorTarget = null }) { Text("Batal") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            val qty = qtyStr.toDoubleOrNull() ?: 1.0
                            val price = unitPriceStr.toLongOrNull() ?: 0L
                            if (name.isNotBlank() && price > 0L) {
                                onSaveAsset(existing?.id, name, assetClass, qty, unitLabel, price, costStr.toLongOrNull() ?: (qty * price).toLong())
                                assetEditorTarget = null
                            }
                        }) { Text("Simpan") }
                    }
                }
            }
        }
    }

    deleteAssetConfirm?.let { asset ->
        AlertDialog(
            onDismissRequest = { deleteAssetConfirm = null },
            title = { Text("Hapus Harta?") },
            text = { Text("Catatan '${asset.name}' akan dihapus dari daftar harta.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteAsset(asset)
                        deleteAssetConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed)
                ) { Text("Hapus") }
            },
            dismissButton = { TextButton(onClick = { deleteAssetConfirm = null }) { Text("Batal") } }
        )
    }

    budgetEditorTarget?.let { (_, existing) ->
        val expenseCats = state.categories.filter { it.type == TxTypes.EXPENSE }
        var catId by remember(existing?.id) { mutableStateOf(existing?.categoryId ?: expenseCats.firstOrNull()?.id ?: "") }
        var limitStr by remember(existing?.id) { mutableStateOf(existing?.limitAmount?.toString() ?: "") }
        var rolloverStr by remember(existing?.id) { mutableStateOf((existing?.rolloverAmount ?: 0L).toString()) }
        var rolloverEnabled by remember(existing?.id) { mutableStateOf(existing?.rolloverEnabled ?: true) }

        Dialog(
            onDismissRequest = { budgetEditorTarget = null },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .widthIn(max = 520.dp)
                    .imePadding(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        if (existing == null) "Atur Batas Belanja" else "Edit Batas Belanja",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Pilih Kategori:", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(expenseCats, key = { it.id }) { c ->
                            FilterChip(selected = catId == c.id, onClick = { catId = c.id }, label = { Text(c.name) })
                        }
                    }
                    OutlinedTextField(
                        value = limitStr,
                        onValueChange = { limitStr = it.filter(Char::isDigit) },
                        label = { Text("Batas Bulanan (Rp)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_budget_limit")
                    )
                    OutlinedTextField(
                        value = rolloverStr,
                        onValueChange = { rolloverStr = it.filter(Char::isDigit) },
                        label = { Text("Sisa Dana Bulan Lalu (Rp)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Bawa Sisa Bulan Lalu ke Bulan Ini")
                        Switch(checked = rolloverEnabled, onCheckedChange = { rolloverEnabled = it })
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { budgetEditorTarget = null }) { Text("Batal") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            val lim = limitStr.toLongOrNull() ?: 0L
                            if (catId.isNotBlank() && lim > 0L) {
                                onSaveBudget(existing?.id, catId, lim, rolloverStr.toLongOrNull() ?: 0L, rolloverEnabled)
                                budgetEditorTarget = null
                            }
                        }) { Text("Simpan") }
                    }
                }
            }
        }
    }

    deleteBudgetConfirm?.let { b ->
        AlertDialog(
            onDismissRequest = { deleteBudgetConfirm = null },
            title = { Text("Hapus Anggaran?") },
            text = { Text("Batas anggaran untuk '${state.categoryMap[b.categoryId]?.name ?: "Kategori"}' akan dihapus.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteBudget(b)
                        deleteBudgetConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed)
                ) { Text("Hapus") }
            },
            dismissButton = { TextButton(onClick = { deleteBudgetConfirm = null }) { Text("Batal") } }
        )
    }

    recurringEditorTarget?.let { (_, existing) ->
        var title by remember(existing?.id) { mutableStateOf(existing?.title ?: "") }
        var amtStr by remember(existing?.id) { mutableStateOf(existing?.amount?.toString() ?: "") }
        var freq by remember(existing?.id) { mutableStateOf(existing?.frequency ?: "MONTHLY") }
        var accId by remember(existing?.id, state.accounts) {
            mutableStateOf(existing?.accountId ?: state.accounts.firstOrNull()?.id ?: "")
        }
        val expenseCats = remember(state.categories) { state.categories.filter { it.type == TxTypes.EXPENSE }.ifEmpty { state.categories } }
        var catId by remember(existing?.id, expenseCats) {
            mutableStateOf(existing?.categoryId ?: expenseCats.firstOrNull { it.iconKey == "receipt_long" }?.id ?: expenseCats.firstOrNull()?.id ?: "")
        }
        var nextDueMillis by remember(existing?.id) {
            mutableLongStateOf(existing?.nextDueDateMillis ?: (System.currentTimeMillis() + 3L * 24 * 60 * 60 * 1000L))
        }
        var reminderEnabled by remember(existing?.id) { mutableStateOf(existing?.reminderEnabled ?: true) }
        var showDueDatePicker by remember { mutableStateOf(false) }

        Dialog(
            onDismissRequest = { recurringEditorTarget = null },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .widthIn(max = 520.dp)
                    .imePadding(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        if (existing == null) "Tambah Tagihan & Pengingat" else "Edit Tagihan: ${existing.title}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Nama Tagihan (Listrik, Wi-Fi, dll)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_recurring_title")
                    )
                    OutlinedTextField(
                        value = amtStr,
                        onValueChange = { amtStr = it.filter(Char::isDigit) },
                        label = { Text("Nominal (Rp)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_recurring_amount")
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("WEEKLY" to "Mingguan", "MONTHLY" to "Bulanan", "YEARLY" to "Tahunan").forEach { (k, lbl) ->
                            FilterChip(selected = freq == k, onClick = { freq = k }, label = { Text(lbl) })
                        }
                    }
                    OutlinedButton(onClick = { showDueDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Jatuh Tempo: ${CurrencyFormatter.formatDateShort(nextDueMillis)}")
                    }
                    if (state.accounts.isNotEmpty()) {
                        Text("Bayar dari Rekening:", style = MaterialTheme.typography.labelMedium)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(state.accounts, key = { it.id }) { a ->
                                FilterChip(selected = accId == a.id, onClick = { accId = a.id }, label = { Text(a.name) })
                            }
                        }
                    }
                    if (expenseCats.isNotEmpty()) {
                        Text("Kategori:", style = MaterialTheme.typography.labelMedium)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(expenseCats, key = { it.id }) { c ->
                                FilterChip(selected = catId == c.id, onClick = { catId = c.id }, label = { Text(c.name) })
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Pengingat H-3 & H-1", style = MaterialTheme.typography.bodyMedium)
                            Text("Notifikasi otomatis 3 hari & 1 hari sebelumnya", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = reminderEnabled, onCheckedChange = { reminderEnabled = it })
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { recurringEditorTarget = null }) { Text("Batal") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            val amt = amtStr.toLongOrNull() ?: 0L
                            if (title.isNotBlank() && amt > 0L && accId.isNotBlank()) {
                                onSaveRecurring(existing?.id, title, amt, accId, catId, freq, nextDueMillis, reminderEnabled)
                                recurringEditorTarget = null
                            }
                        }) { Text("Simpan") }
                    }
                }
            }
        }

        if (showDueDatePicker) {
            val pickerState = rememberDatePickerState(initialSelectedDateMillis = nextDueMillis)
            DatePickerDialog(
                onDismissRequest = { showDueDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        pickerState.selectedDateMillis?.let { nextDueMillis = it }
                        showDueDatePicker = false
                    }) { Text("Pilih") }
                },
                dismissButton = {
                    TextButton(onClick = { showDueDatePicker = false }) { Text("Batal") }
                }
            ) {
                DatePicker(state = pickerState)
            }
        }
    }

    deleteRecurringConfirm?.let { rule ->
        AlertDialog(
            onDismissRequest = { deleteRecurringConfirm = null },
            title = { Text("Hapus Jadwal Tagihan?") },
            text = { Text("Tagihan '${rule.title}' dan pengingat otomatisnya akan dihapus.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteRecurring(rule)
                        deleteRecurringConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed)
                ) { Text("Hapus") }
            },
            dismissButton = { TextButton(onClick = { deleteRecurringConfirm = null }) { Text("Batal") } }
        )
    }
}

private data class BudgetSubTabData(
    val monthBudgets: List<BudgetEntity>,
    val spentByCategory: Map<String, Long>,
    val totalAllocated: Long,
    val totalSpent: Long
)

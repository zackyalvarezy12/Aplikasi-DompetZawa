package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.*
import com.example.domain.CurrencyFormatter
import com.example.ui.MainUiState
import com.example.ui.components.SvgLogo
import com.example.ui.components.accountLogoRes
import com.example.ui.components.assetLogoRes
import com.example.ui.components.categoryLogoRes
import com.example.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun WealthBudgetScreen(
    state: MainUiState,
    onSaveAccount: (existingId: String?, name: String, type: String, balance: Long, creditLimit: Long, billingDay: Int, dueDay: Int, institution: String) -> Unit,
    onReconcileAccount: (AccountEntity, Long) -> Unit,
    onSaveAsset: (name: String, assetClass: String, qty: Double, unitLabel: String, unitPrice: Long, purchaseValue: Long) -> Unit,
    onSaveBudget: (catId: String, limit: Long, rollover: Long, rolloverEnabled: Boolean) -> Unit,
    onSaveRecurring: (title: String, amount: Long, accountId: String, categoryId: String, freq: String, nextDueMillis: Long) -> Unit,
    onPayRecurringNow: (RecurringRuleEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val stealth = state.settings.isStealthMode
    var selectedSubTab by rememberSaveable { mutableStateOf(0) } // 0: Akun & KK, 1: Aset & Investasi, 2: Anggaran, 3: Tagihan
    val subTabLabels = remember { listOf("Akun & KK", "Aset", "Anggaran", "Tagihan") }

    var showAddAccount by remember { mutableStateOf(false) }
    var reconcileTarget by remember { mutableStateOf<AccountEntity?>(null) }
    var showAddAsset by remember { mutableStateOf(false) }
    var showAddBudget by remember { mutableStateOf(false) }
    var showAddRecurring by remember { mutableStateOf(false) }

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
                            Text("Akun Likuid & Deposito", style = MaterialTheme.typography.titleMedium)
                            FilledTonalButton(onClick = { showAddAccount = true }, enabled = state.canEdit) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Akun")
                            }
                        }
                    }
                    items(normalAccounts, key = { it.id }, contentType = { "account" }) { acc ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
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
                                        Text("${acc.type} • ${acc.institutionName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(CurrencyFormatter.formatIdr(acc.balance, stealth), style = MoneyMediumStyle, color = MaterialTheme.colorScheme.primary)
                                    if (state.canEdit) {
                                        TextButton(onClick = { reconcileTarget = acc }, contentPadding = PaddingValues(0.dp)) {
                                            Text("Rekonsiliasi", style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item(key = "cc_header", contentType = "section_header") {
                        Spacer(Modifier.height(6.dp))
                        Text("Modul Kartu Kredit & Rasio Limit", style = MaterialTheme.typography.titleMedium)
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
                                    containerColor = if (isHighRisk) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface
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
                                                Text("Rasio $utilPct% (>30%)", color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
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
                                            "Sisa Limit: ${CurrencyFormatter.formatIdr(remainingLimit, stealth)}",
                                            style = MoneySmallStyle,
                                            color = if (isHighRisk) MaterialTheme.colorScheme.onErrorContainer else IncomeGreen
                                        )
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
                val (totalCurrent, totalCost, gain) = remember(state.assets) {
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
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    SvgLogo(resId = R.drawable.ic_logo_asset_stocks, contentDescription = null, size = 44.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text("Total Portofolio & Aset Fisik", style = MaterialTheme.typography.labelMedium)
                                        Text(CurrencyFormatter.formatIdr(totalCurrent, stealth), style = MoneyDisplayStyle)
                                        Text(
                                            "Unrealized Gain: ${CurrencyFormatter.formatIdr(gain, stealth, showSign = true)}",
                                            style = MoneySmallStyle,
                                            color = if (gain >= 0) IncomeGreen else ExpenseRed
                                        )
                                    }
                                }
                                FilledTonalButton(onClick = { showAddAsset = true }, enabled = state.canEdit) {
                                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Text("Aset")
                                }
                            }
                        }
                    }
                    items(state.assets, key = { it.id }, contentType = { "asset" }) { asset ->
                        val pnl = asset.currentValue - asset.purchaseValue
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
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
                                            "${asset.assetClass} • ${asset.quantity} ${asset.unitLabel} @ ${CurrencyFormatter.formatCompactIdr(asset.unitPrice, stealth)}",
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
                                        Text("Zero-Based & Envelope (${CurrencyFormatter.monthLabel(monthKey)})", style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            "Terpakai ${CurrencyFormatter.formatIdr(totalSpent, stealth)} / ${CurrencyFormatter.formatIdr(totalAllocated, stealth)}",
                                            style = MoneySmallStyle,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                FilledTonalButton(onClick = { showAddBudget = true }, enabled = state.canEdit) {
                                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Text("Atur")
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
                            ratio >= 1f -> "Overbudget"
                            ratio >= 0.75f -> "Waspada"
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
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Pakai: ${CurrencyFormatter.formatIdr(spent, stealth)}", style = MoneySmallStyle)
                                    Text(
                                        "Batas: ${CurrencyFormatter.formatIdr(effectiveLimit, stealth)}" +
                                            if (b.rolloverEnabled && b.rolloverAmount > 0L) " (+Rollover)" else "",
                                        style = MoneySmallStyle,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // SUBTAB 3: RECURRING BILLS & SUBSCRIPTIONS
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
                            Text("Tagihan Rutin & Langganan", style = MaterialTheme.typography.titleMedium)
                            FilledTonalButton(onClick = { showAddRecurring = true }, enabled = state.canEdit) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("Jadwal")
                            }
                        }
                    }
                    items(state.recurringRules, key = { it.id }, contentType = { "recurring" }) { rule ->
                        val isDueSoon = rule.nextDueDateMillis <= System.currentTimeMillis() + 5L * 24 * 60 * 60 * 1000L
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isDueSoon) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
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
                                        Text(rule.title, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            "${rule.frequency} • Jatuh tempo ${CurrencyFormatter.formatDateShort(rule.nextDueDateMillis)}",
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

    // --- DIALOGS ---
    if (showAddAccount) {
        var name by remember { mutableStateOf("") }
        var type by remember { mutableStateOf(AccountTypes.BANK) }
        var bal by remember { mutableStateOf("") }
        var limit by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddAccount = false },
            title = { Text("Tambah Akun / Kartu Kredit") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nama Akun") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(AccountTypes.CASH, AccountTypes.BANK, AccountTypes.EWALLET, AccountTypes.CREDIT_CARD).forEach { t ->
                            FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.take(6), style = MaterialTheme.typography.labelMedium) })
                        }
                    }
                    OutlinedTextField(
                        value = bal,
                        onValueChange = { bal = it.filter(Char::isDigit) },
                        label = { Text(if (type == AccountTypes.CREDIT_CARD) "Pemakaian Berjalan (Rp)" else "Saldo Awal (Rp)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    if (type == AccountTypes.CREDIT_CARD) {
                        OutlinedTextField(
                            value = limit,
                            onValueChange = { limit = it.filter(Char::isDigit) },
                            label = { Text("Limit Kartu Kredit (Rp)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (name.isNotBlank()) {
                        onSaveAccount(null, name, type, bal.toLongOrNull() ?: 0L, limit.toLongOrNull() ?: 0L, 5, 20, name)
                        showAddAccount = false
                    }
                }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { showAddAccount = false }) { Text("Batal") } }
        )
    }

    reconcileTarget?.let { acc ->
        var actualStr by remember { mutableStateOf(acc.balance.toString()) }
        AlertDialog(
            onDismissRequest = { reconcileTarget = null },
            title = { Text("Rekonsiliasi: ${acc.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Saldo tercatat: ${CurrencyFormatter.formatIdr(acc.balance, stealth)}")
                    OutlinedTextField(
                        value = actualStr,
                        onValueChange = { actualStr = it.filter(Char::isDigit) },
                        label = { Text("Saldo Aktual Riil (Rp)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onReconcileAccount(acc, actualStr.toLongOrNull() ?: acc.balance)
                    reconcileTarget = null
                }) { Text("Sesuaikan") }
            },
            dismissButton = { TextButton(onClick = { reconcileTarget = null }) { Text("Batal") } }
        )
    }

    if (showAddAsset) {
        var name by remember { mutableStateOf("") }
        var assetClass by remember { mutableStateOf("GOLD") }
        var qtyStr by remember { mutableStateOf("1") }
        var unitLabel by remember { mutableStateOf("gram") }
        var unitPriceStr by remember { mutableStateOf("") }
        var costStr by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddAsset = false },
            title = { Text("Tambah Aset / Investasi") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nama Aset (mis. Emas Antam)") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("GOLD" to "Emas", "STOCKS" to "Saham", "MUTUAL_FUND" to "Reksadana", "PROPERTY" to "Properti").forEach { (k, lbl) ->
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
                        OutlinedTextField(value = qtyStr, onValueChange = { qtyStr = it }, label = { Text("Jumlah ($unitLabel)") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = unitPriceStr, onValueChange = { unitPriceStr = it.filter(Char::isDigit) }, label = { Text("Harga/@") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    }
                    OutlinedTextField(value = costStr, onValueChange = { costStr = it.filter(Char::isDigit) }, label = { Text("Modal Beli Total (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val qty = qtyStr.toDoubleOrNull() ?: 1.0
                    val price = unitPriceStr.toLongOrNull() ?: 0L
                    if (name.isNotBlank() && price > 0L) {
                        onSaveAsset(name, assetClass, qty, unitLabel, price, costStr.toLongOrNull() ?: (qty * price).toLong())
                        showAddAsset = false
                    }
                }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { showAddAsset = false }) { Text("Batal") } }
        )
    }

    if (showAddBudget) {
        val expenseCats = state.categories.filter { it.type == TxTypes.EXPENSE }
        var catId by remember { mutableStateOf(expenseCats.firstOrNull()?.id ?: "") }
        var limitStr by remember { mutableStateOf("") }
        var rolloverStr by remember { mutableStateOf("0") }
        var rolloverEnabled by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { showAddBudget = false },
            title = { Text("Atur Anggaran Kategori") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        expenseCats.take(3).forEach { c ->
                            FilterChip(selected = catId == c.id, onClick = { catId = c.id }, label = { Text(c.name.split(" ").first()) })
                        }
                    }
                    OutlinedTextField(value = limitStr, onValueChange = { limitStr = it.filter(Char::isDigit) }, label = { Text("Batas Bulanan (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    OutlinedTextField(value = rolloverStr, onValueChange = { rolloverStr = it.filter(Char::isDigit) }, label = { Text("Sisa Rollover Bulan Lalu (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Aktifkan Rollover Otomatis")
                        Switch(checked = rolloverEnabled, onCheckedChange = { rolloverEnabled = it })
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val lim = limitStr.toLongOrNull() ?: 0L
                    if (catId.isNotBlank() && lim > 0L) {
                        onSaveBudget(catId, lim, rolloverStr.toLongOrNull() ?: 0L, rolloverEnabled)
                        showAddBudget = false
                    }
                }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { showAddBudget = false }) { Text("Batal") } }
        )
    }

    if (showAddRecurring) {
        var title by remember { mutableStateOf("") }
        var amtStr by remember { mutableStateOf("") }
        var freq by remember { mutableStateOf("MONTHLY") }
        val accId = state.accounts.firstOrNull()?.id ?: ""
        val catId = state.categories.firstOrNull { it.type == TxTypes.EXPENSE }?.id ?: ""
        AlertDialog(
            onDismissRequest = { showAddRecurring = false },
            title = { Text("Tambah Tagihan Berulang") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Nama Tagihan / Langganan") }, singleLine = true)
                    OutlinedTextField(value = amtStr, onValueChange = { amtStr = it.filter(Char::isDigit) }, label = { Text("Nominal (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("WEEKLY" to "Mingguan", "MONTHLY" to "Bulanan", "YEARLY" to "Tahunan").forEach { (k, lbl) ->
                            FilterChip(selected = freq == k, onClick = { freq = k }, label = { Text(lbl) })
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val amt = amtStr.toLongOrNull() ?: 0L
                    if (title.isNotBlank() && amt > 0L && accId.isNotBlank()) {
                        onSaveRecurring(title, amt, accId, catId, freq, System.currentTimeMillis())
                        showAddRecurring = false
                    }
                }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { showAddRecurring = false }) { Text("Batal") } }
        )
    }
}

private data class BudgetSubTabData(
    val monthBudgets: List<BudgetEntity>,
    val spentByCategory: Map<String, Long>,
    val totalAllocated: Long,
    val totalSpent: Long
)

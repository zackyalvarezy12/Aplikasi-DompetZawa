package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import com.example.data.local.DebtLoanEntity
import com.example.data.local.GoalEntity
import com.example.data.local.SplitBillEntity
import com.example.domain.CurrencyFormatter
import com.example.domain.DebtPayoffSimulator
import com.example.domain.SimplifiedSettlement
import com.example.ui.MainUiState
import com.example.ui.components.SvgLogo
import com.example.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun GroupDebtScreen(
    state: MainUiState,
    onSaveDebtLoan: (existingId: String?, kind: String, title: String, counterparty: String, principal: Long, remainingAmount: Long?, apr: Double, minPay: Long) -> Unit,
    onDeleteDebtLoan: (DebtLoanEntity) -> Unit,
    onPayDebtInstallment: (DebtLoanEntity, Long, String) -> Unit,
    onSaveGoal: (existingId: String?, title: String, target: Long, currentAmount: Long, monthly: Long, monthsAhead: Int) -> Unit,
    onDeleteGoal: (GoalEntity) -> Unit,
    onContributeGoal: (GoalEntity, Long, String) -> Unit,
    onCreateSplitBill: (title: String, total: Long, paidByMemberId: String, participantIds: List<String>) -> Unit,
    onDeleteSplitBill: (SplitBillEntity) -> Unit,
    onSettleTransfer: (SimplifiedSettlement) -> Unit,
    onOpenWorkspaceInvite: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val stealth = state.settings.isStealthMode
    var subTab by rememberSaveable { mutableStateOf(0) } // 0: Utang & Cicilan, 1: Target Tabungan, 2: Patungan
    val subTabLabels = remember { listOf("Utang & Cicilan", "Target Tabungan", "Patungan") }

    var debtEditorTarget by remember { mutableStateOf<Pair<Boolean, DebtLoanEntity?>?>(null) }
    var deleteDebtConfirm by remember { mutableStateOf<DebtLoanEntity?>(null) }
    var payInstallmentTarget by remember { mutableStateOf<DebtLoanEntity?>(null) }

    var goalEditorTarget by remember { mutableStateOf<Pair<Boolean, GoalEntity?>?>(null) }
    var deleteGoalConfirm by remember { mutableStateOf<GoalEntity?>(null) }
    var contributeTarget by remember { mutableStateOf<GoalEntity?>(null) }

    var showAddSplit by remember { mutableStateOf(false) }
    var deleteSplitConfirm by remember { mutableStateOf<SplitBillEntity?>(null) }

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
                    Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        when (subTab) {
            // SUBTAB 0: DEBTS, LOANS & SNOWBALL vs AVALANCHE SIMULATOR
            0 -> {
                val listState = rememberLazyListState()
                var extraPaymentStr by remember { mutableStateOf("1000000") }
                val extraPayment = extraPaymentStr.toLongOrNull() ?: 0L
                val comparison = remember(state.debtsLoans, extraPayment) {
                    DebtPayoffSimulator.compare(state.debtsLoans, extraPayment)
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item(key = "debt_sim", contentType = "simulator") {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    SvgLogo(resId = R.drawable.ic_logo_debt_shield, contentDescription = null, size = 34.dp)
                                    Text("Cara Cepat Melunasi Utang", style = MaterialTheme.typography.titleMedium)
                                }
                                OutlinedTextField(
                                    value = extraPaymentStr,
                                    onValueChange = { extraPaymentStr = it.filter(Char::isDigit) },
                                    label = { Text("Tambahan Bayar Utang / Bulan (Rp)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Card(
                                        modifier = Modifier.weight(1f),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text("Mulai Utang Terkecil", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                            Text("Nominal paling kecil dulu", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f))
                                            Spacer(Modifier.height(4.dp))
                                            Text("${comparison.snowball.monthsToDebtFree} Bulan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                            Text("Bunga: ${CurrencyFormatter.formatCompactIdr(comparison.snowball.totalInterestPaid, stealth)}", style = MoneySmallStyle, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.9f))
                                        }
                                    }
                                    Card(
                                        modifier = Modifier.weight(1f),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text("Mulai Bunga Tertinggi", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                            Text("Bunga paling besar dulu", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f))
                                            Spacer(Modifier.height(4.dp))
                                            Text("${comparison.avalanche.monthsToDebtFree} Bulan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                            Text("Bunga: ${CurrencyFormatter.formatCompactIdr(comparison.avalanche.totalInterestPaid, stealth)}", style = MoneySmallStyle, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f))
                                        }
                                    }
                                }
                                if (comparison.interestSavedByAvalanche > 0L) {
                                    Text(
                                        "Cara bunga tertinggi menghemat bunga ${CurrencyFormatter.formatIdr(comparison.interestSavedByAvalanche, stealth)}!",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = IncomeGreen,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    item(key = "debt_header", contentType = "section_header") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Daftar Utang & Piutang", style = MaterialTheme.typography.titleMedium)
                            FilledTonalButton(onClick = { debtEditorTarget = true to null }, enabled = state.canEdit) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("Catat")
                            }
                        }
                    }

                    if (state.debtsLoans.isEmpty()) {
                        item(key = "debt_empty", contentType = "empty") {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    SvgLogo(resId = R.drawable.ic_logo_empty_state, contentDescription = null, size = 40.dp)
                                    Text("Bersih dari utang & piutang! Ketuk '+ Catat' jika ingin melacak cicilan.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    items(state.debtsLoans, key = { it.id }, contentType = { "debt_loan" }) { item ->
                        val isDebt = item.kind == "DEBT"
                        val paidRatio = if (item.principalAmount > 0L) {
                            ((item.principalAmount - item.remainingAmount).toFloat() / item.principalAmount).coerceIn(0f, 1f)
                        } else 0f
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        SvgLogo(
                                            resId = if (isDebt) R.drawable.ic_logo_debt_shield else R.drawable.ic_logo_cat_salary,
                                            contentDescription = item.kind,
                                            size = 36.dp
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(item.title, style = MaterialTheme.typography.titleSmall)
                                            Text(
                                                "${if (isDebt) "Utang ke" else "Piutang dari"} ${item.counterparty} • Bunga ${item.interestRateAnnual}%/thn",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    if (!item.isPaidOff && state.canEdit) {
                                        FilledTonalButton(onClick = { payInstallmentTarget = item }) {
                                            Text(if (isDebt) "Cicil" else "Terima")
                                        }
                                    } else if (item.isPaidOff) {
                                        Icon(Icons.Filled.CheckCircle, contentDescription = "Lunas", tint = IncomeGreen)
                                    }
                                }
                                LinearProgressIndicator(
                                    progress = { paidRatio },
                                    color = if (isDebt) MaterialTheme.colorScheme.primary else TransferBlue,
                                    modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp))
                                )
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column {
                                        Text("Sisa: ${CurrencyFormatter.formatIdr(item.remainingAmount, stealth)}", style = MoneySmallStyle, color = if (isDebt) ExpenseRed else TransferBlue)
                                        Text("Min/bln: ${CurrencyFormatter.formatIdr(item.minimumMonthlyPayment, stealth)}", style = MoneySmallStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (state.canEdit) {
                                        Row {
                                            IconButton(onClick = { debtEditorTarget = true to item }, modifier = Modifier.size(48.dp)) {
                                                Icon(Icons.Filled.Edit, contentDescription = "Edit utang/piutang", tint = MaterialTheme.colorScheme.primary)
                                            }
                                            IconButton(onClick = { deleteDebtConfirm = item }, modifier = Modifier.size(48.dp)) {
                                                Icon(Icons.Filled.DeleteOutline, contentDescription = "Hapus utang/piutang", tint = ExpenseRed)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // SUBTAB 1: SINKING FUNDS & GOALS
            1 -> {
                val listState = rememberLazyListState()
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item(key = "goal_header", contentType = "section_header") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (state.isSharedWorkspace) "Tabungan Bersama" else "Celengan & Target Tabungan",
                                style = MaterialTheme.typography.titleMedium
                            )
                            FilledTonalButton(onClick = { goalEditorTarget = true to null }, enabled = state.canEdit) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("Target")
                            }
                        }
                    }
                    if (state.goals.isEmpty()) {
                        item(key = "goal_empty", contentType = "empty") {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    SvgLogo(resId = R.drawable.ic_logo_goal_target, contentDescription = null, size = 40.dp)
                                    Text("Belum ada target tabungan. Ketuk '+ Target' untuk buat tujuan dana impianmu.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    items(state.goals, key = { it.id }, contentType = { "goal" }) { goal ->
                        val ratio = if (goal.targetAmount > 0L) (goal.currentAmount.toFloat() / goal.targetAmount).coerceIn(0f, 1f) else 0f
                        val remaining = (goal.targetAmount - goal.currentAmount).coerceAtLeast(0L)
                        val estMonths = if (goal.monthlyContribution > 0L) ((remaining + goal.monthlyContribution - 1) / goal.monthlyContribution) else 0L
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        SvgLogo(resId = R.drawable.ic_logo_goal_target, contentDescription = goal.title, size = 38.dp)
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(goal.title, style = MaterialTheme.typography.titleSmall)
                                            Text(
                                                "Terkumpul ${(ratio * 100).roundToInt()}% • Estimasi $estMonths bln lagi",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    FilledTonalButton(onClick = { contributeTarget = goal }, enabled = state.canEdit) {
                                        Text("+ Setor")
                                    }
                                }
                                LinearProgressIndicator(
                                    progress = { ratio },
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(5.dp))
                                )
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column {
                                        Text(CurrencyFormatter.formatIdr(goal.currentAmount, stealth), style = MoneySmallStyle, color = MaterialTheme.colorScheme.primary)
                                        Text("Target: ${CurrencyFormatter.formatIdr(goal.targetAmount, stealth)}", style = MoneySmallStyle)
                                    }
                                    if (state.canEdit) {
                                        Row {
                                            IconButton(onClick = { goalEditorTarget = true to goal }, modifier = Modifier.size(48.dp)) {
                                                Icon(Icons.Filled.Edit, contentDescription = "Edit target", tint = MaterialTheme.colorScheme.primary)
                                            }
                                            IconButton(onClick = { deleteGoalConfirm = goal }, modifier = Modifier.size(48.dp)) {
                                                Icon(Icons.Filled.DeleteOutline, contentDescription = "Hapus target", tint = ExpenseRed)
                                            }
                                        }
                                    }
                                }
                                if (goal.contributorSummary.isNotBlank()) {
                                    val contributors = goal.contributorSummary.split("|").filter { it.contains(":") }.map {
                                        val p = it.split(":")
                                        "${p[0]}: ${CurrencyFormatter.formatCompactIdr(p[1].toLongOrNull() ?: 0L, stealth)}"
                                    }
                                    Text(
                                        "Setoran: ${contributors.joinToString(" • ")}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // SUBTAB 2: SPLIT BILL & DEBT SIMPLIFICATION
            else -> {
                val listState = rememberLazyListState()
                val memberMap = remember(state.members) { state.members.associateBy { it.id } }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item(key = "split_simplify_card", contentType = "summary") {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f),
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        SvgLogo(resId = R.drawable.ic_logo_split_bill, contentDescription = null, size = 34.dp)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Ringkasan Pembayaran Patungan", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = onOpenWorkspaceInvite,
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(Icons.Filled.PersonAdd, contentDescription = "Undang & Hubungi Anggota", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                        }
                                        FilledTonalButton(onClick = { showAddSplit = true }, enabled = state.canEdit) {
                                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Text("Buat Patungan")
                                        }
                                    }
                                }
                                if (state.simplifiedSettlements.isEmpty()) {
                                    Text("Semua patungan sudah lunas seimbang!", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f))
                                } else {
                                    state.simplifiedSettlements.forEach { stl ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("${stl.fromMember.name} → ${stl.toMember.name}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                                Text(CurrencyFormatter.formatIdr(stl.amount, stealth), style = MoneyMediumStyle, color = MaterialTheme.colorScheme.error)
                                            }
                                            Button(
                                                onClick = { onSettleTransfer(stl) },
                                                enabled = state.canEdit
                                            ) {
                                                Text("Lunasi")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item(key = "split_history_header", contentType = "section_header") {
                        Text("Riwayat Patungan", style = MaterialTheme.typography.titleMedium)
                    }
                    if (state.splitBills.isEmpty()) {
                        item(key = "split_empty", contentType = "empty") {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    SvgLogo(resId = R.drawable.ic_logo_empty_state, contentDescription = null, size = 56.dp)
                                    Text("Belum ada catatan patungan di ruang keuangan ini.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    } else {
                        items(state.splitBills, key = { it.id }, contentType = { "split_bill" }) { bill ->
                            val payerName = memberMap[bill.paidByMemberId]?.name ?: "Anggota"
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    SvgLogo(resId = R.drawable.ic_logo_split_bill, contentDescription = bill.title, size = 36.dp)
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(bill.title, style = MaterialTheme.typography.titleSmall)
                                            Text(CurrencyFormatter.formatIdr(bill.totalAmount, stealth), style = MoneyMediumStyle)
                                        }
                                        Text(
                                            "Ditalangi oleh $payerName • ${if (bill.isSettled) "Lunas" else "Belum Lunas"}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (bill.isSettled) IncomeGreen else WarningAmber
                                        )
                                    }
                                    if (state.canEdit) {
                                        IconButton(onClick = { deleteSplitConfirm = bill }, modifier = Modifier.size(48.dp)) {
                                            Icon(Icons.Filled.DeleteOutline, contentDescription = "Hapus patungan", tint = ExpenseRed)
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
    debtEditorTarget?.let { (_, existing) ->
        var kind by remember(existing?.id) { mutableStateOf(existing?.kind ?: "DEBT") }
        var title by remember(existing?.id) { mutableStateOf(existing?.title ?: "") }
        var party by remember(existing?.id) { mutableStateOf(existing?.counterparty ?: "") }
        var amtStr by remember(existing?.id) { mutableStateOf(existing?.principalAmount?.toString() ?: "") }
        var remStr by remember(existing?.id) { mutableStateOf(existing?.remainingAmount?.toString() ?: "") }
        var aprStr by remember(existing?.id) { mutableStateOf(existing?.interestRateAnnual?.toString() ?: "12.0") }
        var minStr by remember(existing?.id) { mutableStateOf(existing?.minimumMonthlyPayment?.toString() ?: "") }
        Dialog(
            onDismissRequest = { debtEditorTarget = null },
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
                        if (existing == null) "Catat Utang / Piutang" else "Edit: ${existing.title}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = kind == "DEBT", onClick = { kind = "DEBT" }, label = { Text("Utang Saya") })
                        FilterChip(selected = kind == "LOAN", onClick = { kind = "LOAN" }, label = { Text("Piutang Orang") })
                    }
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Nama Catatan") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_debt_title")
                    )
                    OutlinedTextField(
                        value = party,
                        onValueChange = { party = it },
                        label = { Text("Bank / Pemberi / Peminjam") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_debt_party")
                    )
                    OutlinedTextField(
                        value = amtStr,
                        onValueChange = { amtStr = it.filter(Char::isDigit) },
                        label = { Text("Jumlah Pinjaman Awal (Rp)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_debt_amount")
                    )
                    if (existing != null) {
                        OutlinedTextField(
                            value = remStr,
                            onValueChange = { remStr = it.filter(Char::isDigit) },
                            label = { Text("Sisa Saat Ini (Rp)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(value = aprStr, onValueChange = { aprStr = it }, label = { Text("Bunga %/thn") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = minStr, onValueChange = { minStr = it.filter(Char::isDigit) }, label = { Text("Cicilan Min") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { debtEditorTarget = null }) { Text("Batal") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            val p = amtStr.toLongOrNull() ?: 0L
                            if (title.isNotBlank() && p > 0L) {
                                onSaveDebtLoan(
                                    existing?.id,
                                    kind,
                                    title,
                                    party.ifBlank { "-" },
                                    p,
                                    if (existing != null) (remStr.toLongOrNull() ?: p) else null,
                                    aprStr.toDoubleOrNull() ?: 0.0,
                                    minStr.toLongOrNull() ?: (p / 12).coerceAtLeast(100_000L)
                                )
                                debtEditorTarget = null
                            }
                        }) { Text("Simpan") }
                    }
                }
            }
        }
    }

    deleteDebtConfirm?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteDebtConfirm = null },
            title = { Text("Hapus Catatan?") },
            text = { Text("Catatan '${item.title}' akan dihapus permanen.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteDebtLoan(item)
                        deleteDebtConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed)
                ) { Text("Hapus") }
            },
            dismissButton = { TextButton(onClick = { deleteDebtConfirm = null }) { Text("Batal") } }
        )
    }

    payInstallmentTarget?.let { item ->
        var amtStr by remember { mutableStateOf(item.minimumMonthlyPayment.coerceAtMost(item.remainingAmount).toString()) }
        val accId = state.accounts.firstOrNull()?.id ?: ""
        Dialog(
            onDismissRequest = { payInstallmentTarget = null },
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
                        "Bayar Cicilan: ${item.title}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedTextField(
                        value = amtStr,
                        onValueChange = { amtStr = it.filter(Char::isDigit) },
                        label = { Text("Nominal Pembayaran (Rp)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_pay_installment")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { payInstallmentTarget = null }) { Text("Batal") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            val amt = amtStr.toLongOrNull() ?: 0L
                            if (amt > 0L && accId.isNotBlank()) {
                                onPayDebtInstallment(item, amt, accId)
                                payInstallmentTarget = null
                            }
                        }) { Text("Proses") }
                    }
                }
            }
        }
    }

    goalEditorTarget?.let { (_, existing) ->
        var title by remember(existing?.id) { mutableStateOf(existing?.title ?: "") }
        var targetStr by remember(existing?.id) { mutableStateOf(existing?.targetAmount?.toString() ?: "") }
        var currentStr by remember(existing?.id) { mutableStateOf((existing?.currentAmount ?: 0L).toString()) }
        var monthlyStr by remember(existing?.id) { mutableStateOf(existing?.monthlyContribution?.toString() ?: "") }
        Dialog(
            onDismissRequest = { goalEditorTarget = null },
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
                        if (existing == null) "Buat Target Tabungan" else "Edit Target: ${existing.title}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Nama Target (mis. Dana Nikah)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_goal_title")
                    )
                    OutlinedTextField(
                        value = targetStr,
                        onValueChange = { targetStr = it.filter(Char::isDigit) },
                        label = { Text("Target Dana (Rp)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_goal_target")
                    )
                    if (existing != null) {
                        OutlinedTextField(
                            value = currentStr,
                            onValueChange = { currentStr = it.filter(Char::isDigit) },
                            label = { Text("Terkumpul Saat Ini (Rp)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    OutlinedTextField(
                        value = monthlyStr,
                        onValueChange = { monthlyStr = it.filter(Char::isDigit) },
                        label = { Text("Setoran Rencana / Bulan (Rp)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { goalEditorTarget = null }) { Text("Batal") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            val t = targetStr.toLongOrNull() ?: 0L
                            if (title.isNotBlank() && t > 0L) {
                                onSaveGoal(
                                    existing?.id,
                                    title,
                                    t,
                                    if (existing != null) (currentStr.toLongOrNull() ?: 0L) else 0L,
                                    monthlyStr.toLongOrNull() ?: (t / 12).coerceAtLeast(100_000L),
                                    12
                                )
                                goalEditorTarget = null
                            }
                        }) { Text(if (existing == null) "Buat" else "Simpan") }
                    }
                }
            }
        }
    }

    deleteGoalConfirm?.let { goal ->
        AlertDialog(
            onDismissRequest = { deleteGoalConfirm = null },
            title = { Text("Hapus Target?") },
            text = { Text("Target '${goal.title}' akan dihapus dari daftar tabungan.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteGoal(goal)
                        deleteGoalConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed)
                ) { Text("Hapus") }
            },
            dismissButton = { TextButton(onClick = { deleteGoalConfirm = null }) { Text("Batal") } }
        )
    }

    contributeTarget?.let { goal ->
        var amtStr by remember { mutableStateOf(goal.monthlyContribution.toString()) }
        val accId = state.accounts.firstOrNull()?.id ?: ""
        AlertDialog(
            onDismissRequest = { contributeTarget = null },
            title = { Text("Setor ke ${goal.title}") },
            text = {
                OutlinedTextField(
                    value = amtStr,
                    onValueChange = { amtStr = it.filter(Char::isDigit) },
                    label = { Text("Nominal Setoran (Rp)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    val amt = amtStr.toLongOrNull() ?: 0L
                    if (amt > 0L && accId.isNotBlank()) {
                        onContributeGoal(goal, amt, accId)
                        contributeTarget = null
                    }
                }) { Text("Setor") }
            },
            dismissButton = { TextButton(onClick = { contributeTarget = null }) { Text("Batal") } }
        )
    }

    if (showAddSplit) {
        var title by remember { mutableStateOf("") }
        var totalStr by remember { mutableStateOf("") }
        var payerId by remember { mutableStateOf(state.activeMember?.id ?: state.members.firstOrNull()?.id ?: "") }
        val selectedMembers = remember(state.members) { mutableStateListOf<String>().apply { addAll(state.members.map { it.id }) } }
        AlertDialog(
            onDismissRequest = { showAddSplit = false },
            title = { Text("Catat Patungan Baru") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Nama Patungan") }, singleLine = true)
                    OutlinedTextField(value = totalStr, onValueChange = { totalStr = it.filter(Char::isDigit) }, label = { Text("Total Tagihan (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    Text("Dibayar Oleh:", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        state.members.forEach { m ->
                            FilterChip(selected = payerId == m.id, onClick = { payerId = m.id }, label = { Text(m.name) })
                        }
                    }
                    Text("Peserta Patungan (Bagi Rata):", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        state.members.forEach { m ->
                            val inc = m.id in selectedMembers
                            FilterChip(
                                selected = inc,
                                onClick = { if (inc && selectedMembers.size > 1) selectedMembers.remove(m.id) else if (!inc) selectedMembers.add(m.id) },
                                label = { Text(m.name) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val tot = totalStr.toLongOrNull() ?: 0L
                    if (title.isNotBlank() && tot > 0L && payerId.isNotBlank()) {
                        onCreateSplitBill(title, tot, payerId, selectedMembers.toList())
                        showAddSplit = false
                    }
                }) { Text("Buat") }
            },
            dismissButton = { TextButton(onClick = { showAddSplit = false }) { Text("Batal") } }
        )
    }

    deleteSplitConfirm?.let { bill ->
        AlertDialog(
            onDismissRequest = { deleteSplitConfirm = null },
            title = { Text("Hapus Catatan Patungan?") },
            text = { Text("Catatan patungan '${bill.title}' akan dihapus.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteSplitBill(bill)
                        deleteSplitConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed)
                ) { Text("Hapus") }
            },
            dismissButton = { TextButton(onClick = { deleteSplitConfirm = null }) { Text("Batal") } }
        )
    }
}

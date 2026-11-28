package com.example.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.local.AccountEntity
import com.example.data.local.CategoryEntity
import com.example.data.local.RecurringRuleEntity
import com.example.data.local.TransactionEntity
import com.example.data.local.TransactionItemEntity
import com.example.data.local.TxTypes
import com.example.data.prefs.UserSettings
import com.example.domain.CurrencyFormatter
import com.example.ui.components.SwipeableTransactionHistoryItem
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.WarningAmberSoft
import java.util.Calendar

@Composable
fun CalendarScreen(
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    transactions: List<TransactionEntity>,
    recurringRules: List<RecurringRuleEntity>,
    userSettings: UserSettings,
    categoryMap: Map<String, CategoryEntity>,
    accountMap: Map<String, AccountEntity>,
    splitItemsByTxId: Map<String, List<TransactionItemEntity>>,
    canEdit: Boolean,
    onOpenAddTransactionForDate: (Long) -> Unit,
    onEditTransaction: (TransactionEntity) -> Unit,
    onDeleteTransaction: (TransactionEntity) -> Unit,
    onPayRecurring: (RecurringRuleEntity) -> Unit
) {
    val calendarInstance = remember { Calendar.getInstance() }
    var selectedYear by remember { mutableIntStateOf(calendarInstance.get(Calendar.YEAR)) }
    var selectedMonth by remember { mutableIntStateOf(calendarInstance.get(Calendar.MONTH)) } // 0-based
    var selectedDay by remember { mutableIntStateOf(calendarInstance.get(Calendar.DAY_OF_MONTH)) }

    val daysInMonthList = remember(selectedYear, selectedMonth) {
        val cal = Calendar.getInstance().apply {
            set(selectedYear, selectedMonth, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val startDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1
        
        buildList {
            for (i in 0 until startDayOfWeek) {
                add(null)
            }
            for (d in 1..maxDays) {
                add(d)
            }
        }
    }

    val selectedDateMillis = remember(selectedYear, selectedMonth, selectedDay) {
        Calendar.getInstance().apply {
            set(selectedYear, selectedMonth, selectedDay.coerceIn(1, 31), 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    val transactionsForSelectedDate = remember(transactions, selectedYear, selectedMonth, selectedDay) {
        transactions.filter { tx ->
            val txCal = Calendar.getInstance().apply { timeInMillis = tx.dateMillis }
            txCal.get(Calendar.YEAR) == selectedYear &&
                txCal.get(Calendar.MONTH) == selectedMonth &&
                txCal.get(Calendar.DAY_OF_MONTH) == selectedDay
        }
    }

    val billsForSelectedDate = remember(recurringRules, selectedYear, selectedMonth, selectedDay) {
        recurringRules.filter { rule ->
            if (!rule.isActive) return@filter false
            val ruleCal = Calendar.getInstance().apply { timeInMillis = rule.nextDueDateMillis }
            ruleCal.get(Calendar.YEAR) == selectedYear &&
                ruleCal.get(Calendar.MONTH) == selectedMonth &&
                ruleCal.get(Calendar.DAY_OF_MONTH) == selectedDay
        }
    }

    val isPaydayOnSelectedDate = remember(userSettings, selectedDay) {
        userSettings.isAutoSalaryEnabled && userSettings.salaryPaydayDay == selectedDay
    }

    val dayIncome = remember(transactionsForSelectedDate, isPaydayOnSelectedDate, userSettings) {
        val txInc = transactionsForSelectedDate.filter { it.type == TxTypes.INCOME || it.type == TxTypes.REFUND }.sumOf { it.amount }
        val salaryInc = if (isPaydayOnSelectedDate) userSettings.monthlyBaseSalary else 0L
        txInc + salaryInc
    }
    val dayExpense = remember(transactionsForSelectedDate) {
        transactionsForSelectedDate.filter { it.type == TxTypes.EXPENSE || it.type == TxTypes.SPLIT }.sumOf { it.amount }
    }

    val monthNames = listOf(
        "Januari", "Februari", "Maret", "April", "Mei", "Juni",
        "Juli", "Agustus", "September", "Oktober", "November", "Desember"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                imageVector = Icons.Filled.CalendarMonth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${monthNames.getOrElse(selectedMonth) { "" }} $selectedYear",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilledTonalIconButton(
                                onClick = {
                                    if (selectedMonth > 0) {
                                        selectedMonth--
                                    } else {
                                        selectedMonth = 11
                                        selectedYear--
                                    }
                                    selectedDay = 1
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Bulan Sebelumnya")
                            }
                            OutlinedButton(
                                onClick = {
                                    val now = Calendar.getInstance()
                                    selectedYear = now.get(Calendar.YEAR)
                                    selectedMonth = now.get(Calendar.MONTH)
                                    selectedDay = now.get(Calendar.DAY_OF_MONTH)
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text("Hari Ini", style = MaterialTheme.typography.labelSmall)
                            }
                            FilledTonalIconButton(
                                onClick = {
                                    if (selectedMonth < 11) {
                                        selectedMonth++
                                    } else {
                                        selectedMonth = 0
                                        selectedYear++
                                    }
                                    selectedDay = 1
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Bulan Berikutnya")
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        listOf("Min", "Sen", "Sel", "Rab", "Kam", "Jum", "Sab").forEach { dayLabel ->
                            Text(
                                text = dayLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.width(36.dp)
                            )
                        }
                    }

                    val rows = daysInMonthList.chunked(7)
                    for (row in rows) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            for (dayNumOrNull in row) {
                                if (dayNumOrNull == null) {
                                    Spacer(modifier = Modifier.size(38.dp))
                                } else {
                                    val dayNum = dayNumOrNull
                                    val isSelected = dayNum == selectedDay

                                    val cellTxs = transactions.filter { tx ->
                                        val txC = Calendar.getInstance().apply { timeInMillis = tx.dateMillis }
                                        txC.get(Calendar.YEAR) == selectedYear &&
                                            txC.get(Calendar.MONTH) == selectedMonth &&
                                            txC.get(Calendar.DAY_OF_MONTH) == dayNum
                                    }
                                    val cellBills = recurringRules.filter { rule ->
                                        if (!rule.isActive) return@filter false
                                        val rC = Calendar.getInstance().apply { timeInMillis = rule.nextDueDateMillis }
                                        rC.get(Calendar.YEAR) == selectedYear &&
                                            rC.get(Calendar.MONTH) == selectedMonth &&
                                            rC.get(Calendar.DAY_OF_MONTH) == dayNum
                                    }
                                    val isCellPayday = userSettings.isAutoSalaryEnabled && userSettings.salaryPaydayDay == dayNum

                                    val hasIncome = cellTxs.any { it.type == TxTypes.INCOME || it.type == TxTypes.REFUND } || isCellPayday
                                    val hasExpense = cellTxs.any { it.type == TxTypes.EXPENSE || it.type == TxTypes.SPLIT }
                                    val hasBills = cellBills.isNotEmpty()

                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when {
                                                    isSelected -> MaterialTheme.colorScheme.primary
                                                    else -> Color.Transparent
                                                }
                                            )
                                            .border(
                                                width = if (isSelected) 0.dp else 1.dp,
                                                color = if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                                shape = CircleShape
                                            )
                                            .clickable {
                                                selectedDay = dayNum
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = dayNum.toString(),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = when {
                                                    isSelected -> MaterialTheme.colorScheme.onPrimary
                                                    else -> MaterialTheme.colorScheme.onSurface
                                                }
                                            )
                                            if (!isSelected && (hasIncome || hasExpense || hasBills)) {
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                                    modifier = Modifier.padding(top = 1.dp)
                                                ) {
                                                    if (hasIncome) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(4.dp)
                                                                .clip(CircleShape)
                                                                .background(IncomeGreen)
                                                        )
                                                    }
                                                    if (hasExpense) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(4.dp)
                                                                .clip(CircleShape)
                                                                .background(ExpenseRed)
                                                        )
                                                    }
                                                    if (hasBills) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(4.dp)
                                                                .clip(CircleShape)
                                                                .background(WarningAmberSoft)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            repeat(7 - row.size) {
                                Spacer(modifier = Modifier.size(38.dp))
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = CurrencyFormatter.formatDateShort(selectedDateMillis),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${transactionsForSelectedDate.size + billsForSelectedDate.size + (if (isPaydayOnSelectedDate) 1 else 0)} agenda / catatan",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (canEdit) {
                            Button(
                                onClick = { onOpenAddTransactionForDate(selectedDateMillis) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .height(36.dp)
                                    .testTag("calendar_add_tx_button")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Catat", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Uang Masuk", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = CurrencyFormatter.formatIdr(dayIncome),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = IncomeGreen
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Uang Keluar", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = CurrencyFormatter.formatIdr(dayExpense),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = ExpenseRed
                            )
                        }
                    }
                }
            }
        }

        if (isPaydayOnSelectedDate) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Payments,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Jadwal Gajian Otomatis Bulanan",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Gaji Pokok: ${CurrencyFormatter.formatIdr(userSettings.monthlyBaseSalary)} (Tanggal ${userSettings.salaryPaydayDay})",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        if (billsForSelectedDate.isNotEmpty()) {
            items(items = billsForSelectedDate, key = { "bill_${it.id}" }) { rule ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.NotificationsActive,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Tagihan Jatuh Tempo: ${rule.title}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${CurrencyFormatter.formatIdr(rule.amount)} • ${CurrencyFormatter.frequencyLabel(rule.frequency)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (canEdit) {
                            Button(
                                onClick = { onPayRecurring(rule) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Bayar", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }

        if (transactionsForSelectedDate.isEmpty() && billsForSelectedDate.isEmpty() && !isPaydayOnSelectedDate) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Belum ada catatan atau tagihan di tanggal ini.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        if (canEdit) {
                            TextButton(onClick = { onOpenAddTransactionForDate(selectedDateMillis) }) {
                                Text("+ Tambah Catatan Sekarang")
                            }
                        }
                    }
                }
            }
        } else {
            items(items = transactionsForSelectedDate, key = { it.id }) { tx ->
                val cat = categoryMap[tx.categoryId]
                val acc = accountMap[tx.accountId]
                val toAcc = tx.toAccountId?.let { accountMap[it] }

                SwipeableTransactionHistoryItem(
                    tx = tx,
                    category = cat,
                    accountName = acc?.name ?: "-",
                    toAccountName = toAcc?.name,
                    stealthMode = false,
                    canEdit = canEdit,
                    onDelete = onDeleteTransaction,
                    onClick = onEditTransaction
                )
            }
        }
    }
}

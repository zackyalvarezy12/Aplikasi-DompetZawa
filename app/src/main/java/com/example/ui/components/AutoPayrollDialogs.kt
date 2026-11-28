package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Weekend
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.local.AccountEntity
import com.example.data.local.AccountTypes
import com.example.data.prefs.UserSettings
import com.example.domain.CurrencyFormatter
import com.example.domain.PayrollCalculationInput
import com.example.domain.PayrollCalculator
import com.example.ui.theme.MoneyDisplayStyle
import com.example.ui.theme.MoneyMediumStyle
import com.example.ui.theme.MoneySmallStyle

private val PAYDAY_PRESETS = listOf(1, 10, 15, 17, 25, 28)
private val WORKDAY_PRESETS = listOf(20, 22, 25, 26, 30)

@Composable
fun AutoSalarySetupDialog(
    settings: UserSettings,
    accounts: List<AccountEntity>,
    stealthMode: Boolean,
    onDismiss: () -> Unit,
    onSave: (enabled: Boolean, baseSalary: Long, paydayDay: Int, workingDays: Int, targetAccountId: String) -> Unit,
    onSaveAndOpenPayrollNow: (enabled: Boolean, baseSalary: Long, paydayDay: Int, workingDays: Int, targetAccountId: String) -> Unit
) {
    val nonCardAccounts = remember(accounts) {
        accounts.filter { it.type != AccountTypes.CREDIT_CARD }.ifEmpty { accounts }
    }
    var enabled by remember { mutableStateOf(true) }
    var salaryStr by remember {
        mutableStateOf(
            if (settings.monthlyBaseSalary > 0L) settings.monthlyBaseSalary.toString() else "5000000"
        )
    }
    var paydayStr by remember { mutableStateOf(settings.salaryPaydayDay.coerceIn(1, 31).toString()) }
    var workDaysStr by remember { mutableStateOf(settings.salaryWorkingDays.coerceIn(1, 31).toString()) }
    var selectedAccId by remember(nonCardAccounts) {
        mutableStateOf(
            nonCardAccounts.firstOrNull { it.id == settings.salaryTargetAccountId }?.id
                ?: nonCardAccounts.firstOrNull()?.id
                ?: ""
        )
    }

    val parsedSalary = salaryStr.toLongOrNull() ?: 0L
    val parsedPayday = (paydayStr.toIntOrNull() ?: 17).coerceIn(1, 31)
    val parsedWorkDays = (workDaysStr.toIntOrNull() ?: 22).coerceIn(1, 31)
    val dailyPreview = remember(parsedSalary, parsedWorkDays) {
        PayrollCalculator.calculate(
            PayrollCalculationInput(baseSalary = parsedSalary, workingDaysInMonth = parsedWorkDays)
        ).dailyRate
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 540.dp)
        ) {
            LazyColumn(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            SvgLogo(resId = R.drawable.ic_logo_cat_salary, contentDescription = null, size = 38.dp)
                            Column {
                                Text("Atur Gaji Otomatis", style = MaterialTheme.typography.titleLarge)
                                Text(
                                    "Kotak hitung libur, sakit & izin muncul tiap tanggal gajian",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "Tutup")
                        }
                    }
                }

                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Munculkan Hitungan Tiap Tanggal Gajian", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Muncul otomatis saat tanggal gajian tiba",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = enabled,
                                onCheckedChange = { enabled = it },
                                modifier = Modifier.testTag("auto_salary_enable_switch")
                            )
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = salaryStr,
                        onValueChange = { salaryStr = it.filter(Char::isDigit) },
                        label = { Text("Gaji Pokok Sebulan (Rp)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("auto_salary_amount_input")
                    )
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = paydayStr,
                            onValueChange = {
                                val digits = it.filter(Char::isDigit).take(2)
                                paydayStr = digits
                            },
                            label = { Text("Tanggal Terima Gaji Setiap Bulan (1 - 31)") },
                            leadingIcon = { Icon(Icons.Rounded.CalendarMonth, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("auto_salary_payday_input")
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(PAYDAY_PRESETS) { day ->
                                FilterChip(
                                    selected = parsedPayday == day,
                                    onClick = { paydayStr = day.toString() },
                                    label = { Text("Tgl $day") }
                                )
                            }
                        }
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = workDaysStr,
                            onValueChange = {
                                val digits = it.filter(Char::isDigit).take(2)
                                workDaysStr = digits
                            },
                            label = { Text("Total Hari Kerja Sebulan (Pembagi Gaji)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("auto_salary_workdays_input")
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(WORKDAY_PRESETS) { days ->
                                FilterChip(
                                    selected = parsedWorkDays == days,
                                    onClick = { workDaysStr = days.toString() },
                                    label = { Text("$days hari") }
                                )
                            }
                        }
                    }
                }

                if (nonCardAccounts.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Masuk ke Rekening / Dompet", style = MaterialTheme.typography.labelLarge)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(nonCardAccounts, key = { it.id }) { acc ->
                                    FilterChip(
                                        selected = selectedAccId == acc.id,
                                        onClick = { selectedAccId = acc.id },
                                        label = { Text(acc.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "Ringkasan Pembagian Harian",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                "${CurrencyFormatter.formatIdr(dailyPreview, stealthMode)} / hari kerja",
                                style = MoneyMediumStyle,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                "Setiap bulan pada tanggal $parsedPayday, kotak hitungan otomatis muncul untuk mengisi jumlah libur, sakit & izin.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.88f)
                            )
                        }
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                onSave(enabled, parsedSalary, parsedPayday, parsedWorkDays, selectedAccId)
                            },
                            enabled = parsedSalary > 0L,
                            modifier = Modifier.fillMaxWidth().testTag("save_auto_salary_button")
                        ) {
                            Text("Simpan Pengaturan Gaji")
                        }
                        FilledTonalButton(
                            onClick = {
                                onSaveAndOpenPayrollNow(enabled, parsedSalary, parsedPayday, parsedWorkDays, selectedAccId)
                            },
                            enabled = parsedSalary > 0L,
                            modifier = Modifier.fillMaxWidth().testTag("save_and_open_payroll_button")
                        ) {
                            Text("Simpan & Isi Slip Gaji Sekarang")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MonthlyPayrollPopupDialog(
    settings: UserSettings,
    accounts: List<AccountEntity>,
    stealthMode: Boolean,
    isAutoCyclePrompt: Boolean,
    onDismissSession: () -> Unit,
    onSkipCycle: (String) -> Unit,
    onConfirmPayroll: (PayrollCalculationInput, String, String) -> Unit
) {
    val cycleKey = remember { CurrencyFormatter.currentMonthKey() }
    val monthTitle = remember(cycleKey) { CurrencyFormatter.monthLabel(cycleKey) }
    val nonCardAccounts = remember(accounts) {
        accounts.filter { it.type != AccountTypes.CREDIT_CARD }.ifEmpty { accounts }
    }

    var salaryStr by remember {
        mutableStateOf(
            if (settings.monthlyBaseSalary > 0L) settings.monthlyBaseSalary.toString() else "5000000"
        )
    }
    var workDaysStr by remember { mutableStateOf(settings.salaryWorkingDays.coerceIn(1, 31).toString()) }
    var offDays by remember { mutableIntStateOf(0) }
    var deductOffDays by remember { mutableStateOf(true) }
    var sickDays by remember { mutableIntStateOf(0) }
    var sickDeductionPct by remember { mutableIntStateOf(100) }
    var otherLeaveDays by remember { mutableIntStateOf(0) }
    var extraDeductionStr by remember { mutableStateOf("") }
    var bonusStr by remember { mutableStateOf("") }
    var selectedAccId by remember(nonCardAccounts) {
        mutableStateOf(
            nonCardAccounts.firstOrNull { it.id == settings.salaryTargetAccountId }?.id
                ?: nonCardAccounts.firstOrNull()?.id
                ?: ""
        )
    }

    val input = remember(
        salaryStr,
        workDaysStr,
        offDays,
        deductOffDays,
        sickDays,
        sickDeductionPct,
        otherLeaveDays,
        extraDeductionStr,
        bonusStr
    ) {
        PayrollCalculationInput(
            baseSalary = salaryStr.toLongOrNull() ?: 0L,
            workingDaysInMonth = (workDaysStr.toIntOrNull() ?: 22).coerceIn(1, 31),
            offDays = offDays,
            deductOffDays = deductOffDays,
            sickDays = sickDays,
            sickDeductionPercent = sickDeductionPct,
            otherLeaveDays = otherLeaveDays,
            extraDeduction = extraDeductionStr.toLongOrNull() ?: 0L,
            bonusAmount = bonusStr.toLongOrNull() ?: 0L
        )
    }
    val result = remember(input) { PayrollCalculator.calculate(input) }

    Dialog(
        onDismissRequest = onDismissSession,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .widthIn(max = 560.dp)
                .heightIn(max = 760.dp)
        ) {
            LazyColumn(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            SvgLogo(resId = R.drawable.ic_logo_cat_salary, contentDescription = null, size = 40.dp)
                            Column {
                                Text(
                                    "Waktunya Gajian! 🎉",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Periode $monthTitle • Tgl ${settings.salaryPaydayDay}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(onClick = onDismissSession) {
                            Icon(Icons.Filled.Close, contentDescription = "Tutup")
                        }
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = salaryStr,
                            onValueChange = { salaryStr = it.filter(Char::isDigit) },
                            label = { Text("Gaji Pokok (Rp)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1.7f).testTag("payroll_base_salary_input")
                        )
                        OutlinedTextField(
                            value = workDaysStr,
                            onValueChange = { workDaysStr = it.filter(Char::isDigit).take(2) },
                            label = { Text("Hari Kerja") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("payroll_workdays_input")
                        )
                    }
                }

                // 1. Libur / Off
                item {
                    LeaveStepperCard(
                        icon = Icons.Rounded.Weekend,
                        title = "Libur / Cuti",
                        subtitle = if (deductOffDays) {
                            "Potong ${CurrencyFormatter.formatIdr(result.dailyRate, stealthMode)} / hari"
                        } else {
                            "Libur berbayar (tanpa potongan)"
                        },
                        days = offDays,
                        deductionAmount = result.offDeduction,
                        stealthMode = stealthMode,
                        onDecrement = { if (offDays > 0) offDays-- },
                        onIncrement = { if (offDays < 31) offDays++ },
                        testTagPrefix = "payroll_off"
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = deductOffDays,
                                onClick = { deductOffDays = true },
                                label = { Text("Potong Gaji") }
                            )
                            FilterChip(
                                selected = !deductOffDays,
                                onClick = { deductOffDays = false },
                                label = { Text("Libur Berbayar (0%)") }
                            )
                        }
                    }
                }

                // 2. Sakit
                item {
                    LeaveStepperCard(
                        icon = Icons.Rounded.LocalHospital,
                        title = "Izin Sakit",
                        subtitle = "Tarif potongan: $sickDeductionPct% dari gaji harian",
                        days = sickDays,
                        deductionAmount = result.sickDeduction,
                        stealthMode = stealthMode,
                        onDecrement = { if (sickDays > 0) sickDays-- },
                        onIncrement = { if (sickDays < 31) sickDays++ },
                        testTagPrefix = "payroll_sick"
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = sickDeductionPct == 100,
                                onClick = { sickDeductionPct = 100 },
                                label = { Text("Potong 100%") }
                            )
                            FilterChip(
                                selected = sickDeductionPct == 50,
                                onClick = { sickDeductionPct = 50 },
                                label = { Text("50%") }
                            )
                            FilterChip(
                                selected = sickDeductionPct == 0,
                                onClick = { sickDeductionPct = 0 },
                                label = { Text("0% (Surat Dokter)") }
                            )
                        }
                    }
                }

                // 3. Izin / Alpa / Lain-lain
                item {
                    LeaveStepperCard(
                        icon = Icons.Rounded.EventBusy,
                        title = "Izin Pribadi / Lain-lain",
                        subtitle = "Potong ${CurrencyFormatter.formatIdr(result.dailyRate, stealthMode)} / hari",
                        days = otherLeaveDays,
                        deductionAmount = result.otherLeaveDeduction,
                        stealthMode = stealthMode,
                        onDecrement = { if (otherLeaveDays > 0) otherLeaveDays-- },
                        onIncrement = { if (otherLeaveDays < 31) otherLeaveDays++ },
                        testTagPrefix = "payroll_other_leave"
                    )
                }

                // Extra deduction & bonus
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = extraDeductionStr,
                            onValueChange = { extraDeductionStr = it.filter(Char::isDigit) },
                            label = { Text("Potongan Lain (Rp)") },
                            placeholder = { Text("Kasbon/BPJS") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("payroll_extra_deduction_input")
                        )
                        OutlinedTextField(
                            value = bonusStr,
                            onValueChange = { bonusStr = it.filter(Char::isDigit) },
                            label = { Text("Bonus/Lembur (Rp)") },
                            placeholder = { Text("Opsional") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("payroll_bonus_input")
                        )
                    }
                }

                if (nonCardAccounts.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Rekening Tujuan Gaji", style = MaterialTheme.typography.labelLarge)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(nonCardAccounts, key = { it.id }) { acc ->
                                    FilterChip(
                                        selected = selectedAccId == acc.id,
                                        onClick = { selectedAccId = acc.id },
                                        label = { Text(acc.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                    )
                                }
                            }
                        }
                    }
                }

                // Live Salary Breakdown Card
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "Rincian Gaji Bulan Ini",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            PayrollSummaryRow(
                                label = "Gaji per Hari (÷ ${result.workingDaysInMonth} hr)",
                                value = CurrencyFormatter.formatIdr(result.dailyRate, stealthMode)
                            )
                            PayrollSummaryRow(
                                label = "Hari Masuk Efektif",
                                value = "${result.effectiveWorkingDays} dari ${result.workingDaysInMonth} hari"
                            )
                            PayrollSummaryRow(
                                label = "Potongan Izin (${result.totalLeaveDays} hari)",
                                value = "-${CurrencyFormatter.formatIdr(result.totalLeaveDeduction, stealthMode)}"
                            )
                            if (result.extraDeduction > 0L) {
                                PayrollSummaryRow(
                                    label = "Potongan Lainnya",
                                    value = "-${CurrencyFormatter.formatIdr(result.extraDeduction, stealthMode)}"
                                )
                            }
                            if (result.bonusAmount > 0L) {
                                PayrollSummaryRow(
                                    label = "Bonus / Lembur",
                                    value = "+${CurrencyFormatter.formatIdr(result.bonusAmount, stealthMode)}"
                                )
                            }
                            PayrollSummaryRow(
                                label = "Total Potongan Gaji",
                                value = "-${CurrencyFormatter.formatIdr(result.totalDeduction, stealthMode)}",
                                bold = true
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.25f),
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                            Text(
                                text = "Gaji Bersih Diterima",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            )
                            Text(
                                text = CurrencyFormatter.formatIdr(result.netSalary, stealthMode),
                                style = MoneyDisplayStyle,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.testTag("payroll_net_salary_text")
                            )
                        }
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = { onConfirmPayroll(input, selectedAccId, cycleKey) },
                            enabled = result.baseSalary > 0L,
                            modifier = Modifier.fillMaxWidth().testTag("payroll_confirm_button")
                        ) {
                            Text("Catat Gaji Bersih Sekarang")
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (isAutoCyclePrompt) {
                                TextButton(onClick = { onSkipCycle(cycleKey) }) {
                                    Text("Lewati Bulan Ini")
                                }
                            } else {
                                Spacer(Modifier.width(1.dp))
                            }
                            TextButton(onClick = onDismissSession) {
                                Text("Tutup")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LeaveStepperCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    days: Int,
    deductionAmount: Long,
    stealthMode: Boolean,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    testTagPrefix: String,
    extraContent: (@Composable () -> Unit)? = null
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text(title, style = MaterialTheme.typography.titleSmall)
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (deductionAmount > 0L) {
                            Text(
                                "Potongan: -${CurrencyFormatter.formatIdr(deductionAmount, stealthMode)}",
                                style = MoneySmallStyle,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(
                        onClick = onDecrement,
                        enabled = days > 0,
                        modifier = Modifier.size(48.dp).testTag("${testTagPrefix}_dec")
                    ) {
                        Icon(Icons.Filled.Remove, contentDescription = "Kurangi $title")
                    }
                    Text(
                        text = "$days hr",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    FilledTonalIconButton(
                        onClick = onIncrement,
                        modifier = Modifier.size(48.dp).testTag("${testTagPrefix}_inc")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Tambah $title")
                    }
                }
            }
            if (extraContent != null) {
                extraContent()
            }
        }
    }
}

@Composable
private fun PayrollSummaryRow(
    label: String,
    value: String,
    bold: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = if (bold) 1f else 0.88f)
        )
        Text(
            text = value,
            style = MoneySmallStyle,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

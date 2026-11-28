package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.R
import com.example.domain.CurrencyFormatter
import com.example.security.BiometricAuthHelper
import com.example.security.BiometricAvailability
import com.example.ui.MainUiState
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen

private val PROFILE_COLOR_SWATCHES = listOf(
    0xFF065F46L,
    0xFF2563EBL,
    0xFF7C3AEDL,
    0xFFDB2777L,
    0xFFD97706L,
    0xFF0F766EL
)

@Composable
fun ProfileAndSecurityDialog(
    state: MainUiState,
    onDismiss: () -> Unit,
    onUpdateProfile: (displayName: String, avatarColorHex: Long) -> Unit,
    onUpdateProfilePhoto: (Uri) -> Unit = {},
    onRemoveProfilePhoto: () -> Unit = {},
    onSignInWithGoogle: (Context) -> Unit,
    onSignOutGoogle: () -> Unit,
    onToggleOfflineSim: () -> Unit,
    onTriggerSync: () -> Unit,
    onEnablePin: (String) -> Unit,
    onDisablePin: () -> Unit,
    onSetBiometricEnabled: (Boolean) -> Unit,
    onLockNow: () -> Unit,
    onSetBillReminderEnabled: (Boolean) -> Unit,
    onTestBillNotification: () -> Unit,
    onReplayTutorial: () -> Unit,
    onResetCleanAccount: () -> Unit,
    onLogoutKeepData: () -> Unit,
    onLogoutSwitchAccount: () -> Unit
) {
    val context = LocalContext.current
    val biometricStatus = remember(context) { BiometricAuthHelper.checkAvailability(context) }

    var nameInput by remember(state.currentUser?.name) {
        mutableStateOf(state.currentUser?.name ?: state.activeMember?.name ?: "Pengguna")
    }
    var avatarColor by remember(state.currentUser?.avatarColorHex) {
        mutableLongStateOf(state.currentUser?.avatarColorHex ?: PROFILE_COLOR_SWATCHES.first())
    }
    var pinInput by remember { mutableStateOf("") }
    var showResetConfirm by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }

    var hasNotifPermission by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        )
    }
    val notifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPermission = granted
        if (granted) onSetBillReminderEnabled(true)
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onUpdateProfilePhoto(uri)
        }
    }

    val initials = remember(nameInput) {
        val parts = nameInput.trim().split(" ").filter { it.isNotBlank() }
        when {
            parts.isEmpty() -> "DZ"
            parts.size == 1 -> parts[0].take(2).uppercase()
            else -> "${parts[0].first()}${parts[1].first()}".uppercase()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 560.dp)
                .fillMaxHeight(0.90f),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            UserAvatar(
                                initials = initials,
                                avatarColorHex = avatarColor,
                                photoPath = state.settings.profilePhotoPath,
                                size = 44.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("Profil, Cadangan & Keamanan", style = MaterialTheme.typography.titleLarge)
                                Text(
                                    "Atur nama, cadangan akun Google, PIN & Kunci Wajah",
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

                // 1. User Profile Edit Card
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Profil Pengguna", style = MaterialTheme.typography.titleSmall)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                UserAvatar(
                                    initials = initials,
                                    avatarColorHex = avatarColor,
                                    photoPath = state.settings.profilePhotoPath,
                                    size = 56.dp
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Foto Profil (Opsional)",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = {
                                                photoPickerLauncher.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                )
                                            },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                            modifier = Modifier.testTag("profile_upload_photo_button")
                                        ) {
                                            Icon(Icons.Outlined.AddAPhoto, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                if (state.settings.profilePhotoPath.isNotBlank()) "Ganti Foto" else "Pilih Foto",
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        }
                                        if (state.settings.profilePhotoPath.isNotBlank()) {
                                            TextButton(
                                                onClick = onRemoveProfilePhoto,
                                                colors = ButtonDefaults.textButtonColors(contentColor = ExpenseRed),
                                                modifier = Modifier.testTag("profile_remove_photo_button")
                                            ) {
                                                Text("Hapus", style = MaterialTheme.typography.labelMedium)
                                            }
                                        }
                                    }
                                }
                            }
                            OutlinedTextField(
                                value = nameInput,
                                onValueChange = { nameInput = it },
                                label = { Text("Nama Tampilan") },
                                placeholder = { Text("mis. Najwa Nahdatul Huda") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("profile_name_input")
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                PROFILE_COLOR_SWATCHES.forEach { hex ->
                                    val selected = avatarColor == hex
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(Color(hex))
                                            .border(
                                                width = if (selected) 3.dp else 1.dp,
                                                color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                                shape = CircleShape
                                            )
                                            .clickable { avatarColor = hex },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (selected) {
                                            Icon(Icons.Filled.Check, contentDescription = "Terpilih", tint = Color.White, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                            Button(
                                onClick = {
                                    if (nameInput.trim().isNotEmpty()) {
                                        onUpdateProfile(nameInput.trim(), avatarColor)
                                    }
                                },
                                enabled = nameInput.trim().length >= 2,
                                modifier = Modifier.fillMaxWidth().testTag("save_profile_button")
                            ) {
                                Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Simpan Profil")
                            }
                        }
                    }
                }

                // 2. Google Sign-In & Automatic Firebase Sync
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                SvgLogo(resId = R.drawable.ic_logo_cloud_sync, contentDescription = null, size = 34.dp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Cadangan Otomatis Akun Google", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = if (state.isGoogleLinked) {
                                            "Terhubung: ${state.authUserEmail.ifBlank { state.authUserUid ?: "Akun Google" }}"
                                        } else {
                                            "Tersimpan di HP — sambungkan Google agar data aman jika ganti HP"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                    )
                                }
                            }

                            if (!state.isGoogleLinked) {
                                Button(
                                    onClick = { onSignInWithGoogle(context) },
                                    enabled = !state.isAuthBusy,
                                    modifier = Modifier.fillMaxWidth().testTag("profile_google_signin_button")
                                ) {
                                    if (state.isAuthBusy) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Menghubungkan...")
                                    } else {
                                        Icon(Icons.Outlined.AccountCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Sambungkan Akun Google")
                                    }
                                }
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    Button(
                                        onClick = onTriggerSync,
                                        modifier = Modifier.weight(1f).testTag("manual_sync_button")
                                    ) {
                                        Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Cadangkan (${state.pendingSyncCount})")
                                    }
                                    OutlinedButton(
                                        onClick = onSignOutGoogle,
                                        enabled = !state.isAuthBusy
                                    ) {
                                        Text("Lepas Akun")
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Coba Mode Tanpa Internet", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "Menunggu dicadangkan: ${state.pendingSyncCount} catatan" +
                                            if (state.settings.lastSyncTimestamp > 0L) " • Terakhir ${CurrencyFormatter.formatRelativeDateTime(state.settings.lastSyncTimestamp)}" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f)
                                    )
                                }
                                Switch(
                                    checked = state.settings.simulatedOffline,
                                    onCheckedChange = { onToggleOfflineSim() },
                                    modifier = Modifier.testTag("simulated_offline_switch")
                                )
                            }
                        }
                    }
                }

                // 3. Security: PIN 6-Digit + Face / Biometric Unlock
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                SvgLogo(resId = R.drawable.ic_logo_debt_shield, contentDescription = null, size = 32.dp)
                                Column {
                                    Text("Kunci Aplikasi (PIN & Wajah / Sidik Jari)", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = if (state.settings.isPinEnabled) "PIN 6 Angka Aktif" else "Belum dikunci",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (state.settings.isPinEnabled) IncomeGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = pinInput,
                                onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pinInput = it },
                                label = { Text(if (state.settings.isPinEnabled) "Ganti PIN / Kata Sandi (6 angka)" else "Buat PIN 6 Angka Baru") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("pin_setup_input")
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        if (pinInput.length == 6) {
                                            onEnablePin(pinInput)
                                            pinInput = ""
                                        }
                                    },
                                    enabled = pinInput.length == 6,
                                    modifier = Modifier.weight(1f).testTag("save_pin_button")
                                ) {
                                    Text("Simpan PIN")
                                }
                                OutlinedButton(
                                    onClick = {
                                        onDismiss()
                                        onLockNow()
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Kunci Layar")
                                }
                                if (state.settings.isPinEnabled) {
                                    TextButton(onClick = onDisablePin) {
                                        Text("Hapus", color = ExpenseRed)
                                    }
                                }
                            }

                            HorizontalDivider(Modifier.padding(vertical = 4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Rounded.Face, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Column {
                                        Text("Kunci Wajah / Sidik Jari HP", style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            when {
                                                biometricStatus == BiometricAvailability.UNAVAILABLE -> "HP ini belum mendukung kunci wajah atau sidik jari"
                                                biometricStatus == BiometricAvailability.NONE_ENROLLED -> "Daftarkan wajah atau sidik jari di Pengaturan HP dulu"
                                                !state.settings.isPinEnabled -> "Simpan PIN 6 angka dulu sebagai cadangan"
                                                else -> "Otomatis buka dengan kunci wajah atau sidik jari HP"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Switch(
                                    checked = state.settings.isBiometricEnabled,
                                    enabled = biometricStatus == BiometricAvailability.AVAILABLE && state.settings.isPinEnabled,
                                    onCheckedChange = onSetBiometricEnabled,
                                    modifier = Modifier.testTag("profile_biometric_switch")
                                )
                            }
                        }
                    }
                }

                // 4. Monthly Bill Notifications (H-3 & H-1)
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Pengingat Tagihan Otomatis (H-3 & H-1)", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "Notifikasi otomatis 3 hari & 1 hari sebelum jatuh tempo tagihan bulanan",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = state.settings.isBillReminderEnabled,
                                    onCheckedChange = { enabled ->
                                        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifPermission) {
                                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        } else {
                                            onSetBillReminderEnabled(enabled)
                                        }
                                    },
                                    modifier = Modifier.testTag("bill_reminder_switch")
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifPermission) {
                                    Button(
                                        onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Izinkan Notifikasi")
                                    }
                                }
                                FilledTonalButton(
                                    onClick = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifPermission) {
                                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        } else {
                                            onTestBillNotification()
                                        }
                                    },
                                    modifier = Modifier.weight(1f).testTag("test_bill_notification_button")
                                ) {
                                    Icon(Icons.Outlined.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Uji Notifikasi")
                                }
                            }
                        }
                    }
                }

                // 5. Tutorial, Clean Reset & Log Out Account
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = {
                                    onDismiss()
                                    onReplayTutorial()
                                },
                                modifier = Modifier.weight(1f).testTag("replay_tutorial_button")
                            ) {
                                Icon(Icons.Outlined.School, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Lihat Panduan")
                            }
                            OutlinedButton(
                                onClick = { showResetConfirm = true },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ExpenseRed),
                                modifier = Modifier.weight(1f).testTag("reset_clean_data_button")
                            ) {
                                Icon(Icons.Outlined.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Mulai Dari Nol")
                            }
                        }

                        Button(
                            onClick = { showLogoutConfirm = true },
                            enabled = !state.isAuthBusy,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ExpenseRed.copy(alpha = 0.14f),
                                contentColor = ExpenseRed
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("logout_account_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Keluar dari Akun", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // 6. Audit Trail
                item {
                    Text("Catatan Aktivitas & Keamanan", style = MaterialTheme.typography.titleSmall)
                }
                if (state.auditLogs.isEmpty()) {
                    item {
                        Text(
                            "Belum ada catatan aktivitas.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(state.auditLogs.take(20)) { log ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${log.actorName} • ${log.action}", style = MaterialTheme.typography.labelLarge)
                                Text(log.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                CurrencyFormatter.formatRelativeDateTime(log.createdAt),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Hapus & Mulai Dari Rp 0?") },
            text = {
                Text("Semua catatan uang masuk/keluar, rekening, utang, harta, batas belanja, dan jadwal tagihan di HP ini akan dikosongkan ke Rp 0.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirm = false
                        onDismiss()
                        onResetCleanAccount()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed)
                ) {
                    Text("Ya, Kosongkan Rp 0")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text("Batal")
                }
            }
        )
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("Keluar dari Akun?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (state.isGoogleLinked) {
                            "Akun Google (${state.authUserEmail.ifBlank { "terhubung" }}) akan dilepas dari sesi ini. Pilih cara keluar:"
                        } else {
                            "Pilih apakah ingin keluar sebentar dengan tetap menyimpan catatan di HP ini, atau ganti ke akun baru:"
                        }
                    )
                    Button(
                        onClick = {
                            showLogoutConfirm = false
                            onDismiss()
                            onLogoutKeepData()
                        },
                        modifier = Modifier.fillMaxWidth().testTag("logout_keep_data_button")
                    ) {
                        Text("Keluar Sesi (Simpan Data di HP)")
                    }
                    OutlinedButton(
                        onClick = {
                            showLogoutConfirm = false
                            onDismiss()
                            onLogoutSwitchAccount()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ExpenseRed),
                        modifier = Modifier.fillMaxWidth().testTag("logout_switch_account_button")
                    ) {
                        Text("Keluar & Buat / Ganti Akun Baru")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text("Batal")
                }
            }
        )
    }
}

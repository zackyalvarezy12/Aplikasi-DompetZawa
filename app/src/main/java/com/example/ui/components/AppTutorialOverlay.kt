package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

private data class AppTourStep(
    val tabKey: String,
    val badge: String,
    val title: String,
    val description: String,
    val tip: String,
    val icon: ImageVector,
    val askNotificationPermission: Boolean = false
)

private val APP_TOUR_STEPS = listOf(
    AppTourStep(
        tabKey = "DASHBOARD",
        badge = "1 / 5 • Beranda & Asisten Zawa",
        title = "Beranda Mudah & Asisten Zawa",
        description = "Semua mulai bersih Rp 0 tanpa contoh data palsu. Di Beranda kamu bisa memakai Catat Cepat, mengatur Gaji Otomatis (hitung potongan libur/sakit), atau bertanya langsung ke Asisten Zawa.",
        tip = "Coba ketuk kartu 'Asisten Zawa' di Beranda kapan saja!",
        icon = Icons.Filled.AutoAwesome
    ),
    AppTourStep(
        tabKey = "DASHBOARD",
        badge = "2 / 5 • Banyak Ruang Keuangan",
        title = "Tambah Ruang Keuangan Sesuai Keperluan",
        description = "Ketuk tombol Ruang Keuangan di pojok kiri atas untuk menambah catatan terpisah sesuai kebutuhanmu: Pribadi, Keluarga, Usaha/Toko, Tabungan Nikah, Liburan, Kos, hingga Acara Bersama.",
        tip = "Kamu bisa membuat catatan sendiri di HP maupun catatan bersama lengkap dengan undangan keluarga atau teman.",
        icon = Icons.Filled.Groups
    ),
    AppTourStep(
        tabKey = "LEDGER",
        badge = "3 / 5 • Catatan Uang",
        title = "Catat, Ubah & Hapus Catatan Kapan Saja",
        description = "Tekan tombol + untuk mencatat uang masuk, belanja keluar, pindah saldo, atau patungan beserta foto struk belanja. Ketuk catatan untuk mengubah, atau geser kiri untuk menghapus.",
        tip = "Saldo dompet otomatis menyesuaikan setiap kali catatan ditambah, diubah, atau dihapus.",
        icon = Icons.Filled.ReceiptLong
    ),
    AppTourStep(
        tabKey = "WEALTH",
        badge = "4 / 5 • Dompet & Tagihan Bulanan",
        title = "Dompet, Batas Belanja & Pengingat Tagihan",
        description = "Tambahkan rekening bank, dompet digital, atau uang tunai di halaman Dompet. Atur batas belanja tiap bulan dan jadwalkan tagihan rutin agar pengingat otomatis muncul 3 hari & 1 hari sebelum jatuh tempo.",
        tip = "Izinkan notifikasi di bawah agar pengingat tagihan bulanan tidak terlewat.",
        icon = Icons.Filled.AccountBalanceWallet,
        askNotificationPermission = true
    ),
    AppTourStep(
        tabKey = "ADVISOR",
        badge = "5 / 5 • Laporan, Foto Profil & Keamanan",
        title = "Laporan PDF/Tabel, Foto Profil & Cadangan",
        description = "Buka halaman Laporan untuk cek kesehatan keuangan, alat hitung cicilan, serta cetak ringkasan bulanan ke PDF atau tabel CSV. Ketuk foto/nama di kanan atas untuk pilih foto profil, cadangan Google, PIN, & Kunci Wajah.",
        tip = "Kamu bisa melihat panduan ini lagi kapan saja lewat menu Profil.",
        icon = Icons.Filled.Insights
    )
)

@Composable
fun AppTutorialOverlay(
    userName: String,
    onSelectTabKey: (String) -> Unit,
    onFinishTutorial: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var stepIndex by rememberSaveable { mutableIntStateOf(0) }
    val step = APP_TOUR_STEPS[stepIndex]

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
    }

    LaunchedEffect(stepIndex) {
        onSelectTabKey(step.tabKey)
    }

    BackHandler {
        if (stepIndex > 0) {
            stepIndex--
        } else {
            onFinishTutorial()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.62f))
            .navigationBarsPadding()
            .padding(16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .testTag("app_tutorial_overlay")
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Text(
                            text = step.badge,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                    TextButton(
                        onClick = onFinishTutorial,
                        modifier = Modifier.testTag("tutorial_skip_button")
                    ) {
                        Text("Lewati")
                    }
                }

                LinearProgressIndicator(
                    progress = { (stepIndex + 1).toFloat() / APP_TOUR_STEPS.size.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                )

                AnimatedContent(targetState = step, label = "tutorial_step_content") { current ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = current.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        if (stepIndex == 0 && userName.isNotBlank()) {
                            Text(
                                text = "Selamat datang, $userName!",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Text(
                            text = current.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = current.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f),
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "💡 ${current.tip}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }

                if (step.askNotificationPermission &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    !hasNotifPermission
                ) {
                    FilledTonalButton(
                        onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("tutorial_notif_permission_button")
                    ) {
                        Icon(Icons.Filled.Notifications, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Izinkan Notifikasi Pengingat Tagihan")
                    }
                }

                // Step indicator dots
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    APP_TOUR_STEPS.indices.forEach { i ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(if (i == stepIndex) 24.dp else 10.dp, 10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(
                                    if (i == stepIndex) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant
                                )
                                .clickable { stepIndex = i }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (stepIndex > 0) {
                        OutlinedButton(
                            onClick = { stepIndex-- },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Sebelumnya")
                        }
                    }
                    Button(
                        onClick = {
                            if (stepIndex < APP_TOUR_STEPS.lastIndex) {
                                stepIndex++
                            } else {
                                onSelectTabKey("DASHBOARD")
                                onFinishTutorial()
                            }
                        },
                        modifier = Modifier
                            .weight(1.4f)
                            .height(48.dp)
                            .testTag("tutorial_next_or_finish_button")
                    ) {
                        Text(
                            if (stepIndex < APP_TOUR_STEPS.lastIndex) "Lanjut" else "Selesai & Mulai Pakai"
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = if (stepIndex < APP_TOUR_STEPS.lastIndex) {
                                Icons.AutoMirrored.Filled.ArrowForward
                            } else {
                                Icons.Filled.Check
                            },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

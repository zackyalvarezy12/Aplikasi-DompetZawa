package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.security.BiometricAuthHelper
import com.example.security.BiometricAvailability
import com.example.ui.components.InteractiveLottieMascot
import com.example.ui.components.SvgLogo
import com.example.ui.components.UserAvatar
import com.example.ui.theme.Gold100
import com.example.ui.theme.LocalActiveHeroGradient

private val AVATAR_PALETTE = listOf(
    0xFF065F46L,
    0xFF2563EBL,
    0xFF7C3AEDL,
    0xFFDB2777L,
    0xFFD97706L,
    0xFF0F766EL
)

private data class TutorialSlide(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val badge: String
)

private val TUTORIAL_SLIDES = listOf(
    TutorialSlide(
        title = "Catat Cepat & Mudah Diubah",
        subtitle = "Semua mulai bersih dari Rp 0 tanpa contoh data palsu! Ketik biasa di Catat Cepat (mis. 'Beli beras 65rb Tunai'), ketuk catatan kapan saja untuk diubah, atau geser ke kiri untuk menghapus.",
        icon = Icons.Outlined.EditNote,
        badge = "1 / 3 • Catatan Uang"
    ),
    TutorialSlide(
        title = "Gaji Otomatis & Pengingat Tagihan",
        subtitle = "Atur gaji bulanan & tanggal gajian agar potongan libur atau sakit langsung terhitung. Tagihan listrik, internet, dan langganan akan mengirim pengingat otomatis 3 hari & 1 hari sebelum jatuh tempo.",
        icon = Icons.Outlined.NotificationsActive,
        badge = "2 / 3 • Pengingat Rutin"
    ),
    TutorialSlide(
        title = "Cadangan Google, Kunci Wajah & Laporan",
        subtitle = "Catatan tersimpan aman di HP dan bisa dicadangkan otomatis ke akun Google. Kunci aplikasi dengan PIN 6 angka + Kunci Wajah, lalu cetak laporan anggaran bulanan ke PDF atau tabel CSV.",
        icon = Icons.Outlined.VerifiedUser,
        badge = "3 / 3 • Keamanan & Laporan"
    )
)

@Composable
fun OnboardingScreen(
    tutorialOnlyMode: Boolean = false,
    initialName: String = "",
    isGoogleLinked: Boolean = false,
    googleEmail: String = "",
    isAuthBusy: Boolean = false,
    onSignInWithGoogle: (Context, (Boolean) -> Unit) -> Unit,
    onRegisterWithEmailPassword: (Context, String, String, String, (Boolean, String?) -> Unit) -> Unit,
    onSignInWithEmailPassword: (Context, String, String, (Boolean, String?) -> Unit) -> Unit,
    onCompleteOnboarding: (
        displayName: String,
        avatarColorHex: Long,
        pin6Digit: String?,
        enableBiometric: Boolean,
        profilePhotoUri: Uri?
    ) -> Unit,
    onCompleteTutorialOnly: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val heroColors = LocalActiveHeroGradient.current
    val biometricAvailability = remember(context) { BiometricAuthHelper.checkAvailability(context) }

    var step by rememberSaveable { mutableIntStateOf(if (tutorialOnlyMode) 1 else 0) }
    var displayName by rememberSaveable { mutableStateOf(initialName) }
    var selectedColor by rememberSaveable { mutableLongStateOf(AVATAR_PALETTE.first()) }
    var selectedPhotoUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var wantsPin by rememberSaveable { mutableStateOf(false) }
    var pinInput by rememberSaveable { mutableStateOf("") }
    var wantsBiometric by rememberSaveable { mutableStateOf(false) }
    var tutorialSlideIdx by rememberSaveable { mutableIntStateOf(0) }

    var authMode by rememberSaveable { mutableStateOf("LOGIN") } // "LOGIN" or "REGISTER"
    var emailInput by rememberSaveable { mutableStateOf("") }
    var passwordInput by rememberSaveable { mutableStateOf("") }
    var authError by rememberSaveable { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedPhotoUri = uri
        }
    }

    var hasNotifPermission by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        )
    }
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPermission = granted
    }

    val nameInitials = remember(displayName) {
        val parts = displayName.trim().split(" ").filter { it.isNotBlank() }
        when {
            parts.isEmpty() -> "DZ"
            parts.size == 1 -> parts[0].take(2).uppercase()
            else -> "${parts[0].first()}${parts[1].first()}".uppercase()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(heroColors))
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .widthIn(max = 560.dp)
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top step pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = 0.20f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SvgLogo(resId = R.drawable.ic_logo_nusakas, contentDescription = null, size = 20.dp)
                        Text(
                            text = if (tutorialOnlyMode) "Panduan Dompet Zawa" else if (step == 0) "Dompet Zawa • Aman di HP & Cloud" else "Panduan Aplikasi",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (!tutorialOnlyMode && step > 0) {
                    TextButton(
                        onClick = { step = 0 },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Kembali")
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            InteractiveLottieMascot(size = 94.dp)
            Spacer(Modifier.height(10.dp))

            // Main Card Container
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                AnimatedContent(targetState = step, label = "onboarding_step") { currentStep ->
                    when (currentStep) {
                        // STEP 0: LOGIN OR REGISTER GATEWAY
                        0 -> Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Top Choice Tabs: Masuk Akun (Login) vs Daftar Baru (Register)
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().testTag("onboarding_auth_tabs")) {
                                SegmentedButton(
                                    selected = authMode == "LOGIN",
                                    onClick = { authMode = "LOGIN"; authError = null },
                                    shape = SegmentedButtonDefaults.itemShape(0, 2)
                                ) {
                                    Icon(Icons.Filled.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Masuk (Login)", fontWeight = FontWeight.Bold)
                                }
                                SegmentedButton(
                                    selected = authMode == "REGISTER",
                                    onClick = { authMode = "REGISTER"; authError = null },
                                    shape = SegmentedButtonDefaults.itemShape(1, 2)
                                ) {
                                    Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Daftar Akun", fontWeight = FontWeight.Bold)
                                }
                            }

                            // Reassurance Banner: Safe on phone & backed up online
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Outlined.Shield, contentDescription = null, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Tersimpan Aman di HP & Dicadangkan Online",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            "Catatan keuangan tersimpan langsung di memori HP Anda (cepat & bisa offline), dan otomatis dicadangkan online agar saldo Anda tetap aman.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            if (authMode == "LOGIN") {
                                // --- LOGIN SECTION ---
                                Text(
                                    "Masuk ke Akun Anda",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Pilih akun Google terdaftar atau masukkan email dan kata sandi Anda untuk memulihkan seluruh catatan.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                // 1. Masuk dengan Google
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                                    ),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            SvgLogo(resId = R.drawable.ic_logo_cloud_sync, contentDescription = null, size = 32.dp)
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    "Akun Google Terdaftar",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    "Pulihkan catatan & rekening otomatis",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        Button(
                                            onClick = {
                                                authError = null
                                                onSignInWithGoogle(context) { ok ->
                                                    if (ok) {
                                                        onCompleteOnboarding(
                                                            displayName.trim().ifBlank { "Sahabat" },
                                                            selectedColor,
                                                            if (wantsPin && pinInput.length == 6) pinInput else null,
                                                            wantsBiometric && wantsPin,
                                                            selectedPhotoUri
                                                        )
                                                    }
                                                }
                                            },
                                            enabled = !isAuthBusy,
                                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("onboarding_login_google_btn")
                                        ) {
                                            Icon(Icons.Outlined.AccountCircle, contentDescription = null)
                                            Spacer(Modifier.width(8.dp))
                                            Text("Masuk dengan Akun Google")
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    HorizontalDivider(modifier = Modifier.weight(1f))
                                    Text(
                                        "  atau dengan email  ",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    HorizontalDivider(modifier = Modifier.weight(1f))
                                }

                                // 2. Masuk dengan Email & Kata Sandi
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    ),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            "Masuk dengan Email & Sandi",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        OutlinedTextField(
                                            value = emailInput,
                                            onValueChange = { emailInput = it; authError = null },
                                            label = { Text("Alamat Email") },
                                            placeholder = { Text("contoh@email.com") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth().testTag("onboarding_login_email_input")
                                        )
                                        OutlinedTextField(
                                            value = passwordInput,
                                            onValueChange = { passwordInput = it; authError = null },
                                            label = { Text("Kata Sandi") },
                                            visualTransformation = PasswordVisualTransformation(),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth().testTag("onboarding_login_password_input")
                                        )

                                        if (authError != null) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.errorContainer,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = authError!!,
                                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    modifier = Modifier.padding(10.dp)
                                                )
                                            }
                                        }

                                        Button(
                                            onClick = {
                                                authError = null
                                                if (emailInput.isBlank() || passwordInput.isBlank()) {
                                                    authError = "Email dan kata sandi harus diisi."
                                                    return@Button
                                                }
                                                onSignInWithEmailPassword(context, emailInput.trim(), passwordInput) { ok, err ->
                                                    if (ok) {
                                                        onCompleteOnboarding(
                                                            displayName.trim().ifBlank { emailInput.substringBefore("@") },
                                                            selectedColor,
                                                            if (wantsPin && pinInput.length == 6) pinInput else null,
                                                            wantsBiometric && wantsPin,
                                                            selectedPhotoUri
                                                        )
                                                    } else {
                                                        authError = err ?: "Gagal masuk. Periksa kembali email dan kata sandi."
                                                    }
                                                }
                                            },
                                            enabled = !isAuthBusy,
                                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("onboarding_login_submit_btn")
                                        ) {
                                            Icon(Icons.Filled.Login, contentDescription = null)
                                            Spacer(Modifier.width(8.dp))
                                            Text("Masuk Akun")
                                        }
                                    }
                                }

                                TextButton(
                                    onClick = { authMode = "REGISTER"; authError = null },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Belum punya akun? Ketuk di sini untuk Daftar Baru")
                                }

                            } else {
                                // --- REGISTER SECTION ---
                                Text(
                                    "Buat Akun Baru",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Mulai bersih dari Rp 0 tanpa data palsu. Atur profil Anda, lalu pilih daftar dengan Google atau daftarkan email Anda.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                // Profil: Foto & Nama
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(66.dp)
                                            .clickable {
                                                photoPickerLauncher.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                )
                                            }
                                            .testTag("onboarding_avatar_picker"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        UserAvatar(
                                            initials = nameInitials,
                                            avatarColorHex = selectedColor,
                                            photoUri = selectedPhotoUri,
                                            size = 64.dp,
                                            textStyle = MaterialTheme.typography.titleLarge,
                                            contentDescription = "Foto Profil Opsional"
                                        )
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(24.dp).align(Alignment.BottomEnd)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Filled.PhotoCamera,
                                                    contentDescription = "Pilih Foto Profil",
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                    OutlinedTextField(
                                        value = displayName,
                                        onValueChange = { displayName = it },
                                        label = { Text("Nama Akun / Panggilan") },
                                        placeholder = { Text("mis. Najwa Nahdatul Huda") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("onboarding_name_input")
                                    )
                                }

                                Text("Pilih Warna Avatar", style = MaterialTheme.typography.labelLarge)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    AVATAR_PALETTE.forEach { hex ->
                                        val isSel = selectedColor == hex
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(CircleShape)
                                                .background(Color(hex))
                                                .border(
                                                    width = if (isSel) 3.dp else 1.dp,
                                                    color = if (isSel) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                                    shape = CircleShape
                                                )
                                                .clickable { selectedColor = hex },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSel) {
                                                Icon(Icons.Filled.Check, contentDescription = "Terpilih", tint = Color.White, modifier = Modifier.size(20.dp))
                                            }
                                        }
                                    }
                                }

                                HorizontalDivider()

                                // Opsi Pendaftaran & Cadangan
                                Text(
                                    "Pilih Cara Cadangan & Simpan:",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )

                                // Option 1: Daftar Cepat dengan Google
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            SvgLogo(resId = R.drawable.ic_logo_cloud_sync, contentDescription = null, size = 32.dp)
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    "Daftar Cepat dengan Google",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    "Cadangan otomatis, aman & tanpa repot ingat kata sandi",
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                        }
                                        Button(
                                            onClick = {
                                                onSignInWithGoogle(context) { ok ->
                                                    if (ok) {
                                                        onCompleteOnboarding(
                                                            displayName.trim().ifBlank { "Sahabat" },
                                                            selectedColor,
                                                            if (wantsPin && pinInput.length == 6) pinInput else null,
                                                            wantsBiometric && wantsPin,
                                                            selectedPhotoUri
                                                        )
                                                    }
                                                }
                                            },
                                            enabled = !isAuthBusy,
                                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("onboarding_register_google_btn")
                                        ) {
                                            Icon(Icons.Outlined.AccountCircle, contentDescription = null)
                                            Spacer(Modifier.width(8.dp))
                                            Text("Daftar dengan Akun Google")
                                        }
                                    }
                                }

                                // Option 2: Daftar Mandiri dengan Email
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                    ),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            "Atau Daftar Mandiri dengan Email",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        OutlinedTextField(
                                            value = emailInput,
                                            onValueChange = { emailInput = it; authError = null },
                                            label = { Text("Email Baru") },
                                            placeholder = { Text("nama@email.com") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth().testTag("onboarding_register_email_input")
                                        )
                                        OutlinedTextField(
                                            value = passwordInput,
                                            onValueChange = { passwordInput = it; authError = null },
                                            label = { Text("Kata Sandi (Minimal 6 karakter)") },
                                            visualTransformation = PasswordVisualTransformation(),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth().testTag("onboarding_register_password_input")
                                        )

                                        if (authError != null) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.errorContainer,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = authError!!,
                                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    modifier = Modifier.padding(10.dp)
                                                )
                                            }
                                        }

                                        Button(
                                            onClick = {
                                                authError = null
                                                if (emailInput.isBlank() || passwordInput.length < 6) {
                                                    authError = "Email dan kata sandi minimal 6 karakter harus diisi."
                                                    return@Button
                                                }
                                                onRegisterWithEmailPassword(context, emailInput.trim(), passwordInput, displayName.trim().ifBlank { "Sahabat" }) { ok, err ->
                                                    if (ok) {
                                                        onCompleteOnboarding(
                                                            displayName.trim().ifBlank { emailInput.substringBefore("@") },
                                                            selectedColor,
                                                            if (wantsPin && pinInput.length == 6) pinInput else null,
                                                            wantsBiometric && wantsPin,
                                                            selectedPhotoUri
                                                        )
                                                    } else {
                                                        authError = err ?: "Gagal mendaftarkan akun."
                                                    }
                                                }
                                            },
                                            enabled = !isAuthBusy,
                                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("onboarding_register_submit_btn")
                                        ) {
                                            Text("Daftar Akun Mandiri")
                                        }
                                    }
                                }

                                // Option 3: Simpan Lokal di HP
                                OutlinedCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(Icons.Outlined.PhoneAndroid, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    "Simpan Lokal di HP Saja (Offline)",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    "Catatan tersimpan aman di memori HP Anda tanpa akun cloud. Bisa dicadangkan online kapan saja di menu Profil.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                onCompleteOnboarding(
                                                    displayName.trim().ifBlank { "Sahabat" },
                                                    selectedColor,
                                                    if (wantsPin && pinInput.length == 6) pinInput else null,
                                                    wantsBiometric && wantsPin,
                                                    selectedPhotoUri
                                                )
                                            },
                                            modifier = Modifier.fillMaxWidth().height(46.dp).testTag("onboarding_skip_local_btn")
                                        ) {
                                            Text("Mulai Simpan Lokal di HP")
                                        }
                                    }
                                }

                                HorizontalDivider()

                                // Kunci PIN & Biometrik Opsional
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Aktifkan Kunci PIN 6 Angka", style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            "Opsional, untuk mengamankan catatan saat membuka aplikasi",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = wantsPin,
                                        onCheckedChange = {
                                            wantsPin = it
                                            if (!it) wantsBiometric = false
                                        },
                                        modifier = Modifier.testTag("onboarding_pin_switch")
                                    )
                                }

                                if (wantsPin) {
                                    OutlinedTextField(
                                        value = pinInput,
                                        onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pinInput = it },
                                        label = { Text("Masukkan 6 Angka PIN") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                        visualTransformation = PasswordVisualTransformation(),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth().testTag("onboarding_pin_input")
                                    )

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
                                                Text("Buka dengan Wajah / Sidik Jari HP", style = MaterialTheme.typography.titleSmall)
                                                Text(
                                                    when (biometricAvailability) {
                                                        BiometricAvailability.AVAILABLE -> "Siap pakai kunci wajah atau sidik jari HP"
                                                        BiometricAvailability.NONE_ENROLLED -> "Belum ada wajah atau sidik jari terdaftar di Pengaturan HP"
                                                        BiometricAvailability.UNAVAILABLE -> "HP belum mendukung kunci wajah/sidik jari"
                                                    },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        Switch(
                                            checked = wantsBiometric,
                                            enabled = biometricAvailability == BiometricAvailability.AVAILABLE && pinInput.length == 6,
                                            onCheckedChange = { wantsBiometric = it },
                                            modifier = Modifier.testTag("onboarding_biometric_switch")
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(10.dp))
                            TextButton(
                                onClick = { step = 1 },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Outlined.HelpOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Lihat Panduan & Cara Pakai Aplikasi")
                            }
                        }

                        // STEP 2: 3-CARD INTERACTIVE TUTORIAL + NOTIFICATION PERMISSION
                        else -> Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            val slide = TUTORIAL_SLIDES[tutorialSlideIdx]
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ) {
                                Text(
                                    text = slide.badge,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }

                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(64.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(slide.icon, contentDescription = null, modifier = Modifier.size(34.dp))
                                        }
                                    }
                                    Text(
                                        text = slide.title,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = slide.subtitle,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }

                            // Slide dots
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TUTORIAL_SLIDES.indices.forEach { i ->
                                    Box(
                                        modifier = Modifier
                                            .padding(horizontal = 4.dp)
                                            .size(if (i == tutorialSlideIdx) 24.dp else 10.dp, 10.dp)
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(
                                                if (i == tutorialSlideIdx) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.outlineVariant
                                            )
                                            .clickable { tutorialSlideIdx = i }
                                    )
                                }
                            }

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifPermission && tutorialSlideIdx == 1) {
                                FilledTonalButton(
                                    onClick = { notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                                    modifier = Modifier.fillMaxWidth().testTag("onboarding_notif_permission_button")
                                ) {
                                    Icon(Icons.Outlined.Notifications, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Aktifkan Izin Notifikasi Pengingat Tagihan")
                                }
                            } else if (hasNotifPermission && tutorialSlideIdx == 1) {
                                Text(
                                    "Notifikasi pengingat tagihan H-3 & H-1 siap aktif!",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Spacer(Modifier.weight(1f, fill = false))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (tutorialSlideIdx > 0) {
                                    OutlinedButton(
                                        onClick = { tutorialSlideIdx-- },
                                        modifier = Modifier.weight(1f).height(50.dp)
                                    ) {
                                        Text("Sebelumnya")
                                    }
                                }
                                Button(
                                    onClick = {
                                        if (tutorialSlideIdx < TUTORIAL_SLIDES.lastIndex) {
                                            tutorialSlideIdx++
                                        } else if (tutorialOnlyMode) {
                                            onCompleteTutorialOnly()
                                        } else {
                                            onCompleteOnboarding(
                                                displayName.trim().ifBlank { "Sahabat" },
                                                selectedColor,
                                                if (wantsPin && pinInput.length == 6) pinInput else null,
                                                wantsBiometric && wantsPin,
                                                selectedPhotoUri
                                            )
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1.4f)
                                        .height(50.dp)
                                        .testTag("onboarding_finish_or_next_button")
                                ) {
                                    Text(
                                        when {
                                            tutorialSlideIdx < TUTORIAL_SLIDES.lastIndex -> "Lanjut"
                                            tutorialOnlyMode -> "Tutup Panduan"
                                            else -> "Mulai Pakai Dompet"
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "100% Data Keuangan Bersih & Terkendali olehmu ✨",
                style = MaterialTheme.typography.labelMedium,
                color = Gold100
            )
        }
    }
}
